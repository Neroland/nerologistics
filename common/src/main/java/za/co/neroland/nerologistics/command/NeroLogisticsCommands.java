package za.co.neroland.nerologistics.command;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
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
import za.co.neroland.nerologistics.registry.ModItems;
import za.co.neroland.nerologistics.storage.DriveBayBlockEntity;

/**
 * {@code /nerologistics gallery} — builds a creative-only showcase of every NeroLogistics block in
 * front of the player, each with a one-line usage hint under its name, plus <b>live</b> demo lines
 * for every network (energy, items, fluid, the digital storage network and the logistics processor)
 * that actually move resources (powered by Neroland Core's creative source/sink blocks).
 * {@code /nerologistics gallery clear} removes it again.
 * Mirrors {@code /nerospace gallery}. Registered per loader via the loader command-registration event.
 */
public final class NeroLogisticsCommands {

    /** Every NeroLogistics block, in display order (redesign blocks first, then the legacy line-up). */
    private static final String[] SHOWCASE = {
            // Stage 7–16 redesign
            "network_controller", "network_module",
            "universal_duct", "item_storage",
            "drive_bay", "storage_terminal", "logistics_processor",
            "auto_crafter", "buffer",
            "drone_port", "train_station",
            // Legacy (Stage 1–5)
            "item_duct", "fluid_duct", "energy_cable",
            "wireless_cargo_terminal", "storage_request_terminal", "train_cargo_interface",
            "drone_hub", "rocket_cargo_port", "logistics_dashboard"
    };

    /** One-line "how to use it" hint per {@link #SHOWCASE} entry (same order). */
    private static final String[] SHOWCASE_USAGE = {
            "Governs a network — stack Network Modules beside it for capacity",
            "Place beside a Controller — each module raises throughput",
            "One conduit for items + fluids — Configurator sets per-face modes",
            "54-slot warehouse — the network (and storage index) reads it",
            "Holds 6 storage cells — the network's digital storage",
            "Search, sort and pull anything on the network — put it on a duct",
            "Open it: rules keep stocked / export excess / ship above",
            "Ghost a 3×3 recipe — crafts from network stock, needs energy",
            "Keep-stocked or passive cache — set a target level in its GUI",
            "Name it, add Drone items — flies cargo to the matching port",
            "Bulk same-dimension haul between two named stations",
            "Item-only conduit (legacy) — 9-slot whitelist per duct",
            "Fluid-only conduit (legacy)",
            "Moves Neroland Energy between machines",
            "Batched wireless item transfer on a named channel",
            "Pulls filtered items into its buffer (legacy request flow)",
            "Passive chest bridge for Create-style trains",
            "Links docked drones to wireless channels",
            "Cross-dimension shipping — Configurator cycles Express/Bulk",
            "Right-click for live network stats"
    };

    /** The non-block item components, shown in floating item frames alongside the block row. */
    private static final String[] ITEM_SHOWCASE = {
            "drone", "hyperspeed_card", "configurator",
            "wireless_terminal", "item_cell_64k", "fluid_cell_128b"
    };

    /** One-line usage hint per {@link #ITEM_SHOWCASE} entry (same order). */
    private static final String[] ITEM_SHOWCASE_USAGE = {
            "Load into a Drone Port — each drone is a parallel lane",
            "Drone Port upgrade — instant, unrendered transfer",
            "Wrench: cycles duct face modes and shipping class",
            "Sneak-click a Controller to bind, then use anywhere in range",
            "Item storage for the Drive Bay — sneak-use to partition/prioritise",
            "Fluid storage for the Drive Bay"
    };

    /** Blocks between adjacent displays (5 empty blocks of breathing room → a 6-block stride). */
    private static final int SPACING = 6;

