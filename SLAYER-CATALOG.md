# Slayer-каталог alpha34

Сверка доступных wiki-страниц: 2026-10-14. Названия, а не обещание распознавания каждой серверной маскировки. Вики может отставать; серверное имя остаётся источником runtime. Нет сетевых запросов/обновлений каталога во время игры.

## Основные боссы и формы
| Ветка | Основные имена | Отдельные формы/демоны |
|---|---|---|
| Zombie | Revenant Horror | — |
| Spider | Tarantula Broodfather; Primordial Broodfather (T5) | Conjoined Brood |
| Wolf | Sven Packmaster | — |
| Enderman | Voidgloom Seraph | — |
| Blaze | Inferno Demonlord | Quazii; Typhoeus |
| Vampire | Riftstalker Bloodfiend | — |

7имён основных боссов для6веток, не7независимых Slayer-квестов. Формы — отдельные имена: не проверяется фаза, shield, attunement, иммунитет или принадлежность боссу игрока.

## Все перечисленные минибоссы этих веток
| Ветка | Имена |
|---|---|
| Zombie | Revenant Sycophant; Revenant Champion; Deformed Revenant; Atoned Champion; Atoned Revenant |
| Spider | Tarantula Vermin; Tarantula Beast; Mutant Tarantula; Primordial Jockey; Primordial Viscount |
| Wolf | Pack Enforcer; Sven Follower; Sven Alpha |
| Enderman | Voidling Devotee; Voidling Radical; Voidcrazed Maniac |
| Blaze | Flare Demon; Kindleheart Demon; Burningsoul Demon |
| Vampire | Clotgoyle |

Всего20. Не выбирать их, если нужен только главный босс: «Slayer: все боссы» сохраняет прежний набор без минибоссов/форм. Фильтр ветки «босс / формы» включает основной набор+формы только этой ветки; «босс + мини / формы» дополнительно включает её минибоссов. Обычные XPмобы в эти фильтры не входят.

## Обычные/связанные XP-мобы
Существующий MobCatalog сохранён и дополнен именами из таблиц Experience: Zombie Villager, Graveyard Zombie, Lapis/Miner Zombie, Key Guardian, Sea Walker, Rider of the Deep, Water Hydra, Dumpster Diver, варианты Minos Hunter; Cave Spider, Broodmother, Flaming Spider; Old Wolf, Belle; Voidling Fanatic, Voidling Extremist; Bald Blaze, Mutated Blaze, Bezal, Smoldering Blaze; Splatter. Старые алиасы/региональные мобы не удалены. Это вспомогательные точные имена, не доказательство, что любой из них подходит для текущего Slayer-квеста/острова. Не исчерпывающий список всех игровых/ивентовых XPмобов и прислужников (биты/яйца/тотемы не реализованы как механика).

## Распознавание
Только живые загруженные Mob с видимым центром реального хитбокса. Обычный ArmorStand используется только как подпись, игроки никогда не становятся целью; узкое исключение для подтверждённой модели мешка описано ниже. Quazii ожидается как wither_skeleton, Typhoeus как zombified_piglin, ConjoinedBrood как spider/cave_spider. Если сервер использует другое тело/имя/только player-disguise — такой вид может не распознаться; Rift вживую не проверялся. Табличка не доказывает владение боссом, а статический список не выбирает уязвимого демона: иммунную цель игрок меняет сам. Камера не увеличивает дальность оружия и не атакует.

## Источники (факты, не скопированный текст/код)
- https://hypixelskyblock.minecraft.wiki/w/Zombie_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Spider_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Wolf_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Enderman_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Blaze_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Vampire_Slayer
- https://hypixelskyblock.minecraft.wiki/w/Tarantula_Broodfather
- https://hypixelskyblock.minecraft.wiki/w/Primordial_Jockey
- https://hypixelskyblock.minecraft.wiki/w/Quazii (redirect InfernoDemonlord; Quazii/Typhoeus appearance)
- https://wiki.hypixel.net/Slayer (старые официальные таблицы, могут отставать)
- https://wiki.hypixel.net/Spider_Slayer (дополнительная проверка двухT5минибоссов)

Реализация — SlayerCatalog.java/BossTargets.java; полные точные списки MobCatalog.java входят в sourceZIP. Живые атаки/фазы не тестировались; только маленькая проверка составов и фильтров.

### Дополнение: мешки Tarantula / Egg Sacs (alpha)

В ручном Slayer-аиме добавлен переключатель «Tarantula: сначала мешки с яйцами» (включён, но действует только при разрешённом Spider-фильтре). Для приоритета одновременно нужны живой Spider-босс рядом, подпись SHOOT ME!, связанный таймер и модель мешка с предметом на голове. Это узкое исключение для ArmorStand-модели, не наведение на произвольные таблички/декорации и никогда не на игроков. Удерживаемый другой тип босса не перехватывается ради яиц.

Ты сам меняешь оружие и стреляешь. После исчезновения/потери подтверждения/видимости — следующий мешок либо сохранённый босс. Модель/подписи/луч проверяются заново; не угадывается убийство по одному выстрелу. Камера смотрит в приблизительный визуальный центр головной модели (1.7бл, .85для small), не в подтверждённую серверную геометрию стрелы. Нет компенсации падения стрелы/упреждения. Неизвестная модель пропускается; нужны реальные тесты попаданий пользователем. Ни автовыстрела, ни автоматического выбора лука нет. Minecraft не запускался.

Сведения о формате сверены с открытым детектором SkyHanni, реализация Kaelix самостоятельная. Справочные оригиналы с LGPLлицензией приложены в third_party/skyhanni/alpha34, не компилируются в JAR.

Источники яиц:
- https://hypixelskyblock.minecraft.wiki/w/Tarantula_Broodfather (Web of Lies / Tips)
- https://github.com/hannibal002/SkyHanni/pull/5919
- https://github.com/hannibal002/SkyHanni/blob/1de28627933a701662fd1f04cadd6a8fa649321d/src/main/java/at/hannibal2/skyhanni/features/slayer/spider/SpiderEggSacHighlighter.kt

Таймер вида5s1/3 с пробелом и SHOOTME — наблюдаемые подписи, не доказательство своего квеста/владельца. Нет SlayerApi-подтверждения собственного босса или анализа его неуязвимости. Для текущего мешка не применяется8тиков удержания потерянной цели.
