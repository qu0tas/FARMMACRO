package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import com.farmmacro.combat.*;
import java.util.*;
final class ManualAimRows {
 static void build(List<Rows.Row> rows,Runnable save){var c=ModConfig.INSTANCE;
  rows.add(new Rows.Section("Только наведение / Slayer").key("combat.aim"));
  rows.add(new Rows.Toggle("Разрешить наведение", "Только камера: без ходьбы, атак, выстрелов, осмотра и Special-поиска. Старые флаги автоматизации в этом режиме игнорируются.",()->c.aimAssistEnabled,v->{AimAssist.INSTANCE.configure(v);save.run();}));
  rows.add(new Rows.Selector("Режим клавиши", "Общая клавиша Aim assist: Hold или Toggle. WASD, прыжки, спринт, атаки и ручная мышь не выключают этот режим; для выхода отпусти Hold/нажми Toggle или End.",()->List.of(new Rows.Choice("hold","Удерживать"),new Rows.Choice("toggle","Переключать")),()->c.aimKeyMode,v->{c.aimKeyMode=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
  rows.add(new Rows.Note(()->"Клавиша: "+com.farmmacro.macro.MacroManager.keyName(com.farmmacro.FarmMacroMod.keyAim)+" · назначается в разделе Клавиши",Ui.SUB));
  rows.add(new Rows.Selector("Цель наведения", "Все боссы — только основные имена. В каждой ветке отдельно босс/формы либо босс + мини/формы; обычные XP-мобы не входят. Доступны также точное имя и каталог. Владелец, уязвимость и активная фаза не определяются.",()->{
   var out=new ArrayList<Rows.Choice>();out.add(new Rows.Choice("slayers","Slayer: все боссы"));
   for(var b:SlayerCatalog.BRANCHES){
    out.add(new Rows.Choice("slayer:"+b.id()+":boss","Slayer "+b.title()+": босс / формы"));
    out.add(new Rows.Choice("slayer:"+b.id()+":encounter","Slayer "+b.title()+": босс + мини / формы"));
    for(String n:b.bossForms())out.add(new Rows.Choice(n,(b.bosses().contains(n)?"Босс: ":"Форма: ")+n));
   }
   var seen=new HashSet<String>();for(var b:SlayerCatalog.BRANCHES)seen.addAll(b.bossForms());for(var g:MobCatalog.GROUPS)for(String n:g.mobs())if(!n.isBlank()&&seen.add(n))out.add(new Rows.Choice(n,n));
   out.add(new Rows.Choice("custom","Своё имя"));return out;
  },()->c.aimManualTarget,v->{c.aimManualTarget=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
  rows.add(new Rows.TextField("Точное имя моба", "Без цвета, уровня и HP. Только настоящий видимый Mob, не игрок/табличка.",()->c.aimManualName,v->{c.aimManualName=v.strip();AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();return true;},null,80,0).showIf(()->"custom".equals(c.aimManualTarget)));
  rows.add(new Rows.Note("Ручная смена предмета: хотбар-клавиши и колесо сохраняют захват (лук, хил-посох, коса и другие предметы). Неожиданная серверная смена/пропажа по-прежнему останавливает при включённой проверке оружия. Отдельные предметы не выбираются автоматически.",Ui.SUB));
  rows.add(new Rows.Toggle("Tarantula: сначала мешки с яйцами", "Только ручной аим, когда фильтр разрешает Tarantula/Primordial/Conjoined. Подтверждённый мешок (SHOOT ME! + таймер + модель рядом с живым пауком) временно важнее босса. Стреляешь сам; после исчезновения — следующий либо босс. Без автолука и проверки уязвимости. Alpha: визуальный центр модели требует ручного теста.",()->c.aimManualEggSacs,v->{c.aimManualEggSacs=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
  rows.add(new Rows.Number("Радиус захвата", "Только загруженные мобы; дальность атаки оружия не увеличивается.",()->c.aimManualRange,v->{c.aimManualRange=v;save.run();},2,64,1,5,v->Rows.num(v)+" бл"));
  rows.add(new Rows.Number("Угол первого захвата", "Градусы от прицела; живую захваченную цель держит при поворотах. Начальный выбор ближе к прицелу, затем по расстоянию.",()->c.aimManualFov,v->{c.aimManualFov=v;save.run();},5,180,5,20,v->Rows.num(v)+"°"));
  rows.add(new Rows.Note(()->"Скорость: "+Rows.num(c.motion.camera.aimTurnSpeed)+"°/с · настраивается в Motion → Базовая скорость наведения",Ui.SUB));
  rows.add(new Rows.Note("Точный центр без хуманайза независимо от Kaelix/Aether: нет джиттера, задержки реакции, перелёта и опережения. Стена перед центром — пауза; захват хранится до8тиков. Новая цель после смерти/потери. Потеря фокуса/окно/паника — ручной перезапуск.",Ui.SUB));
  rows.add(new Rows.Note(()->AimAssist.INSTANCE.status(),Ui.SUB));
 }
 private ManualAimRows(){}
}
