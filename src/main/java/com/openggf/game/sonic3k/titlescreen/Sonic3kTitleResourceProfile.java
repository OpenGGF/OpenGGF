package com.openggf.game.sonic3k.titlescreen;

import com.openggf.data.Rom;
import com.openggf.game.sonic3k.Sonic3kCartridgePolicy;
import java.util.Map;

/** Explicit per-asset title addresses, verified in original Sonic 3 and the locked-on image. */
record Sonic3kTitleResourceProfile(boolean standaloneS3) {
    static final Sonic3kTitleResourceProfile LOCKED_ON = new Sonic3kTitleResourceProfile(false);
    // Compressed art/maps are shared byte-for-byte; code-section palettes/menu
    // have independent addresses. This is deliberately not a global ROM rebase.
    private static final Map<Integer, Integer> S3 = Map.ofEntries(
            Map.entry(0x350D26, 0x150D26), Map.entry(0x351C86, 0x151C86),
            Map.entry(0x3542E6, 0x1542E6), Map.entry(0x3565E6, 0x1565E6),
            Map.entry(0x357AC6, 0x157AC6), Map.entry(0x358DE6, 0x158DE6),
            Map.entry(0x359FC6, 0x159FC6), Map.entry(0x35026C, 0x15026C),
            Map.entry(0x2C49CC, 0x0C49CC), Map.entry(0x4D2A, 0x4362),
            Map.entry(0x34F6A0, 0x14F6A0), Map.entry(0x34F75C, 0x14F75C),
            Map.entry(0x34F81E, 0x14F81E), Map.entry(0x34F8E2, 0x14F8E2),
            Map.entry(0x34F9A6, 0x14F9A6), Map.entry(0x34FA6A, 0x14FA6A),
            Map.entry(0x34FB30, 0x14FB30), Map.entry(0x34FC2E, 0x14FC2E),
            Map.entry(0x34FD18, 0x14FD18), Map.entry(0x34FDE6, 0x14FDE6),
            Map.entry(0x34FEAA, 0x14FEAA), Map.entry(0x34FF48, 0x14FF48),
            Map.entry(0x350018, 0x150018), Map.entry(0x350112, 0x150112),
            Map.entry(0x459C, 0x3C78), Map.entry(0x460C, 0x3CE8),
            Map.entry(0x47AC, 0x3E88), Map.entry(0x4904, 0x3FD4));

    static Sonic3kTitleResourceProfile fromRom(Rom rom) {
        return new Sonic3kTitleResourceProfile(Sonic3kCartridgePolicy.isStandaloneS3(rom));
    }

    int address(int lockedOnAddress) {
        if (!standaloneS3) return lockedOnAddress;
        Integer mapped = S3.get(lockedOnAddress);
        if (mapped == null) throw new IllegalArgumentException("No original-S3 title asset at "
                + Integer.toHexString(lockedOnAddress));
        return mapped;
    }

    boolean hasAndKnuckles() { return !standaloneS3; }

    int musicId(int nativeByte) {
        return (standaloneS3
                ? com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.SONIC_3
                : com.openggf.game.sonic3k.audio.Sonic3kAudioResourceProfile.LOCKED_ON).musicId(nativeByte);
    }
}
