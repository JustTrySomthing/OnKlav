"""Refresh the compact Radio Browser starter catalog (no credentials)."""
import json,gzip,uuid,urllib.parse,urllib.request,time,argparse
from pathlib import Path
r=Path(__file__).resolve().parents[1];rows=[]
parser=argparse.ArgumentParser();parser.add_argument('--browser-json',type=Path);args=parser.parse_args()
browser_url='https://de1.api.radio-browser.info/json/stations/search?hidebroken=true&is_https=true&limit=100000&order=clickcount&reverse=true'
def fetch(url,limit):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OnKlav-Radio-Catalog/0.85 (https://onklav.app)'}),timeout=60) as response:
  data=response.read(limit+1)
  if len(data)>limit:raise ValueError('Directory export exceeds limit')
  return data
browser_data=args.browser_json.read_bytes() if args.browser_json else fetch(browser_url,96*1024*1024)
browser_rows=json.loads(browser_data)
def url(s):
 try:
  p=urllib.parse.urlsplit(s);host=p.hostname
  if p.scheme!='https' or not host or p.username or p.password or p.fragment or any(c.isspace() for c in s):return None
  if host in ['localhost'] or host.endswith('.local') or (p.port not in [None,443] and not 1024<=p.port<=65535):return None
  return s
 except:return None
for v in browser_rows:
 u=url(v.get('url_resolved',''));name=v.get('name','').strip()
 if not u or not name or v.get('hls') or v.get('lastcheckok')!=1 or v.get('codec','').upper() not in ['MP3','AAC','AAC+','AACP']:continue
 if urllib.parse.urlsplit(u).path.lower().endswith(('.m3u8','.m3u','.pls','.asx')):continue
 try:uuid.UUID(v['stationuuid'])
 except:continue
 row={k:v.get(k,'') for k in ['country','countrycode','language','tags','codec','bitrate']};row.update(id=v['stationuuid'],name=name[:100],url=u,homepage=v.get('homepage',''),source='Radio Browser');rows.append(row)
p=r/'android/app/src/main/assets/radio/catalog.json.gz';p.parent.mkdir(exist_ok=True)
with gzip.GzipFile(p,'wb',mtime=0) as out:out.write(json.dumps({'at':int(time.time()*1000),'rows':rows},ensure_ascii=False,separators=(',',':')).encode())
print('Radio Browser compatible rows',len(rows),'gzip bytes',p.stat().st_size)
