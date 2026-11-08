package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.CdMonitor;
import com.openggf.game.soniccd.objects.CdReservedHud;
import com.openggf.game.soniccd.objects.CdTitleCard;
import com.openggf.game.soniccd.objects.CdCczEnemyAssets;
import com.openggf.game.soniccd.objects.CdCczEnemyPorts;
import com.openggf.game.soniccd.objects.CdObjectAnimation;
import com.openggf.game.soniccd.objects.CdObjectMappings;

import java.util.Arrays;
import java.util.Objects;
import java.util.ArrayList;
import com.openggf.game.soniccd.objects.CdRing;

/**
 * Actual original-USA R31 common startup metadata only, from existing authenticated Asset views.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff R3 shared/Compatibility joins HUD, Monitor, Ring
 * and Title sources; r3/title_card_data.asm and sprites/r3/title_card.asm supply actual64/275+EVEN.
 * This does not authenticate handler PCs/Init directories/dispatch, create a Body/World/Frame,
 * decode/read/select art, establish residency, run scripts or manufacture operation authority.
 * The63/82/168 visual bounds are finite mapping-operand hulls only, never available-art claims.
 */
public final class CdR31aObjectAssets {
    private final CdR31aAssets assets;
    private final CdR31aAssets.Identity identity;
    private CdObjectVisualAssets hudVisuals, monitorTime, titleCard;
    private CdReservedHud.Assets reservedHud;
    private CdMonitor.Assets monitor;
    private CdTitleCard.Assets title;
    private CdCczEnemyAssets kamaSickle;
    /** Fixed borrowed keys; append no public R31A Asset enum or R32A range identity. */
    enum KamaView {
        NORMAL(0x358CA,431), DAMAGED(0x35A7A,348), SICKLE(0xD3DE,48),
        KAMA_ANIMATION(0xD25A,31), SICKLE_ANIMATION(0xD3CA,20),
        KAMA_CODE(0xCFFA,0x260), SICKLE_CODE(0xD27A,0x150);
        final int offset,length;
        KamaView(int offset,int length){this.offset=offset;this.length=length;}
    }

