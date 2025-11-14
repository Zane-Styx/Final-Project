package com.jjmc.chromashift.screens.levels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.files.FileHandle;
import com.jjmc.chromashift.environment.Wall;
import com.jjmc.chromashift.environment.Solid;
import com.jjmc.chromashift.environment.Spawn;
import com.jjmc.chromashift.environment.interactable.Button;
import com.jjmc.chromashift.environment.interactable.Door;
import com.jjmc.chromashift.environment.interactable.Lever;
import com.jjmc.chromashift.environment.interactable.Interactable;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.screens.Initialize;
import com.jjmc.chromashift.entity.boss.BossInstance;

import java.util.Locale;

/**
 * Lightweight level maker/editor.
 * Flow:
 * 1) Choose level to edit with Shift+1/2/3, or press B (bossroom) / T
 * (tutorial).
 * 2) No player is created. Camera is free and controlled by WASD keys.
 * 3) Choose object type (1..8 on the number row). Move mouse to position; it
 * snaps to 32x32 grid.
 * - For variable-size objects (Wall/Door) adjust width/height with Arrow keys
 * - Left click to confirm placement, right click to cancel
 * 4) Press Ctrl+S to save level (writes to local levels/<name>.json via
 * LevelIO.save and to project assets/levels)
 */
public class LevelMakerScreen implements Screen {
	private Initialize.Context ctx;
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private ShapeRenderer shape;
	private BitmapFont font;

	// UI camera for screen-fixed UI
	private OrthographicCamera uiCamera;

	// level list UI
	private Array<String> levelFiles = new Array<>();
	private Array<Rectangle> levelRects = new Array<>();
	private int hoveredLevelIndex = -1;

	private LevelIO.LevelState state;
	private String currentLevelPath = "levels/level1.json";
	private boolean levelSelected = false;

	private enum ObjectType {
		WALL, DOOR, BUTTON, LEVER, BOX, ORB, BOSS, SPAWN, LAUNCHPAD, NONE
	}

	private ObjectType selectedType = ObjectType.NONE;

	// preview placement
	private int previewCols = 1, previewRows = 1;

	// collections to render interactables immediately (from LevelLoader)
	private Array<Wall> walls = new Array<>();
	private Array<Solid> solids = new Array<>();
	private Array<Interactable> interactableInstances = new Array<>();
	private BossInstance bossInstance;
	private Spawn spawnPreview;

	// quick visual feedback for placements
	private static class Flash {
		float x, y, w, h, ttl, max;

