import React from 'react';
import { AlertCircle, X } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { hapticService } from '../services/HapticService';

const ConfirmDialog = ({ isOpen, onConfirm, onCancel, title, message, confirmText = 'Confirm', cancelText = 'Cancel', variant = 'danger' }) => {
    const handleConfirm = () => {
        hapticService.medium();
        onConfirm();
    };

    const handleCancel = () => {
        hapticService.light();
        onCancel();
    };

    const variantStyles = {
        danger: {
            icon: 'text-red-500',
            confirmBg: 'bg-red-600 hover:bg-red-500',
            border: 'border-red-500/30'
        },
        warning: {
            icon: 'text-orange-500',
            confirmBg: 'bg-orange-600 hover:bg-orange-500',
            border: 'border-orange-500/30'
        },
        info: {
            icon: 'text-blue-500',
            confirmBg: 'bg-blue-600 hover:bg-blue-500',
            border: 'border-blue-500/30'
        }
    };

    const styles = variantStyles[variant];

    return (
        <AnimatePresence>
            {isOpen && (
                <>
                    {/* Backdrop */}
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[60] flex items-center justify-center p-4"
                        onClick={handleCancel}
                    >
                        {/* Dialog */}
                        <motion.div
                            initial={{ scale: 0.9, opacity: 0 }}
                            animate={{ scale: 1, opacity: 1 }}
                            exit={{ scale: 0.9, opacity: 0 }}
                            onClick={(e) => e.stopPropagation()}
                            className={`w-full max-w-sm bg-zinc-900 rounded-2xl border ${styles.border} shadow-2xl p-6`}
                        >
                            {/* Icon & Title */}
                            <div className="flex items-start gap-3 mb-4">
                                <div className={`p-2 bg-zinc-800 rounded-lg ${styles.icon}`}>
                                    <AlertCircle size={24} />
                                </div>
                                <div className="flex-1">
                                    <h3 className="text-lg font-bold text-white">{title}</h3>
                                    <p className="text-sm text-zinc-400 mt-1">{message}</p>
                                </div>
                            </div>

                            {/* Buttons */}
                            <div className="flex gap-3 mt-6">
                                <button
                                    onClick={handleCancel}
                                    className="flex-1 px-4 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-white rounded-lg font-medium transition-colors"
                                >
                                    {cancelText}
                                </button>
                                <button
                                    onClick={handleConfirm}
                                    className={`flex-1 px-4 py-2.5 ${styles.confirmBg} text-white rounded-lg font-medium transition-colors`}
                                >
                                    {confirmText}
                                </button>
                            </div>
                        </motion.div>
                    </motion.div>
                </>
            )}
        </AnimatePresence>
    );
};

export default ConfirmDialog;
