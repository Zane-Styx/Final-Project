package com.jjmc.chromashift.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.chromashift.helper.SpriteFont;
import com.chromashift.helper.SpriteFontManager;
import com.jjmc.chromashift.Assets;
import com.jjmc.chromashift.ChromashiftGame;

/**
 * Animated Loading Screen
 * Sequence:
 * 1) Start fully black, fade in.
 * 2) Title letters drop one-by-one with bounce and slight wobble.
 * 3) Loading bar at bottom advances with AssetManager.update().
 * 4) After assets loaded AND letters settled, fade out to next screen.
 *
 * Tweakable parameters are grouped near the top.
 */
public class LoadingScreen implements Screen {
	private final ChromashiftGame game;

	// Rendering
	private SpriteBatch batch;
	private ShapeRenderer shape;
	private SpriteFont font;

	// Fade control
	// -- Durations (seconds) -- tweak here
	private float fadeInDuration = 3.0f;   // fade-in time
	private float fadeOutDuration = 3.0f;  // fade-out time
	private float fadeTimer = 0f;
	private boolean fadingIn = true;
	private boolean fadingOut = false;
	// Post full-black hold after fade-out before switching screens (tweak)
	private float postBlackHoldDuration = 0.3f; // seconds to remain black
	private float postBlackHoldTimer = 0f;
	private boolean fadeOutComplete = false;

	// Title / letters
	private String titleText = "CHROMASHIFT"; // Game name;
	// Title font styling -- tweak here
	private float titleFontScale = 6.0f;   // make title bigger
	private boolean titleBold = true;      // simulate bold by multi-pass draws
	private float boldOffsetPx = 1.2f;     // bold thickness in pixels
	// -- Anim tuning -- tweak here
	private float baseDropDelay = 0.10f;   // seconds between letter drops
	private float fallAccel = 2800f;       // downward acceleration (px/s^2)
	private float maxFallSpeed = 2400f;    // cap fall speed
	private float bounceDampen = 0.35f;    // velocity retained on bounce
	private float minBounceSpeed = 120f;   // below this, settle
	private float wobbleAmount = 6f;       // px; initial wobble amplitude
	private float wobbleFreq = 12f;        // wobble speed (radians/s)
	private float wobbleDecay = 2.2f;      // wobble exponential decay rate
	private float letterExtraSpacing = 7f; // extra spacing between letters (px)

	// Loading bar appearance -- tweak here
	private float barWidthRatio = 0.5f;         // portion of screen width
	private float barHeight = 14f;              // px
	private float barBottomMargin = 48f;        // px from bottom
	private final Color barBgOuter = new Color(0f, 0f, 0f, 0.45f);
	private final Color barBgInner = new Color(0.16f, 0.18f, 0.24f, 1f);
	private final Color barFill = new Color(0.98f, 0.76f, 0.20f, 1f);

	// Letters
	private static class Letter {
		char ch;
		final Vector2 pos = new Vector2();
		final Vector2 target = new Vector2();
		final Vector2 vel = new Vector2();
		float dropStart;      // time when this letter starts falling
		float t;              // local timer since drop start
		float wobblePhase;    // phase offset for wobble
		State state = State.WAITING;
		enum State { WAITING, FALLING, BOUNCING, SETTLED }
	}

	private Array<Letter> letters = new Array<>(true, 32, Letter.class);
	private float lettersGlobalTimer = 0f;
	private boolean lettersSettled = false;
	private float titleBaselineY; // final y baseline for drawing

	// Loading logic
	private boolean assetsQueued = false;
	private boolean assetsLoaded = false;

	// Scratch (to avoid per-frame allocations)
	private final Color scratchColor = new Color();

	public LoadingScreen(ChromashiftGame game) {
		this.game = game;
	}

