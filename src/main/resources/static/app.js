// PayFlow Frontend Application Logic
const API_BASE = window.location.origin;

let state = {
    token: null,
    user: null,
    wallet: null,
    billers: [],
    selectedBiller: null,
    idempBlockedCount: 0,
    lastPaymentResponse: null
};

// --- Initialization ---
document.addEventListener('DOMContentLoaded', async () => {
    initTabs();
    initPresets();
    initIdempotencyKey();
    initModals();
    await authenticateDefaultUser();
    await loadBillers();
    await loadWallet();
    await loadTransactions();
    await loadLedger();
    await loadScheduledBills();
});

// --- Tab Navigation ---
function initTabs() {
    const tabs = document.querySelectorAll('.tab-btn[data-tab]');
    tabs.forEach(btn => {
        btn.addEventListener('click', () => {
            tabs.forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));

            btn.classList.add('active');
            const targetPane = document.getElementById(btn.dataset.tab);
            if (targetPane) targetPane.classList.add('active');

            // Refresh tab-specific data
            if (btn.dataset.tab === 'tab-transactions') loadTransactions();
            if (btn.dataset.tab === 'tab-ledger') loadLedger();
            if (btn.dataset.tab === 'tab-scheduled') loadScheduledBills();
        });
    });
}

// --- UUID Generation ---
function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0, v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function initIdempotencyKey() {
    const keyInput = document.getElementById('idemp-key-input');
    keyInput.value = 'idemp_' + generateUUID();

    document.getElementById('btn-regen-idemp').addEventListener('click', () => {
        keyInput.value = 'idemp_' + generateUUID();
    });
}

// --- Authentication ---
async function authenticateDefaultUser() {
    try {
        logKafkaStream('Authenticating demo user: alex@example.com', 'system-line');
        const res = await fetch(`${API_BASE}/api/v1/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'alex@example.com', password: 'password123' })
        });
        const json = await res.json();
        if (json.success && json.data) {
            state.token = json.data.token;
            state.user = json.data.user;
            document.getElementById('user-display-name').textContent = state.user.fullName;
            logKafkaStream(`Authenticated session established: JWT [${state.token.substring(0, 16)}...]`, 'success-line');
        }
    } catch (e) {
        console.error('Auth error:', e);
    }
}

// --- Wallet & Top Up ---
async function loadWallet() {
    if (!state.token) return;
    try {
        const res = await fetch(`${API_BASE}/api/v1/wallets/me`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        if (json.success && json.data) {
            state.wallet = json.data;
            const balanceElem = document.getElementById('wallet-balance');
            balanceElem.textContent = Number(state.wallet.balance).toLocaleString('en-IN', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            });
        }
    } catch (e) {
        console.error('Wallet error:', e);
    }
}

function initModals() {
    // Top-up modal
    const topupModal = document.getElementById('topup-modal');
    document.getElementById('btn-open-topup').addEventListener('click', () => {
        topupModal.classList.remove('hidden');
    });
    document.getElementById('btn-close-topup').addEventListener('click', () => {
        topupModal.classList.add('hidden');
    });

    document.getElementById('topup-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const amount = parseFloat(document.getElementById('topup-amount').value);
        const method = document.getElementById('topup-method').value;

        try {
            const res = await fetch(`${API_BASE}/api/v1/wallets/top-up`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${state.token}`
                },
                body: JSON.stringify({
                    amount: amount,
                    paymentMethod: method,
                    reference: 'TOPUP_' + Date.now()
                })
            });
            const json = await res.json();
            if (json.success) {
                topupModal.classList.add('hidden');
                await loadWallet();
                await loadLedger();
                logKafkaStream(`Wallet credited +₹${amount.toFixed(2)} via ${method} (Double-Entry Recorded)`, 'success-line');
            } else {
                alert('Top-up failed: ' + json.message);
            }
        } catch (err) {
            alert('Error topping up: ' + err.message);
        }
    });

    // Ledger modal
    document.getElementById('btn-close-ledger-modal').addEventListener('click', () => {
        document.getElementById('ledger-modal').classList.add('hidden');
    });
}

