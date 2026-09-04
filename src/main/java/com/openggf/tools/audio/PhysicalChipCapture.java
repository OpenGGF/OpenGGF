package com.openggf.tools.audio;

import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.audio.synth.PsgChip;
import com.openggf.audio.synth.Ym2612Chip;
import com.openggf.version.BuildIdentity;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded, diagnostic-only collection of dispatched chip bus events. The two
 * clock columns intentionally remain in their chips' native domains: a
 * consumer must not numerically order YM cycles against PSG generator ticks.
 */
final class PhysicalChipCapture implements ChipWriteObserver {
    private static final int INITIAL_CAPACITY = 4_096;

    private sealed interface Event permits Ym, Psg, Boundary {
        long ordinal();

        String toJson();
    }

    private record Ym(long ordinal, long cycle, int busPort, int value,
            PhysicalWriteOrigin origin) implements Event {
        @Override
        public String toJson() {
            return String.format(Locale.ROOT,
                    "{\"type\":\"ym\",\"ordinal\":%d,\"cycle\":%d,\"bus_port\":%d,\"value\":%d,\"origin\":\"%s\"}",
                    ordinal, cycle, busPort, value, origin);
        }
    }

    private record Psg(long ordinal, long tick, int value) implements Event {
        @Override
        public String toJson() {
            return String.format(Locale.ROOT,
                    "{\"type\":\"psg\",\"ordinal\":%d,\"tick\":%d,\"value\":%d}",
                    ordinal, tick, value);
        }
    }

    private record Boundary(long ordinal, ChipClockDomain domain, long clock,
            PhysicalTimelineBoundary boundary) implements Event {
        @Override
        public String toJson() {
            return String.format(Locale.ROOT,
                    "{\"type\":\"boundary\",\"ordinal\":%d,\"domain\":\"%s\",\"clock\":%d,\"boundary\":\"%s\"}",
                    ordinal, domain, clock, boundary);
        }
    }

    private final int capacity;
    private final List<Event> events;
    private long nextOrdinal;
    private long dropped;

    PhysicalChipCapture(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("physical capture capacity must be positive");
        }
        this.capacity = capacity;
        events = new ArrayList<>(Math.min(capacity, INITIAL_CAPACITY));
    }

    @Override
    public void onYm2612Write(int port, int register, int value) {
        // The older frame-stamped diagnostic has its own collector.
    }

    @Override
    public void onPsgWrite(int value) {
        // The older frame-stamped diagnostic has its own collector.
    }

    @Override
    public boolean observesPhysicalWrites() {
        return true;
    }

    @Override
    public void onYm2612BusWrite(long cycle, int busPort, int value,
            PhysicalWriteOrigin origin) {
        add(new Ym(nextOrdinal++, cycle, busPort, value, origin));
    }

    @Override
    public void onPsgBusWrite(long tick, int value) {
        add(new Psg(nextOrdinal++, tick, value));
    }

    @Override
    public void onPhysicalTimelineBoundary(ChipClockDomain domain, long clock,
            PhysicalTimelineBoundary boundary) {
        add(new Boundary(nextOrdinal++, domain, clock, boundary));
    }

    boolean overflowed() {
        return dropped != 0;
    }

    long dropped() {
        return dropped;
    }

    int size() {
        return events.size();
    }

    void write(Path path, String game, String kind, int id,
            double outputRate, String romPath, String romSha1,
            BuildIdentity build) throws IOException {
        try (BufferedWriter output = Files.newBufferedWriter(path)) {
            output.write(String.format(Locale.ROOT,
                    "{\"type\":\"header\",\"format\":\"openggf-physical-chip-bus-v1\",\"engine\":\"openggf\",\"engine_version\":\"%s\",\"engine_commit\":\"%s\",\"engine_dirty\":%s,\"rom_path\":\"%s\",\"rom_sha1\":\"%s\",\"initial_state\":\"constructor_reset\",\"ym_core\":\"nuked-opn2\",\"ym_chip_type\":\"YM2612\",\"output_sample_rate\":%.6f,\"game\":\"%s\",\"kind\":\"%s\",\"id\":%d,\"ym_domain\":\"YM2612_INTERNAL_CYCLE\",\"ym_ticks_per_second\":%.6f,\"psg_domain\":\"PSG_GENERATOR_TICK\",\"psg_ticks_per_second\":%.6f,\"capture_capacity\":%d,\"events\":%d,\"overflow\":%s,\"dropped\":%d}",
                    escape(build.baseVersion()), escape(build.commit()), build.dirty(),
                    escape(romPath), escape(romSha1), outputRate,
                    escape(game), escape(kind), id,
                    Ym2612Chip.getInternalRate() * 24.0, PsgChip.TICK_RATE_HZ,
                    capacity, events.size(),
                    overflowed(), dropped));
            output.newLine();
            for (Event event : events) {
                output.write(event.toJson());
                output.newLine();
            }
        }
    }

    private void add(Event event) {
        if (events.size() < capacity) {
            events.add(event);
        } else {
            dropped++;
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
