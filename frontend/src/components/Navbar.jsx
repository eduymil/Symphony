import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <div className="navbar-inner">
        <NavLink to="/" className="navbar-brand">
          ⚡ Ledger
        </NavLink>
        
        <div className="navbar-links">
          <NavLink to="/" className={({ isActive }) => isActive ? 'active' : ''}>
            Dashboard
          </NavLink>
          <NavLink to="/transfer" className={({ isActive }) => isActive ? 'active' : ''}>
            Transfer
          </NavLink>
          <NavLink to="/transactions" className={({ isActive }) => isActive ? 'active' : ''}>
            History
          </NavLink>
          <NavLink to="/exchange-rates" className={({ isActive }) => isActive ? 'active' : ''}>
            Rates
          </NavLink>
          <NavLink to="/reconciliation" className={({ isActive }) => isActive ? 'active' : ''}>
            Reconciliation
          </NavLink>
          <NavLink to="/debug" className={({ isActive }) => isActive ? 'active' : ''}>
            🔧 Debug
          </NavLink>
        </div>

        <div className="navbar-user">
          <span className="navbar-username">👤 {user?.username}</span>
          <button className="btn btn-outline btn-sm" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </div>
    </nav>
  );
}
