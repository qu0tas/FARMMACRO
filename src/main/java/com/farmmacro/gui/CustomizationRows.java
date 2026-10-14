package com.farmmacro.gui;

import com.farmmacro.config.*;
import java.util.*;

final class CustomizationRows {
  private static String panel = "all";

  private static Rows.Row color(
      String label,
      java.util.function.Supplier<String> get,
      java.util.function.Consumer<String> set,
      Runnable save) {
    return new Rows.TextField(
        label,
        "#RRGGBB; пусто — цвет темы",
        get,
        v -> {
          if (!UiStyle.validColor(v)) return false;
          set.accept(v);
          save.run();
          return true;
        },
        v -> v.matches("#?[0-9a-fA-F]*"),
        7,
        90);
  }

  static void build(List<Rows.Row> rows, Runnable save) {
    var c = ModConfig.INSTANCE;
    rows.add(new Rows.Section("Экран загрузки Kaelix").closed());
    rows.add(
        new Rows.Toggle(
            "Загрузка в стиле Kaelix",
            "Выкл — обычный экран Mojang при следующей загрузке",
            () -> c.kaelixLoading,
            v -> {
              c.kaelixLoading = v;
              save.run();
            }));
    rows.add(
        new Rows.Toggle(
            "Foxy и Fortnite",
            "Только рисунки, без звуков и скримеров",
            () -> c.loadingMemes,
            v -> {
              c.loadingMemes = v;
              save.run();
            }));
    rows.add(
        new Rows.Toggle(
            "Анимация загрузки",
            "Выкл — неподвижные логотип и персонажи",
            () -> c.loadingMotion,
            v -> {
              c.loadingMotion = v;
              save.run();
            }));
    rows.add(new Rows.Section("Свое оформление").closed());
    rows.add(
        new Rows.Toggle(
            "Свои цвета меню",
            null,
            () -> c.uiCustomColors,
            v -> {
              c.uiCustomColors = v;
              save.run();
            }));
    rows.add(
        color(
                "WINDOW",
                () -> c.uiColors.getOrDefault("WINDOW", ""),
                v -> c.uiColors.put("WINDOW", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "SIDEBAR",
                () -> c.uiColors.getOrDefault("SIDEBAR", ""),
                v -> c.uiColors.put("SIDEBAR", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "HEADER",
                () -> c.uiColors.getOrDefault("HEADER", ""),
                v -> c.uiColors.put("HEADER", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "CARD",
                () -> c.uiColors.getOrDefault("CARD", ""),
                v -> c.uiColors.put("CARD", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "CARD_HOVER",
                () -> c.uiColors.getOrDefault("CARD_HOVER", ""),
                v -> c.uiColors.put("CARD_HOVER", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "FIELD",
                () -> c.uiColors.getOrDefault("FIELD", ""),
                v -> c.uiColors.put("FIELD", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "BORDER",
                () -> c.uiColors.getOrDefault("BORDER", ""),
                v -> c.uiColors.put("BORDER", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "TEXT",
                () -> c.uiColors.getOrDefault("TEXT", ""),
                v -> c.uiColors.put("TEXT", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color("SUB", () -> c.uiColors.getOrDefault("SUB", ""), v -> c.uiColors.put("SUB", v), save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color("DIM", () -> c.uiColors.getOrDefault("DIM", ""), v -> c.uiColors.put("DIM", v), save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "ACCENT",
                () -> c.uiColors.getOrDefault("ACCENT", ""),
                v -> c.uiColors.put("ACCENT", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "ACCENT_HI",
                () -> c.uiColors.getOrDefault("ACCENT_HI", ""),
                v -> c.uiColors.put("ACCENT_HI", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color("ON", () -> c.uiColors.getOrDefault("ON", ""), v -> c.uiColors.put("ON", v), save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "DANGER",
                () -> c.uiColors.getOrDefault("DANGER", ""),
                v -> c.uiColors.put("DANGER", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "WARN",
                () -> c.uiColors.getOrDefault("WARN", ""),
                v -> c.uiColors.put("WARN", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "TRACK_OFF",
                () -> c.uiColors.getOrDefault("TRACK_OFF", ""),
                v -> c.uiColors.put("TRACK_OFF", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "TOGGLE_ON",
                () -> c.uiColors.getOrDefault("TOGGLE_ON", ""),
                v -> c.uiColors.put("TOGGLE_ON", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "KNOB_ON",
                () -> c.uiColors.getOrDefault("KNOB_ON", ""),
                v -> c.uiColors.put("KNOB_ON", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "KNOB_OFF",
                () -> c.uiColors.getOrDefault("KNOB_OFF", ""),
                v -> c.uiColors.put("KNOB_OFF", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "ON_ACCENT",
                () -> c.uiColors.getOrDefault("ON_ACCENT", ""),
                v -> c.uiColors.put("ON_ACCENT", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "TOOLTIP",
                () -> c.uiColors.getOrDefault("TOOLTIP", ""),
                v -> c.uiColors.put("TOOLTIP", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        color(
                "HUD_BG",
                () -> c.uiColors.getOrDefault("HUD_BG", ""),
                v -> c.uiColors.put("HUD_BG", v),
                save)
            .showIf(() -> c.uiCustomColors)
            .adv());
    rows.add(
        new Rows.Number(
                "Непрозрачность меню, %",
                null,
                () -> c.uiBackgroundOpacity,
                v -> {
                  c.uiBackgroundOpacity = (int) v;
                  save.run();
                },
                0,
                100,
                5,
                10,
                Rows::num)
            .integer());
    rows.add(
        new Rows.Number(
                "Непрозрачность HUD, %",
                null,
                () -> c.hudBackgroundOpacity,
                v -> {
                  c.hudBackgroundOpacity = (int) v;
                  save.run();
                },
                0,
                100,
                5,
                10,
                Rows::num)
            .integer());
    rows.add(
        new Rows.Number(
            "Скругление углов",
            null,
            () -> c.uiCornerScale,
            v -> {
              c.uiCornerScale = v;
              save.run();
            },
            0,
            1,
            .05,
            .1,
            Rows::num));
    rows.add(
        new Rows.Toggle(
            "Мягкие тени",
            null,
            () -> c.uiSoftShadows,
            v -> {
              c.uiSoftShadows = v;
              save.run();
            }));
    rows.add(
        new Rows.Toggle(
            "Свои PNG-фоны и иконки",
            "config/farmmacro/ui: background.png, hud.png, icons/<id>.png",
            () -> c.customUiImages,
            v -> {
              c.customUiImages = v;
              if (v) CustomUiAssets.reload();
              save.run();
            }));
    rows.add(
        new Rows.Buttons(
            new Rows.Btn(
                "Перезагрузить изображения", Rows.Style.SECONDARY, CustomUiAssets::reload)));
    rows.add(new Rows.Note(() -> CustomUiAssets.status, Ui.SUB));
    rows.add(
        new Rows.Note(
            "За край окна можно перетаскивать меню; двойной клик по заголовку — центр. F8: двигать"
                + " и масштабировать HUD.",
            Ui.SUB));
    rows.add(
        new Rows.Selector(
            "Оформить плашку",
            null,
            () -> {
              var list = new ArrayList<Rows.Choice>();
              list.add(new Rows.Choice("all", "Общая плашка"));
              for (var b : com.farmmacro.hud.FarmHud.BLOCKS)
                list.add(new Rows.Choice(b.id(), b.label()));
              return list;
            },
            () -> panel,
            v -> panel = v));
    rows.add(
        color(
            "Фон выбранной плашки",
            () -> slot().backgroundColor,
            v -> slot().backgroundColor = v,
            save));
    rows.add(
        color(
            "Полоска выбранной плашки",
            () -> slot().accentColor,
            v -> slot().accentColor = v,
            save));
    rows.add(
        new Rows.Number(
                "Прозрачность плашки, %",
                "−1 — общая настройка HUD",
                () -> slot().opacity,
                v -> {
                  slot().opacity = (int) v;
                  save.run();
                },
                -1,
                100,
                1,
                10,
                Rows::num)
            .integer());
    rows.add(
        new Rows.Buttons(
            new Rows.Btn(
                "Сбросить оформление",
                Rows.Style.SECONDARY,
                () -> {
                  c.uiColors.clear();
                  c.uiCustomColors = false;
                  c.customUiImages = false;
                  c.uiBackgroundOpacity = 100;
                  c.hudBackgroundOpacity = 78;
                  c.uiCornerScale = .35;
                  c.uiSoftShadows = false;
                  for (var s : c.hudSlots.values()) {
                    s.backgroundColor = "";
                    s.accentColor = "";
                    s.opacity = -1;
                  }
                  save.run();
                })));
    rows.add(new Rows.Section("CROP FEVER").closed());
    rows.add(
        new Rows.Toggle(
            "Танцоры CROP FEVER",
            "Системный чат / титр; исчезают в конце, через 0.5 с — мем",
            () -> c.cropFeverEnabled,
            v -> {
              c.cropFeverEnabled = v;
              save.run();
            }));
    rows.add(
        new Rows.Number(
                "Громкость, %",
                "Учитывает громкость игры",
                () -> c.cropFeverVolume,
                v -> {
                  c.cropFeverVolume = (int) v;
                  save.run();
                },
                0,
                100,
                5,
                10,
                Rows::num)
            .integer());
    rows.add(
        new Rows.Number(
                "Количество танцоров",
                null,
                () -> c.cropFeverDancers,
                v -> {
                  c.cropFeverDancers = (int) v;
                  save.run();
                },
                1,
                8,
                1,
                1,
                Rows::num)
            .integer());
    rows.add(
        new Rows.Number(
                "Размер танцоров, % экрана",
                null,
                () -> c.cropFeverScale,
                v -> {
                  c.cropFeverScale = (int) v;
                  save.run();
                },
                5,
                40,
                1,
                5,
                Rows::num)
            .integer());
    rows.add(new Rows.Toggle(
        "Светомузыка CROP FEVER",
        "Цветные переливы и мягкая пульсация; при чувствительности к мерцанию выключите",
        () -> c.cropFeverLights, v -> { c.cropFeverLights = v; save.run(); }));
    rows.add(new Rows.Number(
        "Сила светомузыки, %",
        "0 — без эффекта; цвета полупрозрачные, танцоры остаются поверх",
        () -> c.cropFeverLightStrength,
        v -> { c.cropFeverLightStrength = (int) v; save.run(); },
        0, 100, 5, 10, Rows::num).integer().showIf(() -> c.cropFeverLights));
    rows.add(new Rows.Section("Навигация и камера авто-возврата").closed());
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
  }

  private static HudSlot slot() {
    return ModConfig.INSTANCE.hudSlots.computeIfAbsent(panel, k -> new HudSlot());
  }
}
