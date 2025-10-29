package com.jjmc.chromashift.player;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.Solid;

public class PlayerCollision {
	public static boolean checkWallCollision(Circle wallSensor, Array<Wall> walls) {
		for (Wall wall : walls) {
			if (com.badlogic.gdx.math.Intersector.overlaps(wallSensor, wall.bounds)) {
				return true;
			}
		}
		return false;
	}

	public static void resolveWallCollision(Rectangle playerBox, Array<Wall> walls) {
		for (Wall wall : walls) {
			if (playerBox.overlaps(wall.bounds)) {
				// Find the overlap on each axis
				float overlapX, overlapY;
				
				// Calculate overlaps
				if (playerBox.x < wall.bounds.x) {
					overlapX = playerBox.x + playerBox.width - wall.bounds.x;
				} else {
					overlapX = wall.bounds.x + wall.bounds.width - playerBox.x;
				}
				
				if (playerBox.y < wall.bounds.y) {
					overlapY = playerBox.y + playerBox.height - wall.bounds.y;
				} else {
					overlapY = wall.bounds.y + wall.bounds.height - playerBox.y;
				}
				
				// Resolve collision by moving in direction of least overlap
				if (overlapX < overlapY) {
					if (playerBox.x < wall.bounds.x) {
						playerBox.x = wall.bounds.x - playerBox.width;
					} else {
						playerBox.x = wall.bounds.x + wall.bounds.width;
					}
				} else {
					if (playerBox.y < wall.bounds.y) {
						playerBox.y = wall.bounds.y - playerBox.height;
					} else {
						playerBox.y = wall.bounds.y + wall.bounds.height;
					}
				}
			}
		}
	}

	public static void resolveSolidCollision(Rectangle hitbox, Array<Solid> solids) {
		for (Solid s : solids) {
			if (!s.isBlocking()) continue;
			Rectangle b = s.getCollisionBounds();
			if (b == null) continue;
			if (!hitbox.overlaps(b)) continue;

			// Find the overlap on each axis
			float overlapX, overlapY;
			
			// Calculate overlaps
			if (hitbox.x < b.x) {
				overlapX = hitbox.x + hitbox.width - b.x;
			} else {
				overlapX = b.x + b.width - hitbox.x;
			}
			
			if (hitbox.y < b.y) {
				overlapY = hitbox.y + hitbox.height - b.y;
			} else {
				overlapY = b.y + b.height - hitbox.y;
			}
			
			// Resolve collision by moving in direction of least overlap
			if (overlapX < overlapY) {
				if (hitbox.x < b.x) {
					hitbox.x = b.x - hitbox.width;
				} else {
					hitbox.x = b.x + b.width;
				}
			} else {
				if (hitbox.y < b.y) {
					hitbox.y = b.y - hitbox.height;
				} else {
					hitbox.y = b.y + b.height;
				}
			}
		}
	}
}