    private CdR31aObjectAssets(CdR31aAssets assets) {
        this.assets = Objects.requireNonNull(assets, "actual original R31A data");
        assets.requireAuthenticated(); // Existing finite whole-source check, no new raw reader.
        identity = Objects.requireNonNull(assets.identity(), "actual R31A identity");
        if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R31A
                || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !identity.stage().equals(CdStagePayload.R31A.stage())) {
            throw new IllegalArgumentException("Only original USA R31A common metadata");
        }
    }

    public static CdR31aObjectAssets fromAssets(CdR31aAssets assets) { return new CdR31aObjectAssets(assets); }
    public boolean ownsAssets(CdR31aAssets assets) { return this.assets == assets; }
    public CdR31aAssets.Identity identity() { return identity; }

    /** N1ecc5f3a objects/ring.asm RingInit/RingSparkle: actual operands, DATA only.
     * Whole RING_BODY authenticates the historical ABI and excludes the zone6 branch at zone1. */
    public CdRing.SourceData ringData() {
        var body=assets.view(CdR31aAssets.Asset.RING_BODY);
        if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong(0x3C)!=0x209768L
                || body.unsignedLong(0x96+2)!=0x209780L || body.unsignedLong(0x14C+2)!=0x209BE4L || body.unsignedWord(0x154+2)!=0xA7AE
                || body.unsignedLong(0x27C+2)!=0x209BDCL)
            throw new IllegalArgumentException("R31 Ring original operands differ");
        var raw=assets.view(CdR31aAssets.Asset.RING_DELTAS);
        var deltas=new ArrayList<CdR11aObjectAssets.Delta>();
        for(int i=0;i<32;i+=2)deltas.add(new CdR11aObjectAssets.Delta(raw.signedByte(i),raw.signedByte(i+1)));
        return new CdRing.SourceData(deltas,assets.range(CdR31aAssets.Asset.RING_MAPPINGS).nativeAddress(),0xA7AE,
                CdObjectVisualAssets.decode(assets.view(CdR31aAssets.Asset.RING_MAPPINGS).copy(),9,
                        assets.view(CdR31aAssets.Asset.RING_ANIMATIONS).copy(),1,14));
    }

    /** Shared native HUD table:4 frames, max operand $3D+2=63, no digit/life art access. */
    public synchronized CdObjectVisualAssets hudVisuals() {
        if (hudVisuals == null) hudVisuals = CdObjectVisualAssets.decodeStatic(
                assets.view(CdR31aAssets.Asset.HUD_MAPPINGS).copy(), 4, 63);
        return hudVisuals;
    }

    /** Complete450-byte/20-frame and136-byte/10-script includes. Max operand $50+2=82. */
    public synchronized CdObjectVisualAssets monitorTime() {
        if (monitorTime == null) monitorTime = CdObjectVisualAssets.decode(
                assets.view(CdR31aAssets.Asset.MONITOR_MAPPINGS).copy(), 20,
                assets.view(CdR31aAssets.Asset.MONITOR_ANIMATIONS).copy(), 10, 82);
        return monitorTime;
    }

    /** R3's genuine276-byte read view retains its real EVEN; Sprite8 requires $A2+6=168. */
    public synchronized CdObjectVisualAssets titleCard() {
        if (titleCard == null) {
            var canonical = assets.view(CdR31aAssets.Asset.TITLE_MAPPINGS);
            var reader = assets.view(CdR31aAssets.Asset.TITLE_MAPPING_READ_VIEW);
            if (!Arrays.equals(canonical.copy(), reader.slice(0, canonical.length()).copy())) {
                throw new IllegalArgumentException("R31 Title canonical/read-view source differs");
            }
            titleCard = CdObjectVisualAssets.decodeStatic(reader.copy(), 9, 168);
        }
        return titleCard;
    }

    /** Ordinary pointer values only. Public record equality grants no reserved-bank ownership. */
    public synchronized CdReservedHud.Assets reservedHudAssets() {
        if (reservedHud == null) reservedHud = new CdReservedHud.Assets(
                assets.range(CdR31aAssets.Asset.HUD_MAPPINGS).nativeAddress(),
                assets.range(CdR31aAssets.Asset.MONITOR_MAPPINGS).nativeAddress());
        return reservedHud;
    }

    /** Reuses the existing strict20/10/450/136 component contract without admitting its caller. */
    public synchronized CdMonitor.Assets monitorAssets() {
        if (monitor == null) monitor = new CdMonitor.Assets(monitorTime(),
                assets.range(CdR31aAssets.Asset.MONITOR_MAPPINGS).nativeAddress());
        return monitor;
    }

    /** Actual64 character bytes and source-local root; native ACT/read/child owners stay external. */
    public synchronized CdTitleCard.Assets titleCardAssets() {
        if (title == null) title = new CdTitleCard.Assets(assets.view(CdR31aAssets.Asset.TITLE_CHARACTERS),
                assets.range(CdR31aAssets.Asset.TITLE_MAPPINGS).nativeAddress());
        return title;
    }
    /** Original USA R31A pair DATA only. Whole source and literal consumer operands are checked;
     * public construction/equality of these views conveys no physical-pass authority or art residency.
     * N1ecc5f3a KamaKama/Main.asm:69-128,473-484,522-548,559-561; historical ABI=1.
     * Canonical odd431-byte normal mapping is sufficient for the existing counted resolver. */
    public synchronized CdCczEnemyAssets kamaSickleAssets() {
        if(kamaSickle==null){
            assets.requireAuthenticated();
            var index=assets.view(CdR31aAssets.Asset.OBJECT_INDEX);
            if(index.unsignedLong((0x24-1)*4)!=0x20CFFAL||index.unsignedLong((0x25-1)*4)!=0x20D27AL)
                throw new IllegalArgumentException("Original R31A Kama/Sickle dispatch differs");
            operand(KamaView.KAMA_CODE,0xD022,"223c002358ca");
            operand(KamaView.KAMA_CODE,0xD034,"223c00235a7a");
            operand(KamaView.KAMA_CODE,0xD042,"21410004");
            operand(KamaView.KAMA_CODE,0xD086,"43fa01d2");
            operand(KamaView.SICKLE_CODE,0xD2BC,"217c0020d3de0004");
            operand(KamaView.SICKLE_CODE,0xD318,"43fa00b0");
            operand(KamaView.SICKLE_CODE,0xD360,"43fa0068");
            var mappings=new java.util.EnumMap<CdCczEnemyPorts.Mapping,
                CdCczEnemyPorts.MappingView>(CdCczEnemyPorts.Mapping.class);
            mappings.put(CdCczEnemyPorts.Mapping.KAMA_NORMAL,kamaMapping(KamaView.NORMAL,8));
            mappings.put(CdCczEnemyPorts.Mapping.KAMA_DAMAGED,kamaMapping(KamaView.DAMAGED,8));
            mappings.put(CdCczEnemyPorts.Mapping.SICKLE,kamaMapping(KamaView.SICKLE,6));
            var animations=new java.util.EnumMap<CdCczEnemyPorts.Animation,
                CdObjectAnimation.AnimationTable>(CdCczEnemyPorts.Animation.class);
            animations.put(CdCczEnemyPorts.Animation.KAMA,
                new CdObjectAnimation.AnimationTable(kamaRead(KamaView.KAMA_ANIMATION),0,5));
            animations.put(CdCczEnemyPorts.Animation.SICKLE,
                new CdObjectAnimation.AnimationTable(kamaRead(KamaView.SICKLE_ANIMATION),0,2));
            // Required DATA field of the existing shared adapter: actual STAGE_TIME+2=3, unused by this pair.
            kamaSickle=new CdCczEnemyAssets(mappings,animations,3);
        }
        return kamaSickle;
    }
    private CdAssetBytes kamaRead(KamaView view){return assets.internalReader().view(view);}
    private CdCczEnemyPorts.MappingView kamaMapping(KamaView view,int frames){
        return new CdCczEnemyPorts.MappingView(new CdObjectMappings.DeclaredTable(
            0x200000+view.offset,kamaRead(view),0,frames));
    }
    private void operand(KamaView view,int fileOffset,String hex){
        var bytes=kamaRead(view);var expected=java.util.HexFormat.of().parseHex(hex);int at=fileOffset-view.offset;
        if(!Arrays.equals(expected,bytes.slice(at,expected.length).copy()))throw new IllegalArgumentException("Original R31A Kama/Sickle operand differs at "+Integer.toHexString(fileOffset));
    }

}
