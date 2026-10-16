# K2-004 / alpha.10 — общая наземная навигация

Стек: Minecraft26.1.2, Fabric Loader0.19.5/API0.155.3+26.1.2, Loom1.18.3, Java25.
**Implementation complete. Game acceptance outstanding.** Компиляция и статическая инспекция не подтверждают прохождение в игре. Minecraft/client/server/tests/stress/simulations/benchmarks/GUI harness не запускаются.

## Покрытие этапа

| Запрос | Реализация | Граница |
| --- | --- | --- |
| Общий движок | MacroManager.approach, PestHunter.walker, SpecialZealotHunt.walker используют ReturnWalker/PathJob/ReturnCore | Точный записанный playback намеренно не переучен |
| Клавиши+камера | NavigationInput lease, scoped camera request, SmoothTurn frame-owner guard; Macro press/release fencing; CameraPresets и Mousemat не стартуют поверх lease | Другие установленные моды/сервер могут изменить ввод/углы; это не глобальный OS-арбитр |
| Ручное управление | Physical movement keys, mouse displacement>.7px, mouse-down отменяют walker и его текущую farm/pest/special задачу | Первое mouse callback после acquire калибрует координаты; vanilla событие не отменяется |
| Формы/опоры | PathLevel реальные AABB; CachedPathWorld exactAt/supportY только свежая геометрия; GroundProbe9support; loaded A* проверяет полный sweep | Старая сетка x/z+.5, не navmesh; тонкий проход может дать отказ |
| Ступени/прыжок | GroundProbe ищет9reachable treads; GroundTransition up/across/down envelope+3попытки | Не баллистическая симуляция, не моделирует buffs/slime/honey/server-speed |
| Ladder/vine/scaffold | NavigationClimb attachment-side anchor/push, observed onClimbable, центрирование лесов, вверх/вниз и exit sweep | Частичные/нестандартные крепления/ступени могут быть консервативно отвергнуты |
| Уступ | NavigationDrop: проверенная lower support+horizontal/vertical volume, actual vector,80tickphase limit; WalkCore ground/depth confirmation | Не гарантирует безопасную посадку при толчке/серверном лаге; максимум3бл |
| Дверь | NavigationDoor сохранён: видимая shape, actual-view range/ray, useItemOn;3/run/door, ledger64,100ticks | Debounce3ticks не доказывает server acknowledgement; приват даёт отказ |
| Застревание | Best-so-far80ticks; local recovery32cells count≤4 penalty≤12, общий returnReplans | Штраф не гарантирует другой путь, единственный маршрут может повториться до лимита |
| Маршрут/камера | Направление/стены влияют на стоимость; smoothing только verified same-level ходьба без дверей; guided local gaze | A* с heading preference не доказывает глобальную оптимальность, эстетика субъективна |
| Спринт/прыжок | Не зависит от naturalLook; угол<18°, цель>5бл, level,4бл footprint+head clearance, forward-only, hunger>6/mayfly, cooldown35–90ticks | Envelope4бл не предсказывает точную дальность при нестандартной скорости |

## Владение и lifecycle

- Lease выдаётся одному ReturnWalker. Сначала отменяются старые button gates/обычный SmoothTurn и CombatSway; смена владельца не происходит неявно.
- SmoothTurn принимает navigation turn только в scope текущего токена, запоминает владельца и проверяет его **на кадре**, а также GUI/focus/pause/pending-panic. Чужая farming look не пишет углы во время lease.
- NavigationInput.press непосредственно устанавливает реальные KeyMapping; Shift/Sprint toggle-aware. MouseHold/HandDelays не подмешиваются. MacroManager.press и releaseAll не переписывают чужую навигацию; чужой playback не тикает во время lease.
- Mousemat/Freelook/MouseLock/активная реакция препятствуют старту; появившаяся camera lock прекращает навигацию. Обычные presets нельзя применить поверх неё.
- Физический ввод сначала прекращает walker, отменяет текущую внешнюю задачу и освобождает lease; затем vanilla получает событие. Это предотвращает обход ручного stop через Pest skip/retry.
- End/паника/GUI/потеря мира/контекста отпускают keys/camera, cancel job, reset doors; idle/foreign walker не отпускает чужие клавиши. B после паники не resume; master Aim/Combat policy не изменены.
- Pests: inner PanicDetector.tick удалён, потому что FarmMacroMod уже вызывает central pre-action guard. Двойное подтверждение/накопление в одном игровом тике больше не создаётся исходниками.

## Геометрия и переходы

