# K2-007 / alpha.13 — Pests: реализация и границы

Реализация завершена по коду; **игровая приёмка НЕ выполнена**. Не обещание идеальной ловли/универсальной конструкции или защиты от блокировок. Проверено чтением исходников/API, jar-сборкой и архивами. Minecraft/сервер/тесты/stress/симуляции/benchmarks/GUI harness НЕ запускались по просьбе пользователя.

## Где и как включать
Отдельная вкладка Pests · Alpha. F10 — ручной старт/стоп в подтверждённом Hypixel Garden, нужен Vacuum в хотбаре. Разрешение охоты и поиск плотов используют прежние canonical `pestHuntEnabled`/`pestPlotSearch`: старые значения не теряются. Новая `ModConfig.pests` создаётся/санитизируется при загрузке старого JSON. Общая навигация/полёт остаются в Автоматизации; только pest-specific политика теперь здесь. Лассо/обслуживание ловушек, расширенные трассеры и ручной режим — следующий K008; подпись в GUI не означает реализацию.

Все новые активные opt-ins (auto/resume/Tracker/arc/ESP/clock/plotwarp/AOTV/Etherwarp/Shredder/leaf clearing) OFF по умолчанию. `skipFever` и `afterRewarp` — политики, сами ничего не запускают. Установи пределы поиска/цели/сессии/FOV/камеры/lead; реальные range не увеличиваются настройкой.

## Доказательства плотов
- Tab Pests/Alive даёт свежий числовой total10с. Plots: IDs обновляет заражённые плоты; чат spawn/offline может дополнить очередь. TTL3мин, reset при смене ClientLevel/выходе из Garden. Имена читаются из Configure Plots, геометрия5x5/96бл; Greenhouse/incomplete slots не обрабатываются как обычные готовые плоты.
- Per-plot numeric count из подтверждённой lore/числовой sidebar имеетTTL30с. Присутствие ID в Plots не равно количеству. `-1` = неизвестно, не ноль. Различай заражённый плот и уже загруженный Bat/Silverfish с подтверждённой pest-меткой.
- Leave-one IDs0..24: неизвестное количество блокирует охоту во всём таком плоту. Бюджет count-1 только уменьшается по fresh smaller count/наблюдаемой смерти. Stale count не восполняет бюджет.
- Fever: точные сообщения эффекта,60с срок. Server clock Garden h:mmam/pm имеетTTL10с; неизвестное время при включённом окне блокирует автостарт. Overnight поддерживается. Это не часы ОС/vanilla sky.
- Queue ближайшего не посещённого заражённого плота, не глобальный ESP. Мобы за unloaded chunks не придуманы. Неподдерживаемый формат не интерпретируется как завершение.

## Старт, состояния и пределы
`IDLE → SEARCH / PLOT / TRACKER / CLEAR / SHREDDER → FOLLOW / VACUUM → VERIFY → RETURN`.

| Этап | Основание и предел |
|---|---|
| Auto window | только active harvest enabled farm route, свежийtotal>=threshold, screen/focus/lease/panic/Fever/time; настраиваемое ограниченное окно; после Rewarp обычное окно либо30с отсрочки |
| SEARCH/PLOT | searchSeconds10–180; посещённых<=25; loaded navigation/chunk wait/repath bounded; нет подсказки/плота/цели — возврат без права считать всех убитыми |
| Tracker | explicit own request,1.4с cooldown,0–8attempts;2.2с capture/96samples,2.3с wait; phase<=240ticks; loaded safe hint flight, not hidden entity coordinates |
| Target/Vacuum | maxTargets1–128;5–120с цель,3–60с без видимого сниженияHP; реальныеrange/LOS/actual view/acquireTicks; отсутствие use!=damage proof |
| VERIFY | исчезновение не смерть;40ticks поиск близкого замещения; явная наблюдаемая смерть отдельно |
| Travel | <=12actions/hunt,<=160ticks/action,3с server confirmation,3stable observations; exact one-use position/Y/view/slot/world/lease check |
| CLEAR | explicitOFFopt-in,<=3distinct nearby ray-visible leaves,80ticks/block; actual first looked block match before destroy |
| Shredder | whitelistplots,1–4confirmed steps,finite5–100tick downward window,<=400ticks phase; неподходящая конструкция пропускается с причиной |
| Return/session | общийsession30–900с+wall clock cap; return<=1800ticks; loaded corridor/arrival/posture/speed/view; finite no-route failure |

