package com.farmmacro.camera;

import com.farmmacro.config.ModConfig;
import com.farmmacro.config.ModConfig.CamPreset;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Пресеты камеры: имя, yaw/pitch с любой точностью и подпись культуры; панель — вкладка «Камера» меню.
 * Применение по клавише/кнопке работает в любой момент, кроме воспроизведения записи с «Повторять камеру».
 * Поворот к пресету — {@link SmoothTurn} (плавно с настраиваемой скоростью или мгновенно), без случайности.
 */
public final class CameraPresets {
    private CameraPresets() {}

    public static final int MAX = 16;

    public record Crop(String id, String label) {}

    public static final List<Crop> CROPS = List.of(
            new Crop("wheat", "Пшеница"), new Crop("carrot", "Морковь"), new Crop("potato", "Картофель"),
            new Crop("beetroot", "Свёкла"), new Crop("melon", "Арбуз"), new Crop("pumpkin", "Тыква"),
            new Crop("cane", "Тростник"), new Crop("cactus", "Кактус"), new Crop("cocoa", "Какао"),
            new Crop("wart", "Незерский нарост"), new Crop("mushroom", "Грибы"), new Crop("berries", "Ягоды"),
            new Crop("other", "Другое"));

    public static String cropLabel(String id) {
        for (Crop c : CROPS) if (c.id().equals(id)) return c.label();
        return "Другое";
    }

    public static String describe(CamPreset p) {
        return p.name + " · " + cropLabel(p.crop) + " · yaw " + deg(p.yaw) + "° · pitch " + deg(p.pitch) + "°";
    }

    /** Угол без потери введённой точности: 90 → «90.0», 12.345 → «12.345» (кратчайшая запись float). */
    public static String deg(float v) {
        String s = new java.math.BigDecimal(Float.toString(v)).stripTrailingZeros().toPlainString();
        if (s.equals("-0")) s = "0";
        return s.contains(".") ? s : s + ".0";
    }

    /** Разбор числа из поля ввода: точка или запятая, пробелы и «°» игнорируются. null — не число. */
    public static Float parseDeg(String text) {
        if (text == null) return null;
        String t = text.strip().replace(',', '.').replace("°", "").replace(" ", "");
        if (t.isEmpty()) return null;
        try {
            float v = Float.parseFloat(t);
            return Float.isFinite(v) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Пресет по постоянному id или null. */
    public static CamPreset byId(int id) {
        for (CamPreset p : list()) if (p.id == id) return p;
        return null;
    }

    public static int indexOf(int id) {
        List<CamPreset> l = list();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id == id) return i;
        return -1;
    }

    private static List<CamPreset> list() { return ModConfig.INSTANCE.camPresets; }

    public static CamPreset selected() {
        List<CamPreset> l = list();
        if (l.isEmpty()) return null;
        int i = Math.max(0, Math.min(l.size() - 1, ModConfig.INSTANCE.camSelected));
        return l.get(i);
    }

    /** Повернуть к выбранному пресету. */
    public static void applySelected(Minecraft mc) {
        CamPreset p = selected();
        if (p == null) { msg(mc, "§7Нет пресетов камеры — сохрани текущий взгляд (" + key(com.farmmacro.FarmMacroMod.keyCamSave) + ")"); return; }
        apply(mc, ModConfig.INSTANCE.camSelected);
    }

    /** Выбрать следующий пресет и повернуть к нему. */
    public static void next(Minecraft mc) {
        List<CamPreset> l = list();
        if (l.isEmpty()) { applySelected(mc); return; }
        ModConfig.INSTANCE.camSelected = (ModConfig.INSTANCE.camSelected + 1) % l.size();
        ModConfig.save();
        apply(mc, ModConfig.INSTANCE.camSelected);
    }

    public static void apply(Minecraft mc, int index) {
        List<CamPreset> l = list();
        if (index < 0 || index >= l.size() || mc.player == null) return;
        if (MacroManager.INSTANCE.controlsCamera()) { msg(mc, "§cКамерой сейчас управляет макрос"); return; }
        ModConfig c = ModConfig.INSTANCE;
        c.camSelected = index;
        CamPreset p = l.get(index);
        SmoothTurn.turnTo(p.yaw, p.pitch, c.camSmooth ? c.camTurnSpeed : 0);
        msg(mc, "§b◎ Камера " + (index + 1) + "/" + l.size() + ": §f" + describe(p)
                + (c.camSmooth ? "" : " §7(мгновенно)"));
    }

    /** Записать текущий взгляд в выбранный пресет (или создать первый). */
    public static void saveCurrent(Minecraft mc) {
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        ModConfig c = ModConfig.INSTANCE;
        CamPreset p = selected();
        if (p == null) { p = c.newCamPreset("other", 0, 0); c.camPresets.add(p); c.camSelected = 0; }
        p.yaw = Mth.wrapDegrees(pl.getYRot());
        p.pitch = pl.getXRot();
        ModConfig.save();
        msg(mc, "§a◎ Сохранено в пресет " + (c.camSelected + 1) + ": §f" + describe(p));
    }

    /** Новый пресет из текущего взгляда. @return индекс или -1, если пресетов уже MAX */
    public static int addFromCurrent(Minecraft mc) {
        ModConfig c = ModConfig.INSTANCE;
        if (c.camPresets.size() >= MAX) return -1;
        LocalPlayer pl = mc.player;
        c.camPresets.add(c.newCamPreset("other", pl != null ? Mth.wrapDegrees(pl.getYRot()) : 0, pl != null ? pl.getXRot() : 0));
        c.camSelected = c.camPresets.size() - 1;
        ModConfig.save();
        return c.camSelected;
    }

    private static String key(net.minecraft.client.KeyMapping k) { return MacroManager.keyName(k); }

    private static void msg(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendOverlayMessage(Component.literal("§8[§cFM§8] §r" + text));
    }
}
