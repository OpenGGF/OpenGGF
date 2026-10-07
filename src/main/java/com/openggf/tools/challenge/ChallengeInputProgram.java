package com.openggf.tools.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Small maintained held-pad program; it offers input, never gameplay state. */
public final class ChallengeInputProgram {
    public record Segment(int ticks, int held) {}
    private final List<Segment> segments;
    private final int length;
    private ChallengeInputProgram(List<Segment> segments) {
        this.segments = List.copyOf(segments);
        length = segments.stream().mapToInt(Segment::ticks).sum();
    }
    public static ChallengeInputProgram read(Path path) throws IOException {
        List<Segment> segments = new ArrayList<>();
        int total = 0;
        for (String line : Files.readAllLines(path)) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#"))
                continue;
            String[] fields = line.split("\\s+");
            try {
                if (fields.length != 2)
                    throw new IllegalArgumentException();
                int ticks = Integer.parseInt(fields[0]);
                if (ticks < 1 || ticks > 36000)
                    throw new IllegalArgumentException();
                int held = 0;
                if (!fields[1].equals("NEUTRAL"))
                    for (String button : fields[1].split("\\+"))
                        held |= switch (button) {
                            case "UP" -> 1;
                            case "DOWN" -> 2;
                            case "LEFT" -> 4;
                            case "RIGHT" -> 8;
                            case "B" -> 16;
                            case "C" -> 32;
                            case "A" -> 64;
                            case "START" -> 128;
                            default -> throw new IllegalArgumentException();
                        };
                total = Math.addExact(total, ticks);
                if (total > 36000)
                    throw new IllegalArgumentException();
                if ((held & 3) == 3 || (held & 12) == 12)
                    throw new IllegalArgumentException();
                segments.add(new Segment(ticks, held));
            } catch (RuntimeException e) {
                throw new IOException("Invalid held-input program row: " + line, e);
            }
        }
        if (segments.isEmpty())
            throw new IOException("Empty held-input program");
        return new ChallengeInputProgram(segments);
    }
    public int length() {
        return length;
    }
    public int heldAt(int tick) {
        if (tick < 0 || tick >= length)
            throw new IndexOutOfBoundsException(tick);
        for (var s : segments) {
            if (tick < s.ticks())
                return s.held();
            tick -= s.ticks();
        }
        throw new AssertionError();
    }
}
