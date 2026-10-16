package com.farmmacro.visitor;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Packet-only observations, separate from vanilla click prediction; only the current transaction. */
public final class VisitorMenu {
    private static AbstractContainerMenu menu;private static long revision;
    private static final Map<Integer,ItemStack> items=new HashMap<>();
    private static final Map<Integer,Long> stamps=new HashMap<>();
    private static boolean prepare(Minecraft mc,int id){
        if(!mc.isSameThread()||mc.player==null||!Visitors.INSTANCE.active())return false;
        var m=mc.player.containerMenu;if(m==mc.player.inventoryMenu||id!=m.containerId&&id!=-1)return false;
        if(menu!=m){reset();menu=m;}return true;
    }
    public static void slot(Minecraft mc,int id,int index,ItemStack stack){
        if(!prepare(mc,id)||index<-1||index>=menu.slots.size())return;
        items.put(index,stack.copy());stamps.put(index,++revision);
    }
    public static void content(Minecraft mc,int id,List<ItemStack> values,ItemStack cursor){
        if(!prepare(mc,id)||values.size()!=menu.slots.size())return;revision++;
        for(int i=0;i<values.size();i++){items.put(i,values.get(i).copy());stamps.put(i,revision);}
        items.put(-1,cursor.copy());stamps.put(-1,revision);
    }
    public static ItemStack item(AbstractContainerMenu m,int index){return menu==m?items.getOrDefault(index,ItemStack.EMPTY):ItemStack.EMPTY;}
    public static long stamp(AbstractContainerMenu m,int index){return menu==m?stamps.getOrDefault(index,0L):0L;}
    public static long revision(AbstractContainerMenu m){return menu==m?revision:0;}
    public static void reset(){menu=null;revision=0;items.clear();stamps.clear();}
    public static long inventoryRevision;
    public static void inventory(Minecraft mc){if(mc.isSameThread()&&mc.player!=null&&Visitors.INSTANCE.active())inventoryRevision++;}
    private VisitorMenu(){}
}
