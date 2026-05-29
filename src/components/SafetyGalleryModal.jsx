import React, { useState, useEffect, useRef } from 'react';
import { X, Share2, Trash2, MapPin, Play, Pause, AlertTriangle, Image as ImageIcon, Video as VideoIcon } from 'lucide-react';
import { Share } from '@capacitor/share';
import { Dialog } from '@capacitor/dialog';
import { safetyMediaService } from '../services/SafetyMediaService';
import { hapticService } from '../services/HapticService';
import { generateMapsLink } from '../utils/geoUtils';
import ConfirmDialog from './ConfirmDialog';

const SafetyGalleryModal = ({ isOpen, onClose }) => {
    const [mediaList, setMediaList] = useState([]);
    const [selectedMedia, setSelectedMedia] = useState(null); // Rename selectedVideo to selectedMedia
    const [isPlaying, setIsPlaying] = useState(false);
    const [deleteId, setDeleteId] = useState(null);
    const videoRef = useRef(null);

    useEffect(() => {
        if (isOpen) {
            loadMedia();
        } else {
            // Cleanup when closing
            setSelectedMedia(null);
            setIsPlaying(false);
        }
    }, [isOpen]);

    const loadMedia = async () => {
        const list = await safetyMediaService.getMediaList();
        setMediaList(list);
    };

    const isVideo = (media) => {
        return media.type === 'video' || media.fileName.endsWith('.mp4');
    };

    const handleMediaClick = (media) => {
        hapticService.light();

        if (selectedMedia?.fileName === media.fileName) {
            // Toggle play/pause for video
            if (isVideo(media) && videoRef.current) {
                if (isPlaying) {
                    videoRef.current.pause();
                } else {
                    videoRef.current.play();
                }
                setIsPlaying(!isPlaying);
            }
        } else {
            // New selection
            setSelectedMedia(media);
            setIsPlaying(true); // Auto-play video or just show image
        }
    };

    const handleShare = async (media, e) => {
        e.stopPropagation();
        hapticService.medium();

        try {
            const mapLink = generateMapsLink(media.location.lat, media.location.lng);
            const typeStr = isVideo(media) ? 'Video' : 'Photo';
            const shareText = `🚨 Safety ${typeStr} Recorded\n📍 Location: ${mapLink}\n📌 ${media.location.text || 'Location available'}\n🕒 ${new Date(media.createdAt).toLocaleString()}`;

            // Allow file:// uri for sharing
            const fileUri = `file://${media.fullPath}`;

            await Share.share({
                title: `Safety Cam ${typeStr}`,
                text: shareText,
                url: fileUri,
                dialogTitle: `Share ${typeStr}`
            });
        } catch (error) {
            console.error("Share failed:", error);
            await Dialog.alert({
                title: 'Error',
                message: "Share failed: " + error.message
            });
        }
    };

    const confirmDelete = (e, media) => {
        e.stopPropagation();
        hapticService.medium();
        setDeleteId(media.fileName);
    };

    const handleDelete = async () => {
        if (!deleteId) return;

        await safetyMediaService.deleteMedia(deleteId);

        if (selectedMedia?.fileName === deleteId) {
            setSelectedMedia(null);
            setIsPlaying(false);
        }

        setDeleteId(null);
        loadMedia();
        hapticService.success();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[110] bg-black text-white flex flex-col animate-in fade-in duration-200">
            {/* Header */}
            <div className="flex items-center justify-between px-6 pb-6 pt-14 bg-zinc-900 border-b border-zinc-800">
                <h2 className="text-xl font-bold flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" />
                    Video/Photo Gallery
                </h2>
                <button
                    onClick={() => { hapticService.light(); onClose(); }}
                    className="p-2 bg-zinc-800 rounded-full hover:bg-zinc-700 transition"
                >
                    <X size={20} />
                </button>
            </div>

            {/* Media Viewer (Sticky Top) */}
            {selectedMedia && (
                <div className="w-full bg-black aspect-video relative border-b border-zinc-800 flex items-center justify-center overflow-hidden">
                    {isVideo(selectedMedia) ? (
                        <video
                            ref={videoRef}
                            src={window.Capacitor.convertFileSrc(selectedMedia.fullPath)}
                            className="w-full h-full object-contain"
                            controls
                            autoPlay
                            onPlay={() => setIsPlaying(true)}
                            onPause={() => setIsPlaying(false)}
                            onError={(e) => console.error("Video Error:", e)}
                        />
                    ) : (
                        <img
                            src={window.Capacitor.convertFileSrc(selectedMedia.fullPath)}
                            alt="Safety Capture"
                            className="w-full h-full object-contain"
                        />
                    )}

                    <button
                        onClick={() => setSelectedMedia(null)}
                        className="absolute top-2 right-2 p-1 bg-black/50 rounded-full text-white/70 hover:text-white z-10"
                    >
                        <X size={16} />
                    </button>

                    {/* Location Overlay */}
                    <div className="absolute bottom-0 left-0 right-0 p-2 bg-gradient-to-t from-black/80 to-transparent">
                        <p className="text-xs text-zinc-300 flex items-center gap-1">
                            <MapPin size={10} />
                            {selectedMedia.location.lat.toFixed(6)}, {selectedMedia.location.lng.toFixed(6)}
                        </p>
                    </div>
                </div>
            )}

            {/* List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-3">
                {mediaList.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-zinc-500 space-y-4">
                        <div className="w-16 h-16 rounded-full bg-zinc-900 flex items-center justify-center">
                            <AlertTriangle size={32} opacity={0.5} />
                        </div>
                        <p>No recorded safety media yet.</p>
                    </div>
                ) : (
                    mediaList.map((media) => (
                        <div
                            key={media.fileName}
                            onClick={() => handleMediaClick(media)}
                            className={`flex flex-col bg-zinc-900 rounded-xl overflow-hidden border transition-all active:scale-[0.98] ${selectedMedia?.fileName === media.fileName ? 'border-red-500/50 bg-zinc-800' : 'border-zinc-800'}`}
                        >
                            <div className="p-4 flex items-center justify-between">
                                <div className="flex items-center gap-4">
                                    {/* Icon showing type and state */}
                                    <div className={`w-10 h-10 rounded-full flex items-center justify-center ${selectedMedia?.fileName === media.fileName ? 'bg-red-500/20 text-red-500' : 'bg-zinc-800 text-zinc-400'}`}>
                                        {isVideo(media) ? (
                                            selectedMedia?.fileName === media.fileName && isPlaying ? <Pause size={20} fill="currentColor" /> : <Play size={20} fill="currentColor" />
                                        ) : (
                                            <ImageIcon size={20} />
                                        )}
                                    </div>
                                    <div>
                                        <p className="font-medium text-sm text-zinc-200 flex items-center gap-2">
                                            {isVideo(media) ? 'Video' : 'Photo'}
                                            <span className="text-xs text-zinc-500">• {new Date(media.createdAt).toLocaleDateString()}</span>
                                        </p>
                                        <p className="text-xs text-zinc-500">
                                            {new Date(media.createdAt).toLocaleTimeString()}
                                        </p>
                                    </div>
                                </div>

                                <div className="flex items-center gap-2">
                                    <button
                                        onClick={(e) => handleShare(media, e)}
                                        className="p-3 hover:bg-zinc-700/50 rounded-full text-zinc-400 hover:text-green-500 transition"
                                    >
                                        <Share2 size={18} />
                                    </button>
                                    <button
                                        onClick={(e) => confirmDelete(e, media)}
                                        className="p-3 hover:bg-zinc-700/50 rounded-full text-zinc-400 hover:text-red-500 transition"
                                    >
                                        <Trash2 size={18} />
                                    </button>
                                </div>
                            </div>

                            {/* Location Footer inside card */}
                            <div className="px-4 py-2 bg-black/20 text-xs text-zinc-500 border-t border-zinc-800/50 flex items-center gap-2">
                                <MapPin size={10} />
                                <span className="truncate">{media.location.text || `${media.location.lat}, ${media.location.lng}`}</span>
                            </div>
                        </div>
                    ))
                )}
            </div>

            <ConfirmDialog
                isOpen={!!deleteId}
                title="Delete Media?"
                message="This recording will be permanently deleted."
                confirmText="Delete"
                isDestructive={true}
                onConfirm={handleDelete}
                onCancel={() => setDeleteId(null)}
            />
        </div>
    );
};

export default SafetyGalleryModal;
