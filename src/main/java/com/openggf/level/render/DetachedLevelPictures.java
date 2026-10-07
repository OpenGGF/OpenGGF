package com.openggf.level.render;

import com.openggf.level.Level;
import com.openggf.level.Pattern;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Static stock-level pictures from an explicitly detached decoder. The module supplies
 * public zone/act identity and the decoder; this presentation owner never consults live
 * gameplay, settings or graphics. Animated tiles and palettes remain at their load state.
 */
public final class DetachedLevelPictures implements ZonePictureSource {
    @FunctionalInterface
    public interface Loader {
        Level load() throws IOException;
    }

    /** Finds the loader for any act's {@link #kit}, or null when the game has no such act. */
    @FunctionalInterface
    public interface KitLoaders {
        Loader loader(int zone, int act);
    }

    /** Kits keep the few most recent acts; older ones are rebuilt when asked again. */
    private static final int KIT_CACHE = 3;

    private final int zone;
    private final int act;
    private final Loader loader;
    private final int backdropWidth;
    private final int backdropHeight;
    private final KitLoaders kitLoaders;
    private Level level;
    private PlaneRasterizer.TileSource tiles;
    /** Built kits by zone and act, most recent last; acts that cannot be built map to null. */
    private final java.util.LinkedHashMap<List<Integer>, DetachedLevelKit> kits =
            new java.util.LinkedHashMap<>(8, 0.75f, true);

    public DetachedLevelPictures(int zone, int act, Loader loader, int backdropWidth, int backdropHeight) {
        this(zone, act, loader, backdropWidth, backdropHeight, null);
    }

    /**
     * @param kitLoaders how to build any act for {@link #kit}, or null for no kits
     */
    public DetachedLevelPictures(int zone, int act, Loader loader, int backdropWidth, int backdropHeight,
            KitLoaders kitLoaders) {
        if (zone < 0 || act < 0 || backdropWidth < 1 || backdropHeight < 1
                || backdropWidth > 4096 || backdropHeight > 4096) {
            throw new IllegalArgumentException("Invalid detached picture profile");
        }
        this.zone = zone;
        this.act = act;
        this.loader = Objects.requireNonNull(loader, "loader");
        this.backdropWidth = backdropWidth;
        this.backdropHeight = backdropHeight;
        this.kitLoaders = kitLoaders;
    }

    @Override
    public DetachedLevelKit kit(int zone, int act) {
        if (kitLoaders == null || zone < 0 || act < 0) {
            return null;
        }
        List<Integer> key = List.of(zone, act);
        if (kits.containsKey(key)) {
            return kits.get(key);
        }
        DetachedLevelKit kit = null;
        Loader loader = kitLoaders.loader(zone, act);
        if (loader != null) {
            try {
                Level built = loader.load();
                Pattern[] patterns = new Pattern[built.getPatternCount()];
                for (int i = 0; i < patterns.length; i++) patterns[i] = built.getPattern(i);
                int[] edges = bounds(built);
                kit = new DetachedLevelKit(built, PlaneRasterizer.TileSource.of(patterns),
                        new int[] {edges[0], edges[1], edges[2] - edges[0], edges[3] - edges[1]}, null);
            } catch (IOException | IllegalArgumentException | IllegalStateException unsupported) {
                // Acts this decoder cannot build detached (resource-plan zones) have no kit.
                kit = null;
            }
        }
        kits.put(key, kit);
        while (kits.size() > KIT_CACHE) {
            kits.remove(kits.keySet().iterator().next());
        }
        return kit;
    }

    @Override
    public boolean supports(int zone, int act) {
        return this.zone == zone && this.act == act;
    }

    private Level level() {
        if (level == null) {
            try {
                level = loader.load();
                Pattern[] patterns = new Pattern[level.getPatternCount()];
                for (int i = 0; i < patterns.length; i++) patterns[i] = level.getPattern(i);
                tiles = PlaneRasterizer.TileSource.of(patterns);
            } catch (IOException failure) {
                throw new IllegalStateException("Detached zone picture unavailable", failure);
            }
        }
        return level;
    }

