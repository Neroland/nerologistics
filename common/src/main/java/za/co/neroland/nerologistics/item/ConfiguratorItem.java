package za.co.neroland.nerologistics.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerolandcore.sideconfig.ConfiguratorTool;

public class ConfiguratorItem extends Item implements ConfiguratorTool {

    public ConfiguratorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = ConfiguratorTool.useTarget(context);
        return result == InteractionResult.PASS ? ConfiguratorTool.useMachine(context) : result;
    }
}
