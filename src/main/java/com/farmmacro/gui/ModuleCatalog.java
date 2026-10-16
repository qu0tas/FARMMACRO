package com.farmmacro.gui;

import java.util.List;
import java.util.Set;

/** UI-only catalogue. Opening a card never authorizes or starts its controller. */
final class ModuleCatalog {
    static final String[] CATEGORIES = {"Все модули", "Фермы", "Combat", "Внешний вид", "Система"};
    static final String[] ICONS = {"K", "F", "C", "V", "S"};
    record Module(int category, int tab, String title, String description, String badge, Set<String> sections) {}
    private static Module m(int category,int tab,String title,String description,String badge,String... sections) {
        return new Module(category,tab,title,description,badge,Set.of(sections));
    }
    static final List<Module> ALL = List.of(
        m(1,3,"Фермы и маршруты","Конструктор схем, точки и сохранённые маршруты.","НАСТРОЙКИ","r.cur","r.farm","r.help","Кэш чанков","r.walk","r.copy","r.saved"),
        m(1,2,"Запись действий","Текущая запись и библиотека сохранённых макросов.","НАСТРОЙКИ","Текущий макрос","Сохранённые"),
        m(1,1,"Ручной запуск","Старт у точки, отсчёт, задержки рук и случайность.","НАСТРОЙКИ","l.loop","l.stop","l.start","l.human","l.delays","l.mouse"),
        m(1,7,"Kaelix Motion","Общее движение, камера, полёт и необязательный возврат.","НАСТРОЙКИ","Общее движение и камера","r.return","a.flight"),
        m(2,8,"Combat / Hunting","Профили, наведение и каталог разрешённых мобов.","ALPHA","combat.profiles","combat.aim","combat.catalog"),
        m(2,8,"Zealots / Special","Special: включи режим Zealots в Combat-профиле.","ALPHA","combat.profiles","combat.special"),
        m(2,8,"Паника Combat","Отдельные события и остановка боевого профиля.","НАСТРОЙКИ","combat.panic"),
        m(3,4,"Камера и пресеты","Сохранённые углы и привязки к источникам.","НАСТРОЙКИ","Применение","Пресеты","Выбранный пресет"),
        m(3,5,"HUD и редактор","Плашки, инвентарь, положение и масштаб.","НАСТРОЙКИ","v.hud","v.k2hud"),
        m(3,5,"Экономика / XP / питомец","Пассивная статистика, источники и ограничения.","НАСТРОЙКИ","v.stats","v.skill"),
        m(3,5,"Тема и оформление","Цвета, прозрачность, свои изображения и загрузка.","НАСТРОЙКИ","v.ui","Свое оформление","Экран загрузки Kaelix","CROP FEVER"),
        m(3,5,"Отображение маршрута","Линии, точки и собственные цвета маршрута.","НАСТРОЙКИ","v.route","v.colors"),
        m(3,5,"Freecam / обзор и окно","Freecam, Freelook, Low render, PiP и скрытие ника.","НАСТРОЙКИ","v.view","v.pip","v.nick"),
        m(3,5,"Косметика","China Hat и оформление персонажа.","НАСТРОЙКИ","v.hat","v.cheeks"),
        m(4,0,"Паника / Garden","События, передача управления, звук и журнал.","НАСТРОЙКИ","p.main","p.table","p.handoff","p.hands","p.tp","p.move","p.world","p.social","p.friends","p.server","p.susp","p.sound","p.screen","p.react","p.discord"),
        m(4,9,"Клавиши","Все зарегистрированные бинды и их конфликты.","НАСТРОЙКИ","kaelix.keybinds"),
        m(4,6,"Конфиги","Сохранение, импорт и экспорт общих параметров.","НАСТРОЙКИ","Готовые наборы","Мои профили","Строка настроек","Общая случайность"),
        m(4,5,"Производительность","PERF, ограничения и безопасный отчёт диагностики.","НАСТРОЙКИ","v.k014")
    );
    private ModuleCatalog() {}
}
