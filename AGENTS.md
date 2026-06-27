# sulfur-physics — agent guide

Fabric mod for Minecraft 26.2. Java 25, Gradle 9.5.1, Fabric Loom 1.17.

## Build & run

```sh
./gradlew build              # full build (no tests exist)
```

CI runs `./gradlew build` on JDK 25 (Microsoft), uploads `build/libs/`.
ode4j (org.ode4j:core:0.5.4) is bundled via `include` as a nested jar in the output.

## Project structure

- **`src/main/`** — common code; entrypoint `com.sulfurphysics.SulfurPhysics` (ModInitializer)
- **`src/client/`** — client-only code; entrypoint `com.sulfurphysics.client.SulfurPhysicsClient` (ClientModInitializer)
- **`referencecode/`** — decompiled vanilla MC classes and vanilla datapack JSONs (read-only reference, not built)
- **`run/`** — local dev Minecraft instance (gitignored, contains `resources/`)

Split source sets via `loom.splitEnvironmentSourceSets()` in `build.gradle`.

## Mod identity

| Field | Value |
|---|---|
| Mod ID | `sulfur-physics` |
| Group | `com.sulfurphysics` |
| Mixin pkg (main) | `com.sulfurphysics.mixin` |
| Mixin pkg (client) | `com.sulfurphysics.client.mixin` |

Helper: `SulfurPhysics.id(path)` → `Identifier.fromNamespaceAndPath(MOD_ID, path)`.

Dependencies: fabric-loader ≥0.19.3, fabric-api *, minecraft ~26.2, ode4j 0.5.4.

## Physics engine

Uses **ode4j** (pure Java ODE port) for 3D rigid body dynamics. ODE manages force accumulation, velocity integration, and world collision (box-box contacts between entity and solid blocks).

- `OdePhysicsWorld` — singleton, stepped via `ServerTickEvents.START_LEVEL_TICK` (before entity ticks)
- `SulfurCubePhysicsMixin` — cancels `LivingEntity.travel()` for cubes with body items; applies bounce from `BOUNCINESS` attribute; reads ODE velocity and calls `move()`
- `SulfurCubeAiMixin` — cancels `customServerAiStep()` for cubes with body items

Physics flow per tick:
1. `step()`: sync entity foot position → ODE body center (foot + h/2); collect solid blocks within COLLISION_RADIUS; rebuild world block geoms; `space.collide()` → `dCollide()` box-box contacts with bounce/friction; apply air drag; apply gravity (`-mass × GRAVITY × 400`); `world.quickStep(1/20)`; read back ODE body position (center) → entity foot (center − h/2), quaternion → yaw/pitch/roll, velocity ÷20 → delta movement
2. Entity tick → `travel()` mixin: cancels vanilla movement, sets `setDeltaMovement(Vec3.ZERO)` (ODE handles motion), lazy `ensureBody()` on first call

Bodies auto-create on first `travel()` call (lazy), auto-remove when entity removed or loses body item.

## Body/geom setup (OdePhysicsWorld.ensureBody)
- ODE body position = entity foot + h/2 (center of mass / collision box center)
- Geom attached to body at origin (0,0,0), no offset — shares body's center position
- Geom extends from center ± (w/2, h/2, d/2) in world space, matching entity hitbox
- Position readback subtracts h/2 to convert ODE center → Minecraft entity foot

## Rendering rotation
- `LivingEntityRendererMixin.onBeforeIsBodyVisible()` injects at `isBodyVisible()` call in `LivingEntityRenderer.submit()`, which is AFTER `translate(0, -1.501f, 0)` (the standard model offset) but BEFORE `submitModel()` — this ensures `mulPose(Rz)` is right-multiplied AFTER the -1.501 translate, so the rotation center in the vertex pipeline is at model-space origin (0,0,0) = model center.
- Injecting at `SulfurCubeRenderer.scale()` `@At("RETURN")` was WRONG because the renderer applies `translate(0, -1.501f, 0)` after `scale()` returns, making the vertex pipeline run: `T(-1.501) → Rz → ...` instead of `Rz → T(-1.501) → ...`. The -1.501 shift makes Rz rotate around (0, 1.501, 0) in model space, not the model center.

## Key API quirks (Minecraft 26.2 vs older versions)

- `InteractionResultHolder` removed; use `InteractionResult` directly
- Entity type constants in `EntityTypes` class (not `EntityType`)
- Registry via `BuiltInRegistries.ITEM` (not `Registries.ITEM`)
- Fabric lifecycle events use `START_LEVEL_TICK` / `END_LEVEL_TICK` (not `START_WORLD_TICK`)
- `ode4j` DVector3C is in `org.ode4j.math` (not `org.ode4j.ode`), accessed via `get0()`/`get1()`/`get2()`
- `DMassC` (immutable) used for reading mass properties; `DMass` (mutable) for construction/setting
- `SulfurCubeModel`: `addBox(-9, -9, -9, 18, 18, 18)` — centered at origin. `SmallSulfurCubeModel`: `addBox(-5, -5, -5, 10, 10, 10)`
- `SulfurCubeRenderer.scale()` flow: `downscaleSlightly()` → `super.scale()` (squish) → fuse swell → `scale(scaleFactor)` → `translate(0, yOffset - invF, 0)`. `yOffset` = 1.24 (baby) / 0.98 (non-baby), `invF` = 0 (invisible) or 1/16 (visible)

## Test items

| Item | ID | Behavior |
|---|---|---|
| Test Cube Spawner | `sulfur-physics:test_cube_spawner` | Spawns a physics-enabled SulfurCube with `coal_block` (regular archetype) |
| Physics Gun | `sulfur-physics:physics_gun` | Right-click pushes cube away; shift-right-click pulls toward player |

## Key config files

- `gradle.properties` — minecraft/loader/loom/fabric-api versions
- `src/main/resources/fabric.mod.json` — mod metadata, entrypoints, mixins, dependencies
- `.github/workflows/build.yml` — CI: push/PR trigger, JDK 25, `./gradlew build`

## Notes

- No tests, no lint/typecheck config. `build` is the only verification gate.
- `referencecode/` is a local copy of the target Minecraft version's decompiled output — consult it for cross-compile checks and data file formats.
- `computeModifiedFriction(base, modifier)` = `clamp(1.0 - (1.0 - base) * modifier, 0, 1)`. Used for air drag.
- `DMass.setBox` / `createBox` take FULL side lengths, not half-dimensions.
- ODE velocity (blocks/sec) vs Minecraft deltaMovement (blocks/tick): conversion factor = 20.
- ODE body position = entity center of mass (foot + h/2). Always convert when reading/writing entity foot position.
