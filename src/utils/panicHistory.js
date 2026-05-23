// Panic History Utils - Hybrid (SQLite + LocalStorage Fallback)
import { dbService } from '../services/DatabaseService';
import { registerPlugin } from '@capacitor/core';

const SmsPlugin = registerPlugin('SmsPlugin');

// Safely get history from localStorage
const getLocalHistory = () => {
    try {
        const data = localStorage.getItem('panic_history');
        return data ? JSON.parse(data) : [];
    } catch (e) { return []; }
};

export const savePanicEvent = async (data = {}) => {
    const event = {
        id: Date.now(),
        timestamp: new Date().toISOString(),
        location: data.location || 'Unknown',
        battery: data.battery || 'Unknown',
        contactNumber: data.contactNumber || 'Not recorded',
        smsSent: data.smsSent !== false,
        callMade: data.callMade !== false
    };



    // 1. Try SQLite
    try {
        if (dbService.isReady) {
            await dbService.addPanicEvent(event);

            return event;
        }
    } catch (err) {
        console.warn('[PanicHistory] Database save failed, falling back to LocalStorage', err);
    }

    // 2. Fallback to LocalStorage
    try {
        const history = getLocalHistory();
        history.unshift(event);
        if (history.length > 50) history.splice(50);
        localStorage.setItem('panic_history', JSON.stringify(history));

    } catch (err) {
        console.error('[PanicHistory] Critical: Failed to save to LocalStorage', err);
    }

    return event;
};

export const getPanicHistory = async () => {
    // 1. Try SQLite
    try {
        if (dbService.isReady) {
            const dbHistory = await dbService.getHistory();
            if (dbHistory && dbHistory.length > 0) {
                // SELF-HEALING: Check if latest panic needs location update
                const latest = dbHistory[0];
                if (latest && (latest.location === 'Unknown' || latest.location === 'Location unavailable')) {
                    try {
                        const { lastPanicLocation } = await SmsPlugin.getSettings();
                        if (lastPanicLocation && lastPanicLocation.includes("http")) {
                            await dbService.updatePanicLocation(latest.id, lastPanicLocation);
                            // Update local variable to return correct data immediately
                            latest.location = lastPanicLocation;
                        }
                    } catch (e) {
                        console.warn("Self-Healing check failed", e);
                    }
                }
                return dbHistory;
            }
        }
    } catch (err) {
        console.warn('[PanicHistory] Database read failed', err);
    }

    // 2. Fallback to LocalStorage (or if DB is empty, maybe return local?)
    // Decision: If DB is active but empty, it returns [], which is correct.
    // We only access localStorage if DB is NOT ready.
    if (!dbService.isReady) {
        return getLocalHistory();
    }

    // If DB is ready but empty, check if we need to migrate (Optional enhancement for later)
    return [];
};

export const clearPanicHistory = async () => {
    let success = false;

    // 1. Clear SQLite
    try {
        if (dbService.isReady) {
            await dbService.clearHistory();
            success = true;
        }
    } catch (e) {
        console.error('Failed to clear SQLite', e);
    }

    // 2. Clear LocalStorage (Always clear this too, just in case)
    try {
        localStorage.removeItem('panic_history');
        success = true;
    } catch (e) {
        console.error('Failed to clear LocalStorage', e);
    }

    return success;
};

export const deletePanicEvent = async (id) => {
    // 1. Try SQLite
    try {
        if (dbService.isReady) {
            await dbService.deletePanicEvent(id);
            return true;
        }
    } catch (e) {
        console.error('Failed to delete from SQLite', e);
    }

    // 2. Fallback to LocalStorage
    try {
        const history = getLocalHistory();
        const filtered = history.filter(e => e.id !== id);
        localStorage.setItem('panic_history', JSON.stringify(filtered));
        return true;
    } catch (e) {
        console.error('Failed to delete from LocalStorage', e);
        return false;
    }
};

export const getPanicStats = async () => {
    const history = await getPanicHistory(); // Await the async result

    return {
        total: history.length,
        lastPanic: history[0]?.timestamp || null,
        thisMonth: history.filter(e => {
            const eventDate = new Date(e.timestamp);
            const now = new Date();
            return eventDate.getMonth() === now.getMonth() &&
                eventDate.getFullYear() === now.getFullYear();
        }).length
    };
};
