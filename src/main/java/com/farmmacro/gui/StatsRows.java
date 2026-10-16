package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import com.farmmacro.stats.*;
import java.util.*;
/** Existing Visual tab: no new keys and no second settings tree for automation. */
public final class StatsRows {
 private static long confirmSession,confirmAll;
 public static void build(List<Rows.Row> rows,Runnable save){var c=ModConfig.INSTANCE.telemetry;
  rows.add(new Rows.Section("K2 HUD / General").key("v.k2hud"));
  rows.add(new Rows.Toggle("Статистика наблюдений","Пассивно; не запускает макросы, покупки или меню. При выключении сессия сбрасывается, прежний файл итогов остаётся",()->c.enabled,v->{c.enabled=v;if(!v){Telemetry.resetSession();StatsPrices.disabled();}save.run();}));
  rows.add(new Rows.Toggle("Плашки экономики, XP и питомца","Отключает только эти новые карточки; старый HUD/паника/управление остаются",()->c.hud,v->{c.hud=v;save.run();}));
  rows.add(new Rows.Toggle("Compact","Меньше строк и без графика; новые карточки слева, старые позиции справа сохранены",()->c.compact,v->{c.compact=v;save.run();}));
  rows.add(new Rows.Selector("Тема HUD","Не меняет тему меню, позиции или индивидуальные цвета F8",()->List.of(new Rows.Choice("SLATE","Slate / тёмный"),new Rows.Choice("GLASS","Deep blue / стекло"),new Rows.Choice("PAPER","Paper / светлый")),()->c.theme,v->{c.theme=v;save.run();}));
  rows.add(new Rows.Toggle("General: активная задача","Состояние, цель, подсказка перезапуска, удержание Shift. Pending-паника показывается независимо от флага",()->c.general,v->{c.general=v;save.run();}));
  rows.add(new Rows.Toggle("Combat","Текущий профиль, цель/огонь/Special и Shift, только когда модуль активен",()->c.combat,v->{c.combat=v;save.run();}));
  rows.add(new Rows.Toggle("Farms","Статусы фермы; навыки и питомец настраиваются отдельно ниже",()->c.farms,v->{c.farms=v;save.run();}));
  rows.add(new Rows.Toggle("Navigation","Существующие навигатор/возврат; управление ими не меняется",()->c.navigation,v->{c.navigation=v;save.run();}));
  rows.add(new Rows.Section("Экономика: источники и пределы").key("v.stats"));
  rows.add(new Rows.Note("Торговые модули удалены: новые квитанции от них не поступают. Старые локальные итоги сохранены, это не текущая прибыль фарма. Оценка инвентаря — только встроенные цены NPC, без запросов к Базару. Session = наблюдаемая сессия, не таймер Scheduler.",Ui.WARN));
  rows.add(new Rows.Note(()->"Поток "+com.farmmacro.hud.StatsHud.num(Telemetry.net())+" coins • "+Telemetry.receipts()+" квитанций • "+com.farmmacro.hud.StatsHud.num(Telemetry.perHour())+"/ч (наблюдаемое время SkyBlock)",Ui.SUB));
  rows.add(new Rows.Toggle("Rolling-график квитанций","30 минут coins/мин, знак сохранён, серое = нет наблюдения. Последняя минута неполная. Только полный режим",()->c.graph,v->{c.graph=v;save.run();}));
  rows.add(new Rows.Note(StatsPrices::status,Ui.SUB));
  rows.add(new Rows.Note(()->StatsPrices.source()+" • инвентарь ≈ "+com.farmmacro.hud.StatsHud.num(Telemetry.inventory())+" • "+Telemetry.coverage()+" • Hunting shards ≈ "+com.farmmacro.hud.StatsHud.num(Telemetry.shards()),Ui.SUB));
  rows.add(new Rows.Toggle("Daily / lifetime локально","OFF. Все профили/аккаунты устройства вместе, локальная календарная дата. Только суммы/XP, без ников/UUID/серверов/чата; файл statistics.json. Не переносится профилями",()->c.save,v->{c.save=v;if(v)StatsLedger.init();else StatsLedger.disabled();save.run();}));
  rows.add(new Rows.Note(StatsLedger::status,Ui.SUB));
  rows.add(new Rows.Buttons(new Rows.Btn(()->System.nanoTime()<confirmSession?"Подтвердить сброс сессии":"Сбросить сессию статистики",()->Rows.Style.SECONDARY,()->{long now=System.nanoTime();if(now>=confirmSession){confirmSession=now+3_000_000_000L;return;}confirmSession=0;Telemetry.resetSession();}),
    new Rows.Btn(()->System.nanoTime()<confirmAll?"Подтвердить сброс ВСЕГО":"Сбросить daily / lifetime",()->Rows.Style.DANGER,()->{long now=System.nanoTime();if(now>=confirmAll){confirmAll=now+3_000_000_000L;return;}confirmAll=0;Telemetry.resetAll();}).enabledIf(()->c.save)));
  rows.add(new Rows.Section("Навыки / питомец").key("v.skill"));
  rows.add(new Rows.Toggle("HUD навыка","Одна карточка выбранного навыка, независимо от флага Farms",()->c.skillHud,v->{c.skillHud=v;save.run();}));
  rows.add(new Rows.Selector("Навык XP","AUTO: Combat/Farms по задаче, иначе последний свежий навык. Не угадывает XP по убийствам",()->{List<Rows.Choice> choices=new ArrayList<>();choices.add(new Rows.Choice("AUTO","Авто по задаче"));for(var skill:SkillCatalog.all())choices.add(new Rows.Choice(skill.id(),skill.name()));return choices;},()->c.skill,v->{c.skill=v;c.sanitize();save.run();}));
  rows.add(new Rows.Toggle("HUD питомца","Показывается и при Combat. Не вычисляет pet XP из Combat/Farming XP",()->c.petHud,v->{c.petHud=v;save.run();}));
  rows.add(new Rows.Note("XP: разница абсолютного Skill(current/max) серверного actionbar. Повтор +XP не суммируется; мир/пауза >15с — новая база. Темп после ≥60с подтверждённых интервалов; ETA приблизительная. Неизвестный/неоднозначный формат = —. Daily/lifetime сохраняет только Farming, не смесь навыков.",Ui.SUB));
  rows.add(new Rows.Number("Твой Farming cap","50 по умолчанию. Укажи реально разблокированный максимум до60. ETA60 — теоретическая; сам мод cap не открывает",()->c.farmingCap,v->{c.farmingCap=(int)Math.round(v);c.sanitize();save.run();},0,60,1,1,v->Rows.num(v)));
  rows.add(new Rows.Note(()->Telemetry.pet()+" • max "+Telemetry.petMax()+" • цена "+com.farmmacro.hud.StatsHud.num(Telemetry.petValue()),Ui.SUB));
  rows.add(new Rows.Note("Питомец из Tab или вручную открытого Pets с активной серверной меткой. Не открываем меню автоматически. XP до уровня — только явный Progress to Level из меню, снимок до60с; Tab его НЕ освежает. petInfo.exp — отдельно общий XP, не XP уровня. MAX только по MAX LEVEL. Одинаковых по имени/уровню питомцев Tab не различает; после смены обнови Pets вручную. Рыночная цена питомца не извлекается из Bazaar; по желанию ниже ручная пометка, не точная текущая цена аукциона.",Ui.SUB));
  rows.add(new Rows.Number("Ручная цена текущего питомца","−1 неизвестно, 0 допускается как явная пометка. Привязывается к наблюдаемому типу/редкости, не всем питомцам",()->c.petPrice,v->{c.petPrice=v;c.petPriceFor=Telemetry.petKey();c.sanitize();save.run();},-1,1000000000000d,1000,100000,v->v<0?"неизвестно":Rows.num(v)));
  rows.add(new Rows.Note("Позиции, масштаб, видимость и личные цвета — существующий F8; новые плашки можно тащить отдельно. Ничего не сбрасывается автоматически. Minecraft/визуальный стенд не запускались, нужна проверка читаемости у тебя.",Ui.SUB));
 }
 private StatsRows(){}
}
