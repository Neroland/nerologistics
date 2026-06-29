package za.co.neroland.nerologistics.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerolandcore.storage.CreativeItemStoreBlockEntity;

import za.co.neroland.nerologistics.NeroLogisticsCommon;
import za.co.neroland.nerologistics.entity.DeliveryDroneEntity;
import za.co.neroland.nerologistics.registry.ModBlocks;

/**
 * {@code /nerologistics gallery} — builds a creative-only showcase of every NeroLogistics block in
 * front of the player, plus a few <b>live</b> demo lines that actually move resources (powered by
 * Neroland Core's creative source/sink blocks). {@code /nerologistics gallery clear} removes it again.
 * Mirrors {@code /nerospace gallery}. Registered per loader via the loader command-registration event.
 */
public final class NeroLogisticsCommands {

    /** The nine blocks shown, in display order, with their lang keys for labels. */
    private static final String[] SHOWCASE = {
            "item_duct", "fluid_duct", "energy_cable",
            "wireless_cargo_terminal", "storage_request_terminal", "train_cargo_interface",
            "drone_hub", "rocket_cargo_port", "logistics_dashboard"
    };

    /** Blocks between adjacent displays (5 empty blocks of breathing room → a 6-block stride). */
    private static final int SPACING = 6;

    private NeroLogisticsCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("nerologistics")
                        .requires(src -> src.getPlayer() != null)
                        .then(Commands.literal("gallery")
                                .executes(ctx -> runSafely(ctx.getSource(), "gallery",
                                        () -> buildGallery(ctx.getSource())))
                                .then(Commands.literal("clear")
                                        .executes(ctx -> runSafely(ctx.getSource(), "gallery clear",
                                                () -> clearGallery(ctx.getSource()))))));
    }

    // ---------------------------------------------------------------------------------------------

    private static int buildGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.literal("The NeroLogistics gallery is creative-only."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int bx = origin.getX() + 2;
        int bz = origin.getZ() + 2;
        int fy = origin.getY();

        int spanX = SPACING * (SHOWCASE.length - 1); // width of the showcase row
        BlockState floor = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int gx = -1; gx <= spanX + 1; gx++) {
            for (int gz = -1; gz <= 21; gz++) {
                level.setBlockAndUpdate(new BlockPos(bx + gx, fy, bz + gz), floor);
            }
        }

        // --- Showcase row: one of every block, floating, ~5 blocks apart, each with a name label ---
        for (int i = 0; i < SHOWCASE.length; i++) {
            int x = bx + i * SPACING;
            level.setBlockAndUpdate(new BlockPos(x, fy + 2, bz), ourBlock(SHOWCASE[i]).defaultBlockState());
            label(level, new BlockPos(x, fy + 3, bz),
                    Component.translatable("block.nerologistics." + SHOWCASE[i]));
        }

        // --- Live demo lines, each on its own row 5 blocks apart along +Z ---
        // ENERGY (automatic): creative_battery -> energy_cable x3 -> battery (right-click the battery).
        demoLine(level, bx, fy, bz + 4, "creative_battery", "energy_cable", "battery");
        label(level, new BlockPos(bx, fy + 3, bz + 4), Component.literal("Energy — live (read the battery)"));

        // ITEMS (automatic — source preloaded with cobblestone).
        BlockPos itemSrc = demoLine(level, bx, fy, bz + 9, "creative_item_store", "item_duct", "item_store");
        preloadItemStore(level, itemSrc, new ItemStack(Items.COBBLESTONE));
        label(level, new BlockPos(bx, fy + 3, bz + 9), Component.literal("Items — live"));

        // FLUID (one-click: right-click the creative tank with a water bucket).
        demoLine(level, bx, fy, bz + 14, "creative_fluid_tank", "fluid_duct", "fluid_tank");
        label(level, new BlockPos(bx, fy + 3, bz + 14),
                Component.literal("Fluid — right-click source with a water bucket"));

        // STORAGE TERMINAL (automatic — stocks from the network).
        BlockPos stoSrc = new BlockPos(bx, fy + 1, bz + 19);
        level.setBlockAndUpdate(stoSrc, coreBlock("creative_item_store").defaultBlockState());
        preloadItemStore(level, stoSrc, new ItemStack(Items.IRON_INGOT));
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 1, bz + 19), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 19), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 20),
                ModBlocks.STORAGE_REQUEST_TERMINAL.get().defaultBlockState());
        label(level, new BlockPos(bx, fy + 3, bz + 19), Component.literal("Storage Terminal — live (right-click it)"));

        source.sendSuccess(() -> Component.literal(
                "Built the NeroLogistics gallery. Energy/Items/Storage run now; flick the fluid source with a "
                + "water bucket. See the wiki 'Demo & Testing' page for wireless, drones and cargo ports."), false);
        return 1;
    }

    /** A source → conduit ×3 → sink line at y+1 starting at (x, z), returning the source position. */
    private static BlockPos demoLine(ServerLevel level, int x, int fy, int z, String source, String conduit,
            String sink) {
        BlockPos src = new BlockPos(x, fy + 1, z);
        level.setBlockAndUpdate(src, coreBlock(source).defaultBlockState());
        for (int i = 1; i <= 3; i++) {
            level.setBlockAndUpdate(new BlockPos(x + i, fy + 1, z), ourBlock(conduit).defaultBlockState());
        }
        level.setBlockAndUpdate(new BlockPos(x + 4, fy + 1, z), coreBlock(sink).defaultBlockState());
        return src;
    }

    private static void preloadItemStore(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CreativeItemStoreBlockEntity store) {
            store.setSource(stack);
        }
    }

    private static int clearGallery(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Run this as a player."));
            return 0;
        }
        if (!player.getAbilities().instabuild) {
            source.sendFailure(Component.literal("The NeroLogistics gallery is creative-only."));
            return 0;
        }
        ServerLevel level = player.level();
        BlockPos origin = player.blockPosition();
        int bx = origin.getX() + 2;
        int bz = origin.getZ() + 2;
        int fy = origin.getY();
        int spanX = SPACING * (SHOWCASE.length - 1);
        AABB box = new AABB(bx - 1, fy, bz - 1, bx + spanX + 2, fy + 4, bz + 22);

        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            stand.discard();
        }
        for (DeliveryDroneEntity drone : level.getEntitiesOfClass(DeliveryDroneEntity.class, box.inflate(64))) {
            drone.discard();
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (int gx = -1; gx <= spanX + 1; gx++) {
            for (int gy = 0; gy <= 4; gy++) {
                for (int gz = -1; gz <= 21; gz++) {
                    BlockPos pos = new BlockPos(bx + gx, fy + gy, bz + gz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, air);
                        cleared++;
                    }
                }
            }
        }
        int total = cleared;
        source.sendSuccess(() -> Component.literal("Cleared the NeroLogistics gallery (" + total + " blocks)."),
                false);
        return 1;
    }

    // ---------------------------------------------------------------------------------------------

    private static void label(ServerLevel level, BlockPos pos, Component name) {
        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        stand.setCustomName(name);
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        level.addFreshEntity(stand);
    }

    private static Block ourBlock(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, name));
    }

    private static Block coreBlock(String name) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath("nerolandcore", name));
    }

    private static int runSafely(CommandSourceStack source, String name, CommandBody body) {
        try {
            return body.run();
        } catch (RuntimeException ex) {
            NeroLogisticsCommon.LOGGER.error("[NeroLogistics] /nerologistics {} failed", name, ex);
            source.sendFailure(Component.literal("NeroLogistics " + name + " failed; see latest.log."));
            return 0;
        }
    }

    @FunctionalInterface
    private interface CommandBody {
        int run();
    }
}
