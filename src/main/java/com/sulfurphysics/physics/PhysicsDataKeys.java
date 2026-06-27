package com.sulfurphysics.physics;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;

public final class PhysicsDataKeys {
    // Quaternion components (primary rotation source, no gimbal lock)
    private static EntityDataAccessor<Float> physicsQw;
    private static EntityDataAccessor<Float> physicsQx;
    private static EntityDataAccessor<Float> physicsQy;
    private static EntityDataAccessor<Float> physicsQz;

    public static EntityDataAccessor<Float> physicsQw() {
        if (physicsQw == null)
            physicsQw = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.FLOAT);
        return physicsQw;
    }
    public static EntityDataAccessor<Float> physicsQx() {
        if (physicsQx == null)
            physicsQx = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.FLOAT);
        return physicsQx;
    }
    public static EntityDataAccessor<Float> physicsQy() {
        if (physicsQy == null)
            physicsQy = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.FLOAT);
        return physicsQy;
    }
    public static EntityDataAccessor<Float> physicsQz() {
        if (physicsQz == null)
            physicsQz = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.FLOAT);
        return physicsQz;
    }

    public static float getPhysicsQw(LivingEntity entity) {
        if (!(entity instanceof SulfurCube)) return 1.0f;
        EntityDataAccessor<Float> acc = physicsQw();
        return acc == null ? 1.0f : entity.getEntityData().get(acc);
    }
    public static float getPhysicsQx(LivingEntity entity) {
        if (!(entity instanceof SulfurCube)) return 0.0f;
        EntityDataAccessor<Float> acc = physicsQx();
        return acc == null ? 0.0f : entity.getEntityData().get(acc);
    }
    public static float getPhysicsQy(LivingEntity entity) {
        if (!(entity instanceof SulfurCube)) return 0.0f;
        EntityDataAccessor<Float> acc = physicsQy();
        return acc == null ? 0.0f : entity.getEntityData().get(acc);
    }
    public static float getPhysicsQz(LivingEntity entity) {
        if (!(entity instanceof SulfurCube)) return 0.0f;
        EntityDataAccessor<Float> acc = physicsQz();
        return acc == null ? 0.0f : entity.getEntityData().get(acc);
    }

    public static void setPhysicsQw(LivingEntity entity, float qw) {
        if (!(entity instanceof SulfurCube)) return;
        EntityDataAccessor<Float> acc = physicsQw();
        if (acc != null) entity.getEntityData().set(acc, qw);
    }
    public static void setPhysicsQx(LivingEntity entity, float qx) {
        if (!(entity instanceof SulfurCube)) return;
        EntityDataAccessor<Float> acc = physicsQx();
        if (acc != null) entity.getEntityData().set(acc, qx);
    }
    public static void setPhysicsQy(LivingEntity entity, float qy) {
        if (!(entity instanceof SulfurCube)) return;
        EntityDataAccessor<Float> acc = physicsQy();
        if (acc != null) entity.getEntityData().set(acc, qy);
    }
    public static void setPhysicsQz(LivingEntity entity, float qz) {
        if (!(entity instanceof SulfurCube)) return;
        EntityDataAccessor<Float> acc = physicsQz();
        if (acc != null) entity.getEntityData().set(acc, qz);
    }

    public static void init() {
        // Eagerly register data accessors so ClassTreeIdRegistry has the right count
        // before any Builder is created in Entity constructors.
        physicsQw();
        physicsQx();
        physicsQy();
        physicsQz();
    }

    private PhysicsDataKeys() {}
}
