package com.farmmacro;

import com.farmmacro.config.ModConfig;
import com.farmmacro.gui.FarmMacroScreen;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.macro.SavedPositionRenderer;
import com.farmmacro.panic.PanicDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMacroMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("farmmacro");

    /** Своя категория в меню управления (1.21.9+: категория — объект, а не строка). */
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("farmmacro", "general"));

    private static KeyBinding makeKey(String id, int defaultCode) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(id, InputUtil.Type.KEYSYM, defaultCode, CATEGORY));
    }

    public static KeyBinding keyRecord;
    public static KeyBinding keyPlay;
    public static KeyBinding keyClear;
    public static KeyBinding keyOpenGui;
    public static KeyBinding keyResume;
    public static KeyBinding keyResetPos;

    @Override
    public void onInitializeClient() {
        LOGGER.info("FarmMacro loaded!");

        ModConfig.load();
        ModConfig cfg = ModConfig.INSTANCE;

        // Регистрируем оверлей красного экрана
        com.farmmacro.panic.PanicOverlayRenderer.register();

        // Регистрируем маркер сохранённой позиции
        SavedPositionRenderer.register();
        com.farmmacro.macro.StatsOverlay.register();


        keyRecord  = makeKey("key.farmmacro.record",   cfg.keyRecord);
        keyPlay    = makeKey("key.farmmacro.play",      cfg.keyPlay);
        keyClear   = makeKey("key.farmmacro.clear",     cfg.keyClear);
        keyOpenGui = makeKey("key.farmmacro.gui",       cfg.keyOpenGui);
        // O = GLFW_KEY_O = 79
        keyResume  = makeKey("key.farmmacro.resume",    79);
        // End = GLFW_KEY_END = 269 (Delete уже занят под «Очистить запись»)
        keyResetPos = makeKey("key.farmmacro.resetpos", 269);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (keyRecord.wasPressed())  MacroManager.INSTANCE.toggleRecording(client);
            while (keyPlay.wasPressed()) {
                    MacroManager.INSTANCE.togglePlayback(client);
            }
            while (keyClear.wasPressed())   MacroManager.INSTANCE.clearRecording(client);
            while (keyResume.wasPressed())  MacroManager.INSTANCE.resumeFromSaved(client);
            while (keyResetPos.wasPressed()) {
                MacroManager.INSTANCE.clearSavedPosition();
                if (client.player != null)
                    client.player.sendMessage(net.minecraft.text.Text.literal("§7[FM] Сохранённая позиция очищена"), true);
            }
            while (keyOpenGui.wasPressed()) {
                client.setScreen(new FarmMacroScreen(client.currentScreen));
            }

            MacroManager.INSTANCE.tickRecord(client);

            // Сначала проверяем панику — ДО применения кадра макроса.
            if (MacroManager.INSTANCE.isPlaying()) {
                PanicDetector.INSTANCE.tick(client);
            } else {
                // Макрос не играет — только затухание эффектов (красный экран, звук)
                PanicDetector.INSTANCE.tickEffectsOnly(client);
            }

            MacroManager.INSTANCE.tickPlayback(client);
        });
    }

    private static void msg(net.minecraft.client.MinecraftClient client, String text) {
        if (client.player != null)
            client.player.sendMessage(Text.literal("[FM] " + text), true);
    }
}
