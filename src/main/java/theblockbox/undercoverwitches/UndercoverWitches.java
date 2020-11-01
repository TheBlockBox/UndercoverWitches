package theblockbox.undercoverwitches;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.monster.WitchEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod("undercoverwitches")
public class UndercoverWitches {

    public UndercoverWitches() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    // converts a normal cat to a witch cat
    public static void convertCatToWitch(CatEntity cat) {
        // add goal to transform into witch in the morning
        // with higher priority so we don't have to remove the standard gift goal
        cat.goalSelector.addGoal(1, new MorningWitchGiftGoal(cat));
        cat.addTag("undercover_witch");
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        // Add the goal to all entities that are already undercover witches
        if ((event.getEntity() instanceof CatEntity) && (event.getEntity().getTags().contains("undercover_witch"))) {
            convertCatToWitch((CatEntity) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onEntitySpawn(LivingSpawnEvent.SpecialSpawn event) {
        // Make 16.7% of black cats witch cats
        Entity entity = event.getEntity();
        if ((entity instanceof CatEntity) && (((CatEntity) entity).getCatType() == 10) && (((CatEntity) entity).getRNG().nextInt(6) == 0)) {
            convertCatToWitch((CatEntity) entity);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntityLiving();
        Entity source = event.getSource().getTrueSource();
        // 16.7% chance that a witch turns into a black cat on death
        if ((entity.isServerWorld())
                && (entity instanceof WitchEntity)
                && (source instanceof PlayerEntity)
                && (entity.getRNG().nextInt(6) == 0)) {
            ServerWorld world = (ServerWorld) entity.world;
            CatEntity cat = EntityType.CAT.create(world);
            if (cat != null) {
                // Spawn cat tamed to player
                // TODO: Spread spawn position a little
                cat.setLocationAndAngles(entity.getPosX(), entity.getPosY(), entity.getPosZ(), entity.rotationYaw, entity.rotationPitch);
                cat.onInitialSpawn(world, world.getDifficultyForLocation(entity.func_233580_cy_()), SpawnReason.MOB_SUMMONED, null, null);

                // tame cat to player
                // we are not using CatEntity#setTamedBy here as it would add achievements
                cat.setTamed(true);
                cat.setOwnerId(source.getUniqueID());

                cat.setCatType(10); // black color, matching the one in swamps
                cat.enablePersistence();
                convertCatToWitch(cat);
                world.func_242417_l(cat);
            }
        }
    }

    // Makes cats spawn a witch instead of a gift
    public static class MorningWitchGiftGoal extends Goal {
        private final CatEntity cat;
        private PlayerEntity owner;
        private BlockPos bedPos;
        private int tickCounter;

        public MorningWitchGiftGoal(CatEntity cat) {
            this.cat = cat;
        }

        public boolean shouldExecute() {
            if (!cat.isTamed()) {
                return false;
            } else if (cat.func_233685_eM_()) {
                return false;
            } else {
                LivingEntity owner = cat.getOwner();
                if (owner instanceof PlayerEntity) {
                    this.owner = (PlayerEntity) owner;
                    if (!owner.isSleeping() || (cat.getDistanceSq(owner) > 100.0D)) {
                        return false;
                    }
                    BlockPos pos = owner.func_233580_cy_();
                    BlockState blockState = cat.world.getBlockState(pos);
                    if (blockState.getBlock().isIn(BlockTags.BEDS)) {
                        bedPos = blockState.func_235903_d_(BedBlock.HORIZONTAL_FACING).map((p_234186_1_) -> pos.offset(p_234186_1_.getOpposite())).orElseGet(() -> new BlockPos(pos));
                        return canFindNoOtherCats();
                    }
                }

                return false;
            }
        }

        private boolean canFindNoOtherCats() {
            for (CatEntity otherCat : cat.world.getEntitiesWithinAABB(CatEntity.class, (new AxisAlignedBB(bedPos)).grow(2.0D))) {
                if ((otherCat != cat) && (otherCat.func_213416_eg() || otherCat.func_213409_eh())) {
                    return false;
                }
            }
            return true;
        }

        public boolean shouldContinueExecuting() {
            return cat.isTamed() && !cat.func_233685_eM_() && (owner != null) && owner.isSleeping() && (bedPos != null) && canFindNoOtherCats();
        }

        public void startExecuting() {
            if (bedPos != null) {
                cat.func_233686_v_(false);
                cat.getNavigator().tryMoveToXYZ(bedPos.getX(), bedPos.getY(), bedPos.getZ(), 1.1F);
            }
        }

        public void resetTask() {
            cat.func_213419_u(false);
            float f = cat.world.func_242415_f(1.0F);
            if ((owner.getSleepTimer() >= 100) && (f > 0.77D) && (f < 0.8D) && (cat.world.getRandom().nextFloat() < 0.7D)) {
                deliverGift();
            }
            tickCounter = 0;
            cat.func_213415_v(false);
            cat.getNavigator().clearPath();
        }

        private void deliverGift() {
            ServerWorld world = (ServerWorld) cat.world;
            Random random = cat.getRNG();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            pos.setPos(cat.func_233580_cy_());
            cat.attemptTeleport(pos.getX() + random.nextInt(11) - 5, pos.getY() + random.nextInt(5) - 2, pos.getZ() + random.nextInt(11) - 5, false);
            pos.setPos(cat.func_233580_cy_());

            // spawn witch
            WitchEntity witch = EntityType.WITCH.create(world);
            if (witch != null) {
                witch.enablePersistence();
                witch.setLocationAndAngles(cat.getPosX(), cat.getPosY(), cat.getPosZ(), cat.rotationYaw, cat.rotationPitch);
                witch.onInitialSpawn(world, world.getDifficultyForLocation(cat.func_233580_cy_()), SpawnReason.STRUCTURE, null, null);
                witch.setAttackTarget(owner);
                world.func_242417_l(witch);
                // remove cat
                cat.remove();
            }
        }

        public void tick() {
            if (owner != null && bedPos != null) {
                cat.func_233686_v_(false);
                cat.getNavigator().tryMoveToXYZ(bedPos.getX(), bedPos.getY(), bedPos.getZ(), 1.1F);
                if (cat.getDistanceSq(owner) < 2.5D) {
                    ++tickCounter;
                    if (tickCounter > 16) {
                        cat.func_213419_u(true);
                        cat.func_213415_v(false);
                    } else {
                        cat.faceEntity(owner, 45.0F, 45.0F);
                        cat.func_213415_v(true);
                    }
                } else {
                    cat.func_213419_u(false);
                }
            }

        }
    }

}
