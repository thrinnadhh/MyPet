const ADMIN_CONFIG = window.PNM_ADMIN_CONFIG || {};
const API_BASE_URL = String(ADMIN_CONFIG.apiBaseUrl || '').replace(/\/+$/, '');

let activeTab = 'approvals';
let currentDisputeId = null;
let currentProviderPage = 0;
let currentUserPage = 0;
const PROVIDER_PAGE_SIZE = 25;
const USER_PAGE_SIZE = 25;
const pendingMutations = new Set();

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function apiUrl(path) {
    if (!API_BASE_URL) throw new Error('Admin API base URL is not configured.');
    return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}

async function readApiError(response, fallback) {
    try {
        const body = await response.json();
        return body.error || body.message || fallback;
    } catch {
        return `${fallback} (${response.status})`;
    }
}

function renderState(container, iconText, messageText) {
    if (!container) return;
    const state = document.createElement('div');
    state.className = 'empty-state';
    const icon = document.createElement('span');
    icon.className = 'empty-icon';
    icon.textContent = iconText;
    const message = document.createElement('p');
    message.textContent = messageText;
    state.append(icon, message);
    container.replaceChildren(state);
}

function createTextElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) element.className = className;
    element.textContent = text;
    return element;
}

async function runMutation(key, operation) {
    if (pendingMutations.has(key)) return null;
    pendingMutations.add(key);
    try {
        return await operation();
    } finally {
        pendingMutations.delete(key);
    }
}

function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-btn').forEach((btn) => {
        btn.classList.toggle('active', btn.dataset.tab === tabId);
    });
    document.querySelectorAll('.tab-panel').forEach((panel) => {
        panel.hidden = panel.id !== `${tabId}-panel`;
    });

    if (tabId === 'approvals') void fetchPendingProviders(currentProviderPage);
    if (tabId === 'disputes') void fetchDisputes();
    if (tabId === 'banner-auction') void fetchBannerAuctionOutcomes();
    if (tabId === 'users') void fetchUsers(currentUserPage);
}

function showToast(message, isError = false) {
    const toast = document.getElementById('alert-toast');
    const icon = document.getElementById('toast-icon');
    const msg = document.getElementById('toast-message');
    if (!toast || !icon || !msg) return;

    icon.textContent = isError ? '❌' : '✅';
    toast.classList.toggle('error', isError);
    msg.textContent = message;
    toast.classList.add('active');
    window.setTimeout(() => toast.classList.remove('active'), 3500);
}

async function fetchRefundModeConfig() {
    const checkbox = document.getElementById('refund-mode-checkbox');
    if (!checkbox) return;
    checkbox.disabled = true;
    try {
        const response = await fetch(apiUrl('/api/v1/orders/admin/config'));
        if (!response.ok) throw new Error(await readApiError(response, 'Failed to load refund policy'));
        const data = await response.json();
        checkbox.checked = data.dispute_refund_mode === 'AUTOMATED';
    } catch (error) {
        showToast(error.message || 'Failed to load refund policy', true);
    } finally {
        checkbox.disabled = false;
    }
}

async function toggleRefundMode(isAutomated) {
    const checkbox = document.getElementById('refund-mode-checkbox');
    const mode = isAutomated ? 'AUTOMATED' : 'MANUAL';
    await runMutation('refund-mode', async () => {
        if (checkbox) checkbox.disabled = true;
        try {
            const response = await fetch(apiUrl('/api/v1/orders/admin/config'), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ dispute_refund_mode: mode }),
            });
            if (!response.ok) throw new Error(await readApiError(response, 'Failed to update refund policy'));
            const authoritative = await response.json();
            if (checkbox) checkbox.checked = authoritative.dispute_refund_mode === 'AUTOMATED';
            showToast(`Refund policy updated to ${authoritative.dispute_refund_mode}`);
        } catch (error) {
            if (checkbox) checkbox.checked = !isAutomated;
            showToast(error.message || 'Failed to update refund policy', true);
        } finally {
            if (checkbox) checkbox.disabled = false;
        }
    });
}