// --- Billers ---
async function loadBillers() {
    try {
        const res = await fetch(`${API_BASE}/api/v1/billers`);
        const json = await res.json();
        if (json.success && json.data) {
            state.billers = json.data;
            renderCategories();
            populateBillerSelect();
            populateSchedBillerSelect();
        }
    } catch (e) {
        console.error('Error loading billers:', e);
    }
}

function renderCategories() {
    const container = document.getElementById('category-chips');
    const categories = ['ALL', ...new Set(state.billers.map(b => b.category))];

    container.innerHTML = categories.map((cat, idx) => `
        <button type="button" class="cat-chip ${idx === 0 ? 'active' : ''}" data-cat="${cat}">
            ${getCategoryIcon(cat)} ${formatCategory(cat)}
        </button>
    `).join('');

    container.querySelectorAll('.cat-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            container.querySelectorAll('.cat-chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            populateBillerSelect(chip.dataset.cat);
        });
    });
}

function getCategoryIcon(cat) {
    const map = {
        'ALL': '✨',
        'ELECTRICITY': '⚡',
        'MOBILE_PREPAID': '📱',
        'MOBILE_POSTPAID': '📶',
        'DTH': '📡',
        'BROADBAND': '🌐',
        'PIPED_GAS': '🔥',
        'WATER': '💧',
        'CREDIT_CARD': '💳'
    };
    return map[cat] || '📋';
}

function formatCategory(cat) {
    if (cat === 'ALL') return 'All Services';
    return cat.replace(/_/g, ' ');
}

function populateBillerSelect(categoryFilter = 'ALL') {
    const select = document.getElementById('biller-select');
    const filtered = categoryFilter === 'ALL' ?
        state.billers :
        state.billers.filter(b => b.category === categoryFilter);

    select.innerHTML = '<option value="">-- Choose Biller --</option>' +
        filtered.map(b => `<option value="${b.id}">${b.name} (${formatCategory(b.category)})</option>`).join('');

    if (filtered.length > 0) {
        select.value = filtered[0].id;
        onBillerSelected(filtered[0].id);
    }

    select.addEventListener('change', () => {
        onBillerSelected(select.value);
    });
}

function populateSchedBillerSelect() {
    const select = document.getElementById('sched-biller-select');
    select.innerHTML = state.billers.map(b => `<option value="${b.id}">${b.name}</option>`).join('');
}

function onBillerSelected(billerId) {
    const biller = state.billers.find(b => b.id == billerId);
    if (!biller) return;
    state.selectedBiller = biller;

    document.getElementById('consumer-label').textContent = biller.accountNumberLabel || 'Consumer Number';
    document.getElementById('regex-hint').textContent = biller.accountNumberRegex ?
        `Format rule: ${biller.accountNumberRegex}` : 'Any format';

    // Populate realistic sample account number
    const consumerInput = document.getElementById('consumer-input');
    if (biller.category === 'ELECTRICITY') consumerInput.value = '900012345678';
    else if (biller.category.includes('MOBILE')) consumerInput.value = '9876543210';
    else if (biller.category === 'DTH') consumerInput.value = '1000293847';
    else if (biller.category === 'CREDIT_CARD') consumerInput.value = '4111222233334444';
    else consumerInput.value = 'ACT987654';
}

// --- Simulation Presets ---
function initPresets() {
    const consumerInput = document.getElementById('consumer-input');

    document.getElementById('preset-success').addEventListener('click', () => {
        let val = consumerInput.value || '900012345';
        if (val.length >= 3) val = val.substring(0, val.length - 3);
        consumerInput.value = val + '111';
    });

    document.getElementById('preset-timeout').addEventListener('click', () => {
        let val = consumerInput.value || '900012345';
        if (val.length >= 3) val = val.substring(0, val.length - 3);
        consumerInput.value = val + '999';
    });

    document.getElementById('preset-decline').addEventListener('click', () => {
        let val = consumerInput.value || '900012345';
        if (val.length >= 3) val = val.substring(0, val.length - 3);
        consumerInput.value = val + '000';
    });
}

// --- Payment Execution Form ---
document.getElementById('payment-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    await executePayment(false);
});

document.getElementById('btn-resend-idemp').addEventListener('click', async () => {
    await executePayment(true);
});

