package com.farmmacro.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A settings entry, deliberately without a controller toggle or start action. */
final class ModuleCardRow extends Rows.Row {
    private final ModuleCatalog.Module module;
    private final Runnable open;
    private final java.util.function.BooleanSupplier focused;
    private final int icon;
    ModuleCardRow(ModuleCatalog.Module module,Runnable open,java.util.function.BooleanSupplier focused) {
        this.module=module;this.open=open;this.focused=focused;
        int index=ModuleCatalog.ALL.indexOf(module);
        icon=index>=17?index+1:index; // Atlas17 (retired schedule) is skipped.
    }
    @Override public String searchText() { return module.title()+" "+module.description(); }
    @Override int height(Rows.Ctx c,int w){return MenuLayout.CARD_HEIGHT;}
    @Override void render(Rows.Ctx c,GuiGraphicsExtractor g,int x,int y,int w,int mx,int my,boolean hover){
        boolean focus=focused.getAsBoolean();int yy=y+4;
        Ui.roundBordered(g,x,yy,w,MenuLayout.CARD_HEIGHT-8,7,hover?Ui.CARD_HOVER:Ui.CARD,focus?Ui.ACCENT:hover?Ui.lerp(Ui.BORDER,Ui.SUB,.35f):Ui.BORDER);
        Ui.round(g,x+12,yy+12,26,26,5,Ui.lerp(Ui.CARD,Ui.ACCENT,.08f));ModuleGlyphs.draw(g,icon,x+13,yy+13,24);
        int tx=x+48,available=Math.max(8,w-72);
        Ui.text(g,c.font(),Ui.ellipsize(c.font(),module.title(),available),tx,yy+13,Ui.TEXT);
        Ui.text(g,c.font(),module.badge().equals("ALPHA")?"ALPHA":"Настройки",tx,yy+27,module.badge().equals("ALPHA")?Ui.WARN:Ui.SUB);
        Ui.text(g,c.font(),"›",x+w-17,yy+16,hover||focus?Ui.ACCENT_HI:Ui.SUB);
        String rest=module.description();int width=w-24;String first=c.font().plainSubstrByWidth(rest,width);
        if(first.length()<rest.length()){int space=first.lastIndexOf(' ');if(space>0)first=first.substring(0,space);}
        Ui.text(g,c.font(),first,x+12,yy+49,Ui.SUB);
        rest=rest.substring(first.length()).strip();if(!rest.isEmpty())Ui.text(g,c.font(),Ui.ellipsize(c.font(),rest,width),x+12,yy+62,Ui.SUB);
        if(hover){c.hand();c.tooltip(module.title()+" — "+module.description()+" Открытие не запускает модуль.");}
    }
    @Override boolean click(Rows.Ctx c,double mx,double my,int button) {
        if(button!=0)return false;c.clickSound();open.run();return true;
    }
}
