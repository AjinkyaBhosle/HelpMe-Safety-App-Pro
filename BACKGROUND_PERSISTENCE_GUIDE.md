# Help Me! App - Background Persistence Guide

Due to aggressive OEM battery managers (especially on Xiaomi, Oppo, Vivo, and Samsung), keeping a background microphone running 24/7 requires a multi-layered approach.

Here is a comprehensive list of everything implemented in this project to prevent the OS from killing the `WakeWordService`.

## 1. Foreground Service with Notification
The `WakeWordService` runs as a Foreground Service and displays a persistent notification. 
- Declares `foregroundServiceType="microphone"`.
- This is the standard Android requirement to keep a service running beyond 1 minute.

## 2. Partial WakeLock
The service holds a `PowerManager.PARTIAL_WAKE_LOCK`. This prevents the CPU from fully sleeping, ensuring the microphone buffer continues to process audio even when the screen is off.

## 3. Battery Optimization Exemption
The app explicitly requests the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission. During setup, the user is prompted to exempt the app from battery limits.

## 4. UI-Level Instructions (The Gauntlet)
Because OEMs ignore standard Android rules, we instruct the user during the Permission Gauntlet to:
- Navigate to the **Recent Apps** screen (using ☰ or swipe gestures).
- **Lock** the app in memory (the 🔒 padlock icon).
- This is the single most effective way to stop MIUI/ColorOS from killing the app when clearing RAM.

## 5. Dual-Process Guardian Watchdog (Advanced)
The app utilizes a secondary service (`GuardianService`) that runs in a completely separate, isolated Android process (`android:process=":guardian"`).
- It binds to the main `WakeWordService`.
- If the OS kills the main app process to save RAM, the Guardian process detects the disconnection (`onServiceDisconnected`) and instantly broadcasts an intent to revive the main app before the OS can kill the Guardian.

## 6. AlarmClock Heartbeat (Advanced)
To prevent the app from falling into a deep "App Standby Bucket" (e.g., the "Rare" bucket if the user hasn't opened the UI in 3 days), the app uses the `setAlarmClock()` API.
- Unlike standard background jobs or alarms, `setAlarmClock` is legally required by Android to wake the device from deep Doze.
- The app schedules a silent heartbeat every 6 hours to wake the CPU, check the service health, and reset the standby buckets.

## 7. Broadcast Receivers
- **BootReceiver:** Automatically restarts the service when the phone reboots.
- **VoiceRestartReceiver:** Listens for various system broadcasts and internal triggers to aggressively bounce the service back to life.
- **TaskRemoved:** Uses a shotgun approach (immediate broadcast + delayed alarm) to restart the service if the user accidentally swipes the app away from recents.

---

## Future "Nuclear" Options (If Needed)

If users still report the app dying after several days, these are the final escalation strategies:

1. **The Silent Audio Trick:** Play a completely silent 1-second `.wav` file on an infinite loop via `MediaPlayer` inside the Foreground Service. The OS will treat the app like Spotify actively playing music and refuse to kill it.
2. **OEM AutoStart Intent Library:** Use a community library to detect the specific OEM (Xiaomi/Oppo) and programmatically launch their hidden "AutoStart Settings" activity, forcing the user to manually whitelist the app.
3. **FCM Server Pings:** Have a backend server send a high-priority Firebase Cloud Message to the device once a day to wake it up via push notification.
