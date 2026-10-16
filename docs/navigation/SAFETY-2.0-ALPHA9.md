# K2-003 / alpha.9 — реализация безопасности

Стек: Minecraft26.1.2, Fabric Loader0.19.5 / API0.155.3+26.1.2, Java25.
Статус: implementation complete; game acceptance NOT passed. Только исходники/API/javap/компиляция jar/архивы. Ни игра, ни тестовый harness не выполнялись.

## Политика по модулям

| Сигнал | Combat/Hunting | Ферма/маршрут | Pests Alpha |
| --- | --- | --- | --- |
| Серверная позиция/мир/респавн | Свой teleport-switch; единичные малые пакеты в bounded sliding budget | Свой emergency teleport-switch/действие реестра; без Combat-допусков | Как общая политика, не заимствует Combat-конфиг |
| Серверный поворот/look-at | Свой serverRotation, default20°, независим от teleport | Существующие rotation/serverMove/sync/expectTurn и настройки реестра | Свои вызовы expectTurn и общие guard-настройки; отдельного Combat-профиля нет |
| Item/известныеID/UUID/слот | Свой weapon-switch, немедленный local pause | Свой emergency weapon + legacy slot/heldItem | Общий guard; явный select/expectSlot учитывается |
| Приветствие/обращение | Отдельные радиус/память/друзья/слова Combat | Свои nearby-chat/друзья; общий social guard только с подтверждённым автором | Общий nearby/social guard, не Combat |
| GUI | Context handoff без тревоги, освобождение Shift | Чужой GUI: fire по действию; любой GUI даёт обычный стоп даже при LOG/OFF | Чужой GUI по общему guard + abort, закрытие не перезапускает |
| HP/мана/weapon actionbar | Не социальный сигнал | Не социальный сигнал | Не социальный сигнал |
| BPS/Farming XP | Не применяются | Только harvest context, наблюдавшийся урожай, непрерывные ЛКМ+ход | Не применяются; on-broke desync тоже только crop harvest |
| Подозрительный полный блок | Не новая проверка Combat | Ограниченные кандидаты, actual collision contact3ticks; опасное появление вплотную отдельный immediate guard | Фермерский contact-сигнал выключен контекстом; общие опасные block/cage guards остаются |
| Полный инвентарь | Нет стопа | Нет стопа | Нет стопа |

Выключенный пользователем detector/LOG/SUSP не становится принудительной panic-политикой. GUI context stop действует независимо от вида уведомления. Combat master/profile не меняется паникой; End и явное выключение — отдельные команды пользователя.

## Подтверждение / ожидаемые действия

