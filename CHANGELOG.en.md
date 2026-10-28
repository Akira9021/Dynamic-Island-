# What's new

[🇷🇺 Русская версия](CHANGELOG.md)

Versioning: the second number (1.5, 1.6…) means new and changed features, the third (1.5.1…) means optimization and small fixes.

> Since 1.6.1 the app and the island speak English (Features → General → Language). Earlier versions were in Russian only, so older entries give the Russian label in brackets.

## [1.7.0] — 2026-10-28

### 🎛️ More settings — as you asked
- 🔔 **Notifications**: a Card or Pill style; how long to show them — up to 30 seconds or "Until you swipe it away"; **Notification history** — unread ones wait in the island as app icons and as a list when expanded; **Skip repeats**; **Silent notifications** (Snapchat, Instagram and many others) and **rules for each app** — always or never; **Why it didn't show** — what the island decided about recent notifications.
- 🎨 **Appearance**: island **opacity**; the **width** of the expanded island and notifications and **text and icon size**; the **font** — Inter, the system font or your own .ttf/.otf file — and its **weight**; a **delay before showing** and events shown for up to 10 seconds; capsule width up to 400 dp, height up to 90 dp, width with activity up to +200 dp.
- 📱 **Behavior**: in landscape — hide, **At the camera** (like on iPhone: the island turns with the phone and doesn't move) or top center; **on the lock screen** — show or hide until unlocked; the second-activity circle **on the left or right**.
- 🔘 **Expanded island buttons**: **Sound mode** (ring → vibrate → silent) and **Do Not Disturb**.
- ⚡ **Charging**: "Only when plugged in" — a banner for a couple of seconds without the percentage capsule; the expanded island while charging has the clock and buttons.

### 🌍 Languages
- **Indonesian, Portuguese (Brazil) and Kazakh.** "System default" picks your phone's language by itself.

### 🧩 Compatibility
- 🎙️ Voice recorders and screen recording (Xiaomi, Samsung, Oppo, Huawei and others) — red recording with the time and Stop.
- ⏱️ Clock timers and stopwatches that only have the time in the notification text (some Xiaomi, vivo and others).
- 🎧 **Discord**, Zoom, Teams and other calls — incoming with buttons and an ongoing call with the time.

### 🐞 Fixes
- ✨ The island no longer flickers when expanding and collapsing (ColorOS and other skins): the island window doesn't change size while it works, and the touch window moves once, when the island has settled.
- 💬 Consecutive messages in one conversation (WhatsApp and others) all show, not only the first one.
- 💳 Payment: a "Transfer …" right after the tap-to-pay screen counts as the payment; "Payment successful" on screen without an amount shows as "Paid"; Belarusian banks; "Payment diagnostics" also records the screen after paying.
- 🔒 The lock on the lock screen shows even with "Hide the empty capsule", and when the lock kicks in later than the screen turns on.

## [1.6.5] — 2026-10-28

- 🎬 **Liquid expansion** — new in Appearance → Animations: the content grows together with the island and emerges from a blur; duration and bounce follow your settings.
- 🎛️ **Your own buttons in the expanded island**: timer, stopwatch, flashlight, screenshot without the island, camera, lock — or just the clock (Features → Behavior).
- 📸 **Screenshot without the island** — with a button in the expanded island or a double tap / long press (Gestures).
- 🏦 **Bank app won't open?** The Island tile in quick settings turns the island off for a while and back on (Home → Bank app won't open).
- 📞 **Calls**: Answer and Decline work in any phone language, and without the permission they open the call screen; swipe an incoming call up to hide it.
- 🫧 **A smoother collapsed island**: the bubble splits off like a droplet, a hidden island comes out of the camera, artwork and color change smoothly; the equalizer is thinner, calmer and smoother — 60 fps (Appearance → Effects).
- 📌 **Keep the island loaded**: it no longer disappears when you swipe the app away from recents (Home → Keep the island running).
- ⏰ **On a schedule**: the island shows only in the hours you pick (Features → Behavior).
- 💬 **No more double messages**: System banners lists messengers that still pop up their own banner, with a shortcut to turn it off.
- 🔋 Lighter on the phone: a hidden island draws nothing, and the touch window isn't moved during animations.
- 🐞 Fixed: the previous track's artwork on the next one; "alarms" from calendars and reminders; a stuck "00:00" from another app's countdown; with Like a notch, the glow and waves have a flat top.

## [1.6.2] — 2026-10-27

