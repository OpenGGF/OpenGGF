package com.openggf.stageplay.route;

import com.openggf.data.PhysicalImage;
import com.openggf.data.RomIdentity;
import com.openggf.data.RomImageCatalogue;
import com.openggf.data.RomManager;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import com.openggf.stageplay.runtime.StageplayRomTransport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;

/**
 * Exact content binding for a disposable route attempt; never writes ROM or project bytes to disk.
 */
public final class StageplayRouteInputs {
    private StageplayRouteInputs() {}

    public record Snapshot(
            StageplayDocument document,
            StageplayRouteRequest request,
            Path root,
            String romDescriptor,
            String assetDescriptor,
            String fingerprint) {}

    /**
     * Call off the editor thread: fresh physical readers deliberately bypass catalogue fingerprint
     * caches.
     */
    public static Snapshot capture(
            StageplayDocument document, StageplayRouteRequest request, Path root, RomManager roms)
            throws IOException {
        var selected = new LinkedHashMap<String, RomImageCatalogue.Resolution>();
        for (String game : StageplayRomTransport.requiredGames(document)) {
            RomIdentity identity =
                    switch (game) {
                        case "s1" -> RomIdentity.S1;
                        case "s2" -> RomIdentity.S2;
                        default -> RomIdentity.S3K;
                    };
            selected.put(
                    game,
                    roms.resolveLogicalRom(identity)
                            .orElseThrow(
                                    () ->
                                            new IOException(
                                                    "Route helper needs the configured logical "
                                                            + game
                                                            + " ROM")));
        }
        return capture(document, request, root, selected);
    }

    public static Snapshot capture(
            StageplayDocument document,
            StageplayRouteRequest request,
            Path root,
            java.util.Map<String, RomImageCatalogue.Resolution> selected)
            throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        var fresh = new LinkedHashMap<String, RomImageCatalogue.Resolution>();
        var images = new LinkedHashMap<Path, PhysicalImage>();
        for (var entry : selected.entrySet()) {
            var resolution = entry.getValue();
            var parts = new java.util.ArrayList<RomImageCatalogue.Part>();
            for (var part : resolution.parts()) {
                Path path = part.image().path().toAbsolutePath().normalize();
                var image = images.get(path);
                if (image == null) {
                    if (!Files.isRegularFile(path) || Files.size(path) > 64L * 1024 * 1024)
                        throw new IOException("Missing or oversized route ROM image: " + path);
                    image = PhysicalImage.probe(path);
                    images.put(path, image);
                }
                if (image.size() != part.image().size()
                        || !image.fingerprint(part.offset(), part.length())
                                .equals(part.image().fingerprint(part.offset(), part.length())))
                    throw new IOException(
                            "Configured ROM content changed; reload the ROM before planning a"
                                    + " route: "
                                    + path);
                parts.add(
                        new RomImageCatalogue.Part(
                                part.rom(), image, part.offset(), part.length()));
            }
            fresh.put(
                    entry.getKey(),
                    new RomImageCatalogue.Resolution(resolution.rom(), parts, resolution.source()));
        }
        String romDescriptor = StageplayRomTransport.encode(fresh);
        String assetDescriptor = StageplayRouteAssets.capture(document, normalized);
        return new Snapshot(
                document,
                request,
                normalized,
                romDescriptor,
                assetDescriptor,
                fingerprint(document, request, normalized, romDescriptor, assetDescriptor));
    }

    public static String fingerprint(
            StageplayDocument document,
            StageplayRouteRequest request,
            Path root,
            String romDescriptor,
            String assetDescriptor)
            throws IOException {
        try {
            var hash = MessageDigest.getInstance("SHA-256");
            hash.update(StageplayStore.encode(document));
            hash.update((byte) 0);
            hash.update(
                    StageplayRouteProtocol.encodeRequest(request).getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(
                    root.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(romDescriptor.getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(assetDescriptor.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