- Серьёзный телепорт/респавн/смена Item/известногоID/UUID без промедления; никаких дополнительных выстрелов ради правдоподобия. Снимок мира не зависит от weapon-switch. Сетевые inventory-hooks RETURN проверяют применённое состояние, не отменяют vanilla/ack.
- Малые Combat corrections: per packet XZ≤0.8/Y≤0.5/rot≤10°; sliding3с,≤4samples, sum Euclidean lengths≤2бл. GUI: окно1–10с, число1–12, сумма0.05–6. Opposite movements add, not cancel. Caps не растут с ping; бюджеты очищаются при потере контекста/новом run/мире/панике/disconnect. ≤.001бл позиционный шум не считается; отдельно проверяется rot. Серию сверх budget сразу останавливаем, не ждём ещё один тик. Даже малый допустимый перенос может быть реальной проверкой — это компромисс, а не гарантия.
- Собственная камера сообщает expectTurn/expectRotation; Squeaky Mousemat исключён только при совпавшем ожидаемом угле/окне без переноса. Явный expectSlot подтверждает фактически установленный слот, не общий blind250ms grace. Изменение расхода стака/прочности/лора/маны не равно потере оружия. Если исходный ID/UUID ещё не известен, его нельзя сравнить.
- Существующий Rewarp TeleportWindow не расширен новым blanket exemption. При включённом emergency teleport-switch строгий guard может остановить и ожидаемый rewarp; точная единая teleport-policy — ограничение на дальнейшее K004/K005, а не разрешение игнорировать произвольный teleport. Никакого автоматического восстановления после panic.
- Чат: автор UUID либо строгий заголовок unsigned-строки + известная identity. Nearby дополнительно требует реальную текущую/недавнюю близость. Неоднозначность не угадывается и не вызывает автоматический ответ; короткая фаза распознавания — сам разбор текущего события, без фоновой задержки/продолжения. Отсутствие sender может пропустить нестандартный серверный формат. UUID/Tab/header не доказывают намерения и не идентифицируют staff.
- BPS: данные crop break; evidence из crop ray/фактического crop break/ожидаемого XP text. После evidence непрерывный attack+walk считается harvest segment; если идти по пустому переходу с теми же клавишами, это может снизить BPS. Порог ручной, default detectBps=false. Навигация/countdown/route-end/pest/flight/ladder/GUI/pause/lag сбрасывают окно. Полное BPS окно2–30с +40workticks, затем20low ticks; ни latency, ни server success по этому не доказываются.
- XP: detectFarmingXp=false по умолчанию и не opt-in от «Включить все». Разбор только выражений с Farming XP/EXP или +число Farming; отдельный telemetry route перед return actionbar. Нужно наблюдать XP в этом work segment; never-seen текст не тревожит.40ticks warmup, default8с отсутствия (3–60),10consecutive подтверждений. Пауза/контекст/обнаруженный лаг полностью очищают baseline; требуется новая строка. Скрывающий actionbar мод/изменение формата может дать пропуск/ложную тревогу, это не доказанный ghost block. Один сигнал после потери baseline, повторное arm только новой XP-строкой.
- Блок фермы: candidates≤512,TTL60с, недавние crop breaks10с и grown/moved исключены. Три последовательных shape-контакта, сброс при исчезновении/разрыве/лаге/новом событии/выходе контекста. Отсутствующий чанк не читается как воздух. Опасный серверный блок у хитбокса — иной immediate blockNear guard согласно реестру, growth/moved также исключены.

## Shift / ручное управление / камера

Существующая raw A/D-схема сохраняется: паузы/повторы/случайная длительность, без terrain/support/edge gates. KeyboardInput HEAD+RETURN поддерживают только принадлежащий активному local player присед, включая toggleCrouch. Контекст — живой тот же мир, free Combat, фокус/нет GUI/паузы/другого владельца. Потеря контекста не восстанавливает старое физическое состояние.

После local combat panic прежний owned Shift может оставаться до B/ручного ввода/End; боковые клавиши остановлены. Manual Sprint теперь тоже takeover. Нельзя обещать, что Shift удержит от падения; в полёте он снижает. Предусмотрены physical keyboard/mouse и настройки aimYieldMouse; idle/Sway уступают отдельно. Обычный tracking при явном aimYieldMouse=false не переопределён этим этапом.

Локальный stop сбрасывает контроллер, но не возвращает углы назад и не делает snap к исходной камере. При ручном вводе/опасности не добавляется «плавная остановка» с продолжением поворота или стрельбы. При настоящем серверном повороте сам серверный snap не отменяется. Pending панель блокирует новые модульные старты, не vanilla mouse/движение.

GUI: stop и отмена старых Scheduler/Reconnect/Mousemat/SmoothTurn/реакции; закрытие не resume. Окно DisconnectedScreen исключено из общего GUI-policy и обрабатывается исходной disconnect/reconnect policy, не безусловным авторестартом после panic. Для фермы фоновый Ungrab/фокус остаётся прежней явной возможностью, в отличие от free Combat. Это не общий новый lock пользовательского ввода.

## Сравнение Aether / лицензия

