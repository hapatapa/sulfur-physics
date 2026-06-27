package com.sulfurphysics.client.renderer;

import org.joml.Quaternionf;

public interface PhysicsQuaternionContainer {
    Quaternionf getPhysicsQuaternion();
    void setPhysicsQuaternion(Quaternionf q);
}
