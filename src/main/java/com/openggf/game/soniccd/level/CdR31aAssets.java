package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.util.*;
/**
 * Detached original-USA R31A startup DATA, not a player/spawner/Frame/World/RunR31 admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff R31A Legacy/R3 Legacy Act1 Shared:
 * actual r3/data_1a.asm, Scroll (Act1Present), Graphics Lists, native objects/player.asm.
 * Raw player scripts/math/touch and all517 placement rows retain their original source.
 * The explicit R31A placement relation neither fabricates PPZ3D-ramp input nor widens the old497-row constructor.
 * No service, art/current, clock, controller, actor, callback, default RAM or appearance is created.
 */
public final class CdR31aAssets {
    private static final String MMD_SHA="30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e";
    public record Identity(CdReleaseVariant variant,CdStageKey stage,CdNativeFile file,
                           CdMmdImage.Domain domain,String mmdSha256,String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant);Objects.requireNonNull(stage);Objects.requireNonNull(file);
            Objects.requireNonNull(domain);Objects.requireNonNull(mmdSha256);Objects.requireNonNull(observedWholeDiscSha256);
            if(variant!=CdReleaseVariant.USA || !stage.equals(CdStagePayload.R31A.stage()) || file!=CdNativeFile.R31A
                    || domain!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || !MMD_SHA.equals(mmdSha256)
                    || !observedWholeDiscSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Not an original USA R31A data identity");
        }
    }
    /** Fixed original spans. Descriptor construction alone authenticates no source or invocation. */
    public enum Asset {
        LEVEL_CHUNKS(0x10000,54784,"5501b636432a99dfce17a10c1357b497b06a8d48716fbd325536ac82d9ca0d44"),
        COL_ANGLE_MAP(0x1D600,256,"c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd"),
        COL_HEIGHT_MAP(0x1D700,4096,"103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d"),
        COL_WIDTH_MAP(0x1E700,4096,"90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4"),
        LEVEL_COLLISION(0x1F700,700,"7c606a40992f3bab8e995c4e2a343a0c7ee0583de53b3c10039e09e903e537d6"),
        LAYOUT_FG(0x1FA04,290,"874d951032d030e7ae0df432811465b3b7fecedcaff01d7f8a4d19022bc7f13b"),
        LAYOUT_BG(0x1FB26,24,"878fac8156943fdc600fc75e058a55819a7839df1925d084da4dff9ced8253f2"),
        LEVEL_BLOCKS(0x36C76,3960,"51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3"),
        ART_LEVEL_TILES(0x37BEE,10162,"f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37"),
        ART_SONIC(0x20000,57120,"a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276"),
        MAP_SPR_SONIC(0x2DF20,2443,"4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965"),
        DPLC_SONIC(0x2E8AC,1338,"313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae"),
        ANI_SONIC(0x590A,578,"96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9"),
        MINI_ANIMATIONS(0x58BA,80,"3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8"),
        SINE_TABLE(0x79A,640,"bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9"),
        ARC_TAN_TABLE(0xA80,258,"7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f"),
        TOUCH_DIMENSIONS(0x754A,126,"5337f6beae28fe2b8458277610f92d358020a78e947d68b09470b38829e46575"),
        ENEMY_POINTS(0x7340,8,"75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5"),
        CAM_BOUNDS(0x287C,12,"40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1"),
        LEVEL_START_LOC(0x2940,4,"f2084622a23e63902510eea2a083587f297c6f521575b9943592127adbe41df0"),
        SPEC_CHUNKS(0x2944,4,"365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf"),
        PALETTE_CYCLES(0x200,100,"f1c83c9d9f2454d129b2811a0dc138994130ad7754a59cf6935a90ab071a3f72"),
        PAL_SONIC(0x65C,32,"8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a"),
        PAL_LEVEL(0x67C,96,"2af2050f1eb57a5183cb90be48b6e5e7ffbbf7cd3cc5764d466eaf64a185bd15"),
        PAL_LEVEL_END(0x6DC,96,"4277057e64bf28760727361b2b5ecda2795d57e05c49511236c4532e1b03e8c4"),
        SECTIONS(0xBF48,24,"fd7724d154bea8193d2e18a092e570dddca627c749715f20a4582f092c13a9a1"),
        BG_SECTIONS(0x3504,50,"2a2a2a30fe4a460e0fdd45a0d941fe7b0e1cee3ee4fd2a9aacbee6ba6c517e2a"),
        SCROLL_REPEAT_COUNTS(0x2C02,16,"6dcfe3a302eac5f3c0efe96d992b050b7657c04d7906dcaa6bb49db6a1f5ac90"),
        SCROLL_WATER_BOUNDS(0x2C12,6,"677bb2193c2749569097a8311af9320d7269d37ad55c06dac13626006a2d5104"),
        WOBBLE(0x3A746,256,"2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267"),
        HUD_MAPPINGS(0xA850,168,"7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3"),
        POINTS_MAPPINGS(0xA74E,82,"24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e"),
        TITLE_CHARACTERS(0xF966,64,"5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2"),
        TITLE_MAPPINGS(0xF9A6,275,"b02c6847c24061150b4b815cfd6610fd79ed114af5afd4d5ebd736d2b0ff6e42"),
        MONITOR_ANIMATIONS(0xA4B0,136,"c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36"),
        MONITOR_MAPPINGS(0xA538,450,"00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca"),
        ART_LIFE_ICONS(0x32F3C,384,"0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a"),
        ART_HUD_NUMBERS(0x330BC,1024,"843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        PAL_S1_TITLE(0x55C,128,"65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759"),
        PAL_S1_LEVEL_SELECT(0x5DC,128,"809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d"),
        ART_SHIELD(0x3114E,1152,"43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e"),
        ART_INVINCIBILITY(0x315CE,1152,"486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75"),
        ART_TIME_STARS(0x31A4E,992,"1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa"),
        LEVEL_COL_INDEX(0x19C2,32,"cc50e40a8b5ce071fe95bd39ba479d65c4e9a985c9e4ac3c9f66b7f8fac8fa1c"),
        LEVEL_DATA_INDEX(0xFAC0,16,"48bec27bc20f2c10196ae78631fe65d902aeaf3d96a9c52168513c335ce37152"),
        PLC_LISTS(0xFAD0,534,"abac5333eb26053a3373f20f4f87a4e8a0e363c2a0c5e2402bb421fe31e1338c"),
        LEVEL_LAYOUTS(0x1F9BC,72,"31fd506d49908b9e734b6b7792cb731e8c4990c37225d4006913800130bee113"),
        OBJECT_INDEX(0x3DB4,240,"91cadd2958133a5d66c363a7c1233e2a86be70db860dd911df3a98e4a87d4ccb"),
        OBJECT_LAYOUTS(0x7BAE,4162,"cd825a16352a1f385b0f5cc3dec98ec7d8b05c9a0f4621506f0e3011042cbce5"),
        PALETTE_TABLE(0x52C,48,"8dd2f06d81432e838ae90f04765214f0229b3d2e44b401a6f0feac170007fdd4"),
        BG_POINTERS(0x3536,16,"2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1"),
        SONIC_MAPPING_READ_VIEW(0x2DF20,2444,"9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77"),
        TITLE_MAPPING_READ_VIEW(0xF9A6,276,"d8818ede6de8c23469e94b36c8445439969b36b120ec7b69855d1f8ef7134a11"),
        // Actual R3 include: complete raw directories/count lists/profile bytes, no invocation relation.
        PLAYER_BLOCKS(0x6858,8,"fa316848863cb1723eff5b464a05b21e48b87da9444792decf31c760cf992478"),
        PLAYER_BLOCKS_A(0x6860,34,"d05eb8d9f7ab1c19d8dc26425c9d922c8accb09b9c74da0f652e2a89d270f7a9"),
        PLAYER_BLOCKS_B(0x6882,6,"7b054c17b49ee53fe432f8700be73ba7d337e56cc0aa4aeced43ef662a7f2ebe"),
        PLAYER_BLOCKS_C(0x6888,34,"4919cb2975fad8d7ce977c58c4235b139abbfe707a382ad68cb302e081521805"),
        PLAYER_BLOCKS_D(0x68AA,34,"a0e3da04701e194ebf10531da38153f9c058051ca7340833cc068da408c9dc30"),
        PLAYER_BLOCK_ROUTINES(0x6922,32,"234e4a3bb1b19f9237548700a94e9a515571e11284f2ec9d333825c5ad4a9003"),
        PLAYER_BUMPER_COLLIDE(0x6B2E,6,"b310628db3d435d16937fc75ab8b04097658ac0fcf15530843d3c0aa591cae74"),
        PLAYER_BUMPER_COLLIDE_1(0x6B34,16,"46fd7d2954ec5a94f0ab18ed3ce82ef3738b9e9a5f444e952caccba97ec43cd8"),
        PLAYER_BUMPER_COLLIDE_2(0x6B44,16,"7039c80bf965bd800e6e814ae50b42d9316184aa79c63a41075fa1945bf7dd47"),
        PLAYER_BUMPER_COLLIDE_3(0x6B54,16,"55863b4d00c3326f9acf1d393883e0964cd7598563e7f4548d12a1eea4fdf745"),
        // Finite R31 Ring extension; reader/cache/decode algorithms remain unchanged.
        RING_BODY(0x9768,740,"2fe09f8bef2a5da08bf7995bbe9a7f5bcf4493f825a9d40a6caa5e8db85731f9"),
        RING_DELTAS(0x9780,32,"35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86"),
        RING_ANIMATIONS(0x9BDC,8,"4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a"),
        RING_MAPPINGS(0x9BE4,68,"306667dca113be405fd1d78fd69b09152b853236db8ad8fbeb6a73bbde3be7e8");
        final int offset,length;final String sha;
        Asset(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    public record Range(Asset asset,int fileOffset,int length,String sha256) {
        public Range {Objects.requireNonNull(asset);Objects.requireNonNull(sha256);
            if(fileOffset!=asset.offset || length!=asset.length || !sha256.equals(asset.sha))throw new IllegalArgumentException("Only fixed R31A source ranges");}
        public int nativeAddress(){return 0x200000+fileOffset;}public int endExclusive(){return fileOffset+length;}
    }
    private final Identity identity;private final CdProgrammeReader reader;
    private final List<Range> ranges;
    private final CdSonicVisualAssets sonic;private final List<CdAssetBytes> placementRows;
    private final CdObjectPlacements placements;
    private final CdR11bLoadPlan.LevelIndex levelIndex;private final CdR11bLoadPlan.Bounds bounds;private final CdR11bLoadPlan.InitialCentre initialCentre;
    private final CdR31aLoadMetadata loadMetadata;

    private CdR31aAssets(CdProgrammeReader reader)throws IOException {
        this.reader=Objects.requireNonNull(reader);
        if(!reader.stage().equals(CdStagePayload.R31A.stage()))throw new IllegalArgumentException("R31A retained source differs");
        identity=new Identity(CdReleaseVariant.USA,reader.stage(),reader.file(),CdMmdImage.Domain.ORIGINAL_WORD_RAM,reader.mmdSha(),reader.discSha());
        var rr=new ArrayList<Range>();for(var a:Asset.values())rr.add(new Range(a,a.offset,a.length,a.sha));ranges=List.copyOf(rr);
        var index=view(Asset.LEVEL_DATA_INDEX);
        levelIndex=new CdR11bLoadPlan.LevelIndex(new CdR11bLoadPlan.TaggedPointer(index.unsignedLong(0)),
                new CdR11bLoadPlan.TaggedPointer(index.unsignedLong(4)),(int)index.unsignedLong(8),index.slice(12,2),index.slice(14,2));
        var size=view(Asset.CAM_BOUNDS);bounds=new CdR11bLoadPlan.Bounds(size.unsignedWord(0),size.unsignedWord(2),size.unsignedWord(4),size.unsignedWord(6),size.unsignedWord(8),size.unsignedWord(10));
        var start=view(Asset.LEVEL_START_LOC);initialCentre=new CdR11bLoadPlan.InitialCentre(start.unsignedWord(0),start.unsignedWord(2));
        sonic=reader.sonic();
        placements=CdObjectPlacements.decodeR31NativeOrder(this);placementRows=placements.rawRows();loadMetadata=CdR31aLoadMetadata.fromAssets(this);
    }
    public static CdR31aAssets fromPrograms(CdProgramAssets programs,CdStageKey stage)throws IOException {
        Objects.requireNonNull(programs,"actual complete-disc data source");Objects.requireNonNull(stage,"explicit selected stage");
        if(!stage.equals(CdStagePayload.R31A.stage()))throw new UnsupportedOperationException("Only original USA R31A startup data reviewed");
        return new CdR31aAssets(CdProgrammeReader.open(programs,stage));
    }

    public Identity identity(){return identity;}public List<Range> ranges(){return ranges;}
    public Range range(Asset asset){return ranges.get(Objects.requireNonNull(asset).ordinal());}
    public CdAssetBytes view(Asset asset){return reader.view(Objects.requireNonNull(asset));}
    public CdSonicVisualAssets sonic(){return sonic;}
    /** Exact original raw eight-byte rows, retained independently of the typed placement relation. */
    public List<CdAssetBytes> placementRows(){return placementRows;}
    /** Cached native517-row source data; no spawn/World/Frame/current admission. */
    public CdObjectPlacements placements(){return placements;}
    public CdR11bLoadPlan.LevelIndex levelIndex(){return levelIndex;}public CdR11bLoadPlan.Bounds bounds(){return bounds;}
    public CdR11bLoadPlan.InitialCentre initialCentre(){return initialCentre;}
    public CdR31aLoadMetadata loadMetadata(){return loadMetadata;}
    public synchronized CdNemesisAsset.Decoded blocksDecode()throws IOException {return reader.decoded(CdProgrammeReader.Blocks.R31);}
    public CdNemesisAsset.Decoded levelTilesDecode()throws IOException{return loadMetadata.decoded(CdR31aLoadMetadata.SourceId.STAGE_GFX);}
    public synchronized CdStageTerrain terrain()throws IOException {return reader.terrain();}
    // BEGIN fixed R31 programme music source borrow, outside the63-view public inventory.
    record ProgrammeMusicSource(CdAssetBytes consumer,CdAssetBytes commands) { }
    ProgrammeMusicSource programmeMusicSource() {
        requireAuthenticated();
        return new ProgrammeMusicSource(reader.view(CdProgrammeReader.Borrow.MUSIC_CONSUMER),
                reader.view(CdProgrammeReader.Borrow.MUSIC_COMMANDS));
    }
    // END fixed R31 programme music source borrow.
    // BEGIN fixed R31 CCZ12/common-events source borrow 20261107.
    /** Finite private construction binds these six original views to this retained aggregate only. */
    static final class LevelEventsSource {
        private final CdR31aAssets owner;
        private final CdAssetBytes scrollConsumer,common,zoneDirectory,cczDispatch,actDirectory,ccz12;
        private LevelEventsSource(CdR31aAssets owner,CdAssetBytes scrollConsumer,CdAssetBytes common,
                                  CdAssetBytes zoneDirectory,CdAssetBytes cczDispatch,CdAssetBytes actDirectory,CdAssetBytes ccz12) {
            this.owner=owner;this.scrollConsumer=scrollConsumer;this.common=common;this.zoneDirectory=zoneDirectory;
            this.cczDispatch=cczDispatch;this.actDirectory=actDirectory;this.ccz12=ccz12;
        }
        boolean owns(CdR31aAssets expected){return owner==expected;}
        CdAssetBytes scrollConsumer(){return scrollConsumer;}CdAssetBytes common(){return common;}
        CdAssetBytes zoneDirectory(){return zoneDirectory;}CdAssetBytes cczDispatch(){return cczDispatch;}
        CdAssetBytes actDirectory(){return actDirectory;}CdAssetBytes ccz12(){return ccz12;}
    }
    LevelEventsSource levelEventsSource() {
        requireAuthenticated();
        return new LevelEventsSource(this,reader.view(CdProgrammeReader.Borrow.SCROLL_CONSUMER),
                reader.view(CdProgrammeReader.Borrow.COMMON_EVENTS),reader.view(CdProgrammeReader.Borrow.ZONE_DIRECTORY),
                reader.view(CdProgrammeReader.Borrow.CCZ_DISPATCH),reader.view(CdProgrammeReader.Borrow.ACT_DIRECTORY),
                reader.view(CdProgrammeReader.Borrow.CCZ12));
    }
    // END fixed R31 CCZ12/common-events source borrow 20261107.

    // BEGIN fixed R31 native-HUD source borrow 20261107.
    /** Fixed consumer/table custody, outside the original63-view public inventory. */
    static final class HudSource {
        private final CdR31aAssets owner;
        private final CdAssetBytes update,reset,divisors,vint;
        private HudSource(CdR31aAssets owner,CdAssetBytes update,CdAssetBytes reset,CdAssetBytes divisors,CdAssetBytes vint) {
            this.owner=owner;this.update=update;this.reset=reset;this.divisors=divisors;this.vint=vint;
        }
        boolean owns(CdR31aAssets expected){return owner==expected;}
        CdAssetBytes update(){return update;}CdAssetBytes reset(){return reset;}
        CdAssetBytes divisors(){return divisors;}CdAssetBytes vint(){return vint;}
    }
    HudSource hudSource() {
        requireAuthenticated();
        return new HudSource(this,reader.view(CdProgrammeReader.Borrow.HUD_UPDATE),
                reader.view(CdProgrammeReader.Borrow.HUD_RESET),reader.view(CdProgrammeReader.Borrow.HUD_DIVISORS),
                reader.view(CdProgrammeReader.Borrow.HUD_VINT));
    }
    // END fixed R31 native-HUD source borrow 20261107.

    private CdR31aBumperSource bumperSource;
    synchronized CdR31aBumperSource bumperSource(){
        if(bumperSource==null)bumperSource=CdR31aBumperSource.create(this);
        return bumperSource;
    }
    CdProgrammeReader internalReader(){return reader;}
    void requireAuthenticated() {
        reader.requireRetained();
        if(identity.file()!=reader.file() || !identity.stage().equals(reader.stage())
                || identity.variant()!=CdReleaseVariant.USA || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM)throw new IllegalStateException("Retained R31A owner differs");
    }
}
