package slaytherobotnik.map;

import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.RoomType;
import slaytherobotnik.core.Rng;

/**
 * Slay the Spire's map algorithm, thinned out so a map reads at a glance.
 *
 * <ol>
 *   <li>Three paths (Slay the Spire uses six) climb a 7x15 grid. Each starts in its own column
 *       and moves up one floor at a time, left, straight or right. Paths never cross: a step
 *       is clamped so it cannot pass a neighbour's edge, and two paths that merge must not
 *       have split less than three floors earlier.</li>
 *   <li>All paths meet in a single room on floor 9 (the treasure) and on the last floor (the
 *       Starpost before the boss): each path steers for the middle column as those floors
 *       come near, so the act is two stretches of branches joined by corridors.</li>
 *   <li>Fixed floors: the first is all fights, floor 9 is treasure, the last is a Starpost
 *       (rest site) before the boss.</li>
 *   <li>The other rooms come from a shuffled bag (22% events, 12% rest sites, 8% elites,
 *       5% shops, the rest fights) placed under rules: no elites or rest sites before
 *       floor 6, no rest site on floor 14, no shop/rest/treasure/elite directly after the
 *       same type, and siblings that share a parent get different types where possible.</li>
 * </ol>
 */
public final class MapGenerator {
    private static final int PATHS = 3;
    private static final int TREASURE_FLOOR = 8;
    private static final int MIN_ANCESTOR_GAP = 3;
    private static final int MAX_ANCESTOR_GAP = 5;

    private MapGenerator() {
    }

