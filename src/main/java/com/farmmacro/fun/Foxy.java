package com.farmmacro.fun;

import com.farmmacro.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;

/**
 * v1.15.1 пасхалка: скример Foxy. Раз в минуту игры в мире — бросок: первый раз шанс 1 %, после первого — 0.1 %,
 * после второго — 0.01 %, дальше — 0.001 %. Гифка (18 кадров × 50 мс) на весь экран и громкий звук мимо громкости игры.
 * Выключается ТОЛЬКО в файле {@code config/farmmacro/foxy.json}: {@code "enabled": false} (в меню нет).
 */
public final class Foxy {
    private Foxy() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Foxy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int FRAMES = 18, FRAME_MS = 50, TEX_W = 640, TEX_H = 354;
    /** Шанс на бросок по числу уже случившихся скримеров (последний — навсегда). */
    public static final double[] CHANCE = {0.01, 0.001, 0.0001, 0.00001};
    /** Бросок — раз в столько тиков игры в мире (1 мин). */
    public static final int ROLL_TICKS = 20 * 60;

    /** Файл: выключатель и сколько раз уже было. */
    static final class State {
        boolean enabled = true;
        int shown;
        /** true — показать один раз через 5 с в мире (для проверки, не считается), потом само станет false. */
        boolean testNow;
        String note = "Скример Foxy: enabled=false — выключить. shown — сколько раз уже был (шанс: 1 %, 0.1 %, 0.01 %, дальше 0.001 % в минуту).";
    }

    private static State state;
    private static final SplittableRandom RND = new SplittableRandom(System.nanoTime());
    private static int ticks;
    private static long startMs = -1;
    private static final Identifier[] TEX = new Identifier[FRAMES];

    public static void register() {
        for (int i = 0; i < FRAMES; i++) TEX[i] = Identifier.fromNamespaceAndPath("farmmacro", String.format("textures/gui/foxy/f%02d.png", i));
        load();
        com.farmmacro.panic.PanicSound.preload("builtin:foxy");
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farmmacro", "foxy"),
                com.farmmacro.util.Guard.hud("foxy", (g, delta) -> draw(g)));
    }

    private static Path file() { return ModConfig.modDir().resolve("foxy.json"); }

    private static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) state = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), State.class);
        } catch (Throwable t) { LOGGER.warn("foxy.json не прочитан: {}", t.toString()); }
        if (state == null) { state = new State(); save(); }
        state.shown = Math.max(0, state.shown);
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Path tmp = file().resolveSibling("foxy.json.tmp");
            Files.writeString(tmp, GSON.toJson(state), StandardCharsets.UTF_8);
            Files.move(tmp, file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) { LOGGER.warn("foxy.json не записан: {}", t.toString()); }
    }

    public static double chance() { return state == null ? 0 : CHANCE[Math.min(CHANCE.length - 1, state.shown)]; }

    /** Каждый тик клиента: в мире — раз в минуту бросок. */
    public static void tick(Minecraft mc) {
        if (state == null || !state.enabled || mc.player == null || mc.level == null || startMs >= 0) return;
        if (state.testNow && ++ticks >= 100) {
            state.testNow = false; ticks = 0;
            state.shown--;                                   // проверка не считается
            scare();
            return;
        }
        if (++ticks < ROLL_TICKS) return;
        ticks = 0;
        if (RND.nextDouble() < chance()) scare();
    }

    /** Показать сейчас (и засчитать). */
    public static void scare() {
        startMs = System.currentTimeMillis();
        state.shown++;
        save();
        LOGGER.info("Foxy! (раз {}, дальше шанс {} в минуту)", state.shown, chance());
        com.farmmacro.panic.PanicSound.play("builtin:foxy", true, 1.0f, 1.0f);
    }

    private static void draw(net.minecraft.client.gui.GuiGraphicsExtractor g) {
        if (startMs < 0) return;
        long t = System.currentTimeMillis() - startMs;
        int frame = (int) (t / FRAME_MS);
        if (frame >= FRAMES) { startMs = -1; return; }
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        double k = Math.max(sw / (double) TEX_W, sh / (double) TEX_H);   // на весь экран (лишнее — за край)
        int w = (int) Math.ceil(TEX_W * k), h = (int) Math.ceil(TEX_H * k);
        g.blit(RenderPipelines.GUI_TEXTURED, TEX[frame], (sw - w) / 2, (sh - h) / 2, 0f, 0f, w, h, TEX_W, TEX_H, TEX_W, TEX_H);
    }
}
