/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2015-2018 the Valkyrien Warfare team
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income unless it is to be used as a part of a larger project (IE: "modpacks"), nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from the Valkyrien Warfare team.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: The Valkyrien Warfare team), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package valkyrienwarfare.mixin.world;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Interface.Remap;
import org.spongepowered.asm.mixin.Intrinsic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import valkyrienwarfare.ValkyrienWarfareMod;
import valkyrienwarfare.addon.control.nodenetwork.INodeProvider;
import valkyrienwarfare.api.Vector;
import valkyrienwarfare.fixes.WorldChunkloadingCrashFix;
import valkyrienwarfare.mod.coordinates.TransformType;
import valkyrienwarfare.mod.physmanagement.interaction.IWorldVW;
import valkyrienwarfare.physics.collision.polygons.Polygon;
import valkyrienwarfare.physics.management.PhysicsWrapperEntity;
import valkyrienwarfare.physics.management.WorldPhysObjectManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// TODO this class is horrible
@Mixin(value = World.class, priority = 1005)
@Implements(@Interface(iface = WorldChunkloadingCrashFix.class, prefix = "vw$", remap = Remap.NONE))
public abstract class MixinWorld implements IWorldVW {

    private static double MAX_ENTITY_RADIUS_ALT = 2.0D;
    @Shadow
    List<IWorldEventListener> eventListeners;
    @Shadow
    boolean processingLoadedTiles;
    @Shadow
    List<TileEntity> addedTileEntityList;
    private World thisClassAsWorld = World.class.cast(this);
    private WorldPhysObjectManager physManager;
    private boolean isRaytracingRecursive = false;

    @Inject(method = "setBlockState", at = @At("HEAD"))
    public void preSetBlockState(BlockPos pos, IBlockState newState, int flags,
                                 CallbackInfoReturnable callbackInfo) {
        PhysicsWrapperEntity physEntity = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(thisClassAsWorld,
                pos);
        if (physEntity != null) {
            IBlockState oldState = thisClassAsWorld.getBlockState(pos);
            physEntity.getPhysicsObject().onSetBlockState(oldState, newState, pos);
        }
    }

    /**
     * This is easier to have as an overwrite because there's less laggy hackery to
     * be done then :P
     *
     * @author DaPorkchop_
     */
    @Overwrite
    public void spawnParticle(int particleID, boolean ignoreRange, double x, double y, double z, double xSpeed,
                              double ySpeed, double zSpeed, int... parameters) {
        BlockPos pos = new BlockPos(x, y, z);
        PhysicsWrapperEntity wrapper = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(World.class.cast(this),
                pos);
        if (wrapper != null) {
            Vector newPosVec = new Vector(x, y, z);
            // RotationMatrices.applyTransform(wrapper.wrapping.coordTransform.lToWTransform,
            // newPosVec);
            wrapper.getPhysicsObject().getShipTransformationManager().getCurrentTickTransform().transform(newPosVec,
                    TransformType.SUBSPACE_TO_GLOBAL);
            x = newPosVec.X;
            y = newPosVec.Y;
            z = newPosVec.Z;
        }
        for (int i = 0; i < this.eventListeners.size(); ++i) {
            this.eventListeners.get(i).spawnParticle(particleID, ignoreRange, x, y, z, xSpeed, ySpeed, zSpeed,
                    parameters);
        }
    }

    @Shadow
    public IBlockState getBlockState(BlockPos pos) {
        return null;
    }

    @Shadow
    protected abstract boolean isChunkLoaded(int x, int z, boolean allowEmpty);

    @Shadow
    public Chunk getChunkFromChunkCoords(int chunkX, int chunkZ) {
        return null;
    }

