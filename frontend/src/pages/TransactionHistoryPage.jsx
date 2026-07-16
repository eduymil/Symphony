import { useState, useEffect } from 'react';
import { accountApi, transactionApi } from '../api/client';

export default function TransactionHistoryPage() {
  const [accounts, setAccounts] = useState([]);
  const [allAccounts, setAllAccounts] = useState([]);
  const [selectedAccount, setSelectedAccount] = useState('');
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [reverseResult, setReverseResult] = useState('');

  useEffect(() => {
    loadAccounts();
  }, []);

  const loadAccounts = async () => {
    try {
      const [myRes, allRes] = await Promise.all([
        accountApi.getMyAccounts(),
        accountApi.getAllAccounts(),
      ]);
      setAccounts(myRes.data);
      setAllAccounts(allRes.data);
      if (myRes.data.length > 0) {
        setSelectedAccount(myRes.data[0].id.toString());
        loadTransactions(myRes.data[0].id);
      }
    } catch (err) {
      console.error('Failed to load accounts:', err);
    }
  };

  const getAccountName = (id) => {
    const acc = allAccounts.find(a => a.id === id);
    return acc ? `${acc.username.charAt(0).toUpperCase() + acc.username.slice(1)} ${acc.currency}` : id?.toString().substring(0, 8) + '...';
  };

  const loadTransactions = async (accountId) => {
    setLoading(true);
    try {
      const res = await accountApi.getTransactionHistory(accountId);
      setTransactions(res.data);
    } catch (err) {
      console.error('Failed to load transactions:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleAccountChange = (e) => {
    const id = e.target.value;
    setSelectedAccount(id);
    setReverseResult('');
    if (id) loadTransactions(id);
  };

  const handleReverse = async (txnId) => {
    setReverseResult('');
    try {
      const res = await transactionApi.reverse(txnId);
      setReverseResult(`✅ Reversed successfully. Reversal ID: ${res.data.transactionId}`);
      loadTransactions(selectedAccount); // Refresh
    } catch (err) {
      setReverseResult(`❌ ${err.response?.data?.error || 'Reversal failed'}`);
    }
  };

  const formatDate = (dateStr) => {
    return new Date(dateStr).toLocaleString('en-US', {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Transaction History</h1>
        <p className="page-subtitle">View and reverse past transactions.</p>
      </div>

      <div className="card" style={{ marginBottom: '24px' }}>
        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label" htmlFor="historyAccount">Select Account</label>
          <select
            id="historyAccount"
            className="form-select"
            value={selectedAccount}
            onChange={handleAccountChange}
            style={{ maxWidth: '400px' }}
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.username.charAt(0).toUpperCase() + a.username.slice(1)} {a.currency} ({parseFloat(a.balance).toLocaleString()})
              </option>
            ))}
          </select>
        </div>
      </div>

      {reverseResult && (
        <div className={`alert ${reverseResult.startsWith('✅') ? 'alert-success' : 'alert-danger'}`}>
          {reverseResult}
        </div>
      )}

      {loading ? (
        <div className="text-center"><span className="spinner"></span></div>
      ) : transactions.length === 0 ? (
        <div className="card text-center">
          <p style={{ color: 'var(--text-muted)' }}>No transactions for this account.</p>
        </div>
      ) : (
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Type</th>
                <th>From → To</th>
                <th>Source Amount</th>
                <th>Dest Amount</th>
                <th>Rate</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {transactions.map((txn) => (
                <tr key={txn.id}>
                  <td className="mono">{formatDate(txn.createdAt)}</td>
                  <td>
                    <span className={`badge ${txn.type === 'REVERSAL' ? 'badge-warning' : 'badge-info'}`}>
                      {txn.type}
                    </span>
                  </td>
                  <td className="mono">{getAccountName(txn.sourceAccountId)} → {getAccountName(txn.destAccountId)}</td>
                  <td className="mono">{parseFloat(txn.sourceAmount).toFixed(4)} {txn.sourceCurrency}</td>
                  <td className="mono">{parseFloat(txn.destAmount).toFixed(4)} {txn.destCurrency}</td>
                  <td className="mono">{txn.exchangeRate}</td>
                  <td>
                    {txn.reversed ? (
                      <span className="badge badge-danger">Reversed</span>
                    ) : (
                      <span className="badge badge-success">Active</span>
                    )}
                  </td>
                  <td>
                    {!txn.reversed && txn.type !== 'REVERSAL' && (
                      <button
                        className="btn btn-danger btn-sm"
                        onClick={() => handleReverse(txn.id)}
                      >
                        Reverse
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
