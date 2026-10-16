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
    /** «Случайность» (v5): каждый проход немного другой. В старых файлах нет — выключено. */
    public HumanSettings human = new HumanSettings();
    public com.farmmacro.route.RouteRandomSettings routeRandom = new com.farmmacro.route.RouteRandomSettings();
    public com.farmmacro.route.RouteEndSettings routeEnd = new com.farmmacro.route.RouteEndSettings();
    public com.farmmacro.route.FarmSchemeSettings farm = new com.farmmacro.route.FarmSchemeSettings();

    /**
     * v1.12.1: настройки для нового маршрута / новой записи — копия «по умолчанию для новых»
     * ({@code ModConfig.newItemSettings}, набор «Оптимально» или кнопка ⚙ «Сделать по умолчанию для новых»).
     * Старые файлы без {@code settings} это не затрагивает — у них по-прежнему всё выключено.
     */
    public static MacroSettings forNew() {
        MacroSettings s = com.farmmacro.config.ModConfig.INSTANCE.newItemSettings;
        return s == null ? optimalNew() : s.copy();
    }

    /**
     * Стандарт «Оптимально» для новых маршрутов и записей: камера как есть, зажим ЛКМ весь проход без задержки,
     * своя случайность включена (используется, только если общая случайность выключена).
     */
    public static MacroSettings optimalNew() {
        MacroSettings s = new MacroSettings();
        s.hold.button = HoldSettings.ATTACK;
        s.hold.mode = HoldSettings.WHOLE;
        s.hold.delayTicks = 0;
        s.human = HumanSettings.presetRouteOptimal();
        return s;
    }

    public MacroSettings copy() {
        MacroSettings s = new MacroSettings();
        s.camera = camera == null ? new CameraBinding() : camera.copy();
        s.hold = hold == null ? new HoldSettings() : hold.copy();
        s.human = human == null ? new HumanSettings() : human.copy();
        s.routeRandom = routeRandom == null ? new com.farmmacro.route.RouteRandomSettings() : routeRandom.copy();
        s.routeEnd = routeEnd == null ? new com.farmmacro.route.RouteEndSettings() : routeEnd.copy();
        s.farm = farm == null ? new com.farmmacro.route.FarmSchemeSettings() : farm.copy();
        return s;
    }

    /** Перед записью в файл: порядок и свежие значения пресетов. */
    public void refresh() {
        if(farm==null)farm=new com.farmmacro.route.FarmSchemeSettings();farm.sanitize();
        if (camera == null) camera = new CameraBinding();
        camera.refresh();
        if (hold == null) hold = new HoldSettings();
        hold.sort();
        human = HumanSettings.sanitize(human);
        if (routeRandom == null) routeRandom = new com.farmmacro.route.RouteRandomSettings();
        routeRandom.sanitize();
        if(routeEnd==null)routeEnd=new com.farmmacro.route.RouteEndSettings();routeEnd.sanitize();
    }

    /**
     * После чтения файла. {@code legacyCamera} — поле {@code camera} старого формата:
     * берётся, если в {@code settings} своей привязки нет (новые файлы его не пишут).
     */
    public static MacroSettings fromFile(MacroSettings s, CameraBinding legacyCamera) {
        if (s == null) s = new MacroSettings();
        if (legacyCamera != null && (s.camera == null || CameraBinding.NONE.equals(s.camera.mode)
                && (s.camera.changes == null || s.camera.changes.isEmpty()))) s.camera = legacyCamera;
        if(s.farm==null)s.farm=new com.farmmacro.route.FarmSchemeSettings();s.farm.sanitize();
        s.camera = CameraBinding.sanitize(s.camera);
        s.hold = HoldSettings.sanitize(s.hold);
        s.human = HumanSettings.sanitize(s.human);
        if (s.routeRandom == null) s.routeRandom = new com.farmmacro.route.RouteRandomSettings();
        s.routeRandom.sanitize();
        if(s.routeEnd==null)s.routeEnd=new com.farmmacro.route.RouteEndSettings();s.routeEnd.sanitize();
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
        if (human != null && human.enabled) sb.append(" · Случайность: вкл");
        if(route && farm!=null && farm.enabled)sb.append(" · Ферма: ").append(farm.mode);
        return sb.toString();
    }
}
