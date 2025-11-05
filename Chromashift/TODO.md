# TODO: Fix Player Collision with Solids

## Information Gathered
- Player has collision logic for walls and solids, but movement resolution only handles walls.
- Solids like doors and buttons are only resolved for initial overlaps, not during movement.
- Need to unify collision resolution for all blocking solids (walls, doors, buttons, slopes) using their getCollisionBounds().

## Plan
- Modify Player.update methods to handle all solids uniformly.
- Change PlayerLogic movement methods (handleGroundMovement, handleVerticalMovement, handleDash) to resolve collisions with all blocking solids instead of just walls.
- Keep wall-specific logic (wall sliding, wall jumping) using extracted walls from solids.
- Remove separate resolveSolidCollision call in Player.update for solids, as it will be handled in movement logic.

## Dependent Files to be Edited
- core/src/main/java/com/jjmc/chromashift/player/Player.java
- core/src/main/java/com/jjmc/chromashift/player/PlayerLogic.java

## Followup Steps
- Test collision with doors, buttons, and walls.
- If slopes still don't work, implement special slope collision using getHeightAtX().