async function fetchPendingProviders(page = currentProviderPage) {
    const container = document.getElementById('pending-providers-list');
    renderState(container, '⏳', 'Loading onboarding queue...');

    try {
        const safePage = Math.max(0, Number(page) || 0);
        const response = await fetch(apiUrl(`/api/v1/providers/admin?status=PENDING_APPROVAL&page=${safePage}&size=${PROVIDER_PAGE_SIZE}`));
        if (!response.ok) throw new Error(await readApiError(response, 'Failed to fetch pending providers'));
        const payload = await response.json();
        const providers = Array.isArray(payload) ? payload : payload.content;
        if (!Array.isArray(providers) || providers.length === 0) {
            currentProviderPage = safePage;
            renderState(container, '🎉', safePage === 0 ? 'Onboarding queue is clear.' : 'No pending providers on this page.');
            return;
        }

        currentProviderPage = Number(payload.page ?? safePage);
        const totalPages = Number(payload.totalPages ?? 1);
        const totalElements = Number(payload.totalElements ?? providers.length);
        container.replaceChildren();
        providers.forEach((provider) => {
            const item = document.createElement('article');
            item.className = 'list-item';

            const header = document.createElement('div');
            header.className = 'item-header';
            const heading = document.createElement('div');
            heading.append(
                createTextElement('h3', 'item-title', String(provider.name || 'Unnamed provider')),
                createTextElement('p', 'item-subtitle', `${provider.providerType || 'UNKNOWN'} — ${provider.fulfillmentType || 'UNKNOWN'}`),
            );
            const badge = createTextElement('span', 'badge badge-pending', 'PENDING');
            header.append(heading, badge);

            const details = document.createElement('div');
            details.className = 'item-details';
            details.append(
                createTextElement('div', '', `City: ${provider.city || '—'} | Pincode: ${provider.pincode || '—'}`),
                createTextElement('div', '', `Address: ${provider.addressLine || '—'}`),
                createTextElement('div', '', provider.licenseNumber ? `License: ${provider.licenseNumber}` : 'No license number supplied'),
            );

            const actions = document.createElement('div');
            actions.className = 'btn-group';
            const approve = createTextElement('button', 'btn btn-emerald', 'Approve');
            approve.type = 'button';
            approve.addEventListener('click', () => void approveProvider(String(provider.providerId)));
            const reject = createTextElement('button', 'btn btn-rose', 'Reject');
            reject.type = 'button';
            reject.addEventListener('click', () => void rejectProvider(String(provider.providerId)));
            actions.append(approve, reject);

            item.append(header, details, actions);
            container.appendChild(item);
        });

        const pagination = document.createElement('div');
        pagination.className = 'pagination';
        const summary = createTextElement('span', 'pagination-summary', `Page ${currentProviderPage + 1} of ${Math.max(totalPages, 1)} · ${totalElements} pending providers`);
        const controls = document.createElement('div');
        controls.className = 'btn-group';
        const previous = createTextElement('button', 'btn btn-secondary', 'Previous');
        previous.type = 'button';
        previous.disabled = currentProviderPage <= 0;
        previous.addEventListener('click', () => void fetchPendingProviders(currentProviderPage - 1));
        const next = createTextElement('button', 'btn btn-secondary', 'Next');
        next.type = 'button';
        next.disabled = currentProviderPage + 1 >= totalPages;
        next.addEventListener('click', () => void fetchPendingProviders(currentProviderPage + 1));
        controls.append(previous, next);
        pagination.append(summary, controls);
        container.appendChild(pagination);
    } catch (error) {
        renderState(container, '❌', error.message || 'Failed to load onboarding queue.');
    }
}

async function approveProvider(providerId) {
    await runMutation(`approve-provider:${providerId}`, async () => {
        try {
            const response = await fetch(apiUrl(`/api/v1/providers/${encodeURIComponent(providerId)}/approve`), {
                method: 'POST',
            });
            if (!response.ok) throw new Error(await readApiError(response, 'Provider approval failed'));
            showToast('Provider approved');
            await fetchPendingProviders(currentProviderPage);
        } catch (error) {
            showToast(error.message || 'Provider approval failed', true);
        }
    });
}

async function rejectProvider(providerId) {
    const entered = window.prompt('Enter the reason for rejecting this provider:');
    if (entered == null) return;
    const reason = entered.trim();
    if (reason.length < 3) {
        showToast('A rejection reason of at least 3 characters is required.', true);
        return;
    }

    await runMutation(`reject-provider:${providerId}`, async () => {
        try {
            const response = await fetch(apiUrl(`/api/v1/providers/${encodeURIComponent(providerId)}/reject`), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ reason }),
            });
            if (!response.ok) throw new Error(await readApiError(response, 'Provider rejection failed'));
            showToast('Provider rejected');
            await fetchPendingProviders(currentProviderPage);
        } catch (error) {
            showToast(error.message || 'Provider rejection failed', true);
        }
    });
}

