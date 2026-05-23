import React, { useState, useEffect } from 'react';
import { X, Clock, ShieldCheck, AlertTriangle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { registerPlugin } from '@capacitor/core';
import { hapticService } from '../services/HapticService';

const SmsPlugin = registerPlugin('SmsPlugin');

const CheckInModal = ({ isOpen, onClose }) => {
    const [isActive, setIsActive] = useState(false);
    const [selectedInterval, setSelectedInterval] = useState(null); // in minutes
    const [nextCheckInTime, setNextCheckInTime] = useState(null);
    const [loading, setLoading] = useState(false);
    const [showSuccess, setShowSuccess] = useState(false); // New success state

    // Load initial state
    useEffect(() => {
        if (isOpen) {
            checkState();
            setShowSuccess(false);
        }
    }, [isOpen]);

    const checkState = async () => {
        try {
            const savedExpiry = localStorage.getItem('checkin_expiry');
            if (savedExpiry) {
                const expiry = parseInt(savedExpiry);
                if (expiry > Date.now()) {
                    setIsActive(true);
                    setNextCheckInTime(expiry);
                } else {
                    // Expired - likely fired.
                    setIsActive(false);
                    localStorage.removeItem('checkin_expiry');
                }
            } else {
                setIsActive(false);
            }
        } catch (e) {
            console.error("Failed to check state", e);
        }
    };

    const handleStart = async () => {
        if (!selectedInterval) return;
        hapticService.medium();
        setLoading(true);

        try {
            // 1. Check Permissions
            const permStatus = await SmsPlugin.checkPermissions();

            // Check specifically for background location
            if (permStatus.background_location !== 'granted') {
                const proceed = confirm(
                    "⚠️ IMPORTANT ⚠️\n\n" +
                    "To ensure we can send your location even if the app is closed, " +
                    "you MUST select 'Allow all the time' in the next screen.\n\n" +
                    "Tap OK to open Settings/Permissions."
                );

                if (proceed) {
                    await SmsPlugin.requestPermissions({ permissions: ['background_location'] });
                    // Re-check
                    const newStatus = await SmsPlugin.checkPermissions();
                    if (newStatus.background_location !== 'granted') {
                        throw new Error("Background location ('Allow all the time') is required for safety check-ins.");
                    }
                } else {
                    setLoading(false);
                    return;
                }
            }

            await SmsPlugin.scheduleCheckIn({ minutes: selectedInterval });

            // Calculate and save expiry
            const now = Date.now();
            const expiry = now + (selectedInterval * 60 * 1000);

            localStorage.setItem('checkin_expiry', expiry.toString());
            localStorage.setItem('checkin_interval', selectedInterval.toString());
            localStorage.setItem('checkin_start', now.toString()); // Save start time

            setNextCheckInTime(expiry);
            setIsActive(true);
            hapticService.success();
        } catch (e) {
            alert("Failed to start monitoring: " + e.message);
        } finally {
            setLoading(false);
        }
    };

    const handleImSafe = async () => {
        hapticService.success();
        setLoading(true);

        try {
            // Stop monitoring completely
            await SmsPlugin.cancelCheckIn();

            // Clear state
            localStorage.removeItem('checkin_expiry');
            localStorage.removeItem('checkin_interval');
            localStorage.removeItem('checkin_start');

            // Show success message
            setShowSuccess(true);

            // Wait 2 seconds then reset to main page
            setTimeout(() => {
                setShowSuccess(false);
                setIsActive(false);
                setNextCheckInTime(null);
                setSelectedInterval(null);
            }, 2000);

        } catch (e) {
            alert("Failed to update status: " + e.message);
            setLoading(false);
        } finally {
            // Don't set loading false here immediately if showing success, 
            // but effectively we are done 'loading' logic.
            // We want the modal to stay open showing success.
            setLoading(false);
        }
    };

    const formatDateTime = (timestamp) => {
        if (!timestamp) return "";
        const date = new Date(timestamp);
        const day = date.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' });
        const time = date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

        // Check if today/tomorrow
        const now = new Date();
        const isToday = date.getDate() === now.getDate() && date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        const isTomorrow = date.getDate() === tomorrow.getDate() && date.getMonth() === tomorrow.getMonth() && date.getFullYear() === tomorrow.getFullYear();

        let dayStr = day;
        if (isToday) dayStr = 'Today';
        if (isTomorrow) dayStr = 'Tomorrow';

        return `${dayStr}, ${time}`;
    };

    const getStartTime = () => {
        const start = localStorage.getItem('checkin_start');
        if (start) return parseInt(start);
        return null;
    };

    const ranges = [
        { label: '5m', value: 5, desc: 'Test' },
        { label: '15m', value: 15, desc: 'Test' },
        { label: '8h', value: 480, desc: 'Sleep' },
        { label: '12h', value: 720, desc: 'Day' },
        { label: '24h', value: 1440, desc: 'Daily' },
        { label: '48h', value: 2880, desc: 'Trip' },
        { label: '72h', value: 4320, desc: 'Long Trip' },
    ];

    if (showSuccess) {
        return (
            <div className="w-full max-w-sm bg-zinc-900 rounded-2xl border border-zinc-800 shadow-2xl overflow-hidden animate-in fade-in zoom-in duration-300 p-8 flex flex-col items-center justify-center text-center">
                <div className="w-20 h-20 bg-green-500/10 rounded-full flex items-center justify-center mb-6 animate-pulse">
                    <ShieldCheck size={40} className="text-green-500" />
                </div>
                <h3 className="text-2xl font-bold text-white mb-2">You are Safe!</h3>
                <p className="text-zinc-400 text-sm">Monitoring has been stopped.</p>
            </div>
        );
    }

    return (
        <div className="w-full max-w-sm bg-zinc-900 rounded-2xl border border-zinc-800 shadow-2xl overflow-hidden animate-in fade-in zoom-in duration-300">
            {/* Header */}
            <div className={`p-4 ${isActive ? 'bg-green-500/10 border-b border-green-500/20' : 'bg-zinc-800/50 border-b border-zinc-700'} flex justify-between items-center`}>
                <div className="flex items-center gap-2">
                    <Clock className={isActive ? "text-green-500" : "text-white"} size={20} />
                    <h3 className="text-white font-semibold">Scheduled Check-in</h3>
                </div>
                <button
                    onClick={() => {
                        hapticService.light();
                        onClose();
                    }}
                    className="p-1 hover:bg-zinc-800 rounded-full text-zinc-400"
                >
                    <X size={20} />
                </button>
            </div>

            <div className="p-6">
                {!isActive ? (
                    <>
                        {/* SETUP MODE */}
                        <p className="text-zinc-400 text-sm mb-6">
                            Choose how often we should check that you're safe. If you don't respond, we'll alert your contacts.
                        </p>

                        <div className="grid grid-cols-3 gap-3 mb-8">
                            {ranges.map((r) => (
                                <button
                                    key={r.value}
                                    onClick={() => { hapticService.light(); setSelectedInterval(r.value); }}
                                    className={`p-3 rounded-xl border transition-all ${selectedInterval === r.value
                                        ? 'bg-blue-600 border-blue-500 text-white shadow-lg shadow-blue-500/20'
                                        : 'bg-zinc-800 border-zinc-700 text-zinc-300 hover:bg-zinc-700'
                                        }`}
                                >
                                    <div className="text-lg font-bold">{r.label}</div>
                                    <div className="text-xs opacity-70 font-medium">{r.desc}</div>
                                </button>
                            ))}
                        </div>

                        <button
                            disabled={!selectedInterval || loading}
                            onClick={handleStart}
                            className="w-full py-4 bg-white text-black font-bold rounded-xl hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center justify-center gap-2"
                        >
                            {loading ? 'Starting...' : 'Start Monitoring'}
                        </button>
                    </>
                ) : (
                    <>
                        {/* ACTIVE MODE */}
                        <div className="text-center py-4">
                            <div className="w-20 h-20 bg-green-500/10 rounded-full flex items-center justify-center mx-auto mb-4 animate-pulse">
                                <ShieldCheck size={40} className="text-green-500" />
                            </div>

                            <p className="text-zinc-400 text-sm uppercase tracking-widest mb-1">Next Check-in</p>
                            <h2 className="text-3xl font-bold text-white mb-2">
                                {formatDateTime(nextCheckInTime)}
                            </h2>

                            {getStartTime() && (
                                <p className="text-xs text-zinc-500 mb-6">
                                    Last Check-in: {formatDateTime(getStartTime())}
                                </p>
                            )}

                            <p className="text-xs text-zinc-500 mb-8 border-t border-zinc-800 pt-4">
                                We'll assume you're safe until then.
                            </p>

                            <button
                                id="safe-btn"
                                onClick={handleImSafe}
                                disabled={loading}
                                className="w-full py-4 bg-green-600 hover:bg-green-500 text-white font-bold rounded-xl mb-4 shadow-lg shadow-green-900/20 transition-all active:scale-95"
                            >
                                I'm Safe
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

export default CheckInModal;
