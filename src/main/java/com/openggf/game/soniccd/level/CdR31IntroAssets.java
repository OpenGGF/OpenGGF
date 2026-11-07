package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.objects.CdObjectAnimation;
import com.openggf.game.soniccd.player.CdPlayerMath;
import java.util.Objects;

/** Finite P5 data borrowed from the authenticated original USA R31A media owner.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: objects/r3/intro.asm:13-878 (historical A include),
 * CC Roll Tunnel/Main:27-33; B/C/D objects/roll_tunnel.asm:3-179.
 * No media opener, fallback bytes, current-art service or dispatch/invocation authority.
 * B/C/D intro handlers are Null regardless of retained art. Root owns their source adapters.
 */
public final class CdR31IntroAssets {
    /** Original R31A-only DATA; the common reader owns all actual reads and caches. */
    enum View {
        SHARD_MOTION(0xE53A,16), AMY_PALETTE(0xE2D2,32),
        AMY_ANIMATIONS(0x3BF08,39), METAL_ANIMATIONS(0xE054,25), EXHAUST_ANIMATIONS(0xE678,8),
        INTRO_MAPPINGS(0x3BE60,168), METAL_MAPPINGS(0x3BC54,349),
        AMY_MAPPINGS(0x3BDB2,174), ROLL_MAPPINGS(0x3A4C8,274),
        INTRO_MAPPING_STORE(0xE3DE,8), METAL_MAPPING_STORE(0xDE94,8),
        AMY_MAPPING_STORE(0xE0EC,8), ROLL_MAPPING_STORE(0x6578,8),
        METAL_HOVER_STORE(0xDF6A,6), METAL_STAGE_PALETTE(0xE044,6);
        final int offset,length;
        View(int offset,int length) { this.offset=offset;this.length=length; }
    }
    private final CdR31aAssets owner;
    private final CdAssetBytes motion, amyPalette;
    private final CdObjectAnimation.AnimationTable amy, metal, exhaust;
    private final CdPlayerMath math;
    private CdR31IntroAssets(CdR31aAssets owner) {
        this.owner = Objects.requireNonNull(owner);
        owner.requireAuthenticated();
        // All reads are finite original file views; the owner's whole-MMD hash authenticates them.
        motion = read(View.SHARD_MOTION);
        amyPalette = read(View.AMY_PALETTE);
        amy = new CdObjectAnimation.AnimationTable(read(View.AMY_ANIMATIONS),0,4);
        metal = new CdObjectAnimation.AnimationTable(read(View.METAL_ANIMATIONS),0,3);
        exhaust = new CdObjectAnimation.AnimationTable(read(View.EXHAUST_ANIMATIONS),0,1);
        math = new CdPlayerMath(CdR11aPlayerAssets.fromR31aAssets(owner));
        requireOperand(View.INTRO_MAPPING_STORE,"217c0023be600004");
        requireOperand(View.METAL_MAPPING_STORE,"217c0023bc540004");
        requireOperand(View.AMY_MAPPING_STORE,"217c0023bdb20004");
        requireOperand(View.ROLL_MAPPING_STORE,"217c0023a4c80004");
        requireOperand(View.METAL_HOVER_STORE,"117c00500030"); // historical Hover, not semantic timer3A
        requireOperand(View.METAL_STAGE_PALETTE,"47f90020067c"); // actual StagePalette

    }
    public static CdR31IntroAssets fromAssets(CdR31aAssets owner) { return new CdR31IntroAssets(owner); }
    public boolean owns(CdR31aAssets candidate) { return owner == candidate; }
    public CdR31aAssets.Identity identity() { return owner.identity(); }
    private CdAssetBytes read(View view) {
        return owner.internalReader().view(view);
    }
    private void requireOperand(View view,String hex) {
        byte[] expected=java.util.HexFormat.of().parseHex(hex);
        if (!java.util.Arrays.equals(expected,read(view).copy()))
            throw new IllegalArgumentException("P5 original mapping operand differs at " + Integer.toHexString(view.offset));
    }
    public int introMappings() { return 0x23BE60; }
    public int metalMappings() { return 0x23BC54; }
    public int amyMappings() { return 0x23BDB2; }
    public int rollMappings() { return 0x23A4C8; }
    public CdAssetBytes introMappingBytes() { return read(View.INTRO_MAPPINGS); }
    /** Actual odd include; no invented EVEN or widened uniform mapping decoder. */
    public CdAssetBytes metalMappingBytes() { return read(View.METAL_MAPPINGS); }
    public CdAssetBytes amyMappingBytes() { return read(View.AMY_MAPPINGS); }
    public CdAssetBytes rollMappingBytes() { return read(View.ROLL_MAPPINGS); }
    public CdAssetBytes amyPalette() { return amyPalette; }
    public CdAssetBytes capturePalette() { return owner.view(CdR31aAssets.Asset.PAL_LEVEL).slice(0,32); }
    public CdObjectAnimation.AnimationTable amyAnimations() { return amy; }
    public CdObjectAnimation.AnimationTable metalAnimations() { return metal; }
    public CdObjectAnimation.AnimationTable exhaustAnimations() { return exhaust; }
    public int sine(int angle) { return math.calcSine(angle).sine(); }
    /** Actual zero-extended child BYTE times4; invalid dirty selectors fail at this reached read. */
    public int shardX(int child) { return (short)motion.unsignedWord(child*4); }
    public int shardY(int child) { return (short)motion.unsignedWord(child*4+2); }
    /** A finite dispatch disposition, not source authentication or stage admission. */
    public static boolean nativeNull(CdNativeFile programme,int id) {
        return (programme==CdNativeFile.R31B || programme==CdNativeFile.R31C || programme==CdNativeFile.R31D)
                && id>=0x30 && id<=0x34;
    }
}
