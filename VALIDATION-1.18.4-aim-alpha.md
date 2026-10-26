# Статус 1.18.4-aim-alpha

**Только javap API / чтение исходников / compile+resources+jar.** Minecraft, сервер, unit/stress/simulation-тесты и GUI-preview не запускались по прямой просьбе пользователя.

API подтверждены по реальному MC 26.1.2 JAR: GameRenderer.render(DeltaTracker,boolean), Entity.getPosition(float)/getEyePosition(float), DeltaTracker.getGameTimeDeltaPartialTick(boolean). Native KeyMapping регистрации/consumeClick/isDown используются как в существующих клавишах мода.

Проверяемые сборкой изменения: F12 Hold/Toggle, runtime latch и сброс, nearest acquisition + same-target lock, bbox-center/интерполяция, FastAimTurn, единственный AimFrameMixin и новые настройки/локализация. Runtime корректность mixin, скорость/наведение, реальные смерть/LoS и переназначение на мышь пока не подтверждены. docs/navigation/AIM-KEYS.md.

Кумулятивный GitHub пакет от исходного commit 1bc8ee35b3df6ee5c3e50239d29d68158666c69f. Старые validation описывают свои версии, не новую.
