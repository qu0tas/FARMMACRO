package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * Режим «системный» (panicSoundSystem) играет через javax.sound мимо звукового движка игры,
 * поэтому слышен даже при выключенной громкости Minecraft. Звуки mc: всегда идут через игру.
 * Если системный вывод недоступен — автоматически играем через игру.
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

    /** PCM 16 бит, little-endian. */
    private record Clip(byte[] pcm, int channels, int sampleRate) {}

    private static final Map<String, Clip> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean systemAudioBroken = false;

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
    }

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
    public static void clearCache() { CACHE.clear(); }

    // ── Воспроизведение ──────────────────────────────────────────────────────

    public static void playPanic() {
        ModConfig c = ModConfig.INSTANCE;
        play(c.panicSound, c.panicSoundSystem, c.panicSoundVolume, c.panicSoundPitch);
    }

    public static void playDone() {
        play("builtin:done", false, 0.8f, 1.0f);
    }

    public static void play(String id, boolean system, float volume, float pitch) {
        if (id == null) id = "builtin:siren";
        try {
            if (id.startsWith("mc:")) { playGame(Identifier.parse(id.substring(3)), volume, pitch); return; }
            boolean isFile = id.startsWith("file:");
            if ((system || isFile) && !systemAudioBroken) {
                Clip clip = clip(id);
                if (clip != null) { playSystem(clip, volume, pitch, id); return; }
            }
            if (id.startsWith("builtin:")) {
                String key = id.substring(8);
                String path = key.equals("done") ? "done" : "panic." + key;
                playGame(Identifier.fromNamespaceAndPath("farmmacro", path), volume, pitch);
            } else {
                // своё имя файла, а системный звук недоступен — хоть что-то
                playGame(Identifier.fromNamespaceAndPath("farmmacro", "panic.siren"), volume, pitch);
            }
        } catch (Exception e) {
            LOGGER.error("Не удалось проиграть звук {}: {}", id, e.toString());
        }
    }

    private static void playGame(Identifier id, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.getValue(id);
        if (ev == null) ev = SoundEvent.createVariableRangeEvent(id); // события мода (sounds.json) не в реестре
        final SoundEvent sound = ev;
        mc.execute(() -> mc.getSoundManager().play(
                new AbstractSoundInstance(sound, SoundSource.MASTER, RandomSource.create()) {
                    { this.volume = volume; this.pitch = pitch; this.relative = true; }
                }));
    }

    private static void playSystem(Clip clip, float volume, float pitch, String id) {
        Thread t = new Thread(() -> {
            try {
                float rate = clip.sampleRate() * Math.max(0.5f, Math.min(2f, pitch));
                AudioFormat fmt = new AudioFormat(rate, 16, clip.channels(), true, false);
                byte[] data = scale(clip.pcm(), Math.max(0f, Math.min(1f, volume)));
                try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
                    line.open(fmt);
                    line.start();
                    line.write(data, 0, data.length);
                    line.drain();
                }
            } catch (Throwable e) {
                LOGGER.warn("Системный звук недоступен ({}), дальше играю через Minecraft", e.toString());
                systemAudioBroken = true;
                play(id.startsWith("file:") ? "builtin:siren" : id, false, volume, pitch);
            }
        }, "farmmacro-sound");
        t.setDaemon(true);
        t.start();
    }

    private static byte[] scale(byte[] pcm, float volume) {
        if (volume >= 0.999f) return pcm;
        byte[] out = new byte[pcm.length];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            s = Math.round(s * volume);
            out[i] = (byte) s;
            out[i + 1] = (byte) (s >> 8);
        }
        return out;
    }

    // ── Загрузка и декодирование ─────────────────────────────────────────────

    private static Clip clip(String id) {
        Clip c = CACHE.get(id);
        if (c != null) return c;
        try {
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
                Path p = soundsDir().resolve(name).normalize();
                if (!p.startsWith(soundsDir()) || !Files.isRegularFile(p)) {
                    LOGGER.warn("Файл звука не найден: {}", p);
                    return null;
                }
                raw = Files.readAllBytes(p);
            }
            c = name.toLowerCase(Locale.ROOT).endsWith(".ogg") ? decodeOgg(raw) : decodeWav(raw);
            if (c != null) CACHE.put(id, c);
            return c;
        } catch (Throwable e) {
            LOGGER.error("Не удалось загрузить звук {}: {}", id, e.toString());
            return null;
        }
    }

    private static Clip decodeOgg(byte[] raw) {
        ByteBuffer mem = MemoryUtil.memAlloc(raw.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            mem.put(raw).flip();
            IntBuffer ch = stack.mallocInt(1), sr = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(mem, ch, sr);
            if (pcm == null) { LOGGER.warn("Не удалось декодировать ogg"); return null; }
            try {
                byte[] out = new byte[pcm.remaining() * 2];
                for (int i = 0; pcm.hasRemaining(); i += 2) {
                    short s = pcm.get();
                    out[i] = (byte) s;
                    out[i + 1] = (byte) (s >> 8);
                }
                return new Clip(out, ch.get(0), sr.get(0));
            } finally {
                MemoryUtil.memFree(pcm);
            }
        } finally {
            MemoryUtil.memFree(mem);
        }
    }

    private static Clip decodeWav(byte[] raw) throws Exception {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(raw))) {
            AudioFormat f = src.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                    f.getChannels(), f.getChannels() * 2, f.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, src)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                pcm.transferTo(bos);
                return new Clip(bos.toByteArray(), f.getChannels(), Math.round(f.getSampleRate()));
            }
        }
    }
}
