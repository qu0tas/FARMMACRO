package com.farmmacro.route;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v1.15: {@link PathFinder.World} поверх блоков. Верх коллизии — как {@link TerrainLevel}; опасность — вода/лава
 * (в т. ч. блоки с водой внутри), огонь, костёр, магма, кактус, паутина, ягоды, рыхлый снег, роза визера, сталактит.
 * Незагруженный чанк (VOID_AIR) — «опасно» (туда не идём).
 * <p>v1.16.2: читает {@link BlockGetter} — в игре это {@link LevelSnapshot} (копия блоков, можно из потока поиска).
 * Лестницы, лианы, леса — {@link PathFinder#H_CLIMB}; тонкая пластина у края клетки (открытая дверь, открытый люк,
 * лестница, железная дверь) — не «блок», а стенка на грани ({@link #edges}); закрытая деревянная/медная дверь и
 * калитка — {@link PathFinder#H_DOOR} (проходима, ReturnWalker откроет ПКМ).
 */
public final class PathLevel implements PathFinder.World {
    private final BlockGetter level;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    // последняя клетка (PathFinder спрашивает top/hazard/edges подряд для одной клетки)
    private int lx = Integer.MIN_VALUE, ly, lz, lHaz, lEdges;
    private double lTop;

    public PathLevel(BlockGetter level) { this.level = level; }

    /** Закрытая дверь, которую открывает рука (дерево, медь), или закрытая калитка. */
    public static boolean closedOpenable(BlockState st) {
        if (st.getBlock() instanceof DoorBlock d) return d.type().canOpenByHand() && !d.isOpen(st);
        if (st.getBlock() instanceof FenceGateBlock) return !st.getValue(BlockStateProperties.OPEN);
        return false;
    }

    private void load(int x, int y, int z) {
        if (x == lx && y == ly && z == lz) return;
        lx = x; ly = y; lz = z;
        pos.set(x, y, z);
        BlockState st = level.getBlockState(pos);
        lHaz = hazardOf(st);
        lEdges = 0;
        if (lHaz == PathFinder.H_DOOR) { lTop = -1; return; }
        if (st.is(Blocks.SCAFFOLDING)) { lTop = 1.0; return; }       // сверху на лесах стоят, сквозь — проходят
        VoxelShape sh = st.getCollisionShape(level, pos);
        if (sh.isEmpty()) { lTop = -1; return; }
        AABB b = sh.bounds();
        if (b.maxX <= 0.2) lEdges = PathFinder.E_NX;
        else if (b.minX >= 0.8) lEdges = PathFinder.E_PX;
        else if (b.maxZ <= 0.2) lEdges = PathFinder.E_NZ;
        else if (b.minZ >= 0.8) lEdges = PathFinder.E_PZ;
        lTop = lEdges != 0 ? -1 : Math.min(1.5, sh.max(Direction.Axis.Y));
    }

    private static int hazardOf(BlockState st) {
        if (st.is(Blocks.VOID_AIR)) return PathFinder.H_DANGER;
        FluidState fs = st.getFluidState();
        if (!fs.isEmpty()) {
            if (fs.is(FluidTags.LAVA)) return PathFinder.H_LAVA;
            if (fs.is(FluidTags.WATER)) return PathFinder.H_WATER;
        }
        if (st.is(BlockTags.FIRE) || st.is(BlockTags.CAMPFIRES) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.CACTUS)
                || st.is(Blocks.COBWEB) || st.is(Blocks.SWEET_BERRY_BUSH) || st.is(Blocks.POWDER_SNOW)
                || st.is(Blocks.WITHER_ROSE) || st.is(Blocks.POINTED_DRIPSTONE)) return PathFinder.H_DANGER;
        if (st.is(BlockTags.CLIMBABLE)) return PathFinder.H_CLIMB;
        if (closedOpenable(st)) return PathFinder.H_DOOR;
        return PathFinder.H_NONE;
    }

    @Override public double top(int x, int y, int z) { load(x, y, z); return lTop; }
    @Override public int hazard(int x, int y, int z) { load(x, y, z); return lHaz; }
    @Override public int edges(int x, int y, int z) { load(x, y, z); return lEdges; }
}
