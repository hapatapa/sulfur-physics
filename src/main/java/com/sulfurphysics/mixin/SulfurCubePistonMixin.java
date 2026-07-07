package com.sulfurphysics.mixin;

import com.sulfurphysics.SulfurPhysics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.block.piston.PistonMovingBlockEntity")
public class SulfurCubePistonMixin {
    @Inject(
        method = "moveEntityByPiston",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void onMoveEntityByPiston(Direction dir, Entity entity, double distance, Direction movementDir, CallbackInfo ci) {
        if (entity instanceof SulfurCube cube && cube.hasBodyItem() && !entity.level().isClientSide()) {
            SulfurPhysics.PHYSICS.ensureBody(cube);
            SulfurPhysics.PHYSICS.applyPistonPush(cube.getUUID(), movementDir, distance);
            ci.cancel();
        }
    }
}
