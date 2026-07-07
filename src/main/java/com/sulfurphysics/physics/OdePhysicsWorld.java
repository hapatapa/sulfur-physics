package com.sulfurphysics.physics;

import com.sulfurphysics.SulfurPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.ode4j.math.DQuaternionC;
import org.ode4j.math.DVector3C;
import org.ode4j.ode.*;
import org.ode4j.ode.DGeom.DNearCallback;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OdePhysicsWorld {
    private static final double TICK_STEP = 1.0 / 20.0;
    private static final int STEPS_PER_TICK = 3;
    private static final double STEP_DT = 1.0 / 60.0;
    private static final int COLLISION_RADIUS = 2;
    private static final int MAX_CONTACTS = 4;

    private DWorld world;
    private DSpace space;
    private DJointGroup contactGroup;
    private boolean initialized;
    private long lastNanoTime = System.nanoTime();
    private double timeAccumulator = 0;

    private final Map<UUID, PhysicsBody> bodies = new HashMap<>();
    private final Map<Long, List<DGeom>> blockGeoms = new HashMap<>();
    private final DContactBuffer contactBuffer = new DContactBuffer(MAX_CONTACTS);

    public static final ConcurrentHashMap<UUID, float[]> RENDER_QUATERNION = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<UUID, float[]> PREV_RENDER_QUATERNION = new ConcurrentHashMap<>();

    private static final double GRAB_DISTANCE = 4.0;
    private static final double GRAB_SPRING = 200.0;
    private static final double GRAB_DAMP = 20.0;
    private static final double GRAB_MAX_FORCE = 400.0;
    private final Map<UUID, UUID> grabbedEntities = new HashMap<>(); // playerUUID -> entityUUID

    private static final Map<String, Float> ARCHETYPE_DAMAGES = Map.ofEntries(
        Map.entry("regular", 2.0f),
        Map.entry("bouncy", 1.5f),
        Map.entry("slow_bouncy", 4.0f),
        Map.entry("slow_flat", 5.0f),
        Map.entry("fast_flat", 1.0f),
        Map.entry("light", 0.5f),
        Map.entry("fast_sliding", 1.5f),
        Map.entry("slow_sliding", 1.0f),
        Map.entry("high_resistance", 0.5f),
        Map.entry("sticky", 0.5f),
        Map.entry("explosive", 3.0f),
        Map.entry("hot", 3.0f)
    );

    private record PhysicsBody(DBody body, DGeom geom, LivingEntity entity) {}

    private record HitEntry(LivingEntity entity, LivingEntity attacker, double speed) {}

    private double getBaseDamage(SulfurCube cube) {
        if (!cube.hasBodyItem()) return 0;
        ItemStack bodyItem = cube.getItemBySlot(EquipmentSlot.BODY);
        if (bodyItem.isEmpty()) return 2.0f;
        for (Map.Entry<String, Float> entry : ARCHETYPE_DAMAGES.entrySet()) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("minecraft", "sulfur_cube_archetype/" + entry.getKey()));
            if (bodyItem.is(tagKey)) return entry.getValue();
        }
        return 2.0f;
    }

    public void init() {
        if (initialized) return;
        OdeHelper.initODE2(0);
        world = OdeHelper.createWorld();
        world.setGravity(0, 0, 0);
        world.setERP(0.95);
        world.setCFM(0.00001);
        world.setAutoDisableFlag(false);
        world.setContactMaxCorrectingVel(100.0);
        world.setContactSurfaceLayer(0.001);

        space = OdeHelper.createHashSpace(null);
        contactGroup = OdeHelper.createJointGroup();
        initialized = true;
    }

    public void ensureBody(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        if (bodies.containsKey(uuid)) return;

        DBody body = OdeHelper.createBody(world);
        Vec3 pos = entity.position();
        double w = entity.getBbWidth();
        double h = entity.getBbHeight();
        double d = w;
        body.setPosition(pos.x, pos.y + h / 2.0, pos.z);

        DMass mass = OdeHelper.createMass();
        mass.setBox(1.0, w, h, d);
        body.setMass(mass);

        Vec3 vel = entity.getDeltaMovement();
        body.setLinearVel(vel.x * 20.0, vel.y * 20.0, vel.z * 20.0);

        body.setAutoDisableFlag(false);
        body.setAngularDamping(0.3);
        body.setLinearDamping(0.01);

        DGeom geom = OdeHelper.createBox(space, w, h, d);
        geom.setBody(body);
        geom.setPosition(0, 0, 0);
        geom.setData(uuid);

        SulfurPhysics.LOGGER.info("Created physics body for entity {}", uuid);
        bodies.put(uuid, new PhysicsBody(body, geom, entity));
    }

    public void removeBody(UUID uuid) {
        RENDER_QUATERNION.remove(uuid);
        PREV_RENDER_QUATERNION.remove(uuid);
        PhysicsBody pb = bodies.remove(uuid);
        if (pb != null) {
            PhysicsDataKeys.setPhysicsQw(pb.entity, 1.0f);
            PhysicsDataKeys.setPhysicsQx(pb.entity, 0.0f);
            PhysicsDataKeys.setPhysicsQy(pb.entity, 0.0f);
            PhysicsDataKeys.setPhysicsQz(pb.entity, 0.0f);
            pb.geom.destroy();
            pb.body.destroy();
            SulfurPhysics.LOGGER.info("Removed physics body for entity {}", uuid);
        }
    }

    public void setVelocity(UUID uuid, Vec3 vel) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            pb.body.setLinearVel(vel.x * 20.0, vel.y * 20.0, vel.z * 20.0);
            pb.entity().setDeltaMovement(vel);
        }
    }

    public void applyPistonPush(UUID uuid, net.minecraft.core.Direction dir, double distance) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            double odeVel = distance * 20.0;
            pb.body.setLinearVel(
                dir.getStepX() * odeVel,
                dir.getStepY() * odeVel,
                dir.getStepZ() * odeVel
            );
        }
    }

    public void applyForce(UUID uuid, Vec3 force) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            pb.body.addForce(force.x, force.y, force.z);
        }
    }

    public boolean grabEntity(UUID playerUuid, UUID entityUuid) {
        if (!bodies.containsKey(entityUuid)) return false;
        grabbedEntities.put(playerUuid, entityUuid);
        return true;
    }

    public boolean releaseEntity(UUID playerUuid, UUID entityUuid) {
        UUID current = grabbedEntities.get(playerUuid);
        if (current != null && current.equals(entityUuid)) {
            grabbedEntities.remove(playerUuid);
            return true;
        }
        return false;
    }

    public boolean releaseAll(UUID playerUuid) {
        return grabbedEntities.remove(playerUuid) != null;
    }

    public UUID getGrabbedEntity(UUID playerUuid) {
        return grabbedEntities.get(playerUuid);
    }

    public void applyImpulse(UUID uuid, Vec3 impulse) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            DVector3C vel = pb.body.getLinearVel();
            pb.body.setLinearVel(
                vel.get0() + impulse.x * 20.0,
                vel.get1() + impulse.y * 20.0,
                vel.get2() + impulse.z * 20.0
            );
            pb.entity().setDeltaMovement(pb.entity().getDeltaMovement().add(impulse));
        }
    }

    public void addToVelocity(UUID uuid, Vec3 vel) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            DVector3C odev = pb.body.getLinearVel();
            pb.body.setLinearVel(
                odev.get0() + vel.x * 20.0,
                odev.get1() + vel.y * 20.0,
                odev.get2() + vel.z * 20.0
            );
        }
    }

    public Vec3 getVelocity(UUID uuid) {
        PhysicsBody pb = bodies.get(uuid);
        if (pb != null) {
            DVector3C vel = pb.body.getLinearVel();
            return new Vec3(vel.get0() / 20.0, vel.get1() / 20.0, vel.get2() / 20.0);
        }
        return Vec3.ZERO;
    }

    public void step() {
        if (!initialized) return;

        // 1. Remove dead / flagless bodies
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, PhysicsBody> entry : bodies.entrySet()) {
            LivingEntity e = entry.getValue().entity();
            if (e.isRemoved()
                || !(e instanceof SulfurCube cube)
                || !cube.hasBodyItem()) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            removeBody(uuid);
        }
        if (bodies.isEmpty()) {
            if (!blockGeoms.isEmpty()) destroyAllBlockGeoms();
            return;
        }

        // 2. Sync entity→body, collect needed block collision shapes
        Set<Long> neededBlocks = new HashSet<>();
        Map<Long, VoxelShape> blockShapes = new HashMap<>();
        Level level = null;

        for (PhysicsBody pb : bodies.values()) {
            LivingEntity entity = pb.entity();
            DBody body = pb.body();

            Vec3 pos = entity.position();
            double h = entity.getBbHeight();
            body.setPosition(pos.x, pos.y + h / 2.0, pos.z);

            if (level == null) level = entity.level();
            BlockPos epos = entity.blockPosition();
            int r = COLLISION_RADIUS;
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos bp = epos.offset(dx, dy, dz);
                        long key = bp.asLong();
                        if (!neededBlocks.contains(key)) {
                            VoxelShape shape = level.getBlockState(bp).getCollisionShape(level, bp);
                            if (!shape.isEmpty()) {
                                neededBlocks.add(key);
                                blockShapes.put(key, shape);
                            }
                        }
                    }
                }
            }
        }

        // 3. Update world block geoms (add new, remove stale)
        for (long key : neededBlocks) {
            if (!blockGeoms.containsKey(key)) {
                BlockPos bp = BlockPos.of(key);
                VoxelShape shape = blockShapes.get(key);
                List<DGeom> geoms = new ArrayList<>();
                for (AABB aabb : shape.toAabbs()) {
                    DGeom bg = OdeHelper.createBox(space, aabb.getXsize(), aabb.getYsize(), aabb.getZsize());
                    bg.setPosition(
                        bp.getX() + (aabb.minX + aabb.maxX) / 2.0,
                        bp.getY() + (aabb.minY + aabb.maxY) / 2.0,
                        bp.getZ() + (aabb.minZ + aabb.maxZ) / 2.0
                    );
                    bg.setData(null);
                    geoms.add(bg);
                }
                blockGeoms.put(key, geoms);
            }
        }
        Iterator<Map.Entry<Long, List<DGeom>>> it = blockGeoms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, List<DGeom>> entry = it.next();
            if (!neededBlocks.contains(entry.getKey())) {
                for (DGeom g : entry.getValue()) g.destroy();
                it.remove();
            }
        }

        // 4. Update grabbed entity positions (spring-damper toward target)
        if (!grabbedEntities.isEmpty() && level instanceof ServerLevel serverLevel) {
            Iterator<Map.Entry<UUID, UUID>> grabIt = grabbedEntities.entrySet().iterator();
            while (grabIt.hasNext()) {
                Map.Entry<UUID, UUID> gEntry = grabIt.next();
                PhysicsBody pb = bodies.get(gEntry.getValue());
                if (pb == null) {
                    grabIt.remove();
                    continue;
                }
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(gEntry.getKey());
                if (player == null || !player.isAlive()) {
                    grabIt.remove();
                    continue;
                }
                Vec3 target = player.getEyePosition().add(player.getLookAngle().scale(GRAB_DISTANCE));
                DVector3C pos = pb.body.getPosition();
                Vec3 delta = new Vec3(target.x - pos.get0(), target.y - pos.get1(), target.z - pos.get2());
                Vec3 force = delta.scale(GRAB_SPRING);
                DVector3C vel = pb.body.getLinearVel();
                Vec3 damp = new Vec3(vel.get0(), vel.get1(), vel.get2()).scale(-GRAB_DAMP);
                force = force.add(damp);
                double len = force.length();
                if (len > GRAB_MAX_FORCE) force = force.scale(GRAB_MAX_FORCE / len);
                pb.body.addForce(force.x, force.y, force.z);
                pb.body.setAngularVel(0, 0, 0);
            }
        }

        // 5. Record pre-step Y velocity (before any sub-step forces)
        Map<UUID, Double> preStepVy = new HashMap<>();
        for (PhysicsBody pb : bodies.values()) {
            preStepVy.put(pb.entity().getUUID(), pb.body().getLinearVel().get1());
        }

        // 5a. Save previous step quaternion for render interpolation
        for (PhysicsBody pb : bodies.values()) {
            UUID uuid = pb.entity().getUUID();
            float[] curr = RENDER_QUATERNION.get(uuid);
            if (curr != null) {
                PREV_RENDER_QUATERNION.put(uuid, curr.clone());
            }
        }

        // 5b. Physics step loop: collide + forces + step + entity collision
        Map<UUID, HitEntry> hitEntities = new HashMap<>();
        for (int step = 0; step < STEPS_PER_TICK; step++) {
            contactGroup.empty();
            space.collide(null, this::nearCallback);

            for (PhysicsBody pb : bodies.values()) {
                DBody body = pb.body();
                LivingEntity entity = pb.entity();
                DMassC mass = body.getMass();

                double modifier = entity.getAttributeValue(Attributes.AIR_DRAG_MODIFIER);
                double baseAirDrag = 0.98;
                double drag = 1.0 - (1.0 - baseAirDrag) * modifier;
                drag = Math.max(0.0, Math.min(1.0, drag));
                double perStepDrag = Math.pow(drag, 1.0 / STEPS_PER_TICK);
                DVector3C vel = body.getLinearVel();
                body.setLinearVel(vel.get0() * perStepDrag,
                                  vel.get1() * perStepDrag,
                                  vel.get2() * perStepDrag);

                double gravity = entity.getAttributeValue(Attributes.GRAVITY);
                body.addForce(0, -mass.getMass() * gravity * 400.0, 0);
            }

            world.quickStep(STEP_DT);

            // Entity-vs-entity collision: push non-physics entities, track damage
            for (PhysicsBody pb : bodies.values()) {
                DBody body = pb.body();
                LivingEntity entity = pb.entity();
                if (!(entity instanceof SulfurCube cube)) continue;

                DVector3C p = body.getPosition();
                double h = entity.getBbHeight();
                entity.setPos(p.get0(), p.get1() - h / 2.0, p.get2());

                AABB cubeBox = entity.getBoundingBox();
                Vec3 cubeVel = new Vec3(
                    body.getLinearVel().get0() / 20.0,
                    body.getLinearVel().get1() / 20.0,
                    body.getLinearVel().get2() / 20.0
                );

                for (Entity other : level.getEntities(entity, cubeBox.inflate(0.5),
                        e -> e instanceof LivingEntity && e.isAlive()
                          && !bodies.containsKey(e.getUUID()))) {
                    LivingEntity target = (LivingEntity) other;
                    Vec3 targetVel = target.getDeltaMovement();
                    Vec3 relativeVel = cubeVel.subtract(targetVel);
                    double speed = relativeVel.length();
                    if (speed < 0.05) continue;

                    Vec3 knockback = relativeVel.scale(0.5);
                    target.setDeltaMovement(targetVel.add(knockback));

                    hitEntities.merge(other.getUUID(), new HitEntry(target, entity, speed),
                        (a, b) -> a.speed >= b.speed ? a : b);
                }
            }

            // Per-step: publish quaternion for render thread
            for (PhysicsBody pb : bodies.values()) {
                DBody body = pb.body();
                DQuaternionC q = body.getQuaternion();
                // compensate for pipeline: R_y(180) * diag(-1,-1,1) = diag(1,-1,-1) = R_x(180°)
                // corrected = R_x(180°) * q_ode = (0,1,0,0) * (qw,qx,qy,qz) = (-qx, qw, -qz, qy)
                RENDER_QUATERNION.put(pb.entity().getUUID(), new float[]{(float)q.get0(), (float)-q.get3(), (float)q.get2(), (float)-q.get1()});
            }
        }

        // 6. Apply entity damage (once per tick, max velocity) + read back final state
        if (level instanceof ServerLevel serverLevel && !hitEntities.isEmpty()) {
            for (HitEntry entry : hitEntities.values()) {
                if (!(entry.attacker instanceof SulfurCube cube)) continue;
                float baseDmg = (float) getBaseDamage(cube);
                float damage = baseDmg * (float) entry.speed;
                if (damage >= 1.0f) {
                    entry.entity.hurtServer(serverLevel, entry.entity.damageSources().mobAttack(cube), damage);
                }
            }
        }

        // 6b. After all steps: read back position, final rotation, velocity
        for (PhysicsBody pb : bodies.values()) {
            DBody body = pb.body();
            LivingEntity entity = pb.entity();

            DQuaternionC q = body.getQuaternion();
            // corrected = R_x(180°) * q_ode = (-qx, qw, -qz, qy) in (w,x,y,z)
            float qw = (float)-q.get1(), qx = (float)q.get0(), qy = (float)-q.get3(), qz = (float)q.get2();
            PhysicsDataKeys.setPhysicsQw(entity, qw);
            PhysicsDataKeys.setPhysicsQx(entity, qx);
            PhysicsDataKeys.setPhysicsQy(entity, qy);
            PhysicsDataKeys.setPhysicsQz(entity, qz);
            RENDER_QUATERNION.put(entity.getUUID(), new float[]{qx, qy, qz, qw});

            DVector3C p = body.getPosition();
            double h = entity.getBbHeight();
            entity.setPos(p.get0(), p.get1() - h / 2.0, p.get2());

            DVector3C v = body.getLinearVel();
            entity.setDeltaMovement(new Vec3(v.get0() / 20.0, v.get1() / 20.0, v.get2() / 20.0));

            entity.setYRot(0);

            Double vyBefore = preStepVy.get(entity.getUUID());
            if (vyBefore != null && vyBefore < -0.1 && Math.abs(v.get1()) < 0.2) {
                entity.setOnGround(true);
            }

        }

        // 7. Prevent auto-disable
        for (PhysicsBody pb : bodies.values()) {
            pb.body.setAutoDisableFlag(false);
        }
    }



    private void nearCallback(Object data, DGeom o1, DGeom o2) {
        Object d1 = o1.getData();
        Object d2 = o2.getData();

        if (d1 == null && d2 == null) return; // world–world

        if (d1 != null && d2 != null) {
            // entity–entity collision
            UUID uuid1 = (UUID)d1;
            UUID uuid2 = (UUID)d2;
            if (uuid1.equals(uuid2)) return;

            PhysicsBody pb1 = bodies.get(uuid1);
            PhysicsBody pb2 = bodies.get(uuid2);
            if (pb1 == null || pb2 == null) return;

            double friction = (pb1.entity.getAttributeValue(Attributes.FRICTION_MODIFIER)
                             + pb2.entity.getAttributeValue(Attributes.FRICTION_MODIFIER)) / 2.0;
            double bounciness = (pb1.entity.getAttributeValue(Attributes.BOUNCINESS)
                               + pb2.entity.getAttributeValue(Attributes.BOUNCINESS)) / 2.0;

            DContactGeomBuffer geomBuf = contactBuffer.getGeomBuffer();
            int n = OdeHelper.collide(o1, o2, MAX_CONTACTS, geomBuf);
            for (int i = 0; i < n; i++) {
                DContact contact = contactBuffer.get(i);
                contact.surface.mode = OdeConstants.dContactBounce
                                     | OdeConstants.dContactApprox1;
                contact.surface.mu = friction;
                contact.surface.bounce = bounciness;
                contact.surface.bounce_vel = 0.1;
                contact.surface.soft_erp = 0.95;
                contact.surface.soft_cfm = 0.00001;
                DJoint cj = OdeHelper.createContactJoint(world, contactGroup, contact);
                cj.attach(pb1.body, pb2.body);
            }
            return;
        }

        // entity–world collision
        DGeom entityGeom = (d1 != null) ? o1 : o2;
        DGeom worldGeom  = (d1 != null) ? o2 : o1;

        DBody body = entityGeom.getBody();
        if (body == null) return;

        UUID uuid = (UUID) entityGeom.getData();
        PhysicsBody pb = bodies.get(uuid);
        if (pb == null) return;

        LivingEntity entity = pb.entity();
        double friction    = entity.getAttributeValue(Attributes.FRICTION_MODIFIER);
        double bounciness  = entity.getAttributeValue(Attributes.BOUNCINESS);

        DContactGeomBuffer geomBuf = contactBuffer.getGeomBuffer();
        int n = OdeHelper.collide(entityGeom, worldGeom, MAX_CONTACTS, geomBuf);
        for (int i = 0; i < n; i++) {
            DContact contact = contactBuffer.get(i);
            contact.surface.mode = OdeConstants.dContactBounce
                                 | OdeConstants.dContactApprox1;
            contact.surface.mu = friction;
            contact.surface.bounce = bounciness;
            contact.surface.bounce_vel = 0.1;
            contact.surface.soft_erp = 0.95;
            contact.surface.soft_cfm = 0.00001;
            DJoint cj = OdeHelper.createContactJoint(world, contactGroup, contact);
            cj.attach(body, null);
        }
    }

    private void destroyAllBlockGeoms() {
        for (List<DGeom> geoms : blockGeoms.values()) {
            for (DGeom g : geoms) g.destroy();
        }
        blockGeoms.clear();
    }

    public void destroy() {
        RENDER_QUATERNION.clear();
        PREV_RENDER_QUATERNION.clear();
        destroyAllBlockGeoms();
        for (PhysicsBody pb : bodies.values()) {
            pb.geom.destroy();
            pb.body.destroy();
        }
        bodies.clear();
        if (contactGroup != null) contactGroup.destroy();
        if (space != null) space.destroy();
        if (world != null) world.destroy();
        OdeHelper.closeODE();
        initialized = false;
    }
}
