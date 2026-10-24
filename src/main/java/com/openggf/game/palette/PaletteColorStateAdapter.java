package com.openggf.game.palette;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.PaletteColorSnapshot;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.RenderContext;
import com.openggf.level.Palette;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Captures and restores live palette COLOR words for the normal and
 * underwater surfaces and shared donor palettes, and re-caches GPU palette textures on restore.
 *
 * <p>{@link PaletteOwnershipRegistry} snapshots ownership metadata only;
 * the colors themselves are mutated in place on {@code Level} palettes and
 * the water system's underwater set (via {@code resolveInto} and direct
 * writes such as the AIZ intro's palette mutations). Without this adapter
 * those writes are invisible to rewind: flags revert, colors don't.
 *
 * <p>Suppliers are re-evaluated on every capture/restore so the adapter
 * follows level/act swaps without re-registration.
 */
public final class PaletteColorStateAdapter implements RewindSnapshottable<PaletteColorSnapshot> {

    private static final int BYTES_PER_COLOR = 3;
    private static final int BYTES_PER_LINE = Palette.PALETTE_SIZE * BYTES_PER_COLOR;

    private final Supplier<Palette[]> normalPalettes;
    private final Supplier<Palette[]> underwaterPalettes;
    private final Supplier<GraphicsManager> graphics;
    private final Supplier<Collection<RenderContext>> donorContexts;
    // Only current registered banks are retained. Replacing a row within one bank keeps its lifetime;
    // resetting/replacing a bank or creating a new adapter cannot adopt an old capture by GameId/row.
    private final Map<RenderContext, UUID> donorLifetimes = new IdentityHashMap<>();

    public PaletteColorStateAdapter(Supplier<Palette[]> normalPalettes,
                                    Supplier<Palette[]> underwaterPalettes,
                                    Supplier<GraphicsManager> graphics) {
        this(normalPalettes, underwaterPalettes, graphics, List::of);
    }

    public PaletteColorStateAdapter(Supplier<Palette[]> normalPalettes,
                                    Supplier<Palette[]> underwaterPalettes,
                                    Supplier<GraphicsManager> graphics,
                                    Supplier<Collection<RenderContext>> donorContexts) {
        this.normalPalettes = Objects.requireNonNull(normalPalettes, "normalPalettes");
        this.underwaterPalettes = Objects.requireNonNull(underwaterPalettes, "underwaterPalettes");
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        this.donorContexts = Objects.requireNonNull(donorContexts, "donorContexts");
    }

    @Override
    public String key() {
        return "palette-colors";
    }

    @Override
    public PaletteColorSnapshot capture() {
        var donors = currentDonors().stream().map(context -> {
            Palette[] rows = donorRows(context);
            int present = 0;
            for (int line = 0; line < rows.length; line++) if (rows[line] != null) present |= 1 << line;
            return new PaletteColorSnapshot.DonorColors(donorLifetimes.get(context), present, pack(rows));
        }).toList();
        return new PaletteColorSnapshot(pack(normalPalettes.get()), pack(underwaterPalettes.get()), donors);
    }

    @Override
    public void restore(PaletteColorSnapshot snapshot) {
        Palette[] normal = normalPalettes.get();
        Palette[] underwater = underwaterPalettes.get();
        unpack(normal, snapshot.normalRgb());
        unpack(underwater, snapshot.underwaterRgb());
        var restoredDonors = new LinkedHashMap<RenderContext, Integer>();
        for (RenderContext context : currentDonors()) {
            for (var donor : snapshot.donors()) {
                if (!donor.lifetime().equals(donorLifetimes.get(context))) continue;
                for (int line = 0; line < RenderContext.LINES_PER_CONTEXT; line++) {
                    Palette row = context.getPalette(line);
                    if ((donor.presentLines() & (1 << line)) != 0 && row != null)
                        unpackLine(row, donor.rgb(), line * BYTES_PER_LINE);
                }
                restoredDonors.put(context, donor.presentLines());
            }
        }
        recacheTextures(normal, underwater, restoredDonors);
    }

    @Override
    public void resetForMissingSnapshot() {
        // Colors simply stay live; nothing to reset.
    }

    private List<RenderContext> currentDonors() {
        var current = List.copyOf(donorContexts.get());
        donorLifetimes.keySet().retainAll(current);
        for (RenderContext context : current) donorLifetimes.computeIfAbsent(context, ignored -> UUID.randomUUID());
        return current;
    }

    private static Palette[] donorRows(RenderContext context) {
        Palette[] rows = new Palette[RenderContext.LINES_PER_CONTEXT];
        for (int line = 0; line < rows.length; line++) rows[line] = context.getPalette(line);
        return rows;
    }

    private static byte[] pack(Palette[] palettes) {
        if (palettes == null) {
            return new byte[0];
        }
        byte[] out = new byte[palettes.length * BYTES_PER_LINE];
        int i = 0;
        for (Palette palette : palettes) {
            if (palette == null) {
                i += BYTES_PER_LINE;
                continue;
            }
            for (int c = 0; c < Palette.PALETTE_SIZE; c++) {
                Palette.Color color = palette.colors[c];
                out[i++] = color.r;
                out[i++] = color.g;
                out[i++] = color.b;
            }
        }
        return out;
    }

    private static void unpack(Palette[] palettes, byte[] rgb) {
        if (palettes == null || rgb.length == 0) {
            return;
        }
        int lines = Math.min(palettes.length, rgb.length / BYTES_PER_LINE);
        int i = 0;
        for (int l = 0; l < lines; l++) {
            if (palettes[l] == null) {
                i += BYTES_PER_LINE;
                continue;
            }
            unpackLine(palettes[l], rgb, i);
            i += BYTES_PER_LINE;
        }
    }

    private static void unpackLine(Palette palette, byte[] rgb, int offset) {
        for (int c = 0; c < Palette.PALETTE_SIZE; c++) {
            // Write fields in place: other code aliases Color references.
            Palette.Color color = palette.colors[c];
            color.r = rgb[offset++];
            color.g = rgb[offset++];
            color.b = rgb[offset++];
        }
    }

    private void recacheTextures(Palette[] normal, Palette[] underwater,
                                 Map<RenderContext, Integer> restoredDonors) {
        GraphicsManager g = graphics.get();
        if (g == null || !g.isGlInitialized()) {
            return;
        }
        if (normal != null) {
            for (int line = 0; line < normal.length; line++) {
                if (normal[line] != null) {
                    g.cachePaletteTexture(normal[line], line);
                }
            }
        }
        // Match donor super writes' immediate upload, using the live bank's rows. Restore donors
        // before deriving native-water expanded rows; leave native write/fade/profile owners alone.
        restoredDonors.forEach((context, present) -> {
            for (int line = 0; line < RenderContext.LINES_PER_CONTEXT; line++) {
                Palette row = context.getPalette(line);
                if ((present & (1 << line)) != 0 && row != null)
                    g.cachePaletteTexture(row, context.getEffectivePaletteLine(line));
            }
        });
        if (underwater != null) {
            Palette normalLine0 = normal != null && normal.length > 0 ? normal[0] : null;
            g.cacheUnderwaterPaletteTexture(underwater, normalLine0);
        }
    }
}
