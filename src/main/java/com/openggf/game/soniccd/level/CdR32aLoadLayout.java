package com.openggf.game.soniccd.level;

import java.util.List;

/**
 * Candidate declarative R32A PLC, section, palette and finite compressed-source layout.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Graphics Lists (Act 2 Variant).asm,
 * r3/section_gfx_2a.asm, stage_data_load_2a.asm and the actual R3 Act2 include graph.
 * No parsing algorithm, asset owner, queue, load service, timing or residency implementation.
 * Shared reader design is pending. Extra read bytes are actual original neighbors required
 * by the existing Java NemesisReader; native prefetch endpoints are not inferred.
 */
public final class CdR32aLoadLayout {
    private CdR32aLoadLayout() { }

    public enum SourceId {
        POINTS_GFX(0x2EDE6, 100, 101, 0x0005, 29, 160, 101,
                "6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363",
                "9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",
                "2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db"),
        CAPSULE_GFX(0x2EE4A, 1704, 1704, 0x8068, 99, 3328, 1703,
                "da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",
                "da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",
                "b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236"),
        BIG_RING_GFX(0x2F4F2, 1034, 1034, 0x806A, 59, 3392, 1033,
                "5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",
                "5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",
                "31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c"),
        GOAL_GFX(0x2F8FC, 448, 448, 0x8018, 86, 768, 448,
                "24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f",
                "24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f",
                "68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a"),
        SIGNPOST_GFX(0x2FABC, 1500, 1500, 0x804C, 115, 2432, 1500,
                "2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67",
                "2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67",
                "7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c"),
        RESULTS_GFX(0x30098, 1354, 1354, 0x8078, 65, 3840, 1354,
                "c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",
                "c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",
                "fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d"),
        TITLE_CARD_GFX(0x30DA2, 940, 940, 0x806D, 71, 3488, 939,
                "8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",
                "8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",
                "68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a"),
        SPRING45_GFX(0x31E2E, 780, 780, 0x002E, 75, 1472, 779,
                "3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",
                "3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",
                "1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b"),
        SPRING_GFX(0x3213A, 546, 547, 0x8024, 80, 1152, 547,
                "19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831",
                "52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",
                "8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416"),
        MONITOR_TIME_GFX(0x3235C, 1540, 1541, 0x8052, 105, 2624, 1541,
                "ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a",
                "a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",
                "7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f"),
        EXPLOSION_GFX(0x32960, 1256, 1256, 0x0046, 89, 2240, 1256,
                "1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1",
                "1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1",
                "434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664"),
        RING_GFX(0x32E48, 244, 244, 0x000E, 46, 448, 244,
                "530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",
                "530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",
                "c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb"),
        HUD_GFX(0x334BC, 300, 300, 0x801B, 49, 864, 300,
                "d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb",
                "d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb",
                "0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc"),
        CHECKPOINT_GFX(0x335E8, 212, 212, 0x800C, 51, 384, 211,
                "e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",
                "e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",
                "b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3"),
        FLOWER_GFX(0x33732, 602, 602, 0x8027, 75, 1248, 602,
                "2aba86b3ad556142fb0884c272daeadf637b15d543573589ff5e59bd298eaa79",
                "2aba86b3ad556142fb0884c272daeadf637b15d543573589ff5e59bd298eaa79",
                "182ce13f0c42220ce1ea285a4ab1e42e553d07311a4ea90042f734b16d42f2b9"),
        TITLE_CARD_TEXT_GFX(0x3398C, 270, 270, 0x8038, 59, 1792, 269,
                "3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8",
                "3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8",
                "66f8b155465c921a24905390269ff5895713252404fdb42eaeabca363731c24d"),
        FLIPPER_GFX(0x3479A, 614, 614, 0x8031, 82, 1568, 613,
                "a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6",
                "a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6",
                "32d31d26c83cd215501847b2a05daaa96e0bd3b1b07cc58ea7e52e8a5adb3ecc"),
        BUMPER_GFX(0x34A00, 532, 533, 0x8024, 72, 1152, 533,
                "26b2d184360742136708185d0a8b1e9fe8357f30e360c6525907eb0dda150e8e",
                "0ef1d0fde20d3a729e08fdcd878d69b1804174aba1bc0b71d799fa2f10cc68a4",
                "3e1d8bc5ed47e6b31ae0481e244c1eba430688e93382ef6e3013aafb83f82c1f"),
        METAL_PLATFORM_GFX(0x34C14, 294, 294, 0x8010, 63, 512, 293,
                "6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7",
                "6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7",
                "19c88e25eab2812cd04b50150e9ba74c5ddd502562ca99d2d87f4a0b0a3a3d93"),
        SPIKES_HV4_GFX(0x34D3A, 370, 370, 0x8020, 69, 1024, 369,
                "06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",
                "06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",
                "b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf"),
        BLOCK_GFX(0x34EAC, 228, 228, 0x8010, 55, 512, 227,
                "578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23",
                "578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23",
                "6f0bf79e012c5d0700eb06d03bb32c36835955c8bc535de46196e22c895365ee"),
        RETRACT_BLOCK_GFX(0x34F90, 186, 186, 0x8010, 42, 512, 186,
                "2ea56ce4303b2e752e785a5c1469e9ae432ee2085a54ff7e39bc3ad3c4a3df99",
                "2ea56ce4303b2e752e785a5c1469e9ae432ee2085a54ff7e39bc3ad3c4a3df99",
                "8b409d1e9aae8d1bd80ca686d0690bbc40621255abf066549ab260edc3874b44"),
        ONE_WAY_BARRIER_GFX(0x3504A, 124, 125, 0x0008, 40, 256, 125,
                "e22f0a563821ca283041bc05efe241f7ca9aad3a0e03e65978a314538b873ed7",
                "7afe047cfa59195eb46cce051fe096f0bbd9eb6700c2e2864ee542914312cd96",
                "3b473b92860fa335caa3dff6c078a2058db43d1c2f25bb755e14914e2342eeb0"),
        FIRE_SHOOT_GFX(0x350C6, 384, 384, 0x8018, 63, 768, 384,
                "450abb371010d7092111caf083ac5ea491b62ae780c33f03458a44bd5fe201fd",
                "450abb371010d7092111caf083ac5ea491b62ae780c33f03458a44bd5fe201fd",
                "0f640c27cc79f78a8a739a95c5ef77ee3f3a2f86d06bb04059b289f86104cbd0"),
        ROTATE_PLATFORM_GFX(0x35246, 524, 524, 0x0021, 106, 1056, 524,
                "a5d186d7febcf0540959164e292249dc6255af5125876751f40fa1dac4aab1ca",
                "a5d186d7febcf0540959164e292249dc6255af5125876751f40fa1dac4aab1ca",
                "e1412d7cf5d3c87e9c3ce5b9b60c02aaa4b1d7cf152ba16f7d2119248e8a56e0"),
        KAMA_KAMA_GFX(0x35452, 926, 926, 0x002F, 94, 1504, 925,
                "9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437",
                "9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437",
                "aa87d689aa18abbd35ed5b3cc848fc36cc2d8955f70b71d040afb660b1d5ebe6"),
        SPIKES_HV2_GFX(0x357F0, 218, 218, 0x8010, 61, 512, 217,
                "a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80",
                "a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80",
                "70c043d0ed67c1dff044a6c0b5232507288d29ab5a165d877c2d409e8df59a12"),
        GA_GFX(0x35BD6, 1280, 1280, 0x0044, 90, 2176, 1280,
                "6627daf981e7880f49bccd5b0983572b5d4ca704807be615056f51155f32aa4b",
                "6627daf981e7880f49bccd5b0983572b5d4ca704807be615056f51155f32aa4b",
                "1074e220e61614dc3824e8fbc157331d8396a3bb9edd263c86b3e2cce05c57bd"),
        TENTOU_GFX(0x360D6, 790, 790, 0x002B, 90, 1376, 789,
                "68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097",
                "68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097",
                "a9468945d38304b1508aa0d9ac8d48751d7120a8b8dfcebcbf89888422e8d5f8"),
        SPIKE_BOMB_GFX(0x363EC, 194, 194, 0x0008, 44, 256, 193,
                "fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70",
                "fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70",
                "45ea77c701f20807ddf55930af0a64115d5319888dc0c659649a66fb1f38f4bf"),
        TELEPORTER_GFX(0x364AE, 280, 281, 0x800E, 63, 448, 281,
                "4b9035c44de8a7f80ee6d7d66e782e698ee129ec33066ae4a00535d36c7c1000",
                "f5640b74beeb7f4fe01dac967fef31f83bab6bfa68d5711fcd8657779a79cb0b",
                "ad526d8eaec7150cc7120b8536be47238f3df2df4dd0b1fcd784b00d7e58f347"),
        POCKET_GFX_A(0x365C6, 178, 179, 0x800B, 50, 352, 179,
                "24a424281d21cf85626f3aca6beb643541261471120b8fe6eac089a394f5feed",
                "16a912563b1b30bee11e12fa4fbd3608e627737be559d0875b7c05b0ffd5b6d0",
                "fcca6871342b2a4d75172a8457f1059e7493ede78d84ee79f267524d9e3d6ceb"),
        BOSS_BARRIER_GFX(0x36678, 138, 138, 0x8008, 42, 256, 138,
                "810fa937d70a97c7a15a7d177ee4a1cb4e518d7eacf8aca146159f3900a762f6",
                "810fa937d70a97c7a15a7d177ee4a1cb4e518d7eacf8aca146159f3900a762f6",
                "bc136c73eadb3ab4f6cb630dd8fe0a5bf757c5269e5c49748ab05095499f49e1"),
        BOUNCE_PLATFORM_GFX_AB(0x36702, 212, 212, 0x000C, 62, 384, 211,
                "f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d",
                "f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d",
                "b10d3c74b6379fb0c9f08504db8f90c1f9a0e685f407736b0e984c03cfefe21f"),
        GLASS_BREAK_GFX_A(0x367D6, 204, 204, 0x0012, 42, 576, 204,
                "efcebca9c75ed6466128e803c0910b96a832d9680d37f0824a535d4cd328eee4",
                "efcebca9c75ed6466128e803c0910b96a832d9680d37f0824a535d4cd328eee4",
                "aa3fff8b9c396f2b41f774d7dbdb34345a3c76b5f453ab345935a03894d1e8a9"),
        SPIKE_CHAIN_GFX(0x368A2, 216, 216, 0x8008, 61, 256, 216,
                "a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226",
                "a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226",
                "ea3b4aa573268a91e6218b02b015fc3dcbf10d7ee50ec55afaef3cfcfa105d05"),
        ROBOT_TRANSPORT_GFX_A(0x3697A, 332, 332, 0x000F, 77, 480, 332,
                "26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda",
                "26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda",
                "ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67"),
        ANIMALS_GFX(0x36AC6, 432, 432, 0x8014, 66, 640, 432,
                "6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb",
                "6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb",
                "fb65820be406480e1d8322e752b3ad4e60b144a5efd09057604f37aae7c6903e"),
        STAGE_BLOCKS(0x3937E, 3958, 3958, 0x80A4, 59, 5248, 3958,
                "c7d1ff8fb9f5d7f2bf46b632befdeaf72136854e94f2cb055bc057d5e8b4dc5a",
                "c7d1ff8fb9f5d7f2bf46b632befdeaf72136854e94f2cb055bc057d5e8b4dc5a",
                "fd9b10cf2a1ad5cd28e14a27e06e3ea0050ca0ce9009057d9a527dcb9bc932f2"),
        STAGE_GFX(0x3A2F4, 10162, 10162, 0x82B6, 97, 22208, 10161,
                "f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37",
                "f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37",
                "db0664cea0d185e7817e63918fc20350f09afda60e8caed64d0ce19707df8591"),
        BIG_RING_FLASH_GFX(0xB4A2, 384, 384, 0x804D, 36, 2464, 384,
                "6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7",
                "6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7",
                "39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6");

