package za.co.neroland.nerologistics.storage;

//? if <1.20.5 {
/*import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import za.co.neroland.nerologistics.registry.ModDataComponents;

class LegacyStorageCellTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsTaggedItemVariantsSeparateAfterSavingTheCell() {
        ItemStack item = new ItemStack(Items.IRON_PICKAXE);
        ItemStack damaged = item.copy();
        damaged.setDamageValue(1);
        assertNotEquals(ItemKey.of(item), ItemKey.of(damaged));
        assertEquals(ItemKey.of(item), ItemKey.of(item.copyWithCount(2)));

        ItemStack cell = new ItemStack(Items.PAPER);
        var value = ItemCellContents.EMPTY.grown(item, 2).grown(damaged, 5);
        ModDataComponents.ITEM_CELL_CONTENTS.set(cell, value);
        ItemStack loaded = ItemStack.of(cell.save(new CompoundTag()));
        var stored = ModDataComponents.ITEM_CELL_CONTENTS.getOrDefault(loaded, ItemCellContents.EMPTY);
        assertEquals(2, stored.countOf(item));
        assertEquals(5, stored.countOf(damaged));

        ModDataComponents.ITEM_CELL_CONTENTS.remove(loaded);
        assertEquals(0, ModDataComponents.ITEM_CELL_CONTENTS.getOrDefault(loaded, ItemCellContents.EMPTY).total());
    }

    @Test
    void rejectsMalformedCellDataInsteadOfTreatingItAsEmpty() {
        ItemStack cell = new ItemStack(Items.PAPER);
        cell.getOrCreateTag().putString("nerologistics:item_cell_contents", "invalid");
        assertThrows(IllegalStateException.class, () ->
                ModDataComponents.ITEM_CELL_CONTENTS.getOrDefault(cell, ItemCellContents.EMPTY));
    }
}
*///?}
