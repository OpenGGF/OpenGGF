package com.openggf.data;


import java.util.List;
import java.util.Map;

/**
 * Pure, table-driven mapping from a {@link PhysicalImage} to the logical ROMs
 * it contains and where. Games never ask for a filename or a game name here;
 * they ask for a {@link RomIdentity}.
 *
 * <p>One row per supported image layout, keyed by exact size and the header
 * titles at {@code 0} and {@code 0x200000}. The composite table lists the
 * logical ROMs that can also be assembled from several images when no single
 * image contains them.
 */
public final class RomImageClassifier {

    /** Where a logical ROM lives inside an image. */
    public record LogicalWindow(RomIdentity rom, int offset, int length) {
        public int end() {
            return offset + length;
        }
    }

    /** One supported image layout. */
    public record Layout(String label, long size, RomHeaderName headerAt0, RomHeaderName headerAtLockOn,
            List<LogicalWindow> windows) {
        boolean matches(PhysicalImage image) {
            return image.size() == size
                    && image.headerAt0() == headerAt0
                    && image.headerAtLockOn() == headerAtLockOn;
        }
    }

    private static final int S1_SIZE = 0x80000;
    private static final int S2_SIZE = 0x100000;
    private static final int S3_SIZE = 0x200000;
    private static final int SK_SIZE = 0x200000;
    private static final int LOCK_ON = PhysicalImage.LOCK_ON_HEADER_OFFSET;
    private static final int KIS2_CHIP_OFFSET = 0x300000;
    private static final int KIS2_CHIP_SIZE = 0x40000;

    /** Supported layouts, one per row of the design table. */
    public static final List<Layout> LAYOUTS = List.of(
            new Layout("Sonic 1", S1_SIZE, RomHeaderName.S1, RomHeaderName.UNKNOWN, List.of(
                    new LogicalWindow(RomIdentity.S1, 0, S1_SIZE))),
            new Layout("Sonic 2", S2_SIZE, RomHeaderName.S2, RomHeaderName.UNKNOWN, List.of(
                    new LogicalWindow(RomIdentity.S2, 0, S2_SIZE))),
            new Layout("Sonic 3", S3_SIZE, RomHeaderName.S3, RomHeaderName.UNKNOWN, List.of(
                    new LogicalWindow(RomIdentity.S3, 0, S3_SIZE))),
            new Layout("Sonic & Knuckles", SK_SIZE, RomHeaderName.SK, RomHeaderName.UNKNOWN, List.of(
                    new LogicalWindow(RomIdentity.SK, 0, SK_SIZE))),
            new Layout("Sonic 3 & Knuckles lock-on", SK_SIZE + S3_SIZE, RomHeaderName.SK, RomHeaderName.S3, List.of(
                    new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                    new LogicalWindow(RomIdentity.S3, LOCK_ON, S3_SIZE),
                    new LogicalWindow(RomIdentity.S3K, 0, SK_SIZE + S3_SIZE))),
            new Layout("Sonic & Knuckles + Sonic 1 lock-on", SK_SIZE + S1_SIZE, RomHeaderName.SK, RomHeaderName.S1, List.of(
                    new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                    new LogicalWindow(RomIdentity.S1, LOCK_ON, S1_SIZE))),
            new Layout("Sonic & Knuckles + Sonic 2 lock-on", SK_SIZE + S2_SIZE + KIS2_CHIP_SIZE,
                    RomHeaderName.SK, RomHeaderName.S2, List.of(
                    new LogicalWindow(RomIdentity.SK, 0, SK_SIZE),
                    new LogicalWindow(RomIdentity.S2, LOCK_ON, S2_SIZE),
                    new LogicalWindow(RomIdentity.KIS2_CHIP, KIS2_CHIP_OFFSET, KIS2_CHIP_SIZE),
                    new LogicalWindow(RomIdentity.KIS2, 0, SK_SIZE + S2_SIZE + KIS2_CHIP_SIZE))));

    /**
     * Logical ROMs that can be assembled by concatenating other logical ROMs,
     * in address order. A composite exists only when every part resolves.
     */
    public static final Map<RomIdentity, List<RomIdentity>> COMPOSITES = Map.of(
            RomIdentity.S3K, List.of(RomIdentity.SK, RomIdentity.S3),
            RomIdentity.KIS2, List.of(RomIdentity.SK, RomIdentity.S2, RomIdentity.KIS2_CHIP));

    private RomImageClassifier() {
    }

    /** Returns the matching layout, if the image is one the table recognises. */
    public static java.util.Optional<Layout> layoutOf(PhysicalImage image) {
        return LAYOUTS.stream().filter(layout -> layout.matches(image)).findFirst();
    }

    /** Returns every logical ROM the image contains, or an empty list for an unknown image. */
    public static List<LogicalWindow> classify(PhysicalImage image) {
        return layoutOf(image).map(Layout::windows).orElse(List.of());
    }

    /** Returns the window of {@code rom} inside the image, if present. */
    public static java.util.Optional<LogicalWindow> windowOf(PhysicalImage image, RomIdentity rom) {
        return classify(image).stream().filter(window -> window.rom() == rom).findFirst();
    }
}
