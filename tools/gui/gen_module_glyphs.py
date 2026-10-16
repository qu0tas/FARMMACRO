#!/usr/bin/env python3
"""Original Kaelix 16px item-like sprites. No external textures, vectors or antialiasing.
Atlas order must match ModuleCatalog.ALL (alpha26: 19 entries, 6x4 cells).
"""
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'src/main/resources/assets/farmmacro/textures/gui/modules/glyphs.png'
O='#18232f'; WHITE='#edffff'; IRON='#bacbd5'; GRAY='#657d93'
BROWN='#67402f'; WOOD='#a67142'; GOLD='#e5af44'; LIGHT='#ffe18a'
GREEN='#4b9b46'; LIME='#a9e16d'; CYAN='#44c6ca'; BLUE='#4385ce'; PURPLE='#a266d5'; RED='#db5954'
icons=[]
def sprite(name,fn):
 im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
 def box(x,y,w,h,c):d.rectangle((x,y,x+w-1,y+h-1),fill=c)
 def poly(points,c):d.polygon(points,fill=c)
 fn(d,box,poly);icons.append((name,im))
def farm(d,b,p):
 p([(2,8),(8,5),(14,8),(8,11)],LIME);p([(2,9),(8,12),(8,15),(2,12)],BROWN);p([(8,12),(14,9),(14,12),(8,15)],WOOD)
 for x,y in [(5,2),(9,0)]:
  b(x,y,1,7,GOLD);b(x-1,y+1,1,2,LIGHT);b(x+1,y,1,2,LIGHT);b(x+1,y+3,1,2,GOLD)
 b(3,12,2,1,WOOD)
def book(d,b,p):
 p([(1,3),(10,1),(14,4),(14,13),(5,15),(1,12)],O);p([(2,4),(10,2),(13,4),(5,6)],RED);b(2,5,3,7,'#853f49');p([(5,7),(12,5),(12,12),(5,14)],'#e7d7b3');b(6,8,5,1,WOOD);b(6,10,4,1,WOOD);b(9,3,2,3,LIGHT)
def clock(d,b,p):
 p([(5,1),(11,1),(15,5),(15,11),(11,15),(5,15),(1,11),(1,5)],O);p([(5,2),(11,2),(14,5),(14,11),(11,14),(5,14),(2,11),(2,5)],GOLD);b(4,4,8,8,'#e5edf0');b(7,4,2,5,GRAY);b(8,7,4,2,GRAY);b(4,3,5,1,LIGHT);b(7,1,2,2,LIGHT)
def compass(d,b,p):
 p([(5,1),(11,1),(15,5),(15,11),(11,15),(5,15),(1,11),(1,5)],O);p([(5,2),(11,2),(14,5),(14,11),(11,14),(5,14),(2,11),(2,5)],IRON);b(4,4,8,8,'#294153');p([(11,4),(8,9),(5,11),(7,6)],RED);p([(8,9),(5,11),(7,7)],WHITE);b(3,4,1,5,WHITE)
def sword(d,b,p):
 p([(10,1),(15,1),(15,6),(7,14),(2,9)],O);p([(11,2),(14,2),(14,5),(6,13),(3,10)],CYAN);p([(11,2),(14,2),(5,11),(4,10)],'#bcffff');p([(2,7),(9,14),(7,15),(1,9)],GOLD);b(2,12,2,3,BROWN);b(1,14,2,2,WOOD)
def eye(d,b,p):
 p([(5,2),(11,2),(15,6),(15,10),(11,14),(5,14),(1,10),(1,6)],O);p([(5,3),(11,3),(14,6),(14,10),(11,13),(5,13),(2,10),(2,6)],GREEN);p([(5,5),(11,5),(13,8),(11,11),(5,11),(3,8)],LIME);b(7,5,3,7,'#58366f');b(8,6,1,5,O);b(5,5,2,2,WHITE)
def shield(d,b,p):
 p([(1,1),(14,1),(14,9),(12,12),(8,15),(3,12),(1,9)],O);p([(2,2),(13,2),(13,9),(11,11),(8,14),(4,11),(2,9)],IRON);p([(4,4),(11,4),(11,9),(8,12),(4,9)],RED);b(7,4,2,5,LIGHT);b(7,10,2,1,LIGHT)
def camera(d,b,p):
 b(1,4,14,10,O);b(2,5,12,8,GRAY);b(3,3,5,2,O);b(4,3,3,2,IRON);b(3,6,3,1,WHITE);b(8,6,5,5,O);b(9,7,3,3,CYAN);b(9,7,2,1,WHITE);b(2,11,4,2,'#344b61')
def chest(d,b,p):
 p([(1,4),(11,1),(15,4),(15,13),(5,15),(1,12)],O);p([(2,5),(11,2),(14,4),(5,7)],GOLD);p([(2,6),(5,8),(5,14),(2,12)],BROWN);p([(6,8),(14,5),(14,12),(6,14)],WOOD);p([(6,10),(14,7),(14,8),(6,11)],BROWN);b(8,8,2,4,LIGHT);b(8,9,1,2,IRON)
def bottle(d,b,p):
 b(6,1,5,2,BROWN);b(6,3,5,4,IRON);p([(6,6),(10,6),(14,10),(14,13),(11,15),(4,15),(2,13),(2,10)],O);p([(6,7),(10,7),(13,10),(13,13),(10,14),(4,14),(3,12),(3,10)],GREEN);b(4,9,2,3,LIME);b(7,10,4,3,CYAN);b(7,4,1,3,WHITE)
