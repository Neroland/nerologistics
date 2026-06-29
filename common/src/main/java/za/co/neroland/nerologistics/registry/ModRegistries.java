package za.co.neroland.nerologistics.registry;

/**
 * Aggregates NeroLogistics' cross-loader content registries. Called once from
 * {@link za.co.neroland.nerologistics.NeroLogisticsCommon#init()}.
 *
 * <p>Order matters on the eager (Fabric) loader: blocks before items (block items reference their
 * block), block-entities after blocks, then the creative tab. On NeoForge/Forge the DeferredRegisters
 * are created here and flushed to NeroLogistics' mod bus by the loader entry point
 * ({@code *RegistrationFactory.registerAll(...)}).
 */
public final class ModRegistries {

    private ModRegistries() {
    }

    public static void init() {
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModMenuTypes.init();
        ModEntities.init();
        ModCreativeTab.init();
    }
}
