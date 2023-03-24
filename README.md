# Android Screen Caster - Low Latency Screen Mirroring

AndroidScreenCaster is a live screen casting solution for Android devices, with efficient H.264 and WebM encoding via TCP and UDP. Experience low latency streaming to your browser or server for mobile games, presentations, or any other application where real-time screen mirroring is needed.

## Motivation

As a test automation team leader, we needed to mirror live Android screens to web browsers for functional testing of mobile games. Our first approach, using MJPEG, was inefficient, slow, and produced large files. The second approach, using H.264 and VP8 codecs, was successful but lacked readily available code examples.

This [project](https://www.linkedin.com/pulse/introduction-regression-test-automation-system-mobile-ilhwan-seo/) aims to save you time and provide a clear understanding of live screen casting on Android, with efficient media encoding and low latency.

## DEMO
[![Demo](https://img.youtube.com/vi/2AN6EfArfZE/0.jpg)](https://www.youtube.com/watch?v=2AN6EfArfZE)

## Tested Device

- Samsung Galaxy S7 edge (Android 6.0)

## Requirements

- FFmpeg installed on the server

## Screenshots

![Screenshot](screenshot.jpg "Screenshot")

## Quick Start

### Server-side
- Run the following command to start FFplay:

  ```bash
  ffplay -framerate 60 -i tcp://<your server ip here>:49152?listen
  ```

### Client-side (Android App)
1. Enter your remote host address (e.g., IP) in the app.
2. Choose H.264 as the format.
3. Tap "Start" and perform other tasks, allowing FFmpeg to receive enough media data.

## FFmpeg Commands for Server-side
### PLAY
#### TCP+H264
```ffplay -framerate 60 -i tcp://<your server ip here>:49152?listen```
#### TCP+VP8
```ffplay -i tcp://<your server ip here>:49152?listen```
#### UDP+H264
```ffplay -framerate 60 -i udp://@:49152```
#### UDP+VP8
```ffplay -i udp://@:49152```

### RECORD
#### UDP+H264
```ffmpeg -i udp://@:49152 -framerate 60 -codec:v libx264 -profile:v baseline -preset medium -b:v 250k -maxrate 250k -bufsize 500k -vf scale=-1:360 -an -threads 0 output.mp4```

##### To increase playback speed, re-encode output.mp4:
```ffmpeg -i output.mp4 -vf "setpts=(1/2)*PTS" fast_output.mp4```


#### UDP+VP8
```ffmpeg -i udp://@:49152 -c:v libvpx -b:v 1M -c:a libvorbis output.webm```

# Reference
- https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts
- https://trac.ffmpeg.org/wiki/StreamingGuide
- https://trac.ffmpeg.org/wiki/Encode/VP8
- https://trac.ffmpeg.org/wiki/Encode/H.264

# License (MIT)
This project is licensed under the terms of the MIT license.
