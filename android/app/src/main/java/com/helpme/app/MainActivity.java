package com.helpme.app;

import android.os.Bundle;
import android.graphics.Color;
import com.getcapacitor.BridgeActivity;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SmsPlugin.class);
        registerPlugin(TorchPlugin.class);
        registerPlugin(HapticPlugin.class);
        registerPlugin(SafetyCameraPlugin.class);
        registerPlugin(BillingPlugin.class);
        super.onCreate(savedInstanceState);
        
        // Prevent white WebView flash during splash-to-content transition
        if (bridge != null && bridge.getWebView() != null) {
            bridge.getWebView().setBackgroundColor(Color.BLACK);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        // Vibrate on Resume (Warm Start), OR if Cold Start vibration failed/was too
        // long ago (>1000ms)
        if (System.currentTimeMillis() - HelpMeApplication.lastVibrationTime > 1000) {
            vibrateStrong();
        }
    }

    private void vibrateStrong() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(150, 255));
                } else {
                    vibrator.vibrate(150);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
