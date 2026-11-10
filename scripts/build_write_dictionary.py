"""Expand the pinned SCOWL/Hunspell English inflections into an offline Write lexicon.
Run with Debian hunspell-en-us 1:2020.12.07-2. No Hunspell runtime dependency.
"""
from pathlib import Path
import re,gzip,hashlib,json
root=Path(__file__).resolve().parents[1];src=Path('/usr/share/hunspell');rules={};cross={}
for line in (src/'en_US.aff').read_text().splitlines():
 p=line.split()
 if len(p)==4 and p[0] in ('PFX','SFX'):cross[p[0],p[1]]=p[2]=='Y'
 if len(p)>=5 and p[0] in ('PFX','SFX'):rules.setdefault((p[0],p[1]),[]).append((p[2],p[3],p[4]))
def apply(word,kind,flag):
 for strip,add,cond in rules.get((kind,flag),[]):
  if not re.search(('^'+cond) if kind=='PFX' else (cond+'$'),word):continue
  strip='' if strip=='0' else strip;add='' if add=='0' else add
  if kind=='PFX' and word.startswith(strip):yield add+word[len(strip):]
  if kind=='SFX' and word.endswith(strip):yield (word[:-len(strip)] if strip else word)+add
words={}
for line in (src/'en_US.dic').read_text().splitlines()[1:]:
 entry=line.split()[0];word,_,flags=entry.partition('/')
 if 'c' in flags:continue
 variants={word}
 for flag in flags:
  for kind in ('PFX','SFX'):variants.update(apply(word,kind,flag))
 for pre in flags:
  if cross.get(('PFX',pre)):
   for prefixed in apply(word,'PFX',pre):
    for suf in flags:
     if cross.get(('SFX',suf)):variants.update(apply(prefixed,'SFX',suf))
 for v in variants:
  v=v.lower().replace('’',"'")
  if re.fullmatch(r"[a-z]+(?:'[a-z]+)?",v):words[v]=words.get(v,False) or '!' not in flags
out=root/'android/app/src/main/assets/write';out.mkdir(exist_ok=True)
raw='\n'.join(('' if suggest else '!')+w for w,suggest in sorted(words.items()))+'\n'
(out/'english.txt.gz').write_bytes(gzip.compress(raw.encode(),mtime=0))
notice=Path('/usr/share/doc/hunspell-en-us/copyright').read_bytes();(out/'english-COPYRIGHT.txt').write_bytes(notice)
meta={'source':'SCOWL/Hunspell en_US, Debian hunspell-en-us 1:2020.12.07-2','upstream':'https://wordlist.aspell.net/','words':len(words),'source_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [src/'en_US.dic',src/'en_US.aff']},'generated_sha256':hashlib.sha256((out/'english.txt.gz').read_bytes()).hexdigest(),'license':'english-COPYRIGHT.txt'}
(out/'english.json').write_text(json.dumps(meta,indent=2)+'\n')
p=root/'docs/vendor-dependencies.json';m=json.loads(p.read_text());m['android/app/src/main/assets/write/english.txt.gz']=meta['generated_sha256'];p.write_text(json.dumps(m,indent=2)+'\n')
print(meta)
