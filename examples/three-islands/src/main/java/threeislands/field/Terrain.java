package threeislands.field;

/** Read-only collision for a walkable act: level pixels that hold a character up. */
public interface Terrain {
    /** True when the pixel at level coordinates is solid or a top-solid platform. */
    boolean solid(int x, int y);

    /** Playable area {@code {x, y, width, height}} in level pixels. */
    int[] area();
}
