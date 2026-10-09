package starpost.core;

import com.openggf.mods.state.SnapshotRandom;

/**
 * The farm field in belt-view coordinates: {@link #ROWS} rows deep, columns along the valley.
 * Only the first {@link #open} columns are usable; expansions open more. A new farm starts
 * overgrown (Green Hill after the badniks), with a clear starter patch by the house.
 */
public final class Farm {
    public static final int ROWS = 5;
    public static final int COLUMNS = 60;
    public static final int START_COLUMNS = 24;
    public static final int STARTER_PATCH = 6;

    private final Plot[][] plots = new Plot[ROWS][COLUMNS];
    private int open = START_COLUMNS;

    public Farm() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                plots[r][c] = new Plot();
            }
        }
    }

    /** Scatters weeds, rocks and stumps over a new farm, leaving the starter patch clear. */
    public void overgrow(SnapshotRandom rng) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = STARTER_PATCH; c < COLUMNS; c++) {
                int roll = rng.nextInt(100);
                plots[r][c].cover = roll < 26 ? Plot.WEED : roll < 34 ? Plot.ROCK : roll < 39 ? Plot.STUMP : Plot.GRASS;
            }
        }
    }

    public Plot plot(int row, int column) {
        if (row < 0 || row >= ROWS || column < 0 || column >= open) {
            return null;
        }
        return plots[row][column];
    }

    /** Any plot, opened or not (saving and expansion). */
    public Plot raw(int row, int column) {
        return plots[row][column];
    }

    public int open() {
        return open;
    }

    public void open(int columns) {
        open = Math.max(open, Math.min(COLUMNS, columns));
    }

    /** Grows every watered crop by a day, dries the soil, and kills what is out of season. */
    public void nextDay(Catalog catalog, int season, boolean rain, SnapshotRandom rng) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                Plot plot = plots[r][c];
                if (plot.crop != null && !plot.dead) {
                    CropDef crop = catalog.crop(plot.crop);
                    if (crop == null || !crop.grows(season)) {
                        plot.dead = true;
                    } else if (plot.watered && plot.age < crop.days()) {
                        plot.age++;
                    }
                } else if (plot.tilled && plot.crop == null && !plot.watered && rng.nextInt(100) < 10) {
                    plot.tilled = false; // untended soil grasses over
                } else if (!plot.tilled && plot.cover == Plot.GRASS && c >= STARTER_PATCH && rng.nextInt(1000) < 6) {
                    plot.cover = Plot.WEED;
                }
                plot.watered = rain && plot.tilled;
            }
        }
    }

    /** Whether the crop on a plot is ready to pick. */
    public boolean ripe(Catalog catalog, Plot plot) {
        if (plot.crop == null || plot.dead) {
            return false;
        }
        CropDef crop = catalog.crop(plot.crop);
        return crop != null && plot.age >= crop.days();
    }

    /** Picks a ripe crop: the plant is used up, or set back to regrow. Returns the crop. */
    public CropDef harvest(Catalog catalog, Plot plot) {
        CropDef crop = catalog.crop(plot.crop);
        if (crop.regrow() > 0) {
            plot.age = crop.days() - crop.regrow();
        } else {
            plot.clearCrop();
        }
        return crop;
    }
}
