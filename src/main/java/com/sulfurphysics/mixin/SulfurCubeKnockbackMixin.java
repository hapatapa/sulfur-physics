package com.sulfurphysics.mixin;

import com.sulfurphysics.SulfurPhysics;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SulfurCube.class)
public class SulfurCubeKnockbackMixin {
    @Unique
    private Vec3 sulfurPhysicsPreKnockbackDelta;

    @Inject(at = @At("HEAD"), method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void savePreKnockbackDelta(double strength, double x, double z, DamageSource source, float damage, boolean flag, CallbackInfo ci) {
        sulfurPhysicsPreKnockbackDelta = ((SulfurCube)(Object)this).getDeltaMovement();
    }

    @Inject(at = @At("RETURN"), method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void forwardKnockbackToOde(double strength, double x, double z, DamageSource source, float damage, boolean flag, CallbackInfo ci) {
        SulfurCube self = (SulfurCube)(Object)this;
        if (!self.hasBodyItem() || self.level().isClientSide()) return;

        Vec3 post = self.getDeltaMovement();
        Vec3 delta = post.subtract(sulfurPhysicsPreKnockbackDelta);
        if (delta.lengthSqr() < 1.0e-10) return;

        SulfurPhysics.PHYSICS.ensureBody(self);
        SulfurPhysics.PHYSICS.addToVelocity(self.getUUID(), delta);
        self.setDeltaMovement(sulfurPhysicsPreKnockbackDelta);
    }
}
