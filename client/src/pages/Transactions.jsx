import React, { useState, useEffect } from 'react';
import { transactionApi } from '../services/api';
import toast from 'react-hot-toast';
import { Edit2, Check, X, Trash2, Plus } from 'lucide-react';

const CATEGORIES = [
  'FOOD_AND_DINING','SHOPPING','UTILITIES_AND_BILLS','ENTERTAINMENT',
  'INVESTMENTS_AND_SAVINGS','TRAVEL_AND_TRANSPORT','HEALTH_AND_MEDICAL',
  'EDUCATION','INCOME','TRANSFER','OTHER'
];

const MONTHS = ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

function formatCategory(cat) {
  return cat?.split('_').map(w => w[0] + w.slice(1).toLowerCase()).join(' ') || '';
}

const CAT_COLORS = {
  FOOD_AND_DINING:        'bg-amber-100 text-amber-800',
  SHOPPING:               'bg-purple-100 text-purple-800',
  UTILITIES_AND_BILLS:    'bg-blue-100 text-blue-800',
  ENTERTAINMENT:          'bg-pink-100 text-pink-800',
  INVESTMENTS_AND_SAVINGS:'bg-green-100 text-green-800',
  TRAVEL_AND_TRANSPORT:   'bg-cyan-100 text-cyan-800',
  HEALTH_AND_MEDICAL:     'bg-red-100 text-red-800',
  EDUCATION:              'bg-indigo-100 text-indigo-800',
  INCOME:                 'bg-emerald-100 text-emerald-800',
  TRANSFER:               'bg-slate-100 text-slate-700',
  OTHER:                  'bg-gray-100 text-gray-700',
};

const EMPTY_FORM = {
  description: '',
  amount: '',
  date: new Date().toISOString().split('T')[0],
  type: 'DEBIT',
  category: 'OTHER',
  accountName: '',
};

