package org.sosly.ecotale.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.BlockRegistry;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EcoTale.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EcoTale.MOD_ID);

    public static final RegistryObject<Item> GUANO = ITEMS.register("guano",
            () -> new GuanoItem(BlockRegistry.GUANO.get(), new Item.Properties()));

    public static final RegistryObject<Item> GUANO_BLOCK = ITEMS.register("guano_block",
            () -> new BlockItem(BlockRegistry.GUANO_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> ECOTALE_TAB = CREATIVE_TABS.register("ecotale",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecotale"))
                    .icon(() -> new ItemStack(GUANO.get()))
                    .displayItems((params, output) -> {
                        output.accept(GUANO.get());
                        output.accept(GUANO_BLOCK.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
}
