package com.farmmacro.gui;

import com.farmmacro.route.*;
import java.util.*;
import java.util.function.*;

final class RouteEndRows {
  static void build(
      List<Rows.Row> rows, RouteEndSettings s, BooleanSupplier en, Consumer<Runnable> edit) {
    rows.add(new Rows.Section("Конец маршрута"));
    rows.add(
        new Rows.Selector(
                "Режим",
                null,
                () ->
                    List.of(
                        new Rows.Choice("none", "Ничего"),
                        new Rows.Choice("walk", "Пройти дальше"),
                        new Rows.Choice("command", "Rewarp-команда"),
                        new Rows.Choice("both", "Пройти дальше + команда")),
                () -> s.mode,
                v -> edit.accept(() -> s.mode = v))
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "Пройти дальше, бл",
                "До края и падения; не больше заданного расстояния",
                () -> s.distance,
                v -> edit.accept(() -> s.distance = v),
                1,
                30,
                1,
                5,
                Rows::num)
            .showIf(s::walks)
            .enabledIf(en));
    rows.add(
        new Rows.Selector(
                "Направление выхода",
                null,
                () ->
                    List.of(
                        new Rows.Choice("auto", "По ходу"),
                        new Rows.Choice("+x", "+X"),
                        new Rows.Choice("-x", "−X"),
                        new Rows.Choice("+z", "+Z"),
                        new Rows.Choice("-z", "−Z")),
                () -> s.direction,
                v -> edit.accept(() -> s.direction = v))
            .showIf(s::walks)
            .enabledIf(en));
    rows.add(
        new Rows.TextField(
                "Rewarp-команда",
                null,
                () -> s.command,
                v -> {
                  edit.accept(() -> s.command = RouteEndSettings.cleanCommand(v));
                  return true;
                },
                v -> !v.contains("\n") && !v.contains("\r"),
                256,
                160)
            .showIf(s::commands)
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "До команды: от, мс",
                null,
                () -> s.delayMinMs,
                v ->
                    edit.accept(
                        () -> {
                          s.delayMinMs = (int) v;
                          s.delayMaxMs = Math.max(s.delayMaxMs, s.delayMinMs);
                        }),
                0,
                60000,
                50,
                500,
                Rows::num)
            .integer()
            .showIf(s::commands)
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "До команды: до, мс",
                null,
                () -> s.delayMaxMs,
                v ->
                    edit.accept(
                        () -> {
                          s.delayMaxMs = (int) v;
                          s.delayMinMs = Math.min(s.delayMinMs, s.delayMaxMs);
                        }),
                0,
                60000,
                50,
                500,
                Rows::num)
            .integer()
            .showIf(s::commands)
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "Окно телепорта, с",
                "Один телепорт на событие. Другие детекторы не отключаются",
                () -> s.windowSeconds,
                v -> edit.accept(() -> s.windowSeconds = (int) v),
                1,
                60,
                1,
                5,
                Rows::num)
            .integer()
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "Пауза после телепорта, мс",
                null,
                () -> s.postPauseMs,
                v -> edit.accept(() -> s.postPauseMs = (int) v),
                0,
                60000,
                50,
                500,
                Rows::num)
            .integer()
            .enabledIf(en));
    rows.add(
        new Rows.Number(
                "Разброс паузы ±, мс",
                null,
                () -> s.postJitterMs,
                v -> edit.accept(() -> s.postJitterMs = (int) v),
                0,
                60000,
                50,
                500,
                Rows::num)
            .integer()
            .enabledIf(en));
    rows.add(
        new Rows.Note(
            "Нет телепорта вовремя → паника. После него — точка 1, при необходимости поиск пути до"
                + " старта.",
            Ui.SUB));
  }
}
