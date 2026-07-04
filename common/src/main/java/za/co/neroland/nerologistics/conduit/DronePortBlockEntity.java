package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.dashboard.LogisticsMetrics;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;
import za.co.neroland.nerologistics.menu.DronePortMenu;
import za.co.neroland.nerologistics.network.DronePortRegistry;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.registry.ModEntities;
import za.co.neroland.nerologistics.registry.ModItems;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Drone Port — a standalone, RF-powered point-to-point and inter-network transport block. Works with no
 * network: pipe items into an <b>export</b> port's cargo buffer and it dispatches drones to an
 * <b>import</b> port; the import port's buffer fills and ducts/hoppers pull it out.
 *
 * <ul>
 *   <li><b>Drones are lanes.</b> Install drone items in the port's drone slots — the count is how many
 *       drones can be in flight at once, so more drones carry more (throughput = drones × per-drone
 *       capacity per window).</li>
 *   <li><b>Routing.</b> Name a port (rename the item in an anvil before placing). An export port ships
 *       to import ports with the <em>same name</em>; with no name it ships to the nearest import port in
 *       range (Auto).</li>
 *   <li><b>Hyperspeed upgrade.</b> With a Hyperspeed Card in an upgrade slot, transfers happen
 *       near-instantly and <b>no drone entity is spawned or rendered</b> — so thousands of hyperspeed
 *       drones cost no render/entity budget.</li>
 *   <li><b>Network bridge.</b> Because the cargo buffer is a standard inventory, a duct on one network
 *       feeds an export port and a duct on another drains an import port — the sanctioned way to move
 *       goods between two controllers/networks.</li>
 * </ul>
 *
 * <p>Sneak-click the block to toggle import/export. Fluid/gas/energy upgrade cards and in-GUI name and
 * destination editing are follow-ups; Core SPEED/RANGE/CAPACITY/EFFICIENCY tuning is too.
 */
