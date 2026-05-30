# Future R&D and Enhancements

## Real-Time Video Watermarking (OpenGL)

**Goal:** Support long-form continuous "dashcam" style video recording (1-2 hours) with zero post-processing time.

**Current Limitation:** 
Currently, the app uses `CameraX` to record an MP4, and then uses `FFmpeg` to physically burn the location and time into the video after the recording stops. This requires a 1:1 post-processing time (a 5-minute video takes 5 minutes to process) and requires 2x the storage space temporarily.

**Proposed Solution:**
Rewrite the native Android camera pipeline in `SafetyCameraPlugin.kt` to intercept raw video frames using Android's **OpenGL** graphics engine.
1. Capture raw video frames directly from the camera lens.
2. Draw the Location & Date/Time text dynamically onto each frame in memory *before* it gets saved.
3. Pass the combined frame straight into the Android `MediaCodec` / Video Encoder in real-time.

**Benefits:**
- **Zero Processing Time:** The moment the user presses "Stop", the video is instantly 100% finished and ready to share.
- **Unlimited Length:** Supports hours of continuous recording without draining battery or doubling storage requirements during a post-processing phase.

**Technical Considerations:**
- Requires custom OpenGL shaders or Canvas drawing on Android surfaces.
- Need to manually handle device-specific camera rotations and aspect ratios.
- Need to manually multiplex and sync microphone audio with the video frames.
- This would replace the current `CameraX` + `FFmpegKit` implementation for video.

## Pro Tier Feature Pipeline

**1. Shake-to-SOS**
- **Description:** An accelerometer-based fallback for when speech is impossible.
- **Implementation:** Run a lightweight background `SensorEventListener` for `Sensor.TYPE_ACCELEROMETER`. Detect rapid back-and-forth acceleration peaks over a short time window.
- **Tier:** Pro (Companion to Voice SOS)

**2. One-Tap Fake Call**
- **Description:** A common "I'm in trouble, get me out of this" feature that triggers an incoming-call screen with audio without touching the real SOS flow.
- **Implementation:** Create a custom full-screen Android Activity (`FakeCallActivity.kt`) that perfectly mimics a generic Android incoming call screen, complete with playing the default `RingtoneManager` sound. Allow users to customize the fake caller ID name.
- **Tier:** Pro

**3. Trail (Breadcrumbs / Safe Route)**
- **Description:** Allows a user who is walking home or hiking to record and track their location trail for a specific duration (day, week, month).
- **Implementation:** Utilize the existing `ACCESS_BACKGROUND_LOCATION` permission. Run a WorkManager or Foreground Service that logs the GPS coordinates to a local SQLite database table (`trail_history`) every X minutes. Build a React UI to query this database and draw `Polyline` paths on a Google Map.
- **Tier:** Pro
