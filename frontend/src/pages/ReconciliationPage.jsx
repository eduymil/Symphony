import { useState } from 'react';
import { reconciliationApi } from '../api/client';

export default function ReconciliationPage() {
  const [verificationResult, setVerificationResult] = useState(null);
  const [reconciliationResult, setReconciliationResult] = useState(null);
  const [loadingVerify, setLoadingVerify] = useState(false);
  const [loadingRecon, setLoadingRecon] = useState(false);

  const handleVerifyLedger = async () => {
    setLoadingVerify(true);
    setVerificationResult(null);
    try {
      const res = await reconciliationApi.verifyLedger();
      setVerificationResult(res.data);
    } catch (err) {
      setVerificationResult({ error: err.response?.data?.error || 'Verification failed' });
    } finally {
      setLoadingVerify(false);
    }
  };

  const handleReconciliation = async () => {
    setLoadingRecon(true);
    setReconciliationResult(null);
    try {
      const res = await reconciliationApi.runReconciliation();
      setReconciliationResult(res.data);
    } catch (err) {
      setReconciliationResult({ error: err.response?.data?.error || 'Reconciliation failed' });
    } finally {
      setLoadingRecon(false);
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Reconciliation</h1>
        <p className="page-subtitle">
          Verify ledger integrity and run month-end reconciliation.
          Use Debug tools to create intentional discrepancies for testing.
        </p>
      </div>

      <div className="grid-2">
        {/* Ledger Verification */}
        <div className="section">
          <div className="card">
            <h2 className="card-title">🔍 Ledger Verification</h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '16px' }}>
              Recalculates every account balance from transaction history and compares
              with stored balances. Reports any discrepancies.
            </p>
            <button
              className="btn btn-primary"
              onClick={handleVerifyLedger}
              disabled={loadingVerify}
            >
              {loadingVerify ? <><span className="spinner"></span> Verifying...</> : 'Run Verification'}
            </button>

            {verificationResult && !verificationResult.error && (
              <div style={{ marginTop: '16px' }}>
                <div className={`alert ${verificationResult.consistent ? 'alert-success' : 'alert-danger'}`}>
                  {verificationResult.consistent
                    ? `✅ Ledger is consistent. ${verificationResult.accountsChecked} accounts verified.`
                    : `❌ Discrepancies found in ${verificationResult.discrepancies.length} account(s)!`}
                </div>

                {verificationResult.discrepancies?.length > 0 && (
                  <div className="table-wrapper">
                    <table>
                      <thead>
                        <tr>
                          <th>Account</th>
                          <th>User</th>
                          <th>Currency</th>
                          <th>Stored</th>
                          <th>Recalculated</th>
                          <th>Difference</th>
                        </tr>
                      </thead>
                      <tbody>
                        {verificationResult.discrepancies.map((d, i) => (
                          <tr key={i}>
                            <td className="mono">#{d.accountId}</td>
                            <td>{d.username}</td>
                            <td>{d.currency}</td>
                            <td className="mono">{d.storedBalance}</td>
                            <td className="mono">{d.recalculatedBalance}</td>
                            <td className="mono" style={{ color: 'var(--danger)' }}>{d.difference}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}

            {verificationResult?.error && (
              <div className="alert alert-danger mt-2">{verificationResult.error}</div>
            )}
          </div>
        </div>

        {/* Month-end Reconciliation */}
        <div className="section">
          <div className="card">
            <h2 className="card-title">📊 Month-End Reconciliation</h2>
            <p style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', marginBottom: '16px' }}>
              Per-user reconciliation: recalculates each user's account balances from
              transaction history and compares with stored balances.
            </p>
            <button
              className="btn btn-primary"
              onClick={handleReconciliation}
              disabled={loadingRecon}
            >
              {loadingRecon ? <><span className="spinner"></span> Running...</> : 'Run Reconciliation'}
            </button>

            {reconciliationResult && !reconciliationResult.error && (
              <div style={{ marginTop: '16px' }}>
                {reconciliationResult.map((userResult, i) => (
                  <div key={i} style={{ marginBottom: '16px' }}>
                    <div className={`alert ${userResult.consistent ? 'alert-success' : 'alert-danger'}`}>
                      <strong>{userResult.username}</strong> — {userResult.accountsChecked} account(s) —{' '}
                      {userResult.consistent ? '✅ Consistent' : `❌ ${userResult.discrepancies.length} discrepancy(ies)`}
                    </div>

                    {userResult.discrepancies?.length > 0 && (
                      <div className="table-wrapper" style={{ marginTop: '8px' }}>
                        <table>
                          <thead>
                            <tr>
                              <th>Account</th>
                              <th>Currency</th>
                              <th>Stored</th>
                              <th>Recalculated</th>
                              <th>Difference</th>
                            </tr>
                          </thead>
                          <tbody>
                            {userResult.discrepancies.map((d, j) => (
                              <tr key={j}>
                                <td className="mono">#{d.accountId}</td>
                                <td>{d.currency}</td>
                                <td className="mono">{d.storedBalance}</td>
                                <td className="mono">{d.recalculatedBalance}</td>
                                <td className="mono" style={{ color: 'var(--danger)' }}>{d.difference}</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}

            {reconciliationResult?.error && (
              <div className="alert alert-danger mt-2">{reconciliationResult.error}</div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