export default function Transactions() {
  const now = new Date();
  const [year, setYear]               = useState(now.getFullYear());
  const [month, setMonth]             = useState(now.getMonth() + 1);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading]         = useState(true);
  const [editing, setEditing]         = useState(null);
  const [editCat, setEditCat]         = useState('');
  const [showModal, setShowModal]     = useState(false);
  const [form, setForm]               = useState(EMPTY_FORM);
  const [saving, setSaving]           = useState(false);

  useEffect(() => {
    setLoading(true);
    transactionApi.getAll(year, month)
      .then(res => setTransactions(res.data))
      .catch(() => toast.error('Failed to load transactions'))
      .finally(() => setLoading(false));
  }, [year, month]);

  // ── Inline category edit ──────────────────────────────────────────────────
  const startEdit  = (tx) => { setEditing(tx.id); setEditCat(tx.category); };
  const cancelEdit = ()   => setEditing(null);

  const saveEdit = async (id) => {
    try {
      const res = await transactionApi.updateCategory(id, editCat);
      setTransactions(prev => prev.map(t => t.id === id ? res.data : t));
      toast.success('Category updated');
      setEditing(null);
    } catch { toast.error('Failed to update category'); }
  };

  // ── Delete ────────────────────────────────────────────────────────────────
  const deleteTransaction = async (id) => {
    if (!window.confirm('Delete this transaction?')) return;
    try {
      await transactionApi.delete(id);
      setTransactions(prev => prev.filter(t => t.id !== id));
      toast.success('Transaction deleted');
    } catch { toast.error('Failed to delete'); }
  };

  // ── Add transaction ───────────────────────────────────────────────────────
  const openModal  = () => { setForm(EMPTY_FORM); setShowModal(true); };
  const closeModal = () => { setShowModal(false); setForm(EMPTY_FORM); };

  const handleFormChange = (e) => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async () => {
    if (!form.description.trim()) { toast.error('Description is required'); return; }
    if (!form.amount || isNaN(form.amount) || Number(form.amount) <= 0) {
      toast.error('Enter a valid amount'); return;
    }
    if (!form.date) { toast.error('Date is required'); return; }

    setSaving(true);
    try {
      const res = await transactionApi.add({
        description: form.description.trim(),
        amount:      parseFloat(form.amount),
        date:        form.date,
        type:        form.type,
        category:    form.category,
        accountName: form.accountName.trim() || null,
      });
      setTransactions(prev => [res.data, ...prev]);
      toast.success('Transaction added');
      closeModal();
    } catch (err) {
      toast.error(err.response?.data || 'Failed to add transaction');
    } finally {
      setSaving(false);
    }
  };

  // ── Totals ────────────────────────────────────────────────────────────────
  const totalDebit  = transactions
    .filter(t => t.type === 'DEBIT')
    .reduce((s, t) => s + Number(t.amount), 0);
  const totalCredit = transactions
    .filter(t => t.type === 'CREDIT')
    .reduce((s, t) => s + Number(t.amount), 0);

  const fmt = (n) =>
    new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR',
      maximumFractionDigits: 0 }).format(n);

  return (
    <div className="space-y-6">

      {/* ── Header ── */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Transactions</h1>
          <p className="text-gray-500 text-sm mt-0.5">
            {transactions.length} transactions
            {transactions.length > 0 && ` · Out ${fmt(totalDebit)} · In ${fmt(totalCredit)}`}
          </p>
        </div>

        <div className="flex items-center gap-3 flex-wrap">
          <select value={month} onChange={e => setMonth(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
            {MONTHS.slice(1).map((m, i) => <option key={i+1} value={i+1}>{m}</option>)}
          </select>
          <select value={year} onChange={e => setYear(Number(e.target.value))}
            className="px-3 py-2 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
            {[2023,2024,2025,2026].map(y => <option key={y} value={y}>{y}</option>)}
          </select>
          <button onClick={openModal}
            className="flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors">
            <Plus className="w-4 h-4" /> Add Transaction
          </button>
        </div>
      </div>

      {/* ── Transactions table ── */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center h-48">
            <div className="animate-spin w-8 h-8 border-4 border-indigo-500 border-t-transparent rounded-full" />
          </div>
        ) : transactions.length === 0 ? (
          <div className="text-center py-16 text-gray-400">
            <p className="text-lg">No transactions for this period.</p>
            <p className="text-sm mt-1">Upload a statement or add one manually.</p>
            <button onClick={openModal}
              className="mt-4 flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors mx-auto">
              <Plus className="w-4 h-4" /> Add Transaction
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-100">
                <tr>
                  {['Date','Description','Account','Amount','Type','Category','Actions']
                    .map(h => (
                    <th key={h} className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {transactions.map(tx => (
                  <tr key={tx.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {new Date(tx.date).toLocaleDateString('en-IN',
                        { day:'2-digit', month:'short' })}
                    </td>
                    <td className="px-4 py-3 text-gray-900 max-w-xs truncate">
                      {tx.description}
                      {tx.manuallyEdited && (
                        <span className="ml-2 text-xs text-indigo-400">edited</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {tx.accountName || '—'}
                    </td>
                    <td className={`px-4 py-3 font-semibold whitespace-nowrap
                      ${tx.type === 'CREDIT' ? 'text-green-600' : 'text-gray-900'}`}>
                      {tx.type === 'CREDIT' ? '+' : '-'}₹{Number(tx.amount).toLocaleString('en-IN')}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium
                        ${tx.type === 'CREDIT'
                          ? 'bg-green-100 text-green-700'
                          : 'bg-gray-100 text-gray-700'}`}>
                        {tx.type}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      {editing === tx.id ? (
                        <select value={editCat} onChange={e => setEditCat(e.target.value)}
                          className="px-2 py-1 border border-indigo-400 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-indigo-500">
                          {CATEGORIES.map(c => (
                            <option key={c} value={c}>{formatCategory(c)}</option>
                          ))}
                        </select>
                      ) : (
                        <span className={`px-2 py-0.5 rounded-full text-xs font-medium
                          ${CAT_COLORS[tx.category] || CAT_COLORS.OTHER}`}>
                          {formatCategory(tx.category)}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-1">
                        {editing === tx.id ? (
                          <>
                            <button onClick={() => saveEdit(tx.id)}
                              className="p-1.5 text-green-600 hover:bg-green-50 rounded-lg transition-colors">
                              <Check className="w-4 h-4" />
                            </button>
                            <button onClick={cancelEdit}
                              className="p-1.5 text-gray-400 hover:bg-gray-100 rounded-lg transition-colors">
                              <X className="w-4 h-4" />
                            </button>
                          </>
                        ) : (
                          <>
                            <button onClick={() => startEdit(tx)}
                              className="p-1.5 text-indigo-500 hover:bg-indigo-50 rounded-lg transition-colors">
                              <Edit2 className="w-4 h-4" />
                            </button>
                            <button onClick={() => deleteTransaction(tx.id)}
                              className="p-1.5 text-red-400 hover:bg-red-50 rounded-lg transition-colors">
                              <Trash2 className="w-4 h-4" />
                            </button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Add Transaction Modal ── */}
      {showModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          style={{ background: 'rgba(0,0,0,0.4)' }}
          onClick={(e) => { if (e.target === e.currentTarget) closeModal(); }}
        >
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 space-y-5">

            {/* Modal header */}
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold text-gray-900">Add Transaction</h2>
              <button onClick={closeModal}
                className="p-1.5 text-gray-400 hover:bg-gray-100 rounded-lg transition-colors">
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Description */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Description <span className="text-red-500">*</span>
              </label>
              <input
                type="text" name="description" value={form.description}
                onChange={handleFormChange} autoFocus
                placeholder="e.g. Zomato order, Salary credit..."
                className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>

            {/* Amount + Type row */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Amount (₹) <span className="text-red-500">*</span>
                </label>
                <input
                  type="number" name="amount" value={form.amount}
                  onChange={handleFormChange} min="0" step="0.01"
                  placeholder="0.00"
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select name="type" value={form.type} onChange={handleFormChange}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
                  <option value="DEBIT">Debit (expense)</option>
                  <option value="CREDIT">Credit (income)</option>
                </select>
              </div>
            </div>

            {/* Date + Account row */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Date <span className="text-red-500">*</span>
                </label>
                <input
                  type="date" name="date" value={form.date}
                  onChange={handleFormChange}
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Account
                </label>
                <input
                  type="text" name="accountName" value={form.accountName}
                  onChange={handleFormChange}
                  placeholder="e.g. HDFC Savings"
                  className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>
            </div>

            {/* Category */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
              <select name="category" value={form.category} onChange={handleFormChange}
                className="w-full px-3 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
                {CATEGORIES.map(c => (
                  <option key={c} value={c}>{formatCategory(c)}</option>
                ))}
              </select>
            </div>

            {/* Amount preview */}
            {form.amount > 0 && (
              <div className={`px-4 py-2.5 rounded-xl text-sm font-medium
                ${form.type === 'CREDIT'
                  ? 'bg-green-50 text-green-700'
                  : 'bg-red-50 text-red-700'}`}>
                {form.type === 'CREDIT' ? '+ ' : '- '}
                ₹{Number(form.amount).toLocaleString('en-IN')} ·{' '}
                {formatCategory(form.category)}
              </div>
            )}

            {/* Actions */}
            <div className="flex gap-3 pt-1">
              <button onClick={closeModal}
                className="flex-1 px-4 py-2.5 border border-gray-300 text-gray-700 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors">
                Cancel
              </button>
              <button onClick={handleSubmit} disabled={saving}
                className="flex-1 px-4 py-2.5 bg-indigo-600 text-white rounded-lg text-sm font-medium hover:bg-indigo-700 disabled:opacity-50 transition-colors">
                {saving ? 'Adding...' : 'Add Transaction'}
              </button>
            </div>

          </div>
        </div>
      )}
    </div>
  );
}