package com.sulfurphysics;

import com.sulfurphysics.item.PhysicsGunItem;
import com.sulfurphysics.item.TestCubeSpawnerItem;
import com.sulfurphysics.physics.OdePhysicsWorld;
import com.sulfurphysics.physics.PhysicsDataKeys;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class SulfurPhysics implements ModInitializer {
	public static final String MOD_ID = "sulfur-physics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final OdePhysicsWorld PHYSICS = new OdePhysicsWorld();

	public static Item TEST_CUBE_SPAWNER;
	public static Item PHYSICS_GUN;

	@Override
	public void onInitialize() {
		PhysicsDataKeys.init();
		PHYSICS.init();

		TEST_CUBE_SPAWNER = register("test_cube_spawner", new Item.Properties(), TestCubeSpawnerItem::new);
		PHYSICS_GUN = register("physics_gun", new Item.Properties(), PhysicsGunItem::new);

		ServerTickEvents.START_LEVEL_TICK.register(level -> {
			if (level.dimension() == Level.OVERWORLD) {
				PHYSICS.step();
			}
		});

		LOGGER.info("Sulfur Physics initialized");
	}

	private static <T extends Item> T register(String path, Item.Properties properties, Function<Item.Properties, T> factory) {
		Identifier id = id(path);
		ResourceKey<Item> key = ResourceKey.create(BuiltInRegistries.ITEM.key(), id);
		properties.setId(key);
		T item = factory.apply(properties);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
