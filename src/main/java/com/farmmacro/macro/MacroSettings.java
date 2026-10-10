package com.farmmacro.macro;

import com.farmmacro.camera.CameraBinding;

/**
 * Настройки одного макроса или маршрута (окно «Настройки макроса», ⚙ у карточки).
 * Хранятся в файле макроса (поле {@code settings}, формат v4) и маршрута ({@code settings}, v3).
 * Старые файлы с полем {@code camera} (макрос v3, маршрут v2) переносятся сюда при чтении — см. {@link #fromFile}.
 */
public class MacroSettings {
    /** Пресеты камеры: нет / на всё / с кадра (точки) N. */
    public CameraBinding camera = new CameraBinding();
    /** Зажим мыши: ЛКМ/ПКМ весь проход или по участкам/точкам, задержка старта. */
    public HoldSettings hold = new HoldSettings();

    public MacroSettings copy() {
        MacroSettings s = new MacroSettings();
        s.camera = camera == null ? new CameraBinding() : camera.copy();
        s.hold = hold == null ? new HoldSettings() : hold.copy();
        return s;
    }

    /** Перед записью в файл: порядок и свежие значения пресетов. */
    public void refresh() {
        if (camera == null) camera = new CameraBinding();
        camera.refresh();
        if (hold == null) hold = new HoldSettings();
        hold.sort();
    }

    /**
     * После чтения файла. {@code legacyCamera} — поле {@code camera} старого формата:
     * берётся, если в {@code settings} своей привязки нет (новые файлы его не пишут).
     */
    public static MacroSettings fromFile(MacroSettings s, CameraBinding legacyCamera) {
        if (s == null) s = new MacroSettings();
        if (legacyCamera != null && (s.camera == null || CameraBinding.NONE.equals(s.camera.mode)
                && (s.camera.changes == null || s.camera.changes.isEmpty()))) s.camera = legacyCamera;
        s.camera = CameraBinding.sanitize(s.camera);
        s.hold = HoldSettings.sanitize(s.hold);
        return s;
    }

    /** Коротко для подписи в меню. */
    public String summary(boolean route) {
        StringBuilder sb = new StringBuilder("Камера: ");
        if (!camera.active()) sb.append("как есть");
        else if (CameraBinding.WHOLE.equals(camera.mode)) sb.append(camera.changes.get(0).label());
        else sb.append(camera.changes.size()).append(" смен(ы) по ").append(route ? "точкам" : "кадрам");
        sb.append(" · Зажим: ");
        if (!hold.active()) sb.append("нет");
        else sb.append(hold.buttonName()).append(HoldSettings.WHOLE.equals(hold.mode) ? " весь проход"
                : route ? " по точкам" : " на " + hold.ranges.size() + " участк.");
        return sb.toString();
    }
}