    private int backdropColour() {
        // Stock VDP register 7 selects palette line 2, colour 0 ($20).
        return PlaneRasterizer.argb(level().getPalette(2).getColor(0));
    }

    @Override
    public Backdrop backdrop(int zone, int act) {
        if (!supports(zone, act)) return null;
        Level value = level();
        int width = Math.min(backdropWidth, value.getLayerWidthBlocks(PlaneRasterizer.BACKGROUND)
                * value.getBlockPixelSize());
        int height = Math.min(backdropHeight, value.getLayerHeightBlocks(PlaneRasterizer.BACKGROUND)
                * value.getBlockPixelSize());
        return new Backdrop(new Picture(width, height, PlaneRasterizer.rasterize(value, tiles,
                PlaneRasterizer.BACKGROUND, 0, 0, width, height, backdropColour())),
                List.of(new Band(0, height, 0, 0)));
    }

    private int[] bounds() {
        return bounds(level());
    }

    /** Where the camera can show, {@code {left, top, right, bottom}}. */
    private static int[] bounds(Level value) {
        int left = Math.max(0, value.getMinX());
        int top = Math.max(0, value.getMinY());
        int right = Math.min(value.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND) * value.getBlockPixelSize(),
                value.getMaxX() + 320);
        int bottom = Math.min(value.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND) * value.getBlockPixelSize(),
                value.getMaxY() + 224);
        return new int[] {left, top, Math.max(left + 1, right), Math.max(top + 1, bottom)};
    }

    @Override
    public Picture overview(int zone, int act, int maxHeight) {
        if (maxHeight < 1) throw new IllegalArgumentException("maxHeight must be positive");
        if (!supports(zone, act)) return null;
        int[] bounds = bounds();
        int width = bounds[2] - bounds[0];
        int height = bounds[3] - bounds[1];
        int divisor = Math.max(Math.ceilDiv(width, 4096), Math.ceilDiv(height, Math.min(maxHeight, 4096)));
        int[] pixels = PlaneRasterizer.compositeOverview(level(), tiles, bounds[0], bounds[1], width, height,
                divisor, backdropColour(), PlaneRasterizer.BackgroundWrap.layout(level()));
        return new Picture(Math.ceilDiv(width, divisor), Math.ceilDiv(height, divisor), pixels);
    }

    @Override
    public List<LevelFloorScanner.Stage> stages(int zone, int act, int width, int headroom, int maxRise) {
        if (width < 1 || headroom < 1 || maxRise < 0) throw new IllegalArgumentException("Invalid stage size");
        if (!supports(zone, act)) return List.of();
        int[] bounds = bounds();
        return LevelFloorScanner.stages(level(), bounds[0], bounds[1], bounds[2], bounds[3],
                width, headroom, maxRise);
    }

    @Override
    public Picture foreground(int zone, int act, int x, int y, int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Invalid foreground size");
        }
        if (!supports(zone, act)) return null;
        Level value = level();
        int right = value.getLayerWidthBlocks(PlaneRasterizer.FOREGROUND) * value.getBlockPixelSize();
        int bottom = value.getLayerHeightBlocks(PlaneRasterizer.FOREGROUND) * value.getBlockPixelSize();
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(right, x + width);
        int y1 = Math.min(bottom, y + height);
        int[] pixels = new int[width * height];
        if (x0 < x1 && y0 < y1) {
            int[] crop = PlaneRasterizer.rasterize(value, tiles, PlaneRasterizer.FOREGROUND,
                    x0, y0, x1 - x0, y1 - y0, 0);
            for (int row = 0; row < y1 - y0; row++) {
                System.arraycopy(crop, row * (x1 - x0), pixels,
                        (y0 - y + row) * width + x0 - x, x1 - x0);
            }
        }
        return new Picture(width, height, pixels);
    }

    @Override public boolean hasTitleCard(int zone, int act) { return false; }
    @Override public Sprites titleCard(int zone, int act) { return null; }
}
