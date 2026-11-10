package io.safetv.launcher;

import android.content.Context;
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
            String responseBody;

            if (isRevert) {
                SafetyWatchdog.triggerSafetyRevert(context.getApplicationContext(), "Emergency HTTP Revert Request");
                responseBody = "<!DOCTYPE html><html><body style='font-family:sans-serif;background:#0f172a;color:#f8fafc;padding:30px;'>"
                        + "<h2>🛡️ SafeTV Emergency Revert Triggered</h2>"
                        + "<p>Stock Google TV Home has been restored on your TV.</p>"
                        + "<p>SafeTV interceptor disarmed.</p>"
                        + "</body></html>";
            } else {
                responseBody = "{\"status\":\"healthy\",\"server\":\"SafeTV\",\"port\":8888}\n";
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