- Planning PathLevel допускает закрытую открываемую дверь **только при построении**, чтобы найти маршрут. Live PathLevel НЕ игнорирует её коллизию. Двери проверяются NavigationDoor раньше обычной ходьбы. Железная/неоткрываемая форма не получает такое исключение.
- GroundProbe тянет reachable опоры по реальным частям формы, проверяет поднятое тело и hazards. Cached wrappers не отключают live geometry; неизвестная клетка не исполняется вслепую, wait/revalidate policy сохранена.
- Exact walking transitions проверяют swept body/support; jumps — conservative envelope; drops — свободный коридор к фактической опоре. Generic clearance не использует maxTop/edge-mask поверх уже проверенных реальных shapes.
- Climb clearance игнорирует только форму самого CLIMBABLE в dedicated climb edge; прочие solid/hazard/unloaded остаются запретом. Это не отключает игровые коллизии. A* entry/exit проверяют non-climb solids; runtime наблюдает реальный контакт.
- Ladder/Vine направления выводятся из Mojang state properties. Нет horizontal face у вертикальной лианы — нейтральный anchor, движение подтверждается onClimbable/Y. На scaffold вверх Jump, вниз Shift; target baseline сбрасывается при смене точки.
- Climb timeout240ticks/60без улучшения; exit80/40. Реальный промежуточный Y-progress обновляет baseline, одно движение туда-сюда не равно подъёму.
- Drop EDGE/AIR/LAND по80тиков; нижняя опора и hazard проверены, глубина≤3, local length≤2.2. В EDGE Shift/Sprint/Jump отпущены, движение к landing, не только cardinal dropDir. AIR не даёт непроверенную воздушную коррекцию. Grounded height completion±.2 защищает обычную точку от зачёта на другом этаже.
- Recovery запоминает ближайшую неудачную область, не только дальний конец сглаженной прямой; не hard-blacklist. Карта32cells очищается на новую задачу, сохраняется между её replans и копируется immutable в worker.
- Если за время поиска XZ изменились>.65/Y>.75, результат не применяется: конечное перестроение из текущей позиции. Общий maxNodes80k/JOB400ticks/chunkwait200ticks/replans policy сохранены.
- Сглаживание не проходит через closed door и не сливает высотные WALK-узлы. Existing flight smoothing/capability не переучены: этоK005.

## Сравнение Aether, без переноса кода

Прочитаны исходники фиксированного commit34d3b894d407fe06a5ba1e822f0616f17beccd7b (tag1.3.1-r6):
- WalkingMotion: клавиши вычисляются относительно **фактического yaw**. Kaelix сохраняет собственный WalkCore.keysFor/keyboard hysteresis, не пишет velocity.
- WalkingObstacleProbe: collision/headroom/достижимая ступень до столкновения. В Kaelix — собственный9support sampler, loaded exact-shape sweep, ранний GroundGuide и ограниченный envelope, не порт physics расчёта Aether.
- WalkingRoute: высотное продвижение/ограничение lookahead на поворотах/падении. В Kaelix — own height completion/drop milestones и local intent; без переноса projection/route-кода.
- WalkingStepNormalizer: не считать искусственный lift обычным step, сохранять climb/water. Kaelix вместо удаления height-переходов сохраняет special nodes и проверяет реальную опору.

Aether GPLv3-код/ассеты не копировались и не адаптировались; существующая MIT-база не перелицензирована. Нет гарантий human-indistinguishability/антибана. Полёт/AOTE/AOTV/warp-подходы оставленыK005.

## Ручная приёмка — выполняет только пользователь

1. Сохранить config и старый JAR; оставить один новыйalpha.10 вmods. Обычный flat corridor, шаг0.5, лестница ступеней,1бл jump, низкая голова, fence/wall, открытая/закрытая/приватная дверь/калитка. Непроходимое место обязано дать объяснение и stop, не вечный цикл.
2. Ladder с четырёх сторон: снизу вверх и выход на платформу; сверху вниз, смещение/потеря контакта. Vines/vertical vines; scaffold изнутри вверх, с верхушки вниз, выход вбок. При неудаче прислать HUD/state/RETURN log и координаты геометрии, не секреты.
3. Уступ2бл (также1/3): старт рядом/диагонально к краю, нижняя опора, узкая посадка, вода/лава/дыра вместо опоры. Во время planned drop Shift не должен держать на краю; опасный переход должен отказать.
4. NaturalLook ON и OFF: один и тот же GroundGuide/прогресс/сprint; прямой коридор sprint и optional sprint-jump, поворот/край/низкая голова/ступень/дверь без них. Не считать отсутствие спринта на коротком сегменте багом.
5. Macro approach, точка/rejoin, Pest approach/return, Special search: один владелец; preset/MouseHold/CombatSway не вмешиваются. Ручные keys/mouse отменяют всю задачу без Pest повторного запуска. Hold и Toggle crouch/sprint; End/паника/B/GUI/focus/disconnect.
6. Повторный тупик, движущийся obstacle, долгий поиск и перемещение во время него, unload/reload/path-cache. Replans конечны, с понятной причиной. K001–K003 регрессии: aim/weapon/chat/pending-master/owned Shift сохранены; Pests guard не дублируется.

Узкие несеточные проходы, сложные vine exits, прива́ты, быстрые buffed движения и сетевой тайминг требуют реальной приёмки. Выпуск стабильного2.0.0 только после неё.
