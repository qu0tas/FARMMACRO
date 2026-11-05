package com.farmmacro.gui;

import java.util.List;
import java.util.Set;

/** UI-only catalogue. Opening a card never authorizes or starts its controller. */
final class ModuleCatalog {
    static final String[] CATEGORIES = {"Все модули", "Фермы", "Combat", "Внешний вид", "Система"};
    static final String[] ICONS = {"K", "F", "C", "V", "S"};
    /** {@code title}/{@code description} — русский текст по умолчанию; перевод — ключи farmmacro.module.&lt;id&gt;.* в lang. */
    record Module(
            int category, int tab, String id, String title, String description, String badge, Set<String> sections) {
        @Override
        public String title() {
            return Tr.t("farmmacro.module." + id + ".title", title);
        }

        @Override
        public String description() {
            return Tr.t("farmmacro.module." + id + ".desc", description);
        }
    }

    private static Module m(
            int category, int tab, String id, String title, String description, String badge, String... sections) {
        return new Module(category, tab, id, title, description, badge, Set.of(sections));
    }

    /** Название категории на языке игры (по умолчанию — русское из {@link #CATEGORIES}). */
    static String category(int i) {
        return Tr.t("farmmacro.category." + i, CATEGORIES[i]);
    }

    static final List<Module> ALL = List.of(
            m(
                    1,
                    3,
                    "farms",
                    "Фермы и маршруты",
                    "Конструктор схем, точки и сохранённые маршруты.",
                    "НАСТРОЙКИ",
                    "r.cur",
                    "r.farm",
                    "r.help",
                    "Кэш чанков",
                    "r.walk",
                    "r.copy",
                    "r.clip",
                    "r.saved"),
            m(
                    1,
                    2,
                    "recording",
                    "Запись действий",
                    "Текущая запись и библиотека сохранённых макросов.",
                    "НАСТРОЙКИ",
                    "Текущий макрос",
                    "Сохранённые"),
            m(
                    1,
                    1,
                    "launch",
                    "Ручной запуск",
                    "Старт у точки, отсчёт, задержки рук и случайность.",
                    "НАСТРОЙКИ",
                    "l.loop",
                    "l.stop",
                    "l.start",
                    "l.human",
                    "l.delays",
                    "l.mouse"),
            m(
                    1,
                    7,
                    "motion",
                    "Kaelix Motion",
                    "Общее движение, камера, полёт и необязательный возврат.",
                    "НАСТРОЙКИ",
                    "Общее движение и камера",
                    "r.return",
                    "a.flight"),
            m(
                    2,
                    8,
                    "combat",
                    "Combat / Hunting",
                    "Профили, наведение и каталог разрешённых мобов.",
                    "НАСТРОЙКИ",
                    "combat.profiles",
                    "combat.aim",
                    "combat.catalog"),
            m(
                    2,
                    8,
                    "zealots",
                    "Zealots / Special",
                    "Special: включи режим Zealots в Combat-профиле.",
                    "НАСТРОЙКИ",
                    "combat.profiles",
                    "combat.special"),
            m(
                    2,
                    8,
                    "combat_panic",
                    "Паника Combat",
                    "Отдельные события и остановка боевого профиля.",
                    "НАСТРОЙКИ",
                    "combat.panic"),
            m(
                    3,
                    4,
                    "camera",
                    "Камера и пресеты",
                    "Сохранённые углы и привязки к источникам.",
                    "НАСТРОЙКИ",
                    "Применение",
                    "Пресеты",
                    "Выбранный пресет"),
            m(
                    3,
                    5,
                    "hud",
                    "HUD и редактор",
                    "Плашки, инвентарь, положение и масштаб.",
                    "НАСТРОЙКИ",
                    "v.hud",
                    "v.k2hud"),
            m(
                    3,
                    5,
                    "economy",
                    "Экономика / XP / питомец",
                    "Пассивная статистика, источники, ограничения и история запусков.",
                    "НАСТРОЙКИ",
                    "v.stats",
                    "v.history",
                    "v.skill"),
            m(
                    3,
                    5,
                    "theme",
                    "Тема и оформление",
                    "Цвета, прозрачность, свои изображения и загрузка.",
                    "НАСТРОЙКИ",
                    "v.ui",
                    "Свое оформление",
                    "Язык интерфейса",
                    "Экран загрузки Kaelix",
                    "CROP FEVER"),
            m(
                    3,
                    5,
                    "route_display",
                    "Отображение маршрута",
                    "Линии, точки и собственные цвета маршрута.",
                    "НАСТРОЙКИ",
                    "v.route",
                    "v.colors"),
            m(
                    3,
                    5,
                    "freecam",
                    "Freecam / обзор и окно",
                    "Freecam, Freelook, Low render, PiP и скрытие ника.",
                    "НАСТРОЙКИ",
                    "v.view",
                    "v.pip",
                    "v.nick"),
            m(3, 5, "cosmetics", "Косметика", "China Hat и оформление персонажа.", "НАСТРОЙКИ", "v.hat", "v.cheeks"),
            m(
                    4,
                    0,
                    "panic_garden",
                    "Паника / Garden",
                    "События, задержка остановки фермы, звук и журнал.",
                    "НАСТРОЙКИ",
                    "p.main",
                    "p.table",
                    "p.handoff",
                    "p.hands",
                    "p.tp",
                    "p.move",
                    "p.world",
                    "p.social",
                    "p.friends",
                    "p.server",
                    "p.susp",
                    "p.sound",
                    "p.screen",
                    "p.react",
                    "p.discord"),
            m(4, 9, "keys", "Клавиши", "Все зарегистрированные бинды и их конфликты.", "НАСТРОЙКИ", "kaelix.keybinds"),
            m(
                    4,
                    6,
                    "configs",
                    "Конфиги",
                    "Сохранение, импорт и экспорт общих параметров.",
                    "НАСТРОЙКИ",
                    "Готовые наборы",
                    "Мои профили",
                    "Строка настроек",
                    "Общая случайность"),
            m(
                    1,
                    7,
                    "fishing",
                    "Рыбалка / AutoFish",
                    "Подсечка, перезаброс и Fireveil для морских существ.",
                    "НАСТРОЙКИ",
                    "fishing.main",
                    "fishing.delays"),
            m(
                    4,
                    5,
                    "performance",
                    "Производительность",
                    "PERF, ограничения, замеры шагов и безопасный отчёт/ZIP.",
                    "НАСТРОЙКИ",
                    "v.k014"));

    private ModuleCatalog() {}
}