Reference tag1.3.1-r6, commit34d3b894d407fe06a5ba1e822f0616f17beccd7b, GPL3:
- RotationFailsafe: expected baseline/context/grace/mismatch. Использованы идеи ожидаемого поворота и контекста; большие server changes не получили random trigger delay.
- InventorySlotFailsafe: expected slot/250ms/delay. У нас только конкретный actual slot acknowledgement, не blind grace для подмены оружия.
- UnexpectedInventoryFailsafe: FARMING/CLEANING и expected GUI modules. Сейчас нет готовых visitor/loadout GUI automations; не добавляем исключения по произвольному названию окна. Любое GUI останавливает; network disconnect lifecycle отдельно.
- BpsFailsafe: active farming window + sustained low wait. У нас crop-evidence + непрерывное окно/прогрев/20ticks, без inherited threshold/delay.
- GhostBlockFailsafe: farming-text freshness / missing wait. У нас observable-baseline opt-in, timeout/confirmation, wording «пропал текст», а не недоказанное «XP нет».
- DirtFailsafe: placed full block/crops/water/own break/TTL/touch. У нас собственные bounded maps и shape-contact3ticks вместо сферической дистанции1.5бл.

Код и ассеты не копировались/не адаптировались, GPL-код в поставляемую MIT-базу не включён. No staff identity, no banproof, no human-indistinguishability claims.
Sources: https://github.com/mizly/aether/tree/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/failsafe and https://docs.fabricmc.net/26.1.2/develop/porting . Cached official26.1.2 javap verified getPlayerInfo(UUID/String), ToggleKeyMapping.setDown behavior, LocalPlayer.onClimbable and block shape APIs through compilation.

## Ручная приёмка — только пользователь, НЕ запускалась агентом

1. Backup config; один alpha9JAR; включение hold/toggle и переназначенный Aim/B. На платформе: A/D паузы, противоположные циклы, Shift в toggle/hold. Manual mouse/W/A/S/D/Jump/Sprint/B/End, GUI/alt-tab, смерть/мир. Проверить реальный ввод и отсутствие sticky Shift после takeover.
2. Local combat panic сохраняет master/profile, нет camera/fire/A-D, owned Shift только в valid context. B гасит панель и не resume; следующее намеренное Aim press возвращает работу. Не воспроизводить проверки обходом серверных правил; допустимая ручная/private-среда.
3. ≤.001/единичная малая коррекция/повторные в3с/превышение суммы/крупное смещение/rot below-above20°/look-at, teleport выключен при rotation включён. Порог не расширяется сам. Осознанно строгий farm teleport и старый Rewarp-guard interaction проверить отдельно.
4. Слот модом/слот пользователем/потеря/подмена Item/ID/UUID vs расход/прочность/мана. Начало без ID/добавление данных, toggleweapon=false не скрывает world guard. Смена мира/respawn до следующего use request.
5. HP/mana/ability actionbar и такой же системный текст с ключевыми словами не тревожат чат; собственное/To/друг игнорируются; подписанное/известный unsigned sender распознаются; unknown/неоднозначное не угадывается; recent-neighbor hi реагирует правильным профилем. PM/AFK остаются отдельными системными детекторами.
6. BPS: сбор после evidence, прогрев+20low ticks; здоровый сбор, пустой участок с зажатой ЛКМ, пауза/лаг/навигация/конец маршрута/полёт/лестница/Pests; никаких чужих harvest-тревог. XP выключено/включено: never observed vs fresh text vs timeout+10ticks; пауза/лаг требуют fresh baseline, hiddenHUD оговорён.
7. Новый full block рядом vs исчезнувший до3тиков/нетконтакта/реальный shape-contact/ростcrop/поршень/свойcropbreak/выгруженный chunk. Immediate blockNear отдельно по его настройке.
8. GUI личное/чужое во время farm/pest/countdown/расписания; нет кликов/поворотов/авторестарта после закрытия; configured disconnect reconnect не сломан; panicpending не авторезюмируется. История/звук/B/GUI ack, отдельныеCombat options, fullinventorynonstop. K001/K002 и alpha5/6 nav regressions вручную.

Сборка не подтверждает качество/безошибочность выполнения этих сценариев. Реальные дефекты переоткрывают K003 до расширения следующего этапа.

Alpha.9 final review: pending-паника сохраняется после disconnect до явного подтверждения; исходная причина не заменяется вторичной disconnect-тревогой. Reconnect.wouldReconnect/tick и Scheduler.tick имеют прямой pending guard. GUI-policy не вмешивается в connect/login screens без player/world; configured nonpanic reconnect не отменяется собственным ConnectScreen. Это статическая проверка, не сетевой игровой тест.