async function executePayment(isDuplicateTest) {
    const btnSubmit = document.getElementById('btn-pay-submit');
    const billerId = parseInt(document.getElementById('biller-select').value);
    const consumerNumber = document.getElementById('consumer-input').value.trim();
    const amount = parseFloat(document.getElementById('amount-input').value);
    const idempKey = document.getElementById('idemp-key-input').value.trim();
    const saveAccount = document.getElementById('save-account-check').checked;

    if (!idempKey) {
        alert('Idempotency-Key is required!');
        return;
    }

    btnSubmit.disabled = true;
    btnSubmit.querySelector('.btn-text').textContent = 'Processing Pipeline...';
    btnSubmit.querySelector('.btn-spinner').classList.remove('hidden');

    logKafkaStream(`[PIPELINE START] Key: ${idempKey} | Amount: ₹${amount.toFixed(2)} | Consumer: ${consumerNumber}`, 'system-line');

    try {
        const startTime = performance.now();
        const res = await fetch(`${API_BASE}/api/v1/payments`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.token}`,
                'Idempotency-Key': idempKey
            },
            body: JSON.stringify({
                billerId,
                consumerNumber,
                amount,
                saveAccount
            })
        });

        const elapsedMs = Math.round(performance.now() - startTime);
        const json = await res.json();
        const data = json.data;

        // Check if idempotency hit
        if (isDuplicateTest && (res.status === 200 || res.headers.get('X-Cache-Lookup') === 'HIT')) {
            state.idempBlockedCount++;
            document.getElementById('idemp-prevented-count').textContent = `${state.idempBlockedCount} Prevented`;
            logKafkaStream(`[IDEMPOTENCY GUARD] Duplicate request blocked! Returned cached response for ${idempKey}. NO double-charge!`, 'warn-line');
        }

        renderInspectorResult(json, res.status, idempKey, elapsedMs);

        // Update Wallet & Transactions
        await loadWallet();
        await loadTransactions();
        await loadLedger();

        // Regenerate key automatically unless testing duplicate
        if (!isDuplicateTest) {
            initIdempotencyKey();
        }

    } catch (err) {
        alert('Network or payment error: ' + err.message);
    } finally {
        btnSubmit.disabled = false;
        btnSubmit.querySelector('.btn-text').textContent = '⚡ Authorize & Pay Bill';
        btnSubmit.querySelector('.btn-spinner').classList.add('hidden');
    }
}

function renderInspectorResult(responsePayload, httpStatus, idempKey, latencyMs) {
    const placeholder = document.getElementById('inspector-placeholder');
    const resultBox = document.getElementById('inspector-result');

    placeholder.classList.add('hidden');
    resultBox.classList.remove('hidden');

    const data = responsePayload.data;
    const status = data ? data.status : 'FAILED';
    const statusBadge = getStatusBadge(status);

    resultBox.innerHTML = `
        <div class="inspector-card">
            <div class="inspector-header">
                <div>
                    <span class="text-sm text-muted">Status Code: HTTP ${httpStatus}</span>
                    <h3 style="margin-top: 0.2rem;">${data ? data.transactionRef : 'Transaction Failed'}</h3>
                </div>
                ${statusBadge}
            </div>

            <div class="trace-step">
                <span class="step-check">✓</span>
                <span class="step-desc">Idempotency Key Check</span>
                <span class="step-val">${idempKey.substring(0, 18)}...</span>
            </div>
            <div class="trace-step">
                <span class="step-check">✓</span>
                <span class="step-desc">Wallet Balance Locked (PESSIMISTIC_WRITE)</span>
                <span class="step-val">SELECT ... FOR UPDATE</span>
            </div>
            <div class="trace-step">
                <span class="step-check">${status === 'FAILED' ? '↩' : '✓'}</span>
                <span class="step-desc">Double-Entry Ledger Balancing</span>
                <span class="step-val">DEBIT User / CREDIT Biller [₹${data ? data.amount : '0'}]</span>
            </div>
            <div class="trace-step">
                <span class="step-check">${status === 'SUCCESS' ? '✓' : status === 'RETRYING' ? '⏳' : '✕'}</span>
                <span class="step-desc">Mock Gateway Protocol (${latencyMs}ms)</span>
                <span class="step-val">${data && data.gatewayReference ? data.gatewayReference : 'GW_TIMEOUT'}</span>
            </div>
            <div class="trace-step">
                <span class="step-check">✓</span>
                <span class="step-desc">Kafka Event Published</span>
                <span class="step-val">payflow.payment.notifications</span>
            </div>

            ${status === 'RETRYING' ? `
                <div class="mt-3 p-2" style="background: rgba(245,158,11,0.1); border-radius: 8px; border: 1px solid rgba(245,158,11,0.3); font-size: 0.8rem; color: #fcd34d;">
                    ⚠️ Transient timeout simulated! Payment placed into Retry Queue with exponential backoff (attempt 1/3).
                </div>
            ` : ''}

            ${status === 'FAILED' ? `
                <div class="mt-3 p-2" style="background: rgba(244,63,94,0.1); border-radius: 8px; border: 1px solid rgba(244,63,94,0.3); font-size: 0.8rem; color: #fda4af;">
                    ✕ Permanent failure: ${data ? data.failureReason : responsePayload.message}. Wallet balance automatically refunded!
                </div>
            ` : ''}

            ${data ? `
                <div class="mt-3">
                    <button class="btn btn-sm btn-secondary" onclick="viewTransactionLedger('${data.id}')">
                        🔍 View Double-Entry Ledger For This Txn
                    </button>
                </div>
            ` : ''}
        </div>
    `;
}

function getStatusBadge(status) {
    if (status === 'SUCCESS') return `<span class="badge badge-emerald">SUCCESS</span>`;
    if (status === 'RETRYING') return `<span class="badge badge-amber">RETRYING</span>`;
    if (status === 'FAILED') return `<span class="badge badge-rose">FAILED</span>`;
    if (status === 'REFUNDED') return `<span class="badge badge-violet">REFUNDED</span>`;
    return `<span class="badge badge-cyan">${status}</span>`;
}

// --- Transactions Tab ---
async function loadTransactions() {
    if (!state.token) return;
    try {
        const res = await fetch(`${API_BASE}/api/v1/payments`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        const tbody = document.getElementById('transactions-tbody');

        if (json.success && json.data && json.data.length > 0) {
            tbody.innerHTML = json.data.map(t => `
                <tr>
                    <td>
                        <div class="font-mono" style="font-weight:600;">${t.transactionRef}</div>
                        <div class="text-sm text-muted">${new Date(t.createdAt).toLocaleTimeString()}</div>
                    </td>
                    <td>
                        <div>${t.billerName}</div>
                        <div class="font-mono text-sm text-muted">${t.consumerNumber}</div>
                    </td>
                    <td class="font-mono">₹${Number(t.amount).toFixed(2)}</td>
                    <td>${getStatusBadge(t.status)}</td>
                    <td class="font-mono text-sm">${t.gatewayReference || '—'}</td>
                    <td>${t.retryCount > 0 ? `<span class="badge badge-amber">${t.retryCount}/3</span>` : '0'}</td>
                    <td class="font-mono text-sm">${t.idempotencyKey.substring(0, 14)}...</td>
                    <td>
                        <button class="btn btn-sm btn-secondary" onclick="viewTransactionLedger('${t.id}')">Ledger</button>
                    </td>
                </tr>
            `).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center py-4 text-muted">No transactions found</td></tr>`;
        }
    } catch (e) {
        console.error('Error loading transactions:', e);
    }
}
document.getElementById('btn-refresh-txns').addEventListener('click', loadTransactions);

