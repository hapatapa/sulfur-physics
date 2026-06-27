package com.sulfurphysics.client.mixin;

import com.sulfurphysics.client.renderer.PhysicsQuaternionContainer;
import com.sulfurphysics.client.renderer.PhysicsRollContainer;
import com.sulfurphysics.physics.OdePhysicsWorld;
import com.sulfurphysics.physics.PhysicsDataKeys;
import net.minecraft.client.renderer.entity.SulfurCubeRenderer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(SulfurCubeRenderer.class)
public class SulfurCubeRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(SulfurCube entity, SulfurCubeRenderState state, float partialTicks, CallbackInfo ci) {
        UUID uuid = entity.getUUID();
        float[] quat = OdePhysicsWorld.RENDER_QUATERNION.get(uuid);
        Quaternionf q;
        if (quat != null) {
            float[] prevQuatArr = OdePhysicsWorld.PREV_RENDER_QUATERNION.get(uuid);
            if (prevQuatArr != null) {
                Quaternionf prev = new Quaternionf(prevQuatArr[0], prevQuatArr[1], prevQuatArr[2], prevQuatArr[3]);
                Quaternionf curr = new Quaternionf(quat[0], quat[1], quat[2], quat[3]);
                q = new Quaternionf();
                prev.slerp(curr, partialTicks, q);
            } else {
                q = new Quaternionf(quat[0], quat[1], quat[2], quat[3]);
            }
        } else {
            q = new Quaternionf(
                PhysicsDataKeys.getPhysicsQx(entity),
                PhysicsDataKeys.getPhysicsQy(entity),
                PhysicsDataKeys.getPhysicsQz(entity),
                PhysicsDataKeys.getPhysicsQw(entity)
            );
        }
        ((PhysicsQuaternionContainer) state).setPhysicsQuaternion(q);
        state.bodyRot = 0;
        state.yRot = 0;
        state.xRot = 0;
        ((PhysicsRollContainer) state).setPhysicsRoll(0);
    }
}
