const API_BASE_URL = 'http://localhost:8080';

let activeTab = 'approvals';
let currentDisputeId = null;

function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// On Load
document.addEventListener('DOMContentLoaded', () => {
    if (checkAdminAuth()) {
        fetchRefundModeConfig();
        fetchPendingProviders();
    }
});

// Tab switching logic
function switchTab(tabId) {
    activeTab = tabId;
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.tab-panel').forEach(panel => panel.style.display = 'none');
    
    // Find button to activate
    const btn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.textContent.toLowerCase().includes(tabId.substring(0, 3)));
    if (btn) btn.classList.add('active');
    
    document.getElementById(`${tabId}-panel`).style.display = 'block';
    
    if (tabId === 'approvals') {
        fetchPendingProviders();
    } else if (tabId === 'disputes') {
        fetchDisputes();
    } else if (tabId === 'banner-auction') {
        fetchBannerAuctionOutcomes();
    } else if (tabId === 'users') {
        fetchUsers();
    }
}

// Show Alert Toast
function showToast(message, isError = false) {
    const toast = document.getElementById('alert-toast');
    const icon = document.getElementById('toast-icon');
    const msg = document.getElementById('toast-message');
    
    icon.textContent = isError ? '❌' : '✅';
    toast.style.borderLeftColor = isError ? 'var(--accent-rose)' : 'var(--accent-emerald)';
    msg.textContent = message;
    
    toast.classList.add('active');
    setTimeout(() => {
        toast.classList.remove('active');
    }, 3000);
}

// ─── Config Methods ──────────────────────────────────────────────────────────

async function fetchRefundModeConfig() {
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/admin/config`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (res.ok) {
            const data = await res.json();
            const isAutomated = data.dispute_refund_mode === 'AUTOMATED';
            document.getElementById('refund-mode-checkbox').checked = isAutomated;
        }
    } catch (e) {
        console.error("Failed to fetch refund config", e);
    }
}

async function toggleRefundMode(isAutomated) {
    const mode = isAutomated ? 'AUTOMATED' : 'MANUAL';
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/admin/config`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Role': 'ADMIN'
            },
            body: JSON.stringify({ dispute_refund_mode: mode })
        });
        if (res.ok) {
            showToast(`Refund mode toggled to: ${mode}`);
        } else {
            showToast(`Failed to update refund mode`, true);
            // Revert checkbox
            document.getElementById('refund-mode-checkbox').checked = !isAutomated;
        }
    } catch (e) {
        showToast("Error updating refund mode", true);
        document.getElementById('refund-mode-checkbox').checked = !isAutomated;
    }
}

// ─── Provider Onboarding Queue ──────────────────────────────────────────────

async function fetchPendingProviders() {
    const container = document.getElementById('pending-providers-list');
    container.innerHTML = `
        <div class="empty-state">
            <span class="empty-icon">⏳</span>
            <p>Loading onboarding queue...</p>
        </div>
    `;
    
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/providers/pending`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (!res.ok) throw new Error("Failed to fetch pending providers");
        
        const providers = await res.json();
        if (providers.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">🎉</span>
                    <p>Onboarding queue is clear! No pending providers.</p>
                </div>
            `;
            return;
        }
        
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

        container.innerHTML = '';
        providers.forEach(p => {
            const item = document.createElement('div');
            item.className = 'list-item';
            
            // Format licensing detail if present
            const licText = p.licenseNumber ? `License: ${escapeHtml(p.licenseNumber)}` : 'No license required';
            
            item.innerHTML = `
                <div class="item-header">
                    <div>
                        <h3 class="item-title">${escapeHtml(p.name)}</h3>
                        <p class="item-subtitle">${escapeHtml(p.providerType)} — ${escapeHtml(p.fulfillmentType)}</p>
                    </div>
                    <span class="badge badge-pending">PENDING</span>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary); display: flex; flex-direction: column; gap: 0.25rem;">
                    <div>City: ${escapeHtml(p.city)} | Zip: ${escapeHtml(p.pincode)}</div>
                    <div>Address: ${escapeHtml(p.addressLine)}</div>
                    <div>${licText}</div>
                </div>
                <div class="btn-group">
                    <button class="btn btn-emerald" onclick="approveProvider('${escapeHtml(p.providerId)}')">Approve</button>
                    <button class="btn btn-rose" onclick="rejectProvider('${escapeHtml(p.providerId)}')">Reject</button>
                </div>
            `;
            container.appendChild(item);
        });
    } catch (e) {
        container.innerHTML = `
            <div class="empty-state">
                <span class="empty-icon">❌</span>
                <p>Failed to load onboarding queue: ${e.message}</p>
            </div>
        `;
    }
}

