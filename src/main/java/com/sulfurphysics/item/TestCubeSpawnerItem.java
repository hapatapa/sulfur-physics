package com.sulfurphysics.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TestCubeSpawnerItem extends Item {
    public TestCubeSpawnerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        Vec3 look = player.getLookAngle();
        Vec3 spawnPos = player.getEyePosition().add(look.scale(3.0));

        SulfurCube cube = new SulfurCube(EntityTypes.SULFUR_CUBE, level);
        cube.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        cube.setSize(2, true);
        cube.equipItem(new ItemStack(Items.COAL_BLOCK));
        level.addFreshEntity(cube);

        return InteractionResult.SUCCESS;
    }
}
