# Program Implementation

This document highlights important parts of the codebase — core classes, representative methods, and key logic — without full code dumps.

## Core Classes

- **`AbstractMenuScreen`**: Base screen providing common UI scaffolding for menu-like screens (stage lifecycle, input routing, navigation helpers).
- **`MainMenuScreen`**: Primary entry menu; wires navigation to gameplay, credits, settings.
- **`CreditsScreen`**: Renders credits using sprite-based fonts and cycles team portraits with fade transitions. Positioned text and portrait to maintain visual balance.
  - File: [core/src/main/java/com/jjmc/chromashift/screens/ui/CreditsScreen.java](core/src/main/java/com/jjmc/chromashift/screens/ui/CreditsScreen.java)
- **`UIHelper`**: Utilities for building LibGDX UI widgets with consistent styling (buttons, labels, tables).
- **`SpriteFont` / `SpriteFontManager`**: Custom bitmap font and loader for measuring and drawing scaled text (`getWidth()`, `getHeight()`, `draw()`).

## Sample Methods

- **`CreditsScreen.show()`**
  - Initializes `Stage` with `ScreenViewport`, sets `Skin` and root `Table` layout.
  - Loads sprite font via `SpriteFontManager` and prepares `creditLines`.
  - Loads portrait textures with `safeLoad(...)` and sets timers for cycling.

- **`CreditsScreen.render(delta)`**
  - Clears and draws the base scene, handles ESC back navigation.
  - Uses the stage batch to draw two overlays: centered credits text and a fading portrait.

- **`CreditsScreen.drawCenteredCredits(batch)`**
  - Computes a left/middle text area and performs word-wrapping based on measured width (`font.getWidth(text, scale)`).
  - Applies per-line scaling: larger scale for the title (“Credits”), standard scale for other lines.
  - Vertically centers the total block by summing per-line heights (`font.getHeight(scale) + padding`).

- **`CreditsScreen.drawPortraitCycle(batch, delta)`**
  - Manages portrait cycling with `portraitTimer`, `portraitCycleTime`, and `fadeDuration`.
  - Calculates blend alphas for current/next portraits and draws them in the top-right with margin.
  - Resets batch color to avoid side effects on subsequent draws.

## Explanation of Key Logic

- **Word Wrapping & Centering**
  - Text is wrapped by measuring candidate line widths with `SpriteFont` and splitting on spaces to fit the available area.
  - Each line is horizontally centered by subtracting the measured width from the area width.
  - Vertical centering is achieved by computing the total block height and starting draw at half-screen plus half-block height.

- **Per-Line Scaling**
  - The title line (“Credits”) uses a larger `titleScale`; other lines use `fontScale`.
  - Line spacing uses the bitmap font’s measured height per scale plus a small padding for readability.

- **Portrait Fade & Placement**
  - A cycle runs every `portraitCycleTime` seconds with `fadeDuration` dedicated to cross-fading.
  - During fade-out, `alphaCurrent` decreases while `alphaNext` increases, producing a smooth transition.
  - Portraits are drawn at the top-right with proportional sizing (`targetW/H` as fractions of viewport) and a margin.

- **Input & Navigation**
  - ESC triggers `navigateBack()` which sets the screen to `MainMenuScreen`.
  - `Stage` is the input processor; UI elements are constructed via `UIHelper` for consistency.

- **Resource Management**
  - `dispose()` cleans up `Stage`, `Skin`, and textures in `portraits` to prevent memory leaks.
  - `safeLoad()` encapsulates texture loading and tolerates missing files by returning `null`.

## Notes

- Rendering uses the `Stage`'s `SpriteBatch` to ensure draw order and blending are consistent with UI elements.
- The credits layout reserves the right side for portraits, keeping text readable and visually balanced.