    public static ActMap generate(Rng rng) {
        MapNode[][] grid = new MapNode[ActMap.HEIGHT][ActMap.WIDTH];
        for (int y = 0; y < ActMap.HEIGHT; y++) {
            for (int x = 0; x < ActMap.WIDTH; x++) {
                grid[y][x] = new MapNode(x, y);
            }
        }
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < PATHS; i++) {
            int start = rng.range(0, ActMap.WIDTH - 1);
            while (starts.contains(start)) {
                start = rng.range(0, ActMap.WIDTH - 1);
            }
            starts.add(start);
            climb(grid, grid[0][start], rng);
        }
        assignRooms(grid, rng);
        for (MapNode[] row : grid) {
            for (MapNode n : row) {
                n.setOffset(rng.range(-5, 5), rng.range(-4, 4));
            }
        }
        return new ActMap(grid);
    }

    /** A single straight path of fixed rooms in the middle column (the final act). */
    public static ActMap linear(List<String> rooms) {
        MapNode[][] grid = new MapNode[ActMap.HEIGHT][ActMap.WIDTH];
        for (int y = 0; y < ActMap.HEIGHT; y++) {
            for (int x = 0; x < ActMap.WIDTH; x++) {
                grid[y][x] = new MapNode(x, y);
            }
        }
        int mid = ActMap.WIDTH / 2;
        for (int y = 0; y < rooms.size() && y < ActMap.HEIGHT; y++) {
            grid[y][mid].setRoom(rooms.get(y));
            if (y + 1 < rooms.size()) {
                grid[y][mid].addChild(grid[y + 1][mid]);
            } else {
                grid[y][mid].setConnectsToBoss(true);
            }
        }
        return new ActMap(grid);
    }

    private static void climb(MapNode[][] grid, MapNode start, Rng rng) {
        MapNode current = start;
        while (true) {
            int y = current.y();
            if (y + 1 >= ActMap.HEIGHT) {
                current.setConnectsToBoss(true);
                return;
            }
            int x = current.x();
            int last = ActMap.WIDTH - 1;
            int min = x == 0 ? 0 : -1;
            int max = x == last ? 0 : 1;
            int nx = x + rng.range(min, max);
            MapNode candidate = grid[y + 1][nx];

            // Two paths that merge must have been apart for at least MIN_ANCESTOR_GAP floors.
            for (MapNode parent : candidate.parents()) {
                if (parent == current) {
                    continue;
                }
                MapNode ancestor = commonAncestor(parent, current, MAX_ANCESTOR_GAP);
                if (ancestor != null && (y + 1) - ancestor.y() < MIN_ANCESTOR_GAP) {
                    if (candidate.x() > x) {
                        nx = x + rng.range(-1, 0);
                        if (nx < 0) {
                            nx = x;
                        }
                    } else if (candidate.x() == x) {
                        nx = x + rng.range(-1, 1);
                        if (nx > last) {
                            nx = x - 1;
                        } else if (nx < 0) {
                            nx = x + 1;
                        }
                    } else {
                        nx = x + rng.range(0, 1);
                        if (nx > last) {
                            nx = x;
                        }
                    }
                    candidate = grid[y + 1][nx];
                }
            }

            // Steer for the next meeting room: never further from its column than the floors left.
            int meet = y + 1 <= TREASURE_FLOOR ? TREASURE_FLOOR : ActMap.HEIGHT - 1;
            int target = ActMap.WIDTH / 2;
            if (Math.abs(nx - target) > meet - (y + 1)) {
                nx = x + Integer.signum(target - x);
            }

            // Never cross a neighbour's edges.
            if (x > 0) {
                MapNode left = grid[y][x - 1];
                for (MapNode child : left.children()) {
                    nx = Math.max(nx, child.x());
                }
            }
            if (x < last) {
                MapNode right = grid[y][x + 1];
                for (MapNode child : right.children()) {
                    nx = Math.min(nx, child.x());
                }
            }
            MapNode next = grid[y + 1][nx];
            current.addChild(next);
            current = next;
        }
    }

    /** Lowest node both can reach going down within {@code maxDepth} floors, or null. */
    private static MapNode commonAncestor(MapNode a, MapNode b, int maxDepth) {
        if (a.y() != b.y() || a == b) {
            return a == b ? a : null;
        }
        List<MapNode> left = List.of(a.x() < b.x() ? a : b);
        List<MapNode> right = List.of(a.x() < b.x() ? b : a);
        for (int depth = 0; depth < maxDepth; depth++) {
            List<MapNode> nextLeft = new ArrayList<>();
            List<MapNode> nextRight = new ArrayList<>();
            for (MapNode n : left) {
                nextLeft.addAll(n.parents());
            }
            for (MapNode n : right) {
                nextRight.addAll(n.parents());
            }
            for (MapNode n : nextLeft) {
                if (nextRight.contains(n)) {
                    return n;
                }
            }
            if (nextLeft.isEmpty() || nextRight.isEmpty()) {
                return null;
            }
            left = nextLeft;
            right = nextRight;
        }
        return null;
    }

    private static void assignRooms(MapNode[][] grid, Rng rng) {
        List<MapNode> open = new ArrayList<>();
        for (int y = 0; y < ActMap.HEIGHT; y++) {
            for (MapNode n : grid[y]) {
                if (!n.used()) {
                    continue;
                }
                if (y == 0) {
                    n.setRoom(RoomType.MONSTER);
                } else if (y == TREASURE_FLOOR) {
                    n.setRoom(RoomType.TREASURE);
                } else if (y == ActMap.HEIGHT - 1) {
                    n.setRoom(RoomType.REST);
                } else {
                    open.add(n);
                }
            }
        }
        int total = open.size();
        List<String> bag = new ArrayList<>();
        addCopies(bag, RoomType.SHOP, Math.round(total * 0.05f));
        addCopies(bag, RoomType.REST, Math.round(total * 0.12f));
        addCopies(bag, RoomType.EVENT, Math.round(total * 0.22f));
        addCopies(bag, RoomType.ELITE, Math.round(total * 0.08f));
        while (bag.size() < total) {
            bag.add(RoomType.MONSTER);
        }
        rng.shuffle(bag);
        for (MapNode n : open) {
            String chosen = null;
            for (int i = 0; i < bag.size(); i++) {
                if (allowed(n, bag.get(i))) {
                    chosen = bag.remove(i);
                    break;
                }
            }
            n.setRoom(chosen == null ? RoomType.MONSTER : chosen);
        }
    }

    private static void addCopies(List<String> bag, String room, int count) {
        for (int i = 0; i < count; i++) {
            bag.add(room);
        }
    }

    private static boolean allowed(MapNode n, String room) {
        int y = n.y();
        if ((room.equals(RoomType.REST) || room.equals(RoomType.ELITE)) && y < 5) {
            return false;
        }
        if (room.equals(RoomType.REST) && y >= ActMap.HEIGHT - 2) {
            return false;
        }
        boolean noRepeatAfterParent = room.equals(RoomType.REST) || room.equals(RoomType.TREASURE)
                || room.equals(RoomType.SHOP) || room.equals(RoomType.ELITE);
        if (noRepeatAfterParent) {
            for (MapNode parent : n.parents()) {
                if (room.equals(parent.room())) {
                    return false;
                }
            }
            // Also check the floor above, which may already be fixed (treasure, rest).
            for (MapNode child : n.children()) {
                if (room.equals(child.room())) {
                    return false;
                }
            }
        }
        boolean distinctSiblings = room.equals(RoomType.REST) || room.equals(RoomType.MONSTER)
                || room.equals(RoomType.EVENT) || room.equals(RoomType.ELITE) || room.equals(RoomType.SHOP);
        if (distinctSiblings) {
            for (MapNode parent : n.parents()) {
                for (MapNode sibling : parent.children()) {
                    if (sibling != n && room.equals(sibling.room())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
