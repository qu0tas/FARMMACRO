package com.farmmacro.util;

import com.farmmacro.panic.ServerText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** Read-only helpers for SkyBlock item metadata (ExtraAttributes id, lore, display name). */
public final class SkyblockItems {
    /** SkyBlock item id from {@code ExtraAttributes.id}, or an empty string. */
    public static String id(ItemStack s) {
        var d = s.get(DataComponents.CUSTOM_DATA);
        return d == null
                ? ""
                : d.copyTag().getCompoundOrEmpty("ExtraAttributes").getStringOr("id", "");
    }

    /** Lore lines joined with spaces, formatting codes stripped. */
    public static String lore(ItemStack s) {
        var l = s.get(DataComponents.LORE);
        return l == null
                ? ""
                : ServerText.clean(String.join(
                        " ", l.lines().stream().map(x -> x.getString()).toList()));
    }

    /** Hover name with formatting codes stripped. */
    public static String name(ItemStack s) {
        return ServerText.clean(s.getHoverName().getString());
    }

    private SkyblockItems() {}
}