    public <T extends Entity> List<T> getEntitiesWithinAABBOriginal(Class<? extends T> clazz, AxisAlignedBB aabb,
                                                                    @Nullable Predicate<? super T> filter) {
        int i = MathHelper.floor((aabb.minX - MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int j = MathHelper.ceil((aabb.maxX + MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int k = MathHelper.floor((aabb.minZ - MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int l = MathHelper.ceil((aabb.maxZ + MAX_ENTITY_RADIUS_ALT) / 16.0D);
        List<T> list = Lists.newArrayList();

        for (int i1 = i; i1 < j; ++i1) {
            for (int j1 = k; j1 < l; ++j1) {
                if (this.isChunkLoaded(i1, j1, true)) {
                    this.getChunkFromChunkCoords(i1, j1).getEntitiesOfTypeWithinAABB(clazz, aabb, list, filter);
                }
            }
        }

        return list;
    }

    public List<Entity> getEntitiesInAABBexcludingOriginal(@Nullable Entity entityIn, AxisAlignedBB boundingBox,
                                                           @Nullable Predicate<? super Entity> predicate) {
        List<Entity> list = Lists.newArrayList();
        int i = MathHelper.floor((boundingBox.minX - MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int j = MathHelper.floor((boundingBox.maxX + MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int k = MathHelper.floor((boundingBox.minZ - MAX_ENTITY_RADIUS_ALT) / 16.0D);
        int l = MathHelper.floor((boundingBox.maxZ + MAX_ENTITY_RADIUS_ALT) / 16.0D);

        for (int i1 = i; i1 <= j; ++i1) {
            for (int j1 = k; j1 <= l; ++j1) {
                if (this.isChunkLoaded(i1, j1, true)) {
                    this.getChunkFromChunkCoords(i1, j1).getEntitiesWithinAABBForEntity(entityIn, boundingBox, list,
                            predicate);
                }
            }
        }

        return list;
    }

    /**
     * aa
     *
     * @author xd
     */
    @Overwrite
    public <T extends Entity> List<T> getEntitiesWithinAABB(Class<? extends T> clazz, AxisAlignedBB aabb,
                                                            @Nullable Predicate<? super T> filter) {
        List toReturn = this.getEntitiesWithinAABBOriginal(clazz, aabb, filter);

        if (ValkyrienWarfareMod.physicsManager == null) {
            return toReturn;
        }

        BlockPos pos = new BlockPos((aabb.minX + aabb.maxX) / 2D, (aabb.minY + aabb.maxY) / 2D,
                (aabb.minZ + aabb.maxZ) / 2D);
        PhysicsWrapperEntity wrapper = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(World.class.cast(this),
                pos);
        if (wrapper != null) {
            Polygon poly = new Polygon(aabb, wrapper.getPhysicsObject().getShipTransformationManager().getCurrentTickTransform(),
                    TransformType.SUBSPACE_TO_GLOBAL);
            aabb = poly.getEnclosedAABB();// .contract(.3D);
            toReturn.addAll(this.getEntitiesWithinAABBOriginal(clazz, aabb, filter));

            toReturn.remove(wrapper);
        }
        return toReturn;
    }

    /**
     * aa
     *
     * @author xd
     */
    @Overwrite
    public List<Entity> getEntitiesInAABBexcluding(@Nullable Entity entityIn, AxisAlignedBB boundingBox,
                                                   @Nullable Predicate<? super Entity> predicate) {
        if ((boundingBox.maxX - boundingBox.minX) * (boundingBox.maxZ - boundingBox.minZ) > 1000000D) {
            return new ArrayList();
        }

        // Prevents the players item pickup AABB from merging with a
        // PhysicsWrapperEntity AABB
        if (entityIn instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entityIn;
            if (player.isRiding() && !player.getRidingEntity().isDead
                    && player.getRidingEntity() instanceof PhysicsWrapperEntity) {
                AxisAlignedBB axisalignedbb = player.getEntityBoundingBox()
                        .union(player.getRidingEntity().getEntityBoundingBox()).expand(1.0D, 0.0D, 1.0D);

                if (boundingBox.equals(axisalignedbb)) {
                    boundingBox = player.getEntityBoundingBox().expand(1.0D, 0.5D, 1.0D);
                }
            }
        }

        List toReturn = this.getEntitiesInAABBexcludingOriginal(entityIn, boundingBox, predicate);

        BlockPos pos = new BlockPos((boundingBox.minX + boundingBox.maxX) / 2D,
                (boundingBox.minY + boundingBox.maxY) / 2D, (boundingBox.minZ + boundingBox.maxZ) / 2D);
        PhysicsWrapperEntity wrapper = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(World.class.cast(this),
                pos);
        if (wrapper != null) {
            Polygon poly = new Polygon(boundingBox, wrapper.getPhysicsObject().getShipTransformationManager().getCurrentTickTransform(),
                    TransformType.SUBSPACE_TO_GLOBAL);
            boundingBox = poly.getEnclosedAABB().shrink(.3D);
            toReturn.addAll(this.getEntitiesInAABBexcludingOriginal(entityIn, boundingBox, predicate));

            toReturn.remove(wrapper);
        }
        return toReturn;
    }

    // TODO: acutally move the sound, i don't think this does anything yet
    @Inject(method = "playSound(DDDLnet/minecraft/util/SoundEvent;Lnet/minecraft/util/SoundCategory;FFZ)V", at = @At("HEAD"))
    public void prePlaySound(double x, double y, double z, SoundEvent soundIn, SoundCategory category, float volume,
                             float pitch, boolean distanceDelay, CallbackInfo callbackInfo) {
        BlockPos pos = new BlockPos(x, y, z);
        PhysicsWrapperEntity wrapper = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(World.class.cast(this),
                pos);
        if (wrapper != null) {
            Vector posVec = new Vector(x, y, z);
            wrapper.getPhysicsObject().getShipTransformationManager().fromLocalToGlobal(posVec);
            x = posVec.X;
            y = posVec.Y;
            z = posVec.Z;
        }
    }

    /*
     * @Inject(method =
     * "markAndNotifyBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;I)V",
     * at = @At("HEAD")) public void preMarkAndNotifyBlock(BlockPos pos, Chunk
     * chunk, IBlockState oldState, IBlockState newState, int flags, CallbackInfo
     * callbackInfo) { PhysicsWrapperEntity wrapper =
     * ValkyrienWarfareMod.physicsManager.getObjectManagingPos(World.class.cast(this
     * ), pos); if (wrapper != null) { System.out.println("Got the state 1");
     * wrapper.wrapping.onSetBlockState(oldState, newState, pos); } }
     */

    // TODO: Something about this is just too buggy to work reliably
    /*
     * @Inject(method =
     * "getBiome(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/biome/Biome;",
     * at = @At("HEAD"), cancellable = true) public void preGetBiome(final BlockPos
     * pos, CallbackInfoReturnable<Biome> callbackInfoReturnable) { if
     * (PhysicsChunkManager.isLikelyShipChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
     * try { if (ValkyrienWarfareMod.physicsManager == null || pos == null ||
     * thisClassAsWorld == null) { return; } PhysicsWrapperEntity wrapper =
     * ValkyrienWarfareMod.physicsManager.getObjectManagingPos(thisClassAsWorld,
     * pos); if (wrapper != null && wrapper.wrapping != null &&
     * wrapper.wrapping.coordTransform != null && pos != null) { // BlockPos realPos
     * = // RotationMatrices.applyTransform(wrapper.wrapping.coordTransform.
     * lToWTransform, // pos); BlockPos realPos =
     * wrapper.wrapping.coordTransform.getCurrentTickTransform().transform(pos,
     * TransformType.LOCAL_TO_GLOBAL); Biome toReturn =
     * thisClassAsWorld.getBiome(realPos); if (toReturn != null) {
     * callbackInfoReturnable.setReturnValue(toReturn); return; } } } catch
     * (Exception e) { e.printStackTrace(); } } // do nothing and run vanilla }
     */

    /**
     * aa
     *
     * @author xd
     */
    @Overwrite
    public void setTileEntity(BlockPos pos, @Nullable TileEntity tileEntityIn) {
        pos = pos.toImmutable(); // Forge - prevent mutable BlockPos leaks

        if (tileEntityIn instanceof INodeProvider) {
            PhysicsWrapperEntity wrapper = ValkyrienWarfareMod.physicsManager.getObjectManagingPos(thisClassAsWorld,
                    pos);
            if (wrapper != null) {
                ((INodeProvider) tileEntityIn).getNode().updateParentEntity(wrapper.getPhysicsObject());
            }
        }

        if (!this.isOutsideBuildHeight(pos)) {
            if (tileEntityIn != null && !tileEntityIn.isInvalid()) {
                if (processingLoadedTiles) {
                    tileEntityIn.setPos(pos);
                    if (tileEntityIn.getWorld() != thisClassAsWorld)
                        tileEntityIn.setWorld(thisClassAsWorld); // Forge - set the world early as vanilla doesn't set
                    // it until next tick
                    Iterator<TileEntity> iterator = this.addedTileEntityList.iterator();

                    while (iterator.hasNext()) {
                        TileEntity tileentity = iterator.next();

                        if (tileentity.getPos().equals(pos)) {
                            tileentity.invalidate();
                            iterator.remove();
                        }
                    }

                    this.addedTileEntityList.add(tileEntityIn);
                } else {
                    Chunk chunk = thisClassAsWorld.getChunkFromBlockCoords(pos);
                    if (chunk != null)
                        chunk.addTileEntity(pos, tileEntityIn);
                    thisClassAsWorld.addTileEntity(tileEntityIn);
                }
            }
        }
    }

    @Intrinsic(displace = true)
    public Iterator<Chunk> vw$getPersistentChunkIterable(Iterator<Chunk> chunkIterator) {
        ArrayList<Chunk> persistantChunks = new ArrayList<Chunk>();
        while (chunkIterator.hasNext()) {
            Chunk chunk = chunkIterator.next();
            persistantChunks.add(chunk);
        }
        Iterator<Chunk> replacementIterator = persistantChunks.iterator();

        return getPersistentChunkIterable(replacementIterator);
    }

    @Shadow
    public abstract Iterator<Chunk> getPersistentChunkIterable(Iterator<Chunk> chunkIterator);

    @Shadow
    abstract boolean isOutsideBuildHeight(BlockPos pos);

    @Inject(method = "rayTraceBlocks(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;ZZZ)Lnet/minecraft/util/math/RayTraceResult;", at = @At("HEAD"), cancellable = true)
    public void preRayTraceBlocks(Vec3d vec31, Vec3d vec32, boolean stopOnLiquid, boolean ignoreBlockWithoutBoundingBox,
                                  boolean returnLastUncollidableBlock, CallbackInfoReturnable<RayTraceResult> callbackInfo) {
        if (!isRaytracingRecursive) {
            callbackInfo.setReturnValue(rayTraceBlocksIgnoreShip(vec31, vec32, stopOnLiquid,
                    ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock, null));
        }
    }

    @Override
    public RayTraceResult rayTraceBlocksIgnoreShip(Vec3d vec31, Vec3d vec32, boolean stopOnLiquid,
                                                   boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock, PhysicsWrapperEntity toIgnore) {
        isRaytracingRecursive = true;
        RayTraceResult vanillaTrace = thisClassAsWorld.rayTraceBlocks(vec31, vec32, stopOnLiquid,
                ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
        WorldPhysObjectManager physManager = ValkyrienWarfareMod.physicsManager
                .getManagerForWorld(World.class.cast(this));
        if (physManager == null) {
            return vanillaTrace;
        }

        Vec3d playerEyesPos = vec31;
        Vec3d playerReachVector = vec32.subtract(vec31);

        AxisAlignedBB playerRangeBB = new AxisAlignedBB(vec31.x, vec31.y, vec31.z, vec32.x, vec32.y, vec32.z);

        List<PhysicsWrapperEntity> nearbyShips = physManager.getNearbyPhysObjects(playerRangeBB);
        // Get rid of the Ship that we're not supposed to be RayTracing for
        nearbyShips.remove(toIgnore);

        double reachDistance = playerReachVector.lengthVector();
        double worldResultDistFromPlayer = 420000000D;
        if (vanillaTrace != null && vanillaTrace.hitVec != null) {
            worldResultDistFromPlayer = vanillaTrace.hitVec.distanceTo(vec31);
        }

        PhysicsWrapperEntity transformedEntity = null;

        for (PhysicsWrapperEntity wrapper : nearbyShips) {
            playerEyesPos = vec31;
            playerReachVector = vec32.subtract(vec31);
            // TODO: Re-enable
            if (World.class.cast(this).isRemote) {
                // ValkyrienWarfareMod.proxy.updateShipPartialTicks(wrapper);
            }

            playerEyesPos = wrapper.getPhysicsObject().getShipTransformationManager().getRenderTransform().transform(playerEyesPos,
                    TransformType.GLOBAL_TO_SUBSPACE);
            playerReachVector = wrapper.getPhysicsObject().getShipTransformationManager().getRenderTransform().rotate(playerReachVector,
                    TransformType.GLOBAL_TO_SUBSPACE);

            Vec3d playerEyesReachAdded = playerEyesPos.addVector(playerReachVector.x * reachDistance,
                    playerReachVector.y * reachDistance, playerReachVector.z * reachDistance);
            RayTraceResult resultInShip = thisClassAsWorld.rayTraceBlocks(playerEyesPos, playerEyesReachAdded,
                    stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock);
            if (resultInShip != null && resultInShip.hitVec != null
                    && resultInShip.typeOfHit == RayTraceResult.Type.BLOCK) {
                double shipResultDistFromPlayer = resultInShip.hitVec.distanceTo(playerEyesPos);
                if (shipResultDistFromPlayer < worldResultDistFromPlayer) {
                    worldResultDistFromPlayer = shipResultDistFromPlayer;
                    resultInShip.hitVec = wrapper.getPhysicsObject().getShipTransformationManager().getRenderTransform()
                            .transform(resultInShip.hitVec, TransformType.SUBSPACE_TO_GLOBAL);
                    vanillaTrace = resultInShip;
                    transformedEntity = wrapper;
                }
            }
        }

        if (transformedEntity != null) {
            Vec3d hitVec2 = vanillaTrace.hitVec;
            hitVec2 = transformedEntity.getPhysicsObject().getShipTransformationManager().getRenderTransform().transform(hitVec2,
                    TransformType.GLOBAL_TO_SUBSPACE);
            vanillaTrace.hitVec = hitVec2;
        }

        isRaytracingRecursive = false;
        return vanillaTrace;
    }
}
