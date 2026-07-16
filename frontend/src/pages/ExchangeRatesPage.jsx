import { useState, useEffect } from 'react';
import { exchangeRateApi } from '../api/client';

export default function ExchangeRatesPage() {
  const [rates, setRates] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadRates();
  }, []);

  const loadRates = async () => {
    try {
      const res = await exchangeRateApi.getAll();
      setRates(res.data);
    } catch (err) {
      console.error('Failed to load rates:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleString('en-US', {
      month: 'short', day: 'numeric', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  };

  if (loading) {
    return <div className="text-center mt-4"><span className="spinner"></span></div>;
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Exchange Rates</h1>
        <p className="page-subtitle">
          Current exchange rates used for cross-currency transfers. 
          Rates can be modified via Debug tools for testing.
        </p>
      </div>

      <div className="grid-3">
        {rates.map((rate) => (
          <div className="card" key={rate.id}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
              <span style={{ fontSize: '1.5rem' }}>💱</span>
              <div>
                <div style={{ fontSize: '1rem', fontWeight: '600' }}>
                  {rate.sourceCurrency} → {rate.targetCurrency}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
                  ID: {rate.id}
                </div>
              </div>
            </div>
            <div style={{ fontSize: '1.75rem', fontWeight: '700', color: 'var(--accent)' }}>
              {parseFloat(rate.rate).toFixed(4)}
            </div>
            <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '8px' }}>
              1 {rate.sourceCurrency} = {parseFloat(rate.rate).toFixed(4)} {rate.targetCurrency}
            </div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginTop: '4px' }}>
              Updated: {formatDate(rate.updatedAt)}
            </div>
          </div>
        ))}
      </div>

      <div className="card mt-4">
        <h3 className="card-title">Conversion Calculator</h3>
        <ConversionCalculator rates={rates} />
      </div>
    </div>
  );
}

function ConversionCalculator({ rates }) {
  const [amount, setAmount] = useState('100');
  const [from, setFrom] = useState('SGD');
  const [to, setTo] = useState('USD');

  const currencies = [...new Set(rates.flatMap(r => [r.sourceCurrency, r.targetCurrency]))];
  const rate = rates.find(r => r.sourceCurrency === from && r.targetCurrency === to);
  const converted = rate ? (parseFloat(amount) * parseFloat(rate.rate)).toFixed(4) : null;

  return (
    <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-end', flexWrap: 'wrap' }}>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label className="form-label">Amount</label>
        <input
          className="form-input"
          type="number"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          style={{ width: '150px' }}
        />
      </div>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label className="form-label">From</label>
        <select className="form-select" value={from} onChange={(e) => setFrom(e.target.value)} style={{ width: '100px' }}>
          {currencies.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      <span style={{ padding: '10px', color: 'var(--text-muted)' }}>→</span>
      <div className="form-group" style={{ marginBottom: 0 }}>
        <label className="form-label">To</label>
        <select className="form-select" value={to} onChange={(e) => setTo(e.target.value)} style={{ width: '100px' }}>
          {currencies.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
      </div>
      <div style={{ padding: '10px', fontSize: '1.1rem', fontWeight: '600' }}>
        {converted !== null ? (
          <span>= <span style={{ color: 'var(--accent)' }}>{converted} {to}</span></span>
        ) : (
          <span style={{ color: 'var(--text-muted)' }}>No rate available</span>
        )}
      </div>
    </div>
  );
}
