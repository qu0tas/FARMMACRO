package com.farmmacro.gui;
import com.farmmacro.config.ModConfig;
import com.farmmacro.combat.*;
import net.minecraft.client.Minecraft;
import java.util.*;
public final class CombatRows {
    private CombatRows(){}
    public static void build(List<Rows.Row> rows,Runnable save,Runnable rebuild){
        ModConfig c=ModConfig.INSTANCE;var group=MobCatalog.get(c.aimPreset);
        rows.add(new Rows.Section("Режим Combat").key("combat.profiles"));
        rows.add(new Rows.Selector("Управление боем", "Только наведение: ты бегаешь и атакуешь, мод держит центр выбранного моба. Автоматический Combat сохраняет прежние профили и опции.",
            ()->List.of(new Rows.Choice("automatic","Автоматический Combat"),new Rows.Choice("manual","Только наведение / Slayer")),()->c.aimManualOnly?"manual":"automatic",v->{
                AimAssist.INSTANCE.configure(c.aimAssistEnabled);c.aimManualOnly="manual".equals(v);save.run();rebuild.run();}));
        if(c.aimManualOnly){ManualAimRows.build(rows,save);CombatPanicRows.build(rows,save);return;}
        CombatProfileRows.build(rows,save,rebuild);
        CombatPanicRows.build(rows,save);
        rows.add(new Rows.Section("Камера и бой — выбранный профиль (альфа)").key("combat.aim"));
        rows.add(new Rows.Toggle("Разрешить aim assist", "Клавиша выбирает видимую разрешённую цель и удерживает её. Камера; автовыстрел косы и ходьба A/D включаются отдельно",
            () -> c.aimAssistEnabled,v -> {AimAssist.INSTANCE.configure(v);save.run();}));
        rows.add(new Rows.Note(() -> "Клавиша: "+com.farmmacro.macro.MacroManager.keyName(com.farmmacro.FarmMacroMod.keyAim)+" — изменить в Minecraft → Управление → Kaelix → Aim assist",Ui.SUB));
        rows.add(new Rows.Selector("Режим клавиши", "Hold: держать для наведения. Toggle: одно нажатие вкл, ещё одно выкл",
            () -> List.of(new Rows.Choice("hold","Удерживать"),new Rows.Choice("toggle","Переключать")),() -> c.aimKeyMode,v -> {
                c.aimKeyMode=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();
            }));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.status(),Ui.SUB));
        rows.add(new Rows.Toggle("Подробности выбора целей", "Последний поиск: до12 приоритетных строк и причины. Не живой список всех мобов; при удержании цели повторный выбор не выполняется",
            () -> c.aimDiagnostics,v -> {c.aimDiagnostics=v;save.run();}));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.targetDiagnostics(),Ui.SUB).showIf(() -> c.aimDiagnostics));
        rows.add(new Rows.Buttons(new Rows.Btn("Скопировать диагностику Combat",Rows.Style.SECONDARY,()->{
            var mc=Minecraft.getInstance();mc.keyboardHandler.setClipboard(AimAssist.INSTANCE.diagnosticReport());
            if(mc.player!=null)mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Kaelix: диагностика Combat скопирована"));
        })));
        rows.add(new Rows.Note("Отчёт: версия, режимы, камера и причины последнего выбора. Без чата, ников, координат, адреса сервера, своих имён профилей и фильтров. Мобы обозначены стандартным видом или Zealot/Special.",Ui.SUB).showIf(() -> c.aimDiagnostics));
        rows.add(new Rows.Note("Следующая цель: сначала видимые соседи предыдущей в том же секторе, затем остальные перед камерой. Разворот назад — запасной вариант. Живой захват не переключает до выстрела/смерти/потери.",Ui.SUB));
        rows.add(new Rows.Toggle("Фильтр: только зиалоты", "Наводиться только на Zealot, Zealot Bruiser и Special Zealot. Имеет приоритет над каталогом; остальных мобов не захватывает",
            () -> c.aimZealotsOnly,v -> {c.aimZealotsOnly=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Toggle("Дальние цели: Enderman без имени", "Только для режима зилотов: если сервер не прислал табличку, разрешить реальный Enderman. Это может быть НЕ зилот; по умолчанию выключено",
            () -> c.aimUnnamedEndermen,v -> {c.aimUnnamedEndermen=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> c.aimZealotsOnly));
        rows.add(new Rows.Toggle("Только выбранные мобы", "Если «Только зилоты» выключено: применять каталог и дополнительные имена при включении фильтра",
            () -> c.aimSelectedOnly,v -> {c.aimSelectedOnly=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Toggle("Захват только перед камерой", "Выкл: клавиша может повернуть к удобной цели сбоку/сзади, если оценка лучше. Вкл: ограничить угол первоначального захвата",
            () -> c.aimFrontOnly,v -> {c.aimFrontOnly=v;save.run();}));
        rows.add(new Rows.Toggle("Профиль Glacial Scythe", "Только с Glacial Scythe в основной руке; открытый центр или доступная часть тела. Автовыстрел — отдельная опция",() -> c.aimScytheOnly,v -> {c.aimScytheOnly=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Toggle("Автовыстрел Glacial Scythe", "Только с косой в основной руке, активным F12 и прицелом на теле или допустимой областью болта/взрыва. Остановка/стена/окно/потеря цели блокируют выстрел",
            () -> c.aimAutoFire,v -> {c.aimAutoFire=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Toggle("Человеческий момент выстрела", "Human + Glacial Scythe: иногда нажать до точной доводки, иногда на70–180мс позже, в том числе при коррекции после перелёта. Только пока реальный луч в допустимой зоне; возможны промахи",
            () -> c.aimHumanShotTiming,v -> {c.aimHumanShotTiming=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> c.aimAutoFire&&"human".equals(c.motion.camera.mode(c.motion))));
        rows.add(new Rows.Number("Доля ранних нажатий", "Не процент промахов: расширенная зона у тела до0.55бл. Позднее нажатие выбирается примерно для25% планов; если луч ушёл, отсрочка сбрасывается, выстрела вслепую нет",
            () -> c.aimEarlyShotChance,v -> {c.aimEarlyShotChance=(int)Math.round(v);save.run();},0,40,5,10,v -> Rows.num(v)+"%").showIf(() -> c.aimAutoFire&&c.aimHumanShotTiming&&"human".equals(c.motion.camera.mode(c.motion))));
        rows.add(new Rows.Toggle("Следующая цель после выстрела", "Номер → запрос использования отправлен → другая цель. После паузы вернуться к локально живой цели. FAIL клиента не продвигает очередь. Исчезновение не считается убийством; Special держится до завершения",
            () -> c.aimNextAfterShot,v -> {c.aimNextAfterShot=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> c.aimAutoFire));
        rows.add(new Rows.Number("Пауза проверки выстреленной цели", "Не расчёт скорости болта/подтверждение попадания. После паузы локально живую цель снова можно выбрать; по умолчанию900мс. PASS клиента не доказывает расход маны или выстрел сервера",
            () -> c.aimShotObservationMs,v -> {c.aimShotObservationMs=(int)Math.round(v);save.run();},300,3000,100,300,v -> Rows.num(v)+" мс").showIf(() -> c.aimAutoFire&&c.aimNextAfterShot));
        rows.add(new Rows.Number("Пауза перед дальним разворотом", "Если локальные соседи ожидают результат выстрела, кратко не выбирать дальнюю/заднюю замену. Близкая свободная цель или Special не ждут. По умолчанию350мс после выстрела;0 — без паузы",
            () -> c.aimChainRearWaitMs,v -> {c.aimChainRearWaitMs=(int)Math.round(v);save.run();},0,1500,50,250,v -> Rows.num(v)+" мс").showIf(() -> c.aimAutoFire&&c.aimNextAfterShot));
        rows.add(new Rows.Number("Интервал автовыстрела", "Минимальный интервал попыток использования; серверная мана/дальность не гарантируются",
            () -> c.aimAutoFireIntervalMs,v -> {c.aimAutoFireIntervalMs=(int)Math.round(v);save.run();},100,1500,20,100,v -> Rows.num(v)+" мс").showIf(() -> c.aimAutoFire));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.fireStatus(),Ui.SUB).showIf(() -> c.aimAutoFire));
        rows.add(new Rows.Note("Очередь хранит до512 локальных записей. Отправленный запрос не подтверждает выстрел/попадание/убийство; наблюдавшаяся смерть не приписывается тебе. Удаление/выгрузка — неизвестный исход. Живые после паузы доступны повторно.",Ui.SUB).showIf(() -> c.aimAutoFire&&c.aimNextAfterShot));
        rows.add(new Rows.Toggle("Все загруженные дистанции", "Без локального лимита радиуса. Не увеличивает дальность оружия и не загружает далёких мобов",() -> c.aimLoadedRange,v -> {c.aimLoadedRange=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Toggle("Приоритет Zealot / Zealot Bruiser", "Умеренное преимущество зилотам при сравнимом расстоянии/повороте, не обязательный разворот назад. Живую цель не переключает",() -> c.aimZealotPriority,v -> {c.aimZealotPriority=v;save.run();}));
        rows.add(new Rows.Toggle("Подсвечивать видимых мобов", "Те же фильтры/радиус, что у aim. Голубой/жёлтый — доступный моб/зилот, оранжевый — захват, фиолетовый — ожидание выстрела, серый — FOV/приоритет Special. Не дальность оружия и не сквозь стены",() -> c.aimHighlight,v -> {c.aimHighlight=v;save.run();}));
        rows.add(new Rows.Number("Дальность", "Локальный лимит захвата; не дальность оружия",() -> c.aimRange,v -> {c.aimRange=v;save.run();},1,15,.5,1,v -> Rows.num(v)+" бл").showIf(() -> !c.aimLoadedRange));
        rows.add(new Rows.Note("До9 проверок открытых точек реального тела: центр, бока, верх/низ. Полное перекрытие запрещает выстрел; небольшое сохраняет цель до8тиков. Это не проверка всех пикселей и не гарантия попадания.",Ui.SUB));
        rows.add(new Rows.Number("Угол захвата", "Отклонение от прицела; не ищет цель за спиной",() -> c.aimFov,v -> {c.aimFov=v;save.run();},5,75,5,10,v -> Rows.num(v)+"°").showIf(() -> c.aimFrontOnly));
        rows.add(new Rows.Toggle("Glacial Scythe: наведение по области", "Human + коса: сбоку от тела/на открытой земле рядом. Общая проверка фактического луча/первой земли и расстояния до хитбокса, не только центра высокого моба. Геометрия клиента, не гарантия урона",
            () -> c.aimScytheArea,v -> {c.aimScytheArea=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> "human".equals(c.motion.camera.mode(c.motion))));
        rows.add(new Rows.Number("Доля точек рядом с мобом", "Human + область косы: шанс плана сбоку/у земли вместо тела. По умолчанию80%; не процент промахов. Если земли нет — бок тела",
            () -> c.aimScytheNearChance,v -> {c.aimScytheNearChance=(int)Math.round(v);save.run();},0,100,5,10,v -> Rows.num(v)+"%").showIf(() -> "human".equals(c.motion.camera.mode(c.motion))&&c.aimScytheArea));
        rows.add(new Rows.Toggle("Осматриваться без целей", "При активном F12 и подходящем оружии/локации: сектор, соседний взгляд, возврат и короткие/длинные фиксации. Если все цели закрыты или вне угла — тоже ищет сектор; очередь выстрелов и краткое перекрытие захвата ждёт. Без выстрелов",
            () -> c.aimIdleLook,v -> {c.aimIdleLook=v;save.run();}));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.searchStatus(),Ui.SUB).showIf(() -> c.aimIdleLook));
        rows.add(new Rows.Toggle("Ходить на месте", "Активный F12: Непрерывный Shift + короткие случайные A/D и паузы. Не одновременно A+D. Без проверок земли/края/полёта/радиуса, независимо от оружия и локации. Может смещать/ронять игрока; Shift в полёте опускает",
            () -> c.aimWalkInPlace,v -> {c.aimWalkInPlace=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.swayStatus(),Ui.SUB).showIf(() -> c.aimWalkInPlace));
        rows.add(new Rows.Note("Ручные движение/кнопка/движение мыши прекращают удержание до нового F12. Боевая паника останавливает A/D, но сохраняет Shift до перехвата или нового F12 в том же игровом контексте. Special/макрос/песты/окно/фокус имеют приоритет. Радиуса и защиты от края нет; XP/AFK-правила не меняются.",Ui.SUB).showIf(() -> c.aimWalkInPlace));
        rows.add(new Rows.Note("Камера общая: движок, скорости, реакция и осмотр настраиваются только в Kaelix Motion. Combat-профиль их не переопределяет.",Ui.SUB));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.cameraStatus(),Ui.SUB));
        rows.add(new Rows.Note("Одна камера: реакция → основной перевод → торможение/доводка → сопровождение. При переходе сохраняет только подходящую горизонтальную инерцию. Навигация имеет приоритет даже во время поиска пути; резкий внешний поворот/провал кадра сбрасывает старое движение. Точный режим без реакции.",Ui.SUB));
        rows.add(new Rows.Toggle("Помогать по вертикали", "Открытый центр тела; если закрыт — доступная часть настоящего хитбокса, не табличка с именем",() -> c.aimPitch,v -> {c.aimPitch=v;save.run();}));
        rows.add(new Rows.Toggle("Ограничить локацией пресета", "Опционально: искать место в sidebar. При неизвестном названии подзоны выключи",() -> c.aimLimitLocation,v -> {c.aimLimitLocation=v;save.run();}));
        rows.add(new Rows.Note("Живая цель удерживается; с переходом после выстрела становится ожидающей результата. Hold: отпустить — стоп. Toggle: нажать ещё раз — стоп. End выключает разрешение; боевая паника ставит сессию на паузу до нового нажатия; окна/смена мира сбрасывают Toggle.",Ui.SUB));
        rows.add(new Rows.Section("Зиалоты → Special Zealot (альфа)").key("combat.special").showIf(() -> c.aimZealotsOnly));
        rows.add(new Rows.Toggle("Искать Special Zealot по title", "Только при активном F12: известный title → ограниченный автоматический поиск рядом → special первым. Может двигать игрока; ручной ввод/End/паника отменяют",
            () -> c.aimSpecialHunt,v -> {c.aimSpecialHunt=v;AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}).showIf(() -> c.aimZealotsOnly));
        rows.add(new Rows.Number("Таймаут поиска/боя special", "Не дальше18бл от места title, только загруженный мир. При неудаче прекратить поиск; автолут/warp не используются",
            () -> c.aimSpecialSearchSeconds,v -> {c.aimSpecialSearchSeconds=(int)Math.round(v);save.run();},5,45,5,10,v -> Rows.num(v)+" с").showIf(() -> c.aimZealotsOnly&&c.aimSpecialHunt));
        rows.add(new Rows.Note(() -> AimAssist.INSTANCE.specialStatus(),Ui.SUB).showIf(() -> c.aimZealotsOnly));
        rows.add(new Rows.Note("Title не сообщает координат/владельца конкретного entity. Ищется подтверждённое имя Special Zealot рядом; чужой nearby special возможен. Для автоматического убийства включи автовыстрел. Ручная кнопка прекращает автоматику.",Ui.SUB).showIf(() -> c.aimZealotsOnly));
        rows.add(new Rows.Section("Каталог мобов и локаций").key("combat.catalog"));
        rows.add(new Rows.Selector("Группа", "Смена группы заменяет выбранный список; можно отключить каждого моба отдельно",
            () -> MobCatalog.GROUPS.stream().map(g->new Rows.Choice(g.id(),g.title())).toList(),() -> c.aimPreset,v -> {
                c.aimPreset=v;var g=MobCatalog.get(v);
                c.aimMobNames=new ArrayList<>(v.equals("nearby")?nearby():g.mobs());AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();rebuild.run();
            }));
        List<String> names=c.aimPreset.equals("nearby")?nearby():group.mobs();
        rows.add(new Rows.Note(c.aimPreset.equals("nearby")?"Список реальных загруженных Mob; не игроки/NPC-таблички":"Локации: "+String.join(", ",group.locations()),Ui.SUB));
        rows.add(new Rows.Buttons(new Rows.Btn("Выбрать всех",Rows.Style.SECONDARY,()->{c.aimMobNames=new ArrayList<>(names);save.run();}),
            new Rows.Btn("Убрать всех",Rows.Style.SECONDARY,()->{c.aimMobNames.clear();c.aimExtraNames="";AimAssist.INSTANCE.configure(c.aimAssistEnabled);save.run();}),
            new Rows.Btn("Обновить список",Rows.Style.SECONDARY,rebuild)));
        for(String name:names)if(!name.isBlank())rows.add(new Rows.Toggle(name,"Разрешённая цель для выбранного пресета",
            () -> c.aimMobNames.contains(name),v -> {if(v&&!c.aimMobNames.contains(name))c.aimMobNames.add(name);else if(!v)c.aimMobNames.remove(name);save.run();}));
        rows.add(new Rows.TextField("Дополнительные имена", "Точные имена без [Lv] и здоровья, через запятую; расширяют список выбранного пресета",
            () -> c.aimExtraNames,v -> {c.aimExtraNames=v.strip();save.run();return true;},null,1024,0));
        rows.add(new Rows.Note("Каталог расширяемый, не гарантия распознавания каждого серверного disguise. Автовыстрел Glacial Scythe работает по выбранным мобам любого профиля, если есть открытая траектория. Автоматики других оружий/ближнего боя/инструментов Hunting пока нет.",Ui.SUB));
    }
    private static List<String> nearby(){return MobNames.nearby(Minecraft.getInstance(),15).stream().map(MobNames.Named::name).distinct().sorted().toList();}
}