	@Override
	public void show() {
		batch = new SpriteBatch();
		shape = new ShapeRenderer();
		
		// Load sprite font
		try {
			if (!SpriteFontManager.isLoaded("loading")) {
				SpriteFontManager.load("loading", "ui/ctm.uiskin.png");
			}
			font = SpriteFontManager.get("loading");
		} catch (Exception e) {
			Gdx.app.error("LoadingScreen", "Failed to load sprite font", e);
			font = null;
		}
		// Do NOT queue assets yet; wait until fade-in finishes per new requirement.

		buildLettersLayout(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		// Start fully black (fade overlay alpha = 1)
		fadingIn = true;
		fadingOut = false;
		fadeTimer = 0f;
		lettersGlobalTimer = 0f;
		lettersSettled = false;
	}

	private void buildLettersLayout(int screenW, int screenH) {
		letters.clear();
		
		if (font == null) return;

		// Measure total width for centering (using SpriteFont)
		float totalWidth = 0f;
		float charHeight = font.getHeight(titleFontScale);
		for (int i = 0; i < titleText.length(); i++) {
			char c = titleText.charAt(i);
			totalWidth += font.getWidth(String.valueOf(c), titleFontScale);
			if (i < titleText.length() - 1) totalWidth += letterExtraSpacing;
		}

		float startX = (screenW - totalWidth) / 2f;
		titleBaselineY = screenH * 0.66f; // target Y: about two-thirds up the screen

		float x = startX;
		for (int i = 0; i < titleText.length(); i++) {
			char c = titleText.charAt(i);
			float w = font.getWidth(String.valueOf(c), titleFontScale);
			float h = charHeight;

			Letter L = new Letter();
			L.ch = c;
			// Start well above screen
			L.pos.set(x, screenH + h * 2f + 80f);
			L.target.set(x, titleBaselineY);
			L.vel.set(0f, 0f);
			L.dropStart = i * baseDropDelay; // staggered start
			L.t = 0f;
			L.wobblePhase = MathUtils.random(-MathUtils.PI2, MathUtils.PI2);
			L.state = Letter.State.WAITING;
			letters.add(L);

			x += w + letterExtraSpacing;
		}
	}

	@Override
	public void render(float delta) {
		Assets.loadAll();
		// Update loading progress ONLY after fade-in finished and assets queued.
		if (!fadingIn && assetsQueued && !assetsLoaded) {
			assetsLoaded = Assets.manager.update();
		}

		// Update fades, letters, and loading bar state
		updateFadeIn(delta);
		if (!fadingIn) {
			updateLetters(delta);
		}
		updateLoadingBar(delta); // nothing to store; progress read live

		// Clear and draw
		Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		batch.begin();
		drawLetters();
		batch.end();

		shape.begin(ShapeRenderer.ShapeType.Filled);
		drawLoadingBar();
		shape.end();

		// Fade overlay on top (handles both fade-in and fade-out)
		drawFadeOverlay();

		// Start fading out immediately after the title settles
		if (!fadingOut && lettersSettled) {
			fadingOut = true;
			fadeTimer = 0f;
		}
		if (fadingOut) {
			fadeTimer += delta;
			if (!fadeOutComplete && fadeTimer >= fadeOutDuration) {
				fadeOutComplete = true;
				postBlackHoldTimer = 0f; // reset hold timer
			}
			// Once fade-out complete, wait for assets to finish then begin hold
			if (fadeOutComplete) {
				if (assetsLoaded) {
					postBlackHoldTimer += delta;
					if (postBlackHoldTimer >= postBlackHoldDuration) {
						try {
							game.setScreen(new TestMenuScreen());
						} catch (Exception e) {
							Gdx.app.error("LoadingScreen", "Failed to switch to start screen", e);
						}
					}
				}
				// If assets not loaded yet, stay at full black until they are.
		
            }
        }
	}

	// ---------------- Update methods ----------------
	private void updateFadeIn(float delta) {
		if (!fadingIn) return;
		fadeTimer += delta;
		if (fadeTimer >= fadeInDuration) {
			fadeTimer = fadeInDuration;
			fadingIn = false;
			// Reset letter timer to start drops right after fade-in completes
			lettersGlobalTimer = 0f;
			// Queue assets ONLY now (delayed start) so loading begins after fade-in.
			if (!assetsQueued) {
				Assets.queueAll();
				assetsQueued = true;
			}
		}
	}

	private void updateLetters(float delta) {
		lettersGlobalTimer += delta;
		boolean allSettled = true;

		for (int i = 0; i < letters.size; i++) {
			Letter L = letters.get(i);
			switch (L.state) {
				case WAITING: {
					if (lettersGlobalTimer >= L.dropStart) {
						L.state = Letter.State.FALLING;
						L.t = 0f;
					} else {
						allSettled = false;
					}
				} break;
				case FALLING: {
					allSettled = false;
					L.t += delta;
					// Apply gravity
					L.vel.y -= fallAccel * delta;
					if (L.vel.y < -maxFallSpeed) L.vel.y = -maxFallSpeed;
					L.pos.y += L.vel.y * delta;

					// Wobble while falling (decays over time)
					float wobble = wobbleAmount * (float)Math.exp(-wobbleDecay * L.t) * MathUtils.sin(wobbleFreq * L.t + L.wobblePhase);
					L.pos.x = L.target.x + wobble;

					// Hit the floor (target baseline)
					if (L.pos.y <= L.target.y) {
						L.pos.y = L.target.y;
						if (-L.vel.y > minBounceSpeed) {
							L.vel.y = -L.vel.y * bounceDampen;
							L.state = Letter.State.BOUNCING;
						} else {
							L.vel.y = 0f;
							L.pos.set(L.target);
							L.state = Letter.State.SETTLED;
						}
					}
				} break;
				case BOUNCING: {
					allSettled = false;
					L.t += delta;
					// Gravity continues
					L.vel.y -= fallAccel * delta;
					if (L.vel.y < -maxFallSpeed) L.vel.y = -maxFallSpeed;
					L.pos.y += L.vel.y * delta;
					// Minor horizontal decay to center while bouncing
					float wobble = wobbleAmount * 0.5f * (float)Math.exp(-wobbleDecay * (L.t + 0.4f)) * MathUtils.sin(wobbleFreq * L.t + L.wobblePhase);
					L.pos.x = MathUtils.lerp(L.pos.x, L.target.x + wobble, 0.15f);

					if (L.pos.y <= L.target.y) {
						L.pos.y = L.target.y;
						if (-L.vel.y > minBounceSpeed) {
							L.vel.y = -L.vel.y * bounceDampen;
						} else {
							L.vel.y = 0f;
							L.pos.set(L.target);
							L.state = Letter.State.SETTLED;
						}
					}
				} break;
				case SETTLED: {
					// stay
				} break;
			}
		}

		lettersSettled = allSettled;
	}

	private void updateLoadingBar(float delta) {
		// No state to keep; progress read directly from AssetManager in draw
	}

	// ---------------- Draw methods ----------------
	private void drawLetters() {
		if (font == null) return;
		
		font.setColor(Color.WHITE);
		for (int i = 0; i < letters.size; i++) {
			Letter L = letters.get(i);
			String charStr = String.valueOf(L.ch);
			// Draw baseline-aligned text at L.pos (treat as bottom-left for simplicity)
			float drawX = L.pos.x;
			float drawY = L.pos.y;
			if (titleBold) {
				// Simple faux-bold by drawing around the center in 4 directions
				float o = boldOffsetPx;
				font.draw(batch, charStr, drawX - o, drawY, titleFontScale);
				font.draw(batch, charStr, drawX + o, drawY, titleFontScale);
				font.draw(batch, charStr, drawX, drawY - o, titleFontScale);
				font.draw(batch, charStr, drawX, drawY + o, titleFontScale);
			}
			font.draw(batch, charStr, drawX, drawY, titleFontScale);
		}
	}

	private void drawLoadingBar() {
		float w = Gdx.graphics.getWidth();
		@SuppressWarnings("unused")
		float h = Gdx.graphics.getHeight();
		float barW = Math.max(160f, w * barWidthRatio);
		float barX = (w - barW) * 0.5f;
		float barY = barBottomMargin;

		// If assets not queued yet (during fade-in), treat progress as 0 and optionally skip drawing fill.
		float progress = assetsQueued ? Assets.manager.getProgress() : 0f; // 0..1

		// Outer shadow
		shape.setColor(barBgOuter);
		shape.rect(barX - 4, barY - 4, barW + 8, barHeight + 8);
		// Background
		shape.setColor(barBgInner);
		shape.rect(barX, barY, barW, barHeight);
		// Fill
		// Fill only once loading started
		shape.setColor(barFill);
		shape.rect(barX, barY, barW * progress, barHeight);
	}

	private void drawFadeOverlay() {
		float alpha;
		if (fadingIn) {
			alpha = 1f - MathUtils.clamp(fadeTimer / fadeInDuration, 0f, 1f);
		} else if (fadingOut) {
			alpha = MathUtils.clamp(fadeTimer / fadeOutDuration, 0f, 1f);
		} else {
			alpha = 0f;
		}

		if (alpha <= 0f) return;

		// Draw full-screen black quad with alpha
		Gdx.gl.glEnable(GL20.GL_BLEND);
		shape.begin(ShapeRenderer.ShapeType.Filled);
		scratchColor.set(0f, 0f, 0f, alpha);
		shape.setColor(scratchColor);
		shape.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		shape.end();
		Gdx.gl.glDisable(GL20.GL_BLEND);
	}

	@Override
	public void resize(int width, int height) {
		// Rebuild layout to remain centered on resize
		buildLettersLayout(width, height);
	}
	@Override public void pause() { }
	@Override public void resume() { }
	@Override public void hide() { }

	@Override
	public void dispose() {
		if (batch != null) batch.dispose();
		if (shape != null) shape.dispose();
		// SpriteFont is managed by SpriteFontManager, don't dispose directly
	}
}