        private final int fileOffset, includeBytes, readerBytes, headerWord, codeTableBytes, outputBytes, consumedInputBytes;
        private final String includeSha256, readerSha256, outputSha256;
        SourceId(int fileOffset, int includeBytes, int readerBytes, int headerWord, int codeTableBytes, int outputBytes,
                 int consumedInputBytes, String includeSha256, String readerSha256, String outputSha256) {
            this.fileOffset = fileOffset; this.includeBytes = includeBytes; this.readerBytes = readerBytes;
            this.headerWord = headerWord; this.codeTableBytes = codeTableBytes; this.outputBytes = outputBytes; this.consumedInputBytes = consumedInputBytes;
            this.includeSha256 = includeSha256; this.readerSha256 = readerSha256; this.outputSha256 = outputSha256;
        }
        public int fileOffset() { return fileOffset; }
        public int nativeAddress() { return 0x200000 + fileOffset; }
        public int includeBytes() { return includeBytes; }
        public int readerBytes() { return readerBytes; }
        public int headerWord() { return headerWord; }
        public int codeTableBytes() { return codeTableBytes; }
        public int outputBytes() { return outputBytes; }
        public int consumedInputBytes() { return consumedInputBytes; }
        public String includeSha256() { return includeSha256; }
        public String readerSha256() { return readerSha256; }
        public String outputSha256() { return outputSha256; }
    }

