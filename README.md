# Sulfur Physics

A Fabric mod for Minecraft 26.2 that replaces the vanilla entity movement of SulfurCubes with a full 3D rigid body physics engine powered by [ode4j](https://github.com/tzaeschke/ode4j).

## Features

- **Physically simulated SulfurCubes** — movement, rotation, and collision handled by a real physics engine (ODE)
- **Per-block collision** — slabs, stairs, chests, walls, and full blocks all have accurate collision shapes
- **Entity–entity collisions** — physics cubes collide with each other
- **Rotating hitbox** — the attack AABB rotates dynamically to match the visual cube
- **Quaternion rotation** — smooth, gimbal-lock-free rotation rendered via slerp interpolation
- **60 Hz physics** — 3 sub-steps per game tick for stable simulation
- **Test items** — Test Cube Spawner and Physics Gun (push/pull cubes)

## Items

| Item | ID | Description |
|---|---|---|
| Test Cube Spawner | `sulfur-physics:test_cube_spawner` | Spawns a physics-enabled SulfurCube |
| Physics Gun | `sulfur-physics:physics_gun` | Right-click pushes cubes away; shift-right-click pulls them toward you |

## Development

```sh
./gradlew build
```

Built jars are output to `build/libs/`.

## Requirements

- Minecraft 26.2
- Fabric Loader ≥0.19.3
- Fabric API
- Java 25

## License

MIT
