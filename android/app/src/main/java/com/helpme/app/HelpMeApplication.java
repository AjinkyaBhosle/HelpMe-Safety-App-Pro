package com.helpme.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class HelpMeApplication extends Application implements androidx.work.Configuration.Provider {
    public static long lastVibrationTime = 0;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // Try vibrating instantly at base context attach time
        vibrateStrong(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .build();
    }

    private void vibrateStrong(Context context) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use MAX amplitude (255) and 150ms duration - proven to be felt
                    vibrator.vibrate(VibrationEffect.createOneShot(150, 255));
                } else {
                    vibrator.vibrate(150);
                }
                lastVibrationTime = System.currentTimeMillis();
            }
        } catch (Exception e) {
            // Ignore errors (keep silent if context fails)
        }
    }
}
