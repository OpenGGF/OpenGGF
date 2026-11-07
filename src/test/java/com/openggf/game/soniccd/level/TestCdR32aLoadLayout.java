package com.openggf.game.soniccd.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Candidate declarative layout validation through the original media pipeline; Java UNRUN. */
class TestCdR32aLoadLayout {
    private static final String SOURCES = """
POINTS_GFX 2EDE6 100 101 0005 160 101 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
CAPSULE_GFX 2EE4A 1704 1704 8068 3328 1703 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808 b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236
BIG_RING_GFX 2F4F2 1034 1034 806A 3392 1033 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
GOAL_GFX 2F8FC 448 448 8018 768 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
SIGNPOST_GFX 2FABC 1500 1500 804C 2432 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
RESULTS_GFX 30098 1354 1354 8078 3840 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
TITLE_CARD_GFX 30DA2 940 940 806D 3488 939 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
SPRING45_GFX 31E2E 780 780 002E 1472 779 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
SPRING_GFX 3213A 546 547 8024 1152 547 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
MONITOR_TIME_GFX 3235C 1540 1541 8052 2624 1541 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
EXPLOSION_GFX 32960 1256 1256 0046 2240 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
RING_GFX 32E48 244 244 000E 448 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
HUD_GFX 334BC 300 300 801B 864 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
CHECKPOINT_GFX 335E8 212 212 800C 384 211 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
FLOWER_GFX 33732 602 602 8027 1248 602 2aba86b3ad556142fb0884c272daeadf637b15d543573589ff5e59bd298eaa79 182ce13f0c42220ce1ea285a4ab1e42e553d07311a4ea90042f734b16d42f2b9
TITLE_CARD_TEXT_GFX 3398C 270 270 8038 1792 269 3d5ca60e7c8f253ed0665fd0af32c7e428ac4afc2c6cb77fda80a2cb30dbc1f8 66f8b155465c921a24905390269ff5895713252404fdb42eaeabca363731c24d
FLIPPER_GFX 3479A 614 614 8031 1568 613 a986f5b9a93e4cf0297c033a1955f1937ebb8280941e2b6d8ca0e42ca38272b6 32d31d26c83cd215501847b2a05daaa96e0bd3b1b07cc58ea7e52e8a5adb3ecc
BUMPER_GFX 34A00 532 533 8024 1152 533 0ef1d0fde20d3a729e08fdcd878d69b1804174aba1bc0b71d799fa2f10cc68a4 3e1d8bc5ed47e6b31ae0481e244c1eba430688e93382ef6e3013aafb83f82c1f
METAL_PLATFORM_GFX 34C14 294 294 8010 512 293 6c0ed1ff94e4171caace147ecf427d936a940a16077fb2e628d284af0966a8d7 19c88e25eab2812cd04b50150e9ba74c5ddd502562ca99d2d87f4a0b0a3a3d93
SPIKES_HV4_GFX 34D3A 370 370 8020 1024 369 06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941 b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf
BLOCK_GFX 34EAC 228 228 8010 512 227 578176f5fa319d634f48495c38065d2891aeffb3ae203f5229109eb84e13ec23 6f0bf79e012c5d0700eb06d03bb32c36835955c8bc535de46196e22c895365ee
RETRACT_BLOCK_GFX 34F90 186 186 8010 512 186 2ea56ce4303b2e752e785a5c1469e9ae432ee2085a54ff7e39bc3ad3c4a3df99 8b409d1e9aae8d1bd80ca686d0690bbc40621255abf066549ab260edc3874b44
ONE_WAY_BARRIER_GFX 3504A 124 125 0008 256 125 7afe047cfa59195eb46cce051fe096f0bbd9eb6700c2e2864ee542914312cd96 3b473b92860fa335caa3dff6c078a2058db43d1c2f25bb755e14914e2342eeb0
FIRE_SHOOT_GFX 350C6 384 384 8018 768 384 450abb371010d7092111caf083ac5ea491b62ae780c33f03458a44bd5fe201fd 0f640c27cc79f78a8a739a95c5ef77ee3f3a2f86d06bb04059b289f86104cbd0
ROTATE_PLATFORM_GFX 35246 524 524 0021 1056 524 a5d186d7febcf0540959164e292249dc6255af5125876751f40fa1dac4aab1ca e1412d7cf5d3c87e9c3ce5b9b60c02aaa4b1d7cf152ba16f7d2119248e8a56e0
KAMA_KAMA_GFX 35452 926 926 002F 1504 925 9860f3101159fb13e2439a5964d1d957eef669e911b46dd52695a6b2433b4437 aa87d689aa18abbd35ed5b3cc848fc36cc2d8955f70b71d040afb660b1d5ebe6
SPIKES_HV2_GFX 357F0 218 218 8010 512 217 a22061edc90f38168d76f08fe3f544862313030d26bc192726ee0849ecebda80 70c043d0ed67c1dff044a6c0b5232507288d29ab5a165d877c2d409e8df59a12
GA_GFX 35BD6 1280 1280 0044 2176 1280 6627daf981e7880f49bccd5b0983572b5d4ca704807be615056f51155f32aa4b 1074e220e61614dc3824e8fbc157331d8396a3bb9edd263c86b3e2cce05c57bd
TENTOU_GFX 360D6 790 790 002B 1376 789 68b42bec569d58ea87f93374d02e2bcdde6e25f35286d533412e3ff6fbbc3097 a9468945d38304b1508aa0d9ac8d48751d7120a8b8dfcebcbf89888422e8d5f8
SPIKE_BOMB_GFX 363EC 194 194 0008 256 193 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70 45ea77c701f20807ddf55930af0a64115d5319888dc0c659649a66fb1f38f4bf
TELEPORTER_GFX 364AE 280 281 800E 448 281 f5640b74beeb7f4fe01dac967fef31f83bab6bfa68d5711fcd8657779a79cb0b ad526d8eaec7150cc7120b8536be47238f3df2df4dd0b1fcd784b00d7e58f347
POCKET_GFX_A 365C6 178 179 800B 352 179 16a912563b1b30bee11e12fa4fbd3608e627737be559d0875b7c05b0ffd5b6d0 fcca6871342b2a4d75172a8457f1059e7493ede78d84ee79f267524d9e3d6ceb
BOSS_BARRIER_GFX 36678 138 138 8008 256 138 810fa937d70a97c7a15a7d177ee4a1cb4e518d7eacf8aca146159f3900a762f6 bc136c73eadb3ab4f6cb630dd8fe0a5bf757c5269e5c49748ab05095499f49e1
BOUNCE_PLATFORM_GFX_AB 36702 212 212 000C 384 211 f09700e63813b634c6d447dae1ce0a1adf0369bedfaad0f58051d011b1b69e2d b10d3c74b6379fb0c9f08504db8f90c1f9a0e685f407736b0e984c03cfefe21f
GLASS_BREAK_GFX_A 367D6 204 204 0012 576 204 efcebca9c75ed6466128e803c0910b96a832d9680d37f0824a535d4cd328eee4 aa3fff8b9c396f2b41f774d7dbdb34345a3c76b5f453ab345935a03894d1e8a9
SPIKE_CHAIN_GFX 368A2 216 216 8008 256 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226 ea3b4aa573268a91e6218b02b015fc3dcbf10d7ee50ec55afaef3cfcfa105d05
ROBOT_TRANSPORT_GFX_A 3697A 332 332 000F 480 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67
ANIMALS_GFX 36AC6 432 432 8014 640 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb fb65820be406480e1d8322e752b3ad4e60b144a5efd09057604f37aae7c6903e
STAGE_BLOCKS 3937E 3958 3958 80A4 5248 3958 c7d1ff8fb9f5d7f2bf46b632befdeaf72136854e94f2cb055bc057d5e8b4dc5a fd9b10cf2a1ad5cd28e14a27e06e3ea0050ca0ce9009057d9a527dcb9bc932f2
STAGE_GFX 3A2F4 10162 10162 82B6 22208 10161 f959c37d4a78c6fbe47ebf119e8db80fe2a5121093482559cc61675f5feb7b37 db0664cea0d185e7817e63918fc20350f09afda60e8caed64d0ce19707df8591
BIG_RING_FLASH_GFX B4A2 384 384 804D 2464 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
        """;
    private static final String CODE_TABLES = """
POINTS_GFX 29
CAPSULE_GFX 99
BIG_RING_GFX 59
GOAL_GFX 86
SIGNPOST_GFX 115
RESULTS_GFX 65
TITLE_CARD_GFX 71
SPRING45_GFX 75
SPRING_GFX 80
MONITOR_TIME_GFX 105
EXPLOSION_GFX 89
RING_GFX 46
HUD_GFX 49
CHECKPOINT_GFX 51
FLOWER_GFX 75
TITLE_CARD_TEXT_GFX 59
FLIPPER_GFX 82
BUMPER_GFX 72
METAL_PLATFORM_GFX 63
SPIKES_HV4_GFX 69
BLOCK_GFX 55
RETRACT_BLOCK_GFX 42
ONE_WAY_BARRIER_GFX 40
FIRE_SHOOT_GFX 63
ROTATE_PLATFORM_GFX 106
KAMA_KAMA_GFX 94
SPIKES_HV2_GFX 61
GA_GFX 90
TENTOU_GFX 90
SPIKE_BOMB_GFX 44
TELEPORTER_GFX 63
POCKET_GFX_A 50
BOSS_BARRIER_GFX 42
BOUNCE_PLATFORM_GFX_AB 62
GLASS_BREAK_GFX_A 42
SPIKE_CHAIN_GFX 61
ROBOT_TRANSPORT_GFX_A 77
ANIMALS_GFX 66
STAGE_BLOCKS 59
STAGE_GFX 97
BIG_RING_FLASH_GFX 36
        """;

