package io.safetv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class HomeInterceptorService extends AccessibilityService {
    private static final String TAG = "HomeInterceptor";
    private int mPanicPressCount = 0;
    private long mLastPanicPressTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Whenever stock Google TV launcher tries to take foreground, immediately bring Zenith Launcher to front
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && "com.google.android.apps.tv.launcherx".equals(pkg.toString())) {
                SharedPreferences prefs = getSharedPreferences("safetv_safety", Context.MODE_PRIVATE);
                boolean safetyActive = prefs.getBoolean("safety_active", false);

                if (!safetyActive) {
                    Log.i(TAG, "Stock Google TV detected in foreground; redirecting to Zenith Launcher");
                    Intent launchIntent = new Intent(this, MainActivity.class);
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(launchIntent);
                }
            }
        }
    }

    @Override
    public void onInterrupt() {
        // Not used
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        if (action == KeyEvent.ACTION_DOWN) {
            // Check panic sequence (Volume Down 3 times in 2 seconds)
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                long now = System.currentTimeMillis();
                if (now - mLastPanicPressTime < 1500) {
                    mPanicPressCount++;
                    if (mPanicPressCount >= 3) {
                        SafetyWatchdog.triggerSafetyRevert(this, "Remote Panic Sequence (Volume Down x3)");
                        mPanicPressCount = 0;
                        return super.onKeyEvent(event);
                    }
                } else {
                    mPanicPressCount = 1;
                }
                mLastPanicPressTime = now;
            }

            // Intercept Home button
            if (keyCode == KeyEvent.KEYCODE_HOME) {
                SharedPreferences prefs = getSharedPreferences("safetv_safety", Context.MODE_PRIVATE);
                boolean safetyActive = prefs.getBoolean("safety_active", false);

                if (safetyActive) {
                    Log.i(TAG, "Safety mode active: allowing Home event to pass to stock launcher");
                    return super.onKeyEvent(event);
                }

                Log.i(TAG, "Intercepted Home key: launching Zenith cleanly over stock");
                Intent launchIntent = new Intent(this, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(launchIntent);
                return true; // Consume the event so stock doesn't process it
            }
        }

        return super.onKeyEvent(event);
    }
}
