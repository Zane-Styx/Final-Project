package com.jjmc.chromashift.environment.enemy;

import com.badlogic.gdx.math.Rectangle;
import com.jjmc.chromashift.entity.boss.BossGuardian;

/**
 * Adapter to expose BossGuardian guardians as Enemy instances for player attacks/projectiles.
 * Each adapter instance wraps a single guardian index (1..3).
 */
public class BossGuardianEnemyAdapter implements Enemy {
    private final BossGuardian boss;
    private final int guardianIndex; // 1..3

    public BossGuardianEnemyAdapter(BossGuardian boss, int guardianIndex) {
        this.boss = boss;
        this.guardianIndex = guardianIndex;
    }

    @Override
    public boolean isAlive() {
        return boss != null && boss.getGuardianHealth(guardianIndex) > 0f;
    }

    @Override
    public Rectangle getBounds() {
        if (boss == null) return null;
        if (!isAlive()) return null;
        return boss.getGuardianHitbox(guardianIndex);
    }

    @Override
    public void takeDamage(float amount) {
        if (boss == null) return;
        boss.damageGuardian(guardianIndex, amount);
    }

    // Optional: expose health if Enemy supports it
    @Override
    public float getHealth() {
        if (boss == null) return 0f;
        return boss.getGuardianHealth(guardianIndex);
    }
}