    /** Declarative values only, never a source authentication or art service capability. */
    public record PlcEntry(SourceId source, int vramAddress) { }
    public record PlcList(int nativeId, int fileOffset, List<PlcEntry> entries) {
        public PlcList { entries = List.copyOf(entries); }
    }
    public record PaletteBinding(int nativeId, CdR32aDataLayout.Asset source,
                                 int destinationWord, int longwordCounter) { }

    private static final List<PlcEntry> BODY_F1A0 = List.of(
            new PlcEntry(SourceId.STAGE_GFX, 0x0000),
            new PlcEntry(SourceId.CHECKPOINT_GFX, 0xD960));

    private static final List<PlcEntry> BODY_F1AE = List.of(
            new PlcEntry(SourceId.GOAL_GFX, 0x6000),
            new PlcEntry(SourceId.TITLE_CARD_GFX, 0x6C00),
            new PlcEntry(SourceId.TITLE_CARD_TEXT_GFX, 0x7A00),
            new PlcEntry(SourceId.TENTOU_GFX, 0x8D40),
            new PlcEntry(SourceId.SPIKE_BOMB_GFX, 0x92A0),
            new PlcEntry(SourceId.FLIPPER_GFX, 0x93A0),
            new PlcEntry(SourceId.BUMPER_GFX, 0x99C0),
            new PlcEntry(SourceId.SPRING45_GFX, 0x9E40),
            new PlcEntry(SourceId.SPRING_GFX, 0xA400),
            new PlcEntry(SourceId.HUD_GFX, 0xAD00),
            new PlcEntry(SourceId.MONITOR_TIME_GFX, 0xB500),
            new PlcEntry(SourceId.EXPLOSION_GFX, 0xD000),
            new PlcEntry(SourceId.POINTS_GFX, 0xD8C0),
            new PlcEntry(SourceId.FLOWER_GFX, 0xDAE0),
            new PlcEntry(SourceId.RING_GFX, 0xF5C0));

