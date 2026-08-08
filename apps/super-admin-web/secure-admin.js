(() => {
    'use strict';

    const config = window.PNM_ADMIN_CONFIG || {};
    const configuredApiBaseUrl = String(config.apiBaseUrl || '').replace(/\/+$/, '');
    const nativeFetch = window.fetch.bind(window);
    const forbiddenIdentityHeaders = [
        'X-User-Id',
        'X-User-Role',
        'X-User-Email',
        'X-User-Full-Name',
        'X-User-Phone',
        'X-Admin-Api-Key',
        'X-Internal-Gateway-Secret',
        'X-Internal-Secret',
        'X-Service-Name',
    ];

    let configuredApiUrl = null;
    let adminSession = null;
    let authClient = null;

    try {
        configuredApiUrl = configuredApiBaseUrl ? new URL(configuredApiBaseUrl, window.location.href) : null;
    } catch {
        configuredApiUrl = null;
    }

    function getRole(user) {
        return String(user?.app_metadata?.role || '').toUpperCase();
    }

    function isAdminSession(session) {
        return Boolean(session?.access_token && getRole(session.user) === 'ADMIN');
    }

    function setLoginError(message) {
        const error = document.getElementById('login-error-msg');
        if (!error) return;
        error.textContent = message || '';
        error.hidden = !message;
    }

    function setAuthenticatedUi(authenticated) {
        const modal = document.getElementById('admin-login-modal');
        const badge = document.getElementById('admin-session-badge');
        if (modal) modal.hidden = authenticated;
        if (badge) badge.hidden = !authenticated;
    }

    function renderEnvironment() {
        const target = document.getElementById('admin-environment');
        if (!target) return;
        if (!configuredApiUrl) {
            target.textContent = 'UNCONFIGURED';
            return;
        }
        target.textContent = String(config.environmentName || configuredApiUrl.host || 'CONFIGURED').toUpperCase();
    }

    function requireConfiguration() {
        renderEnvironment();
        if (!configuredApiUrl || !config.supabaseUrl || !config.supabaseAnonKey) {
            setAuthenticatedUi(false);
            setLoginError('Admin console configuration is missing. Configure the API URL and Supabase public values during deployment.');
            return false;
        }
        if (!window.supabase?.createClient) {
            setAuthenticatedUi(false);
            setLoginError('Supabase Auth SDK failed to load.');
            return false;
        }
        return true;
    }

    async function resolveSession() {
        if (!authClient) return null;
        const { data, error } = await authClient.auth.getSession();
        if (error) throw error;
        adminSession = isAdminSession(data.session) ? data.session : null;
        return adminSession;
    }

    function isConfiguredApiRequest(rawUrl) {
        if (!configuredApiUrl) return false;
        try {
            const candidate = new URL(rawUrl, window.location.href);
            if (candidate.origin !== configuredApiUrl.origin) return false;

            const basePath = configuredApiUrl.pathname.replace(/\/+$/, '');
            if (!basePath || basePath === '/') return true;
            return candidate.pathname === basePath || candidate.pathname.startsWith(`${basePath}/`);
        } catch {
            return false;
        }
    }

    function sanitizeHeaders(input, init) {
        const headers = new Headers(input instanceof Request ? input.headers : undefined);
        new Headers(init?.headers || {}).forEach((value, key) => headers.set(key, value));
        forbiddenIdentityHeaders.forEach((header) => headers.delete(header));
        return headers;
    }

    window.fetch = async function securedFetch(input, init = {}) {
        const rawUrl = typeof input === 'string' || input instanceof URL ? String(input) : input.url;
        if (!isConfiguredApiRequest(rawUrl)) return nativeFetch(input, init);

        if (!adminSession) await resolveSession();
        if (!isAdminSession(adminSession)) {
            setAuthenticatedUi(false);
            throw new Error('Authenticated ADMIN session required');
        }

        const headers = sanitizeHeaders(input, init);
        headers.set('Authorization', `Bearer ${adminSession.access_token}`);

        const request = input instanceof Request ? new Request(rawUrl, input) : new Request(rawUrl);
        return nativeFetch(request, { ...init, headers });
    };

    window.getAuthHeaders = (customHeaders = {}) => {
        const headers = new Headers(customHeaders);
        forbiddenIdentityHeaders.forEach((header) => headers.delete(header));
        headers.delete('Authorization');
        return Object.fromEntries(headers.entries());
    };

    window.checkAdminAuth = () => {
        const authenticated = isAdminSession(adminSession);
        setAuthenticatedUi(authenticated);
        return authenticated;
    };

    async function loadInitialAdminData() {
        await Promise.allSettled([
            window.fetchRefundModeConfig?.(),
            window.fetchPendingProviders?.(),
            window.fetchOperationalSnapshot?.(),
        ]);
    }

    window.handleAdminLogin = async (event) => {
        event?.preventDefault();
        setLoginError('');
        if (!requireConfiguration()) return;

        const email = document.getElementById('admin-token-input')?.value.trim() || '';
        const password = document.getElementById('admin-password-input')?.value || '';
        if (!email || !password) {
            setLoginError('Admin email and password are required.');
            return;
        }

        const { data, error } = await authClient.auth.signInWithPassword({ email, password });
        if (error) {
            setLoginError(error.message || 'Authentication failed.');
            return;
        }
        if (!isAdminSession(data.session)) {
            await authClient.auth.signOut();
            adminSession = null;
            setLoginError('This account does not have the ADMIN role.');
            return;
        }

        adminSession = data.session;
        setAuthenticatedUi(true);
        window.showToast?.('Admin session authenticated');
        await loadInitialAdminData();
    };

    window.handleAdminSignOut = async () => {
        adminSession = null;
        if (authClient) await authClient.auth.signOut();
        sessionStorage.removeItem('admin_token');
        localStorage.removeItem('admin_token');
        setAuthenticatedUi(false);
        window.showToast?.('Admin session signed out');
    };

    async function initialize() {
        renderEnvironment();
        if (!requireConfiguration()) return;

        // Admin sessions intentionally do not survive a full browser restart/reload.
        // Auto-refresh remains enabled while the authenticated console is open.
        authClient = window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey, {
            auth: { persistSession: false, autoRefreshToken: true, detectSessionInUrl: false },
        });

        try {
            await resolveSession();
        } catch (error) {
            adminSession = null;
            setLoginError(error.message || 'Unable to restore the Admin session.');
        }
        setAuthenticatedUi(isAdminSession(adminSession));

        authClient.auth.onAuthStateChange((event, session) => {
            adminSession = isAdminSession(session) ? session : null;
            setAuthenticatedUi(Boolean(adminSession));
            if (adminSession && (event === 'SIGNED_IN' || event === 'TOKEN_REFRESHED')) {
                void loadInitialAdminData();
            }
        });

        if (adminSession) await loadInitialAdminData();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => void initialize());
    } else {
        void initialize();
    }
})();
