package com.openggf.mods.ui;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Ordered, mod-owned focus/hit regions. Replacing a layout preserves focused identity. */
@com.openggf.game.ModApi
public final class FocusRegions {
    @com.openggf.game.ModApi
    public enum Direction { UP, DOWN, LEFT, RIGHT, NEXT, PREVIOUS }
    @com.openggf.game.ModApi
    public record Region(String id, int x, int y, int width, int height, boolean enabled) {
        public Region {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid focus region");
        }
        boolean contains(int px, int py) {
            return enabled && px >= x && py >= y && (long) px < (long) x + width && (long) py < (long) y + height;
        }
    }
    private List<Region> regions = List.of();
    private String focused;
    public List<Region> regions() { return regions; }
    public String focused() { return focused; }
    public void replace(List<Region> layout) {
        List<Region> copy = List.copyOf(layout);
        HashSet<String> seen = new HashSet<>();
        for (Region r : copy) if (!seen.add(r.id())) throw new IllegalArgumentException("Duplicate focus identity: " + r.id());
        regions = copy;
        if (regions.stream().noneMatch(r -> r.enabled() && r.id().equals(focused)))
            focused = regions.stream().filter(Region::enabled).map(Region::id).findFirst().orElse(null);
    }
    public boolean focus(String id) {
        if (regions.stream().noneMatch(r -> r.enabled() && r.id().equals(id))) return false;
        focused = id;
        return true;
    }
    /** Last drawn enabled region wins if hit areas overlap. */
    public String hit(int x, int y) {
        for (int i = regions.size() - 1; i >= 0; i--) if (regions.get(i).contains(x, y)) return regions.get(i).id();
        return null;
    }
    public String move(Direction direction) {
        Objects.requireNonNull(direction, "direction");
        List<Region> active = regions.stream().filter(Region::enabled).toList();
        if (active.isEmpty()) return focused = null;
        int index = 0;
        for (int i = 0; i < active.size(); i++) if (active.get(i).id().equals(focused)) index = i;
        if (direction == Direction.NEXT || direction == Direction.PREVIOUS) {
            return focused = active.get(Math.floorMod(index + (direction == Direction.NEXT ? 1 : -1), active.size())).id();
        }
        Region from = active.get(index), best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Region r : active) {
            double dx = ((double) r.x() + r.width() / 2.0) - (from.x() + from.width() / 2.0);
            double dy = ((double) r.y() + r.height() / 2.0) - (from.y() + from.height() / 2.0);
            boolean candidate = switch (direction) {
                case UP -> dy < 0; case DOWN -> dy > 0; case LEFT -> dx < 0; case RIGHT -> dx > 0;
                default -> false;
            };
            double score = dx * dx + dy * dy;
            if (candidate && score < bestScore) { best = r; bestScore = score; }
        }
        if (best != null) focused = best.id();
        return focused;
    }
}
