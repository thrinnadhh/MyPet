const API_BASE_URL = 'http://localhost:8080';

let activeTab = 'approvals';
let currentDisputeId = null;

// On Load
document.addEventListener('DOMContentLoaded', () => {
    fetchRefundModeConfig();
    fetchPendingProviders();
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
        
        container.innerHTML = '';
        providers.forEach(p => {
            const item = document.createElement('div');
            item.className = 'list-item';
            
            // Format licensing detail if present
            const licText = p.licenseNumber ? `License: ${p.licenseNumber}` : 'No license required';
            
            item.innerHTML = `
                <div class="item-header">
                    <div>
                        <h3 class="item-title">${p.name}</h3>
                        <p class="item-subtitle">${p.providerType} — ${p.fulfillmentType}</p>
                    </div>
                    <span class="badge badge-pending">PENDING</span>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary); display: flex; flex-direction: column; gap: 0.25rem;">
                    <div>City: ${p.city} | Zip: ${p.pincode}</div>
                    <div>Address: ${p.addressLine}</div>
                    <div>${licText}</div>
                </div>
                <div class="btn-group">
                    <button class="btn btn-emerald" onclick="approveProvider('${p.providerId}')">Approve</button>
                    <button class="btn btn-rose" onclick="rejectProvider('${p.providerId}')">Reject</button>
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
    container.innerHTML = `
        <div class="empty-state">
            <span class="empty-icon">⏳</span>
            <p>Loading disputes queue...</p>
        </div>
    `;
    
    try {
        const res = await fetch(`${API_BASE_URL}/api/v1/orders/disputes`, {
            headers: { 'X-User-Role': 'ADMIN' }
        });
        if (!res.ok) throw new Error("Failed to fetch disputes");
        
        const disputes = await res.json();
        if (disputes.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <span class="empty-icon">🛡️</span>
                    <p>No disputes submitted.</p>
                </div>
            `;
            return;
        }
        
        container.innerHTML = '';
        disputes.forEach(d => {
            const item = document.createElement('div');
            item.className = 'list-item';
            
            const isResolved = d.status !== 'OPEN';
            const badgeClass = d.status === 'OPEN' ? 'badge-pending' : (d.status === 'RESOLVED' ? 'badge-success' : 'badge-danger');
            
            let btnOrNotesHtml = `
                <div class="btn-group">
                    <button class="btn" onclick="openDisputeModal('${d.disputeId}', '${d.reason.replace(/'/g, "\\'")}')">⚖️ Resolve Ticket</button>
                    <button class="btn btn-emerald" style="background-color: #374151;" onclick="viewInvoice('${d.orderId}')">📄 View Invoice</button>
                </div>
            `;
            if (isResolved) {
                btnOrNotesHtml = `
                    <div style="font-size: 0.85rem; padding: 0.5rem; background: rgba(255,255,255,0.02); border-radius: 6px; border: 1px dashed var(--border-glass);">
                        <div style="font-weight: 700; color: var(--text-primary);">Resolution:</div>
                        <div style="color: var(--text-secondary);">${d.resolutionNotes || 'No notes provided'}</div>
                    </div>
                `;
            }
            
            item.innerHTML = `
                <div class="item-header">
                    <div>
                        <h3 class="item-title">Dispute on Order</h3>
                        <p class="item-subtitle">Order ID: ${d.orderId}</p>
                    </div>
                    <span class="badge ${badgeClass}">${d.status}</span>
                </div>
                <div style="font-size: 0.85rem; color: var(--text-secondary);">
                    <strong>Reason:</strong> ${d.reason}
                </div>
                ${btnOrNotesHtml}
            `;
            container.appendChild(item);
        });
    } catch (e) {
        container.innerHTML = `
            <div class="empty-state">
                <span class="empty-icon">❌</span>
                <p>Failed to load disputes: ${e.message}</p>
            </div>
        `;
    }
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
