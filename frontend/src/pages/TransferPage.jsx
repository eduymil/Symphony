import { useState, useEffect } from 'react';
import { accountApi, transactionApi, exchangeRateApi } from '../api/client';

export default function TransferPage() {
  const [myAccounts, setMyAccounts] = useState([]);
  const [allAccounts, setAllAccounts] = useState([]);
  const [exchangeRates, setExchangeRates] = useState([]);
  const [sourceAccountId, setSourceAccountId] = useState('');
  const [destAccountId, setDestAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      const [myRes, allRes, ratesRes] = await Promise.all([
        accountApi.getMyAccounts(),
        accountApi.getAllAccounts(),
        exchangeRateApi.getAll(),
      ]);
      setMyAccounts(myRes.data);
      setAllAccounts(allRes.data);
      setExchangeRates(ratesRes.data);
    } catch (err) {
      console.error('Failed to load data:', err);
    }
  };

  const getApplicableRate = () => {
    if (!sourceAccountId || !destAccountId) return null;
    const source = myAccounts.find(a => a.id === sourceAccountId);
    const dest = allAccounts.find(a => a.id === destAccountId);
    if (!source || !dest) return null;
    if (source.currency === dest.currency) return { rate: '1.00000000', same: true };
    const rate = exchangeRates.find(
      r => r.sourceCurrency === source.currency && r.targetCurrency === dest.currency
    );
    return rate ? { ...rate, same: false } : null;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setResult(null);
    setLoading(true);

    const idempotencyKey = `txn-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    try {
      const res = await transactionApi.transfer(
        sourceAccountId,
        destAccountId,
        amount,
        idempotencyKey
      );
      setResult(res.data);
      setAmount('');
      loadData(); // Refresh balances
    } catch (err) {
      setError(err.response?.data?.error || 'Transfer failed');
    } finally {
      setLoading(false);
    }
  };

  const applicableRate = getApplicableRate();

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Transfer Money</h1>
        <p className="page-subtitle">Send money between accounts with automatic currency conversion.</p>
      </div>

      <div className="grid-2">
        <div className="card">
          <h2 className="card-title" style={{ marginBottom: '20px' }}>New Transfer</h2>

          {error && <div className="alert alert-danger">{error}</div>}
          {result && (
            <div className="alert alert-success">
              ✅ Transfer successful! ID: {result.transactionId?.substring(0, 8)}...
              <br />
              {result.sourceAmount} {result.sourceCurrency} → {result.destAmount} {result.destCurrency}
              {result.exchangeRate !== '1.00000000' && ` (Rate: ${result.exchangeRate})`}
            </div>
          )}

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label" htmlFor="sourceAccount">From Account (yours)</label>
              <select
                id="sourceAccount"
                className="form-select"
                value={sourceAccountId}
                onChange={(e) => setSourceAccountId(e.target.value)}
                required
              >
                <option value="">Select source account</option>
                {myAccounts.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} ({parseFloat(a.balance).toLocaleString()})
                  </option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="destAccount">To Account</label>
              <select
                id="destAccount"
                className="form-select"
                value={destAccountId}
                onChange={(e) => setDestAccountId(e.target.value)}
                required
              >
                <option value="">Select destination account</option>
                {allAccounts
                  .filter((a) => a.id !== sourceAccountId)
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} ({parseFloat(a.balance).toLocaleString()})
                    </option>
                  ))}
              </select>
            </div>

            {applicableRate && !applicableRate.same && (
              <div className="alert alert-info">
                💱 Exchange rate: 1 {myAccounts.find(a => a.id === sourceAccountId)?.currency} = {applicableRate.rate}{' '}
                {allAccounts.find(a => a.id === destAccountId)?.currency}
                {amount && (
                  <span>
                    {' '}→ You'll send ≈ {(parseFloat(amount) * parseFloat(applicableRate.rate)).toFixed(4)}
                  </span>
                )}
              </div>
            )}

            <div className="form-group">
              <label className="form-label" htmlFor="amount">Amount (in source currency)</label>
              <input
                id="amount"
                className="form-input"
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="100.00"
                required
              />
            </div>

            <button
              className="btn btn-primary"
              type="submit"
              disabled={loading}
              style={{ width: '100%' }}
            >
              {loading ? <><span className="spinner"></span> Processing...</> : '💸 Send Transfer'}
            </button>
          </form>
        </div>

        <div>
          <div className="card">
            <h2 className="card-title">Your Accounts</h2>
            {myAccounts.map((a) => (
              <div key={a.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--border)' }}>
                <div className="account-label">{a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency}</div>
                <div style={{ fontSize: '1.2rem', fontWeight: '600' }}>
                  {parseFloat(a.balance).toLocaleString('en-US', { minimumFractionDigits: 2 })} {a.currency}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
