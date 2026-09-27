package za.co.neroland.nerologistics.item;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerologistics.conduit.AbstractConduitBlockEntity;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlock;
import za.co.neroland.nerologistics.conduit.RocketCargoPortBlockEntity;

/**
 * The Configurator — a wrench-style tool for conduits. Right-click a conduit face to cycle that face's
 * mode (DISABLED → INPUT → OUTPUT → IO); sneak-right-click to read the current mode. On a Rocket Cargo
 * Port, right-click cycles its shipping class (handled by the port block) and sneak-right-click cycles its
 * launch schedule, then prints the port's status. A face's mode
 * decides how it interacts with the <em>external</em> block on it: INPUT pulls into the network, OUTPUT
 * pushes out, IO both, DISABLED ignores it.
 */
public class ConfiguratorItem extends Item {

    /** The cycle order applied by a normal right-click. */
    private static final SideMode[] CYCLE = {SideMode.DISABLED, SideMode.INPUT, SideMode.OUTPUT, SideMode.IO};

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (context.isSecondaryUseActive()
                && level.getBlockEntity(context.getClickedPos()) instanceof RocketCargoPortBlockEntity port) {
            // Sneak-use on a rocket cargo port cycles its launch schedule and prints its status (vanilla
            // skips block use while sneaking with an item in hand, so the port block never sees this).
            if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
                RocketCargoPortBlock.cycleSchedule(serverLevel, player, port);
            }
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof AbstractConduitBlockEntity conduit)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        if (context.isSecondaryUseActive()) {
            tell(context, Component.translatable("item.nerologistics.configurator.read",
                    face.getName(), conduit.faceMode(face).name()));
            return InteractionResult.SUCCESS;
        }
        SideMode next = nextMode(conduit.faceMode(face));
        conduit.setFaceMode(face, next);
        tell(context, Component.translatable("item.nerologistics.configurator.set", face.getName(), next.name()));
        return InteractionResult.SUCCESS;
    }

    private static SideMode nextMode(SideMode current) {
        int idx = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                idx = i;
                break;
            }
        }
        return CYCLE[(idx + 1) % CYCLE.length];
    }

    private static void tell(UseOnContext context, Component message) {
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(message);
        }
    }
}
