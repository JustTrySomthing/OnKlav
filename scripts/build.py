#!/usr/bin/env python3
"""Incremental phone APK build. Broad tests are an explicit --tests phase."""
import argparse
import json
import os
from pathlib import Path
import re
import signal
import shutil
import subprocess
import sys
import time
from urllib.parse import urlparse
from build_speech import current as speech_current
from build_inputs import native_current, sdk_current, games_current, game_inputs, sha, android_inputs

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--variant', choices=['debug', 'optimized'], default='optimized')
    parser.add_argument('--abis', default='arm64-v8a', help='Comma-separated arm64-v8a,x86_64')
    parser.add_argument('--tests', action='store_true', help='Also run Android framework host tests')
    parser.add_argument('--test',action='append',default=[],help='Selected debug host test class; run in the same Gradle invocation as assembly')
    parser.add_argument('--plan', action='store_true', help='Show stale components without building')
    parser.add_argument('--online', action='store_true', help='Allow Gradle to fetch missing dependencies once')
    args = parser.parse_args()
    if args.test and args.variant!='debug':parser.error('--test requires --variant debug')
    abis = args.abis.split(',')
    if not abis or len(set(abis)) != len(abis) or any(a not in {'arm64-v8a', 'x86_64'} for a in abis):
        parser.error('--abis must contain arm64-v8a and/or x86_64, without duplicates')
    plan = {'sdk_apps': 'reuse' if sdk_current(ROOT) else 'build',
            'mobile_rust': 'reuse' if native_current(ROOT, abis) else 'build',
            'neural_speech': 'reuse' if speech_current(abis) else 'build',
            'games': 'reuse' if games_current(ROOT) else 'test/export',
            'android_tests': args.test if args.test else ('run' if args.tests else 'not requested'),
            'variant': args.variant, 'abis': abis}
    print(json.dumps(plan, indent=2), flush=True)
    if args.plan:
        return
    sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', '')))
    jdk = Path(os.environ.get('JAVA_HOME', ''))
    if not (sdk / 'platforms/android-36/android.jar').is_file() or not (jdk / 'bin/javac').is_file():
        sys.exit('Set ANDROID_HOME (platform 36/build-tools 35) and JAVA_HOME (JDK 17).')
    signing_home = Path.home() / '.android'
    if not (signing_home / 'debug.keystore').is_file():
        sys.exit('Restore the existing OnKlav signing backup; do not generate a replacement key.')
    # AGP can default to the XDG config directory and silently create a different signer.
    os.environ['ANDROID_USER_HOME'] = str(signing_home)
    version = re.search(r'versionName="([^"]+)"', (ROOT / 'android/app/build.gradle.kts').read_text()).group(1).split('-')[0]
    evidence = ROOT / f'docs/evidence/v{version}'
    evidence.mkdir(parents=True, exist_ok=True)
    report = {'plan': plan, 'phases': [], 'status': 'running'}

    def run(label, command, limit):
        print(f'{label}: starting (maximum {limit}s)', flush=True)
        started = time.monotonic()
        path = evidence / f'{label}.log'
        with path.open('wb') as log:
            child = subprocess.Popen(list(map(str, command)), cwd=ROOT, stdout=log,
                                     stderr=subprocess.STDOUT, start_new_session=True)
            try:
                deadline = started + limit
                while child.poll() is None:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise subprocess.TimeoutExpired(command, limit)
                    try:
                        child.wait(timeout=min(20, remaining))
                    except subprocess.TimeoutExpired:
                        print(f'{label}: {time.monotonic()-started:.0f}s elapsed', flush=True)
            except BaseException:
                os.killpg(child.pid, signal.SIGTERM)
                try:
                    child.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(child.pid, signal.SIGKILL)
                    child.wait()
                raise
        record = {'phase': label, 'seconds': round(time.monotonic()-started, 2),
                  'returncode': child.returncode, 'log': path.relative_to(ROOT).as_posix()}
        report['phases'].append(record)
        print(f"{label}: finished in {record['seconds']}s", flush=True)
        if child.returncode:
            print(path.read_text(errors='replace')[-5000:], file=sys.stderr)
            raise RuntimeError(f'{label} failed; see {path}')

    try:
        if plan['sdk_apps'] == 'build':
            run('sdk-build', [sys.executable, 'scripts/build_sdk_apps.py'], 600)
        if plan['mobile_rust'] == 'build':
            run('native-build', [sys.executable, 'scripts/build_native.py', '--abis', args.abis], 600)
        if plan['neural_speech']=='build':
            run('speech-native',[sys.executable,'scripts/build_speech.py','--abis',args.abis],600)
        if plan['games'] != 'reuse':
            run('shared-game-runtime', [sys.executable, 'scripts/build_games.py'], 600)
        if not (sdk_current(ROOT) and native_current(ROOT, abis) and games_current(ROOT)):
            raise RuntimeError('Prepared artifacts do not match their current source proofs')
        if args.tests:
            if not native_current(ROOT, [], host=True):
                run('host-native', [sys.executable, 'scripts/build_native.py', '--host-only'], 600)
            run('android-host-tests', [sys.executable, 'scripts/check_android.py', '--tests-only'], 600)
        gradle = ['bash', 'gradlew', *([] if args.online else ['--offline']), '--no-daemon', '--max-workers=2', '--console=plain', '--stacktrace',
                  '-Dorg.gradle.internal.http.connectionTimeout=15000', '-Dorg.gradle.internal.http.socketTimeout=15000',
                  f"-Pandroid.aapt2FromMavenOverride={sdk / 'build-tools/35.0.0/aapt2'}",
                  f'-PonklavAbis={args.abis}']
        proxy=urlparse(os.environ.get('HTTPS_PROXY',os.environ.get('https_proxy','')))
        if args.online and proxy.hostname:
            for scheme in ('http','https'):
                gradle += [f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port or 80}']
        certs=Path('/etc/ssl/certs/java/cacerts')
        if args.online and certs.is_file():
            gradle += [f'-Djavax.net.ssl.trustStore={certs}']
        before_android=android_inputs(ROOT,args.variant)
        # AGP may retain the prior R.jar's DEX when resource inputs change in a
        # restored cache. Keep Kotlin-only incremental builds; reset only these
        # generated DEX outputs after a resource/dependency change.
        resource_inputs={name:digest for name,digest in before_android.items()
                         if "/res/" in name or name.endswith(("AndroidManifest.xml","build.gradle.kts","settings.gradle.kts"))}
        resource_stamp=ROOT/f"android/app/build/onklav-resource-inputs-{args.variant}.json"
        previous_resources=json.loads(resource_stamp.read_text()) if resource_stamp.is_file() else None
        if resource_inputs!=previous_resources:
            print("Resource inputs changed: refreshing generated resource/package/DEX caches",flush=True)
            # AGP can regenerate listing metadata while retaining a previous
            # signed APK. Remove only this regenerable output before packaging.
            previous_apk=ROOT/f"android/app/build/outputs/apk/{args.variant}/app-{args.variant}.apk"
            previous_apk.unlink(missing_ok=True)
            intermediates=ROOT/"android/app/build/intermediates"
            for folder in (f"project_dex_archive/{args.variant}",f"incremental/dexBuilder{args.variant.capitalize()}",f"dex/{args.variant}/mergeProjectDex{args.variant.capitalize()}",f"linked_resources_binary_format/{args.variant}",f"linked_resources_proto_format/{args.variant}",f"incremental/package{args.variant.capitalize()}"):
                generated=intermediates/folder
                assert generated.resolve().is_relative_to(intermediates.resolve())
                if generated.exists():shutil.rmtree(generated)
        (evidence/f'inputs-{args.variant}.json').write_text(json.dumps(before_android,indent=2)+'\n')
        selected=[]
        if args.test:
            if not native_current(ROOT,[],host=True):raise RuntimeError('Selected host tests require the source-matched host JNI library')
            selected=[':android:app:testDebugUnitTest',*[value for selector in args.test for value in ['--tests',selector]]]
        run('assemble-' + args.variant, gradle + [':android:app:assemble' + args.variant.capitalize(),*selected], 900 if args.variant=='optimized' else 600)
        if before_android!=android_inputs(ROOT,args.variant) or not (sdk_current(ROOT) and native_current(ROOT,abis) and games_current(ROOT)):
            raise RuntimeError('Production inputs changed during APK assembly; refusing to certify a mixed checkpoint')
        run('verify-' + args.variant, [sys.executable, 'scripts/verify_apks.py', '--variant', args.variant, '--abis', args.abis], 120)
        resource_stamp.write_text(json.dumps(resource_inputs,sort_keys=True)+'\n')
        report['status'] = 'passed'
        print('APK assembled and verified. Phone/carrier/performance acceptance remains separate.')
    finally:
        if report['status'] == 'running':
            report['status'] = 'failed'
        (evidence / f'build-{args.variant}.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
