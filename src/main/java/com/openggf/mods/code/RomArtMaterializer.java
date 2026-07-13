package com.openggf.mods.code;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.io.ModInputLimits;
import com.openggf.util.DplcStaticFlattener;
import com.openggf.util.PatternDecompressor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Materializes staged {@link RomArtRequest}s into {@link ObjectSpriteSheet}s from the user's
 * Sonic 2 ROM at gameplay launch. Registration ({@link ModContext#registerRomObjectArt}) only
 * validates static address bounds with no ROM open; this class does the real decompression,
 * mapping-frame parsing, and optional DPLC flattening once a {@link Rom} is available.
 *
 * <p>ROM-derived bytes stay in memory only. Every failure — decompression errors, malformed
 * mapping/DPLC data, or a request exceeding {@link ModInputLimits} sheet caps — is wrapped as
 * an owner-attributed {@link ModRegistrationException} with finding code
 * {@code MOD_ROM_ART_INVALID}, naming the offending key and ROM art address; thrown during
 * patch apply, it aborts the launch through the existing creator-apply fault path.
 *
 * <p>Engine-internal: not {@code @ModApi} and must never appear in the published mod API
 * surface.
 */
final class RomArtMaterializer {

    private static final String FINDING_CODE = "MOD_ROM_ART_INVALID";

    private RomArtMaterializer() {}

    static Map<String, ObjectSpriteSheet> materialize(String owner,
            Map<String, RomArtRequest> requests, Rom rom, ModInputLimits limits) {
        Map<String, ObjectSpriteSheet> out = new LinkedHashMap<>();
        for (Map.Entry<String, RomArtRequest> entry : requests.entrySet()) {
            out.put(entry.getKey(),
                    materializeOne(owner, entry.getKey(), entry.getValue(), rom, limits));
        }
        return out;
    }

    private static ObjectSpriteSheet materializeOne(String owner, String key,
            RomArtRequest request, Rom rom, ModInputLimits limits) {
        try {
            RomByteReader reader = RomByteReader.fromRom(rom);

            Pattern[] patterns = switch (request.compression()) {
                case NEMESIS -> {
                    synchronized (rom) {
                        yield PatternDecompressor.nemesis(rom, request.artAddress());
                    }
                }
                case KOSINSKI -> {
                    synchronized (rom) {
                        yield PatternDecompressor.kosinski(rom, request.artAddress());
                    }
                }
                case UNCOMPRESSED -> PatternDecompressor.uncompressed(
                        reader, request.artAddress(), request.uncompressedByteSize());
            };

            if (patterns.length == 0) {
                throw invalid(owner, key, request, "art decompressed to zero patterns", null);
            }
            if (patterns.length > limits.maxSheetPatterns()) {
                throw invalid(owner, key, request,
                        "pattern count " + patterns.length + " exceeds limit "
                                + limits.maxSheetPatterns(), null);
            }

            var mappings = S2SpriteDataLoader.loadMappingFrames(reader, request.mappingAddress());
            if (request.hasDplc()) {
                var dplcFrames = Sonic2PlayerArt.parseDplcFrames(reader, request.dplcAddress());
                mappings = DplcStaticFlattener.applyDplcRemap(mappings, dplcFrames);
            }

            if (mappings.size() > limits.maxSheetFrames()) {
                throw invalid(owner, key, request,
                        "frame count " + mappings.size() + " exceeds limit "
                                + limits.maxSheetFrames(), null);
            }

            int pieces = mappings.stream().mapToInt(frame -> frame.pieces().size()).sum();
            if (pieces > limits.maxSheetPieces()) {
                throw invalid(owner, key, request,
                        "piece count " + pieces + " exceeds limit " + limits.maxSheetPieces(),
                        null);
            }

            return new ObjectSpriteSheet(patterns, mappings, request.paletteLine(),
                    request.bankSize());
        } catch (ModRegistrationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid(owner, key, request, "materialization failed: " + e.getMessage(), e);
        }
    }

    private static ModRegistrationException invalid(String owner, String key,
            RomArtRequest request, String detail, Throwable cause) {
        return new ModRegistrationException(owner, FINDING_CODE,
                "ROM art '" + key + "' at 0x" + Integer.toHexString(request.artAddress())
                        + ": " + detail,
                key, cause);
    }
}
