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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMacroMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("farmmacro");

    private static final String CATEGORY_NAME = "key.categories.farmmacro";

    /**
     * Fabric API через mixin добавляет в KeyBinding строковый конструктор
     * (String id, InputUtil.Type type, int code, String category) в рантайме.
     * Yarn-маппинги его не видят — компилятор ругается на String→Category.
     * Обходим через рефлексию: компилятор доволен, в рантайме всё работает.
     */
    private static KeyBinding makeKey(String id, int defaultCode) {
        try {
            var ctor = KeyBinding.class.getDeclaredConstructor(
                    String.class, InputUtil.Type.class, int.class, String.class);
            ctor.setAccessible(true);
            return KeyBindingHelper.registerKeyBinding(
                    (KeyBinding) ctor.newInstance(id, InputUtil.Type.KEYSYM, defaultCode, CATEGORY_NAME));
        } catch (Exception e) {
            LOGGER.warn("[FarmMacro] Reflection keybind failed, falling back to MISC: " + e.getMessage());
            return KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(id, InputUtil.Type.KEYSYM, defaultCode, KeyBinding.Category.MISC));
        }
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
        // Delete = GLFW_KEY_DELETE = 261
        keyResetPos = makeKey("key.farmmacro.resetpos", 261);

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
            // Во время panic_move детекторы не работают — только затухание эффектов.
            if (MacroManager.INSTANCE.isPlaying() && !MacroManager.INSTANCE.isPlayingPanic()) {
                PanicDetector.INSTANCE.tick(client);
            } else {
                // panic_move или макрос не играет — тикаем только эффекты
                // (красный экран, плавный поворот после паники)
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
