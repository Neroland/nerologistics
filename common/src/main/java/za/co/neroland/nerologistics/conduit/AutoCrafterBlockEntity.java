package za.co.neroland.nerologistics.conduit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
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

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.config.NeroLogisticsConfig;
import za.co.neroland.nerologistics.menu.AutoCrafterMenu;
import za.co.neroland.nerologistics.network.ConduitNetwork;
import za.co.neroland.nerologistics.network.NetworkManager;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerologistics.registry.ModBlockEntities;
import za.co.neroland.nerologistics.transport.InventoryTransfer;

/**
 * Auto-Crafter — native pattern-based auto-crafting. A 10-slot ghost <b>pattern</b> (3×3 inputs + 1
 * output) encodes one recipe; the crafter pulls the input items from the inventories on the attached
 * item network and produces the pattern's output into its output buffer (which ducts/hoppers then
 * distribute). It runs on an interval, charges energy per craft, and its crafts-per-interval scales
 * with the managing {@link NetworkControllerBlockEntity}'s capacity multiplier — config-capped.
 *
 * <p>The output is stored explicitly in the pattern (no recipe-registry lookup), so it crafts any
 * recipe — vanilla or modded — deterministically. Recursive multi-step planning, separate pattern
 * <em>items</em>/hosts, and delegation to AE2/Nerotech crafters are Stage-9 follow-ups; this is the
 * native baseline.
 */
public class AutoCrafterBlockEntity extends AbstractTerminalBlockEntity implements MenuProvider {

    /** Pattern slots 0–8 = 3×3 inputs, slot 9 = output. */
    public static final int PATTERN_SIZE = 10;
    public static final int OUTPUT_INDEX = 9;
    public static final int BUFFER_SIZE = 9;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_IO = 4_000;

    private final SimpleContainer pattern = new SimpleContainer(PATTERN_SIZE);

    public AutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_CRAFTER.get(), pos, state, BUFFER_SIZE, ENERGY_CAPACITY, ENERGY_MAX_IO);
    }

    public SimpleContainer pattern() {
        return this.pattern;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.nerologistics.auto_crafter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new AutoCrafterMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < PATTERN_SIZE; i++) {
            output.store("Pat" + i, ItemStack.OPTIONAL_CODEC, this.pattern.getItem(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < PATTERN_SIZE; i++) {
            this.pattern.setItem(i, input.read("Pat" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoCrafterBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }
        if (!NeroLogisticsConfig.enableAutoCrafting()) {
            return;
        }
        if (level.getGameTime() % NeroLogisticsConfig.craftIntervalTicks() != 0L) {
            return;
        }
        be.runCrafts(level, pos);
    }

    private void runCrafts(Level level, BlockPos pos) {
        ItemStack output = this.pattern.getItem(OUTPUT_INDEX);
        if (output.isEmpty()) {
            return;
        }
        Map<Integer, Demand> demand = demand();
        if (demand.isEmpty()) {
            return;
        }
        ConduitNetwork network = adjacentItemNetwork(level, pos);
        if (network == null) {
            return;
        }
        List<Source> sources = collectSources(level, network);
        if (sources.isEmpty()) {
            return;
        }
        int perCraft = NeroLogisticsConfig.autoCraftEnergyPerCraft();
        int cap = scaledCraftCap(level, network);
        for (int n = 0; n < cap; n++) {
            if (this.energy.getAmount() < perCraft || !bufferHasRoom(output)) {
                break;
            }
            if (!ingredientsAvailable(sources, demand)) {
                break;
            }
            consumeIngredients(sources, demand);
            InventoryTransfer.insert(this, Direction.UP, output, output.getCount());
            this.energy.consume(perCraft);
            setChanged();
        }
    }

    /** Crafts allowed this interval: base config × the managing controller's throughput multiplier. */
    private int scaledCraftCap(Level level, ConduitNetwork network) {
        int base = NeroLogisticsConfig.autoCraftsPerInterval();
        double mult = network.capacityMultiplier(level);
        return Math.max(1, (int) Math.round(base * mult));
    }

    /** Distinct input demands keyed by a representative pattern slot (count = matching input slots). */
    private Map<Integer, Demand> demand() {
        Map<Integer, Demand> map = new HashMap<>();
        for (int i = 0; i < OUTPUT_INDEX; i++) {
            ItemStack in = this.pattern.getItem(i);
            if (in.isEmpty()) {
                continue;
            }
            Integer key = null;
            for (Map.Entry<Integer, Demand> e : map.entrySet()) {
                if (ItemStack.isSameItem(e.getValue().sample, in)) {
                    key = e.getKey();
                    break;
                }
            }
            if (key == null) {
                map.put(i, new Demand(in, 1));
            } else {
                map.get(key).count++;
            }
        }
        return map;
    }

    private boolean ingredientsAvailable(List<Source> sources, Map<Integer, Demand> demand) {
        for (Demand d : demand.values()) {
            int have = 0;
            for (Source s : sources) {
                have += InventoryTransfer.count(s.container, s.side, d.sample);
                if (have >= d.count) {
                    break;
                }
            }
            if (have < d.count) {
                return false;
            }
        }
        return true;
    }

    private void consumeIngredients(List<Source> sources, Map<Integer, Demand> demand) {
        for (Demand d : demand.values()) {
            int need = d.count;
            for (Source s : sources) {
                if (need <= 0) {
                    break;
                }
                need -= InventoryTransfer.extract(s.container, s.side, d.sample, need);
            }
        }
    }

    private boolean bufferHasRoom(ItemStack output) {
        int need = output.getCount();
        int max = Math.min(output.getMaxStackSize(), this.buffer.getMaxStackSize());
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            ItemStack slot = this.buffer.getItem(i);
            if (slot.isEmpty()) {
                need -= max;
            } else if (ItemStack.isSameItemSameComponents(slot, output)) {
                need -= Math.max(0, max - slot.getCount());
            }
            if (need <= 0) {
                return true;
            }
        }
        return need <= 0;
    }

    private List<Source> collectSources(Level level, ConduitNetwork network) {
        Map<BlockPos, Source> map = new HashMap<>();
        for (BlockPos member : network.members()) {
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = member.relative(dir);
                if (network.contains(neighbor) || map.containsKey(neighbor)) {
                    continue;
                }
                Container container = InventoryTransfer.containerAt(level, neighbor);
                if (container == null || container == this) {
                    continue;
                }
                map.put(neighbor.immutable(), new Source(container, dir.getOpposite()));
            }
        }
        return new ArrayList<>(map.values());
    }

    @Nullable
    private ConduitNetwork adjacentItemNetwork(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            BlockEntity be = level.getBlockEntity(neighbor);
            if (be instanceof AbstractConduitBlockEntity conduit && conduit.media().contains(NetworkMedium.ITEM)) {
                ConduitNetwork net = NetworkManager.networkAt(level, NetworkMedium.ITEM, neighbor);
                if (net != null) {
                    return net;
                }
            }
        }
        return null;
    }

    /** A demanded ingredient: a sample stack (for type matching) and how many the pattern needs per craft. */
    private static final class Demand {
        private final ItemStack sample;
        private int count;

        Demand(ItemStack sample, int count) {
            this.sample = sample;
            this.count = count;
        }
    }

    /** An external inventory on the network and the side to access it from. */
    private record Source(Container container, Direction side) {
    }
}
