package com.jjmc.chromashift.screens;

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
import com.jjmc.chromashift.environment.interactable.Button;
import com.jjmc.chromashift.environment.interactable.Door;
import com.jjmc.chromashift.environment.interactable.Lever;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.entity.boss.BossInstance;
import com.jjmc.chromashift.screens.Initialize;
import com.jjmc.chromashift.screens.LevelIO;

import java.util.Locale;

/**
 * Lightweight level maker/editor.
 * Flow:
 * 1) Choose level to edit by pressing 1,2,3,B (bossroom) or T (tutorial)
 * 2) No player is created. Camera is free and controlled by WASD keys.
 * 3) Choose object type (1..8). Move mouse to position; it snaps to 32x32 grid.
 *    - For variable-size objects (Wall/Door) adjust width/height with Arrow keys
 *    - Left click to confirm placement, right click to cancel
 * 4) Press S to save level (writes to local levels/<name>.json via LevelIO.save)
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

	private enum ObjectType { WALL, DOOR, BUTTON, LEVER, BOX, ORB, BOSS, SPAWN, NONE }
	private ObjectType selectedType = ObjectType.NONE;

	// preview placement
	private int previewCols = 1, previewRows = 1;

	// collections to render interactables immediately
	private Array<Wall> walls = new Array<>();
	private Array<Object> interactableInstances = new Array<>();
	private BossInstance bossInstance;

	// keep simple placement history for undo/erase
	private static class Placement {
		ObjectType type;
		int x,y,cols,rows;
		String id; // for doors
		Placement(ObjectType t,int x,int y,int c,int r,String id){ this.type=t;this.x=x;this.y=y;this.cols=c;this.rows=r;this.id=id; }
	}
	private Array<Placement> placements = new Array<>();
	// door quick lookup
	private static class DoorRecord { String id; float x,y; int cols,rows; DoorRecord(String id,float x,float y,int c,int r){this.id=id;this.x=x;this.y=y;this.cols=c;this.rows=r;} }
	private Array<DoorRecord> doorRecords = new Array<>();

	// camera pan speed
	private float camSpeed = 400f;

	// UI buttons
	private static class UIButton { Rectangle rect; ObjectType type; String label; UIButton(Rectangle r,ObjectType t,String l){rect=r;type=t;label=l;} }
	private Array<UIButton> uiButtons = new Array<>();
	private Array<Rectangle> doorDirButtons = new Array<>();
	private final String[] doorDirections = new String[]{"UP","DOWN","LEFT","RIGHT"};
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
		uiCamera.position.set(uiCamera.viewportWidth/2f, uiCamera.viewportHeight/2f, 0f);
		uiCamera.update();

		loadLevel(currentLevelPath);

		// build UI buttons (screen-fixed near top-left)
		float bx = uiCamera.position.x - 360f;
		float by = uiCamera.position.y + 120f;
		float bw = 60f, bh = 28f, gap = 8f;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.WALL, "Wall")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.DOOR, "Door")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BUTTON, "Button")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.LEVER, "Lever")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BOX, "Box")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.ORB, "Orb")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.BOSS, "Boss")); bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.SPAWN, "Spawn"));

		// door direction buttons (below main ui)
		float dx = uiCamera.position.x - 360f;
		float dy = by - (bh + 6f);
		for (int i=0;i<doorDirections.length;i++) {
			doorDirButtons.add(new Rectangle(dx + i*(bw+6f), dy, bw, bh));
		}

		// list available level files from internal assets/levels
		levelFiles.clear(); levelRects.clear();
		FileHandle dir = Gdx.files.internal("levels");
		if (dir != null && dir.exists()) {
			FileHandle[] files = dir.list();
			float lx = uiCamera.position.x + uiCamera.viewportWidth/2f - 180f;
			float ly = uiCamera.position.y + uiCamera.viewportHeight/2f - 32f;
			float lh = 20f, lgap = 4f;
			for (int i=0;i<files.length;i++) {
				levelFiles.add(files[i].name());
				levelRects.add(new Rectangle(lx, ly - i*(lh+lgap), 170f, lh));
			}
		}
	}

	private Vector2 screenToWorld(int screenX, int screenY) {
		// convert using camera unproject
		com.badlogic.gdx.math.Vector3 v = new com.badlogic.gdx.math.Vector3(screenX, screenY, 0);
		camera.unproject(v);
		return new Vector2(v.x, v.y);
	}

	private int snap32(float v) {
		return (int)Math.floor(v / 32f) * 32;
	}

	/**
	 * Snap world coordinate so the preview rectangle follows the cursor correctly
	 * even when the camera pans. Returns the bottom-left x/y of the preview area.
	 */
	private int snapForPreviewX(float worldX) {
		// deprecated: keep for compatibility but prefer screen-based snapping
		int cell = (int)Math.floor(worldX / 32f);
		return cell * 32;
	}
    private int snapForPreviewY(float worldY) {
		int cell = (int)Math.floor(worldY / 32f);
		return cell * 32;
    }

	/**
	 * Compute preview bottom-left grid position using screen-space snapping.
	 * Steps:
	 *  - Snap the mouse screen pixel to 32px cells (so the preview stays visually under cursor when panning)
	 *  - Unproject that snapped screen point to world coordinates
	 *  - Convert the world point to a bottom-left grid-aligned cell for drawing
	 */
	private Vector2 getPreviewGridPosFromScreen() {
        int mx = Gdx.input.getX();
        int my = Gdx.input.getY();

        // Convert directly to world coordinates
        com.badlogic.gdx.math.Vector3 world = new com.badlogic.gdx.math.Vector3(mx, my, 0);
        camera.unproject(world);

        // Snap in world space, not screen space
        float gx = (float)Math.floor(world.x / 32f) * 32;
        float gy = (float)Math.floor(world.y / 32f) * 32;

        return new Vector2(gx, gy);
    }

	/**
	 * Get preview bottom-left in UI (screen) coordinates by snapping the mouse world position
	 * to the world 32px grid, then projecting that world cell back to screen pixels and into
	 * UI coordinates. This makes the preview snap to the world grid while remaining drawn in UI space.
	 */
	private Vector2 getSnappedPreviewScreenBL() {
		// unproject mouse to world
		com.badlogic.gdx.math.Vector3 mv = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
		camera.unproject(mv);
		float wx = (float)Math.floor(mv.x / 32f) * 32f;
		float wy = (float)Math.floor(mv.y / 32f) * 32f;
		// project the snapped world cell to screen pixels
		com.badlogic.gdx.math.Vector3 pv = new com.badlogic.gdx.math.Vector3(wx, wy, 0);
		camera.project(pv);
		// convert screen pixels to uiCamera space for drawing
		com.badlogic.gdx.math.Vector3 uv = new com.badlogic.gdx.math.Vector3(pv.x, pv.y, 0);
		uiCamera.unproject(uv);
		return new Vector2(uv.x, uv.y);
	}

	/**
	 * Backwards-compatible alias used by render(); returns the preview bottom-left position in UI (screen) coordinates.
	 * This delegates to getSnappedPreviewScreenBL() so existing code using getPreviewScreenBL() works as expected.
	 */
	private Vector2 getPreviewScreenBL() {
		return getSnappedPreviewScreenBL();
	}

	/**
	 * Convert the mouse screen position to the bottom-left world-grid cell (floored to 32px).
	 * This ensures placement/erase use the same world grid the preview snaps to.
	 */
	private Vector2 screenCellBottomLeftToWorldGrid() {
		com.badlogic.gdx.math.Vector3 mv = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
		camera.unproject(mv);
		int gx = (int)Math.floor(mv.x / 32f) * 32;
		int gy = (int)Math.floor(mv.y / 32f) * 32;
		return new Vector2(gx, gy);
	}


	@Override
	public void render(float delta) {
		handleInput(delta);
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
	
		// ensure world rendering uses the main camera
		shape.setProjectionMatrix(camera.combined);
		batch.setProjectionMatrix(camera.combined);

		// draw placement grid lightly
		shape.begin(ShapeRenderer.ShapeType.Line);
		shape.setColor(0.12f, 0.12f, 0.12f, 1f);
		float left = camera.position.x - camera.viewportWidth/2f;
		float right = camera.position.x + camera.viewportWidth/2f;
		float bottom = camera.position.y - camera.viewportHeight/2f;
		float top = camera.position.y + camera.viewportHeight/2f;
		for (int x = snap32(left); x <= right + 32; x += 32) shape.line(x, bottom - 10000, x, top + 10000);
		for (int y = snap32(bottom); y <= top + 32; y += 32) shape.line(left - 10000, y, right + 10000, y);
		shape.end();

		// draw existing walls
		batch.begin();
		for (Wall w : walls) w.render(batch);
		for (Object o : interactableInstances) {
			if (o instanceof Box) ((Box)o).render(batch);
			if (o instanceof Orb) ((Orb)o).render(batch);
		}
		if (bossInstance != null) bossInstance.render(batch);
		batch.end();

        // preview of the current object under mouse (drawn in UI space so it stays under cursor)
        Vector2 screenPreview = getPreviewScreenBL();
        float screenGx = screenPreview.x;
        float screenGy = screenPreview.y;

		// draw UI (screen-fixed) using uiCamera
		shape.setProjectionMatrix(uiCamera.combined);
		batch.setProjectionMatrix(uiCamera.combined);
	shape.begin(ShapeRenderer.ShapeType.Filled);
		for (UIButton b : uiButtons) {
			Color c = (selectedType == b.type) ? Color.DARK_GRAY : Color.DARK_GRAY.cpy().lerp(Color.LIGHT_GRAY, 0.25f);
			shape.setColor(c);
			shape.rect(b.rect.x, b.rect.y, b.rect.width, b.rect.height);
		}
		// door dir buttons
		for (int i=0;i<doorDirButtons.size;i++) {
			Rectangle r = doorDirButtons.get(i);
			shape.setColor((i==selectedDoorDirIndex)?Color.GOLD:Color.DARK_GRAY);
			shape.rect(r.x, r.y, r.width, r.height);
		}
		// level list background
		for (Rectangle lr : levelRects) {
			shape.setColor(0f, 0f, 0f, 0.5f);
			shape.rect(lr.x - 4, lr.y - 2, lr.width + 8, lr.height + 4);
		}
		shape.end();

		batch.begin();
		for (UIButton b : uiButtons) {
			font.setColor(Color.WHITE);
			font.draw(batch, b.label, b.rect.x + 6, b.rect.y + b.rect.height - 8);
		}
		for (int i=0;i<doorDirButtons.size;i++) {
			Rectangle r = doorDirButtons.get(i);
			font.setColor(Color.BLACK);
			font.draw(batch, doorDirections[i], r.x + 6, r.y + r.height - 8);
		}
		// draw level list labels
		for (int i=0;i<levelFiles.size;i++) {
			font.setColor(i==hoveredLevelIndex?Color.YELLOW:Color.WHITE);
			Rectangle r = levelRects.get(i);
			font.draw(batch, levelFiles.get(i), r.x + 4, r.y + r.height - 4);
		}
		batch.end();

		shape.begin(ShapeRenderer.ShapeType.Filled);
		shape.setColor(0f, 0f, 0f, 0.35f);
		if (selectedType == ObjectType.WALL) {
			shape.setColor(Color.CYAN);
			shape.rect(screenGx, screenGy, previewCols * 32, previewRows * 32);
		} else if (selectedType == ObjectType.DOOR) {
			shape.setColor(Color.ORANGE);
			shape.rect(screenGx, screenGy, previewCols * 32, previewRows * 32);
		} else if (selectedType == ObjectType.BUTTON) {
			shape.setColor(Color.GREEN);
			shape.rect(screenGx, screenGy, 28, 12);
		} else if (selectedType == ObjectType.LEVER) {
			shape.setColor(Color.BROWN);
			shape.rect(screenGx, screenGy, 16, 36);
		} else if (selectedType == ObjectType.BOX) {
			shape.setColor(Color.LIGHT_GRAY);
			shape.rect(screenGx, screenGy, 32, 32);
		} else if (selectedType == ObjectType.ORB) {
			shape.setColor(Color.ORANGE);
			shape.circle(screenGx + 16, screenGy + 16, 12);
		} else if (selectedType == ObjectType.BOSS) {
			shape.setColor(Color.PURPLE);
			shape.rect(screenGx - 48, screenGy, 96, 96);
		} else if (selectedType == ObjectType.SPAWN) {
			shape.setColor(Color.YELLOW);
			shape.rect(screenGx, screenGy, 16, 32);
		}
		shape.end();

		// UI overlay (screen-fixed)
		batch.setProjectionMatrix(uiCamera.combined);
		batch.begin();
		font.setColor(Color.WHITE);
		font.draw(batch, "Level Maker - Choose level (or pick from the list)", uiCamera.position.x - 380, uiCamera.position.y + 220);
		font.draw(batch, String.format(Locale.ROOT, "Editing: %s    Selected: %s", currentLevelPath, selectedType), uiCamera.position.x - 380, uiCamera.position.y + 200);
		font.draw(batch, "Keys: 1=Wall 2=Door 3=Button 4=Lever 5=Box 6=Orb 7=Boss 8=Spawn 0=None", uiCamera.position.x - 380, uiCamera.position.y + 180);
		font.draw(batch, "Arrow Keys: adjust size (Wall/Door). Left click=place, Right click=cancel. S=Save", uiCamera.position.x - 380, uiCamera.position.y + 160);
		font.draw(batch, "WASD to pan camera. Esc to exit LevelMaker.", uiCamera.position.x - 380, uiCamera.position.y + 140);
		batch.end();
	}

	private void handleInput(float delta) {
		// camera movement (WASD)
		if (Gdx.input.isKeyPressed(Input.Keys.W)) camera.translate(0, camSpeed * delta);
		if (Gdx.input.isKeyPressed(Input.Keys.S)) camera.translate(0, -camSpeed * delta);
		if (Gdx.input.isKeyPressed(Input.Keys.A)) camera.translate(-camSpeed * delta, 0);
		if (Gdx.input.isKeyPressed(Input.Keys.D)) camera.translate(camSpeed * delta, 0);
		camera.update();

		// level selection
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) { currentLevelPath = "levels/level1.json"; state = LevelIO.load(currentLevelPath); }
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) { currentLevelPath = "levels/level2.json"; state = LevelIO.load(currentLevelPath); }
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) { currentLevelPath = "levels/level3.json"; state = LevelIO.load(currentLevelPath); }
		if (Gdx.input.isKeyJustPressed(Input.Keys.B)) { currentLevelPath = "levels/bossroom.json"; state = LevelIO.load(currentLevelPath); }
		if (Gdx.input.isKeyJustPressed(Input.Keys.T)) { currentLevelPath = "levels/tutorial.json"; state = LevelIO.load(currentLevelPath); }

		// object selection
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) selectedType = ObjectType.WALL;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) selectedType = ObjectType.DOOR;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) selectedType = ObjectType.BUTTON;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) selectedType = ObjectType.LEVER;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) selectedType = ObjectType.BOX;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6)) selectedType = ObjectType.ORB;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7)) selectedType = ObjectType.BOSS;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8)) selectedType = ObjectType.SPAWN;
		if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0)) selectedType = ObjectType.NONE;

		// size adjustments for wall/door
		if (selectedType == ObjectType.WALL || selectedType == ObjectType.DOOR) {
			if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) { if (previewCols > 1) previewCols--; }
			if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) { previewCols++; }
			if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) { if (previewRows > 1) previewRows--; }
			if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) { previewRows++; }
		} else {
			previewCols = 1; previewRows = 1;
		}

		// place/cancel
		if (Gdx.input.justTouched() && Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
			// left click - first check UI buttons (screen-fixed)
			com.badlogic.gdx.math.Vector3 uiVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
			uiCamera.unproject(uiVec);
			float ux = uiVec.x, uy = uiVec.y;
			// check UI
			boolean clickedUI = false;
			for (UIButton b : uiButtons) {
				if (b.rect.contains(ux, uy)) {
					selectedType = b.type;
					clickedUI = true; break;
				}
			}
				if (!clickedUI) {
					// not UI - place into world. Convert the bottom-left snapped screen cell to world-grid coords
					Vector2 worldPlace = screenCellBottomLeftToWorldGrid();
					placeAt((int)worldPlace.x, (int)worldPlace.y);
				}
		}
		if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
			// erase placement under cursor (right click)
			Vector2 worldXY = screenCellBottomLeftToWorldGrid();
			int gx = (int)worldXY.x;
			int gy = (int)worldXY.y;
			// find last placement at this grid cell and remove
			for (int i = placements.size - 1; i >= 0; --i) {
				Placement p = placements.get(i);
				if (p.x == gx && p.y == gy) {
					removePlacement(p);
					placements.removeIndex(i);
					break;
				}
			}
		}

		// save
		if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
			LevelIO.save(currentLevelPath, state);
		}
		// quick Export As (timestamped file) - E
		if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
			String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			String export = String.format("levels/export_%s.json", ts);
			LevelIO.save(export, state);
		}

		// update hovered level index for UI hover highlight
		{
			com.badlogic.gdx.math.Vector3 uiVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
			uiCamera.unproject(uiVec);
			hoveredLevelIndex = -1;
			for (int i=0;i<levelRects.size;i++) {
				if (levelRects.get(i).contains(uiVec.x, uiVec.y)) { hoveredLevelIndex = i; break; }
			}
		}

		// undo last placement (Z)
		if (Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
			if (placements.size > 0) {
				Placement last = placements.pop();
				removePlacement(last);
			}
		}

		// exit
		if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
			Gdx.app.postRunnable(() -> {
				// attempt to dispose and return to default behaviour
				dispose();
			});
		}
	}

	private void placeAt(int gx, int gy) {
		switch (selectedType) {
			case WALL -> {
				// create wall data and instance
				LevelIO.LevelState.WallData wd = new LevelIO.LevelState.WallData();
				wd.x = gx; wd.y = gy; wd.width = previewCols; wd.height = previewRows;
				state.walls.add(wd);
				walls.add(new Wall(wd.x, wd.y, previewCols, previewRows));
			}
			case DOOR -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "door"; idd.x = gx; idd.cols = previewCols; idd.rows = previewRows; idd.openDirection = doorDirections[selectedDoorDirIndex]; idd.id = "door_" + (state.interactables.size + 1);
				state.interactables.add(idd);
				// create a door anchored to the first wall (simple heuristic)
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(gx, gy - 32, 4, 1);
				Door d = new Door(anchor, idd.cols, 0, idd.cols, idd.rows, Door.OpenDirection.UP, 2f, 0.5f);
				interactableInstances.add(d);
				doorRecords.add(new DoorRecord(idd.id, gx, gy, idd.cols, idd.rows));
				placements.add(new Placement(ObjectType.DOOR, gx, gy, idd.cols, idd.rows, idd.id));
			}
			case BUTTON -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "button"; idd.x = gx; idd.color = "GREEN"; idd.targetId = null;
				state.interactables.add(idd);
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(gx, gy - 32, 4, 1);
				// try to auto-link to a door under cursor
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs(dr.x - gx) <= 32 && Math.abs(dr.y - gy) <= 32) { foundDoor = dr.id; break; }
				}
				idd.targetId = foundDoor;
				Button b = new Button(anchor.bounds.x, anchor, foundDoor != null ? findDoorById(foundDoor) : null, Button.ButtonColor.GREEN);
				interactableInstances.add(b);
				placements.add(new Placement(ObjectType.BUTTON, gx, gy, 1, 1, null));
			}
			case LEVER -> {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "lever"; idd.x = gx; idd.targetId = null;
				state.interactables.add(idd);
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(gx, gy - 32, 4, 1);
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs(dr.x - gx) <= 32 && Math.abs(dr.y - gy) <= 32) { foundDoor = dr.id; break; }
				}
				idd.targetId = foundDoor;
				Lever l = new Lever(anchor.bounds.x + 200f, anchor.bounds.y + anchor.bounds.height, 16, 36, foundDoor != null ? findDoorById(foundDoor) : null);
				if (foundDoor != null) {
					Lever temp = l;
					String tid = foundDoor;
					l.setOnToggle(() -> {
						Door dd = findDoorById(tid);
						if (dd != null) { dd.setOpen(!dd.isOpen()); dd.interact(); }
					});
				}
				interactableInstances.add(l);
				placements.add(new Placement(ObjectType.LEVER, gx, gy, 1, 1, null));
			}
			case BOX -> {
				LevelIO.LevelState.BoxData bd = new LevelIO.LevelState.BoxData();
				bd.x = gx; bd.y = gy;
				state.boxes.add(bd);
				Box box = new Box(bd.x, bd.y, new Array<>());
				interactableInstances.add(box);
				placements.add(new Placement(ObjectType.BOX, gx, gy, 1, 1, null));
			}
			case ORB -> {
				LevelIO.LevelState.OrbData od = new LevelIO.LevelState.OrbData();
				od.x = gx; od.y = gy;
				state.orbs.add(od);
				Orb orb = new Orb(od.x, od.y, new Array<>());
				interactableInstances.add(orb);
				placements.add(new Placement(ObjectType.ORB, gx, gy, 1, 1, null));
			}
			case BOSS -> {
				LevelIO.LevelState.BossData bd = new LevelIO.LevelState.BossData();
				bd.x = gx; bd.y = gy;
				state.boss = bd;
				if (bossInstance == null) bossInstance = new BossInstance();
				bossInstance.setPosition(bd.x, bd.y);
				placements.add(new Placement(ObjectType.BOSS, gx, gy, 1, 1, null));
			}
			case SPAWN -> {
				LevelIO.LevelState.SpawnData sd = new LevelIO.LevelState.SpawnData();
				sd.x = gx; sd.y = gy;
				state.spawn = sd;
				placements.add(new Placement(ObjectType.SPAWN, gx, gy, 1, 1, null));
			}
			default -> {}
		}
	}

	/**
	 * Load a level JSON and populate editor preview state.
	 */
	private void loadLevel(String path) {
		// clear current preview
		walls.clear(); interactableInstances.clear(); doorRecords.clear(); placements.clear(); bossInstance = null;
		try {
			state = LevelIO.load(path);
		} catch (Exception ex) {
			Gdx.app.error("LevelMaker", "Failed to load level: " + path + " - " + ex.getMessage());
			state = null;
		}
		if (state == null) state = new LevelIO.LevelState();
		// defensive init for missing arrays in LevelState
		if (state.walls == null) state.walls = new Array<>();
		if (state.boxes == null) state.boxes = new Array<>();
		if (state.orbs == null) state.orbs = new Array<>();
		if (state.interactables == null) state.interactables = new Array<>();
		if (state.spawn == null) state.spawn = new LevelIO.LevelState.SpawnData();
		// create walls
		for (LevelIO.LevelState.WallData wd : state.walls) {
			walls.add(new Wall(wd.x, wd.y, (int)wd.width, (int)wd.height));
		}
		// create other interactables shallowly for visualization
		for (LevelIO.LevelState.BoxData bd : state.boxes) {
			if (bd != null) interactableInstances.add(new Box(bd.x, bd.y, new Array<>()));
		}
		for (LevelIO.LevelState.OrbData od : state.orbs) {
			if (od != null) interactableInstances.add(new Orb(od.x, od.y, new Array<>()));
		}
		for (LevelIO.LevelState.InteractableData idd : state.interactables) {
			if (idd == null) continue;
			if (idd.type != null && idd.type.equalsIgnoreCase("door")) {
				// reconstruct a simple Door instance (anchor to first wall or default)
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(idd.x, idd.y - 32, 4, 1);
				Door.OpenDirection dir = Door.OpenDirection.UP;
				try { if (idd.openDirection != null) dir = Door.OpenDirection.valueOf(idd.openDirection.toUpperCase()); } catch (Exception ignored) {}
				Door d = new Door(anchor, idd.cols, 0, idd.cols, idd.rows, dir, 2f, 0.5f);
				interactableInstances.add(d);
				doorRecords.add(new DoorRecord(idd.id, idd.x, idd.y, idd.cols, idd.rows));
			} else if (idd.type != null && idd.type.equalsIgnoreCase("button")) {
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(idd.x, idd.y - 32, 4, 1);
				Button b = new Button(anchor.bounds.x, anchor, idd.targetId != null ? findDoorById(idd.targetId) : null, Button.ButtonColor.GREEN);
				interactableInstances.add(b);
			} else if (idd.type != null && idd.type.equalsIgnoreCase("lever")) {
				Wall anchor = walls.size > 0 ? walls.get(0) : new Wall(idd.x, idd.y - 32, 4, 1);
				Lever l = new Lever(anchor.bounds.x + 200f, anchor.bounds.y + anchor.bounds.height, 16, 36, idd.targetId != null ? findDoorById(idd.targetId) : null);
				interactableInstances.add(l);
			}
		}
		if (state.boss != null) {
			bossInstance = new BossInstance();
			bossInstance.setPosition(state.boss.x, state.boss.y);
		}
	}

	private void rebuildUIPositions() {
		// reposition uiButtons in order
		float bx = uiCamera.position.x - 360f;
		float by = uiCamera.position.y + 120f;
		float bw = 60f, bh = 28f, gap = 8f;
		for (int i=0;i<uiButtons.size;i++) {
			UIButton b = uiButtons.get(i);
			b.rect = new Rectangle(bx, by, bw, bh);
			bx += bw + gap;
		}
		// door dir buttons
		doorDirButtons.clear();
		float dx = uiCamera.position.x - 360f;
		float dy = uiCamera.position.y + 120f - (bh + 6f);
		for (int i=0;i<doorDirections.length;i++) {
			doorDirButtons.add(new Rectangle(dx + i*(bw+6f), dy, bw, bh));
		}
		// recompute level rects
		levelRects.clear();
		float lx = uiCamera.position.x + uiCamera.viewportWidth/2f - 180f;
		float ly = uiCamera.position.y + uiCamera.viewportHeight/2f - 32f;
		float lh = 20f, lgap = 4f;
		for (int i=0;i<levelFiles.size;i++) {
			levelRects.add(new Rectangle(lx, ly - i*(lh+lgap), 170f, lh));
		}
	}

		private Door findDoorById(String id) {
			if (id == null) return null;
			for (Object o : interactableInstances) {
				if (o instanceof Door) {
					// We don't have a reliable id on Door instances; return first Door instance as heuristic
					return (Door)o;
				}
			}
			return null;
		}

		private void removePlacement(Placement p) {
			switch (p.type) {
				case WALL:
					for (int i = 0; i < state.walls.size; ++i) {
						LevelIO.LevelState.WallData wd = state.walls.get(i);
						if (wd.x == p.x && wd.y == p.y && wd.width == p.cols && wd.height == p.rows) { state.walls.removeIndex(i); break; }
					}
					for (int i = 0; i < walls.size; ++i) {
						Wall w = walls.get(i);
						if ((int)w.bounds.x == p.x && (int)w.bounds.y == p.y) { walls.removeIndex(i); break; }
					}
					break;
				case DOOR:
					// remove interactable with matching id
					for (int i = 0; i < state.interactables.size; ++i) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(i);
						if (idd.id != null && idd.id.equals(p.id)) { state.interactables.removeIndex(i); break; }
					}
					// remove record
					for (int i = 0; i < doorRecords.size; ++i) {
						if (doorRecords.get(i).id.equals(p.id)) { doorRecords.removeIndex(i); break; }
					}
					// remove door instance
					for (int i = interactableInstances.size - 1; i >= 0; --i) {
						if (interactableInstances.get(i) instanceof Door) { interactableInstances.removeIndex(i); break; }
					}
					break;
				case BUTTON:
				case LEVER:
					// remove one interactable of that type at position
					for (int i = 0; i < state.interactables.size; ++i) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(i);
						if (idd.type != null && idd.type.equalsIgnoreCase(p.type.name())) { state.interactables.removeIndex(i); break; }
					}
					// remove instance
					for (int i = interactableInstances.size - 1; i >= 0; --i) {
						Object o = interactableInstances.get(i);
						if ((p.type == ObjectType.BUTTON && o instanceof Button) || (p.type == ObjectType.LEVER && o instanceof Lever)) { interactableInstances.removeIndex(i); break; }
					}
					break;
				case BOX:
					for (int i = 0; i < state.boxes.size; ++i) { LevelIO.LevelState.BoxData bd = state.boxes.get(i); if (bd.x == p.x && bd.y == p.y) { state.boxes.removeIndex(i); break; } }
					for (int i = interactableInstances.size - 1; i >= 0; --i) if (interactableInstances.get(i) instanceof Box) interactableInstances.removeIndex(i);
					break;
				case ORB:
					for (int i = 0; i < state.orbs.size; ++i) { LevelIO.LevelState.OrbData od = state.orbs.get(i); if (od.x == p.x && od.y == p.y) { state.orbs.removeIndex(i); break; } }
					for (int i = interactableInstances.size - 1; i >= 0; --i) if (interactableInstances.get(i) instanceof Orb) interactableInstances.removeIndex(i);
					break;
				case BOSS:
					state.boss = null; bossInstance = null; break;
				case SPAWN:
					state.spawn = new LevelIO.LevelState.SpawnData(); state.spawn.x = 0; state.spawn.y = 0; break;
			}
		}

	@Override public void resize(int width, int height) {
		camera.viewportWidth = width; camera.viewportHeight = height; camera.update();
		if (uiCamera != null) {
			uiCamera.viewportWidth = width; uiCamera.viewportHeight = height;
			uiCamera.position.set(uiCamera.viewportWidth/2f, uiCamera.viewportHeight/2f, 0f);
			uiCamera.update();
			rebuildUIPositions();
		}
	}
	@Override public void pause() {}
	@Override public void resume() {}
	@Override public void hide() {}

	@Override
	public void dispose() {
		if (ctx != null) ctx.dispose();
	}
}
