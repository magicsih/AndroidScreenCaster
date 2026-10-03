# Maintainer notes

## Existing community threads

These are response drafts for human review. No reply, closure, or merge of these existing threads was performed as part of this update.

### Issue #11 — USB connection

Draft: “USB is not required for normal streaming. Install the Android APK, put your phone and FFplay receiver on a reachable network, enter the receiver computer's IP address, and start FFplay before approving capture. See the README's First stream section. USB/ADB is optional for installation and development.”

### Issue #12 — Client/server build

Draft: “The Android APK is the sender, and FFplay or FFmpeg is the receiver, so no custom server implementation needs to be compiled. The README now includes the pinned build tools, APK installation steps, and commands for all four supported transport/codec combinations. The release page provides an example debug APK and a checksum; exact device validation is listed separately.”

### PR #13 — Build modernization

Alex Lugo/alugocp proposed namespace and build cleanup, an explicitly exported Activity, executable wrapper, and removal of obsolete IDE/sample-test files. These useful directions are independently incorporated and credited in the README. The older AGP/Gradle/SDK versions, extra AndroidX runtime dependencies, committed device/IDE state, and `minSdk 28` are not adopted. The updated example retains API 21 support.

Draft: “Thanks for the build cleanup and for flagging the minimum-SDK change. The current modernization applies the namespace/export/wrapper/obsolete-file cleanup and credits your contribution. It uses the current toolchain while keeping minSdk 21, and removes the need for ConstraintLayout/AndroidX runtime dependencies. The original PR remains open so its author and maintainer can review the replacement and decide how to close it.”

## Search/traffic baseline

Observed through the GitHub repository/traffic API on 2026-10-03 before the update:

| Measure | Baseline |
| --- | --- |
| Stars / forks | 211 / 74 |
| Views in the last 14 days | 47 views / 29 unique visitors |
| Clones in the last 14 days | 11 clones / 8 unique cloners |
| Existing v0.1 debug APK downloads | 494 |
| Referrers | GitHub 15/10; Google 5/5; DuckDuckGo 2/2; doubao.com 1/1; Brave Search 1/1 — visits/unique visitors |

These rolling counts are a comparison baseline, not evidence that documentation changes increase search rank or adoption. Record a later snapshot over a comparable window before drawing a conclusion.
