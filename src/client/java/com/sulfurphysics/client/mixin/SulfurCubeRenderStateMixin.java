package com.sulfurphysics.client.mixin;

import com.sulfurphysics.client.renderer.PhysicsQuaternionContainer;
import com.sulfurphysics.client.renderer.PhysicsRollContainer;
import net.minecraft.client.renderer.entity.state.SulfurCubeRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SulfurCubeRenderState.class)
public class SulfurCubeRenderStateMixin implements PhysicsRollContainer, PhysicsQuaternionContainer {
    private float physicsRoll;
    private Quaternionf physicsQuaternion;

    @Override
    public float getPhysicsRoll() {
        return physicsRoll;
    }

    @Override
    public void setPhysicsRoll(float roll) {
        this.physicsRoll = roll;
    }

    @Override
    public Quaternionf getPhysicsQuaternion() {
        return physicsQuaternion;
    }

    @Override
    public void setPhysicsQuaternion(Quaternionf q) {
        this.physicsQuaternion = q;
    }
}
