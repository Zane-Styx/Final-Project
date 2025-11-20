# Required Asset Files

The Diamond and Shop systems have been fully implemented but require the following sprite assets to render correctly:

## Diamond Sprite
**Path:** `assets/environment/diamond.png`
- **Dimensions:** 192×32 pixels (sprite sheet)
- **Format:** 6 frames of 32×32 each (horizontal layout)
- **Animation:** Spinning/rotating diamond effect
- **Usage:** Collectible item that gives the player 1 diamond currency

## Shop Sprite
**Path:** `assets/environment/shop.png`
- **Dimensions:** 64×64 pixels (single frame)
- **Format:** Static image
- **Appearance:** Shop building, merchant stand, or store icon
- **Usage:** Interactive shop where players spend diamonds

## Current Status
Without these assets:
- The systems will log errors but continue to function
- Diamond animations won't display (but collision still works)
- Shop will not display its sprite (but UI dialogs still work)

## Temporary Workaround
For testing purposes, you can create placeholder images:
1. Create simple colored rectangles matching the dimensions above
2. Place them in the correct asset paths
3. Replace with final artwork later

## Integration Notes
- Both systems are fully integrated into LevelMakerScreen (level editor)
- Both systems save/load correctly from level JSON files
- Diamond collection and shop purchases work without sprites
- Only visual rendering requires the actual image files
