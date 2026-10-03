# Contributing

Thanks for helping keep this Java Android capture example useful. Ask usage questions in [Discussions](https://github.com/magicsih/AndroidScreenCaster/discussions); use Issues for reproducible bugs and proposed features. Read the [Code of Conduct](CODE_OF_CONDUCT.md) and use [private vulnerability reporting](SECURITY.md) for security findings.

## Development

Use JDK 17 and the pinned Gradle wrapper. Install Android SDK Platform 37.0 and Build Tools 36.0.0. See the [README](README.md) for build and receiver commands.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
```

Keep `minSdk 21`, the application ID, saved preference keys, port 49152, and the raw H.264 / VP8 IVF wire formats unless a change is explicitly discussed. Keep the example in Java with Groovy build files. Avoid new runtime dependencies unless they make the example substantially easier to understand or maintain. Preserve AOSP/Gradle license notices.

## Verify changes

Unit tests cover IVF headers/timestamps, packet order, initial delivery, connection/write failure, queue limits, and repeated shutdown. For a device UI smoke test:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w \
  com.github.magicsih.androidscreencaster.test/com.github.magicsih.androidscreencaster.UiSmokeInstrumentation
```

For an actual decoded-screen check, use a trusted receiver/network. The test APK displays changing shapes for the requested interval and checks that capture threads finish. Approve Android's visible screen-sharing dialog; select the entire screen for this test.

```sh
python3 tools/device_qa.py --serial YOUR_ADB_SERIAL --host RECEIVER_LAN_IP \
  --protocol tcp --codec h264 --seconds 125 --output /tmp/screen-caster-qa
```

Repeat with `tcp/vp8`, `udp/h264`, and `udp/vp8`. The script requires `adb` and `ffmpeg`; it never clears app data or uninstalls an existing app. It changes capture preferences through the app and installs no packages itself. A debug-signature mismatch must be handled without deleting data on a user's device.

For capture/lifecycle changes, also test consent denial, repeated Start, Stop/restart with fresh consent, switching apps, rotation, screen lock, system/notification Stop, invalid/unreachable hosts, and a receiver that closes or stops reading. Inspect the app on a small screen with the keyboard open. Describe missing environments honestly; a compiled device-test APK is not an executed device test.

## Pull requests

Use a focused branch and a Ready pull request. Explain the observed problem, resulting behavior, and the commands/devices that verified it. Include screenshots for UI changes and decoded-video evidence for encoder/transport changes. Do not add credentials, device identifiers, private screens, generated build directories, or machine-specific IDE files. Open a separate discussion before changing the supported formats or significantly expanding the scope.

The project uses PR checks on `master`; passing CI alone does not justify a release. Release APKs are example debug builds, accompanied by hashes and a validation report.
