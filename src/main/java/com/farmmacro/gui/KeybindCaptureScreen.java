package com.farmmacro.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Dedicated capture screen keeps keyboard navigation and row search from consuming the new bind. */
public final class KeybindCaptureScreen extends Screen {
    private final Screen parent;
    private final KeyMapping key;
    public KeybindCaptureScreen(Screen parent,KeyMapping key){
        super(Component.literal("Назначить клавишу"));this.parent=parent;this.key=key;
    }
    private void assign(InputConstants.Key value){KeybindRows.assign(key,value);onClose();}
    @Override public boolean keyPressed(KeyEvent e){
        if(e.key()==256){onClose();return true;}
        if(e.key()==259||e.key()==261){assign(InputConstants.UNKNOWN);return true;}
        assign(InputConstants.getKey(e));return true;
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        assign(InputConstants.Type.MOUSE.getOrCreate(e.button()));return true;
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        super.extractRenderState(g,mx,my,delta);
        int w=Math.min(360,width-16),h=108,x=(width-w)/2,y=(height-h)/2;
        Ui.shadow(g,x,y,w,h,8);Ui.window(g,x,y,w,h,8);
        Ui.text(g,font,"Назначить клавишу / кнопку мыши",x+12,y+12,Ui.ACCENT_HI);
        Ui.text(g,font,Ui.ellipsize(font,Component.translatable(key.getName()).getString(),w-24),x+12,y+32,Ui.TEXT);
        Ui.text(g,font,"Нажми новую клавишу или кнопку мыши",x+12,y+53,Ui.TEXT);
        Ui.text(g,font,"Esc — отмена · Delete/Backspace — снять",x+12,y+76,Ui.SUB);
    }
}