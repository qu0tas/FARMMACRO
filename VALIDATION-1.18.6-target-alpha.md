# Validation 1.18.6-target-alpha

- Изменён только новый выбор цели, не lock/стрельба/навигация/песты.
- TargetChoice — чистая Java, ограниченный бонус зилотов, штраф большого/заднего поворота; существующие Minecraft API без расширения.
- Статический просмотр: current living target возвращается до scoring, стены/загруженность/фильтр/FOV сохраняются.
- Проверка сборки: JDK25 `gradlew --no-daemon jar`; итоговый лог /data/build-1.18.6-final.log (не коммитится), упаковщик требует BUILD SUCCESSFUL.
- JAR metadata/classes и один AimFrameMixin проверяются упаковщиком, SHA256 в ZIP.
- Minecraft/сервер/тесты/stress не запускались. Выбор/плавность/попадание в игре не подтверждены.
