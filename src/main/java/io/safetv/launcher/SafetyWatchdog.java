package io.safetv.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class SafetyWatchdog {
    private static final String TAG = "SafetyWatchdog";
    private static final String PREF_NAME = "safetv_safety";
    private static final String KEY_CRASH_COUNT = "crash_count";
    private static final String KEY_SAFETY_ACTIVE = "safety_active";
    private static final int MAX_CRASH_THRESHOLD = 2;
    private static final long HEALTH_CONFIRMATION_DELAY_MS = 10000; // 10 seconds

    private static Handler sHandler = new Handler(Looper.getMainLooper());
    private static Runnable sHealthCheckRunnable;

    /**
     * Called in onCreate of MainActivity to check health state and arm watchdog.
     * Returns true if safe to proceed, false if Safety Mode was triggered.
     */
    public static boolean checkAndArm(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean safetyActive = prefs.getBoolean(KEY_SAFETY_ACTIVE, false);
        int crashes = prefs.getInt(KEY_CRASH_COUNT, 0);

        Log.i(TAG, "Watchdog init: crashes=" + crashes + ", safetyActive=" + safetyActive);

        if (safetyActive || crashes >= MAX_CRASH_THRESHOLD) {
            triggerSafetyRevert(context, "Watchdog threshold exceeded (" + crashes + " crashes)");
            return false;
        }

        // Increment crash count optimistically (will be reset if app survives 10s)
        prefs.edit().putInt(KEY_CRASH_COUNT, crashes + 1).apply();

        // Schedule health confirmation after 10 seconds of stable operation
        if (sHealthCheckRunnable != null) {
            sHandler.removeCallbacks(sHealthCheckRunnable);
        }
        sHealthCheckRunnable = () -> {
            Log.i(TAG, "App has been stable for 10s. Resetting crash count to 0.");
            prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
        };
        sHandler.postDelayed(sHealthCheckRunnable, HEALTH_CONFIRMATION_DELAY_MS);

        return true;
    }

    public static void recordHealthyLaunch(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply();
    }

    /**
     * Immediately reverts to the stock Google TV launcher and disables the interceptor.
     */
    public static void triggerSafetyRevert(Context context, String reason) {
        Log.w(TAG, "TRIGGERING SAFETY REVERT: " + reason);
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SAFETY_ACTIVE, true).putInt(KEY_CRASH_COUNT, 0).apply();

        Toast.makeText(context, "SafeTV Safety Revert: " + reason, Toast.LENGTH_LONG).show();

        // Launch stock Google TV home
        Intent stockIntent = new Intent(Intent.ACTION_MAIN);
        stockIntent.addCategory(Intent.CATEGORY_HOME);
        stockIntent.setPackage("com.google.android.apps.tv.launcherx");
        stockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        try {
            context.startActivity(stockIntent);
        } catch (Exception e) {
            Log.e(TAG, "Could not open launcherx explicitly, attempting general fallback: " + e.getMessage());
            // General launcher fallback
            Intent fallback = new Intent(Intent.ACTION_MAIN);
            fallback.addCategory(Intent.CATEGORY_HOME);
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    public static void resetSafetyMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SAFETY_ACTIVE, false).putInt(KEY_CRASH_COUNT, 0).apply();
    }
}
