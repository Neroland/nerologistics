package za.co.neroland.nerologistics.compat.nerospace;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerologistics.network.NetworkMedium;
import za.co.neroland.nerospace.pipe.PipeResourceType;
import za.co.neroland.nerospace.pipe.UniversalPipeBlockEntity;

public final class NerospacePipeEndpoints {
    private NerospacePipeEndpoints() {
    }

    public static boolean isPipe(@Nullable BlockEntity entity) {
        return entity instanceof UniversalPipeBlockEntity;
    }

    public static boolean connects(BlockEntity entity, Direction side, NetworkMedium medium) {
        return ((UniversalPipeBlockEntity) entity).mode(side, resource(medium)).isConnected();
    }

    public static boolean allowsItem(BlockEntity entity, Direction side, ItemStack stack, boolean insert) {
        var pipe = (UniversalPipeBlockEntity) entity;
        var mode = pipe.mode(side, PipeResourceType.ITEM);
        return (insert ? mode.canPull() : mode.canPush()) && pipe.filter(side).test(stack);
    }

    public static NeroEnergyStorage energy(BlockEntity entity, Direction side, boolean insert) {
        var pipe = (UniversalPipeBlockEntity) entity;
        var mode = pipe.mode(side, PipeResourceType.ENERGY);
        return (insert ? mode.canPull() : mode.canPush()) ? pipe.getEnergy() : null;
    }

    public static NeroFluidStorage fluid(BlockEntity entity, Direction side, boolean insert) {
        var pipe = (UniversalPipeBlockEntity) entity;
        var mode = pipe.mode(side, PipeResourceType.FLUID);
        if (!(insert ? mode.canPull() : mode.canPush())) {
            return null;
        }
        var storage = pipe.getFluidTank();
        return new NeroFluidStorage() {
            @Override
            public Fluid getFluid() {
                return storage.getFluid();
            }

            @Override
            public long getAmount() {
                return storage.getAmount();
            }

            @Override
            public long getCapacity() {
                return storage.getCapacity();
            }

            @Override
            public long fill(Fluid fluid, long amount, boolean simulate) {
                return storage.fill(fluid, amount, simulate);
            }

            @Override
            public long drain(long amount, boolean simulate) {
                return storage.drain(amount, simulate);
            }
        };
    }

    private static PipeResourceType resource(NetworkMedium medium) {
        return switch (medium) {
            case ITEM -> PipeResourceType.ITEM;
            case FLUID -> PipeResourceType.FLUID;
            case ENERGY -> PipeResourceType.ENERGY;
        };
    }
}