public class DronePortBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    public enum Mode {
        EXPORT,
        IMPORT
    }

    public static final int BUFFER_SIZE = 9;
    public static final int DRONE_SLOTS = 4;
    public static final int UPGRADE_SLOTS = 3;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_IO = 4_000;

    private final SimpleContainer droneSlots = new SimpleContainer(DRONE_SLOTS);
    private final SimpleContainer upgradeSlots = new SimpleContainer(UPGRADE_SLOTS);
    private Mode mode = Mode.EXPORT;
    private String portName = "";
    private boolean joined;

    public DronePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_PORT.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    public SimpleContainer droneSlots() {
        return this.droneSlots;
    }

    public SimpleContainer upgradeSlots() {
        return this.upgradeSlots;
    }

    public Mode mode() {
        return this.mode;
    }

    public Mode cycleMode() {
        this.mode = this.mode == Mode.EXPORT ? Mode.IMPORT : Mode.EXPORT;
        setChanged();
        return this.mode;
    }

    public String portName() {
        return this.portName;
    }

    public void setPortName(String name) {
        this.portName = name == null ? "" : name;
        setChanged();
    }

    /** Installed drones = total drone items in the drone slots, capped by config. */
    public int installedDrones() {
        int total = 0;
        for (int i = 0; i < this.droneSlots.getContainerSize(); i++) {
            ItemStack stack = this.droneSlots.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.DRONE.get()) {
                total += stack.getCount();
            }
        }
        return Math.min(total, NeroLogisticsConfig.maxDronesPerPort());
    }

    public boolean hasHyperspeed() {
        for (int i = 0; i < this.upgradeSlots.getContainerSize(); i++) {
            if (this.upgradeSlots.getItem(i).getItem() == ModItems.HYPERSPEED_CARD.get()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Component getDisplayName() {
        return this.portName.isEmpty()
                ? Component.translatable("block.nerologistics.drone_port")
                : Component.literal(this.portName);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new DronePortMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Mode", this.mode.ordinal());
        output.putString("PortName", this.portName);
        for (int i = 0; i < DRONE_SLOTS; i++) {
            output.store("Drone" + i, ItemStack.OPTIONAL_CODEC, this.droneSlots.getItem(i));
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            output.store("Upg" + i, ItemStack.OPTIONAL_CODEC, this.upgradeSlots.getItem(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int ordinal = input.getIntOr("Mode", Mode.EXPORT.ordinal());
        Mode[] modes = Mode.values();
        this.mode = ordinal >= 0 && ordinal < modes.length ? modes[ordinal] : Mode.EXPORT;
        this.portName = input.getStringOr("PortName", "");
        for (int i = 0; i < DRONE_SLOTS; i++) {
            this.droneSlots.setItem(i, input.read("Drone" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            this.upgradeSlots.setItem(i, input.read("Upg" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            DronePortRegistry.onRemoved(this.level, this.worldPosition);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DronePortBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!be.joined) {
            DronePortRegistry.onPlaced(level, pos);
            be.joined = true;
        }
        if (!NeroLogisticsConfig.enableDrones() || be.mode != Mode.EXPORT) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.wirelessIntervalTicks() != 0L) {
            return;
        }
        be.dispatch(serverLevel, pos);
    }

    private void dispatch(ServerLevel level, BlockPos pos) {
        if (firstNonEmptyBufferSlot() < 0) {
            return;
        }
        int drones = installedDrones();
        if (drones == 0) {
            return;
        }
        int perStack = NeroLogisticsConfig.dronePortEnergyPerStack();
        if (this.energy.getAmount() < perStack) {
            return;
        }
        int range = NeroLogisticsConfig.dronePortRange();
        DronePortBlockEntity dest = findDestination(level, pos, (long) range * range);
        if (dest == null) {
            return;
        }
        boolean hyper = hasHyperspeed();
        int lanes;
        if (hyper) {
            lanes = drones;
        } else {
            AABB box = new AABB(pos).inflate(range);
            int inFlight = level.getEntitiesOfClass(DeliveryDroneEntity.class, box, d -> pos.equals(d.homePos())).size();
            lanes = Math.max(0, drones - inFlight);
        }
        int cap = NeroLogisticsConfig.dronePerDroneCapacity();
        for (int n = 0; n < lanes; n++) {
            if (this.energy.getAmount() < perStack) {
                break;
            }
            int slot = firstNonEmptyBufferSlot();
            if (slot < 0) {
                break;
            }
            ItemStack inBuf = this.buffer.getItem(slot);
            int amount = Math.min(inBuf.getCount(), cap);
            ItemStack cargo = inBuf.copyWithCount(amount);
            if (hyper) {
                int inserted = InventoryTransfer.insert(dest, Direction.UP, cargo, amount);
                if (inserted <= 0) {
                    break; // destination full
                }
                inBuf.shrink(inserted);
            } else {
                DeliveryDroneEntity drone = new DeliveryDroneEntity(ModEntities.DELIVERY_DRONE.get(), level);
                drone.dispatch(pos, dest.getBlockPos(), cargo);
                level.addFreshEntity(drone);
                inBuf.shrink(amount);
            }
            this.buffer.setChanged();
            this.energy.consume(perStack);
            LogisticsMetrics.recordDrone(level);
        }
        setChanged();
    }

    @Nullable
    private DronePortBlockEntity findDestination(ServerLevel level, BlockPos pos, long rangeSq) {
        String myName = this.portName; // never null — coerced to "" on set/load
        boolean named = !myName.isEmpty();
        DronePortBlockEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : DronePortRegistry.membersOf(level)) {
            if (candidate.equals(pos)) {
                continue;
            }
            double distSq = pos.distSqr(candidate);
            if (distSq > rangeSq || distSq >= bestDist) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(candidate);
            if (!(be instanceof DronePortBlockEntity port) || port.mode != Mode.IMPORT) {
                continue;
            }
            if (named && !myName.equalsIgnoreCase(port.portName)) {
                continue;
            }
            best = port;
            bestDist = distSq;
        }
        return best;
    }

    private int firstNonEmptyBufferSlot() {
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            if (!this.buffer.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}