async function approveProvider(providerId) {
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/providers/${providerId}/approve`, {
            method: 'POST',
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (res.ok) {
            showToast("Provider approved successfully!");
            fetchPendingProviders();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || "Failed to approve provider", true);
        }
    } catch (e) {
        showToast("Network error approving provider", true);
    }
}

async function rejectProvider(providerId) {
    // Scaffold reject action (simulated)
    showToast("Provider rejection registered (Simulated)");
}

// ─── Dispute Tickets Queue ──────────────────────────────────────────────────

async function fetchDisputes() {
    const container = document.getElementById('disputes-list');
    renderDisputeMessage(container, '⏳', 'Loading disputes queue...');
    
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/disputes`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (!res.ok) throw new Error("Failed to fetch disputes");
        
        const disputes = await res.json();
        if (disputes.length === 0) {
            renderDisputeMessage(container, '🛡️', 'No disputes submitted.');
            return;
        }

        container.replaceChildren();
        disputes.forEach(d => {
            const item = document.createElement('div');
            item.className = 'list-item';

            const isResolved = d.status !== 'OPEN';
            const badgeClass = d.status === 'OPEN' ? 'badge-pending' : (d.status === 'RESOLVED' ? 'badge-success' : 'badge-danger');

            const header = document.createElement('div');
            header.className = 'item-header';
            const heading = document.createElement('div');
            const title = document.createElement('h3');
            title.className = 'item-title';
            title.textContent = 'Dispute on Order';
            const subtitle = document.createElement('p');
            subtitle.className = 'item-subtitle';
            subtitle.textContent = `Order ID: ${String(d.orderId ?? '')}`;
            heading.append(title, subtitle);
            const badge = document.createElement('span');
            badge.className = `badge ${badgeClass}`;
            badge.textContent = String(d.status ?? 'UNKNOWN');
            header.append(heading, badge);

            const reason = document.createElement('div');
            reason.style.cssText = 'font-size: 0.85rem; color: var(--text-secondary);';
            const reasonLabel = document.createElement('strong');
            reasonLabel.textContent = 'Reason: ';
            reason.append(reasonLabel, document.createTextNode(String(d.reason ?? '')));
            item.append(header, reason);

            if (isResolved) {
                const resolution = document.createElement('div');
                resolution.style.cssText = 'font-size: 0.85rem; padding: 0.5rem; background: rgba(255,255,255,0.02); border-radius: 6px; border: 1px dashed var(--border-glass);';
                const resolutionLabel = document.createElement('div');
                resolutionLabel.style.cssText = 'font-weight: 700; color: var(--text-primary);';
                resolutionLabel.textContent = 'Resolution:';
                const resolutionNotes = document.createElement('div');
                resolutionNotes.style.color = 'var(--text-secondary)';
                resolutionNotes.textContent = String(d.resolutionNotes || 'No notes provided');
                resolution.append(resolutionLabel, resolutionNotes);
                item.appendChild(resolution);
            } else {
                const actions = document.createElement('div');
                actions.className = 'btn-group';
                const resolveButton = document.createElement('button');
                resolveButton.type = 'button';
                resolveButton.className = 'btn';
                resolveButton.textContent = '⚖️ Resolve Ticket';
                resolveButton.addEventListener('click', () => openDisputeModal(String(d.disputeId), String(d.reason ?? '')));
                const invoiceButton = document.createElement('button');
                invoiceButton.type = 'button';
                invoiceButton.className = 'btn btn-emerald';
                invoiceButton.style.backgroundColor = '#374151';
                invoiceButton.textContent = '📄 View Invoice';
                invoiceButton.addEventListener('click', () => viewInvoice(String(d.orderId)));
                actions.append(resolveButton, invoiceButton);
                item.appendChild(actions);
            }

            container.appendChild(item);
        });
    } catch (e) {
        renderDisputeMessage(container, '❌', `Failed to load disputes: ${e.message || 'Unknown error'}`);
    }
}

function renderDisputeMessage(container, iconText, messageText) {
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

function openDisputeModal(disputeId, reason) {
    currentDisputeId = disputeId;
    document.getElementById('modal-dispute-reason').textContent = `Reason for dispute: "${reason}"`;
    document.getElementById('resolution-notes').value = '';
    document.getElementById('dispute-modal').classList.add('active');
}

function closeDisputeModal() {
    document.getElementById('dispute-modal').classList.remove('active');
    currentDisputeId = null;
}

async function submitResolution(decision) {
    if (!currentDisputeId) return;
    const notes = document.getElementById('resolution-notes').value.trim();
    if (!notes) {
        showToast("Please enter resolution notes", true);
        return;
    }
    
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/disputes/${currentDisputeId}/resolve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-User-Role': 'ADMIN'
            },
            body: JSON.stringify({ decision, resolutionNotes: notes })
        });
        if (res.ok) {
            showToast(`Dispute marked as ${decision}`);
            closeDisputeModal();
            fetchDisputes();
        } else {
            showToast("Failed to resolve dispute", true);
        }
    } catch (e) {
        showToast("Error sending resolution request", true);
    }
}

