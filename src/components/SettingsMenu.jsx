import React, { useState } from 'react';
import { UserCog, History, Info, X, Flashlight, Siren, Mic, Settings, Shield, Video, Timer, Sparkles, AlertTriangle } from 'lucide-react';

const HumanHeadSpeaking = ({ size = 24, className = "", ...props }) => (
    <svg 
      xmlns="http://www.w3.org/2000/svg" 
      width={size} 
      height={size} 
      viewBox="0 0 36 36" 
      className={className}
      {...props}
    >
      <path 
        fill="currentColor" 
        d="M35.838 23.159c.003.553-.443 1.002-.998 1.003l-5 .013c-.552.002-.999-.446-1-.997-.003-.555.444-1.002.995-1.004l5-.013c.553 0 1.002.445 1.003.998zm-1.587-5.489c.238.499.025 1.095-.475 1.333l-4.517 2.145c-.498.236-1.094.023-1.33-.476-.239-.498-.025-1.094.474-1.333l4.516-2.144c.5-.236 1.095-.024 1.332.475zm.027 10.987c.234-.501.02-1.096-.48-1.33l-4.527-2.122c-.501-.235-1.095-.02-1.33.48-.234.501-.019 1.096.482 1.33l4.526 2.123c.499.234 1.096.019 1.329-.481z"
      />
      <path 
        fill="currentColor" 
        d="M27.979 14.875c-1.42-.419-2.693-1.547-3.136-2.25-.76-1.208.157-1.521-.153-4.889C24.405 4.653 20.16 1.337 15 1c-2.346-.153-4.786.326-7.286 1.693-6.42 3.511-8.964 10.932-4.006 18.099 4.47 6.46.276 9.379.276 9.379s.166 1.36 2.914 3.188c2.749 1.827 6.121.588 6.121.588s1.112-3.954 4.748-3.59c2.606.384 6.266-.129 7.191-1.024.865-.837-.151-1.886.539-4.224-2.365-.232-3.665-1.359-3.79-2.948 2.625.255 3.708-.578 4.458-1.495-.021-.54-.075-1.686-.127-2.454 2.322-.672 3.212-2.962 1.941-3.337z"
      />
    </svg>
);
import { motion, AnimatePresence } from 'framer-motion';
import { registerPlugin } from '@capacitor/core';
import { sirenService } from '../services/SirenService';
import { hapticService } from '../services/HapticService';

const TorchPlugin = registerPlugin('TorchPlugin');
const SmsPlugin = registerPlugin('SmsPlugin');

