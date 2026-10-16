package com.farmmacro.config;

/**
 * v1.14: место плашки HUD (редактор HUD). Позиция — от якоря (лево/центр/право, верх/центр/низ) со сдвигом в пикселях GUI,
 * поэтому при смене размера окна плашка остаётся у своего края. placed=false — стоит в стопке справа, как раньше.
 */
public class HudSlot {
    public String backgroundColor="",accentColor="";public int opacity=-1;
    public boolean placed = false;
    /** Якорь по X: 0 — левый край, 0.5 — центр, 1 — правый край (так же по Y). */
    public double  ax = 1, ay = 0;
    /** Сдвиг от якоря внутрь экрана, пиксели GUI (для центра — сдвиг центра плашки). */
    public double  ox = 6, oy = 6;
    public double  scale = 1;
    public boolean hidden = false;
    public boolean always = false;

    public HudSlot copy() {
        HudSlot s = new HudSlot();
        s.placed = placed; s.ax = ax; s.ay = ay; s.ox = ox; s.oy = oy; s.scale = scale; s.hidden = hidden;s.always=always;
        s.backgroundColor=backgroundColor;s.accentColor=accentColor;s.opacity=opacity;return s;
    }

    public void sanitize() {
        if(!com.farmmacro.gui.UiStyle.validColor(backgroundColor))backgroundColor="";if(!com.farmmacro.gui.UiStyle.validColor(accentColor))accentColor="";opacity=Math.clamp(opacity,-1,100);
        ax = ax < 0.25 ? 0 : ax > 0.75 ? 1 : 0.5;
        ay = ay < 0.25 ? 0 : ay > 0.75 ? 1 : 0.5;
        if (!Double.isFinite(ox)) ox = 6;
        if (!Double.isFinite(oy)) oy = 6;
        ox = Math.max(-4000, Math.min(4000, ox));
        oy = Math.max(-4000, Math.min(4000, oy));
        scale = Double.isFinite(scale) ? Math.max(0.5, Math.min(2.0, scale)) : 1;
    }
}
