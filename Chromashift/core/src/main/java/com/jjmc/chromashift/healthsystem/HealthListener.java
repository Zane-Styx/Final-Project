package com.jjmc.chromashift.healthsystem;

/**
 * Listener interface for health changes and death events.
 */
public interface HealthListener {
    /**
     * Called when health changes. delta is newHealth - previousHealth (can be negative for damage).
     */
    void onHealthChanged(HealthSystem healthSystem, float delta, float currentHealth, float maxHealth);

    /**
     * Called when the health system reached zero and the owner should be considered dead.
     * @param source optional source object that caused the death
     */
    void onDeath(HealthSystem healthSystem, Object source);
}
