"""Reproduce the offline starter directory from Podcast Index's MIT sample export."""
import csv,io,json,hashlib,urllib.request,urllib.parse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
URL='https://raw.githubusercontent.com/Podcastindex-org/database/master/db_newsfeeds_1000rows.csv'
def project(data):
 rows=[];seen=set()
 for r in csv.DictReader(io.StringIO(data.decode('utf-8-sig'))):
  feed=r['url'];u=urllib.parse.urlsplit(feed)
  if u.scheme!='https' or not u.hostname or u.username or u.password or u.port not in (None,443)or len(feed)>2048 or r['dead']!='0' or not r['title'].strip()or feed in seen:continue
  seen.add(feed)
  def plain(s,n):return ''.join(c for c in s if c.isprintable()).strip()[:n]
  rows.append({'id':hashlib.sha256(feed.encode()).hexdigest(),'title':plain(r['title'],200),'author':plain(r['itunes_author'],160),'feed':feed,'language':plain(r['language'],16)})
 return sorted(rows,key=lambda r:r['title'].casefold())
if __name__=='__main__':
 data=urllib.request.urlopen(URL,timeout=30).read();rows=project(data)
 dest=ROOT/'android/app/src/main/assets/podcasts';dest.mkdir(parents=True,exist_ok=True);(dest/'catalog.json').write_text(json.dumps(rows,ensure_ascii=False,separators=(',',':'))+'\n')
 (dest/'catalog-source.json').write_text(json.dumps({'source':URL,'source_sha256':hashlib.sha256(data).hexdigest(),'license':'MIT (repository README declaration)','records':len(rows),'profile':'Bundled starter snapshot, not the complete or live Podcast Index. Public feed availability is checked on opening. No directory API call, account or key.'},indent=2)+'\n')
 print('Bundled HTTPS directory records:',len(rows))
