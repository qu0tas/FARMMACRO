package com.farmmacro.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Original authored Kaelix pixel-art, not Minecraft item or third-party icon assets. */
final class ModuleGlyphs {
    private static final Identifier ATLAS=Identifier.fromNamespaceAndPath("farmmacro","textures/gui/modules/glyphs.png");
    static void draw(GuiGraphicsExtractor g,int index,int x,int y,int size) {
        int cell=Math.clamp(index,0,18);
        g.blit(RenderPipelines.GUI_TEXTURED,ATLAS,x,y,(cell%6)*64,(cell/6)*64,size,size,64,64,384,256);
    }
    private ModuleGlyphs() {}
}