def palette(d,b,p):
 p([(3,2),(12,2),(15,6),(15,11),(11,14),(3,14),(1,11),(1,5)],O);p([(3,3),(12,3),(14,6),(14,11),(10,13),(3,13),(2,10),(2,5)],WOOD)
 for x,y,c in [(4,4,RED),(8,3,LIGHT),(11,6,CYAN),(8,10,PURPLE)]:b(x,y,2,2,c)
 b(4,9,2,3,O)
def mapicon(d,b,p):
 p([(1,2),(5,1),(10,3),(14,1),(15,13),(10,15),(5,13),(2,15)],O);p([(2,3),(5,2),(10,4),(13,2),(14,12),(10,14),(5,12),(3,14)],'#ded4ad');b(5,3,1,9,'#b6a775');b(10,5,1,8,'#b6a775');d.line([(4,10),(7,10),(7,6),(11,6)],fill=GREEN,width=1);b(10,5,3,3,RED);b(11,6,1,1,LIGHT)
def scope(d,b,p):
 p([(1,10),(10,1),(15,6),(6,15)],O);p([(2,10),(10,2),(14,6),(6,14)],GOLD);p([(2,10),(5,7),(9,11),(6,14)],WOOD);p([(10,2),(14,6),(12,8),(8,4)],CYAN);b(10,3,2,2,WHITE);d.line([(5,10),(9,6)],fill=LIGHT,width=1)
def hat(d,b,p):
 p([(7,1),(9,1),(14,11),(15,12),(15,14),(1,14),(1,12)],O);p([(8,2),(13,11),(3,11)],PURPLE);b(2,12,12,1,'#c79afb');b(5,10,7,2,BLUE);b(8,9,2,2,LIGHT);b(7,4,1,3,'#dab5ff')
def alarm(d,b,p):
 b(2,12,12,3,O);b(3,12,10,2,GRAY);p([(5,2),(10,2),(13,6),(13,11),(3,11),(3,6)],O);p([(5,3),(10,3),(12,6),(12,10),(4,10),(4,6)],RED);b(5,4,2,4,'#ffb0a0');b(1,1,2,3,GOLD);b(13,1,2,3,GOLD)
def keys(d,b,p):
 b(1,4,14,10,O);b(2,5,12,8,GRAY)
 for y in [6,9]:
  for x in [3,6,9,12]:b(x,y,2,2,IRON);b(x,y,1,1,WHITE)
 b(5,12,6,1,IRON)
def scroll(d,b,p):
 b(3,2,10,12,O);b(4,3,8,10,'#dfcea7');b(1,1,11,3,WOOD);b(2,1,9,1,LIGHT);b(5,12,10,3,WOOD);b(6,12,8,1,LIGHT);b(6,5,4,1,GRAY);b(6,7,4,1,GRAY);b(6,9,3,1,GRAY)
def bed(d,b,p):
 p([(1,8),(10,3),(15,6),(15,11),(6,15),(1,12)],O);p([(2,8),(10,4),(14,6),(6,11)],BLUE);p([(2,9),(6,12),(14,7),(14,10),(6,14),(2,11)],WOOD);p([(3,7),(5,6),(9,8),(6,10)],WHITE);b(2,12,2,3,BROWN);b(12,10,2,3,BROWN)
def repeater(d,b,p):
 p([(1,8),(10,3),(15,6),(15,11),(6,15),(1,12)],O);p([(2,8),(10,4),(14,6),(6,11)],IRON);p([(2,10),(6,12),(14,8),(14,10),(6,14),(2,12)],GRAY);b(5,7,2,4,BROWN);b(5,6,2,2,RED);b(10,5,2,4,BROWN);b(10,4,2,2,RED);b(8,8,2,1,RED)
for name,fn in [('Farms',farm),('Recorder',book),('Session',clock),('Navigation',compass),('Combat',sword),('Zealots',eye),('Combat panic',shield),('Camera',camera),('HUD',chest),('XP / economy',bottle),('Appearance',palette),('Route',mapicon),('View / window',scope),('Cosmetics',hat),('Garden panic',alarm),('Keybinds',keys),('Configs',scroll),('Schedule',bed),('Performance',repeater)]:sprite(name,fn)
assert len(icons)==19 and len({im.tobytes() for _,im in icons})==19
atlas=Image.new('RGBA',(384,256))
for i,(_,im) in enumerate(icons):atlas.paste(im.resize((64,64),Image.Resampling.NEAREST),(i%6*64,i//6*64))
assert set(atlas.getchannel('A').getdata())=={0,255}
OUT.parent.mkdir(parents=True,exist_ok=True);atlas.save(OUT)
# Optional authored-sprite QA sheet. Not a Minecraft screenshot.
if __name__=='__main__':
 import argparse
 ap=argparse.ArgumentParser();ap.add_argument('--preview');args=ap.parse_args()
 if args.preview:
  sheet=Image.new('RGB',(960,400),'#151b24');d=ImageDraw.Draw(sheet)
  for i,(name,im) in enumerate(icons):
   x=i%5*192;y=i//5*100;d.rectangle((x+4,y+4,x+186,y+94),fill='#232e3c');sheet.paste(im.resize((64,64),Image.Resampling.NEAREST),(x+8,y+8),im.resize((64,64),Image.Resampling.NEAREST));sheet.paste(im.resize((28,28),Image.Resampling.NEAREST),(x+145,y+24),im.resize((28,28),Image.Resampling.NEAREST));d.text((x+10,y+78),name,fill='#e8f0f7')
  sheet.save(args.preview)
 print(f'{len(icons)} original pixel sprites → {OUT}')
