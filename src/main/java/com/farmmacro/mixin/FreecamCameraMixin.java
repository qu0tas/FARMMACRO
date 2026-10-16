package com.farmmacro.mixin;
import com.farmmacro.camera.Freecam;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Verified 26.1.2: alignWithEntity runs before FOV/frustum preparation in Camera.update. */
@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
 @Shadow protected abstract void setPosition(double x,double y,double z);
 @Shadow protected abstract void setRotation(float yaw,float pitch);
 @Shadow private boolean detached;
 @Inject(method="alignWithEntity",at=@At("RETURN"))
 private void farmmacro$freecam(float partial,CallbackInfo ci){
  if(!Freecam.active())return;var p=Freecam.position();if(p==null)return;setRotation(Freecam.yaw(),Freecam.pitch());setPosition(p.x(),p.y(),p.z());detached=true;
 }
}
