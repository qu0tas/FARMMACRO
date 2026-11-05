package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.fishing.AutoFish;
import com.farmmacro.macro.MacroManager;
import java.util.*;

/** One isolated settings section/card; UI never starts work just by opening it. */
public final class FishingRows {
    public static void build(List<Rows.Row> rows, Runnable save) {
        var c = ModConfig.INSTANCE.fishing;
        var fish = AutoFish.INSTANCE;
        rows.add(new Rows.Section("Рыбалка / AutoFish — ALPHA").key("fishing.main"));
        rows.add(new Rows.Note(
                "На основе lyn1dev/skyblock-autofisher (CC0). Разрешение не запускает рыбалку. Первый заброс вручную, затем только свой поплавок и !!!. Автоматизация может нарушать правила сервера; обычный useItem не гарантирует отсутствие бана.",
                Ui.WARN));
        rows.add(new Rows.Toggle(
                "Разрешить AutoFish",
                "Не автозапуск. После входа/паники/меню нужен новый старт",
                () -> c.enabled,
                v -> {
                    c.enabled = v;
                    if (!v) fish.stop("разрешение выключено");
                    save.run();
                }));
        rows.add(new Rows.Buttons(
                new Rows.Btn("Запуск после закрытия меню", Rows.Style.PRIMARY, fish::armFromGui),
                new Rows.Btn("Стоп", Rows.Style.SECONDARY, () -> fish.stop("остановлено вручную"))));
        rows.add(new Rows.Note(fish::status, Ui.TEXT));
        rows.add(new Rows.Note(() -> fish.counters() + " · " + fish.technical(), Ui.SUB));
        rows.add(new Rows.Note(
                () -> "Бинд: " + MacroManager.keyName(FarmMacroMod.keyFishing)
                        + " · назначь в Клавиши. Подготовка из GUI: закрыть за 15 с. End — стоп. Первый заброс — до 60 с.",
                Ui.SUB));
        rows.add(new Rows.Toggle(
                "Перезаброс",
                "После подтверждённого исчезновения своего поплавка; один заброс и ожидание нового, без спама повторов",
                () -> c.recast,
                v -> {
                    c.recast = v;
                    save.run();
                }));
        rows.add(new Rows.Toggle(
                "Fireveil / ПКМ предметом",
                "Только точное системное сообщение из списка после своей подсечки; слот → ПКМ → удочка. Предмет выбираешь сам; проверяй слот и оружие",
                () -> c.fireveil,
                v -> {
                    c.fireveil = v;
                    fish.stop("настройка Fireveil изменена");
                    save.run();
                }));
        rows.add(new Rows.Number(
                        "Слот Fireveil",
                        "Отдельный от удочки. По умолчанию второй как в исходнике. Одна активация, cooldown 5 с; без наведения и движения",
                        () -> c.toolSlot,
                        v -> {
                            c.toolSlot = (int) v;
                            fish.stop("слот Fireveil изменён");
                            save.run();
                        },
                        1,
                        9,
                        1,
                        2,
                        v -> Rows.num(v))
                .under(() -> c.fireveil));
        rows.add(new Rows.Number(
                "Подсечка: минимум",
                "Миллисекунды; фактическая точность ограничена клиентским тиком ~50 мс",
                () -> c.reelMinMs,
                v -> {
                    c.reelMinMs = (int) v;
                    c.sanitize();
                    save.run();
                },
                50,
                2000,
                10,
                50,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Подсечка: максимум",
                "Два слоя случайной задержки, исходные границы 56–129 мс",
                () -> c.reelMaxMs,
                v -> {
                    c.reelMaxMs = (int) v;
                    c.sanitize();
                    save.run();
                },
                50,
                3000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                        "Перезаброс: минимум",
                        "Исходные границы 390–980 мс, три слоя; не гарантия человекоподобности",
                        () -> c.recastMinMs,
                        v -> {
                            c.recastMinMs = (int) v;
                            c.sanitize();
                            save.run();
                        },
                        200,
                        5000,
                        50,
                        200,
                        v -> Rows.num(v) + " мс")
                .under(() -> c.recast));
        rows.add(new Rows.Number(
                        "Перезаброс: максимум",
                        "Отсчёт после исчезновения поплавка/возврата удочки",
                        () -> c.recastMaxMs,
                        v -> {
                            c.recastMaxMs = (int) v;
                            c.sanitize();
                            save.run();
                        },
                        200,
                        8000,
                        50,
                        200,
                        v -> Rows.num(v) + " мс")
                .under(() -> c.recast));
        rows.add(new Rows.Number(
                "Ожидание поклёвки",
                "Таймаут — локальный стоп, без слепых забросов. Настрой под свою скорость рыбалки",
                () -> c.biteTimeoutSec,
                v -> {
                    c.biteTimeoutSec = (int) v;
                    save.run();
                },
                10,
                600,
                10,
                60,
                v -> Rows.num(v) + " с"));
        rows.add(new Rows.Toggle(
                "Отладка рыбалки",
                "Локальный чат только на действии: подсечка/заброс/слот/Fireveil. На сервер текст не отправляется",
                () -> c.debug,
                v -> {
                    c.debug = v;
                    save.run();
                }));
        rows.add(new Rows.Section("Рыбалка: задержки Fireveil").key("fishing.delays"));
        rows.add(new Rows.Number(
                "Fireveil: до слота",
                "Базовая задержка",
                () -> c.selectBaseMs,
                v -> {
                    c.selectBaseMs = (int) v;
                    save.run();
                },
                50,
                2000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Fireveil: разброс слота",
                "Добавляется 0…значение",
                () -> c.selectSpreadMs,
                v -> {
                    c.selectSpreadMs = (int) v;
                    save.run();
                },
                0,
                1000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Fireveil: до ПКМ",
                "Базовая задержка после выбора слота",
                () -> c.useBaseMs,
                v -> {
                    c.useBaseMs = (int) v;
                    save.run();
                },
                50,
                2000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Fireveil: разброс ПКМ",
                "Добавляется 0…значение",
                () -> c.useSpreadMs,
                v -> {
                    c.useSpreadMs = (int) v;
                    save.run();
                },
                0,
                1000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Fireveil: возврат удочки",
                "Базовая задержка после ПКМ",
                () -> c.returnBaseMs,
                v -> {
                    c.returnBaseMs = (int) v;
                    save.run();
                },
                50,
                2000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Number(
                "Fireveil: разброс возврата",
                "Добавляется 0…значение",
                () -> c.returnSpreadMs,
                v -> {
                    c.returnSpreadMs = (int) v;
                    save.run();
                },
                0,
                1000,
                10,
                100,
                v -> Rows.num(v) + " мс"));
        rows.add(new Rows.Note(
                "Смена предмета, окно, смерть, мир, большой перенос, паника, другая автоматика — стоп без автопродолжения. При аварии на Fireveil слот не восстанавливается сам: управление уже у тебя. Обычная рыбалка не двигает мышь и персонажа; старые макросы и стол экспериментов не включаются.",
                Ui.SUB));
    }

    private FishingRows() {}
}
