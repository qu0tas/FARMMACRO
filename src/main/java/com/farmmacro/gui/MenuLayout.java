package com.farmmacro.gui;
/** Shared drawing/hit geometry, pure and bounded. All coordinates are GUI units. */
public final class MenuLayout {
 public static final int HEADER=48,GUTTER=12,CARD_HEIGHT=92;
 public record Rect(int x,int y,int w,int h){public boolean contains(double px,double py){return px>=x&&py>=y&&px<x+w&&py<y+h;}}
 public record Window(int x,int y,int w,int h,int side){
  public Rect content(){return new Rect(x+side+12,y+HEADER+8,Math.max(1,w-side-30),Math.max(1,h-HEADER-20));}
  public Rect close(){return new Rect(x+w-30,y+12,20,20);}
  public int categoryStride(){return Math.max(10,Math.min(34,(h-HEADER-22)/5));}
  public Rect category(int index){return new Rect(x+6,y+HEADER+6+index*categoryStride(),side-12,categoryStride()-4);}
 }
 public static Window window(int screenW,int screenH,int offsetX,int offsetY){
  int margin=screenW<400||screenH<260?6:12,w=Math.max(80,Math.min(780,screenW-2*margin)),h=Math.max(100,Math.min(460,screenH-2*margin));
  w=Math.min(Math.max(1,screenW),w);h=Math.min(Math.max(1,screenH),h);
  int x=Math.clamp((screenW-w)/2+offsetX,0,Math.max(0,screenW-w)),y=Math.clamp((screenH-h)/2+offsetY,0,Math.max(0,screenH-h));
  return new Window(x,y,w,h,w<540?40:148);
 }
 public static int columns(int contentWidth){return contentWidth>=480?2:1;}
 public static Rect card(int index,int contentWidth,int top){int cols=columns(contentWidth),w=(contentWidth-(cols-1)*GUTTER)/cols;return new Rect(index%cols*(w+GUTTER),top+index/cols*CARD_HEIGHT,w,CARD_HEIGHT);}
 public static int nextSelection(int current,int size,int columns,int key){if(size<1)return 0;int step=switch(key){case 264->columns;case 265->-columns;case 263->-1;case 262->1;default->0;};return Math.clamp(current+step,0,size-1);}
 private MenuLayout(){}
}