    @Test void everyCompressedIncludeReadViewAndExistingDecoderProductMatches() throws Exception {
        var image = TestCdR32aDataLayout.actual().image();
        var lines = SOURCES.strip().lines().toList();
        assertEquals(lines.size(), CdR32aLoadLayout.SourceId.values().length);
        for (int i = 0; i < lines.size(); i++) {
            var p = lines.get(i).split(" ");
            var id = CdR32aLoadLayout.SourceId.valueOf(p[0]);
            assertEquals(i, id.ordinal()); assertEquals(Integer.parseInt(p[1], 16), id.fileOffset());
            assertEquals(Integer.parseInt(p[2]), id.includeBytes()); assertEquals(Integer.parseInt(p[3]), id.readerBytes());
            var read = CdAssetBytes.copyOf(image.readFile(id.fileOffset(), id.readerBytes()));
            assertEquals(Integer.parseInt(p[4], 16), id.headerWord());
            assertEquals(Integer.parseInt(p[4], 16), read.unsignedWord(0));
            assertEquals(p[7], id.readerSha256());
            assertEquals(p[7], TestCdR32aDataLayout.hash(read.copy()));
            assertEquals(id.includeSha256(), TestCdR32aDataLayout.hash(read.slice(0, id.includeBytes()).copy()));
            var decoded = TestCdR32aDataLayout.decode(image, id);
            assertEquals(Integer.parseInt(p[5]), decoded.bytes().length());
            assertEquals(Integer.parseInt(p[6]), decoded.consumedInputBytes());
            assertEquals(p[8], TestCdR32aDataLayout.hash(decoded.bytes().copy()));
        }
    }