Status/overlay показывают причину действия или отказа. PanicDetector один раз до pest input, не второй внутри hunter. Ручной ввод/мышь/End/F10/GUI/смена мира/потеря разрешения и предмета снимают управление и право resume. Продолжение только новым ручным запуском, кроме строго разрешённого успешного auto-return ниже.

## Vacuum, распознавание, камера
Распознаются загруженные Bat/Silverfish и pest custom names/HP ArmorStand labels. Ограниченный индекс:<=256bodies/512labels, одна ближайшая body на label; короткий3с grace для исчезнувшей надписи, не вечная метка. Scoped by world и expiry; выбранная цель сохраняется до конечного исхода.

`VacuumInfo` приоритетно проверяет SkyBlockID (unknown explicitID rejected), ability-section range в lore; известные диапазоны fallback, missingID compatibility name fallback. Не берётся дальность другого ability из lore. Открытая точка тела ищется несколькими raycasts; lead0–3ticks ограничен0.7бл, discontinuity/blocked lead игнорируется. Use только в фактической range выбранного тела, с реальным look ray пересекающим bbox, углом<6° и acquireTicks. Выход изrange/LOS/смерть отпускает Use. HP берётся из связанной метки, не первой произвольной стойки рядом.

ESP (отдельныйOFFopt-in) depth-tested только loaded recognized mobs; дуга — лишь собственные наблюдаемые частицы. Tracker использует vanilla `LocalPlayer.swing(MAIN_HAND)` (javap: отправляет swing packet), НЕ attack/block breaking. Capture только own-request/world/краткое окно/ANGRY_VILLAGER count1 speed0 xyzDist0/последовательность nearby particles. Последняя касательная — ограниченная подсказка направления, не polynomial distant-pest coordinates и не гарантия сервера.

## Предметные перемещения / безопасность
AOTV/AOTE — точныйID/ability/lore range4–16, loaded full-body clear short corridor и безопасная landing если не flying. Etherwarp — способность в lore,range8–100, видимая верхняя грань, свободная безопасная позиция, реальныйray передuse. Крышу ищет ограниченно по направлению кплоту; нет blind teleports into unknown geometry. Sneak для merged Etherwarp, не standalone Conduit. Мана/Soulflow/Slayer/server policy НЕ проверены независимо: сервер может отклонить, это finite failure, не успех.

Plot warp отправляется только при opt-in и вручную сохранённой landing соответствующего ID/world/dimension. Встань **в реальный spawn после своего ручного plot warp**, сохрани координаты и настоящий послепереносный взгляд. Не сохраняй случайную точку грядки: mismatch вызовет стоп/панику. Сохранение разрешено только Garden; команда не создаёт landing автоматически. Имя берётся из Configure Plots либо ID, ограниченный command alias без control chars.

PestTravel использует NavigationInput lease, выбранныеID/UUID, deliberate slot expectation, не SetPos/SetVelocity/abilities. Один request→WAIT, exact observed server position<=1.25,Y±0.4,view±2°,same world/lease/slot,<=3с,single-use. Иные server moves проходят обычные guards; неожиданное перемещение без подтверждения вызывает отказ даже при panicOFF. Нет whole-plot/TTL-only общего допуска телепортов. Exact acknowledgment ограничен ожидаемым действием и не доказывает происхождение пакета. Нет blanket staff-check bypass.

## Shredder / листья — условная применимость
Изученный Aether `PestBallsackShredder` — layout-specific pre-stage, не отдельное название предмета. В этой реализации намеренно НЕ слепой зажим AOTV до Nлюбых изменений позиции. Explicit plots (пусто=нигде), текущий реальный взгляд/видимая верхняя грань/подходящий Etherwarp/free landing/точное подтверждение. Потом ограниченное окно вниз75°, Vacuum лишь для распознанного открытого тела в реальнойrange/actual look ray. Если очередная посадка неприменима — обычная охота с причиной. Билд с невидимым потолком/недоступной верхней гранью не обещается поддержанным.