async function fetchDisputes() {
    const container = document.getElementById('disputes-list');
    renderState(container, '⏳', 'Loading disputes queue...');

    try {
        const response = await fetch(apiUrl('/api/v1/orders/disputes'));
        if (!response.ok) throw new Error(await readApiError(response, 'Failed to fetch disputes'));
        const disputes = await response.json();
        if (!Array.isArray(disputes) || disputes.length === 0) {
            renderState(container, '🛡️', 'No disputes submitted.');
            return;
        }

        container.replaceChildren();
        disputes.forEach((dispute) => {
            const item = document.createElement('article');
            item.className = 'list-item';
            const resolved = dispute.status !== 'OPEN';

            const header = document.createElement('div');
            header.className = 'item-header';
            const heading = document.createElement('div');
            heading.append(
                createTextElement('h3', 'item-title', 'Dispute on Order'),
                createTextElement('p', 'item-subtitle', `Order ID: ${String(dispute.orderId || '')}`),
            );
            const badgeClass = dispute.status === 'OPEN' ? 'badge-pending' : dispute.status === 'RESOLVED' ? 'badge-success' : 'badge-danger';
            header.append(heading, createTextElement('span', `badge ${badgeClass}`, String(dispute.status || 'UNKNOWN')));
            item.append(header, createTextElement('div', 'item-details', `Reason: ${String(dispute.reason || '')}`));

            if (resolved) {
                item.appendChild(createTextElement('div', 'resolution-box', `Resolution: ${String(dispute.resolutionNotes || 'No notes provided')}`));
            } else {
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const resolve = createTextElement('button', 'btn', '⚖️ Resolve Ticket');
                resolve.type = 'button';
                resolve.addEventListener('click', () => openDisputeModal(String(dispute.disputeId), String(dispute.reason || '')));
                const invoice = createTextElement('button', 'btn btn-secondary', '📄 View Invoice');
                invoice.type = 'button';
                invoice.addEventListener('click', () => void viewInvoice(String(dispute.orderId)));
                actions.append(resolve, invoice);
                item.appendChild(actions);
            }
            container.appendChild(item);
        });
    } catch (error) {
        renderState(container, '❌', error.message || 'Failed to load disputes.');
    }
}

function openDisputeModal(disputeId, reason) {
    currentDisputeId = disputeId;
    const reasonNode = document.getElementById('modal-dispute-reason');
    const notes = document.getElementById('resolution-notes');
    if (reasonNode) reasonNode.textContent = `Reason for dispute: “${reason}”`;
    if (notes) notes.value = '';
    document.getElementById('dispute-modal')?.classList.add('active');
}

function closeDisputeModal() {
    document.getElementById('dispute-modal')?.classList.remove('active');
    currentDisputeId = null;
}

async function submitResolution(decision) {
    if (!currentDisputeId) return;
    const notes = document.getElementById('resolution-notes')?.value.trim() || '';
    if (!notes) {
        showToast('Resolution notes are required.', true);
        return;
    }

    const disputeId = currentDisputeId;
    await runMutation(`resolve-dispute:${disputeId}`, async () => {
        try {
            const response = await fetch(apiUrl(`/api/v1/orders/disputes/${encodeURIComponent(disputeId)}/resolve`), {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ decision, resolutionNotes: notes }),
            });
            if (!response.ok) throw new Error(await readApiError(response, 'Failed to resolve dispute'));
            showToast(`Dispute marked as ${decision}`);
            closeDisputeModal();
            await fetchDisputes();
            await fetchOperationalSnapshot();
        } catch (error) {
            showToast(error.message || 'Failed to resolve dispute', true);
        }
    });
}

async function viewInvoice(orderId) {
    try {
        const response = await fetch(apiUrl(`/api/v1/orders/${encodeURIComponent(orderId)}/invoice`));
        if (!response.ok) throw new Error(await readApiError(response, 'Invoice is not available'));
        AlertInvoiceInfo(await response.json());
    } catch (error) {
        showToast(error.message || 'Failed to retrieve invoice', true);
    }
}

