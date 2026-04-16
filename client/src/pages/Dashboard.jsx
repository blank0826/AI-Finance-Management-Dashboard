import React, { useState, useEffect } from 'react';
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import { dashboardApi } from '../services/api';
import toast from 'react-hot-toast';
import { TrendingUp, TrendingDown, Wallet, Sparkles } from 'lucide-react';

const CATEGORY_COLORS = {
  FOOD_AND_DINING: '#f59e0b',
  SHOPPING: '#8b5cf6',
  UTILITIES_AND_BILLS: '#3b82f6',
  ENTERTAINMENT: '#ec4899',
  INVESTMENTS_AND_SAVINGS: '#10b981',
  TRAVEL_AND_TRANSPORT: '#06b6d4',
  HEALTH_AND_MEDICAL: '#ef4444',
  EDUCATION: '#6366f1',
  INCOME: '#22c55e',
  TRANSFER: '#94a3b8',
  OTHER: '#9ca3af',
};

const MONTHS = ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

function fmt(num) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(num);
}

function formatCategory(cat) {
  return cat.split('_').map(w => w[0] + w.slice(1).toLowerCase()).join(' ');
}

export default function Dashboard() {
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    dashboardApi.getSummary(year, month)
      .then(res => setData(res.data))
      .catch(() => toast.error('Failed to load dashboard data'))
      .finally(() => setLoading(false));
  }, [year, month]);

  const handleExport = async (type) => {
    const toastId = toast.loading(`Generating ${type.toUpperCase()} report...`);
    try {
      const res = type === 'excel'
        ? await dashboardApi.exportExcel(year, month)
        : await dashboardApi.exportPdf(year, month);
      const url = URL.createObjectURL(new Blob([res.data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = `finance-report-${year}-${month}.${type === 'excel' ? 'xlsx' : 'pdf'}`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('Report downloaded!', { id: toastId });
    } catch {
      toast.error('Export failed', { id: toastId });
    }
  };

  const pieData = data
    ? Object.entries(data.spendingByCategory).map(([k, v]) => ({
        name: formatCategory(k), value: Number(v), color: CATEGORY_COLORS[k] || '#9ca3af'
      }))
    : [];

  const barData = data?.monthlyTrend?.slice(-6).map(t => ({
    name: `${MONTHS[t.month]} ${t.year}`, amount: Number(t.amount)
  })) || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-500">Your financial overview</p>
        </div>
        <div className="flex items-center gap-3">
          <select value={month} onChange={e => setMonth(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
            {MONTHS.slice(1).map((m, i) => <option key={i+1} value={i+1}>{m}</option>)}
          </select>
          <select value={year} onChange={e => setYear(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
            {[2023,2024,2025,2026].map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <button onClick={() => handleExport('excel')}
            className="px-4 py-2 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 transition-colors">
            Excel
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="animate-spin w-10 h-10 border-4 border-indigo-500 border-t-transparent rounded-full" />
        </div>
      ) : !data ? null : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {[
              { label: 'Total Income', value: data.totalIncome, icon: TrendingUp, color: 'text-green-600', bg: 'bg-green-50' },
              { label: 'Total Spending', value: data.totalSpending, icon: TrendingDown, color: 'text-red-500', bg: 'bg-red-50' },
              { label: 'Net Savings', value: data.netSavings, icon: Wallet, color: 'text-indigo-600', bg: 'bg-indigo-50' },
            ].map(({ label, value, icon: Icon, color, bg }) => (
              <div key={label} className="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 flex items-center gap-4">
                <div className={`${bg} p-3 rounded-xl`}>
                  <Icon className={`w-6 h-6 ${color}`} />
                </div>
                <div>
                  <p className="text-sm text-gray-500">{label}</p>
                  <p className={`text-xl font-bold ${color}`}>{fmt(value)}</p>
                </div>
              </div>
            ))}
          </div>

          {/* AI Summary */}
          {data.aiSummary && (
            <div className="bg-gradient-to-r from-indigo-50 to-purple-50 border border-indigo-100 rounded-2xl p-5">
              <div className="flex items-center gap-2 mb-2">
                <Sparkles className="w-5 h-5 text-indigo-500" />
                <span className="font-semibold text-indigo-800">AI Financial Insight</span>
              </div>
              <p className="text-gray-700 leading-relaxed">{data.aiSummary}</p>
            </div>
          )}

          {/* Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Pie Chart */}
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-4">Spending by Category</h2>
              {pieData.length === 0
                ? <p className="text-gray-400 text-center py-12">No spending data for this period</p>
                : <ResponsiveContainer width="100%" height={280}>
                    <PieChart>
                      <Pie data={pieData} cx="50%" cy="50%" innerRadius={60} outerRadius={100}
                        dataKey="value" nameKey="name">
                        {pieData.map((entry, i) => (
                          <Cell key={i} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(v) => fmt(v)} />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
              }
            </div>

            {/* Bar Chart */}
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-4">Monthly Spending Trend</h2>
              {barData.length === 0
                ? <p className="text-gray-400 text-center py-12">No trend data yet</p>
                : <ResponsiveContainer width="100%" height={280}>
                    <BarChart data={barData}>
                      <XAxis dataKey="name" tick={{ fontSize: 12 }} />
                      <YAxis tickFormatter={v => `₹${(v/1000).toFixed(0)}k`} tick={{ fontSize: 12 }} />
                      <Tooltip formatter={(v) => fmt(v)} />
                      <Bar dataKey="amount" fill="#6366f1" radius={[6, 6, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
              }
            </div>
          </div>

          {/* Category Breakdown Table */}
          {pieData.length > 0 && (
            <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
              <h2 className="font-semibold text-gray-900 mb-4">Category Breakdown</h2>
              <div className="space-y-3">
                {pieData.map(({ name, value, color }) => {
                  const pct = data.totalSpending > 0
                    ? ((value / data.totalSpending) * 100).toFixed(1) : 0;
                  return (
                    <div key={name} className="flex items-center gap-3">
                      <div className="w-3 h-3 rounded-full shrink-0" style={{ background: color }} />
                      <span className="text-sm text-gray-700 w-40 shrink-0">{name}</span>
                      <div className="flex-1 bg-gray-100 rounded-full h-2">
                        <div className="h-2 rounded-full" style={{ width: `${pct}%`, background: color }} />
                      </div>
                      <span className="text-sm font-medium text-gray-900 w-24 text-right">{fmt(value)}</span>
                      <span className="text-xs text-gray-400 w-10 text-right">{pct}%</span>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
