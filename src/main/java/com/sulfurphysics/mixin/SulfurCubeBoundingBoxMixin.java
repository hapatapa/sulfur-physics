package com.sulfurphysics.mixin;

import com.sulfurphysics.physics.PhysicsDataKeys;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class SulfurCubeBoundingBoxMixin {

    @Inject(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
            at = @At("RETURN"), cancellable = true)
    private void onMakeBoundingBox(Vec3 pos, CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity)(Object)this;
        if (!(self instanceof SulfurCube cube)) return;

        float qx = PhysicsDataKeys.getPhysicsQx(cube);
        float qy = PhysicsDataKeys.getPhysicsQy(cube);
        float qz = PhysicsDataKeys.getPhysicsQz(cube);
        float qw = PhysicsDataKeys.getPhysicsQw(cube);
        // skip identity (default) or all-zero (invalid) — only non-default quaternion means ODE is active
        if ((qw == 1.0f && qx == 0.0f && qy == 0.0f && qz == 0.0f)
         || (qw == 0.0f && qx == 0.0f && qy == 0.0f && qz == 0.0f)) return;

        double hw = cube.getBbWidth() / 2.0;
        double hh = cube.getBbHeight() / 2.0;
        double hd = hw;

        double cx = pos.x;
        double cy = pos.y + hh;
        double cz = pos.z;

        // rotation matrix from quaternion
        float xx = qx * qx, yy = qy * qy, zz = qz * qz;
        float xy = qx * qy, xz = qx * qz, xw = qx * qw;
        float yz = qy * qz, yw = qy * qw, zw = qz * qw;

        float r00 = 1.0f - 2.0f * (yy + zz);
        float r01 = 2.0f * (xy - zw);
        float r02 = 2.0f * (xz + yw);
        float r10 = 2.0f * (xy + zw);
        float r11 = 1.0f - 2.0f * (xx + zz);
        float r12 = 2.0f * (yz - xw);
        float r20 = 2.0f * (xz - yw);
        float r21 = 2.0f * (yz + xw);
        float r22 = 1.0f - 2.0f * (xx + yy);

        // rotate all 8 box vertices, track AABB min/max
        double minX =  Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY =  Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double minZ =  Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double lx = ((i & 1) == 0) ? -hw : hw;
            double ly = ((i & 2) == 0) ? -hh : hh;
            double lz = ((i & 4) == 0) ? -hd : hd;

            double wx = lx * r00 + ly * r01 + lz * r02;
            double wy = lx * r10 + ly * r11 + lz * r12;
            double wz = lx * r20 + ly * r21 + lz * r22;

            if (wx < minX) minX = wx;
            if (wx > maxX) maxX = wx;
            if (wy < minY) minY = wy;
            if (wy > maxY) maxY = wy;
            if (wz < minZ) minZ = wz;
            if (wz > maxZ) maxZ = wz;
        }

        cir.setReturnValue(new AABB(cx + minX, cy + minY, cz + minZ,
                                    cx + maxX, cy + maxY, cz + maxZ));
    }
}
