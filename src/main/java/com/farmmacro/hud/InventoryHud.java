package com.farmmacro.hud;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

/** Read-only 27 storage + 9 hotbar slots, distinct render coordinates, independent player preview. */
final class InventoryHud {
    static int draw(GuiGraphicsExtractor g,Minecraft mc,int x,int y,int w) {
        if((!ModConfig.INSTANCE.hudInventory&&!FarmHud.editing()&&!FarmHud.pinned("inventory"))||mc.player==null)return 0;
        boolean model=ModConfig.INSTANCE.hudInventoryPlayer&&w>=244;
        int cell=Math.min(18,Math.max(3,(w-16-(model?64:0))/9));
        int h=30+cell*4;
        if(g==null)return h;
        FarmHud.panel(g,x,y,w,h,Ui.ACCENT);
        Ui.text(g,mc.font,Ui.ellipsize(mc.font,"ИНВЕНТАРЬ · 27 + 9",w-16),x+8,y+6,StatsHud.fg());
        var inventory=mc.player.getInventory();
        for(int row=0;row<4;row++)for(int col=0;col<9;col++){
            int px=x+8+col*cell,py=y+20+row*cell+(row==3?4:0);
            boolean selected=row==3&&col==inventory.getSelectedSlot();
            Ui.roundBordered(g,px,py,cell-1,cell-1,2,0x503b4655,selected?Ui.ACCENT:0x607e8da5);
        }
        // One shared grid transform and unique item positions; no 36 identical local (0,0) item states.
        g.nextStratum();var pose=g.pose();pose.pushMatrix();
        try{
            pose.translate(x+9,y+21);float scale=(cell-2)/16f;pose.scale(scale,scale);
            for(int row=0;row<4;row++)for(int col=0;col<9;col++){
                int index=InventorySlots.inventoryIndex(row,col);
                // Vanilla player-menu indices: storage9..35, hotbar36..44. Read only, even with a chest open.
                int menuSlot=InventorySlots.menuIndex(row,col);
                ItemStack stack=mc.player.inventoryMenu.getSlot(menuSlot).getItem();
                if(stack.isEmpty())stack=inventory.getItem(index);
                if(stack.isEmpty())continue;
                int ix=Math.round(col*cell/scale),iy=Math.round((row*cell+(row==3?4:0))/scale);
                ItemStack item=stack;
                com.farmmacro.util.Guard.run("hud/inventory-item",()->{g.item(item,ix,iy,index);g.itemDecorations(mc.font,item,ix,iy);});
            }
        }finally{pose.popMatrix();}
        if(model){int left=x+12+cell*9,top=y+18;
            com.farmmacro.util.Guard.run("hud/inventory-player",()->{
                // Entity PiP ignores g.pose(): explicitly project the current HUD
                // translation/scale, including the merged-widget offset, once.
                var bounds=HudModelBounds.project(g.pose(),left,top,x+w-6,y+h-3,27);
                InventoryScreen.extractEntityInInventoryFollowsMouse(g,bounds.left(),bounds.top(),bounds.right(),bounds.bottom(),bounds.size(),0f,bounds.centerX(),bounds.centerY(),mc.player);
            });
        }
        return h;
    }
    private InventoryHud(){}
}
