package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Detached original-USA R31A programme operands and pure music selection only.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: R31A Legacy/R3 Legacy Act1 Shared95,
 * PlayLevelMusic/MusicPlayCmds (Level/Main.asm:555-584). The full58-byte consumer and28-byte
 * table were independently authenticated in original R31A media, not borrowed from equal C/D data.
 * No source selection invocation, ordinary raw-gate admission, SubCPU/playback acknowledgement,
 * audio, initialization, clock, Frame, programme-load or World authority is created here.
 */
public final class CdR31aProgrammeAssets {
    private static final int MUSIC_ADDRESS = 0x201AB0;
    private static final String CONSUMER = "70007200103900ff152e088000074a3900ff0f01660c0c0000026606d03900ff156a123900ff1506d241d241d2407000103b10084ef900205c2a";
    private static final String MUSIC_SHA = "6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932";

    /** The first actually selected byte outside this finite owned table, never a fallback command. */
    public static final class UnownedMusicByte extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final int address;
        private UnownedMusicByte(int address) {
            super("Unowned original R31 MusicPlayCmds byte at $" + Integer.toHexString(address));
            this.address = address;
        }
        public int address() { return address; }
    }

    private final CdR31aAssets assets;
    private final CdAssetBytes music;

    private CdR31aProgrammeAssets(CdR31aAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual original R31 aggregate");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R31A
                || !identity.stage().equals(CdStagePayload.R31A.stage())
                || !CdRetailReleases.USA.normalizedSha256().equals(identity.observedWholeDiscSha256())) {
            throw new IllegalArgumentException("Programme music requires its actual original-USA R31 source relation");
        }
        var original = assets.programmeMusicSource(); // original owner audits the complete image/header first
        if (!Arrays.equals(HexFormat.of().parseHex(CONSUMER), original.consumer().copy())) {
            throw new IllegalArgumentException("Original complete R31 music consumer differs");
        }
        music = original.commands();
        if (music.length() != 28 || !MUSIC_SHA.equals(CdR11aProfile.sha256(music.copy()))) {
            throw new IllegalArgumentException("Original complete R31 MusicPlayCmds table differs");
        }
    }

    public static CdR31aProgrammeAssets fromAssets(CdR31aAssets assets) { return new CdR31aProgrammeAssets(assets); }
    public boolean ownsAssets(CdR31aAssets assets) { return this.assets == assets; }
    public CdAssetBytes musicCommands() { return music; }
    public int musicCommandsAddress() { return MUSIC_ADDRESS; }

    /**
     * Explicit scalar DATA, not native read/callback authority. The actual caller reads source globals
     * in native order and owns its programme/raw0100/1 cut. Main masks period bit7, conditionally adds
     * the whole goodFuture BYTE modulo256, then uses the zone BYTE and WORD index without normalizing.
     */
    public CdCommand selectLevelMusic(int rawZoneActWord,int rawPeriodByte,int timeAttackByte,int goodFutureByte) {
        if ((rawZoneActWord & ~65535) != 0) throw new IllegalArgumentException("Native zoneAct WORD required");
        for (int value : new int[]{rawPeriodByte,timeAttackByte,goodFutureByte})
            if ((value & ~255) != 0) throw new IllegalArgumentException("Native music selector BYTE required");
        int period = rawPeriodByte & 0x7F;
        if (timeAttackByte == 0 && period == 2) period = (period + goodFutureByte) & 255;
        int index = ((rawZoneActWord >>> 8) * 4 + period) & 65535;
        if (index >= music.length()) throw new UnownedMusicByte(MUSIC_ADDRESS + index);
        return CdCommand.fromId(music.unsignedByte(index));
    }
}