- 📏 **A smaller island** (104×31), with width and height adjustable again: Appearance → Position and size.
- 💧 **Like a notch** — for waterdrop cameras; an island moved sideways no longer pushes banners off the screen.
- 🎬 **Softer animations**: light bounce by default, Elasticity right on the Appearance tab.
- 🔒 **A lock** in the capsule on the lock screen.
- 🎵 **Like and Dislike** in the player — Yandex Music and others.
- 💳 **More reliable "Paid"** (wallets, the "Payment successful" screen, more banks) and Payment diagnostics.
- 🛠️ **Keep the island running** on Home: battery, autostart, locking in recent apps; help for "Restricted setting".
- ⬇️ New versions are checked **every 3 hours**.

## [1.6.1] — 2026-10-25

⚠️ **New signing key.** The old key became public, so since 1.6.1 the app is signed with a new one. If you have 1.6.0 or older, the update won't install over it: uninstall the old version once, install 1.6.1, then turn accessibility and notification access back on. After that, updates install over the top as before.

### 🌍 English
- **The app and the island speak English**: every screen and island banner ("Charging", "Do Not Disturb", "Silent", "Paid", "Hold near reader"…), the alarm, player, navigation, calls and the update card. The wording matches the promo video; long English labels shrink a little instead of running into the camera.
- **Features → General → Language** («Функции → Общие → Язык»): System default (Russian if the phone is in Russian, otherwise English), Русский or English. It switches right away, no restart needed.
- The app name and the accessibility service description follow the phone's language. If a navigation app sends Russian units, the English island shows them in English too: "4.2 km", "12 min".
- With a 12-hour clock, AM/PM is smaller than the digits, like on iPhone, so alarm and clock times don't stretch the capsule.

### ⬇️ Updates right in the island
- Like in the video: "Version is available" with Later and Update, percent and a ring while it downloads, and after installing — "Updated" with What's new and Done.
- If the APK is already downloaded but the installer didn't open (a permission is needed), the button becomes **Install** and installs the downloaded file without downloading it again. A download error shows in full right on the card, and a long caption shrinks a little instead of being cut off.
- Try it («Проверка») has an **Update** demo: the card, running percent and "Updated", nothing is downloaded.
- An update download no longer stops when you leave the About screen, and Install installs only the downloaded file of that very version.

