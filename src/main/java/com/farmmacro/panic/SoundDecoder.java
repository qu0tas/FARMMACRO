package com.farmmacro.panic;

import net.minecraft.client.sounds.JOrbisAudioStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Декодирование звуков в PCM16 только на Java, без нативной памяти.
 *
 * .ogg — через {@link JOrbisAudioStream} (тот же декодер на чистой Java, которым игра читает свои звуки).
 * .wav — через javax.sound.sampled.
 *
 * Раньше здесь был STBVorbis.stb_vorbis_decode_memory + MemoryUtil.memFree: буфер выделял malloc
 * внутри stb, а освобождал аллокатор LWJGL (в Minecraft это jemalloc) — нативный краш JVM без исключения.
 * Класс не зависит от Minecraft.getInstance(), поэтому его можно гонять вне игры (см. SoundDecodeStress).
 */
public final class SoundDecoder {
    private SoundDecoder() {}

    /** Не держим в памяти больше 30 секунд звука (свои файлы бывают огромными). */
    public static final int MAX_SECONDS = 30;

    /** PCM 16 бит со знаком, кадры чередуются по каналам. Частота всегда целая. */
    public record Clip(short[] samples, int channels, int sampleRate) {
        public int frames() { return samples.length / channels; }
        public double seconds() { return frames() / (double) sampleRate; }
    }

    public static Clip decode(byte[] raw, String fileName) throws IOException {
        String n = fileName.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".ogg")) return decodeOgg(raw);
        if (n.endsWith(".wav")) return decodeWav(raw);
        throw new IOException("Неизвестный формат: " + fileName);
    }

    public static Clip decodeOgg(byte[] raw) throws IOException {
        try (JOrbisAudioStream in = new JOrbisAudioStream(new ByteArrayInputStream(raw))) {
            AudioFormat f = in.getFormat();
            int channels = f.getChannels();
            int rate = Math.round(f.getSampleRate());
            if (channels < 1 || channels > 8 || rate < 1000 || rate > 192_000)
                throw new IOException("Странный формат ogg: " + f);
            ShortSink sink = new ShortSink((long) MAX_SECONDS * rate * channels);
            while (in.readChunk(sink) && !sink.full()) { /* читаем до конца */ }
            short[] pcm = sink.toArray();
            pcm = Arrays.copyOf(pcm, pcm.length - pcm.length % channels);  // только целые кадры
            if (pcm.length == 0) throw new IOException("Пустой ogg");
            return new Clip(pcm, channels, rate);
        }
    }

    public static Clip decodeWav(byte[] raw) throws IOException {
        try (AudioInputStream src = AudioSystem.getAudioInputStream(new ByteArrayInputStream(raw))) {
            AudioFormat f = src.getFormat();
            int channels = f.getChannels();
            int rate = Math.round(f.getSampleRate());
            if (channels < 1 || channels > 8 || rate < 1000 || rate > 192_000)
                throw new IOException("Странный формат wav: " + f);
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16,
                    channels, channels * 2, rate, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, src)) {
                long maxBytes = (long) MAX_SECONDS * rate * channels * 2;
                byte[] bytes = pcm.readNBytes((int) Math.min(Integer.MAX_VALUE - 16, maxBytes));
                int count = bytes.length / 2;
                count -= count % channels;
                if (count == 0) throw new IOException("Пустой wav");
                short[] out = new short[count];
                for (int i = 0; i < count; i++)
                    out[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
                return new Clip(out, channels, rate);
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException | IllegalArgumentException e) {
            throw new IOException("wav не поддерживается: " + e.getMessage(), e);
        }
    }

    /**
     * Готовит байты для SourceDataLine: громкость и смена тона ресэмплингом (линейная интерполяция).
     * Частота дискретизации остаётся исходной (целой): тон выше — звук короче, как в движке игры.
     */
    public static byte[] render(Clip clip, float volume, float pitch) {
        float vol = Math.max(0f, Math.min(1f, volume));
        double p = Math.max(0.5, Math.min(2.0, pitch));
        int ch = clip.channels();
        short[] s = clip.samples();
        int inFrames = clip.frames();
        boolean resample = Math.abs(p - 1.0) > 1e-3;
        int outFrames = resample ? Math.max(1, (int) Math.floor((inFrames - 1) / p) + 1) : inFrames;
        byte[] out = new byte[outFrames * ch * 2];
        int o = 0;
        for (int i = 0; i < outFrames; i++) {
            double pos = resample ? i * p : i;
            int i0 = (int) pos;
            int i1 = Math.min(i0 + 1, inFrames - 1);
            double frac = pos - i0;
            for (int c = 0; c < ch; c++) {
                double v = s[i0 * ch + c] * (1 - frac) + s[i1 * ch + c] * frac;
                int q = (int) Math.round(v * vol);
                if (q > Short.MAX_VALUE) q = Short.MAX_VALUE;
                else if (q < Short.MIN_VALUE) q = Short.MIN_VALUE;
                out[o++] = (byte) q;
                out[o++] = (byte) (q >> 8);
            }
        }
        return out;
    }

    /** Приёмник float-сэмплов от JOrbis → short[] без лишних объектов. */
    private static final class ShortSink implements it.unimi.dsi.fastutil.floats.FloatConsumer {
        private final long limit;
        private short[] buf = new short[1 << 16];
        private int size;

        ShortSink(long limit) { this.limit = limit; }

        @Override
        public void accept(float f) {
            if (size >= limit) return;
            if (size == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
            float c = f > 1f ? 1f : (f < -1f ? -1f : f);
            buf[size++] = (short) Math.round(c * 32767f);
        }

        boolean full() { return size >= limit; }
        short[] toArray() { return Arrays.copyOf(buf, size); }
    }
}
