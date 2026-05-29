import { Capacitor } from '@capacitor/core';
import { CapacitorSQLite, SQLiteConnection } from '@capacitor-community/sqlite';

/**
 * Singleton Service for SQLite Database
 * Handles initialization and CRUD operations for Panic History.
 */
class DatabaseService {
    constructor() {
        this.sqliteConnection = new SQLiteConnection(CapacitorSQLite);
        this.db = null;
        this.isReady = false;
        this.dbName = 'helpme_safety_db';
    }

    /**
     * Initialize the database connection and schema.
     * Should be called once on App start.
     */
    async initialize() {
        if (!Capacitor.isNativePlatform()) {
            console.warn('[DatabaseService] Not on native platform. Native Database disabled.');
            return;
        }

        try {
            // Create connection
            this.db = await this.sqliteConnection.createConnection(
                this.dbName,
                false,
                'no-encryption',
                1,
                false
            );

            await this.db.open();

            // Create Tables
            const schema = `
                CREATE TABLE IF NOT EXISTS panic_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    timestamp TEXT NOT NULL,
                    location TEXT,
                    battery TEXT,
                    contactNumber TEXT,
                    smsSent BOOLEAN,
                    callMade BOOLEAN
                );
            `;
            await this.db.execute(schema);

            this.isReady = true;


        } catch (error) {
            console.error('[DatabaseService] Initialization failed:', error);
            this.isReady = false;
        }
    }

    /**
     * Add a new panic event to the database.
     * @param {Object} event
     */
    async addPanicEvent(event) {
        if (!this.isReady || !this.db) {
            throw new Error('Database not initialized');
        }

        const query = `
            INSERT INTO panic_history (timestamp, location, battery, contactNumber, smsSent, callMade)
            VALUES (?, ?, ?, ?, ?, ?);
        `;
        const values = [
            event.timestamp,
            event.location,
            event.battery,
            event.contactNumber,
            event.smsSent ? 1 : 0,  // SQLite stores booleans as 0/1
            event.callMade ? 1 : 0
        ];

        return await this.db.run(query, values);
    }

    /**
     * Retrieve all panic history, ordered by newest first.
     */
    async getHistory() {
        if (!this.isReady || !this.db) {
            return [];
        }

        const query = `SELECT * FROM panic_history ORDER BY id DESC;`;
        const result = await this.db.query(query);

        // Map SQLite 0/1 back to Booleans if needed, though JS treats 1 as truthy anyway.
        return (result.values || []).map(row => ({
            ...row,
            smsSent: !!row.smsSent,
            callMade: !!row.callMade
        }));
    }

    /**
     * Clear all history functionality
     */
    async clearHistory() {
        if (!this.isReady || !this.db) {
            return;
        }
        await this.db.execute('DELETE FROM panic_history;');
    }

    /**
     * Delete a single panic event by ID
     */
    async deletePanicEvent(id) {
        if (!this.isReady || !this.db) {
            return;
        }
        const query = 'DELETE FROM panic_history WHERE id = ?;';
        await this.db.run(query, [id]);
    }

    /**
     * Update location for an existing event (Self-Healing)
     */
    async updatePanicLocation(id, location) {
        if (!this.isReady || !this.db) return;

        const query = `UPDATE panic_history SET location = ? WHERE id = ?;`;
        await this.db.run(query, [location, id]);
    }
}

export const dbService = new DatabaseService();
