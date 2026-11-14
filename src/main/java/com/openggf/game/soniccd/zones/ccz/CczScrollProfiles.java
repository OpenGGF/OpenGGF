package com.openggf.game.soniccd.zones.ccz;

import com.openggf.game.soniccd.CdStagePayload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The ten Collision Chaos scroll routines as data: deformation rates, the ordered blocks of the
 * per-8-line word table, and the water pass limits. Generated from the disassembly by
 * {@code tools/soniccd/ccz_scroll_profiles.py}; every row carries its source citation in the file.
 */
final class CczScrollProfiles {
    private static final String RESOURCE = "/soniccd/ccz-scroll-profiles.tsv";
    private static final Map<CdStagePayload, Profile> PROFILES = load();

    /** The background layer a fill block copies (cameraBgX, cameraBg2X, cameraBg3X). */
    enum Layer { BGX, BG2X, BG3X }

    sealed interface Block permits Fghi, Fill, Deform, Persp { }

    /** {@code move.w cameraX,d0 / neg.w / swap}: the foreground word held in the high half of d0. */
    record Fghi() implements Block { }

    /** {@code n} words of the negated layer position. */
    record Fill(Layer layer, int count) implements Block { }

    /** One word per deformation long, repeated {@code repeats[d6]+1} times, d6 counting down from n-1. */
    record Deform(int count, int[] repeats) implements Block { }

    /**
     * A perspective ramp: d2 = ((cameraX - cameraBgX) &lt;&lt; shift1) / divisor &lt;&lt; shift2, then n words
     * starting at cameraBgX and stepping by d2. {@code back} blocks write downwards (-(a1)).
     */
    record Persp(int divisor, int shift1, int shift2, int count, int[] repeats, boolean back) implements Block { }

    /** The hscroll pass limits (see CczScrollHandler.writeLines). */
    record Water(int rows, int total, int compare, int clamp, int[] bounds, int window, int waterLine,
                 boolean doubledWobble, boolean checksNegative) { }

    record Profile(int[] rates, int curveCount, int curveMultiplier, int curveHalf, List<Block> blocks, Water water) { }

    private CczScrollProfiles() { }

    static Profile of(CdStagePayload payload) {
        Profile profile = PROFILES.get(payload);
        if (profile == null) throw new IllegalArgumentException(payload + " is not a Collision Chaos stage");
        return profile;
    }

    static boolean has(CdStagePayload payload) {
        return PROFILES.containsKey(payload);
    }

    private static int[] ints(String csv, int radix) {
        return Arrays.stream(csv.split(",")).mapToInt(s -> Integer.parseInt(s, radix)).toArray();
    }

    private static Map<CdStagePayload, Profile> load() {
        record Draft(int[] rates, int[] curve, List<Block> blocks, Water water) { }
        Map<CdStagePayload, Draft> drafts = new EnumMap<>(CdStagePayload.class);
        try (InputStream stream = CczScrollProfiles.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing " + RESOURCE);
            var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.US_ASCII));
            boolean header = false;
            Map<CdStagePayload, int[]> rates = new EnumMap<>(CdStagePayload.class);
            Map<CdStagePayload, int[]> curves = new EnumMap<>(CdStagePayload.class);
            Map<CdStagePayload, List<Block>> blocks = new EnumMap<>(CdStagePayload.class);
            Map<CdStagePayload, Water> waters = new EnumMap<>(CdStagePayload.class);
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] c = Arrays.copyOf(line.split("\t", -1), 9);
                if (!header) {
                    if (!c[0].equals("stage") || !c[1].equals("kind")) {
                        throw new IllegalStateException(RESOURCE + " has unexpected columns");
                    }
                    header = true;
                    continue;
                }
                CdStagePayload stage = CdStagePayload.valueOf(c[0]);
                List<Block> list = blocks.computeIfAbsent(stage, k -> new ArrayList<>());
                switch (c[1]) {
                    case "rates" -> rates.put(stage, ints(c[2], 16));
                    case "curve" -> curves.put(stage, new int[] {Integer.parseInt(c[2]), Integer.parseInt(c[3], 16),
                            Integer.parseInt(c[4], 16)});
                    case "fghi" -> list.add(new Fghi());
                    case "fill" -> list.add(new Fill(Layer.valueOf(c[2]), Integer.parseInt(c[3])));
                    case "deform" -> list.add(new Deform(Integer.parseInt(c[2]), ints(c[3], 10)));
                    case "persp" -> list.add(new Persp(Integer.parseInt(c[2], 16), Integer.parseInt(c[3]),
                            Integer.parseInt(c[4]), Integer.parseInt(c[5]),
                            c[6].equals("-") ? null : ints(c[6], 10), c[7].equals("back")));
                    case "water" -> {
                        String[] f = c[7].split(":");
                        waters.put(stage, new Water(Integer.parseInt(c[2], 16), Integer.parseInt(c[3], 16),
                                Integer.parseInt(c[4], 16), Integer.parseInt(c[5], 16), ints(c[6], 16),
                                Integer.parseInt(f[0], 16), Integer.parseInt(f[1], 16), f[2].equals("doubled"),
                                f[3].equals("bmi")));
                    }
                    default -> throw new IllegalStateException(RESOURCE + ": unknown row kind " + c[1]);
                }
            }
            for (CdStagePayload stage : rates.keySet()) {
                drafts.put(stage, new Draft(rates.get(stage), curves.get(stage), blocks.get(stage), waters.get(stage)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<CdStagePayload, Profile> out = new EnumMap<>(CdStagePayload.class);
        drafts.forEach((stage, d) -> out.put(stage, new Profile(d.rates(), d.curve()[0], d.curve()[1], d.curve()[2],
                List.copyOf(d.blocks()), d.water())));
        return Map.copyOf(out);
    }
}
