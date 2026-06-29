package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;
import za.co.neroland.nerologistics.network.WirelessRegistry;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.registry.ModEntities;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Drone hub: stocks an item buffer (ducts/hoppers fill it) and dispatches a hard-capped pool of delivery
 * drones to wireless cargo terminals on its channel within range, charging energy per delivery. The
 * per-hub cap is enforced by counting this hub's live drones each dispatch window; targets come from the
 * cheap wireless-channel membership list (no world scan).
 */
public class DroneHubBlockEntity extends AbstractTerminalBlockEntity {

    public static final int BUFFER_SIZE = 9;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_IO = 4_000;

    private int channel;

    public DroneHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_HUB.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    public int channel() {
        return this.channel;
    }

    public int cycleChannel() {
        this.channel = (this.channel + 1) % WirelessCargoTerminalBlockEntity.CHANNELS;
        setChanged();
        return this.channel;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Channel", this.channel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.channel = input.getIntOr("Channel", 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DroneHubBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.wirelessIntervalTicks() != 0L) {
            return;
        }
        be.tryDispatch(serverLevel, pos);
    }

    private void tryDispatch(ServerLevel level, BlockPos pos) {
        if (!NeroLogisticsConfig.enableDrones() || this.buffer.isEmpty()) {
            return;
        }
        int perDelivery = NeroLogisticsConfig.droneEnergyPerDelivery();
        if (this.energy.getAmount() < perDelivery) {
            return;
        }
        int range = NeroLogisticsConfig.droneRange();
        int cap = NeroLogisticsConfig.dronesPerHub();
        AABB box = new AABB(pos).inflate(range);
        int live = level.getEntitiesOfClass(DeliveryDroneEntity.class, box, d -> pos.equals(d.homePos())).size();
        if (live >= cap) {
            return;
        }
        BlockPos target = findTarget(level, pos, range);
        if (target == null) {
            return;
        }
        for (int slot = 0; slot < this.buffer.getContainerSize(); slot++) {
            ItemStack stack = this.buffer.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            DeliveryDroneEntity drone = new DeliveryDroneEntity(ModEntities.DELIVERY_DRONE.get(), level);
            drone.dispatch(pos, target, stack.copy());
            level.addFreshEntity(drone);
            this.buffer.setItem(slot, ItemStack.EMPTY);
            this.energy.consume(perDelivery);
            LogisticsMetrics.recordDrone(level);
            setChanged();
            return; // one dispatch per window
        }
    }

    @Nullable
    private BlockPos findTarget(ServerLevel level, BlockPos pos, int range) {
        double rangeSq = (double) range * range;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : WirelessRegistry.membersOf(level, this.channel)) {
            double distSq = pos.distSqr(candidate);
            if (distSq > rangeSq || distSq >= bestDist) {
                continue;
            }
            Container dest = InventoryTransfer.containerAt(level, candidate);
            if (dest != null) {
                best = candidate;
                bestDist = distSq;
            }
        }
        return best;
    }
}
