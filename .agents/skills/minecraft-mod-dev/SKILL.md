---
name: minecraft-mod-dev
description: "Create, modify, debug or migrate Minecraft Java mods for NeoForge and Fabric (current 26.x on Java 25, legacy 1.21.x) and Forge 1.20.1: blocks, items, entities, block entities, machines, recipes, networking, datagen, mixins, events, inter-mod integration (JEI/EMI, AE2, Create, convention tags), Gradle/MDK/Loom setup, and porting between versions (e.g. 1.21.11 → 26.1 → 26.2). Use for loader-based mod code (\"сделай мод для Майнкрафта\", \"блок для AE2 с рецептами в JEI\", \"перенеси мод на 26.2\"); use minecraft-multiloader when one codebase targets both loaders, minecraft-plugin-dev for Paper/Bukkit, minecraft-datapack for vanilla datapacks."
---

# minecraft-mod-dev

## Сначала — правила FarmMacro (важнее всего ниже)

Скилл общий (NeoForge/Fabric/Forge). В этом репозитории он подчиняется `AGENTS.md` и `CONTEXT.md`:

- Проект: **клиентский** Fabric-мод, Minecraft 26.1.2, официальные имена Mojang (без Yarn, Loom 1.18.3 без remap), Loader 0.19.5, Fabric API 0.155.3+26.1.2, Java 25. Блоков, предметов, сущностей, рецептов и datagen нет — разделы про них ниже для этого мода не нужны.
- API 26.1.2 не угадывать и не брать из примеров 1.21.x ниже: проверять `javap` по jar игры из `.gradle/loom-cache/minecraftMaven/...`.
- Паника = стоп + красный экран + звук + окно игры вперёд (v1.16.3). По желанию игрока (выкл по умолчанию) — «человеческая реакция»: оглядеться и одна фраза в чат; любой ввод игрока её сразу прекращает. Других действий паника не делает.
- Код FarmHelper (CC BY-NC-SA) и других модов с несвободной лицензией не копировать — только идеи.
- Пакеты `ClientPacketListener` в миксинах обрабатывать только при `Minecraft.getInstance().isSameThread()`.
- Чистую логику — в классы без Minecraft с проверкой в `src/stress` (`./gradlew routeStress`).
- `runData`/`runGameTestServer` здесь не используются; проверка — `./gradlew build` и `./gradlew routeStress`.
- Подстраницы (neoforge-api, fabric-api, forge-1.20.1-api, common-patterns) лежат в Notion по ссылкам в конце и в репозиторий не входят.

---

