package com.farmmacro.camera;

import com.farmmacro.config.ModConfig.CamPreset;
import com.farmmacro.panic.PanicDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Привязка пресетов камеры к макросу (по кадрам) или маршруту (по точкам). Хранится в файле макроса/маршрута.
 *  • {@code none}   — камеру не трогаем;
 *  • {@code whole}  — один пресет на весь проход (ставится при старте и в начале каждого круга);
 *  • {@code points} — «с элемента N действует пресет X» (ставится при старте и когда дошли до N).
 * Ставится мгновенно ({@code Entity.absSnapRotationTo}), без плавности и случайности; после этого yaw/pitch не меняются.
 * После каждой установки детектору сообщается новый поворот ({@link PanicDetector#expectRotation}).
 * В смене хранится копия yaw/pitch/имени: если пресет удалили, работают сохранённые значения.
 */
public class CameraBinding {
    public static final String NONE = "none", WHOLE = "whole", POINTS = "points";

    public String mode = NONE;
    /** В файле — по порядку from, без повторов. В режиме whole действует первая. */
    public List<Change> changes = new ArrayList<>();

    public static class Change {
        /** С какого элемента (0-based: индекс точки или кадра). */
        public int    from;
        public int    presetId;
        public String name = "";
        public float  yaw, pitch;

        public Change() {}

        public Change copy() {
            Change c = new Change();
            c.from = from; c.presetId = presetId; c.name = name; c.yaw = yaw; c.pitch = pitch;
            return c;
        }

        /** Взять значения у пресета (копия на случай его удаления). */
        public void take(CamPreset p) {
            presetId = p.id; name = p.name; yaw = p.yaw; pitch = p.pitch;
        }

        /** Действующие yaw/pitch: у живого пресета, иначе сохранённые. */
        public float yaw()   { CamPreset p = CameraPresets.byId(presetId); return p != null ? p.yaw : yaw; }
        public float pitch() { CamPreset p = CameraPresets.byId(presetId); return p != null ? p.pitch : pitch; }
        public boolean presetExists() { return CameraPresets.byId(presetId) != null; }
        public String label() {
            CamPreset p = CameraPresets.byId(presetId);
            return p != null ? p.name : (name == null || name.isEmpty() ? "?" : name) + " (удалён)";
        }
    }

    public boolean active() { return !NONE.equals(mode) && !changes.isEmpty(); }

    /** Смена, действующая на элементе index, или null (камеру не трогать). */
    public Change at(int index) {
        if (!active()) return null;
        if (WHOLE.equals(mode)) return changes.get(0);
        Change best = null;                       // список может быть не отсортирован, пока его правят в меню
        for (Change c : changes) if (c.from <= index && (best == null || c.from >= best.from)) best = c;
        return best;
    }

    public CameraBinding copy() {
        CameraBinding b = new CameraBinding();
        b.mode = mode;
        for (Change c : changes) b.changes.add(c.copy());
        return b;
    }

    /** Перед записью в файл: порядок по from и свежие копии значений у существующих пресетов. */
    public void refresh() {
        sort();
        for (Change c : changes) {
            CamPreset p = CameraPresets.byId(c.presetId);
            if (p != null) c.take(p);
        }
    }

    /** Порядок по from, без повторов (остаётся последняя). */
    public void sort() {
        changes.sort(Comparator.comparingInt(c -> c.from));
        for (int i = changes.size() - 1; i > 0; i--)
            if (changes.get(i).from == changes.get(i - 1).from) changes.remove(i - 1);
    }

    /** После чтения файла (старые файлы — без поля, null). */
    public static CameraBinding sanitize(CameraBinding b) {
        if (b == null) return new CameraBinding();
        if (!NONE.equals(b.mode) && !WHOLE.equals(b.mode) && !POINTS.equals(b.mode)) b.mode = NONE;
        if (b.changes == null) b.changes = new ArrayList<>();
        b.changes.removeIf(Objects::isNull);
        for (Change c : b.changes) {
            c.from = Math.max(0, c.from);
            if (c.name == null) c.name = "";
            c.yaw = Mth.wrapDegrees(Float.isFinite(c.yaw) ? c.yaw : 0f);
            c.pitch = Mth.clamp(Float.isFinite(c.pitch) ? c.pitch : 0f, -90f, 90f);
        }
        while (b.changes.size() > 256) b.changes.remove(b.changes.size() - 1);
        b.sort();
        if (b.changes.isEmpty()) b.mode = NONE;
        return b;
    }

    /**
     * Поставить камеру в пресет смены — мгновенно, и сообщить детектору, что это свой поворот.
     * Плавный поворот к пресету по клавише, если шёл, прекращается.
     */
    public static void apply(Minecraft mc, Change c, String where) {
        LocalPlayer p = mc.player;
        if (p == null || c == null) return;
        SmoothTurn.stop();
        var human = com.farmmacro.macro.Humanizer.ACTIVE;
        float[] j = human != null ? human.cameraJitter() : new float[]{0, 0};
        p.absSnapRotationTo(c.yaw() + j[0], c.pitch() + j[1]);
        PanicDetector.INSTANCE.expectRotation(p.getYRot(), p.getXRot());
        p.sendOverlayMessage(Component.literal("§8[§cFM§8] §b◎ " + c.label() + " §7(" + where + "): yaw "
                + CameraPresets.deg(c.yaw()) + "°, pitch " + CameraPresets.deg(c.pitch()) + "°"));
    }

    /** Следит, какая смена уже поставлена в текущем проходе. Одна на источник. */
    public static final class Tracker {
        private Change applied;
        private boolean any;

        public void reset() { applied = null; any = false; }

        /** @return true — камеру только что поставили */
        public boolean update(Minecraft mc, CameraBinding b, int index, String where) {
            Change c = b == null ? null : b.at(index);
            if (c == null || (any && c == applied)) return false;
            apply(mc, c, where);
            applied = c;
            any = true;
            return true;
        }
    }
}
