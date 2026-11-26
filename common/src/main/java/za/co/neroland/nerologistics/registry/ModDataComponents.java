package za.co.neroland.nerologistics.registry;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerologistics.storage.CellSettings;
import za.co.neroland.nerologistics.storage.FluidCellContents;
import za.co.neroland.nerologistics.storage.ItemCellContents;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import za.co.neroland.nerologistics.NeroLogisticsCommon;
//?} else {
/*import net.minecraft.nbt.NbtOps;
*///?}

/** Persistent item data for storage cells and wireless terminal bindings. */
public final class ModDataComponents {

    //? if >=1.20.5 {
    private static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroLogisticsCommon.MOD_ID);
    //?}

    public static final StackData<ItemCellContents> ITEM_CELL_CONTENTS = register("item_cell_contents", ItemCellContents.CODEC);
    public static final StackData<FluidCellContents> FLUID_CELL_CONTENTS = register("fluid_cell_contents", FluidCellContents.CODEC);
    public static final StackData<CellSettings> CELL_SETTINGS = register("cell_settings", CellSettings.CODEC);
    public static final StackData<GlobalPos> WIRELESS_TARGET = register("wireless_target", GlobalPos.CODEC);

    private static <T> StackData<T> register(String key, Codec<T> codec) {
        //? if >=1.20.5 {
        var entry = COMPONENTS.register(key, ignored -> DataComponentType.<T>builder().persistent(codec).build());
        return new StackData<>(entry);
        //?} else {
        /*return new StackData<>("nerologistics:" + key, codec);
        *///?}
    }

    //? if >=1.20.5 {
    public record StackData<T>(RegistrationProvider.RegistryEntry<DataComponentType<T>> entry) {
        public @Nullable T get(ItemStack stack) {
            return stack.get(entry.get());
        }

        public void set(ItemStack stack, T value) {
            stack.set(entry.get(), value);
        }

        public void remove(ItemStack stack) {
            stack.remove(entry.get());
        }
    //?} else {
    /*public record StackData<T>(String key, Codec<T> codec) {
        public @Nullable T get(ItemStack stack) {
            var tag = stack.getTag();
            if (tag == null || !tag.contains(key)) {
                return null;
            }
            return codec.parse(NbtOps.INSTANCE, tag.get(key)).result()
                    .orElseThrow(() -> new IllegalStateException("Invalid NeroLogistics item data: " + key));
        }

        public void set(ItemStack stack, T value) {
            var encoded = codec.encodeStart(NbtOps.INSTANCE, value).result()
                    .orElseThrow(() -> new IllegalArgumentException("Cannot encode NeroLogistics item data: " + key));
            stack.getOrCreateTag().put(key, encoded);
        }

        public void remove(ItemStack stack) {
            stack.removeTagKey(key);
        }
    *///?}

        public T getOrDefault(ItemStack stack, T fallback) {
            T current = get(stack);
            return current == null ? fallback : current;
        }
    }

    private ModDataComponents() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}
