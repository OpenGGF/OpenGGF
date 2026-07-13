package com.openggf.tools.modsdk;

import com.openggf.io.ModInputLimits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact optional GSHG/GSWD/GSAN reader and deterministic two-profile default. */
final class TmxSolidProfileReader {
    private final ModInputLimits limits;

    TmxSolidProfileReader(ModInputLimits limits) { this.limits = limits; }

    TmxLevelImporter.Profiles read(Path directory) throws IOException {
        if (directory == null) return defaults();
        Path root = directory.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
            throw new IOException("Solid profile input must be a non-symlink directory");
        }
        Set<String> expected = Set.of("solid-heights.bin", "solid-widths.bin", "solid-angles.bin");
        try (var files = Files.list(root)) {
            if (!files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()).equals(expected)) {
                throw new IOException("Solid profile directory must contain exactly GSHG/GSWD/GSAN files");
            }
        }
        Profile heights = profile(root.resolve("solid-heights.bin"), "GSHG", 16);
        Profile widths = profile(root.resolve("solid-widths.bin"), "GSWD", 16);
        Profile angles = profile(root.resolve("solid-angles.bin"), "GSAN", 1);
        if (heights.count != widths.count || heights.count != angles.count) throw new IOException("Solid profile counts must match");
        return new TmxLevelImporter.Profiles(heights.bytes, widths.bytes, angles.bytes, heights.count);
    }

    private Profile profile(Path path, String magic, int size) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid profile file");
        long length = Files.size(path);
        if (length > limits.maxAssetBytes() || length > 12L + 256L * size) {
            throw new IOException("Profile file exceeds exact format/asset limit");
        }
        byte[] bytes = Files.readAllBytes(path);
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (!magic.equals(new String(input.readNBytes(4), java.nio.charset.StandardCharsets.US_ASCII))
                    || input.readUnsignedShort() != 1 || input.readUnsignedShort() != size) throw new IOException("Invalid " + magic + " profile");
            int count = input.readInt();
            if (count < 1 || count > 256 || bytes.length != 12 + count * size) throw new IOException("Invalid " + magic + " profile count/length");
            return new Profile(bytes, count);
        }
    }

    private static TmxLevelImporter.Profiles defaults() throws IOException {
        return new TmxLevelImporter.Profiles(defaultFile("GSHG", 16, true),
                defaultFile("GSWD", 16, true), defaultFile("GSAN", 1, false), 2);
    }

    private static byte[] defaultFile(String magic, int size, boolean full) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes(magic); output.writeShort(1); output.writeShort(size); output.writeInt(2);
            output.write(new byte[size]); byte[] second = new byte[size]; if (full) Arrays.fill(second, (byte) 16); output.write(second);
        }
        return bytes.toByteArray();
    }

    private record Profile(byte[] bytes, int count) { }
}
