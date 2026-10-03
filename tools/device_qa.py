#!/usr/bin/env python3
"""Receive/decode a changing screen from the debug APK without clearing app data.

Install debug + androidTest APKs first. Approve the visible Android permission
and screen-sharing dialogs on the device. The test APK shows a moving screen,
then stops capture and checks that capture/sender threads have ended.
Requires adb and ffmpeg. Does not uninstall apps or reset preferences.
"""
import argparse
import json
import subprocess
import time
from pathlib import Path

PACKAGE = 'com.github.magicsih.androidscreencaster'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--host', required=True, help='Receiver address reachable from this device')
    parser.add_argument('--protocol', choices=['tcp', 'udp'], required=True)
    parser.add_argument('--codec', choices=['h264', 'vp8'], required=True)
    parser.add_argument('--resolution', type=int, choices=[0, 1, 2], default=2,
                        help='0:1280x720, 1:800x480, 2:640x360')
    parser.add_argument('--seconds', type=int, default=125)
    parser.add_argument('--receiver-port', type=int, default=49152,
                        help='Local listening port; app always sends to 49152. Use 49153 with adb reverse.')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.seconds < 1:
        parser.error('--seconds must be positive')
    args.output.mkdir(parents=True, exist_ok=True)
    adb = ['adb', '-s', args.serial]
    stream_format = 'h264' if args.codec == 'h264' else 'ivf'
    url = f'tcp://0.0.0.0:{args.receiver_port}' if args.protocol == 'tcp' else f'udp://@:{args.receiver_port}'
    url += '?listen=1' if args.protocol == 'tcp' else '?fifo_size=65536'
    hashes = args.output / 'frames.md5'
    receiver = None
    test = None
    with (args.output / 'ffmpeg.log').open('w') as log, (args.output / 'instrumentation.log').open('w') as test_log:
        try:
            receiver = subprocess.Popen(['ffmpeg', '-hide_banner', '-nostdin', '-y', '-f', stream_format,
                                         '-i', url, '-an', '-fps_mode', 'passthrough', '-f', 'framemd5', str(hashes)], stdout=log, stderr=log)
            time.sleep(0.5)
            if receiver.poll() is not None:
                raise RuntimeError('Receiver could not start. Check port availability and ffmpeg.log.')
            command = adb + ['shell', 'am', 'instrument', '-w', '-e', 'mode', 'capture',
                             '-e', 'host', args.host, '-e', 'protocol', args.protocol,
                             '-e', 'codec', args.codec, '-e', 'resolution', str(args.resolution), '-e', 'seconds', str(args.seconds),
                             PACKAGE + '.test/' + PACKAGE + '.UiSmokeInstrumentation']
            test = subprocess.Popen(command, stdout=test_log, stderr=test_log)
            print('Approve the Android screen-sharing dialog on the device.', flush=True)
            deadline = time.monotonic() + args.seconds + 210
            while test.poll() is None:
                if time.monotonic() > deadline:
                    raise RuntimeError('Device test timed out')
                if receiver.poll() is not None and receiver.returncode != 0:
                    raise RuntimeError('FFmpeg failed during capture. Inspect ffmpeg.log.')
                time.sleep(1)
            test_log.flush()
            report = (args.output / 'instrumentation.log').read_text()
            if 'Capture completed:' not in report or 'Test failed:' in report:
                raise RuntimeError('Capture failed. Inspect instrumentation.log.')
            if args.protocol == 'tcp':
                receiver.wait(timeout=10)
        finally:
            for process in (test, receiver):
                if process is not None and process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
    if not hashes.exists():
        raise RuntimeError('No decoded video was received. Inspect ffmpeg.log and network permissions.')
    frames = [line for line in hashes.read_text().splitlines() if line and not line.startswith('#')]
    unique = len({line.split(',')[-1].strip() for line in frames})
    tail_unique = len({line.split(',')[-1].strip() for line in frames[-30:]})
    summary = {'api': subprocess.check_output(adb + ['shell', 'getprop', 'ro.build.version.sdk'], text=True).strip(),
               'protocol': args.protocol, 'codec': args.codec, 'wall_seconds': args.seconds,
               'decoded_frames': len(frames), 'unique_frames': unique, 'tail_unique_frames': tail_unique,
               'receiver_exit_code': receiver.returncode}
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary), flush=True)
    if len(frames) < args.seconds or tail_unique < 5:
        raise RuntimeError('Too few decoded/changing frames. Inspect FFmpeg output.')


if __name__ == '__main__':
    main()
