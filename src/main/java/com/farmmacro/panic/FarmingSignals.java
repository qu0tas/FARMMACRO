package com.farmmacro.panic;

import com.farmmacro.config.ModConfig;
import com.farmmacro.macro.MacroManager;
import com.farmmacro.pest.PestHunter;
import com.farmmacro.route.NavigationInput;
import com.farmmacro.route.RouteEndRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.regex.Pattern;

/** Harvest-segment evidence, not a guarantee of earned XP or an administrator detector. */
final class FarmingSignals {
    private static final Pattern XP=Pattern.compile("(?iu)(?:\\+\\s*[0-9][0-9,. ]*\\s+Farming(?:\\s+(?:XP|EXP))?|Farming\\s+(?:XP|EXP)\\s*[:+]\\s*[0-9])");
    private boolean harvestSeen,xpArmed;
    private int warmup,missingTicks,lowBpsTicks;
    private long xpAt;
    private static boolean base(Minecraft mc){
        if(mc.player==null||mc.level==null||mc.screen!=null||mc.isPaused()||!mc.isWindowActive()
            ||!mc.player.isAlive()||mc.player.isSpectator()||mc.player.getAbilities().flying||mc.player.onClimbable()
            ||!MacroManager.INSTANCE.harvestPlayback()||PestHunter.INSTANCE.active()||NavigationInput.busy()
            ||RouteEndRuntime.INSTANCE.begun()||PanicHandoff.pending())return false;
        var o=mc.options;
        return o.keyAttack.isDown()&&(o.keyUp.isDown()||o.keyDown.isDown()||o.keyLeft.isDown()||o.keyRight.isDown());
    }
    static boolean crop(BlockState s){
        if(s==null)return false;var b=s.getBlock();
        return b==Blocks.WHEAT||b==Blocks.CARROTS||b==Blocks.POTATOES||b==Blocks.NETHER_WART
            ||b==Blocks.COCOA||b==Blocks.MELON||b==Blocks.PUMPKIN||b==Blocks.SUGAR_CANE
            ||b==Blocks.CACTUS||b==Blocks.BROWN_MUSHROOM||b==Blocks.RED_MUSHROOM;
    }
    void broke(BlockState s){if(base(Minecraft.getInstance())&&crop(s))harvestSeen=true;}
    void actionbar(String text){
        var mc=Minecraft.getInstance();
        if(!ModConfig.INSTANCE.detectFarmingXp||!base(mc)||LagGuard.INSTANCE.lagging())return;
        String clean=ServerText.clean(text);if(clean.length()>2048)clean=clean.substring(0,2048);
        if(XP.matcher(clean).find()){xpAt=System.nanoTime();xpArmed=true;harvestSeen=true;missingTicks=0;}
    }
    boolean working(Minecraft mc,boolean lag){
        if(lag||!base(mc)){reset();return false;}
        if(mc.hitResult instanceof BlockHitResult h&&mc.level.hasChunkAt(h.getBlockPos())
            &&crop(mc.level.getBlockState(h.getBlockPos())))harvestSeen=true;
        if(!harvestSeen)return false;
        warmup=Math.min(1200,warmup+1);return true;
    }
    boolean lowBps(boolean low){lowBpsTicks=low?Math.min(40,lowBpsTicks+1):0;return lowBpsTicks>=20;}
    String missingXp(ModConfig c){
        if(!c.detectFarmingXp){xpArmed=false;xpAt=0;missingTicks=0;return null;}
        // Need an observed XP line in THIS uninterrupted work segment; never infer it from HP/mana.
        if(!xpArmed||warmup<40)return null;
        boolean missing=System.nanoTime()-xpAt>=(long)(c.farmingXpMissingSec*1_000_000_000L);
        missingTicks=missing?Math.min(20,missingTicks+1):0;
        if(missingTicks<10)return null;
        missingTicks=0;xpArmed=false;
        return "При непрерывном сборе пропал ранее видимый текст Farming XP ("+c.farmingXpMissingSec+" с + подтверждение10тиков); это не доказательство проверки/потери XP";
    }
    void reset(){harvestSeen=xpArmed=false;warmup=missingTicks=lowBpsTicks=0;xpAt=0;}
}
