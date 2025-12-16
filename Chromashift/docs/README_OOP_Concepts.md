# OOP Concepts Applied

This document explains only the OOP concepts present in the codebase, with concrete references.

## Encapsulation
- **Hidden Data:**
  - `Player` fields such as position (`x`, `y`), velocities, state flags (`onGround`, `dashing`, `isStunned`), and configuration (`PlayerConfig`) are `private`/`protected`.
  - `GuardianCrystal` internal state like `hitsRemaining`, `grounded`, `cooldowns`, and projectile/wall arrays.
  - `IceWall` encapsulates its damage timing (`damageApplied`, `lifetime`) and hitbox.
- **Why:** Prevent external modules from mutating core state unsafely; expose controlled APIs for interaction.
- **Getters/Setters:**
  - `Player` provides getters like `getHitboxRect()`, `getVelocityY()`, and setters like `setVelocityX()`, `setOnGround()` to control movement and collision outcomes.
  - `Player` exposes `applySlow(multiplier, duration)` for controlled movement modifiers.
  - `GuardianCrystal.getBounds()` returns a safe rectangle (zero-sized when dead) instead of `null`.

## Inheritance
- **Interfaces Implemented:**
  - `GuardianCrystal implements Enemy` — conforms to the enemy contract (`takeDamage()`, `isAlive()`, `getBounds()`).
- **Anonymous Class Extension:**
  - `Player` creates a `HealthSystem` instance via an anonymous subclass to override `damage()` behavior (shield/invulnerability handling) and attach listeners.
- **Purpose:** Provide common contracts for enemies; allow custom player health behavior while reusing generic health management.

## Polymorphism
- **Method Overriding:**
  - `HealthSystem.damage()` overridden in `Player` to implement shield logic and invulnerability checks.
- **Interface-Based Polymorphism:**
  - Code consuming `Enemy` can operate on `GuardianCrystal` without knowing its concrete type (e.g., bounds, alive state, damage application).
- **Dynamic Type Checks:**
  - `Player` uses `instanceof` (e.g., `Solid` → `Wall`) to build wall arrays from solids for collision handling.

## Abstraction
- **Abstracted Behavior via Interfaces/Contracts:**
  - `Enemy` abstracts enemy behavior (health, bounds), enabling different enemy implementations to be updated and checked consistently.
- **Helper/Utility Abstractions:**
  - `SpriteAnimator` abstracts sprite animation handling away from entity logic, providing `addAnimation()`, `play()`, `update()`, `render()`.
  - `HealthSystem` abstracts health management (regen, invulnerability, listeners) from gameplay entities.
