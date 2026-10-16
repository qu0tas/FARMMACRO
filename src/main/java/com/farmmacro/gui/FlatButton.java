package com.farmmacro.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Native focus/click/narration, own Kaelix drawing. */
public final class FlatButton extends Button {
    public FlatButton(int x,int y,int w,int h,String text,OnPress press) {
        super(x,y,w,h,Component.literal(text),press,DEFAULT_NARRATION);
    }
    @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
        var f=Minecraft.getInstance().font;
        Ui.roundBordered(g,getX(),getY(),getWidth(),getHeight(),5,isHoveredOrFocused()?Ui.CARD_HOVER:Ui.CARD,isFocused()?Ui.ACCENT:Ui.BORDER);
        Ui.textCentered(g,f,Ui.ellipsize(f,getMessage().getString(),getWidth()-12),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?Ui.TEXT:Ui.SUB);
    }
}
