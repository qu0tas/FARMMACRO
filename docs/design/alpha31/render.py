"""Offline design projections, not screenshots of Minecraft. Geometry exported by real MenuLayout.
No runtime game classes. Fonts/control/value rendering are approximations; see README.md.
"""
from pathlib import Path
import json,re,html
D=Path(__file__).resolve().parent;R=D.parents[2]
mods=re.findall(r'm\((\d+),(\d+),"([^"]+)","([^"]+)","([^"]+)"',(R/'src/main/java/com/farmmacro/gui/ModuleCatalog.java').read_text())
cats=['Все модули','Фермы','Combat','Внешний вид','Система']
C={'bg':'#15181e','side':'#101319','card':'#1c2028','field':'#10141b','border':'#343d4b','text':'#f0f3f8','sub':'#afbacb','dim':'#8896aa','accent':'#6da9f7','hi':'#a4cbff'}
# Guard against drift in the authored default palette.
u=(R/'src/main/java/com/farmmacro/gui/Ui.java').read_text().lower()
for color in C.values():assert '0xff'+color[1:] in u,color

def rect(x,y,w,h,body='',css='',cl=''):
 return f'<div class="box {cl}" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px;{css}">{body}</div>'
def text(x,y,w,label,color=None,size=9):
 return rect(x,y,w,13 if size==9 else 16,html.escape(label),f'font-size:{size}px;color:{color or C["text"]};white-space:nowrap;overflow:hidden;text-overflow:ellipsis;')
def icon(i,x,y,size=24):
 i=i+1 if i>=17 else i
 return rect(x,y,size,size,'',f'background-image:var(--atlas);background-size:{size*6}px {size*4}px;background-position:{-(i%6)*size}px {-(i//6)*size}px;image-rendering:pixelated;')
