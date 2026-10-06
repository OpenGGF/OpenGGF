package com.openggf.mods.scene;

import java.util.Arrays;

/**
 * A run of a stock act's floor, in level pixels, found by {@link SceneRomArt#levelStages}:
 * somewhere a scene can stand characters on the real level. Columns {@code x .. x + width - 1}
 * each have a floor row ({@link #floorAt}); {@link #floorY} is their median, a good row to line
 * up with a scene's ground. Draw {@link SceneRomArt#levelForeground} for a rectangle around the
 * stage and put each character's feet on the floor under it.
 *
 * <p>Immutable. A scene may build its own (for example a stage re-based on a window of the
 * screen), and two stages with the same columns and rows are equal.
 */
@com.openggf.game.ModApi
public final class SceneLevelStage {
    private final int x;
    private final int floorY;
    private final int[] floor;

    /**
     * @param x      the stage's left edge
     * @param floorY the row to line the stage up by (the median floor row for found stages)
     * @param width  the stage's width, 1 or more
     * @param floor  the floor's top row in each column, {@code floor[i]} for column {@code x + i}
     *               (the row a standing character's feet rest on); {@code width} entries, copied
     * @throws IllegalArgumentException when {@code floor} is null or not {@code width} long
     */
    public SceneLevelStage(int x, int floorY, int width, int[] floor) {
        if (width <= 0 || floor == null || floor.length != width) {
            throw new IllegalArgumentException("A stage needs a floor row for each of its " + width + " columns");
        }
        this.x = x;
        this.floorY = floorY;
        this.floor = floor.clone();
    }

    /** The stage's left edge in level pixels. */
    public int x() {
        return x;
    }

    /** The median floor row across the stage. */
    public int floorY() {
        return floorY;
    }

    /** The stage's width in pixels. */
    public int width() {
        return floor.length;
    }

    /** A copy of the floor rows, {@code floor()[i]} for column {@code x() + i}. */
    public int[] floor() {
        return floor.clone();
    }

    /** The floor row under level column {@code levelX}, using the nearest edge column outside the stage. */
    public int floorAt(int levelX) {
        return floor[Math.max(0, Math.min(floor.length - 1, levelX - x))];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SceneLevelStage o && x == o.x && floorY == o.floorY && Arrays.equals(floor, o.floor);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + floorY) + Arrays.hashCode(floor);
    }

    @Override
    public String toString() {
        return "SceneLevelStage[x " + x + ", width " + floor.length + ", floorY " + floorY + "]";
    }
}
