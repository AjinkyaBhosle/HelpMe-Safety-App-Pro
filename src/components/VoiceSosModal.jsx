import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, Shield, AlertTriangle, ExternalLink } from 'lucide-react';
import { hapticService } from '../services/HapticService';
import { registerPlugin } from '@capacitor/core';

const SmsPlugin = registerPlugin('SmsPlugin');

const HumanHeadSpeaking = ({ size = 24, className = "", ...props }) => (
    <svg xmlns="http://www.w3.org/2000/svg" width={size} height={size} viewBox="0 0 36 36" className={className} {...props}>
      <path fill="currentColor" d="M35.838 23.159c.003.553-.443 1.002-.998 1.003l-5 .013c-.552.002-.999-.446-1-.997-.003-.555.444-1.002.995-1.004l5-.013c.553 0 1.002.445 1.003.998zm-1.587-5.489c.238.499.025 1.095-.475 1.333l-4.517 2.145c-.498.236-1.094.023-1.33-.476-.239-.498-.025-1.094.474-1.333l4.516-2.144c.5-.236 1.095-.024 1.332.475zm.027 10.987c.234-.501.02-1.096-.48-1.33l-4.527-2.122c-.501-.235-1.095-.02-1.33.48-.234.501-.019 1.096.482 1.33l4.526 2.123c.499.234 1.096.019 1.329-.481z" />
      <path fill="currentColor" d="M27.979 14.875c-1.42-.419-2.693-1.547-3.136-2.25-.76-1.208.157-1.521-.153-4.889C24.405 4.653 20.16 1.337 15 1c-2.346-.153-4.786.326-7.286 1.693-6.42 3.511-8.964 10.932-4.006 18.099 4.47 6.46.276 9.379.276 9.379s.166 1.36 2.914 3.188c2.749 1.827 6.121.588 6.121.588s1.112-3.954 4.748-3.59c2.606.384 6.266-.129 7.191-1.024.865-.837-.151-1.886.539-4.224-2.365-.232-3.665-1.359-3.79-2.948 2.625.255 3.708-.578 4.458-1.495-.021-.54-.075-1.686-.127-2.454 2.322-.672 3.212-2.962 1.941-3.337z" />
    </svg>
);

const VoiceSosModal = ({ isOpen, onClose, isVoiceActive, setIsVoiceActive }) => {
    const [accent, setAccent] = useState(localStorage.getItem('voice_sos_accent') || 'us');

    const handleToggle = async () => {
        hapticService.medium();
        const newState = !isVoiceActive;
        setIsVoiceActive(newState);
        localStorage.setItem('voiceActivation', newState);
        
        try {
            if (newState) {
                await SmsPlugin.startVoiceListener();
            } else {
                await SmsPlugin.stopVoiceListener();
            }
        } catch (e) {
            console.error("Failed to toggle voice listener", e);
        }
    };

    const handleAccentChange = async (newAccent) => {
        hapticService.light();
        setAccent(newAccent);
        localStorage.setItem('voice_sos_accent', newAccent);
        
        try {
            await SmsPlugin.setVoiceAccent({ accent: newAccent });
        } catch (e) {
            console.error("Failed to set accent", e);
        }
    };

    return (
        <AnimatePresence>
            {isOpen && (
                <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200">
                    <motion.div
                        initial={{ scale: 0.95, opacity: 0 }}
                        animate={{ scale: 1, opacity: 1 }}
                        exit={{ scale: 0.95, opacity: 0 }}
                        className="w-full max-w-sm bg-zinc-900 rounded-3xl border border-zinc-800 shadow-2xl overflow-hidden p-6 text-center"
                    >
                        <div className="flex justify-between items-start mb-4">
                            <div className={`w-16 h-16 rounded-full flex items-center justify-center border transition-colors ${isVoiceActive ? 'bg-green-500/20 border-green-500/30 shadow-[0_0_15px_rgba(34,197,94,0.2)]' : 'bg-zinc-800 border-zinc-700'}`}>
                                <HumanHeadSpeaking className={`w-8 h-8 transition-colors ${isVoiceActive ? 'text-green-500' : 'text-zinc-500'}`} />
                            </div>
                            <button onClick={() => { hapticService.light(); onClose(); }} className="p-2 hover:bg-zinc-800 rounded-full text-zinc-400 transition-colors">
                                <X size={20} />
                            </button>
                        </div>
                        
                        <div className="flex items-center justify-between mb-2">
                            <h3 className="text-xl font-bold text-white">Voice SOS</h3>
                            <button
                                onClick={handleToggle}
                                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${isVoiceActive ? 'bg-green-500' : 'bg-zinc-700'}`}
                            >
                                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${isVoiceActive ? 'translate-x-6' : 'translate-x-1'}`} />
                            </button>
                        </div>
                        
                        <p className="text-sm text-zinc-400 mb-6 text-left leading-relaxed">
                            Say <b className="text-white">"Help Me"</b> clearly to trigger the SOS alarm, even when your phone is locked and offline.
                        </p>

                        <div className="bg-zinc-800/50 rounded-xl border border-zinc-700/50 mb-6 overflow-hidden">
                            <div className="px-4 py-2 border-b border-zinc-700/50 bg-zinc-800/30">
                                <h4 className="text-xs font-semibold text-zinc-400 text-left uppercase tracking-wider">Accent Region</h4>
                            </div>
                            
                            <button 
                                onClick={() => handleAccentChange('us')}
                                className="w-full px-4 py-3 flex items-center justify-between hover:bg-zinc-800 transition-colors border-b border-zinc-700/50"
                            >
                                <span className="text-sm font-medium text-white flex items-center gap-2">
                                    <span className="text-lg">🇺🇸</span> English (US)
                                </span>
                                <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors ${accent === 'us' ? 'border-green-500' : 'border-zinc-600'}`}>
                                    {accent === 'us' && <div className="w-2.5 h-2.5 rounded-full bg-green-500" />}
                                </div>
                            </button>
                            
                            <button 
                                onClick={() => handleAccentChange('in')}
                                className="w-full px-4 py-3 flex items-center justify-between hover:bg-zinc-800 transition-colors"
                            >
                                <span className="text-sm font-medium text-white flex items-center gap-2">
                                    <span className="text-lg">🇮🇳</span> English (Indian)
                                </span>
                                <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center transition-colors ${accent === 'in' ? 'border-green-500' : 'border-zinc-600'}`}>
                                    {accent === 'in' && <div className="w-2.5 h-2.5 rounded-full bg-green-500" />}
                                </div>
                            </button>
                        </div>
                        
                        <div className="bg-zinc-800/30 p-3 rounded-xl border border-zinc-800 text-left space-y-3">
                            <p className="text-xs text-zinc-400 flex items-start gap-2">
                                <Shield className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
                                <span><b>100% Private:</b> Audio is analyzed by an on-device AI model. Nothing is recorded or sent to the cloud.</span>
                            </p>
                            <p className="text-xs text-zinc-400 flex items-start gap-2">
                                <AlertTriangle className="w-4 h-4 text-orange-400 shrink-0 mt-0.5" />
                                <span><b>Battery:</b> Continuous listening uses extra battery. We recommend disabling it when safe.</span>
                            </p>
                        </div>
                    </motion.div>
                </div>
            )}
        </AnimatePresence>
    );
};

export default VoiceSosModal;
