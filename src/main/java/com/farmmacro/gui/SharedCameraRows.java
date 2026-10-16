package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import com.farmmacro.combat.AimAssist;
import java.util.List;
/** One authoritative camera editor, never built in Combat profiles. */
final class SharedCameraRows {
 static void build(List<Rows.Row> rows,Runnable save){var c=ModConfig.INSTANCE;int start=rows.size();
        rows.add(new Rows.Selector("Режим трекинга", "Внутренний режим Kaelix. Если выбран движок Aether, общий Motion использует Aether и здесь, и при навигации.",
            () -> List.of(new Rows.Choice("precise","Точный (старый)"),new Rows.Choice("human","Человеческий (старый)")),() -> c.motion.camera.aimTrackingMode,v -> {c.motion.camera.aimTrackingMode=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).enabledIf(MotionRows::idle).showIf(()->!c.motion.aether()));
        rows.add(new Rows.Number("Выраженность человеческого трекинга", "Больше — заметнее инерция/допуск. Редкий перелёт — только короткое окно доводки новой цели, не постоянное раскачивание. Задержка в мс;0% убирает её. На дальней цели допуски меньше",
            () -> c.motion.camera.aimHumanStrength,v -> {c.motion.camera.aimHumanStrength=(int)Math.round(v);AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();},0,100,5,10,v -> Rows.num(v)+"%").showIf(() -> !c.motion.aether()&&"human".equals(c.motion.camera.aimTrackingMode)));
        rows.add(new Rows.Toggle("Небольшой случайный джиттер", "Только Human: ограниченный offset по отдельным размерам yaw/pitch; выключен в узком открытом участке. Не постоянная тряска, не гарантия попадания",
            () -> c.motion.camera.aimJitterEnabled,v -> {c.motion.camera.aimJitterEnabled=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> !c.motion.aether()&&"human".equals(c.motion.camera.aimTrackingMode)));
        rows.add(new Rows.Number("Амплитуда джиттера", "По умолчанию0.12°, pitch меньше yaw; чувствительность может скрыть очень малый offset",
            () -> c.motion.camera.aimJitterDegrees,v -> {c.motion.camera.aimJitterDegrees=v;save.run();},0,.5,.02,.1,v -> String.format(java.util.Locale.ROOT,"%.2f°",v)).showIf(() -> !c.motion.aether()&&"human".equals(c.motion.camera.aimTrackingMode)&&c.motion.camera.aimJitterEnabled));
        rows.add(new Rows.Number("Пауза перед осмотром", "Очередь ожидающих выстрела/поиск special не запускают обычный осмотр",
            () -> c.motion.camera.aimIdleDelayMs,v -> {c.motion.camera.aimIdleDelayMs=(int)Math.round(v);save.run();},400,5000,100,500,v -> Rows.num(v)+" мс"));
        rows.add(new Rows.Number("Размах бокового осмотра", "От исходного направления, не бесконечное вращение. Осмотр не наследует крутой вертикальный угол прошлого выстрела",
            () -> c.motion.camera.aimIdleYaw,v -> {c.motion.camera.aimIdleYaw=v;save.run();},20,100,5,10,v -> Rows.num(v)+"°"));
        rows.add(new Rows.Number("Скорость осмотра", "Отдельно от скорости захвата: быстрый перевод сектора, короткая остановка, иногда проверка соседнего места",
            () -> c.motion.camera.aimIdleSpeed,v -> {c.motion.camera.aimIdleSpeed=v;save.run();},30,300,5,25,v -> Rows.num(v)+"°/с"));
        rows.add(new Rows.Number("Базовая скорость наведения", "Градусы в секунду, можно ввести100. Общий предел: автоматический Combat использует движок/множители, «Только наведение» — эту скорость напрямую, без хуманайза",
            () -> c.motion.camera.aimTurnSpeed,v -> {c.motion.camera.aimTurnSpeed=v;save.run();},20,1440,5,25,v -> Rows.num(v)+"°/с"));
        rows.add(new Rows.Number("Скорость захвата", "Множитель базы для основного разворота. По умолчанию135%; после доводки переходит к скорости сопровождения. Не отменяет задержку Human",
            () -> c.motion.camera.aimAcquireSpeedPct,v -> {c.motion.camera.aimAcquireSpeedPct=(int)Math.round(v);save.run();},100,250,5,25,v -> Rows.num(v)+"%"));
        rows.add(new Rows.Number("Скорость сопровождения", "Множитель базы после захвата. По умолчанию100%; отдельный от скорости осмотра и разворота к Special",
            () -> c.motion.camera.aimTrackSpeedPct,v -> {c.motion.camera.aimTrackSpeedPct=(int)Math.round(v);save.run();},25,150,5,25,v -> Rows.num(v)+"%"));
        rows.add(new Rows.Number("Разворот к Special", "Минимальная базовая скорость основного разворота к подтверждённому Special Zealot, до случайного множителя. По умолчанию900°/с; затем обычное сопровождение. Не телепорт камеры",
            () -> c.motion.camera.aimSpecialTurnSpeed,v -> {c.motion.camera.aimSpecialTurnSpeed=v;save.run();},20,1440,5,100,v -> Rows.num(v)+"°/с"));
        rows.add(new Rows.Number("Разброс скорости наведения", "Плавный множитель к базовой скорости: например100 и25% → примерно75–125°/с. Не новый бросок случайности каждый кадр.0% — без разброса",
            () -> c.motion.camera.aimSpeedVariationPct,v -> {c.motion.camera.aimSpeedVariationPct=(int)Math.round(v);save.run();},0,60,5,10,v -> Rows.num(v)+"%"));
        rows.add(new Rows.Number("Реакция на движение: минимум", "Human: наблюдение положения приходит с задержкой; резкое движение не отслеживается мгновенно",
            () -> c.motion.camera.aimReactionMinMs,v -> {c.motion.camera.aimReactionMinMs=(int)Math.round(v);c.motion.camera.aimReactionMaxMs=Math.max(c.motion.camera.aimReactionMaxMs,c.motion.camera.aimReactionMinMs);save.run();},0,500,10,50,v -> Rows.num(v)+" мс").showIf(() -> !c.motion.aether()&&"human".equals(c.motion.camera.aimTrackingMode)));
        rows.add(new Rows.Number("Реакция на движение: максимум", "Задержка выбирается при захвате/переходе; очередь хранит прошлые наблюдения, а не текущую позицию",
            () -> c.motion.camera.aimReactionMaxMs,v -> {c.motion.camera.aimReactionMaxMs=Math.max(c.motion.camera.aimReactionMinMs,(int)Math.round(v));save.run();},0,700,10,50,v -> Rows.num(v)+" мс").showIf(() -> !c.motion.aether()&&"human".equals(c.motion.camera.aimTrackingMode)));
        rows.add(new Rows.Toggle("Отдавать приоритет движению мыши", "Выкл: пока активна клавиша, удерживать центр. Вкл: движение мыши даёт паузу 180 мс",
            () -> c.motion.camera.aimYieldMouse,v -> {c.motion.camera.aimYieldMouse=v;save.run();}).enabledIf(MotionRows::idle));

  for(int i=start;i<rows.size();i++)rows.get(i).enabledIf(MotionRows::idle);
 }
 private SharedCameraRows(){}
}
