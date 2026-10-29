package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.release.CdRetailReleases;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR11bAssets.Asset.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual-media positives; constructed bundles exercise rejection only. All authored vectors are initially UNRUN. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR11bAssets {
    private static final String PAST_SHA = "b7ebd9d362e91b213d53e984f737642f3e09cd2a511d3c5f1142c660e6aff265";
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 54272 75a5361ed9b0766746faa65cbb2eac63cf2bcebb5f66f619a3e9f45c31b9252a
LEVEL_COLLISION 3b274 800 619ddf334652ab2f8c5d1519ff7bebc5f35378b4883c3fcf12903b249ac6467d
LAYOUT_FG 3b5dc 170 c1d0ab43d6a16690c46c627775f883cecbdc4544f3c7a850eaedb8ab7d163cab
LAYOUT_BG 3b686 22 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963
LEVEL_START_LOC 29d4 4 16e8b5f89f778488cf83e444f35153670972d982c8c1424f8f5c1249129b0580
LEVEL_BLOCKS 3ba3a 4228 74c580b819c07f7bf1e79df5f11afe99b99579767d552478e8574a0688a9f928
ART_LEVEL_TILES 3cabe 10222 7470e75276fb99ff7bb90840dee1e670e3bb45df9e69423b71930f30743ae206
PAL_SONIC 6f0 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 710 96 6ba4727e35858ac2701d5942ac4e733d169ac607a46fe778fbe0dde3fb42d993
COL_ANGLE_MAP 39174 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 39274 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 3a274 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
OBJECT_PLACEMENT_RECORDS 775e 3984 c5441a5a61b8c17cbac91d85a830f1560ca1c4e101df72e10326f0c7d9d71791
LEVEL_DATA_INDEX f188 16 037869c414a1fe5278fc52e07acad543512432e277f2e599ce6db7c1192383e7
PLC_LISTS f198 498 9c7ee668df0b36a446de88501d146b5c16bdd37d3c0ede2c626eb6b70c8afb61
LEVEL_LAYOUTS 3b594 72 0c5ca7cb062310b35fc7f9d039c397ad3f4f0ad1bf1eff5724e2237906918d77
LEVEL_COL_INDEX 1a56 32 d787e684f62191c78644c363e4b512117c29e1b1ab9153ea925b6708fc2d79a7
CAM_BOUNDS 2910 12 fadb413a244c2a9cc5336b23ff20a78505d3353f4cd32a51c9b2ca63b16afa97
OBJECT_LAYOUTS 7752 4002 935b240634f0b7d4de0e080d33ec48a0af1efd5a333d277f8c95cf5aa4e214ad
PALETTE_TABLE 5c0 48 61fb9e45ba5cfdd2247fd899ecbb489ed1693c6dafbb189e7f359d61b7c3d1e3
SECTION_DATA cf5a 24 497fbb274bd9dd7182f7aa4efe63ad2f37b4669fe207a7177b81fe772e0da180
PALETTE_CYCLE_DATA 2c0 84 42c4b898e82d548a0ed3806eca7d398e3c7abaa0cd967c529c9ad1a8ca2891bc
BG_CAMERA_SECT_IDS 3398 34 6cccaa8b9614ea8d3f7e203cfc84f29d0ac8042b08aa5ccb6aa20a398262d701
BG_CAMERA_SECTS 33ba 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
OBJECT_INDEX 3c38 256 d46ec4888d5befcc4e9a4f00d3a10de6b70fe326322e3b7085800951c1577f5b
ART_SONIC 20000 69471 5160ff8ea26ed050cd3f41aaf1689d4e05a58cf15f090c0ca910932e7f629955
MAP_SPR_SONIC 30f60 3004 88968a4a46556ad579811c935d1ad2f9eb2df08f4658c424a20f75c265edec0c
DPLC_SONIC 31b1c 1636 0c5298383621ae7515a6f3f4b6cdca6704d3a446ef15264f002f9e9fe5fbaaea
ANI_SONIC 579e 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
SONIC_MINI_ANIMATIONS 574e 60 adcb220955503a487e05e9d1310f244e7c84e50c26c0fbf5c2a64e968f2f4c1a
SONIC_RAMP_CHUNKS 4514 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
SPEC_CHUNKS 29d8 4 a39b8a3638d112d8d1114cdd3830b49fa5b7d1f221ac2564b309e572e49c0d66
SINE_TABLE 82e 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE b14 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
ART_ANIMALS 38d7c 446 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab
ART_ANTON 37a02 484 d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a
ART_BIG_RING 3288c 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
ART_BIG_RING_FLASH db86 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
ART_BOULDER 3707e 270 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f
ART_CHECKPOINT 36982 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
ART_DIAGONAL_SPRING 351c8 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
ART_EXPLOSIONS 35cfa 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
ART_FLOAT_BLOCK 3718c 230 8ccc2959a0cbebd2fc24ed2f4cc08fd1baaafbf971f13df254784cc65b7c5e76
ART_FLOWER 36b0a 646 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1
ART_GOAL_POST 32c96 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
ART_HUD 36856 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
ART_LOG_SHADOW 36a7c 24 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19
ART_MONITOR_TIME_POSTS 356f6 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
ART_MOSQUI 37be6 846 b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892
ART_PATA_BATA 37f34 714 e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f
ART_PLATFORM 36eba 452 0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d
ART_POINTS 32180 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
ART_PROJ_ANIMALS 3f45e 434 61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef
ART_PROJECTOR 3f610 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
ART_RESULTS 33432 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
ART_RINGS 361e2 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
ART_ROBOT_GENERATOR 3f2ac 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
ART_SCENERY 3b908 306 f1334c4d9219fb54413cde041362fc19e1873eb93b824ddb7218008d209189db
ART_SIGNPOST 32e56 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
ART_SONIC_HOLE 3b896 114 97622d18562de1a4b39b7d676a914fd7c266f11b9578b31cf91fa8fbecbe5c53
ART_SPIKES 38bd4 86 4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea
ART_SPINNING_DISC 37328 166 a2bb54013b05cf8b26ae95194cdab0532c8d24530e89259069bf37e5588b9022
ART_SPRINGBOARD 38982 312 ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc
ART_SPRINGS 354d4 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
ART_TAMABBOH 3850c 1142 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f
ART_TITLE_CARD 3413c 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
ART_TITLE_CARD_TEXT 36d90 298 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5
ART_TUNNEL_DOOR 376ba 154 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b
ART_TUNNEL_WATERFALL 373ce 328 08d33432bf01cd85abc8653cba9b9901bd7f305932234685bc348ce014192d8e
ART_LIFE_ICON 362d6 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
TOUCH_DIMENSIONS 6eca 126 658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d
ENEMY_POINTS 6ce0 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
ART_HUD_NUMBERS 36456 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
HUD_RESET_TILES aef4 12 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c
HUD_DIVISORS b054 40 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea
MAP_SPR_HUD abbe 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
MAP_SPR_MONITOR_TIME a8a6 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
RING_DELTAS 9aee 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
ANI_RING 9f4a 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
MAP_SPR_RING 9f52 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
SPRING_SPEEDS 9740 4 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621
ANI_SPRING 99ea 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
MAP_SPRING 99f8 200 09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c
OBJECT_TILE_IDS cf94 72 7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1
PLATFORM_MAPPINGS cd1a 132 a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7
PLATFORM_WIDTHS c992 4 87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3
PLATFORM_RANGES c996 4 ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a
ANI_TUNNEL_DOOR 1d6b6 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
MAP_SPR_TUNNEL_DOOR 1d6c4 28 7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92
            """;
    // blob, retained Java view, native view, independently modelled Java minimum/cursor, output, output SHA.
    private static final String READERS = """
LEVEL_BLOCKS 4228 4228 4228 4227 5280 bbbfc1389c418e33af33c83782981eb862a8b64842d31aa9262ee6f27b128d58
ART_LEVEL_TILES 10222 10222 10223 10222 23872 256dd866d08c90833dd3c7c8ac8e7e2cc970e43526785fb331a3ceb4a555a42e
ART_ANIMALS 446 446 446 445 640 ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_ANTON 484 484 485 484 704 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_BIG_RING 1034 1034 1034 1033 3392 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH 384 384 385 384 2464 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
ART_BOULDER 270 270 271 270 608 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_CHECKPOINT 212 212 212 211 384 b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_DIAGONAL_SPRING 780 780 780 779 1472 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_EXPLOSIONS 1256 1256 1257 1256 2240 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_FLOAT_BLOCK 230 231 231 231 480 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_FLOWER 646 646 646 645 1248 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_GOAL_POST 448 448 449 448 768 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_HUD 300 300 301 300 864 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_LOG_SHADOW 24 24 24 23 512 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_MONITOR_TIME_POSTS 1540 1541 1541 1541 2624 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_MOSQUI 846 846 847 846 2240 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_PATA_BATA 714 714 715 714 1600 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_PLATFORM 452 452 453 452 1824 b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_POINTS 100 101 101 101 160 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_PROJ_ANIMALS 434 434 435 434 640 608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a
ART_PROJECTOR 1024 1024 1024 1024 1632 4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77
ART_RESULTS 1354 1354 1354 1354 3840 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_RINGS 244 244 244 244 448 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_ROBOT_GENERATOR 434 434 434 434 736 672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6
ART_SCENERY 306 306 307 306 544 4dc8389222e4ffcd585afd720ef722fe1e9694399ea8f05eefb3c6748999cfbd
ART_SIGNPOST 1500 1500 1501 1500 2432 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_SONIC_HOLE 114 114 114 113 448 8038f15c0f2952ab7ce2c2e9bb2823a14888aab2e5d9d77146ed01ade5845399
ART_SPIKES 86 86 87 86 256 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_SPINNING_DISC 166 167 167 167 384 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_SPRINGBOARD 312 312 312 311 672 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_SPRINGS 546 547 547 547 1152 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_TAMABBOH 1142 1142 1143 1142 1888 aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_TITLE_CARD 940 940 940 939 3488 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 298 298 298 298 1664 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_TUNNEL_DOOR 154 154 154 153 384 dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_TUNNEL_WATERFALL 328 328 328 327 928 f3cc6438669479cd3bb36fa0f6cb176ed514865e9ec06d19ee0a5b80d993a8c6
            """;

    @Test void nullForeignAndContradictorySourcesStopBeforeImages() throws Exception {
        assertThrows(NullPointerException.class, () -> CdR11bAssets.fromPrograms(null));
        for (var source : List.of(
                new CdTimeWarpPrograms.Source(CdReleaseVariant.JAPAN, CdRetailReleases.USA.normalizedSha256(), "unused", PAST_SHA),
                new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, "0".repeat(64), "unused", PAST_SHA),
                new CdTimeWarpPrograms.Source(CdReleaseVariant.USA, CdRetailReleases.USA.normalizedSha256(), "unused", "0".repeat(64)))) {
            var bundle = mock(CdTimeWarpPrograms.class); when(bundle.source()).thenReturn(source);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle));
            verify(bundle, never()).r11b(); verify(bundle, never()).warp();
        }
    }

    @Test void sameHeaderPresentTruncationMutationAndWarpCopyNeverAuthenticatePast() throws Exception {
        var f = retail(); var past = f.programs().r11b();
        assertEquals(past.header(), f.present().header());
        byte[] changed = past.readFile(0, past.fileBytes()); changed[changed.length - 1] ^= 1;
        byte[] badHeader = past.readFile(0, past.fileBytes()); badHeader[1] = 1;
        for (var wrong : List.of(f.present(), CdMmdImage.parse(changed),
                CdMmdImage.parse(past.readFile(0, past.fileBytes() - 1)), CdMmdImage.parse(badHeader), f.programs().warp())) {
            var bundle = mock(CdTimeWarpPrograms.class);
            when(bundle.source()).thenReturn(f.programs().source()); when(bundle.r11b()).thenReturn(wrong);
            assertThrows(IOException.class, () -> CdR11bAssets.fromPrograms(bundle));
            verify(bundle, never()).warp();
        }
    }

    @Test void actualUsaIdentityIsDetachedDistinctPastAndOriginalFileBacked() throws Exception {
        var f = retail(); var a = f.assets();
        assertEquals(CdReleaseVariant.USA, a.identity().variant());
        assertEquals(CdStagePayload.R11B.stage(), a.identity().stage());
        assertEquals(CdNativeFile.R11B, a.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, a.identity().domain());
        assertEquals(PAST_SHA, a.identity().mmdSha256());
        assertEquals(CdRetailReleases.USA.normalizedSha256(), a.identity().observedWholeDiscSha256());
        assertEquals(PAST_SHA, sha(f.programs().r11b().readFile(0, 262144)));
        assertNotEquals(sha(f.present().readFile(0, 262144)), a.identity().mmdSha256());
        assertEquals(0, f.programs().r11b().fileBackedCopyBytes());
        assertEquals(0, f.programs().r11b().unresolvedCopyBytes());
    }

    @Test void allSeventyViewsRetainLiteralOrderRangesAndCompleteHashes() throws Exception {
        var a = retail().assets(); var rows = MANIFEST.strip().lines().toList();
        assertEquals(89, rows.size()); assertEquals(89, CdR11bAssets.Asset.values().length);
        for (int i = 0; i < rows.size(); i++) {
            var c = rows.get(i).split(" "); var key = CdR11bAssets.Asset.values()[i];
            assertEquals(c[0], key.name()); assertEquals(Integer.parseInt(c[1], 16), key.fileOffset());
            assertEquals(Integer.parseInt(c[2]), key.length()); var v = a.view(key);
            assertSame(key, v.asset()); assertEquals(key.range(), v.range());
            assertEquals(CdNativeFile.R11B, v.range().file()); assertEquals(0x200000 + key.fileOffset(), v.range().nativeAddress());
            assertEquals(key.length(), v.bytes().length()); assertEquals(c[3], sha(v.bytes().copy()), key.name());
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
    }

    @Test void rangesAndReaderSelectionCannotMintArbitraryReads() throws Exception {
        for (int[] r : new int[][]{{-1, 0}, {0, -1}, {0x40000, 1}, {1, Integer.MAX_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                    CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, r[0], r[1]));
        }
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11A, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CdR11bAssets.FileRange(
                CdNativeFile.R11B, CdMmdImage.Domain.RELOCATED_COPY, 0, 1));
        var end = new CdR11bAssets.FileRange(CdNativeFile.R11B, CdMmdImage.Domain.ORIGINAL_WORD_RAM, 0x40000, 0);
        assertEquals(0x240000, end.endExclusive());
        var a = retail().assets();
        assertThrows(IllegalArgumentException.class, () -> a.readerView(ART_SONIC, CdR11bAssets.ReaderKind.JAVA));
        assertThrows(NullPointerException.class, () -> a.view(null));
        assertThrows(NullPointerException.class, () -> a.readerView(LEVEL_BLOCKS, null));
    }

    @Test void coreDecodesUseIndependentCursorsAndKeepUnreadBlockTail() throws Exception {
        var a = retail().assets();
        assertEquals(4227, a.blocksDecode().consumedInputBytes()); assertEquals(5280, a.blocksDecode().bytes().length());
        assertEquals(10222, a.levelTilesDecode().consumedInputBytes()); assertEquals(23872, a.levelTilesDecode().bytes().length());
        byte[] block = a.readerView(LEVEL_BLOCKS, CdR11bAssets.ReaderKind.JAVA).bytes().copy();
        var minimum = CdNemesisAsset.decode(Arrays.copyOf(block, 4227), 5280, 5280);
        assertArrayEquals(a.blocksDecode().bytes().copy(), minimum.bytes().copy());
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(Arrays.copyOf(block, 4226), 5280, 5280));
        block[4227] ^= (byte) 0xFF;
        assertArrayEquals(minimum.bytes().copy(), CdNemesisAsset.decode(block, 5280, 5280).bytes().copy());
        byte[] tile = a.readerView(ART_LEVEL_TILES, CdR11bAssets.ReaderKind.JAVA).bytes().copy();
        assertThrows(IOException.class, () -> CdNemesisAsset.decode(Arrays.copyOf(tile, 10221), 23872, 23872));
    }

    @Test void allThirtySevenProfilesNameSourceAndBothRealReadViews() throws Exception {
        var f = retail(); var a = f.assets(); int count = 0;
        for (String row : READERS.strip().lines().toList()) {
            var c = row.split(" "); var key = CdR11bAssets.Asset.valueOf(c[0]); var p = key.readerProfile().orElseThrow();
            count++; assertSame(key, p.asset()); assertEquals(key.range(), p.sourceBlobRange());
            assertEquals(Integer.parseInt(c[2]), p.javaInputRange().length());
            assertEquals(Integer.parseInt(c[3]), p.nativeInputRange().length());
            assertEquals(Integer.parseInt(c[4]), p.javaConsumedInputBytes()); assertEquals(Integer.parseInt(c[5]), p.outputBytes());
            for (var kind : CdR11bAssets.ReaderKind.values()) {
                var view = a.readerView(key, kind); int length = Integer.parseInt(c[kind == CdR11bAssets.ReaderKind.JAVA ? 2 : 3]);
                assertEquals(length, view.bytes().length());
                assertArrayEquals(f.programs().r11b().readFile(key.fileOffset(), length), view.bytes().copy());
            }
        }
        assertEquals(37, count); assertFalse(LEVEL_BLOCKS.isPlcSource());
        assertTrue(ART_LEVEL_TILES.isPlcSource()); assertTrue(ART_SONIC.readerProfile().isEmpty());
        assertEquals(10223, ART_LEVEL_TILES.readerProfile().orElseThrow().nativeInputRange().length());
    }

    @Test void explicitPlcDecodesCompareAllOutputHashesAndIndependentMinimumCuts() throws Exception {
        var a = retail().assets(); int tested = 0;
        for (String row : READERS.strip().lines().toList()) {
            var c = row.split(" "); var key = CdR11bAssets.Asset.valueOf(c[0]);
            if (key == LEVEL_BLOCKS) { continue; }
            int minimum = Integer.parseInt(c[4]), output = Integer.parseInt(c[5]);
            byte[] actual = a.readerView(key, CdR11bAssets.ReaderKind.JAVA).bytes().copy();
            var decoded = CdNemesisAsset.decode(actual, output, output); tested++;
            assertEquals(minimum, decoded.consumedInputBytes(), key.name());
            assertEquals(c[6], sha(decoded.bytes().copy()), key.name());
            assertThrows(IOException.class, () -> CdNemesisAsset.decode(Arrays.copyOf(actual, minimum - 1), output, output), key.name());
            assertArrayEquals(decoded.bytes().copy(), CdNemesisAsset.decode(Arrays.copyOf(actual, minimum), output, output).bytes().copy());
            if (actual.length > minimum) {
                actual[minimum] ^= (byte) 0xA5;
                assertArrayEquals(decoded.bytes().copy(), CdNemesisAsset.decode(actual, output, output).bytes().copy());
            }
        }
        assertEquals(36, tested); // These are explicit test consumers; the production factory decodes only two core sources.
    }

    @Test void sonicUsesLivePastOperandAndActualAlignmentRatherThanDuplicateMapping() throws Exception {
        var f = retail(); var a = f.assets(); var s = a.sonic();
        assertEquals(0x30F60, MAP_SPR_SONIC.fileOffset()); assertEquals(0x31B1C, DPLC_SONIC.fileOffset());
        assertEquals("217c00230f600004", HexFormat.of().formatHex(f.programs().r11b().readFile(0x3E6A, 8)));
        assertArrayEquals(a.view(MAP_SPR_SONIC).bytes().copy(), f.programs().r11b().readFile(0x1F200, 3004));
        assertEquals(198, s.mappingFrames().size()); assertEquals(198, s.dplcFrames().size());
        assertEquals(515, s.mappingFrames().stream().mapToInt(CdSonicVisualAssets.MappingFrame::pieceCount).sum());
        assertEquals(736, s.dplcFrames().stream().mapToInt(CdSonicVisualAssets.DplcFrame::outputBytes).max().orElseThrow());
        int endpoint = s.dplcFrames().stream().flatMap(x -> x.pieces().stream())
                .mapToInt(x -> x.artByteOffset() + x.byteLength()).max().orElseThrow();
        assertEquals(69472, endpoint); assertEquals(0, s.alignmentByte());
        assertEquals(69471, a.view(ART_SONIC).bytes().length()); assertEquals(69472, a.sonicArtReadRange().length());
        assertEquals("94edeaff466c9e0c8ddd0b090a99b24bca663b8c40ac1dcd45c530de60e9ce64", sha(s.artReadView().copy()));
        byte[] wrong = s.artReadView().copy(); wrong[69471] = 1;
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(s.mappings().copy(), s.dplcs().copy(), wrong));
        assertThrows(IllegalArgumentException.class, () -> CdSonicVisualAssets.decode(
                s.mappings().copy(), s.dplcs().copy(), s.rawArt().copy()));
    }

    @Test void playerTablesArePastLocalPureFormatsWithoutPresentIdentity() throws Exception {
        var a = retail().assets(); var p = a.player();
        assertSame(a.sonic(), p.sonic()); assertEquals(60, p.animations().size());
        assertArrayEquals(a.view(ANI_SONIC).bytes().copy(), p.animationTable().copy());
        assertArrayEquals(a.view(SONIC_MINI_ANIMATIONS).bytes().copy(), p.miniAnimations().copy());
        assertArrayEquals(a.view(SONIC_RAMP_CHUNKS).bytes().copy(), p.rampChunks().copy());
        assertEquals("8c7f1e1e", HexFormat.of().formatHex(p.specialChunks().copy()));
        assertArrayEquals(a.view(SINE_TABLE).bytes().copy(), p.sineTable().copy());
        assertArrayEquals(a.view(ARC_TAN_TABLE).bytes().copy(), p.arcTanTable().copy());
        assertEquals(257, p.arcTanTable().length()); assertEquals(0x20, p.arcTanByte(256));
        for (int i = 0; i < 64; i++) { assertEquals(p.sineWord(i), p.sineWord(i + 256)); }
    }

    @Test void terrainHasExactPastExtentsAndNativeClearedLayout() throws Exception {
        var t = retail().assets().terrain(); assertEquals(CdStageTerrain.Shape.PPZ1_PAST, t.shape());
        assertEquals(660, t.blockCount()); assertEquals(746, t.patternCount()); assertEquals(106, t.chunkCount());
        assertEquals(800, t.collision().ids().length());
        assertEquals(42, t.layout().columns(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(4, t.layout().rows(CdStageTerrain.Plane.FOREGROUND));
        assertEquals(10, t.layout().columns(CdStageTerrain.Plane.BACKGROUND));
        assertEquals(2, t.layout().rows(CdStageTerrain.Plane.BACKGROUND));
        assertEquals("d0e0a9657f5f154420d6953ae4ac785abb29affe7b3c12823ca97802079dae90", sha(t.layout().ram().copy()));
        assertEquals(0, t.layout().nativeCell(CdStageTerrain.Plane.FOREGROUND, 42, 0).rawByte());
        assertEquals(0, t.layout().nativeCell(CdStageTerrain.Plane.BACKGROUND, 10, 0).rawByte());
        assertEquals(0, t.layout().nativeCell(CdStageTerrain.Plane.FOREGROUND, 0, 4).rawByte());
    }

    @Test void commonPlacementsRetainNativeOrderPeriodFlagsAndSentinels() throws Exception {
        var a = retail().assets(); var p = a.placements();
        assertEquals(497, p.records().size()); assertEquals(270, p.forPeriod(CdPeriod.PAST).size());
        assertEquals(227, p.forPeriod(CdPeriod.PRESENT).size()); assertEquals(283, p.forPeriod(CdPeriod.GOOD_FUTURE).size());
        assertArrayEquals(p.forPeriod(CdPeriod.GOOD_FUTURE).toArray(), p.forPeriod(CdPeriod.BAD_FUTURE).toArray());
        assertArrayEquals(a.view(OBJECT_LAYOUTS).bytes().slice(12, 3984).copy(), a.view(OBJECT_PLACEMENT_RECORDS).bytes().copy());
        assertEquals(8, p.forwardSentinel().length()); assertEquals(8, p.backwardSentinel().orElseThrow().length());
        assertEquals(6, p.nullList().orElseThrow().length()); int previous = -1;
        for (var row : p.records()) { assertTrue(row.centreX() >= previous); previous = row.centreX(); }
        assertThrows(UnsupportedOperationException.class, () -> p.records().clear());
    }

    @Test void allRetainedValuesStayImmutableAcrossCopiesAndReadOnlyViews() throws Exception {
        var a = retail().assets(); var v = a.view(PAL_LEVEL).bytes(); int first = v.unsignedByte(0);
        byte[] copy = v.copy(); copy[0] ^= 0x55; assertEquals(first, a.view(PAL_LEVEL).bytes().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> v.buffer().put(0, (byte) 0));
        var one = v.buffer(); one.get(); assertEquals(0, v.buffer().position());
        assertSame(a.terrain(), a.terrain()); assertSame(a.loadPlan(), a.loadPlan());
        assertSame(a.sonic(), a.sonic()); assertSame(a.player(), a.player()); assertSame(a.placements(), a.placements());
        assertThrows(UnsupportedOperationException.class, () -> a.sonic().mappingFrames().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.loadPlan().plcs().clear());
    }

    private String retainedRoot;
    private Fixture retained;
    private record Fixture(CdTimeWarpPrograms programs, CdR11bAssets assets, CdMmdImage present) { }
    private Fixture retail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        Assumptions.assumeTrue(root != null && !root.isBlank(), "Actual USA media requires soniccd.media.root");
        if (retained != null && root.equals(retainedRoot)) { return retained; }
        Path cue;
        try (var paths = Files.list(Path.of(root).resolve("USA"))) {
            var cues = paths.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, cues.size(), "Configured USA source must name exactly one existing CUE");
            cue = cues.getFirst();
        }
        CdTimeWarpPrograms programs; CdMmdImage present;
        try (var owner = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            programs = CdTimeWarpPrograms.fromPrograms(owner); present = owner.mmd(CdNativeFile.R11A);
        }
        retained = new Fixture(programs, CdR11bAssets.fromPrograms(programs), present); retainedRoot = root;
        return retained; // Immutable detached values only; no file owner/handle or cached validation verdict.
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }


    @Test void rawLifeIconIsBoundByItsActualAbsoluteLeaAndIsNotANemesisOrPlcSource() throws Exception {
        var f = retail(); var a = f.assets(); var icon = a.view(ART_LIFE_ICON);
        assertEquals(69, ART_LIFE_ICON.ordinal());
        assertEquals(0x362D6, icon.range().fileOffset());
        assertEquals(0x2362D6, icon.range().nativeAddress());
        assertEquals(384, icon.bytes().length());
        assertEquals("0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a", sha(icon.bytes().copy()));
        assertFalse(ART_LIFE_ICON.isPlcSource());
        assertTrue(ART_LIFE_ICON.readerProfile().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> a.readerView(ART_LIFE_ICON, CdR11bAssets.ReaderKind.JAVA));
        var mmd = f.programs().r11b();
        assertArrayEquals(HexFormat.of().parseHex("203c742000027400143900ff152e08820007ef4a2d400004"), mmd.readFile(0x1B6A, 24));
        assertArrayEquals(HexFormat.of().parseHex("43f9002362d647f12000"), mmd.readFile(0x1B82, 10));
        assertArrayEquals(HexFormat.of().parseHex("2c9b".repeat(32) + "4e75"), mmd.readFile(0x1B8C, 66));
        // Native LoadLifeIcon masks only scratch bit7, then selects128 raw bytes.
        // This finite read is data evidence, not a destination publication or broader period admission.
        assertEquals("8e6567c3ec8470cd2ab5d251bf6c3431c4c74f369e37bc7ba141c3dc610e25fb",
                sha(icon.bytes().slice((0x80 & 0x7F) << 7, 128).copy()));
        assertArrayEquals(icon.bytes().slice(0, 128).copy(), icon.bytes().slice((0x80 & 0x7F) << 7, 128).copy());
        assertEquals(128, icon.bytes().slice(128, 128).length());
        assertEquals(128, icon.bytes().slice(256, 128).length());
        assertThrows(IndexOutOfBoundsException.class, () -> icon.bytes().slice(384, 128));
    }


    // Native HUD raw views and explicit current writes; these are not a completed HUD/World.
    @Test void hudRawViewsAppendExactSourceIdentityWithoutBecomingPlcOrNemesisInputs() throws Exception {
        var a = hudRetail().assets();
        var keys = new CdR11bAssets.Asset[]{ART_HUD_NUMBERS, HUD_RESET_TILES, HUD_DIVISORS,
                MAP_SPR_HUD, MAP_SPR_MONITOR_TIME};
        String[] labels = {"Art_HUDNumbers", "HUD_DrawScoreTiles/HUD_DrawRingsTiles",
                "Hud_100000/Hud_1000h", "MapSpr_HUD", "MapSpr_MonitorTime"};
        int[] offsets = {0x36456, 0xAEF4, 0xB054, 0xABBE, 0xA8A6};
        int[] sizes = {1024, 12, 40, 168, 450};
        for (int i = 0; i < keys.length; i++) {
            var key = keys[i]; var view = a.view(key);
            assertEquals(72 + i, key.ordinal()); assertEquals(labels[i], key.nativeLabel());
            assertEquals(offsets[i], key.fileOffset()); assertEquals(sizes[i], key.length());
            assertSame(key, view.asset()); assertEquals(key.range(), view.range());
            assertEquals(0x200000 + offsets[i], view.range().nativeAddress());
            assertFalse(key.isPlcSource()); assertTrue(key.readerProfile().isEmpty());
            for (var kind : CdR11bAssets.ReaderKind.values())
                assertThrows(IllegalArgumentException.class, () -> a.readerView(key, kind));
            assertThrows(IndexOutOfBoundsException.class, () -> view.bytes().slice(key.length(), 1));
        }
        assertEquals(36, Arrays.stream(CdR11bAssets.Asset.values()).filter(CdR11bAssets.Asset::isPlcSource).count());
        assertEquals(37, Arrays.stream(CdR11bAssets.Asset.values()).filter(k -> k.readerProfile().isPresent()).count());
        assertEquals(69, ART_LIFE_ICON.ordinal()); assertEquals(70, TOUCH_DIMENSIONS.ordinal());
        assertEquals(71, ENEMY_POINTS.ordinal());
    }

    @Test void hudFontKeepsAllSixteenGlyphsAndItsFiveActualAbsoluteLongReferences() throws Exception {
        var f = hudRetail(); var font = f.assets().view(ART_HUD_NUMBERS).bytes();
        String[] glyphHashes = {
                "4031de1c91ad5cc71ddad6a06e406c45036246e853d76beae1804248011b52c2",
                "4f6c673bb4ad7b0e2f5555a6856e2874d537ef8065b3485a88037c8d830c3885",
                "c88a4c831111f4cad0efe7a41ceb68e3c2aa1cac759ddf1f693c4a05b48efcbf",
                "3277c9c279eeb1e7b80f3aa97ea7e3dbc23d0341266b9a7ab61729fa4dbcce4f",
                "f0995f835b078c674cb5b43774193b0f92221368e9fb5b28f616eee1c95f50db",
                "db13b572638a282c60d201f3ff54f28aef93098763756256bf27dfb610c88ec4",
                "08cd3d02403092bd928250e47a5d1523fdebb6667dbbc7cc8ec2026ddc4a0f20",
                "3ae80c55d16a068232ffbdf7a389bd47396330f7b8630b0b153b580640f94e8b",
                "90e83518c011c6b09e7fded8d9780b065c4b8db7a4fd4faeb5aa0882e47f01c5",
                "13eba47a8089ea5f5eaf5f148aeb56fc05d348ff4229aca657dd3f972061b2a9",
                "a029b65495c7422c2f0870843869a23ea907128e3b679098cda6a16cf051e157",
                "d31736c339a1224304f9876d2962100c72d7b088a6f0966d0a376bea0a531d03",
                "c7e9874b7cd756e878da07379134aab72d25f00ea3edee536d979b36ea63754d",
                "c15ea3364bec2f3ffb833a7e06abb811b426c31f72064a71214c542592da9ea3",
                "81625d0270ef4e7b04579b6f35919815e4506329ffdc5f7a643ec36c70fd5502",
                "017a50cefadf624aa8d6919f26204b6dea47ea7600f36eeea0957ed7d7bdc481"
        };
        assertEquals(1024, font.length()); assertEquals(16, glyphHashes.length);
        for (int glyph = 0; glyph < glyphHashes.length; glyph++)
            assertEquals(glyphHashes[glyph], sha(font.slice(glyph * 64, 64).copy()), "glyph=" + glyph);
        assertEquals("00000000006666100666666106611661", HexFormat.of().formatHex(font.slice(0, 16).copy()));
        assertEquals(64, font.slice(15 * 64, 64).length());
        assertThrows(IndexOutOfBoundsException.class, () -> font.slice(16 * 64, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> font.slice(15 * 64, 65));
        for (int offset : new int[]{0xAEC6, 0xAF28, 0xAFA4, 0xB014, 0xB0BA})
            assertArrayEquals(HexFormat.of().parseHex("43f900236456"), f.programs().r11b().readFile(offset, 6));
        assertArrayEquals(HexFormat.of().parseHex("43f900236456323c000f"), f.programs().r11b().readFile(0xAEC6, 10));
        byte[] detached = font.copy(); detached[0] = 0x7F;
        assertEquals(0, font.unsignedByte(0)); assertEquals(0, f.assets().view(ART_HUD_NUMBERS).bytes().unsignedByte(0));
        assertThrows(ReadOnlyBufferException.class, () -> font.buffer().put(0, (byte) 0));
    }

    @Test void hudResetRowsAndDivisorsKeepNativeSevenThreeCountsAndBothNumericFamilies() throws Exception {
        var f = hudRetail(); var reset = f.assets().view(HUD_RESET_TILES).bytes();
        assertArrayEquals(HexFormat.of().parseHex("ffffffffffff0000ffff0000"), reset.copy());
        assertArrayEquals(new byte[]{-1, -1, -1, -1, -1, -1, 0}, reset.slice(0, 7).copy());
        assertEquals(0, reset.unsignedByte(7));
        assertArrayEquals(new byte[]{-1, -1, 0}, reset.slice(8, 3).copy()); assertEquals(0, reset.unsignedByte(11));
        assertThrows(IndexOutOfBoundsException.class, () -> reset.unsignedByte(12));
        var divisors = f.assets().view(HUD_DIVISORS).bytes();
        long[] literal = {100000L, 10000L, 1000L, 100L, 10L, 1L, 4096L, 256L, 16L, 1L};
        for (int i = 0; i < literal.length; i++) assertEquals(literal[i], divisors.unsignedLong(i * 4));
        assertThrows(IndexOutOfBoundsException.class, () -> divisors.unsignedLong(37));
        var mmd = f.programs().r11b();
        assertArrayEquals(HexFormat.of().parseHex("45fa0034343c0006"), mmd.readFile(0xAEBE, 8));
        assertEquals(0x20AEF4, 0x20AEC0 + (short) 0x34);
        assertArrayEquals(HexFormat.of().parseHex("45fa005a343c0002"), mmd.readFile(0xAEA0, 8));
        assertEquals(0x20AEFC, 0x20AEA2 + (short) 0x5A);
        assertArrayEquals(HexFormat.of().parseHex("45f90020b068"), mmd.readFile(0xB09C, 6));
        // Source evidence only: no division, driver, glyph writer or HUD timer is executed.
    }

    @Test void hudAndTimeIconMapsKeepTheirActualRootStoresAndCompleteFrameDirectories() throws Exception {
        var f = hudRetail(); var hud = f.assets().view(MAP_SPR_HUD).bytes();
        int[] roots = {8, 80, 96, 152}; int[] pieces = {14, 3, 11, 3};
        for (int i = 0; i < roots.length; i++) {
            assertEquals(roots[i], hud.unsignedWord(i * 2));
            assertEquals(pieces[i], hud.unsignedByte(roots[i]));
            assertEquals(1 + pieces[i] * 5 + ((1 + pieces[i] * 5) & 1),
                    (i + 1 < roots.length ? roots[i + 1] : 168) - roots[i]);
        }
        var monitor = f.assets().view(MAP_SPR_MONITOR_TIME).bytes();
        int[] timeRoots = {40, 62, 84, 106, 128, 150, 172, 194, 216, 238,
                260, 286, 312, 338, 364, 390, 416, 432, 438, 444};
        for (int i = 0; i < timeRoots.length; i++) assertEquals(timeRoots[i], monitor.unsignedWord(i * 2));
        assertArrayEquals(HexFormat.of().parseHex("01f80d0032f0"), monitor.slice(438, 6).copy());
        assertArrayEquals(HexFormat.of().parseHex("01f80d003af0"), monitor.slice(444, 6).copy());
        assertThrows(IndexOutOfBoundsException.class, () -> hud.slice(168, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> monitor.slice(450, 1));
        assertArrayEquals(HexFormat.of().parseHex("217c0020abbe0004"), f.programs().r11b().readFile(0xAB2C, 8));
        assertArrayEquals(HexFormat.of().parseHex("217c0020a8a60004"), f.programs().r11b().readFile(0xA2D4, 8));
        // Reserved TimeIcon uses last two frames directly; no animation view is added.
    }

    @Test void explicitOriginalGlyphTilesCommitOnlyToTheExistingNewCurrentWindows() throws Exception {
        var f = hudRetail(); var font = f.assets().view(ART_HUD_NUMBERS).bytes();
        var before = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.unknown();
        var regionKeys = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.Region.values();
        for (int i = 0; i < 11; i++) before =
                com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.writeTile(before,
                        regionKeys[i].destinationWord(), CdAssetBytes.copyOf(new byte[32]));
        var upper = font.slice(9 * 64, 32); var lower = font.slice(9 * 64 + 32, 32);
        var afterUpper = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.writeTile(before, 0xB4A0, upper);
        var after = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.writeTile(afterUpper, 0xB4C0, lower);
        assertArrayEquals(upper.copy(), com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.readTile(after, 0xB4A0 >>> 5).copy());
        assertArrayEquals(lower.copy(), com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.readTile(after, 0xB4C0 >>> 5).copy());
        for (int i = 0; i < 11; i++) assertSame(before.region(regionKeys[i]), after.region(regionKeys[i]));
        var digits = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.Region.AT_B060;
        var lives = com.openggf.game.soniccd.resources.CdR11bCameraArtResidency.Region.AT_B4A0;
        assertSame(before.region(digits), after.region(digits));
        assertArrayEquals(new byte[0x3C0], after.region(digits).knownMask().copy());
        assertArrayEquals(new byte[0x40], before.region(lives).knownMask().copy());
        assertArrayEquals(new byte[]{1, 1, 1, 1}, after.region(lives).knownMask().slice(0, 4).copy());
        assertTrue(before.region(lives).knownByte(0).isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> after.region(lives).knownByte(0x40));
        // Only explicit test-owned32-byte transfers executed; this is not native HUD or a World.
    }

    private Fixture hudRetail() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assertNotNull(root, "New explicit HUD retail cases require the configured original USA root");
        assertFalse(root.isBlank(), "The original USA media root must not be blank");
        return retail(); // Preserve the inherited fixture policy; no assumption can fire after these checks.
    }
}
