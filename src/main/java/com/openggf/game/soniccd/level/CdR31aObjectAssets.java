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
import com.openggf.game.soniccd.objects.CdExplosion;
import com.openggf.game.soniccd.objects.CdPoints;
import com.openggf.game.soniccd.objects.CdCcAnimal;

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
    private com.openggf.game.soniccd.objects.CdFlower.Assets flower;
    enum FlowerView { DISPATCH(0x6064,20), MAPPING_STORE(0x60A6,8), ANIMATION_LEA(0x612E,6);
        final int offset,length;FlowerView(int o,int n){offset=o;length=n;} }

    private com.openggf.game.soniccd.objects.CdSpring.FiniteProfile spring;
    enum SpringView {
        MAP(0x968A,199,"f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd"),
        ANIMATION(0x967C,14,"7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5"),
        SPEED(0x93D2,4,"5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"),
        TILE_SET(0xBF60,8,"48d4bc013390e36d7bbe8f75e44b3994fe5cff24bfe67a64288f30de697f7e9c"),
        CONSUMERS(0x9298,0x10A,null);
        final int offset,length;final String sha;SpringView(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    private CdCcAnimal.Assets ccAnimal;
    enum CcAnimalView { CODE(0xE680,550,"88b978044b81556200f2aad144dd7e2a7da9bbaf32927e305f2dbd251c0da9aa");
        final int offset,length;final String sha;
        CcAnimalView(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    // Selected R31A ObjGa DATA; both mapping tables share one authenticated source hull.
    private CdCczEnemyAssets ga;
    enum GaView {
        MAPPINGS(0xB4B0,159,"ec1000af9b25b68f3247120256c7ded529247e0f36401bac93f646171ee4ee4a"),
        ANIMATION(0xB49C,19,"bafadabc4592a6b0dd7728dc37d4c8b955b5db02e60dcba9922e51c930a2d8cb"),
        DUST_ANIMATION(0xB5C0,8,"9e1a182a96f146ef3e54a4e37e2230e859b5255e21879f2742da4a7af4bc4f13"),
        PARENT_CODE(0xB328,0x174,null), DUST_CODE(0xB550,0x70,null);
        final int offset,length;final String sha;
        GaView(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    private CdCczEnemyAssets kamaSickle;
    // BEGIN selected original Tentou DATA, no art/lifecycle authority, 20261108.
    private CdCczEnemyAssets tentou;
    enum TentouView {
        NORMAL(0xBBC2,114,"d1a1623531939f4faf5e6dec94eaa5e476c483f2c39c1537bab678287c230515"),
        DAMAGED(0xBB50,114,"b2414198196338370e9ea176b2b3c94eb94e0086990c122436d30dbcb59ee79b"),
        BOMB(0xBD10,16,"f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d"),
        PARENT_ANIMATION(0xBB48,7,"0fc1b03be15aa3b7d81cde42b958b3241dee6103eeace0502548ed9b0591421f"),
        BOMB_ANIMATION(0xBD0A,6,"ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c"),
        PARENT_CODE(0xB928,544,"eb04840e9a6e1ca8939a12e012eddef0fcee1a4b2eeb6b8030849f30a0b1f108"),
        BOMB_CODE(0xBC34,214,"09971fd5313b347b5d79e870d2e85fc434668881a04479670b798be8b57fc12d");
        final int offset,length;final String sha;
        TentouView(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    // END selected original Tentou DATA 20261108.

    /** Fixed borrowed keys; append no public R31A Asset enum or R32A range identity. */
    enum KamaView {
        NORMAL(0x358CA,431), DAMAGED(0x35A7A,348), SICKLE(0xD3DE,48),
        KAMA_ANIMATION(0xD25A,31), SICKLE_ANIMATION(0xD3CA,20),
        KAMA_CODE(0xCFFA,0x260), SICKLE_CODE(0xD27A,0x150);
        final int offset,length;
        KamaView(int offset,int length){this.offset=offset;this.length=length;}
    }
    private CdExplosion.Assets explosion;
    private CdPoints.Assets points;
    /** Finite original R31A views; public Asset keys/ordinals and other programmes stay exact. */
    enum EffectView {
        EXPLOSION_ANIMATIONS(0x3A6A0,20), EXPLOSION_MAPPINGS(0x3A6B4,146),
        EXPLOSION_CODE(0x5FD6,0x74), MAKE_POINTS_CODE(0x5FA4,0x32),
        POINTS_CODE(0xA6FA,0x54), POINTS_SELECTOR(0xA7A0,8);
        final int offset,length;
        EffectView(int offset,int length){this.offset=offset;this.length=length;}
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
    /** Actual R31A ObjTentou/TentouBomb operands at N1ecc5f3a Tentou/Main.asm:34-443.
     * Canonical counted3/3/2 roots; the equal Spring bomb mapping does NOT grant admission.
     * All borrowed bytes remain in the original authenticated programme reader. */
    public synchronized CdCczEnemyAssets tentouAssets(){
        if(tentou==null){
            assets.requireAuthenticated();
            if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x2E-1)*4)!=0x20B928L)
                throw new IllegalArgumentException("Original R31A Tentou dispatch differs");
            tentouOperand(TentouView.PARENT_CODE,0xB928,"10280028060000ff6b0460000300");
            tentouOperand(TentouView.PARENT_CODE,0xB936,"4eb90020f104");
            tentouOperand(TentouView.PARENT_CODE,0xB94A,"43fa01fc");
            tentouOperand(TentouView.PARENT_CODE,0xB994,"43fa01ba");
            tentouOperand(TentouView.PARENT_CODE,0xB99E,"43fa0222");
            tentouOperand(TentouView.PARENT_CODE,0xB9A6,"21490004");
            tentouOperand(TentouView.PARENT_CODE,0xB9B2,"4eb900206ed6");
            tentouOperand(TentouView.PARENT_CODE,0xBA48,"43f900207142");
            tentouOperand(TentouView.PARENT_CODE,0xBA54,"43f900206fa0");
            tentouOperand(TentouView.PARENT_CODE,0xBA8A,"4eb900207afc");
            tentouOperand(TentouView.PARENT_CODE,0xBAF0,"4eb900206eda");
            tentouOperand(TentouView.BOMB_CODE,0xBC80,"217c0020bd100004");
            tentouOperand(TentouView.BOMB_CODE,0xBCDC,"43fa002c");
            tentouOperand(TentouView.BOMB_CODE,0xBCFE,"303c009e4eb900202282");
            var maps=new java.util.EnumMap<CdCczEnemyPorts.Mapping,CdCczEnemyPorts.MappingView>(CdCczEnemyPorts.Mapping.class);
            maps.put(CdCczEnemyPorts.Mapping.TENTOU_NORMAL,tentouMapping(TentouView.NORMAL,3));
            maps.put(CdCczEnemyPorts.Mapping.TENTOU_DAMAGED,tentouMapping(TentouView.DAMAGED,3));
            maps.put(CdCczEnemyPorts.Mapping.BOMB,tentouMapping(TentouView.BOMB,2));
            var animations=new java.util.EnumMap<CdCczEnemyPorts.Animation,CdObjectAnimation.AnimationTable>(CdCczEnemyPorts.Animation.class);
            animations.put(CdCczEnemyPorts.Animation.TENTOU,new CdObjectAnimation.AnimationTable(tentouRead(TentouView.PARENT_ANIMATION),0,1));
            animations.put(CdCczEnemyPorts.Animation.BOMB,new CdObjectAnimation.AnimationTable(tentouRead(TentouView.BOMB_ANIMATION),0,1));
            // Existing adapter requires the source STAGE_TIME+2=3; Tentou never reads chain count.
            tentou=new CdCczEnemyAssets(maps,animations,3);
        }
        return tentou;
    }
    /** Actual ObjGa $2D/$20B328 (N1ecc5f3a Ga/Main.asm:13-64).
     * Animation/mapping operands (Ga/Main.asm:211-227); ObjGa_Dust (Ga/Main.asm:235-282).
     * Selected ABI1 source, DATA only. Dust borrows the existing Ring mapping; no art write. */
    public synchronized CdCczEnemyAssets gaAssets(){
        if(ga==null){
            assets.requireAuthenticated();
            if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x2D-1)*4)!=0x20B328L)
                throw new IllegalArgumentException("Original R31A Ga handler differs");
            gaOperand(GaView.PARENT_CODE,0xB38C,"43fa0122");
            gaOperand(GaView.PARENT_CODE,0xB396,"43fa015a");
            gaOperand(GaView.PARENT_CODE,0xB3D2,"43f90020b49c");
            gaOperand(GaView.DUST_CODE,0xB5B0,"43fa000e");
            var bytes=assets.internalReader().view(GaView.MAPPINGS);
            var maps=new java.util.EnumMap<CdCczEnemyPorts.Mapping,CdCczEnemyPorts.MappingView>(CdCczEnemyPorts.Mapping.class);
            maps.put(CdCczEnemyPorts.Mapping.GA_NORMAL,new CdCczEnemyPorts.MappingView(new CdObjectMappings.DeclaredTable(0x20B4B0,bytes,66,6)));
            maps.put(CdCczEnemyPorts.Mapping.GA_DAMAGED,new CdCczEnemyPorts.MappingView(new CdObjectMappings.DeclaredTable(0x20B4B0,bytes,0,6)));
            var ring=ringData();
            maps.put(CdCczEnemyPorts.Mapping.RING,new CdCczEnemyPorts.MappingView(new CdObjectMappings.DeclaredTable(ring.mappingAddress(),ring.visuals().mappings(),0,9)));
            var animations=new java.util.EnumMap<CdCczEnemyPorts.Animation,CdObjectAnimation.AnimationTable>(CdCczEnemyPorts.Animation.class);
            animations.put(CdCczEnemyPorts.Animation.GA,new CdObjectAnimation.AnimationTable(assets.internalReader().view(GaView.ANIMATION),0,3));
            animations.put(CdCczEnemyPorts.Animation.DUST,new CdObjectAnimation.AnimationTable(assets.internalReader().view(GaView.DUST_ANIMATION),0,1));
            // Existing adapter's actual STAGE_TIME+2=3; Ga never asks for a chain count.
            ga=new CdCczEnemyAssets(maps,animations,3);
        }
        return ga;
    }
    private void gaOperand(GaView view,int offset,String hex){
        var expected=java.util.HexFormat.of().parseHex(hex);
        if(!Arrays.equals(expected,assets.internalReader().view(view).slice(offset-view.offset,expected.length).copy()))
            throw new IllegalArgumentException("Original R31A Ga operand differs at "+Integer.toHexString(offset));
    }
    private CdAssetBytes tentouRead(TentouView view){return assets.internalReader().view(view);}
    private CdCczEnemyPorts.MappingView tentouMapping(TentouView view,int frames){
        return new CdCczEnemyPorts.MappingView(new CdObjectMappings.DeclaredTable(0x200000+view.offset,tentouRead(view),0,frames));
    }
    private void tentouOperand(TentouView view,int offset,String hex){
        var expected=java.util.HexFormat.of().parseHex(hex);
        if(!Arrays.equals(expected,tentouRead(view).slice(offset-view.offset,expected.length).copy()))
            throw new IllegalArgumentException("Original R31A Tentou operand differs at "+Integer.toHexString(offset));
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

    /** Actual original-media DATA only, not a World invocation or current-art grant.
     * N1ecc5f3a R3 Legacy Act1 Shared:25,36,110,130; Explosion/Main:14-106; HUD/Points:15-69. */
    public synchronized CdExplosion.Assets explosionAssets(){effectsData();return explosion;}
    public synchronized CdPoints.Assets pointsAssets(){effectsData();return points;}
    private void effectsData(){
        if(explosion!=null)return;
        assets.requireAuthenticated();var index=assets.view(CdR31aAssets.Asset.OBJECT_INDEX);
        if(index.unsignedLong((0x18-1)*4)!=0x205FD6L||index.unsignedLong((0x1C-1)*4)!=0x20A7A0L)
            throw new IllegalArgumentException("Original R31A Explosion/Points dispatch differs");
        effectOperand(EffectView.EXPLOSION_CODE,0x5FD6,"700010280024303b00064efb0002000600560066");
        effectOperand(EffectView.EXPLOSION_CODE,0x600C,"217c0023a6b40004618e");
        effectOperand(EffectView.EXPLOSION_CODE,0x603A,"43f90023a6a06100fc284ef900203a72");
        effectOperand(EffectView.MAKE_POINTS_CODE,0x5FA4,"4a280025662a72003228003ee20900010080");
        // The called FindObjSlot is R31's SpawnObject alias: native result is A1, never A0.
        effectOperand(EffectView.MAKE_POINTS_CODE,0x5FB6,"4eb9");
        effectOperand(EffectView.MAKE_POINTS_CODE,0x5FBC,"6616137c001c00003368000800083368000c000c134100284e75");
        effectOperand(EffectView.POINTS_CODE,0xA6FA,"700010280024303b000c4ebb00084ef900203a720004002e");
        effectOperand(EffectView.POINTS_CODE,0xA722,"217c0020a74e0004");
        effectOperand(EffectView.POINTS_CODE,0xA736,"117c0018002a5328002a");
        effectOperand(EffectView.POINTS_CODE,0xA748,"5568000c4e75");
        effectOperand(EffectView.POINTS_SELECTOR,0xA7A0,"4a2800286b00ff54");
        var e=new CdExplosion.Assets(CdObjectVisualAssets.decode(effectRead(EffectView.EXPLOSION_MAPPINGS).copy(),7,
            effectRead(EffectView.EXPLOSION_ANIMATIONS).copy(),2,70),0x23A6B4);
        var p=new CdPoints.Assets(CdObjectVisualAssets.decodeStatic(assets.view(CdR31aAssets.Asset.POINTS_MAPPINGS).copy(),6,5),0x20A74E);
        // Prepare both immutable data values before either cache assignment; failure leaves no partial pair.
        explosion=e;points=p;
    }
    private CdAssetBytes effectRead(EffectView view){return assets.internalReader().view(view);}
    private void effectOperand(EffectView view,int fileOffset,String hex){
        var expected=java.util.HexFormat.of().parseHex(hex);var bytes=effectRead(view);
        if(!Arrays.equals(expected,bytes.slice(fileOffset-view.offset,expected.length).copy()))
            throw new IllegalArgumentException("Original R31A effect operand differs at "+Integer.toHexString(fileOffset));
    }
    /** Actual original R31A P1 metadata; genuine199/14/speed4, no invented tile table.
     * N Spring/Main.asm:130-244,492-496; R3 Compatibility:584-591 and section_gfx_1a:72-74. */
    public synchronized com.openggf.game.soniccd.objects.CdSpring.FiniteProfile springAssets(){
        if(spring==null){assets.requireAuthenticated();if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong(9*4)!=0x209298L)throw new IllegalArgumentException("Original R31A Spring dispatch differs");
            var reader=assets.internalReader();var code=reader.view(SpringView.CONSUMERS);
            if(!java.util.HexFormat.of().formatHex(code.slice(0x9326-0x9298,8).copy()).equals("217c0020968a0004")
                ||!java.util.HexFormat.of().formatHex(code.slice(0x936E-0x9298,8).copy()).equals("217c002096900004")
                ||!java.util.HexFormat.of().formatHex(code.slice(0x9390-0x9298,8).copy()).equals("217c002096da0004")
                ||!java.util.HexFormat.of().formatHex(code.slice(0x939C-0x9298,6).copy()).equals("4eb90020bf60"))throw new IllegalArgumentException("Original R31A Spring mapping/tile operands differ");
            var map=reader.view(SpringView.MAP);var ani=reader.view(SpringView.ANIMATION);var helper=reader.view(SpringView.TILE_SET);
            if(!java.util.HexFormat.of().formatHex(helper.copy()).equals("317c04f200024e75"))throw new IllegalArgumentException("Original R31A SetObjectSpriteTile differs");
            spring=new com.openggf.game.soniccd.objects.CdSpring.FiniteProfile(
                CdObjectVisualAssets.decodeIndexed(map.copy(),0,3,12,46,ani.copy(),1,36),
                CdObjectVisualAssets.decodeIndexed(map.copy(),6,3,46,80,ani.copy(),1,36),
                CdObjectVisualAssets.decodeIndexed(map.copy(),80,3,86,199,ani.copy(),1,46),0x20968A,reader.view(SpringView.SPEED),helper.unsignedWord(2));
        }return spring;
    }

    /** Original R31A DATA only; P6's finite adapter supplies the existing eight/four/39 format.
     * N1ecc5f3 Flower/Main.asm:10-162, R3 Compatibility:518-522 and data_1a:323-329. */
    public synchronized com.openggf.game.soniccd.objects.CdFlower.Assets flowerAssets(){
        if(flower==null){assets.requireAuthenticated();
            if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x1F-1)*4)!=0x206064L)throw new IllegalArgumentException("Original R31A Flower dispatch differs");
            var reader=assets.internalReader();
            if(!java.util.HexFormat.of().formatHex(reader.view(FlowerView.DISPATCH).copy()).equals("700010280024303b000c4ebb00084ef900203a72")
                ||!java.util.HexFormat.of().formatHex(reader.view(FlowerView.MAPPING_STORE).copy()).equals("217c002336d80004")
                ||!java.util.HexFormat.of().formatHex(reader.view(FlowerView.ANIMATION_LEA).copy()).equals("43f9002336bc"))throw new IllegalArgumentException("Original R31A Flower STORE/LEA differs");
            var adapter=new CdR31EcologyEndAssets(new FlowerSource());flower=adapter.flower();
        }return flower;
    }

    /** Original R31A Animal39: source-only DATA, never presence/history/art/World authority.
     * N1ecc5f3a CC Animal/Main.asm:38-295, ABI1; the existing P6 decoder/body owns all algorithms. */
    public synchronized CdCcAnimal.Assets ccAnimalAssets(){
        if(ccAnimal==null){assets.requireAuthenticated();
            if(assets.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x39-1)*4)!=0x20E680L)
                throw new IllegalArgumentException("Original R31A CCAnimal dispatch differs");
            ccAnimalOperand(0xE680,"4eb90020f14c");
            ccAnimalOperand(0xE6B8,"217c0020e9240004");
            ccAnimalOperand(0xE7CC,"217c0020e9380004");
            ccAnimalOperand(0xE72A,"43fa017a");
            ccAnimalOperand(0xE864,"43fa0082");
            ccAnimalOperand(0xE786,"4eb900200782");
            ccAnimalOperand(0xE81A,"4eb900206ed6");
            ccAnimalOperand(0xE888,"43fa00d2");
            ccAnimalOperand(0xE88E,"103900ff1507e540d03900ff152ed040");
            ccAnimal=new CdR31EcologyEndAssets(new CcAnimalSource()).animal();
        }return ccAnimal;
    }
    private void ccAnimalOperand(int offset,String hex){
        var expected=java.util.HexFormat.of().parseHex(hex);var code=assets.internalReader().view(CcAnimalView.CODE);
        if(!Arrays.equals(expected,code.slice(offset-CcAnimalView.CODE.offset,expected.length).copy()))
            throw new IllegalArgumentException("Original CCAnimal operand differs at "+Integer.toHexString(offset));
    }
    private final class CcAnimalSource implements CdR31EcologyEndAssets.Source {
        public com.openggf.game.soniccd.CdNativeFile file(){return com.openggf.game.soniccd.CdNativeFile.R31A;}
        public CdAssetBytes borrow(CdR31EcologyEndAssets.View view){return switch(Objects.requireNonNull(view)){
            case ANIMAL_FLY_MAP,ANIMAL_GROUND_MAP,ANIMAL_FLY_ANI,ANIMAL_GROUND_ANI,ANIMAL_TILES->assets.internalReader().view(view);
            default->throw new IllegalArgumentException("Not an actual R31A CCAnimal operand: "+view);
        };}
    }

    /** Finite P6 DATA borrow only. The enclosing original reader authenticates identity. */
    private final class FlowerSource implements CdR31EcologyEndAssets.Source {
        public CdNativeFile file(){return assets.identity().file();}
        public CdAssetBytes borrow(CdR31EcologyEndAssets.View view){
            if(view!=CdR31EcologyEndAssets.View.FLOWER_MAP&&view!=CdR31EcologyEndAssets.View.FLOWER_ANI)
                throw new IllegalArgumentException("Only authenticated Flower pair");
            return assets.internalReader().view(view);
        }
    }

}
