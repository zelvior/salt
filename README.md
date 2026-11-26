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
> *Display over other apps*, *Notification access*, and *Modify system settings*. After that,
> pull the invisible top-edge strip downward to open the shade.

---

## Notes

- **Glassmorphism:** uses the platform `RenderEffect` blur (`Modifier.blur`) on Android 12+
  with a clean translucent-gradient fallback on older versions.
- **Privacy:** the `NotificationSanitizer` (regex/blacklist) is implemented now and is meant
  to run *before* any text reaches the AI layer.
- **Shizuku:** declared as an optional dependency + manifest provider; wired in Phase 2.