    private static final List<PlcEntry> BODY_F20A = List.of(
            new PlcEntry(SourceId.SPIKES_HV4_GFX, 0x63C0),
            new PlcEntry(SourceId.BLOCK_GFX, 0x67C0),
            new PlcEntry(SourceId.METAL_PLATFORM_GFX, 0x69C0),
            new PlcEntry(SourceId.BOUNCE_PLATFORM_GFX_AB, 0x6BC0),
            new PlcEntry(SourceId.ROBOT_TRANSPORT_GFX_A, 0x6D40),
            new PlcEntry(SourceId.GLASS_BREAK_GFX_A, 0x7160),
            new PlcEntry(SourceId.RETRACT_BLOCK_GFX, 0x73E0),
            new PlcEntry(SourceId.TELEPORTER_GFX, 0x75E0),
            new PlcEntry(SourceId.POCKET_GFX_A, 0x75E0),
            new PlcEntry(SourceId.SPIKE_CHAIN_GFX, 0x77A0),
            new PlcEntry(SourceId.ANIMALS_GFX, 0x7B40),
            new PlcEntry(SourceId.KAMA_KAMA_GFX, 0x7E40),
            new PlcEntry(SourceId.GA_GFX, 0x8420));

    private static final List<PlcEntry> BODY_F25A = List.of(
            new PlcEntry(SourceId.SPIKES_HV4_GFX, 0x63C0),
            new PlcEntry(SourceId.BLOCK_GFX, 0x67C0),
            new PlcEntry(SourceId.METAL_PLATFORM_GFX, 0x69C0),
            new PlcEntry(SourceId.BOUNCE_PLATFORM_GFX_AB, 0x6BC0),
            new PlcEntry(SourceId.ROTATE_PLATFORM_GFX, 0x6D40),
            new PlcEntry(SourceId.GLASS_BREAK_GFX_A, 0x7160),
            new PlcEntry(SourceId.RETRACT_BLOCK_GFX, 0x73E0),
            new PlcEntry(SourceId.TELEPORTER_GFX, 0x75E0),
            new PlcEntry(SourceId.POCKET_GFX_A, 0x75E0),
            new PlcEntry(SourceId.SPIKE_CHAIN_GFX, 0x77A0),
            new PlcEntry(SourceId.ANIMALS_GFX, 0x7B40),
            new PlcEntry(SourceId.KAMA_KAMA_GFX, 0x7E40),
            new PlcEntry(SourceId.GA_GFX, 0x8420));

