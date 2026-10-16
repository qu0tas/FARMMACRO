# K2-005 / alpha.11 — полёт и расширенный возврат

**Implementation complete; game acceptance outstanding.** MC26.1.2 / Loader0.19.5/API0.155.3+26.1.2 / Loom1.18.3 / Java25. Только code/API/javap/jar/archive inspection. Игру, сервер, тесты, stress, симуляции, benchmarks и GUI-стенды не запускали.

## Исправления и lifecycle

- FlightCapability.allowed: mayfly OR flying. Нет Creative-проверки, unilateral abilities writes или fake packet. Auto/«Есть полёт» — только разрешение планировать при настоящем permission. Packet observe после vanilla handlePlayerAbilities, только client thread; диагностическая запись scoped к player/world, свежесть30с. Она не выдаёт полёт и не заменяет актуальные flags.
- FlightSwitch: RELEASE/JUMP pulses0/3 в16tick cycle,3cycles/48ticks. Подтверждение — flying3последовательных наблюдения. Подтверждающий подъём только при live-clear. Потолок/permission/таймаут -> один noFly план, не бесконечные попытки. Во время уже наблюдавшегося полёта его неожиданный сброс без земли -> stop, не повторное включение.
- Grace собственного toggle4ticks вместо прежних200. Existing ability guard всё ещё проверяет изменение mayfly и скорости, исключение касается ожидаемого flying-тоггла, не общего перемещения/мира/оружия.
- Air controller перенесён до naturalLook early return. Удалены конкурирующие старые vertical branch и инверсия торможения; единый FlightGuide принимает фактический yaw и observed per-tick XYZ, axis hysteresis/min dwell, clear bounded prediction. Противоположный input используется для торможения, не переписывает физическую скорость.
- FlightGuide прогнозирует offset по инерции+input, проверяет swept standing-body/radius. В опасном объёме пробует brake и только проверенные local35/70degree стороны; общие best-so-far/replans ограничивают повтор. Неопасная видимая геометрия не гарантирует физически выполнимый манёвр при серверной скорости/лаговом толчке.
- EarlyTakeoff выбирает4/2/1lead, проверяет reachable walkway, vertical launch и connection к flight point, высоту≥edge+1.3 и≤planner ceiling. Применяется независимо от camera styling. Если места нет — остаётся исходный маршрут/finite takeoff refusal, не насильственное поднятие.
- Loaded A* fly edges проверяются реальным body sweep, включая диагональ и exact-goal connection. Air не использует climb-shape exemption. WALK landing nodes отделены от FLY; последняя FLY точка использует настоящий Goal.y. Direct flight leg≤30бл проверяет загруженность и запас cruise height, сбрасывает lifecycle на новую задачу.
- Arrival отделена от общего waypoint done: точка по радиусу не равна остановке. Land:9same-height supports/hazards, спуск только до поверхности, grounded+flyingOFF+низкаяXZspeed3ticks. Hover:radius.35/Y.22/XZspeed.045/Yspeed.04,3ticks. Deadline160ticks; failed support/shaft/clearance stops with reason. После стабилизации выравнивается endYaw/endPitch; completed latch не продолжает landing timer при долгом повороте.
- Не меняются реальные позиции, velocity, mayfly/flying, selected slot, flight-speed attributes. No useItem, new warp commands, packet spoof/cancel. Vanilla владеет ускорением и collisions.

## Сравнение с Aether и границы телепортов

Источник: фиксированныйtag1.3.1-r6 commit34d3b894d407fe06a5ba1e822f0616f17beccd7b; read-only GPLv3 reference, ни код, ни ассеты не копировались/адаптировались.

| Aether область | Наблюдаемый подход | Решение KaelixK005 |
| --- | --- | --- |
| FlightGuidance/FlightMotion | arrival/deceleration modes, braking input, coast checks | Собственный ограниченный keyboard FlightGuide/Arrival, не порт formulas/physics/harness |
| FlightProgressTracker | best-distance per waypoint | Existing NavigationProgress+bounded local replans сохраняются |
| FlightPathClearance | real body sweeps/coast geometry | Own NavigationMotion.airClear+loaded A* edge guards+live predicted corridor |
| WalkingRoute/PathExecutor | pair direction/height/goal centering/rejoin | Own route goal direction/height checks/finish yaw, stable ground-to-air/air-to-WALK |
| EtherwarpExecutor/EtherwarpHelper | Visible target, actual look, item ID/slot, bounded attempts, wait for observed destination | Изучено для K007. Не внедрён free right-click, teleport allowance или слот-автоматизация |
| PestAotvManager | roof/allowed plots/thread checks/startup/teleport observation | Изучено для K007. Нет нового AOTV/AOTE use или roof-breaking |
| Existing Kaelix Rewarp/Reconnect | Настроенная пользователем команда, finite post/near/return policy | Сохранена; RouteEnd.begin/tick/send теперь direct pending-panic fenced |

AOTE/AOTV/Etherwarp и plot-warp **не объявляются реализованными**: roadmapK007 явно содержит предметные/distant/roof/plot маршруты. ВK005 требование — изучить подходы. Включать такую возможность без known item/mana/range/actual view/destination acknowledgement и narrow expected-move policy опасно для управления, поэтому обычный flight не маскируется телепортом.

Existing Rewarp ожидание не доказывает, что пришло в правильную точку; строгая общая teleport policy всё ещё может остановить даже настроенный warp. Она не отключена автоматически и требует отдельной дальнейшей приёмки. Reconnect не стартует при pending panic; B не resume. Новых послепанических действий нет.

## Сохранённая безопасность

K004 lease keys+camera, physical manual cancellation of entire task, GUI/focus/pause/world/liveness/8block displacement, stale snapshot/maxNodes/job timeout, cache witnesses/chunkwait200ticks сохраняются. Flight prediction может отвергнуть участок раньше immediate2block readiness — тогда конечный replan/отказ, не движение по неизвестной геометрии. Релиз не гарантирует server ack, идеальный путь, бесконфликтность со сторонними модами или отсутствие санкций.

## Ручная приёмка — только пользователь

1. Survival SkyBlock с доступным полётом: в GUI mayfly/flying и packet status, естественный стиль ON/OFF. Без разрешения настройка не должна выдавать flying; выдать/отозвать permission, сменить мир/умереть.
2. На земле перед стеной/крышей: launch до упора, невысокий потолок/нет места/приват. Успешный double-jump,3наблюдения flying; неуспех≤3попыток/noFly/понятный отказ.
3. Вертикальный подъём, диагональный коридор/столб, поворот и торможение, низ/верх target, быстрый server speed/лаг. Препятствие до касания: brake/recovery/replan finite; обязательно проверить без naturalLook.
4. Ground return: полёт->WALK, финальная посадка на ровную опору,2ticktemporaryground, flyingflagdrop, затем endYaw/endPitch. Hover pests: стабильная близость/высота/малые скорости, исчезнувшая опора/опасный низ — отказ.
5. End/ручнойW/A/S/D/Space/Shift/мышь/B/GUI/потеря фокуса отменяют без автоповтора. Unexpected server displacement/permission revoke/flight loss не вызывают новых прыжков/кликов/команд.
6. Cache/unload/reload/different dimension/server, goal updates, новая direct flight leg, stale path drift. Pests/Special/Macro competition; K001–K004 regression. RouteEnd pending guards не разрешают warp/sendChat after panic.

Для замечаний нужны phase/status и RETURN log плюс описание препятствия; не присылайте личные токены/конфиги с секретами. Stable2.0.0 — только после одобрения пользователем.
