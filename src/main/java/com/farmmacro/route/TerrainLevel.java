package com.farmmacro.route;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** {@link Terrain.Cells} поверх мира клиента: верх коллизии блока (getCollisionShape), −1 — коллизии нет. */
public final class TerrainLevel implements Terrain.Cells {
    private final Level level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public TerrainLevel(Level level) { this.level = level; }

    @Override
    public double top(int x, int y, int z) {
        pos.set(x, y, z);
        BlockState st = level.getBlockState(pos);
        VoxelShape sh = st.getCollisionShape(level, pos);
        return sh.isEmpty() ? -1 : Math.min(1.5, sh.max(Direction.Axis.Y));
    }
}
