package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import com.farmmacro.motion.*;
import java.util.List;
/** Rows live inside the existing Motion section, not every module's advanced settings. */
final class MotionRows {
 static void build(List<Rows.Row> rows,Runnable save){
  var s=ModConfig.INSTANCE.motion;int firstControl=rows.size();
  rows.add(new Rows.Toggle("Работать при Alt+Tab / PiP","Продолжает только уже запущенную ферму/Combat. Для Combat в фоне нужен Toggle: Hold прекращается при отпускании. Окна, реальная пауза, мир и паника по-прежнему останавливают. После остановки исходная pause-on-focus-loss возвращается.",()->ModConfig.INSTANCE.backgroundWork,v->{ModConfig.INSTANCE.backgroundWork=v;save.run();}));
  java.util.function.BooleanSupplier idle=MotionRows::idle;
  rows.add(new Rows.Selector("Движок Motion","Kaelix сохраняет прежние настройки. Aether — отдельная камера и паузы маршрута, без Kaelix Humanizer. Плавные привязки точек — отдельный переключатель ниже. Combat использует этот же движок; параметры трекинга общие ниже. Запись сохраняет записанные углы.",
   ()->List.of(new Rows.Choice("kaelix","Kaelix / наш"),new Rows.Choice("aether","Aether / отдельный")),()->s.backend,v->{s.backend=v;save.run();}).enabledIf(idle));
  rows.add(new Rows.Note("Режим меняется только на остановке. Камера фермы держит рабочий угол; разброс выбирается один раз на проход. Паника и ручной стоп не задерживаются.",Ui.SUB));
  rows.add(new Rows.Selector("Пресет Aether","Применит исходные параметры камеры/паузы ряда. Имена Safe/Efficient из upstream не обещают безопасность или скорость. Efficient действительно имеет 6мс/градус, остальные 2.",
   ()->List.of(new Rows.Choice("normal","Normal"),new Rows.Choice("safe","Safe (upstream)"),new Rows.Choice("efficient","Efficient (upstream)")),()->s.preset,v->{s.preset(v);save.run();}).enabledIf(idle).showIf(s::aether));
  rows.add(new Rows.Selector("Кривая Aether","MANAGER — исходная комбинация ease-in/out; остальные кривые из Aether EasingType. BACK допускает перелёт.",
   ()->{var list=new java.util.ArrayList<Rows.Choice>();list.add(new Rows.Choice("MANAGER","RotationManager / по умолчанию"));for(var e:AetherEasing.values())list.add(new Rows.Choice(e.name(),e.name()));return list;},()->s.curve,v->{s.curve=v;save.run();}).enabledIf(idle).showIf(s::aether));
  class N {void n(String label,String tip,java.util.function.DoubleSupplier get,java.util.function.DoubleConsumer set,double lo,double hi,double step,boolean aether){
   Rows.Row row=new Rows.Number(label,tip,get,v->{set.accept(v);s.sanitize();save.run();},lo,hi,step,step*5,Rows::num).enabledIf(idle);
   if(aether)row.showIf(s::aether);rows.add(row);
  }}var n=new N();
  rows.add(new Rows.Toggle("Плавные привязки к точкам","Aether, маршруты: стоять до подтверждённого угла. Нужна опора; нет полёта/воды/лестницы. Сдвиг >0.75бл, потеря опоры, ввод, окно или таймаут — локальный стоп без автопродолжения. Запись не меняется.",()->s.smoothBindings,v->{s.smoothBindings=v;save.run();}).enabledIf(idle).showIf(s::aether));
  n.n("Скорость привязки, °/с","Предел для привязок точек, не скорость сопровождения цели. Default120, диапазон20–360. Тик маршрута ждёт завершения.",()->s.bindingSpeed,v->s.bindingSpeed=v,20,360,10,true);
  n.n("Базовое время Aether, мс","Фактическое время: не меньше 150мс, базы с ±15% и угла × мс/градус. Предел скорости остаётся у вызывающей задачи.",()->s.rotationMs,v->s.rotationMs=(int)v,0,2000,10,true);
  n.n("Время на градус, мс",null,()->s.msPerDegree,v->s.msPerDegree=v,0,20,.5,true);
  rows.add(new Rows.Toggle("Aether ease-in",null,()->s.easeIn,v->{s.easeIn=v;save.run();}).enabledIf(idle).showIf(s::aether));
  rows.add(new Rows.Toggle("Aether ease-out",null,()->s.easeOut,v->{s.easeOut=v;save.run();}).enabledIf(idle).showIf(s::aether));
  n.n("Сила ease-in",null,()->s.inFactor,v->s.inFactor=v,1,5,.25,true);
  n.n("Сила ease-out",null,()->s.outFactor,v->s.outFactor=v,1,5,.25,true);
  n.n("Общий трекинг Aether, мс","Единый адаптер для навигации, Combat и осмотра; не переопределяется боевым профилем.",()->s.trackingMs,v->s.trackingMs=v,30,1000,10,true);
  n.n("Шум трекинга: минимум, %","Проценты шага, НЕ градусы дрожания. Не добавляет постоянную тряску стоящей камеры.",()->s.noiseMinPct,v->s.noiseMinPct=v,0,15,1,true);
  n.n("Шум трекинга: максимум, %",null,()->s.noiseMaxPct,v->s.noiseMaxPct=v,0,15,1,true);
  n.n("Aether: разброс yaw фермы, °","Замещает диапазон Kaelix схемы только в режиме Aether; исходный пресет 0.",()->s.yawRange,v->s.yawRange=v,0,2,.1,true);
  n.n("Aether: разброс pitch фермы, °","Один рабочий угол на проход, не дрейф по ряду; исходный пресет 0.",()->s.pitchRange,v->s.pitchRange=v,0,10,.25,true);
  rows.add(new Rows.Toggle("Заминка на поворотах / этажах","Только фермерские схемы: угол от25°, метка поворота, переход высоты или спуск. Нет случайных стопов на прямой. Явная более долгая пауза не сокращается; это максимум, не сумма.",()->s.boundaryPauses,v->{s.boundaryPauses=v;save.run();}).enabledIf(idle));
  n.n("Kaelix: поворот минимум, мс",null,()->s.cornerMinMs,v->s.cornerMinMs=(int)v,0,2000,10,false);
  n.n("Kaelix: поворот максимум, мс",null,()->s.cornerMaxMs,v->s.cornerMaxMs=(int)v,0,2000,10,false);
  n.n("Aether: смена ряда минимум, мс",null,()->s.laneMinMs,v->s.laneMinMs=(int)v,0,2000,10,true);
  n.n("Aether: смена ряда максимум, мс",null,()->s.laneMaxMs,v->s.laneMaxMs=(int)v,0,2000,10,true);
  n.n("Этаж минимум, мс","Общее расширение Kaelix, не скопированный параметр Aether. Также после приземления.",()->s.floorMinMs,v->s.floorMinMs=(int)v,0,2000,10,false);
  n.n("Этаж максимум, мс",null,()->s.floorMaxMs,v->s.floorMaxMs=(int)v,0,2000,10,false);
  rows.add(new Rows.Note("Общий трекинг Motion — параметры для сопровождения и осмотра. Скорость привязок и точных записей задаётся задачей; режим двигателя общий.",Ui.SUB));
  SharedCameraRows.build(rows,save);
  rows.add(new Rows.Note("Применение сохранённых углов (Kaelix)",Ui.SUB));
        rows.add(new Rows.Selector("Поворот", "Мгновенно; плавно — постоянная скорость с торможением в конце; как мышь — "
                + "по пикселям мыши с учётом чувствительности, разгон и торможение, лёгкий перелёт, углы не ровные",
                () -> List.of(new Rows.Choice("instant","Мгновенно"),new Rows.Choice("smooth","Плавно"),new Rows.Choice("mouse","Как мышь")), () -> !ModConfig.INSTANCE.camSmooth ? "instant" : ModConfig.INSTANCE.camMouseLike ? "mouse" : "smooth", v -> {
            ModConfig.INSTANCE.camSmooth = !"instant".equals(v);
            ModConfig.INSTANCE.camMouseLike = "mouse".equals(v);
            save.run();
        }));
        rows.add(new Rows.Number("Скорость поворота", "Градусов в секунду", () -> ModConfig.INSTANCE.camTurnSpeed,
                v -> ModConfig.INSTANCE.camTurnSpeed = v, 1, 3600, 10, 60, v -> Rows.num(v) + "°/с").enabledIf(() -> ModConfig.INSTANCE.camSmooth));
        rows.add(new Rows.Number("Скорость ±", "Каждый поворот чуть быстрее или медленнее (0 — всегда одинаково)", () -> ModConfig.INSTANCE.smoothTurnJitterPct,
                v -> ModConfig.INSTANCE.smoothTurnJitterPct = (int) v, 0, 90, 1, 10, v -> v == 0 ? "выкл" : "±" + (int) v + " %").integer()
                .under(() -> ModConfig.INSTANCE.camSmooth).adv());
        rows.add(new Rows.Number("Перелёт до", "«Как мышь»: чуть проскочить и вернуться, % от поворота (0 — без перелёта)", () -> ModConfig.INSTANCE.camOvershootPct,
                v -> ModConfig.INSTANCE.camOvershootPct = v, 0, 3, 0.1, 1, v -> v == 0 ? "выкл" : Rows.num(v) + " %")
                .under(() -> ModConfig.INSTANCE.camSmooth && ModConfig.INSTANCE.camMouseLike).adv());

  for(int i=firstControl+1;i<rows.size();i++)rows.get(i).enabledIf(MotionRows::idle);
 }
 static boolean idle(){return !com.farmmacro.macro.MacroManager.INSTANCE.isActive()&&!com.farmmacro.macro.MacroManager.INSTANCE.isRecording()&&!com.farmmacro.route.NavigationInput.busy()&&!com.farmmacro.camera.SmoothTurn.isActive()&&!com.farmmacro.combat.AimAssist.INSTANCE.keyActive()&&!com.farmmacro.camera.Freecam.active();}
 private MotionRows(){}
}
