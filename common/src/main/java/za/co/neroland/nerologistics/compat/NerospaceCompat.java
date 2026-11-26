package za.co.neroland.nerologistics.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.platform.FluidLookup;
import za.co.neroland.nerolandcore.platform.Services;
import za.co.neroland.nerologistics.compat.nerospace.NerospacePipeEndpoints;
import za.co.neroland.nerologistics.network.NetworkMedium;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.compat.nerospace.NerospaceRouteProvider;

/**
 * Soft-dependency seam for <b>Nerospace</b>. Called once from {@code NeroLogisticsCommon.init()}: when
 * Nerospace is loaded (Core's {@code Services.PLATFORM.isModLoaded}), it binds the
 * {@link NerospaceRouteProvider} — a compile-time ({@code compileOnly}) binding against Nerospace's
 * semver-stable {@code za.co.neroland.nerospace.api.route} surface (1.3.0+), so rocket cargo becomes a real
 * Nerospace flight. When Nerospace is absent nothing in {@code compat.nerospace} is ever touched, so the JVM
 * never loads a Nerospace class and the stub route provider keeps its every-loaded-dimension behaviour.
 *
 * <p>A Nerospace too old to have the route API surfaces as a {@link LinkageError} on the first touch; that
 * is logged once and the stub stays active. The server config {@code nerospaceRouting=false} keeps the stub
 * even with a compatible Nerospace (see {@code RouteProviders.get()}).</p>
 */
public final class NerospaceCompat {

    /** Nerospace's mod id, as declared optional in all three loader manifests. */
    public static final String NEROSPACE_MOD_ID = "nerospace";

    private static boolean pipeIntegration;

    public static boolean isPipe(@Nullable BlockEntity entity) {
        return pipeIntegration && NerospacePipeEndpoints.isPipe(entity);
    }

    public static boolean connects(BlockEntity entity, Direction side, NetworkMedium medium) {
        return NerospacePipeEndpoints.connects(entity, side, medium);
    }

    public static boolean allowsItem(Object endpoint, Direction side, ItemStack stack, boolean insert) {
        return !(endpoint instanceof BlockEntity entity) || !isPipe(entity)
                || NerospacePipeEndpoints.allowsItem(entity, side, stack, insert);
    }

    @Nullable
    public static NeroEnergyStorage energy(Level level, BlockPos pos, Direction side, boolean insert) {
        BlockEntity entity = pipeIntegration ? level.getBlockEntity(pos) : null;
        if (entity != null && isPipe(entity)) {
            return NerospacePipeEndpoints.energy(entity, side, insert);
        }
        return EnergyLookup.INSTANCE.find(level, pos, side);
    }

    @Nullable
    public static NeroFluidStorage fluid(Level level, BlockPos pos, Direction side, boolean insert) {
        BlockEntity entity = pipeIntegration ? level.getBlockEntity(pos) : null;
        if (entity != null && isPipe(entity)) {
            return NerospacePipeEndpoints.fluid(entity, side, insert);
        }
        return FluidLookup.INSTANCE.find(level, pos, side);
    }

    private NerospaceCompat() {
    }

    /** Bind the Nerospace route provider if Nerospace is present; otherwise leave the stub in place. */
    public static void init() {
        if (!Services.PLATFORM.isModLoaded(NEROSPACE_MOD_ID)) {
            NeroLogisticsCommon.LOGGER.info(
                    "[NeroLogistics] Nerospace absent — stub route provider (every loaded dimension is a "
                    + "destination)");
            return;
        }
        try {
            // Static call into a separate class: nothing Nerospace-typed is resolved until this line runs.
            NerospaceRouteProvider.install();
            pipeIntegration = true;
            NeroLogisticsCommon.LOGGER.info(
                    "[NeroLogistics] Nerospace detected — rocket cargo flies as Nerospace cargo flights "
                    + "(route API); set nerospaceRouting=false to keep the stub");
        } catch (LinkageError e) {
            NeroLogisticsCommon.LOGGER.warn(
                    "[NeroLogistics] Nerospace is loaded but predates its route API (needs 1.3.0+; {}); "
                    + "keeping the stub route provider", e.getClass().getSimpleName());
        }
    }
}
