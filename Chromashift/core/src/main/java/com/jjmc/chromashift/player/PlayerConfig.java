package com.jjmc.chromashift.player;

public class PlayerConfig {
	public float speed = 120f;         // Reduced from 150f for better control
	public float jumpForce = 400f;     // Reduced from 300f for better control
	public float gravity = -800f;      // Reduced from -800f for smoother falling
	public float dashSpeed = 250f;     // Reduced from 300f for better control
	// Maximum (most negative) fall speed. Make positive magnitude larger for faster falls.
	public float maxFallSpeed = -1200f;
	public float dashTime = 0.2f;
	public float dashCooldown = .75f;
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
