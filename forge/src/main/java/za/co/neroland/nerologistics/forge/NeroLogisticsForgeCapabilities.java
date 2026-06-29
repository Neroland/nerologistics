package za.co.neroland.nerologistics.forge;

import java.util.EnumMap;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.ForgeEnergyLookup;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.conduit.AbstractTerminalBlockEntity;

/**
 * Forge capability wiring: attaches every NeroLogistics terminal/hub/port to (a) Core's shared
 * {@code nerolandcore:energy} capability (so cables power them) and (b) the standard
 * {@code ITEM_HANDLER} capability (so hoppers, Create, AE2 and pipes move items in/out). Mirrors
 * NeroTech's Forge capability provider; one provider covers every {@link AbstractTerminalBlockEntity}.
 * (NeoForge + Fabric wire the same capabilities in their entry points.)
 */
public final class NeroLogisticsForgeCapabilities {

    private static final Identifier TERMINAL_CAPS =
            Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, "terminal_caps");

    private NeroLogisticsForgeCapabilities() {
    }

    public static void register() {
        AttachCapabilitiesEvent.BlockEntities.BUS.addListener(NeroLogisticsForgeCapabilities::onAttachBlockEntity);
    }

    private static void onAttachBlockEntity(AttachCapabilitiesEvent.BlockEntities event) {
        if (event.getObject() instanceof AbstractTerminalBlockEntity terminal) {
            TerminalProvider provider = new TerminalProvider(terminal);
            event.addCapability(TERMINAL_CAPS, provider);
            event.addListener(provider::invalidate);
        }
    }

    private static final class TerminalProvider implements ICapabilityProvider {

        private final AbstractTerminalBlockEntity terminal;
        private final LazyOptional<NeroEnergyStorage> energy;
        private final EnumMap<Direction, LazyOptional<IItemHandler>> sidedItems = new EnumMap<>(Direction.class);
        @Nullable
        private LazyOptional<IItemHandler> unsidedItems;

        TerminalProvider(AbstractTerminalBlockEntity terminal) {
            this.terminal = terminal;
            this.energy = LazyOptional.of(terminal::getEnergy);
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            if (cap == ForgeEnergyLookup.ENERGY) {
                return this.energy.cast();
            }
            if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                return items(side).cast();
            }
            return LazyOptional.empty();
        }

        private LazyOptional<IItemHandler> items(@Nullable Direction side) {
            if (side == null) {
                if (this.unsidedItems == null) {
                    this.unsidedItems = LazyOptional.of(() -> new InvWrapper(this.terminal));
                }
                return this.unsidedItems;
            }
            return this.sidedItems.computeIfAbsent(side,
                    d -> LazyOptional.of(() -> new SidedInvWrapper(this.terminal, d)));
        }

        void invalidate() {
            this.energy.invalidate();
            if (this.unsidedItems != null) {
                this.unsidedItems.invalidate();
            }
            this.sidedItems.values().forEach(LazyOptional::invalidate);
        }
    }
}
