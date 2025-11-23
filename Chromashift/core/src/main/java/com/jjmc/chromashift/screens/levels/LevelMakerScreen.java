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
import com.jjmc.chromashift.environment.interactable.Target;
import com.jjmc.chromashift.environment.interactable.Box;
import com.jjmc.chromashift.environment.interactable.Orb;
import com.jjmc.chromashift.environment.interactable.Laser;
import com.jjmc.chromashift.environment.interactable.Mirror;
import com.jjmc.chromashift.environment.interactable.Glass;
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
		WALL, DOOR, BUTTON, LEVER, BOX, ORB, BOSS, SPAWN, LAUNCHPAD, LASER, MIRROR, GLASS, DIAMOND, SHOP, TENTACLE, TARGET, KEY, LOCKED_DOOR, NONE
	}

	private ObjectType selectedType = ObjectType.NONE;

	// preview placement
	private int previewCols = 1, previewRows = 1;

	// collections to render interactables immediately (from LevelLoader)
	private Array<Wall> walls = new Array<>();
	private Array<Solid> solids = new Array<>();
	private Array<Interactable> interactableInstances = new Array<>();
	private Array<com.jjmc.chromashift.environment.collectible.Collectible> collectibleInstances = new Array<>();
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

	private static class LaserRecord {
		String id;
		float x, y;

		LaserRecord(String id, float x, float y) {
			this.id = id;
			this.x = x;
			this.y = y;
		}
	}

	private Array<LaserRecord> laserRecords = new Array<>();

	// Mirror quick lookup for linking (center point use width/height)
	private static class MirrorRecord {
		String id;
		float x, y, width, height;

		MirrorRecord(String id, float x, float y, float w, float h) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.width = w;
			this.height = h;
		}
	}

	private Array<MirrorRecord> mirrorRecords = new Array<>();
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

	// Laser type selection (normal vs rotating LaserRay)
	private boolean selectedLaserIsRotating = false;
	private Array<Rectangle> laserTypeRects = new Array<>();
	// Laser direction selector (U/R/D/L)
	private Array<Rectangle> laserDirRects = new Array<>();
	private float selectedLaserRotation = 0f; // 0=RIGHT, 90=UP, 180=LEFT, 270=DOWN
	private Array<Rectangle> launchpadDirRects = new Array<>();

	// Mirror rotate control
	private Rectangle mirrorRotateRect;
	private float selectedMirrorAngleDeg = 45f;

	// Glass color selection
	private Array<Rectangle> glassColorRects = new Array<>();
	private String selectedGlassColor = "CYAN"; // default
	// Target color selection (same as Button colors)
	private Array<Rectangle> targetColorRects = new Array<>();
	private Button.ButtonColor selectedTargetColor = Button.ButtonColor.RED;
	// Box color selection (same palette as Glass)
	private Array<Rectangle> boxColorRects = new Array<>();
	private String selectedBoxColor = "CYAN";
	// Respawn area dimensions for new boxes/orbs (centered). Adjustable with [ ]
	// keys.
	private float selectedBoxAreaW = 1600f, selectedBoxAreaH = 1200f;
	private float selectedOrbAreaW = 1600f, selectedOrbAreaH = 1200f;

	// lever orientation UI and preview
	private boolean selectedLeverHorizontal = false;
	private Rectangle leverOrientRect;
	private Rectangle linkToggleRect;
	private Rectangle tentacleSegmentMinusRect, tentacleSegmentPlusRect;
	private int selectedTentacleSegments = 30; // Default segment count
	private int selectedTentacleRootIndex = -1; // Selected existing tentacle root
	private boolean linkingMode = false;

	private enum LinkStage {
		PICK_SOURCE, PICK_DOORS
	}

	private LinkStage linkStage = LinkStage.PICK_SOURCE;
	private ObjectType linkSourceType = ObjectType.NONE;
	private int linkSourceX = 0, linkSourceY = 0;
	private Array<String> linkSelectedDoorIds = new Array<>();
	private com.chromashift.helper.SpriteAnimator previewLeverAnim;
	// Debug toggle to visualize respawn areas for all placed Boxes/Orbs
	private boolean debugRespawnAreas = false;
	// Selected box/orb for editing respawn area (right-click to select, [ ] to
	// resize)
	private Interactable selectedObject = null;
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
		// Detach any previous screen Stage (e.g., TestMenuScreen) so its UI stops receiving input
		try { Gdx.input.setInputProcessor(null); } catch (Throwable ignored) {}
		// Ensure culling disabled in editor so off-screen objects remain visible
		try { com.jjmc.chromashift.helper.VisibilityCuller.setEnabled(false); } catch (Throwable ignored) {}
		// Initialize editor mode flags to false (will be set true only when delete mode
		// active)
		com.jjmc.chromashift.environment.interactable.Box.EDITOR_DELETE_MODE = false;
		com.jjmc.chromashift.environment.interactable.Orb.EDITOR_DELETE_MODE = false;

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
		// Added Diamond, Shop, Tentacle, Target, Key, LockedDoor, laser types + Link + Delete (20 buttons total)
		float totalWidth = 20 * bw + 19 * gap;
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
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.LASER, "Laser"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.MIRROR, "Mirror"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.GLASS, "Glass"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.DIAMOND, "Diamond"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.SHOP, "Shop"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.TENTACLE, "Tentacle"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.TARGET, "Target"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.KEY, "Key"));
		bx += bw + gap;
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.LOCKED_DOOR, "LockDoor"));
		bx += bw + gap;
		// Linking mode toggle button (works for Button/Lever)
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.NONE, "Link"));
		bx += bw + gap;
		// Delete mode toggle button
		uiButtons.add(new UIButton(new Rectangle(bx, by, bw, bh), ObjectType.NONE, "Delete"));

		// Contextual option buttons - positioned above main buttons but only shown when
		// relevant type selected
		// door direction buttons (shown only when Door selected)
		// Position contextual options above the matching main toolbar button so they
		// don't overlap
		Rectangle doorButtonRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.DOOR) {
				doorButtonRect = ub.rect;
				break;
			}
		float optionY = (doorButtonRect != null) ? doorButtonRect.y + doorButtonRect.height + 8f : by + bh + 8f;
		float optionX = (doorButtonRect != null) ? (doorButtonRect.x + (doorButtonRect.width - (4 * bw + 3 * gap)) / 2f)
				: (uiCamera.viewportWidth - (4 * bw + 3 * gap)) / 2f;
		for (int i = 0; i < doorDirections.length; i++) {
			doorDirButtons.add(new Rectangle(optionX + i * (bw + gap), optionY, bw, bh));
		}

		// door speed controls (open/close) - small +/- boxes to the right of options
		float speedW = 28f, speedH = 20f, speedGap = 6f;
		float speedStartX = optionX + (4 * (bw + gap));
		// Place the small +/- buttons above the slider, aligned with other door
		// controls
		float doorControlsBaseY = optionY + 46f;
		doorOpenMinusRect = new Rectangle(speedStartX, doorControlsBaseY, speedW, speedH);
		doorOpenPlusRect = new Rectangle(speedStartX + speedW + speedGap, doorControlsBaseY, speedW, speedH);
		doorCloseMinusRect = new Rectangle(speedStartX, doorControlsBaseY - (speedH + 4f), speedW, speedH);
		doorClosePlusRect = new Rectangle(speedStartX + speedW + speedGap, doorControlsBaseY - (speedH + 4f),
				speedW, speedH);

		// Button color swatches (shown only when Button selected)
		float sw = 40f, sh = 24f, sg = 6f;
		Button.ButtonColor[] cols = Button.ButtonColor.values();
		float colorsWidth = cols.length * sw + (cols.length - 1) * sg;
		// position above the BUTTON toolbar button
		Rectangle buttonBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.BUTTON) {
				buttonBtnRect = ub.rect;
				break;
			}
		float buttonColorsX = (buttonBtnRect != null) ? (buttonBtnRect.x + (buttonBtnRect.width - colorsWidth) / 2f)
				: (uiCamera.viewportWidth - colorsWidth) / 2f;
		for (int i = 0; i < cols.length; i++) {
			buttonColorRects.add(new Rectangle(buttonColorsX + i * (sw + sg),
					(buttonBtnRect != null) ? (buttonBtnRect.y + buttonBtnRect.height + 8f) : optionY, sw, sh));
		}

		// Lever orientation toggle (shown only when Lever selected)
		Rectangle leverBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.LEVER) {
				leverBtnRect = ub.rect;
				break;
			}
		leverOrientRect = new Rectangle(
				(leverBtnRect != null) ? (leverBtnRect.x + (leverBtnRect.width - 80f) / 2f)
						: ((uiCamera.viewportWidth - 80f) / 2f),
				(leverBtnRect != null) ? (leverBtnRect.y + leverBtnRect.height + 8f) : optionY, 80f, sh);

		// Tentacle segment count controls (shown only when Tentacle selected)
		Rectangle tentacleBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.TENTACLE) {
				tentacleBtnRect = ub.rect;
				break;
			}
		float tentacleControlW = 28f, tentacleControlH = 24f, tentacleGap = 6f;
		float tentacleControlX = (tentacleBtnRect != null)
				? (tentacleBtnRect.x + (tentacleBtnRect.width - (2 * tentacleControlW + tentacleGap)) / 2f)
				: ((uiCamera.viewportWidth - (2 * tentacleControlW + tentacleGap)) / 2f);
		float tentacleControlY = (tentacleBtnRect != null) ? (tentacleBtnRect.y + tentacleBtnRect.height + 8f)
				: optionY;
		tentacleSegmentMinusRect = new Rectangle(tentacleControlX, tentacleControlY, tentacleControlW,
				tentacleControlH);
		tentacleSegmentPlusRect = new Rectangle(tentacleControlX + tentacleControlW + tentacleGap, tentacleControlY,
				tentacleControlW, tentacleControlH);

		// Launchpad direction buttons (shown only when Launchpad selected)
		// 3 directions: UP, LEFT, RIGHT
		launchpadDirRects.clear();
		float lpDirW = 50f, lpDirH = 24f, lpDirGap = 4f;
		float lpDirsWidth = 3 * lpDirW + 2 * lpDirGap;
		float lpDirX = (uiCamera.viewportWidth - lpDirsWidth) / 2f;
		for (int i = 0; i < 3; i++) {
			launchpadDirRects.add(new Rectangle(lpDirX + i * (lpDirW + lpDirGap), optionY, lpDirW, lpDirH));
		}

		// Laser type buttons (shown only when Laser selected)
		// 2 types: Normal (static) and Rotating (LaserRay)
		laserTypeRects.clear();
		float laserTypeW = 70f, laserTypeH = 24f, laserTypeGap = 4f;
		float laserTypesWidth = 2 * laserTypeW + laserTypeGap;
		float laserTypeX = (uiCamera.viewportWidth - laserTypesWidth) / 2f;
		laserTypeRects.add(new Rectangle(laserTypeX, optionY, laserTypeW, laserTypeH)); // Normal
		laserTypeRects.add(new Rectangle(laserTypeX + laserTypeW + laserTypeGap, optionY, laserTypeW, laserTypeH)); // Rotating

		// Laser direction buttons (U, R, D, L) - row above the type buttons
		laserDirRects.clear();
		float ldirW = 36f, ldirH = 24f, ldirGap = 6f;
		float ldirsWidth = 4 * ldirW + 3 * ldirGap;
		float ldirX = (uiCamera.viewportWidth - ldirsWidth) / 2f;
		float ldirY = optionY + laserTypeH + 8f;
		for (int i = 0; i < 4; i++) {
			laserDirRects.add(new Rectangle(ldirX + i * (ldirW + ldirGap), ldirY, ldirW, ldirH));
		}

		// Sliders for door open/close speed (raise further for clear separation)
		float sliderW = 220f, sliderH = 14f;
		float sliderX = (uiCamera.viewportWidth - sliderW) / 2f;
		// Place sliders near the other door controls (aligned with +/- buttons)
		float sliderY = optionY + 46f; // align with door +/- controls
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

		// Mirror rotate button (contextual option)
		float optW = 110f, optH = 24f;
		Rectangle mirrorBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.MIRROR) {
				mirrorBtnRect = ub.rect;
				break;
			}
		float optX = (mirrorBtnRect != null) ? (mirrorBtnRect.x + (mirrorBtnRect.width - optW) / 2f)
				: (uiCamera.viewportWidth - optW) / 2f;
		mirrorRotateRect = new Rectangle(optX,
				(mirrorBtnRect != null) ? (mirrorBtnRect.y + mirrorBtnRect.height + 8f) : optionY, optW, optH);

		// Glass color swatches (red, blue, green, yellow, purple) shown when GLASS
		// selected
		glassColorRects.clear();
		String[] gcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
		float gcw = 40f, gch = 24f, gcgap = 6f;
		float gtotal = gcolors.length * gcw + (gcolors.length - 1) * gcgap;
		Rectangle glassBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.GLASS) {
				glassBtnRect = ub.rect;
				break;
			}
		float gcx = (glassBtnRect != null) ? (glassBtnRect.x + (glassBtnRect.width - gtotal) / 2f)
				: (uiCamera.viewportWidth - gtotal) / 2f;
		for (int i = 0; i < gcolors.length; i++) {
			glassColorRects.add(new Rectangle(gcx + i * (gcw + gcgap),
					(glassBtnRect != null) ? (glassBtnRect.y + glassBtnRect.height + 8f) : optionY, gcw, gch));
		}

		// Box color swatches (same palette)
		boxColorRects.clear();
		float btotal = gcolors.length * gcw + (gcolors.length - 1) * gcgap;
		Rectangle boxBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.BOX) {
				boxBtnRect = ub.rect;
				break;
			}
		float bcx = (boxBtnRect != null) ? (boxBtnRect.x + (boxBtnRect.width - btotal) / 2f)
				: (uiCamera.viewportWidth - btotal) / 2f;
		for (int i = 0; i < gcolors.length; i++) {
			boxColorRects.add(new Rectangle(bcx + i * (gcw + gcgap),
					(boxBtnRect != null) ? (boxBtnRect.y + boxBtnRect.height + 8f) : optionY - 32f, gcw, gch));
		}

		// Target color swatches (same palette, position above TARGET toolbar button)
		targetColorRects.clear();
		float ttotal = cols.length * sw + (cols.length - 1) * sg;
		Rectangle targetBtnRect = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.TARGET) {
				targetBtnRect = ub.rect;
				break;
			}
		float targetCx = (targetBtnRect != null) ? (targetBtnRect.x + (targetBtnRect.width - ttotal) / 2f)
				: (uiCamera.viewportWidth - ttotal) / 2f;
		for (int i = 0; i < cols.length; i++) {
			targetColorRects.add(new Rectangle(targetCx + i * (sw + sg),
					(targetBtnRect != null) ? (targetBtnRect.y + targetBtnRect.height + 8f) : optionY, sw, sh));
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
		// camera.project returns screen pixels with a Y origin opposite to Input Y.
		// Flip using the actual backbuffer height to avoid HiDPI/fullscreen mismatches.
		pv.y = Gdx.graphics.getBackBufferHeight() - pv.y;
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

		// update and draw existing walls and interactables
		// Ensure interactables (e.g., Laser/LaserRay) compute cached beams before
		// rendering
		for (int i = 0; i < interactableInstances.size; i++) {
			try {
				interactableInstances.get(i).update(delta);
			} catch (Throwable ignored) {
			}
		}
		// Update collectibles for animation
		for (int i = 0; i < collectibleInstances.size; i++) {
			try {
				collectibleInstances.get(i).update(delta);
			} catch (Throwable ignored) {
			}
		}
		// Finalize Target activation state after all lasers have been updated
		try {
			Target.finalizeFrame();
		} catch (Throwable ignored) {
		}
		batch.begin();
		for (Wall w : walls)
			w.render(batch);
		for (Interactable i : interactableInstances) {
			i.render(batch);
		}
		// Render collectibles (Diamond, etc.)
		for (com.jjmc.chromashift.environment.collectible.Collectible c : collectibleInstances) {
			c.render(batch);
		}
		if (spawnPreview != null) {
			// Editor: always show first frame (non-animated)
			spawnPreview.renderFirstFrame(batch);
		}
		if (bossInstance != null)
			bossInstance.render(batch);
		batch.end(); // preview of the current object under mouse (drawn in UI space so it stays
						// under cursor)

		// Render tentacle roots (static indicator only, no physics in editor)
		if (state.tentacles != null && state.tentacles.size > 0) {
			shape.setProjectionMatrix(camera.combined);
			shape.begin(ShapeRenderer.ShapeType.Filled);
			shape.setColor(0.6f, 0.1f, 0.6f, 0.7f);
			for (int i = 0; i < state.tentacles.size; i++) {
				LevelIO.LevelState.TentacleData td = state.tentacles.get(i);
				shape.circle(td.x + 16f, td.y + 16f, 16f);
			}
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(Color.MAGENTA);
			for (int i = 0; i < state.tentacles.size; i++) {
				LevelIO.LevelState.TentacleData td = state.tentacles.get(i);
				// Highlight selected root
				if (i == selectedTentacleRootIndex) {
					shape.setColor(Color.GOLD);
					shape.circle(td.x + 16f, td.y + 16f, 18f);
					shape.setColor(Color.MAGENTA);
				}
				shape.circle(td.x + 16f, td.y + 16f, 16f);
			}
			shape.end();
			// Segment count label for selected root
			if (selectedTentacleRootIndex >= 0 && selectedTentacleRootIndex < state.tentacles.size) {
				batch.begin();
				LevelIO.LevelState.TentacleData td = state.tentacles.get(selectedTentacleRootIndex);
				font.setColor(Color.WHITE);
				font.draw(batch, "Segments: " + td.segments, td.x, td.y + 48f);
				batch.end();
			}
		}

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
			case BUTTON: {
				previewW = 64f;
				previewH = 32f;
				break;
			}
			case LEVER: {
				previewW = 64f;
				previewH = 64f;
				break;
			}
			case BOX: {
				previewW = 24f;
				previewH = 24f;
				break;
			}
			case ORB: {
				previewW = 24f;
				previewH = 24f;
				break;
			}
				case TENTACLE: {
					// Tentacle root occupies a 32x32 cell
					previewW = 32f;
					previewH = 32f;
					break;
				}
			case BOSS: {
				previewW = 96f;
				previewH = 96f;
				break;
			}
			case KEY: {
				previewW = 32f;
				previewH = 32f;
				break;
			}
			case LOCKED_DOOR: {
				previewW = 48f;
				previewH = 96f;
				break;
			}
			case SPAWN: {
				previewW = 16f;
				previewH = 32f;
				break;
			}
			case LAUNCHPAD: {
				if (selectedLaunchpadDirection == com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP) {
					previewW = 64f;
					previewH = 32f;
				} else {
					previewW = 32f;
					previewH = 64f;
				}
				break;
			}
			case LASER: {
				previewW = 32f;
				previewH = 32f;
				break;
			}
			case MIRROR: {
				// Mirror is fixed 32x32
				previewW = 32f;
				previewH = 32f;
				break;
			}
			case GLASS: {
				// Glass is fixed 16x16, centered within the 32x32 cell
				previewW = 16f;
				previewH = 16f;
				break;
			}
			case DIAMOND: {
				// Diamond is 32x32
				previewW = 32f;
				previewH = 32f;
				break;
			}
			case SHOP: {
				// Shop is 64x64
				previewW = 64f;
				previewH = 64f;
				break;
			}
			case TARGET: {
				// Target is 32x32
				previewW = 32f;
				previewH = 32f;
				break;
			}
			case NONE:
			default: {
				break;
			}
		}
		Rectangle previewWorldRect = new Rectangle(worldPreview.x, worldPreview.y, previewW, previewH);
		if (selectedType == ObjectType.GLASS) {
			// center 16x16 glass in the 32x32 grid cell
			previewWorldRect.set(worldPreview.x + 8f, worldPreview.y + 8f, 16f, 16f);
		}
		previewBlocked = !isAreaFree(previewWorldRect);

		// draw UI (screen-fixed) using uiCamera
		shape.setProjectionMatrix(uiCamera.combined);
		batch.setProjectionMatrix(uiCamera.combined);
		shape.begin(ShapeRenderer.ShapeType.Filled);
		// Main object type buttons (always visible)
		for (UIButton b : uiButtons) {
			Color c = (selectedType == b.type) ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f);
			// Highlight toggle-style buttons when active
			if ("Link".equalsIgnoreCase(b.label) && linkingMode)
				c = Color.GOLD;
			if ("Delete".equalsIgnoreCase(b.label) && deleteMode)
				c = Color.GOLD;
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
			// draw sliders for open/close speed (draw at rect.y so hits match visuals)
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
					case RED:
						c = Color.RED;
						break;
					case BLUE:
						c = Color.BLUE;
						break;
					case GREEN:
						c = Color.GREEN;
						break;
					case YELLOW:
						c = Color.YELLOW;
						break;
					case PURPLE:
						c = Color.PURPLE;
						break;
					default:
						break;
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
		} else if (selectedType == ObjectType.TENTACLE) {
			// tentacle segment count controls
			if (tentacleSegmentMinusRect != null && tentacleSegmentPlusRect != null) {
				// Draw minus button
				shape.setColor(new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(tentacleSegmentMinusRect.x, tentacleSegmentMinusRect.y,
						tentacleSegmentMinusRect.width, tentacleSegmentMinusRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(tentacleSegmentMinusRect.x, tentacleSegmentMinusRect.y,
						tentacleSegmentMinusRect.width, tentacleSegmentMinusRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);

				// Draw plus button
				shape.setColor(new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(tentacleSegmentPlusRect.x, tentacleSegmentPlusRect.y,
						tentacleSegmentPlusRect.width, tentacleSegmentPlusRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(tentacleSegmentPlusRect.x, tentacleSegmentPlusRect.y,
						tentacleSegmentPlusRect.width, tentacleSegmentPlusRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
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
		} else if (selectedType == ObjectType.LASER) {
			// laser type buttons (Normal vs Rotating)
			for (int i = 0; i < laserTypeRects.size; i++) {
				Rectangle r = laserTypeRects.get(i);
				boolean selected = (i == 1) ? selectedLaserIsRotating : !selectedLaserIsRotating;
				shape.setColor(selected ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
			// laser direction buttons (U, R, D, L)
			for (int i = 0; i < laserDirRects.size; i++) {
				Rectangle r = laserDirRects.get(i);
				boolean selected;
				// order: U(90), R(0), D(270), L(180)
				switch (i) {
					case 0:
						selected = (selectedLaserRotation == 90f);
						break;
					case 1:
						selected = (selectedLaserRotation == 0f);
						break;
					case 2:
						selected = (selectedLaserRotation == 270f);
						break;
					case 3:
						selected = (selectedLaserRotation == 180f);
						break;
					default:
						selected = false;
						break;
				}
				shape.setColor(selected ? Color.GOLD : new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
		} else if (selectedType == ObjectType.MIRROR) {
			// Mirror rotate button
			if (mirrorRotateRect != null) {
				shape.setColor(new Color(0.2f, 0.2f, 0.25f, 0.9f));
				shape.rect(mirrorRotateRect.x, mirrorRotateRect.y, mirrorRotateRect.width, mirrorRotateRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(Color.WHITE);
				shape.rect(mirrorRotateRect.x, mirrorRotateRect.y, mirrorRotateRect.width, mirrorRotateRect.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
		} else if (selectedType == ObjectType.BOX) {
			// Box color swatches
			String[] bcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
			for (int i = 0; i < boxColorRects.size && i < bcolors.length; i++) {
				Rectangle r = boxColorRects.get(i);
				Color c;
				switch (bcolors[i]) {
					case "RED":
						c = Color.RED;
						break;
					case "BLUE":
						c = Color.BLUE;
						break;
					case "GREEN":
						c = Color.GREEN;
						break;
					case "YELLOW":
						c = Color.YELLOW;
						break;
					case "PURPLE":
						c = Color.PURPLE;
						break;
					default:
						c = Color.CYAN;
						break;
				}
				shape.setColor(c);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(selectedBoxColor.equalsIgnoreCase(bcolors[i]) ? Color.WHITE : Color.LIGHT_GRAY);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
			// Draw respawn area preview outline around mouse preview origin
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(new Color(0f, 0.6f, 1f, 0.6f));
			float aw = selectedBoxAreaW;
			float ah = selectedBoxAreaH;
			shape.rect(screenGx - aw / 2f + 12f, screenGy - ah / 2f + 12f, aw, ah);
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Filled);
		} else if (selectedType == ObjectType.GLASS) {
			// Glass color swatches
			String[] gcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
			for (int i = 0; i < glassColorRects.size && i < gcolors.length; i++) {
				Rectangle r = glassColorRects.get(i);
				Color c;
				switch (gcolors[i]) {
					case "RED":
						c = Color.RED;
						break;
					case "BLUE":
						c = Color.BLUE;
						break;
					case "GREEN":
						c = Color.GREEN;
						break;
					case "YELLOW":
						c = Color.YELLOW;
						break;
					case "PURPLE":
						c = Color.PURPLE;
						break;
					default:
						c = Color.CYAN;
						break;
				}
				shape.setColor(c);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(selectedGlassColor.equalsIgnoreCase(gcolors[i]) ? Color.WHITE : Color.LIGHT_GRAY);
				shape.rect(r.x, r.y, r.width, r.height);
				shape.end();
				shape.begin(ShapeRenderer.ShapeType.Filled);
			}
		} else if (selectedType == ObjectType.TARGET) {
			// Target color swatches (same as button colors)
			for (int i = 0; i < targetColorRects.size; i++) {
				Rectangle r = targetColorRects.get(i);
				Button.ButtonColor tc = Button.ButtonColor.values()[i];
				Color c = Color.WHITE;
				switch (tc) {
					case RED:
						c = Color.RED;
						break;
					case BLUE:
						c = Color.BLUE;
						break;
					case GREEN:
						c = Color.GREEN;
						break;
					case YELLOW:
						c = Color.YELLOW;
						break;
					case PURPLE:
						c = Color.PURPLE;
						break;
					default:
						break;
				}
				shape.setColor(c);
				shape.rect(r.x, r.y, r.width, r.height);
				// highlight selected with border
				if (tc == selectedTargetColor) {
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
				font.draw(batch, "-", doorOpenMinusRect.x + 8, doorOpenMinusRect.y + doorOpenMinusRect.height);
				font.draw(batch, "+", doorOpenPlusRect.x + 8, doorOpenPlusRect.y + doorOpenPlusRect.height);
				font.draw(batch, String.format("Open: %.1f", selectedDoorOpenSpeed), doorOpenPlusRect.x,
						doorOpenPlusRect.y + doorOpenPlusRect.height - 2);
				font.draw(batch, "-", doorCloseMinusRect.x + 8, doorCloseMinusRect.y + doorCloseMinusRect.height);
				font.draw(batch, "+", doorClosePlusRect.x + 8, doorClosePlusRect.y + doorClosePlusRect.height);
				font.draw(batch, String.format("Close: %.1f", selectedDoorCloseSpeed), doorClosePlusRect.x,
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
		} else if (selectedType == ObjectType.TENTACLE) {
			// tentacle segment count controls
			if (tentacleSegmentMinusRect != null && tentacleSegmentPlusRect != null) {
				font.setColor(Color.WHITE);
				font.draw(batch, "-", tentacleSegmentMinusRect.x + 10,
						tentacleSegmentMinusRect.y + tentacleSegmentMinusRect.height - 6);
				font.draw(batch, "+", tentacleSegmentPlusRect.x + 10,
						tentacleSegmentPlusRect.y + tentacleSegmentPlusRect.height - 6);
				// Draw segment count between buttons
				font.draw(batch, "Seg: " + selectedTentacleSegments,
						(tentacleSegmentMinusRect.x + tentacleSegmentPlusRect.x + tentacleSegmentPlusRect.width) / 2f
								- 20,
						tentacleSegmentMinusRect.y + tentacleSegmentMinusRect.height + 16);
			}
		} else if (selectedType == ObjectType.LASER) {
			// laser type labels
			for (int i = 0; i < laserTypeRects.size; i++) {
				Rectangle r = laserTypeRects.get(i);
				font.setColor(Color.WHITE);
				String label = (i == 0) ? "Normal" : "Rotating";
				font.draw(batch, label, r.x + 8, r.y + r.height - 6);
			}
			// laser direction labels U, R, D, L
			String[] dlabels = { "U", "R", "D", "L" };
			for (int i = 0; i < laserDirRects.size && i < dlabels.length; i++) {
				Rectangle r = laserDirRects.get(i);
				font.setColor(Color.WHITE);
				font.draw(batch, dlabels[i], r.x + 14, r.y + r.height - 6);
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
		} else if (selectedType == ObjectType.MIRROR) {
			if (mirrorRotateRect != null) {
				font.setColor(Color.WHITE);
				font.draw(batch, "Rotate 45°", mirrorRotateRect.x + 10,
						mirrorRotateRect.y + mirrorRotateRect.height - 6);
				// show current angle
				font.draw(batch, String.format("Angle: %.0f°", selectedMirrorAngleDeg), mirrorRotateRect.x + 10,
						mirrorRotateRect.y - 4);
			}
		} else if (selectedType == ObjectType.GLASS) {
			// labels for glass color (first letter)
			String[] gcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
			for (int i = 0; i < glassColorRects.size && i < gcolors.length; i++) {
				Rectangle r = glassColorRects.get(i);
				font.setColor(Color.BLACK);
				font.draw(batch, gcolors[i].substring(0, 1), r.x + r.width / 2f - 4, r.y + r.height - 6);
			}
		} else if (selectedType == ObjectType.BOX) {
			// labels for box color swatches
			String[] bcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
			for (int i = 0; i < boxColorRects.size && i < bcolors.length; i++) {
				Rectangle r = boxColorRects.get(i);
				font.setColor(Color.BLACK);
				font.draw(batch, bcolors[i].substring(0, 1), r.x + r.width / 2f - 4, r.y + r.height - 6);
			}
			// Respawn area size label
			font.setColor(Color.WHITE);
			if (boxColorRects.size > 0) {
				Rectangle first = boxColorRects.first();
				font.draw(batch,
						String.format("Area %.0fx%.0f ([ ] width, - = height)", selectedBoxAreaW, selectedBoxAreaH),
						first.x, first.y - 12);
			}
		} else if (selectedType == ObjectType.TARGET) {
			// target color labels (first letter)
			for (int i = 0; i < targetColorRects.size; i++) {
				Rectangle r = targetColorRects.get(i);
				font.setColor(Color.BLACK);
				font.draw(batch, Button.ButtonColor.values()[i].name().substring(0, 1), r.x + 16, r.y + r.height - 6);
			}
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
				previewLeverAnim.render(batch, screenGx, screenGy, 64, 64);
				batch.end();
				if (previewBlocked) {
					shape.begin(ShapeRenderer.ShapeType.Filled);
					shape.setColor(new Color(1f, 0f, 0f, 0.45f));
					shape.rect(screenGx, screenGy, 64, 64);
					shape.end();
					shapeActive = true;
				}
			} else {
				shape.setColor(previewBlocked ? Color.FIREBRICK : Color.BROWN);
				shape.rect(screenGx, screenGy, 64, 64);
			}
		} else if (selectedType == ObjectType.BOX) {
			// draw box preview with selected color
			Color bc = Color.CYAN;
			switch (selectedBoxColor.toUpperCase()) {
				case "RED" -> bc = Color.RED;
				case "BLUE" -> bc = Color.BLUE;
				case "GREEN" -> bc = Color.GREEN;
				case "YELLOW" -> bc = Color.YELLOW;
				case "PURPLE" -> bc = Color.PURPLE;
				default -> bc = Color.CYAN;
			}
			shape.setColor(previewBlocked ? Color.FIREBRICK : bc);
			shape.rect(screenGx, screenGy, 24, 24);
		} else if (selectedType == ObjectType.ORB) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.ORANGE);
			shape.circle(screenGx + 12, screenGy + 12, 12);
			// Respawn area preview outline for orb
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(new Color(1f, 0.5f, 0f, 0.6f));
			float aw = selectedOrbAreaW;
			float ah = selectedOrbAreaH;
			shape.rect(screenGx - aw / 2f + 12f, screenGy - ah / 2f + 12f, aw, ah);
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Filled);
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
		} else if (selectedType == ObjectType.LASER) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.RED);
			shape.rect(screenGx, screenGy, 32, 32);
		} else if (selectedType == ObjectType.MIRROR) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.CYAN);
			shape.rect(screenGx, screenGy, 32f, 32f);
		} else if (selectedType == ObjectType.GLASS) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.SKY);
			// draw centered 16x16 preview in the 32x32 cell
			shape.rect(screenGx + 8f, screenGy + 8f, 16f, 16f);
		} else if (selectedType == ObjectType.DIAMOND) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.CYAN);
			// draw diamond shape (approximate as a rotated square for preview)
			shape.rect(screenGx, screenGy, 32f, 32f);
		} else if (selectedType == ObjectType.SHOP) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : Color.GOLD);
			shape.rect(screenGx, screenGy, 64f, 64f);
		} else if (selectedType == ObjectType.TENTACLE) {
			shape.setColor(previewBlocked ? Color.FIREBRICK : new Color(0.6f, 0.1f, 0.6f, 0.7f));
			// Draw tentacle anchor point (larger circle)
			shape.circle(screenGx + 16, screenGy + 16, 16);
			// Draw segment count indicator
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(Color.MAGENTA);
			shape.circle(screenGx + 16, screenGy + 16, 16);
			shape.end();
			shape.begin(ShapeRenderer.ShapeType.Filled);
		} else if (selectedType == ObjectType.TARGET) {
			// Draw colored target preview (32x32)
			Color tc = Color.RED;
			switch (selectedTargetColor) {
				case RED:
					tc = Color.RED;
					break;
				case BLUE:
					tc = Color.BLUE;
					break;
				case GREEN:
					tc = Color.GREEN;
					break;
				case YELLOW:
					tc = Color.YELLOW;
					break;
				case PURPLE:
					tc = Color.PURPLE;
					break;
				default:
					break;
			}
			shape.setColor(previewBlocked ? Color.FIREBRICK : tc);
			shape.rect(screenGx, screenGy, 32f, 32f);
		} else if (selectedType == ObjectType.ORB) {
			// Orb area label (reuse first box rect if available or fallback near preview)
			font.setColor(Color.WHITE);
			float labelX = 20f;
			float labelY = 40f;
			if (boxColorRects.size > 0) {
				Rectangle first = boxColorRects.first();
				labelX = first.x;
				labelY = first.y - 12;
			}
			font.draw(batch,
					String.format("Area %.0fx%.0f ([ ] width, - = height)", selectedOrbAreaW, selectedOrbAreaH), labelX,
					labelY);
		}
		if (shapeActive)
			shape.end();

		// Linking preview: when in link mode, show possible link targets
		if (linkingMode) {
			// Draw world-space lines from the selected source to all doors (possible
			// targets)
			shape.setProjectionMatrix(camera.combined);
			// If we have picked a source already, draw lines to doors
			if (linkStage == LinkStage.PICK_DOORS && linkSourceType != ObjectType.NONE) {
				// try to find a runtime object for the source to get an accurate center
				float srcCx = linkSourceX + 16f;
				float srcCy = linkSourceY + 16f;
				for (Interactable it : interactableInstances) {
					if ((linkSourceType == ObjectType.BUTTON && it instanceof Button)
							|| (linkSourceType == ObjectType.LEVER && it instanceof Lever)) {
						Rectangle b = it.getBounds();
						if (b != null && Math.abs(b.x - linkSourceX) < 5f && Math.abs(b.y - linkSourceY) < 5f) {
							srcCx = b.x + b.width / 2f;
							srcCy = b.y + b.height / 2f;
							break;
						}
					}
				}
				// draw filled circles at door centers; highlight already-selected targets
				shape.begin(ShapeRenderer.ShapeType.Filled);
				for (DoorRecord dr : doorRecords) {
					if (dr == null)
						continue;
					float dx = dr.x + (Math.max(1, dr.cols) * 32f) / 2f;
					float dy = dr.y + (Math.max(1, dr.rows) * 32f) / 2f;
					boolean sel = linkSelectedDoorIds.contains(dr.id, false);
					shape.setColor(sel ? Color.GOLD : new Color(0.2f, 0.5f, 1f, 0.9f));
					shape.circle(dx, dy, 4f);
				}
				shape.end();
			}
		}
		// Persistent saved links: draw lines between sources and their saved targets
		// (doors or lasers)
		if (state != null && state.interactables != null) {
			shape.setProjectionMatrix(camera.combined);
			shape.begin(ShapeRenderer.ShapeType.Line);
			// Persistent saved links: draw in red to distinguish from previews
			shape.setColor(new Color(1f, 0f, 0f, 0.85f));
			for (LevelIO.LevelState.InteractableData src : state.interactables) {
				if (src == null || src.targetId == null || src.targetId.trim().isEmpty())
					continue;
				float srcCx = src.x + 16f;
				float srcCy = src.y + 16f;
				String[] parts = src.targetId.split(",");
				for (String p : parts) {
					String tid = p.trim();
					if (tid.length() == 0)
						continue;
					// find door target
					boolean drawn = false;
					for (DoorRecord dr : doorRecords) {
						if (dr != null && dr.id != null && dr.id.equals(tid)) {
							float dx = dr.x + (Math.max(1, dr.cols) * 32f) / 2f;
							float dy = dr.y + (Math.max(1, dr.rows) * 32f) / 2f;
							shape.line(srcCx, srcCy, dx, dy);
							drawn = true;
							break;
						}
					}
					if (drawn)
						continue;
					// find laser target
					for (LaserRecord lr : laserRecords) {
						if (lr != null && lr.id != null && lr.id.equals(tid)) {
							float dx = lr.x + 16f;
							float dy = lr.y + 16f;
							shape.line(srcCx, srcCy, dx, dy);
							break;
						}
					}
					// find mirror target
					for (MirrorRecord mr : mirrorRecords) {
						if (mr != null && mr.id != null && mr.id.equals(tid)) {
							float dx = mr.x + mr.width / 2f;
							float dy = mr.y + mr.height / 2f;
							shape.line(srcCx, srcCy, dx, dy);
							break;
						}
					}
				}
			}
			shape.end();
		}

		// Debug draw: respawn areas for all Boxes/Orbs if toggled
		if (debugRespawnAreas) {
			shape.setProjectionMatrix(camera.combined);
			shape.begin(ShapeRenderer.ShapeType.Line);
			shape.setColor(new Color(0f, 0.5f, 1f, 0.25f));
			for (Interactable it : interactableInstances) {
				if (it instanceof Box b) {
					Rectangle ra = b.getRespawnArea();
					if (ra != null)
						shape.rect(ra.x, ra.y, ra.width, ra.height);
				} else if (it instanceof Orb o) {
					Rectangle ra = o.getRespawnArea();
					if (ra != null)
						shape.rect(ra.x, ra.y, ra.width, ra.height);
				}
			}
			shape.end();
		}

		// Highlight selected box/orb for editing
		if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
			shape.setProjectionMatrix(camera.combined);
			shape.begin(ShapeRenderer.ShapeType.Line);
			Gdx.gl.glLineWidth(3f);
			shape.setColor(Color.YELLOW);
			Rectangle b = selectedObject.getBounds();
			shape.rect(b.x - 2, b.y - 2, b.width + 4, b.height + 4);
			// Draw respawn area outline
			shape.setColor(new Color(1f, 1f, 0f, 0.7f));
			Rectangle ra = null;
			if (selectedObject instanceof Box box)
				ra = box.getRespawnArea();
			else if (selectedObject instanceof Orb orb)
				ra = orb.getRespawnArea();
			if (ra != null)
				shape.rect(ra.x, ra.y, ra.width, ra.height);
			Gdx.gl.glLineWidth(1f);
			shape.end();
		}

		// Linking preview: when in link mode, show possible link targets
		if (linkingMode) {
			// Draw world-space lines from the selected source to all doors (possible
			// targets)
			shape.setProjectionMatrix(camera.combined);
			// If we have picked a source already, draw lines to doors
			if (linkStage == LinkStage.PICK_DOORS && linkSourceType != ObjectType.NONE) {
				shape.begin(ShapeRenderer.ShapeType.Line);
				shape.setColor(new Color(0.2f, 0.5f, 1f, 0.9f));
				for (Interactable it : interactableInstances) {
					if (it instanceof Button || it instanceof Lever) {
						Rectangle b = it.getBounds();
						if (b != null) {
							shape.rect(b.x - 2f, b.y - 2f, b.width + 4f, b.height + 4f);
						}
					}
				}
				shape.end();
			}
		}

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
		font.draw(batch,
				"Arrow Keys: adjust size (Wall/Door). Left click=place. Delete button toggles delete. P=Export. Ctrl+S=Save",
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
		// Respawn debug hint
		if (debugRespawnAreas) {
			font.setColor(Color.CYAN);
			font.draw(batch, "Respawn Areas ON (F3 to toggle)", uiCamera.position.x - 380, uiCamera.position.y + 110);
		}
		// Selected object info
		if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
			font.setColor(Color.YELLOW);
			String type = selectedObject instanceof Box ? "Box" : "Orb";
			Rectangle ra = null;
			if (selectedObject instanceof Box box)
				ra = box.getRespawnArea();
			else if (selectedObject instanceof Orb orb)
				ra = orb.getRespawnArea();
			if (ra != null) {
				font.draw(batch,
						String.format("Selected %s - Area: %.0fx%.0f ([ ] width, - = height)", type, ra.width,
								ra.height),
						uiCamera.position.x - 380, uiCamera.position.y + 95);
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
		// Tentacle root selection/move support
		// Maintain selected tentacle root index
		if (selectedTentacleRootIndex >= state.tentacles.size) {
			selectedTentacleRootIndex = -1;
		}
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
		// Resize respawn area with independent width/height controls when placing Box
		// or Orb OR editing selected object
		// [ ] for width, - = for height
		if (selectedType == ObjectType.BOX || selectedType == ObjectType.ORB || selectedObject != null) {
			float step = 200f;

			// WIDTH CONTROLS: [ ]
			if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
				if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
					// Decrease width of selected object's respawn area
					Rectangle ra = null;
					if (selectedObject instanceof Box box)
						ra = box.getRespawnArea();
					else if (selectedObject instanceof Orb orb)
						ra = orb.getRespawnArea();
					if (ra != null) {
						float newW = Math.max(200f, ra.width - step);
						Rectangle bounds = selectedObject.getBounds();
						float cx = bounds.x + bounds.width / 2f;
						float cy = bounds.y + bounds.height / 2f;
						if (selectedObject instanceof Box box)
							box.setRespawnArea(new Rectangle(cx - newW / 2f, cy - ra.height / 2f, newW, ra.height));
						else if (selectedObject instanceof Orb orb)
							orb.setRespawnArea(new Rectangle(cx - newW / 2f, cy - ra.height / 2f, newW, ra.height));
						updateRespawnAreaInState(selectedObject, newW, ra.height);
					}
				} else if (selectedType == ObjectType.BOX) {
					selectedBoxAreaW = Math.max(200f, selectedBoxAreaW - step);
				} else if (selectedType == ObjectType.ORB) {
					selectedOrbAreaW = Math.max(200f, selectedOrbAreaW - step);
				}
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
				if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
					// Increase width of selected object's respawn area
					Rectangle ra = null;
					if (selectedObject instanceof Box box)
						ra = box.getRespawnArea();
					else if (selectedObject instanceof Orb orb)
						ra = orb.getRespawnArea();
					if (ra != null) {
						float newW = Math.min(5000f, ra.width + step);
						Rectangle bounds = selectedObject.getBounds();
						float cx = bounds.x + bounds.width / 2f;
						float cy = bounds.y + bounds.height / 2f;
						if (selectedObject instanceof Box box)
							box.setRespawnArea(new Rectangle(cx - newW / 2f, cy - ra.height / 2f, newW, ra.height));
						else if (selectedObject instanceof Orb orb)
							orb.setRespawnArea(new Rectangle(cx - newW / 2f, cy - ra.height / 2f, newW, ra.height));
						updateRespawnAreaInState(selectedObject, newW, ra.height);
					}
				} else if (selectedType == ObjectType.BOX) {
					selectedBoxAreaW = Math.min(5000f, selectedBoxAreaW + step);
				} else if (selectedType == ObjectType.ORB) {
					selectedOrbAreaW = Math.min(5000f, selectedOrbAreaW + step);
				}
			}

			// HEIGHT CONTROLS: - =
			if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
				if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
					// Decrease height of selected object's respawn area
					Rectangle ra = null;
					if (selectedObject instanceof Box box)
						ra = box.getRespawnArea();
					else if (selectedObject instanceof Orb orb)
						ra = orb.getRespawnArea();
					if (ra != null) {
						float newH = Math.max(200f, ra.height - step);
						Rectangle bounds = selectedObject.getBounds();
						float cx = bounds.x + bounds.width / 2f;
						float cy = bounds.y + bounds.height / 2f;
						if (selectedObject instanceof Box box)
							box.setRespawnArea(new Rectangle(cx - ra.width / 2f, cy - newH / 2f, ra.width, newH));
						else if (selectedObject instanceof Orb orb)
							orb.setRespawnArea(new Rectangle(cx - ra.width / 2f, cy - newH / 2f, ra.width, newH));
						updateRespawnAreaInState(selectedObject, ra.width, newH);
					}
				} else if (selectedType == ObjectType.BOX) {
					selectedBoxAreaH = Math.max(200f, selectedBoxAreaH - step);
				} else if (selectedType == ObjectType.ORB) {
					selectedOrbAreaH = Math.max(200f, selectedOrbAreaH - step);
				}
			}
			if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS)) {
				if (selectedObject != null && (selectedObject instanceof Box || selectedObject instanceof Orb)) {
					// Increase height of selected object's respawn area
					Rectangle ra = null;
					if (selectedObject instanceof Box box)
						ra = box.getRespawnArea();
					else if (selectedObject instanceof Orb orb)
						ra = orb.getRespawnArea();
					if (ra != null) {
						float newH = Math.min(5000f, ra.height + step);
						Rectangle bounds = selectedObject.getBounds();
						float cx = bounds.x + bounds.width / 2f;
						float cy = bounds.y + bounds.height / 2f;
						if (selectedObject instanceof Box box)
							box.setRespawnArea(new Rectangle(cx - ra.width / 2f, cy - newH / 2f, ra.width, newH));
						else if (selectedObject instanceof Orb orb)
							orb.setRespawnArea(new Rectangle(cx - ra.width / 2f, cy - newH / 2f, ra.width, newH));
						updateRespawnAreaInState(selectedObject, ra.width, newH);
					}
				} else if (selectedType == ObjectType.BOX) {
					selectedBoxAreaH = Math.min(5000f, selectedBoxAreaH + step);
				} else if (selectedType == ObjectType.ORB) {
					selectedOrbAreaH = Math.min(5000f, selectedOrbAreaH + step);
				}
			}
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
		} else if (selectedType == ObjectType.TENTACLE && selectedTentacleRootIndex >= 0) {
			// Move selected tentacle root in grid increments
			LevelIO.LevelState.TentacleData td = state.tentacles.get(selectedTentacleRootIndex);
			float moveX = 0f, moveY = 0f;
			if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) moveX = -32f;
			if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) moveX = 32f;
			if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) moveY = -32f;
			if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) moveY = 32f;
			if (moveX != 0f || moveY != 0f) {
				Rectangle newPos = new Rectangle(td.x + moveX, td.y + moveY, 32f, 32f);
				if (isAreaFree(newPos)) {
					td.x += moveX; td.y += moveY;
					toastText = "Tentacle root moved"; toastTimer = 1.0f;
				} else {
					toastText = "Blocked"; toastTimer = 0.6f;
				}
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
						// When entering link mode, clear delete and any selected placement tool
						linkingMode = !linkingMode;
						if (linkingMode) {
							// entering link mode -> deselect other placement buttons
							selectedType = ObjectType.NONE;
							deleteMode = false;
						} else {
							// leaving link mode
							linkStage = LinkStage.PICK_SOURCE;
							linkSourceType = ObjectType.NONE;
							linkSelectedDoorIds.clear();
						}
						if (linkingMode) {
							linkStage = LinkStage.PICK_SOURCE;
							linkSourceType = ObjectType.NONE;
							linkSelectedDoorIds.clear();
							toastText = "Link mode: click a Button/Lever";
							toastTimer = 1.5f;
						} else {
							toastText = "Link mode off";
							toastTimer = 1.2f;
						}
					} else if ("Delete".equalsIgnoreCase(b.label)) {
						// Toggle delete mode
						// When entering delete mode, deselect other placement tools
						deleteMode = !deleteMode;
						if (deleteMode) {
							selectedType = ObjectType.NONE;
							linkingMode = false;
							com.jjmc.chromashift.environment.interactable.Box.EDITOR_DELETE_MODE = true;
							com.jjmc.chromashift.environment.interactable.Orb.EDITOR_DELETE_MODE = true;
						} else {
							com.jjmc.chromashift.environment.interactable.Box.EDITOR_DELETE_MODE = false;
							com.jjmc.chromashift.environment.interactable.Orb.EDITOR_DELETE_MODE = false;
						}
						toastText = deleteMode ? "Delete mode ON (Left-click to delete)" : "Delete mode OFF";
						toastTimer = 1.4f;
					} else {
						selectedType = b.type;
						// Selecting any placement tool should clear toggle modes
						linkingMode = false;
						linkStage = LinkStage.PICK_SOURCE;
						linkSourceType = ObjectType.NONE;
						linkSelectedDoorIds.clear();
						deleteMode = false;
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
			} else if (selectedType == ObjectType.TENTACLE) {
				// Handle tentacle segment count buttons
				if (tentacleSegmentMinusRect != null && tentacleSegmentMinusRect.contains(ux, uy)) {
					if (selectedTentacleRootIndex >= 0) {
						LevelIO.LevelState.TentacleData td = state.tentacles.get(selectedTentacleRootIndex);
						td.segments = Math.max(10, td.segments - 5);
					} else {
						selectedTentacleSegments = Math.max(10, selectedTentacleSegments - 5);
					}
					return;
				}
				if (tentacleSegmentPlusRect != null && tentacleSegmentPlusRect.contains(ux, uy)) {
					if (selectedTentacleRootIndex >= 0) {
						LevelIO.LevelState.TentacleData td = state.tentacles.get(selectedTentacleRootIndex);
						td.segments = Math.min(50, td.segments + 5);
					} else {
						selectedTentacleSegments = Math.min(50, selectedTentacleSegments + 5);
					}
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
			} else if (selectedType == ObjectType.LASER) {
				// Handle laser type button clicks (Normal vs Rotating)
				for (int i = 0; i < laserTypeRects.size; i++) {
					if (laserTypeRects.get(i).contains(ux, uy)) {
						selectedLaserIsRotating = (i == 1);
						return;
					}
				}
				// Handle laser direction selection (U, R, D, L)
				for (int i = 0; i < laserDirRects.size; i++) {
					if (laserDirRects.get(i).contains(ux, uy)) {
						// order: U(90), R(0), D(270), L(180)
						switch (i) {
							case 0 -> selectedLaserRotation = 90f;
							case 1 -> selectedLaserRotation = 0f;
							case 2 -> selectedLaserRotation = 270f;
							case 3 -> selectedLaserRotation = 180f;
						}
						return;
					}
				}
			} else if (selectedType == ObjectType.MIRROR) {
				// Handle mirror rotate button
				if (mirrorRotateRect != null && mirrorRotateRect.contains(ux, uy)) {
					selectedMirrorAngleDeg = (selectedMirrorAngleDeg + 45f) % 360f;
					return;
				}
			} else if (selectedType == ObjectType.BOX) {
				// Box color swatch click
				String[] bcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
				for (int i = 0; i < boxColorRects.size && i < bcolors.length; i++) {
					if (boxColorRects.get(i).contains(ux, uy)) {
						selectedBoxColor = bcolors[i];
						return;
					}
				}
			} else if (selectedType == ObjectType.GLASS) {
				// Glass color swatch click
				String[] gcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
				for (int i = 0; i < glassColorRects.size && i < gcolors.length; i++) {
					if (glassColorRects.get(i).contains(ux, uy)) {
						selectedGlassColor = gcolors[i];
						return;
					}
				}
			} else if (selectedType == ObjectType.TARGET) {
				// Target color swatch click
				for (int i = 0; i < targetColorRects.size; i++) {
					if (targetColorRects.get(i).contains(ux, uy)) {
						selectedTargetColor = Button.ButtonColor.values()[i];
						return;
					}
				}
			}
			// Tentacle root click selection (outside UI controls)
			if (!deleteMode && !linkingMode && selectedType == ObjectType.TENTACLE) {
				// Convert click to world space to test against roots
				com.badlogic.gdx.math.Vector3 worldVec = new com.badlogic.gdx.math.Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
				camera.unproject(worldVec);
				float wx = worldVec.x; float wy = worldVec.y;
				selectedTentacleRootIndex = -1;
				for (int i = 0; i < state.tentacles.size; i++) {
					LevelIO.LevelState.TentacleData td = state.tentacles.get(i);
					Rectangle r = new Rectangle(td.x, td.y, 32f, 32f);
					if (r.contains(wx, wy)) {
						selectedTentacleRootIndex = i;
						toastText = "Tentacle root selected";
						toastTimer = 1.0f;
						break;
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
					// Find a Button, Lever, or Target under click in runtime instances
					Interactable hit = null;
					for (int i = interactableInstances.size - 1; i >= 0; --i) {
						Interactable it = interactableInstances.get(i);
						if ((it instanceof Button || it instanceof Lever || it instanceof Target) && it.getBounds().contains(wx, wy)) {
							hit = it;
							break;
						}
					}
					if (hit == null) {
						toastText = "Pick a Button/Lever/Target first";
						toastTimer = 1.2f;
						return;
					}
					if (hit instanceof Button) {
						linkSourceType = ObjectType.BUTTON;
					} else if (hit instanceof Lever) {
						linkSourceType = ObjectType.LEVER;
					} else {
						linkSourceType = ObjectType.TARGET;
					}
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
					toastText = "Now click doors/lasers to add/remove";
					toastTimer = 1.6f;
					return;
				} else if (linkStage == LinkStage.PICK_DOORS) {
					String did = findDoorIdAt(wx, wy);
					String targetType = "door";
					if (did == null) {
						// try laser
						did = findLaserIdAt(wx, wy);
						targetType = "laser";
					}
					if (did == null) {
						// try mirror
						did = findMirrorIdAt(wx, wy);
						targetType = "mirror";
					}
					if (did == null) {
						toastText = "No door/laser/mirror at cursor";
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

		// RIGHT CLICK: finish linking (if in linking mode), or select box/orb for
		// editing
		if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
			if (linkingMode) {
				linkingMode = false;
				linkStage = LinkStage.PICK_SOURCE;
				linkSourceType = ObjectType.NONE;
				linkSelectedDoorIds.clear();
				toastText = "Linking finished";
				toastTimer = 1.2f;
				return;
			} else {
				// Select box/orb for editing respawn area
				Vector2 worldClick = screenCellBottomLeftToWorldGrid();
				Interactable clicked = null;
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					Interactable it = interactableInstances.get(i);
					if ((it instanceof Box || it instanceof Orb)
							&& it.getBounds().contains(worldClick.x, worldClick.y)) {
						clicked = it;
						break;
					}
				}
				if (clicked != null) {
					selectedObject = clicked;
					toastText = "Selected " + (clicked instanceof Box ? "Box" : "Orb") + " ([ ] width, - = height)";
					toastTimer = 1.5f;
				} else if (selectedObject != null) {
					selectedObject = null;
					toastText = "Deselected";
					toastTimer = 1.0f;
				}
			}
		} // end right-click block

		// save (Ctrl+S to avoid conflict with S key used for camera panning)
		if ((Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT))
				&& Gdx.input.isKeyJustPressed(Input.Keys.S)) {
			LevelIO.save(currentLevelPath, state);
		}
		// Toggle Delete Mode - E
		if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
			deleteMode = !deleteMode;
			com.jjmc.chromashift.environment.interactable.Box.EDITOR_DELETE_MODE = deleteMode;
			com.jjmc.chromashift.environment.interactable.Orb.EDITOR_DELETE_MODE = deleteMode;
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

		// Toggle respawn area debug visualization (F3)
		if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
			debugRespawnAreas = !debugRespawnAreas;
			toastText = debugRespawnAreas ? "Respawn areas: ON" : "Respawn areas: OFF";
			toastTimer = 1.2f;
		}

		// exit: return to TestMenuScreen on ESC
		if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
			((com.badlogic.gdx.Game) Gdx.app.getApplicationListener())
					.setScreen(new com.jjmc.chromashift.screens.TestMenuScreen());
			return;
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
		// Diamonds (collectibles)
		if (state.diamonds != null) {
			for (int i = state.diamonds.size - 1; i >= 0; --i) {
				LevelIO.LevelState.DiamondData dd = state.diamonds.get(i);
				Rectangle db = new Rectangle(dd.x, dd.y, 32f, 32f);
				if (db.overlaps(area)) {
					state.diamonds.removeIndex(i);
					// remove live diamond instance
					for (int k = collectibleInstances.size - 1; k >= 0; --k) {
						com.jjmc.chromashift.environment.collectible.Collectible c = collectibleInstances.get(k);
						Rectangle cb = new Rectangle(c.getX(), c.getY(), c.getWidth(), c.getHeight());
						if (cb.overlaps(db)) {
							collectibleInstances.removeIndex(k);
							break;
						}
					}
					deleted = true;
				}
			}
		}
		// Keys (collectibles)
		if (state.keys != null) {
			for (int i = state.keys.size - 1; i >= 0; --i) {
				LevelIO.LevelState.KeyData kd = state.keys.get(i);
				Rectangle kb = new Rectangle(kd.x, kd.y, 32f, 32f);
				if (kb.overlaps(area)) {
					state.keys.removeIndex(i);
					for (int k = collectibleInstances.size - 1; k >= 0; --k) {
						com.jjmc.chromashift.environment.collectible.Collectible c = collectibleInstances.get(k);
						Rectangle cb = new Rectangle(c.getX(), c.getY(), c.getWidth(), c.getHeight());
						if (cb.overlaps(kb)) {
							collectibleInstances.removeIndex(k);
							break;
						}
					}
					deleted = true;
				}
			}
		}
		// Tentacle roots
		if (state.tentacles != null) {
			for (int i = state.tentacles.size - 1; i >= 0; --i) {
				LevelIO.LevelState.TentacleData td = state.tentacles.get(i);
				Rectangle rt = new Rectangle(td.x, td.y, 32f, 32f);
				if (rt.overlaps(area)) {
					state.tentacles.removeIndex(i);
					deleted = true;
				}
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
				} else if (it instanceof Target) {
					for (int j = 0; j < state.interactables.size; ++j) {
						LevelIO.LevelState.InteractableData idd = state.interactables.get(j);
						if (idd != null && "target".equalsIgnoreCase(String.valueOf(idd.type))
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
				} else if (it instanceof Laser
						|| it instanceof com.jjmc.chromashift.environment.interactable.LaserRay) {
					if (state.lasers != null) {
						for (int j = 0; j < state.lasers.size; ++j) {
							LevelIO.LevelState.LaserData ld = state.lasers.get(j);
							if (Math.abs(ld.x - ob.x) < 5f && Math.abs(ld.y - ob.y) < 5f) {
								// remove any editor laser record with matching id
								if (ld.id != null) {
									for (int k = laserRecords.size - 1; k >= 0; --k) {
										if (laserRecords.get(k).id != null && laserRecords.get(k).id.equals(ld.id)) {
											laserRecords.removeIndex(k);
											break;
										}
									}
								}
								state.lasers.removeIndex(j);
								break;
							}
						}
					}
				} else if (it instanceof Mirror) {
					if (state.mirrors != null) {
						for (int j = 0; j < state.mirrors.size; ++j) {
							LevelIO.LevelState.MirrorData md = state.mirrors.get(j);
							if (Math.abs(md.x - ob.x) < 5f && Math.abs(md.y - ob.y) < 5f) {
								// remove mirror record if id matches
								if (md.id != null) {
									for (int k = mirrorRecords.size - 1; k >= 0; --k) {
										if (mirrorRecords.get(k).id != null && mirrorRecords.get(k).id.equals(md.id)) {
											mirrorRecords.removeIndex(k);
											break;
										}
									}
								}
								state.mirrors.removeIndex(j);
								break;
							}
						}
					}
				} else if (it instanceof Glass) {
					if (state.glasses != null) {
						for (int j = 0; j < state.glasses.size; ++j) {
							LevelIO.LevelState.GlassData gd = state.glasses.get(j);
							if (Math.abs(gd.x - ob.x) < 5f && Math.abs(gd.y - ob.y) < 5f) {
								state.glasses.removeIndex(j);
								break;
							}
						}
					}
				} else if (it instanceof com.jjmc.chromashift.environment.interactable.LockedDoor) {
					if (state.lockedDoors != null) {
						for (int j = 0; j < state.lockedDoors.size; ++j) {
							LevelIO.LevelState.LockedDoorData ld = state.lockedDoors.get(j);
							if (Math.abs(ld.x - ob.x) < 5f && Math.abs(ld.y - ob.y) < 5f) {
								state.lockedDoors.removeIndex(j);
								break;
							}
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
				if (it instanceof Solid)
					solids.removeValue((Solid) it, true);
				interactableInstances.removeIndex(i);
				deleted = true;
			}
		}
		// Ensure Launchpads can be deleted by clicking any part of their full footprint
		for (int j = state.launchpads.size - 1; j >= 0; --j) {
			LevelIO.LevelState.LaunchpadData lpd = state.launchpads.get(j);
			if (lpd == null || lpd.direction == null)
				continue;
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
							if (ib != null && !fb.overlaps(ib))
								continue;
						}
						if (it instanceof Solid)
							solids.removeValue((Solid) it, true);
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
		// compute preview area for this placement and cancel if overlapping existing
		// objects
		float areaW = 32f * previewCols;
		float areaH = 32f * previewRows;
		switch (selectedType) {
			case BUTTON: {
				areaW = 64f;
				areaH = 32f;
				break;
			}
			case LEVER: {
				areaW = 16f;
				areaH = 36f;
				break;
			}
			case BOX: {
				areaW = 32f;
				areaH = 32f;
				break;
			}
			case ORB: {
				areaW = 24f;
				areaH = 24f;
				break;
			}
			case BOSS: {
				areaW = 96f;
				areaH = 96f;
				break;
			}
			case SPAWN: {
				areaW = 16f;
				areaH = 32f;
				break;
			}
			case KEY: {
				areaW = 32f;
				areaH = 32f;
				break;
			}
			case LOCKED_DOOR: {
				areaW = 48f;
				areaH = 96f;
				break;
			}
			case LAUNCHPAD: {
				if (selectedLaunchpadDirection == com.jjmc.chromashift.environment.Launchpad.LaunchDirection.UP) {
					areaW = 64f;
					areaH = 32f;
				} else {
					areaW = 32f;
					areaH = 64f;
				}
				break;
			}
			case LASER: {
				areaW = 32f;
				areaH = 32f;
				break;
			}
			case MIRROR:
			case GLASS: {
				areaW = previewCols * 32f;
				areaH = previewRows * 32f;
				break;
			}
			case NONE:
			default: {
				break;
			}
		}
		Rectangle intended = new Rectangle(gx, gy, areaW, areaH);
		if (selectedType == ObjectType.GLASS) {
			// Glass uses a centered 16x16 footprint
			intended.x += 8f;
			intended.y += 8f;
		}
		boolean areaFree = isAreaFree(intended);
		if (!areaFree) {
			// Log overlaps for debugging
			for (Wall w : walls) {
				if (w.getBounds().overlaps(intended))
					Gdx.app.log("LevelMaker",
							"Overlaps wall at " + (int) w.getBounds().x + "," + (int) w.getBounds().y);
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
			case WALL: {
				LevelIO.LevelState.WallData wd = new LevelIO.LevelState.WallData();
				wd.x = gx;
				wd.y = gy;
				wd.width = previewCols;
				wd.height = previewRows;
				state.walls.add(wd);
				walls.add(new Wall(wd.x, wd.y, previewCols, previewRows));
				placed = true;
				break;
			}
			case DOOR: {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "door";
				idd.x = gx;
				idd.y = gy;
				idd.cols = previewCols;
				idd.rows = previewRows;
				idd.openDirection = doorDirections[selectedDoorDirIndex];
				idd.id = "door_" + (state.interactables.size + 1);
				idd.openSpeed = selectedDoorOpenSpeed;
				idd.closeSpeed = selectedDoorCloseSpeed;
				state.interactables.add(idd);
				Door.OpenDirection dir = Door.OpenDirection.valueOf(idd.openDirection);
				int cols = Math.max(1, idd.cols);
				int rows = Math.max(1, idd.rows);
				Wall base = new Wall(gx, gy - 32, Math.max(1, cols), 1);
				Door d = new Door(gx, base, cols, rows, dir, idd.openSpeed, idd.closeSpeed);
				interactableInstances.add(d);
				solids.add(d);
				doorRecords.add(new DoorRecord(idd.id, gx, gy, idd.cols, idd.rows));
				placements.add(new Placement(ObjectType.DOOR, gx, gy, idd.cols, idd.rows, idd.id));
				placed = true;
				break;
			}
			case BUTTON: {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "button";
				idd.x = gx;
				idd.y = gy;
				idd.color = selectedButtonColor.name();
				// assign a unique id for button
				int btnCount = 0;
				for (LevelIO.LevelState.InteractableData e : state.interactables)
					if (e != null && "button".equalsIgnoreCase(String.valueOf(e.type)))
						btnCount++;
				idd.id = "button_" + (btnCount + 1);
				state.interactables.add(idd);
				Array<Solid> allSolids = new Array<>();
				for (Wall w : walls)
					allSolids.add(w);
				for (Interactable inter : interactableInstances)
					if (inter instanceof Solid)
						allSolids.add((Solid) inter);
				Solid baseSolid = findBaseSolidFor(gx, gy, allSolids);
				if (baseSolid == null)
					baseSolid = new Wall(gx, gy - 32, 4, 1);
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs((int) dr.x - gx) <= 32 && Math.abs((int) dr.y - gy) <= 32) {
						foundDoor = dr.id;
						break;
					}
				}
				idd.targetId = foundDoor;
				Button b = new Button(gx, baseSolid, foundDoor != null ? findDoorById(foundDoor) : null,
						selectedButtonColor);
				interactableInstances.add(b);
				solids.add(b);
				placements.add(new Placement(ObjectType.BUTTON, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case LEVER: {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "lever";
				idd.x = gx;
				idd.y = gy;
				idd.orientation = selectedLeverHorizontal ? "HORIZONTAL" : "VERTICAL";
				// assign a unique id for lever
				int levCount = 0;
				for (LevelIO.LevelState.InteractableData e : state.interactables)
					if (e != null && "lever".equalsIgnoreCase(String.valueOf(e.type)))
						levCount++;
				idd.id = "lever_" + (levCount + 1);
				state.interactables.add(idd);
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs((int) dr.x - gx) <= 32 && Math.abs((int) dr.y - gy) <= 32) {
						foundDoor = dr.id;
						break;
					}
				}
				idd.targetId = foundDoor;
				Lever l = new Lever(gx, gy, 64, 64, selectedLeverHorizontal,
						foundDoor != null ? findDoorById(foundDoor) : null);
				if (foundDoor != null) {
					String tid = foundDoor;
					l.setOnToggle(() -> {
						Door dd = findDoorById(tid);
						if (dd != null) {
							dd.setOpen(!dd.isOpen());
							dd.interact();
						}
					});
				}
				interactableInstances.add(l);
				placements.add(new Placement(ObjectType.LEVER, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case BOX: {
				LevelIO.LevelState.BoxData bd = new LevelIO.LevelState.BoxData();
				// Center 24x24 box in 32x32 grid cell
				bd.x = gx + 4f;
				bd.y = gy + 4f;
				bd.color = selectedBoxColor;
				bd.areaW = selectedBoxAreaW;
				bd.areaH = selectedBoxAreaH;
				state.boxes.add(bd);
				Box box = new Box(bd.x, bd.y, new Array<>());
				// apply color
				com.badlogic.gdx.graphics.Color base = com.badlogic.gdx.graphics.Color.CYAN;
				switch (selectedBoxColor.toUpperCase()) {
					case "RED":
						base = com.badlogic.gdx.graphics.Color.RED;
						break;
					case "BLUE":
						base = com.badlogic.gdx.graphics.Color.BLUE;
						break;
					case "GREEN":
						base = com.badlogic.gdx.graphics.Color.GREEN;
						break;
					case "YELLOW":
						base = com.badlogic.gdx.graphics.Color.YELLOW;
						break;
					case "PURPLE":
						base = com.badlogic.gdx.graphics.Color.PURPLE;
						break;
					default:
						base = com.badlogic.gdx.graphics.Color.CYAN;
						break;
				}
				try {
					box.setColor(base);
				} catch (Throwable ignored) {
				}
				try {
					box.setRespawnArea(new Rectangle(bd.x - bd.areaW / 2f, bd.y - bd.areaH / 2f, bd.areaW, bd.areaH));
				} catch (Throwable ignored) {
				}
				interactableInstances.add(box);
				placements.add(new Placement(ObjectType.BOX, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case ORB: {
				LevelIO.LevelState.OrbData od = new LevelIO.LevelState.OrbData();
				// Center 24x24 orb in grid cell
				od.x = gx + 4f;
				od.y = gy + 4f;
				od.areaW = selectedOrbAreaW;
				od.areaH = selectedOrbAreaH;
				state.orbs.add(od);
				Orb orb = new Orb(od.x, od.y, new Array<>());
				try {
					orb.setRespawnArea(new Rectangle(od.x - od.areaW / 2f, od.y - od.areaH / 2f, od.areaW, od.areaH));
				} catch (Throwable ignored) {
				}
				interactableInstances.add(orb);
				placements.add(new Placement(ObjectType.ORB, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case BOSS: {
				LevelIO.LevelState.BossData bd = new LevelIO.LevelState.BossData();
				bd.x = gx;
				bd.y = gy;
				state.boss = bd;
				if (bossInstance == null)
					bossInstance = new BossInstance();
				bossInstance.setPosition(bd.x, bd.y);
				placements.add(new Placement(ObjectType.BOSS, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case SPAWN: {
				LevelIO.LevelState.SpawnData sd = new LevelIO.LevelState.SpawnData();
				sd.x = gx;
				sd.y = gy;
				state.spawn = sd;
				if (spawnPreview == null)
					spawnPreview = new Spawn(gx, gy);
				else
					spawnPreview.setPosition(gx, gy);
				placements.add(new Placement(ObjectType.SPAWN, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case LAUNCHPAD: {
				LevelIO.LevelState.LaunchpadData lpd = new LevelIO.LevelState.LaunchpadData();
				lpd.x = gx;
				lpd.y = gy;
				lpd.direction = selectedLaunchpadDirection.name();
				lpd.speed = selectedLaunchpadSpeed;
				state.launchpads.add(lpd);
				com.jjmc.chromashift.environment.Launchpad launchpad = new com.jjmc.chromashift.environment.Launchpad(
						gx, gy, selectedLaunchpadDirection, selectedLaunchpadSpeed);
				interactableInstances.add(launchpad);
				placements.add(new Placement(ObjectType.LAUNCHPAD, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case LASER: {
				LevelIO.LevelState.LaserData ld = new LevelIO.LevelState.LaserData();
				ld.x = gx;
				ld.y = gy;
				ld.rotation = selectedLaserRotation;
				ld.maxBounces = 8;
				ld.rotating = selectedLaserIsRotating;
				// assign unique id for laser
				int laserCount = (state.lasers != null) ? state.lasers.size : 0;
				ld.id = "laser_" + (laserCount + 1);
				state.lasers.add(ld);
				if (selectedLaserIsRotating) {
					// Preview as interactable LaserRay emitter
					com.jjmc.chromashift.environment.interactable.LaserRay lray = new com.jjmc.chromashift.environment.interactable.LaserRay(
							gx, gy, true);
					lray.setRotation(selectedLaserRotation);
					interactableInstances.add(lray);
					laserRecords.add(new LaserRecord(ld.id, ld.x, ld.y));
				} else {
					Laser laser = new Laser(gx, gy);
					laser.setRotation(selectedLaserRotation);
					interactableInstances.add(laser);
					laserRecords.add(new LaserRecord(ld.id, ld.x, ld.y));
				}
				placements.add(new Placement(ObjectType.LASER, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case MIRROR: {
				LevelIO.LevelState.MirrorData md = new LevelIO.LevelState.MirrorData();
				md.x = gx;
				md.y = gy;
				md.width = 32f;
				md.height = 32f;
				md.angleDeg = selectedMirrorAngleDeg;
				// assign unique id for mirror
				int mirrorCount = (state.mirrors != null) ? state.mirrors.size : 0;
				md.id = "mirror_" + (mirrorCount + 1);
				state.mirrors.add(md);
				Mirror mirror = new Mirror(gx, gy, md.width, md.height);
				try {
					mirror.setAngleDegrees(md.angleDeg);
				} catch (Exception ignored) {
				}
				interactableInstances.add(mirror);
				mirrorRecords.add(new MirrorRecord(md.id, md.x, md.y, md.width, md.height));
				placements.add(new Placement(ObjectType.MIRROR, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case KEY: {
				LevelIO.LevelState.KeyData kd = new LevelIO.LevelState.KeyData();
				kd.x = gx;
				kd.y = gy;
				state.keys.add(kd);
				// Instantiate Key now for immediate preview
				try { collectibleInstances.add(new com.jjmc.chromashift.environment.collectible.Key(gx, gy)); } catch (Throwable ignored) {}
				placements.add(new Placement(ObjectType.KEY, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case LOCKED_DOOR: {
				LevelIO.LevelState.LockedDoorData ld = new LevelIO.LevelState.LockedDoorData();
				ld.x = gx;
				ld.y = gy;
				state.lockedDoors.add(ld);
				// Instantiate LockedDoor now for immediate preview
				try { 
					com.jjmc.chromashift.environment.interactable.LockedDoor door = new com.jjmc.chromashift.environment.interactable.LockedDoor(gx, gy);
					interactableInstances.add(door);
					solids.add(door);
				} catch (Throwable ignored) {}
				placements.add(new Placement(ObjectType.LOCKED_DOOR, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
				
			case GLASS: {
				LevelIO.LevelState.GlassData gd = new LevelIO.LevelState.GlassData();
				gd.x = gx + 8f; // center 16x16 in 32x32 cell
				gd.y = gy + 8f;
				gd.width = 16f;
				gd.height = 16f;
				gd.rainbow = false; // user chooses static color
				gd.speed = 1.5f;
				gd.color = selectedGlassColor;
				state.glasses.add(gd);
				com.badlogic.gdx.graphics.Color base;
				switch (selectedGlassColor.toUpperCase()) {
					case "RED":
						base = com.badlogic.gdx.graphics.Color.RED;
						break;
					case "BLUE":
						base = com.badlogic.gdx.graphics.Color.BLUE;
						break;
					case "GREEN":
						base = com.badlogic.gdx.graphics.Color.GREEN;
						break;
					case "YELLOW":
						base = com.badlogic.gdx.graphics.Color.YELLOW;
						break;
					case "PURPLE":
						base = com.badlogic.gdx.graphics.Color.PURPLE;
						break;
					default:
						base = com.badlogic.gdx.graphics.Color.CYAN;
						break;
				}
				Glass glass = new Glass(gd.x, gd.y, gd.width, gd.height, base, true, 1f, gd.rainbow);
				interactableInstances.add(glass);
				placements.add(new Placement(ObjectType.GLASS, (int) gd.x, (int) gd.y, 1, 1, null));
				placed = true;
				break;
			}
			case DIAMOND: {
				LevelIO.LevelState.DiamondData dd = new LevelIO.LevelState.DiamondData();
				dd.x = gx;
				dd.y = gy;
				state.diamonds.add(dd);
				// Instantiate Diamond now for immediate preview & deletion consistency
				try { collectibleInstances.add(new com.jjmc.chromashift.environment.collectible.Diamond(gx, gy)); } catch (Throwable ignored) {}
				placements.add(new Placement(ObjectType.DIAMOND, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case SHOP: {
				LevelIO.LevelState.ShopData sd = new LevelIO.LevelState.ShopData();
				sd.x = gx;
				sd.y = gy;
				state.shops.add(sd);
				// Note: Shop will be instantiated by the level loader with proper context
				// For preview purposes, we'll skip adding it to interactableInstances
				placements.add(new Placement(ObjectType.SHOP, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case TENTACLE: {
				LevelIO.LevelState.TentacleData td = new LevelIO.LevelState.TentacleData();
				td.x = gx;
				td.y = gy;
				td.segments = selectedTentacleSegments;
				state.tentacles.add(td);
				placements.add(new Placement(ObjectType.TENTACLE, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case TARGET: {
				LevelIO.LevelState.InteractableData idd = new LevelIO.LevelState.InteractableData();
				idd.type = "target";
				idd.x = gx;
				idd.y = gy;
				idd.color = selectedTargetColor.name();
				// assign a unique id for target (similar to Button/Lever pattern)
				int targetCount = 0;
				for (LevelIO.LevelState.InteractableData e : state.interactables)
					if (e != null && "target".equalsIgnoreCase(String.valueOf(e.type)))
						targetCount++;
				idd.id = "target_" + (targetCount + 1);
				// Optional: Auto-link to nearby door (same as Button does) or leave null
				String foundDoor = null;
				for (DoorRecord dr : doorRecords) {
					if (Math.abs((int) dr.x - gx) <= 32 && Math.abs((int) dr.y - gy) <= 32) {
						foundDoor = dr.id;
						break;
					}
				}
				idd.targetId = foundDoor;
				state.interactables.add(idd);
				// Create runtime Target instance for immediate preview
				Target tObj = new Target(gx, gy, selectedTargetColor);
				if (foundDoor != null) {
					Door dd = findDoorById(foundDoor);
					if (dd != null) tObj.addLinkedDoor(dd);
				}
				interactableInstances.add(tObj);
				solids.add(tObj);
				placements.add(new Placement(ObjectType.TARGET, gx, gy, 1, 1, null));
				placed = true;
				break;
			}
			case NONE:
			default: {
				break;
			}
		}
		if (placed) {
			refreshPreviewFromState();
			placementFlashes.add(new Flash(gx, gy, areaW, areaH, 0.6f));
			toastText = "Placed " + selectedType + " at (" + gx + "," + gy + ")";
			toastTimer = 1.4f;
			try {
				LevelIO.save(currentLevelPath, state);
			} catch (Exception ignored) {
			}
			Gdx.app.log("LevelMaker", "Placed " + selectedType + " at (" + gx + "," + gy + ")");
		}
	}

	/**
	 * Update the respawn area dimensions in the level state for a given box/orb.
	 */
	private void updateRespawnAreaInState(Interactable obj, float newW, float newH) {
		if (state == null)
			return;
		Rectangle bounds = obj.getBounds();
		float cx = bounds.x + bounds.width / 2f;
		float cy = bounds.y + bounds.height / 2f;
		if (obj instanceof Box && state.boxes != null) {
			for (LevelIO.LevelState.BoxData bd : state.boxes) {
				if (Math.abs(bd.x - bounds.x) < 5f && Math.abs(bd.y - bounds.y) < 5f) {
					bd.areaW = newW;
					bd.areaH = newH;
					break;
				}
			}
		} else if (obj instanceof Orb && state.orbs != null) {
			for (LevelIO.LevelState.OrbData od : state.orbs) {
				if (Math.abs(od.x - bounds.x) < 5f && Math.abs(od.y - bounds.y) < 5f) {
					od.areaW = newW;
					od.areaH = newH;
					break;
				}
			}
		}
		try {
			LevelIO.save(currentLevelPath, state);
		} catch (Exception ignored) {
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
			this.collectibleInstances = r.collectibles;
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
			// Rebuild laserRecords for linking
			this.laserRecords.clear();
			// Rebuild mirrorRecords for linking
			this.mirrorRecords.clear();
			if (state != null && state.interactables != null) {
				for (LevelIO.LevelState.InteractableData idd : state.interactables) {
					if (idd != null && "door".equalsIgnoreCase(String.valueOf(idd.type))) {
						this.doorRecords.add(new DoorRecord(idd.id, idd.x, idd.y, idd.cols, idd.rows));
					}
				}
			}
			if (state != null && state.lasers != null) {
				for (LevelIO.LevelState.LaserData ld : state.lasers) {
					if (ld != null && ld.id != null) {
						this.laserRecords.add(new LaserRecord(ld.id, ld.x, ld.y));
					}
				}
			}
			if (state != null && state.mirrors != null) {
				for (LevelIO.LevelState.MirrorData md : state.mirrors) {
					if (md != null && md.id != null) {
						this.mirrorRecords.add(new MirrorRecord(md.id, md.x, md.y, md.width, md.height));
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
		collectibleInstances.clear();
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
		if (state.lasers == null)
			state.lasers = new Array<>();
		if (state.mirrors == null)
			state.mirrors = new Array<>();
		if (state.glasses == null)
			state.glasses = new Array<>();
		if (state.interactables == null)
			state.interactables = new Array<>();
		if (state.spawn == null)
			state.spawn = new LevelIO.LevelState.SpawnData();
		if (state.diamonds == null)
			state.diamonds = new Array<>();
		if (state.tentacles == null)
			state.tentacles = new Array<>();
		if (state.keys == null)
			state.keys = new Array<>();
		if (state.lockedDoors == null)
			state.lockedDoors = new Array<>();

		// Adopt loaded runtime objects for preview rendering
		this.walls = loaded.walls;
		this.solids = loaded.solids;
		this.interactableInstances = loaded.interactables;
		this.collectibleInstances = loaded.collectibles;
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

		// Compute a shared optionY baseline from the DOOR button (fallback to by + bh +
		// 8f)
		Rectangle doorBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.DOOR) {
				doorBtn = ub.rect;
				break;
			}
		float optionY = (doorBtn != null) ? (doorBtn.y + doorBtn.height + 8f) : (by + bh + 8f);

		// door dir buttons (position above the DOOR toolbar button)
		doorDirButtons.clear();
		float optionX = (doorBtn != null) ? (doorBtn.x + (doorBtn.width - (4 * bw + 3 * gap)) / 2f)
				: (uiCamera.viewportWidth - (4 * bw + 3 * gap)) / 2f;
		for (int i = 0; i < doorDirections.length; i++) {
			doorDirButtons.add(new Rectangle(optionX + i * (bw + gap), optionY, bw, bh));
		}

		// door speed controls (placed to the right of the door options)
		float speedW = 28f, speedH = 20f, speedGap = 6f;
		float speedStartX = optionX + (4 * (bw + gap));
		// Move the controls higher so they don't overlap the main toolbar
		doorOpenMinusRect = new Rectangle(speedStartX, optionY + 46f, speedW, speedH);
		doorOpenPlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + 46f, speedW, speedH);
		doorCloseMinusRect = new Rectangle(speedStartX, optionY + 46f - (speedH + 6f), speedW, speedH);
		doorClosePlusRect = new Rectangle(speedStartX + speedW + speedGap, optionY + 46f - (speedH + 6f), speedW,
				speedH);

		// launchpad direction buttons (rebuild above LAUNCHPAD toolbar button)
		launchpadDirRects.clear();
		float lpDirW = 50f, lpDirH = 24f, lpDirGap = 4f;
		Rectangle lpBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.LAUNCHPAD) {
				lpBtn = ub.rect;
				break;
			}
		float lpDirsWidth = 3 * lpDirW + 2 * lpDirGap;
		float lpDirX = (lpBtn != null) ? (lpBtn.x + (lpBtn.width - lpDirsWidth) / 2f)
				: (uiCamera.viewportWidth - lpDirsWidth) / 2f;
		for (int i = 0; i < 3; i++) {
			launchpadDirRects.add(new Rectangle(lpDirX + i * (lpDirW + lpDirGap),
					(lpBtn != null) ? (lpBtn.y + lpBtn.height + 8f) : optionY, lpDirW, lpDirH));
		}

		// laser type buttons (rebuild on resize) - above LASER toolbar button
		laserTypeRects.clear();
		float laserTypeW = 70f, laserTypeH = 24f, laserTypeGap = 4f;
		float laserTypesWidth = 2 * laserTypeW + laserTypeGap;
		Rectangle laserBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.LASER) {
				laserBtn = ub.rect;
				break;
			}
		float laserTypeX = (laserBtn != null) ? (laserBtn.x + (laserBtn.width - laserTypesWidth) / 2f)
				: (uiCamera.viewportWidth - laserTypesWidth) / 2f;
		laserTypeRects.add(new Rectangle(laserTypeX, (laserBtn != null) ? (laserBtn.y + laserBtn.height + 8f) : optionY,
				laserTypeW, laserTypeH));
		laserTypeRects.add(new Rectangle(laserTypeX + laserTypeW + laserTypeGap,
				(laserBtn != null) ? (laserBtn.y + laserBtn.height + 8f) : optionY, laserTypeW, laserTypeH));

		// laser direction buttons (rebuild on resize) above type buttons
		laserDirRects.clear();
		float ldirW = 36f, ldirH = 24f, ldirGap = 6f;
		float ldirsWidth = 4 * ldirW + 3 * ldirGap;
		float ldirX = (laserBtn != null) ? (laserBtn.x + (laserBtn.width - ldirsWidth) / 2f)
				: (uiCamera.viewportWidth - ldirsWidth) / 2f;
		float ldirY = (laserBtn != null) ? (laserBtn.y + laserBtn.height + 8f + laserTypeH + 8f)
				: optionY + laserTypeH + 8f;
		for (int i = 0; i < 4; i++) {
			laserDirRects.add(new Rectangle(ldirX + i * (ldirW + ldirGap), ldirY, ldirW, ldirH));
		}

		// sliders positions (placed above the door options if available)
		float sliderW = 220f, sliderH = 14f;
		float sliderX = (doorBtn != null) ? (doorBtn.x + (doorBtn.width - sliderW) / 2f)
				: (uiCamera.viewportWidth - sliderW) / 2f;
		float sliderY = (doorBtn != null) ? (doorBtn.y + doorBtn.height + 8f + 40f) : (optionY + 70f);
		openSpeedSliderRect = new Rectangle(sliderX, sliderY, sliderW, sliderH);
		closeSpeedSliderRect = new Rectangle(sliderX, sliderY - (sliderH + 10f), sliderW, sliderH);

		// Button color swatches (rebuild on resize) - placed above BUTTON toolbar
		// button
		buttonColorRects.clear();
		float sw = 40f, sh = 24f, sg = 6f;
		Button.ButtonColor[] cols = Button.ButtonColor.values();
		float colorsWidth = cols.length * sw + (cols.length - 1) * sg;
		Rectangle buttonBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.BUTTON) {
				buttonBtn = ub.rect;
				break;
			}
		float bcx = (buttonBtn != null) ? (buttonBtn.x + (buttonBtn.width - colorsWidth) / 2f)
				: (uiCamera.viewportWidth - colorsWidth) / 2f;
		for (int i = 0; i < cols.length; i++) {
			buttonColorRects.add(new Rectangle(bcx + i * (sw + sg),
					(buttonBtn != null) ? (buttonBtn.y + buttonBtn.height + 8f) : optionY, sw, sh));
		}

		// lever orientation - above LEVER toolbar button
		Rectangle leverBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.LEVER) {
				leverBtn = ub.rect;
				break;
			}
		leverOrientRect = new Rectangle(
				(leverBtn != null) ? (leverBtn.x + (leverBtn.width - 80f) / 2f) : ((uiCamera.viewportWidth - 80f) / 2f),
				(leverBtn != null) ? (leverBtn.y + leverBtn.height + 8f) : optionY, 80f, sh);

		// Mirror rotate - above MIRROR toolbar button
		float optW = 110f, optH = 24f;
		Rectangle mirrorBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.MIRROR) {
				mirrorBtn = ub.rect;
				break;
			}
		float optX = (mirrorBtn != null) ? (mirrorBtn.x + (mirrorBtn.width - optW) / 2f)
				: (uiCamera.viewportWidth - optW) / 2f;
		mirrorRotateRect = new Rectangle(optX, (mirrorBtn != null) ? (mirrorBtn.y + mirrorBtn.height + 8f) : optionY,
				optW, optH);

		// Glass color swatches
		glassColorRects.clear();
		String[] gcolors = { "RED", "BLUE", "GREEN", "YELLOW", "PURPLE" };
		float gcw = 40f, gch = 24f, gcgap = 6f;
		float gtotal = gcolors.length * gcw + (gcolors.length - 1) * gcgap;
		Rectangle glassBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.GLASS) {
				glassBtn = ub.rect;
				break;
			}
		float gcx = (glassBtn != null) ? (glassBtn.x + (glassBtn.width - gtotal) / 2f)
				: (uiCamera.viewportWidth - gtotal) / 2f;
		for (int i = 0; i < gcolors.length; i++) {
			glassColorRects.add(new Rectangle(gcx + i * (gcw + gcgap),
					(glassBtn != null) ? (glassBtn.y + glassBtn.height + 8f) : optionY, gcw, gch));
		}

		// Box color swatches
		boxColorRects.clear();
		float btotal = gcolors.length * gcw + (gcolors.length - 1) * gcgap;
		Rectangle boxBtn = null;
		for (UIButton ub : uiButtons)
			if (ub.type == ObjectType.BOX) {
				boxBtn = ub.rect;
				break;
			}
		float boxCx = (boxBtn != null) ? (boxBtn.x + (boxBtn.width - btotal) / 2f)
				: (uiCamera.viewportWidth - btotal) / 2f;
		for (int i = 0; i < gcolors.length; i++) {
			boxColorRects.add(new Rectangle(boxCx + i * (gcw + gcgap),
					(boxBtn != null) ? (boxBtn.y + boxBtn.height + 8f) : optionY, gcw, gch));
		}

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

	/**
	 * Find a laser record id at the provided world-grid cell (approx). Returns null
	 * if none found.
	 */
	private String findLaserIdAt(int wx, int wy) {
		for (LaserRecord lr : laserRecords) {
			if (Math.abs((int) lr.x - wx) <= 32 && Math.abs((int) lr.y - wy) <= 32)
				return lr.id;
		}
		return null;
	}

	/** Find a mirror record id near the provided world-grid cell. */
	private String findMirrorIdAt(int wx, int wy) {
		for (MirrorRecord mr : mirrorRecords) {
			if (Math.abs((int) mr.x - wx) <= 32 && Math.abs((int) mr.y - wy) <= 32)
				return mr.id;
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
			case LASER:
				for (int i = 0; i < state.lasers.size; ++i) {
					LevelIO.LevelState.LaserData ld = state.lasers.get(i);
					if (ld.x == p.x && ld.y == p.y) {
						state.lasers.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i) {
					Interactable it = interactableInstances.get(i);
					if (it instanceof Laser) {
						Rectangle rb = it.getBounds();
						if ((int) rb.x == p.x && (int) rb.y == p.y) {
							interactableInstances.removeIndex(i);
							break;
						}
					}
				}
				break;
			case MIRROR:
				for (int i = 0; i < state.mirrors.size; ++i) {
					LevelIO.LevelState.MirrorData md = state.mirrors.get(i);
					if (md.x == p.x && md.y == p.y) {
						state.mirrors.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i)
					if (interactableInstances.get(i) instanceof Mirror)
						interactableInstances.removeIndex(i);
				break;
			case GLASS:
				for (int i = 0; i < state.glasses.size; ++i) {
					LevelIO.LevelState.GlassData gd = state.glasses.get(i);
					if (gd.x == p.x && gd.y == p.y) {
						state.glasses.removeIndex(i);
						break;
					}
				}
				for (int i = interactableInstances.size - 1; i >= 0; --i)
					if (interactableInstances.get(i) instanceof Glass)
						interactableInstances.removeIndex(i);
				break;
			case DIAMOND:
				if (state.diamonds != null) {
					for (int i = 0; i < state.diamonds.size; ++i) {
						LevelIO.LevelState.DiamondData dd = state.diamonds.get(i);
						if (dd.x == p.x && dd.y == p.y) {
							state.diamonds.removeIndex(i);
							break;
						}
					}
				}
				break;
			case SHOP:
				if (state.shops != null) {
					for (int i = 0; i < state.shops.size; ++i) {
						LevelIO.LevelState.ShopData sd = state.shops.get(i);
						if (sd.x == p.x && sd.y == p.y) {
							state.shops.removeIndex(i);
							break;
						}
					}
				}
				break;
			case TENTACLE:
				if (state.tentacles != null) {
					for (int i = 0; i < state.tentacles.size; ++i) {
						LevelIO.LevelState.TentacleData td = state.tentacles.get(i);
						if (td.x == p.x && td.y == p.y) {
							state.tentacles.removeIndex(i);
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