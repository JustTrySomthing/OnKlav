"""Bounded source-matched checks; no APK/device/provider access. Usage: run.py BUILD_ENV OUTPUT"""
from pathlib import Path
import sys, subprocess, zipfile, json, hashlib
ROOT=Path(__file__).resolve().parents[3]
ENV=Path(sys.argv[1]).resolve();OUT=Path(sys.argv[2]).resolve();OUT.mkdir(parents=True,exist_ok=True)
cache=ENV/'gradle/caches/modules-2/files-2.1'
stdlib=next((cache/'org.jetbrains.kotlin/kotlin-stdlib/2.1.20').rglob('*.jar'))
jars=[stdlib]+list(cache.rglob('*.jar'))
for i,a in enumerate(cache.rglob('*.aar')):
 with zipfile.ZipFile(a) as z:
  if 'classes.jar' in z.namelist():
   p=OUT/f'aar-{i}.jar';p.write_bytes(z.read('classes.jar'));jars.append(p)
jars.append(next((ENV/'m2').rglob('android-all-instrumented-10-*.jar')))
sdk=OUT/'sdk';sdk.mkdir(exist_ok=True)
for source in (ENV/'m2').rglob('*.jar'):
 target=sdk/source.name
 if not target.exists():target.symlink_to(source)
cp=':'.join(map(str,jars))
parts=[('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.1.20'),('org.jetbrains.kotlin','kotlin-stdlib','2.1.20'),('org.jetbrains.kotlin','kotlin-script-runtime','2.1.20'),('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),('org.jetbrains','annotations','13.0'),('org.jetbrains.intellij.deps','trove4j','1.0.20200330')]
compiler=':'.join(str(next((cache/g/a/v).rglob('*.jar')))for g,a,v in parts)
base=ROOT/'android/app/src/main/kotlin/app/onklav/home'
sources=[base/n for n in ['CalendarData.kt','CalendarIcs.kt','CalendarReminders.kt','CalendarModel.kt','CloudData.kt']]
secret=OUT/'SecretKeys.kt';secret.write_text((base/'SdkSecrets.kt').read_text().split('/** Ciphertext only')[0]);sources.append(secret)
sources+=[ROOT/'android/shared/src/main/kotlin/app/onklav/shared/LauncherLayout.kt']
stub=OUT/'MainActivity.kt';stub.write_text('package app.onklav.home\nclass MainActivity\n');sources.append(stub)
names=['CalendarTest','CalendarRemindersTest','CalendarModelTest']
for n in names:
 p=OUT/(n+'.kt');p.write_text((ROOT/f'android/app/src/test/kotlin/app/onklav/home/{n}.kt').read_text().replace('@Config(sdk=[29])','@Config(sdk=[29], manifest=Config.NONE)'));sources.append(p)
p=ROOT/'android/shared/src/test/kotlin/app/onklav/shared/LauncherLayoutTest.kt'
layout=OUT/p.name;layout.write_text(p.read_text().replace('@Config(sdk=[29])','@Config(sdk=[29], manifest=Config.NONE)'));sources.append(layout)
sources.append(Path(__file__).parent/'AuditObservations.kt')
java='/usr/bin/java'
compile_cp=str(ENV/'sdk/platforms/android-36/android.jar')+':'+cp
subprocess.run([java,'-Xmx2g','-cp',compiler,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',compile_cp,'-d',str(OUT/'classes'),*map(str,sources)],check=True,timeout=75)
subprocess.run([java,'-Xmx2g',f'-Drobolectric.dependency.dir={sdk}','-Drobolectric.offline=true','-cp',str(OUT/'classes')+':'+cp,'org.junit.runner.JUnitCore',*[f'app.onklav.home.{n}'for n in names],'app.onklav.shared.LauncherLayoutTest','app.onklav.home.AuditObservations','app.onklav.home.AuditScheduleObservation'],check=True,timeout=100)
(OUT/'source-hashes.json').write_text(json.dumps({str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sources if p.is_relative_to(ROOT)},indent=2)+'\n')
