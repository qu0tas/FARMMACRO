package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import java.util.List;
public final class AutomationRows {
  private AutomationRows() {}
  public static void build(List<Rows.Row> rows,Runnable save) {
    ModConfig c=ModConfig.INSTANCE;
    rows.add(new Rows.Section("Общее движение и камера"));
    rows.add(new Rows.Note("Kaelix Motion — общее управление движением/камерой. Возврат выключен по умолчанию; подход к старту удалён. Отдельный Aether-режим ниже; это не перенос всего мода Aether.",Ui.SUB));
    MotionRows.build(rows,save);
    rows.add(new Rows.Toggle("Спринт при подходе", "Свободная прямая с запасом торможения; точные фермерские ряды не меняются",
        () -> c.pathSprint, v -> {c.pathSprint=v;save.run();}).showIf(() -> c.pathNaturalLook));
    rows.add(new Rows.Toggle("Спринт с прыжками (эксперимент)", "Проверка опоры, посадки и головы. Пока не проверено в игре; выключено по умолчанию",
        () -> c.pathSprintJump, v -> {c.pathSprintJump=v;save.run();}).showIf(() -> c.pathNaturalLook && c.pathSprint));
    rows.add(new Rows.Note("Подход/возврат: подъём проверяет объём тела, до3 попыток прыжка на участок;4с без реального сближения — новый путь в пределах лимита. Shift/Sprint учитывают toggle-режим; точные записанные ряды не переписываются.",Ui.SUB));
    rows.add(new Rows.Toggle("Отладка навигации", "Путь, выбранное направление и проверки препятствий в мире; подробности в HUD",
        () -> c.pathDebug, v -> {c.pathDebug=v;save.run();}));
    rows.add(
        new Rows.Toggle(
            "Естественный взгляд по пути",
            "Единый контроллер камеры и движения, локальный обход; выключение возвращает прежнее управление",
            () -> c.pathNaturalLook,
            v -> {
              c.pathNaturalLook = v;
              save.run();
            }));
    rows.add(
        new Rows.Number(
            "Смотреть вперед, бл",
            null,
            () -> c.pathLookAhead,
            v -> {
              c.pathLookAhead = v;
              save.run();
            },
            .5,
            8,
            .25,
            1,
            Rows::num));
    rows.add(new Rows.Toggle("Смотреть по высоте пути", "Выключено: держать горизонт ровно даже при подъёме/полёте. Не влияет на прицел Vacuum или aim assist",
        () -> c.pathHeightLook,v -> {c.pathHeightLook=v;save.run();}));
    rows.add(new Rows.Toggle("Вариативность походки", "Небольшой дрейф и смена темпа. Выключено: меньше лишних коррекций и устойчивый спринт",
        () -> c.pathVariation,v -> {c.pathVariation=v;save.run();}));
  }
}
