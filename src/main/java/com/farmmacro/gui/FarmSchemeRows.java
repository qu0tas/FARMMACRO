package com.farmmacro.gui;

import com.farmmacro.route.*;
import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.*;

/** Editor-generated schemes use the existing route save/start/editor controls. */
final class FarmSchemeRows {
    static List<Rows.Choice> keyChoices(){
        var out=new ArrayList<Rows.Choice>();
        for(String k:FarmSchemeSettings.KEYS)out.add(new Rows.Choice(k,k.isEmpty()?"Авто: к точке":k.toUpperCase(Locale.ROOT)));
        return out;
    }
    private static String label(String k){return switch(k){
        case "s_shape" -> "S-shape: A/D + переход";
        case "sugarcane" -> "Тростник: A/S";
        case "mushroom" -> "Грибы: A → S → SD";
        case "cocoa" -> "Какао: W/S + D";
        case "ad" -> "A/D";
        case "ws" -> "W/S";
        default -> "Custom: точки редактора";
    };}
    static void builder(List<Rows.Row> rows){
        var s=ModConfig.INSTANCE.farmBuilder;
        BooleanSupplier idle=()->!MacroManager.INSTANCE.isActive()&&!MacroManager.INSTANCE.isRecording();
        Consumer<Runnable> edit=job->{job.run();s.sanitize();ModConfig.save();};
        rows.add(new Rows.Section("Фермы: конструктор схем").key("r.farm"));
        rows.add(new Rows.Note("Геометрические шаблоны под твой участок, не автоопределение фермы. Начало — текущее место ног. Сначала сохрани старый маршрут, затем очисти буфер. Генератор не заменяет непустой маршрут и не удаляет файлы.",Ui.SUB));
        rows.add(new Rows.Selector("Схема",null,()->FarmSchemeSettings.MODES.stream().map(k->new Rows.Choice(k,label(k))).toList(),()->s.mode,
            v->edit.accept(()->s.preset(v))).enabledIf(idle));
        rows.add(new Rows.Number("Проходов",null,()->s.rows,v->edit.accept(()->s.rows=(int)v),1,128,1,8,Rows::num).integer().enabledIf(idle));
        rows.add(new Rows.Number("Длина прохода, бл","Каждый длинный участок; подстрой под свой билд",()->s.rowLength,v->edit.accept(()->s.rowLength=v),1,256,1,8,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Переход, бл","0 = разворот на месте. Грибы: короткий S после A, затем SD и снова A. Остальные: выбранные клавиши перехода.",()->s.laneSpacing,v->edit.accept(()->s.laneSpacing=v),0,16,.1,1,Rows::num).enabledIf(idle));
        rows.add(new Rows.Selector("Первый ряд","Для S-shape/A-D/W-S/какао. У тростника A/S и грибов A/SD фиксированный цикл.",FarmSchemeRows::keyChoices,()->s.firstKeys,
            v->edit.accept(()->s.firstKeys=v.isEmpty()?"a":v)).enabledIf(idle));
        rows.add(new Rows.Selector("Клавиши перехода",null,FarmSchemeRows::keyChoices,()->s.laneKeys,
            v->edit.accept(()->s.laneKeys=v.isEmpty()?"w":v)).enabledIf(idle));
        rows.add(new Rows.Toggle("Зеркально A ↔ D","W/S не меняются",()->s.reverse,v->edit.accept(()->s.reverse=v)).enabledIf(idle));
        rows.add(new Rows.Toggle("Hold W на A/D","Генерирует диагональную геометрию WA/WD, а не добавляет W в прямой старый маршрут",()->s.holdW,v->edit.accept(()->s.holdW=v)).enabledIf(idle));
        controls(rows,s,idle,edit,false);
        rows.add(new Rows.Note("Squeaky Mousemat остаётся в существующей подготовке старта. При активной привязке пресетов камеры она приоритетнее углов схемы. ЛКМ — на длинных участках, не на переходах. Случайная геометрия/оглядывание маршрута здесь не применяются.",Ui.SUB));
        rows.add(new Rows.Buttons(new Rows.Btn("Создать схему здесь",Rows.Style.PRIMARY,()->{
            var mc=Minecraft.getInstance();var rb=RouteBuffer.INSTANCE;
            if(!idle.getAsBoolean()||!rb.isEmpty()||mc.player==null||mc.level==null||"custom".equals(s.mode))return;
            var route=FarmRouteBuilder.generate(s,mc.player.getX(),mc.player.getY(),mc.player.getZ(),RouteBuffer.worldId(mc),RouteBuffer.dimensionId(mc));
            rb.load(null,route);rb.changed();
            MacroManager.INSTANCE.msgPublic(mc,"§aФерма создана в буфере: проверь точки в редакторе и сохрани маршрут");
        }).enabledIf(()->idle.getAsBoolean()&&RouteBuffer.INSTANCE.isEmpty()&&Minecraft.getInstance().player!=null&&!"custom".equals(s.mode))));
        rows.add(new Rows.Note("Custom: поставь/загрузи точки, затем ⚙ → «Фермерская схема». У точек доступны Авто/W/A/S/D/диагонали и метка смены ряда. Генерация сама ничего не запускает. /setspawn выключен, включай только для своего Garden.",Ui.SUB));
    }
    static void build(List<Rows.Row> rows,FarmSchemeSettings s,BooleanSupplier idle,Consumer<Runnable> edit){
        rows.add(new Rows.Section("Фермерская схема"));
        rows.add(new Rows.Toggle("Включить для маршрута","Старые файлы: выключено. Сохрани изменения в маршруте.",()->s.enabled,v->edit.accept(()->s.enabled=v)).enabledIf(idle));
        rows.add(new Rows.Selector("Метка схемы", "Меняет только подпись, не перестраивает точки. Для своего маршрута выбери Custom.",
            ()->FarmSchemeSettings.MODES.stream().map(k->new Rows.Choice(k,label(k))).toList(),()->s.mode,v->edit.accept(()->s.mode=v)).enabledIf(idle));
        rows.add(new Rows.Note(()->"Схема: "+label(s.mode)+". Точки редактируются обычным редактором; изменение углов не поворачивает уже созданную геометрию. Границы вычисляются по текущим точкам при старте.",Ui.SUB));
        controls(rows,s,()->idle.getAsBoolean()&&s.enabled,edit,true);
    }
    private static void controls(List<Rows.Row> rows,FarmSchemeSettings s,BooleanSupplier idle,Consumer<Runnable> edit,boolean route){
        rows.add(new Rows.Toggle("Камера схемы","Без повторного SNAP/Mousemat; плавно только подготовка. Привязанные пресеты имеют приоритет.",()->s.lockCamera,v->edit.accept(()->s.lockCamera=v)).enabledIf(idle));
        rows.add(new Rows.Number("Yaw схемы",null,()->s.yaw,v->edit.accept(()->s.yaw=v),-180,180,.1,5,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Pitch схемы",null,()->s.pitch,v->edit.accept(()->s.pitch=v),-89,89,.1,5,Rows::num).enabledIf(idle));
        rows.add(new Rows.Buttons(new Rows.Btn("Взять текущий взгляд",Rows.Style.SECONDARY,()->{
            var p=Minecraft.getInstance().player;if(p!=null)edit.accept(()->{s.yaw=net.minecraft.util.Mth.wrapDegrees(p.getYRot());s.pitch=p.getXRot();});
        }).enabledIf(idle)));
        rows.add(new Rows.Number("Yaw ± за проход","Выбирается один раз, не шумит каждый кадр. 0 = фиксированно.",()->s.yawRange,v->edit.accept(()->s.yawRange=v),0,2,.05,.2,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Pitch ± за проход",null,()->s.pitchRange,v->edit.accept(()->s.pitchRange=v),0,10,.1,1,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Поворот, °/с",null,()->s.turnSpeed,v->edit.accept(()->s.turnSpeed=v),20,360,5,20,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Радиус точки, бл",null,()->s.reachRadius,v->edit.accept(()->s.reachRadius=v),.05,.45,.01,.05,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Запас границ, бл","XZ bounding box всех точек + запас; живые опоры/коридор проверяются отдельно",()->s.boundMargin,v->edit.accept(()->s.boundMargin=v),.35,4,.05,.25,Rows::num).enabledIf(idle));
        rows.add(new Rows.Number("Пауза смены ряда, т","Дополнительно к ручной паузе у точки с меткой смены ряда",()->s.switchPauseTicks,v->edit.accept(()->s.switchPauseTicks=(int)v),0,200,1,5,Rows::num).integer().enabledIf(idle));
        rows.add(new Rows.Toggle("Быстрая смена (эксперимент)","Только opt-in: без добавочной паузы смены, радиус +0.12 (до 0.45). Может срезать конец ряда.",()->s.fastSwitch,v->edit.accept(()->s.fastSwitch=v)).enabledIf(idle));
        rows.add(new Rows.Toggle("/setspawn при ручном старте","Выключено по умолчанию. Один запрос у начала на опоре, не на resume/повторах/после паники. Сервер может отклонить.",()->s.setspawnOnStart,v->edit.accept(()->s.setspawnOnStart=v)).enabledIf(idle));
        if(route)rows.add(new Rows.Note("Нет отдельных глобальных клавиш запуска: обычные Start/End/Continue и GUI. Ручные WASD/Shift/Space/Sprint или мышь останавливают схему. Для циклов используется обычный возврат к старту/Rewarp. Автовозобновления после паники нет. Альтернативная случайная геометрия не применяется к включённой ферме.",Ui.SUB));
    }
}
