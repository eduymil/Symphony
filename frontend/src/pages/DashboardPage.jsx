import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { accountApi } from '../api/client';
import { useAuth } from '../context/AuthContext';

export default function DashboardPage() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const { user } = useAuth();

  useEffect(() => {
    loadAccounts();
  }, []);

  const loadAccounts = async () => {
    try {
      const res = await accountApi.getMyAccounts();
      setAccounts(res.data);
    } catch (err) {
      console.error('Failed to load accounts:', err);
    } finally {
      setLoading(false);
    }
  };

  const formatBalance = (balance) => {
    return parseFloat(balance).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 4,
    });
  };

  if (loading) {
    return (
      <div className="text-center mt-4">
        <span className="spinner"></span>
      </div>
    );
  }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Dashboard</h1>
        <p className="page-subtitle">Welcome back, {user?.username}. Here are your accounts.</p>
      </div>

      <div className="grid-2">
        {accounts.map((account) => (
          <div className="card" key={account.id}>
            <div className="account-label">
              {account.username.charAt(0).toUpperCase() + account.username.slice(1)} {account.currency}
            </div>
            <div className="account-balance">
              {formatBalance(account.balance)}
              <span className="account-currency">{account.currency}</span>
            </div>
            <div className="mt-4 flex gap-2">
              <Link to="/transfer" className="btn btn-primary btn-sm">
                Transfer
              </Link>
              <Link to="/transactions" className="btn btn-outline btn-sm">
                History
              </Link>
            </div>
          </div>
        ))}
      </div>

      {accounts.length === 0 && (
        <div className="card text-center">
          <p className="text-secondary">No accounts found.</p>
        </div>
      )}
    </div>
  );
}
