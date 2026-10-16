package com.farmmacro.pest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Server packet values are kept apart from the predicted menu, including the carried cursor. */
public final class PestMenuEvidence {
    private static AbstractContainerMenu menu;private static long revision;
    private static final Map<Integer,ItemStack> values=new HashMap<>();private static final Map<Integer,Long> stamps=new HashMap<>();
    private static boolean prepare(Minecraft mc,int id){if(!mc.isSameThread()||mc.player==null||!PestTraps.INSTANCE.active())return false;var m=mc.player.containerMenu;if(id!=m.containerId&&id!=-1)return false;if(menu!=m){menu=m;revision=0;values.clear();stamps.clear();}return true;}
    public static void slot(Minecraft mc,int id,int index,ItemStack stack){if(!prepare(mc,id)||index<-1||index>=menu.slots.size())return;values.put(index,stack.copy());stamps.put(index,++revision);}
    public static void content(Minecraft mc,int id,List<ItemStack> items,ItemStack carried){if(!prepare(mc,id)||items.size()!=menu.slots.size())return;revision++;for(int i=0;i<items.size();i++){values.put(i,items.get(i).copy());stamps.put(i,revision);}values.put(-1,carried.copy());stamps.put(-1,revision);}
    public static long revision(AbstractContainerMenu m){return menu==m?revision:0;}
    public static long stamp(AbstractContainerMenu m,int index){return menu==m?stamps.getOrDefault(index,0L):0;}
    public static ItemStack observed(AbstractContainerMenu m,int index){return menu==m?values.getOrDefault(index,ItemStack.EMPTY):ItemStack.EMPTY;}
    public static void reset(){menu=null;revision=0;values.clear();stamps.clear();}
    private PestMenuEvidence(){}
}
