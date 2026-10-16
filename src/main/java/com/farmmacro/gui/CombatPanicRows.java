package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import java.util.*;
/** Combat-only panic UI; setters never touch farming detector configuration. */
public final class CombatPanicRows {
    public static void build(List<Rows.Row> rows,Runnable save){
        var p=ModConfig.INSTANCE.combatPanic;
        rows.add(new Rows.Section("Паника Combat / Hunting — отдельно").key("combat.panic"));
        rows.add(new Rows.Toggle("Боевая паника включена", "Не зависит от общей вкладки Паника. Только активный aim/автовыстрел/осмотр/A-D/special; ферма и Garden используют свой профиль",
            ()->p.enabled,v->{p.enabled=v;save.run();}));
        rows.add(new Rows.Toggle("Combat: игрок встал в персонажа", "Включено по умолчанию. Только активный режим зиалотов: пересечение реальных хитбоксов с живым не-spectator игроком немедленно останавливает камеру, выстрелы и A/D. Не радиус рядом, не определение staff; B и новый запуск вручную",
            ()->p.playerOverlap,v->{p.playerOverlap=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Toggle("Combat: стоп при телепорте", "Смещение выше допуска, смена мира и респавн. Локальный стоп: разрешение Aim сохраняется; камера — отдельный переключатель ниже",
            ()->p.teleport,v->{p.teleport=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Toggle("Combat: допуск малых коррекций", "Небольшой пакет допустим только в пределах размера, числа и суммы за скользящее окно. Нулевой пакет не смещение. Это НЕ доказательство лага и не определение администратора",
            ()->p.smallCorrections,v->{p.smallCorrections=v;save.run();}).under(()->p.enabled&&p.teleport));
        rows.add(new Rows.Number("Combat: коррекция по горизонтали", "По умолчанию0.8бл, не расширяется автоматически из-за высокого пинга",()->p.correctionHorizontal,v->{p.correctionHorizontal=v;save.run();},0,1.5,.05,.2,v->Rows.num(v)+" бл").under(()->p.enabled&&p.teleport&&p.smallCorrections));
        rows.add(new Rows.Number("Combat: коррекция по высоте", "По умолчанию0.5бл. Большой перенос/респавн не исключаются этим допуском",()->p.correctionVertical,v->{p.correctionVertical=v;save.run();},0,1,.05,.2,v->Rows.num(v)+" бл").under(()->p.enabled&&p.teleport&&p.smallCorrections));
        rows.add(new Rows.Number("Combat: окно коррекций", "Скользящее окно; высокий пинг не расширяет лимит",()->p.correctionWindowSec,v->{p.correctionWindowSec=(int)v;save.run();},1,10,1,3,v->Rows.num(v)+" с").under(()->p.enabled&&p.teleport&&p.smallCorrections));
        rows.add(new Rows.Number("Combat: максимум коррекций", "Пятый пакет при лимите4 уже остановит. Только реальные сдвиги больше0.001бл",()->p.correctionMaxPackets,v->{p.correctionMaxPackets=(int)v;save.run();},1,12,1,4,v->Rows.num(v)).under(()->p.enabled&&p.teleport&&p.smallCorrections));
        rows.add(new Rows.Number("Combat: суммарный допуск", "Сумма расстояний в окне, не итоговое смещение; A/B откаты не взаимоуничтожаются",()->p.correctionBudget,v->{p.correctionBudget=v;save.run();},.05,6,.05,2,v->Rows.num(v)+" бл").under(()->p.enabled&&p.teleport&&p.smallCorrections));
        rows.add(new Rows.Toggle("Combat: стоп при потере оружия", "Исчезновение/подмена известного Item/ID/UUID или чужая смена слота; расход стака не считается. В режиме Только наведение ручной хотбар/колесо разрешены только с подтверждённым вводом; автоматический Combat без изменений",
            ()->p.weapon,v->{p.weapon=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Toggle("Combat: сервер повернул камеру", "Пакеты поворота/look-at и угол в пакете позиции; не движение своей мыши. Не определяет администратора. Ожидаемый Mousemat учитывается отдельно",
            ()->p.serverRotation,v->{p.serverRotation=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Number("Combat: порог серверного поворота", "По умолчанию20°. Не зависит от переключателя телепорта и допуска малых коррекций",
            ()->p.serverRotationDegrees,v->{p.serverRotationDegrees=v;save.run();},1,180,1,10,v->Rows.num(v)+"°").under(()->p.enabled&&p.serverRotation));
        rows.add(new Rows.Toggle("Combat: обращение игрока рядом", "Hi/привет, твой ник или слова от известного недавнего соседа. Неизвестного автора не угадывать",
            ()->p.nearbyChat,v->{p.nearbyChat=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Number("Combat: радиус чата", "Независим от радиуса фермы",()->p.chatRadius,v->{p.chatRadius=v;save.run();},1,32,1,5,v->Rows.num(v)+" бл").under(()->p.enabled&&p.nearbyChat));
        rows.add(new Rows.Number("Combat: память соседей", "Отдельный кэш, не общий с фермой",()->p.chatMemorySec,v->{p.chatMemorySec=(int)v;save.run();},3,60,1,5,v->Rows.num(v)+" с").under(()->p.enabled&&p.nearbyChat));
        rows.add(new Rows.Toggle("Combat: реагировать на мой ник", "В сообщении недавнего соседа",()->p.mentionName,v->{p.mentionName=v;save.run();}).under(()->p.enabled&&p.nearbyChat));
        rows.add(new Rows.TextField("Combat: слова чата", "Через запятую. Приветствия распознаются отдельно; настройки фермы не меняются",()->p.keywords,v->{p.keywords=v;save.run();return true;},null,1000,0).under(()->p.enabled&&p.nearbyChat));
        rows.add(new Rows.Toggle("Combat: игнорировать чат друзей", "Только отдельный список ниже",()->p.ignoreFriends,v->{p.ignoreFriends=v;save.run();}).under(()->p.enabled&&p.nearbyChat));
        rows.add(new Rows.TextField("Combat: друзья", "Ники через запятую, отдельный список",()->p.friends,v->{p.friends=v;save.run();return true;},null,1000,0).under(()->p.enabled&&p.nearbyChat&&p.ignoreFriends));
        rows.add(new Rows.Toggle("Combat: звук тревоги", "Свой сигнал/громкость/повторы, через движок Minecraft",()->p.sound,v->{p.sound=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Selector("Combat: сигнал", "Не меняет сигнал фермы",()->List.of(new Rows.Choice("builtin:siren","Сирена"),new Rows.Choice("builtin:klaxon","Тревога ти-та"),new Rows.Choice("builtin:alarm","Пищалка"),new Rows.Choice("builtin:alert","Цифровая тревога")),()->p.soundId,v->{p.soundId=v;save.run();}).under(()->p.enabled&&p.sound));
        rows.add(new Rows.Number("Combat: громкость", "Независима от общей громкости паники",()->p.volume,v->{p.volume=v;save.run();},0,1,.05,.1,v->Rows.num(v*100)+"%").under(()->p.enabled&&p.sound));
        rows.add(new Rows.Number("Combat: повторов сигнала", "Включая первый",()->p.soundRepeats,v->{p.soundRepeats=(int)v;save.run();},1,20,1,2,v->Rows.num(v)).under(()->p.enabled&&p.sound));
        rows.add(new Rows.Toggle("Combat: красный экран", "Независим от красного экрана фермы",()->p.redScreen,v->{p.redScreen=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Number("Combat: длительность экрана", "Номинально20тиков в секунду",()->p.redScreenTicks,v->{p.redScreenTicks=(int)v;save.run();},1,1200,10,20,v->Rows.num(v)+" т").under(()->p.enabled&&p.redScreen));
        rows.add(new Rows.Toggle("Combat: окно игры вперёд", "Отдельно от общего фокуса паники",()->p.focusWindow,v->{p.focusWindow=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Toggle("Combat: выйти из картинки в картинке", "Только при боевой панике",()->p.exitPip,v->{p.exitPip=v;save.run();}).under(()->p.enabled));
        rows.add(new Rows.Note("В бою свои телепорт/мир, оружие, чат, серверная камера и пересечение с игроком в режиме зиалотов. После паники — причина и клавиша передачи управления; затем новый старт вручную. Меню/потеря фокуса/другой владелец камеры дают локальный стоп без тревоги и отпускают Shift. Общие урон/игрок рядом/действия таблицы не наследуются. End и ручной ввод остаются. Автоответа/автопродолжения нет. Полный инвентарь не останавливает мод.",Ui.SUB));
    }
    private CombatPanicRows(){}
}
