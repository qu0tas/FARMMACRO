package com.farmmacro.release;
import com.farmmacro.gui.MenuLayout;
/** A few pure bounds/hit/keyboard cases, not a renderer or a game test. */
public final class UiLayoutQuickCheck {
 private static void ok(boolean b){if(!b)throw new IllegalStateException("UI geometry");}
 public static void main(){
  for(int[] size:new int[][]{{320,240},{480,270},{960,540},{1920,1080}}){
   var w=MenuLayout.window(size[0],size[1],0,0);var c=w.content();ok(w.x()>=0&&w.y()>=0&&w.x()+w.w()<=size[0]&&w.y()+w.h()<=size[1]);ok(c.x()>w.x()+w.side()&&c.y()>=w.y()+MenuLayout.HEADER&&c.x()+c.w()<w.x()+w.w()&&c.y()+c.h()<w.y()+w.h());
   for(int i=0;i<5;i++){var box=w.category(i);ok(box.x()>=w.x()&&box.x()+box.w()<c.x()&&box.y()+box.h()<=w.y()+w.h());ok(box.contains(box.x(),box.y())&&!box.contains(box.x()+box.w(),box.y()));if(i>0)ok(w.category(i-1).y()+w.category(i-1).h()<box.y());}
   int columns=MenuLayout.columns(c.w());var a=MenuLayout.card(0,c.w(),84);var b=MenuLayout.card(1,c.w(),84);ok(a.x()+a.w()<=c.w()&&b.x()+b.w()<=c.w());ok(columns==2?a.x()+a.w()<b.x():a.y()+a.h()<=b.y());
   var moved=MenuLayout.window(size[0],size[1],10000,-10000);ok(moved.x()+moved.w()==size[0]&&moved.y()==0);var close=w.close();ok(close.x()>=c.x()&&close.x()+close.w()<w.x()+w.w()&&close.y()+close.h()<c.y());
  }
  ok(MenuLayout.nextSelection(0,18,2,264)==2);ok(MenuLayout.nextSelection(0,18,2,265)==0);ok(MenuLayout.nextSelection(17,18,2,264)==17);ok(MenuLayout.nextSelection(4,18,2,263)==3);ok(MenuLayout.nextSelection(4,18,2,262)==5);
  System.out.println("UiLayoutQuickCheck: PASS (four GUI sizes, shared bounds/hits, 2/1-column cards, keyboard; no game)");
 }
 public static void main(String[] args){if(args.length==3&&args[0].equals("--snapshot")){
  var w=MenuLayout.window(Integer.parseInt(args[1]),Integer.parseInt(args[2]),0,0);var c=w.content();System.out.print("{\"x\":"+w.x()+",\"y\":"+w.y()+",\"w\":"+w.w()+",\"h\":"+w.h()+",\"side\":"+w.side()+",\"cx\":"+c.x()+",\"cy\":"+c.y()+",\"cw\":"+c.w()+",\"ch\":"+c.h()+",\"columns\":"+MenuLayout.columns(c.w())+",\"stride\":"+w.categoryStride()+"}");
 }else main();}
 private UiLayoutQuickCheck(){}
}
