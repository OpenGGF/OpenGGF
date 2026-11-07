package com.openggf.game.soniccd.level;
import java.io.IOException;import java.util.*;
/**
 * Exact original-USA R31A source/load metadata and pure existing-decoder products.
 * N1ecc5f3a Graphics Lists/Section Data/Palette Data (Act1Present), r3/data_1a.asm.
 * No prepare/service/queue/timing/current/residency/presentation/World grant. Include length
 * and existing Java-reader extent are independent; extra bytes are genuine original neighbors.
 * Native prefetch endpoints are not asserted by this Java-reader descriptor.
 */
public final class CdR31aLoadMetadata {
    public enum SourceId {
        STAGE_GFX("StageGfx",0x37BEE,10162,10162,0x82B6,97,10161,"f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37","f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37","db0664cea0d185e7817e63918fc20350f09afda60e8caed64d0ce19707df8591"),
        CHECKPOINT_GFX("CheckpointGfx",0x335E8,212,212,0x800C,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3"),
        GOAL_GFX("GoalGfx",0x2F8FC,448,448,0x8018,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a"),
        TITLE_CARD_GFX("TitleCardGfx",0x30DA2,940,940,0x806D,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a"),
        TITLE_CARD_TEXT_GFX("TitleCardTextGfx",0x3398C,270,270,0x8038,59,269,"3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8","3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8","66f8b155465c921a24905390269ff5895713252404fdb42eaeabca363731c24d"),
        TENTOU_GFX("TentouGfx",0x360D6,790,790,0x2B,90,789,"68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097","68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097","a9468945d38304b1508aa0d9ac8d48751d7120a8b8dfcebcbf89888422e8d5f8"),
        SPIKE_BOMB_GFX("SpikeBombGfx",0x363EC,194,194,0x8,44,193,"fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70","fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70","45ea77c701f20807ddf55930af0a64115d5319888dc0c659649a66fb1f38f4bf"),
        FLIPPER_GFX("FlipperGfx",0x3479A,614,614,0x8031,82,613,"a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6","a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6","32d31d26c83cd215501847b2a05daaa96e0bd3b1b07cc58ea7e52e8a5adb3ecc"),
        BUMPER_GFX("BumperGfx",0x34A00,532,533,0x8024,72,533,"26b2d184360742136708185d0a8b1e9fe8357f30e360c6525907eb0dda150e8e","0ef1d0fde20d3a729e08fdcd878d69b1804174aba1bc0b71d799fa2f10cc68a4","3e1d8bc5ed47e6b31ae0481e244c1eba430688e93382ef6e3013aafb83f82c1f"),
        SPRING45_GFX("Spring45Gfx",0x31E2E,780,780,0x2E,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b"),
        SPRING_GFX("SpringGfx",0x3213A,546,547,0x8024,80,547,"19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831","52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416"),
        HUD_GFX("HudGfx",0x334BC,300,300,0x801B,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc"),
        MONITOR_TIME_GFX("MonitorTimeGfx",0x3235C,1540,1541,0x8052,105,1541,"ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a","a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f"),
        EXPLOSION_GFX("ExplosionGfx",0x32960,1256,1256,0x46,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664"),
        POINTS_GFX("PointsGfx",0x2EDE6,100,101,0x5,29,101,"6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363","9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db"),
        FLOWER_GFX("FlowerGfx",0x33732,602,602,0x8027,75,602,"2aba86b3ad556142fb0884c272daeadf637b15d543573589ff5e59bd298eaa79","2aba86b3ad556142fb0884c272daeadf637b15d543573589ff5e59bd298eaa79","182ce13f0c42220ce1ea285a4ab1e42e553d07311a4ea90042f734b16d42f2b9"),
        RING_GFX("RingGfx",0x32E48,244,244,0xE,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb"),
        SPIKES_HV2_GFX("SpikesHV2Gfx",0x357F0,218,218,0x8010,61,217,"a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80","a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80","70c043d0ed67c1dff044a6c0b5232507288d29ab5a165d877c2d409e8df59a12"),
        BLOCK_GFX("BlockGfx",0x34EAC,228,228,0x8010,55,227,"578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23","578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23","6f0bf79e012c5d0700eb06d03bb32c36835955c8bc535de46196e22c895365ee"),
        METAL_PLATFORM_GFX("MetalPlatformGfx",0x34C14,294,294,0x8010,63,293,"6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7","6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7","19c88e25eab2812cd04b50150e9ba74c5ddd502562ca99d2d87f4a0b0a3a3d93"),
        SPIKES_HV4_GFX("SpikesHV4Gfx",0x34D3A,370,370,0x8020,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf"),
        BOUNCE_PLATFORM_GFX_AB("BouncePlatformGfxAB",0x36702,212,212,0xC,62,211,"f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d","f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d","b10d3c74b6379fb0c9f08504db8f90c1f9a0e685f407736b0e984c03cfefe21f"),
        ONE_WAY_BARRIER_GFX("OneWayBarrierGfx",0x3504A,124,125,0x8,40,125,"e22f0a563821ca283041bc05efe241f7ca9aad3a0e03e65978a314538b873ed7","7afe047cfa59195eb46cce051fe096f0bbd9eb6700c2e2864ee542914312cd96","3b473b92860fa335caa3dff6c078a2058db43d1c2f25bb755e14914e2342eeb0"),
        FIRE_SHOOT_GFX("FireShootGfx",0x350C6,384,384,0x8018,63,384,"450abb371010d7092111caf083ac5ea491b62ae780c33f03458a44bd5fe201fd","450abb371010d7092111caf083ac5ea491b62ae780c33f03458a44bd5fe201fd","0f640c27cc79f78a8a739a95c5ef77ee3f3a2f86d06bb04059b289f86104cbd0"),
        RETRACT_BLOCK_GFX("RetractBlockGfx",0x34F90,186,186,0x8010,42,186,"2ea56ce4303b2e752e785a5c1469e9ae432ee2085a54ff7e39bc3ad3c4a3df99","2ea56ce4303b2e752e785a5c1469e9ae432ee2085a54ff7e39bc3ad3c4a3df99","8b409d1e9aae8d1bd80ca686d0690bbc40621255abf066549ab260edc3874b44"),
        POCKET_GFX_A("PocketGfxA",0x365C6,178,179,0x800B,50,179,"24a424281d21cf85626f3aca6beb643541261471120b8fe6eac089a394f5feed","16a912563b1b30bee11e12fa4fbd3608e627737be559d0875b7c05b0ffd5b6d0","fcca6871342b2a4d75172a8457f1059e7493ede78d84ee79f267524d9e3d6ceb"),
        SPIKE_CHAIN_GFX("SpikeChainGfx",0x368A2,216,216,0x8008,61,216,"a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226","a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226","ea3b4aa573268a91e6218b02b015fc3dcbf10d7ee50ec55afaef3cfcfa105d05"),
        ANIMALS_GFX("AnimalsGfx",0x36AC6,432,432,0x8014,66,432,"6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb","6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb","fb65820be406480e1d8322e752b3ad4e60b144a5efd09057604f37aae7c6903e"),
        KAMA_KAMA_GFX("KamaKamaGfx",0x35452,926,926,0x2F,94,925,"9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437","9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437","aa87d689aa18abbd35ed5b3cc848fc36cc2d8955f70b71d040afb660b1d5ebe6"),
        GA_GFX("GaGfx",0x35BD6,1280,1280,0x44,90,1280,"6627daf981e7880f49bccd5b0983572b5d4ca704807be615056f51155f32aa4b","6627daf981e7880f49bccd5b0983572b5d4ca704807be615056f51155f32aa4b","1074e220e61614dc3824e8fbc157331d8396a3bb9edd263c86b3e2cce05c57bd"),
        ROBOT_TRANSPORT_GFX_A("RobotTransportGfxA",0x3697A,332,332,0xF,77,332,"26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda","26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda","ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67"),
        AMY_ROSE_GFX("AmyRoseGfx",0x3B486,1998,1998,0x71,110,1998,"9b3ec92dafac2c1b7baa8d21b25403f5736350b08862787dfd9bf599f21724ea","9b3ec92dafac2c1b7baa8d21b25403f5736350b08862787dfd9bf599f21724ea","02fc72328e5607bd8824795801768829a3d1521f23b9d8eef890eab64f796a20"),
        METAL_SONIC_GFX("MetalSonicGfx",0x3AAD8,2478,2479,0x808F,97,2479,"b74f3af4a576a1a1ac39a37df73a9355cb9842308a6a786653032c1c3aa6e115","89f9db5a9c5833c64cf47442c56d30b8a909955ce1b557040dfef3ff60e5d0df","52ce98ccdc2ad3cdbe3b21a333883f3f50987b7a0f2292f2154e38f425e4f7d5"),
        RESULTS_GFX("ResultsGfx",0x30098,1354,1354,0x8078,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d"),
        SIGNPOST_GFX("SignpostGfx",0x2FABC,1500,1500,0x804C,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c"),
        BIG_RING_GFX("BigRingGfx",0x2F4F2,1034,1034,0x806A,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c"),
        BIG_RING_FLASH("BigRingFlash",0xDCD0,384,384,0x804D,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6");
        private final String label,includeSha,readerSha,decodedSha;private final int offset,include,reader,header,table,consumed;
        SourceId(String label,int offset,int include,int reader,int header,int table,int consumed,String includeSha,String readerSha,String decodedSha){
            this.label=label;this.offset=offset;this.include=include;this.reader=reader;this.header=header;this.table=table;this.consumed=consumed;
            this.includeSha=includeSha;this.readerSha=readerSha;this.decodedSha=decodedSha;
        }
    }
    /** A finite descriptive value. Only the authenticated retained owner supplies bytes/products. */
    public record Source(SourceId id) {
        public Source{Objects.requireNonNull(id);}
        public String nativeLabel(){return id.label;}public int fileOffset(){return id.offset;}
        public long nativeAddress(){return 0x200000L+id.offset;}public int includeBytes(){return id.include;}
        public int javaReaderBytes(){return id.reader;}public int headerWord(){return id.header;}
        public int codeTableBytes(){return id.table;}public int expectedJavaConsumedBytes(){return id.consumed;}
        public int outputBytes(){return (id.header&0x7FFF)*32;}public String includeSha256(){return id.includeSha;}
        public String javaReaderSha256(){return id.readerSha;}public String decodedSha256(){return id.decodedSha;}
    }
    public record PlcEntry(SourceId source,int vramAddress){public PlcEntry{Objects.requireNonNull(source);if((vramAddress&~65535)!=0 || (vramAddress&31)!=0 || (source.header&0x7FFF)*32>65536-vramAddress)throw new IllegalArgumentException("R31A PLC output range");}}
    public record PlcList(int nativeId,int bodyFileOffset,List<PlcEntry> entries){public PlcList{entries=List.copyOf(entries);if(nativeId<0||nativeId>=19||entries.isEmpty()||entries.size()>15||bodyFileOffset<0xFAF6||bodyFileOffset>0xFCE6||2+6*entries.size()>0xFCE6-bodyFileOffset)throw new IllegalArgumentException("R31A finite PLC body");}}
    public record Palette(int nativeId,int destinationWord,int longwordCounter,CdR31aAssets.Asset source,CdAssetBytes bytes){public Palette{Objects.requireNonNull(source);Objects.requireNonNull(bytes);if(nativeId<0||nativeId>=6||(destinationWord&~65535)!=0||longwordCounter<0||bytes.length()!=(longwordCounter+1)*4)throw new IllegalArgumentException("R31A palette source/count");}}
    public record Cycle(CdAssetBytes script,CdAssetBytes colors){public Cycle{Objects.requireNonNull(script);Objects.requireNonNull(colors);}}
    private final CdR31aAssets assets;private final List<Source> sources;private final Map<Long,Source> addresses;
    private final List<PlcList> plcs;private final List<Palette> palettes;private final List<Cycle> cycles;
    private final List<CdR11bLoadPlan.SectionArt> sections;private final List<Integer> backgroundPointers;
    private CdR31aLoadMetadata(CdR31aAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual retained R31A owner");assets.requireAuthenticated();
        var list=new ArrayList<Source>();var map=new HashMap<Long,Source>();
        for(var id:SourceId.values()){var s=new Source(id);if(id.offset<0 || id.reader<id.include || id.reader>id.include+1 || id.consumed>id.reader
                || id.table<3 || id.table>id.consumed || id.reader>0x40000-id.offset || map.putIfAbsent(s.nativeAddress(),s)!=null)throw new IllegalArgumentException("R31A fixed source extent differs");list.add(s);}
        sources=List.copyOf(list);addresses=Map.copyOf(map);
        var tables=assets.internalReader().loadTables();
        var adaptedBodies=new IdentityHashMap<CdProgrammeLoadTables.Body,List<PlcEntry>>();
        plcs=tables.plcs().stream().map(p->new PlcList(p.nativeId(),p.body().fileOffset(),
            adaptedBodies.computeIfAbsent(p.body(),body->List.copyOf(body.entries().stream()
                .map(e->new PlcEntry((SourceId)e.source(),e.vramAddress())).toList())))).toList();
        palettes=tables.palettes().stream().map(p->new Palette(p.nativeId(),p.destinationWord(),p.longwordCounter(),
            (CdR31aAssets.Asset)p.source(),p.bytes())).toList();
        cycles=tables.cycles().stream().map(c->new Cycle(c.script(),c.colors())).toList();
        sections=tables.sections();backgroundPointers=tables.backgroundPointers();
    }
    public static CdR31aLoadMetadata fromAssets(CdR31aAssets assets){return new CdR31aLoadMetadata(assets);}
    public boolean ownsAssets(CdR31aAssets expected){return assets==expected;}public CdR31aAssets.Identity identity(){return assets.identity();}
    public List<Source> sources(){return sources;}public Source source(SourceId id){return sources.get(Objects.requireNonNull(id).ordinal());}
    public Source source(long nativeAddress){var s=addresses.get(nativeAddress);if(s==null)throw new IllegalArgumentException("Unknown R31A compressed source");return s;}
    public List<PlcList> plcs(){return plcs;}public List<Palette> palettes(){return palettes;}public List<Cycle> cycles(){return cycles;}
    public List<CdR11bLoadPlan.SectionArt> sections(){return sections;}public List<Integer> backgroundPointers(){return backgroundPointers;}
    /** Describes Main's actual calls only; the caller owns the real native branch/order/adoption. */
    public List<Integer> immediatePlcIds(){return List.of(3,1);}public int conditionalQueuedPlcId(){return 2;}
    public synchronized CdAssetBytes canonicalView(SourceId id){return assets.internalReader().canonicalInput(id);}
    public synchronized CdAssetBytes javaView(SourceId id){return assets.internalReader().javaInput(id);}
    public synchronized CdNemesisAsset.Decoded decoded(SourceId id)throws IOException {return assets.internalReader().decoded(id);}
}
