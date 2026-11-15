# Dynamic Island 1.7.0 — подготовленное описание / prepared release notes

Выпуск разрешён владельцем с указанными ограничениями обновления. / Publication authorized by the owner with the upgrade limitations below.

## 🇬🇧 English

### ✨ New features
- 🫧 **Liquid Glass**: refined highlights and transparency, glass density and system blur strength controls. Available for the island, expanded cards and secondary activity on supported devices.
- 📌 **Pinned apps and contacts**: a compact shortcut row below the date.
- 🕒 **Brief**: separate compact clock, steps and weather pages, clock styles and extra controls.
- 🌤 **Weather**: city selection, a standalone card or Brief page, manual refresh and retries after errors.
- 🎵 **Lyrics**: a continuous soft highlighting wave and smooth line transitions; unsynced lyrics remain scrollable.
- 🎨 **Custom colors and fonts**: text color, HEX/HSV picker, system or custom fonts, weight and element sizing.
- 📶 **Hotspot**: persistent state, a settings shortcut and client counts when reliable system information is available.
- ⬇️ **Transfer speed in expanded download cards**: app-provided details or optional approximate total network rates ↓/↑, preserving actual download percentages.
- 🔓 **Face ID · face only**: optional verified face-unlock animation using separately configured Shizuku. PIN and fingerprint unlock are not presented as Face ID.
- ⏰ **Create alarms**, auto-hide the idle island, scroll to top in supported apps and keep cards open on outside touch.
- 🔋 **Battery**: an icon filled according to the current charge and optional approximate charging-time estimates.
- 🔔 **Notifications**: history, display styles, duration, silent messages, per-app rules and missing-notification diagnostics.
- 🌍 **Languages**: Indonesian, Portuguese, Kazakh, French and Spanish; improved language switching.
- 💜 **Support the developer**: Telegram and Boosty in About, with English and Russian text.

### 🛠 Fixes and optimization
- Distinct voice, screen and camera recording states; improved Google Recorder detection, pause/resume and real source controls.
- Improved lyrics search, cache, Enhanced LRC handling, track changes and media sessions. MediaStyle alone no longer classifies music as a download.
- Improved consecutive messages, replies, mark-as-read actions and action wrapping.
- Pending alarms use the secondary activity; improved false-alarm filtering and timer layout on narrow cards.
- Preserved detected payment amounts and improved recognition of brief success screens; individual bank apps still require device testing.
- Refined animations, notch-mode blur geometry, shortcut placement and landscape color-picker accessibility.
- Reduced polling, redundant subscriptions and hidden-effect work; removed confirmed unused code.
- Hardened download URLs, response limits, APK/certificate checks and partial-download cleanup.
- **296 unit tests passed** on functional commit `1863d0b`; this does not replace testing every phone.

### Compatibility
System blur depends on Android and firmware. Face ID requires Shizuku and reliable face events. Hotspot, recording and Bluetooth data depend on the source. TrafficStats reports approximate total network speed, not an exact per-file rate.

### Updating
The package remains `com.akira.island`. APKs for 1.6.1, 1.6.2, 1.6.5 and the verified test 1.7.0 use compatible signing certificates; versionCode is now 14. Android's standard installer asks for confirmation. Previous upgrade tests preserved data, but Android 15 restricted Accessibility again: you may need to manually allow restricted settings and re-enable the service. The complete final-APK upgrade flow on a physical device has not yet been verified.

