#!/usr/bin/env python3
"""Collect bounded startup/memory/frame evidence from an explicitly selected phone.

Run separately for the reference and new app. Does not install, clear data,
change permissions, exercise carrier functions or pretend host tests are timings.
"""
import argparse
import datetime
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',required=True)
    p.add_argument('--package',required=True)
    p.add_argument('--activity',required=True,help='Fully qualified launch Activity')
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--runs',type=int,default=3)
    args=p.parse_args()
    if not shutil.which('adb'):
        p.error('adb is not installed; no device measurement was made')
    if not 1<=args.runs<=5:
        p.error('--runs must be 1 to 5')
    for value in [args.package,args.activity]:
        if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.]*',value):
            p.error('Use explicit package and Activity class names')
    adb=['adb','-s',args.serial]
    def call(*tail):
        result=subprocess.run(adb+list(tail),capture_output=True,text=True,timeout=20,check=True)
        return result.stdout.strip()
    if call('get-state')!='device':
        sys.exit('Selected device is not ready; no retry loop was started')
    package=call('shell','dumpsys','package',args.package)
    if 'versionCode=' not in package:
        sys.exit('Requested app is not installed')
    report={'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'serial':args.serial,'package':args.package,'package_dump':package,
            'device':call('shell','getprop','ro.product.model'),
            'android':call('shell','getprop','ro.build.version.release'),
            'scope':'process-cold startup; gfxinfo covers these launches only, not app navigation',
            'launches':[]}
    call('shell','dumpsys','gfxinfo',args.package,'reset')
    for i in range(args.runs):
        call('shell','am','force-stop',args.package)
        raw=call('shell','am','start','-W','-n',args.package+'/'+args.activity)
        if not re.search(r'^Status: ok$',raw,re.M) or re.search(r'^Error',raw,re.M):
            raise RuntimeError('Launch failed; no timing claimed: '+raw)
        values={k:int(v) for k,v in re.findall(r'^(ThisTime|TotalTime|WaitTime):\s*(\d+)',raw,re.M)}
        report['launches'].append({'run':i+1,'milliseconds':values,'raw':raw})
    report['memory']=call('shell','dumpsys','meminfo',args.package)
    report['frames']=call('shell','dumpsys','gfxinfo',args.package)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(report,indent=2)+'\n')
    print('Saved '+str(args.output)+'. Navigation/scrolling and subjective responsiveness need a separate handset pass.')


if __name__=='__main__':
    main()
