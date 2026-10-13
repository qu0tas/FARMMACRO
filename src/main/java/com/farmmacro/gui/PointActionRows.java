package com.farmmacro.gui;

import com.farmmacro.route.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

final class PointActionRows {
  static void build(List<Rows.Row> rows, RoutePoint point, Runnable changed) {
    if (point == null) return;
    var a = point.pointAction;
    int begin = rows.size();
    rows.add(new Rows.Section("Тип точки"));
    rows.add(
        new Rows.Selector(
            "Тип точки",
            "В простом режиме все точки исполняются как обычные",
            () ->
                List.of(
                    new Rows.Choice("ordinary", "Обычная"),
                    new Rows.Choice("wait", "Ждать N мс"),
                    new Rows.Choice("position", "Ждать позицию"),
                    new Rows.Choice("item", "ПКМ предметом"),
                    new Rows.Choice("jump", "Прыжок в точке"),
                    new Rows.Choice("command", "Команда в чат")),
            () -> a.type,
            v -> {
              a.type = v;
              changed.run();
            }));
    rows.add(
        new Rows.Number(
                "Ждать, мс",
                null,
                () -> a.waitMs,
                v -> {
                  a.waitMs = (int) v;
                  changed.run();
                },
                0,
                600000,
                50,
                500,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("wait")));
    rows.add(
        new Rows.Number(
                "Разброс ±, мс",
                null,
                () -> a.jitterMs,
                v -> {
                  a.jitterMs = (int) v;
                  changed.run();
                },
                0,
                600000,
                50,
                500,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("wait")));
    rows.add(
        new Rows.Number(
                "Радиус R, бл",
                "Ждём позицию после достижения точки; подходит для серверных поправок",
                () -> a.radius,
                v -> {
                  a.radius = v;
                  changed.run();
                },
                .05,
                30,
                .05,
                .5,
                Rows::num)
            .showIf(() -> a.type.equals("position")));
    rows.add(
        new Rows.Number(
                "Таймаут, мс",
                null,
                () -> a.timeoutMs,
                v -> {
                  a.timeoutMs = (int) v;
                  changed.run();
                },
                50,
                600000,
                100,
                1000,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("position")));
    rows.add(
        new Rows.Toggle(
                "Паника по таймауту",
                "Выкл — продолжить маршрут",
                () -> a.panicOnTimeout,
                v -> {
                  a.panicOnTimeout = v;
                  changed.run();
                })
            .showIf(() -> a.type.equals("position")));
    rows.add(
        new Rows.Number(
                "Слот предмета",
                "0 — текущий; id имеет приоритет",
                () -> a.slot,
                v -> {
                  a.slot = (int) v;
                  changed.run();
                },
                0,
                9,
                1,
                1,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("item")));
    rows.add(
        new Rows.TextField(
                "Id предмета",
                "minecraft:… либо SkyBlock id; ищем только в хотбаре",
                () -> a.itemId,
                v -> {
                  a.itemId = v.trim();
                  changed.run();
                  return true;
                },
                v -> true,
                128,
                140)
            .showIf(() -> a.type.equals("item")));
    rows.add(
        new Rows.Number(
                "Кулдаун ПКМ, мс",
                null,
                () -> a.cooldownMs,
                v -> {
                  a.cooldownMs = (int) v;
                  changed.run();
                },
                0,
                60000,
                50,
                500,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("item")));
    rows.add(
        new Rows.Toggle(
                "Свой блок для взгляда",
                "Иначе — блок под точкой",
                () -> a.lookBlock,
                v -> {
                  a.lookBlock = v;
                  changed.run();
                })
            .showIf(() -> a.type.equals("item")));
    rows.add(
        new Rows.Buttons(
                new Rows.Btn(
                    "Блок под прицелом",
                    Rows.Style.SECONDARY,
                    () -> {
                      var mc = Minecraft.getInstance();
                      if (mc.hitResult instanceof BlockHitResult b) {
                        a.lookBlock = true;
                        a.blockX = b.getBlockPos().getX();
                        a.blockY = b.getBlockPos().getY();
                        a.blockZ = b.getBlockPos().getZ();
                        changed.run();
                      }
                    }))
            .showIf(() -> a.type.equals("item")));
    rows.add(
        new Rows.Number(
                "Блок X",
                null,
                () -> a.blockX,
                v -> {
                  a.blockX = (int) v;
                  changed.run();
                },
                -30000000,
                30000000,
                1,
                10,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("item") && a.lookBlock));
    rows.add(
        new Rows.Number(
                "Блок Y",
                null,
                () -> a.blockY,
                v -> {
                  a.blockY = (int) v;
                  changed.run();
                },
                -2048,
                2048,
                1,
                10,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("item") && a.lookBlock));
    rows.add(
        new Rows.Number(
                "Блок Z",
                null,
                () -> a.blockZ,
                v -> {
                  a.blockZ = (int) v;
                  changed.run();
                },
                -30000000,
                30000000,
                1,
                10,
                Rows::num)
            .integer()
            .showIf(() -> a.type.equals("item") && a.lookBlock));
    rows.add(
        new Rows.TextField(
                "Команда / сообщение",
                null,
                () -> a.command,
                v -> {
                  a.command = RouteEndSettings.cleanCommand(v);
                  changed.run();
                  return true;
                },
                v -> !v.contains("\n") && !v.contains("\r"),
                256,
                150)
            .showIf(() -> a.type.equals("command")));
    for (int i = begin; i < rows.size(); i++) rows.get(i).adv();
  }
}
