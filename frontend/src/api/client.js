import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Attach X-Username header to every request if user is logged in
client.interceptors.request.use((config) => {
  const username = localStorage.getItem('username');
  if (username) {
    config.headers['X-Username'] = username;
  }
  return config;
});

// Auth API
export const authApi = {
  login: (username, password) =>
    client.post('/auth/login', { username, password }),
  logout: () => client.post('/auth/logout'),
};

// Account API
export const accountApi = {
  getMyAccounts: () => client.get('/accounts/me'),
  getAccount: (id) => client.get(`/accounts/${id}`),
  getTransactionHistory: (id) => client.get(`/accounts/${id}/transactions`),
  getAllAccounts: () => client.get('/accounts'),
};

// Transaction API
export const transactionApi = {
  transfer: (sourceAccountId, destAccountId, amount, idempotencyKey) =>
    client.post('/transactions',
      { sourceAccountId, destAccountId, amount },
      { headers: { 'Idempotency-Key': idempotencyKey } }
    ),
  reverse: (transactionId) =>
    client.post(`/transactions/${transactionId}/reverse`),
};

// Exchange Rate API
export const exchangeRateApi = {
  getAll: () => client.get('/exchange-rates'),
};

// Reconciliation API
export const reconciliationApi = {
  verifyLedger: () => client.get('/reconciliation/ledger-verification'),
  runReconciliation: () => client.post('/reconciliation/run'),
};

// Debug API
export const debugApi = {
  setBalance: (accountId, balance) =>
    client.put(`/debug/accounts/${accountId}/balance`, { balance }),
  setExchangeRate: (id, rate) =>
    client.put(`/debug/exchange-rates/${id}`, { rate }),
  concurrentTransfers: (data) =>
    client.post('/debug/concurrent-transfers', data),
};

export default client;
