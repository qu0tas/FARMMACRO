#!/usr/bin/env python3
"""Generate early-loading bitmap art from bundled Kaelix/Foxy assets. Pillow + numpy."""
from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import numpy as np
root=Path(__file__).resolve().parents[2]
folder=root/'src/main/resources/assets/farmmacro/textures/gui/loading';folder.mkdir(parents=True,exist_ok=True)
font_path=next((p for p in ['/usr/share/fonts/msttcore/arialbd.ttf','/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf'] if Path(p).exists()),'DejaVuSans-Bold.ttf')
props=dict(line.split('=',1) for line in (root/'gradle.properties').read_text().splitlines() if '=' in line and not line.lstrip().startswith('#'))
# Wordmark uses logo colours and never depends on Minecraft Font during bootstrap.
mask=Image.new('L',(512,128));d=ImageDraw.Draw(mask);font=ImageFont.truetype(font_path,92);d.text((256,54),'KAELIX',font=font,fill=255,anchor='mm')
arr=np.zeros((128,512,4),dtype=np.uint8)
for x in range(512):
 t=x/511;rgb=np.array([111,157,255])*(1-t)+np.array([225,240,255])*t
 arr[:,x,:3]=rgb.astype(np.uint8)
arr[:,:,3]=np.asarray(mask);word=Image.fromarray(arr);dw=ImageDraw.Draw(word);dw.text((256,113),'FARM  /  FLOW  /  FEVER',font=ImageFont.truetype(font_path,16),fill=(116,136,178,255),anchor='mm');word.save(folder/'word.png',optimize=True)
labels=['ЗАГРУЗКА РЕСУРСОВ','НАВОДИМ ПОРЯДОК','ПОЧТИ ГОТОВО','ГОТОВО',f"KAELIX {props['mod_version']}  /  MINECRAFT {props['minecraft_version']}",'FOXY НА ПОСТУ','CROP FEVER CLUB']
im=Image.new('RGBA',(512,32*len(labels)));d=ImageDraw.Draw(im)
for i,text in enumerate(labels):d.text((256,i*32+16),text,font=ImageFont.truetype(font_path,24 if i<4 else 20),fill=(141,163,211,255) if i<4 else (91,111,156,255),anchor='mm')
im.save(folder/'labels.png',optimize=True)
im=Image.new('RGBA',(16*11,24));d=ImageDraw.Draw(im)
for i,ch in enumerate('0123456789%'):d.text((i*16+8,12),ch,font=ImageFont.truetype(font_path,19),fill=(225,237,255,255),anchor='mm')
im.save(folder/'digits.png',optimize=True)
# No screamer or sound: only a short loop of small Foxy frames.
im=Image.new('RGBA',(1024,320))
for i,n in enumerate([4,5,6,7,8,7,6,5]):
 src=Image.open(root/f'src/main/resources/assets/farmmacro/textures/gui/foxy/f{n:02d}.png').convert('RGBA')
 a=np.asarray(src).copy();rgb=a[:,:,:3].astype(float);ex=rgb[:,:,1]-np.maximum(rgb[:,:,0],rgb[:,:,2]);green=ex>35
 a[:,:,3]=np.where(green,np.clip(a[:,:,3].astype(float)*(1-np.clip((ex-35)/60,0,1)),0,255),a[:,:,3]).astype(np.uint8)
 a[:,:,1]=np.where(green,np.minimum(a[:,:,1],np.maximum(a[:,:,0],a[:,:,2])+8),a[:,:,1]);src=Image.fromarray(a)
 # Fixed crop prevents camera jitter from per-frame bounding-box changes.
 src=src.crop((120,0,510,245)).resize((256,160),Image.Resampling.LANCZOS)
 im.paste(src,((i%4)*256,(i//4)*160))
im.save(folder/'foxy.png',optimize=True)
print('Loading art generated: word, labels, digits, Foxy atlas')