### 🗂️ The app, reorganized
- **Four labeled tabs**: Home, Features, Appearance and About («Главная», «Функции», «Оформление», «О приложении»). Settings are separate from the version, help and links.
- **Home** keeps just the essentials: the live preview, Island on («Остров включён»), **Permissions** («Разрешения») in one row (all granted or what's missing) and **Try it** («Проверка»). While the accessibility service is off, a "Turn on the island" card with a button and instructions sits at the top.
- **Try it** shows every demo on one screen as tiles: event, ring mode, message, call, timer, alarm, payment, navigation and update (alarm and update are new).
- **Features** — what the island shows (cards, notifications, events, lyrics), controls (gestures, Behavior, system banners) and general (language, resetting settings).
- **Appearance** — position and size, color and Liquid Glass, island expansion and effects.
- **About** — the icon, version and whether an update is out, update checks, **What's new** at any time, instructions, navigation diagnostics, GitHub and About the island («Об острове»).
- New screens: **Behavior** («Поведение»: when to show the island, collapsing, haptics, turning the screen on) and **Notifications** («Уведомления»: everything about messages in the island, including the button color). Dynamic cards and Events are now tidy sectioned lists without extra words.

### 🔔 Notification banner — sized like the system one
- Width, margins and sizes now match an Android heads-up notification: 16 dp from the screen edges, a 40 dp avatar, 15 sp name, 14 sp text, 14 sp buttons, a smaller counter and reply field.
- In iPhone island style: the app icon and name sit to the left of the camera and "now" with a bell to the right, and the notification itself starts below the status bar — at the same height as the system one, not right against the camera. The height follows the status bar and how far the island is lowered in Position.
- Text grows with Android's font size without breaking the banner: the name and buttons are shortened with an ellipsis, the text takes up to three lines.

### 💬 Replying from the island works
- Tapping the keyboard no longer closes the reply field: before, the very first letter collapsed the banner, so you couldn't reply at all.
- The input closes when you hide the keyboard (Back), swipe the banner up, or tap outside the island while there's no keyboard. While a reply is open, Back closes the input instead of doing nothing.
- The banner can't be swiped away by accident when your finger slides a little while tapping "Reply" — the swipe-up threshold is twice as large.
- If a reply fails to send, you see "Не удалось отправить ответ" ("Couldn't send the reply") and the field keeps your text.
- Your own reply no longer comes back to the island as a new banner when the messenger adds it to the notification.

### 🌙 Alarm — new
- When you set an alarm, the island shows a banner: a small **sleep dial**, "Wake up 7:30" and how much sleep is left — "sleep 8 h 15 min".
- 12 hours before the alarm it appears in the island: a moon on the left, the wake-up time on the right. Expanded — **a sleep dial like iPhone's Sleep schedule**: 24 hours around a circle, an arc from now to the alarm shifting from night indigo to morning orange, a moon and an alarm at its ends, the sleep left in the middle; next to it the large alarm time, "Wake up · tomorrow" and "Sleep — 8 h 15 min". A tap opens the Clock alarms.
- The alarm comes from the system — it works with any Clock app, even with its notifications off. In the daytime "Wake up" and "sleep" become "Alarm" and "in". The expanded idle island shows the next alarm under the date.
- Turn off separately: Dynamic cards → Alarm («Динамические карточки → Будильник») and Events → Alarm set («События → Будильник поставлен»).

### ⏱️ Clock timer and stopwatch without notifications
- If Clock's notifications are off, Android gives them to no one. Now the island watches the Clock screen itself while it's open: tap Start — the timer or stopwatch is in the island **right away** and keeps running after you leave the app; Pause — it freezes with a pause icon; Reset or delete — it disappears. Without a tap the timer is recognized by its moving digits: a stopwatch within a fraction of a second, a timer within a second.
- The island doesn't keep walking the whole Clock screen: it remembers the time labels and refreshes only them, and gets screen changes and taps only while Clock is open — the app doesn't lag.
- If Clock's notifications are on, the island still takes the timer from them.
- **Buttons like the island's own timer**: the expanded island shows pause/resume and reset for the Clock timer and stopwatch, and they control Clock itself. With Clock's notifications on, the island taps the notification's buttons — instantly and without opening the app. With them off, it opens Clock for a moment, finds the button by its label or id, checks whether the timer is really running (so a round toggle button isn't pressed the wrong way), taps it and brings you back; while a timer runs Reset isn't shown, so the island pauses it first.
- Pausing from the island no longer "bounces back": the island knows it pressed Pause and doesn't take the last step of the digits while Clock was stopping as the stopwatch running; when paused it shows exactly the time Clock shows. A stopwatch with fractions of a second is seen as paused almost instantly.
- Pausing from the island also sticks when the island briefly opens Clock: Clock reports the tap with the button's new label ("Resume"), and the island took that for Start — Clock was paused while the time in the island kept running. Now the island recognises its own tap and goes back only once Clock has actually stopped.
- **Inactive ones don't linger**: a paused, reset, deleted or finished Clock timer or stopwatch leaves the island — a pause stays visible for 3 seconds, and a reset or delete in Clock is noticed even as you leave the app.
- Less delay: the island also recognizes a Clock button by its id (Google Clock's round button), and reads the Clock screen one last time as you leave — a timer started right before leaving isn't lost.
- "Set a timer" from a voice assistant, widget or another app can go to the island: "Dynamic Island" now appears in the chooser, and the timer starts right in it, without Clock.
- System banners now has a tip: choose "Silent" and "Minimize" for Clock's notifications instead of turning them off completely, then everything works instantly.

### 💳 Payments: only when you pay with the phone
- **"Pay · Hold near reader" appears only on a phone payment screen** — T-Pay, SberPay, Mir Pay, Yandex Pay, Google Wallet and others: the island waits for "Hold your phone up to the terminal" or Google's payment window, not just an open bank app. The island asks the system which apps can pay over NFC, so any bank works, not only known ones.
- The banner **stays until you pay or leave the app** — before, it disappeared after 5 seconds in T-Pay. Android's NFC "Pay with…?" question doesn't count as leaving. Swipe it up to dismiss; it also goes away when the screen turns off and, just in case, after 3 minutes.
- To get it right when you open a bank app, as before, turn on Events → **Hint when opening a bank** («События → Подсказка при открытии банка», off by default).
- **"Paid" with the amount** appears as soon as the wallet (Mir Pay, Google Wallet, Samsung Wallet) shows its check mark and amount — no waiting for the bank's notification, which often comes later or never. Before, the "Pay" banner went away and the amount never showed. In bank apps the amount counts if it appeared after the payment screen, next to "Paid" or while the "Hold your phone" hint is still visible; the transaction history isn't taken for a payment.
- "Paid" understands notifications from any bank and **bank SMS** (900, T-Bank, VTB and others); the same payment from the screen and from a notification isn't shown twice. An amount like "*1234. 89.99 RUB" is read correctly.
- Like in the video: NFC waves spread from the island while it waits for the reader, and on "Paid" it bounces and a ring and sparks burst out.

### 🎬 The island as in the promo video
- **Icons** are the same simple shapes as in the video: bolt, phone, moon, hourglass, bell, flashlight, headphones, Wi-Fi, plane, navigation arrow, player buttons.
- **Animations** as in the video: the island morphs with a spring and a slight overshoot, old content fades out with a blur, new content appears from 90% size. The default Duration (35%) matches the video exactly; the slider still speeds things up and slows them down.
- **Full-width events**: icon and name on the left, the value in color on the right — "Charging 68%" with a battery, "Headphones 80%" with a ring, "Wi-Fi Connected", "Do Not Disturb On". Long names shrink a little instead of an ellipsis.
- **Capsule**: timer — hourglass and large time, call — phone, time and a green wave, navigation — arrow and bold "300 m", Silent — at full size.
- **Incoming call** — avatar, "Incoming call" and the name, two round buttons; the green one breathes.
- **Navigation**: a banner with the arrow, distance, street and arrival time; expanded — a circle with the arrow, the distance large and an Open button.
- **Player**: bigger artwork, the lyrics as one line that fills with white as it's sung, a thin bar and three buttons.
- **Flashlight**: a square card with a yellow flashlight in a glow and "Flashlight · 80%"; swipe to change brightness.
- **Reply**: the send button sits next to the field; after sending — "Sent ✓" and to whom.
- **Updates right in the island**: "Version available", what's new and Later / Update buttons — percent and a ring while it downloads; after installing — "Updated ✓".

### ✨ Effects — each one is a setting
- A new **Appearance → Animations → Effects** section («Оформление → Анимации → Эффекты»): **blur on change**, **live equalizer** (turn it off and the bars stand still, which saves power), **shake** of the ring-mode pill and the timer bell, **payment waves** (NFC waves and the Paid burst). Reduce effects («Уменьшить эффекты») still turns everything off at once, and the section has its own Reset.
- New **artwork glow**: a soft halo in the artwork's color around the island while music plays. You can see it on the island preview in settings too. Off by default.
- **Battery saving** («Экономия заряда», off by default): when Android's battery saver is on, the island skips live effects by itself.

### 🧭 Behavior
- Features → Behavior («Функции → Поведение»): **Collapse the expanded island** («Сворачивать раскрытый остров») — by itself after 5, 10, 20 or 30 seconds without a touch (by default only by tapping outside or swiping up); **Second activity alongside** («Вторая активность рядом») — the small circle with the second activity next to the capsule can be turned off.
- Features → **Notifications** («Функции → Уведомления»): **From the open app** («Из открытого приложения») — also show notifications from the app that's on screen; **Text lines** («Строк текста») in the banner — from 1 to 6 (it was always 3).

### 🔋 Battery
- While the screen is off, the island doesn't wake up: the time in its timers and clocks, Clock screen reading and the lyrics search wait until the screen turns on.
- The alarm in the island is checked exactly twice — when it's 12 hours away and when it rings — instead of every half a minute.
- The sleep dial in the capsule no longer pulses its glow: before, it could redraw the island every frame for hours.
- The equalizer, the payment icon, the flashlight dot and the answer button now just redraw instead of rebuilding the island every frame; with Reduce effects the flashlight and call animations don't spin for nothing.
- A payment app's screen is read only while it's open and can pay over NFC, and no more often than every 0.4 s.
- Unused icons and code were removed.

### ⚡ Animations without delay
- Animation speed is still set by the Duration setting, and animations start right away: the island content, the ring-mode pill, event icons and the Paid check no longer wait before starting, and banners follow each other almost without a pause.
- **Unlock** shows as soon as the lock screen goes away, not when the system message arrives.

### ⚙️ Everything follows the settings
- **Event display** («Показ событий») moved to Appearance → Island expansion («Оформление → Раскрытие острова»), next to duration and elasticity, and resets with them. It now sets the time of **all** banners: a message without buttons stays 2 s longer, with buttons 4.5 s, alarm and navigation 2 s, "Paid" 0.9 s, the update banner 3.5 s. A paused Clock timer or stopwatch leaves after the same time.
- New switches: Features → Behavior → **Turn on the screen** («Включать экран») for a ringing timer, alarm or incoming call; Dynamic cards → **Clock buttons** («Кнопки для «Часов»», off — the island's buttons just open Clock) and **Timer from assistant** («Таймер от помощника», off — the island isn't offered when asked to set a timer).

### 🔒 Security
- **A new signing key** is kept only in GitHub secrets, not in the code; the build checks that the APK is signed with exactly this key. This way nobody can pass off their own build as an island update.
- The "Set a timer" command starts a timer in the island only from apps Android allows to set alarms and timers (the assistant, Clock, widgets) — not from any app.
- The update installs only the island itself: if the downloaded file holds a different app, the installer won't open.
- The service description and the Permissions screen say exactly what of the data the island reads goes online: only the song title and artist, to find lyrics.
- GitHub build: code and dependencies build without write access to the repository, the release is published in a separate step without third-party actions, test APKs are kept for a week.

### 🔗 Small things
- Added an easter egg.
- The Single tap button is gone from Dynamic cards («Динамические карточки») — single tap is set in Gestures («Жесты»).
- Every setting was checked: each toggle changes exactly what it shows and affects the island.
- About → **GitHub page** («О приложении → Страница GitHub») with the GitHub icon — opens the project's main page.
- The Liquid Glass label is now **«Экспериментальный»** ("Experimental").

### 🔕 System banners
- A new **Features → System banners** screen («Функции → Системные плашки»): the dialer, Clock, System UI (battery) and Android System (USB charging) — a tap opens their notification settings, right on the needed channel if the island has already seen it. Turn off "Pop on screen" there: the notification stays in the shade, and only the island shows the event on screen. Messenger notifications aren't affected.
- With the system banner off, **the island turns the screen on**: for an incoming call, a Clock alarm or timer and its own timer. While it rings the screen stays on; if you turn it off with the power button, the island won't wake the phone again until the next alert.

### 🔒 Lock screen
- On a locked phone the island behaves like the notification shade: no "Reply" or "Mark as read" buttons, and if the system hides notification content — only the app name and "Содержимое скрыто" ("Content hidden"). "Secret" notifications aren't shown there at all. The reply field closes when the screen turns off.

### 🐞 Fixes
- **Paid** no longer shows up for "payment failed", "amount due", "don't forget to pay", "will be charged", "Payment due" or notifications without an amount. Amounts like "500р" are now recognized.
- **The keyboard** no longer counts as the open app: while you type in a messenger, its own messages aren't duplicated in the island, the player card doesn't come back, and navigator screen reading doesn't stop.
- **Timer** rings on time with the screen off — a system alarm wakes the phone. When the timer rings, **the screen turns on** and stays on until you stop it; the power button stops the alarm, like on iPhone.
- **Updates** are checked in the background every 6 hours, as promised, and a banner on the island tells you about a new version (tap opens the app). A failed check without internet is retried sooner; a partly downloaded or damaged APK doesn't open in the installer.
- **USB**: a cable from a computer shows one "Charging" banner, without a second "USB" one.
- **Bluetooth**: a device whose type can't be read is no longer called "Headphones".
- **Battery and memory**: the artwork color isn't recomputed on every pause, regular notifications from delivery apps aren't parsed as navigation, and navigation diagnostics keep only the latest entries.

## [1.6.0] — 2026-10-23

### 🔔 Big notification banner, like the notification shade
- A messenger message arrives as a **large banner** almost the full width of the screen: a round avatar with a small app badge on the left, the name, "• now" and a bell next to it, an unread counter with an arrow on the right ("3 ⌄" expands the conversation), and the text below. The banner sits below the camera cutout, so the name and text are fully visible.
- Under the text are **the notification's own buttons**: "Reply" and "Mark as read" (or whatever the app provides — Telegram, WhatsApp, mail) in a bold accent color. "Reply" opens an input field right inside the island with a round send button with an up arrow; the reply goes to the messenger.
- While you reply or read the conversation, the banner doesn't disappear on a timer; tapping outside closes the input.
- Turn it off in Dynamic cards → Notification buttons («Динамические карточки → Кнопки в уведомлениях»).

### 🎨 Notification button color
- Capsule settings → Capsule color → **Notification buttons** («Настройки Капсулы → Цвет капсулы → Кнопки в уведомлениях»): choose the color of "Reply", "Mark as read", the counter and the send button — **lavender** (default), **system** (wallpaper color on Android 12+, Material You), **from avatar** (picked from the avatar or the app icon) or **custom** from a palette. A live preview is shown on top.

### ⚙️ Settings by category
- **Capsule settings** look the same as before — live preview, Position, and the sections Island look, Colors, Animations, Haptics, Other; the bottom tabs are in place. Long sets of sliders moved into categories, like Position: **Size with activity** (thickness and width of the capsule with an activity, with a live preview) and **Island expansion** (style, duration, elasticity, Reduce effects, reset). Liquid Glass, haptics and island hiding are toggled right on the tab.
- **Colored icons** next to items on every screen — like iPhone Settings: sections are easier to tell apart at a glance.
- On the Dynamic cards screen the "Instructions" button became a round lightbulb icon — the title no longer breaks mid-word.

### 💊 Capsule width with an activity
- Capsule settings → Size with activity → **Width** («Настройки Капсулы → Размер с активностью → Ширина»): how much wider the collapsed capsule with navigation, flashlight, music or a timer is than the idle one — from "same as idle" to +120 dp. While you drag the slider, the island shows an example (an arrow and "300 m"), so you can see right away whether it overlaps the status bar icons.
- In a narrow capsule the icons and text shrink neatly and stay at the edges without going under the camera; the capsule widens only when there's no other way.

### 🫧 Liquid Glass — redone, experimental
- The island, bubble and banners become **glass that catches the light**: a translucent smoky body, a light spot on top and a caustic at the opposite edge, a glow along the rim, bright lens highlights in the top-left and bottom-right corners, a thin colored fringe (dispersion) and a soft shadow.
- **The highlights are alive**: they shift when you tilt the phone, and a streak of light runs across the glass when the content changes. The tilt sensor is used only while Liquid Glass is on.
- **Glass density** slider (40–95%). Liquid Glass is **off** by default; in settings it's marked with an **Experimental** («Эксперимент») badge.
- Android doesn't allow real blur and refraction of what's under an overlay, so they are only imitated.

### 💳 Payments
- Open **Google Wallet, T-Bank, Alfa-Bank, Sber, VTB**, Raiffeisen, Gazprombank, Samsung Wallet, Mir Pay and others — the island shows the **contactless payment symbol** (the one printed on terminals and cards) with running arcs and the caption "Hold near reader". At most once every 30 seconds.
- When the bank or wallet sends a payment notification — **"Paid"** with a green check that draws itself, an expanding ring, a light vibration and the amount.
- Turn it on and off in Events → Payments («События → Оплата»).

### 🧭 Navigation, rides, deliveries
- Ongoing notifications of **Google Maps, Yandex Maps and Navigator, 2GIS, Waze, OsmAnd**, as well as **Yandex Go, Uber, Bolt, Yandex Eats, Samokat, SberMarket** and others are shown like an iPhone Live Activity.
- **A wide banner first, then the capsule**: when navigation starts, the island expands for a few seconds — the turn arrow and distance on the left, the hint and time on the right — and then shrinks into the capsule. The capsule shows the **turn arrow** on the left (left, right, straight, U-turn) and the **distance** on the right; if the direction is unknown, the app icon.
- Distance, time and arrow come from the notification, and if it's empty (2GIS and Yandex Navigator only say "Navigator is running") — **from the navigator's own screen** while it's open: "12 km", the street, "17:16 · 44 min". The island recognizes the **turn arrow** from the maneuver picture on the navigator screen (Android 11+). Under the distance there's a **hint** ("Turn right onto …"; if the screen has none — built from the arrow and the street) and the line **"Arrive 12:31 · 19 h · 1390 km"**: arrival time, travel time and distance left. The screen is read only for navigation apps; when you leave the navigator, this data is removed after 15 seconds so outdated meters aren't shown.
- General → **Navigation diagnostics** («Общие → Диагностика навигации»): shows exactly what a navigator, ride or delivery app sends and copies it with one tap — useful if the distance isn't shown.
- As a second activity it moves into the bubble next to the island. Turn it off in Dynamic cards → Navigation, rides and deliveries.

### 🌙 Do Not Disturb
- New event: when Do Not Disturb is turned on or off, the island shows a moon and the state. Events → Modes («События → Режимы»).

### 🔄 In-app updates
- Every 6 hours the app checks GitHub for a new version. If there is one, the home screen shows a **"Version X is available"** card with **Update** and **Later** buttons and a What's new list. "Update" downloads the APK and opens the installer over the current version; settings are kept. On the first install Android will ask you to allow the island to install apps.
- After an update, a **What's new** window is shown on first launch — what changed and what was fixed.
- General → Updates («Общие → Обновления»): an auto-check toggle and a Check now button.

### 🔋 Optimization
- While the screen is off, the **tilt sensor** for Liquid Glass highlights is off and **the navigator screen isn't read** — polling resumes only when the screen is turned on again.
- **The navigator notification layout** (RemoteViews) is built at most once every 2 seconds and not built at all if distance and time are already in the notification fields; with the screen off the previous one is reused.
- **Liquid Glass** renders faster: gradients and strokes are built once per size instead of every frame.
- The button color from the avatar (palette extraction) is computed only in "From avatar" mode.
- Removed unused imports and leftovers of deleted features.

### 🧪 Try it without waiting
- In Test («Проверка») on the home screen: a message with buttons and a reply field, a card payment (hint and "Paid"), a test navigation — the banner and capsule disappear by themselves after 10 seconds.

### 🧹 Removed
- **Volume control in the capsule** — the volume bar no longer appears when you press the volume buttons.
- **Lyrics in the collapsed island** — the lyrics line in the capsule and its setting. Lyrics in the expanded player stay.
- **Island size**: width and height are no longer adjustable, only the **position** (Capsule settings → Position: horizontal and top offset). The size you already chose is kept. The Position screen now has just two sliders; while it's open, the island is highlighted in red so you can see where it is.

## [1.5.0] — 2026-10-23

### 💊 Capsule like the iPhone's
- **Icons at the edges, not next to the camera.** In the collapsed capsule the icon sits at the left edge, the text or time at the right, the camera in the middle, like on iPhone: "🔕 … Silent", "⏱ … 0:45", "⚡ … 25% 🔋".
- A capsule with an activity is wider than the idle one by its height on each side; if the text is longer, it widens exactly enough not to go over the camera.
- The side padding equals the top padding — the content fits neatly into the rounding.

### 📞 Incoming call
- The avatar, "Incoming call" with the name and both buttons are **on one line**, the text no longer slides down. The whole row is moved below the camera so the cutout doesn't cover it.

### 🧹 Cleaned up
- **Auto-fit to the camera** was removed: on some phones it placed the island in the wrong spot. By default the island is like on iPhone — 126 × 37 dp and 11 dp from the top. You can fit it to your camera manually in Size and position: horizontal, top offset, width, height, and the red capsule shows whether the camera is visible.
- Removed the "Side parts" and "Event width" settings — they barely changed anything visually. "Thickness with activity" stays.
- The "Camera cutout" slider was removed; if auto-fit in 1.4 set a strange value, it goes back to normal.

## [1.4.0] — 2026-10-22

### 💊 Capsule like the iPhone's — by Apple's rules
- **In the resting state** (charging, timer, music, call) the capsule became **thicker and narrower**: icons and text hug the camera instead of spreading to the edges, so the Android status bar is barely covered. That's what Apple's Dynamic Island guidelines ask for: "keep content snug around the camera, don't cover the status bar".
- **An event** (charging, headphones, Wi-Fi) first expands into a wide banner, then shrinks into a compact capsule. Icons and labels on the banner are moved toward the center, leaving room for the camera in the middle.
- **Notifications no longer hide under the camera**: like the expanded iPhone island, the text flows around the cutout — avatar and name top left, "now" on the right, the message itself below the camera, up to three lines.
- **Expanded cards** (player, timer, call) start below the cutout, the incoming call too.

### 🎨 Icons and smoothness
- **iOS-style icons** in the island from the free Ionicons set (MIT): bolt, bell, phone, flashlight, player, timer, headphones, Wi-Fi and others.
- The island's content **flows with a light blur**, like on iPhone (Android 12+).
- Buttons in the island gently **sink under your finger**.
- **The flashlight glows brighter** while you change the brightness and flashes at every step; the capsule shows a softly pulsing dot.

### ⚙️ More settings
- Capsule settings → **Island look**: thickness of the capsule with an activity, side parts (compact / normal / wide), event width (full / medium / narrow). Live preview on top.
- Size and position → new **Camera cutout** slider: the content snaps to it.

### 🛠 Fixed
- **Auto-fit to the camera** now works: the cutout is taken from the island window, the display and the app window — on phones where the app window didn't report it, the button used to do nothing.
- **Size and position** is back in the app's style: grouped cards, sliders with neat ± buttons, Auto and Save buttons; the red capsule for fitting stays.

## [1.3.0] — 2026-10-22

### 🔔 App notifications in the island
- **Messages from Telegram, WhatsApp, mail** and other apps appear in the island as a card, like a banner on iPhone: an avatar with the app badge, the name and two lines of text.
- Tapping opens the message, swiping up dismisses the card.
- Only notifications the system would show as heads-up anyway are displayed: no silent ones, none during Do Not Disturb and none from the app that's currently open.
- Turn it on in Dynamic cards → App notifications.

### ✨ iPhone-like animations
- **Ringer mode**: a short "Ring / Vibrate / Silent" pill, the island shakes sideways, the bell swings and settles.
- **Headphones**: "Connected" and the name, a green ring with the battery percentage on the right; the ring and battery fill in as they appear.
- **Flashlight**, like iOS 18: when it turns on, the island expands into a tall card with a glowing flashlight and a beam. Swipe up and down to change the brightness (if the phone supports it), tap to turn it off. After a couple of seconds without touches the card collapses.
- **Timer**: an orange pause and a gray cross on the left, "Timer" and a large time on the right; the ringing bell swings.
- **Screen and audio recording**: a red wave, the time and a stop button, like the voice recorder on iPhone.

### 📐 Fitting to the camera
- The Size and position screen was redone: an example with a red capsule, ± buttons at the ends of the sliders, Center, Save.
- While the screen is open, the island on the phone is **red with a white rim** — move it until the red hides under the camera.
- The **Fit to camera automatically** button places the island by the camera cutout reported by the system.
- Back without saving restores the previous size.

### 🔤 Font
- The whole island and app use **Inter**, close to SF Pro on iPhone (free OFL license).

## [1.2.0] — 2026-10-22

### 📐 iPhone sizes
- **A 126 × 37 capsule** with an 11 offset from the top, like the Dynamic Island on iPhone 14 Pro and newer. If you never changed the size manually, it updates by itself.
- **Collapsed cards** stick out about 52 on each side, like on iPhone: music, timer, call, charging.
- **The expanded island** is almost the full screen width, 11 on each side, with a 44 corner radius.

### 🔔 Full-width events
- Charging, headphones, Wi-Fi and other events expand the island almost to the full screen width and noticeably taller: a large icon, a caption and the state, a battery or a charge ring.
- An incoming call is a card 84 tall, like on iPhone.

### 🎵 Lyrics
- **Lyrics are back** in the expanded player and are found better: LRCLIB in three ways (by fields, by duration, free search), then **NetEase**, which has a lot of Russian music.
- If synced lyrics aren't available anywhere, plain lyrics lines are spread across the song length.
- The player shows what's happening: "Looking for lyrics…" or "Lyrics not found".
- Lyrics are looked up once per track and aren't lost when the card hides on pause.

### 📞 Calls work with any dialer
- An incoming call is detected from the phone state, not only from a notification: on Xiaomi, Samsung and other phones the dialer shows the call in its own window, and the island used to miss it.
- The name and photo come from contacts, the answer and decline buttons work through the system telecom service.
- A new Calls item in permissions on the home screen and a test call in the Test section.

### 🧹 Tidier settings
- The lyrics description and the instructions match what the app actually does.
- "Color from artwork" turns on the outline right away — without it the setting had no effect.

## [1.1.0] — 2026-10-22

### 🎨 iPhone-like design
- **Events on one line**: an icon and a caption on the left, the state on the right, room for the camera in the middle. Charging: "Charging", green percentage and a battery on the right.
- **Headphones** show the battery as a ring; on unlock the capsule shows just a lock.
- **Player**: time on both sides of the seek bar, ⏪ ⏸ ⏩ buttons, an audio output button on the right.
- **A sound wave** of thin bars in the artwork color instead of the old equalizer.
- **Charging indicator** with a battery next to the percentage.

### 📞 New
- **Calls**: an incoming call is shown large right away — avatar, name, red and green buttons. During a call there's a green phone with the time on the left, a wave on the right, and an End button on tap.
- **Screen recording** is shown in red, like on iPhone.
- A Calls toggle in Dynamic cards.

### 🛠 Fixed
- **The capsule no longer jitters** when expanding, collapsing and showing events. The island is drawn in a window of constant width, and touches are caught by a separate invisible window above it.
- **Notifications are no longer cut off** at the bottom.
- **Touches near the capsule** are no longer lost during an animation.
- If an expanded card disappears (music closed, timer cancelled), the capsule collapses instead of showing the clock.
- The capsule's horizontal offset no longer drifts during an animation.

## [1.0.0] — 2026-10-22

The first version.

- A capsule above the camera: music with artwork, an equalizer and synced lyrics (LRCLIB), a 1–99 minute timer with an alarm, a stopwatch, Google Clock timers, download progress, a flashlight with brightness, volume, charging.
- A bubble for a second activity next to the island.
- Events: charging, Wi-Fi, headphones, airplane mode, ringer mode, adaptive brightness, USB, unlock.
- Gestures: tap, double tap and long press, swipe down and sideways.
- Capsule settings: size, position, color, outline, shadow, animation style, duration and elasticity, haptics.
