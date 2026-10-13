package com.farmmacro.gui;

import com.farmmacro.route.RouteVariants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small editor toolbar; empty background leaves both world ribbons visible. */
public final class RouteVariantScreen extends Screen {
    public RouteVariantScreen() { super(Component.literal("Рандом маршрута")); }
    @Override protected void init() {
        int x=(width-304)/2,y=height-40;
        addRenderableWidget(Button.builder(Component.literal("Зарандомить"),b -> {
            RouteVariants.clearPreview();RouteVariants.nextPreview();
        }).bounds(x,y,100,20).build());
        addRenderableWidget(Button.builder(Component.literal("Ещё"),b -> RouteVariants.nextPreview()).bounds(x+102,y,70,20).build());
        addRenderableWidget(Button.builder(Component.literal("Сохранить копией"),b -> {
            boolean ok=RouteVariants.saveCopy();
            com.farmmacro.macro.MacroManager.INSTANCE.msgPublic(minecraft,ok?"§aВариант сохранён как _rnd":"§cНет превью или ошибка сохранения");
        }).bounds(x+174,y,130,20).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(null); }
}
