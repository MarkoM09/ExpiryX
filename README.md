<p align="center">
  <img src="docs/logo.png" alt="ExpiryX Logo" width="120"/>
</p>

<h1 align="center">ExpiryX (Beta Testing Release)</h1>

<p align="center">
  <em>Smart expiry tracking to reduce food waste and save money</em>
</p>

---

## 📖 About ExpiryX

**ExpiryX** is an offline-first Android application designed for Australian households to track pantry inventory, reduce food waste, and minimize grocery costs.

### 🧪 What's in this Beta Build?
This release is a **Beta Testing Build** designed for user evaluation. You will be testing:
- 📷 **Barcode & Label Ingestion:** Rapid product scanning using Google ML Kit.
- ⏱️ **4-Tap Quick Logging:** Fast manual and automated entry.
- 🎨 **Dual-Coded Interface:** Visual urgency indicators (traffic-light colors + geometric icons) adhering to WCAG 2.1 AA contrast.
- 🛡️ **Data Safety & Recovery:** 5-second "Undo" state migration for deleted items.
- 📊 **Local Data Management:** Offline storage retrieval and CSV data export.

---

## 📲 How to Install the APK

1. **Download the APK:**
   - Download `ExpiryX-v1.0.0-beta.apk` from the [Releases](../../releases) section.
2. **Enable Unknown Sources:**
   - Go to your Android device **Settings** > **Security** (or **Apps & notifications** > **Special app access**) and enable **Install Unknown Apps** for your browser or file manager.
3. **Install & Open:**
   - Tap the downloaded `.apk` file and select **Install**.
   - Launch **ExpiryX** and accept camera/notification permissions when prompted.

---


## 🔒 Privacy Policy & Device Permissions

* **Camera (`android.permission.CAMERA`):** Used exclusively for real-time barcode scanning. No image frames or photos are saved, transmitted, or uploaded.
* **Notifications (`android.permission.POST_NOTIFICATIONS`):** Used solely for local expiry reminders scheduled on your device.
* **Local Data Storage:** All pantry records, logs, and settings are saved strictly on your local device storage. ExpiryX does not collect personal analytics or sell user data.

---

## 🛠 Technical Requirements

* **Android Version:** Android 7.0 (API Level 24) or higher
* **Hardware:** Functional rear camera with autofocus
* **Network:** Offline-capable (Internet connection optional for extended product lookup APIs)
