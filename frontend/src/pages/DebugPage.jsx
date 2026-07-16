import { useState, useEffect } from 'react';
import { debugApi, accountApi, exchangeRateApi } from '../api/client';

export default function DebugPage() {
  const [accounts, setAccounts] = useState([]);
  const [rates, setRates] = useState([]);

  // Balance manipulation
  const [balanceAccountId, setBalanceAccountId] = useState('');
  const [newBalance, setNewBalance] = useState('');
  const [balanceResult, setBalanceResult] = useState(null);

  // Exchange rate manipulation
  const [rateId, setRateId] = useState('');
  const [newRate, setNewRate] = useState('');
  const [rateResult, setRateResult] = useState(null);

  // Concurrent transfers
  const [concSourceId, setConcSourceId] = useState('');
  const [concDestId, setConcDestId] = useState('');
  const [concAmount, setConcAmount] = useState('10.00');
  const [concCount, setConcCount] = useState('5');
  const [concResult, setConcResult] = useState(null);
  const [concLoading, setConcLoading] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [accRes, ratesRes] = await Promise.all([
        accountApi.getAllAccounts(),
        exchangeRateApi.getAll(),
      ]);
      setAccounts(accRes.data);
      setRates(ratesRes.data);
    } catch (err) {
      console.error('Failed to load data:', err);
    }
  };

  const handleSetBalance = async (e) => {
    e.preventDefault();
    setBalanceResult(null);
    try {
      const res = await debugApi.setBalance(balanceAccountId, newBalance);
      setBalanceResult(res.data);
      loadData();
    } catch (err) {
      setBalanceResult({ error: err.response?.data?.error || 'Failed' });
    }
  };

  const handleSetRate = async (e) => {
    e.preventDefault();
    setRateResult(null);
    try {
      const res = await debugApi.setExchangeRate(rateId, newRate);
      setRateResult(res.data);
      loadData();
    } catch (err) {
      setRateResult({ error: err.response?.data?.error || 'Failed' });
    }
  };

  const handleConcurrentTest = async (e) => {
    e.preventDefault();
    setConcResult(null);
    setConcLoading(true);
    try {
      const sourceAccount = accounts.find(a => a.id === concSourceId);
      const res = await debugApi.concurrentTransfers({
        sourceAccountId: concSourceId,
        destAccountId: concDestId,
        amountPerTransfer: concAmount,
        numberOfTransfers: parseInt(concCount),
        username: sourceAccount ? sourceAccount.username : '',
      });
      setConcResult(res.data);
      loadData();
    } catch (err) {
      setConcResult({ error: err.response?.data?.error || 'Test failed' });
    } finally {
      setConcLoading(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">🔧 Debug Tools</h1>
        <p className="page-subtitle">Testing-only endpoints for demonstrating reconciliation and concurrency.</p>
      </div>

      <div className="debug-warning">
        ⚠️ These tools exist solely for assignment demonstration. They intentionally manipulate data
        to test ledger verification, reconciliation, and concurrency features.
      </div>

      <div className="grid-2">
        {/* Balance Manipulation */}
        <div className="card">
          <h2 className="card-title">💰 Set Account Balance</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '16px' }}>
            Directly modify a balance without creating a transaction.
            This creates a discrepancy detectable by reconciliation.
          </p>
          <form onSubmit={handleSetBalance}>
            <div className="form-group">
              <label className="form-label">Account</label>
              <select
                className="form-select"
                value={balanceAccountId}
                onChange={(e) => setBalanceAccountId(e.target.value)}
                required
              >
                <option value="">Select account</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} (current: {a.balance})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">New Balance</label>
              <input
                className="form-input"
                type="number"
                step="0.0001"
                value={newBalance}
                onChange={(e) => setNewBalance(e.target.value)}
                placeholder="99999.0000"
                required
              />
            </div>
            <button className="btn btn-warning" type="submit">Set Balance</button>
          </form>
          {balanceResult && (
            <div className={`result-box ${balanceResult.error ? 'alert-danger' : ''}`}>
              {JSON.stringify(balanceResult, null, 2)}
            </div>
          )}
        </div>

        {/* Exchange Rate Manipulation */}
        <div className="card">
          <h2 className="card-title">💱 Modify Exchange Rate</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '16px' }}>
            Change an exchange rate. Historical transactions keep their original rates.
          </p>
          <form onSubmit={handleSetRate}>
            <div className="form-group">
              <label className="form-label">Exchange Rate</label>
              <select
                className="form-select"
                value={rateId}
                onChange={(e) => setRateId(e.target.value)}
                required
              >
                <option value="">Select rate</option>
                {rates.map(r => (
                  <option key={r.id} value={r.id}>
                    {r.sourceCurrency}/{r.targetCurrency} (current: {r.rate})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">New Rate</label>
              <input
                className="form-input"
                type="number"
                step="0.00000001"
                value={newRate}
                onChange={(e) => setNewRate(e.target.value)}
                placeholder="0.80000000"
                required
              />
            </div>
            <button className="btn btn-warning" type="submit">Update Rate</button>
          </form>
          {rateResult && (
            <div className="result-box">
              {JSON.stringify(rateResult, null, 2)}
            </div>
          )}
        </div>
      </div>

      {/* Concurrent Transfers Test */}
      <div className="card mt-4">
        <h2 className="card-title">⚡ Concurrent Transfers Test</h2>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.85rem', marginBottom: '16px' }}>
          Spawns multiple threads that simultaneously transfer from the same source account.
          Verifies that pessimistic locking prevents lost updates.
        </p>
        <form onSubmit={handleConcurrentTest}>
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <div className="form-group">
              <label className="form-label">Source Account</label>
              <select
                className="form-select"
                value={concSourceId}
                onChange={(e) => setConcSourceId(e.target.value)}
                required
              >
                <option value="">Select</option>
                {accounts.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} ({a.balance})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Dest Account</label>
              <select
                className="form-select"
                value={concDestId}
                onChange={(e) => setConcDestId(e.target.value)}
                required
              >
                <option value="">Select</option>
                {accounts.filter(a => a.id !== concSourceId).map(a => (
                  <option key={a.id} value={a.id}>
                    {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} ({a.balance})
                  </option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">Amount Each</label>
              <input
                className="form-input"
                type="number"
                step="0.01"
                value={concAmount}
                onChange={(e) => setConcAmount(e.target.value)}
                style={{ width: '120px' }}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label"># Transfers</label>
              <input
                className="form-input"
                type="number"
                min="1"
                max="50"
                value={concCount}
                onChange={(e) => setConcCount(e.target.value)}
                style={{ width: '100px' }}
                required
              />
            </div>
          </div>
          <button className="btn btn-warning" type="submit" disabled={concLoading}>
            {concLoading ? <><span className="spinner"></span> Running...</> : '🚀 Run Concurrent Test'}
          </button>
        </form>

        {concResult && (
          <div style={{ marginTop: '16px' }}>
            {concResult.error ? (
              <div className="alert alert-danger">{concResult.error}</div>
            ) : (
              <>
                <div className="alert alert-info">
                  <strong>Results:</strong> {concResult.successes} succeeded, {concResult.failures} failed
                  out of {concResult.totalAttempted} attempted.
                  <br />
                  Source final balance: <strong>{concResult.sourceAccountFinalBalance}</strong> |
                  Dest final balance: <strong>{concResult.destAccountFinalBalance}</strong>
                </div>
                <div className="result-box">
                  {JSON.stringify(concResult.details, null, 2)}
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
