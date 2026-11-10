# 🏔️ Zenith TV Launcher

An ultra-premium, 100% bootloop-proof, high-performance custom launcher designed for 4K HDR Android TV and Google TV devices.

Developed by Unni Ampadi with Antigravity ♥

---

## ✨ Features

- **Ultra-Premium 4K Aesthetic:** Dusk mountain lake landscape with natural water surface ambient lighting.
- **Whole-Icon Radiant Glow:** Cards illuminate smoothly on selection with an organic, brand-colored Gaussian halo bloom and specular surface highlight.
- **Dynamic Lake Reflection:** Soft, horizontally diffused water reflections bloom in real time beneath only the selected icon.
- **Hardware Panoramic Inputs:** Dedicated quick-switch tiles for HDMI 1 (eARC Dolby Atmos), HDMI 2 (Xbox Console), HDMI 3 (Media Box), and AV Composite (TV).
- **100% Bootloop-Proof Safety:** Never disables or modifies the stock system launcher at the OS level. Safe across all reboots.
- **Deterministic D-Pad Navigation:** Built for smooth TV remote control with hardwired 4-way focus navigation.
- **Built-in Safety Watchdog:**
  - **Remote Panic Sequence:** Press **Volume Down 3 times** rapidly to instantly release all intercepts and return to stock TV home.
  - **Emergency HTTP Portal:** Web interface running on port `8888` (`http://<tv-ip>:8888/revert`) to revert remotely from any phone or PC on the same network.

---

## 📦 Download & Installation

Download the latest pre-compiled APK from the [Releases](https://github.com/unni61771/zenith-tv-launcher/releases) tab.

### Option 1: Sideload via USB or "Send Files to TV"
1. Transfer `ZenithTV-Launcher.apk` to your TV using a USB drive or the **Send Files to TV (SFTV)** app.
2. Install the APK using any file manager on your TV.
3. Open **Zenith TV Launcher**.
4. To enable continuous Home/Back button trapping: Go to **TV Settings → Device Preferences → Accessibility → Zenith TV Launcher** and toggle it **ON**.

### Option 2: Quick ADB Installation
```bash
adb install -r ZenithTV-Launcher.apk
adb shell am start -n io.safetv.launcher/.MainActivity
adb shell settings put secure enabled_accessibility_services io.safetv.launcher/io.safetv.launcher.HomeInterceptorService
adb shell settings put secure accessibility_enabled 1
```

---

## 🛠️ Building from Source

Requirements:
- Android SDK (API 34)
- JDK 17
- AAPT2, D8, ZipAlign, APKSigner

```bash
git clone https://github.com/unni61771/zenith-tv-launcher.git
cd zenith-tv-launcher
bash build.sh
```
The output APK will be generated at `build/SafeTV.apk`.
