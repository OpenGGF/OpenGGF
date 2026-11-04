package com.openggf.game.internal;

import com.openggf.data.RomByteReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Frozen, independently selected cartridge inputs of one native initialization operation. */
public final class NativeLevelLoadInputs {
    private static final Set<String> PHASES = Set.of(
            "display_clear", "level_data_decode", "level_setup", "initial_placement", "initial_objects", "initial_presentation");
    private final byte[] romDigest;
    private final List<FrozenRegion> regions;
    private final List<Integer> recipeChoices;

    public record RegionSelection(int address, int length) {
        public RegionSelection {
            if (address < 0 || length <= 0) throw new IllegalArgumentException("Invalid ROM region");
        }
    }

    private record FrozenRegion(int address, byte[] bytes) { }

    private NativeLevelLoadInputs(byte[] romDigest, List<FrozenRegion> regions, List<Integer> recipeChoices) {
        this.romDigest = romDigest.clone();
        this.regions = List.copyOf(regions);
        this.recipeChoices = List.copyOf(recipeChoices);
    }

    public static NativeLevelLoadInputs fromRom(RomByteReader rom,
            List<RegionSelection> regions, List<Integer> recipeChoices) {
        Objects.requireNonNull(rom, "rom");
        return new NativeLevelLoadInputs(sha256(rom.slice(0, rom.size())),
                regions.stream().map(region -> new FrozenRegion(region.address(),
                        rom.slice(region.address(), region.length()))).toList(), recipeChoices);
    }

    /** Canonical encoding shared with TraceChaser; contains no runtime clock or imported gameplay state. */
    public String fingerprint(String phase) {
        if (!PHASES.contains(phase)) throw new IllegalArgumentException("Invalid initialization phase");
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            writeString(output, "INITIALIZATION_PHASE_V1");
            writeString(output, phase);
            output.write(romDigest);
            output.writeInt(regions.size());
            for (FrozenRegion region : regions) {
                output.writeInt(region.address());
                output.writeInt(region.bytes().length);
                output.write(region.bytes());
            }
            output.writeInt(recipeChoices.size());
            for (int choice : recipeChoices) output.writeInt(choice);
            return "sha256:" + HexFormat.of().formatHex(sha256(bytes.toByteArray()));
        } catch (IOException impossibleForMemoryOutput) {
            throw new IllegalStateException(impossibleForMemoryOutput);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException missingRequiredAlgorithm) {
            throw new AssertionError(missingRequiredAlgorithm);
        }
    }
}
