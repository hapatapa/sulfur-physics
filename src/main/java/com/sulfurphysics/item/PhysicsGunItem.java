package com.sulfurphysics.item;

import com.sulfurphysics.SulfurPhysics;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class PhysicsGunItem extends Item {
    private static final double PUSH_FORCE = 3.0;

    public PhysicsGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(target instanceof SulfurCube cube) || !cube.hasBodyItem()) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            SulfurPhysics.PHYSICS.releaseAll(player.getUUID());
            return InteractionResult.SUCCESS;
        }

        UUID playerId = player.getUUID();
        UUID grabbed = SulfurPhysics.PHYSICS.getGrabbedEntity(playerId);
        if (grabbed != null && grabbed.equals(cube.getUUID())) {
            SulfurPhysics.PHYSICS.releaseAll(playerId);
        } else {
            SulfurPhysics.PHYSICS.releaseAll(playerId);
            SulfurPhysics.PHYSICS.grabEntity(playerId, cube.getUUID());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.level().isClientSide()) return;
        if (target instanceof SulfurCube cube && cube.hasBodyItem() && attacker instanceof Player player) {
            SulfurPhysics.PHYSICS.releaseAll(player.getUUID());
            Vec3 push = attacker.getLookAngle().scale(PUSH_FORCE);
            SulfurPhysics.PHYSICS.applyImpulse(cube.getUUID(), push);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player.isShiftKeyDown()) {
            SulfurPhysics.PHYSICS.releaseAll(player.getUUID());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
