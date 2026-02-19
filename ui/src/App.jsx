import React, { useState, useEffect, useMemo, useRef, memo } from 'react';
import {
  Tag, Map as MapIcon, History, ArrowRight,
  Activity, Database, LayoutDashboard,
  Search, Clock, MapPin, Menu, Smartphone, Layers,
  Globe, Watch, ChevronRight, ChevronDown, RefreshCw, AlertCircle,
  Zap, Info, User, Cpu, HardDrive, Filter,
  Calendar, X, Monitor, Radio, Settings, Box, Eye, EyeOff,
  TrendingUp, Github, Linkedin, Mail
} from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

// --- CONFIGURATION ---
const API_BASE = "/beam/api";
const REFRESH_INTERVAL = 5000;

function App() {
  const [activeTab, setActiveTab] = useState('dashboard');
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  // Data States
  const [assets, setAssets] = useState([]);
  const [history, setHistory] = useState([]);
  const [scanners, setScanners] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [lastUpdated, setLastUpdated] = useState(new Date());

  const fetchData = async () => {
    try {
      setLoading(true);
      const [assetsRes, historyRes, scannersRes] = await Promise.all([
        fetch(`${API_BASE}/assets/current`),
        fetch(`${API_BASE}/assets/history?limit=50`),
        fetch(`${API_BASE}/scanners`)
      ]);

      if (!assetsRes.ok || !historyRes.ok || !scannersRes.ok) {
        throw new Error("Failed to fetch data from API");
      }

      const assetsData = await assetsRes.json();
      const historyData = await historyRes.json();
      const scannersData = await scannersRes.json();

      setAssets(assetsData);
      setHistory(historyData);
      setScanners(scannersData);
      setError(null);
      setLastUpdated(new Date());
    } catch (err) {
      console.error("Error fetching data:", err);
      setError("Could not connect to API. Please check if the backend is running.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, REFRESH_INTERVAL);
    return () => clearInterval(interval);
  }, []);

  // --- VIEWS ---

  const DashboardView = () => {
    const totalAssets = assets.length;
    const activeAssets = assets.filter(a => new Date(a.last_seen) > new Date(Date.now() - 3600000)).length; // seen in last hour
    const totalScanners = scanners.length;

    // Group assets by zone
    const zoneCounts = assets.reduce((acc, curr) => {
      const zone = curr.zone || 'Unknown';
      acc[zone] = (acc[zone] || 0) + 1;
      return acc;
    }, {});

    return (
      <div className="space-y-6">
        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">Total Assets</p>
                <p className="text-2xl font-bold text-gray-900">{totalAssets}</p>
              </div>
              <div className="bg-blue-100 p-3 rounded-full">
                <Box className="h-6 w-6 text-blue-600" />
              </div>
            </div>
            <p className="mt-2 text-sm text-green-600 flex items-center gap-1">
              <Activity size={14} />
              {activeAssets} active recently
            </p>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">Active Scanners</p>
                <p className="text-2xl font-bold text-gray-900">{totalScanners}</p>
              </div>
              <div className="bg-green-100 p-3 rounded-full">
                <Radio className="h-6 w-6 text-green-600" />
              </div>
            </div>
            <p className="mt-2 text-sm text-gray-500">Online</p>
          </div>

          <div className="bg-white p-6 rounded-lg shadow-sm border border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-gray-500">System Status</p>
                <p className="text-2xl font-bold text-green-600">Healthy</p>
              </div>
              <div className="bg-purple-100 p-3 rounded-full">
                <Activity className="h-6 w-6 text-purple-600" />
              </div>
            </div>
            <p className="mt-2 text-sm text-gray-500">Last updated: {lastUpdated.toLocaleTimeString()}</p>
          </div>
        </div>

        {/* Zone Distribution */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <h3 className="text-lg font-medium text-gray-900 mb-4">Asset Distribution by Zone</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {Object.entries(zoneCounts).map(([zone, count]) => (
              <div key={zone} className="bg-gray-50 p-4 rounded-lg border border-gray-100">
                <p className="text-sm text-gray-500 font-medium">{zone}</p>
                <p className="text-xl font-bold text-gray-900 mt-1">{count}</p>
              </div>
            ))}
            {Object.keys(zoneCounts).length === 0 && (
              <p className="text-gray-500 text-sm col-span-4 text-center py-4">No assets found in any zone.</p>
            )}
          </div>
        </div>

        {/* Recent History Preview */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-200 flex justify-between items-center">
            <h3 className="text-lg font-medium text-gray-900">Recent Activity</h3>
            <button onClick={() => setActiveTab('history')} className="text-sm text-blue-600 hover:text-blue-800 font-medium">View All</button>
          </div>
          <div className="divide-y divide-gray-200">
            {history.slice(0, 5).map((log) => (
              <div key={log.id} className="px-6 py-3 flex items-center justify-between hover:bg-gray-50">
                <div className="flex items-center gap-3">
                  <Clock size={16} className="text-gray-400" />
                  <span className="text-sm text-gray-900">{new Date(log.timestamp).toLocaleString()}</span>
                </div>
                <div className="text-sm text-gray-600">
                  <span className="font-mono text-gray-500">{log.mac}</span> moved to <span className="font-semibold text-gray-900">{log.to_zone}</span>
                </div>
              </div>
            ))}
            {history.length === 0 && (
              <div className="px-6 py-8 text-center text-gray-500">No recent history available.</div>
            )}
          </div>
        </div>
      </div>
    );
  };

  const AssetsView = () => (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-200">
        <h3 className="text-lg font-medium text-gray-900">All Assets</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Asset Name</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">MAC Address</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Current Zone</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Last Seen</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Signal (RSSI)</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {assets.map((asset) => (
              <tr key={asset.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{asset.name || 'Unnamed'}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">{asset.mac}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                  <span className="px-2 py-1 inline-flex text-xs leading-5 font-semibold rounded-full bg-blue-100 text-blue-800">
                    {asset.zone || 'Unknown'}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{new Date(asset.last_seen).toLocaleString()}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{asset.rssi} dBm</td>
              </tr>
            ))}
            {assets.length === 0 && (
              <tr>
                <td colSpan="5" className="px-6 py-8 text-center text-gray-500">No assets found in the database.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );

  const HistoryView = () => (
    <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-200">
        <h3 className="text-lg font-medium text-gray-900">Movement History</h3>
      </div>
      <div className="overflow-x-auto">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Time</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">MAC Address</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">From Zone</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">To Zone</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">RSSI</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {history.map((log) => (
              <tr key={log.id} className="hover:bg-gray-50">
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{new Date(log.timestamp).toLocaleString()}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500 font-mono">{log.mac}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{log.from_zone || '-'}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 font-medium">{log.to_zone || 'Unknown'}</td>
                <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{log.rssi}</td>
              </tr>
            ))}
            {history.length === 0 && (
              <tr>
                <td colSpan="5" className="px-6 py-8 text-center text-gray-500">No movement history found.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-50 text-gray-900 font-sans">
      {/* Navigation Bar */}
      <nav className="bg-white shadow-sm border-b border-gray-200 sticky top-0 z-30">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center">
              <div className="flex-shrink-0 flex items-center gap-2">
                <div className="bg-blue-600 p-2 rounded-lg">
                  <MapIcon className="h-6 w-6 text-white" />
                </div>
                <span className="font-bold text-xl tracking-tight text-gray-900">ZoneTrack</span>
              </div>

              {/* Desktop Menu */}
              <div className="hidden md:ml-10 md:flex md:space-x-1">
                <button
                  onClick={() => setActiveTab('dashboard')}
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${activeTab === 'dashboard'
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  <div className="flex items-center gap-2">
                    <LayoutDashboard size={18} />
                    <span>Dashboard</span>
                  </div>
                </button>
                <button
                  onClick={() => setActiveTab('assets')}
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${activeTab === 'assets'
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  <div className="flex items-center gap-2">
                    <Box size={18} />
                    <span>Assets</span>
                  </div>
                </button>
                <button
                  onClick={() => setActiveTab('history')}
                  className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${activeTab === 'history'
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  <div className="flex items-center gap-2">
                    <History size={18} />
                    <span>History</span>
                  </div>
                </button>
              </div>
            </div>

            <div className="flex items-center gap-4">
              <div className="hidden md:flex items-center gap-2 text-sm text-gray-500 bg-gray-100 px-3 py-1 rounded-full">
                <div className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></div>
                System Online
              </div>
            </div>

            {/* Mobile menu button */}
            <div className="flex items-center md:hidden">
              <button
                onClick={() => setIsMenuOpen(!isMenuOpen)}
                className="inline-flex items-center justify-center p-2 rounded-md text-gray-400 hover:text-gray-500 hover:bg-gray-100 transition-colors"
                aria-expanded="false"
              >
                <span className="sr-only">Open main menu</span>
                <Menu className="block h-6 w-6" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>

        {/* Mobile menu panel */}
        <AnimatePresence>
          {isMenuOpen && (
            <motion.div
              initial={{ height: 0, opacity: 0 }}
              animate={{ height: 'auto', opacity: 1 }}
              exit={{ height: 0, opacity: 0 }}
              className="md:hidden overflow-hidden bg-white border-t border-gray-200"
            >
              <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
                <button
                  onClick={() => { setActiveTab('dashboard'); setIsMenuOpen(false); }}
                  className={`block w-full text-left px-3 py-2 rounded-md text-base font-medium ${activeTab === 'dashboard' ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  Dashboard
                </button>
                <button
                  onClick={() => { setActiveTab('assets'); setIsMenuOpen(false); }}
                  className={`block w-full text-left px-3 py-2 rounded-md text-base font-medium ${activeTab === 'assets' ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  Assets
                </button>
                <button
                  onClick={() => { setActiveTab('history'); setIsMenuOpen(false); }}
                  className={`block w-full text-left px-3 py-2 rounded-md text-base font-medium ${activeTab === 'history' ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-50 hover:text-gray-900'
                    }`}
                >
                  History
                </button>
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </nav>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {activeTab === 'dashboard' && <DashboardView />}
        {activeTab === 'assets' && <AssetsView />}
        {activeTab === 'history' && <HistoryView />}
      </main>
    </div>
  );
}

export default App;
