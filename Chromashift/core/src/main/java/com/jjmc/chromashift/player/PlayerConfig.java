package com.jjmc.chromashift.player;

public class PlayerConfig {
	public float speed = 120f;         
	public float jumpForce = 400f;     
	public float gravity = -800f;     
	public float dashSpeed = 250f;     
	public float maxHorizontalSpeed = 600f;
	public float maxJumpVelocity = 600f;
	public float maxFallSpeed = -1200f;
	public float dashTime = 0.2f;
	public float dashCooldown = .75f;
	public float attackCooldown = 0.75f;
	public float airAttackDuration = 0.3f;
	public float airAttackLungeSpeed = 100f;
	public float wallSlideSpeed = -50f;  
	public float wallJumpForceX = 200f;  
	public float baseWidth = 32f;
	public float baseHeight = 32f;
	public float hitboxOffsetX = 7.5f;
	public float hitboxOffsetY = 0f;
	public float hitboxWidth = 17f;
	public float hitboxHeight = 20f;
	public PlayerConfig() {}
}
