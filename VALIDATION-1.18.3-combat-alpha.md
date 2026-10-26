# Статус 1.18.3-combat-alpha

**Только компиляция и упаковка `gradlew jar`.** По прямой просьбе пользователя НЕ запускались Minecraft, сервер, тесты/stress/симуляции или GUI-preview. Старые validation не распространяются на эту правку.

API MC 26.1.2 проверено через javap: Mob/Entity, LivingEntity.hasLineOfSight, Entity.turn/getEyePosition/getLookAngle, AABB.clip, Minecraft.isWindowActive, MouseHandler.isMouseGrabbed. Java 25, Gradle 9.7.1, Loom 1.18.3, Fabric API 0.155.3+26.1.2.

Проверено на уровне исходников/компиляции: новый aim assist и каталог/вкладка, приоритет других владельцев камеры, End/паника off, альфа-пометка пестов и direct-close capture, ровный navigation pitch и сокращение throttle/strafe на поворотах. Реальные nametags/hitboxes, плавность/LoS и поведение в Garden/Galatea/Slayer не подтверждены. Mob-only: player-skin NPC, составные display-only critters не поддерживаются.

Пакет исходников кумулятивный от commit 1bc8ee35b3df6ee5c3e50239d29d68158666c69f; старые файлы конфигурации/маршрутов не заменяются.
