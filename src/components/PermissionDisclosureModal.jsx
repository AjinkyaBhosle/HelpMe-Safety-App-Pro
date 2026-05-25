import React from 'react';
import { Shield, AlertTriangle, Check, Send, Phone, MapPin, Mic, Camera, Battery } from 'lucide-react';
import { motion } from 'framer-motion';
import { hapticService } from '../services/HapticService';

const PermissionDisclosureModal = ({ isOpen, onAccept }) => {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 bg-black/90 z-[60] flex items-center justify-center p-6 backdrop-blur-md">
            <motion.div
                initial={{ opacity: 0, scale: 0.95 }}
                animate={{ opacity: 1, scale: 1 }}
                className="bg-zinc-900 w-full max-w-sm rounded-2xl border border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[75vh]"
            >
                {/* Header covering the "Safety Exception" requirement */}
                <div className="bg-blue-900/30 p-6 border-b border-blue-800/50 text-center">
                    <Shield className="w-12 h-12 text-blue-400 mx-auto mb-3" />
                    <h2 className="text-xl font-bold text-white">Essential Permissions</h2>
                </div>

                <div className="p-6 space-y-6 overflow-y-auto pb-4">

                    {/* The Core Disclosure Statement */}
                    <div className="space-y-3">
                        <p className="text-zinc-300 text-sm leading-relaxed">
                            To ensure your safety when you <b>need help</b>, <b>Help Me!</b> needs to perform actions on your behalf even when the app is closed.
                        </p>
                    </div>

                    {/* Specific Permission Breakdown */}
                    <div className="space-y-4">
                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <Send className="w-4 h-4 text-green-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Send SMS</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Directly sends safety alerts to your trusted contacts as configured by you.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <Phone className="w-4 h-4 text-blue-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Make Calls</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Initiates phone calls to your trusted contact.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <MapPin className="w-4 h-4 text-red-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Location</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Attaches your precise coordinates to the alert message.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <Mic className="w-4 h-4 text-purple-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Microphone</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Required for Voice Recorder and Safety Cam. If you enable the Voice SOS feature, it also listens continuously in the background to detect your emergency wake word.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <Camera className="w-4 h-4 text-rose-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Camera</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Used only for the Safety Cam feature to record video overlays. Recording only starts when you tap record.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <AlertTriangle className="w-4 h-4 text-orange-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Background Monitoring</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    With your approval, this ensures the app works reliably in the background when you need it most.
                                </p>
                            </div>
                        </div>

                        <div className="flex gap-3">
                            <div className="mt-1 bg-zinc-800 p-2 rounded-lg h-fit">
                                <Battery className="w-4 h-4 text-yellow-400" />
                            </div>
                            <div>
                                <h3 className="text-white font-medium text-sm">Battery Optimization</h3>
                                <p className="text-zinc-500 text-xs mt-1">
                                    Disabling battery optimization ensures Voice SOS and Safety Timer continue working reliably in the background.
                                </p>
                                <div className="mt-3 bg-red-900/20 border border-red-500/30 rounded p-3">
                                    <h4 className="text-red-400 font-semibold text-xs mb-1">🚨 Critical for Background Reliability:</h4>
                                    <ol className="text-red-200 text-[11px] list-decimal ml-3 space-y-1">
                                        <li>Open your phone's <b>Settings</b> app.</li>
                                        <li>Go to <b>Apps &rarr; App Management &rarr; HelpMe</b> (or <b>Battery &rarr; App Battery Management</b>).</li>
                                        <li>Turn ON <b>"Allow Background Activity"</b> or set to <b>"Unrestricted"</b>.</li>
                                    </ol>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Simple Privacy Policy Link/Text */}
                    <div className="bg-zinc-800/50 p-3 rounded-lg border border-zinc-700/50">
                        <p className="text-xs text-zinc-400 leading-relaxed">
                            <b>Offline First:</b> The core safety features (SOS, SMS, Calls) work <b>without internet</b>.<br />
                            <b>Your Control:</b> Media recording only happens when <b>you</b> tap a button. The Voice SOS wake-word is processed 100% offline by an on-device AI model and is never saved.<br />
                            We do not collect or sell your personal data.
                        </p>
                    </div>
                </div>

                {/* Footer Actions */}
                <div className="p-6 border-t border-zinc-800 bg-zinc-900/50">
                    <button
                        onClick={() => { hapticService.medium(); onAccept(); }}
                        className="w-full bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white font-bold py-3.5 rounded-xl transition flex items-center justify-center gap-2"
                    >
                        <Check className="w-5 h-5" />
                        I Understand & Allow
                    </button>
                </div>

            </motion.div >
        </div >
    );
};

export default PermissionDisclosureModal;
