package com.jjmc.chromashift.healthsystem;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic HealthSystem that can be attached to any entity.
 * Responsibilities:
 * - Track current and maximum health
 * - Apply damage and healing
 * - Support temporary invulnerability
 * - Optional regeneration over time with a post-damage delay
 * - Notify registered {@link HealthListener}s about changes and death
 */
public class HealthSystem {
	private float maxHealth;
	private float currentHealth;

	// When true, damage calls will be ignored
	private boolean invulnerable = false;

	// Regeneration rate (HP per second). 0 = no regen.
	private float regenPerSecond = 0f;
	// Delay (seconds) after taking damage before regen starts
	private float regenDelayAfterDamage = 1.5f;
	// Time since last damage; used to wait before starting regen
	private float timeSinceDamage = Float.POSITIVE_INFINITY;

	private final List<HealthListener> listeners = new ArrayList<>();

	public HealthSystem(float maxHealth) {
		this.maxHealth = Math.max(1f, maxHealth);
		this.currentHealth = this.maxHealth;
	}

	// --- lifecycle / query ---
	public float getMaxHealth() { return maxHealth; }
	public float getCurrentHealth() { return currentHealth; }
	public boolean isDead() { return currentHealth <= 0f; }

	// --- configuration ---
	public void setMaxHealth(float max) {
		this.maxHealth = Math.max(1f, max);
		if (currentHealth > maxHealth) currentHealth = maxHealth;
		notifyHealthChanged(0f);
	}

	public void setHealth(float health) {
		float clamped = Math.max(0f, Math.min(maxHealth, health));
		if (clamped != currentHealth) {
			float delta = clamped - currentHealth;
			currentHealth = clamped;
			notifyHealthChanged(delta);
			if (isDead()) {
				notifyDeath(null);
			}
		}
	}

	public void setInvulnerable(boolean inv) { this.invulnerable = inv; }
	public boolean isInvulnerable() { return invulnerable; }

	public void setRegenPerSecond(float regenPerSecond) { this.regenPerSecond = Math.max(0f, regenPerSecond); }
	public void setRegenDelayAfterDamage(float delaySeconds) { this.regenDelayAfterDamage = Math.max(0f, delaySeconds); }

	// --- listeners ---
	public void addListener(HealthListener l) {
		if (l == null) return;
		if (!listeners.contains(l)) listeners.add(l);
	}
	public void removeListener(HealthListener l) { listeners.remove(l); }

	// --- actions ---
	/**
	 * Apply damage to this health system.
	 * @param amount positive damage amount
	 * @param source optional source object (attacker, trap, etc.)
	 * @return true if damage was applied (health changed)
	 */
	public boolean damage(float amount, Object source) {
		if (amount <= 0f) return false;
		if (invulnerable) return false;
		if (isDead()) return false;

		float prev = currentHealth;
		currentHealth = Math.max(0f, currentHealth - amount);
		timeSinceDamage = 0f; // reset regen timer

		float delta = currentHealth - prev; // negative
		notifyHealthChanged(delta);

		if (isDead()) {
			notifyDeath(source);
		}
		return true;
	}

	/**
	 * Heal the entity by amount. Returns true if healed (health changed).
	 */
	public boolean heal(float amount) {
		if (amount <= 0f) return false;
		if (isDead()) return false;

		float prev = currentHealth;
		currentHealth = Math.min(maxHealth, currentHealth + amount);
		float delta = currentHealth - prev; // positive
		if (delta != 0f) notifyHealthChanged(delta);
		return delta != 0f;
	}

	/**
	 * Instantly kill the entity and notify listeners.
	 */
	public void kill(Object source) {
		if (isDead()) return;
		currentHealth = 0f;
		notifyHealthChanged(-maxHealth);
		notifyDeath(source);
	}

	/**
	 * Reset health to max. Use this to revive dead entities.
	 */
	public void reset() {
		float prev = currentHealth;
		currentHealth = maxHealth;
		float delta = currentHealth - prev;
		if (delta != 0f) {
			notifyHealthChanged(delta);
		}
	}

	/**
	 * Update method; call from the owner's update loop to handle regen timing.
	 * @param delta seconds elapsed since last update
	 */
	public void update(float delta) {
		if (delta <= 0f) return;
		if (isDead()) return;

		// track time since last damage to delay regen
		if (timeSinceDamage < Float.POSITIVE_INFINITY) {
			timeSinceDamage += delta;
		} else {
			timeSinceDamage = Float.POSITIVE_INFINITY;
		}

		if (regenPerSecond > 0f && currentHealth < maxHealth && timeSinceDamage >= regenDelayAfterDamage) {
			float healAmount = regenPerSecond * delta;
			if (healAmount > 0f) heal(healAmount);
		}
	}

	// --- notifications ---
	private void notifyHealthChanged(float delta) {
		for (HealthListener l : new ArrayList<>(listeners)) {
			try { l.onHealthChanged(this, delta, currentHealth, maxHealth); } catch (Exception e) { /* swallow listener errors */ }
		}
	}

	private void notifyDeath(Object source) {
		for (HealthListener l : new ArrayList<>(listeners)) {
			try { l.onDeath(this, source); } catch (Exception e) { /* swallow */ }
		}
	}
}
