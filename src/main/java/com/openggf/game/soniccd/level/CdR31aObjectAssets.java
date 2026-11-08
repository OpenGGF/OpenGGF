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
    private CdCczEnemyAssets kamaSickle;
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
