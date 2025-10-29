package com.jjmc.chromashift.player;

public class PlayerConfig {
	public float speed = 150f;         // Reduced from default
	public float jumpForce = 300f;     // Initial jump velocity
	public float gravity = -800f;      // Negative because it pulls down
	public float dashSpeed = 300f;     // Reduced from 500f
	public float dashTime = 0.2f;
	public float dashCooldown = 1f;
	public float attackCooldown = 0.75f;
	public float airAttackDuration = 0.3f;
	public float airAttackLungeSpeed = 100f;
	public float wallSlideSpeed = -50f;  // Reduced from -100f
	public float wallJumpForceX = 200f;  // Reduced from 250f
	public float baseWidth = 32f;
	public float baseHeight = 32f;
	public float hitboxOffsetX = 7.5f;
	public float hitboxOffsetY = 0f;
	public float hitboxWidth = 17f;
	public float hitboxHeight = 20f;
	// Add more config fields as needed
	public PlayerConfig() {}
}