function AlertInvoiceInfo(invoice) {
    const subtotal = Number(invoice.subtotalAmount || 0).toFixed(2);
    const tax = Number(invoice.taxAmount || 0).toFixed(2);
    const total = Number(invoice.totalAmount || 0).toFixed(2);
    window.alert(`Invoice ${invoice.invoiceNumber || '—'}\nSubtotal: ₹${subtotal}\nTax: ₹${tax}\nTotal: ₹${total}\nGenerated: ${new Date(invoice.generatedAt).toLocaleString()}`);
}

async function fetchBannerAuctionOutcomes() {
    const container = document.getElementById('banner-auction-list');
    renderState(container, '⏳', 'Loading auction outcomes...');

    try {
        const response = await fetch(apiUrl('/api/v1/content/banners/auction-outcomes'));
        if (!response.ok) throw new Error(await readApiError(response, 'Failed to fetch auction outcomes'));
        const outcomes = await response.json();
        if (!Array.isArray(outcomes) || outcomes.length === 0) {
            renderState(container, '📭', 'No active banner slots with auction outcomes.');
            return;
        }

        container.replaceChildren();
        outcomes.forEach((slot) => {
            const item = document.createElement('article');
            item.className = 'list-item';
            const header = document.createElement('div');
            header.className = 'item-header';
            const heading = document.createElement('div');
            heading.append(
                createTextElement('h3', 'item-title', `Slot ${slot.slotOrder ?? '—'}: ${slot.title || 'Banner'}`),
                createTextElement('p', 'item-subtitle', `Duration: ${slot.durationSec ?? '—'}s`),
            );
            header.append(
                heading,
                createTextElement('span', `badge ${slot.active ? 'badge-success' : 'badge-pending'}`, String(slot.status || 'UNKNOWN')),
            );
            const details = document.createElement('div');
            details.className = 'item-details';
            details.append(
                createTextElement('div', '', `Winning provider: ${slot.providerId || 'Unassigned'}`),
                createTextElement('div', '', `Winning bid: ${slot.bidAmount == null ? '—' : `₹${Number(slot.bidAmount).toFixed(2)}`}`),
                createTextElement('div', '', `Active: ${slot.active ? 'Yes' : 'No'}`),
            );
            item.append(header, details);
            container.appendChild(item);
        });
    } catch (error) {
        renderState(container, '❌', error.message || 'Failed to load auction outcomes.');
    }
}

async function fetchUsers(page = currentUserPage) {
    const container = document.getElementById('users-list');
    renderState(container, '⏳', 'Loading users...');

    try {
        const safePage = Math.max(0, Number(page) || 0);
        const response = await fetch(apiUrl(`/api/v1/profiles/admin?page=${safePage}&size=${USER_PAGE_SIZE}`));
        if (!response.ok) throw new Error(await readApiError(response, 'Failed to fetch users'));
        const payload = await response.json();
        const users = Array.isArray(payload) ? payload : payload.content;
        if (!Array.isArray(users) || users.length === 0) {
            currentUserPage = safePage;
            renderState(container, '👥', safePage === 0 ? 'No user profiles registered.' : 'No users on this page.');
            return;
        }

        currentUserPage = Number(payload.page ?? safePage);
        const totalPages = Number(payload.totalPages ?? 1);
        const totalElements = Number(payload.totalElements ?? users.length);
        container.replaceChildren();

        users.forEach((user) => {
            const item = document.createElement('article');
            item.className = 'list-item';
            const role = String(user.role || 'USER').toUpperCase();
            const header = document.createElement('div');
            header.className = 'item-header';
            const heading = document.createElement('div');
            heading.append(
                createTextElement('h3', 'item-title', String(user.fullName || 'User')),
                createTextElement('p', 'item-subtitle', `${role} — ${user.phoneNumber || 'N/A'}`),
            );
            header.append(
                heading,
                createTextElement('span', `badge ${user.suspended ? 'badge-danger' : 'badge-success'}`, user.suspended ? 'REVOKED' : 'ACTIVE'),
            );
            item.append(header, createTextElement('div', 'item-details', `User ID: ${String(user.userId || '')}`));

            const actions = document.createElement('div');
            actions.className = 'btn-group';
            if (role === 'ADMIN') {
                actions.appendChild(createTextElement('span', 'protected-note', 'Protected Admin identity — generic suspension is disabled'));
            } else {
                const action = createTextElement('button', user.suspended ? 'btn btn-emerald' : 'btn btn-rose', user.suspended ? '🔓 Restore Access' : '🚫 Revoke Access');
                action.type = 'button';
                action.addEventListener('click', () => {
                    if (user.suspended) void restoreUserAccess(String(user.userId));
                    else void revokeUserAccess(String(user.userId));
                });
                actions.appendChild(action);
            }
            item.appendChild(actions);
            container.appendChild(item);
        });

        const pagination = document.createElement('div');
        pagination.className = 'pagination';
        const summary = createTextElement('span', 'pagination-summary', `Page ${currentUserPage + 1} of ${Math.max(totalPages, 1)} · ${totalElements} users`);
        const controls = document.createElement('div');
        controls.className = 'btn-group';
        const previous = createTextElement('button', 'btn btn-secondary', 'Previous');
        previous.type = 'button';
        previous.disabled = currentUserPage <= 0;
        previous.addEventListener('click', () => void fetchUsers(currentUserPage - 1));
        const next = createTextElement('button', 'btn btn-secondary', 'Next');
        next.type = 'button';
        next.disabled = currentUserPage + 1 >= totalPages;
        next.addEventListener('click', () => void fetchUsers(currentUserPage + 1));
        controls.append(previous, next);
        pagination.append(summary, controls);
        container.appendChild(pagination);
    } catch (error) {
        renderState(container, '❌', error.message || 'Failed to load users.');
    }
}

