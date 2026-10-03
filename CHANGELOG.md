# Changelog

## 0.2.0

- Keep Android 5.0/API 21 support and modernize to AGP 9.4.1, Gradle 9.8.0, JDK 17, and SDK 37.
- Request fresh screen-sharing consent for each session, run capture in a private foreground service, and clean up after app/system Stop.
- Connect before encoding and deliver ordered video through a bounded queue with connection/write errors and timeouts.
- Select Surface-capable H.264/VP8 encoders, send initial codec/container headers, and use microsecond IVF timestamps.
- Replace the fixed form with a scrollable native Android layout, validate addresses, show connection status, and handle system/keyboard insets.
- Add Android 17 local-network permission handling, reproducible tests, and GitHub-hosted CI.
- Correct receiver instructions, document trusted-network/UDP limits, and add contribution, security, and community guidance.

The APK is an example debug build. See [docs/VALIDATION.md](docs/VALIDATION.md) for tested environments and remaining limits. Compatibility with the signing key of v0.1 is not guaranteed.

## v0.1

Historical initial example release. Its APK and release page are preserved.
