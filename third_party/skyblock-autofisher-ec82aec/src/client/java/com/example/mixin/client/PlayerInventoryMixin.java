package com.example.mixin.client;

import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Inventory.class)
public interface PlayerInventoryMixin {
    @Accessor("selected")
    void setSelectedSlot(int selectedSlot);

    @Accessor("selected")
    int getSelectedSlot();
}
