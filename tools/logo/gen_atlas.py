import numpy as np
from PIL import Image, ImageDraw, ImageFilter
C=64; SS=4; R=C*SS; COLS,ROWS=12,8; N=COLS*ROWS
def field(x,y,n,fmax,seed):
    r=np.random.default_rng(seed); v=0
    for _ in range(n):
        kx,ky=r.integers(-fmax,fmax+1,2)
        if kx==0 and ky==0: kx=1
        v=v+np.sin(kx*x+ky*y+r.uniform(0,6.28))/np.hypot(kx,ky)
    return v
stops=np.array([[0,20,30,110],[0.45,40,70,200],[0.7,60,120,250],[1.0,110,190,255],[1.3,200,235,255],[1.8,255,255,255]])
def tex(px,py):  # px,py in texture-period units*2pi
    wx=field(px,py,6,2,1); wy=field(px,py,6,2,2)
    xx=px+1.1*wx; yy=py+1.1*wy
    n1=field(xx,yy,8,3,3); n2=field(xx+0.5*wy,yy,8,5,4)
    rd=lambda n,s: np.exp(-(n/s)**2)
    v=0.75*rd(n1,0.3)+0.45*rd(n2,0.18); base=(field(xx,yy,5,2,5)+2.5)/5
    t=np.clip(0.45+0.35*base+v*0.9,0,1.8)
    return np.stack([np.interp(t,stops[:,0],stops[:,i]) for i in (1,2,3)],-1)
def kmask(size,pad=0.0):
    m=Image.new('L',(size,size),0); d=ImageDraw.Draw(m)
    sc=lambda pts:[(a*size/100,b*size/100) for a,b in pts]
    d.polygon(sc([(24,16),(42,16),(42,84),(24,84)]),255)
    d.polygon(sc([(58,16),(80,16),(52,50),(80,84),(58,84),(38,58),(38,42)]),255)
    return m
mask=kmask(R)
yy,xx=np.mgrid[0:R,0:R]/R   # cell units; texture period = 2 cells
def frame(f, size=C):
    o=2*(1-f/N)   # moves toward bottom-right, one period per loop
    px=(xx+o)/2*2*np.pi; py=(yy+o)/2*2*np.pi
    rgb=tex(px,py)
    m=np.asarray(mask,float)/255
    glow=np.asarray(mask.filter(ImageFilter.GaussianBlur(R*0.035)),float)/255
    edge=np.asarray(mask.filter(ImageFilter.FIND_EDGES).filter(ImageFilter.GaussianBlur(SS*0.6)),float)/255
    gcol=np.array([90,150,255.])
    a=np.maximum(m,glow*0.45)
    col=(rgb*m[...,None]+gcol*(glow*0.45*(1-m))[...,None])/np.maximum(a,1e-4)[...,None]
    col=col*(1-0.5*edge[...,None])+np.array([170,210,255.])*0.5*edge[...,None]
    img=Image.fromarray(np.dstack([np.clip(col,0,255),a*255]).astype(np.uint8),'RGBA')
    return img.resize((size,size),Image.LANCZOS)
atlas=Image.new('RGBA',(C*COLS,C*ROWS),(0,0,0,0))
for f in range(N): atlas.paste(frame(f),((f%COLS)*C,(f//COLS)*C))
atlas.save('src/main/resources/assets/farmmacro/textures/gui/logo/k_atlas.png',optimize=True)
# icon 128
ic=frame(10,R).resize((112,112),Image.LANCZOS)
bg=Image.new('RGBA',(128,128),(0,0,0,0)); d=ImageDraw.Draw(bg); d.rounded_rectangle((0,0,127,127),26,fill=(12,12,16,255))
bg.alpha_composite(ic,(8,8)); bg.save('src/main/resources/assets/farmmacro/icon.png')
print(atlas.size)
