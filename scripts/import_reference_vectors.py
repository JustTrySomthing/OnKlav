"""Reference icon geometry exported as static artwork; no legacy application implementation."""
from pathlib import Path
import math,xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]/'android/shared/src/main/res/drawable';root.mkdir(parents=True,exist_ok=True)
def line(x,y,a,b):return f'M{x},{y} L{a},{b}'
def oval(l,t,r,b):
 x=(l+r)/2;y=(t+b)/2;rx=(r-l)/2;ry=(b-t)/2
 return f'M{l},{y} A{rx},{ry} 0 1,0 {r},{y} A{rx},{ry} 0 1,0 {l},{y}'
def circ(x,y,r):return oval(x-r,y-r,x+r,y+r)
def rr(l,t,r,b,k):return f'M{l+k},{t} H{r-k} Q{r},{t} {r},{t+k} V{b-k} Q{r},{b} {r-k},{b} H{l+k} Q{l},{b} {l},{b-k} V{t+k} Q{l},{t} {l+k},{t} Z'
def arc(l,t,r,b,start,sweep):
 cx=(l+r)/2;cy=(t+b)/2;rx=(r-l)/2;ry=(b-t)/2
 def pt(a):return f'{cx+rx*math.cos(math.radians(a)):.4f},{cy+ry*math.sin(math.radians(a)):.4f}'
 return f'M{pt(start)} A{rx},{ry} 0 {int(abs(sweep)>180)},{int(sweep>0)} {pt(start+sweep)}'
def grid():return ' '.join(rr(3+x*7,3+y*7,7+x*7,7+y*7,.7) for y in range(3) for x in range(3))
def save(name,data,size=24,width=1.7,fill=False):
 ns='http://schemas.android.com/apk/res/android';E.register_namespace('android',ns)
 def at(**kw):return {f'{{{ns}}}{k}':str(v) for k,v in kw.items()}
 v=E.Element('vector',at(width=f'{size}dp',height=f'{size}dp',viewportWidth=size,viewportHeight=size))
 E.SubElement(v,'path',at(pathData=data,fillColor='#FFFFFFFF' if fill else '#00000000',strokeColor='#FFFFFFFF',strokeWidth=width,strokeLineCap='round',strokeLineJoin='round'))
 (root/f'ref_{name}.xml').write_text(E.tostring(v,encoding='unicode')+'\n')
