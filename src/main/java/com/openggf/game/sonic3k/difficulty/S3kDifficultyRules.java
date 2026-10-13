package com.openggf.game.sonic3k.difficulty;

import java.util.ArrayDeque;
import java.util.Objects;

/** Engine admission rules for bounded native difficulty content, never gameplay authority. */
public final class S3kDifficultyRules {
    private static final int GRID_BYTES = 0x400;
    private static final int LAYOUT_BYTES = 0x408;
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int BUMPER = 3;
    private static final int RING = 4;
    private static final int SPRING = 5;

    private S3kDifficultyRules() { }

    public static int followupDelayTicks(int candidate) {
        if (candidate != -1 && (candidate < 16 || candidate > 60)) {
            throw new IllegalArgumentException("Boss follow-up requires -1 or 16..60 warning updates");
        }
        return candidate;
    }

    public static byte[] blueSphereLayout(byte[] trustedOriginal, byte[] candidate) {
        Objects.requireNonNull(trustedOriginal, "trustedOriginal");
        Objects.requireNonNull(candidate, "candidate");
        if (trustedOriginal.length != LAYOUT_BYTES || candidate.length != LAYOUT_BYTES) {
            throw new IllegalArgumentException("Blue Sphere layout must contain 1024 cells and four trailing words");
        }
        byte[] admitted = candidate.clone();
        for (int i = GRID_BYTES; i < LAYOUT_BYTES; i++) {
            if (trustedOriginal[i] != admitted[i]) {
                throw new IllegalArgumentException("Blue Sphere policy must preserve entry parameters and perfect ring target");
            }
        }
        int edits = 0;
        for (int i = 0; i < GRID_BYTES; i++) {
            int originalType = trustedOriginal[i] & 0xFF;
            int candidateType = admitted[i] & 0xFF;
            if (originalType > SPRING) {
                throw new IllegalArgumentException("Decoded ROM layout contains a non-entry cell type");
            }
            if (originalType != candidateType) {
                if (originalType != EMPTY || (candidateType != RED && candidateType != BUMPER)) {
                    throw new IllegalArgumentException("Blue Sphere policy may only turn native empty cells into red spheres or bumpers");
                }
                if (++edits > 32) {
                    throw new IllegalArgumentException("Blue Sphere policy exceeds 32 changed approach cells");
                }
            }
        }

        int angle = word(trustedOriginal, GRID_BYTES);
        int startX = word(trustedOriginal, GRID_BYTES + 2);
        int startY = word(trustedOriginal, GRID_BYTES + 4);
        if ((angle & 0x3FFF) != 0 || (startX & 0xE0FF) != 0 || (startY & 0xE0FF) != 0) {
            throw new IllegalArgumentException("Decoded ROM layout requires a cardinal, cell-aligned native start");
        }
        int direction = angle >> 8;
        int dx = direction == 0x40 ? -1 : direction == 0xC0 ? 1 : 0;
        int dy = direction == 0 ? -1 : direction == 0x80 ? 1 : 0;
        for (int step = 0; step <= 4; step++) {
            int cell = index((startX >> 8) + step * dx, (startY >> 8) + step * dy);
            if (trustedOriginal[cell] != admitted[cell]) {
                throw new IllegalArgumentException("Blue Sphere policy must preserve the native opening runway");
            }
        }

        // Some shipped boards intentionally need bumper/jump passages. Preserve
        // every originally cardinal-reachable blue/ring, rather than pretending
        // a graph walk certifies those native passages or an entire stage clear.
        boolean[] before = reachable(trustedOriginal, startX >> 8, startY >> 8);
        boolean[] after = reachable(admitted, startX >> 8, startY >> 8);
        for (int i = 0; i < GRID_BYTES; i++) {
            int type = trustedOriginal[i] & 0xFF;
            if ((type == BLUE || type == RING) && before[i] && !after[i]) {
                throw new IllegalArgumentException("Blue Sphere policy closes an originally reachable native collectible route");
            }
        }
        return admitted;
    }

    public static int[] gumballSpringOffsets(int[] candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.length < 2 || candidate.length > 4) {
            throw new IllegalArgumentException("Gumball challenge requires two to four native recovery springs");
        }
        int[] admitted = candidate.clone();
        for (int i = 0; i < admitted.length; i++) {
            if (admitted[i] < -48 || admitted[i] > 48
                    || (i > 0 && admitted[i] - admitted[i - 1] < 16)
                    || admitted[i] != -admitted[admitted.length - 1 - i]) {
                throw new IllegalArgumentException("Gumball offsets must be increasing, separated, symmetric, and within native clearance");
            }
        }
        return admitted;
    }

    private static boolean[] reachable(byte[] layout, int startX, int startY) {
        boolean[] visited = new boolean[GRID_BYTES];
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        int start = index(startX, startY);
        visited[start] = true;
        pending.add(start);
        while (!pending.isEmpty()) {
            int cell = pending.removeFirst();
            int x = cell & 31;
            int y = cell >> 5;
            for (int neighbor : new int[] {index(x - 1, y), index(x + 1, y),
                    index(x, y - 1), index(x, y + 1)}) {
                int type = layout[neighbor] & 0xFF;
                if (!visited[neighbor] && type != RED && type != BUMPER) {
                    visited[neighbor] = true;
                    pending.addLast(neighbor);
                }
            }
        }
        return visited;
    }

    private static int index(int x, int y) {
        return ((y & 31) << 5) | (x & 31);
    }

    private static int word(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
