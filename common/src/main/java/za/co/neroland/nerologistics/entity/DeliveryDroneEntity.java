package za.co.neroland.nerologistics.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * A non-living delivery drone. Dispatched by a drone hub with a cargo stack and a target position; flies
 * straight to the target (a cached point — no per-tick pathfinding), deposits its cargo into the
 * {@link Container} there (dropping any remainder), then despawns. A failsafe age bounds its lifetime so
 * a stranded drone never lingers. Movement is server-authoritative; the client just interpolates the
 * tracked position.
 */
public class DeliveryDroneEntity extends Entity {

    private static final int MAX_AGE_TICKS = 6_000;
    private static final double SPEED = 0.4D;
    private static final double ARRIVE_DISTANCE = 1.2D;

    private BlockPos homePos = BlockPos.ZERO;
    private BlockPos targetPos = BlockPos.ZERO;
    private ItemStack cargo = ItemStack.EMPTY;
    private int age;

    @SuppressWarnings("this-escape")
    public DeliveryDroneEntity(EntityType<? extends DeliveryDroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Configure a freshly-created drone and place it at its home hub. Call before {@code addFreshEntity}. */
    public void dispatch(BlockPos home, BlockPos target, ItemStack cargo) {
        this.homePos = home.immutable();
        this.targetPos = target.immutable();
        this.cargo = cargo.copy();
        this.setPos(home.getX() + 0.5D, home.getY() + 0.5D, home.getZ() + 0.5D);
    }

    public BlockPos homePos() {
        return this.homePos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced state: the client renders from the tracked position only.
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (++this.age > MAX_AGE_TICKS) {
            dropCargo();
            discard();
            return;
        }
        Vec3 target = Vec3.atCenterOf(this.targetPos);
        Vec3 here = position();
        Vec3 diff = target.subtract(here);
        double dist = diff.length();
        if (dist < ARRIVE_DISTANCE) {
            deliver();
            discard();
            return;
        }
        Vec3 step = diff.scale(Math.min(SPEED, dist) / dist);
        setPos(here.x + step.x, here.y + step.y, here.z + step.z);
    }

    private void deliver() {
        if (this.cargo.isEmpty()) {
            return;
        }
        Container dest = InventoryTransfer.containerAt(level(), this.targetPos);
        if (dest != null) {
            int moved = InventoryTransfer.insert(dest, Direction.DOWN, this.cargo, this.cargo.getCount());
            this.cargo.shrink(moved);
        }
        dropCargo();
    }

    private void dropCargo() {
        if (!this.cargo.isEmpty()) {
            Containers.dropItemStack(level(), this.targetPos.getX() + 0.5D, this.targetPos.getY() + 0.5D,
                    this.targetPos.getZ() + 0.5D, this.cargo);
            this.cargo = ItemStack.EMPTY;
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.homePos = new BlockPos(input.getIntOr("HomeX", 0), input.getIntOr("HomeY", 0),
                input.getIntOr("HomeZ", 0));
        this.targetPos = new BlockPos(input.getIntOr("TgtX", 0), input.getIntOr("TgtY", 0),
                input.getIntOr("TgtZ", 0));
        this.cargo = input.read("Cargo", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        this.age = input.getIntOr("Age", 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("HomeX", this.homePos.getX());
        output.putInt("HomeY", this.homePos.getY());
        output.putInt("HomeZ", this.homePos.getZ());
        output.putInt("TgtX", this.targetPos.getX());
        output.putInt("TgtY", this.targetPos.getY());
        output.putInt("TgtZ", this.targetPos.getZ());
        output.store("Cargo", ItemStack.OPTIONAL_CODEC, this.cargo);
        output.putInt("Age", this.age);
    }
}
