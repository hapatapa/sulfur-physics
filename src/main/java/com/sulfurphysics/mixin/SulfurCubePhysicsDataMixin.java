package com.sulfurphysics.mixin;

import com.sulfurphysics.physics.PhysicsDataKeys;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCube.class)
public class SulfurCubePhysicsDataMixin {
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void onDefineSynchedData(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(PhysicsDataKeys.physicsQw(), 1.0f);
        builder.define(PhysicsDataKeys.physicsQx(), 0.0f);
        builder.define(PhysicsDataKeys.physicsQy(), 0.0f);
        builder.define(PhysicsDataKeys.physicsQz(), 0.0f);
    }
}
