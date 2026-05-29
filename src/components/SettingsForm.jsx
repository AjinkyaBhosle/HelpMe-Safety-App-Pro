import React, { useState, useEffect } from 'react';
import { Settings, Save, Smartphone, Clock, X, MapPin, Mic } from 'lucide-react';
import { motion } from 'framer-motion';
import { Geolocation } from '@capacitor/geolocation';
import { Dialog } from '@capacitor/dialog';
import { hapticService } from '../services/HapticService';

export default function SettingsForm({ userSettings, onSave, onCancel, loading }) {
    // userSettings comes from SmsPlugin now: { phoneNumbers, intervalMinutes, ... }
    // We'll map it to local state.

    const [formData, setFormData] = useState({
        email: localStorage.getItem('user_email') || '',
        emergencyPhone: ''
    });

    useEffect(() => {
        if (userSettings) {
            setFormData(prev => ({
                ...prev,
                emergencyPhone: userSettings.emergencyPhone || userSettings.phoneNumbers || ''
            }));
        }
    }, [userSettings]);

    const handleChange = (e) => {
        const value = e.target.type === 'checkbox' ? e.target.checked : e.target.value;
        setFormData({ ...formData, [e.target.name]: value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // 1. Robust Parsing (Split by comma, space, newline, semicolon)
        // This accepts: "123 456" or "123, 456" or "123\n456"
        let phones = formData.emergencyPhone
            .split(/[,;\n]+/)                // Split by comma, semicolon, or newline (NOT spaces)
            .map(p => p.trim())                // Clean whitespace
            .filter(p => p.length >= 3);       // Remove empty/garbage (min 3 chars for a number)

        // 2. Validate Max Limit
        if (phones.length > 5) {
            await Dialog.alert({
                title: 'Limit Exceeded',
                message: `You can only add up to 5 emergency contacts.\n\nWe found ${phones.length} numbers. Please remove ${phones.length - 5}.\n\n(Parsed: ${phones.join(', ')})`
            });
            return;
        }

        // 3. Validate Individual Number Length (Strict Indian/E.164 Standards)
        // India: 10 digits (local) to ~13/14 digits (with +91 or 0091)
        for (const phone of phones) {
            // Count actual digits only (ignore +, -, etc)
            const digitCount = phone.replace(/[^0-9]/g, '').length;

            if (digitCount < 3) {
                await Dialog.alert({
                    title: 'Invalid Number',
                    message: `Invalid Number: "${phone}"\n\nToo short! Please enter a valid mobile or emergency number.\n(You entered ${digitCount} digits)`
                });
                return;
            }
            if (digitCount > 15) {
                await Dialog.alert({
                    title: 'Invalid Number',
                    message: `Invalid Number: "${phone}"\n\nToo long! Max 15 digits allowed globally (including country code).\n(You entered ${digitCount} digits).\n\nDid you forget a comma?`
                });
                return;
            }
        }

        // 3. Auto-Format: Save as clean comma-separated string
        const formattedPhoneNumbers = phones.join(',');

        // Update formData with the clean list before sending
        const payload = {
            ...formData,
            emergencyPhone: formattedPhoneNumbers
        };

        // Success haptic
        hapticService.success();

        onSave(payload);
    };

    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="w-full max-w-md bg-surface p-6 rounded-2xl border border-zinc-800 shadow-xl"
        >
            <div className="flex items-center justify-between mb-6 text-white/90">
                <div className="flex items-center gap-2">
                    <Settings className="w-5 h-5" />
                    <h2 className="text-lg font-semibold">Configure Settings</h2>
                </div>
                <button
                    onClick={() => { hapticService.light(); onCancel(); }}
                    type="button"
                    className="p-1 hover:bg-zinc-800 rounded-full transition-colors"
                >
                    <X size={20} className="text-zinc-400 hover:text-white" />
                </button>
            </div>

            <form onSubmit={handleSubmit} className="space-y-5">
                <div className="p-3 bg-blue-900/20 border border-blue-800 rounded-lg text-sm text-blue-300">
                    ℹ️ <b>Tip:</b> Keep your GPS enabled for accurate location sharing during an emergency.
                </div>

                {/* Emergency Contact */}
                <div className="space-y-3">
                    <h3 className="text-xs font-bold text-muted uppercase tracking-wider flex items-center gap-2">
                        <Smartphone size={14} /> Emergency Contacts
                    </h3>
                    <input
                        type="tel"
                        name="emergencyPhone"
                        value={formData.emergencyPhone}
                        onChange={handleChange}
                        className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-white placeholder-zinc-600 focus:border-danger outline-none"
                        placeholder="e.g. 9876543210, +919876543210"
                        required
                    />
                    <div className="text-xs text-zinc-500 space-y-1 mt-1">
                        <p>Separate multiple numbers with commas.</p>
                        <p><b>Format:</b> Plain 10-digits for Domestic (e.g., 9876543210), or use +CountryCode for International (e.g., +919876543210).</p>
                        <p className="text-yellow-500/80"><b>Recommendation:</b> Place a trusted personal contact (Family/Friend) as the very <b>first</b> number. Android restricts automated calls to standard emergency numbers (100, 112, etc.).</p>
                    </div>
                </div>

                <div className="pt-2">
                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full flex items-center justify-center gap-2 bg-white text-black hover:bg-zinc-200 py-3 rounded-lg font-bold transition-all duration-300 disabled:opacity-50"
                    >
                        <Save size={18} />
                        {loading ? 'Saving...' : 'Save Configuration'}
                    </button>
                </div>
            </form>
        </motion.div>
    );
}
