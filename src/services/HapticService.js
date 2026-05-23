import { registerPlugin } from '@capacitor/core';

const HapticPlugin = registerPlugin('HapticPlugin');

class HapticService {
    async light() {
        try {
            await HapticPlugin.impact({ style: 'light' });
        } catch (e) {
            console.warn('Haptic not available:', e);
        }
    }

    async medium() {
        try {
            await HapticPlugin.impact({ style: 'medium' });
        } catch (e) {
            console.warn('Haptic not available:', e);
        }
    }

    async heavy() {
        try {
            await HapticPlugin.impact({ style: 'heavy' });
        } catch (e) {
            console.warn('Haptic not available:', e);
        }
    }

    async success() {
        try {
            await HapticPlugin.impact({ style: 'success' });
        } catch (e) {
            console.warn('Haptic not available:', e);
        }
    }

    async error() {
        try {
            await HapticPlugin.impact({ style: 'error' });
        } catch (e) {
            console.warn('Haptic not available:', e);
        }
    }
}

export const hapticService = new HapticService();
