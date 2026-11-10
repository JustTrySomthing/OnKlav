from pathlib import Path
import subprocess, zipfile, tempfile, re, os
import argparse
parser=argparse.ArgumentParser(description='Focused actual ChatStore/Rust checks; not full Android notification/transport qualification')
parser.add_argument('--env',required=True,type=Path);parser.add_argument('--output',required=True,type=Path)
args=parser.parse_args();root=Path(__file__).resolve().parents[1];env=args.env.resolve();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
cache=env/'gradle/caches/modules-2/files-2.1'
jars=list(cache.rglob('*.jar'))
preferred=[next((cache/'org.jetbrains.kotlin/kotlin-stdlib/2.1.0').rglob('*.jar'))]
cp=preferred+jars
for i,a in enumerate(cache.rglob('*.aar')):
 with zipfile.ZipFile(a) as z:
  if 'classes.jar' in z.namelist():
   p=out/f'aar-{i}.jar';p.write_bytes(z.read('classes.jar'));cp.append(p)
cp.append(next((env/'m2').rglob('android-all-instrumented-10-*.jar')))
sdk=out/'sdk';sdk.mkdir(exist_ok=True)
for source in (env/'m2').rglob('*.jar'):
 target=sdk/source.name
 if not target.exists():target.symlink_to(source)
classpath=':'.join(map(str,cp))
core=(root/'android/shared/src/main/kotlin/app/onklav/shared/Core.kt').read_text();obj=core[core.index('object NativeCore {'):core.index('\ndata class Note')]
(out/'NativeCore.kt').write_text('package app.onklav.shared\n'+obj)
messages=(root/'android/app/src/main/kotlin/app/onklav/home/Messages.kt').read_text();phone=messages[messages.index('fun phoneKey('):messages.index('\nfun sameRecipients')]
(out/'Phone.kt').write_text('package app.onklav.home\n'+phone)
# Exercise the exact production store and Rust persistence independently of unrelated Android app dependencies.
test=(root/'android/app/src/test/kotlin/app/onklav/home/IncomingArchiveTest.kt').read_text()
test=test.replace('@Config(sdk=[29])','@Config(sdk=[29], manifest=Config.NONE)')
test=re.sub(r'MessageNotifications.incoming\(c, (\d+), ("[^"]*"), "[^"]*"\)',r'store.incomingThread(\1, \2)',test)
(out/'IncomingArchiveTest.kt').write_text(test)
sources=[root/'android/app/src/main/kotlin/app/onklav/home/ChatStore.kt',out/'NativeCore.kt',out/'Phone.kt',out/'IncomingArchiveTest.kt']
java=str(env/'jdk/bin/java');classes=out/'classes'
compiler_cp=':'.join(str(next((cache / group / artifact / version).rglob('*.jar'))) for group, artifact, version in [('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.1.20'),('org.jetbrains.kotlin','kotlin-stdlib','2.1.0'),('org.jetbrains.kotlin','kotlin-script-runtime','2.1.20'),('org.jetbrains.kotlin','kotlin-reflect','1.6.10'),('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm','1.8.0'),('org.jetbrains','annotations','13.0'),('org.jetbrains.intellij.deps','trove4j','1.0.20200330')])
subprocess.run([java,'-cp',compiler_cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','17','-classpath',classpath,'-d',str(classes),*map(str,sources)],check=True,timeout=60)
subprocess.run([java,'-Xmx2g',f'-Djava.library.path={root}/core/host-libs/linux-x86_64',f'-Drobolectric.dependency.dir={out}/sdk','-Drobolectric.offline=true','-cp',str(classes)+':'+classpath,'org.junit.runner.JUnitCore','app.onklav.home.IncomingArchiveTest'],check=True,timeout=90)
