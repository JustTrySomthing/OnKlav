"""Synthetic XYZ test rasters, deliberately not real map data."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1]/'docs/fixtures/maps';root.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',18)
for x in range(2):
 for y in range(2):
  image=Image.new('RGB',(256,256),('#e4efd8','#d9eaf5','#f4e8d5','#eee0f3')[x*2+y]);d=ImageDraw.Draw(image)
  for line in range(0,257,64):d.line((line,0,line,256),fill='#86928a',width=2);d.line((0,line,256,line),fill='#86928a',width=2)
  d.line((0,32,256,224),fill='#ffffff',width=12);d.line((0,32,256,224),fill='#63839e',width=3)
  d.rectangle((12,79,244,169),fill='white',outline='#333333',width=2)
  d.text((22,88),f'DEMO z1/{x}/{y}',font=font,fill='#222222');d.text((22,118),'Not geographic data',font=font,fill='#222222')
  image.save(root/f'1-{x}-{y}.png')
print('Four synthetic map tiles generated')
