import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Settings, ShieldAlert, CircleCheck, Battery, MapPin, Zap } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { registerPlugin } from '@capacitor/core';
import { savePanicEvent } from '../utils/panicHistory';
import { hapticService } from '../services/HapticService';

const SmsPlugin = registerPlugin('SmsPlugin');

const SosPage = ({ onSettingsClick, settings }) => {
    const navigate = useNavigate();
    const [status, setStatus] = useState('safe');
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        let voiceListener = null;

        const setupListener = async () => {
            voiceListener = await SmsPlugin.addListener('onVoicePanic', () => {
                console.log("🎤 Voice SOS Triggered!");
                handlePanic();
            });
        };

        setupListener();

        return () => {
            if (voiceListener) {
                voiceListener.remove();
            }
        };
    }, [settings]); // Re-bind if settings change

    // Header Status (Always Active)
    const getEmail = () => localStorage.getItem('user_email');

    const getBrowserLocation = () => {
        return new Promise((resolve) => {
            const tryNativeFallback = async () => {
                try {
                    const res = await SmsPlugin.getLastKnownLocation();
                    if (res && res.latitude) {
                        resolve(`${res.latitude.toFixed(4)}, ${res.longitude.toFixed(4)}`);
                        return true;
                    }
                } catch (e) {
                    console.warn("Native location fallback failed", e);
                }
                resolve('Location unavailable');
                return false;
            };

            if (!navigator.geolocation) {
                tryNativeFallback();
                return;
            }

            navigator.geolocation.getCurrentPosition(
                (pos) => resolve(`${pos.coords.latitude.toFixed(4)}, ${pos.coords.longitude.toFixed(4)}`),
                (err) => {
                    console.warn("Browser location failed, trying native...", err);
                    tryNativeFallback();
                },
                { enableHighAccuracy: true, timeout: 5000, maximumAge: 0 }
            );
        });
    };

    const getBrowserBattery = async () => {
        try {
            if ('getBattery' in navigator) {
                const battery = await navigator.getBattery();
                return `${Math.round(battery.level * 100)}%`;
            }
        } catch (e) {
            console.warn('Battery API not supported');
        }
        return 'Unknown';
    };

    const handlePanic = async () => {
        // Immediate haptic feedback
        hapticService.heavy();

        setStatus('panic');

        try {
            // Get emergency contact from settings
            const rawContacts = settings.phoneNumbers || '';

            // STRICT VALIDATION
            if (!rawContacts || rawContacts.trim().length < 3) {
                // < 3 is arbitrary but catches empty/garbage like " "
                throw new Error("No emergency contacts configured! Please go to Settings.");
            }

            const contactCount = rawContacts.split(',').length;
            const emergencyContact = rawContacts; // Pass through fully

            // 1. Trigger Native Panic (Background Thread - Most Reliable)
            const nativePromise = SmsPlugin.triggerPanic();

            // 2. Capture Browser Data for Local History (Visual Log)
            // run in parallel with native trigger so we don't delay
            const [locationStr, batteryStr] = await Promise.all([
                getBrowserLocation(),
                getBrowserBattery()
            ]);

            await nativePromise;

            // Panic history is now saved NATIVELY by AlertWorker to ensure it records
            // even if the app UI is closed or killed during the background process.

            // Simple success feedback (updated)
            alert(`SOS INITIATED!\n\nCalling Primary: 1st Contact\nSMS Alerts: Sent to ${contactCount} contacts.`);

        } catch (error) {
            console.error("Panic trigger failed:", error);
            alert("Failed to send SOS: " + error.message);
        } finally {
            setStatus('safe');
        }
    };

    return (
        <div className="h-full w-full bg-black text-white flex flex-col relative overflow-hidden font-sans selection:bg-green-500/30">

            {/* Dynamic Background Glow */}
            <div className={`absolute top-0 left-0 w-full h-full pointer-events-none transition-colors duration-700
        ${status === 'panic' ? 'bg-red-900/20' : 'bg-green-900/10'}`} />

            <div className={`absolute top-[-20%] left-[-20%] w-[140%] h-[50%] bg-gradient-to-b ${status === 'panic' ? 'from-red-600/20' : 'from-green-500/10'} to-transparent rounded-full blur-[100px] pointer-events-none transition-all duration-1000`} />

            {/* Header - Increased padding for better spacing */}
            <header className="flex justify-between items-center pt-8 pb-6 px-6 z-10 w-full">
                <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
                    <span className="text-xs font-medium tracking-widest text-green-500/80 uppercase">System Active</span>
                </div>
                {/* Larger settings button for easier clicking */}
                <button
                    onClick={() => { hapticService.light(); onSettingsClick(); }}
                    className="p-3 rounded-full bg-white/5 hover:bg-white/10 transition-colors backdrop-blur-md min-w-[44px] min-h-[44px] flex items-center justify-center"
                >
                    <Settings className="w-6 h-6 text-gray-300" />
                </button>
            </header>

            {/* Main Content */}
            <main className="flex-1 flex flex-col items-center justify-center relative z-10 px-6 w-full">

                {/* The Big Button Container */}
                <div className="relative mb-8">

                    {/* Ripple Effects (Panic Mode) */}
                    {status === 'panic' && (
                        <>
                            <motion.div initial={{ scale: 1, opacity: 0.8 }} animate={{ scale: 2, opacity: 0 }} transition={{ repeat: Infinity, duration: 1.5 }}
                                className="absolute inset-0 bg-red-600 rounded-full blur-md opacity-50" />
                            <motion.div initial={{ scale: 1, opacity: 0.6 }} animate={{ scale: 3, opacity: 0 }} transition={{ repeat: Infinity, duration: 2, delay: 0.2 }}
                                className="absolute inset-0 bg-red-600 rounded-full blur-xl opacity-30" />
                        </>
                    )}

                    {/* The Interactive SOS Button */}
                    <motion.button
                        whileTap={{ scale: 0.95 }}
                        onClick={handlePanic}
                        onLongPress={handlePanic}
                        className={`w-64 h-64 rounded-full flex flex-col items-center justify-center relative shadow-2xl transition-all duration-500 border-4 border-opacity-20 backdrop-blur-sm
                            bg-gradient-to-br from-red-600 to-red-800 border-red-400 shadow-red-900/50
                        `}
                    >
                        {loading ? (
                            <Zap className="w-16 h-16 text-white/50 animate-pulse" />
                        ) : (
                            <>
                                <ShieldAlert className="w-20 h-20 mb-4 text-white" />
                                <span className="text-4xl font-black bg-clip-text text-transparent bg-gradient-to-b from-white to-gray-200">
                                    SOS
                                </span>
                                <span className="text-xs text-white/70 mt-2 font-medium tracking-wide">TAP FOR HELP</span>
                            </>
                        )}
                    </motion.button>
                </div>

                <p className="text-xs text-zinc-500 text-center max-w-[280px]">
                    Triggers emergency calls and SMS alerts with your current location, battery level, and timestamp.
                </p>

            </main>
        </div>
    );
};

export default SosPage;
