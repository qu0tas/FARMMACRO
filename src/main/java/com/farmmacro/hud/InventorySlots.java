package com.farmmacro.hud;
/** Read-only storage/hotbar mapping; no inventory mutation or renderer dependency. */
public final class InventorySlots {
 public static int inventoryIndex(int row,int col){
  if(row<0||row>3||col<0||col>8)throw new IllegalArgumentException("inventory HUD cell");
  return row==3?col:9+row*9+col;
 }
 public static int menuIndex(int row,int col){int index=inventoryIndex(row,col);return row==3?36+col:index;}
 private InventorySlots(){}
}
