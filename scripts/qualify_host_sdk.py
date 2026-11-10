"""One bounded optimized synthetic host run; does not build an APK or qualify Android."""
from pathlib import Path
import os,json,tempfile,subprocess,signal,hashlib
ROOT=Path(__file__).resolve().parents[1]
def run(cmd,env,timeout,stdout):
 p=subprocess.Popen(cmd,env=env,stdout=stdout,stderr=subprocess.PIPE,text=True,start_new_session=True)
 try:_,err=p.communicate(timeout=timeout)
 except subprocess.TimeoutExpired:
  os.killpg(p.pid,signal.SIGKILL);p.wait();raise SystemExit('Bounded qualification deadline exceeded; process group stopped')
 if p.returncode:raise SystemExit(err[-4000:])
 return err
inputs=sorted((ROOT/'developer/components/src').rglob('*.rs'))
source={x.relative_to(ROOT).as_posix():hashlib.sha256(x.read_bytes()).hexdigest() for x in inputs}
with tempfile.TemporaryDirectory(prefix='onklav-host-qualification-') as tmp:
 p=Path(tmp);(p/'src').mkdir();(p/'src/main.rs').write_bytes((ROOT/'scripts/qualification_sdk_workload.rs').read_bytes())
 (p/'Cargo.toml').write_text('[package]\nname="onklav-host-qualification"\nversion="0.1.0"\nedition="2021"\n[dependencies]\nonklav-ui={path='+json.dumps(str(ROOT/'developer/components'))+'}\n')
 env=os.environ.copy();env['CARGO_NET_OFFLINE']='true';env['CARGO_INCREMENTAL']='0';env['CARGO_TARGET_DIR']=str(ROOT/'developer/target/qualification')
 evidence=ROOT/'docs/evidence/v0.70.0';evidence.mkdir(parents=True,exist_ok=True)
 with (evidence/'qualification41-build.log').open('w')as log:
  err=run(['cargo','build','--offline','--release','--manifest-path',str(p/'Cargo.toml')],env,120,log);log.write(err)
 with (evidence/'qualification41-host-raw.json').open('w')as out:run([str(Path(env['CARGO_TARGET_DIR'])/'release/onklav-host-qualification')],env,30,out)
 result=json.loads((evidence/'qualification41-host-raw.json').read_text());current={x.relative_to(ROOT).as_posix():hashlib.sha256(x.read_bytes()).hexdigest() for x in sorted((ROOT/'developer/components/src').rglob('*.rs'))};assert current==source,'SDK inputs changed during qualification; discard run';result['source_sha256']=source;result['runner_sha256']=hashlib.sha256((ROOT/'scripts/qualification_sdk_workload.rs').read_bytes()).hexdigest();result['apk_created']=False
 (ROOT/'docs/qualification41-host-performance.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items() if k not in ['source_sha256']},indent=2))
