package org.sosly.ecotale.items;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.sosly.ecotale.EcoTale;
import org.sosly.ecotale.blocks.BlockRegistry;
import org.sosly.ecotale.entities.EntityRegistry;

public class ItemRegistry {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, EcoTale.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EcoTale.MOD_ID);

    public static final RegistryObject<Item> GUANO = ITEMS.register("guano",
            () -> new GuanoItem(BlockRegistry.GUANO.get(), new Item.Properties()));

    public static final RegistryObject<Item> GUANO_BLOCK = ITEMS.register("guano_block",
            () -> new BlockItem(BlockRegistry.GUANO_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<Item> STONE_ROOST = ITEMS.register("stone_roost",
            () -> new BlockItem(BlockRegistry.STONE_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> DEEPSLATE_ROOST = ITEMS.register("deepslate_roost",
            () -> new BlockItem(BlockRegistry.DEEPSLATE_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> BLACKSTONE_ROOST = ITEMS.register("blackstone_roost",
            () -> new BlockItem(BlockRegistry.BLACKSTONE_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> BASALT_ROOST = ITEMS.register("basalt_roost",
            () -> new BlockItem(BlockRegistry.BASALT_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> TUFF_ROOST = ITEMS.register("tuff_roost",
            () -> new BlockItem(BlockRegistry.TUFF_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> CALCITE_ROOST = ITEMS.register("calcite_roost",
            () -> new BlockItem(BlockRegistry.CALCITE_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> DRIPSTONE_ROOST = ITEMS.register("dripstone_roost",
            () -> new BlockItem(BlockRegistry.DRIPSTONE_ROOST.get(), new Item.Properties()));

    public static final RegistryObject<Item> BAT_SPAWN_EGG = ITEMS.register("bat_spawn_egg",
            () -> new ForgeSpawnEggItem(EntityRegistry.BAT, 0x4C3E30, 0x0F0F0F, new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> ECOTALE_TAB = CREATIVE_TABS.register("ecotale",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecotale"))
                    .icon(() -> new ItemStack(GUANO.get()))
                    .displayItems((params, output) -> {
                        output.accept(GUANO.get());
                        output.accept(GUANO_BLOCK.get());
                        output.accept(STONE_ROOST.get());
                        output.accept(DEEPSLATE_ROOST.get());
                        output.accept(BLACKSTONE_ROOST.get());
                        output.accept(BASALT_ROOST.get());
                        output.accept(TUFF_ROOST.get());
                        output.accept(CALCITE_ROOST.get());
                        output.accept(DRIPSTONE_ROOST.get());
                        output.accept(BAT_SPAWN_EGG.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        CREATIVE_TABS.register(eventBus);
    }
}
