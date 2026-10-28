package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdNemesisAsset;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Finite immutable USA WARP views, loaded only from the retained authenticated original programme.
 * Native pin1ecc5f3a, Time Warp Cutscene/Main.asm:125-161,203-244,939-954 and the Sonic/Sparkle
 * mapping sources. WARP's own mapping format retains LONG pointers, frame counts and every stored
 * leaf; it is not the level's mapping format. The fifth Sparkle leaf is data outside its count4.
 * No mutable current-art bank, decoder, PLC, scene clock or programme execution owner is created.
 */
public final class CdWarpAssets {
    public static final int SONIC_MAPPING_ADDRESS = 0xFF2482;
    public static final int SPARKLE_MAPPING_ADDRESS = 0xFF2576;
    private static final String WARP_SHA256 = "a417c910f61e0d95b33f292b3e84206e1f71018c4478c83da86737b10d0ffe06";
    private final CdTimeWarpPrograms.Source source;
    private final CdAssetBytes art;
    private final CdAssetBytes backgroundPlane;
    private final CdAssetBytes palette;
    private final CdAssetBytes sonicMappings;
    private final CdAssetBytes sparkleMappings;
    private final CdAssetBytes cycleColours;

    private CdWarpAssets(CdTimeWarpPrograms.Source source, CdAssetBytes art, CdAssetBytes backgroundPlane,
                         CdAssetBytes palette, CdAssetBytes sonicMappings, CdAssetBytes sparkleMappings,
                         CdAssetBytes cycleColours) {
        this.source = source; this.art = art; this.backgroundPlane = backgroundPlane; this.palette = palette;
        this.sonicMappings = sonicMappings; this.sparkleMappings = sparkleMappings; this.cycleColours = cycleColours;
    }

    /** Detach finite source views once; missing media or unread relocation bytes never become defaults. */
    public static CdWarpAssets fromPrograms(CdTimeWarpPrograms programs) throws IOException {
        Objects.requireNonNull(programs, "authenticated retained programme bundle");
        var source = Objects.requireNonNull(programs.source(), "actual programme source");
        if (source.variant() != CdReleaseVariant.USA || !WARP_SHA256.equals(source.warpSha256()))
            throw new IOException("Finite WARP assets require the authenticated USA programme source");
        var warp = Objects.requireNonNull(programs.warp(), "actual detached WARP image");
        if (warp.fileBytes() != 7242 || !WARP_SHA256.equals(sha256(warp.readFile(0, warp.fileBytes()))))
            throw new IOException("Retained WARP content differs from its authenticated original");
        // The existing Java reader consumes exactly1536 genuine bytes. Native's separate1537-byte
        // refill view remains distinct and is already covered by the programme-reader regression.
        var decoded = CdNemesisAsset.decode(warp.readFile(0x748, 1536), 3232, 3232);
        if (decoded.consumedInputBytes() != 1536)
            throw new IOException("WARP art reader no longer matches its finite reviewed input extent");
        byte[] plane = new byte[64 * 32 * 2]; // InitMD clears the plane before both DrawTilemapH64 calls.
        byte[] mappings = warp.readFile(0xD48, 2240);
        for (int row = 0; row < 32; row++) {
            int sourceRow = row < 28 ? row : row - 28;
            System.arraycopy(mappings, sourceRow * 40 * 2, plane, row * 64 * 2, 40 * 2);
        }
        byte[] palette = new byte[128]; // Start's complete variable clear precedes its64-byte palette copy.
        System.arraycopy(warp.readFile(0x708, 64), 0, palette, 0, 64);
        return new CdWarpAssets(source, decoded.bytes(), CdAssetBytes.copyOf(plane), CdAssetBytes.copyOf(palette),
                CdAssetBytes.copyOf(warp.readFile(0x582, 116)), CdAssetBytes.copyOf(warp.readFile(0x676, 126)),
                CdAssetBytes.copyOf(warp.readFile(0x238, 56)));
    }

    public CdTimeWarpPrograms.Source source() { return source; }
    public CdAssetBytes art() { return art; }
    public CdAssetBytes backgroundPlane() { return backgroundPlane; }
    public CdAssetBytes palette() { return palette; }
    public CdAssetBytes sonicMappings() { return sonicMappings; }
    public CdAssetBytes sparkleMappings() { return sparkleMappings; }
    public CdAssetBytes cycleColours() { return cycleColours; }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError("Required SHA-256 unavailable", impossible); }
    }
}
