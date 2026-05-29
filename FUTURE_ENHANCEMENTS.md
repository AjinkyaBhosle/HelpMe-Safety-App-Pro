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
