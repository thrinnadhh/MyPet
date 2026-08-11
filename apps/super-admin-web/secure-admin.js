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

    window.getAuthHeaders = (customHeaders = {}) => {
        const headers = { ...customHeaders };
        delete headers['X-User-Role'];
        delete headers['X-User-Id'];
        delete headers.Authorization;
        return headers;
    };

    window.checkAdminAuth = () => {
        const authenticated = isAdminSession(adminSession);
        setAuthenticatedUi(authenticated);
        return authenticated;
    };

    function normalizeAdminBranding() {
        document.title = 'PawsNearMe — Admin Console';
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        const nodes = [];
        while (walker.nextNode()) nodes.push(walker.currentNode);
        nodes.forEach((node) => {
            if (!node.nodeValue) return;
            node.nodeValue = node.nodeValue
                .replace(/PNM SUPER ADMIN/g, 'PNM ADMIN')
                .replace(/Super Admin/g, 'Admin')
                .replace(/SUPER ADMIN/g, 'ADMIN');
        });
        document.querySelectorAll('[placeholder]').forEach((element) => {
            const current = element.getAttribute('placeholder');
            if (current) element.setAttribute('placeholder', current.replace(/Super Admin/gi, 'Admin'));
        });

        // Remove hard-coded infrastructure statistics. The Admin console renders only
        // server-authoritative operational data for this stabilization sprint.
        Array.from(document.querySelectorAll('.card-title')).forEach((title) => {
            if (title.textContent?.includes('System Statistics')) {
                const card = title.closest('.card');
                if (card) card.style.display = 'none';
            }
        });
    }

    function createMetric(label, value, danger = false) {
        const card = document.createElement('div');
        card.className = 'metric-card';
        const labelNode = document.createElement('div');
        const heading = document.createElement('h4');
        heading.style.cssText = 'font-size: 0.82rem; color: var(--text-secondary);';
        heading.textContent = label;
        labelNode.appendChild(heading);
        const valueNode = document.createElement('span');
        valueNode.className = 'metric-value';
        if (danger && Number(value) > 0) valueNode.style.color = 'var(--accent-rose)';
        valueNode.textContent = String(value ?? 0);
        card.append(labelNode, valueNode);
        return card;
    }

    function renderOperationsSnapshot(snapshot) {
        const container = document.getElementById('admin-operations-metrics');
        const generated = document.getElementById('admin-operations-generated');
        if (!container) return;
        container.replaceChildren();
        const metrics = [
            ['Orders placed', snapshot.ordersPlaced],
            ['Merchant pending', snapshot.merchantPending],
            ['Accepted', snapshot.accepted],
            ['Preparing', snapshot.preparing],
            ['Ready for pickup', snapshot.readyForPickup],
            ['Captain assigned', snapshot.assigned],
            ['Dispatch failures', snapshot.dispatchFailures, true],
            ['Picked up', snapshot.pickedUp],
            ['Delivered', snapshot.delivered],
            ['Completed', snapshot.completed],
            ['Cancelled', snapshot.cancelled],
            ['Rejected', snapshot.rejected],
            ['Payment failures', snapshot.paymentFailures, true],
            ['Refund pending', snapshot.refundPending, true],
            ['Refunded', snapshot.refunds],
            ['Open support', snapshot.openSupportCases, true],
            ['Open disputes', snapshot.openDisputes, true],
        ];
        metrics.forEach(([label, value, danger]) => container.appendChild(createMetric(label, value, danger)));
        if (generated) {
            const parsed = snapshot.generatedAt ? new Date(snapshot.generatedAt) : null;
            generated.textContent = parsed && !Number.isNaN(parsed.getTime())
                ? `Server snapshot generated ${parsed.toLocaleString()}`
                : 'Server-authoritative lifecycle snapshot';
        }
    }

    async function fetchOperationsSnapshot() {
        const container = document.getElementById('admin-operations-metrics');
        if (container) {
            container.replaceChildren();
            const loading = document.createElement('p');
            loading.textContent = 'Loading canonical order lifecycle snapshot...';
            container.appendChild(loading);
        }
        try {
            const response = await fetch(`${configuredApiBaseUrl}/api/v1/orders/admin/operations/snapshot`);
            if (!response.ok) throw new Error(`Request failed (${response.status})`);
            const snapshot = await response.json();
            renderOperationsSnapshot(snapshot);
        } catch (error) {
            if (container) {
                container.replaceChildren();
                const message = document.createElement('p');
                message.textContent = `Admin operations unavailable: ${error.message || 'Unknown error'}`;
                container.appendChild(message);
            }
        }
    }
    window.fetchOperationsSnapshot = fetchOperationsSnapshot;

    function installOperationsDashboard() {
        if (document.getElementById('operations-panel')) return;
        const tabs = document.querySelector('.tabs');
        const section = document.querySelector('.grid-panels > section');
        if (!tabs || !section) return;

        const button = document.createElement('button');
        button.className = 'tab-btn';
        button.type = 'button';
        button.textContent = 'Operations';
        button.addEventListener('click', () => {
            window.switchTab?.('operations');
            void fetchOperationsSnapshot();
        });
        tabs.prepend(button);

        const panel = document.createElement('div');
        panel.id = 'operations-panel';
        panel.className = 'tab-panel';
        panel.style.display = 'none';

        const card = document.createElement('div');
        card.className = 'card';
        const header = document.createElement('div');
        header.className = 'card-header';
        const title = document.createElement('h2');
        title.className = 'card-title';
        title.textContent = 'Canonical Order Operations';
        const refresh = document.createElement('button');
        refresh.type = 'button';
        refresh.className = 'btn btn-emerald';
        refresh.textContent = 'Refresh';
        refresh.addEventListener('click', () => void fetchOperationsSnapshot());
        header.append(title, refresh);

        const explanation = document.createElement('p');
        explanation.style.cssText = 'color: var(--text-secondary); margin-bottom: 1rem;';
        explanation.textContent = 'This dashboard reads the same canonical order and payment lifecycle used by Customer, Merchant, Dispatch and Captain flows. No alternate Admin order states are defined.';

        const generated = document.createElement('p');
        generated.id = 'admin-operations-generated';
        generated.style.cssText = 'font-size: 0.82rem; color: var(--text-secondary); margin-bottom: 1rem;';

        const metrics = document.createElement('div');
        metrics.id = 'admin-operations-metrics';
        metrics.style.cssText = 'display: grid; grid-template-columns: repeat(auto-fit,minmax(180px,1fr)); gap: 0.75rem;';
        card.append(header, explanation, generated, metrics);
        panel.appendChild(card);
        section.prepend(panel);
    }

    function showOperationsDashboard() {
        if (!isAdminSession(adminSession)) return;
        window.switchTab?.('operations');
        void fetchOperationsSnapshot();
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
        await Promise.allSettled([
            window.fetchRefundModeConfig?.(),
            window.fetchPendingProviders?.(),
            fetchOperationsSnapshot(),
        ]);
        showOperationsDashboard();
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
        normalizeAdminBranding();
        installOperationsDashboard();
        configureLoginForm();
        if (!requireConfiguration()) return;
        authClient = window.supabase.createClient(config.supabaseUrl, config.supabaseAnonKey, {
            auth: { persistSession: false, autoRefreshToken: true, detectSessionInUrl: false },
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
                fetchOperationsSnapshot(),
            ]);
            showOperationsDashboard();
        }
    }

    normalizeAdminBranding();
    installOperationsDashboard();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => void initialize());
    } else {
        void initialize();
    }
})();
