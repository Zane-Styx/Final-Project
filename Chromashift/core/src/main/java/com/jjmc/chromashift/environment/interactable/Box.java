package com.jjmc.chromashift.environment.interactable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.jjmc.chromashift.player.PlayerCollision;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.player.Player;

/**
 * A simple 16x16 box with basic physics (gravity, velocity) that collides with solids
 * but does not block the player. The player can interact (press F) when close to apply an impulse.
 */
public class Box implements Interactable, Pickable {
    private float x, y;
    private float width = 16f, height = 16f;
    private float vx = 0f, vy = 0f;
    private final Array<Solid> solids;
    private Array<Interactable> interactables;
    private final Rectangle bounds;
    private boolean inRange = false;
    
    // Velocity getters/setters for collision handling
    public float getVelocityX() { return vx; }
    public float getVelocityY() { return vy; }
    public void setVelocity(float vx, float vy) { 
        this.vx = vx; 
        this.vy = vy; 
    }
    
    private boolean isCollidingWithSelf(Interactable other) {
        return other == this || (other instanceof Box && ((Box)other).holder == this.holder);
    }

    // pick-up state
    private boolean held = false;
    private Player holder = null;
    private final float holdOffsetX = 0f; // relative to player's center
    private final float holdOffsetY = 6f;

    public Box(float x, float y, Array<Solid> solids) {
        this.x = x;
        this.y = y;
        this.solids = solids;
        this.bounds = new Rectangle(x, y, width, height);
    }

    @Override
    public Rectangle getBounds() {
        return bounds;
    }

