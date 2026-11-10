#!/usr/bin/env python3
"""Bounded signed-package workflow checks; provider responses are explicit fixtures."""
import json,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'developer'))
from onklav import Runtime

def main():
 count=0
 with tempfile.TemporaryDirectory(prefix='onklav-podcast-polish-') as folder,Runtime(folder) as runtime:
  review=runtime.request({'action':'stage','path':str(ROOT/'sdk/packages/Podcasts.onklav-a')})
  runtime.request({'action':'install','hash':review['hash'],'grants':['storage.private','audio.playback','podcasts.library']})
  current=runtime.request({'action':'launch','id':'app.onklav.podcasts'});session=current['session']
  def event(action,value=''):
   nonlocal current,count
   current=runtime.request({'action':'event','session':session,'event':{'action':action,'value':value}});count+=1
   return current['frame']['state']
  def reply(service,value=None,ok=True):
   nonlocal current,count
   p=current['pending'];assert p['service']==service,(p,service)
   current=runtime.request({'action':'reply','session':session,'token':p['token'],'reply':{'ok':ok,**({'value':value} if ok else {'error':'Fixture unavailable'})}});count+=1
   return current['frame']['state']
  row={'title':'Science Show','feed':'https://example.org/rss','author':'Creator'}
  reply('podcasts.search',{'items':[row],'total':1,'next':None})
  event('pod.settings');assert current['frame']['menu_title']=='Podcast settings'
  event('pod.feed.url','invalid-feed');event('pod.feed.add');assert current['frame']['state']['settings'] is True;assert current['frame']['state']['source_error'];assert current.get('pending') is None
  event('pod.feed.url',row['feed']);event('pod.feed.add')
  reply('podcasts.feed',ok=False);assert current['frame']['back_action']=='pod.back'
  event('pod.back');event('pod.settings');event('pod.feed.add')
  episode={'id':'a'*64,'title':'Episode one','date':'Today','duration':'12:30'}
  reply('podcasts.feed',{'title':row['title'],'items':[episode],'total':1,'next':None})
  assert len(current['frame']['state']['sources'])==1
  assert current['frame']['state']['library']==[]
  assert any(n['text']=='Subscribe' for n in current['frame']['body'] if n['kind']=='button')
  event('pod.episode.play.'+episode['id'])
  reply('podcasts.play',{'episode':episode,'player':{'state':'playing'}})
  assert len(current['frame']['state']['recent'])==1
  assert current['frame']['state']['library']==[]
  event('pod.recent');event('pod.recent.play.'+episode['id']);reply('podcasts.play',ok=False)
  assert len(current['frame']['state']['recent'])==1
  event('pod.retry');reply('podcasts.play',{'episode':episode,'player':{'state':'playing'}})
  event('pod.discover');open_id=next(n['id'] for n in current['frame']['body'] if n['id'].startswith('pod.open.'))
  event(open_id);reply('podcasts.feed',{'title':row['title'],'items':[episode],'total':1,'next':None})
  event('pod.follow');assert current['frame']['state']['followed'] is True
  event('pod.library');assert len(current['frame']['state']['library'])==1
  runtime.request({'action':'close','session':session});current=runtime.request({'action':'launch','id':'app.onklav.podcasts'});session=current['session']
  reply('podcasts.search',{'items':[row],'total':1,'next':None})
  assert len(current['frame']['state']['recent'])==1
  assert len(current['frame']['state']['sources'])==1
  assert len(current['frame']['state']['library'])==1
  event('pod.recent');event('pod.clear');event('pod.clear.accept')
  assert current['frame']['state']['recent']==[]
  assert len(current['frame']['state']['library'])==1
 print(json.dumps({'passed_steps':count,'signed_package':True,'provider_responses':'fixtures','phone_audio_tested':False}))
if __name__=='__main__':main()
