package com.openggf.mods.scene;

/**
 * A run of a stock act's floor, in level pixels, found by {@link SceneRomArt#levelStages}:
 * somewhere a scene can stand characters on the real level. Columns {@code x .. x + width - 1}
 * each have a floor ({@link #floorAt}); {@code floorY} is their median row, a good row to line
 * up with a scene's ground. Draw {@link SceneRomArt#levelForeground} for a rectangle around the
 * stage and put each character's feet on the floor under it.
 *
 * @param x      the stage's left edge
 * @param floorY the median floor row across the stage
 * @param width  the stage's width
 * @param floor  the floor's top row in each column, {@code floor[i]} for column {@code x + i}
 *               (the row a standing character's feet rest on); copied in and out
 */
@com.openggf.game.ModApi
public record SceneLevelStage(int x, int floorY, int width, int[] floor) {
    public SceneLevelStage {
        if (width <= 0 || floor == null || floor.length != width) {
            throw new IllegalArgumentException("A stage needs a floor row for each of its " + width + " columns");
        }
        floor = floor.clone();
    }

    /** A copy of the floor rows. */
    @Override
    public int[] floor() {
        return floor.clone();
    }

    /** The floor row under level column {@code levelX}, using the nearest edge column outside the stage. */
    public int floorAt(int levelX) {
        return floor[Math.max(0, Math.min(width - 1, levelX - x))];
    }
}
