import React, { useState, useEffect, useRef } from 'react';
import { X, Mic, Square, Play, Trash2, Save, FileAudio, Pause, Share2 } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { audioRecorder } from '../services/AudioRecorderService';
import { hapticService } from '../services/HapticService';
import { getFormattedLocation, generateMapsLink } from '../utils/geoUtils';
import ConfirmDialog from './ConfirmDialog';
import { Share } from '@capacitor/share';

const VoiceRecorderModal = ({ isOpen, onClose }) => {
    const [isRecording, setIsRecording] = useState(false);
    const [recordings, setRecordings] = useState([]);
    const [recordingTime, setRecordingTime] = useState(0);
    const [confirmDialog, setConfirmDialog] = useState({ isOpen: false, fileName: null });

    // Playback State
    const [playingFile, setPlayingFile] = useState(null); // Name of file
    const [isPlaying, setIsPlaying] = useState(false);
    const [progress, setProgress] = useState(0); // 0-100
    const [duration, setDuration] = useState(0);
    const [currTime, setCurrTime] = useState(0);

    const timerRef = useRef(null);
    const audioRef = useRef(null); // Holds the current HTMLAudioElement

    useEffect(() => {
        if (isOpen) {
            loadRecordings();
        }
        return () => {
            if (timerRef.current) clearInterval(timerRef.current);
            if (isRecording) stopRecording();
            stopPlayback(); // Cleanup audio on close
        };
    }, [isOpen]);

    const loadRecordings = async () => {
        const files = await audioRecorder.getRecordings();
        setRecordings(files);
    };

    const startRecording = async () => {
        try {
            hapticService.medium();
            stopPlayback(); // Stop any playing audio before recording
            await audioRecorder.startRecording();
            setIsRecording(true);
            setRecordingTime(0);
            timerRef.current = setInterval(() => {
                setRecordingTime(prev => prev + 1);
            }, 1000);
        } catch (e) {
            alert("Could not access microphone.");
        }
    };

    const stopRecording = async () => {
        hapticService.success();
        if (timerRef.current) clearInterval(timerRef.current);
        setIsRecording(false);
        try {
            await audioRecorder.stopRecording();
            loadRecordings();
        } catch (e) {
            console.error(e);
        }
    };

    const handleShare = async (fileName) => {
        hapticService.light();
        try {
            const uri = await audioRecorder.getRecordingFileUri(fileName);

            let shareText = `🎙️ Voice Recording\n`;

            try {
                // Get Location
                const location = await getFormattedLocation();
                const mapLink = generateMapsLink(location.lat, location.lng);

                // Add Location Data
                shareText += `📍 Location: ${mapLink}\n`;
                if (location.text) shareText += `📌 ${location.text}\n`;
                // Add Lat/Long text for offline reference
                if (location.lat) shareText += `📌 Lat: ${location.lat} | Long: ${location.lng}\n`;

            } catch (e) {
                console.warn("Location fetch failed for sharing", e);
                shareText += `📍 Location: Unavailable\n`;
            }

            // Add Time
            shareText += `🕒 ${new Date().toLocaleString()}\n\nShared via Help Me! app`;

            console.log('[VoiceRecorder] Sharing with payload:', {
                title: 'Voice Recording',
                text: shareText,
                url: uri,
                dialogTitle: 'Share Recording (Verifying)'
            });

            await Share.share({
                title: 'Voice Recording',
                text: shareText,
                url: uri,
                dialogTitle: 'Share Recording (Verifying)'
            });

        } catch (e) {
            // Ignore user cancellation errors
            if (e.message !== 'Share canceled') {
                console.warn("Share failed or cancelled", e);
            }
        }
    };

    // --- Playback Logic ---

    const stopPlayback = () => {
        if (audioRef.current) {
            audioRef.current.pause();
            audioRef.current = null;
        }
        setPlayingFile(null);
        setIsPlaying(false);
        setProgress(0);
        setCurrTime(0);
        setDuration(0);
    };

    const handlePlayPause = async (fileName) => {
        hapticService.light();
        // If clicking the same file that is already active
        if (playingFile === fileName && audioRef.current) {
            if (isPlaying) {
                audioRef.current.pause();
                setIsPlaying(false);
            } else {
                audioRef.current.play();
                setIsPlaying(true);
            }
            return;
        }

        // If clicking a new file, stop old one
        stopPlayback();

        try {
            const audio = await audioRecorder.playRecording(fileName);
            audioRef.current = audio;
            setPlayingFile(fileName);
            setIsPlaying(true);

            // Audio Events
            audio.ontimeupdate = () => {
                if (audio.duration) {
                    setCurrTime(audio.currentTime);
                    setProgress((audio.currentTime / audio.duration) * 100);
                }
            };

            audio.onloadedmetadata = () => {
                setDuration(audio.duration);
            };

            audio.onended = () => {
                setIsPlaying(false);
                setProgress(100);
            };

        } catch (e) {
            alert("Failed to play file");
        }
    };

    const handleSeek = (e) => {
        if (audioRef.current && duration) {
            const val = parseFloat(e.target.value);
            const newTime = (val / 100) * duration;
            audioRef.current.currentTime = newTime;
            setCurrTime(newTime);
            setProgress(val);
        }
    };

    const deleteRecording = async (fileName) => {
        hapticService.light();
        // Small delay to ensure haptic triggers before confirm dialog
        await new Promise(resolve => setTimeout(resolve, 50));
        setConfirmDialog({ isOpen: true, fileName });
    };

    const handleConfirmDelete = async () => {
        if (playingFile === confirmDialog.fileName) stopPlayback();
        await audioRecorder.deleteRecording(confirmDialog.fileName);
        loadRecordings();
        setConfirmDialog({ isOpen: false, fileName: null });
    };

    const handleCancelDelete = () => {
        setConfirmDialog({ isOpen: false, fileName: null });
    };

    const formatTime = (seconds) => {
        if (!seconds && seconds !== 0) return "0:00";
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    return (
        <div className="w-full max-w-sm bg-zinc-900 rounded-2xl border border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[80vh] animate-in fade-in zoom-in duration-300">
            {/* Header */}
            <div className="flex justify-between items-center p-4 border-b border-zinc-800">
                <h3 className="text-xl font-bold text-white flex items-center gap-2">
                    <Mic className="text-red-500" /> Record & Share Audio
                </h3>
                <button onClick={() => { hapticService.light(); onClose(); }} className="p-2 hover:bg-zinc-800 rounded-full text-zinc-400">
                    <X size={20} />
                </button>
            </div>

            {/* Recorder Area */}
            <div className="p-8 flex flex-col items-center justify-center bg-zinc-900/50">
                <p className="text-xs text-zinc-400 mb-6 text-center max-w-[260px] leading-relaxed">
                    Record audio and instantly share the file with your contacts
                </p>
                <div className="mb-4 text-4xl font-mono text-white font-bold">
                    {formatTime(recordingTime)}
                </div>

                {!isRecording ? (
                    <button
                        onClick={startRecording}
                        className="w-20 h-20 rounded-full bg-red-600 hover:bg-red-500 flex items-center justify-center shadow-lg shadow-red-900/20 transition-all active:scale-95"
                    >
                        <Mic size={32} className="text-white" />
                    </button>
                ) : (
                    <button
                        onClick={stopRecording}
                        className="w-20 h-20 rounded-full bg-zinc-800 border-4 border-red-500 animate-pulse flex items-center justify-center"
                    >
                        <Square size={32} className="text-red-500 fill-current" />
                    </button>
                )}
                <p className="mt-4 text-zinc-500 text-sm">
                    {isRecording ? "Recording..." : "Tap to Record"}
                </p>
            </div>

            {/* Saved List inside Scroll Area */}
            <div className="flex-1 overflow-y-auto p-4 border-t border-zinc-800 bg-zinc-950/50">
                <h4 className="text-xs font-semibold text-zinc-500 uppercase tracking-wider mb-3">
                    Saved Recordings
                </h4>
                {recordings.length === 0 ? (
                    <div className="text-zinc-600 text-center py-8 text-sm italic">
                        No recordings yet
                    </div>
                ) : (
                    <div className="space-y-3">
                        {recordings.map((file) => {
                            const isThisPlaying = playingFile === file.name;
                            return (
                                <div key={file.name} className={`p-3 bg-zinc-900 rounded-lg border transition-all ${isThisPlaying ? 'border-red-500/50 bg-zinc-800' : 'border-zinc-800'}`}>
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="flex items-center gap-3 overflow-hidden">
                                            <div className={`p-2 rounded-full ${isThisPlaying ? 'text-red-500 bg-red-500/10' : 'text-zinc-400 bg-zinc-800'}`}>
                                                <FileAudio size={16} />
                                            </div>
                                            <div className="flex-1 min-w-0">
                                                <p className={`text-sm truncate ${isThisPlaying ? 'text-red-400 font-medium' : 'text-white'}`}>{file.name}</p>
                                                <p className="text-xs text-zinc-500 font-mono">
                                                    {new Date(file.createdAt).toLocaleString()}
                                                </p>
                                            </div>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <button
                                                onClick={() => handleShare(file.name)}
                                                className="p-2 hover:bg-green-500/10 text-zinc-400 hover:text-green-500 rounded-full transition-colors"
                                            >
                                                <Share2 size={16} />
                                            </button>
                                            <button
                                                onClick={() => handlePlayPause(file.name)}
                                                className={`p-2 rounded-full transition-colors ${isThisPlaying && isPlaying
                                                    ? 'bg-red-500 text-white shadow-lg shadow-red-500/20'
                                                    : 'hover:bg-zinc-700 text-zinc-400'
                                                    }`}
                                            >
                                                {isThisPlaying && isPlaying ? <Pause size={14} fill="currentColor" /> : <Play size={16} fill="currentColor" />}
                                            </button>
                                            <button
                                                onClick={() => deleteRecording(file.name)}
                                                className="p-2 hover:bg-red-500/10 text-red-500 rounded-full transition-colors"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>

                                    {/* Progress Bar (Only visible when active) */}
                                    {isThisPlaying && (
                                        <div className="mt-2 px-1">
                                            <div className="flex justify-between text-[10px] text-zinc-400 font-mono mb-1">
                                                <span>{formatTime(currTime)}</span>
                                                <span>{formatTime(duration)}</span>
                                            </div>
                                            <input
                                                type="range"
                                                min="0"
                                                max="100"
                                                value={progress}
                                                onChange={handleSeek}
                                                className="w-full h-1 bg-zinc-700 rounded-lg appearance-none cursor-pointer accent-red-500 align-middle"
                                                style={{
                                                    backgroundImage: `linear-gradient(to right, #ef4444 ${progress}%, #27272a ${progress}%)`
                                                }}
                                            />
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            <ConfirmDialog
                isOpen={confirmDialog.isOpen}
                onConfirm={handleConfirmDelete}
                onCancel={handleCancelDelete}
                title="Delete Recording?"
                message="This recording will be permanently deleted."
                confirmText="Delete"
                variant="danger"
            />
        </div>
    );
};

export default VoiceRecorderModal;