def shell(kind,narrow=False,empty=False):
 l=json.loads((D/('layout-narrow.json' if narrow else 'layout-wide.json')).read_text());sw,sh,scale=(320,240,2) if narrow else (960,540,1.6)
 x,y,w,h,side=[l[k] for k in ['x','y','w','h','side']];cx,cy,cw,ch=[l[k] for k in ['cx','cy','cw','ch']]
 parts=[rect(x,y,w,h,'',f'background:{C["bg"]};border:1px solid {C["border"]};border-radius:3px;'),rect(x+1,y+1,side-1,h-2,'',f'background:{C["side"]};border-right:1px solid {C["border"]};')]
 parts+=[rect(x+10,y+12,20,20,'','background-image:var(--logo);background-size:240px 160px;image-rendering:pixelated;')]
 if side>60:parts+=[text(x+38,y+16,side-44,'KAELIX',C['hi']),text(x+38,y+30,side-44,'WORKSPACE',C['dim'],8)]
 active=3 if kind=='detail' else 0
 for i,name in enumerate(cats):
  yy=y+54+i*l['stride'];hh=l['stride']-4
  if i==active:parts+=[rect(x+6,yy,side-12,hh,'',f'background:#1c2c42;border-radius:3px;')]
  ix=x+6+(side-12-16)/2 if side<60 else x+14
  iy=yy+(hh-16)/2
  if i==0:
   for q in range(4):parts+=[rect(ix+(q%2)*8,iy+(q//2)*8,6,6,'',f'background:{C["hi"] if i==active else C["sub"]};border-radius:1px;')]
  else:parts+=[icon({1:0,2:4,3:8,4:16}[i],ix,iy,16)]
  if side>60:parts+=[text(x+37,yy+(hh-8)/2,side-47,name,C['text'] if i==active else C['sub'])]
 if side>60:parts+=[rect(x+12,y+h-40,side-24,1,'',f'background:{C["border"]};'),text(x+12,y+h-32,side-24,'Запуск — вручную',C['sub']),text(x+12,y+h-19,side-24,'v2.0.0-alpha.31',C['dim'])]
 title='HUD и редактор' if kind=='detail' else 'Модули'
 parts+=[text(cx,y+10,cw-(120 if kind=='detail' else 30),title,size=11.25),text(cx,y+31,cw-26,'Готов · маршрут' if kind=='detail' else f'Найдено: {0 if empty else 18} · запуск отдельно',C['sub']),text(x+w-25,y+17,14,'×',C['sub']),rect(cx,y+48,w+x-12-cx,1,'',f'background:{C["border"]};')]
 if kind=='detail':parts+=[rect(x+w-120,y+12,82,20,'Основные',f'background:{C["card"]};border:1px solid {C["border"]};border-radius:3px;text-align:center;line-height:19px;color:{C["sub"]};')]
 content=[]
 if kind=='detail':
  content+=[text(2,10,cw-4,'- HUD',C['sub'])]
  rows=[('HUD только по делу','toggle',True),('Стиль плашек','select','Kaelix / Slate'),('Инвентарь HUD','toggle',True),('Модель игрока в инвентаре HUD','toggle',True),('Панель статуса','toggle',True),('Навигатор','toggle',True),('Стрелка у прицела','toggle',True),('Полоска подозрительности','toggle',True),('Одна общая плашка','toggle',False),('Ширина плашек','select','190 px'),('Размер HUD','select','100 %'),('Переносить длинный текст','toggle',True)]
  for j,(label,typ,val) in enumerate(rows):
   yy=24+j*22;reserve=32 if typ=='toggle' else 164
   content+=[text(2,yy+7,cw-reserve-16,label),rect(0,yy+21,cw,1,'',f'background:{C["border"]};')]
   if typ=='toggle':
    xx=cw-20;content+=[rect(xx,yy+6,18,10,'',f'background:{C["accent"] if val else "#34343a"};border-radius:5px;'),rect(xx+(10 if val else 2),yy+8,6,6,'',f'background:{"#ffffff" if val else "#7a7a82"};border-radius:3px;')]
   else:content+=[text(cw-150,yy+7,148,'‹  '+str(val)+'  ›',C['hi'])]
 else:
  content+=[rect(2,3,cw-4,15,'',f'background:{C["field"]};border:1px solid {C["border"]};border-radius:2px;'),text(8,6,cw-24,'zzz' if empty else 'Поиск…',C['dim'])]
  for i,label in enumerate(['Редактор HUD','Все параметры']):content+=[rect(i*(cw/2+2),22,(cw-4)/2,18,label,f'background:{C["card"]};border-radius:2px;color:{C["sub"]};text-align:center;line-height:18px;')]
  content+=[text(8,47,cw-16,'Enter — настройки · запуск отдельно',C['sub'])]
  if empty:content+=[text(8,74,cw-16,'Модули не найдены.',C['sub']),text(8,90,cw-16,'Попробуй другое название или «Все параметры».',C['sub'])]
  else:
   columns=l['columns'];cardw=(cw-(columns-1)*12)/columns
   for i,(_,_,title,desc,badge) in enumerate(mods):
    xx=(i%columns)*(cardw+12);yy=66+(i//columns)*92
    q=[rect(0,4,cardw,84,'',f'background:{C["card"]};border:1px solid {C["accent"] if i==0 else C["border"]};border-radius:3px;'),rect(12,16,26,26,'',f'background:#222b39;border-radius:2px;'),icon(i,13,17),text(48,17,cardw-72,title),text(48,31,cardw-72,'ALPHA' if badge=='ALPHA' else 'Настройки','#ffb547' if badge=='ALPHA' else C['sub']),text(cardw-17,20,10,'›',C['hi'])]
    q+=[rect(12,53,cardw-24,29,html.escape(desc),f'color:{C["sub"]};line-height:13px;overflow:hidden;')]
    content+=[rect(xx,yy,cardw,92,''.join(q))]
 parts+=[rect(cx,cy,cw,ch,''.join(content),'overflow:hidden;')]
 # Projection of existing scroll clipping/fade. Detail is explicitly a scrolled-to-section view.
 if not empty:parts+=[rect(cx,cy+ch-8,cw,8,'',f'background:linear-gradient(transparent,{C["bg"]});'),rect(cx+cw+3,cy,3,ch,'',f'background:{C["border"]};border-radius:2px;'),rect(cx+cw+3,cy+(55 if kind=='detail' else 0),3,100 if not narrow else 24,'',f'background:{C["accent"]};border-radius:2px;')]
 name='narrow' if narrow else 'search' if empty else kind
 label='узкое окно · 320×240 GUI' if narrow else 'модуль HUD · прокручено к разделу HUD' if kind=='detail' else 'поиск без результатов' if empty else 'каталог модулей · 960×540 GUI'
 write(name,sw,sh,scale,''.join(parts),label)

def write(name,sw,sh,scale,body,label):
 css='''*{box-sizing:border-box}body{margin:0;background:#0a0d12;color:#afbacb;font-family:Arial,sans-serif}.caption{height:48px;padding:13px 24px;font-size:13px;border-bottom:1px solid #242e3d}.caption b{color:#d6e8ff;margin-right:16px}.stage{position:relative;overflow:hidden;background:radial-gradient(ellipse at 40% 0%,#182133,#0d1118 70%)}.scene{position:relative;transform-origin:top left;font-family:'Courier New',monospace;font-size:9px;line-height:10px}.box{position:absolute}.scene{--atlas:url('../../../src/main/resources/assets/farmmacro/textures/gui/modules/glyphs.png');--logo:url('../../../src/main/resources/assets/farmmacro/textures/gui/logo/k_atlas.png')}'''
 h=f'<!doctype html><html lang="ru"><meta charset="utf-8"><title>Kaelix — offline design projection</title><style>{css}</style><div class="caption"><b>KAELIX / C2</b>{html.escape(label)} · дизайн-проекция, не Minecraft</div><div class="stage" style="width:{sw*scale}px;height:{sh*scale}px"><div class="scene" style="width:{sw}px;height:{sh}px;transform:scale({scale})">{body}</div></div></html>'
 (D/f'{name}.html').write_text(h)

def hud():
 p=[]
 # Subtle checker background is a neutral contrast board, not a game/world screenshot.
 p+=[rect(0,0,800,440,'','background:repeating-conic-gradient(#26332e 0 25%,#293832 0 50%) 50% / 48px 48px;')]
 def card(x,y,w,title,rows,col,progress=None):
  h=24+len(rows)*11+(8 if progress is not None else 0)
  c=[rect(x,y,w,h,'','background:rgba(21,27,36,.78);border:1px solid rgba(178,192,208,.3);border-radius:3px;'),rect(x+1,y+4,2,12,'',f'background:{col};'),text(x+9,y+6,w-18,title,'#eff4fa'),rect(x+9,y+16,w-18,1,'','background:rgba(178,192,208,.16);')]
  for j,row in enumerate(rows):c+=[text(x+9,y+21+j*11,w-18,row,'#eff4fa' if j==0 else '#b2c0d0')]
  if progress is not None:
   yy=y+21+len(rows)*11+1;c+=[rect(x+9,yy,w-18,3,'','background:#334256;'),rect(x+9,yy,(w-18)*progress,3,'',f'background:{col};')]
  return c,h
 yy=16
 for title,rows,color,value in [('РАБОТАЕТ',['Farms / Маршрут','Точка 6 / 18 · проход 2'], '#67d8b4',.33),('Навык · Farming',['Уровень 42 • +24.5k XP','До уровня: 1.8m XP','60.0k XP/ч • ETA 30ч 00м'], '#e7bd73',.52),('Питомец',['[78] Elephant • LEGENDARY','До уровня 79: ≈ 150k XP','50k / 200k XP','Pets • снимок 12 с назад'],'#b8a5f6',.25)]:
  q,h=card(16,yy,230,title,rows,color,value);p+=q;yy+=h+8
 q,_=card(548,16,236,'Combat',['Zealots','Поиск цели','Combat XP +12.0k • 90.0k/ч'],'#b8a5f6');p+=q
 q,_=card(548,122,236,'Питомец',['XP неизвестен • открой меню Pets','Снимок XP хранится до 60 с'],'#b8a5f6');p+=q
 # Actual 27+9 grid geometry: unique cells, hotbar separate. Items/model intentionally absent in projection.
 x,y,w=16,330,244;p+=[rect(x,y,w,102,'','background:rgba(21,27,36,.78);border:1px solid rgba(178,192,208,.3);border-radius:3px;'),rect(x+1,y+4,2,12,'',f'background:{C["accent"]};'),text(x+8,y+6,w-16,'ИНВЕНТАРЬ · 27 + 9','#eff4fa')]
 for row in range(4):
  for col in range(9):p+=[rect(x+8+col*18,y+20+row*18+(4 if row==3 else 0),17,17,'','background:rgba(59,70,85,.3);border:1px solid '+(C['accent'] if row==3 and col==0 else 'rgba(126,141,165,.4)')+';')]
 p+=[text(x+178,y+33,59,'Модель',C['sub']),text(x+178,y+45,59,'в игре',C['sub']),text(300,360,480,'Макет: числа-примеры, не игровые данные.', '#e0eee7'),text(300,379,480,'Предметы и модель здесь не рендерились.', '#e0eee7')]
 write('hud',800,440,1.6,''.join(p),'Kaelix / Slate · карточки и состояния данных')

shell('home');shell('detail');shell('home',True);shell('home',empty=True);hud()
print('Generated 5 offline projections; real layout JSON, palette and authored pixel atlas. No Minecraft.')