async function viewInvoice(orderId) {
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/${orderId}/invoice`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (res.ok) {
            const inv = await res.json();
            AlertInvoiceInfo(inv);
        } else {
            showToast("Invoice not generated yet for this order", true);
        }
    } catch (e) {
        showToast("Error retrieving invoice details", true);
    }
}

function AlertInvoiceInfo(inv) {
    alert(`📄 INVOICE DETAIL\n\nNumber: ${inv.invoiceNumber}\nSubtotal: ₹${inv.subtotalAmount.toFixed(2)}\nGST (18%): ₹${inv.taxAmount.toFixed(2)}\nGrand Total: ₹${inv.totalAmount.toFixed(2)}\nGenerated At: ${new Date(inv.generatedAt).toLocaleString()}`);
}

// ─── Banner Auction Outcomes ────────────────────────────────────────────────

async function fetchBannerAuctionOutcomes() {
    const container = document.getElementById('banner-auction-list');
    container.innerHTML = `
        <div class="empty-state">
            <span class="empty-icon">⏳</span>
            <p>Loading auction outcomes...</p>
        </div>
    `;

    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/content/banners/auction-outcomes`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (!res.ok) throw new Error(`Failed to fetch auction outcomes (${res.status})`);

        const outcomes = await res.json();
        if (!outcomes.length) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">📭</span>
                    <p>No active banner slots with auction outcomes yet.</p>
                </div>
            `;
            return;
        }

        container.innerHTML = '';
        outcomes.forEach(slot => {
            const item = document.createElement('div');
            item.className = 'list-item';

            const hasWinner = slot.providerId != null;
            const badgeClass = slot.active ? 'badge-success' : 'badge-pending';
            const bidText = slot.bidAmount != null ? `₹${Number(slot.bidAmount).toFixed(2)}` : '—';
            const providerText = hasWinner ? slot.providerId : 'Unassigned';

            item.innerHTML = `
                <div class="item-header">
                    <div>
                        <h3 class="item-title">Slot ${slot.slotOrder}: ${slot.title || 'Banner'}</h3>
                        <p class="item-subtitle">Duration: ${slot.durationSec || '—'}s</p>
                    </div>
                    <span class="badge ${badgeClass}">${slot.status || 'UNKNOWN'}</span>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary); display: flex; flex-direction: column; gap: 0.25rem;">
                    <div><strong>Winning provider:</strong> ${providerText}</div>
                    <div><strong>Winning bid:</strong> ${bidText}</div>
                    <div><strong>Active:</strong> ${slot.active ? 'Yes' : 'No'}</div>
                </div>
            `;
            container.appendChild(item);
        });
    } catch (e) {
        container.innerHTML = `
            <div class="empty-state">
                <span class="empty-icon">❌</span>
                <p>Failed to load auction outcomes: ${e.message}</p>
            </div>
        `;
    }
}

async function fetchUsers() {
    const container = document.getElementById('users-list');
    container.innerHTML = `
        <div class="empty-state">
            <span class="empty-icon">⏳</span>
            <p>Loading users...</p>
        </div>
    `;

    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/profiles`, {
            headers: getAuthHeaders()
        });
        if (!res.ok) throw new Error("Failed to fetch users");

        const users = await res.json();
        if (users.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">👥</span>
                    <p>No user profiles registered.</p>
                </div>
            `;
            return;
        }

        container.innerHTML = '';
        users.forEach(u => {
            const item = document.createElement('div');
            item.className = 'list-item';

            const safeUserId = escapeHtml(u.userId);
            const safeName = escapeHtml(u.fullName || 'User');
            const safeRole = escapeHtml(u.role || 'USER');
            const safePhone = escapeHtml(u.phoneNumber || 'N/A');

            const statusText = u.suspended ? 'REVOKED' : 'ACTIVE';
            const badgeClass = u.suspended ? 'badge-danger' : 'badge-success';
            const actionButton = u.suspended 
                ? `<button class="btn btn-emerald" onclick="restoreUserAccess('${safeUserId}')">🔓 Restore Access</button>`
                : `<button class="btn btn-rose" onclick="revokeUserAccess('${safeUserId}')">🚫 Revoke Access</button>`;

            item.innerHTML = `
                <div class="item-header">
                    <div>
                        <h3 class="item-title">${safeName}</h3>
                        <p class="item-subtitle">${safeRole} — ${safePhone}</p>
                    </div>
                    <span class="badge ${badgeClass}">${statusText}</span>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary);">
                    User ID: ${safeUserId}
                </div>
                <div class="btn-group">
                    ${actionButton}
                </div>
            `;
            container.appendChild(item);
        });
    } catch (e) {
        container.innerHTML = `
            <div class="empty-state">
                <span class="empty-icon">❌</span>
                <p>Failed to load users: ${escapeHtml(e.message)}</p>
            </div>
        `;
    }
}

async function revokeUserAccess(userId) {
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/profiles/${userId}/revoke`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (res.ok) {
            showToast("Access revoked successfully!");
            fetchUsers();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || "Failed to revoke access", true);
        }
    } catch (e) {
        showToast("Error revoking user access", true);
    }
}

async function restoreUserAccess(userId) {
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/profiles/${userId}/restore`, {
            method: 'POST',
            headers: getAuthHeaders()
        });
        if (res.ok) {
            showToast("Access restored successfully!");
            fetchUsers();
        } else {
            const err = await res.json().catch(() => ({}));
            showToast(err.error || "Failed to restore access", true);
        }
    } catch (e) {
        showToast("Error restoring user access", true);
    }
}
