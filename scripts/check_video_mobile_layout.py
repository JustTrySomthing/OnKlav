#!/usr/bin/env python3
"""Bounded offline production-UI checks with explicit test-only backend fixtures.

This does not build a distributable APK or qualify real codecs, storage, permissions,
playback or export. Never copy the verification stubs into production source.
"""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--env',type=Path,required=True,help='Restored JDK/SDK/Gradle/m2 directory')
p.add_argument('--output',type=Path,required=True,help='Isolated temporary verification directory')
a=p.parse_args();env=a.env.resolve();out=a.output.resolve()
if out==root or out.is_relative_to(root):sys.exit('Use an isolated directory outside production source.')
for name in ['jdk/bin/java','sdk/platforms/android-36/android.jar','gradle','m2']:
 if not (env/name).exists():sys.exit('Missing restored build component: '+name)
out.mkdir(parents=True,exist_ok=True)
fixtures=root/'docs/fixtures/video-editor-mobile-ui'
for name in ['build.gradle.kts','gradlew','gradle.properties']:shutil.copy2(root/name,out/name)
shutil.copytree(root/'gradle',out/'gradle',dirs_exist_ok=True)
(out/'settings.gradle.kts').write_text((root/'settings.gradle.kts').read_text().split('rootProject.name')[0]+'rootProject.name="VideoUiVerification"\ninclude(":ui")\n')
ui=out/'ui';home=ui/'src/main/kotlin/app/onklav/home';home.mkdir(parents=True,exist_ok=True)
inputs={}
for name in ['VideoEditor.kt','VideoProWorkspace.kt','VideoEditorLayout.kt','VideoTimelinePrecision.kt','VideoEditData.kt','VideoProData.kt','AudioEditorRefinements.kt']:
 source=root/'android/app/src/main/kotlin/app/onklav/home'/name
 shutil.copy2(source,home/name);inputs[str(source.relative_to(root))]=hashlib.sha256(source.read_bytes()).hexdigest()
shared=ui/'src/main/kotlin/app/onklav/shared';shared.mkdir(parents=True,exist_ok=True)
shutil.copy2(root/'android/shared/src/main/kotlin/app/onklav/shared/OnKlavFileContracts.kt',shared)
shutil.copytree(root/'android/shared/src/main/res',ui/'src/main/res',dirs_exist_ok=True)
(ui/'src/main/AndroidManifest.xml').write_bytes((fixtures/'manifest.txt').read_bytes())
sdk=out/'sdk';sdk.mkdir(exist_ok=True)
for jar in (env/'m2').rglob('*.jar'):
 target=sdk/jar.name
 if not target.exists():target.symlink_to(jar)
(ui/'build.gradle.kts').write_text((fixtures/'module-build.txt').read_text().replace('/tmp/video-ui-sdk',str(sdk)).replace('/tmp/video-ui-evidence',str(out/'evidence')))
(home/'UiFakes.kt').write_bytes((fixtures/'UiFakes.txt').read_bytes())
for folder,name,fixture in [('androidx/media3/ui','PlayerView.kt','PlayerView.txt'),('androidx/media3/common/util','Markers.kt','Markers.txt')]:
 target=ui/'src/main/kotlin'/folder;target.mkdir(parents=True,exist_ok=True);(target/name).write_bytes((fixtures/fixture).read_bytes())
tests=ui/'src/test/kotlin/app/onklav/home';tests.mkdir(parents=True,exist_ok=True)
(tests/'VideoMobileLayoutTest.kt').write_text((fixtures/'VideoMobileLayoutTest.txt').read_text().replace('/tmp/video-ui-progress.txt',str(out/'progress.txt')))
# Regenerate the runtime jar so old compiled lambdas cannot mask a changed UI.
runtime=ui/'build/intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar'
if runtime.exists():shutil.rmtree(runtime)
process_env=os.environ.copy();process_env.update(JAVA_HOME=str(env/'jdk'),ANDROID_HOME=str(env/'sdk'),GRADLE_USER_HOME=str(env/'gradle'))
process_env['PATH']=str(env/'jdk/bin')+os.pathsep+process_env['PATH']
process_env['GRADLE_OPTS']='-Dorg.gradle.jvmargs=-Xmx4g -Dorg.gradle.caching=false'
command=['bash','gradlew','--offline','--no-daemon','--max-workers=2','--console=plain','-Pandroid.aapt2FromMavenOverride='+str(env/'sdk/build-tools/35.0.0/aapt2'),':ui:testDebugUnitTest','--tests','app.onklav.home.VideoMobileLayoutTest']
print('Compiling production UI and running nine isolated screen checks (180s limit).',flush=True)
with (out/'check.log').open('wb') as log:
 child=subprocess.Popen(command,cwd=out,env=process_env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 try:code=child.wait(timeout=180)
 except (subprocess.TimeoutExpired,KeyboardInterrupt):
  os.killpg(child.pid,signal.SIGTERM)
  try:child.wait(timeout=5)
  except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
  sys.exit('Stopped the bounded screen check; inspect '+str(out/'check.log'))
if code:sys.exit('Screen check failed; inspect '+str(out/'check.log'))
result=ET.parse(ui/'build/test-results/testDebugUnitTest/TEST-app.onklav.home.VideoMobileLayoutTest.xml').getroot()
assert result.attrib['tests']=='9' and all(result.attrib[k]=='0' for k in ['failures','errors','skipped'])
count=0
with zipfile.ZipFile(runtime/'classes.jar') as jar:
 for cls in (ui/'build/tmp/kotlin-classes/debug').rglob('*.class'):
  assert jar.read(cls.relative_to(ui/'build/tmp/kotlin-classes/debug').as_posix())==cls.read_bytes();count+=1
report={'screen_checks':9,'runtime_classes_matched':count,'production_ui_sha256':inputs,'apk_created':False,'scope':'Isolated UI only; fixture model/permissions/player. Not real media/backend acceptance.'}
(out/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