`breakLeaves` толькоOFFopt-in: ближайшая блокирующая leaf по действительному лучу кцели, не нижеplayerY, <=3attempts; не пол/стены/урожай/произвольная расчистка. Серверное запрещённое breaking finite-fails.

## Завершение и возврат
20consecutive observations fresh server zero либо fresh total==число подтверждённых retained plot counts1 дают completionKnown. Отсутствие загруженныхtargets/плотexpired/timeout/исчезновение не дают такого права. Возврат вlaunch position/view/posture требуетstable3 близость<0.6/speed<0.08,оригинальныйflightилиground,точныйвзгляд; потеря возможности исходногоflight—стоп.

Auto resume OFF поумолчанию. Нужны auto-run+explicitresumeFarm+completionKnown+fresh noWorkRemains **повторно после возврата**,sameworld+sameRouteBufferrevision+savedMatchesSource+screen/focus/pending guards. Нетresumeпослемануальнойохоты/паники/отмены/изменённогофайла/unknown/timeout. ИсходныйslotвосстанавливаетсялишьеслиещёнашVacuumslotиnopendingpanic, не поверх ручной смены.

## Референсы и происхождение
Aether tag1.3.1-r6,commit34d3b894d407fe06a5ba1e822f0616f17beccd7b (GPL-3.0): изучены PestTrackerAbility/Search/Trail, PestCompletionGuard, PestTabSnapshot, LeaveOne, CropFever/Time, Aotv/Etherwarp/Roof/Shredder, VacuumProfile. Код/ассеты НЕ копировались/адаптировались. Наши boundedFSM/particle-tangent/exact-travel/cache/render используют существующие собственные классы Kaelix.

Источники для исследования (не гарантия свежести серверного протокола):
- https://github.com/mizly/aether/releases/tag/1.3.1-r6
- https://wiki.hypixel.net/InfiniVacuum%E2%84%A2
- https://wiki.hypixel.net/Etherwarp_Conduit
- https://hypixelskyblock.minecraft.wiki/w/Vacuums
- https://hypixelskyblock.minecraft.wiki/w/Pests
- https://hypixelskyblock.minecraft.wiki/w/Crop_Fever

## Только пользовательская приёмка — агент НЕ выполнял
1. СтарыйJSON/toggles/settings, вкладка/F10/manual stop/masterpermission; Vacuumвхотбаре/смена/UUID/неVacuum.
2. ТаблоGarden/Tabnumbers/plots/customaliases/ConfigurePlots/incomplete/Greenhouse/unknownTTL; leave-oneunknown/knowncount, nofalseempty.
3. Auto harvestonly/threshold/window/Fever/time/overnight/rewarp; noautofromidle/Combat; lease/manual/GUI/panic cancels.
4. Front/near/far loadedpest, markerloss/death/despawn/replacement, LOSopenbody/range/livelead, noDamagebounded; Tracker particles/failure/nohint; ESPdepth.
5. Existing verifiedFlightGuide/ReturnWalker flight/rise/drop/chunks/roof/flypermissionloss, budget/timeout/failuremessage.
6. Itemrequirementrejection/cooldown/mana/Soulflow; exactlanding/view vsotherteleport/packet/noack/UUID/manualcancel. Без broadpanic exception.
7. Realplotspawn savedpoint, renamedalias/movedspawn/unknownpoint preventsfalseack. Shredder only compatiblevisiblelayout; leavesOFF/visibleonly/noharvestfloor.
8. Successfulreturn/stableposture/view/actualfreshcompletion, samefarmrevision/file/autoresumeoptin; noresumeafterunknown/manual/panic/GUI/timeout.

Ни один пункт не считается пройденным по успешной компиляции. Stable2.0.0 только после согласования пользователя; KeyAuth отложен, push/commit/auth/publication не выполнялись.