    @Test void all19PlcDeclarationsInvertToExactOriginalCountAndRowBytes() throws Exception {
        var raw = TestCdR32aDataLayout.view(CdR32aDataLayout.Asset.PLC_LISTS);
        int[] counts = {2,15,13,2,13,1,1,13,13,13,13,13,13,13,13,13,1,13,3};
        assertEquals(19, CdR32aLoadLayout.PLC_LISTS.size());
        for (int i = 0; i < 19; i++) {
            var list = CdR32aLoadLayout.PLC_LISTS.get(i);
            assertEquals(i, list.nativeId()); assertEquals(counts[i], list.entries().size());
            int relative = raw.unsignedWord(i * 2);
            assertEquals(0xF17A + relative, list.fileOffset());
            var inverse = java.nio.ByteBuffer.allocate(2 + 6 * counts[i]);
            inverse.putShort((short) (counts[i] - 1));
            for (var entry : list.entries()) inverse.putInt(entry.source().nativeAddress()).putShort((short) entry.vramAddress());
            assertArrayEquals(raw.slice(relative, inverse.capacity()).copy(), inverse.array());
        }
        var zero = CdR32aLoadLayout.PLC_LISTS.get(2).entries();
        var one = CdR32aLoadLayout.PLC_LISTS.get(4).entries();
        assertEquals(CdR32aLoadLayout.SourceId.ROBOT_TRANSPORT_GFX_A, zero.get(4).source());
        assertEquals(CdR32aLoadLayout.SourceId.ROTATE_PLATFORM_GFX, one.get(4).source());
        assertEquals(0x6D40, zero.get(4).vramAddress());
        assertEquals(CdR32aLoadLayout.SourceId.TELEPORTER_GFX, zero.get(7).source());
        assertEquals(CdR32aLoadLayout.SourceId.POCKET_GFX_A, zero.get(8).source());
        assertEquals(zero.get(7).vramAddress(), zero.get(8).vramAddress());
        assertEquals(CdR32aLoadLayout.SourceId.BIG_RING_FLASH_GFX, CdR32aLoadLayout.PLC_LISTS.get(18).entries().get(2).source());
    }

