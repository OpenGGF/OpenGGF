package slaytherobotnik.map;

import java.util.ArrayList;
import java.util.List;

/**
 * The generated map of one act: a 7-lane by 15-floor grid of nodes, then the boss. In the model
 * floors run upward from 0 to the boss, as in Slay the Spire; {@code MapView} draws them left
 * to right through the zone instead.
 */
public final class ActMap {
    public static final int WIDTH = 7;
    public static final int HEIGHT = 15;

    private final MapNode[][] grid;

    ActMap(MapNode[][] grid) {
        this.grid = grid;
    }

    public MapNode node(int x, int y) {
        return grid[y][x];
    }

    /** Nodes on a floor that belong to a path, left to right. */
    public List<MapNode> row(int y) {
        List<MapNode> list = new ArrayList<>();
        for (MapNode n : grid[y]) {
            if (n.used()) {
                list.add(n);
            }
        }
        return list;
    }

    /** Every node that belongs to a path, bottom row first. */
    public List<MapNode> usedNodes() {
        List<MapNode> list = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            list.addAll(row(y));
        }
        return list;
    }

    /** Where the player may go next from {@code current} (null = start of act). */
    public List<MapNode> nextChoices(MapNode current) {
        if (current == null) {
            return row(0);
        }
        return current.children();
    }

    /** A compact text drawing, top floor first, for logs and tests. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int y = HEIGHT - 1; y >= 0; y--) {
            sb.append(String.format("%2d ", y));
            for (int x = 0; x < WIDTH; x++) {
                MapNode n = grid[y][x];
                sb.append(n.used() ? symbol(n.room()) : '.').append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static char symbol(String room) {
        if (room == null) {
            return '?';
        }
        return switch (room) {
            case "Monster" -> 'M';
            case "Elite" -> 'E';
            case "Event" -> '?';
            case "Treasure" -> 'T';
            case "Shop" -> '$';
            case "Rest" -> 'R';
            default -> '*';
        };
    }
}
