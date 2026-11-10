#!/usr/bin/env python3
"""Build the portable game developer kit, excluding caches and keys; the verified export template is developer tooling."""
import argparse,hashlib,json,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parent

def package(output):
 if output.exists():raise ValueError("Output already exists")
 files={}
 for p in ROOT.rglob("*"):
  if p.is_file() and not p.is_symlink() and not any(x.startswith(".") or x in {"__pycache__","build","target"} for x in p.relative_to(ROOT).parts) and p.suffix not in {".pyc",".key",".pem",".onklav-g"}:
   files[p.relative_to(ROOT).as_posix()]=p.read_bytes()
 inventory={n:hashlib.sha256(b).hexdigest()for n,b in sorted(files.items())}
 files["CONTENTS.json"]=(json.dumps({"schema":"onklav.game.sdk.contents.v1","sha256":inventory},indent=2)+"\n").encode()
 output.parent.mkdir(parents=True,exist_ok=True)
 with output.open("xb")as stream,zipfile.ZipFile(stream,"w",zipfile.ZIP_DEFLATED)as z:
  for n,b in sorted(files.items()):z.writestr(n,b)
 with zipfile.ZipFile(output)as z:
  assert z.testzip()is None
  for n,h in inventory.items():assert hashlib.sha256(z.read(n)).hexdigest()==h
 return {"files":len(files),"bytes":output.stat().st_size,"sha256":hashlib.sha256(output.read_bytes()).hexdigest()}
if __name__=="__main__":
 p=argparse.ArgumentParser(description=__doc__);p.add_argument("output",type=Path);print(json.dumps(package(p.parse_args().output.resolve()),indent=2))
