package io.safetv.launcher;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class EmergencyHttpServer {
    private static final String TAG = "EmergencyHttpServer";
    private static final int PORT = 8888;
    private static ServerSocket sServerSocket;
    private static volatile boolean sRunning = false;

    public static synchronized void start(final Context context) {
        if (sRunning) return;
        sRunning = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sServerSocket = new ServerSocket(PORT);
                    Log.i(TAG, "Emergency HTTP Server listening on port " + PORT);

                    while (sRunning && !sServerSocket.isClosed()) {
                        final Socket socket = sServerSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(context, socket);
                            }
                        }).start();
                    }
                } catch (Exception e) {
                    if (sRunning) {
                        Log.e(TAG, "Server error: " + e.getMessage());
                    }
                }
            }
        }, "SafeTV-HttpServer").start();
    }

    private static void handleClient(Context context, Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }

            Log.i(TAG, "HTTP Request: " + line);
            boolean isRevert = line.contains("/revert");
            boolean isArm = line.contains("/arm");
            boolean isDashboard = line.contains("/dashboard") || line.contains("/settings");
            boolean isHdmi2 = line.contains("/hdmi2");
            String responseBody;

            if (isRevert) {
                SafetyWatchdog.triggerSafetyRevert(context.getApplicationContext(), "Emergency HTTP Revert Request");
                responseBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;'>"
                        + "<h2>🛡️ SafeTV Emergency Revert Triggered</h2>"
                        + "<p>Stock Google TV Home has been restored on your TV.</p>"
                        + "<p><a href='/arm' style='color:#38bdf8;'>Click here to Re-Arm Zenith Launcher</a></p>"
                        + "<p><a href='/' style='color:#94a3b8;'>Back to Control Panel</a></p>"
                        + "</body></html>";
            } else if (isArm) {
                SafetyWatchdog.resetSafetyMode(context.getApplicationContext());
                Intent launchIntent = new Intent(context, MainActivity.class);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                context.startActivity(launchIntent);
                responseBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;'>"
                        + "<h2>✨ Zenith TV Launcher Re-Armed</h2>"
                        + "<p>Zenith Launcher is now active and in foreground.</p>"
                        + "<p><a href='/' style='color:#38bdf8;'>Back to Control Panel</a></p>"
                        + "</body></html>";
            } else if (isDashboard) {
                try {
                    Intent dashIntent = new Intent("android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL");
                    dashIntent.setPackage("com.google.android.apps.tv.launcherx");
                    dashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(dashIntent);
                } catch (Exception e1) {
                    try {
                        Intent dashIntent = new Intent();
                        dashIntent.setClassName("com.google.android.apps.tv.launcherx", "com.google.android.apps.tv.launcherx.dashboard.DashboardActivity");
                        dashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(dashIntent);
                    } catch (Exception e2) {
                        Intent sysIntent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                        sysIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(sysIntent);
                    }
                }
                responseBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;'>"
                        + "<h2>📺 TV Quick Settings / Dashboard Opened</h2>"
                        + "<p>Dashboard overlay opened on TV screen.</p>"
                        + "<p><a href='/' style='color:#38bdf8;'>Back to Control Panel</a></p>"
                        + "</body></html>";
            } else if (isHdmi2) {
                try {
                    android.net.Uri uri = android.net.Uri.parse("content://android.media.tv/passthrough/" + android.net.Uri.encode("com.realtek.tv.passthrough/.hdmiinput.HDMITvInputService/HW151519744"));
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception ignored) {}
                responseBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;'>"
                        + "<h2>🎮 Switched to HDMI 2 (Xbox)</h2>"
                        + "<p><a href='/dashboard' style='color:#38bdf8;'>Open Picture & 120Hz Game Mode Settings</a></p>"
                        + "<p><a href='/' style='color:#94a3b8;'>Back to Control Panel</a></p>"
                        + "</body></html>";
            } else {
                responseBody = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'><title>Zenith TV Control Panel</title>"
                        + "<style>body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;background:#0b0f19;color:#e2e8f0;padding:24px;max-width:480px;margin:auto;}"
                        + "h1{font-size:22px;color:#38bdf8;margin-bottom:8px;}p{color:#94a3b8;font-size:14px;}"
                        + ".btn{display:block;width:100%;box-sizing:border-box;padding:14px 18px;margin:12px 0;background:#1e293b;color:#f8fafc;text-decoration:none;border-radius:10px;font-weight:600;font-size:15px;text-align:center;border:1px solid #334155;}"
                        + ".btn-primary{background:#0284c7;border-color:#38bdf8;}"
                        + ".btn-danger{background:#7f1d1d;border-color:#ef4444;color:#fecaca;}"
                        + ".btn-success{background:#14532d;border-color:#22c55e;color:#bbf7d0;}</style></head><body>"
                        + "<h1>Zenith TV Controller</h1><p>Acer Android TV (192.168.20.2)</p>"
                        + "<a class='btn btn-primary' href='/dashboard'>📺 Open TV Quick Settings / 120Hz Game Mode</a>"
                        + "<a class='btn' href='/hdmi2'>🎮 Switch to HDMI 2 (Xbox)</a>"
                        + "<a class='btn btn-success' href='/arm'>✨ Re-Arm Zenith TV Launcher</a>"
                        + "<a class='btn btn-danger' href='/revert'>🛡️ Revert to Stock Google TV</a>"
                        + "</body></html>";
            }

            byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: " + (isRevert ? "text/html; charset=UTF-8" : "application/json") + "\r\n"
                    + "Content-Length: " + bodyBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bodyBytes);
            out.flush();
            socket.close();
        } catch (Exception e) {
            Log.e(TAG, "Client handle error: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        sRunning = false;
        if (sServerSocket != null) {
            try {
                sServerSocket.close();
            } catch (Exception ignored) {}
            sServerSocket = null;
        }
    }
}