nav={
'messages':'M4,3 H20 Q22,3 22,5 V16 Q22,18 20,18 H9 L3,22 V5 Q3,3 4,3 '+line(7,8,18,8)+' '+line(7,12,15,12),
'groups':circ(12,9.2,3.1)+' '+circ(4.1,6.5,2.3)+' '+circ(19.9,6.5,2.3)+' '+arc(6.5,14.5,17.5,28,180,180)+' '+arc(.5,11.5,8.1,22.5,180,130)+' '+arc(15.9,11.5,23.5,22.5,230,130),
'apps':grid(),
'games':'M7,6 H17 Q20,6 21,10 L23,18 Q22,22 19,19 L16,16 H8 L5,19 Q2,22 1,18 L3,10 Q4,6 7,6 '+line(5,11,10,11)+' '+line(7.5,8.5,7.5,13.5)+' '+circ(16.5,10,.7)+' '+circ(19,12.5,.7),
'settings':' '.join(('M' if i==0 else 'L')+f'{12+math.cos(i*math.pi/16)*(10 if i%4<2 else 7.5):.4f},{12+math.sin(i*math.pi/16)*(10 if i%4<2 else 7.5):.4f}' for i in range(32))+' Z '+circ(12,12,3),
'contacts':circ(12,7,4)+' '+arc(4,13,20,27,180,180)}
for k,v in nav.items():save('nav_'+k,v)
settings={
'apps':grid(),'games':'M6,6 H18 L22,19 L17,16 H7 L2,19 Z '+line(6,10,11,10)+' '+line(8.5,7.5,8.5,12.5)+' '+circ(17,10,1),
'sounds':'M3,9 H8 L13,4 V20 L8,15 H3 Z '+arc(11,5,23,19,-65,130)+' '+arc(13,8,19,16,-65,130),
'security':'M12,2 L21,6 L19,16 L12,22 L5,16 L3,6 Z '+line(8,12,11,15)+' '+line(11,15,16,9),
'privacy':'M12,2 L21,6 L19,16 L12,22 L5,16 L3,6 Z '+rr(8,11,16,17,1)+' '+arc(9,7,15,15,180,180),
'quiet_hours':'M17,3 C4,0 0,17 10,21 C16,24 22,18 21,13 C13,18 8,9 17,3 Z',
'reminders':circ(12,12,9)+' '+line(12,6,12,12)+' '+line(12,12,17,14),
'walkie_talkie':rr(5,7,19,22,3)+' '+line(8,7,8,1)+' '+rr(8,10,16,14,1)+' '+line(9,17,15,17)+' '+line(9,19,13,19),
'google_drive':'M9,3 H15 L23,17 L20,22 H4 L1,17 Z '+line(9,3,20,22)+' '+line(15,3,4,22)+' '+line(1,17,23,17),
'storage':rr(3,4,21,20,2)+' '+line(3,9,21,9)+' '+line(9,13,15,13),
'help':circ(12,12,9)+' '+arc(8,6,16,13,190,285)+' '+line(12,12,12,15)+' '+circ(12,18,.05),
'appearance':oval(2,3,22,21)+' '+circ(7,9,1)+' '+circ(12,6,1)+' '+circ(17,9,1)+' '+circ(7,15,1)+' '+circ(16,16,3),
'setup':circ(12,12,5)+' '+' '.join(line(12+math.cos(i*math.pi/4)*8,12+math.sin(i*math.pi/4)*8,12+math.cos(i*math.pi/4)*10,12+math.sin(i*math.pi/4)*10) for i in range(8)),
'profile':circ(12,8,4)+' '+arc(4,14,20,28,180,180)}
settings['read_aloud']=settings['sounds']
for k,v in settings.items():save('setting_'+k,v)
actions={'add':circ(12,12,9)+' '+line(7,12,17,12)+' '+line(12,7,12,17),'keypad':' '.join(rr(x-1.5,y-1.5,x+1.5,y+1.5,.6) for y in [5,12,19] for x in [5,12,19]),'back':line(15,5,8,12)+' '+line(8,12,15,19),'download':line(12,3,12,16)+' M7,11 L12,16 L17,11 M4,17 V21 H20 V17','close':line(7,7,17,17)+' '+line(17,7,7,17),'search':circ(10,10,6)+' '+line(14.5,14.5,21,21),'bell':'M5,17 L7,14 V9 C7,2 17,2 17,9 V14 L19,17 Z '+arc(9,17,15,22,0,180)+' '+line(12,2,12,3),'pin':'M7,3 H17 L15,6 V11 L19,15 H5 L9,11 V6 Z '+line(12,15,12,22)}
for k,v in actions.items():save('action_'+k,v,width=1.6 if k in ['add','keypad'] else (1.7 if k in ['download','close','back'] else 1.8),fill=k=='keypad')
apps={
'walkie':rr(7,9,25,30,4)+' '+line(10,9,7,1)+' '+line(19,9,19,6)+' '+line(22,9,22,6)+' '+rr(11,13,21,18,1)+' '+' '.join(line(12,22+i*2.5,20,22+i*2.5) for i in range(3))+' '+line(25,14,27,14)+' '+line(25,19,27,19),
'social':circ(12,10,5)+' '+arc(2,19,22,38,180,180)+' '+circ(25,12,3)+' '+arc(22,19,32,31,210,130),
'browser':circ(16,16,13)+' '+oval(10,3,22,29)+' '+line(3,16,29,16)+' '+arc(4,6,28,20,0,180),
'youtube':rr(2,6,30,26,6)+' M13,11 L22,16 L13,21 Z',
'reader':rr(5,2,24,30,3)+' '+line(9,8,20,8)+' '+line(9,13,18,13)+' '+line(9,18,14,18)+' '+arc(19,15,32,29,-60,120)+' '+arc(21,18,28,26,-60,120),
'notes':rr(5,3,27,29,3)+' '+line(10,10,23,10)+' '+line(10,16,23,16)+' '+line(10,22,19,22),
'annotation':rr(3,5,27,27,3)+' '+circ(10,12,2.5)+' M5,24 L13,16 L19,22 M17,24 L26,15 L30,19 L21,28 L16,29 Z '+line(24,17,28,21),
'rss':arc(-13,5,27,45,270,90)+' '+arc(-4,14,18,36,270,90)+' '+circ(7,25,2)}
for k,v in apps.items():save('app_'+k,v,32,1.8 if k=='walkie' else 1.7)
print('wrote',len(list(root.glob('ref_*.xml'))),'reference vector artwork assets')

help_icons = {
 'permissions': 'M12,2 L21,6 L19,15 Q17,20 12,22 Q7,20 5,15 L3,6 Z '+line(8,12,11,15)+' '+line(11,15,16,9),
 'sim': 'M5,3 H14 L19,8 V21 H5 Z '+rr(8,11,16,18,1),
 'mobile': line(7,3,7,21)+' '+line(17,3,17,21)+' '+line(3,7,7,3)+' '+line(11,7,7,3)+' '+line(13,17,17,21)+' '+line(21,17,17,21),
 'retry': arc(4,4,20,20,25,290)+' M5,3 V9 H11',
 'licenses': rr(5,3,19,21,2)+' '+line(9,8,15,8)+' '+line(9,12,15,12)+' '+line(9,16,13,16),
}
for k,v in help_icons.items(): save('help_'+k,v)

# Original ComposeIcon kind 13 (group editor pencil).
save("action_edit", "M4,16 L4,20 L8,20 L20,8 L16,4 Z M14,6 L18,10", width=1.8)
