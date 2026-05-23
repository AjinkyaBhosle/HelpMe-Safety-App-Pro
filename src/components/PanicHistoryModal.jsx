import React, { useState, useEffect } from 'react';
import { X, Clock, MapPin, Battery, Trash2, AlertCircle } from 'lucide-react';
import { getPanicHistory, getPanicStats, clearPanicHistory, deletePanicEvent } from '../utils/panicHistory';
import { hapticService } from '../services/HapticService';
import ConfirmDialog from './ConfirmDialog';

const PanicHistoryModal = ({ isOpen, onClose }) => {
    const [history, setHistory] = useState([]);
    const [stats, setStats] = useState({});
    const [confirmDialog, setConfirmDialog] = useState({ isOpen: false, type: '', id: null });

    useEffect(() => {
        // Always load data on mount since it's now a View
        const loadData = async () => {
            const h = await getPanicHistory();
            const s = await getPanicStats();
            setHistory(h);
            setStats(s);
        };
        loadData();
    }, []);

    const handleClearHistory = async () => {
        hapticService.medium();
        // Small delay to ensure haptic triggers before confirm dialog
        await new Promise(resolve => setTimeout(resolve, 50));
        setConfirmDialog({ isOpen: true, type: 'clear', id: null });
    };

    const handleDeleteEvent = async (id) => {
        hapticService.light();
        // Small delay to ensure haptic triggers before confirm dialog
        await new Promise(resolve => setTimeout(resolve, 50));
        setConfirmDialog({ isOpen: true, type: 'delete', id });
    };

    const handleConfirmAction = async () => {
        setConfirmDialog({ isOpen: false, type: '', id: null });

        if (confirmDialog.type === 'clear') {
            await clearPanicHistory();
            setHistory([]);
            setStats({ total: 0, lastPanic: null, thisMonth: 0 });
        } else if (confirmDialog.type === 'delete') {
            const success = await deletePanicEvent(confirmDialog.id);
            if (success) {
                const h = await getPanicHistory();
                const s = await getPanicStats();
                setHistory(h);
                setStats(s);
            }
        }
    };

    const handleCancelAction = () => {
        setConfirmDialog({ isOpen: false, type: '', id: null });
    };

    const formatDate = (timestamp) => {
        const date = new Date(timestamp);
        return date.toLocaleDateString('en-IN', {
            day: 'numeric',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    return (
        <div className="w-full max-w-md bg-zinc-900 rounded-2xl border border-zinc-800 shadow-xl flex flex-col h-full max-h-[85vh] animate-in fade-in zoom-in duration-300">

            {/* Header */}
            <div className="flex items-center justify-between p-6 border-b border-zinc-800">
                <h2 className="text-xl font-bold text-white">Panic History</h2>
                <button onClick={() => { hapticService.light(); onClose(); }} className="p-2 hover:bg-zinc-800 rounded-lg transition">
                    <X className="w-5 h-5 text-gray-400" />
                </button>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-3 gap-4 p-6 border-b border-zinc-800">
                <div className="text-center">
                    <div className="text-2xl font-bold text-red-500">{stats.total || 0}</div>
                    <div className="text-xs text-gray-500">Total</div>
                </div>
                <div className="text-center">
                    <div className="text-2xl font-bold text-orange-500">{stats.thisMonth || 0}</div>
                    <div className="text-xs text-gray-500">This Month</div>
                </div>
                <div className="text-center">
                    <button
                        onClick={handleClearHistory}
                        disabled={history.length === 0}
                        className="text-xs text-gray-400 hover:text-red-500 disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        <Trash2 className="w-5 h-5 mx-auto mb-1" />
                        Clear
                    </button>
                </div>
            </div>

            {/* History List */}
            <div className="flex-1 overflow-y-auto p-6 space-y-3">
                {history.length === 0 ? (
                    <div className="text-center py-12">
                        <AlertCircle className="w-12 h-12 text-gray-600 mx-auto mb-3" />
                        <p className="text-gray-500 text-sm">No panic events yet</p>
                        <p className="text-gray-600 text-xs mt-1">Your panic history will appear here</p>
                    </div>
                ) : (
                    history.map((event) => (
                        <div
                            key={event.id}
                            className="bg-zinc-800/50 rounded-xl p-4 border border-zinc-700/50 hover:border-zinc-600 transition-all group"
                        >
                            <div className="flex items-start justify-between mb-2">
                                <div className="flex items-center gap-2">
                                    <Clock className="w-4 h-4 text-gray-400" />
                                    <span className="text-sm font-medium text-white">
                                        {formatDate(event.timestamp)}
                                    </span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <div className="flex gap-2">
                                        {/* SMS Status */}
                                        {event.contactNumber && event.contactNumber !== 'Not configured' && event.smsSent ? (
                                            <span className="px-2 py-0.5 bg-green-500/20 text-green-400 rounded text-xs border border-green-500/30 flex items-center gap-1">
                                                SMS <span>✓</span>
                                            </span>
                                        ) : (
                                            <span className="px-2 py-0.5 bg-red-500/10 text-red-400 rounded text-xs border border-red-500/30 flex items-center gap-1">
                                                SMS <span>✕</span>
                                            </span>
                                        )}

                                        {/* Call Status */}
                                        {event.contactNumber && event.contactNumber !== 'Not configured' && event.callMade ? (
                                            <span className="px-2 py-0.5 bg-blue-500/20 text-blue-400 rounded text-xs border border-blue-500/30 flex items-center gap-1">
                                                Call <span>✓</span>
                                            </span>
                                        ) : (
                                            <span className="px-2 py-0.5 bg-red-500/10 text-red-400 rounded text-xs border border-blue-500/30 flex items-center gap-1">
                                                Call <span>✕</span>
                                            </span>
                                        )}
                                    </div>
                                    <button
                                        onClick={() => handleDeleteEvent(event.id)}
                                        className="p-1.5 bg-red-500/10 hover:bg-red-500/20 rounded-lg transition-colors"
                                        title="Delete this event"
                                    >
                                        <Trash2 className="w-4 h-4 text-red-400 hover:text-red-300" />
                                    </button>
                                </div>
                            </div>

                            <div className="text-xs text-gray-500 space-y-1 mt-2">
                                <div className="flex items-center gap-2">
                                    <MapPin className="w-3 h-3" />
                                    <span>{event.location}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <Battery className="w-3 h-3" />
                                    <span>{event.battery}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                    <span>📞</span>
                                    <span className={`font-mono ${event.contactNumber === 'Not configured' ? 'text-red-400' : 'text-blue-400'}`}>
                                        {event.contactNumber || 'Not configured'}
                                    </span>
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>

            <ConfirmDialog
                isOpen={confirmDialog.isOpen}
                onConfirm={handleConfirmAction}
                onCancel={handleCancelAction}
                title={confirmDialog.type === 'clear' ? 'Clear All History?' : 'Delete Event?'}
                message={confirmDialog.type === 'clear'
                    ? 'This will permanently delete all panic history. This action cannot be undone.'
                    : 'This panic event will be permanently deleted.'}
                confirmText={confirmDialog.type === 'clear' ? 'Clear All' : 'Delete'}
                variant="danger"
            />
        </div>
    );
};

export default PanicHistoryModal;
