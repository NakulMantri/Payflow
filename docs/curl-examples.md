# PayFlow — cURL Command Reference Guide

This reference demonstrates all production API endpoints for the **PayFlow** platform.

Base URL: `http://localhost:8080`

---

## 1. Authentication

### Register a New User
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah@example.com",
    "password": "password123",
    "fullName": "Sarah Connor",
    "phone": "+919811223344"
  }'
```

### Login (Obtain JWT Token)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alex@example.com",
    "password": "password123"
  }'
```
*Export the returned token:*
```bash
export TOKEN="<YOUR_JWT_TOKEN>"
```

### View User Profile
```bash
curl -X GET http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## 2. Wallet & Double-Entry Ledger

### Check Current Balance
```bash
curl -X GET http://localhost:8080/api/v1/wallets/me \
  -H "Authorization: Bearer $TOKEN"
```

### Top-Up Wallet Balance
```bash
curl -X POST http://localhost:8080/api/v1/wallets/top-up \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "amount": 2500.00,
    "paymentMethod": "UPI",
    "reference": "UPI_TOPUP_8899"
  }'
```

### Inspect Double-Entry Ledger for Wallet
```bash
curl -X GET http://localhost:8080/api/v1/wallets/ledger \
  -H "Authorization: Bearer $TOKEN"
```

---

## 3. Billers Catalog & Accounts

### List All Billers
```bash
curl -X GET http://localhost:8080/api/v1/billers \
  -H "Authorization: Bearer $TOKEN"
```

### Filter Billers by Category
```bash
curl -X GET "http://localhost:8080/api/v1/billers?category=ELECTRICITY" \
  -H "Authorization: Bearer $TOKEN"
```

### Save Consumer Number
```bash
curl -X POST http://localhost:8080/api/v1/billers/accounts \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345678",
    "nickname": "Home Electricity Meter"
  }'
```

---

## 4. Payment Execution & Idempotency Testing

### 1. Standard Idempotent Payment
```bash
export IDEMP_KEY=$(uuidgen)

curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345678",
    "amount": 450.00,
    "saveAccount": false
  }'
```

### 2. Verify Idempotency Protection (Re-send Same Key)
```bash
# Re-sending the identical Idempotency-Key returns cached response without double debiting:
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345678",
    "amount": 450.00
  }'
```

### 3. Simulate Gateway Timeout & Retry Queue (Consumer ends with 999)
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345999",
    "amount": 750.00
  }'
```

### 4. Simulate Hard Biller Decline & Automatic Refund (Consumer ends with 000)
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345000",
    "amount": 200.00
  }'
```

### 5. View Payment Transaction History
```bash
curl -X GET http://localhost:8080/api/v1/payments \
  -H "Authorization: Bearer $TOKEN"
```

### 6. Inspect Double-Entry Ledger for a Transaction
```bash
curl -X GET http://localhost:8080/api/v1/payments/<TRANSACTION_ID>/ledger \
  -H "Authorization: Bearer $TOKEN"
```

---

## 5. Scheduled & Recurring Payments

### Schedule a Recurring Monthly Payment
```bash
curl -X POST http://localhost:8080/api/v1/bills/schedule \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "billerId": 1,
    "consumerNumber": "900012345678",
    "amount": 600.00,
    "frequency": "MONTHLY",
    "dueDate": "2026-10-05"
  }'
```

### List Scheduled Bills
```bash
curl -X GET http://localhost:8080/api/v1/bills/scheduled \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Reconciliation & Webhook Simulation

### Trigger On-Demand Reconciliation
```bash
curl -X POST http://localhost:8080/api/v1/reconciliation/run \
  -H "Authorization: Bearer $TOKEN"
```

### Inspect Reconciliation Discrepancy Records
```bash
curl -X GET http://localhost:8080/api/v1/reconciliation/records \
  -H "Authorization: Bearer $TOKEN"
```

### Simulate Asynchronous Webhook
```bash
curl -X POST "http://localhost:8080/api/v1/mock-gateway/simulate-webhook?transactionRef=TXN_SAMPLE_01&amount=500.00&status=SUCCESS"
```
