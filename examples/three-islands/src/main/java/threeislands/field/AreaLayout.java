package threeislands.field;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import threeislands.core.Zone;

/** Authored room graphs. Only explicit links form passages, even between neighbouring rooms. */
public final class AreaLayout {
    public record Point(int x, int y) {}
    public record Passage(Point from, Point to, String lock) {
        boolean contains(double x, double y, int radius) {
            return x >= Math.min(from.x, to.x) - radius && x <= Math.max(from.x, to.x) + radius
                    && y >= Math.min(from.y, to.y) - radius && y <= Math.max(from.y, to.y) + radius;
        }
        boolean gate(double x, double y) {
            int cx = (from.x + to.x) / 2, cy = (from.y + to.y) / 2;
            return Math.abs(x - cx) <= (from.x == to.x ? 32 : 8)
                    && Math.abs(y - cy) <= (from.y == to.y ? 32 : 8);
        }
    }
    public final List<Point> route;
    public final List<Point> alcoves;
    public final List<Passage> passages;
    public final boolean interior;
    public final int width, height;

    private AreaLayout(boolean interior, String path, String branches) {
        this.interior = interior;
        int step = interior ? 192 : 256;
        route = points(path, step);
        List<Point> side = new ArrayList<>();
        List<Passage> links = new ArrayList<>();
        for (int i = 1; i < route.size(); i++) {
            String lock = interior ? i == 2 ? "dungeon-guard-0" : i == route.size() - 1 ? "vault" : ""
                    : i == route.size() - 2 ? "route" : "";
            links.add(new Passage(route.get(i - 1), route.get(i), lock));
        }
        for (String branch : branches.split(";")) {
            String[] parts = branch.split(":");
            Point parent = route.get(Integer.parseInt(parts[0]));
            for (Point point : points(parts[1], step)) {
                side.add(point);
                links.add(new Passage(parent, point, ""));
                parent = point;
            }
        }
        alcoves = List.copyOf(side);
        passages = List.copyOf(links);
        int maxX = 0, maxY = 0;
        for (Point p : rooms()) { maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); }
        width = maxX + 144;
        height = maxY + 144;
    }
    private static List<Point> points(String text, int step) {
        List<Point> result = new ArrayList<>();
        for (String token : text.split(" ")) {
            String[] xy = token.split(",");
            result.add(new Point(144 + Integer.parseInt(xy[0]) * step, 336 + Integer.parseInt(xy[1]) * 192));
        }
        return List.copyOf(result);
    }
    /** The last authored branch ends in a secret room: a lost animal outside, treasure inside. */
    public Point secret() {
        return alcoves.getLast();
    }

    /** Side rooms available for puzzle controls (every alcove except the secret room). */
    public int controlRooms() {
        return alcoves.size() - 1;
    }

    public List<Point> rooms() {
        List<Point> rooms = new ArrayList<>(route); rooms.addAll(alcoves); return rooms;
    }
    public boolean floor(double x, double y) {
        int radius = interior ? 64 : 88;
        for (Point p : route) if (Math.abs(x - p.x) <= radius && Math.abs(y - p.y) <= radius) return true;
        for (Point p : alcoves) if (Math.abs(x - p.x) <= radius && Math.abs(y - p.y) <= radius) return true;
        // The western arrival/exit is always connected to the first refuge.
        if (x >= 48 && x <= 144 && Math.abs(y - 336) <= 32) return true;
        for (Passage passage : passages) if (passage.contains(x, y, 32)) return true;
        return false;
    }
    public boolean sealed(double x, double y, Predicate<String> open) {
        for (Passage passage : passages) if (!passage.lock.isEmpty() && !open.test(passage.lock) && passage.gate(x, y)) return true;
        return false;
    }

    public static AreaLayout outside(Zone zone) {
        return switch (zone) {
            // Outdoor routes finish at the eastern crossing, but reach it through different districts.
            case GREEN_HILL, STAR_LIGHT -> throw new IllegalArgumentException(zone + " uses its authored tile map");
            case SPRING_YARD -> new AreaLayout(false, "0,0 0,1 1,1 2,1 2,0 3,0 3,-1 4,-1 5,-1 5,0", "2:1,2;4:1,0;5:3,1;1:0,2 0,3");
            case EMERALD_HILL -> new AreaLayout(false, "0,0 1,0 1,-1 2,-1 3,-1 3,0 3,1 4,1 5,1 5,0", "1:1,1;4:4,-1;6:2,1;6:3,2 2,2");
            case CHEMICAL_PLANT -> new AreaLayout(false, "0,0 0,1 1,1 1,2 2,2 3,2 3,1 3,0 4,0 5,0", "2:2,1;5:4,2;7:3,-1;4:2,3 1,3");
            case MYSTIC_CAVE -> new AreaLayout(false, "0,0 1,0 1,1 0,1 0,2 1,2 2,2 3,2 3,1 4,1 4,0 5,0", "1:1,-1;6:2,1;8:3,0;6:2,3 3,3");
            case ANGEL_ISLAND -> new AreaLayout(false, "0,0 0,-1 1,-1 2,-1 2,0 1,0 1,1 2,1 3,1 4,1 4,0 5,0", "3:3,-1;6:1,2;9:4,2;7:2,2 3,2");
            case HYDROCITY -> new AreaLayout(false, "0,0 1,0 1,1 2,1 2,2 3,2 4,2 4,1 3,1 3,0 4,0 5,0", "1:1,-1;4:1,2;9:3,-1;5:3,3 4,3");
            case LAUNCH_BASE -> new AreaLayout(false, "0,0 0,1 0,2 1,2 2,2 2,1 2,0 2,-1 3,-1 4,-1 4,0 5,0", "4:3,2;6:1,0;8:3,0;2:0,3 1,3");
            case DEATH_EGG -> new AreaLayout(false, "0,0 0,-1 1,-1 1,0 2,0 2,1 1,1 1,2 2,2 3,2 4,2 4,1 4,0 5,0", "4:2,-1;7:0,2;9:3,1;11:5,1;8:2,3 3,3");
        };
    }
    public static AreaLayout inside(Zone zone) {
        return switch (zone) {
            case GREEN_HILL -> new AreaLayout(true, "0,0 1,0 1,-1 2,-1 3,-1", "2:0,-1;3:2,0");
            case STAR_LIGHT -> new AreaLayout(true, "0,0 1,0 2,0 2,-1 3,-1 4,-1", "2:2,1;3:1,-1;4:3,0");
            case SPRING_YARD -> new AreaLayout(true, "0,0 0,1 1,1 2,1 2,0 3,0 3,-1", "2:1,2;4:1,0;3:2,2");
            case EMERALD_HILL -> new AreaLayout(true, "0,0 1,0 1,-1 2,-1 3,-1 3,0 3,1", "2:0,-1;4:4,-1;5:2,0;5:4,0");
            case CHEMICAL_PLANT -> new AreaLayout(true, "0,0 0,1 1,1 1,2 2,2 3,2 3,1 4,1", "2:2,1;4:2,3;6:3,0;3:0,2");
            case MYSTIC_CAVE -> new AreaLayout(true, "0,0 1,0 1,1 0,1 0,2 1,2 2,2 2,1 3,1", "1:1,-1;5:1,3;6:3,2;6:2,3");
            case ANGEL_ISLAND -> new AreaLayout(true, "0,0 0,-1 1,-1 2,-1 2,0 1,0 1,1 2,1 3,1", "3:3,-1;6:0,1;7:2,2;6:1,2");
            case HYDROCITY -> new AreaLayout(true, "0,0 0,1 1,1 1,2 2,2 3,2 3,1 2,1 2,0 3,0", "3:0,2;5:4,2;8:2,-1;4:2,3");
            case LAUNCH_BASE -> new AreaLayout(true, "0,0 1,0 1,-1 2,-1 3,-1 3,0 2,0 2,1 3,1 4,1 4,0", "4:4,-1;7:1,1;8:3,2;7:2,2");
            case DEATH_EGG -> new AreaLayout(true, "0,0 0,1 0,2 1,2 2,2 2,1 1,1 1,0 1,-1 2,-1 3,-1 3,0", "3:1,3;5:3,1;8:0,-1;10:4,-1;4:3,2");
        };
    }
}