    /** Depth (+Z) of the gallery floor/clear region — covers all demo rows. */
    private static final int DEPTH = 31;

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
            for (int gz = -1; gz <= DEPTH; gz++) {
                level.setBlockAndUpdate(new BlockPos(bx + gx, fy, bz + gz), floor);
            }
        }

        // --- Showcase row: one of every block, floating, ~5 blocks apart, with name + usage hint ---
        for (int i = 0; i < SHOWCASE.length; i++) {
            int x = bx + i * SPACING;
            level.setBlockAndUpdate(new BlockPos(x, fy + 2, bz), ourBlock(SHOWCASE[i]).defaultBlockState());
            hologram(level, x + 0.5, fy + 3.4, bz + 0.5,
                    Component.translatable("block.nerologistics." + SHOWCASE[i]), SHOWCASE_USAGE[i]);
        }

        // --- Item components: floating item frames on plinths, a short row in front of the blocks ---
        for (int i = 0; i < ITEM_SHOWCASE.length; i++) {
            int x = bx + i * SPACING;
            itemDisplay(level, new BlockPos(x, fy + 1, bz + 2), ITEM_SHOWCASE[i],
                    Component.translatable("item.nerologistics." + ITEM_SHOWCASE[i]),
                    ITEM_SHOWCASE_USAGE[i]);
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

        // REQUEST TERMINAL (legacy — pulls filtered items into its buffer).
        BlockPos stoSrc = new BlockPos(bx, fy + 1, bz + 19);
        level.setBlockAndUpdate(stoSrc, coreBlock("creative_item_store").defaultBlockState());
        preloadItemStore(level, stoSrc, new ItemStack(Items.IRON_INGOT));
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 1, bz + 19), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 19), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 20),
                ModBlocks.STORAGE_REQUEST_TERMINAL.get().defaultBlockState());
        label(level, new BlockPos(bx, fy + 3, bz + 19),
                Component.literal("Request Terminal (legacy) — pulls filtered items into its buffer"));

        // STORAGE NETWORK (live — a Drive Bay with a preloaded cell plus a Storage Terminal).
        BlockPos netSrc = new BlockPos(bx, fy + 1, bz + 24);
        level.setBlockAndUpdate(netSrc, coreBlock("creative_item_store").defaultBlockState());
        preloadItemStore(level, netSrc, new ItemStack(Items.GOLD_INGOT));
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 1, bz + 24), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 24), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 3, fy + 1, bz + 24), ModBlocks.DRIVE_BAY.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 25),
                ModBlocks.STORAGE_TERMINAL.get().defaultBlockState());
        if (level.getBlockEntity(new BlockPos(bx + 3, fy + 1, bz + 24)) instanceof DriveBayBlockEntity drive) {
            drive.bays().setItem(0, new ItemStack(ModItems.ITEM_CELL_8K.get()));
        }
        label(level, new BlockPos(bx, fy + 3, bz + 24),
                Component.literal("Storage network — cells live in the Drive Bay; right-click the Storage Terminal"));

        // LOGISTICS PROCESSOR (open it to add rules — the chest is its adjacent target).
        BlockPos ruleSrc = new BlockPos(bx, fy + 1, bz + 29);
        level.setBlockAndUpdate(ruleSrc, coreBlock("creative_item_store").defaultBlockState());
        preloadItemStore(level, ruleSrc, new ItemStack(Items.BREAD));
        level.setBlockAndUpdate(new BlockPos(bx + 1, fy + 1, bz + 29), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 2, fy + 1, bz + 29), ModBlocks.ITEM_DUCT.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 3, fy + 1, bz + 29),
                ModBlocks.LOGISTICS_PROCESSOR.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(bx + 3, fy + 1, bz + 30), Blocks.CHEST.defaultBlockState());
        label(level, new BlockPos(bx, fy + 3, bz + 29),
                Component.literal("Logistics Processor — open it, ghost bread, pick Keep Stocked: it fills the chest"));

        source.sendSuccess(() -> Component.literal(
                "Built the NeroLogistics gallery: every block with a usage hint under its name, the item "
                + "components, and live lines for each network — Energy, Items, Fluid (right-click the "
                + "source with a water bucket), the legacy Request Terminal, the Storage network "
                + "(Drive Bay + Storage Terminal) and the Logistics Processor."), false);
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

    /** A plinth with a floating (frameless) item frame on top showing {@code itemName}, plus a label. */
    private static void itemDisplay(ServerLevel level, BlockPos plinth, String itemName, Component name,
            String usage) {
        level.setBlockAndUpdate(plinth, Blocks.SMOOTH_STONE.defaultBlockState());
        Item item = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath(NeroLogisticsCommon.MOD_ID, itemName));
        ItemFrame frame = new ItemFrame(level, plinth.above(), Direction.UP);
        frame.setInvisible(true);
        frame.setInvulnerable(true);
        frame.setItem(new ItemStack(item));
        level.addFreshEntity(frame);
        hologram(level, plinth.getX() + 0.5, plinth.getY() + 2.2, plinth.getZ() + 0.5, name, usage);
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
        AABB box = new AABB(bx - 1, fy, bz - 1, bx + spanX + 2, fy + 4, bz + DEPTH + 1);

        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            stand.discard(); // legacy galleries built before the text_display switch
        }
        for (Display.TextDisplay display : level.getEntitiesOfClass(Display.TextDisplay.class, box)) {
            display.discard();
        }
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
            frame.discard();
        }
        for (DeliveryDroneEntity drone : level.getEntitiesOfClass(DeliveryDroneEntity.class, box.inflate(64))) {
            drone.discard();
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (int gx = -1; gx <= spanX + 1; gx++) {
            for (int gy = 0; gy <= 4; gy++) {
                for (int gz = -1; gz <= DEPTH; gz++) {
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
        hologram(level, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, name, null);
    }

    /**
     * One {@code minecraft:text_display} hologram (billboard {@code center}) carrying the name and,
     * when given, a gray usage line below it. A single cached-quad display entity per exhibit is
     * drastically cheaper to render than the armor-stand name tags used previously: every visible
     * name tag is a full LivingEntity plus a two-pass (normal + seethrough) per-frame text draw,
     * and ~60 of them tanked the client to 12 FPS (GPU idle, render thread CPU-bound). TextDisplay
     * setters are private in 26.x, so the entity is built via its NBT load path.
     */
    private static void hologram(ServerLevel level, double x, double y, double z,
            Component name, String usage) {
        Component text = usage == null ? name
                : Component.empty().append(name)
                        .append(Component.literal("\n"))
                        .append(Component.literal(usage).withStyle(ChatFormatting.GRAY));
        CompoundTag tag = new CompoundTag();
        ComponentSerialization.CODEC
                .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text)
                .result().ifPresent(encoded -> tag.put("text", encoded));
        tag.putString("billboard", "center");
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath("minecraft", "text_display"));
        Entity display = EntityType.loadEntityRecursive(type, tag, level, EntitySpawnReason.COMMAND,
                entity -> {
                    entity.setPos(x, y, z);
                    return entity;
                });
        if (display != null) {
            level.addFreshEntity(display);
        }
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
