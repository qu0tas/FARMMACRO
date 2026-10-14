#!/usr/bin/env python3
"""Offline raster composition matching KaelixLoading geometry; NOT a Minecraft screenshot."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,math
root=Path(__file__).resolve().parents[2];assets=root/'src/main/resources/assets/farmmacro/textures/gui'
output=Path('/data/loading-previews');output.mkdir(parents=True,exist_ok=True)

def draw(w,h,elapsed,progress,memes=True):
 canvas=Image.new('RGBA',(960,540),(9,13,23,255));d=ImageDraw.Draw(canvas)
 for y in range(0,540,12):
  light=.045*math.exp(-((y-540*.3)/(540*.35))**2);a=Image.new('RGBA',canvas.size);ad=ImageDraw.Draw(a);ad.rectangle((0,y,960,y+11),fill=(88,126,255,round(light*255)));canvas.alpha_composite(a)
 a=Image.new('RGBA',canvas.size);ad=ImageDraw.Draw(a)
 for x in range(20,960,80):ad.line((x,20,x,520),fill=(25,38,64,76))
 for y in range(20,540,80):ad.line((20,y,940,y),fill=(25,38,64,76))
 canvas.alpha_composite(a)
 def sprite(filename,box,crop=None,opacity=1,angle=0):
  im=Image.open(assets/filename).convert('RGBA');im=im.crop(crop) if crop else im;im=im.resize((box[2],box[3]),Image.Resampling.LANCZOS)
  if opacity!=1:im.putalpha(im.getchannel('A').point(lambda v:round(v*opacity)))
  if angle:im=im.rotate(-math.degrees(angle),Image.Resampling.BICUBIC,expand=True);x=round(box[0]+box[2]/2-im.width/2);y=round(box[1]+box[3]/2-im.height/2)
  else:x,y=box[:2]
  canvas.alpha_composite(im,(x,y))
 frame=int((elapsed%6000)*96/6000);u=(frame%12)*64;v=(frame//12)*64
 sprite('logo/k_atlas.png',(424,50,112,112),(u,v,u+64,v+64))
 sprite('loading/word.png',(302,176,356,89))
 stage=3 if progress>=1 else 2 if progress>=.85 else 1 if progress>=.3 else 0
 sprite('loading/labels.png',(325,303,310,20),(0,stage*32,512,stage*32+32))
 d=ImageDraw.Draw(canvas);d.rectangle((250,342,709,349),fill=(26,37,66));end=250+round(progress*460)
 if end>250:d.rectangle((250,342,end-1,349),fill=(88,126,255));d.rectangle((max(250,end-16),342,end-1,349),fill=(156,199,255))
 digits=str(int(progress*100))+'%';left=480-len(digits)*8
 for i,c in enumerate(digits):n=10 if c=='%' else int(c);sprite('loading/digits.png',(left+i*16,370,16,24),(n*16,0,n*16+16,24))
 sprite('loading/labels.png',(300,486,360,23),(0,128,512,160))
 if memes:
  f=int(elapsed%1600/200);u=f%4*256;v=f//4*160;sprite('loading/foxy.png',(36,296,174,109),(u,v,u+256,v+160),.9)
  sprite('loading/labels.png',(44,438,156,18),(112,160,400,192))
  meta=json.loads((assets/'crop_fever/being.json').read_text());dur=meta['frameDurationsMs']
  for i,(x,y,size) in enumerate([(775,320,88),(875,380,80)]):
   t=(elapsed+i*1000)%7130;f=0
   while f<len(dur)-1 and t>=dur[f]:t-=dur[f];f+=1
   bob=math.sin(elapsed/650+i)*9;angle=math.sin(elapsed/1400)*.45 if i else 0;u=f%10*176;v=f//10*176
   sprite('crop_fever/being.png',(x-size//2,round(y+bob-size/2),size,size),(u,v,u+176,v+176),.9,angle)
  sprite('loading/labels.png',(728,438,196,22),(112,192,400,224))
 scale=min(w/960,h/540);out=Image.new('RGBA',(w,h),(9,13,23,255));scaled=canvas.resize((round(960*scale),round(540*scale)),Image.Resampling.LANCZOS);out.alpha_composite(scaled,(round((w-scaled.width)/2),round((h-scaled.height)/2)));return out.convert('RGB')
for name,w,h,t,p,memes in [('wide',960,540,2200,.64,True),('classic',640,480,900,.21,True),('small',480,270,4200,.92,True),('ultrawide',1280,540,3200,.77,True),('clean',960,540,3000,.42,False),('complete',960,540,5500,1,True)]:
 draw(w,h,t,p,memes).save(output/f'{name}.png')
 print(name,w,h)