    @Override
    public void update(float delta) {
        // If currently held by a player, follow the holder and skip physics
        if (held && holder != null) {
            // position above player's hitbox center
            float centerX = holder.getHitboxX() + holder.getHitboxWidth() / 2f;
            float centerY = holder.getHitboxY() + holder.getHitboxHeight() / 2f;
            x = centerX - width / 2f + holdOffsetX;
            y = centerY + holdOffsetY;
            bounds.set(x, y, width, height);
            vx = 0f; vy = 0f;
            return;
        }

        // simple physics
        float gravity = -800f;
        vy += gravity * delta;
        
        // Apply friction/damping
        float groundFriction = 40f;  // Higher = more friction
        float airDamping = 1f;      // Higher = more air resistance
        
        // Check if on ground (vy close to 0 and collision detected)
        boolean onGround = Math.abs(vy) < 0.1f;
        if (onGround) {
            // Direct velocity reduction for ground friction
            float frictionForce = groundFriction * 60f; // Scale for better control
            if (Math.abs(vx) <= frictionForce * delta) {
                vx = 0; // Stop completely if speed is very low
            } else {
                // Apply friction in opposite direction of movement
                float direction = vx > 0 ? -1 : 1;
                vx += direction * frictionForce * delta;
            }
        } else {
            // Air resistance
            vx *= (1f - airDamping * delta);
            vy *= (1f - airDamping * delta);
        }

        // Integrate
        Rectangle before = new Rectangle(bounds);
        x += vx * delta;
        y += vy * delta;
        bounds.set(x, y, width, height);

        // Resolve collisions against solids (walls, platforms, doors)
        if (solids != null) {
            Rectangle resolved = new Rectangle(bounds);
            PlayerCollision.resolveSolidCollision(resolved, solids);

            // apply resolved position and adjust velocities if blocked
            float appliedX = resolved.x - before.x;
            float appliedY = resolved.y - before.y;

            // If horizontal corrected, zero horizontal velocity
            if (Math.abs(appliedX - (x - before.x)) > 0.001f) {
                vx = 0f;
            }
            // If vertical corrected, zero vertical velocity
            if (Math.abs(appliedY - (y - before.y)) > 0.001f) {
                vy = 0f;
            }

            x = resolved.x;
            y = resolved.y;
            bounds.set(x, y, width, height);
        }
        
        // Handle collisions with other interactables
        if (!held && interactables != null) {
            for (Interactable other : interactables) {
                if (other != this && !isCollidingWithSelf(other)) {
                    Rectangle otherBounds = other.getBounds();
                    if (bounds.overlaps(otherBounds)) {
                        // Simple elastic collision response
                        float centerX = bounds.x + bounds.width/2;
                        float centerY = bounds.y + bounds.height/2;
                        float otherCenterX = otherBounds.x + otherBounds.width/2;
                        float otherCenterY = otherBounds.y + otherBounds.height/2;
                        
                        // Direction from other to this
                        float dx = centerX - otherCenterX;
                        float dy = centerY - otherCenterY;
                        float len = (float)Math.sqrt(dx*dx + dy*dy);
                        if (len > 0.001f) {
                            dx /= len;
                            dy /= len;
                            
                            // Resolve overlap
                            float overlap = (bounds.width + otherBounds.width)/2 - len;
                            if (overlap > 0) {
                                x += dx * overlap/2;
                                y += dy * overlap/2;
                                bounds.setPosition(x, y);
                                
                                // Exchange velocities (elastic collision)
                                if (other instanceof Box || other instanceof Orb) {
                                    float tmpVx = vx;
                                    float tmpVy = vy;
                                    
                                    // Get other object's velocity if available
                                    float otherVx = 0, otherVy = 0;
                                    if (other instanceof Box b) {
                                        otherVx = b.getVelocityX();
                                        otherVy = b.getVelocityY();
                                    } else if (other instanceof Orb o) {
                                        otherVx = o.getVelocityX();
                                        otherVy = o.getVelocityY();
                                    }
                                    
                                    // Apply collision response
                                    vx = otherVx;
                                    vy = otherVy;
                                    if (other instanceof Box b) {
                                        b.setVelocity(tmpVx, tmpVy);
                                    } else if (other instanceof Orb o) {
                                        o.setVelocity(tmpVx, tmpVy);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void render(SpriteBatch batch) {
        // visual rendering handled in debug draw for now
    }

    private com.badlogic.gdx.graphics.Camera gameCamera;
    
    public void setCamera(com.badlogic.gdx.graphics.Camera camera) {
        this.gameCamera = camera;
    }
    
    @Override
    public void debugDraw(ShapeRenderer shape) {
        // Draw box
        shape.setColor(inRange ? 0.2f : 0.6f, 0.2f, 0.8f, 1f);
        shape.rect(bounds.x, bounds.y, bounds.width, bounds.height);
        
        // Draw throw guide when held
        if (held && holder != null && gameCamera != null) {
            float startX = bounds.x + bounds.width/2;
            float startY = bounds.y + bounds.height/2;
            
            // Convert mouse to world coordinates using camera
            com.badlogic.gdx.math.Vector3 mousePos = new com.badlogic.gdx.math.Vector3(
                Gdx.input.getX(), Gdx.input.getY(), 0
            );
            gameCamera.unproject(mousePos);
            float mouseX = mousePos.x;
            float mouseY = mousePos.y;
            
            float dirX = mouseX - startX;
            float dirY = mouseY - startY;
            float len = (float)Math.sqrt(dirX*dirX + dirY*dirY);
            if (len > 0.001f) {
                dirX /= len;
                dirY /= len;
                
                // Draw aim line
                shape.setColor(1f, 1f, 0.2f, 0.8f);
                float guideLen = 64f; // Length of guide line
                shape.line(startX, startY, 
                          startX + dirX * guideLen,
                          startY + dirY * guideLen);
                          
                // Draw arrow head
                float headLen = 8f;
                float angle = 0.5f; // ~30 degrees
                float ax = dirX * (float)Math.cos(angle) - dirY * (float)Math.sin(angle);
                float ay = dirX * (float)Math.sin(angle) + dirY * (float)Math.cos(angle);
                shape.line(startX + dirX * guideLen,
                          startY + dirY * guideLen,
                          startX + dirX * guideLen - ax * headLen,
                          startY + dirY * guideLen - ay * headLen);
                          
                ax = dirX * (float)Math.cos(-angle) - dirY * (float)Math.sin(-angle);
                ay = dirX * (float)Math.sin(-angle) + dirY * (float)Math.cos(-angle);
                shape.line(startX + dirX * guideLen,
                          startY + dirY * guideLen,
                          startX + dirX * guideLen - ax * headLen,
                          startY + dirY * guideLen - ay * headLen);
            }
        }
    }

    @Override
    public void interact() {
        // apply an upward impulse when player presses F
        this.vy = 240f;
    }

    // --- Pickable implementation ---
    @Override
    public void pickUp(Player player) {
        if (player == null) return;
        held = true;
        holder = player;
        vx = 0f; vy = 0f;
        // immediately position to holder
        float centerX = holder.getHitboxX() + holder.getHitboxWidth() / 2f;
        float centerY = holder.getHitboxY() + holder.getHitboxHeight() / 2f;
        x = centerX - width / 2f + holdOffsetX;
        y = centerY + holdOffsetY;
        bounds.set(x, y, width, height);
    }

    @Override
    public void throwWithVelocity(float vx, float vy) {
        // release and apply velocity
        held = false;
        holder = null;
        this.vx = vx;
        this.vy = vy;
    }

    @Override
    public boolean isHeld() { return held; }

    // Expose current holder for systems (e.g., launchpads) that need to affect both
    public Player getHolder() { return holder; }

    @Override
    public void drop() {
        held = false;
        holder = null;
    }

    @Override
    public void checkInteraction(Rectangle playerHitbox) {
        // player can interact when near (small radius)
        Rectangle r = new Rectangle(bounds.x - 8, bounds.y - 8, bounds.width + 16, bounds.height + 16);
        inRange = playerHitbox.overlaps(r);
    }

    @Override
    public boolean canInteract() {
        return inRange;
    }
    
    /**
     * Get the collision bounds used for button activation.
     * This is the actual physical bounds of the object that can press buttons.
     */
    public Rectangle getActivationBounds() {
        return bounds;
    }
}