async function revokeUserAccess(userId) {
    if (!window.confirm('Revoke this user’s access? Active sessions will be blocked by the gateway.')) return;
    await runMutation(`revoke-user:${userId}`, async () => {
        try {
            const response = await fetch(apiUrl(`/api/v1/profiles/${encodeURIComponent(userId)}/revoke`), { method: 'POST' });
            if (!response.ok) throw new Error(await readApiError(response, 'Failed to revoke access'));
            showToast('Access revoked');
            await fetchUsers(currentUserPage);
        } catch (error) {
            showToast(error.message || 'Failed to revoke access', true);
        }
    });
}

async function restoreUserAccess(userId) {
    if (!window.confirm('Restore this user’s access?')) return;
    await runMutation(`restore-user:${userId}`, async () => {
        try {
            const response = await fetch(apiUrl(`/api/v1/profiles/${encodeURIComponent(userId)}/restore`), { method: 'POST' });
            if (!response.ok) throw new Error(await readApiError(response, 'Failed to restore access'));
            showToast('Access restored');
            await fetchUsers(currentUserPage);
        } catch (error) {
            showToast(error.message || 'Failed to restore access', true);
        }
    });
}

async function fetchOperationalSnapshot() {
    const activeOrders = document.getElementById('metric-active-orders');
    const alerts = document.getElementById('metric-operational-alerts');
    const details = document.getElementById('metric-operational-alert-details');
    if (activeOrders) activeOrders.textContent = '…';
    if (alerts) alerts.textContent = '…';
    if (details) details.textContent = 'Loading authoritative order-service snapshot';

    try {
        const response = await fetch(apiUrl('/api/v1/orders/admin/operations/snapshot'));
        if (!response.ok) throw new Error(await readApiError(response, 'Operational snapshot unavailable'));
        const snapshot = await response.json();
        const alertCount = Number(snapshot.delayedOrders || 0)
            + Number(snapshot.failedPayments || 0)
            + Number(snapshot.openDisputes || 0)
            + Number(snapshot.openSupportCases || 0);
        if (activeOrders) activeOrders.textContent = String(snapshot.activeOrders ?? 0);
        if (alerts) alerts.textContent = String(alertCount);
        if (details) {
            details.textContent = `Delayed ${snapshot.delayedOrders ?? 0} · Failed payments ${snapshot.failedPayments ?? 0} · Open disputes ${snapshot.openDisputes ?? 0} · Support ${snapshot.openSupportCases ?? 0}`;
        }
    } catch (error) {
        if (activeOrders) activeOrders.textContent = 'UNAVAILABLE';
        if (alerts) alerts.textContent = 'UNAVAILABLE';
        if (details) details.textContent = error.message || 'Operational snapshot unavailable';
    }
}

document.addEventListener('DOMContentLoaded', () => {
    if (window.checkAdminAuth?.()) {
        void Promise.allSettled([
            fetchRefundModeConfig(),
            fetchPendingProviders(currentProviderPage),
            fetchOperationalSnapshot(),
        ]);
    }
});