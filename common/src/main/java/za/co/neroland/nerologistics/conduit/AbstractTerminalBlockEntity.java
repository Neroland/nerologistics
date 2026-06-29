package za.co.neroland.nerologistics.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * Shared base for Stage-3 terminal/interface blocks (wireless cargo terminal, storage request terminal,
 * Create train interface). Each holds a small item {@link SimpleContainer} buffer (so vanilla
 * hoppers/Create and NeroLogistics ducts move items in and out — they all see a {@link Container}) and a
 * Core {@link EnergyBuffer} (cables power it via Core's energy capability, registered per loader). The
 * buffer is exposed by delegating the {@link Container} contract to the inner {@link SimpleContainer}.
 */
public abstract class AbstractTerminalBlockEntity extends BlockEntity implements WorldlyContainer {

    protected final SimpleContainer buffer;
    protected final EnergyBuffer energy;

    protected AbstractTerminalBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int bufferSize, int energyCapacity, int energyMaxIo) {
        super(type, pos, state);
        this.buffer = new SimpleContainer(bufferSize);
        this.energy = new EnergyBuffer(energyCapacity, energyMaxIo, energyMaxIo, this::setChanged);
    }

    public NeroEnergyStorage getEnergy() {
        return this.energy;
    }

    /** Mutable energy buffer for charging/consuming (generate/consume bypass the per-side I/O cap). */
    public EnergyBuffer energyBuffer() {
        return this.energy;
    }

    public SimpleContainer buffer() {
        return this.buffer;
    }

    // --- Container (delegate to the inner SimpleContainer) ------------------

    @Override
    public int getContainerSize() {
        return this.buffer.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.buffer.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.buffer.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = this.buffer.removeItem(slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return this.buffer.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.buffer.setItem(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level == null || this.level.getBlockEntity(this.worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + 0.5D, this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.buffer.clearContent();
    }

    // --- WorldlyContainer: every face can insert/extract every slot ---------

    @Override
    public int[] getSlotsForFace(Direction side) {
        int[] slots = new int[this.buffer.getContainerSize()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @org.jetbrains.annotations.Nullable Direction side) {
        return true;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Energy", this.energy.getRaw());
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            output.store("Buf" + i, ItemStack.OPTIONAL_CODEC, this.buffer.getItem(i));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.energy.setRaw(input.getIntOr("Energy", 0));
        for (int i = 0; i < this.buffer.getContainerSize(); i++) {
            this.buffer.setItem(i, input.read("Buf" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        }
    }
}
