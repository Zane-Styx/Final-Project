# Algorithms Implemented

Only algorithms present in the codebase are listed, with usage and complexity.

## Collision Resolution (Walls and Solids)
- **What:** Resolve axis-aligned collisions for the player against walls and solids; adjust hitbox to prevent overlap.
- **Where:** `PlayerCollision.resolveWallCollision()`, `PlayerCollision.resolveSolidCollision()` (invoked from `PlayerLogic`).
- **Time Complexity:** O(W + S) per frame where W/S are counts of walls/solids considered.
- **Space Complexity:** O(1) auxiliary (reuses rectangles; occasional temporaries).

## Dash Sub-Stepping to Prevent Tunneling
- **What:** Break dash movement into small steps to pre-check forward solids and avoid passing through thin obstacles.
- **Where:** `PlayerLogic.handleDash()`.
- **Time Complexity:** O(N + S) per dash step, N = number of sub-steps (bounded by movement distance / step size); S = solids checked.
- **Space Complexity:** O(1) auxiliary.

## Ground Landing Selection (Platform Snap)
- **What:** Identify the best landing platform directly beneath the player and snap to its top surface.
- **Where:** `PlayerLogic.handlePlatformLanding()`.
- **Time Complexity:** O(W) per check (iterate candidate walls and compute vertical proximity).
- **Space Complexity:** O(1) auxiliary.

## Gravity Integration with Terminal Velocity
- **What:** Integrate vertical velocity under gravity with a cap on max fall speed; resolve vertical collisions.
- **Where:** `PlayerLogic.handleVerticalMovement()`.
- **Time Complexity:** O(W + S) for collision checks post integration.
- **Space Complexity:** O(1) auxiliary.

## Melee Attack Hit Detection
- **What:** Construct a transient attack AABB in front of the player and test overlaps with enemies.
- **Where:** `PlayerLogic.checkEnemyHits()`.
- **Time Complexity:** O(E) per attack hit frame; E = number of enemies.
- **Space Complexity:** O(1) auxiliary.

## Proximity Zone Checks for Boss Attacks
- **What:** Test if player is inside invisible left/right zones to decide Ice Wall spawns and to gate Ice Pick.
- **Where:** `GuardianCrystal.tryIceWallAttack()`, `GuardianCrystal.isPlayerInIceWallRange()`.
- **Time Complexity:** O(1) per check (construct two rectangles and test overlaps).
- **Space Complexity:** O(1).

## Pre-Attack Telegraph (Red Flash) Timing
- **What:** Drive a 1-second red flash timer before firing Ice Pick; only fires after flashing completes.
- **Where:** `GuardianCrystal.updateTint()` + `GuardianCrystal.tryIcePickAttack()`.
- **Time Complexity:** O(1) per frame.
- **Space Complexity:** O(1).

## Projectile Fan Firing
- **What:** Compute base direction to player, then rotate to emit a 3-shot fan (-15°, 0°, +15°).
- **Where:** `GuardianCrystal.tryIcePickAttack()`.
- **Time Complexity:** O(1) per volley (constant number of shots).
- **Space Complexity:** O(1) auxiliary.
