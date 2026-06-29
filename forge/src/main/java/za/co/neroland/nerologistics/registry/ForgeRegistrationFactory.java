package za.co.neroland.nerologistics.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Forge {@link RegistrationProvider.Factory}: each provider wraps a Forge
 * {@link DeferredRegister}. Registers are collected as created (during
 * {@code NeroLogisticsCommon.init()}) and attached to NeroLogistics' mod bus group by
 * the loader entry point via {@link #registerAll(BusGroup)}. Registered via
 * {@code META-INF/services/za.co.neroland.nerologistics.registry.RegistrationProvider$Factory}.
 */
public final class ForgeRegistrationFactory implements RegistrationProvider.Factory {

    private static final List<DeferredRegister<?>> REGISTERS = new ArrayList<>();

    /** Attach every DeferredRegister created so far to the mod bus group. */
    public static void registerAll(BusGroup modBusGroup) {
        REGISTERS.forEach(register -> register.register(modBusGroup));
    }

    @Override
    public <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        DeferredRegister<T> register = DeferredRegister.create(registryKey, modId);
        REGISTERS.add(register);
        return new Provider<>(register, registryKey, modId);
    }

    private static final class Provider<T> implements RegistrationProvider<T> {

        private final DeferredRegister<T> register;
        private final ResourceKey<? extends Registry<T>> registryKey;
        private final String modId;

        Provider(DeferredRegister<T> register, ResourceKey<? extends Registry<T>> registryKey, String modId) {
            this.register = register;
            this.registryKey = registryKey;
            this.modId = modId;
        }

        @Override
        public <I extends T> RegistryEntry<I> register(String name, Function<ResourceKey<T>, I> factory) {
            Identifier id = Identifier.fromNamespaceAndPath(modId, name);
            ResourceKey<T> key = ResourceKey.create(registryKey, id);
            Supplier<I> supplier = () -> factory.apply(key);
            RegistryObject<I> holder = register.register(name, supplier);
            return new RegistryEntry<>() {
                @Override
                public I get() {
                    return holder.get();
                }

                @Override
                public Identifier id() {
                    return id;
                }
            };
        }
    }
}