    @Test void sectionsAndAllPaletteDescriptorsInvertToOriginalBytes() throws Exception {
        var section = TestCdR32aDataLayout.view(CdR32aDataLayout.Asset.SECTIONS);
        for (int i = 0; i < 2; i++) {
            var row = CdR32aLoadLayout.SECTIONS.get(i);
            assertEquals(row.rightThresholdWord(), section.unsignedWord(i * 2));
            assertEquals(row.initialPlcId(), section.unsignedWord(8 + i * 2));
            assertEquals(row.updatePlcId(), section.unsignedWord(4 + i * 2));
        }
        var raw = TestCdR32aDataLayout.view(CdR32aDataLayout.Asset.PALETTE_TABLE);
        assertEquals(6, CdR32aLoadLayout.PALETTES.size());
        var inverse = java.nio.ByteBuffer.allocate(48);
        for (var row : CdR32aLoadLayout.PALETTES) {
            inverse.putInt(row.source().nativeAddress()).putShort((short) row.destinationWord()).putShort((short) row.longwordCounter());
            assertEquals(4 * (row.longwordCounter() + 1), TestCdR32aDataLayout.view(row.source()).length());
        }
        assertArrayEquals(raw.copy(), inverse.array());
        assertEquals(CdR32aDataLayout.Asset.PAL_SONIC, CdR32aLoadLayout.PALETTES.get(3).source());
        assertEquals(CdR32aDataLayout.Asset.PAL_LEVEL_END, CdR32aLoadLayout.PALETTES.get(5).source());
        assertEquals(5, TestCdR32aDataLayout.view(CdR32aDataLayout.Asset.LEVEL_DATA_INDEX).unsignedByte(15));
    }

    @Test void descriptorWidthsAndCollectionsAreBoundedWithoutMedia() {
        var codeTables = CODE_TABLES.strip().lines().toList();
        assertEquals(CdR32aLoadLayout.SourceId.values().length, codeTables.size());
        for (var id : CdR32aLoadLayout.SourceId.values()) {
            var table = codeTables.get(id.ordinal()).split(" ");
            assertEquals(id.name(), table[0]);
            assertEquals(Integer.parseInt(table[1]), id.codeTableBytes());
            assertEquals(id.fileOffset(), id.nativeAddress() - 0x200000);
            assertTrue(id.includeBytes() > 0 && id.readerBytes() >= id.includeBytes());
            assertTrue(id.readerBytes() <= id.includeBytes() + 1);
            assertTrue(id.fileOffset() >= 0 && id.fileOffset() + id.readerBytes() <= 262144);
            assertEquals((id.headerWord() & 0x7FFF) * 32, id.outputBytes());
            assertTrue(id.consumedInputBytes() <= id.readerBytes());
            assertTrue(id.codeTableBytes() >= 3 && id.codeTableBytes() < id.consumedInputBytes());
        }
        assertThrows(UnsupportedOperationException.class, () -> CdR32aLoadLayout.PLC_LISTS.clear());
        assertThrows(UnsupportedOperationException.class, () -> CdR32aLoadLayout.PLC_LISTS.get(0).entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> CdR32aLoadLayout.PALETTES.clear());
    }
}
