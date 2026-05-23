import { Filesystem, Directory } from '@capacitor/filesystem';
import { VoiceRecorder } from 'capacitor-voice-recorder';

class AudioRecorderService {
    constructor() {
        this.isRecording = false;
    }

    async requestPermissions() {
        try {
            const result = await VoiceRecorder.requestAudioRecordingPermission();
            return result.value;
        } catch (err) {
            console.error("Microphone permission request failed:", err);
            throw err;
        }
    }

    async requestPermissionOnly() {
        try {
            const status = await VoiceRecorder.hasAudioRecordingPermission();
            if (status.value) return true;

            const result = await VoiceRecorder.requestAudioRecordingPermission();
            return result.value;
        } catch (e) {
            console.error(e);
            return false;
        }
    }

    async checkPermissionStatus() {
        try {
            const result = await VoiceRecorder.hasAudioRecordingPermission();
            return result.value ? 'granted' : 'prompt';
        } catch (e) {
            return 'prompt';
        }
    }

    async startRecording() {
        if (this.isRecording) return;

        try {
            const perm = await this.checkPermissionStatus();
            if (perm !== 'granted') {
                const granted = await this.requestPermissionOnly();
                if (!granted) throw new Error("Permission denied");
            }

            // Start recording - plugin handles format (AAC/m4a on Android)
            await VoiceRecorder.startRecording();
            this.isRecording = true;
        } catch (error) {
            console.error("Failed to start recording:", error);
            throw error;
        }
    }

    async pauseRecording() {
        try {
            await VoiceRecorder.pauseRecording();
        } catch (e) {
            console.error("Pause not supported or failed", e);
        }
    }

    async resumeRecording() {
        try {
            await VoiceRecorder.resumeRecording();
        } catch (e) {
            console.error("Resume not supported or failed", e);
        }
    }

    async stopRecording() {
        if (!this.isRecording) return null;

        try {
            const result = await VoiceRecorder.stopRecording();
            this.isRecording = false;

            if (!result.value || !result.value.recordDataBase64) {
                throw new Error("No audio data received");
            }

            const base64Data = result.value.recordDataBase64;
            const mimeType = result.value.mimeType; // usually audio/aac or audio/mp4

            // Generate filename with appropriate extension
            let ext = 'm4a';
            // map mime type if needed, but m4a is safe for aac
            if (mimeType && mimeType.includes('webm')) ext = 'webm';

            const fileName = `recording_${new Date().getTime()}.${ext}`;

            await Filesystem.writeFile({
                path: fileName,
                data: base64Data,
                directory: Directory.Data
            });

            return fileName;

        } catch (err) {
            console.error("Failed to stop/save recording:", err);
            this.isRecording = false; // reset state anyway
            throw err;
        }
    }

    async getRecordings() {
        try {
            const result = await Filesystem.readdir({
                path: '',
                directory: Directory.Data
            });
            // Filter for evidence_* or recording_* with supported extensions
            return result.files
                .filter(f => (f.name.startsWith('evidence_') || f.name.startsWith('recording_')) &&
                    (f.name.endsWith('.webm') || f.name.endsWith('.m4a') || f.name.endsWith('.mp3')))
                .map(f => {
                    // Extract timestamp from filename: evidence_1678999.webm
                    const parts = f.name.split('_');
                    if (parts.length < 2) return { ...f, createdAt: 0 };

                    const timestampStr = parts[1].split('.')[0];
                    const timestamp = parseInt(timestampStr) || 0;
                    return {
                        ...f,
                        createdAt: timestamp
                    };
                })
                .sort((a, b) => b.createdAt - a.createdAt);
        } catch (e) {
            console.error("Error reading recordings:", e);
            return [];
        }
    }

    async playRecording(fileName) {
        try {
            const file = await Filesystem.readFile({
                path: fileName,
                directory: Directory.Data
            });

            // Determine mime type based on extension
            let mimeType = 'audio/aac'; // Default to aac/m4a for native
            if (fileName.endsWith('.webm')) mimeType = 'audio/webm';
            else if (fileName.endsWith('.mp3')) mimeType = 'audio/mpeg';
            else if (fileName.endsWith('.m4a')) mimeType = 'audio/mp4';

            const audioSrc = `data:${mimeType};base64,${file.data}`;
            const audio = new Audio(audioSrc);
            audio.play();
            return audio;
        } catch (e) {
            console.error("Error playing file:", e);
            throw e;
        }
    }

    async deleteRecording(fileName) {
        await Filesystem.deleteFile({
            path: fileName,
            directory: Directory.Data
        });
    }

    async getRecordingFileUri(fileName) {
        try {
            const result = await Filesystem.getUri({
                path: fileName,
                directory: Directory.Data
            });
            return result.uri;
        } catch (e) {
            console.error("Error getting file URI:", e);
            throw e;
        }
    }
}

export const audioRecorder = new AudioRecorderService();
