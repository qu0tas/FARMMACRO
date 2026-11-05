package com.farmmacro.gametest;

import com.farmmacro.FarmMacroMod;
import com.farmmacro.config.ModConfig;
import com.farmmacro.fun.CropFever;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.gui.GuiTestAccess;
import com.farmmacro.gui.WelcomeScreen;
import com.farmmacro.gui.WhatsNewScreen;
import com.farmmacro.hud.HudEditorScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.panic.PanicDetector;
import com.farmmacro.route.RouteEditor;
import com.farmmacro.util.Diagnostics;
import com.farmmacro.util.Guard;
import com.farmmacro.util.mixin.MixinAudit;
import com.google.gson.Gson;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

/**
 * Настоящий клиент Minecraft: главное меню, все экраны Kaelix, одиночный мир, клавиши, HUD,
 * аварийный стоп, CROP FEVER и сохранение конфига. Скриншоты — build/run/clientGameTest/screenshots.
 * Главная проверка в конце: ни одной ошибки, пойманной {@link Guard}, за весь прогон.
 * Сервер Hypixel и сетевые функции здесь не проверяются.
 */
@SuppressWarnings("UnstableApiUsage")
public class KaelixClientGameTest implements FabricClientGameTest {
    private static final Gson GSON = new Gson();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        firstRunScreens(ctx);
        mixinsAtTitle(ctx);
        allMenuScreens(ctx);
        try (TestSingleplayerContext sp = ctx.worldBuilder()
                .adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
                .create()) {
            sp.getClientLevel().waitForChunksRender();
            ctx.waitTicks(20);
            ctx.takeScreenshot("kaelix-20-world-hud");
            inWorld(ctx, sp);
        }
        ctx.waitForScreen(TitleScreen.class);
        configRoundTrip(ctx);
        long failures = ctx.computeOnClient(mc -> Guard.failureCount());
        String summary = ctx.computeOnClient(mc -> Guard.diagnosticSummary());
        check(failures == 0, "Guard поймал ошибки за прогон: " + summary);
    }

    /** Мастер первого запуска появляется в главном меню; потом «Что нового» не должно выскакивать. */
    private static void firstRunScreens(ClientGameTestContext ctx) {
        ctx.waitFor(mc -> mc.screen instanceof WelcomeScreen || mc.screen instanceof TitleScreen, 200);
        ctx.waitTicks(5);
        boolean welcome = ctx.computeOnClient(mc -> mc.screen instanceof WelcomeScreen);
        ctx.takeScreenshot(welcome ? "kaelix-00-welcome" : "kaelix-00-title");
        ctx.runOnClient(mc -> {
            ModConfig.INSTANCE.welcomeDone = true;
            ModConfig.INSTANCE.lastSeenVersion = WhatsNewScreen.VERSION;
        });
        ctx.setScreen(() -> new TitleScreen());
        ctx.waitTicks(3);
        check(
                ctx.computeOnClient(mc -> mc.screen instanceof TitleScreen),
                "после мастера должно остаться главное меню");
        ctx.setScreen(() -> new WhatsNewScreen(new TitleScreen()));
        ctx.waitTicks(3);
        ctx.takeScreenshot("kaelix-01-whats-new");
        ctx.setScreen(() -> new TitleScreen());
    }

    private static void mixinsAtTitle(ClientGameTestContext ctx) {
        List<String> expected = MixinAudit.expected();
        check(expected.size() >= 20, "не прочитан список миксинов: " + expected);
        check(ctx.computeOnClient(mc -> Diagnostics.report(mc)).contains("Kaelix diagnostics"), "отчёт диагностики");
    }

    /** Главный экран Kaelix и каждый модуль из каталога: открывается, рисуется, закрывается без ошибок. */
    private static void allMenuScreens(ClientGameTestContext ctx) {
        ctx.setScreen(() -> new FarmMacroScreen(new TitleScreen()));
        ctx.waitTicks(3);
        ctx.takeScreenshot("kaelix-02-menu-home");
        List<String> titles = GuiTestAccess.moduleTitles();
        for (int i = 0; i < titles.size(); i++) {
            int index = i;
            ctx.setScreen(() -> GuiTestAccess.module(index));
            ctx.waitTicks(3);
            ctx.getInput().scroll(-3);
            ctx.waitTicks(2);
            ctx.takeScreenshot(String.format(Locale.ROOT, "kaelix-03-module-%02d-%s", i, slug(titles.get(i))));
        }
        ctx.setScreen(() -> new TitleScreen());
        ctx.waitTicks(2);
        languageSwitch(ctx);
    }

    /** По умолчанию всё по-русски; с «Язык как в Minecraft» названия модулей берутся из en_us.json. */
    private static void languageSwitch(ClientGameTestContext ctx) {
        String ru = ctx.computeOnClient(mc -> GuiTestAccess.moduleTitles().get(0));
        check(ru.equals("Фермы и маршруты"), "по умолчанию русское название модуля: " + ru);
        ctx.runOnClient(mc -> ModConfig.INSTANCE.uiFollowGameLanguage = true);
        try {
            String game = ctx.computeOnClient(mc -> mc.getLanguageManager().getSelected());
            String en = ctx.computeOnClient(mc -> GuiTestAccess.moduleTitles().get(0));
            if (game.equals("en_us")) check(en.equals("Farms & routes"), "перевод из en_us.json: " + en);
            ctx.setScreen(() -> new FarmMacroScreen(new TitleScreen()));
            ctx.waitTicks(3);
            ctx.takeScreenshot("kaelix-02-menu-home-game-language");
        } finally {
            ctx.runOnClient(mc -> ModConfig.INSTANCE.uiFollowGameLanguage = false);
        }
        ctx.setScreen(() -> new TitleScreen());
        ctx.waitTicks(2);
    }

    private static void inWorld(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        // Все обязательные миксины к этому моменту применены (Sodium-миксин — только если стоит Sodium).
        Set<String> pending = ctx.computeOnClient(mc -> MixinAudit.pending(FabricLoader.getInstance()::isModLoaded));
        check(
                pending.isEmpty(),
                "не применились миксины: " + pending + " / "
                        + MixinAudit.summary(FabricLoader.getInstance()::isModLoaded));

        // Клавиша меню Kaelix в мире.
        ctx.getInput().pressKey(FarmMacroMod.keyOpenGui);
        ctx.waitForScreen(FarmMacroScreen.class);
        ctx.takeScreenshot("kaelix-21-menu-in-world");
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);

        // Редактор HUD (F8).
        ctx.getInput().pressKey(FarmMacroMod.keyHudEdit);
        ctx.waitForScreen(HudEditorScreen.class);
        ctx.waitTicks(2);
        ctx.takeScreenshot("kaelix-22-hud-editor");
        ctx.setScreen(() -> null);
        ctx.waitTicks(2);

        // Редактор маршрута (B): включается и выключается.
        ctx.getInput().pressKey(FarmMacroMod.keyEditor);
        ctx.waitTicks(3);
        check(ctx.computeOnClient(mc -> RouteEditor.isActive()), "редактор маршрута не включился");
        ctx.takeScreenshot("kaelix-23-route-editor");
        ctx.getInput().pressKey(FarmMacroMod.keyEditor);
        ctx.waitTicks(3);
        check(!ctx.computeOnClient(mc -> RouteEditor.isActive()), "редактор маршрута не выключился");

        // Старт без маршрута: макрос не должен поехать и не должен падать.
        ctx.getInput().pressKey(FarmMacroMod.keyPlay);
        ctx.waitTicks(5);
        check(!ctx.computeOnClient(mc -> MacroManager.INSTANCE.isPlaying()), "макрос запустился без маршрута");

        // CROP FEVER: анимация из сжатого end.png рисуется.
        ctx.runOnClient(mc -> CropFever.onText("WOAH! You caught a case of the CROP FEVER for 5 seconds!", false));
        ctx.waitTicks(10);
        ctx.takeScreenshot("kaelix-24-crop-fever");
        ctx.runOnClient(mc -> CropFever.onText("GONE! Your CROP FEVER has been cured!", false));
        ctx.waitTicks(30);
        ctx.takeScreenshot("kaelix-25-crop-fever-end");
        ctx.runOnClient(CropFever::reset);

        // Аварийный стоп вручную: оверлей и звук, затем End сбрасывает всё временное.
        ctx.runOnClient(mc -> PanicDetector.INSTANCE.triggerPanic(mc, "game-test"));
        ctx.waitTicks(5);
        ctx.takeScreenshot("kaelix-26-panic");
        ctx.getInput().pressKey(FarmMacroMod.keyResetPos);
        ctx.waitTicks(5);
        ctx.takeScreenshot("kaelix-27-after-reset");

        // Отчёт диагностики в мире: все домены заполнены (ловит выход за границы массива, как в 2.0.2).
        String report = ctx.computeOnClient(mc -> Diagnostics.report(mc));
        for (String domain : List.of("WORLD=PRESENT", "MACRO=", "NAV=", "COMBAT=", "PANIC="))
            check(report.contains(domain), "в отчёте нет " + domain + ":\n" + report);
        sp.getServer().runCommand("time set day");
        ctx.waitTicks(5);
    }

    /** Сохранить → загрузить: конфиг не меняется и не теряет полей. */
    private static void configRoundTrip(ClientGameTestContext ctx) {
        String before = ctx.computeOnClient(mc -> GSON.toJson(ModConfig.INSTANCE));
        ctx.runOnClient(mc -> {
            ModConfig.save();
            ModConfig.load();
        });
        String after = ctx.computeOnClient(mc -> GSON.toJson(ModConfig.INSTANCE));
        check(before.equals(after), "конфиг изменился после save/load");
    }

    private static final String CYR = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LAT = {
        "a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f",
        "h", "c", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"
    };

    /** Имя файла скриншота: только ASCII — JVM в CI может работать с POSIX-локалью без UTF-8 путей. */
    private static String slug(String title) {
        StringBuilder out = new StringBuilder();
        for (char c : title.toLowerCase(Locale.ROOT).toCharArray()) {
            int k = CYR.indexOf(c);
            out.append(k >= 0 ? LAT[k] : String.valueOf(c));
        }
        String s = out.toString().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "module" : s;
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
