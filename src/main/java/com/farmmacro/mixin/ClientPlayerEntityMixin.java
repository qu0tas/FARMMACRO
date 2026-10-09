package com.farmmacro.mixin;

import com.farmmacro.macro.MacroManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientPlayerEntityMixin — обновляет ожидаемый слот в PanicDetector
 * после каждого тика макроса.
 *
 * Детект координат и поворота теперь работает через сравнение
 * тик-к-тику прямо в PanicDetector.tick() — Mixin для этого не нужен.
 */
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        // Mixin оставлен пустым — вся логика в PanicDetector.tick()
        // и MacroManager.tickPlayback()
    }
}
