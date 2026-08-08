(() => {
    'use strict';

    const pageState = { merchants: 0, orders: 0, subscriptions: 0, catalog: 0 };
    const pageSize = 25;
    const activeMutations = new Set();

    function text(tag, className, value) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        node.textContent = value == null ? '' : String(value);
        return node;
    }

    function state(container, icon, message) {
        if (!container) return;
        const box = document.createElement('div');
        box.className = 'empty-state';
        box.append(text('span', 'empty-icon', icon), text('p', '', message));
        container.replaceChildren(box);
    }

    async function errorFrom(response, fallback) {
        try {
            const body = await response.json();
            return body.error || body.message || fallback;
        } catch {
            return `${fallback} (${response.status})`;
        }
    }

    async function mutate(key, fn) {
        if (activeMutations.has(key)) return;
        activeMutations.add(key);
        try { await fn(); } finally { activeMutations.delete(key); }
    }

    function promptReason(label) {
        const entered = window.prompt(`${label}\nEnter an operational reason:`);
        if (entered == null) return null;
        const reason = entered.trim();
        if (reason.length < 3 || reason.length > 500) {
            window.showToast?.('Reason must contain between 3 and 500 characters.', true);
            return null;
        }
        return reason;
    }

    function pagination(container, key, page, totalPages, loader) {
        const row = document.createElement('div');
        row.className = 'pagination';
        row.appendChild(text('span', 'pagination-summary', `Page ${page + 1} of ${Math.max(1, totalPages)}`));
        const controls = document.createElement('div');
        controls.className = 'btn-group';
        const previous = text('button', 'btn btn-secondary', 'Previous');
        previous.type = 'button';
        previous.disabled = page <= 0;
        previous.addEventListener('click', () => { pageState[key] = page - 1; void loader(); });
        const next = text('button', 'btn btn-secondary', 'Next');
        next.type = 'button';
        next.disabled = page + 1 >= totalPages;
        next.addEventListener('click', () => { pageState[key] = page + 1; void loader(); });
        controls.append(previous, next);
        row.appendChild(controls);
        container.appendChild(row);
    }

    window.fetchAdminMerchants = async function fetchAdminMerchants() {
        const container = document.getElementById('admin-merchants-list');
        state(container, '⏳', 'Loading merchants...');
        try {
            const response = await fetch(apiUrl(`/api/v1/providers/admin?page=${pageState.merchants}&size=${pageSize}`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to load merchants'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                state(container, '🏪', 'No merchants on this page.');
                return;
            }
            page.content.forEach((provider) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const header = document.createElement('div');
                header.className = 'item-header';
                const heading = document.createElement('div');
                heading.append(
                    text('h3', 'item-title', provider.name || 'Unnamed merchant'),
                    text('p', 'item-subtitle', `${provider.providerType || 'UNKNOWN'} · ${provider.city || '—'} · ${provider.providerId}`),
                );
                const status = String(provider.status || 'UNKNOWN');
                header.append(heading, text('span', `badge ${status === 'ACTIVE' ? 'badge-success' : status === 'SUSPENDED' ? 'badge-danger' : 'badge-pending'}`, status));
                item.append(header, text('div', 'item-details', `Owner ${provider.ownerUserId || '—'} · Pincode ${provider.pincode || '—'} · Commission ${provider.commissionPct ?? '—'}%`));

                const actions = document.createElement('div');
                actions.className = 'btn-group';
                if (status === 'ACTIVE') {
                    const suspend = text('button', 'btn btn-rose', 'Suspend merchant');
                    suspend.type = 'button';
                    suspend.addEventListener('click', () => void window.suspendAdminMerchant(provider.providerId));
                    actions.appendChild(suspend);
                } else if (status === 'SUSPENDED') {
                    const restore = text('button', 'btn btn-emerald', 'Reactivate merchant');
                    restore.type = 'button';
                    restore.addEventListener('click', () => void window.reactivateAdminMerchant(provider.providerId));
                    actions.appendChild(restore);
                }
                if (actions.childNodes.length) item.appendChild(actions);
                container.appendChild(item);
            });
            pagination(container, 'merchants', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminMerchants);
        } catch (error) {
            state(container, '❌', error.message || 'Failed to load merchants.');
        }
    };

    window.suspendAdminMerchant = async function suspendAdminMerchant(providerId) {
        const reason = promptReason('Suspend this merchant? New orders and appointments will be blocked server-side.');
        if (!reason || !window.confirm('Confirm merchant suspension?')) return;
        await mutate(`merchant-suspend:${providerId}`, async () => {
            try {
                const response = await fetch(apiUrl(`/api/v1/providers/${encodeURIComponent(providerId)}/suspend`), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ reason }),
                });
                if (!response.ok) throw new Error(await errorFrom(response, 'Merchant suspension failed'));
                window.showToast?.('Merchant suspended');
                await window.fetchAdminMerchants();
            } catch (error) {
                window.showToast?.(error.message || 'Merchant suspension failed', true);
            }
        });
    };

    window.reactivateAdminMerchant = async function reactivateAdminMerchant(providerId) {
        const reason = promptReason('Reactivate this merchant?');
        if (!reason || !window.confirm('Confirm merchant reactivation?')) return;
        await mutate(`merchant-reactivate:${providerId}`, async () => {
            try {
                const response = await fetch(apiUrl(`/api/v1/providers/${encodeURIComponent(providerId)}/reactivate`), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ reason }),
                });
                if (!response.ok) throw new Error(await errorFrom(response, 'Merchant reactivation failed'));
                window.showToast?.('Merchant reactivated');
                await window.fetchAdminMerchants();
            } catch (error) {
                window.showToast?.(error.message || 'Merchant reactivation failed', true);
            }
        });
    };

    window.fetchAdminOrders = async function fetchAdminOrders() {
        const container = document.getElementById('admin-orders-list');
        state(container, '⏳', 'Loading orders...');
        try {
            const response = await fetch(apiUrl(`/api/v1/orders/admin/orders?page=${pageState.orders}&size=${pageSize}`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to load orders'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                state(container, '📦', 'No orders on this page.');
                return;
            }
            page.content.forEach((order) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const header = document.createElement('div');
                header.className = 'item-header';
                const heading = document.createElement('div');
                heading.append(text('h3', 'item-title', `Order ${order.orderId}`), text('p', 'item-subtitle', `${order.customerId} → ${order.providerId}`));
                header.append(heading, text('span', 'badge badge-pending', order.status || 'UNKNOWN'));
                item.append(header, text('div', 'item-details', `₹${Number(order.totalAmount || 0).toFixed(2)} · Payment ${order.paymentStatus || 'UNKNOWN'} · ${order.paymentMethod || '—'}`));
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const details = text('button', 'btn btn-secondary', 'View timeline');
                details.type = 'button';
                details.addEventListener('click', () => void window.loadAdminOrderDetail(order.orderId, item));
                actions.appendChild(details);
                item.appendChild(actions);
                container.appendChild(item);
            });
            pagination(container, 'orders', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminOrders);
        } catch (error) {
            state(container, '❌', error.message || 'Failed to load orders.');
        }
    };

    window.loadAdminOrderDetail = async function loadAdminOrderDetail(orderId, host) {
        try {
            const response = await fetch(apiUrl(`/api/v1/orders/admin/orders/${encodeURIComponent(orderId)}`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to load order detail'));
            const detail = await response.json();
            host.querySelector('.admin-order-detail')?.remove();
            const box = document.createElement('div');
            box.className = 'resolution-box admin-order-detail';
            const items = Array.isArray(detail.items) ? detail.items.map((item) => `${item.quantity}× ${item.name} @ ₹${Number(item.unitPrice || 0).toFixed(2)}`).join(' · ') : 'No item snapshots';
            const timeline = Array.isArray(detail.timeline) ? detail.timeline.map((entry) => `${entry.status} (${new Date(entry.changedAt).toLocaleString()})`).join(' → ') : 'No status history';
            box.append(text('div', '', `Items: ${items}`), text('div', '', `Timeline: ${timeline}`));
            host.appendChild(box);
        } catch (error) {
            window.showToast?.(error.message || 'Failed to load order detail', true);
        }
    };

    window.fetchAdminSubscriptions = async function fetchAdminSubscriptions() {
        const container = document.getElementById('admin-subscriptions-list');
        state(container, '⏳', 'Loading recurring subscriptions...');
        try {
            const response = await fetch(apiUrl(`/api/v1/orders/admin/subscriptions?page=${pageState.subscriptions}&size=${pageSize}`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to load subscriptions'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                state(container, '🔁', 'No recurring subscriptions on this page.');
                return;
            }
            page.content.forEach((subscription) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const header = document.createElement('div');
                header.className = 'item-header';
                const heading = document.createElement('div');
                heading.append(text('h3', 'item-title', `Subscription ${subscription.subscriptionId}`), text('p', 'item-subtitle', `${subscription.cadenceDays}-day cadence · ${subscription.customerId}`));
                header.append(heading, text('span', `badge ${subscription.status === 'ACTIVE' ? 'badge-success' : 'badge-pending'}`, subscription.status || 'UNKNOWN'));
                item.append(header, text('div', 'item-details', `Provider ${subscription.providerId} · Next ${new Date(subscription.nextOrderAt).toLocaleString()} · Last failure ${subscription.lastFailureCode || 'None'}`));
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const trace = text('button', 'btn btn-secondary', 'Trace occurrences');
                trace.type = 'button';
                trace.addEventListener('click', () => void window.loadAdminSubscriptionTrace(subscription.subscriptionId, item));
                actions.appendChild(trace);
                item.appendChild(actions);
                container.appendChild(item);
            });
            pagination(container, 'subscriptions', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminSubscriptions);
        } catch (error) {
            state(container, '❌', error.message || 'Failed to load subscriptions.');
        }
    };

    window.loadAdminSubscriptionTrace = async function loadAdminSubscriptionTrace(subscriptionId, host) {
        try {
            const response = await fetch(apiUrl(`/api/v1/orders/admin/subscriptions/${encodeURIComponent(subscriptionId)}?page=0&size=25`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to trace subscription'));
            const trace = await response.json();
            host.querySelector('.admin-subscription-trace')?.remove();
            const box = document.createElement('div');
            box.className = 'resolution-box admin-subscription-trace';
            if (!Array.isArray(trace.occurrences) || trace.occurrences.length === 0) {
                box.appendChild(text('div', '', 'No occurrences recorded yet.'));
            } else {
                trace.occurrences.forEach((occurrence) => {
                    box.appendChild(text('div', '', `${new Date(occurrence.scheduledFor).toLocaleString()} · ${occurrence.status} · Order ${occurrence.orderId || '—'} · ${occurrence.failureCode || 'OK'} ${occurrence.failureDetail || ''}`));
                });
            }
            host.appendChild(box);
        } catch (error) {
            window.showToast?.(error.message || 'Failed to trace subscription', true);
        }
    };

    window.fetchAdminCatalog = async function fetchAdminCatalog() {
        const container = document.getElementById('admin-catalog-list');
        state(container, '⏳', 'Loading catalog...');
        try {
            const response = await fetch(apiUrl(`/api/v1/catalog/admin/offerings?page=${pageState.catalog}&size=${pageSize}`));
            if (!response.ok) throw new Error(await errorFrom(response, 'Failed to load catalog'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                state(container, '🛍️', 'No offerings on this page.');
                return;
            }
            page.content.forEach((offering) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const header = document.createElement('div');
                header.className = 'item-header';
                const heading = document.createElement('div');
                heading.append(text('h3', 'item-title', offering.name || 'Unnamed offering'), text('p', 'item-subtitle', `${offering.offeringId} · Provider ${offering.providerId}`));
                header.append(heading, text('span', `badge ${offering.adminDisabled ? 'badge-danger' : offering.status === 'ACTIVE' ? 'badge-success' : 'badge-pending'}`, offering.adminDisabled ? 'ADMIN_DISABLED' : offering.status || 'UNKNOWN'));
                item.append(header, text('div', 'item-details', `₹${Number(offering.price || 0).toFixed(2)} · Stock ${offering.stockQuantity ?? 'untracked'}${offering.moderationReason ? ` · Reason: ${offering.moderationReason}` : ''}`));
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const action = text('button', offering.adminDisabled ? 'btn btn-emerald' : 'btn btn-rose', offering.adminDisabled ? 'Restore listing' : 'Disable listing');
                action.type = 'button';
                action.addEventListener('click', () => void window.moderateAdminOffering(offering.offeringId, offering.adminDisabled));
                actions.appendChild(action);
                item.appendChild(actions);
                container.appendChild(item);
            });
            pagination(container, 'catalog', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminCatalog);
        } catch (error) {
            state(container, '❌', error.message || 'Failed to load catalog.');
        }
    };

    window.moderateAdminOffering = async function moderateAdminOffering(offeringId, currentlyDisabled) {
        const reason = promptReason(currentlyDisabled ? 'Restore this listing?' : 'Disable this listing? Customers will be blocked from purchasing it.');
        if (!reason || !window.confirm(currentlyDisabled ? 'Confirm listing restoration?' : 'Confirm listing disable?')) return;
        const action = currentlyDisabled ? 'restore' : 'disable';
        await mutate(`catalog-${action}:${offeringId}`, async () => {
            try {
                const response = await fetch(apiUrl(`/api/v1/catalog/admin/offerings/${encodeURIComponent(offeringId)}/${action}`), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ reason }),
                });
                if (!response.ok) throw new Error(await errorFrom(response, `Catalog ${action} failed`));
                window.showToast?.(currentlyDisabled ? 'Listing restored' : 'Listing disabled');
                await window.fetchAdminCatalog();
            } catch (error) {
                window.showToast?.(error.message || `Catalog ${action} failed`, true);
            }
        });
    };
})();
