# K2-014: статическое сопоставление с Aether

Публичный reference tree SHA `34d3b894d407fe06a5ba1e822f0616f17beccd7b`, read-only. GPL-3.0 implementation/assets не скопированы и не адаптированы в Kaelix; реализации написаны в существующей архитектуре проекта. Это сравнение кода, не performance measurements.

| Область | Что рассмотрено в Aether | Выбор Kaelix |
|---|---|---|
| Performance | PerformanceModeManager: временные FPS/renderDistance/ParticleStatus и changed flags | Улучшен свой старый PERF: crash-recovery fields, compare-owned restore, независимое восстановление, отдельный opt-in cap чанков, no focus spoof |
| Sound | MuteManager: config/state/volume accessor | Собственный выборочный multiply категорий, не глобальный mute; MASTER/panic/UI/players остаются; не обещаем immediate смену существующих каналов |
| ESP/targets | PestEspManager: renderable real loaded pest bodies через tracker, отдельные highlights/tracers/route, проверки removed/dead | Свой depth-tested render сохранён; discovery Combat/Pests делит один tick-снимок, геометрия/решение выстрела свежие; временные массивы prism убраны. Armorstand label не становится телом |
| HUD | HudRegistry: разделение gameplay/edit paths, подготовка/очередь render callbacks | Свои Fabric callbacks/F8 сохранены, без NanoVG/дублирующей очереди. Подготовленный payload measure+draw, числовые карточки10Hz, горячая задача каждыйlayout |
| Path | AbstractPathfinder: async executor/context/max-iterations/cancellation; ранее изученные flight/navigation files | Свой snapshot/A*; не work-stealing pool, а1worker/queue1; cancel/removal,5с cooperative budget, cap snapshot/decode; live validation before movement unchanged |

## Источники (публичный код, не зависимости JAR)
- https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/performance/PerformanceModeManager.java
- https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/performance/MuteManager.java
- https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/visuals/PestEspManager.java
- https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/hud/HudRegistry.java
- https://github.com/mizly/aether/blob/34d3b894d407fe06a5ba1e822f0616f17beccd7b/src/main/java/dev/aether/modules/pathfinding/pathfinder/AbstractPathfinder.java

API проверен javap MC26.1.2: com.mojang.blaze3d.platform.FramerateLimitTracker.getFramerateLimit() RETURN; ClientLevel.addDestroyBlockEffect(BlockPos,BlockState), addBreakingBlockEffect(BlockPos,Direction); SoundEngine.calculateVolume(float,SoundSource) RETURN. Три собственных mixin зарегистрированы. Их runtime apply/совместимость с другими модами не проверялась запуском, нужна пользовательская приёмка.
