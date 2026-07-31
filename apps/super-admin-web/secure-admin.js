(() => {
    'use strict';

    const config = window.PNM_ADMIN_CONFIG || {};
    const configuredApiBaseUrl = String(config.apiBaseUrl || '').replace(/\/+$/, '');
    const nativeFetch = window.fetch.bind(window);
    let adminSession = null;
    let authClient = null;

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
        error.style.display = message ? 'block' : 'none';
    }

    function setAuthenticatedUi(authenticated) {
        const modal = document.getElementById('admin-login-modal');
        const badge = document.getElementById('admin-session-badge');
        if (modal) modal.style.display = authenticated ? 'none' : 'flex';
        if (badge) badge.style.display = authenticated ? 'flex' : 'none';
    }

    function requireConfiguration() {
        if (!configuredApiBaseUrl || !config.supabaseUrl || !config.supabaseAnonKey) {
            setAuthenticatedUi(false);
            setLoginError('Admin console configuration is missing. Configure the API and Supabase public values during deployment.');
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

    function rewriteApiUrl(input) {
        const raw = typeof input === 'string' ? input : input.url;
        if (!configuredApiBaseUrl) return raw;
        if (raw.startsWith('http://localhost:8080')) {
            return `${configuredApiBaseUrl}${raw.substring('http://localhost:8080'.length)}`;
        }
        return raw;
    }

    window.fetch = async function securedFetch(input, init = {}) {
        const rewrittenUrl = rewriteApiUrl(input);
        const isApiRequest = configuredApiBaseUrl && rewrittenUrl.startsWith(configuredApiBaseUrl);
        if (!isApiRequest) return nativeFetch(input, init);

        if (!adminSession) await resolveSession();
        if (!isAdminSession(adminSession)) {
            setAuthenticatedUi(false);
            throw new Error('Authenticated ADMIN session required');
        }

        const headers = new Headers(input instanceof Request ? input.headers : undefined);
        new Headers(init.headers || {}).forEach((value, key) => headers.set(key, value));
        headers.delete('X-User-Role');
        headers.delete('X-User-Id');
        headers.delete('X-Internal-Gateway-Secret');
        headers.set('Authorization', `Bearer ${adminSession.access_token}`);

        return nativeFetch(rewrittenUrl, { ...init, headers });
    };

    window.getAdminToken = () => adminSession?.access_token || null;
    window.getAuthHeaders = (customHeaders = {}) => {
        const headers = { ...customHeaders };
        delete headers['X-User-Role'];
        delete headers['X-User-Id'];
        if (adminSession?.access_token) {
            headers.Authorization = `Bearer ${adminSession.access_token}`;
        }
        return headers;
    };

    window.checkAdminAuth = () => {
        const authenticated = isAdminSession(adminSession);
        setAuthenticatedUi(authenticated);
        return authenticated;
    };

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
        await Promise.allSettled([
            window.fetchRefundModeConfig?.(),
            window.fetchPendingProviders?.(),
        ]);
    };

    window.handleAdminSignOut = async () => {
        adminSession = null;
        if (authClient) await authClient.auth.signOut();
        sessionStorage.removeItem('admin_token');
        localStorage.removeItem('admin_token');
        setAuthenticatedUi(false);
        window.showToast?.('Admin session signed out');
    };

    window.rejectProvider = async (providerId) => {
        try {
            const response = await fetch(
                `${configuredApiBaseUrl}/api/v1/providers/${encodeURIComponent(providerId)}/reject`,
                { method: 'POST' },
            );
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.error || 'Provider rejection failed');
            }
            window.showToast?.('Provider rejected');
            await window.fetchPendingProviders?.();
        } catch (error) {
            window.showToast?.(error.message || 'Provider rejection failed', true);
        }
    };

    window.fetchBannerAuctionOutcomes = async () => {
        const container = document.getElementById('banner-auction-list');
        if (!container) return;
        container.replaceChildren();
        const loading = document.createElement('p');
        loading.textContent = 'Loading auction outcomes...';
        container.appendChild(loading);

        try {
            const response = await fetch(`${configuredApiBaseUrl}/api/v1/content/banners/auction-outcomes`);
            if (!response.ok) throw new Error(`Request failed (${response.status})`);
            const outcomes = await response.json();
            container.replaceChildren();

            if (!Array.isArray(outcomes) || outcomes.length === 0) {
                const empty = document.createElement('p');
                empty.textContent = 'No active banner slots with auction outcomes yet.';
                container.appendChild(empty);
                return;
            }

            outcomes.forEach((slot) => {
                const item = document.createElement('div');
                item.className = 'list-item';

                const header = document.createElement('div');
                header.className = 'item-header';
                const title = document.createElement('h3');
                title.className = 'item-title';
                title.textContent = `Slot ${slot.slotOrder ?? '—'}: ${slot.title || 'Banner'}`;
                const status = document.createElement('span');
                status.className = `badge ${slot.active ? 'badge-success' : 'badge-pending'}`;
                status.textContent = slot.status || 'UNKNOWN';
                header.append(title, status);

                const details = document.createElement('div');
                const provider = document.createElement('div');
                provider.textContent = `Winning provider: ${slot.providerId || 'Unassigned'}`;
                const amount = document.createElement('div');
                amount.textContent = `Winning bid: ${slot.bidAmount == null ? '—' : `₹${Number(slot.bidAmount).toFixed(2)}`}`;
                const duration = document.createElement('div');
                duration.textContent = `Duration: ${slot.durationSec ?? '—'}s`;
                details.append(provider, amount, duration);

                item.append(header, details);
                container.appendChild(item);
            });
        } catch (error) {
            container.replaceChildren();
            const message = document.createElement('p');
            message.textContent = `Failed to load auction outcomes: ${error.message || 'Unknown error'}`;
            container.appendChild(message);
        }
    };

    function configureLoginForm() {
        const emailInput = document.getElementById('admin-token-input');
        if (!emailInput) return;
        emailInput.type = 'email';
        emailInput.autocomplete = 'username';
        emailInput.placeholder = 'Admin email';

        if (!document.getElementById('admin-password-input')) {
            const password = document.createElement('input');
            password.id = 'admin-password-input';
            password.type = 'password';
            password.required = true;
            password.autocomplete = 'current-password';
            password.placeholder = 'Password';
            password.style.cssText = emailInput.style.cssText;
            emailInput.insertAdjacentElement('afterend', password);
        }

        const description = emailInput.closest('div')?.querySelector('p');
        if (description) {
            description.textContent = 'Sign in with an authorized Supabase ADMIN account.';
        }
    }

    async function initialize() {
        configureLoginForm();
        if (!requireConfiguration()) return;
        authClient = window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey, {
            auth: { persistSession: true, autoRefreshToken: true, detectSessionInUrl: true },
        });
        await resolveSession();
        setAuthenticatedUi(isAdminSession(adminSession));
        authClient.auth.onAuthStateChange((_event, session) => {
            adminSession = isAdminSession(session) ? session : null;
            setAuthenticatedUi(Boolean(adminSession));
        });
        if (adminSession) {
            await Promise.allSettled([
                window.fetchRefundModeConfig?.(),
                window.fetchPendingProviders?.(),
            ]);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => void initialize());
    } else {
        void initialize();
    }
})();