const SettingsMenu = ({ isOpen, onClose, onNavigate, isPro, onUpgradeRequest }) => {
    const [isStrobing, setIsStrobing] = useState(false);
    const [isVoiceActive, setIsVoiceActive] = useState(localStorage.getItem('voiceActivation') === 'true');
    const [showSoundSelector, setShowSoundSelector] = useState(false);
    const [showVoiceRecorder, setShowVoiceRecorder] = useState(false);
    const [showCheckIn, setShowCheckIn] = useState(false);
    const [showVoiceInfo, setShowVoiceInfo] = useState(false);
    const [showVoiceDisabled, setShowVoiceDisabled] = useState(false);

    const freeItems = [
        {
            icon: UserCog,
            label: 'Configure Settings',
            description: 'Add Emergency contacts (Mandatory)',
            action: 'settings',
            color: 'text-blue-400'
        },
        {
            icon: UserCog,
            label: 'User Details',
            description: 'Add personal info (Optional)',
            action: 'profile',
            color: 'text-purple-400'
        },
        {
            icon: History,
            label: 'Panic History',
            description: 'View past emergency alerts',
            action: 'history',
            color: 'text-green-400'
        }
    ];

    const proItems = [
        {
            icon: HumanHeadSpeaking,
            label: 'Voice SOS',
            description: 'Say "Help Me" to trigger SOS even offline',
            action: 'voice',
            color: isVoiceActive ? 'text-green-500' : 'text-zinc-400'
        },
        {
            icon: Flashlight,
            label: isStrobing ? 'Stop SOS Flash' : 'SOS Flashlight',
            description: 'Toggle emergency strobe light',
            action: 'flashlight',
            color: isStrobing ? 'text-red-500 animate-pulse' : 'text-yellow-400'
        },
        {
            icon: Siren,
            label: 'Safety Sounds',
            description: 'Alert others & scare away threats',
            action: 'siren',
            color: 'text-orange-500'
        },
        {
            icon: Mic,
            label: 'Voice Recorder',
            description: 'Record audio & conversations',
            action: 'recorder',
            color: 'text-red-400'
        },
        {
            icon: Video,
            label: 'Safety Cam',
            description: 'Record video with GPS Overlay',
            action: 'safety-cam',
            color: 'text-rose-500'
        },
        {
            icon: Timer,
            label: 'Scheduled Check-in',
            description: 'Are you safe? Safety timer',
            action: 'checkin',
            color: 'text-amber-500'
        }
    ];

    const infoItems = [
        {
            icon: Shield,
            label: 'Privacy & Policy',
            description: 'Data safety & permissions',
            action: 'privacy',
            color: 'text-zinc-400'
        },
        {
            icon: Info,
            label: 'About Us',
            description: 'App information & how to use',
            action: 'about',
            color: 'text-purple-400'
        }
    ];

    const handleItemClick = async (action) => {
        const proActions = ['voice', 'flashlight', 'siren', 'recorder', 'safety-cam', 'checkin'];
        if (proActions.includes(action) && !isPro) {
            hapticService.light();
            onUpgradeRequest(action);
            return;
        }

        if (action === 'flashlight') {
            hapticService.medium();
            const newState = !isStrobing;
            setIsStrobing(newState);
            try {
                await TorchPlugin.setStrobe({ enable: newState });
            } catch (e) {
                console.error("Torch error", e);
            }
            return;
        }

        if (action === 'voice') {
            hapticService.medium();
            const newState = !isVoiceActive;
            setIsVoiceActive(newState);
            localStorage.setItem('voiceActivation', newState);
            try {
                if (newState) {
                    await SmsPlugin.startVoiceListener();
                    setShowVoiceInfo(true);
                } else {
                    await SmsPlugin.stopVoiceListener();
                    setShowVoiceDisabled(true);
                }
            } catch (e) {
                console.error("Failed to toggle voice listener", e);
            }
            return;
        }

        // Delegating all navigation-based actions to parent
        hapticService.light();
        onNavigate(action);
    };

    return (
        <>
            <AnimatePresence>
                {isOpen && (
                    <>
                        {/* Backdrop */}
                        <motion.div
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            exit={{ opacity: 0 }}
                            onClick={onClose}
                            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-[80]"
                        />

                        {/* Menu */}
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95, y: -20 }}
                            animate={{ opacity: 1, scale: 1, y: 0 }}
                            exit={{ opacity: 0, scale: 0.95, y: -20 }}
                            className="fixed top-20 right-6 w-80 bg-zinc-900 rounded-2xl border border-zinc-800 shadow-2xl z-[90] overflow-hidden"
                        >
                            {/* Header */}
                            <div className="flex items-center justify-between p-4 border-b border-zinc-800">
                                <h3 className="text-white font-semibold flex items-center gap-2">
                                    <Settings size={18} /> Settings Menu
                                </h3>
                                <button
                                    onClick={() => { hapticService.light(); onClose(); }}
                                    className="p-1 hover:bg-zinc-800 rounded-full transition-colors"
                                >
                                    <X size={18} className="text-zinc-400" />
                                </button>
                            </div>

                            {/* Menu Items */}
                            <div className="p-2 max-h-96 overflow-y-auto scrollbar-thin scrollbar-thumb-zinc-700 scrollbar-track-zinc-900 space-y-4">
                                
                                {/* Free Features */}
                                <div>
                                    <h4 className="text-xs font-semibold text-zinc-500 uppercase tracking-wider px-4 py-2">Basic Features</h4>
                                    {freeItems.map((item, index) => {
                                        const Icon = item.icon;
                                        return (
                                            <button
                                                key={`free-${index}`}
                                                onClick={() => handleItemClick(item.action)}
                                                className="w-full p-3 hover:bg-zinc-800/50 rounded-xl transition-all group flex items-start gap-3 text-left"
                                            >
                                                <div className={`p-2 bg-zinc-800 rounded-lg group-hover:bg-zinc-700 transition-colors ${item.color}`}>
                                                    <Icon size={20} />
                                                </div>
                                                <div className="flex-1">
                                                    <h4 className="text-white font-medium group-hover:text-white/90 transition-colors">
                                                        {item.label}
                                                    </h4>
                                                    <p className="text-xs text-zinc-500 mt-0.5">
                                                        {item.description}
                                                    </p>
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>

                                {/* Pro Features */}
                                <div className="border-t border-zinc-800/50 pt-2">
                                    <div className="flex items-center justify-between px-4 py-2">
                                        <h4 className="text-xs font-semibold text-yellow-500 uppercase tracking-wider flex items-center gap-1">
                                            <Sparkles size={12} /> Pro Features
                                        </h4>
                                        {!isPro && <span className="text-[10px] bg-yellow-500/10 text-yellow-500 px-2 py-0.5 rounded-full font-semibold">PAID</span>}
                                    </div>
                                    
                                    {proItems.map((item, index) => {
                                        const Icon = item.icon;
                                        return (
                                            <button
                                                key={`pro-${index}`}
                                                onClick={() => handleItemClick(item.action)}
                                                className="w-full p-3 hover:bg-zinc-800/50 rounded-xl transition-all group flex items-start gap-3 text-left relative"
                                            >
                                                <div className={`p-2 bg-zinc-800 rounded-lg group-hover:bg-zinc-700 transition-colors ${item.color}`}>
                                                    <Icon size={20} />
                                                </div>
                                                <div className="flex-1">
                                                    <h4 className="text-white font-medium group-hover:text-white/90 transition-colors flex items-center gap-2">
                                                        {item.label}
                                                    </h4>
                                                    <p className="text-xs text-zinc-500 mt-0.5">
                                                        {item.description}
                                                    </p>
                                                </div>
                                                {item.action === 'voice' && (
                                                    <div className={`w-11 h-6 rounded-full p-1 shrink-0 transition-colors flex items-center ${isVoiceActive ? 'bg-green-500' : 'bg-zinc-700'}`}>
                                                        <div className={`w-4 h-4 bg-white rounded-full transition-transform ${isVoiceActive ? 'translate-x-5' : 'translate-x-0'}`} />
                                                    </div>
                                                )}
                                                {item.action === 'flashlight' && (
                                                    <div className={`w-11 h-6 rounded-full p-1 shrink-0 transition-colors flex items-center ${isStrobing ? 'bg-red-500' : 'bg-zinc-700'}`}>
                                                        <div className={`w-4 h-4 bg-white rounded-full transition-transform ${isStrobing ? 'translate-x-5' : 'translate-x-0'}`} />
                                                    </div>
                                                )}
                                            </button>
                                        );
                                    })}
                                </div>

                                {/* Information Links */}
                                <div className="border-t border-zinc-800/50 pt-2 pb-2">
                                    <h4 className="text-xs font-semibold text-zinc-500 uppercase tracking-wider px-4 py-2">Information</h4>
                                    {infoItems.map((item, index) => {
                                        const Icon = item.icon;
                                        return (
                                            <button
                                                key={`info-${index}`}
                                                onClick={() => handleItemClick(item.action)}
                                                className="w-full p-3 hover:bg-zinc-800/50 rounded-xl transition-all group flex items-start gap-3 text-left"
                                            >
                                                <div className={`p-2 bg-zinc-800 rounded-lg group-hover:bg-zinc-700 transition-colors ${item.color}`}>
                                                    <Icon size={20} />
                                                </div>
                                                <div className="flex-1">
                                                    <h4 className="text-white font-medium group-hover:text-white/90 transition-colors flex items-center gap-2">
                                                        {item.label}
                                                    </h4>
                                                    <p className="text-xs text-zinc-500 mt-0.5">
                                                        {item.description}
                                                    </p>
                                                </div>
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        </motion.div>
                    </>
                )}
            </AnimatePresence>

            {/* Voice Info Modal overlay */}
            <AnimatePresence>
                {showVoiceInfo && (
                    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
                        <motion.div
                            initial={{ scale: 0.95, opacity: 0 }}
                            animate={{ scale: 1, opacity: 1 }}
                            exit={{ scale: 0.95, opacity: 0 }}
                            className="w-full max-w-sm bg-zinc-900 rounded-3xl border border-zinc-800 shadow-2xl overflow-hidden p-6 text-center"
                        >
                            <div className="w-16 h-16 bg-green-500/20 rounded-full flex items-center justify-center mx-auto mb-4 border border-green-500/30 shadow-[0_0_15px_rgba(34,197,94,0.2)]">
                                <HumanHeadSpeaking className="w-8 h-8 text-green-500" />
                            </div>
                            
                            <h3 className="text-xl font-bold text-white mb-2">Voice SOS Active</h3>
                            
                            <p className="text-sm text-zinc-400 mb-4 leading-relaxed">
                                Say <b className="text-white">"Help Me"</b> clearly to trigger the SOS alarm, even when your phone is locked and offline.
                            </p>
                            
                            <div className="bg-zinc-800/50 p-3 rounded-xl border border-zinc-700/50 mb-6 text-left space-y-3">
                                <p className="text-xs text-zinc-300 flex items-start gap-2">
                                    <Shield className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
                                    <span><b>100% Private:</b> Audio is analyzed by an on-device AI model. Nothing is recorded or sent to the cloud.</span>
                                </p>
                                <p className="text-xs text-zinc-300 flex items-start gap-2">
                                    <AlertTriangle className="w-4 h-4 text-orange-400 shrink-0 mt-0.5" />
                                    <span><b>Battery Usage:</b> Continuous listening uses extra battery. We recommend disabling it when you reach a safe location.</span>
                                </p>
                            </div>
                            
                            <button
                                onClick={() => { hapticService.light(); setShowVoiceInfo(false); }}
                                className="w-full bg-zinc-800 hover:bg-zinc-700 active:bg-zinc-600 text-white font-bold py-3.5 rounded-xl transition"
                            >
                                Got it
                            </button>
                        </motion.div>
                    </div>
                )}
            </AnimatePresence>

            {/* Voice Disabled Modal overlay */}
            <AnimatePresence>
                {showVoiceDisabled && (
                    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
                        <motion.div
                            initial={{ scale: 0.95, opacity: 0 }}
                            animate={{ scale: 1, opacity: 1 }}
                            exit={{ scale: 0.95, opacity: 0 }}
                            className="w-full max-w-sm bg-zinc-900 rounded-3xl border border-zinc-800 shadow-2xl overflow-hidden p-6 text-center"
                        >
                            <div className="w-16 h-16 bg-zinc-800 rounded-full flex items-center justify-center mx-auto mb-4 border border-zinc-700">
                                <HumanHeadSpeaking className="w-8 h-8 text-zinc-500" />
                            </div>
                            
                            <h3 className="text-xl font-bold text-white mb-2">Voice SOS Disabled</h3>
                            
                            <p className="text-sm text-zinc-400 mb-6 leading-relaxed">
                                The background microphone listener has been completely stopped. You can no longer trigger SOS using your voice.
                            </p>
                            
                            <button
                                onClick={() => { hapticService.light(); setShowVoiceDisabled(false); }}
                                className="w-full bg-zinc-800 hover:bg-zinc-700 active:bg-zinc-600 text-white font-bold py-3.5 rounded-xl transition"
                            >
                                Close
                            </button>
                        </motion.div>
                    </div>
                )}
            </AnimatePresence>
        </>
    );
};

export default SettingsMenu;
