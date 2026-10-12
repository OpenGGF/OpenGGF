package starpost.festivals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import starpost.core.Game;

/**
 * Scrap Brain Night's haunted maze (Fall 27): a perfect maze (one way between any two cells)
 * carved on a grid by a seeded depth-first walk, a new one each year, entered at the west and
 * left by the east where Mecha Sonic waits in the dark. Rings and a few scraps lie in its dead
 * ends; shadows of Scrap Brain's badniks lurk along the way. Engine-free.
 */
public final class Maze {
    public static final int COLUMNS = 13;
    public static final int ROWS = 7;
    /** Wall bits per cell. */
    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;
    /** Time allowed before the lights come up and the haunt is over. */
    public static final int TICKS = 60 * 90;
    public static final int RINGS_EACH = 10;
    public static final int FINISH_PURSE = 300;

    public final int[] walls = new int[COLUMNS * ROWS];
    public final int startRow;
    public final int exitRow;
    /** Dead ends holding a treasure (cell indexes), and where the badnik shadows lurk. */
    public final List<Integer> treasures = new ArrayList<>();
    public final List<Integer> lurkers = new ArrayList<>();

    /** Carves the year's maze. */
    public Maze(int seed) {
        java.util.Arrays.fill(walls, NORTH | EAST | SOUTH | WEST);
        startRow = Board.mix(seed, 1) % ROWS;
        exitRow = Board.mix(seed, 2) % ROWS;
        boolean[] seen = new boolean[COLUMNS * ROWS];
        Deque<Integer> stack = new ArrayDeque<>();
        int start = cell(0, startRow);
        seen[start] = true;
        stack.push(start);
        int step = 0;
        while (!stack.isEmpty()) {
            int c = stack.peek();
            int[] options = new int[4];
            int n = 0;
            for (int dir : new int[] {NORTH, EAST, SOUTH, WEST}) {
                int next = neighbour(c, dir);
                if (next >= 0 && !seen[next]) {
                    options[n++] = dir;
                }
            }
            if (n == 0) {
                stack.pop();
                continue;
            }
            int dir = options[Board.mix(seed, 100 + step++) % n];
            int next = neighbour(c, dir);
            walls[c] &= ~dir;
            walls[next] &= ~opposite(dir);
            seen[next] = true;
            stack.push(next);
        }
        int exit = cell(COLUMNS - 1, exitRow);
        for (int c = 0; c < walls.length; c++) {
            if (c == start || c == exit) {
                continue;
            }
            if (Integer.bitCount(walls[c]) == 3) {
                treasures.add(c);
            } else if (Board.mix(seed, 500 + c) % 9 == 0 && column(c) > 1) {
                lurkers.add(c);
            }
        }
    }

    public static int cell(int column, int row) {
        return row * COLUMNS + column;
    }

    public static int column(int cell) {
        return cell % COLUMNS;
    }

    public static int row(int cell) {
        return cell / COLUMNS;
    }

    public boolean open(int cell, int dir) {
        return (walls[cell] & dir) == 0;
    }

    /** The neighbouring cell in a direction, or -1 at the edge. */
    public static int neighbour(int cell, int dir) {
        int c = column(cell), r = row(cell);
        return switch (dir) {
            case NORTH -> r > 0 ? cell - COLUMNS : -1;
            case SOUTH -> r < ROWS - 1 ? cell + COLUMNS : -1;
            case EAST -> c < COLUMNS - 1 ? cell + 1 : -1;
            default -> c > 0 ? cell - 1 : -1;
        };
    }

    static int opposite(int dir) {
        return switch (dir) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            default -> EAST;
        };
    }

    /** Cells reachable from the entrance (all of them, in a perfect maze). */
    public int reachable() {
        boolean[] seen = new boolean[walls.length];
        Deque<Integer> queue = new ArrayDeque<>();
        int start = cell(0, startRow);
        seen[start] = true;
        queue.add(start);
        int count = 0;
        while (!queue.isEmpty()) {
            int c = queue.poll();
            count++;
            for (int dir : new int[] {NORTH, EAST, SOUTH, WEST}) {
                int next = neighbour(c, dir);
                if (next >= 0 && open(c, dir) && !seen[next]) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return count;
    }

    /** Openings between cells: a perfect maze has exactly one fewer than it has cells. */
    public int passages() {
        int count = 0;
        for (int c = 0; c < walls.length; c++) {
            if (open(c, EAST) && neighbour(c, EAST) >= 0) {
                count++;
            }
            if (open(c, SOUTH) && neighbour(c, SOUTH) >= 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * The night's prizes: rings found pay, reaching Mecha Sonic pays a purse and, the first time,
     * the Death Egg Record. Returns notices.
     */
    public static List<String> reward(Game game, Festivals festivals, int rings, int scrap, boolean finished,
            int ticks) {
        List<String> notices = new ArrayList<>();
        festivals.record(FestivalBook.SCRAP_BRAIN, game.calendar.year(), finished ? 1 : 0, rings);
        int pay = rings * RINGS_EACH + (finished ? FINISH_PURSE : 0);
        game.rings += pay;
        notices.add("+" + pay + " RINGS");
        if (scrap > 0) {
            Prizes.give(game, festivals, "scrap", scrap, notices);
        }
        if (finished) {
            festivals.recordTime(FestivalBook.SCRAP_BRAIN, ticks);
            if (festivals.takePrize("trophy." + FestivalBook.SCRAP_BRAIN)) {
                Prizes.give(game, festivals, "record_death_egg", 1, notices);
                notices.add("A MECHA SONIC FOR THE TROPHY STAND");
            }
        }
        Prizes.everyone(game, 20);
        return notices;
    }
}
