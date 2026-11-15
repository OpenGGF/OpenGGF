package com.openggf.game.soniccd.release;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdObservedMediaIdentity;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact split-raw content profiles, with no stock admission or execution-environment authority. */
public final class CdRetailReleases {
    /** Primary Redump MCD export, 2026-10-23 10-16-53. Reproduce with tools/soniccd/retail_profiles.py. */
    public static final String METADATA_DAT_SHA256 =
            "6910bd275ab99049147bb43367f9dfa149e164bf1dd0486acb96523e740f4fea";

    public static final CdRetailRelease USA = new CdRetailRelease("redump-29188", CdReleaseVariant.USA,
            "a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b",
            URI.create("https://redump.info/disc/29188"));
    public static final CdRetailRelease EUROPE = new CdRetailRelease("redump-8302", CdReleaseVariant.EUROPE,
            "91fed9c846a688e8d8e8a130923ef8a7af6bcc130d19c6f179aaf69a2e9cf168",
            URI.create("https://redump.info/disc/8302"));
    public static final CdRetailRelease JAPAN = new CdRetailRelease("redump-1632", CdReleaseVariant.JAPAN,
            "3b82f0701dcc225158020225eba56687df03467ad97435e2cb6a5a2734238109",
            URI.create("https://redump.info/disc/1632"));
    private static final List<CdRetailRelease> KNOWN = List.of(USA, EUROPE, JAPAN);

    private CdRetailReleases() { }

    /**
     * Match an internal observation captured from user media. Record construction
     * alone cannot authenticate bytes. Callers at a media boundary must use
     * {@link CdObservedMediaIdentity#capture} rather than accept creator-reported hashes.
     * Rechecking the descriptor digest rejects stale or inconsistent observations.
     */
    public static Optional<CdRetailRelease> find(CdObservedMediaIdentity actual) {
        Objects.requireNonNull(actual);
        if (!CdObservedMediaIdentity.DEFINITION.equals(actual.definition()) || actual.tracks().size() != 35) {
            return Optional.empty();
        }
        Optional<CdRetailRelease> known = KNOWN.stream()
                .filter(release -> release.normalizedSha256().equals(actual.normalizedSha256())).findFirst();
        return known.filter(release -> descriptorsAgree(actual));
    }

    /** Reject a forced known key unless the observed whole-disc content matches it. */
    public static CdRetailRelease requireRequested(CdObservedMediaIdentity actual, String key) {
        Objects.requireNonNull(key);
        CdRetailRelease requested = KNOWN.stream().filter(release -> release.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Sonic CD retail content key " + key));
        CdRetailRelease matched = find(actual)
                .orElseThrow(() -> new IllegalArgumentException("Media does not match a known Sonic CD retail content record"));
        if (!matched.equals(requested)) {
            throw new IllegalArgumentException("Requested " + key + " but media matches " + matched.key());
        }
        return matched;
    }

    private static boolean descriptorsAgree(CdObservedMediaIdentity actual) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeUTF(actual.definition());
                output.writeInt(actual.tracks().size());
                for (var track : actual.tracks()) {
                    if (track == null || track.mode() == null || track.storedFramesSha256() == null
                            || !track.storedFramesSha256().matches("[0-9a-f]{64}")) {
                        return false;
                    }
                    output.writeInt(track.number());
                    output.writeUTF(track.mode().name());
                    output.writeLong(track.discIndex01Frame());
                    output.writeLong(track.programFrames());
                    output.writeLong(track.storedPregapFrames());
                    output.writeLong(track.syntheticPregapFrames());
                    output.write(HexFormat.of().parseHex(track.storedFramesSha256()));
                }
            }
            return HexFormat.of().formatHex(digest.digest()).equals(actual.normalizedSha256());
        } catch (NoSuchAlgorithmException | IOException exception) {
            throw new IllegalStateException("Cannot hash Sonic CD track descriptors", exception);
        }
    }
}
