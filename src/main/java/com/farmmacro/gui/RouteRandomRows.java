package com.farmmacro.gui;

import com.farmmacro.route.*;
import java.util.List;
import java.util.function.*;

final class RouteRandomRows {
    private RouteRandomRows() {}
    static Rows.Row range(String name, RouteRandomSettings.Range r, double max, boolean integer,
                          BooleanSupplier enabled, Consumer<Runnable> edit) {
        Rows.Number lo = new Rows.Number("", null, () -> r.min, v -> edit.accept(() -> { r.min=v; if(r.max<v) r.max=v; }),
                0,max,integer?1:.01,integer?5:.05,Rows::num);
        Rows.Number hi = new Rows.Number("", null, () -> r.max, v -> edit.accept(() -> { r.max=v; if(r.min>v) r.min=v; }),
                0,max,integer?1:.01,integer?5:.05,Rows::num);
        if(integer) { lo.integer(); hi.integer(); }
        return new Rows.Range(name, null,lo,hi).enabledIf(enabled);
    }
    static void build(List<Rows.Row> rows, RouteRandomSettings s, PointRoute ideal, BooleanSupplier en,
                      Consumer<Runnable> edit, Runnable rebuild) {
        rows.add(new Rows.Section("Рандом маршрута"));
        rows.add(new Rows.Selector("Режим", "Идеальный маршрут не меняется; случайность ввода настраивается отдельно", () -> List.of(
                new Rows.Choice("off","Выкл"), new Rows.Choice("each","Новый вариант каждый круг"),
                new Rows.Choice("fixed","N фиксированных вариантов")), () -> s.mode,
                v -> { edit.accept(() -> s.mode=v); rebuild.run(); }).enabledIf(en));
        rows.add(new Rows.TextField("Сид вариантов", "Один сид и настройки дают те же варианты", () -> Long.toString(s.seed), v -> {
            try { long n=Long.parseLong(v.trim()); edit.accept(() -> s.seed=n); return true; }
            catch(NumberFormatException e) { return false; }
        }, text -> text.matches("-?[0-9]*"),22,90).enabledIf(en));
        rows.add(new Rows.Number("Число фиксированных вариантов", null, () -> s.variants,
                v -> edit.accept(() -> s.variants=(int)v),1,100,1,5,Rows::num).integer().enabledIf(en));
        rows.add(range("Макс. смещение, бл",s.offset,.15,false,en,edit));
        rows.add(range("Шанс паузы, %",s.chance,100,false,en,edit));
        rows.add(range("Пауза в точке, т",s.pause,1200,true,en,edit));
        rows.add(new Rows.Number("Шанс протупить перед спуском, %",null,() -> s.beforeChance,
                v -> edit.accept(() -> s.beforeChance=v),0,100,1,5,Rows::num).enabledIf(en));
        rows.add(range("Перед спуском, т",s.beforeDrop,1200,true,en,edit));
        rows.add(range("После приземления +, т",s.landing,1200,true,en,edit));
        rows.add(range("Отпустить у края позже, т",s.edgeRelease,6,true,en,edit));
        rows.add(new Rows.Note("Диапазоны добавляют паузы к идеальным. Старт и спуски закреплены; смещение ≤ 0.15 бл, внутри блока и полосы ряда.",Ui.SUB));
        rows.add(new Rows.Section("Чередовать с маршрутами"));
        rows.add(new Rows.Note("Основной маршрут: вес 1. Несовместимые (другой мир/измерение, старт > 2 бл) не играются.",Ui.SUB));
        for(var info : RouteStorage.INSTANCE.list()) {
            if (ideal != null && java.util.Objects.equals(info.name(),ideal.name)) continue;
            var other = RouteStorage.INSTANCE.load(info.filename());
            boolean compatible = ideal != null && RouteVariants.compatible(ideal,other);
            rows.add(new Rows.Toggle(info.name() + (compatible ? "" : " · ⚠ старт/мир"),
                    compatible ? "Тот же старт (≤ 2 бл)" : "Предупреждение: старт дальше 2 бл или другой мир/измерение",
                    () -> s.alternatives.stream().anyMatch(a -> a.filename.equals(info.filename())), v -> {
                edit.accept(() -> { s.alternatives.removeIf(a -> a.filename.equals(info.filename()));
                    if(v) s.alternatives.add(new RouteRandomSettings.Alternative(info.filename())); }); rebuild.run();
            }).enabledIf(en));
            var a = s.alternatives.stream().filter(q -> q.filename.equals(info.filename())).findFirst().orElse(null);
            if(a != null) rows.add(new Rows.Number("Вес: " + info.name(), "0 — не выбирать",() -> a.weight,
                    v -> edit.accept(() -> a.weight=v),0,1000,.1,1,Rows::num).enabledIf(en));
        }
    }
}
