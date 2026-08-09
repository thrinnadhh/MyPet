(() => {
    'use strict';

    const pageSize = 25;
    const pages = { payments: 0, loyalty: 0, appointments: 0, notifications: 0 };
    const mutations = new Set();

    function text(tag, className, value) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        node.textContent = value == null ? '' : String(value);
        return node;
    }

    function empty(container, icon, message) {
        if (!container) return;
        const box = document.createElement('div');
        box.className = 'empty-state';
        box.append(text('span', 'empty-icon', icon), text('p', '', message));
        container.replaceChildren(box);
    }

    async function apiError(response, fallback) {
        try {
            const body = await response.json();
            return body.error || body.message || fallback;
        } catch {
            return `${fallback} (${response.status})`;
        }
    }

    function addPagination(container, key, page, totalPages, loader) {
        const row = document.createElement('div');
        row.className = 'pagination';
        row.appendChild(text('span', 'pagination-summary', `Page ${page + 1} of ${Math.max(1, totalPages)}`));
        const controls = document.createElement('div');
        controls.className = 'btn-group';
        const previous = text('button', 'btn btn-secondary', 'Previous');
        previous.type = 'button';
        previous.disabled = page <= 0;
        previous.addEventListener('click', () => { pages[key] = Math.max(0, page - 1); void loader(); });
        const next = text('button', 'btn btn-secondary', 'Next');
        next.type = 'button';
        next.disabled = page + 1 >= totalPages;
        next.addEventListener('click', () => { pages[key] = page + 1; void loader(); });
        controls.append(previous, next);
        row.appendChild(controls);
        container.appendChild(row);
    }

    async function mutate(key, operation) {
        if (mutations.has(key)) return;
        mutations.add(key);
        try { await operation(); } finally { mutations.delete(key); }
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

    window.fetchAdminPayments = async function fetchAdminPayments() {
        const container = document.getElementById('admin-payments-list');
        empty(container, '⏳', 'Loading authoritative payment records...');
        try {
            const response = await fetch(apiUrl(`/api/v1/payments/admin/transactions?page=${pages.payments}&size=${pageSize}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load payments'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                empty(container, '💳', 'No payment transactions on this page.');
                return;
            }
            page.content.forEach((payment) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const header = document.createElement('div');
                header.className = 'item-header';
                const heading = document.createElement('div');
                heading.append(
                    text('h3', 'item-title', `${payment.transactionType || 'PAYMENT'} · ₹${Number(payment.amount || 0).toFixed(2)}`),
                    text('p', 'item-subtitle', `Reference ${payment.referenceId || '—'} · User ${payment.userId || '—'}`),
                );
                const status = String(payment.status || 'UNKNOWN');
                header.append(heading, text('span', `badge ${status === 'SUCCESS' || status === 'REFUNDED' ? 'badge-success' : status.includes('FAIL') ? 'badge-danger' : 'badge-pending'}`, status));
                item.append(
                    header,
                    text('div', 'item-details', `Gateway ${payment.gateway || '—'} · Provider ID ${payment.gatewayTransactionId || '—'} · Created ${payment.createdAt ? new Date(payment.createdAt).toLocaleString() : '—'}`),
                );
                container.appendChild(item);
            });
            addPagination(container, 'payments', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminPayments);
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load payments.');
        }
    };

    window.fetchAdminLoyalty = async function fetchAdminLoyalty() {
        const container = document.getElementById('admin-loyalty-list');
        empty(container, '⏳', 'Loading loyalty accounts...');
        try {
            const response = await fetch(apiUrl(`/api/v1/payments/loyalty/admin/accounts?page=${pages.loyalty}&size=${pageSize}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load loyalty accounts'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                empty(container, '⭐', 'No loyalty accounts on this page.');
                return;
            }
            page.content.forEach((account) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                item.append(
                    text('h3', 'item-title', `${account.starBalance ?? 0} stars · Customer ${account.customerId}`),
                    text('p', 'item-subtitle', `Provider ${account.providerId}`),
                    text('div', 'item-details', `Total earned ${account.totalStarsEarned ?? 0} · Rewards issued ${account.totalRewardsIssued ?? 0} · Welcome star ${account.welcomeStarClaimed ? 'claimed' : 'available'}`),
                );
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const ledger = text('button', 'btn btn-secondary', 'View ledger');
                ledger.type = 'button';
                ledger.addEventListener('click', () => void window.loadAdminLoyaltyLedger(account.customerId, item));
                actions.appendChild(ledger);
                item.appendChild(actions);
                container.appendChild(item);
            });
            addPagination(container, 'loyalty', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminLoyalty);
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load loyalty accounts.');
        }
    };

    window.loadAdminLoyaltyLedger = async function loadAdminLoyaltyLedger(customerId, host) {
        try {
            const response = await fetch(apiUrl(`/api/v1/payments/loyalty/admin/ledger?customerId=${encodeURIComponent(customerId)}&page=0&size=25`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load loyalty ledger'));
            const page = await response.json();
            host.querySelector('.admin-loyalty-ledger')?.remove();
            const box = document.createElement('div');
            box.className = 'resolution-box admin-loyalty-ledger';
            if (!Array.isArray(page.content) || page.content.length === 0) {
                box.appendChild(text('div', '', 'No ledger entries.'));
            } else {
                page.content.forEach((entry) => box.appendChild(text('div', '', `${entry.entryType || 'ENTRY'} · ${Number(entry.deltaStars || 0) >= 0 ? '+' : ''}${entry.deltaStars || 0} star(s) · Provider ${entry.providerId || '—'} · Ref ${entry.referenceId || '—'} · ${entry.createdAt ? new Date(entry.createdAt).toLocaleString() : '—'}`)));
            }
            host.appendChild(box);
        } catch (error) {
            window.showToast?.(error.message || 'Failed to load loyalty ledger', true);
        }
    };

    window.fetchAdminAppointments = async function fetchAdminAppointments() {
        const container = document.getElementById('admin-appointments-list');
        empty(container, '⏳', 'Loading grooming and veterinary appointments...');
        try {
            const response = await fetch(apiUrl(`/api/v1/appointments/admin?page=${pages.appointments}&size=${pageSize}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load appointments'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                empty(container, '🩺', 'No appointments on this page.');
                return;
            }
            page.content.forEach((appointment) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const id = appointment.appointmentId;
                const status = String(appointment.status || 'UNKNOWN');
                item.append(
                    text('h3', 'item-title', `Appointment ${id}`),
                    text('p', 'item-subtitle', `Customer ${appointment.customerId || '—'} · Provider ${appointment.providerId || '—'}`),
                    text('div', 'item-details', `Status ${status} · Pet ${appointment.petId || '—'} · Slot ${appointment.startTime ? new Date(appointment.startTime).toLocaleString() : appointment.appointmentTime ? new Date(appointment.appointmentTime).toLocaleString() : '—'}`),
                );
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const detail = text('button', 'btn btn-secondary', 'View timeline');
                detail.type = 'button';
                detail.addEventListener('click', () => void window.loadAdminAppointmentDetail(id, item));
                actions.appendChild(detail);
                item.appendChild(actions);
                container.appendChild(item);
            });
            addPagination(container, 'appointments', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminAppointments);
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load appointments.');
        }
    };

    window.loadAdminAppointmentDetail = async function loadAdminAppointmentDetail(appointmentId, host) {
        try {
            const response = await fetch(apiUrl(`/api/v1/appointments/admin/${encodeURIComponent(appointmentId)}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load appointment detail'));
            const detail = await response.json();
            host.querySelector('.admin-appointment-detail')?.remove();
            const box = document.createElement('div');
            box.className = 'resolution-box admin-appointment-detail';
            const timeline = Array.isArray(detail.timeline) ? detail.timeline.map((entry) => `${entry.status || entry.newStatus || 'STATE'} (${entry.changedAt ? new Date(entry.changedAt).toLocaleString() : '—'})`).join(' → ') : 'No timeline';
            box.append(text('div', '', `Payment ${detail.appointment?.paymentStatus || '—'} · Service ${detail.appointment?.serviceId || detail.appointment?.offeringId || '—'}`), text('div', '', `Timeline: ${timeline}`));
            host.appendChild(box);
        } catch (error) {
            window.showToast?.(error.message || 'Failed to load appointment detail', true);
        }
    };

    window.fetchAdminNotifications = async function fetchAdminNotifications() {
        const container = document.getElementById('admin-notifications-list');
        empty(container, '⏳', 'Loading notification deliveries...');
        try {
            const response = await fetch(apiUrl(`/api/v1/notifications/admin/email-deliveries?page=${pages.notifications}&size=${pageSize}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load notification deliveries'));
            const page = await response.json();
            container.replaceChildren();
            if (!Array.isArray(page.content) || page.content.length === 0) {
                empty(container, '✉️', 'No notification deliveries on this page.');
                return;
            }
            page.content.forEach((delivery) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                const status = String(delivery.status || 'UNKNOWN');
                item.append(
                    text('h3', 'item-title', `${delivery.templateCode || 'EMAIL'} · ${delivery.recipientEmailMasked || '***'}`),
                    text('p', 'item-subtitle', `Delivery ${delivery.emailDeliveryId}`),
                    text('div', 'item-details', `Status ${status} · Attempts ${delivery.attemptCount ?? 0} · Provider ${delivery.provider || '—'}${delivery.lastError ? ` · Last error: ${delivery.lastError}` : ''}`),
                );
                if (status === 'FAILED') {
                    const actions = document.createElement('div');
                    actions.className = 'btn-group';
                    const retry = text('button', 'btn btn-emerald', 'Retry failed email');
                    retry.type = 'button';
                    retry.addEventListener('click', () => void window.retryAdminNotification(delivery.emailDeliveryId));
                    actions.appendChild(retry);
                    item.appendChild(actions);
                }
                container.appendChild(item);
            });
            addPagination(container, 'notifications', Number(page.page || 0), Number(page.totalPages || 1), window.fetchAdminNotifications);
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load notification deliveries.');
        }
    };

    window.retryAdminNotification = async function retryAdminNotification(deliveryId) {
        const reason = promptReason('Retry this definitively failed email delivery?');
        if (!reason || !window.confirm('Confirm one manual email retry?')) return;
        await mutate(`notification-retry:${deliveryId}`, async () => {
            try {
                const response = await fetch(apiUrl(`/api/v1/notifications/admin/email-deliveries/${encodeURIComponent(deliveryId)}/retry`), {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ reason }),
                });
                if (!response.ok) throw new Error(await apiError(response, 'Notification retry failed'));
                window.showToast?.('Failed email scheduled for one audited retry');
                await window.fetchAdminNotifications();
            } catch (error) {
                window.showToast?.(error.message || 'Notification retry failed', true);
            }
        });
    };

    window.fetchAdminAnalytics = async function fetchAdminAnalytics() {
        const container = document.getElementById('admin-analytics-list');
        empty(container, '⏳', 'Calculating authoritative analytics...');
        try {
            const to = new Date();
            const from = new Date(to.getTime() - (7 * 24 * 60 * 60 * 1000));
            const response = await fetch(apiUrl(`/api/v1/orders/admin/analytics?from=${encodeURIComponent(from.toISOString())}&to=${encodeURIComponent(to.toISOString())}`));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load analytics'));
            const data = await response.json();
            container.replaceChildren();
            const metrics = [
                ['Orders placed', data.ordersPlaced],
                ['Completed', data.completedOrders],
                ['Cancelled / rejected', data.cancelledOrRejectedOrders],
                ['Failed payments', data.failedPayments],
                ['Distinct customers', data.distinctCustomers],
                ['Distinct merchants', data.distinctProviders],
                ['GMV', `₹${Number(data.grossMerchandiseValue || 0).toFixed(2)}`],
                ['Average completed order', `₹${Number(data.averageCompletedOrderValue || 0).toFixed(2)}`],
                ['Completion rate', `${Number(data.completionRatePct || 0).toFixed(2)}%`],
                ['Cancellation rate', `${Number(data.cancellationRatePct || 0).toFixed(2)}%`],
            ];
            metrics.forEach(([label, value]) => {
                const card = document.createElement('div');
                card.className = 'metric-card';
                card.append(text('h3', '', label), text('span', 'metric-value', value));
                container.appendChild(card);
            });
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load analytics.');
        }
    };

    window.fetchAdminServiceAreas = async function fetchAdminServiceAreas() {
        const container = document.getElementById('admin-service-areas-list');
        empty(container, '⏳', 'Loading serviceability configuration...');
        try {
            const response = await fetch(apiUrl('/api/v1/orders/admin/operations/service-areas'));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load service areas'));
            const areas = await response.json();
            container.replaceChildren();
            if (!Array.isArray(areas) || areas.length === 0) {
                empty(container, '📍', 'No service areas configured.');
                return;
            }
            areas.forEach((area) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                item.append(
                    text('h3', 'item-title', `${area.city} · ${area.pincode}`),
                    text('p', 'item-subtitle', `${area.enabled ? 'City active' : 'City disabled'} · Delivery ${area.deliveryEnabled ? 'enabled' : 'disabled'} · Radius ${area.serviceRadiusKm} km`),
                    text('div', 'item-details', area.emergencyMessage || 'No emergency message'),
                );
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const toggle = text('button', area.enabled ? 'btn btn-rose' : 'btn btn-emerald', area.enabled ? 'Disable area' : 'Enable area');
                toggle.type = 'button';
                toggle.addEventListener('click', () => void window.toggleAdminServiceArea(area));
                actions.appendChild(toggle);
                item.appendChild(actions);
                container.appendChild(item);
            });
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load service areas.');
        }
    };

    window.toggleAdminServiceArea = async function toggleAdminServiceArea(area) {
        const nextEnabled = !area.enabled;
        const reason = promptReason(`${nextEnabled ? 'Enable' : 'Disable'} ${area.city} ${area.pincode}?`);
        if (!reason || !window.confirm(`Confirm service area ${nextEnabled ? 'activation' : 'deactivation'}?`)) return;
        await mutate(`service-area:${area.pincode}`, async () => {
            try {
                const response = await fetch(apiUrl(`/api/v1/orders/admin/operations/service-areas/${encodeURIComponent(area.pincode)}`), {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        city: area.city,
                        enabled: nextEnabled,
                        deliveryEnabled: nextEnabled ? area.deliveryEnabled : false,
                        serviceRadiusKm: area.serviceRadiusKm,
                        emergencyMessage: area.emergencyMessage || null,
                        reason,
                    }),
                });
                if (!response.ok) throw new Error(await apiError(response, 'Service area update failed'));
                window.showToast?.(`Service area ${nextEnabled ? 'enabled' : 'disabled'}`);
                await Promise.allSettled([window.fetchAdminServiceAreas(), window.fetchAdminAuditLogs()]);
            } catch (error) {
                window.showToast?.(error.message || 'Service area update failed', true);
            }
        });
    };

    window.fetchAdminAuditLogs = async function fetchAdminAuditLogs() {
        const container = document.getElementById('admin-audit-list');
        empty(container, '⏳', 'Loading administrative audit evidence...');
        try {
            const response = await fetch(apiUrl('/api/v1/orders/admin/operations/audit-logs?limit=100'));
            if (!response.ok) throw new Error(await apiError(response, 'Failed to load audit logs'));
            const logs = await response.json();
            container.replaceChildren();
            if (!Array.isArray(logs) || logs.length === 0) {
                empty(container, '🧾', 'No order-domain admin audit entries recorded yet.');
                return;
            }
            logs.forEach((entry) => {
                const item = document.createElement('article');
                item.className = 'list-item';
                item.append(
                    text('h3', 'item-title', `${entry.action} · ${entry.entityType}`),
                    text('p', 'item-subtitle', `Actor ${entry.adminUserId} · Target ${entry.entityId || '—'} · ${entry.createdAt ? new Date(entry.createdAt).toLocaleString() : '—'}`),
                    text('div', 'item-details', `Reason: ${entry.reason} · Request/trace ${entry.traceId}`),
                );
                container.appendChild(item);
            });
        } catch (error) {
            empty(container, '❌', error.message || 'Failed to load audit logs.');
        }
    };
})();
