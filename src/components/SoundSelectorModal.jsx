import React, { useState } from 'react';
import { Settings, X, Siren, Bell, Mic, Dog, Skull, Car } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { sirenService } from '../services/SirenService';
import { hapticService } from '../services/HapticService';

// Custom Whistle Icon to match user request (Dynamic Action Style)
const WhistleIcon = ({ size = 24, className }) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
    >
        {/* Whistle Body & Mouthpiece (Angled Up-Right) */}
        {/* Body Arc */}
        <path d="M12.6 11.4c.5.5 1.4 1.4 1.4 2.6a4 4 0 1 1-6.8-2.8" />
        {/* Top Line to Mouthpiece */}
        <path d="M7.2 11.2l9-3" />
        {/* Mouthpiece End */}
        <path d="M16.2 8.2l2 3-3 2" />
        {/* Connection back to body */}
        <path d="M15.2 13.2l-1.2-1.2" />

        {/* The Pea / Hole */}
        <circle cx="9" cy="15" r="1.5" />

        {/* Lanyard Loop (Bottom Left) */}
        <path d="M6 14l-1 1" />
        <circle cx="4" cy="16" r="1.5" />

        {/* Sound Waves (Radiating from Mouthpiece) */}
        <path d="M19 5l2-2" />
        <path d="M21 9l2-1" />
        <path d="M20 12l2 2" />
    </svg>
);

const soundOptions = [
    {
        id: 'siren',
        label: 'Siren',
        icon: Siren,
        color: 'text-blue-500',
        bg: 'bg-blue-500/10',
        activeBg: 'bg-blue-500/30'
    },
    {
        id: 'alarm',
        label: 'Alarm',
        icon: Bell,
        color: 'text-red-500',
        bg: 'bg-red-500/10',
        activeBg: 'bg-red-500/30'
    },
    {
        id: 'dog',
        label: 'Dog Bark',
        icon: Dog,
        color: 'text-amber-700',
        bg: 'bg-amber-700/10',
        activeBg: 'bg-amber-700/30'
    },
    {
        id: 'whistle',
        label: 'Whistle',
        icon: WhistleIcon,
        color: 'text-yellow-500',
        bg: 'bg-yellow-500/10',
        activeBg: 'bg-yellow-500/30'
    },
    {
        id: 'carhorn',
        label: 'Car Horn',
        icon: Car,
        color: 'text-indigo-500',
        bg: 'bg-indigo-500/10',
        activeBg: 'bg-indigo-500/30'
    },
    {
        id: 'scream',
        label: 'Scream',
        icon: Skull,
        color: 'text-purple-500',
        bg: 'bg-purple-500/10',
        activeBg: 'bg-purple-500/30'
    }
];

const SoundSelectorModal = ({ isOpen, onClose }) => {
    const [activeSound, setActiveSound] = useState(null);

    const handleSoundClick = (soundId) => {
        hapticService.light();
        if (activeSound === soundId) {
            // Stop
            sirenService.stop(soundId);
            setActiveSound(null);
        } else {
            // Stop previous if any
            if (activeSound) {
                sirenService.stop(activeSound);
            }
            // Start new
            sirenService.start(soundId);
            setActiveSound(soundId);
        }
    };

    const handleClose = () => {
        hapticService.light();
        if (activeSound) {
            sirenService.stop(activeSound);
            setActiveSound(null);
        }
        onClose();
    };

    return (
        <div className="w-full max-w-sm bg-zinc-900 rounded-2xl border border-zinc-800 shadow-2xl p-6 animate-in fade-in zoom-in duration-300 max-h-[85vh] flex flex-col">
            <div className="flex justify-between items-center mb-6">
                <h3 className="text-xl font-bold text-white">Safety Sounds</h3>
                <button
                    onClick={handleClose}
                    className="p-2 hover:bg-zinc-800 rounded-full text-zinc-400"
                >
                    <X size={20} />
                </button>
            </div>

            <div className="grid grid-cols-2 gap-4 overflow-y-auto p-2 -m-2 scrollbar-thin scrollbar-thumb-zinc-700">
                {soundOptions.map((sound) => {
                    const Icon = sound.icon;
                    const isActive = activeSound === sound.id;

                    return (
                        <button
                            key={sound.id}
                            onClick={() => handleSoundClick(sound.id)}
                            className={`p-4 rounded-xl flex flex-col items-center gap-3 transition-all border ${isActive
                                ? `${sound.activeBg} border-${sound.color.split('-')[1]}-500/50 scale-105`
                                : `${sound.bg} border-transparent hover:border-zinc-700`
                                }`}
                        >
                            <div className={`${sound.color} ${isActive ? 'animate-pulse' : ''} p-2 bg-black/20 rounded-full`}>
                                <Icon size={32} />
                            </div>
                            <span className={`font-medium ${isActive ? 'text-white' : 'text-zinc-300'}`}>
                                {sound.label}
                            </span>
                        </button>
                    );
                })}
            </div>

            <p className="text-xs text-center text-zinc-500 mt-6">
                Tap to play/stop. Audio continues playing even in the background.
            </p>
        </div>
    );
};

export default SoundSelectorModal;
