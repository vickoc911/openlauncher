# OpenLauncher Mod

A customized fork of the OpenLauncher project, specifically optimized for Android car head units (tested on **FYT 7870**, designed to work on **FYT devices**). This version focuses on a cleaner user interface, enhanced navigation widgets, and better automation for in-car use.

## 📌 Credits & Lineage
*   **Original Idea:** [dw2lam/openlauncher](https://github.com/dw2lam/openlauncher)
---

## ✨ Features

### 🧩 Modular Widget Grid
The home screen is a fully drag-and-drop, resize-capable grid. Every widget pane can be moved, scaled, and stacked however you want. Nothing is locked to a fixed position. Add and remove widgets from the built-in library at any time.

### 🎛️ Instrument Panel Widgets
A growing library of purpose-built car widgets designed to look like they belong on a dash — not a phone:

* **Smart Music Player** — pulls track metadata, album art, and playback controls from any local or streaming source. Detects when CarPlay or Android Auto is in use and switches into shortcut mode automatically.
* **AM/FM Radio** — instrument panel-style digital display that mirrors your head unit's **real tuner**. On szchoiceway-based units it talks to the MCU directly (seek, FM1/FM2/FM3/AM band switching, direct-tune frequency presets with memory). On every other unit it mirrors and controls your vendor radio app through its media session — assign the app once from the widget and get live frequency/station readout plus seek controls. No simulated stations or fake static.
* **Speedometer** — standalone GPS-based digital speed readout. Independent from the trip tracker so it can live anywhere on the grid.
* **Altimeter** — live elevation tracking pulled from the device GPS.
* **Trip Meter** — taxi-style rolling odometer display with trip distance and elapsed time. Includes a hidden **0–100 km/h timer** (tap the meter label to reveal it) that auto-starts from standstill and locks in your time at 100.
* **Head Unit Vitals** — real-time CPU load, memory pressure, and temperature readouts for monitoring your head unit's thermals on long drives.
* **Soundboard** — 6 fully assignable sound pads. Each pad can be set to a built-in synth type (HORN, BEEP, ALERT, KICK, SNARE, BASS, FART) or loaded with any custom audio file from device storage. Assignments persist across restarts.
* **Dynamic Weather** — auto-detects connectivity. Shows live weather when online, hides cleanly when offline. No broken blank widgets.
* **GPS Compass** — live bearing with calibrated heading display.

### 📌 Sidebar Shortcuts
The sidebar holds your most-used app shortcuts. Drag to reorder, long-press to remap, and position the entire bar on the Left, Right, or Bottom of the screen to suit your driving hand or interior layout.

### 🌗 Smart Day/Night Theme Engine
Four distinct modes for head units that can't always pass the car's headlight signal to Android:
* **Forced Dark / Forced Light** — static overrides.
* **System Sync** — follows the head unit's native light/dark setting.
* **Sunset Mode** — automatically switches at local sunrise/sunset using offline location calculations, no internet required.

### 🛰️ GPS with Offline Calibration
Speed and distance calculations use a rewritten GPS math layer with improved filtering. A calibration offset option is available for devices whose GPS chips report inaccurate baselines — accessible from the trip meter settings for reliable offline use.

### 🎨 Deep Personalization
Icons, Accent color, background color, gradient, wallpaper with adjustable dim, font weight, text scale, UI scale, and app font — all tunable from the settings menu, which is organized into logical sections (Appearance, Layout, Widgets, System).

### 🎨 UI Enhancements
*   **Decluttered Layout:** Removed the top bar entirely to maximize screen real estate.
*   **Sidebar Integration:** Essential elements from the top bar have been moved to the sidebar.
*   **Clean Look:** Removed the home button for a more streamlined aesthetic.
*   **Time & Date:** Added a dedicated date display.

### 🗺️ Enhanced Map Widget
*   **Google API Provider Extensions:** Added extra overlay layers to the native map widget.
*   **View Toggles:** Easily switch between **Satellite** or **Terrain** views.
*   **Real-time Data:** Integrated Google **Traffic info** overlay.

### 📱 Picture-in-Picture (PIP) Widget
*   **Multi-tasking:** A brand-new PIP widget that allows you to display apps pinned to the sidebar directly within the widget area.
*   **Smart Launching:** Apps opened via the app gallery automatically launch in fullscreen.
*   **Hardware Compatibility:** The implementation is tailored for FYT devices. The APK is signed with **AOSP test keys** (*actively tested and verified on FYT 7870 only*).

### 🧩 Widget Support
*   **Third-Party Widgets:** Added full support for standard Android widgets, allowing you to add and display widgets from other installed applications directly on your screens.*   

### ⚙️ Automation & Settings
*   **App Auto-Launch:** Added an option to launch specified apps automatically on boot.
*   **Configurable Delay:** Set a custom delay before apps launch on startup to ensure system stability during the boot sequence.

### 📦 Simplified App Gallery
*   **Unified View:** Removed confusing tabs. The app gallery now features a single, clean tab containing all installed applications that utilize a user interface.

### 📷 Screenshots 

## ✨ Current Features

This project is currently in active development, but the core foundation is highly customizable:

![Settings](Screenshots/settings.png?raw=true "Settings")

![Home2](Screenshots/home2.png?raw=true "Home2")

![Home](Screenshots/home.png?raw=true "Home")

![Home_white](Screenshots/home_white.png?raw=true "Home White")

![applibrary](Screenshots/applibrary.png?raw=true "applibrary")

### ☕ Buy Me a Coffee!

If this code helped you, your support allows me to continue maintenance.

[![Donate with PayPal button](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/donate/?business=XSHX7RDT74QN2&no_recurring=0&item_name=Support+my+code%3A+If+it+saved+you+time+or+helped%2C+please+consider+donating.+Your+support+keeps+this+Open+Source+project+alive%21&currency_code=USD)
 
