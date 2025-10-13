package com.jjmc.chromashift.player;

// TODO: Add character icon for each color
// TODO: Add character extra skill srite
/**
 * Enum for player color/type and corresponding sprite sheet paths.
 */
public enum PlayerType {
    RED("Red", "player/player-red.png", "player/attack-red.png"),
    BLUE("Blue", "player/player-blue.png", "player/attack-blue.png"),
    GREEN("Green", "player/player-green.png", "player/attack-green.png"),
    YELLOW("Yellow", "player/player-yellow.png", "player/attack-yellow.png"),
    PINK("Pink", "player/player-pink.png", "player/attack-pink.png"),
    PURPLE("Purple", "player/player-purple.png", "player/attack-purple.png"),
    ORANGE("Orange", "player/player-orange.png", "player/attack-orange.png");

    private final String colorName;
    private final String spritePath;
    private final String attackSpritePath;

    PlayerType(String colorName, String spritePath, String attackSpritePath) {
        this.colorName = colorName;
        this.spritePath = spritePath;
        this.attackSpritePath = attackSpritePath;
    }

    public String getColorName() {
        return colorName;
    }

    public String getSpritePath() {
        return spritePath;
    }

    public String getAttackSpritePath() {
        return attackSpritePath;
    }
}
