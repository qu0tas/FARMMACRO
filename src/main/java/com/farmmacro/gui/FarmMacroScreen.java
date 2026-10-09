package com.farmmacro.gui;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.MacroStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class FarmMacroScreen extends Screen {

    private static final int BG_DARK        = 0xFF0D0D0D;
    private static final int BG_PANEL       = 0xFF161616;
    private static final int BG_SIDEBAR     = 0xFF111111;
    private static final int ACCENT_RED     = 0xFFE02020;
    private static final int TEXT_WHITE     = 0xFFFFFFFF;
    private static final int TEXT_GRAY      = 0xFF888888;
    private static final int TEXT_LIGHT     = 0xFFCCCCCC;
    private static final int BORDER         = 0xFF2A2A2A;

    private static final int SIDEBAR_W = 48;

    private static final String[] TAB_LABELS = {"Паника", "Реакция", "Сохранения"};

    private int activeTab = 0;

    private EditBox fieldYaw, fieldPitch, fieldTeleport, fieldBlockRadius;
    private EditBox fieldServerRotTicks;
    private EditBox fieldStuckThreshold;
    private EditBox fieldSoundId, fieldSoundVol, fieldSoundPitch;
    private EditBox fieldSoundRepeats, fieldSoundRepeatDelay;
    private EditBox fieldRedTicks;

    private final List<String> lblText  = new ArrayList<>();
    private final List<int[]>  lblPos   = new ArrayList<>();

    private final Screen parent;

    public FarmMacroScreen(Screen parent) {
        super(Component.literal("FarmMacro"));
        this.parent = parent;
    }

    private int pW() { return Math.min(width  - 60, 580); }
    private int pH() { return Math.min(height - 60, 420); }
    private int pX() { return (width  - pW()) / 2; }
    private int pY() { return (height - pH()) / 2; }
    private int cX() { return pX() + SIDEBAR_W; }
    private int cW() { return pW() - SIDEBAR_W; }

    @Override
    protected void init() {
        lblText.clear(); lblPos.clear();
        resetFields();

        int px = pX(), py = pY(), pw = pW(), ph = pH();
        int tabH = (ph - 40) / TAB_LABELS.length;

        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            String shortLabel = TAB_LABELS[i].length() > 4
                    ? TAB_LABELS[i].substring(0, 4)
                    : TAB_LABELS[i];
            addRenderableWidget(Button.builder(
                    Component.literal(shortLabel),
                    b -> { saveAll(); activeTab = idx; rebuildWidgets(); }
            ).bounds(px + 2, py + 30 + i * (tabH + 4), SIDEBAR_W - 4, tabH).build());
        }

        addRenderableWidget(Button.builder(Component.literal("×"),
                b -> { saveAll(); minecraft.setScreen(parent); }
        ).bounds(px + pw - 22, py + 4, 18, 14).build());

        int btnY = py + ph - 24;
        int btnW = (cW() - 12) / 2;
        addRenderableWidget(Button.builder(Component.literal("✔  Сохранить"),
                b -> { saveAll(); minecraft.setScreen(parent); }
        ).bounds(cX() + 4, btnY, btnW, 20).build());
        addRenderableWidget(Button.builder(Component.literal("✖  Отмена"),
                b -> minecraft.setScreen(parent)
        ).bounds(cX() + btnW + 8, btnY, btnW, 20).build());

        int cy = py + 28;
        int ch = ph - 52;
        switch (activeTab) {
            case 0 -> initPanic(cX(), cy, cW(), ch);
            case 1 -> initReaction(cX(), cy, cW(), ch);
            case 2 -> initSaved(cX(), cy, cW(), ch);
        }
    }

    private void resetFields() {
        fieldYaw = fieldPitch = fieldTeleport = fieldBlockRadius = null;
        fieldServerRotTicks = null;
        fieldStuckThreshold = null;
        fieldSoundId = fieldSoundVol = fieldSoundPitch = null;
        fieldSoundRepeats = fieldSoundRepeatDelay = null;
        fieldRedTicks = null;
    }

    private void initPanic(int x, int y, int w, int h) {
        ModConfig c = ModConfig.INSTANCE;
        int colW = w / 2 - 8;
        int L = x + 8;
        int R = x + w / 2 + 4;
        int fieldX = R + colW - 60;
        int r = y + 6;

        lbl("ДЕТЕКТОРЫ", L, r, TEXT_GRAY); r += 14;
        tog(c.panicEnabled,         L, r, "Паника включена",             v -> { c.panicEnabled = v;            rebuildWidgets(); }); r += 24;
        tog(c.detectRotation,       L, r, "А: Поворот камеры",           v -> { c.detectRotation = v;          rebuildWidgets(); }); r += 24;
        tog(c.detectTeleport,       L, r, "Б: Телепорт",                 v -> { c.detectTeleport = v;          rebuildWidgets(); }); r += 24;
        tog(c.detectBlockInFace,    L, r, "В: Блок в лицо",              v -> { c.detectBlockInFace = v;       rebuildWidgets(); }); r += 24;
        tog(c.detectSlotChange,     L, r, "Г: Смена слота",              v -> { c.detectSlotChange = v;        rebuildWidgets(); }); r += 24;
        tog(c.detectGuiOpen,        L, r, "Д: GUI снаружи",              v -> { c.detectGuiOpen = v;           rebuildWidgets(); }); r += 24;
        tog(c.detectDamage,         L, r, "Урон",                        v -> { c.detectDamage = v;            rebuildWidgets(); }); r += 24;
        tog(c.detectServerRotation, L, r, "Ж: Ротация сервером",         v -> { c.detectServerRotation = v;    rebuildWidgets(); }); r += 24;
        tog(c.detectPotionEffect,   L, r, "З: Эффекты (potion)",         v -> { c.detectPotionEffect = v;      rebuildWidgets(); });

        int rr = y + 6;
        lbl("ПОРОГИ", R, rr, TEXT_GRAY); rr += 16;
        lbl("Yaw (°)",         R, rr + 4, TEXT_LIGHT); fieldYaw          = inp(s(c.yawThreshold),                    fieldX, rr, 58); rr += 22;
        lbl("Pitch (°)",       R, rr + 4, TEXT_LIGHT); fieldPitch        = inp(s(c.pitchThreshold),                  fieldX, rr, 58); rr += 22;
        lbl("Телепорт (бл)",   R, rr + 4, TEXT_LIGHT); fieldTeleport     = inp(s(c.teleportThreshold),               fieldX, rr, 58); rr += 22;
        lbl("Радиус блока",    R, rr + 4, TEXT_LIGHT); fieldBlockRadius  = inp(s(c.blockDetectRadius),               fieldX, rr, 58); rr += 22;
        lbl("Ж: мышь (тики)", R, rr + 4, TEXT_LIGHT); fieldServerRotTicks = inp(s(c.serverRotationMouseTickWindow),  fieldX, rr, 58); rr += 28;
        lbl("Застрял (тики)",  R, rr + 4, TEXT_LIGHT); fieldStuckThreshold = inp(s(c.stuckThresholdTicks),           fieldX, rr, 58); rr += 22;
        tog(c.stuckBlockDetectEnabled, R, rr, "З: Блок на пути",
                v -> { c.stuckBlockDetectEnabled = v; ModConfig.save(); rebuildWidgets(); });
    }

    private void initReaction(int x, int y, int w, int h) {
        ModConfig c = ModConfig.INSTANCE;
        int cx = x + 8, r = y + 6;
        int lbl2X  = cx + 130;
        int fld1X  = cx + 90;
        int fld2X  = cx + 190;
        int fldW   = 55;

        lbl("ЗВУК ПРИ ПАНИКЕ", cx, r, TEXT_GRAY); r += 14;
        tog(c.panicSoundEnabled, cx, r, "Включён",
                v -> { c.panicSoundEnabled = v; rebuildWidgets(); }); r += 24;

        lbl("Sound ID", cx + 4, r + 4, TEXT_LIGHT);
        fieldSoundId = new net.minecraft.client.gui.components.EditBox(
                font, cx + 65, r, w - 78, 16, net.minecraft.network.chat.Component.empty());
        fieldSoundId.setValue(c.panicSoundId);
        fieldSoundId.setMaxLength(64);
        addRenderableWidget(fieldSoundId); r += 22;

        lbl("Громкость", cx + 4, r + 4, TEXT_LIGHT);
        fieldSoundVol   = inp(s(c.panicSoundVolume), fld1X, r, fldW);
        lbl("Питч",      lbl2X, r + 4, TEXT_LIGHT);
        fieldSoundPitch = inp(s(c.panicSoundPitch),  fld2X, r, fldW); r += 22;

        lbl("Повторов", cx + 4, r + 4, TEXT_LIGHT);
        fieldSoundRepeats     = inp(s(c.panicSoundRepeats),          fld1X, r, fldW);
        lbl("Задержка (тики)", lbl2X, r + 4, TEXT_LIGHT);
        fieldSoundRepeatDelay = inp(s(c.panicSoundRepeatDelayTicks),  fld2X, r, fldW); r += 30;

        lbl("КРАСНЫЙ ЭКРАН", cx, r, TEXT_GRAY); r += 14;
        tog(c.panicRedScreenEnabled, cx, r, "Включён",
                v -> { c.panicRedScreenEnabled = v; rebuildWidgets(); }); r += 24;

        lbl("Длительность (тики)", cx + 4, r + 4, TEXT_LIGHT);
        fieldRedTicks = inp(s(c.panicRedScreenTicks), fld1X, r, fldW);
        lbl("(20 тиков = 1 сек)", fld1X + fldW + 8, r + 4, TEXT_GRAY);
    }

    private void initSaved(int x, int y, int w, int h) {
        int cx = x + 8, r = y + 6;

        boolean loop = MacroManager.INSTANCE.isLoopEnabled();
        lbl("ВОСПРОИЗВЕДЕНИЕ", cx, r, TEXT_GRAY); r += 14;
        tog(loop, cx, r, "Зациклить макрос",
                v -> MacroManager.INSTANCE.setLoopEnabled(v)); r += 24;
        ModConfig cfg = ModConfig.INSTANCE;
        tog(cfg.statsHudEnabled, cx, r, "HUD статистики",
                v -> { cfg.statsHudEnabled = v; ModConfig.save(); }); r += 28;

        lbl("СОХРАНЁННЫЕ МАКРОСЫ", cx, r, TEXT_GRAY); r += 16;

        var list = MacroStorage.INSTANCE.listMacros();

        if (list.isEmpty()) {
            lbl("Нет сохранённых макросов.", cx + 4, r + 4, TEXT_GRAY);
            lbl("Запиши макрос (R) и останови — появится окно сохранения.", cx + 4, r + 18, TEXT_GRAY);
            return;
        }

        int rowH = 22;
        int maxVisible = (h - 80) / rowH;
        int shown = 0;

        for (MacroStorage.MacroInfo info : list) {
            if (shown >= maxVisible) break;
            int rowY = r;
            lbl(info.name, cx + 4, rowY + 5, TEXT_LIGHT);
            lbl(info.frameCount + " кадров",
                    cx + 4 + font.width(info.name) + 6, rowY + 5, TEXT_GRAY);

            int btnX = x + w - 118;
            addRenderableWidget(Button.builder(
                    Component.literal("Загрузить"),
                    b -> {
                        var frames = MacroStorage.INSTANCE.load(info.filename);
                        if (frames != null) {
                            MacroManager.INSTANCE.loadMacro(frames, minecraft);
                        }
                        rebuildWidgets();
                    }
            ).bounds(btnX, rowY + 1, 56, 18)
             .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                     Component.literal("Загрузить «" + info.name + "» в буфер")))
             .build());

            addRenderableWidget(Button.builder(
                    Component.literal("X"),
                    b -> {
                        MacroStorage.INSTANCE.delete(info.filename);
                        rebuildWidgets();
                    }
            ).bounds(btnX + 60, rowY + 1, 18, 18)
             .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                     Component.literal("Удалить «" + info.name + "»")))
             .build());

            r += rowH;
            shown++;
        }

        if (list.size() > maxVisible) {
            lbl("... ещё " + (list.size() - maxVisible) + " макросов", cx + 4, r + 4, TEXT_GRAY);
        }
    }

    private void saveAll() {
        ModConfig c = ModConfig.INSTANCE;
        c.yawThreshold                  = pf(fieldYaw,              c.yawThreshold);
        c.pitchThreshold                = pf(fieldPitch,             c.pitchThreshold);
        c.teleportThreshold             = pd(fieldTeleport,          c.teleportThreshold);
        c.blockDetectRadius             = pd(fieldBlockRadius,       c.blockDetectRadius);
        c.serverRotationMouseTickWindow = pi(fieldServerRotTicks,    c.serverRotationMouseTickWindow);
        c.stuckThresholdTicks           = Math.max(5, pi(fieldStuckThreshold, c.stuckThresholdTicks));
        if (fieldSoundId != null) c.panicSoundId = fieldSoundId.getValue().trim();
        c.panicSoundVolume              = pf(fieldSoundVol,          c.panicSoundVolume);
        c.panicSoundPitch               = pf(fieldSoundPitch,        c.panicSoundPitch);
        c.panicSoundRepeats             = pi(fieldSoundRepeats,      c.panicSoundRepeats);
        c.panicSoundRepeatDelayTicks    = pi(fieldSoundRepeatDelay,  c.panicSoundRepeatDelayTicks);
        c.panicRedScreenTicks           = pi(fieldRedTicks,          c.panicRedScreenTicks);
        ModConfig.save();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractBackground(ctx, mx, my, delta);

        int px = pX(), py = pY(), pw = pW(), ph = pH();
        int cx = cX();

        ctx.fill(px + 4, py + 4, px + pw + 4, py + ph + 4, 0x88000000);
        ctx.fill(px, py, px + pw, py + ph, BG_DARK);
        ctx.fill(px, py, px + SIDEBAR_W, py + ph, BG_SIDEBAR);
        ctx.fill(px + SIDEBAR_W, py, px + SIDEBAR_W + 1, py + ph, ACCENT_RED);
        ctx.fill(cx, py, px + pw, py + ph, BG_PANEL);
        ctx.fill(cx, py, px + pw, py + 26, 0xFF0D0D0D);
        ctx.fill(cx, py + 26, px + pw, py + 27, BORDER);
        ctx.fill(px, py, px + pw, py + 2, ACCENT_RED);

        int tabH = (ph - 40) / TAB_LABELS.length;
        int tabY = py + 30 + activeTab * (tabH + 4);
        ctx.fill(px, tabY, px + SIDEBAR_W, tabY + tabH, 0xFF1A0000);
        ctx.fill(px, tabY, px + 2, tabY + tabH, ACCENT_RED);

        ctx.fill(cx, py + ph - 26, px + pw, py + ph - 25, BORDER);

        String tabName = TAB_LABELS[activeTab];
        ctx.text(font, "FarmMacro", cx + 8, py + 8, ACCENT_RED);
        ctx.text(font, "/ " + tabName, cx + 8 + font.width("FarmMacro") + 4, py + 8, TEXT_GRAY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);
        for (int i = 0; i < lblText.size(); i++) {
            int[] p = lblPos.get(i);
            ctx.text(font, lblText.get(i), p[0], p[1], p[2]);
        }
    }

    @Override
    protected void rebuildWidgets() { super.rebuildWidgets(); }

    @Override
    public boolean keyPressed(KeyEvent input) { return super.keyPressed(input); }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void onClose() { saveAll(); minecraft.setScreen(parent); }

    private void tog(boolean cur, int x, int y, String label, Consumer<Boolean> setter) {
        addRenderableWidget(Button.builder(
                Component.literal(cur ? "●  ON" : "○  OFF"),
                b -> setter.accept(!cur)
        ).bounds(x, y, 52, 16).build());
        lbl(label, x + 58, y + 4, cur ? TEXT_WHITE : TEXT_GRAY);
    }

    private EditBox inp(String val, int x, int y, int w) {
        EditBox f = new EditBox(font, x, y, w, 16, Component.empty());
        f.setValue(val);
        f.setMaxLength(12);
        addRenderableWidget(f);
        return f;
    }

    private void lbl(String t, int x, int y) {
        lblText.add(t); lblPos.add(new int[]{x, y, 0xFFDDDDDD});
    }

    private void lbl(String t, int x, int y, int color) {
        lblText.add(t); lblPos.add(new int[]{x, y, color});
    }

    private String s(float v)  { return String.valueOf(v); }
    private String s(double v) { return String.valueOf(v); }
    private String s(int v)    { return String.valueOf(v); }

    private int    pi(EditBox f, int d)    { if(f==null)return d; try{return Integer.parseInt(f.getValue().trim());}    catch(Exception e){return d;} }
    private float  pf(EditBox f, float d)  { if(f==null)return d; try{return Float.parseFloat(f.getValue().trim());}    catch(Exception e){return d;} }
    private double pd(EditBox f, double d) { if(f==null)return d; try{return Double.parseDouble(f.getValue().trim());} catch(Exception e){return d;} }
}