Source: [chouzz/minecraft-mod-dev](https://github.com/chouzz/minecraft-mod-dev), `skills/minecraft-mod-dev/SKILL.md`.

Act as an expert Minecraft mod developer. Prioritize modern standards, data-driven design and inter-mod compatibility.

## Core protocols

### 1. Fetch current documentation

When the user names a Minecraft version (e.g. 1.21.1) or loader:

- Load current docs with web tools: [NeoForge Docs](https://docs.neoforged.net/), [Fabric Wiki](https://fabricmc.net/wiki/) / [Fabric Docs](https://docs.fabricmc.net/).
- Do not rely on memory for registry names, packet/networking code, or event names — they change between versions.

### 2. Ecosystem first

Before building custom systems, check integration options:

- **JEI/EMI:** register a custom `RecipeType` and provide an `IModPlugin` (JEI) implementation.
- **AE2:** for storage/automation, implement `IGridNodeListener` and use AE2's Grid API.
- **Create:** reuse Create's block-entity patterns or custom mixing/pressing recipe types.
- **Convention tags:** use common tags (`c:ingots`, `c:dusts`, … on 1.21 NeoForge/Fabric) for cross-mod compatibility.

### 3. Standards

- **Registration:** `DeferredRegister` on NeoForge; standard `Registry` on Fabric.
- **Side separation:** keep client-only code (renderers, screens) in client classes (`@OnlyIn(Dist.CLIENT)` / client entrypoint).
- **Data generation:** write `DataProvider` classes instead of hand-editing JSON.
- **Data Components:** on 1.20.5+, use `DataComponentType` instead of raw NBT for item data.

## Tooling (user's machine)

- `./gradlew runData` — generate resources.
- `./gradlew runClient` — test in game.
- `./gradlew runGameTestServer` — integration tests.
- Detect the project setup from `gradle.properties` / `build.gradle` (Minecraft version, loader, mappings, mod id) before writing code; ask the user to paste them if unknown.

## Migration protocol

When asked to migrate, rewrite or refactor old mod code:

1. Check the official version primers/migration notes for the target version (NeoForge blog/docs, Fabric blog).
2. Extract core logic (formulas, business rules) first, not old API calls.
3. Modernize patterns:
    - TileEntity → `BlockEntity` + `BlockEntityTicker`
    - NBT item data → Data Components (1.20.5+)
    - Hard-coded JSON → `DataProvider`
4. Afterwards always remind the user: "I rewrote the logic for 1.21+. Run `./gradlew runData` so legacy resource files are regenerated correctly."

## Output

Give complete, compilable Java files with package paths, registration code, and the resource/datagen pieces needed; state the target version and loader at the top.

## References

- [NeoForge Docs](https://docs.neoforged.net/), [Fabric Wiki](https://fabricmc.net/wiki/)
- Repo references (load when needed): `references/mod-links.md`, `references/migration-guide.md` at [https://github.com/chouzz/minecraft-mod-dev/tree/master/references](https://github.com/chouzz/minecraft-mod-dev/tree/master/references)
- Porting primers 1.21.11 → 26.x (vanilla changes, load when migrating): [1.21.11 → 26.1](https://docs.neoforged.net/primer/docs/26.1/) + [Neo changes](https://docs.neoforged.net/primer/docs/26.1/neo), [26.1 → 26.2](https://docs.neoforged.net/primer/docs/26.2/), [26.2 → 26.3](https://docs.neoforged.net/primer/docs/26.3/); Fabric: [Porting to 26.2](https://docs.fabricmc.net/develop/porting), [Porting to 26.1](https://docs.fabricmc.net/26.1.2/develop/porting). Key facts: 26.1 needs Java 25, is unobfuscated (all 1.21.11 mods must be recompiled), Fabric dropped Yarn → migrate to Mojang names.

---

## Extended guide (from minecraft-agent-skills)

Source: [Jahrome907/minecraft-agent-skills](https://github.com/Jahrome907/minecraft-agent-skills/tree/main/.agents/skills/minecraft-modding) (MIT), skill `minecraft-modding`. Script `scripts/check-build.sh` lives in that repo folder. Reference files (neoforge-api, fabric-api, forge-1.20.1-api, common-patterns) are subpages of this page. Related skills in this database: minecraft-multiloader, minecraft-testing, minecraft-world-generation, minecraft-datapack, minecraft-resource-pack, minecraft-ci-release.

### Overview

Supported platforms:

| Platform | MC Version | Java | Build System |
| --- | --- | --- | --- |
| **NeoForge** | 26.x current; 1.21.11 examples retained | Java 25 current; Java 21 on 1.21.x | Gradle + ModDevGradle |
| **Forge** | 1.20.1 legacy lane | Java 17 | Gradle + ForgeGradle 6 |
| **Fabric** | 26.x current; 1.21.11 examples retained | Java 25 current; Java 21 on 1.21.x | Gradle + Fabric Loom |
| **Architectury** (multiloader) | 26.x or 1.21.x | Match Minecraft | Gradle + Architectury Loom |

Always confirm the platform and Minecraft version from `gradle.properties` or `build.gradle`
before writing any mod-specific code.

Minecraft 26.1 introduced Java 25 and unobfuscated game executables. For 26.x
projects, start from the current loader generator or example mod and preserve
its build layout. Do not copy the 1.21.11 mapping, Loom plugin, remapping task,
or Java 21 snippets in this skill into a 26.x project. Fabric 26.x uses the
non-remapping Loom path and official names; NeoForge 26.x should start from the
current NeoForge generator. The detailed API references cover legacy 1.21.x
examples unless a section explicitly says 26.x.

#### Routing Boundaries

- `Use when`: the task is Java/Kotlin mod code, registry/event work, networking, datagen wiring, and loader APIs.
- `Do not use when`: the task is command-only vanilla logic (`minecraft-commands-scripting`) or pure datapacks (`minecraft-datapack`).
- `Do not use when`: the task targets Paper/Bukkit plugins (`minecraft-plugin-dev`).

---

### 1. Identifying the Platform

```bash
# NeoForge project signature
grep -r "net.neoforged" gradle.properties build.gradle settings.gradle 2>/dev/null | head -5

# Forge 1.20.1 project signature
grep -r "net.minecraftforge" gradle.properties build.gradle settings.gradle 2>/dev/null | head -5

# Fabric project signature
grep -r "fabric" gradle.properties build.gradle settings.gradle 2>/dev/null | head -5

# Read mod ID and version
cat gradle.properties
```

Key files per platform:

- **NeoForge**: `src/main/resources/META-INF/neoforge.mods.toml`, annotated `@Mod` main class
- **Forge 1.20.1**: `src/main/resources/META-INF/mods.toml`, `net.minecraftforge:forge` dependency
- **Fabric**: `src/main/resources/fabric.mod.json`, class implementing `ModInitializer`
- **Architectury**: `common/`, `fabric/`, `neoforge/` subprojects

---

### 2. Build & Test Commands

```bash
# Build the mod jar
./gradlew build

# Run the Minecraft client to test
./gradlew runClient

# Run a dedicated server to test
./gradlew runServer

# Run game tests (NeoForge JUnit-style game tests)
./gradlew runGameTestServer

# Run data generation (generates JSON assets automatically)
./gradlew runData

# Remove this project's generated build outputs before a fresh rebuild
./gradlew clean

# Check for dependency updates (optional)
./gradlew dependencyUpdates
```

`./gradlew build` runs the project's configured build tasks. Candidate mod jars are
usually under `build/libs/`, but task names and file names are project-specific.
Check the build result, then identify the intended
distributable before publishing it.

---

### 3. Project Layout (NeoForge)

```
src/
  main/
    java/<groupId>/<modid>/
      MyMod.java               ← @Mod entry point
      block/
        ModBlocks.java         ← DeferredRegister.Blocks
        MyCustomBlock.java
      item/
        ModItems.java          ← DeferredRegister.Items
      entity/
        ModEntities.java       ← DeferredRegister.Entities
      menu/                    ← custom GUI containers
      recipe/
      worldgen/
      datagen/
        ModDataGen.java        ← GatherDataEvent handler
        providers/
    resources/
      META-INF/
        neoforge.mods.toml     ← mod metadata (renamed from mods.toml in NeoForge 1.20.5+)
      assets/<modid>/
        blockstates/           ← JSON blockstate definitions
        models/
          block/               ← block model JSON
          item/                ← item model JSON
        items/                 ← 1.21.x item-definition JSON
        textures/
          block/               ← 16×16 PNG textures
          item/
        lang/
          en_us.json           ← translation strings
      data/<modid>/
        recipe/                ← crafting recipe JSON (26.x)
        loot_table/
          blocks/              ← per-block loot table JSON
        tags/
          blocks/
          items/
```

### 4. Project Layout (Forge 1.20.1)

Use this layout only when `minecraft_version=1.20.1` and the project depends on
`net.minecraftforge:forge`. Forge 1.20.1 is not NeoForge: keep `mods.toml`,
`net.minecraftforge.*` imports, Java 17, and ForgeGradle 6 patterns.

```
src/
  main/
    java/<groupId>/<modid>/
      MyMod.java               <- @Mod entry point
      block/
        ModBlocks.java         <- DeferredRegister.Blocks
      item/
        ModItems.java          <- DeferredRegister.Items
      datagen/
        ModDataGen.java        <- GatherDataEvent handler
    resources/
      META-INF/
        mods.toml              <- Forge metadata
      assets/<modid>/          <- client assets
      data/<modid>/            <- server data using 1.20.1 paths
```

See subpage **forge-1.20.1-api** before editing Forge 1.20.1 projects.

### 5. Project Layout (Fabric)

```
src/
  main/
    java/<groupId>/<modid>/
      MyMod.java               ← implements ModInitializer
      client/
        MyModClient.java       ← implements ClientModInitializer
      block/
      item/
      mixin/                   ← Mixin classes
    resources/
      fabric.mod.json
      assets/<modid>/          ← same as NeoForge
      data/<modid>/            ← same as NeoForge
      <modid>.mixins.json      ← mixin configuration
```

---

### 6. Core Concepts Cheatsheet

#### Sides

- **Physical client** – the game client JAR (has rendering code)
- **Physical server** – the dedicated server JAR (no rendering)
- **Logical client** – the client thread (handles rendering, input)
- **Logical server** – the server thread (handles world simulation)
- Code decorated with `@OnlyIn(Dist.CLIENT)` (NeoForge) or `@Environment(EnvType.CLIENT)` (Fabric)
must NEVER run on the server.

#### Registries

Everything in Minecraft lives in a registry. Always register objects; never
construct them at field initializer time outside a registry call. Use the
mapping-appropriate registry constants for the loader you are editing:

| Type | NeoForge / Mojang mappings | Fabric / Yarn mappings |
| --- | --- | --- |
| Blocks | `BuiltInRegistries.BLOCK` | `Registries.BLOCK` |
| Items | `BuiltInRegistries.ITEM` | `Registries.ITEM` |
| Entity types | `BuiltInRegistries.ENTITY_TYPE` | `Registries.ENTITY_TYPE` |
| Block entity types | `BuiltInRegistries.BLOCK_ENTITY_TYPE` | `Registries.BLOCK_ENTITY_TYPE` |
| Menu / screen-handler types | `BuiltInRegistries.MENU` | `Registries.SCREEN_HANDLER` |
| Sound events | `BuiltInRegistries.SOUND_EVENT` | `Registries.SOUND_EVENT` |
| Biomes | `Registries.BIOME` registry keys | `RegistryKeys.BIOME` registry keys |

Do not copy older `Registry.BLOCK` / `Registry.ITEM` constants into 1.21.x code;
those names are stale for the examples in this skill.

#### ResourceLocation / Identifier

Every registry entry needs a namespaced ID:

```java
// NeoForge / vanilla Java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mymod", "my_block");

// Fabric with Yarn mappings
Identifier id = Identifier.of("mymod", "my_block");
```

---

### 7. NeoForge Quick Patterns (26.x)

For 26.x, use the explicitly labelled 26.x sections in
subpage **common-patterns** and select the project's exact version in the
[NeoForge documentation](https://docs.neoforged.net/docs/gettingstarted/).
subpage **neoforge-api** contains legacy 1.21.x / Java 21 patterns only;
do not copy its dependency pins into a 26.x project.

```java
// Main mod class
@Mod(MyMod.MOD_ID)
public class MyMod {
    public static final String MOD_ID = "mymod";

    public MyMod(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // runs after all mods are registered
    }
}
```

```java
// Block registration
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(MyMod.MOD_ID);

    public static final DeferredBlock<Block> MY_BLOCK =
        BLOCKS.registerSimpleBlock("my_block",
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
}
```

---

### 8. Forge 1.20.1 Quick Patterns

See full patterns in subpage **forge-1.20.1-api**.

```java
// Main mod class
@Mod(MyMod.MOD_ID)
public class MyMod {
    public static final String MOD_ID = "mymod";

    public MyMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // runs after registries are prepared
    }
}
```

```java
// Block registration
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, MyMod.MOD_ID);

    public static final RegistryObject<Block> MY_BLOCK =
        BLOCKS.register("my_block", () -> new Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5f, 6.0f)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops()));
}
```

---

### 9. Fabric Quick Patterns

Match the project's Minecraft version and mappings in the
[Fabric documentation](https://docs.fabricmc.net/develop/).
subpage **fabric-api** contains legacy 1.21.x / Java 21 patterns only.
The explicitly labelled 26.x sections in subpage **common-patterns**
use NeoForge syntax; adapt them against the exact Fabric API rather than
copying loader-specific classes or legacy dependency pins.

```java
// Main mod class
public class MyMod implements ModInitializer {
    public static final String MOD_ID = "mymod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.register();
    }
}
```

```java
// Fabric 26.x with official Mojang mappings. Create the key before the object
// so its properties receive the required id during construction.
public final class ModBlocks {
    public static final ResourceKey<Block> MY_BLOCK_KEY = ResourceKey.create(
        Registries.BLOCK,
        Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "my_block")
    );

    public static final Block MY_BLOCK = register(
        MY_BLOCK_KEY,
        Block::new,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(1.5f, 6.0f)
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()
    );

    private static Block register(ResourceKey<Block> key,
            Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties) {
        Block block = factory.apply(properties.setId(key));
        return Registry.register(BuiltInRegistries.BLOCK, key, block);
    }

    public static void initialize() {}
}
```

Call `ModBlocks.initialize()` from the Fabric initializer. The Yarn-named
1.21.11 examples remain in subpage **fabric-api**; do not mix those names
with this 26.x pattern.

---

### 10. JSON Asset Templates

Always provide matching JSON assets for every registered block/item.
Codex should generate or update these files alongside Java code.
For Forge 1.20.1, check subpage **forge-1.20.1-api** for legacy server-data
directory names before creating loot tables or tags.

See subpage **common-patterns** for full JSON templates for:

- Blockstate JSON
- Block model JSON (cube, slab, stairs, fence, door, trapdoor, etc.)
- Item model JSON
- Loot table JSON
- Recipe JSON (crafting_shaped, crafting_shapeless, smelting, blasting, stonecutting)
- Language file (`en_us.json`) entries
- Tag JSON

---

### 11. Data Generation

Prefer data generation over hand-authored JSON for maintainability.

```java
// NeoForge – register data gen providers in GatherDataEvent
@SubscribeEvent
public static void gatherData(GatherDataEvent event) {
    DataGenerator gen = event.getGenerator();
    PackOutput output = gen.getPackOutput();
    ExistingFileHelper helper = event.getExistingFileHelper();
    CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

    gen.addProvider(event.includeClient(), new ModBlockStateProvider(output, helper));
    gen.addProvider(event.includeClient(), new ModItemModelProvider(output, helper));
    gen.addProvider(event.includeServer(), new ModRecipeProvider(output, lookupProvider));
    gen.addProvider(event.includeServer(), new ModLootTableProvider(output, lookupProvider));
    gen.addProvider(event.includeServer(), new ModBlockTagsProvider(output, lookupProvider, helper));
}
```

Run data generation with `./gradlew runData`, then commit the generated files.
For Forge 1.20.1, use the mod-event-bus registration, `GatherDataEvent`
signature, provider classes, and legacy output paths from
subpage **forge-1.20.1-api**.

---

### 12. Common Tasks Checklist

When adding a **new block**:

- [ ]  `Block` subclass (or use vanilla Block with properties)
- [ ]  Register in `ModBlocks.BLOCKS` / `Registries.BLOCK`
- [ ]  Register `BlockItem` in `ModItems.ITEMS` / `Registries.ITEM`
- [ ]  Blockstate JSON → `assets/<modid>/blockstates/<name>.json`
- [ ]  Block model JSON → `assets/<modid>/models/block/<name>.json`
- [ ]  Item model JSON → `assets/<modid>/models/item/<name>.json` (or inherits from block)
- [ ]  1.21.x item definition → `assets/<modid>/items/<name>.json`, pointing at the item or block model
- [ ]  Texture PNG → `assets/<modid>/textures/block/<name>.png`
- [ ]  Loot table JSON -> 1.21.x: `data/<modid>/loot_table/blocks/<name>.json`; Forge 1.20.1: `data/<modid>/loot_tables/blocks/<name>.json`
- [ ]  Tags -> 1.21.x: `data/<modid>/tags/block/` and `tags/item/`; Forge 1.20.1: `data/<modid>/tags/blocks/` and `tags/items/`
- [ ]  Language entry in `en_us.json`
- [ ]  Mine-with-correct-tool tag if hardness > 0
- [ ]  Do not mix Forge 1.20.1 plural server-data paths with 1.21.x singular server-data paths

When adding a **new item**:

- [ ]  `Item` subclass (or use `new Item(properties)`)
- [ ]  Register in `ModItems` / `Registries.ITEM`
- [ ]  Item model JSON
- [ ]  1.21.x item definition → `assets/<modid>/items/<name>.json`
- [ ]  Texture PNG
- [ ]  Language entry
- [ ]  Creative tab registration (NeoForge/Forge: `BuildCreativeModeTabContentsEvent`; Fabric: `ItemGroupEvents`)
- [ ]  Recipe JSON if craftable (`data/<modid>/recipe/` for 26.x; see the
version-specific recipe reference before using a 1.21.x project)

When adding a **new entity**:

- [ ]  Entity class (extends appropriate base: `Mob`, `Animal`, `TamableAnimal`, etc.)
- [ ]  `EntityType` registration
- [ ]  Renderer class (`@OnlyIn(Dist.CLIENT)`)
- [ ]  Model class (`@OnlyIn(Dist.CLIENT)`)
- [ ]  Register renderer in `EntityRenderersEvent.RegisterRenderers` (NeoForge) or
`EntityModelLayerRegistry` (Fabric)
- [ ]  Spawn egg item (optional)
- [ ]  Spawn rules / biome modifier

---

### 13. Open-Source Conventions

- **License**: MIT or LGPL-3.0 — include `LICENSE` file and `SPDX-License-Identifier` header
- **Versioning**: `{mod_version}+{mc_version}` (e.g., `2.0.0+1.21.11`)
- **Changelog**: Keep `CHANGELOG.md` up to date with semver notes
- **Publishing**: Use `gradle-modrinth` or `curseforgegradle` plugins for CurseForge / Modrinth
- **CI**: GitHub Actions with `./gradlew build` and `./gradlew runGameTestServer`
- **PR conventions**: Keep PRs scoped to a single feature; include asset files with Java changes

---

### 14. References

- NeoForge API patterns and event system: subpage **neoforge-api**
- Forge 1.20.1 API patterns and MDK workflow: subpage **forge-1.20.1-api**
- Fabric API patterns and mixin guide: subpage **fabric-api**
- Blocks, items, recipes, commands, GUIs, datagen: subpage **common-patterns**
- NeoForge official docs: https://docs.neoforged.net/
- Fabric developer docs: https://docs.fabricmc.net/develop/
- Architectury (multiloader): https://docs.architectury.dev/
- Minecraft Wiki (data formats): https://minecraft.wiki/w/Java_Edition_data_values

[neoforge-api](https://app.notion.com/p/neoforge-api-7377536b16a44f3da6e7cbb773eb9733?pvs=21)

[fabric-api](https://app.notion.com/p/fabric-api-a98f1715e04f4bb38c5904c90fc32c75?pvs=21)

[forge-1.20.1-api](https://app.notion.com/p/forge-1-20-1-api-56005813aa7c4879936859c408302773?pvs=21)

[common-patterns](https://app.notion.com/p/common-patterns-2671fb93c7514c9f8082619d2eeea2b9?pvs=21)
