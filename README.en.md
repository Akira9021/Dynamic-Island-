<p align="center">
<a href="README.md"><img src="docs/lang/ru-off.svg" height="46" alt="Русский"></a>&nbsp;&nbsp;<a href="README.en.md"><img src="docs/lang/en-on.svg" height="46" alt="English"></a>
</p>

<div align="center">

# 🏝️ Dynamic Island for Android

**An iPhone-style Dynamic Island around the camera of any Android phone**

Notifications with reply · navigation · payments · music with lyrics · calls · timers · downloads · charging · flashlight · system events

[![Version 1.6.5](https://img.shields.io/badge/version-1.6.5-C6C3F7?style=for-the-badge&labelColor=1C1B22)](https://github.com/Akira9021/Dynamic-Island-/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Akira9021/Dynamic-Island-/total?style=for-the-badge&label=downloads&color=34C759&labelColor=1C1B22)](https://github.com/Akira9021/Dynamic-Island-/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/Akira9021/Dynamic-Island-/android.yml?style=for-the-badge&label=build&labelColor=1C1B22)](https://github.com/Akira9021/Dynamic-Island-/actions)
<br>
![Android 8+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![No ads](https://img.shields.io/badge/ads-0-black?style=flat-square)

> [!NOTE]
> This fork adds a Samsung One UI look. On Samsung the island starts in One UI style and takes its accent
> from the current phone theme (One UI / Material You palette, then the wallpaper). If the theme color can't
> be read, the island turns gray. Change it in Appearance → Capsule color → Island look.

<img src="docs/preview.en.png" width="820" alt="Collapsed and expanded island views: idle, music, charging, timer, call, the player with lyrics, incoming call, headphones">

### [⬇️ Download the latest version](https://github.com/Akira9021/Dynamic-Island-/releases/latest)

[What's new](#-whats-new-in-162) · [Features](#-features) · [Installation](#-installation) · [Gestures](#-gestures) · [Settings](#-settings) · [How it works](#-how-it-works)

</div>

> [!NOTE]
> Since 1.6.1 the app and the island speak English and Russian — switch in Features → General → Language («Функции → Общие → Язык»). Russian labels in brackets are for older versions.

---

## ✨ What's new in 1.6.5

- 🎬 **Liquid expansion**: content grows with the island — Appearance → Animations
- 🎛️ **Your own buttons** in the expanded island, or just the clock — Features → Behavior
- 📸 **Screenshot without the island** — with a button or a gesture
- 🏦 **Bank app won't open?** The Island tile in quick settings turns the island off for a while
- 📞 **Calls** in any phone language; swipe an incoming call up to hide it
- 🫧 **A smoother collapsed island**: the bubble splits off like a drop, a 60 fps equalizer
- 📌 **Keep the island loaded** and **On a schedule**
- 🐞 Fixed: the previous track's artwork, "alarms" from calendars and a stuck "00:00"

<div align="center">
<img src="docs/app.en.png" width="820" alt="The app: Home with Keep the island running, Appearance with Elasticity, Position and size with width, height and Like a notch, Keep running and Try it">
</div>

> [!IMPORTANT]
> Coming from 1.6.0 or older, uninstall the old version once: since 1.6.1 the app has a new signing key.

All changes — in [CHANGELOG.en.md](CHANGELOG.en.md).

## 📱 Features

<div align="center">
<img src="docs/preview-1.6.1.en.png" width="820" alt="The island: Pay · Hold near reader, Paid with the amount, the alarm and sleep dial, Sent, the new version card, download with percent and Updated">
</div>

<table>
<tr><th>Card</th><th>In the capsule</th><th>On tap</th></tr>
<tr><td>🎵 <b>Music</b></td><td>Artwork to the left of the camera, a sound wave in the artwork color on the right</td><td>Artwork, title, synced lyrics, seeking, ⏪ ⏸ ⏩, audio output</td></tr>
<tr><td>📞 <b>Calls</b></td><td>Incoming — avatar, name and buttons right away; during a call — a phone with the time and a wave</td><td>Name, duration, End</td></tr>
<tr><td>⏳ <b>Timer</b></td><td>An hourglass and a countdown, the last 10 seconds in red</td><td>A remaining-time ring around pause, cancel; at the end — an alarm and vibration</td></tr>
<tr><td>⏱️ <b>Stopwatch</b></td><td>Time</td><td>Pause, reset, stop</td></tr>
<tr><td>⏰ <b>Clock</b></td><td>A Clock timer or stopwatch — from the notification or, if its notifications are off, from the app's screen; screen recording in red</td><td>Large time, paused state, open the app</td></tr>
<tr><td>🌙 <b>Alarm</b></td><td>12 hours before it rings: a moon and the wake-up time; when you set an alarm — a "Wake up 7:30 · sleep 8 h 15 min" banner</td><td>An iPhone-style sleep dial: an arc from now to the alarm, the alarm time and how much sleep is left</td></tr>
<tr><td>⬇️ <b>Downloads</b></td><td>The app icon and a progress ring (Google Play, Telegram…)</td><td>Name, bar and percentage</td></tr>
<tr><td>🔦 <b>Flashlight</b></td><td>A yellow icon</td><td>A card with a beam like in iOS 18: swipe for brightness (Android 13+), tap to turn off</td></tr>
<tr><td>🔔 <b>Notifications</b></td><td>A big banner: an avatar with the app badge, the name, the text and Reply / Mark as read buttons</td><td>Reply right inside the island; tap opens the app, swipe up dismisses</td></tr>
<tr><td>🧭 <b>Navigation, rides, deliveries</b></td><td>The turn arrow and distance, for rides and deliveries — the arrival time</td><td>Where to turn, arrival time, travel time, Open</td></tr>
<tr><td>⚡ <b>Charging</b></td><td>A bolt, percentage and a battery</td><td>A charge bar</td></tr>
<tr><td>🕐 <b>Idle island</b></td><td>A small capsule above the camera</td><td>Clock, date, battery, a 1–99 min timer, stopwatch, flashlight</td></tr>
</table>

If there are two activities — say, music and a timer — the second one moves into a round **bubble** next to the island. Tapping the bubble expands it.

### 🔔 Events

Short notices in the capsule, each can be turned on separately:

| | | |
| --- | --- | --- |
| ⚡ Charging started / stopped | 🪫 Low battery (5%) | 📶 Wi-Fi connected / disconnected |
| 🎧 Headphones connected / disconnected (with battery) | ✈️ Airplane mode | 🔕 Ring / vibrate / silent |
| 🔆 Adaptive brightness | 🔌 USB device | 🔓 Unlock |
| 🌙 Do Not Disturb | 💳 Hold near reader | ✅ Paid |

## 📥 Installation

1. Download **`DynamicIsland.apk`** from the [latest release](https://github.com/Akira9021/Dynamic-Island-/releases/latest) page and install it.
2. Open the app — on Home tap **Open accessibility** («Открыть спец. возможности») and turn on "Dynamic Island" («Динамический остров»).
   > On Android 13+ you'll see "Restricted setting": close it, open **App info → ⋮ → Allow restricted settings** and repeat step 2. The "⋮" menu item only appears after the first attempt.
3. Home → **Permissions** («Разрешения»): turn on **Notification access** («Доступ к уведомлениям») — without it there's no music, downloads or Clock timers.
4. There, tap **Calls** («Звонки») and allow access to the phone, call log and contacts — then the island shows incoming calls with any dialer.
5. On Android 12+ allow **Bluetooth** to see headphone names.
6. **Appearance → Position and size** («Оформление → Положение и размер») — the island turns red: move it until the red sits exactly over the camera and pick the width and height. Waterdrop camera — turn on **Like a notch** («Как вырез»).

> [!TIP]
> If the island disappears (Xiaomi and HyperOS, Oppo, realme, Samsung, Infinix…): **Permissions → Keep the island running** («Чтобы остров не закрывался») — unrestricted battery, autostart and locking in recent apps.

### If it won't install

| What the phone says | What to do |
| --- | --- |
| **"App blocked to protect your device"** / "Potentially harmful app" (Play Protect) | That's how Google treats any app not from Google Play that asks for accessibility. The code is open — you can check it. Play Store → profile icon → **Play Protect** → ⚙️ → temporarily turn off "Scan apps", install the island and turn it back on |
| **"There was a problem parsing the package"** | The file didn't download fully or was opened by the wrong app. Download `DynamicIsland.apk` from the release page in **Chrome** and open it from Chrome's downloads or **Files by Google**, not from TikTok, Telegram or video downloaders |
| **"App not installed"** over an old version | From 1.6.0 or older — uninstall the old version once: 1.6.1+ has a new signing key |
| **"Restricted setting"** / "App was denied access" when turning on the service | See step 2: **App info → ⋮ → Allow restricted settings** |

New versions install over old ones; your settings are kept (from 1.6.0 or older — reinstall once, see above).

## 👆 Gestures

| Action | Result |
| --- | --- |
| Tap | Expand (can be changed: open the app, pause, next track, nothing) |
| Double tap / long press | Configurable, long press opens the app by default |
| Pull down | Expand — the capsule follows your finger |
| Pull up, tap or touch outside | Collapse |
| Push left / right | Next / previous track |
| Tap a ringing timer | Stop the alarm |

## ⚙️ Settings

Four tabs:

- **Home** («Главная») — the live preview, whether the island is on, **permissions** and **Try it** («Проверка»): every island demo on one screen.
- **Features** («Функции») — what the island shows and how you handle it:
  - **Dynamic cards**, **notifications** and **events** — each can be turned off; notifications also have reply buttons, the number of text lines and the button color: lavender, system, from the avatar or custom.
  - **Lyrics** — in the expanded player.
  - **Gestures** and **Behavior** — when to show and collapse the island, the second-activity circle, haptics, turning the screen on for a call.
  - **System banners** — shortcuts to the Android settings where you turn off the pop-ups of the dialer, Clock and battery, so only the island shows these events.
  - **Language** — English or Russian, for the app and the island at once (General → Language).
- **Appearance** («Оформление») — how the island looks:
  - **Position and size** — horizontal offset, top offset, width, height and Like a notch; the capsule updates on screen right away.
  - **Size with activity** — how much thicker and wider the capsule with an activity is than the idle one, with a live preview.
  - **Capsule color** — color, outline (color, thickness, color from artwork) and shadow.
  - **Liquid Glass** *(experimental)* — a glass island and cards, glass density.
  - **Island expansion** — style ("Like a Fruit" with a jelly effect, "Spring", "Smooth", "No animation"), duration, elasticity, Reduce effects.
  - **Effects** — blur, live equalizer, shake, payment waves, artwork glow and battery saving.
- **About** («О приложении») — version and updates (an auto-check every 3 hours, Check now, What's new), instructions, navigation diagnostics and the GitHub page button («Страница GitHub») that leads here.

## 🧩 How it works

<details>
<summary>For developers</summary>

<br>

Kotlin, Jetpack Compose, Material 3. Requires JDK 17 and Android SDK 35.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk — for your own phone
./gradlew assembleRelease   # app-release-unsigned.apk — CI adds the release signature
```

Every push builds an APK in [GitHub Actions](https://github.com/Akira9021/Dynamic-Island-/actions). To release a new version, bump `versionName` and `versionCode` in `app/build.gradle.kts` and the version badge at the top of both READMEs, and add a `## [x.y.z]` section to [CHANGELOG.md](CHANGELOG.md) and [CHANGELOG.en.md](CHANGELOG.en.md) — after a push to the default branch the release with the APK and bilingual notes is created automatically. A `v*` tag works too.

| File | Responsible for |
| --- | --- |
| `service/IslandService.kt` | The accessibility service and two windows: drawing (full width, touches pass through) and touch (invisible, exactly over the island) |
| `service/NotifListener.kt` | Notification access: calls, downloads, timers from notifications |
| `service/MediaMonitor.kt` | Media sessions of any player, artwork and its color, lyrics |
| `service/SystemEvents.kt` | Charging, Wi-Fi, headphones, airplane mode, sound, brightness, USB, unlock |
| `lyrics/LyricsRepo.kt` | Lyrics: [LRCLIB](https://lrclib.net), then NetEase |
| `service/CallMonitor.kt` | Calls from the phone state, name and photo from contacts |
| `island/IslandOverlay.kt` | Choosing the main activity and the bubble, shape, springs, gestures |
| `island/Compact.kt`, `Expanded.kt` | Collapsed and expanded card views |
| `ui/` | The settings app |

Why two windows: if a window is resized during an animation, Android shows the old picture in the new place for a frame — and the island jitters. So the drawing window never changes its width, and the invisible touch window follows the island and forwards touches.

The signing key isn't stored in the repository: Gradle builds an unsigned APK, CI signs it with the key from GitHub secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`) and checks the certificate fingerprint. A local debug build is signed with the usual debug key.

</details>

## 💜 Credits

The idea and behavior come from [Dynamic Island for Windows](https://github.com/mihailkotovski/DynamicIsland) (mihailkotovski): spring animations, the player on click, a sideways push to change tracks, the ring timer, lyrics from LRCLIB. Lyrics — [LRCLIB](https://lrclib.net).

<div align="center">
<sub>Font <a href="https://github.com/rsms/inter">Inter</a> — SIL OFL 1.1 · Icons <a href="https://ionic.io/ionicons">Ionicons</a> — MIT · Made with <a href="https://claude.ai/code">Claude Code</a></sub>
</div>
