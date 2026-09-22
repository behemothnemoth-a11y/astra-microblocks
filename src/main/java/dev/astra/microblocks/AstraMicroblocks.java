package dev.astra.microblocks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class AstraMicroblocks
        implements ModInitializer {

    public static final String MOD_ID =
            "astra_microblocks";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(
                MOD_ID,
                path
        );
    }

    private static final ResourceKey<Block>
            TEST_HOST_KEY =
            ResourceKey.create(
                    BuiltInRegistries.BLOCK.key(),
                    id("test_host")
            );

    private static final ResourceKey<Item>
            TEST_HOST_ITEM_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ITEM.key(),
                    id("test_host")
            );

    private static final ResourceKey<Item>
            ASTRA_CHISEL_KEY =
            ResourceKey.create(
                    BuiltInRegistries.ITEM.key(),
                    id("astra_chisel")
            );

    public static final TestHostBlock TEST_HOST =
            new TestHostBlock(
                    BlockBehaviour.Properties
                            .ofFullCopy(Blocks.STONE)
                            .noOcclusion()
                            .dynamicShape()
                            .setId(TEST_HOST_KEY)
            );

    public static final AstraChiselItem ASTRA_CHISEL =
            new AstraChiselItem(
                    new Item.Properties()
                            .setId(ASTRA_CHISEL_KEY)
            );

    public static BlockEntityType<TestHostBlockEntity>
            TEST_HOST_ENTITY;

    @Override
    public void onInitialize() {

        Registry.register(
                BuiltInRegistries.BLOCK,
                TEST_HOST_KEY,
                TEST_HOST
        );

        Registry.register(
                BuiltInRegistries.ITEM,
                TEST_HOST_ITEM_KEY,
                new BlockItem(
                        TEST_HOST,
                        new Item.Properties()
                                .useBlockDescriptionPrefix()
                                .setId(TEST_HOST_ITEM_KEY)
                )
        );

        Registry.register(
                BuiltInRegistries.ITEM,
                ASTRA_CHISEL_KEY,
                ASTRA_CHISEL
        );

        TEST_HOST_ENTITY =
                Registry.register(
                        BuiltInRegistries.BLOCK_ENTITY_TYPE,
                        id("test_host"),
                        FabricBlockEntityTypeBuilder
                                .create(
                                        TestHostBlockEntity::new,
                                        TEST_HOST
                                )
                                .build()
                );

        if (Boolean.getBoolean(
                "astra.lifecycleTest"
        )) {
            LifecycleTest.register();
        }

        System.out.println(
                "Astra Microblocks initialized on Minecraft 26.2"
        );
    }
}
