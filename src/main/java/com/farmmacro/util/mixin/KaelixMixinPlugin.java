package com.farmmacro.util.mixin;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Только наблюдает: какие миксины реально применились. Ничего не включает и не выключает.
 * Загружается до игры, поэтому здесь нельзя трогать классы Minecraft.
 */
public final class KaelixMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        MixinAudit.configured(mixinPackage);
    }

    @Override
    public @Nullable String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public @Nullable List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        MixinAudit.applied(mixinClassName, targetClassName);
    }
}
