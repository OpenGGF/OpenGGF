package threeislands.field;

import java.util.ArrayList;
import java.util.List;
import threeislands.core.Zone;

/**
 * A hand-authored outdoor area: rows of terrain characters on a 32-pixel grid, followed by a
 * legend that turns marker characters into landmarks (see {@link Maps}). Markers stand on
 * ordinary ground. Rows and legend lines are instance-owned, as creator mods keep no static state.
 */
public final class TileMap {
    public static final int CELL = 32;

    private final String[] rows;
    private final List<String[]> legend = new ArrayList<>();
    public final int columns;

    public TileMap(String text) {
        String[] parts = text.split("\n---\n", 2);
        rows = parts[0].split("\n");
        int widest = 0;
        for (String row : rows) widest = Math.max(widest, row.length());
        columns = widest;
        if (parts.length > 1) {
            for (String line : parts[1].split("\n")) {
                if (line.isBlank()) continue;
                legend.add(line.strip().split(" ", 4));
            }
        }
    }

    /** The authored map for a zone, or null when the zone uses a room graph instead. */
    public static TileMap of(Zone zone) {
        return switch (zone) {
            case GREEN_HILL -> new TileMap(Maps.GREEN_HILL);
            case STAR_LIGHT -> new TileMap(Maps.STAR_LIGHT);
            default -> null;
        };
    }

    public int rowCount() { return rows.length; }
    public int width() { return columns * CELL; }
    public int height() { return rows.length * CELL; }

    /** The character at a cell; outside the map counts as cliff. */
    public char at(int column, int row) {
        if (row < 0 || row >= rows.length || column < 0 || column >= rows[row].length()) return '#';
        return rows[row].charAt(column);
    }

    /** The character under a pixel. */
    public char cell(double px, double py) {
        return at((int) Math.floor(px / CELL), (int) Math.floor(py / CELL));
    }

    /** The terrain drawn under a cell: a marker takes the ground it interrupts (trail, bridge or grass). */
    public char base(int column, int row) {
        char c = at(column, row);
        if (terrain(c)) return c;
        char left = at(column - 1, row), right = at(column + 1, row), up = at(column, row - 1), down = at(column, row + 1);
        if (left == right && terrain(left) && !solid(left)) return left;
        if (up == down && terrain(up) && !solid(up)) return up;
        for (char near : new char[] {left, right, up, down}) if (near == '=' || near == 'b') return near;
        return '.';
    }

    private static boolean terrain(char c) {
        return "#~TrHOR.,=b".indexOf(c) >= 0;
    }

    /** Terrain nobody walks on. Gates ({@code O}, {@code R}) are decided by the field. */
    public static boolean solid(char c) {
        return c == '#' || c == '~' || c == 'T' || c == 'r' || c == 'H';
    }

    /** Ground a character stands on: markers count as ordinary ground. */
    public static boolean ground(char c) {
        return !solid(c) && c != 'O' && c != 'R';
    }

    /** Cell centres holding {@code marker}, in reading order. */
    public List<int[]> find(char marker) {
        List<int[]> out = new ArrayList<>();
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == marker) out.add(new int[] {column * CELL + CELL / 2, row * CELL + CELL / 2});
            }
        }
        return out;
    }

    /** Legend lines as {marker, kind, id, rest}; missing fields are absent. */
    public List<String[]> legend() {
        return List.copyOf(legend);
    }
}