// --- Double-Entry Ledger Tab ---
async function loadLedger() {
    if (!state.token) return;
    try {
        const res = await fetch(`${API_BASE}/api/v1/wallets/ledger`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        const tbody = document.getElementById('ledger-tbody');

        if (json.success && json.data && json.data.length > 0) {
            tbody.innerHTML = json.data.map(l => `
                <tr>
                    <td>
                        <div class="font-mono">#${l.id}</div>
                        <div class="text-sm text-muted">${new Date(l.createdAt).toLocaleTimeString()}</div>
                    </td>
                    <td><span class="badge badge-cyan">${l.accountType}</span></td>
                    <td class="font-mono text-sm">${l.accountId}</td>
                    <td>
                        <span class="badge ${l.entryType === 'CREDIT' ? 'badge-emerald' : 'badge-rose'}">
                            ${l.entryType}
                        </span>
                    </td>
                    <td class="font-mono" style="font-weight:600; color: ${l.entryType === 'CREDIT' ? 'var(--emerald)' : 'var(--rose)'};">
                        ${l.entryType === 'CREDIT' ? '+' : '-'}₹${Number(l.amount).toFixed(2)}
                    </td>
                    <td class="font-mono">₹${l.balanceAfter ? Number(l.balanceAfter).toFixed(2) : '—'}</td>
                    <td class="text-muted text-sm">${l.description}</td>
                </tr>
            `).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-muted">No ledger records yet</td></tr>`;
        }
    } catch (e) {
        console.error('Error loading ledger:', e);
    }
}

// --- View Single Txn Ledger Modal ---
window.viewTransactionLedger = async function(transactionId) {
    if (!state.token) return;
    try {
        const res = await fetch(`${API_BASE}/api/v1/payments/${transactionId}/ledger`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        const modal = document.getElementById('ledger-modal');
        const tbody = document.getElementById('modal-ledger-tbody');

        if (json.success && json.data) {
            tbody.innerHTML = json.data.map(l => `
                <tr>
                    <td><span class="badge badge-cyan">${l.accountType}</span></td>
                    <td class="font-mono text-sm">${l.accountId}</td>
                    <td>
                        <span class="badge ${l.entryType === 'CREDIT' ? 'badge-emerald' : 'badge-rose'}">
                            ${l.entryType}
                        </span>
                    </td>
                    <td class="font-mono">₹${Number(l.amount).toFixed(2)}</td>
                    <td class="font-mono">₹${l.balanceAfter ? Number(l.balanceAfter).toFixed(2) : '—'}</td>
                    <td class="text-sm text-muted">${l.description}</td>
                </tr>
            `).join('');
            modal.classList.remove('hidden');
        }
    } catch (e) {
        console.error('Ledger modal error:', e);
    }
};

// --- Scheduled Bills ---
async function loadScheduledBills() {
    if (!state.token) return;
    try {
        const res = await fetch(`${API_BASE}/api/v1/bills/scheduled`, {
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        const tbody = document.getElementById('scheduled-tbody');

        if (json.success && json.data && json.data.length > 0) {
            tbody.innerHTML = json.data.map(s => `
                <tr>
                    <td>${s.billerName}</td>
                    <td class="font-mono text-sm">${s.consumerNumber}</td>
                    <td class="font-mono">₹${Number(s.amount).toFixed(2)}</td>
                    <td><span class="badge badge-violet">${s.frequency}</span></td>
                    <td class="font-mono text-sm">${new Date(s.nextExecutionDate).toLocaleString()}</td>
                    <td>${getStatusBadge(s.status)}</td>
                    <td>
                        ${s.status === 'ACTIVE' ? `
                            <button class="btn btn-sm btn-secondary" onclick="cancelScheduledBill(${s.id})">Cancel</button>
                        ` : '—'}
                    </td>
                </tr>
            `).join('');
        } else {
            tbody.innerHTML = `<tr><td colspan="7" class="text-center py-4 text-muted">No scheduled bills active</td></tr>`;
        }
    } catch (e) {
        console.error('Error loading scheduled bills:', e);
    }
}
document.getElementById('btn-refresh-sched').addEventListener('click', loadScheduledBills);

document.getElementById('schedule-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const billerId = parseInt(document.getElementById('sched-biller-select').value);
    const consumerNumber = document.getElementById('sched-consumer-input').value.trim();
    const amount = parseFloat(document.getElementById('sched-amount-input').value);
    const frequency = document.getElementById('sched-frequency-select').value;

    try {
        const res = await fetch(`${API_BASE}/api/v1/bills/schedule`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${state.token}`
            },
            body: JSON.stringify({ billerId, consumerNumber, amount, frequency })
        });
        const json = await res.json();
        if (json.success) {
            alert('Recurring bill scheduled successfully!');
            await loadScheduledBills();
        }
    } catch (err) {
        alert('Error scheduling bill: ' + err.message);
    }
});

window.cancelScheduledBill = async function(id) {
    if (!confirm('Cancel this scheduled payment?')) return;
    try {
        await fetch(`${API_BASE}/api/v1/bills/scheduled/${id}`, {
            method: 'DELETE',
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        await loadScheduledBills();
    } catch (e) {
        alert('Error cancelling: ' + e.message);
    }
};

// --- Reconciliation ---
document.getElementById('btn-run-recon').addEventListener('click', async () => {
    const btn = document.getElementById('btn-run-recon');
    btn.disabled = true;
    btn.textContent = 'Running Batch...';

    try {
        const res = await fetch(`${API_BASE}/api/v1/reconciliation/run`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${state.token}` }
        });
        const json = await res.json();
        if (json.success && json.data) {
            const d = json.data;
            document.getElementById('recon-batch-id').textContent = d.batchId;
            document.getElementById('recon-checked').textContent = d.totalChecked;
            document.getElementById('recon-discrepancies').textContent = d.discrepancyCount;
            document.getElementById('recon-resolved').textContent = d.autoResolvedCount;

            // Load records
            const recRes = await fetch(`${API_BASE}/api/v1/reconciliation/records`, {
                headers: { 'Authorization': `Bearer ${state.token}` }
            });
            const recJson = await recRes.json();
            const tbody = document.getElementById('recon-records-tbody');

            if (recJson.success && recJson.data && recJson.data.length > 0) {
                tbody.innerHTML = recJson.data.map(r => `
                    <tr>
                        <td class="font-mono text-sm">${r.transactionId.substring(0, 10)}...</td>
                        <td>${r.ledgerStatus}</td>
                        <td>${r.gatewayStatus}</td>
                        <td><span class="badge badge-amber">${r.discrepancyType}</span></td>
                        <td class="text-sm">${r.resolved ? '✅ Auto-Resolved' : '⚠️ Pending Review'}</td>
                    </tr>
                `).join('');
            }
            logKafkaStream(`Reconciliation batch ${d.batchId} complete: Checked=${d.totalChecked}, Discrepancies=${d.discrepancyCount}, Resolved=${d.autoResolvedCount}`, 'success-line');
        }
    } catch (err) {
        alert('Reconciliation error: ' + err.message);
    } finally {
        btn.disabled = false;
        btn.textContent = '▶ Trigger Recon Job';
    }
});

// --- Webhook Simulation Form ---
document.getElementById('webhook-sim-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const txnRef = document.getElementById('wh-txn-ref').value.trim();
    const amount = parseFloat(document.getElementById('wh-amount').value);
    const status = document.getElementById('wh-status').value;

    try {
        const res = await fetch(`${API_BASE}/api/v1/mock-gateway/simulate-webhook?transactionRef=${txnRef}&amount=${amount}&status=${status}`, {
            method: 'POST'
        });
        const json = await res.json();
        const resultElem = document.getElementById('wh-sim-result');

        if (json.success) {
            resultElem.innerHTML = `
                <div class="p-2" style="background: rgba(16,185,129,0.1); border: 1px solid rgba(16,185,129,0.3); border-radius: 8px; font-size: 0.8rem; color: #6ee7b7;">
                    ✓ Webhook signed and delivered! Signature: <code>${json.data.signature.substring(0, 24)}...</code>
                </div>
            `;
            await loadTransactions();
            await loadWallet();
            await loadLedger();
            logKafkaStream(`Mock Gateway sent signed webhook for ${txnRef}: Status=${status}`, 'success-line');
        } else {
            resultElem.innerHTML = `<div class="text-danger text-sm">Failed: ${json.message}</div>`;
        }
    } catch (err) {
        alert('Webhook error: ' + err.message);
    }
});

// --- Kafka Event Console Helper ---
function logKafkaStream(text, lineClass = 'system-line') {
    const consoleBox = document.getElementById('kafka-stream-console');
    if (!consoleBox) return;

    const line = document.createElement('div');
    line.className = `stream-line ${lineClass}`;
    line.textContent = `[${new Date().toLocaleTimeString()}] ${text}`;
    consoleBox.appendChild(line);
    consoleBox.scrollTop = consoleBox.scrollHeight;
}

document.getElementById('btn-clear-events').addEventListener('click', () => {
    document.getElementById('kafka-stream-console').innerHTML = '';
});
