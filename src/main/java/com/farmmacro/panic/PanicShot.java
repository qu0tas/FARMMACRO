package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.notify.DiscordNotify;
import com.farmmacro.notify.Webhook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * v1.14: скриншот при панике (config/farmmacro/screenshots/panic-ДАТА_ВРЕМЯ_причина.png) и сообщение в Discord.
 * Это не «действие в игре»: только файл на диске и (по желанию) запрос к своему вебхуку. Снимок — последний кадр
 * до красного экрана; запись PNG и отправка — в фоновых потоках.
 */
public final class PanicShot {
    private PanicShot() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("FarmMacro/Panic");
    private static final DateTimeFormatter NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final java.util.concurrent.ThreadPoolExecutor IO = new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"Kaelix-Screenshot");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private static long lastShot;

    public static Path dir() { return ModConfig.modDir().resolve("screenshots"); }

    /** Имя файла: panic-2026-10-12_21-30-05_Камера_повернулась.png (причина — до 40 символов, без спецсимволов). */
    public static String fileName(LocalDateTime t, String reason) {
        String r = reason == null ? "" : reason.replaceAll("[^\\p{L}\\p{N}]+", "_").replaceAll("^_+|_+$", "");
        if (r.length() > 40) r = r.substring(0, 40).replaceAll("_+$", "");
        return "panic-" + NAME.format(t) + (r.isEmpty() ? "" : "_" + r) + ".png";
    }

    /** Вызывается из triggerPanic (главный поток). */
    public static void onPanic(Minecraft mc,String reason){onPanic(mc,null,reason);}
    public static void onPanic(Minecraft mc, String id, String reason) {
        ModConfig c = ModConfig.INSTANCE;
        LocalDateTime now = LocalDateTime.now();
        boolean hook = c.webhookEnabled && DiscordNotify.configured();
        // v1.16.3 «Скрытие ника»: в Discord и в имени файла — без ника; снимок в режиме «только скриншоты» — после
        // кадра, нарисованного с заменой
        String text = com.farmmacro.notify.DiscordEvents.panicText(mc,id,reason);
        long nano=System.nanoTime();
        if (!c.panicScreenshot || lastShot!=0&&nano-lastShot<10_000_000_000L || IO.getQueue().remainingCapacity()==0) {
            if (hook) DiscordNotify.send(text, null, "паника");
            return;
        }
        lastShot=nano;
        String name = fileName(now, ""); // No private chat/name/coordinates in the actual screenshot file name.
        com.farmmacro.visual.NickHide.armShot(() -> grab(mc, name, path -> {
            if (hook) DiscordNotify.send(text, c.webhookScreenshot && c.webhookMediaConsent ? path : null, "паника");
        }));
    }

    /** Снимок основного кадра в файл; done(путь или null) — в фоновом потоке. */
    static void grab(Minecraft mc, String name, Consumer<Path> done) {
        try {
            Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {try{IO.execute(() -> {
                Path out = null;
                try (image) {
                    Files.createDirectories(dir());
                    out = dir().resolve(name);
                    image.writeToFile(out);
                    EventLog.log("SHOT", out.getFileName().toString());
                } catch (Exception e) {
                    LOGGER.warn("Скриншот паники не сохранён ({})", e.getClass().getSimpleName());
                    out = null;
                }
                done.accept(out);
            });}catch(java.util.concurrent.RejectedExecutionException e){image.close();done.accept(null);}});
        } catch (Exception e) {
            LOGGER.warn("Скриншот паники не снят ({})", e.getClass().getSimpleName());
            done.accept(null);
        }
    }

    public static String server(Minecraft mc) {
        if (mc.isSingleplayer()) return "одиночная игра";
        var d = mc.getCurrentServer();
        return d != null && d.ip != null ? d.ip : "неизвестно";
    }

    public static String humanTime() { return HUMAN.format(LocalDateTime.now()); }
}
