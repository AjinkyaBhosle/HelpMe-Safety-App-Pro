import React, { useState, useEffect } from 'react';
import { User, Save, X, Home, Briefcase, HeartPulse, FileText, Droplet } from 'lucide-react';
import { hapticService } from '../services/HapticService';

export default function UserProfileView({ onClose }) {
    const [loading, setLoading] = useState(false);
    const [formData, setFormData] = useState({
        userName: '',
        bloodGroup: '',
        residentialAddress: '',
        workplaceAddress: '',
        allergies: '',
        medicalHistory: ''
    });

    useEffect(() => {
        try {
            const savedProfile = localStorage.getItem('user_profile');
            if (savedProfile) {
                setFormData(JSON.parse(savedProfile));
            }
        } catch (e) {
            console.error("Failed to load profile", e);
        }
    }, []);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const hasData = Object.values(formData).some(val => val && val.trim().length > 0);
        if (!hasData) {
            alert("Please fill at least one field to save details.");
            return;
        }

        hapticService.medium();
        setLoading(true);

        try {
            // Save to LocalStorage
            localStorage.setItem('user_profile', JSON.stringify(formData));

            // Simulating a short delay for feedback
            await new Promise(resolve => setTimeout(resolve, 500));

            hapticService.success();
            onClose();
        } catch (err) {
            alert('Failed to save profile: ' + err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="w-full max-w-md bg-surface p-6 rounded-2xl border border-zinc-800 shadow-xl animate-in fade-in zoom-in duration-300 max-h-[75vh] flex flex-col">
            <div className="flex items-center justify-between mb-6 text-white/90">
                <div className="flex items-center gap-2">
                    <User className="w-5 h-5 text-blue-400" />
                    <h2 className="text-lg font-semibold">User Details</h2>
                </div>
                <button
                    onClick={() => { hapticService.light(); onClose(); }}
                    type="button"
                    className="p-1 hover:bg-zinc-800 rounded-full transition-colors"
                >
                    <X size={20} className="text-zinc-400 hover:text-white" />
                </button>
            </div>

            <p className="text-xs text-zinc-500 mb-4 -mt-3">
                Personal information for emergency and medical reference
            </p>

            <form onSubmit={handleSubmit} className="space-y-4 overflow-y-auto pr-2 pb-4 scrollbar-thin scrollbar-thumb-zinc-700">

                {/* Personal Info */}
                <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-2">
                        <label className="text-xs text-zinc-400 flex items-center gap-1">
                            <User size={12} /> Full Name
                        </label>
                        <input
                            type="text"
                            name="userName"
                            value={formData.userName}
                            onChange={handleChange}
                            className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-blue-500 outline-none"
                            placeholder="John Doe"
                        />
                    </div>
                    <div className="space-y-2">
                        <label className="text-xs text-zinc-400 flex items-center gap-1">
                            <Droplet size={12} /> Blood Group
                        </label>
                        <input
                            type="text"
                            name="bloodGroup"
                            value={formData.bloodGroup}
                            onChange={handleChange}
                            className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-red-500 outline-none"
                            placeholder="O+"
                        />
                    </div>
                </div>

                {/* Addresses */}
                <div className="space-y-2">
                    <label className="text-xs text-zinc-400 flex items-center gap-1">
                        <Home size={12} /> Residential Address
                    </label>
                    <textarea
                        name="residentialAddress"
                        value={formData.residentialAddress}
                        onChange={handleChange}
                        className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-green-500 outline-none resize-none h-20"
                        placeholder="Home Address..."
                    />
                </div>

                <div className="space-y-2">
                    <label className="text-xs text-zinc-400 flex items-center gap-1">
                        <Briefcase size={12} /> Workplace Address
                    </label>
                    <textarea
                        name="workplaceAddress"
                        value={formData.workplaceAddress}
                        onChange={handleChange}
                        className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-purple-500 outline-none resize-none h-20"
                        placeholder="Office Address..."
                    />
                </div>

                {/* Medical */}
                <div className="space-y-2">
                    <label className="text-xs text-zinc-400 flex items-center gap-1">
                        <HeartPulse size={12} /> Allergies
                    </label>
                    <input
                        type="text"
                        name="allergies"
                        value={formData.allergies}
                        onChange={handleChange}
                        className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-yellow-500 outline-none"
                        placeholder="Peanuts, Penicillin..."
                    />
                </div>

                <div className="space-y-2">
                    <label className="text-xs text-zinc-400 flex items-center gap-1">
                        <FileText size={12} /> Medical History
                    </label>
                    <textarea
                        name="medicalHistory"
                        value={formData.medicalHistory}
                        onChange={handleChange}
                        className="w-full bg-void border border-zinc-700 rounded-lg p-3 text-sm text-white focus:border-blue-500 outline-none resize-none h-20"
                        placeholder="Diabetes, Hypertension..."
                    />
                </div>

                <div className="pt-2 sticky bottom-0 bg-surface pb-1">
                    <button
                        type="submit"
                        disabled={loading}
                        className="w-full flex items-center justify-center gap-2 bg-white text-black hover:bg-zinc-200 py-3 rounded-lg font-bold transition-all duration-300 disabled:opacity-50"
                    >
                        <Save size={18} />
                        {loading ? 'Saving...' : 'Save Details'}
                    </button>
                </div>
            </form>
        </div>
    );
}
