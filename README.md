# AndroidScreenCaster
A live android screen caster which encoding media by h264,webm via TCP and UDP with low latency

# Motivation
I'm currently in charge of test automation team. We try to make possible functional testing for mobile games. While we're working on it, we needed to mirror live android screen to web browser. The first approach was MJPEG. We captured entire screen and sent it over network in every very short period. Surely, it was ineffiecient, slow and huge. The first approach was helpful anyway to prove our concept of system, though.

The second approach was encoding our media data by using well known codecs such as h264 and vp8. It ended up a success anyway. However, it was hard to find code examples. I mostly refer to android googlesource(specially media test cases). I hope this project helps you to save your time and understand concept of live screen casting on Android.

# Test Environment
- Samsung Galaxy S7 edge (Android 6.0)

# Requirement
- ffmpeg on server (demonstrating works well)

# Reference
- https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts
