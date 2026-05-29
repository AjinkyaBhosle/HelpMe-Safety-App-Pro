import React from 'react';
import { Sparkles, X, AlertCircle } from 'lucide-react';
import { hapticService } from '../services/HapticService';

const ProUpgradeModal = ({ isOpen, onClose, onUpgrade, onRestore, isUpgrading, error }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-black/80 backdrop-blur-sm animate-in fade-in duration-300">
            <div className="w-full max-w-sm bg-surface rounded-3xl border border-zinc-800 shadow-2xl overflow-hidden animate-in zoom-in-95 duration-300">

                {/* Header Image / Pattern Area */}
                <div className="h-32 bg-gradient-to-br from-red-600/20 to-orange-500/20 relative flex items-center justify-center overflow-hidden">
                    <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAiIGhlaWdodD0iMjAiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGNpcmNsZSBjeD0iMSIgY3k9IjEiIHI9IjEiIGZpbGw9InJnYmEoMjU1LDI1NSwyNTUsMC4wNSkiLz48L3N2Zz4=')] [mask-image:linear-gradient(to_bottom,white,transparent)]"></div>

                    <div className="relative z-10 p-4 bg-black/40 rounded-full backdrop-blur-md border border-white/10 shadow-lg">
                        <Sparkles className="w-8 h-8 text-yellow-500" />
                    </div>

                    <button
                        onClick={() => { hapticService.light(); onClose(); }}
                        className="absolute top-4 right-4 p-2 bg-black/40 hover:bg-black/60 rounded-full backdrop-blur-md border border-white/10 transition-colors z-20"
                    >
                        <X size={16} className="text-zinc-300" />
                    </button>
                </div>

                {/* Content */}
                <div className="p-6 pt-5">
                    <div className="text-center mb-6">
                        <h2 className="text-2xl font-bold text-white tracking-tight mb-2">Unlock Pro</h2>
                        <p className="text-zinc-400 text-sm">
                            Keep the core SOS features free, forever. Upgrade to Pro to access advanced safety tools.
                        </p>
                    </div>

                    <div className="space-y-4 mb-8 h-[300px] overflow-y-auto scrollbar-thin scrollbar-thumb-zinc-700">
                        <FeatureRow icon="🗣️" title="Voice SOS" desc="Hands-free trigger by saying 'Help Me'" />
                        <FeatureRow icon="📸" title="Safety Cam" desc="Record videos with GPS overlays" />
                        <FeatureRow icon="⏰" title="Scheduled Check-in" desc="Dead-man's switch for safety" />
                    </div>

                    {error && (
                        <div className="mb-4 p-3 rounded-xl bg-red-900/30 border border-red-800 flex items-start gap-2">
                            <AlertCircle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />
                            <p className="text-xs text-red-200">{error}</p>
                        </div>
                    )}

                    <div className="flex flex-col gap-3">
                        <button
                            onClick={() => { hapticService.medium(); onUpgrade(); }}
                            disabled={isUpgrading}
                            className="w-full relative overflow-hidden bg-gradient-to-r from-red-600 to-red-500 hover:from-red-500 hover:to-red-400 text-white py-4 rounded-xl font-bold shadow-[0_0_20px_rgba(220,38,38,0.3)] transition-all active:scale-[0.98] disabled:opacity-70 disabled:active:scale-100 flex items-center justify-center gap-2"
                        >
                            {isUpgrading ? (
                                <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                            ) : (
                                <>
                                    <span>Unlock Pro - ₹99</span>
                                </>
                            )}
                        </button>

                        <button
                            onClick={() => { hapticService.light(); onRestore(); }}
                            disabled={isUpgrading}
                            className="w-full py-3 text-sm text-zinc-400 font-medium hover:text-white transition-colors disabled:opacity-50"
                        >
                            Restore Purchase
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

const FeatureRow = ({ icon, title, desc }) => (
    <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-zinc-800/50 flex items-center justify-center text-lg border border-zinc-800 shrink-0">
            {icon}
        </div>
        <div>
            <h4 className="text-white font-medium text-sm">{title}</h4>
            <p className="text-zinc-500 text-xs">{desc}</p>
        </div>
    </div>
);

export default ProUpgradeModal;
