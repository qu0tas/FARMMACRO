# Validation 1.18.8-fire-alpha

API javap: vanilla useItem MAIN_HAND, packet yaw/pitch source, Player.getCooldowns/isOnCooldown(ItemStack), LivingEntity.isUsingItem, AABB.clip. Статический просмотр rate limit, actual camera ray, center/hit LOS, guards active/free/master/keys, target alive/name/location path and main-hand scythe; no attack/use-on-block/slot spoof. Ручное использование имеет приоритет, нет удерживаемой кнопки.

Nameplate normalization, generic-name override/cache world invalidation и unknown Enderman opt-in проверены просмотром кода. Причина конкретного сообщения пользователя в игре не подтверждена. Обычный Enderman не считается зилотом автоматически.

Только JDK25 gradlew jar. Упаковщик требует текущий BUILD SUCCESSFUL, version/classes/один AimFrameMixin/SHA256. Minecraft/сервер/тесты/stress не запускались. Не гарантируются стрельба/попадание/мана/дальность/отсутствие санкций.
