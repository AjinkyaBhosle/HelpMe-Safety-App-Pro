import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';

const METADATA_FILE = 'safety_media_log.json';
const SAFETY_CAM_FOLDER = 'SafetyCam'; // Native plugin saves here (in external or data) but logic might vary.
// Actually, the native plugin saves to context.getExternalFilesDir(null) + "/SafetyCam"
// which maps to Directory.External in Capacitor, but on Android "External" often means the same private app storage.
// However, the AudioRecorder uses Directory.Data.
// The native plugin returns an absolute path. We might need to handle absolute paths or copy them.
// Wait, the native plugin returns `filePath`. Filesystem can read from absolute paths using `file://` uri or just path if we know the directory mapping.
// But mostly we just need to keep track of the filename and the location data.
// We will store the full path or filename + location.

class SafetyMediaService {
    constructor() {
        this.metadata = [];
        this.isInitialized = false;
    }

    async init() {
        if (this.isInitialized) return;
        await this.loadMetadata();
        this.isInitialized = true;
    }

    async loadMetadata() {
        try {
            const result = await Filesystem.readFile({
                path: METADATA_FILE,
                directory: Directory.Data,
                encoding: Encoding.UTF8
            });
            this.metadata = JSON.parse(result.data);
        } catch (e) {
            // File doesn't exist yet, start empty
            this.metadata = [];
        }
    }

    async saveMetadataToStorage() {
        try {
            await Filesystem.writeFile({
                path: METADATA_FILE,
                data: JSON.stringify(this.metadata),
                directory: Directory.Data,
                encoding: Encoding.UTF8
            });
        } catch (e) {
            console.error("Failed to save media metadata:", e);
        }
    }

    async saveMediaMetadata(filePath, locationData, type = 'video') {
        await this.init();

        // Extract filename from path for easier matching
        // filePath example: /data/user/0/com.helpme.app/files/SafetyCam/safety_cam_123.mp4
        const fileName = filePath.split('/').pop();

        // Infer type if not provided
        if (!type) {
            type = fileName.endsWith('.mp4') ? 'video' : 'photo';
        }

        const newEntry = {
            fileName,
            fullPath: filePath, // Store full path for native playback/sharing
            createdAt: Date.now(),
            location: locationData, // { lat, lng, text }
            type: type
        };

        this.metadata.unshift(newEntry); // Add to beginning
        await this.saveMetadataToStorage();
    }

    async getMediaList() {
        await this.init();

        // Optional: Filter out files that no longer exist? 
        // For now, just return metadata. We can handle missing files in the UI or valid check.
        // Doing a check existence might be slow if many files.
        return this.metadata;
    }

    async deleteMedia(fileName) {
        await this.init();

        // 1. Remove from metadata
        const entryIndex = this.metadata.findIndex(m => m.fileName === fileName);
        if (entryIndex === -1) return;

        const entry = this.metadata[entryIndex];
        const subFolder = entry.type === 'photo' ? 'Photos' : 'Videos';
        
        this.metadata.splice(entryIndex, 1);
        await this.saveMetadataToStorage();

        // 2. Delete actual file
        try {
            await Filesystem.deleteFile({
                path: `SafetyCam/${subFolder}/${fileName}`,
                directory: Directory.External
            });

        } catch (e) {
            console.error("Failed to delete actual video file:", e);
            // Even if file delete fails (maybe already gone), we removed metadata.
        }
    }
}

export const safetyMediaService = new SafetyMediaService();
