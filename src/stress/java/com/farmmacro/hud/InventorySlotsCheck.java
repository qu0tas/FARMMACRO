package com.farmmacro.hud;
import java.util.*;
public final class InventorySlotsCheck {
 public static void main(String... args){
  Set<Integer> inv=new HashSet<>(),menu=new HashSet<>();
  for(int row=0;row<4;row++)for(int col=0;col<9;col++){
   int i=InventorySlots.inventoryIndex(row,col),m=InventorySlots.menuIndex(row,col);
   if(!inv.add(i)||!menu.add(m))throw new IllegalStateException("duplicate HUD slot "+row+":"+col);
   if(i!=(row==3?col:9+row*9+col)||m!=(row==3?36+col:i))throw new IllegalStateException("wrong slot");
  }
  if(inv.size()!=36||!inv.containsAll(java.util.stream.IntStream.range(0,36).boxed().toList()))throw new IllegalStateException("missing inventory slot");
  if(menu.size()!=36||!menu.containsAll(java.util.stream.IntStream.range(9,45).boxed().toList()))throw new IllegalStateException("missing menu slot");
  for(int[] v:new int[][]{{-1,0},{4,0},{0,-1},{0,9}}){try{InventorySlots.inventoryIndex(v[0],v[1]);throw new IllegalStateException("invalid cell accepted");}catch(IllegalArgumentException expected){}}
  for(float scale:new float[]{.5f,1f,1.5f,2f}){
   var pose=new org.joml.Matrix3x2f().translation(120,70).scale(scale,scale);
   var a=HudModelBounds.project(pose,174,18,238,99,27);
   if(a.left()!=(int)Math.floor(120+174*scale)||a.top()!=(int)Math.floor(70+18*scale)
       ||a.right()!=(int)Math.ceil(120+238*scale)||a.bottom()!=(int)Math.ceil(70+99*scale)
       ||a.size()!=Math.round(27*scale))throw new IllegalStateException("model HUD transform: "+a);
   pose=new org.joml.Matrix3x2f().translation(203,44).scale(scale,scale);
   var b=HudModelBounds.project(pose,174,18,238,99,27);
   if(b.left()-a.left()!=83||b.top()-a.top()!=-26||b.size()!=a.size())throw new IllegalStateException("model must follow drag");
   var merged=HudModelBounds.project(pose,174,218,238,299,27);
   if(merged.top()-b.top()!=Math.round(200*scale))throw new IllegalStateException("merged model offset");
  }
  var identity=HudModelBounds.project(new org.joml.Matrix3x2f(),1,2,11,22,27);
  if(identity.left()!=1||identity.top()!=2||identity.size()!=27)throw new IllegalStateException("identity model transform");
  System.out.println("InventorySlotsCheck: OK (36 unique storage/hotbar indices, 36 unique menu indices, invalid cells rejected; model drag/scale/merged projection checked; not a rendered GUI test)");
 }
}
