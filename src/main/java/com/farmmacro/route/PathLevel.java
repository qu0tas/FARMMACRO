package com.farmmacro.route;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * v1.15: {@link PathFinder.World} поверх мира клиента. Верх коллизии — как {@link TerrainLevel}; опасность — вода/лава
 * (в т. ч. блоки с водой внутри), огонь, костёр, магма, кактус, паутина, ягоды, рыхлый снег, роза визера, сталактит.
 * Незагруженный чанк — «опасно» (туда не идём). Только главный поток.
 */
public final class PathLevel implements PathFinder.World {
    private final Level level;
    private final TerrainLevel terrain;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public PathLevel(Level level) { this.level = level; this.terrain = new TerrainLevel(level); }

    @Override public double top(int x, int y, int z) { return terrain.top(x, y, z); }

    @Override
    public int hazard(int x, int y, int z) {
        pos.set(x, y, z);
        if (!level.isLoaded(pos)) return PathFinder.H_DANGER;
        BlockState st = level.getBlockState(pos);
        FluidState fs = st.getFluidState();
        if (!fs.isEmpty()) {
            if (fs.is(FluidTags.LAVA)) return PathFinder.H_LAVA;
            if (fs.is(FluidTags.WATER)) return PathFinder.H_WATER;
        }
        if (st.is(BlockTags.FIRE) || st.is(BlockTags.CAMPFIRES) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CACTUS)
                || st.is(Blocks.COBWEB) || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.POWDER_SNOW)
                || st.is(Blocks.WITHER_ROSE) || st.is(Blocks.POINTED_DRIPSTONE)) return PathFinder.H_DANGER;
        return PathFinder.H_NONE;
    }
}
