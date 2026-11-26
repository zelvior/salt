# Salt — Custom Control Center & Quick Settings

`com.zelvior.salt` — an ultra-lightweight, modular, AI-supported Custom Control Center &
Quick Settings shade for Android.

- **100% Kotlin · Jetpack Compose · Coroutines/Flow · Ktor (streaming LLM)**
- **Min SDK 29 (Android 10) · Target SDK 35**
- Designed for a tiny footprint, low idle RAM, and fluid 60/120 fps animations.

---

## Phase 1 — Foundation + Unsigned CI/CD (current)

This checkpoint delivers the project skeleton, build system, manifest, and a production-ready
`OverlayService` that hosts a Jetpack Compose control-center UI above other apps via
`WindowManager`, with correct `LifecycleOwner` / `ViewModelStoreOwner` / `SavedStateRegistryOwner`
wiring for the overlay.

### Module layout (feature-based)

```
app/src/main/java/com/zelvior/salt/
├── SaltApplication.kt            # App + manual DI container
├── MainActivity.kt               # Launcher + permission checklist + start/stop
├── core/
│   ├── di/                       # AppContainer (minimal manual DI)
│   ├── permissions/              # Permission checks + settings intents
│   ├── overlay/                  # ★ WindowManager overlay host & gesture detector
│   │   ├── OverlayService.kt         # Foreground service + Compose overlay
│   │   ├── OverlayLifecycleOwner.kt  # Lifecycle/VM/SavedState wiring
│   │   ├── OverlayWindowHost.kt      # WindowManager window + ComposeView binding
│   │   ├── EdgeGestureDetector.kt    # Top-edge swipe detection
│   │   └── OverlayInteractor.kt      # Open/close state contract
│   ├── notifications/            # (Phase 2) listener stub + privacy sanitizer (real)
│   ├── system/                  # (Phase 2) SystemController interface stub
│   ├── ai/                      # (Phase 2) AiClient / tool-call schema stubs
│   └── plugins/                 # (Phase 2) Plugin JSON schema stubs
└── ui/
    ├── shade/                   # Control center Compose UI (glassmorphic)
    ├── onboarding/              # Permission setup flow
    ├── components/              # Shared composables (PermissionRow)
    └── theme/                   # Theme tokens + glassmorphism modifier
```

Phase 2 modules (`system`, `ai`, `plugins`, notification listener) are present as
interfaces/schema stubs with clear `Phase 2` markers — awaiting your go-ahead before
implementation.

---

## Building

### CI (recommended — no secrets required)

Push a version tag to publish a directly-installable APK:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The [release workflow](.github/workflows/release.yml) will:

1. Set up JDK 17 and Gradle 8.9 (caches dependencies).
2. Generate the Gradle wrapper (the wrapper jar is intentionally **not** committed).
3. Run `./gradlew assembleDebug` (debug-signed → installable everywhere).
4. Rename the APK to `Salt-<version>-unsigned.apk` and attach it to a GitHub Release
   with auto-generated release notes.

You can also trigger it manually from **Actions → Build & Release** and pass a `version`
and `build_type` (`debug` or `release`). The `release` variant falls back to the debug
keystore so the artifact stays installable without storing secrets; add your own
`signingConfigs` in `app/build.gradle.kts` before publishing to the Play Store.

### Local development

The repo does **not** commit `gradle-wrapper.jar` (binary, CI-generated). To build locally:

```bash
# Option A — if you have Gradle installed:
gradle wrapper --gradle-version 8.9   # creates ./gradlew + wrapper jar
./gradlew assembleDebug

# Option B — open in Android Studio (Arctic Fox+); it resolves the project directly.
```

Then install on a device/emulator (API 29+):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Permissions:** On first launch, Salt routes you to the onboarding checklist to grant
> *Display over other apps*, *Notification access*, *Modify system settings*,
> *Do Not Disturb access* (for the DND toggle), and *Bluetooth*. After that, pull the
> invisible top-edge strip downward to open the shade.

---

## Phase 2 — System Toggles & Shizuku

The shade is now live: brightness and media-volume sliders plus a 2×3 toggle grid
(Wi-Fi, Bluetooth, mobile data, airplane mode, DND, flashlight), all reflecting and
driving real device state.

- **`core/system/SystemController`** — the single surface the UI talks to
  (`states`, `brightness`, `volume` flows; `toggle/setBrightness/setVolume/connectShizuku`).
- **`core/system/SystemControllerImpl`** — standard SDK where possible:
  `Settings.System` for brightness, `AudioManager` for media volume,
  `NotificationManager.setInterruptionFilter` for DND, `CameraManager.setTorchMode`
  for the flashlight, `BluetoothAdapter` for BT (with Shizuku `svc bluetooth` preferred
  on Android 13+, which blocks `enable()` for normal apps).
- **`core/system/ShizukuBridge`** — root-free shell execution for the restricted toggles:
  `svc wifi/data enable|disable`, `settings put global airplane_mode_on` + broadcast.
  Bluetooth also routes through Shizuku when available. Permission is requested once and
  awaited via `OnRequestPermissionResultListener`.
- **Gating:** if Shizuku isn't installed/authorized, the Wi-Fi / data / airplane chips
  dim and the panel shows a one-tap *Connect* banner that launches Shizuku's permission
  flow (or its Play Store page if missing).

State observation is passive (ContentObservers + broadcast receivers + torch callback),
so idle cost stays near zero — consistent with Salt's low-idle-RAM goal.

---

## Notes

- **Glassmorphism:** uses the platform `RenderEffect` blur (`Modifier.blur`) on Android 12+
  with a clean translucent-gradient fallback on older versions.
- **Privacy:** the `NotificationSanitizer` (regex/blacklist) is implemented now and is meant
  to run *before* any text reaches the AI layer.
- **Shizuku:** declared as an optional dependency + manifest provider; wired in Phase 2.

---

## Branding & Launcher Icon Pipeline

The logo lives once, at **`art/salt-logo.png`** (1024-class full-bleed square: a glowing
glass salt-crystal cube on charcoal — emblem centered inside the adaptive-icon safe zone).
Everything else is *derived* from that master by a deterministic script:

```bash
python3 art/generate_launcher_icons.py   # requires Pillow
```

This regenerates:

- **Adaptive icon layers** (`mipmap-*/ic_launcher_foreground.webp` at the 66% safe zone,
  `ic_launcher_monochrome.webp` for Android 13+ themed icons) over the flat brand
  charcoal `#0B0E14` (`drawable/ic_launcher_background.xml`).
- **Legacy statics** (`ic_launcher.webp` rounded-square, `ic_launcher_round.webp` circle)
  composed as background + emblem so old launchers match the adaptive look.
- **In-app brand mark** (`drawable-nodpi/salt_logo.webp`) used in the shade header,
  the launcher activity header, and onboarding.
- **Store art** (`art/salt-emblem.png` transparent emblem, `art/playstore-icon.png`).
- **`art/preview-sheet.png`** — squircle / circle / rounded / legacy / themed previews.

**Rules:** the master is the only hand-made image; never edit derived files by hand
(re-run the script). The emblem is extracted cube-only (the floor reflection is cut at
its alpha-width minimum and the bottom edge feathered), so future master updates keep
the exact same launcher shape — you can turn this into a full launcher icon set without
touching the artwork.
