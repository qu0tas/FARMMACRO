# Validation — 1.18.5-scythe-alpha

## Выполнено
- API проверены javap по официальному merged jar MC26.1.2.
- Только компиляция/упаковка: JDK25, `gradlew --no-daemon jar`, BUILD SUCCESSFUL.
- Статический просмотр: нет attack/item-use/movement/slot-change в Combat. Один AimFrameMixin. Hold/Toggle, End/паника сохранены. Depth-tested rendering.
- Упаковщик проверяет fabric.mod.json, новые классы, отсутствие test/stress classes, SHA256 JAR и единственный актуальный JAR в ZIP.

## НЕ выполнялось
Minecraft, клиент, сервер, игровые/модульные/stress тесты, бенчмарки. Новое поведение не подтверждено в игре; не гарантируется попадание, скорость/дальность Ice Bolt или anti-cheat safety.

## Необязательная проверка пользователем
Сначала безопасное место: разрешение, Glacial Scythe, F12 Hold/Toggle. Зилоты приоритетны; фильтр может исключить их. Центр тела, сохраняемый target lock, смерть → следующая цель. За стеной/незагруженным чанком не наводится. Подсветка без стен, selected orange. End/окна/переключение мира прекращают управление. С другим оружием профиль выключить. Проверить FPS, Sodium/Iris и дальние загруженные цели.
