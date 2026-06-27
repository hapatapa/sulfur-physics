package com.sulfurphysics.mixin;

import com.sulfurphysics.SulfurPhysics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class SulfurCubePhysicsMixin {
    @Inject(at = @At("HEAD"), method = "travel", cancellable = true)
    private void onTravel(Vec3 input, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof SulfurCube cube
            && cube.hasBodyItem()
            && !self.level().isClientSide()) {
            SulfurPhysics.PHYSICS.ensureBody(cube);
            cube.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
