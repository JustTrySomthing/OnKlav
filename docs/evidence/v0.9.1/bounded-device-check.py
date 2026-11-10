import os, pathlib, subprocess, time, json

root = pathlib.Path.cwd()
sdk = root / 'android-sdk'
project = root / 'project/OnKlav'
evidence = project / 'docs/evidence/v0.9.1'
evidence.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['ANDROID_HOME'] = str(sdk)
env['ANDROID_AVD_HOME'] = '/root/.android/avd'
env['LD_LIBRARY_PATH'] = str(root / 'tools/emulator-deps/usr/lib/x86_64-linux-gnu') + ':' + env.get('LD_LIBRARY_PATH', '')
for folder in ('emulator', 'platform-tools'):
    for file in (sdk / folder).rglob('*'):
        if file.is_file():
            with file.open('rb') as stream: header = stream.read(4)
            if header.startswith(b'\x7fELF') or header.startswith(b'#!'): file.chmod(0o755)
adb = str(sdk / 'platform-tools/adb')

def command(args, seconds=20, check=True):
    result = subprocess.run(args, env=env, capture_output=True, timeout=seconds)
    if check and result.returncode:
        raise RuntimeError(result.stdout.decode(errors='replace') + result.stderr.decode(errors='replace'))
    return result.stdout

def device(*args, seconds=20, check=True):
    return command([adb, '-s', 'emulator-5554', *args], seconds, check)

started = time.monotonic()
emulator_log = (evidence / 'emulator.log').open('wb')
emulator = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', 'OnKlavCheck',
    '-port', '5554', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
    '-accel', 'off', '-gpu', 'swiftshader_indirect', '-memory', '2048', '-cores', '2',
    '-camera-back', 'none', '-camera-front', 'none'], env=env, stdout=emulator_log, stderr=subprocess.STDOUT)
try:
    print('Booting software device; one attempt, 300-second limit', flush=True)
    deadline = time.monotonic() + 300
    last_update = 0
    while time.monotonic() < deadline:
        if emulator.poll() is not None: raise RuntimeError('Emulator exited; inspect emulator.log')
        try:
            if device('shell', 'getprop', 'sys.boot_completed', seconds=8, check=False).strip() == b'1': break
        except subprocess.TimeoutExpired: pass
        if time.monotonic() - last_update > 30:
            print('Waiting for boot', round(time.monotonic() - started), 'seconds', flush=True)
            last_update = time.monotonic()
        time.sleep(4)
    else: raise TimeoutError('Boot limit reached; no automatic restart')
    print('Device booted; installing verified host', flush=True)
    try: device('shell', 'input', 'keyevent', '82', seconds=8, check=False)
    except subprocess.TimeoutExpired: print('Optional unlock timed out; continuing installation', flush=True)
    host = project / 'android/app/build/outputs/apk/debug/app-debug.apk'
    harness = project / 'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    install = device('install', '-r', '-g', str(host), seconds=180)
    (evidence / 'install.log').write_bytes(install)
    assert b'Success' in install
    print('Host installed; installing internal test harness', flush=True)
    test_install = device('install', '-r', str(harness), seconds=150)
    assert b'Success' in test_install
    device('logcat', '-b', 'crash', '-c')
    print('Running reminder chooser, Rust persistence, reschedule/cancel and stale-alarm test', flush=True)
    output = device('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'app.onklav.home.ReminderDeviceTest',
        'app.onklav.home.prototype.rewrite.test/androidx.test.runner.AndroidJUnitRunner', seconds=240)
    (evidence / 'ReminderDeviceTest.log').write_bytes(output)
    print(output.decode(errors='replace'), flush=True)
    assert b'OK (1 test)' in output and b'FAILURES' not in output, 'Reminder test did not pass'
    crash = device('logcat', '-b', 'crash', '-d')
    (evidence / 'crash-buffer.log').write_bytes(crash)
    assert b'FATAL EXCEPTION' not in crash and b'Fatal signal' not in crash
    image = device('exec-out', 'run-as', 'app.onklav.home.prototype.rewrite', 'cat',
        'cache/qa-reminder-chooser.png', seconds=20, check=False)
    if image.startswith(b'\x89PNG'): (evidence / 'reminder-chooser.png').write_bytes(image)
    record = {'result': 'pass', 'tests': 1, 'device': 'Android 10 API 29 x86_64, software graphics, no KVM',
        'elapsed_seconds': round(time.monotonic() - started, 1), 'external_sms_sent': False,
        'default_sms_role_requested': False, 'crash_buffer': 'no fatal entries'}
    (evidence / 'device-result.json').write_text(json.dumps(record, indent=2) + '\n')
    print(json.dumps(record), flush=True)
finally:
    try: device('emu', 'kill', seconds=8, check=False)
    except Exception: pass
    try: emulator.wait(timeout=10)
    except subprocess.TimeoutExpired: emulator.kill()
    emulator_log.close()
