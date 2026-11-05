package com.farmmacro.gui;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.route.PathJob;
import com.farmmacro.util.BugReport;
import com.farmmacro.util.Diagnostics;
import com.farmmacro.util.StepProfiler;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class PerformanceRows {
    public static void build(List<Rows.Row> rows, Runnable save) {
        var c = ModConfig.INSTANCE;
        rows.add(new Rows.Section("K2 · Производительность и диагностика").key("v.k014"));
        rows.add(new Rows.Note(
                "F9 / PERF: прежние FPS, облака, тени и частицы. Опции ниже действуют только пока PERF включён. Low render (F7) и отпуск курсора (U) остаются отдельными; фокус окна не подделывается.",
                Ui.SUB));
        rows.add(new Rows.Number(
                        "Фоновый FPS: 0 = без доп. лимита",
                        "Только уменьшает ванильный лимит при неактивном окне. 0 выключено; свёрнутое/AFK-окно не ускоряет. Тревога не замедляется этим дополнением",
                        () -> c.perfBackgroundFps,
                        v -> {
                            c.perfBackgroundFps = (int) v;
                            save.run();
                        },
                        0,
                        120,
                        5,
                        20,
                        v -> (int) v == 0 ? "Выкл" : (int) v + " FPS")
                .integer());
        rows.add(new Rows.Number(
                        "PERF: лимит чанков (0 = прежний)",
                        "Временно снижает дальность до заданной, но не повышает. Возвращает прежнюю, если значение не изменено вручную. Меняет только прорисовку, не сервер и не радиус навигации",
                        () -> c.perfChunks,
                        v -> {
                            c.perfChunks = (int) v == 1 ? 2 : (int) v;
                            save.run();
                        },
                        0,
                        32,
                        1,
                        4,
                        v -> (int) v == 0 ? "Выкл" : (int) v + " chunks")
                .integer());
        rows.add(new Rows.Toggle(
                "PERF: убрать осколки ломания",
                "Только локальные частицы удара/разрушения блока; не пакеты частиц Tracker и не события ломания",
                () -> c.perfBlockParticles,
                v -> {
                    c.perfBlockParticles = v;
                    save.run();
                }));
        rows.add(new Rows.Number(
                        "PERF: громкость окружения",
                        "Блоки, враги, животные, окружение и погода. MASTER/паника, игроки, UI и музыка не затрагиваются. Для новых/пересчитанных звуков; уже запущенные могут сохранить прежнюю громкость",
                        () -> c.perfWorldVolume,
                        v -> {
                            c.perfWorldVolume = (int) v;
                            save.run();
                        },
                        0,
                        100,
                        5,
                        20,
                        v -> (int) v + "%")
                .integer());
        rows.add(new Rows.Note(
                "Пока значение совпадает с установленным PERF — оно принадлежит режиму. Ручные изменения не перетираются при выключении. Смена полного профиля сначала выключает PERF. При выходе из мира временные настройки возвращаются.",
                Ui.SUB));
        rows.add(new Rows.Note(() -> Diagnostics.summary(), Ui.SUB));
        rows.add(new Rows.Note(() -> "Дольше всего: " + StepProfiler.summary(3), Ui.SUB));
        rows.add(new Rows.Buttons(new Rows.Btn("Сбросить замеры шагов", Rows.Style.SECONDARY, StepProfiler::reset)));
        rows.add(new Rows.Note(
                () -> "Navigation: " + NavigationInput.technicalState() + " • " + PathJob.technicalState(), Ui.SUB));
        rows.add(new Rows.Buttons(new Rows.Btn("Скопировать общий отчёт", Rows.Style.SECONDARY, () -> {
            var mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(Diagnostics.report(mc));
            if (mc.player != null)
                mc.player.sendOverlayMessage(Component.literal("Kaelix: технический отчёт скопирован"));
        })));
        rows.add(new Rows.Buttons(
                new Rows.Btn(
                        "ZIP для багрепорта", Rows.Style.SECONDARY, () -> BugReport.create(Minecraft.getInstance())),
                new Rows.Btn("Папка отчётов", Rows.Style.SECONDARY, () -> {
                    try {
                        Files.createDirectories(BugReport.dir());
                        Util.getPlatform().openPath(BugReport.dir());
                    } catch (Exception e) {
                        FarmMacroMod.LOGGER.warn("Не удалось открыть папку отчётов: {}", e.toString());
                    }
                })));
        rows.add(new Rows.Note(
                () -> {
                    String s = BugReport.status();
                    return s.isEmpty()
                            ? "ZIP: отчёт + моды + конфиг без секретов + хвост latest.log с замаскированными ником/IP/ссылками. Ничего не отправляется"
                            : s;
                },
                Ui.SUB));
        rows.add(new Rows.Note(
                "Только версия, zealots/custom, enum-состояния, причины отказа и счётчики. Без чата, ников, координат, сервера, имён профилей, предметов и секретов. 128 переходов в памяти; диск/сеть не используются. Не FPS-бенчмарк.",
                Ui.SUB));
    }

    private PerformanceRows() {}
}
