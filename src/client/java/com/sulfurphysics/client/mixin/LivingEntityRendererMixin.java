package com.sulfurphysics.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sulfurphysics.client.renderer.PhysicsQuaternionContainer;
import com.sulfurphysics.client.renderer.PhysicsRollContainer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
                     shift = At.Shift.BEFORE))
    private void onBeforeIsBodyVisible(LivingEntityRenderState state, PoseStack poseStack,
                                        SubmitNodeCollector collector, CameraRenderState camera,
                                        CallbackInfo ci) {
        if (state instanceof SulfurCubeRenderState cubeState) {
            Quaternionf full = ((PhysicsQuaternionContainer) cubeState).getPhysicsQuaternion();
            if (full != null) {
                poseStack.mulPose(full);
            } else {
                float pitch = state.xRot;
                if (pitch != 0.0f) {
                    poseStack.mulPose(new Quaternionf().rotationX(pitch * ((float) Math.PI / 180.0f)));
                }
                float roll = ((PhysicsRollContainer) cubeState).getPhysicsRoll();
                if (roll != 0.0f) {
                    poseStack.mulPose(new Quaternionf().rotationZ(roll));
                }
            }
        }
    }
}
