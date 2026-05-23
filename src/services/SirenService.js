import { registerPlugin } from '@capacitor/core';
const SmsPlugin = registerPlugin('SmsPlugin');

class SirenService {
    constructor() {
        this.ctx = null;
        this.oscillator = null; // For synth
        this.gainNode = null;
        this.activeSource = null; // For audio buffers (MP3s)
        this.isPlaying = false;
        this.activeType = null;
    }

    // Initialize Audio Context interaction
    init() {
        if (!this.ctx) {
            this.ctx = new (window.AudioContext || window.webkitAudioContext)();
        }
        if (this.ctx.state === 'suspended') {
            this.ctx.resume();
        }
    }

    start(type = 'siren') {
        // If already playing this type, do nothing (or restart? let's ignore)
        if (this.isPlaying && this.activeType === type) return;

        // Force MAX VOLUME via Native Plugin
        try {
            SmsPlugin.maximizeVolume();
            console.log("🔊 Requesting MAX volume natively...");
        } catch (e) {
            console.error("Volume maximize failed", e);
        }

        // Stop any current sound
        this.stop();

        this.init();
        this.activeType = type;
        this.isPlaying = true;

        switch (type) {
            case 'siren':
            case 'alarm':
            case 'whistle':
            case 'dog':
            case 'scream':
                this.playAsset(type);
                break;
            default:
                this.playAsset('siren');
        }
    }

    stop() {
        if (this.activeSource) {
            try {
                this.activeSource.stop();
            } catch (e) { }
            this.activeSource = null;
        }

        if (this.oscillator) {
            try {
                this.gainNode.gain.cancelScheduledValues(this.ctx.currentTime);
                this.gainNode.gain.setValueAtTime(this.gainNode.gain.value, this.ctx.currentTime);
                this.gainNode.gain.linearRampToValueAtTime(0, this.ctx.currentTime + 0.1);
                this.oscillator.stop(this.ctx.currentTime + 0.1);
            } catch (e) { }
            this.oscillator = null;
        }

        if (this.loopTimer) clearTimeout(this.loopTimer);
        this.isPlaying = false;
        this.activeType = null;
    }

    // --- SYNTHESIZERS (Code-Generated Sounds) ---

    playSynthSiren() {
        this.oscillator = this.ctx.createOscillator();
        this.oscillator.type = 'sawtooth';
        this.gainNode = this.ctx.createGain();
        this.gainNode.connect(this.ctx.destination);
        this.oscillator.connect(this.gainNode);

        // Volume up (MAX)
        this.gainNode.gain.setValueAtTime(0, this.ctx.currentTime);
        this.gainNode.gain.linearRampToValueAtTime(1.0, this.ctx.currentTime + 0.1);

        const now = this.ctx.currentTime;
        this.oscillator.frequency.setValueAtTime(600, now);
        this.oscillator.start(now);

        // Schedule "Yelp" loop (Fast)
        const schedule = (time) => {
            if (!this.isPlaying || this.activeType !== 'siren') return;
            // 0.35s cycle
            this.oscillator.frequency.linearRampToValueAtTime(1600, time + 0.35);
            this.oscillator.frequency.linearRampToValueAtTime(600, time + 0.7);

            this.loopTimer = setTimeout(() => {
                schedule(this.ctx.currentTime);
            }, 680);
        };
        schedule(now);
    }

    playSynthAlarm() {
        this.oscillator = this.ctx.createOscillator();
        this.oscillator.type = 'square'; // Harsh square wave
        this.gainNode = this.ctx.createGain();
        this.gainNode.connect(this.ctx.destination);
        this.oscillator.connect(this.gainNode);

        const now = this.ctx.currentTime;
        this.oscillator.frequency.setValueAtTime(1000, now); // Fixed 1kHz
        this.oscillator.start(now);

        // Pulse volume (MAX)
        const schedule = (time) => {
            if (!this.isPlaying || this.activeType !== 'alarm') return;
            // Beep ON
            this.gainNode.gain.setValueAtTime(1.0, time);
            // Beep OFF
            this.gainNode.gain.setValueAtTime(0, time + 0.3);

            this.loopTimer = setTimeout(() => {
                schedule(this.ctx.currentTime);
            }, 600); // 300ms on, 300ms off
        };
        schedule(now);
    }

