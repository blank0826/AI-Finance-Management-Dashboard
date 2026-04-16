import React, { useState, useCallback, useEffect } from 'react';
import { useDropzone } from 'react-dropzone';
import { uploadApi } from '../services/api';
import toast from 'react-hot-toast';
import { UploadCloud, CheckCircle, XCircle, Clock, FileText, Trash2, Download } from 'lucide-react';

const STATUS_ICONS = {
  PROCESSING: <Clock className="w-5 h-5 text-yellow-500 animate-pulse" />,
  COMPLETED:  <CheckCircle className="w-5 h-5 text-green-500" />,
  FAILED:     <XCircle className="w-5 h-5 text-red-500" />,
};

export default function Upload() {
  const [accountName, setAccountName] = useState('');
  const [uploads, setUploads] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(true);

  // Load upload history on mount
  useEffect(() => {
    uploadApi.getHistory()
      .then(res => setUploads(res.data))
      .catch(() => toast.error('Could not load upload history'))
      .finally(() => setLoadingHistory(false));
  }, []);

  const pollStatus = useCallback((uploadId) => {
    const interval = setInterval(async () => {
      try {
        const res = await uploadApi.getStatus(uploadId);
        const { status, transactionCount, errorMessage } = res.data;
        setUploads(prev => prev.map(u => u.id === uploadId ? { ...u, ...res.data } : u));
        if (status === 'COMPLETED') {
          toast.success(`Done! ${transactionCount} transactions imported.`);
          clearInterval(interval);
        } else if (status === 'FAILED') {
          toast.error(`Failed: ${errorMessage}`);
          clearInterval(interval);
        }
      } catch { clearInterval(interval); }
    }, 2000);
  }, []);

  const onDrop = useCallback(async (acceptedFiles) => {
    for (const file of acceptedFiles) {
      const name = accountName || file.name.replace(/\.[^.]+$/, '');
      const toastId = toast.loading(`Uploading ${file.name}...`);
      try {
        const res = await uploadApi.upload(file, name);
        toast.success('Uploaded! AI is categorizing transactions...', { id: toastId });
        const newUpload = {
          id: res.data.uploadId,
          fileName: file.name,
          accountName: name,
          status: 'PROCESSING',
          transactionCount: 0,
          fileUrl: null,
        };
        setUploads(prev => [newUpload, ...prev]);
        pollStatus(res.data.uploadId);
      } catch (err) {
        toast.error(err.response?.data || 'Upload failed', { id: toastId });
      }
    }
  }, [accountName, pollStatus]);

  const handleDelete = async (uploadId) => {
    if (!window.confirm('Delete this upload and all its transactions?')) return;
    try {
      await uploadApi.deleteUpload(uploadId);
      setUploads(prev => prev.filter(u => u.id !== uploadId));
      toast.success('Upload deleted');
    } catch {
      toast.error('Failed to delete upload');
    }
  };

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/pdf': ['.pdf'], 'text/csv': ['.csv'], 'application/vnd.ms-excel': ['.xlsx'] },
    multiple: true,
  });

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Upload Statements</h1>
        <p className="text-gray-500 mt-1">Upload PDF or CSV bank statements. AI will categorize all transactions automatically.</p>
      </div>

      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-5">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Account Name (optional)</label>
          <input
            type="text"
            className="w-full px-4 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
            placeholder="e.g. HDFC Savings, SBI Current..."
            value={accountName}
            onChange={e => setAccountName(e.target.value)}
          />
        </div>

        <div
          {...getRootProps()}
          className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors
            ${isDragActive ? 'border-indigo-500 bg-indigo-50' : 'border-gray-300 hover:border-indigo-400 hover:bg-gray-50'}`}
        >
          <input {...getInputProps()} />
          <UploadCloud className={`w-12 h-12 mx-auto mb-3 ${isDragActive ? 'text-indigo-500' : 'text-gray-400'}`} />
          {isDragActive
            ? <p className="text-indigo-600 font-medium">Drop files here...</p>
            : <>
                <p className="text-gray-700 font-medium">Drag & drop files here, or click to browse</p>
                <p className="text-sm text-gray-400 mt-1">Supports PDF and CSV bank statements</p>
              </>
          }
        </div>
      </div>

      {/* Upload History */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
        <h2 className="font-semibold text-gray-900 mb-4">Upload History</h2>

        {loadingHistory ? (
          <div className="flex justify-center py-8">
            <div className="animate-spin w-7 h-7 border-4 border-indigo-500 border-t-transparent rounded-full" />
          </div>
        ) : uploads.length === 0 ? (
          <p className="text-gray-400 text-center py-8">No uploads yet. Drop a file above to get started.</p>
        ) : (
          <div className="space-y-3">
            {uploads.map((u) => (
              <div key={u.id} className="flex items-center gap-4 p-4 bg-gray-50 rounded-xl">
                <FileText className="w-8 h-8 text-indigo-400 shrink-0" />

                <div className="flex-1 min-w-0">
                  <p className="font-medium text-gray-900 truncate">{u.fileName}</p>
                  <p className="text-sm text-gray-500">{u.accountName}</p>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  {STATUS_ICONS[u.status] || STATUS_ICONS.PROCESSING}
                  <span className="text-sm text-gray-600">
                    {u.status === 'COMPLETED'
                      ? `${u.transactionCount} transactions`
                      : u.status === 'FAILED'
                      ? 'Failed'
                      : 'Processing...'}
                  </span>
                </div>

                {/* Download original file (only if Cloudinary is enabled) */}
                {u.fileUrl && (
                  <a href={u.fileUrl} target="_blank" rel="noreferrer"
                    className="p-2 text-indigo-500 hover:bg-indigo-50 rounded-lg transition-colors"
                    title="Download original file">
                    <Download className="w-4 h-4" />
                  </a>
                )}

                {/* Delete upload */}
                <button onClick={() => handleDelete(u.id)}
                  className="p-2 text-red-400 hover:bg-red-50 rounded-lg transition-colors"
                  title="Delete upload and its transactions">
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