    private static final List<PlcEntry> BODY_F2AA = List.of(
            new PlcEntry(SourceId.ROBOT_TRANSPORT_GFX_A, 0x6D40));

    private static final List<PlcEntry> BODY_F2B2 = List.of(
            new PlcEntry(SourceId.ROTATE_PLATFORM_GFX, 0x6D40));

    private static final List<PlcEntry> BODY_F2BA = List.of(
            new PlcEntry(SourceId.RESULTS_GFX, 0x7880));

    private static final List<PlcEntry> BODY_F2C2 = List.of(
            new PlcEntry(SourceId.SIGNPOST_GFX, 0x8780),
            new PlcEntry(SourceId.BIG_RING_GFX, 0x9100),
            new PlcEntry(SourceId.BIG_RING_FLASH_GFX, 0x7DE0));

    public static final List<PlcList> PLC_LISTS = List.of(
            new PlcList(0, 0xF1A0, BODY_F1A0),
            new PlcList(1, 0xF1AE, BODY_F1AE),
            new PlcList(2, 0xF20A, BODY_F20A),
            new PlcList(3, 0xF1A0, BODY_F1A0),
            new PlcList(4, 0xF25A, BODY_F25A),
            new PlcList(5, 0xF2AA, BODY_F2AA),
            new PlcList(6, 0xF2B2, BODY_F2B2),
            new PlcList(7, 0xF20A, BODY_F20A),
            new PlcList(8, 0xF20A, BODY_F20A),
            new PlcList(9, 0xF20A, BODY_F20A),
            new PlcList(10, 0xF20A, BODY_F20A),
            new PlcList(11, 0xF20A, BODY_F20A),
            new PlcList(12, 0xF20A, BODY_F20A),
            new PlcList(13, 0xF20A, BODY_F20A),
            new PlcList(14, 0xF20A, BODY_F20A),
            new PlcList(15, 0xF20A, BODY_F20A),
            new PlcList(16, 0xF2BA, BODY_F2BA),
            new PlcList(17, 0xF20A, BODY_F20A),
            new PlcList(18, 0xF2C2, BODY_F2C2));

    public static final List<CdR11bLoadPlan.SectionArt> SECTIONS = List.of(
            new CdR11bLoadPlan.SectionArt(0x1400, 2, 5),
            new CdR11bLoadPlan.SectionArt(0xFFFF, 4, 6));

    public static final List<PaletteBinding> PALETTES = List.of(
            new PaletteBinding(0, CdR32aDataLayout.Asset.PAL_S1_TITLE, 0xFB00, 0x1F),
            new PaletteBinding(1, CdR32aDataLayout.Asset.PAL_S1_TITLE, 0xFB00, 0x1F),
            new PaletteBinding(2, CdR32aDataLayout.Asset.PAL_S1_LEVEL_SELECT, 0xFB00, 0x1F),
            new PaletteBinding(3, CdR32aDataLayout.Asset.PAL_SONIC, 0xFB00, 7),
            new PaletteBinding(4, CdR32aDataLayout.Asset.PAL_LEVEL, 0xFB20, 0x17),
            new PaletteBinding(5, CdR32aDataLayout.Asset.PAL_LEVEL_END, 0xFB20, 0x17));
}