    playSynthWhistle() {
        this.oscillator = this.ctx.createOscillator();
        this.oscillator.type = 'sine'; // Pure tone
        this.gainNode = this.ctx.createGain();
        this.gainNode.connect(this.ctx.destination);
        this.oscillator.connect(this.gainNode);

        this.gainNode.gain.value = 1.0;
        const now = this.ctx.currentTime;
        this.oscillator.start(now);

        const schedule = (time) => {
            if (!this.isPlaying || this.activeType !== 'whistle') return;
            // Rapid rise 800 -> 2500
            this.oscillator.frequency.setValueAtTime(800, time);
            this.oscillator.frequency.linearRampToValueAtTime(2500, time + 0.15);
            // Drop silence
            this.gainNode.gain.setValueAtTime(1.0, time + 0.15);
            this.gainNode.gain.linearRampToValueAtTime(0, time + 0.2);
            this.gainNode.gain.linearRampToValueAtTime(1.0, time + 0.4); // Reset for next

            this.loopTimer = setTimeout(() => {
                schedule(this.ctx.currentTime);
            }, 500);
        };
        schedule(now);
    }

    // --- ASSETS (MP3 Files) ---

    async playAsset(filename) {
        const formats = ['ogg', 'mp3'];
        let buffer = null;

        for (const fmt of formats) {
            try {
                // Add timestamp to force bypass cache
                const response = await fetch(`/sounds/${filename}.${fmt}?v=${new Date().getTime()}`);
                if (response.ok) {
                    const arrayBuffer = await response.arrayBuffer();
                    buffer = await this.ctx.decodeAudioData(arrayBuffer);
                    break;
                }
            } catch (e) {
                // Continue to next format
            }
        }

        if (buffer) {
            this.playBuffer(buffer, filename);
        } else {
            console.warn(`Failed to play ${filename} (checked ogg, mp3)`);
            this.playFallbackNoise();
        }
    }

    playBuffer(buffer, type = 'siren') {
        // Natural pause for Whistle/Dog (Organic sounds)
        // Continuous loop for Siren/Alarm (Mechanical sounds)
        let loopDelay = 0;
        if (type === 'whistle') loopDelay = 2000; // 2s pause (User requested)
        if (type === 'dog') loopDelay = 1000;    // 1s pause

        // Logic for Organic Looping (Play -> Wait -> Play)
        const play = () => {
            if (!this.isPlaying) return;

            this.activeSource = this.ctx.createBufferSource();
            this.activeSource.buffer = buffer;
            this.activeSource.loop = false; // Disable native loop
            this.activeSource.connect(this.ctx.destination);

            this.activeSource.onended = () => {
                if (this.isPlaying) {
                    this.loopTimer = setTimeout(() => {
                        play();
                    }, loopDelay);
                }
            };

            this.activeSource.start();
        };

        if (loopDelay > 0) {
            play();
        } else {
            // Standard Seamless Loop (Siren, Alarm)
            this.activeSource = this.ctx.createBufferSource();
            this.activeSource.buffer = buffer;
            this.activeSource.loop = true;
            this.activeSource.connect(this.ctx.destination);
            this.activeSource.start();
        }
    }

    playFallbackNoise() {
        // Create a simple "Static" noise as error/fallback
        const bufferSize = this.ctx.sampleRate * 1; // 1 sec
        const buffer = this.ctx.createBuffer(1, bufferSize, this.ctx.sampleRate);
        const data = buffer.getChannelData(0);
        for (let i = 0; i < bufferSize; i++) {
            data[i] = Math.random() * 2 - 1;
        }

        this.playBuffer(buffer);
    }
}

export const sirenService = new SirenService();