Full list in Russian: [CHANGELOG-1.7.0-FULL.md](https://github.com/Akira9021/Dynamic-Island-/blob/test-1.7.0/docs/CHANGELOG-1.7.0-FULL.md).

---

## 🇷🇺 Русский

### ✨ Новые возможности
- 🫧 **Liquid Glass**: обновлённые блики и прозрачность, настройка плотности и силы системного размытия. Эффект распространяется на остров, раскрытые карточки и вторую активность на поддерживаемых устройствах.
- 📌 **Приложения и контакты**: компактный ряд быстрого доступа под датой.
- 🕒 **Сводка**: часы, шаги и погода на отдельных компактных страницах; варианты часов и дополнительные элементы управления.
- 🌤 **Погода**: выбор города, отдельная карточка или страница сводки, ручное обновление и повтор после ошибки.
- 🎵 **Текст песен**: мягкая непрерывная волна подсветки и плавная смена строк; обычный текст остаётся прокручиваемым.
- 🎨 **Свои цвета и шрифты**: цвет текста, HEX/HSV-палитра, системный или собственный шрифт, толщина и размеры элементов.
- 📶 **Точка доступа**: постоянное состояние, переход к настройкам и количество клиентов, когда система предоставляет достоверные сведения.
- ⬇️ **Скорость загрузки** в раскрытой карточке: данные приложения или необязательная приблизительная общая скорость сети ↓/↑; проценты загрузки сохраняются.
- 🔓 **Face ID · только лицо**: необязательная анимация подтверждённого распознавания лица через отдельно настроенный Shizuku. PIN и отпечаток не выдаются за Face ID.
- ⏰ **Создание будильника**, автоскрытие пустого острова, прокрутка наверх в поддерживающих приложениях и сохранение раскрытой карточки при касании снаружи.
- 🔋 **Батарея**: значок с заполнением по текущему заряду и необязательная приблизительная оценка времени зарядки.
- 🔔 **Уведомления**: история, варианты показа, длительность, тихие сообщения, правила для приложений и диагностика пропусков.
- 🌍 **Языки**: индонезийский, португальский, казахский, французский и испанский; улучшено переключение языка.
- 💜 **Поддержка автора**: Telegram и Boosty в разделе «О приложении», с русскими и английскими подписями.

### 🛠 Исправления и оптимизация
- Разделены диктофон, запись экрана и видео; улучшены Google Recorder, пауза, продолжение и настоящие действия управления.
- Улучшены поиск и кеш текстов песен, обработка Enhanced LRC, переключение треков и медиасессии. Музыка больше не классифицируется как загрузка по одному MediaStyle-уведомлению.
- Доработаны последовательные сообщения, ответ, отметка прочитанного и перенос действий уведомлений.
- Ожидающий будильник уходит во вторую активность; исправлены дополнительные ложные срабатывания часов и размещение таймера на узких карточках.
- Исправлены потеря найденной суммы оплаты и распознавание быстрых экранов успеха; конкретные банки требуют проверки на устройстве.
- Доработаны анимации, положение и обрезка размытия в режиме «Как вырез», расположение ярлыков и доступность палитры в горизонтальном окне.
- Сокращены фоновые опросы, лишние подписки и работа скрытых эффектов; удалён подтверждённо неиспользуемый код.
- Усилены проверки адресов загрузки, размера ответа, APK и сертификата; улучшена очистка незавершённых загрузок.
- На функциональной базе `1863d0b` прошли **296 unit-тестов**. Это не заменяет проверку каждого телефона.

### Совместимость
Системное размытие зависит от Android и прошивки. Face ID требует Shizuku и доступных достоверных событий лица. Сведения хотспота, записи и Bluetooth зависят от источника. TrafficStats показывает общую приблизительную скорость сети, а не точную скорость файла.

### Обновление
Пакет `com.akira.island` сохранён. У APK 1.6.1, 1.6.2, 1.6.5 и проверенной тестовой 1.7.0 совместимая подпись; versionCode увеличен до 14. Обновление подтверждается стандартным установщиком Android. В предыдущем тесте обновления данные сохранились, но Android 15 повторно ограничил доступ к специальным возможностям: может потребоваться вручную разрешить ограниченные настройки и включить службу. Полный сценарий с финальным APK на физическом устройстве пока не проверен.

Полный список: [CHANGELOG-1.7.0-FULL.md](https://github.com/Akira9021/Dynamic-Island-/blob/test-1.7.0/docs/CHANGELOG-1.7.0-FULL.md).