		Flash(float x, float y, float w, float h, float ttl) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.ttl = ttl;
			this.max = ttl;
		}
	}

	private final Array<Flash> placementFlashes = new Array<>();
	private String toastText = null;
	private float toastTimer = 0f;

	// keep simple placement history for undo/erase
	private static class Placement {
		ObjectType type;
		int x, y, cols, rows;
		String id; // for doors

		Placement(ObjectType t, int x, int y, int c, int r, String id) {
			this.type = t;
			this.x = x;
			this.y = y;
			this.cols = c;
			this.rows = r;
			this.id = id;
		}
	}

	private Array<Placement> placements = new Array<>();

	// Delete mode: toggle with 'E' to show an outline brush and delete on
	// left-click
	private boolean deleteMode = false;

	// door quick lookup
	private static class DoorRecord {
		String id;
		float x, y;
		int cols, rows;

		DoorRecord(String id, float x, float y, int c, int r) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.cols = c;
			this.rows = r;
		}
	}

	private Array<DoorRecord> doorRecords = new Array<>();
	// door speed presets for placement
	private float selectedDoorOpenSpeed = 3f;
	private float selectedDoorCloseSpeed = 3f;
	private Rectangle doorOpenMinusRect, doorOpenPlusRect, doorCloseMinusRect, doorClosePlusRect;
	// preview collision state
	private boolean previewBlocked = false;

	// Button color selection for preview/placement
	private Button.ButtonColor selectedButtonColor = Button.ButtonColor.GREEN;
	private Array<Rectangle> buttonColorRects = new Array<>();
	private com.chromashift.helper.SpriteAnimator previewButtonAnim;

	// Launchpad direction selection
	private com.jjmc.chromashift.environment.Launchpad.LaunchDirection selectedLaunchpadDirection = com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP;
	private float selectedLaunchpadSpeed = 600f;
	private Array<Rectangle> launchpadDirRects = new Array<>();

	// lever orientation UI and preview
	private boolean selectedLeverHorizontal = false;
	private Rectangle leverOrientRect;
	private Rectangle linkToggleRect;
	private boolean linkingMode = false;

	private enum LinkStage {
		PICK_SOURCE, PICK_DOORS
	}

	private LinkStage linkStage = LinkStage.PICK_SOURCE;
	private ObjectType linkSourceType = ObjectType.NONE;
	private int linkSourceX = 0, linkSourceY = 0;
	private Array<String> linkSelectedDoorIds = new Array<>();
	private com.chromashift.helper.SpriteAnimator previewLeverAnim;
	// Door speed sliders and state
	private Rectangle openSpeedSliderRect;
	private Rectangle closeSpeedSliderRect;
	private boolean draggingOpenSlider = false;
	private boolean draggingCloseSlider = false;
	private boolean wasLeftPressed = false;
	private final float DOOR_SPEED_MIN = 0.5f;
	private final float DOOR_SPEED_MAX = 12f;

	// camera pan speed
	private float camSpeed = 400f;

	// UI buttons
	private static class UIButton {
		Rectangle rect;
		ObjectType type;
		String label;

		UIButton(Rectangle r, ObjectType t, String l) {
			rect = r;
			type = t;
			label = l;
		}
	}

	private Array<UIButton> uiButtons = new Array<>();
	private Array<Rectangle> doorDirButtons = new Array<>();
	private final String[] doorDirections = new String[] { "UP", "DOWN", "LEFT", "RIGHT" };
	private int selectedDoorDirIndex = 0;

	@Override
	public void show() {
		ctx = Initialize.createCommon(800, 480, null);
		camera = ctx.camera;
		batch = ctx.batch;
		shape = ctx.shape;
		font = ctx.font;

		// set up UI camera (screen-fixed) and build level list UI
		uiCamera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		uiCamera.setToOrtho(false);
		uiCamera.position.set(uiCamera.viewportWidth / 2f, uiCamera.viewportHeight / 2f, 0f);
		uiCamera.update();

		loadLevel(currentLevelPath);

		// build UI buttons at BOTTOM center (Minecraft inventory style)
		float bw = 60f, bh = 28f, gap = 8f;
		// Added Launchpad + Link button (10 buttons total)
		float totalWidth = 10 * bw + 9 * gap;
		float bx = (uiCamera.viewportWidth - totalWidth) / 2f;
		float by = 20f; // 20px from bottom
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.WALL, "Wall"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.DOOR, "Door"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BUTTON, "Button"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.LEVER, "Lever"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BOX, "Box"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.ORB, "Orb"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BOSS, "Boss"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.SPAWN, "Spawn"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.LAUNCHPAD, "Launch"));
		bx += bw + gap;
		// Linking mode toggle button (works for Button/Lever)
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.NONE, "Link"));

		// Contextual option buttons - positioned above main buttons but only shown when
		// relevant type selected
		// Door direction buttons (shown only when Door selected)
		float optionY = by + bh + 8f; // 8px above main buttons
		float optionX = (uiCamera.viewportWidth - (4 * bw + 3 * gap)) / 2f;
		for (int i = 0; i < doorDirections.length; i++) {
			doorDirButtons.add(new Rectangle(optionX + i * (bw + gap), optionY, bw, bh));
		}

		// door speed controls (open/close) - small +/- boxes to the right of options
		float speedW = 28f, speedH = 20f, speedGap = 6f;
		float speedStartX = optionX + (4 * (bw + gap));
		// Raise the speed controls further above direction buttons
		float speedShiftY = 300f; // increased vertical offset
		doorOpenMinusRect = new Rectangle(speedStartX, optionY + speedShiftY, speedW, speedH);
		doorOpenPlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + speedShiftY, speedW, speedH);
		doorCloseMinusRect = new Rectangle(speedStartX, optionY + speedShiftY - (speedH + 4f), speedW, speedH);
		doorClosePlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + speedShiftY - (speedH + 4f),
				speedW, speedH);

		// Button color swatches (shown only when Button selected)
		float sw = 40f, sh = 24f, sg = 6f;
		Button.ButtonColor[] cols = Button.ButtonColor.values();
		float colorsWidth = cols.length * sw + (cols.length - 1) * sg;
		float cx = (uiCamera.viewportWidth - colorsWidth) / 2f;
		for (int i = 0; i < cols.length; i++) {
			buttonColorRects.add(new Rectangle(cx + i * (sw + sg), optionY, sw, sh));
		}

		// Lever orientation toggle (shown only when Lever selected)
		leverOrientRect = new Rectangle((uiCamera.viewportWidth - 80f) / 2f, optionY, 80f, sh);

		// Launchpad direction buttons (shown only when Launchpad selected)
		// 3 directions: UP, LEFT, RIGHT
		launchpadDirRects.clear();
		float lpDirW = 50f, lpDirH = 24f, lpDirGap = 4f;
		float lpDirsWidth = 3 * lpDirW + 2 * lpDirGap;
		float lpDirX = (uiCamera.viewportWidth - lpDirsWidth) / 2f;
		for (int i = 0; i < 3; i++) {
			launchpadDirRects.add(new Rectangle(lpDirX + i * (lpDirW + lpDirGap), optionY, lpDirW, lpDirH));
		}

		// Sliders for door open/close speed (raise further for clear separation)
		float sliderW = 220f, sliderH = 14f;
		float sliderX = (uiCamera.viewportWidth - sliderW) / 2f;
		// Place sliders well above speed buttons
		float sliderY = optionY + 200f; // open speed slider higher
		openSpeedSliderRect = new Rectangle(sliderX, sliderY, sliderW, sliderH);
		// close speed slider below open slider
		closeSpeedSliderRect = new Rectangle(sliderX, sliderY - (sliderH + 10f), sliderW, sliderH);
		// (Deprecated: link toggle rect replaced by global Link button)
		linkToggleRect = null;

		// preview animator for buttons (reuse SpriteAnimator to draw actual image in UI
		// preview)
		try {
			previewButtonAnim = new com.chromashift.helper.SpriteAnimator("environment/button/buttons.png", 5, 2);
			// add one animation per color row so we can switch quickly
			for (Button.ButtonColor bc : Button.ButtonColor.values()) {
				previewButtonAnim.addAnimation(bc.name(), bc.getRowIndex(), 0, 2, 0.1f, false);
			}
			previewButtonAnim.play(selectedButtonColor.name(), false);
			previewButtonAnim.setFrame(0);
		} catch (Exception ignored) {
		}

		// preview animator for lever (2 rows: vertical, horizontal; 2 cols)
		try {
			previewLeverAnim = new com.chromashift.helper.SpriteAnimator("environment/lever/lever.png", 2, 2);
			previewLeverAnim.addAnimation("VERTICAL", 0, 0, 2, 0.1f, false);
			previewLeverAnim.addAnimation("HORIZONTAL", 1, 0, 2, 0.1f, false);
			previewLeverAnim.play(selectedLeverHorizontal ? "HORIZONTAL" : "VERTICAL", false);
			previewLeverAnim.setFrame(0);
		} catch (Exception ignored) {
		}

		// list available level files from internal assets/levels
		levelFiles.clear();
		levelRects.clear();
		FileHandle dir = Gdx.files.internal("levels");
		if (dir != null && dir.exists()) {
			FileHandle[] files = dir.list();
			float lx = uiCamera.position.x + uiCamera.viewportWidth / 2f - 180f;
			float ly = uiCamera.position.y + uiCamera.viewportHeight / 2f - 32f;
			float lh = 20f, lgap = 4f;
			for (int i = 0; i < files.length; i++) {
				levelFiles.add(files[i].name());
				levelRects.add(new Rectangle(lx, ly - i * (lh + lgap), 170f, lh));
			}
		}
	}

	private int snap32(float v) {
		return (int) Math.floor(v / 32f) * 32;
	}

	/**
	 * Get preview bottom-left in UI (screen) coordinates by snapping the mouse
	 * world position
	 * to the world 32px grid, then projecting that world cell back to screen pixels
	 * and into
	 * UI coordinates. This makes the preview snap to the world grid while remaining
	 * drawn in UI space.
	 */
	private Vector2 getSnappedPreviewScreenBL() {
		// unproject mouse to world
		com.badlogic.gdx.math.Vector3 mv = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
		camera.unproject(mv);
		float wx = (float) Math.floor(mv.x / 32f) * 32f;
		float wy = (float) Math.floor(mv.y / 32f) * 32f;
		// project the snapped world cell to screen pixels
		com.badlogic.gdx.math.Vector3 pv = new com.badlogic.gdx.math.Vector3(wx, wy, 0);
		camera.project(pv);
		// camera.project returns screen pixels with a Y origin opposite to the Input Y.
		// Flip Y to convert to the UI/input coordinate space before unprojecting with
		// uiCamera.
		pv.y = Gdx.graphics.getHeight() - pv.y;
		// convert screen pixels to uiCamera space for drawing
		com.badlogic.gdx.math.Vector3 uv = new com.badlogic.gdx.math.Vector3(pv.x, pv.y, 0);
		uiCamera.unproject(uv);
		return new Vector2(uv.x, uv.y);
	}

	/**
	 * Return true if the provided world-space rectangle does not overlap any
	 * existing placed object.
	 */
	private boolean isAreaFree(Rectangle worldArea) {
		// check walls
		for (Wall w : walls) {
			if (w.getBounds().overlaps(worldArea))
				return false;
		}
		// check interactables
		for (Interactable it : interactableInstances) {
			if (it.getBounds().overlaps(worldArea))
				return false;
		}
		// boss (use saved boss coords if present)
		if (state != null && state.boss != null) {
			Rectangle b = new Rectangle(state.boss.x - 48f, state.boss.y, 96f, 96f);
			if (b.overlaps(worldArea))
				return false;
		}
		// spawn
		if (state != null && state.spawn != null) {
			Rectangle sp = new Rectangle(state.spawn.x, state.spawn.y, 16f, 32f);
			if (sp.overlaps(worldArea))
				return false;
		}
		return true;
	}

	/**
	 * Backwards-compatible alias used by render(); returns the preview bottom-left
	 * position in UI (screen) coordinates.
	 * This delegates to getSnappedPreviewScreenBL() so existing code using
	 * getPreviewScreenBL() works as expected.
	 */
	private Vector2 getPreviewScreenBL() {
		return getSnappedPreviewScreenBL();
	}

	/**
	 * Convert the mouse screen position to the bottom-left world-grid cell (floored
	 * to 32px).
	 * This ensures placement/erase use the same world grid the preview snaps to.
	 */
	private Vector2 screenCellBottomLeftToWorldGrid() {
		com.badlogic.gdx.math.Vector3 mv = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
		camera.unproject(mv);
		int gx = (int) Math.floor(mv.x / 32f) * 32;
		int gy = (int) Math.floor(mv.y / 32f) * 32;
		return new Vector2(gx, gy);
	}

	@Override
	public void render(float delta) {
		handleInput(delta);
		// update ephemeral placement feedback
		for (int i = placementFlashes.size - 1; i >= 0; --i) {
			Flash f = placementFlashes.get(i);
			f.ttl -= delta;
			if (f.ttl <= 0f)
				placementFlashes.removeIndex(i);
		}
		if (toastTimer > 0f) {
			toastTimer -= delta;
			if (toastTimer <= 0f) {
				toastTimer = 0f;
				toastText = null;
			}
		}
		Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		// ensure world rendering uses the main camera
		shape.setProjectionMatrix(camera.combined);
		batch.setProjectionMatrix(camera.combined);

		// draw placement grid lightly
		shape.begin(ShapeRenderer.ShapeType.Line);
		shape.setColor(0.12f, 0.12f, 0.12f, 1f);
		float left = camera.position.x - camera.viewportWidth / 2f;
		float right = camera.position.x + camera.viewportWidth / 2f;
		float bottom = camera.position.y - camera.viewportHeight / 2f;
		float top = camera.position.y + camera.viewportHeight / 2f;
		for (int x = snap32(left); x <= right + 32; x += 32)
			shape.line(x, bottom - 10000, x, top + 10000);
		for (int y = snap32(bottom); y <= top + 32; y += 32)
			shape.line(left - 10000, y, right + 10000, y);
		shape.end();

		// draw existing walls and interactables
		batch.begin();
		for (Wall w : walls)
			w.render(batch);
		for (Interactable i : interactableInstances) {
			i.render(batch);
		}
		if (spawnPreview != null) {
			// Editor: always show first frame (non-animated)
			spawnPreview.renderFirstFrame(batch);
		}
		if (bossInstance != null)
			bossInstance.render(batch);
		batch.end(); // preview of the current object under mouse (drawn in UI space so it stays
						// under cursor)

		// draw placement flashes in world space
		if (placementFlashes.size > 0) {
			shape.setProjectionMatrix(camera.combined);
			shape.begin(ShapeRenderer.ShapeType.Line);
			for (int i = 0; i < placementFlashes.size; i++) {
				Flash f = placementFlashes.get(i);
				float a = (f.max <= 0f) ? 0f : Math.max(0f, Math.min(1f, f.ttl / f.max));
				shape.setColor(1f, 1f, 0f, 0.25f + 0.6f * a);
				shape.rect(f.x, f.y, f.w, f.h);
			}
			shape.end();
		}
		Vector2 screenPreview = getPreviewScreenBL();
		float screenGx = screenPreview.x;
		float screenGy = screenPreview.y;
		// also compute preview in world coords to detect overlaps
		Vector2 worldPreview = screenCellBottomLeftToWorldGrid();
		float previewW = 32f * previewCols;
		float previewH = 32f * previewRows;
		// adjust sizes for non-grid objects
		switch (selectedType) {
			case BUTTON -> {
				previewW = 64f;
				previewH = 32f;
			}
			case LEVER -> {
				previewW = 16f;
				previewH = 36f;
			}
			case BOX -> {
				previewW = 32f;
				previewH = 32f;
			}
			case ORB -> {
				previewW = 24f;
				previewH = 24f;
			}
			case BOSS -> {
				previewW = 96f;
				previewH = 96f;
			}
			case SPAWN -> {
				previewW = 16f;
				previewH = 32f;
			}
			case LAUNCHPAD -> {
				if (selectedLaunchpadDirection == com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP) {
					previewW = 64f;
					previewH = 32f;
				} else {
					previewW = 32f;
					previewH = 64f;
				}
			}
			default -> {
			}
		}
		Rectangle previewWorldRect = new Rectangle(worldPreview.x, worldPreview.y, previewW, previewH);
		previewBlocked = !isAreaFree(previewWorldRect);

		// draw UI (screen-fixed) using uiCamera
		shape.setProjectionMatrix(uiCamera.combined);
		batch.setProjectionMatrix(uiCamera.combined);
		shape.begin(ShapeRenderer.ShapeType.Filled);
		// Main object type buttons (always visible)
		for (UIButton b : uiButtons) {
			Color c = (selectedType == b.type) ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f);
			shape.setColor(c);
			shape.rect(b.rect.x, b.rect.y, b.rect.width, b.rect.height);
			// Border for clarity
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(Color.WHITE);
			shape.rect(b.rect.x, b.rect.y, b.rect.width, b.rect.height);
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Filled);
		}

		// Contextual options - only show for selected type
		if (selectedType == ObjectType.DOOR) {
			// door direction buttons
			for (int i = 0; i < doorDirButtons.size; i++) {
				Rectangle r = doorDirButtons.get(i);
				shape.setColor((i == selectedDoorDirIndex) ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
			// draw sliders for open/close speed
			if (openSpeedSliderRect != null) {
				// background bar
				shape.setColor(new Color(0.12f, 0.12f, 0.12f, 1f));
				shape.rect(openSpeedSliderRect.x, openSpeedSliderRect.y, openSpeedSliderRect.width,
						openSpeedSliderRect.height);
				// filled portion
				float frac = (selectedDoorOpenSpeed - DOOR_SPEED_MIN) / (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
				frac = Math.max(0f, Math.min(1f, frac));
				shape.setColor(Color.GREEN);
				shape.rect(openSpeedSliderRect.x, openSpeedSliderRect.y, openSpeedSliderRect.width * frac,
						openSpeedSliderRect.height);
				// thumb
				float tx = openSpeedSliderRect.x + openSpeedSliderRect.width * frac;
				shape.setColor(Color.WHITE);
				shape.rect(tx - 3, openSpeedSliderRect.y - 4, 6, openSpeedSliderRect.height + 8);
			}
			if (closeSpeedSliderRect != null) {
				shape.setColor(new Color(0.12f, 0.12f, 0.12f, 1f));
				shape.rect(closeSpeedSliderRect.x, closeSpeedSliderRect.y, closeSpeedSliderRect.width,
						closeSpeedSliderRect.height);
				float frac2 = (selectedDoorCloseSpeed - DOOR_SPEED_MIN) / (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
				frac2 = Math.max(0f, Math.min(1f, frac2));
				shape.setColor(Color.ORANGE);
				shape.rect(closeSpeedSliderRect.x, closeSpeedSliderRect.y, closeSpeedSliderRect.width * frac2,
						closeSpeedSliderRect.height);
				float tx2 = closeSpeedSliderRect.x + closeSpeedSliderRect.width * frac2;
				shape.setColor(Color.WHITE);
				shape.rect(tx2 - 3, closeSpeedSliderRect.y - 4, 6, closeSpeedSliderRect.height + 8);
			}
		} else if (selectedType == ObjectType.BUTTON) {
			// button color swatches
			for (int i = 0; i < buttonColorRects.size; i++) {
				Rectangle r = buttonColorRects.get(i);
				Button.ButtonColor bc = Button.ButtonColor.values()[i];
				Color c = Color.WHITE;
				switch (bc) {
					case RED -> c = Color.RED;
					case BLUE -> c = Color.BLUE;
					case GREEN -> c = Color.GREEN;
					case YELLOW -> c = Color.YELLOW;
					case PURPLE -> c = Color.PURPLE;
				}
				shape.setColor(c);
				shape.rect(r.x, r.y, r.width, r.height);
				// highlight selected with border
				if (bc == selectedButtonColor) {
					shape.end();
					shape.begin(ShapeRenderer.ShapeType.Line);
					Gdx.gl.glLineWidth(3f);
					shape.setColor(Color.WHITE);
					shape.rect(r.x - 2, r.y - 2, r.width + 4, r.height + 4);
					Gdx.gl.glLineWidth(1f);
					shape.end();
					shape.begin(ShapeRenderer.ShapeType.Filled);
				}
			}
		} else if (selectedType == ObjectType.LEVER) {
			// lever orientation toggle
			if (leverOrientRect != null) {
				shape.setColor(selectedLeverHorizontal ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(leverOrientRect.x, leverOrientRect.y, leverOrientRect.width, leverOrientRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(leverOrientRect.x, leverOrientRect.y, leverOrientRect.width, leverOrientRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
			// (Link toggle moved to dedicated 'Link' button in main bar)
		} else if (selectedType == ObjectType.LAUNCHPAD) {
			// launchpad direction buttons
			com.jjmc.chromashift.environment.Launchpad.LaunchDirection[] dirs = com.jjmc.chromashift.environment.Launchpad.LaunchDirection
					.values();
			for (int i = 0; i < launchpadDirRects.size && i < dirs.length; i++) {
				Rectangle r = launchpadDirRects.get(i);
				boolean selected = (dirs[i] == selectedLaunchpadDirection);
				shape.setColor(selected ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
		}
		// level list background
		for (Rectangle lr : levelRects) {
			shape.setColor(0f, 0f, 0f, 0.5f);
			shape.rect(lr.x - 4, lr.y - 2, lr.width + 8, lr.height + 4);
		}
		shape.end();

		batch.begin();
		// Main button labels (always visible)
		for (UIButton b : uiButtons) {
			font.setColor(Color.WHITE);
			font.draw(batch, b.label, b.rect.x + 6, b.rect.y + b.rect.height - 8);
		}

		// Contextual option labels - only show for selected type
		if (selectedType == ObjectType.DOOR) {
			// door direction labels
			for (int i = 0; i < doorDirButtons.size; i++) {
				Rectangle r = doorDirButtons.get(i);
				font.setColor(Color.WHITE);
				font.draw(batch, doorDirections[i], r.x + 8, r.y + r.height - 8);
			}
			// door speed labels
			if (doorOpenMinusRect != null) {
				font.setColor(Color.WHITE);
				font.draw(batch, "-", doorOpenMinusRect.x + 8, doorOpenMinusRect.y + doorOpenMinusRect.height - 4);
				font.draw(batch, "+", doorOpenPlusRect.x + 8, doorOpenPlusRect.y + doorOpenPlusRect.height - 4);
				font.draw(batch, String.format("Open: %.1f", selectedDoorOpenSpeed), doorOpenPlusRect.x + 60,
						doorOpenPlusRect.y + doorOpenPlusRect.height - 2);
				font.draw(batch, "-", doorCloseMinusRect.x + 8, doorCloseMinusRect.y + doorCloseMinusRect.height - 4);
				font.draw(batch, "+", doorClosePlusRect.x + 8, doorClosePlusRect.y + doorClosePlusRect.height - 4);
				font.draw(batch, String.format("Close: %.1f", selectedDoorCloseSpeed), doorClosePlusRect.x + 60,
						doorClosePlusRect.y + doorClosePlusRect.height - 2);
			}
		} else if (selectedType == ObjectType.BUTTON) {
			// button color labels
			for (int i = 0; i < buttonColorRects.size; i++) {
				Rectangle r = buttonColorRects.get(i);
				font.setColor(Color.BLACK);
				font.draw(batch, Button.ButtonColor.values()[i].name().substring(0, 1), r.x + 16, r.y + r.height - 6);
			}
		} else if (selectedType == ObjectType.LEVER) {
			// lever orientation label
			if (leverOrientRect != null) {
				font.setColor(Color.WHITE);
				font.draw(batch, selectedLeverHorizontal ? "Horizontal" : "Vertical", leverOrientRect.x + 8,
						leverOrientRect.y + leverOrientRect.height - 6);
			}
		} else if (selectedType == ObjectType.LAUNCHPAD) {
			// launchpad direction labels
			com.jjmc.chromashift.environment.Launchpad.LaunchDirection[] dirs = com.jjmc.chromashift.environment.Launchpad.LaunchDirection
					.values();
			String[] dirLabels = { "UP", "LEFT", "RIGHT" };
			for (int i = 0; i < launchpadDirRects.size && i < dirs.length; i++) {
				Rectangle r = launchpadDirRects.get(i);
				font.setColor(Color.WHITE);
				font.draw(batch, dirLabels[i], r.x + 8, r.y + r.height - 6);
			}
			// Speed display
			font.draw(batch, String.format("Speed: %.0f", selectedLaunchpadSpeed),
					launchpadDirRects.first().x, launchpadDirRects.first().y - 8);
		}
		// draw level list labels
		for (int i = 0; i < levelFiles.size; i++) {
			font.setColor(i == hoveredLevelIndex ? Color.YELLOW : Color.WHITE);
			Rectangle r = levelRects.get(i);
			font.draw(batch, levelFiles.get(i), r.x + 4, r.y + r.height - 4);
		}
		batch.end();

		shape.begin(ShapeRenderer.ShapeType.Filled);
		boolean shapeActive = true;
		shape.setColor(0f, 0f, 0f, 0.35f);
		if (selectedType == ObjectType.WALL) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.CYAN);
			shape.rect(screenGx, screenGy, previewCols * 32, previewRows * 32);
		} else if (selectedType == ObjectType.DOOR) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.ORANGE);
			shape.rect(screenGx, screenGy, previewCols * 32, previewRows * 32);
		} else if (selectedType == ObjectType.BUTTON) {
			// draw actual button image using preview animator if available
			if (previewButtonAnim != null) {
				// Sprite rendering requires SpriteBatch.begin(); end the ShapeRenderer first.
				shape.end();
				shapeActive = false;
				batch.setProjectionMatrix(uiCamera.combined);
				batch.begin();
				previewButtonAnim.play(selectedButtonColor.name(), false);
				previewButtonAnim.setFrame(0);
				previewButtonAnim.render(batch, screenGx, screenGy, 64, 32);
				batch.end();
				// overlay blocked indicator if needed
				if (previewBlocked) {
					shape.begin(ShapeRenderer.ShapeType.Filled);
					shape.setColor(new Color(1f, 0f, 0f, 0.45f));
					shape.rect(screenGx, screenGy, 64, 32);
					shape.end();
					shapeActive = true; // keep shape state consistent
				}
			} else {
				shape.setColor(previewBlocked ? Color.FIREBRICK : Color.GREEN);
				shape.rect(screenGx, screenGy, 28, 12);
			}
		} else if (selectedType == ObjectType.LEVER) {
			if (previewLeverAnim != null) {
				// Sprite rendering requires SpriteBatch.begin(); end the ShapeRenderer first.
				shape.end();
				shapeActive = false;
				batch.setProjectionMatrix(uiCamera.combined);
				batch.begin();
				previewLeverAnim.play(selectedLeverHorizontal ? "HORIZONTAL" : "VERTICAL", false);
				previewLeverAnim.setFrame(0);
				previewLeverAnim.render(batch, screenGx, screenGy, 16, 36);
				batch.end();
				if (previewBlocked) {
					shape.begin(ShapeRenderer.ShapeType.Filled);
					shape.setColor(new Color(1f, 0f, 0f, 0.45f));
					shape.rect(screenGx, screenGy, 16, 36);
					shape.end();
					shapeActive = true;
				}
			} else {
				shape.setColor(previewBlocked ? Color.FIREBRICK : Color.BROWN);
				shape.rect(screenGx, screenGy, 16, 36);
			}
		} else if (selectedType == ObjectType.BOX) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.LIGHT_GRAY);
			shape.rect(screenGx, screenGy, 32, 32);
		} else if (selectedType == ObjectType.ORB) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.ORANGE);
			shape.circle(screenGx + 16, screenGy + 16, 12);
		} else if (selectedType == ObjectType.BOSS) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.PURPLE);
			shape.rect(screenGx - 48, screenGy, 96, 96);
		} else if (selectedType == ObjectType.SPAWN) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.YELLOW);
			shape.rect(screenGx, screenGy, 16, 32);
		} else if (selectedType == ObjectType.LAUNCHPAD) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.CYAN);
			if (selectedLaunchpadDirection == com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP) {
				shape.rect(screenGx, screenGy, 64, 32);
			} else {
				shape.rect(screenGx, screenGy, 32, 64);
			}
		}
		if (shapeActive)
			shape.end();

		// Draw delete preview outline (in UI space) if delete mode is active
		if (deleteMode) {
			shape.setProjectionMatrix(uiCamera.combined);
			shape.begin(ShapeRenderer.ShapeType.Line);
			Gdx.gl.glLineWidth(2f);
			shape.setColor(Color.RED);
			shape.rect(screenGx, screenGy, previewW, previewH);
			Gdx.gl.glLineWidth(1f);
			shape.end();
		}

		// UI overlay (screen-fixed)
		batch.setProjectionMatrix(uiCamera.combined);
		batch.begin();
		font.setColor(Color.WHITE);
		font.draw(batch, "Level Maker - Hold Shift+1/2/3 to load levels (B=Boss, T=Tutorial, list on right)",
				uiCamera.position.x - 380, uiCamera.position.y + 220);
		font.draw(batch, String.format(Locale.ROOT, "Editing: %s    Selected: %s", currentLevelPath, selectedType),
				uiCamera.position.x - 380, uiCamera.position.y + 200);
		font.draw(batch, "Keys: 1=Wall 2=Door 3=Button 4=Lever 5=Box 6=Orb 7=Boss 8=Spawn 9=Launchpad 0=None",
				uiCamera.position.x - 380, uiCamera.position.y + 180);
		font.draw(batch, "Arrow Keys: adjust size (Wall/Door). Left click=place. E=Delete Mode. P=Export. Ctrl+S=Save",
				uiCamera.position.x - 380, uiCamera.position.y + 160);
		font.draw(batch, "WASD to pan camera. Esc to exit LevelMaker.", uiCamera.position.x - 380,
				uiCamera.position.y + 140);
		// Linking overlay hints
		if (linkingMode) {
			font.setColor(Color.GOLD);
			float lx = uiCamera.position.x - 380;
			float ly = uiCamera.position.y + 125;
			if (linkStage == LinkStage.PICK_SOURCE) {
				font.draw(batch, "Link: click a Button or Lever to select source", lx, ly);
			} else {
				String src = (linkSourceType == ObjectType.BUTTON ? "Button" : "Lever") +
						" @ (" + linkSourceX + "," + linkSourceY + ")";
				font.draw(batch, "Link: " + src + ". Left-click doors to toggle, Right-click to finish.", lx, ly);
				font.draw(batch, "Selected doors: " + linkSelectedDoorIds.size, lx, ly - 16);
			}
		}
		// placement toast (screen-fixed)
		if (toastText != null && toastTimer > 0f) {
			font.setColor(Color.GOLD);
			float tx = uiCamera.position.x - 160f;
			float ty = uiCamera.position.y + 120f;
			font.draw(batch, toastText, tx, ty);
		}
		batch.end();
	}

	private void handleInput(float delta) {
		// camera movement (WASD)
		if (Gdx.input.isKeyPressed(Input.Keys.W))
			camera.translate(0, camSpeed * delta);
		if (Gdx.input.isKeyPressed(Input.Keys.S))
			camera.translate(0, -camSpeed * delta);
		if (Gdx.input.isKeyPressed(Input.Keys.A))
			camera.translate(-camSpeed * delta, 0);
		if (Gdx.input.isKeyPressed(Input.Keys.D))
			camera.translate(camSpeed * delta, 0);
		camera.update();
		// Handle dragging of sliders while mouse is pressed
		if ((draggingOpenSlider || draggingCloseSlider) && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
			com.badlogic.gdx.math.Vector3 uiVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(),
					0);
			uiCamera.unproject(uiVec);
			float ux = uiVec.x;
			if (draggingOpenSlider && openSpeedSliderRect != null) {
				float frac = (ux - openSpeedSliderRect.x) / openSpeedSliderRect.width;
				frac = Math.max(0f, Math.min(1f, frac));
				selectedDoorOpenSpeed = DOOR_SPEED_MIN + frac * (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
			}
			if (draggingCloseSlider && closeSpeedSliderRect != null) {
				float frac = (ux - closeSpeedSliderRect.x) / closeSpeedSliderRect.width;
				frac = Math.max(0f, Math.min(1f, frac));
				selectedDoorCloseSpeed = DOOR_SPEED_MIN + frac * (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
			}
		}
		// stop dragging on release (manual because libGDX lacks isButtonJustReleased)
		boolean leftCurrentlyPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
		if (!leftCurrentlyPressed && wasLeftPressed) {
			draggingOpenSlider = false;
			draggingCloseSlider = false;
		}
		wasLeftPressed = leftCurrentlyPressed;

		boolean levelModifier = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
				|| Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

		// level selection (reloading ensures workspaces stay in sync)
		if (levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
			changeLevel("levels/level1.json");
		}
		if (levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
			changeLevel("levels/level2.json");
		}
		if (levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
			changeLevel("levels/level3.json");
		}
		if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
			changeLevel("levels/bossroom.json");
		}
		if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
			changeLevel("levels/tutorial.json");
		}

		// object selection
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_1))
			selectedType = ObjectType.WALL;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_2))
			selectedType = ObjectType.DOOR;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_3))
			selectedType = ObjectType.BUTTON;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_4))
			selectedType = ObjectType.LEVER;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_5))
			selectedType = ObjectType.BOX;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_6))
			selectedType = ObjectType.ORB;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_7))
			selectedType = ObjectType.BOSS;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_8))
			selectedType = ObjectType.SPAWN;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_9))
			selectedType = ObjectType.LAUNCHPAD;
		if (!levelModifier && Gdx.input.isKeyJustPressed(Input.Keys.NUM_0))
			selectedType = ObjectType.NONE;

		// size adjustments for wall/door
		if (selectedType == ObjectType.WALL || selectedType == ObjectType.DOOR) {
			if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
				if (previewCols > 1)
					previewCols--;
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
				previewCols++;
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
				if (previewRows > 1)
					previewRows--;
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
				previewRows++;
			}
		} else {
			previewCols = 1;
			previewRows = 1;
		}

		// LEFT CLICK: placement / UI selection / linking / delete-mode
		if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
			// 1) Convert to UI space and handle UI hits first
			com.badlogic.gdx.math.Vector3 uiVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(),
					0);
			uiCamera.unproject(uiVec);
			float ux = uiVec.x, uy = uiVec.y;
			// Main object type buttons
			for (UIButton b : uiButtons) {
				if (b.rect.contains(ux, uy)) {
					if ("Link".equalsIgnoreCase(b.label)) {
						// Toggle linking mode with two-stage flow
						linkingMode = !linkingMode;
						if (linkingMode) {
							linkStage = LinkStage.PICK_SOURCE;
							linkSourceType = ObjectType.NONE;
							linkSelectedDoorIds.clear();
							toastText = "Link mode: click a Button/Lever";
							toastTimer = 1.5f;
						} else {
							linkStage = LinkStage.PICK_SOURCE;
							linkSourceType = ObjectType.NONE;
							linkSelectedDoorIds.clear();
							toastText = "Link mode off";
							toastTimer = 1.2f;
						}
					} else {
						selectedType = b.type;
						// Turning off linking mode when choosing a non-link button
						if (selectedType != ObjectType.BUTTON && selectedType != ObjectType.LEVER) {
							linkingMode = false;
							linkStage = LinkStage.PICK_SOURCE;
							linkSourceType = ObjectType.NONE;
							linkSelectedDoorIds.clear();
						}
					}
					return;
				}
			}
			// Contextual options
			if (selectedType == ObjectType.DOOR) {
				for (int i = 0; i < doorDirButtons.size; i++) {
					if (doorDirButtons.get(i).contains(ux, uy)) {
						selectedDoorDirIndex = i;
						return;
					}
				}
			} else if (selectedType == ObjectType.BUTTON) {
				for (int i = 0; i < buttonColorRects.size; i++) {
					if (buttonColorRects.get(i).contains(ux, uy)) {
						selectedButtonColor = Button.ButtonColor.values()[i];
						return;
					}
				}
			} else if (selectedType == ObjectType.LEVER) {
				if (leverOrientRect != null && leverOrientRect.contains(ux, uy)) {
					selectedLeverHorizontal = !selectedLeverHorizontal;
					return;
				}
			} else if (selectedType == ObjectType.LAUNCHPAD) {
				// Handle launchpad direction button clicks
				com.jjmc.chromashift.environment.Launchpad.LaunchDirection[] dirs = com.jjmc.chromashift.environment.Launchpad.LaunchDirection
						.values();
				for (int i = 0; i < launchpadDirRects.size && i < dirs.length; i++) {
					if (launchpadDirRects.get(i).contains(ux, uy)) {
						selectedLaunchpadDirection = dirs[i];
						return;
					}
				}
			}
			// handle clicks on door speed controls
			if (doorOpenMinusRect != null && doorOpenMinusRect.contains(ux, uy)) {
				selectedDoorOpenSpeed = Math.max(0.1f, selectedDoorOpenSpeed - 0.5f);
				return;
			}
			if (doorOpenPlusRect != null && doorOpenPlusRect.contains(ux, uy)) {
				selectedDoorOpenSpeed = Math.max(0.1f, selectedDoorOpenSpeed + 0.5f);
				return;
			}
			if (doorCloseMinusRect != null && doorCloseMinusRect.contains(ux, uy)) {
				selectedDoorCloseSpeed = Math.max(0.1f, selectedDoorCloseSpeed - 0.5f);
				return;
			}
			if (doorClosePlusRect != null && doorClosePlusRect.contains(ux, uy)) {
				selectedDoorCloseSpeed = Math.max(0.1f, selectedDoorCloseSpeed + 0.5f);
				return;
			}
			// Slider click start
			if (openSpeedSliderRect != null && openSpeedSliderRect.contains(ux, uy)) {
				float frac = (ux - openSpeedSliderRect.x) / openSpeedSliderRect.width;
				frac = Math.max(0f, Math.min(1f, frac));
				selectedDoorOpenSpeed = DOOR_SPEED_MIN + frac * (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
				draggingOpenSlider = true;
				return;
			}
			if (closeSpeedSliderRect != null && closeSpeedSliderRect.contains(ux, uy)) {
				float frac = (ux - closeSpeedSliderRect.x) / closeSpeedSliderRect.width;
				frac = Math.max(0f, Math.min(1f, frac));
				selectedDoorCloseSpeed = DOOR_SPEED_MIN + frac * (DOOR_SPEED_MAX - DOOR_SPEED_MIN);
				draggingCloseSlider = true;
				return;
			}
			// Level list click to load
			for (int i = 0; i < levelRects.size; i++) {
				if (levelRects.get(i).contains(ux, uy)) {
					String name = levelFiles.get(i);
					if (name != null && !name.isEmpty())
						changeLevel("levels/" + name, true);
					return;
				}
			}

			// 2) If not UI, treat as world placement/linking/delete
			Vector2 worldPlace = screenCellBottomLeftToWorldGrid();
			int wx = (int) worldPlace.x;
			int wy = (int) worldPlace.y;
			Gdx.app.log("LevelMaker",
					String.format("Click at ui=(%f,%f) world=(%d,%d) selectedType=%s linking=%b preview=%dx%d",
							uiVec.x, uiVec.y, wx, wy, selectedType, linkingMode, previewCols, previewRows));

			// New two-stage linking flow
			if (linkingMode) {
				if (linkStage == LinkStage.PICK_SOURCE) {
					// Find a Button or Lever under click in runtime instances
					Interactable hit = null;
					for (int i = interactableInstances.size - 1; i >= 0; --i) {
						Interactable it = interactableInstances.get(i);
						if ((it instanceof Button || it instanceof Lever) && it.getBounds().contains(wx, wy)) {
							hit = it;
							break;
						}
					}
					if (hit == null) {
						toastText = "Pick a Button/Lever first";
						toastTimer = 1.2f;
						return;
					}
					linkSourceType = (hit instanceof Button) ? ObjectType.BUTTON : ObjectType.LEVER;
					linkSourceX = (int) hit.getBounds().x;
					linkSourceY = (int) hit.getBounds().y;
					// Preload any existing target ids
					linkSelectedDoorIds.clear();
					LevelIO.LevelState.InteractableData src = null;
					for (LevelIO.LevelState.InteractableData idd : state.interactables) {
						if (idd != null && idd.type != null && idd.type.equalsIgnoreCase(linkSourceType.name())
								&& (int) idd.x == linkSourceX && (int) idd.y == linkSourceY) {
							src = idd;
							break;
						}
					}
					if (src != null && src.targetId != null && !src.targetId.isEmpty()) {
						String[] parts = src.targetId.split(",");
						for (String p : parts) {
							String t = p.trim();
							if (t.length() > 0)
								linkSelectedDoorIds.add(t);
						}
					}
					linkStage = LinkStage.PICK_DOORS;
					toastText = "Now click doors to add/remove";
					toastTimer = 1.6f;
					return;
				} else if (linkStage == LinkStage.PICK_DOORS) {
					String did = findDoorIdAt(wx, wy);
					if (did == null) {
						toastText = "No door at cursor";
						toastTimer = 1.0f;
						return;
					}
					// Toggle add/remove
					boolean exists = false;
					for (int i = 0; i < linkSelectedDoorIds.size; i++) {
						if (did.equals(linkSelectedDoorIds.get(i))) {
							linkSelectedDoorIds.removeIndex(i);
							exists = true;
							break;
						}
					}
					if (!exists)
						linkSelectedDoorIds.add(did);
					// Update state for current source
					LevelIO.LevelState.InteractableData src = null;
					for (LevelIO.LevelState.InteractableData idd : state.interactables) {
						if (idd != null && idd.type != null && idd.type.equalsIgnoreCase(linkSourceType.name())
								&& (int) idd.x == linkSourceX && (int) idd.y == linkSourceY) {
							src = idd;
							break;
						}
					}
					if (src != null) {
						if (linkSelectedDoorIds.size == 0)
							src.targetId = null;
						else {
							StringBuilder sb = new StringBuilder();
							for (int i = 0; i < linkSelectedDoorIds.size; i++) {
								if (i > 0)
									sb.append(",");
								sb.append(linkSelectedDoorIds.get(i));
							}
							src.targetId = sb.toString();
						}
						try {
							LevelIO.save(currentLevelPath, state);
						} catch (Exception ignored) {
						}
						refreshPreviewFromState();
						toastText = (exists ? "Removed " : "Added ") + did + " (" + linkSelectedDoorIds.size + ")";
						toastTimer = 1.2f;
					}
					return;
				}
			}

			// Delete mode: delete any object overlapping the preview area
			if (deleteMode) {
				// Use current preview rect as delete brush
				float delW = 32;
				float delH = 32;
				Rectangle delArea = new Rectangle(wx, wy, delW, delH);
				boolean anyDeleted = deleteOverlapping(delArea);
				if (anyDeleted) {
					refreshPreviewFromState();
					try {
						LevelIO.save(currentLevelPath, state);
					} catch (Exception ignored) {
					}
				}
				return;
			}

			// Normal placement path when not linking and not in delete mode
			placeAt(wx, wy);
		}

		// RIGHT CLICK: finish linking (if in linking mode). No deletion here anymore.
		if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
			if (linkingMode) {
				linkingMode = false;
				linkStage = LinkStage.PICK_SOURCE;
				linkSourceType = ObjectType.NONE;
				linkSelectedDoorIds.clear();
				toastText = "Linking finished";
				toastTimer = 1.2f;
				return;
			}
		} // end right-click deletion block

		// save (Ctrl+S to avoid conflict with S key used for camera panning)
		if ((Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
				&& Gdx.input.isKeyJustPressed(Input.Keys.S)) {
			LevelIO.save(currentLevelPath, state);
		}
		// Toggle Delete Mode - E
		if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
			deleteMode = !deleteMode;
			toastText = deleteMode ? "Delete mode ON (Left-click to delete)" : "Delete mode OFF";
			toastTimer = 1.4f;
		}

		// quick Export As (timestamped file) - P (remapped from E)
		if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
			String ts = java.time.LocalDateTime.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			String export = String.format("levels/export_%s.json", ts);
			LevelIO.save(export, state);
		}

		// update hovered level index for UI hover highlight
		{
			com.badlogic.gdx.math.Vector3 uiHoverVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(),
					Gdx.input.getY(), 0);
			uiCamera.unproject(uiHoverVec);
			hoveredLevelIndex = -1;
			for (int i = 0; i < levelRects.size; i++) {
				if (levelRects.get(i).contains(uiHoverVec.x, uiHoverVec.y)) {
					hoveredLevelIndex = i;
					break;
				}
			}
		}

		// undo last placement (Z)
		if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
			if (placements.size > 0) {
				Placement last = placements.pop();
				removePlacement(last);
				refreshPreviewFromState();
				try {
					LevelIO.save(currentLevelPath, state);
				} catch (Exception ignored) {
				}
			}
		}

		// exit
		if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
			Gdx.app.postRunnable(() -> {
				dispose();
			});
		}
	}

	/**
	 * Delete any objects whose bounds overlap the given world-space area.
	 * Returns true if anything was deleted.
	 */
	private boolean deleteOverlapping(Rectangle area) {
		boolean deleted = false;
		// Walls
		for (int i = walls.size - 1; i >= 0; --i) {
			Wall w = walls.get(i);
			if (w.getBounds().overlaps(area)) {
				// remove from state
				for (int j = 0; j < state.walls.size; ++j) {
					LevelIO.LevelState.WallData wd = state.walls.get(j);
					if ((int) wd.x == (int) w.getBounds().x && (int) wd.y == (int) w.getBounds().y) {
						state.walls.removeIndex(j);
						break;
					}
				}
				walls.removeIndex(i);
				solids.removeValue(w, true);
				deleted = true;
			}
		}
		// Interactables and others
		for (int i = interactableInstances.size - 1; i >= 0; --i) {
			Interactable it = interactableInstances.get(i);
			Rectangle ob = it.getBounds();
			if (ob != null && ob.overlaps(area)) {
				if (it instanceof Door) {
					for (int j = 0; j < state.interactables.size; ++j) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(j);
						if (idd != null && "door".equalsIgnoreCase(String.valueOf(idd.type))
								&& Math.abs(idd.x - ob.x) < 5f && Math.abs(idd.y - ob.y) < 5f) {
							for (int k = doorRecords.size - 1; k >= 0; --k) {
								if (doorRecords.get(k).id != null && doorRecords.get(k).id.equals(idd.id)) {
									doorRecords.removeIndex(k);
									break;
								}
							}
							state.interactables.removeIndex(j);
							break;
						}
					}
				} else if (it instanceof Button) {
					for (int j = 0; j < state.interactables.size; ++j) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(j);
						if (idd != null && "button".equalsIgnoreCase(String.valueOf(idd.type))
								&& Math.abs(idd.x - ob.x) < 5f && Math.abs(idd.y - ob.y) < 5f) {
							state.interactables.removeIndex(j);
							break;
						}
					}
				} else if (it instanceof Lever) {
					for (int j = 0; j < state.interactables.size; ++j) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(j);
						if (idd != null && "lever".equalsIgnoreCase(String.valueOf(idd.type))
								&& Math.abs(idd.x - ob.x) < 5f && Math.abs(idd.y - ob.y) < 5f) {
							state.interactables.removeIndex(j);
							break;
						}
					}
				} else if (it instanceof Box) {
					for (int j = 0; j < state.boxes.size; ++j) {
						LevelIO.LevelState.BoxData bd = state.boxes.get(j);
						if (Math.abs(bd.x - ob.x) < 5f && Math.abs(bd.y - ob.y) < 5f) {
							state.boxes.removeIndex(j);
							break;
						}
					}
				} else if (it instanceof Orb) {
					for (int j = 0; j < state.orbs.size; ++j) {
						LevelIO.LevelState.OrbData od = state.orbs.get(j);
						if (Math.abs(od.x - ob.x) < 5f && Math.abs(od.y - ob.y) < 5f) {
							state.orbs.removeIndex(j);
							break;
						}
					}
				} else if (it instanceof com.jjmc.chromashift.environment.Launchpad) {
					for (int j = 0; j < state.launchpads.size; ++j) {
						LevelIO.LevelState.LaunchpadData lpd = state.launchpads.get(j);
						if (Math.abs(lpd.x - ob.x) < 5f && Math.abs(lpd.y - ob.y) < 5f) {
							state.launchpads.removeIndex(j);
							break;
						}
					}
				}
				if (it instanceof Solid) solids.removeValue((Solid) it, true);
				interactableInstances.removeIndex(i);
				deleted = true;
			}
		}
		// Ensure Launchpads can be deleted by clicking any part of their full footprint
		for (int j = state.launchpads.size - 1; j >= 0; --j) {
			LevelIO.LevelState.LaunchpadData lpd = state.launchpads.get(j);
			if (lpd == null || lpd.direction == null) continue;
			boolean up = "UP".equalsIgnoreCase(lpd.direction);
			Rectangle lpRect = new Rectangle(lpd.x, lpd.y, up ? 64f : 32f, up ? 32f : 64f);
			if (lpRect.overlaps(area)) {
				// remove state record
				state.launchpads.removeIndex(j);
				// remove instance and solid
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					Interactable it = interactableInstances.get(i);
					if (it instanceof com.jjmc.chromashift.environment.Launchpad) {
						Rectangle ib = it.getBounds();
						if (ib == null || !ib.overlaps(lpRect)) {
							// try a fallback overlap using the full rectangle as bounds
							Rectangle fb = new Rectangle(lpRect);
							if (ib != null && !fb.overlaps(ib)) continue;
						}
						if (it instanceof Solid) solids.removeValue((Solid) it, true);
						interactableInstances.removeIndex(i);
						break;
					}
				}
				deleted = true;
			}
		}
		// Boss
		if (state.boss != null) {
			Rectangle bossRect = new Rectangle(state.boss.x - 48f, state.boss.y, 96f, 96f);
			if (bossRect.overlaps(area)) {
				state.boss = null;
				bossInstance = null;
				deleted = true;
			}
		}
		// Spawn
		if (state.spawn != null) {
			Rectangle sp = new Rectangle(state.spawn.x, state.spawn.y, 16f, 32f);
			if (sp.overlaps(area)) {
				state.spawn = null;
				spawnPreview = null;
				deleted = true;
			}
		}
		return deleted;
	}

	private void placeAt(int gx, int gy) {
		// compute preview area for this placement and cancel if overlapping existing objects
		float areaW = 32f * previewCols;
		float areaH = 32f * previewRows;
		switch (selectedType) {
			case BUTTON -> { areaW = 64f; areaH = 32f; }
			case LEVER -> { areaW = 16f; areaH = 36f; }
			case BOX -> { areaW = 32f; areaH = 32f; }
			case ORB -> { areaW = 24f; areaH = 24f; }
			case BOSS -> { areaW = 96f; areaH = 96f; }
			case SPAWN -> { areaW = 16f; areaH = 32f; }
			case LAUNCHPAD -> {
				if (selectedLaunchpadDirection == com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP) {
					areaW = 64f; areaH = 32f;
				} else {
					areaW = 32f; areaH = 64f;
				}
			}
			default -> {}
		}
		Rectangle intended = new Rectangle(gx, gy, areaW, areaH);
		boolean areaFree = isAreaFree(intended);
		if (!areaFree) {
			// Log overlaps for debugging
			for (Wall w : walls) {
				if (w.getBounds().overlaps(intended))
					Gdx.app.log("LevelMaker", "Overlaps wall at " + (int) w.getBounds().x + "," + (int) w.getBounds().y);
			}
			for (Interactable it : interactableInstances) {
				if (it.getBounds().overlaps(intended))
					Gdx.app.log("LevelMaker", "Overlaps interactable: " + it.getClass().getSimpleName() + " at "
							+ (int) it.getBounds().x + "," + (int) it.getBounds().y);
			}
			Gdx.app.log("LevelMaker", "Placement blocked: area occupied at " + gx + "," + gy);
			return;
		}
		boolean placed = false;
		switch (selectedType) {
			case WALL -> {
				LevelIO.LevelState.WallData wd = new LevelIO.LevelState.WallData();
				wd.x = gx; wd.y = gy; wd.width = previewCols; wd.height = previewRows;
				state.walls.add(wd);
				walls.add(new Wall(wd.x, wd.y, previewCols, previewRows));
				placed = true;
			}
			case DOOR -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "door"; idd.x = gx; idd.y = gy; idd.cols = previewCols; idd.rows = previewRows;
				idd.openDirection = doorDirections[selectedDoorDirIndex];
				idd.id = "door_" + (state.interactables.size + 1);
				idd.openSpeed = selectedDoorOpenSpeed; idd.closeSpeed = selectedDoorCloseSpeed;
				state.interactables.add(idd);
				Door.OpenDirection dir = Door.OpenDirection.valueOf(idd.openDirection);
				int cols = Math.max(1, idd.cols); int rows = Math.max(1, idd.rows);
				Wall base = new Wall(gx, gy - 32, Math.max(1, cols), 1);
				Door d = new Door(gx, base, cols, rows, dir, idd.openSpeed, idd.closeSpeed);
				interactableInstances.add(d);
				solids.add(d);
				doorRecords.add(new DoorRecord(idd.id, gx, gy, idd.cols, idd.rows));
				placements.add(new Placement(ObjectType.DOOR, gx, gy, idd.cols, idd.rows, idd.id));
				placed = true;
			}
			case BUTTON -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "button"; idd.x = gx; idd.y = gy; idd.color = selectedButtonColor.name();
				state.interactables.add(idd);
				Array<Solid> allSolids = new Array<>();
				for (Wall w : walls) allSolids.add(w);
				for (Interactable inter : interactableInstances) if (inter instanceof Solid) allSolids.add((Solid) inter);
				Solid baseSolid = findBaseSolidFor(gx, gy, allSolids);
				if (baseSolid == null) baseSolid = new Wall(gx, gy - 32, 4, 1);
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs((int) dr.x - gx) <= 32 && Math.abs((int) dr.y - gy) <= 32) { foundDoor = dr.id; break; }
				}
				idd.targetId = foundDoor;
				Button b = new Button(gx, baseSolid, foundDoor != null ? findDoorById(foundDoor) : null, selectedButtonColor);
				interactableInstances.add(b);
				solids.add(b);
				placements.add(new Placement(ObjectType.BUTTON, gx, gy, 1, 1, null));
				placed = true;
			}
			case LEVER -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "lever"; idd.x = gx; idd.y = gy; idd.orientation = selectedLeverHorizontal ? "HORIZONTAL" : "VERTICAL";
				state.interactables.add(idd);
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs((int) dr.x - gx) <= 32 && Math.abs((int) dr.y - gy) <= 32) { foundDoor = dr.id; break; }
				}
				idd.targetId = foundDoor;
				Lever l = new Lever(gx, gy, 16, 36, selectedLeverHorizontal, foundDoor != null ? findDoorById(foundDoor) : null);
				if (foundDoor != null) {
					String tid = foundDoor;
					l.setOnToggle(() -> {
						Door dd = findDoorById(tid);
						if (dd != null) { dd.setOpen(!dd.isOpen()); dd.interact(); }
					});
				}
				interactableInstances.add(l);
				placements.add(new Placement(ObjectType.LEVER, gx, gy, 1, 1, null));
				placed = true;
			}
			case BOX -> {
				LevelIO.LevelState.BoxData bd = new LevelIO.LevelState.BoxData();
				bd.x = gx; bd.y = gy; state.boxes.add(bd);
				Box box = new Box(bd.x, bd.y, new Array<>());
				interactableInstances.add(box);
				placements.add(new Placement(ObjectType.BOX, gx, gy, 1, 1, null));
				placed = true;
			}
			case ORB -> {
				LevelIO.LevelState.OrbData od = new LevelIO.LevelState.OrbData();
				od.x = gx; od.y = gy;
				state.orbs.add(od);
				Orb orb = new Orb(od.x, od.y, new Array<>());
				interactableInstances.add(orb);
				placements.add(new Placement(ObjectType.ORB, gx, gy, 1, 1, null));
				placed = true;
			}
			case BOSS -> {
				LevelIO.LevelState.BossData bd = new LevelIO.LevelState.BossData();
				bd.x = gx; bd.y = gy; state.boss = bd;
				if (bossInstance == null) bossInstance = new BossInstance();
				bossInstance.setPosition(bd.x, bd.y);
				placements.add(new Placement(ObjectType.BOSS, gx, gy, 1, 1, null));
				placed = true;
			}
			case SPAWN -> {
				LevelIO.LevelState.SpawnData sd = new LevelIO.LevelState.SpawnData();
				sd.x = gx; sd.y = gy; state.spawn = sd;
				if (spawnPreview == null) spawnPreview = new Spawn(gx, gy); else spawnPreview.setPosition(gx, gy);
				placements.add(new Placement(ObjectType.SPAWN, gx, gy, 1, 1, null));
				placed = true;
			}
			case LAUNCHPAD -> {
				LevelIO.LevelState.LaunchpadData lpd = new LevelIO.LevelState.LaunchpadData();
				lpd.x = gx; lpd.y = gy; lpd.direction = selectedLaunchpadDirection.name(); lpd.speed = selectedLaunchpadSpeed;
				state.launchpads.add(lpd);
				com.jjmc.chromashift.environment.Launchpad launchpad = new com.jjmc.chromashift.environment.Launchpad(
						gx, gy, selectedLaunchpadDirection, selectedLaunchpadSpeed);
				interactableInstances.add(launchpad);
				placements.add(new Placement(ObjectType.LAUNCHPAD, gx, gy, 1, 1, null));
				placed = true;
			}
			default -> {}
		}
		if (placed) {
			refreshPreviewFromState();
			placementFlashes.add(new Flash(gx, gy, areaW, areaH, 0.6f));
			toastText = "Placed " + selectedType + " at (" + gx + "," + gy + ")";
			toastTimer = 1.4f;
			try { LevelIO.save(currentLevelPath, state); } catch (Exception ignored) {}
			Gdx.app.log("LevelMaker", "Placed " + selectedType + " at (" + gx + "," + gy + ")");
		}
	}

	/**
	 * Rebuilds preview arrays (walls/solids/interactables/boss) from the current
	 * editable state.
	 */
	private void refreshPreviewFromState() {
		try {
			LevelLoader.Result r = LevelLoader.build(state);
			this.walls = r.walls;
			this.solids = r.solids;
			this.interactableInstances = r.interactables;
			this.bossInstance = r.boss;
			// Build spawn preview from state
			if (state != null && state.spawn != null) {
				if (spawnPreview == null)
					spawnPreview = new Spawn(state.spawn.x, state.spawn.y);
				else
					spawnPreview.setPosition(state.spawn.x, state.spawn.y);
			} else {
				spawnPreview = null;
			}
			// Rebuild doorRecords for linking
			this.doorRecords.clear();
			if (state != null && state.interactables != null) {
				for (LevelIO.LevelState.InteractableData idd : state.interactables) {
					if (idd != null && "door".equalsIgnoreCase(String.valueOf(idd.type))) {
						this.doorRecords.add(new DoorRecord(idd.id, idd.x, idd.y, idd.cols, idd.rows));
					}
				}
			}
		} catch (Exception ex) {
			Gdx.app.error("LevelMaker", "Failed to refresh preview from state: " + ex.getMessage(), ex);
		}
	}

	/**
	 * Load a level JSON and populate editor preview state using LevelLoader.
	 */
	private void loadLevel(String path) {
		if (path == null || path.isEmpty())
			return;
		currentLevelPath = path;
		// clear current preview
		walls.clear();
		solids.clear();
		interactableInstances.clear();
		doorRecords.clear();
		placements.clear();
		bossInstance = null;

		// Load via LevelLoader to guarantee editor and runtime see identical objects
		LevelLoader.Result loaded;
		try {
			loaded = LevelLoader.loadFromWorkspace(path);
			// Also keep the LevelState for editing/saving
			state = LevelIO.loadFromWorkspaceThenCopyToBuild(path);
		} catch (Exception ex) {
			Gdx.app.error("LevelMaker", "Failed to load level: " + path + " - " + ex.getMessage());
			loaded = new LevelLoader.Result();
			state = new LevelIO.LevelState();
		}

		// Defensive init for missing arrays in state (for editing/saving)
		if (state.walls == null)
			state.walls = new Array<>();
		if (state.boxes == null)
			state.boxes = new Array<>();
		if (state.orbs == null)
			state.orbs = new Array<>();
		if (state.launchpads == null)
			state.launchpads = new Array<>();
		if (state.interactables == null)
			state.interactables = new Array<>();
		if (state.spawn == null)
			state.spawn = new LevelIO.LevelState.SpawnData();

		// Adopt loaded runtime objects for preview rendering
		this.walls = loaded.walls;
		this.solids = loaded.solids;
		this.interactableInstances = loaded.interactables;
		this.bossInstance = loaded.boss;
		// Spawn preview
		if (state != null && state.spawn != null) {
			spawnPreview = new Spawn(state.spawn.x, state.spawn.y);
		} else {
			spawnPreview = null;
		}

		// Rebuild door lookup for editor linking
		for (Interactable i : loaded.interactables) {
			if (i instanceof Door d) {
				// Find corresponding data to get the id
				for (LevelIO.LevelState.InteractableData idd : state.interactables) {
					if (idd != null && "door".equalsIgnoreCase(idd.type)) {
						// Match by approximate position
						if (Math.abs(d.getBounds().x - idd.x) < 5f) {
							doorRecords.add(new DoorRecord(idd.id, idd.x, idd.y, idd.cols, idd.rows));
							break;
						}
					}
				}
			}
		}

		levelSelected = true;
	}

	private void changeLevel(String path) {
		changeLevel(path, false);
	}

	private void changeLevel(String path, boolean forceReload) {
		if (path == null || path.isEmpty())
			return;
		if (forceReload || state == null || !path.equals(currentLevelPath)) {
			loadLevel(path);
		}
	}

	private void rebuildUIPositions() {
		// reposition uiButtons at bottom center
		float bw = 60f, bh = 28f, gap = 8f;
		float totalWidth = uiButtons.size * bw + (uiButtons.size - 1) * gap;
		float bx = (uiCamera.viewportWidth - totalWidth) / 2f;
		float by = 20f;
		for (int i = 0; i < uiButtons.size; i++) {
			UIButton b = uiButtons.get(i);
			b.rect = new Rectangle(bx, by, bw, bh);
			bx += bw + gap;
		}
		// door dir buttons (above main buttons)
		doorDirButtons.clear();
		float optionY = by + bh + 8f;
		float optionX = (uiCamera.viewportWidth - (4 * bw + 3 * gap)) / 2f;
		for (int i = 0; i < doorDirections.length; i++) {
			doorDirButtons.add(new Rectangle(optionX + i * (bw + gap), optionY, bw, bh));
		}
		// door speed controls
		float speedW = 28f, speedH = 20f, speedGap = 6f;
		float speedStartX = optionX + (4 * (bw + gap));
		doorOpenMinusRect = new Rectangle(speedStartX, optionY + 70f, speedW, speedH);
		doorOpenPlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + 70f, speedW, speedH);
		doorCloseMinusRect = new Rectangle(speedStartX, optionY + 70f - (speedH + 4f), speedW, speedH);
		doorClosePlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + 70f - (speedH + 4f), speedW,
				speedH);

		// launchpad direction buttons (rebuild)
		launchpadDirRects.clear();
		float lpDirW = 50f, lpDirH = 24f, lpDirGap = 4f;
		float lpDirsWidth = 3 * lpDirW + 2 * lpDirGap;
		float lpDirX = (uiCamera.viewportWidth - lpDirsWidth) / 2f;
		for (int i = 0; i < 3; i++) {
			launchpadDirRects.add(new Rectangle(lpDirX + i * (lpDirW + lpDirGap), optionY, lpDirW, lpDirH));
		}

		// sliders positions (keep above options)
		float sliderW = 220f, sliderH = 14f;
		float sliderX = (uiCamera.viewportWidth - sliderW) / 2f;
		float sliderY = optionY + 70f;
		openSpeedSliderRect = new Rectangle(sliderX, sliderY, sliderW, sliderH);
		closeSpeedSliderRect = new Rectangle(sliderX, sliderY - (sliderH + 10f), sliderW, sliderH);
		// recompute level rects
		levelRects.clear();
		float lx = uiCamera.position.x + uiCamera.viewportWidth / 2f - 180f;
		float ly = uiCamera.position.y + uiCamera.viewportHeight / 2f - 32f;
		float lh = 20f, lgap = 4f;
		for (int i = 0; i < levelFiles.size; i++) {
			levelRects.add(new Rectangle(lx, ly - i * (lh + lgap), 170f, lh));
		}
	}

	private Door findDoorById(String id) {
		if (id == null)
			return null;
		// Try to locate a DoorRecord for this id and then find the runtime Door whose
		// bounds match the record position.
		DoorRecord foundRec = null;
		for (DoorRecord dr : doorRecords) {
			if (dr != null && dr.id != null && dr.id.equals(id)) {
				foundRec = dr;
				break;
			}
		}
		if (foundRec != null) {
			for (Interactable it : interactableInstances) {
				if (it instanceof Door) {
					Door d = (Door) it;
					if (Math.abs(d.getBounds().x - foundRec.x) < 8f && Math.abs(d.getBounds().y - foundRec.y) < 8f)
						return d;
				}
			}
		}
		// Fallback: try to match using the saved LevelState entry (position stored
		// there)
		if (state != null && state.interactables != null) {
			for (LevelIO.LevelState.InteractableData idd : state.interactables) {
				if (idd != null && idd.id != null && idd.id.equals(id)) {
					for (Interactable it : interactableInstances) {
						if (it instanceof Door) {
							Door d = (Door) it;
							if (Math.abs(d.getBounds().x - idd.x) < 8f && Math.abs(d.getBounds().y - idd.y) < 8f)
								return d;
						}
					}
				}
			}
		}
		// Last resort: return any Door instance if present
		for (Interactable it : interactableInstances)
			if (it instanceof Door)
				return (Door) it;
		return null;
	}

	/**
	 * Find a door record id at the provided world-grid cell (approx). Returns null
	 * if none found.
	 */
	private String findDoorIdAt(int wx, int wy) {
		for (DoorRecord dr : doorRecords) {
			if (Math.abs((int) dr.x - wx) <= 32 && Math.abs((int) dr.y - wy) <= 32)
				return dr.id;
		}
		return null;
	}

	private void removePlacement(Placement p) {
		switch (p.type) {
			case WALL:
				for (int i = 0; i < state.walls.size; ++i) {
					LevelIO.LevelState.WallData wd = state.walls.get(i);
					if (wd.x == p.x && wd.y == p.y && wd.width == p.cols && wd.height == p.rows) {
						state.walls.removeIndex(i);
						break;
					}
				}
				for (int i = 0; i < walls.size; ++i) {
					Wall w = walls.get(i);
					if ((int) w.bounds.x == p.x && (int) w.bounds.y == p.y) {
						walls.removeIndex(i);
						break;
					}
				}
				break;
			case DOOR:
				// remove interactable with matching id
				for (int i = 0; i < state.interactables.size; ++i) {
					LevelIO.LevelState.InteractableData idd = state.interactables.get(i);
					if (idd.id != null && idd.id.equals(p.id)) {
						state.interactables.removeIndex(i);
						break;
					}
				}
				// remove record
				for (int i = 0; i < doorRecords.size; ++i) {
					if (doorRecords.get(i).id.equals(p.id)) {
						doorRecords.removeIndex(i);
						break;
					}
				}
				// remove door instance
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					if (interactableInstances.get(i) instanceof Door) {
						interactableInstances.removeIndex(i);
						break;
					}
				}
				break;
			case BUTTON:
			case LEVER:
				// remove one interactable of that type at the placement position
				for (int i = 0; i < state.interactables.size; ++i) {
					LevelIO.LevelState.InteractableData idd = state.interactables.get(i);
					if (idd.type != null && idd.type.equalsIgnoreCase(p.type.name()) && (int) idd.x == p.x
							&& (int) idd.y == p.y) {
						state.interactables.removeIndex(i);
						break;
					}
				}
				// remove matching instance by bounds position
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					Object o = interactableInstances.get(i);
					if (p.type == ObjectType.BUTTON && o instanceof Button) {
						Button bb = (Button) o;
						if ((int) bb.getBounds().x == p.x && (int) bb.getBounds().y == p.y) {
							interactableInstances.removeIndex(i);
							break;
						}
					} else if (p.type == ObjectType.LEVER && o instanceof Lever) {
						Lever ll = (Lever) o;
						if ((int) ll.getBounds().x == p.x && (int) ll.getBounds().y == p.y) {
							interactableInstances.removeIndex(i);
							break;
						}
					}
				}
				break;
			case BOX:
				for (int i = 0; i < state.boxes.size; ++i) {
					LevelIO.LevelState.BoxData bd = state.boxes.get(i);
					if (bd.x == p.x && bd.y == p.y) {
						state.boxes.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i)
					if (interactableInstances.get(i) instanceof Box)
						interactableInstances.removeIndex(i);
				break;
			case ORB:
				for (int i = 0; i < state.orbs.size; ++i) {
					LevelIO.LevelState.OrbData od = state.orbs.get(i);
					if (od.x == p.x && od.y == p.y) {
						state.orbs.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i)
					if (interactableInstances.get(i) instanceof Orb)
						interactableInstances.removeIndex(i);
				break;
			case BOSS:
				state.boss = null;
				bossInstance = null;
				break;
			case SPAWN:
				state.spawn = new LevelIO.LevelState.SpawnData();
				state.spawn.x = 0;
				state.spawn.y = 0;
				break;
			case LAUNCHPAD:
				for (int i = 0; i < state.launchpads.size; ++i) {
					LevelIO.LevelState.LaunchpadData lpd = state.launchpads.get(i);
					if (lpd.x == p.x && lpd.y == p.y) {
						state.launchpads.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					if (interactableInstances.get(i) instanceof com.jjmc.chromashift.environment.Launchpad) {
						com.jjmc.chromashift.environment.Launchpad lp = (com.jjmc.chromashift.environment.Launchpad) interactableInstances
								.get(i);
						if ((int) lp.getSpriteBounds().x == p.x && (int) lp.getSpriteBounds().y == p.y) {
							interactableInstances.removeIndex(i);
							break;
						}
					}
				}
				break;
			case NONE:
				break;
			default:
				break;
		}
	}

	
	@Override
	public void resize(int width, int height) {
		camera.viewportWidth = width;
		camera.viewportHeight = height;
		camera.update();
		if (uiCamera != null) {
			uiCamera.viewportWidth = width;
			uiCamera.viewportHeight = height;
			uiCamera.position.set(uiCamera.viewportWidth / 2f, uiCamera.viewportHeight / 2f, 0f);
			uiCamera.update();
			rebuildUIPositions();
		}
	}



	// (Removed anchor-based door helpers; doors can be placed anywhere now.)

	private Solid findBaseSolidFor(float x, float y, Array<Solid> solids) {
		if (solids == null || solids.size == 0)
			return null;
		Solid best = null;
		float bestTopY = -Float.MAX_VALUE;
		for (Solid s : solids) {
			Rectangle r = s.getBounds();
			float topY = r.y + r.height;
			if (topY <= y && x >= r.x && x <= r.x + r.width) {
				if (topY > bestTopY) {
					bestTopY = topY;
					best = s;
				}
			}
		}
		return best;
	}

	@Override
	public void pause() {
		// no-op: keep editor state in memory while paused
	}

	@Override
	public void resume() {
		// no-op
	}

	@Override
	public void hide() {
		// no-op
	}

	@Override
	public void dispose() {
		// Intentionally left blank: resources are managed by Initialize/context.
		// If ownership changes, dispose batch/shape/font here.
	}
}