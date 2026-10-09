
================================================================
  ДОПОЛНЕНИЕ — ИЗ СЕССИИ СБОРКИ (октябрь 2026)
================================================================

----------------------------------------------------------------
  ВЕРСИЯ И СТЕК
----------------------------------------------------------------
Целевая версия: Minecraft 1.21.11 (Mounts of Mayhem)
Загрузчик: Fabric
Маппинги: Yarn 1.21.11+build.4

Рабочие версии зависимостей (проверено в билде):
  fabric-loom:    1.13.6   (в plugins блоке build.gradle)
  fabric-loader:  0.18.1
  fabric-api:     0.141.3+1.21.11
  Gradle:         9.2.1 (используется gradlew.bat из шаблона)
  JDK:            21

Путь к проекту у автора: C:\AIcheats\MODS\farmmacro\

----------------------------------------------------------------
  ИЗВЕСТНЫЕ ПРОБЛЕМЫ С YARN-МАППИНГАМИ ДЛЯ 1.21.11
----------------------------------------------------------------
- Yarn 1.21.11+build.X несовместим с Loom < 1.13 (ошибка: Unsupported unpick version)
- Loom 1.6.5 и 1.9.2 — не поддерживают Gradle 9.2.1 (ошибка: ProblemReporter)
- Loom 1.8.9 — работает с Gradle 9 но ломается на Yarn 1.21.11
- Рабочая комбинация: loom 1.13.6 + Yarn 1.21.11+build.4 + loader 0.18.1

----------------------------------------------------------------
  API ИЗМЕНЕНИЯ В 1.21.11 (критично для кода)
----------------------------------------------------------------

1. KeyBinding / KeyMapping категории:
   - В 1.21.11 конструктор KeyBinding принимает KeyBinding.Category (объект), НЕ строку
   - Строка "key.categories.misc" больше не работает → ошибка компиляции
   - Правильный способ:
       private static final KeyBinding.Category CATEGORY =
           new KeyBinding.Category(Identifier.of("farmmacro", "general"));
       new KeyBinding("key.farmmacro.record", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY)

2. PlayerInventory.selectedSlot:
   - Поле selectedSlot стало приватным
   - Для чтения: inventory.getSelectedSlot()
   - Для записи: inventory.setSelectedSlot(int slot)
   - scrollInHotbar() — метода нет в 1.21.11

3. KeyBindingHelper (Fabric API) — без изменений, работает как раньше

----------------------------------------------------------------
  fabric.mod.json — РАБОЧАЯ ВЕРСИЯ
----------------------------------------------------------------
{
  "schemaVersion": 1,
  "id": "farmmacro",
  "version": "1.0.0",
  "name": "Farm Macro",
  "description": "Macro recorder with panic function for farm automation",
  "authors": ["Vadik"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": ["com.farmmacro.FarmMacroMod"]
  },
  "mixins": ["farmmacro.mixins.json"],
  "depends": {
    "fabricloader": ">=0.15.0",
    "fabric-api": "*",
    "minecraft": "*"
  }
}

Важно: "minecraft": "*" вместо "~1.21.1" — иначе мод не загружается
        "fabricloader": ">=0.15.0" вместо ">=0.16.0"

----------------------------------------------------------------
  farmmacro.mixins.json — РАБОЧАЯ ВЕРСИЯ
----------------------------------------------------------------
{
  "required": true,
  "package": "com.farmmacro.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "ClientPlayerEntityMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}

----------------------------------------------------------------
  build.gradle — РАБОЧАЯ ВЕРСИЯ
----------------------------------------------------------------
plugins {
    id 'fabric-loom' version '1.13.6'
    id 'maven-publish'
}
version = '1.0.0'
group = 'com.farmmacro'
base { archivesName = 'farmmacro' }
repositories {
    maven { url 'https://maven.fabricmc.net/' }
}
dependencies {
    minecraft 'com.mojang:minecraft:1.21.11'
    mappings 'net.fabricmc:yarn:1.21.11+build.4:v2'
    modImplementation 'net.fabricmc:fabric-loader:0.18.1'
    modImplementation 'net.fabricmc.fabric-api:fabric-api:0.141.3+1.21.11'
}
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType(JavaCompile).configureEach {
    it.options.encoding = 'UTF-8'
}

================================================================
