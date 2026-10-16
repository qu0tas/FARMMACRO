package com.farmmacro.mixin;
import com.farmmacro.combat.CombatSway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only the active local KeyboardInput is affected. No terrain/edge checks or network spoofing. */
@Mixin(KeyboardInput.class)
public abstract class CombatCrouchInputMixin extends ClientInput {
    private boolean farmmacro$owned(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.input==(Object)this&&CombatSway.INSTANCE.maintainInput(mc);}
    @Inject(method="tick",at=@At("HEAD"))
    private void farmmacro$beforeInput(CallbackInfo ci){farmmacro$owned();}
    @Inject(method="tick",at=@At("RETURN"))
    private void farmmacro$keepCrouch(CallbackInfo ci){
        if(com.farmmacro.camera.Freecam.active()){keyPresses=Input.EMPTY;moveVector=net.minecraft.world.phys.Vec2.ZERO;return;}
        if(!farmmacro$owned())return;Input k=keyPresses;
        if(!k.shift())keyPresses=new Input(k.forward(),k.backward(),k.left(),k.right(),k.jump(),true,k.sprint());
    }
}
