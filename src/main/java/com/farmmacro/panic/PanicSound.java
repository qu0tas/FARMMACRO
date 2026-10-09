package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Звуки мода: сирена паники и «дзинь» по окончании макроса.
 *
 * Источник звука задаётся строкой:
 *   builtin:siren   — встроенный (assets/farmmacro/sounds/panic/*.ogg)
 *   mc:minecraft:block.bell.use — любой звук Minecraft
 *   file:имя.ogg    — свой файл .ogg/.wav из config/farmmacro/sounds/
 *
 * «Безопасный звук» (panicSoundSafe, по умолчанию) — всё только через движок игры.
 * Иначе режим «системный» (panicSoundSystem) играет через javax.sound мимо звукового движка игры,
 * поэтому слышен даже при выключенной громкости Minecraft. Звуки mc: всегда идут через игру.
 * Если системный вывод сломался — автоматически и до конца сессии играем через игру.
 *
 * Потоки: декодирование и файлы — только в фоне (LOADER), вывод — в SystemAudioPlayer,
 * главный поток в момент паники лишь берёт клип из кеша и ставит в очередь.
 */
public final class PanicSound {

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Sound");

    public record Option(String id, String label) {}

    private static final String[][] BUILTIN = {
            {"siren",  "Сирена"},
            {"klaxon", "Тревога «ти-та»"},
            {"alarm",  "Пищалка"},
            {"alert",  "Цифровая тревога"},
    };
    private static final String[][] MC_PRESETS = {
            {"minecraft:entity.ghast.scream",     "MC: крик гаста"},
            {"minecraft:block.bell.use",          "MC: колокол"},
            {"minecraft:block.anvil.land",        "MC: наковальня"},
            {"minecraft:block.note_block.pling",  "MC: нотный блок"},
            {"minecraft:entity.wither.spawn",     "MC: визер"},
            {"minecraft:entity.elder_guardian.curse", "MC: проклятие стража"},
    };

    private static final Map<String, SoundDecoder.Clip> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    /** Один фоновый поток для чтения файлов и декодирования. */
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "farmmacro-sound-loader");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private PanicSound() {}

    public static Path soundsDir() { return ModConfig.modDir().resolve("sounds"); }

    /** Создаёт папку звуков и переносит старый config/farmmacro/panic.wav (версии ≤1.1). */
    public static void init() {
        try {
            Files.createDirectories(soundsDir());
            Path old = ModConfig.modDir().resolve("panic.wav");
            Path dst = soundsDir().resolve("panic.wav");
            if (Files.exists(old) && !Files.exists(dst)) {
                Files.move(old, dst);
                LOGGER.info("Перенёс {} -> {}", old, dst);
                if ("builtin:siren".equals(ModConfig.INSTANCE.panicSound)) {
                    ModConfig.INSTANCE.panicSound = "file:panic.wav";
                    ModConfig.save();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Не удалось подготовить папку звуков: {}", e.toString());
        }
        preloadAll();
    }

    /** Предзагрузка встроенных звуков и текущего сигнала в фоне (при старте и после «Обновить»). */
    public static void preloadAll() {
        for (String[] b : BUILTIN) preload("builtin:" + b[0]);
        preload(ModConfig.INSTANCE.panicSound);
    }

    /** Поставить звук в очередь на загрузку (в фоне). Повторный вызов для того же id ничего не делает. */
    public static void preload(String id) {
        if (id == null || !(id.startsWith("builtin:") || id.startsWith("file:"))) return;
        if (CACHE.containsKey(id) || !LOADING.add(id)) return;
        try {
            LOADER.execute(() -> {
                try {
                    SoundDecoder.Clip c = load(id);
                    if (c != null) {
                        CACHE.put(id, c);
                        LOGGER.info("Звук {} готов: {} Гц, {} кан., {} с", id, c.sampleRate(), c.channels(),
                                String.format(Locale.ROOT, "%.2f", c.seconds()));
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Не удалось загрузить звук {}: {}", id, t.toString());
                } finally {
                    LOADING.remove(id);
                }
            });
        } catch (Throwable t) {
            LOADING.remove(id);
            LOGGER.warn("Очередь загрузки звуков недоступна: {}", t.toString());
        }
    }

    /** true — системный вывод сломался в этой сессии, всё играет через движок игры. */
    public static boolean systemAudioBroken() { return SystemAudioPlayer.isBroken(); }

    /** Загружен ли звук для системного режима (для подсказки в меню). */
    public static boolean isReady(String id) { return CACHE.containsKey(id); }

    /** Все доступные варианты для переключателя в GUI (свои файлы перечитываются при каждом вызове). */
    public static List<Option> options() {
        List<Option> list = new ArrayList<>();
        for (String[] b : BUILTIN) list.add(new Option("builtin:" + b[0], b[1]));
        for (String file : listFiles()) list.add(new Option("file:" + file, "Файл: " + file));
        for (String[] m : MC_PRESETS) list.add(new Option("mc:" + m[0], m[1]));
        return list;
    }

    public static String label(String id) {
        for (Option o : options()) if (o.id().equals(id)) return o.label();
        if (id.startsWith("file:")) return "Файл: " + id.substring(5) + " (нет файла)";
        if (id.startsWith("mc:")) return "MC: " + id.substring(3);
        return id;
    }

    private static List<String> listFiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(soundsDir())) {
            s.filter(Files::isRegularFile)
             .map(p -> p.getFileName().toString())
             .filter(n -> { String l = n.toLowerCase(Locale.ROOT); return l.endsWith(".ogg") || l.endsWith(".wav"); })
             .sorted(String.CASE_INSENSITIVE_ORDER)
             .forEach(out::add);
        } catch (Exception ignored) {}
        return out;
    }

    /** Сбросить кеш (например, пользователь заменил файл с тем же именем). */
    public static void clearCache() {
        CACHE.clear();
        preloadAll();
    }

    // ── Воспроизведение ──────────────────────────────────────────────────────

    public static void playPanic() {
        ModConfig c = ModConfig.INSTANCE;
        play(c.panicSound, !c.panicSoundSafe && c.panicSoundSystem, c.panicSoundVolume, c.panicSoundPitch);
    }

    public static void playDone() {
        play("builtin:done", false, 0.8f, 1.0f);
    }

    /**
     * Запуск звука. Вызывается из главного потока, поэтому здесь НЕТ чтения файлов и декодирования:
     * системный режим берёт готовый клип из кеша и кладёт его в очередь проигрывателя,
     * иначе (или если клип ещё не готов) звук идёт через движок игры.
     */
    public static void play(String id, boolean system, float volume, float pitch) {
        if (id == null) id = "builtin:siren";
        try {
            if (id.startsWith("mc:")) { playGame(Identifier.parse(id.substring(3)), volume, pitch); return; }
            boolean wantSystem = !ModConfig.INSTANCE.panicSoundSafe
                    && (system || id.startsWith("file:")) && !SystemAudioPlayer.isBroken();
            if (wantSystem) {
                SoundDecoder.Clip clip = CACHE.get(id);
                if (clip != null) {
                    final String fid = id;
                    SystemAudioPlayer.Job job = new SystemAudioPlayer.Job(clip, volume, pitch, id,
                            () -> playGameFallback(fid, volume, pitch));
                    if (SystemAudioPlayer.submit(job)) return;
                } else {
                    preload(id);
                    LOGGER.info("Звук {} ещё не загружен, этот раз играю через Minecraft", id);
                }
            }
            playGameFallback(id, volume, pitch);
        } catch (Throwable e) {
            LOGGER.error("Не удалось проиграть звук {}: {}", id, e.toString());
        }
    }

    /** builtin:* — тот же звук из sounds.json; свои файлы движок игры не знает — играем сирену. */
    private static void playGameFallback(String id, float volume, float pitch) {
        if (id.startsWith("builtin:")) {
            String key = id.substring(8);
            String path = key.equals("done") ? "done" : "panic." + key;
            playGame(Identifier.fromNamespaceAndPath("farmmacro", path), volume, pitch);
        } else {
            playGame(Identifier.fromNamespaceAndPath("farmmacro", "panic.siren"), volume, pitch);
        }
    }

    /** Потокобезопасно: само воспроизведение всегда в главном потоке через mc.execute. */
    private static void playGame(Identifier id, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(id);
                if (ev == null) ev = SoundEvent.createVariableRangeEvent(id); // события мода (sounds.json) не в реестре
                final SoundEvent sound = ev;
                mc.getSoundManager().play(
                        new AbstractSoundInstance(sound, SoundSource.MASTER, RandomSource.create()) {
                            { this.volume = volume; this.pitch = pitch; this.relative = true; }
                        });
            } catch (Throwable t) {
                LOGGER.error("Движок игры не проиграл {}: {}", id, t.toString());
            }
        });
    }

    // ── Загрузка (только в фоновом потоке LOADER) ────────────────────────────

    private static SoundDecoder.Clip load(String id) throws Exception {
        byte[] raw;
        String name;
        if (id.startsWith("builtin:")) {
            name = id.substring(8) + ".ogg";
            try (InputStream in = PanicSound.class.getResourceAsStream("/assets/farmmacro/sounds/panic/" + name)) {
                if (in == null) { LOGGER.warn("Нет встроенного звука {}", name); return null; }
                raw = in.readAllBytes();
            }
        } else {
            name = id.substring(5);
            Path dir = soundsDir().toAbsolutePath().normalize();
            Path p = dir.resolve(name).normalize();
            if (!p.startsWith(dir) || !Files.isRegularFile(p)) {
                LOGGER.warn("Файл звука не найден: {}", p);
                return null;
            }
            if (Files.size(p) > 32L * 1024 * 1024) {
                LOGGER.warn("Файл звука слишком большой (>32 МБ): {}", p);
                return null;
            }
            raw = Files.readAllBytes(p);
        }
        return SoundDecoder.decode(raw, name);
    }
}
