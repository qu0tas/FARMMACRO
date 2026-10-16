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
    private final boolean planning;
    public PathLevel(BlockGetter level,boolean planning){this.level=level;this.planning=planning;}
    @Override public boolean openablePlanning(){return planning;}
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    // последняя клетка (PathFinder спрашивает top/hazard/edges подряд для одной клетки)
    private int lx = Integer.MIN_VALUE, ly, lz, lHaz, lEdges;
    private double lTop;
    /** Adapter-local, bounded cache; live adapters are recreated each navigation tick. */
    private record Collision(BlockState state,VoxelShape shape,java.util.List<AABB> boxes){}
    private final java.util.Map<BlockPos,Collision> collisions=new java.util.HashMap<>();
    private Collision collision(BlockPos at){
        Collision known=collisions.get(at);if(known!=null)return known;
        BlockState state=level.getBlockState(at);VoxelShape shape=state.getCollisionShape(level,at);
        var value=new Collision(state,shape,shape.toAabbs());
        if(collisions.size()>=512)collisions.clear();
        collisions.put(at.immutable(),value);return value;
    }

    public PathLevel(BlockGetter level) { this(level,false); }

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
        Collision cell=collision(pos);BlockState st=cell.state();
        lHaz = hazardOf(st);
        lEdges = 0;
        if (lHaz == PathFinder.H_DOOR) { lTop = -1; return; }
        if (st.is(Blocks.SCAFFOLDING)) { lTop = 1.0; return; }       // сверху на лесах стоят, сквозь — проходят
        VoxelShape sh = cell.shape();
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

    @Override public boolean bodyClear(double x,double y,double z,double radius,double height) {
        return bodyClear(x,y,z,radius,height,false);
    }
    @Override public boolean climbClear(double x,double y,double z,double radius,double height){return bodyClear(x,y,z,radius,height,true);}
    private boolean bodyClear(double x,double y,double z,double radius,double height,boolean climbing){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Double.isFinite(radius)||!Double.isFinite(height)||radius<=0||radius>2||height<=0||height>8)return false;
        AABB body=new AABB(x-radius,y,z-radius,x+radius,y+height,z+radius);
        for(int bx=(int)Math.floor(body.minX);bx<=(int)Math.floor(body.maxX);bx++)
          for(int bz=(int)Math.floor(body.minZ);bz<=(int)Math.floor(body.maxZ);bz++)
            for(int by=(int)Math.floor(y)-1;by<=(int)Math.floor(y+height);by++) {
              BlockPos at=new BlockPos(bx,by,bz);
              if(level instanceof net.minecraft.world.level.Level live&&!live.hasChunkAt(at))return false;
              Collision cell=collision(at);
              if(cell.state().is(Blocks.VOID_AIR)||level instanceof net.minecraft.world.level.Level live&&!live.hasChunkAt(at)) return false;
              if(planning&&closedOpenable(cell.state())||climbing&&cell.state().is(BlockTags.CLIMBABLE))continue;
              for(AABB box:cell.boxes())
                if(box.move(at).intersects(body)) return false;
            }
        return true;
    }
    @Override public boolean exactShapes(){return true;}
    @Override public double supportY(double x,double z,double min,double max){
        if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(min)||!Double.isFinite(max)||max<min||max-min>80)return Double.NaN;
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        double best=Double.NaN;
        for(int by=(int)Math.floor(max);by>=(int)Math.floor(min)-2;by--){
            BlockPos at=new BlockPos(bx,by,bz);
            if(level instanceof net.minecraft.world.level.Level live&&!live.hasChunkAt(at))return Double.NaN;
            Collision cell=collision(at);
            if(cell.state().is(Blocks.VOID_AIR)||level instanceof net.minecraft.world.level.Level live&&!live.hasChunkAt(at))return Double.NaN;
            for(AABB b:cell.boxes()){
                double feet=by+b.maxY;
                if(x>=bx+b.minX-1e-7&&x<=bx+b.maxX+1e-7&&z>=bz+b.minZ-1e-7&&z<=bz+b.maxZ+1e-7
                    &&feet>=min-1e-6&&feet<=max+1e-6&&(!Double.isFinite(best)||feet>best))best=feet;
            }
        }
        return best;
    }

    @Override public PathFinder.World.Climb climb(int x,int y,int z){
        BlockPos at=new BlockPos(x,y,z);
        if(level instanceof net.minecraft.world.level.Level live&&!live.hasChunkAt(at))return null;
        var st=level.getBlockState(at);if(!st.is(BlockTags.CLIMBABLE))return null;
        if(st.is(Blocks.SCAFFOLDING))return new PathFinder.World.Climb(x+.5,z+.5,0,0,true);
        Direction face=null;
        if(st.getBlock() instanceof net.minecraft.world.level.block.LadderBlock)face=st.getValue(BlockStateProperties.HORIZONTAL_FACING);
        else for(Direction d:new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST}){
            var property=net.minecraft.world.level.block.VineBlock.getPropertyForFace(d);
            if(st.hasProperty(property)&&st.getValue(property)){face=d.getOpposite();break;}
        }
        double px=face==null?0:-face.getStepX(),pz=face==null?0:-face.getStepZ();
        return new PathFinder.World.Climb(x+.5+px*.18,z+.5+pz*.18,px,pz,false);
    }
    @Override public double top(int x, int y, int z) { load(x, y, z); return lTop; }
    @Override public int hazard(int x, int y, int z) { load(x, y, z); return lHaz; }
    @Override public int edges(int x, int y, int z) { load(x, y, z); return lEdges; }
}
