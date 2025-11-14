package com.jjmc.chromashift.environment;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.chromashift.helper.SpriteAnimator;

/**
 * Spawn marker that is invisible by default. In editor, draw first frame.
 * In-game, call playOnce() on respawn to show a one-shot 6-frame animation,
 * then hide again when finished.
 */
public class Spawn {
	private static final String SPRITE_PATH = "environment/spawn/spawn.png";
	private static final String ANIM_NAME = "SPAWN";
	private static final int TILE_W = 32;
	private static final int TILE_H = 32;
	private static final int ROWS = 1;
	private static final int COLS = 12; // 6 frames
	private static final float FRAME_DURATION = 0.08f; // ~12.5 fps

	private final SpriteAnimator anim;
	private float x, y;
	private boolean visible = false; // hidden unless respawning

	public Spawn(float x, float y) {
		this.x = x;
		this.y = y;
		anim = new SpriteAnimator(SPRITE_PATH, ROWS, COLS);
		anim.addAnimation(ANIM_NAME, 0, 0, COLS, FRAME_DURATION, false); // one-shot
		anim.play(ANIM_NAME, false);
		anim.setFrame(0); // ensure first frame primed
	}

	public void setPosition(float x, float y) {
		this.x = x;
		this.y = y;
	}

	/** Start a one-shot animation and hide when done. */
	public void playOnce() {
		visible = true;
		anim.play(ANIM_NAME, false);
	}

	public boolean isVisible() { return visible; }

	public void update(float delta) {
		if (!visible) return;
		anim.update(delta);
		if (anim.isAnimationFinished()) {
			visible = false;
			// Reset to frame 0 for next time
			anim.play(ANIM_NAME, false);
			anim.setFrame(0);
		}
	}

	/** Draws current animation if visible. */
	public void render(SpriteBatch batch) {
		if (!visible) return;
		anim.render(batch, x, y, TILE_W, TILE_H);
	}

	/** Editor helper: draw the first frame without changing visibility. */
	public void renderFirstFrame(SpriteBatch batch) {
		anim.play(ANIM_NAME, false);
		anim.setFrame(0);
		anim.render(batch, x, y, TILE_W, TILE_H);
	}
}