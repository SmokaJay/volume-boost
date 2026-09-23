# Volume Boost

Free, ad-free Android volume booster (LoudnessEnhancer + max streams). No ads, no tracking.

**Package:** `com.maximus.volumeboost`  
**Version:** 1.0.1  
**minSdk:** 26 · **targetSdk:** 34

## Install (APK)

Download the latest APK from **[GitHub Releases](https://github.com/SmokaJay/volume-boost/releases)** (e.g. `VolumeBoost-1.0.1.apk`).

### On the phone
1. Download the APK from the release page onto your phone.
2. Enable **Install unknown apps** for the app you use to open the APK (Files, Chrome, Drive, …).
3. Open the APK → Install.
4. On Android 13+, allow **Notifications** when asked (needed for the “Volume Boost active” status).

### Via adb
```bash
adb install -r VolumeBoost-1.0.1.apk
```

## What it does / doesn’t do

**Does:**
- Applies digital gain with Android’s `LoudnessEnhancer` (0–100% → 0–~2500 mB)
- Maxes system volume streams (Music, Ring, Alarm, Notification, System)
- Keeps boost attached via a lightweight foreground service while enabled
- Saves last boost level and on/off state (DataStore)
- If boost was left on, it restarts after reboot (notification appears)
- No ads, analytics, IAP, or internet permission

**Doesn’t:**
- Cannot exceed the speaker’s hardware maximum without root — same limit as commercial “boosters”
- High gain can clip/distort; start low and raise carefully

## Rebuild

Requirements: JDK 17+ (JDK 21 works), Android SDK with `platforms;android-34` and `build-tools;34.0.0`.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # or your JDK
export ANDROID_HOME=/path/to/android-sdk
# local.properties should contain: sdk.dir=...

./gradlew assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`

### Signing (for release / Play Store / updates)

Do **not** commit a keystore or passwords.

- Generate your own keystore, or use **Android Studio → Build → Generate Signed Bundle / APK**.
- For local release builds, copy `keystore.properties.example` to `keystore.properties` (gitignored) and point it at your keystore. Example:

```properties
storeFile=keystore/your-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

- If `keystore.properties` is absent, `assembleRelease` falls back to the debug keystore so the project still builds for open-source contributors.
- Keep the same keystore to publish updates to an existing install.

## Permissions

- `MODIFY_AUDIO_SETTINGS` — max streams / audio effect
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — keep boost attached
- `POST_NOTIFICATIONS` (API 33+) — service notification
- `RECEIVE_BOOT_COMPLETED` — restore boost after reboot if it was left on

## Open in Android Studio

File → Open → select this folder. Sync Gradle, then Run or Build → Generate Signed Bundle / APK.

## License

MIT — see [LICENSE](LICENSE).
