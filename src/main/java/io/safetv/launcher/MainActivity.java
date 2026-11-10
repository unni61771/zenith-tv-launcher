package io.safetv.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    // 6 Curated apps requested by user
    private static final List<String> CURATED_PACKAGES = Arrays.asList(
            "dev.akash.skystream",
            "com.android.vending",
            "org.smarttube.stable",
            "in.startv.hotstar",
            "com.amazon.amazonvideo.livingroom",
            "org.videolan.vlc"
    );

    // Hardware passthrough IDs for this Acer / Realtek TV
    private static final String HDMI_1_ID = "com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151519488";
    private static final String HDMI_2_ID = "com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151519744";
    private static final String HDMI_3_ID = "com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151520000";
    private static final String AV_ID     = "com.realtek.tv.passthrough/.avinput.AVTvInputService/HW50593792";

    private View mViewAmbientGlow;
    private TextView mTvClock;
    private TextView mTvDate;
    private LinearLayout mContainerCuratedApps;
    private LinearLayout mContainerInputs;
    private View mOverlayAllApps;
    private GridLayout mGridAllApps;
    private Map<String, ResolveInfo> mAppMap = new LinkedHashMap<>();

    private int mCurrentAmbientColor = Color.TRANSPARENT;
    private Handler mTimeHandler = new Handler(Looper.getMainLooper());
    private Runnable mTimeRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Safety Watchdog / Dead Man's Switch Check
        if (!SafetyWatchdog.checkAndArm(this)) {
            finish();
            return;
        }

        // 2. Start Emergency HTTP Server (port 8888)
        EmergencyHttpServer.start(this);

        setContentView(R.layout.activity_main);

        mViewAmbientGlow = findViewById(R.id.view_ambient_glow);
        mTvClock = findViewById(R.id.tv_clock);
        mTvDate = findViewById(R.id.tv_date);
        mContainerCuratedApps = findViewById(R.id.container_curated_apps);
        mContainerInputs = findViewById(R.id.container_inputs);
        mOverlayAllApps = findViewById(R.id.overlay_all_apps);
        mGridAllApps = findViewById(R.id.grid_all_apps);

        loadInstalledApps();
        startTimeTicker();

        // Top Navigation Tabs
        View tabHome = findViewById(R.id.tab_home);
        tabHome.setOnClickListener(v -> hideAllAppsOverlay());
        attachTabFocusAnimation(tabHome);

        View tabAllApps = findViewById(R.id.tab_all_apps);
        tabAllApps.setOnClickListener(v -> showAllAppsOverlay(mAppMap));
        attachTabFocusAnimation(tabAllApps);

        // Top Action Buttons
        View btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> openSystemSettings());
        attachTabFocusAnimation(btnSettings);

        View btnStock = findViewById(R.id.btn_stock_google_tv);
        btnStock.setOnClickListener(v -> launchStockLauncher());
        attachTabFocusAnimation(btnStock);

        View btnCloseAllApps = findViewById(R.id.btn_close_all_apps);
        btnCloseAllApps.setOnClickListener(v -> hideAllAppsOverlay());
        attachTabFocusAnimation(btnCloseAllApps);

        // Populate curated apps and HDMI inputs with user's custom 4K assets
        setupCuratedApps();
        setupHardwareInputs();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        mAppMap.clear();

        Intent tvIntent = new Intent(Intent.ACTION_MAIN, null);
        tvIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> installed = pm.queryIntentActivities(tvIntent, 0);

        Intent mobileIntent = new Intent(Intent.ACTION_MAIN, null);
        mobileIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> mobileInstalled = pm.queryIntentActivities(mobileIntent, 0);

        for (ResolveInfo info : installed) {
            mAppMap.put(info.activityInfo.packageName, info);
        }
        for (ResolveInfo info : mobileInstalled) {
            if (!mAppMap.containsKey(info.activityInfo.packageName)) {
                mAppMap.put(info.activityInfo.packageName, info);
            }
        }
    }

    private void startTimeTicker() {
        mTimeRunnable = new Runnable() {
            @Override
            public void run() {
                Date now = new Date();
                SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a", Locale.getDefault());
                SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, MMM d", Locale.getDefault());

                if (mTvClock != null) mTvClock.setText(timeFmt.format(now));
                if (mTvDate != null) mTvDate.setText(dateFmt.format(now));

                mTimeHandler.postDelayed(this, 1000);
            }
        };
        mTimeHandler.post(mTimeRunnable);
    }

    private void transitionAmbientColor(int targetColor) {
        if (mViewAmbientGlow == null) return;
        ValueAnimator anim = ValueAnimator.ofArgb(mCurrentAmbientColor, targetColor);
        anim.setDuration(240);
        anim.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            mViewAmbientGlow.setBackgroundColor(color);
        });
        anim.start();
        mCurrentAmbientColor = targetColor;
    }

    private void attachTabFocusAnimation(View v) {
        v.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                view.animate().scaleX(1.10f).scaleY(1.10f).setDuration(160).start();
            } else {
                view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start();
            }
        });
    }

    private void attachCardFocusAnimation(View tile, View cardContainer, ImageView haloView,
                                          ImageView cardArt, ImageView reflectionView,
                                          int haloResId, int reflResId, int brandColor, float scale) {
        if (haloView != null && haloResId != 0) {
            haloView.setImageResource(haloResId);
        }

        if (reflectionView != null && reflResId != 0) {
            reflectionView.setImageResource(reflResId);
            reflectionView.setAlpha(0.0f);
        }

        if (cardArt != null) {
            cardArt.setAlpha(0.85f);
        }

        tile.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                // Elevate focused tile hardware Z-index without altering layout child index
                tile.setElevation(28f);
                tile.setTranslationZ(28f);

                // 1. Entire Card scales and elevates
                cardContainer.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationY(-6f)
                        .translationZ(20f)
                        .setDuration(180)
                        .start();

                // 2. Card artwork reaches 100% full vibrant luminance
                if (cardArt != null) {
                    cardArt.animate().alpha(1.0f).setDuration(180).start();
                }

                // 3. Whole-icon radiant halo bloom emanates from behind
                if (haloView != null) {
                    haloView.animate()
                            .alpha(1.0f)
                            .scaleX(1.15f)
                            .scaleY(1.22f)
                            .setDuration(180)
                            .start();
                }

                // 4. Smooth diffuse water reflection blooms ONLY when selected
                if (reflectionView != null) {
                    reflectionView.animate()
                            .alpha(0.70f)
                            .scaleX(1.06f)
                            .setDuration(180)
                            .start();
                }

                // 5. Project radiant ambient glow across the mountain lake water surface
                transitionAmbientColor(Color.argb(70, Color.red(brandColor), Color.green(brandColor), Color.blue(brandColor)));
            } else {
                tile.setElevation(0f);
                tile.setTranslationZ(0f);

                // Return to clean resting state
                cardContainer.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .translationZ(4f)
                        .setDuration(180)
                        .start();

                if (cardArt != null) {
                    cardArt.animate().alpha(0.85f).setDuration(180).start();
                }

                if (haloView != null) {
                    haloView.animate()
                            .alpha(0.0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180)
                            .start();
                }

                // Reflection fades to completely invisible when not selected
                if (reflectionView != null) {
                    reflectionView.animate()
                            .alpha(0.0f)
                            .scaleX(1.0f)
                            .setDuration(180)
                            .start();
                }
            }
        });
    }

    private List<View> mAppTiles = new ArrayList<>();
    private List<View> mInputTiles = new ArrayList<>();

    private void setupCuratedApps() {
        LayoutInflater inflater = LayoutInflater.from(this);
        mContainerCuratedApps.removeAllViews();
        mAppTiles.clear();

        boolean firstFocused = false;

        // 1. SkyStream (Radiant Cyan #00D2FF)
        mAppTiles.add(addAppCard(inflater, "dev.akash.skystream",
                R.drawable.card_user_skystream, R.drawable.card_refl_skystream, R.drawable.halo_skystream,
                Color.parseColor("#00D2FF"), !firstFocused));
        firstFocused = true;

        // 2. Play Store (Radiant Emerald #00E676)
        mAppTiles.add(addAppCard(inflater, "com.android.vending",
                R.drawable.card_user_playstore, R.drawable.card_refl_playstore, R.drawable.halo_playstore,
                Color.parseColor("#00E676"), false));

        // 3. SmartTube (Radiant Crimson #FF1E40)
        mAppTiles.add(addAppCard(inflater, "org.smarttube.stable",
                R.drawable.card_user_smarttube, R.drawable.card_refl_smarttube, R.drawable.halo_smarttube,
                Color.parseColor("#FF1E40"), false));

        // 4. JioHotstar (Radiant Magenta/Purple #E02080)
        mAppTiles.add(addAppCard(inflater, "in.startv.hotstar",
                R.drawable.card_user_hotstar, R.drawable.card_refl_hotstar, R.drawable.halo_hotstar,
                Color.parseColor("#E02080"), false));

        // 5. Prime Video (Radiant Amazon Blue #00A8E1)
        mAppTiles.add(addAppCard(inflater, "com.amazon.amazonvideo.livingroom",
                R.drawable.card_user_prime, R.drawable.card_refl_prime, R.drawable.halo_prime,
                Color.parseColor("#00A8E1"), false));

        // 6. VLC (Radiant Traffic Amber-Orange #FF7700)
        mAppTiles.add(addAppCard(inflater, "org.videolan.vlc",
                R.drawable.card_user_vlc, R.drawable.card_refl_vlc, R.drawable.halo_vlc,
                Color.parseColor("#FF7700"), false));
    }

    private View addAppCard(LayoutInflater inflater, String pkg, int artResId, int reflResId, int haloResId,
                            int brandColor, boolean requestFocus) {
        View tile = inflater.inflate(R.layout.item_banner_app, mContainerCuratedApps, false);
        tile.setId(View.generateViewId());
        View cardContainer = tile.findViewById(R.id.card_container);
        ImageView ivHalo = tile.findViewById(R.id.card_halo);
        ImageView ivArt = tile.findViewById(R.id.card_art);
        ImageView ivRefl = tile.findViewById(R.id.card_reflection);

        ivArt.setImageResource(artResId);

        tile.setOnClickListener(v -> launchApp(pkg));
        attachCardFocusAnimation(tile, cardContainer, ivHalo, ivArt, ivRefl, haloResId, reflResId, brandColor, 1.08f);
        mContainerCuratedApps.addView(tile);

        if (requestFocus) {
            tile.requestFocus();
            if (ivArt != null) ivArt.setAlpha(1.0f);
            if (ivHalo != null) {
                ivHalo.setAlpha(1.0f);
                ivHalo.setScaleX(1.15f);
                ivHalo.setScaleY(1.22f);
            }
            if (ivRefl != null) {
                ivRefl.setAlpha(0.70f);
                ivRefl.setScaleX(1.06f);
            }
            transitionAmbientColor(Color.argb(70, Color.red(brandColor), Color.green(brandColor), Color.blue(brandColor)));
        }
        return tile;
    }

    private void setupHardwareInputs() {
        LayoutInflater inflater = LayoutInflater.from(this);
        mContainerInputs.removeAllViews();
        mInputTiles.clear();

        // 1. HDMI 1: Dolby Atmos (Radiant Violet #9333EA)
        mInputTiles.add(addInputCard(inflater, R.drawable.card_user_dolby, R.drawable.card_refl_dolby, R.drawable.halo_dolby,
                "HDMI 1 (Dolby Atmos)", HDMI_1_ID, Color.parseColor("#9333EA")));

        // 2. HDMI 2: Xbox Console (Radiant Xbox Green #107C41)
        mInputTiles.add(addInputCard(inflater, R.drawable.card_user_xbox, R.drawable.card_refl_xbox, R.drawable.halo_xbox,
                "HDMI 2 (Xbox Console)", HDMI_2_ID, Color.parseColor("#107C41")));

        // 3. HDMI 3: Media Box (Radiant Cinema Blue #0EA5E9)
        mInputTiles.add(addInputCard(inflater, R.drawable.card_user_media, R.drawable.card_refl_media, R.drawable.halo_media,
                "HDMI 3 (Media Box)", HDMI_3_ID, Color.parseColor("#0EA5E9")));

        // 4. AV Composite: TV (Radiant Sunset Amber #F59E0B)
        mInputTiles.add(addInputCard(inflater, R.drawable.card_user_tv, R.drawable.card_refl_tv, R.drawable.halo_tv,
                "AV Composite (TV)", AV_ID, Color.parseColor("#F59E0B")));

        // Link explicit focus navigation across Apps and Inputs (guarantees left/right/up/down always work)
        linkFocusDirections();
    }

    private View addInputCard(LayoutInflater inflater, int artResId, int reflResId, int haloResId,
                              String friendlyName, String inputId, int brandColor) {
        View tile = inflater.inflate(R.layout.item_input, mContainerInputs, false);
        tile.setId(View.generateViewId());
        View cardContainer = tile.findViewById(R.id.input_card_container);
        ImageView ivHalo = tile.findViewById(R.id.input_card_halo);
        ImageView ivArt = tile.findViewById(R.id.input_card_art);
        ImageView ivRefl = tile.findViewById(R.id.input_card_reflection);

        ivArt.setImageResource(artResId);

        tile.setOnClickListener(v -> tuneToHardwareInput(friendlyName, inputId));
        attachCardFocusAnimation(tile, cardContainer, ivHalo, ivArt, ivRefl, haloResId, reflResId, brandColor, 1.08f);
        mContainerInputs.addView(tile);
        return tile;
    }

    private void linkFocusDirections() {
        for (int i = 0; i < mAppTiles.size(); i++) {
            View t = mAppTiles.get(i);
            if (i > 0) {
                t.setNextFocusLeftId(mAppTiles.get(i - 1).getId());
            }
            if (i < mAppTiles.size() - 1) {
                t.setNextFocusRightId(mAppTiles.get(i + 1).getId());
            }
            t.setNextFocusUpId(R.id.tab_home);
            if (!mInputTiles.isEmpty()) {
                int targetInput = Math.min(mInputTiles.size() - 1, (i * mInputTiles.size()) / mAppTiles.size());
                t.setNextFocusDownId(mInputTiles.get(targetInput).getId());
            }
        }

        for (int i = 0; i < mInputTiles.size(); i++) {
            View t = mInputTiles.get(i);
            if (i > 0) {
                t.setNextFocusLeftId(mInputTiles.get(i - 1).getId());
            }
            if (i < mInputTiles.size() - 1) {
                t.setNextFocusRightId(mInputTiles.get(i + 1).getId());
            }
            if (!mAppTiles.isEmpty()) {
                int targetApp = Math.min(mAppTiles.size() - 1, (i * mAppTiles.size()) / mInputTiles.size());
                t.setNextFocusUpId(mAppTiles.get(targetApp).getId());
            }
        }

        View tabHome = findViewById(R.id.tab_home);
        if (tabHome != null && !mAppTiles.isEmpty()) {
            tabHome.setNextFocusDownId(mAppTiles.get(0).getId());
        }
    }

    private void tuneToHardwareInput(String name, String inputId) {
        Toast.makeText(this, "Switching to " + name + "...", Toast.LENGTH_SHORT).show();
        try {
            Uri uri = Uri.parse("content://android.media.tv/passthrough/" + Uri.encode(inputId));
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed direct passthrough to " + inputId, e);
            try {
                Intent fallback = new Intent("android.media.tv.action.VIEW_INPUTS");
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Please use TV Input button on remote", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void launchApp(String packageName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage(packageName);
        }
        if (intent != null) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error opening app", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "App not installed on TV: " + packageName, Toast.LENGTH_SHORT).show();
        }
    }

    private void showAllAppsOverlay(Map<String, ResolveInfo> appMap) {
        if (mOverlayAllApps == null || mGridAllApps == null) return;

        PackageManager pm = getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(this);
        mGridAllApps.removeAllViews();

        List<ResolveInfo> allApps = new ArrayList<>(appMap.values());
        Collections.sort(allApps, new ResolveInfo.DisplayNameComparator(pm));

        for (ResolveInfo info : allApps) {
            View item = inflater.inflate(R.layout.item_app, mGridAllApps, false);
            ImageView ivIcon = item.findViewById(R.id.app_icon);
            TextView tvLabel = item.findViewById(R.id.app_label);

            ivIcon.setImageDrawable(info.loadIcon(pm));
            tvLabel.setText(info.loadLabel(pm));

            String pkg = info.activityInfo.packageName;
            item.setOnClickListener(v -> {
                hideAllAppsOverlay();
                launchApp(pkg);
            });
            attachTabFocusAnimation(item);
            mGridAllApps.addView(item);
        }

        mOverlayAllApps.setVisibility(View.VISIBLE);
        View closeBtn = findViewById(R.id.btn_close_all_apps);
        if (closeBtn != null) closeBtn.requestFocus();
    }

    private void hideAllAppsOverlay() {
        if (mOverlayAllApps != null && mOverlayAllApps.getVisibility() == View.VISIBLE) {
            mOverlayAllApps.setVisibility(View.GONE);
            if (mContainerCuratedApps != null && mContainerCuratedApps.getChildCount() > 0) {
                mContainerCuratedApps.getChildAt(0).requestFocus();
            }
        }
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchStockLauncher() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage("com.google.android.apps.tv.launcherx");
        if (intent == null) {
            intent = pm.getLaunchIntentForPackage("com.google.android.apps.tv.launcherx");
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Stock Google TV home not found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (mOverlayAllApps != null && mOverlayAllApps.getVisibility() == View.VISIBLE) {
            hideAllAppsOverlay();
            return;
        }
        // As the Home Launcher, pressing Back on the main screen should never exit or finish!
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mOverlayAllApps != null && mOverlayAllApps.getVisibility() == View.VISIBLE) {
                hideAllAppsOverlay();
                return true;
            }
            return true; // Consume Back so it never exits to stock launcher
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true; // Consume Back key release
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SafetyWatchdog.recordHealthyLaunch(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EmergencyHttpServer.stop();
        if (mTimeHandler != null && mTimeRunnable != null) {
            mTimeHandler.removeCallbacks(mTimeRunnable);
        }
    }
}
