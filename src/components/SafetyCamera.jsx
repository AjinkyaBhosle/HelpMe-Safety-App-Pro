import React, { useState, useEffect } from 'react';
import { X, Video, Camera } from 'lucide-react';
import { registerPlugin } from '@capacitor/core';
import { getFormattedLocation, generateMapsLink } from '../utils/geoUtils';
import { Share } from '@capacitor/share';
import { Filesystem } from '@capacitor/filesystem';
import { hapticService } from '../services/HapticService';
import { safetyMediaService } from '../services/SafetyMediaService';
import SafetyGalleryModal from './SafetyGalleryModal';

const SafetyCameraPlugin = registerPlugin('SafetyCameraPlugin');

const SafetyCamera = ({ onClose }) => {
    const [locationData, setLocationData] = useState({ text: "Initializing GPS...", lat: null, lng: null });
    const [loadingType, setLoadingType] = useState(null); // 'video' | 'photo' | null
    const [showGallery, setShowGallery] = useState(false);

    // Update GPS periodically
    useEffect(() => {
        const updateGPS = async () => {
            const data = await getFormattedLocation();
            setLocationData(data);
        };

        updateGPS();
        const interval = setInterval(updateGPS, 2000);
        return () => clearInterval(interval);
    }, []);

    const captureMedia = async (type) => {
        hapticService.medium(); // Haptic feedback when starting
        setLoadingType(type);

        try {
            console.log(`[SafetyCam] Launching ${type} camera...`);
            let result;

            const latStr = locationData.lat ? locationData.lat.toFixed(5) : 'Unknown';
            const lngStr = locationData.lng ? locationData.lng.toFixed(5) : 'Unknown';
            const mapLink = generateMapsLink(locationData.lat, locationData.lng);
            
            const watermarkText = `Location: ${mapLink}\nTime: ${new Date().toLocaleString()}`;

            if (type === 'video') {
                result = await SafetyCameraPlugin.captureVideo({ watermark: watermarkText });
            } else {
                result = await SafetyCameraPlugin.capturePhoto({ watermark: watermarkText });
            }

            console.log('[SafetyCam] Camera returned:', result);

            if (result.success && result.filePath) {
                const isVideo = result.type === 'video' || type === 'video';
                const actionText = isVideo ? 'Safety Video Recorded' : 'Safety Photo Captured';

                // Convert absolute path to file:// URI
                const fileUri = `file://${result.filePath}`;

                // Save metadata
                try {
                    await safetyMediaService.saveMediaMetadata(result.filePath, locationData, isVideo ? 'video' : 'photo');
                } catch (metaErr) {
                    console.error('[SafetyCam] Metadata save failed:', metaErr);
                }

                console.log('[SafetyCam] About to share:', { fileUri });

                // Share (No text payload sent so there's no editable caption)
                try {
                    await Share.share({
                        title: actionText,
                        url: fileUri,
                        dialogTitle: `Share ${isVideo ? 'Video' : 'Photo'}`
                    });
                } catch (shareError) {
                    console.error('[SafetyCam] Share failed:', shareError);
                }
            }
        } catch (error) {
            console.error('[SafetyCam] Camera error:', error);
            alert('Camera failed: ' + error);
        } finally {
            setLoadingType(null);
        }
    };

    return (
        <div className="fixed inset-0 bg-gradient-to-br from-zinc-900 via-black to-zinc-900 z-[100] flex flex-col items-center justify-center">
            {/* Header - Reverted to SS3 Style: Title Left, Close Right */}
            <div className="absolute top-0 left-0 right-0 pt-12 pb-6 px-6 flex justify-between items-center z-10 bg-gradient-to-b from-black/80 to-transparent">
                <div className="flex items-center gap-2 text-white">
                    <Video className="text-red-500 w-6 h-6" />
                    <span className="text-lg font-bold">Safety Cam</span>
                </div>

                <button
                    onClick={() => { hapticService.light(); onClose(); }}
                    className="p-2 bg-zinc-800/50 hover:bg-zinc-700/50 rounded-full backdrop-blur-md text-white transition-colors"
                >
                    <X className="w-6 h-6" />
                </button>
            </div>

            {/* Center Content */}
            <div className="flex flex-col items-center justify-center gap-8 px-6 w-full max-w-md mt-10">
                {/* Camera Icon */}
                <div className="relative mb-4">
                    <div className="w-32 h-32 rounded-full bg-red-500/10 border-4 border-red-500 flex items-center justify-center animate-pulse">
                        <Video className="w-16 h-16 text-red-500" />
                    </div>
                    {locationData.lat && (
                        <div className="absolute -top-2 -right-2 w-8 h-8 bg-green-500 rounded-full flex items-center justify-center">
                            <span className="text-white text-xs font-bold">✓</span>
                        </div>
                    )}
                </div>

                {/* Buttons Container */}
                <div className="flex flex-col gap-4 w-full">
                    {/* Record Video - Red */}
                    <button
                        onClick={() => captureMedia('video')}
                        disabled={loadingType !== null || !locationData.lat}
                        className={`w-full py-5 rounded-2xl text-white font-bold text-xl shadow-2xl transition-all transform active:scale-95 flex items-center justify-center gap-3
                            ${loadingType === 'video' ? 'bg-red-700 cursor-wait' : 'bg-red-600 hover:bg-red-500 shadow-red-500/30'}
                            ${loadingType === 'photo' ? 'opacity-50 cursor-not-allowed' : ''}
                            ${!locationData.lat ? 'opacity-50 cursor-not-allowed' : ''}
                        `}
                    >
                        <Video className="w-6 h-6" />
                        {loadingType === 'video' ? 'Processing...' : 'Record Video'}
                    </button>

                    {/* Take Photo - Blue */}
                    <button
                        onClick={() => captureMedia('photo')}
                        disabled={loadingType !== null || !locationData.lat}
                        className={`w-full py-5 rounded-2xl text-white font-bold text-xl shadow-xl transition-all transform active:scale-95 flex items-center justify-center gap-3
                            ${loadingType === 'photo' ? 'bg-blue-700 cursor-wait' : 'bg-gradient-to-r from-blue-600 to-blue-500 hover:from-blue-500 hover:to-blue-400'}
                             ${loadingType === 'video' ? 'opacity-50 cursor-not-allowed' : ''}
                             ${!locationData.lat ? 'opacity-50 cursor-not-allowed' : ''}
                        `}
                    >
                        <Camera className="w-6 h-6" />
                        {loadingType === 'photo' ? 'Processing...' : 'Take Photo'}
                    </button>
                </div>

                {/* Info Text */}
                <div className="text-center max-w-sm space-y-2">
                    <p className="text-zinc-300 text-sm font-medium">
                        📹 Location & Time are burned into media
                    </p>
                    <p className="text-zinc-500 text-xs leading-relaxed px-4">
                        Permanently watermarked on photo/video files while sharing to prevent tampering
                    </p>
                </div>

                {/* Gallery Button */}
                <button
                    onClick={() => { hapticService.light(); setShowGallery(true); }}
                    className="mt-2 px-6 py-2 bg-zinc-800/80 rounded-full border border-zinc-700 text-zinc-300 text-sm flex items-center gap-2 hover:bg-zinc-700 transition"
                >
                    <div className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />
                    Video/Photo Gallery
                </button>

                {/* Bottom Status - Moved into flow to prevent overlap */}
                <div className="pt-4 pb-8">
                    <div className="px-6 py-3 bg-white/5 backdrop-blur-md rounded-full border border-white/10">
                        <p className="text-zinc-300 text-sm font-medium">
                            {locationData.lat ? '✓ GPS Ready' : '⌛ Waiting for GPS signal...'}
                        </p>
                    </div>
                </div>
            </div>

            <SafetyGalleryModal
                isOpen={showGallery}
                onClose={() => setShowGallery(false)}
            />
        </div>
    );
};

export default SafetyCamera;
