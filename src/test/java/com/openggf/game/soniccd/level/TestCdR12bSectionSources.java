package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12bSectionSources.ReaderKind.*;
import static com.openggf.game.soniccd.level.CdR12bSectionSources.SourceId.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ALL JAVA UNRUN by the source author. Required original USA ingress; no assumptions/skips.
 * Literal witnesses are the independent original-media Python census, N1ecc5f3a.
 * Expected Java/native-style decoder cursors below require Root execution, not fitted changes.
 * Declared mocks are malformed component controls, never authenticated media or current art.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bSectionSources {
    // SourceId order is actual PLC-directory encounter order. Research S32 is plan ordinal27.
    // id offset blob header prefix javaCursor nativeCursor tiles blobHash javaViewHash nativeViewHash decodedHash
    private static final String MANIFEST = """
ART_LEVEL_TILES 3cba0 10802 831d 10802 10802 10803 797 c47ef93e567aa11c9726a7d2f4db49b9093700989fe685de301e889af137c8af c47ef93e567aa11c9726a7d2f4db49b9093700989fe685de301e889af137c8af df9075f7af1c9cd58fb188225e71ac7d829076ea16c6111bd2f39ef3728b446b 5f09700683b979ae263721baffcc8b47b1dbf03d2ef0715bb89bd8f26f355b7d
ART_SPRING_WHEEL 37272 182 8 181 181 182 8 b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee dae6ce7abe19d2a44be58d37d804aff175e3b0d541983ee782d60176bcaf63f2
ART_TUNNEL_DOOR 376ba 154 800c 152 153 154 12 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_BLOCK 3718c 230 800f 230 231 231 15 8ccc2959a0cbebd2fc24ed2f4cc08fd1baaafbf971f13df254784cc65b7c5e76 131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776 131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_TITLE_CARD 3413c 940 806d 938 939 940 109 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 36d90 298 8034 297 298 298 52 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_PLATFORM 36eba 452 8039 452 452 453 57 0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d 0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d 924566bd57e2d30028d458fb15c95034a5c736931c7dbd751a3edce4ed21593b b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_ANIMALS 38d7c 446 14 445 445 446 20 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_SPRINGS 354d4 546 8024 546 547 547 36 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_HUD 36856 300 801b 300 300 301 27 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_MONITOR_TIME_POSTS 356f6 1540 8052 1540 1541 1541 82 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_EXPLOSIONS 35cfa 1256 46 1256 1256 1257 70 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_POINTS 32180 100 5 100 101 101 5 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_FLOWER 36b0a 646 8027 645 645 646 39 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_RINGS 361e2 244 e 243 244 244 14 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_SPIKES_V2 38bd4 86 8008 86 86 87 8 4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea 4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea e6b3bfee35febba9961f852f78580f1571883b49453e21c17af008cdab8d4304 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_BOULDER 3707e 270 8013 270 270 271 19 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_SPINNING_DISC 37328 166 800c 166 167 167 12 a2bb54013b05cf8b26ae95194cdab0532c8d24530e89259069bf37e5588b9022 2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d 2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_GOAL_POST 32c96 448 8018 448 448 449 24 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f 1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_ANTON 37a02 484 16 484 484 485 22 d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a e0e121da920926a86b5a445ba9df766931ee4cac91d4e611a3b30fcf5ebba556 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_TAMABBOH 3850c 1142 803b 1142 1142 1143 59 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_PATA_BATA 37f34 714 8032 713 714 715 50 e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f a415ffa282fb1e90378a97f458f05e379a6d82d1a006751d1b85c6efcfc736e3 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_SPRINGBOARD 38982 312 15 311 311 312 21 ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_LOG_SHADOW 36a7c 24 10 22 23 24 16 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_SCENERY_B 3b8fa 306 8011 306 306 307 17 f1334c4d9219fb54413cde041362fc19e1873eb93b824ddb7218008d209189db f1334c4d9219fb54413cde041362fc19e1873eb93b824ddb7218008d209189db c74b8ee9b9e084304a479a26e219552bab90a317d01ac80c1887531bf70ce760 4dc8389222e4ffcd585afd720ef722fe1e9694399ea8f05eefb3c6748999cfbd
ART_CHECKPOINT 36982 212 800c 211 211 212 12 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_SPRING_45 351c8 780 2e 779 779 780 46 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_HOLOGRAM_ANIMALS 3f784 434 14 434 434 435 20 61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef 61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef 51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df 608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a
ART_MOSQUI 37be6 846 46 846 846 847 70 b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892 b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892 7696cc4d61b4f4607afcafdb693994cd65972efce4949f18a622dd630df8c4b5 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_ROBOT_TRANSPORT_B 3f5d2 434 17 433 434 434 23 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806 672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6
ART_SWING 38c2a 338 e 337 338 338 14 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621 d19d3dc9345daa92d6937f38f716008340c820078be7f7bd4355cfbc63937da4
ART_TAGA_TAGA 381fe 782 802d 782 782 783 45 66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732 66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732 8a1766a33e7c721ea4f348ee8306fe34179805072ca4a2f7f44c80b3960cbaf1 70dc53f69da2592c19459ec9b591980da55eb27e50e5352f9c5172121ab8fa49
ART_HOLOGRAM 3f936 1024 33 1023 1024 1024 51 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5 4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77
ART_RESULTS 33432 1354 8078 1353 1354 1354 120 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_SIGNPOST 32e56 1500 804c 1499 1500 1501 76 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67 c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_BIG_RING 3288c 1034 806a 1033 1033 1034 106 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH d7f2 384 804d 384 384 385 77 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7 e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
            """;

    @Test void allThirtySevenDescriptorsHaveLiteralOriginalRangesCountsAndExpectedReaderMetadata() throws Exception {
        var sources = retail().sectionSources(); var ids = CdR12bSectionSources.SourceId.values();
        assertEquals(37, ids.length); assertEquals(37, sources.sources().size());
        for (int i = 0; i < ids.length; i++) {
            var c = columns().get(i); var s = sources.source(ids[i]);
            assertEquals(c[0], ids[i].name()); assertSame(s, sources.sources().get(i)); assertSame(ids[i], s.id());
            assertEquals(Integer.parseInt(c[1],16), s.blobRange().fileOffset());
            assertEquals(Integer.parseInt(c[2]), s.blobRange().length());
            assertEquals(Integer.parseInt(c[3],16), s.headerWord()); assertEquals(Integer.parseInt(c[7]), s.tileCount());
            assertEquals(s.tileCount()*32,s.outputBytes()); assertEquals(s.headerWord()&0x7FFF,s.tileCount());
            assertEquals(Integer.parseInt(c[4]),s.prefixMinimumConsumedBytes());
            assertEquals(Integer.parseInt(c[5]),s.expectedJavaConsumedBytes());
            assertEquals(Integer.parseInt(c[6]),s.expectedNativeConsumedBytes());
            assertEquals(c[8],s.sha256(CANONICAL)); assertEquals(c[9],s.sha256(JAVA));
            assertEquals(c[10],s.sha256(NATIVE)); assertEquals(c[11],s.decodedSha256());
            assertEquals(Math.max(s.blobRange().length(),s.expectedJavaConsumedBytes()),s.javaReaderRange().length());
            assertEquals(s.expectedNativeConsumedBytes(),s.nativeReaderRange().length());
            for (var range : List.of(s.blobRange(),s.javaReaderRange(),s.nativeReaderRange())) {
                assertEquals(CdNativeFile.R12B,range.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,range.domain());
                assertEquals(s.blobRange().fileOffset(),range.fileOffset());
                assertEquals(0x200000L+range.fileOffset(),s.nativeAddress());
            }
        }
        assertEquals(24,CdR12bProfile.Asset.values().length,"No replacement or expansion of the terrain profile");
        assertThrows(UnsupportedOperationException.class,()->sources.sources().clear());
    }

    @Test void everyActualCanonicalJavaAndNativeViewPinsTheLiteralHashAndOriginalHeader() throws Exception {
        var sources=retail().sectionSources();
        for(var c:columns()) {
            var id=CdR12bSectionSources.SourceId.valueOf(c[0]); var s=sources.source(id);
            for(var kind:CdR12bSectionSources.ReaderKind.values()) {
                var bytes=sources.view(id,kind);
                assertEquals(s.headerWord(),bytes.unsignedWord(0),id+" "+kind);
                assertEquals(c[kind==CANONICAL?8:kind==JAVA?9:10],hash(bytes.copy()),id+" "+kind);
                assertSame(bytes,sources.view(id,kind));
            }
        }
    }

    @Test void nativeLabelAliasesResolveToTheSameCachedDescriptorAndView() throws Exception {
        var s=retail().sectionSources();
        for(var source:s.sources()) {
            assertSame(source,s.sourceForNativeLabel(source.nativeLabel()));
            for(var label:source.nativeLabels())assertSame(source,s.sourceForNativeLabel(label));
        }
        String[][] aliases={
                {"ART_LEVEL_TILES","Art_LevelTiles","StageGfx"},
                {"ART_BLOCK","Art_FloatBlock","Art_Block"},
                {"ART_SPIKES_V2","Art_SpikesV2","Art_Spikes"},
                {"ART_SPRING_45","Art_DiagonalSpring","Art_Spring45"},
                {"ART_SCENERY_B","Art_Scenery","Art_SceneryB"},
                {"ART_ROBOT_TRANSPORT_B","Art_RobotTransport","Art_RobotTransportB","Art_RobotGenerator"},
                {"ART_SWING","Art_Swing","Art_SwingingPlatform"},
                {"ART_HOLOGRAM","Art_Hologram","Art_Projector"},
                {"ART_HOLOGRAM_ANIMALS","Art_HologramAnimals","Art_ProjAnimals"}};
        for(var row:aliases) {
            var id=CdR12bSectionSources.SourceId.valueOf(row[0]);var descriptor=s.source(id);
            for(int i=1;i<row.length;i++)assertSame(descriptor,s.sourceForNativeLabel(row[i]));
            assertThrows(UnsupportedOperationException.class,()->descriptor.nativeLabels().clear());
        }
    }

    @Test void allNineteenNativeListsJoinThePointerKeyedRegistryWithoutChangingEncounterOrder() throws Exception {
        var a=retail();var s=a.sectionSources();var pointers=s.sources().stream().map(CdR12bSectionSources.Source::nativeAddress).toList();
        assertEquals(a.loadPlan().sourcePointers(),pointers);
        assertSame(ART_HOLOGRAM_ANIMALS,s.sources().get(27).id());
        assertSame(ART_MOSQUI,s.sources().get(28).id());
        var seen=new HashSet<Long>();int rows=0;
        for(var list:a.loadPlan().plcs())for(var row:list.entries()) {
            var source=s.source(row.sourceNativeAddress());assertEquals(row.sourceNativeAddress(),source.nativeAddress());
            assertSame(source,s.source(source.id()));seen.add(source.nativeAddress());rows++;
        }
        assertEquals(19,a.loadPlan().plcs().size());assertEquals(116,rows);assertEquals(37,seen.size());
        assertSame(s.source(a.loadPlan().plcs().get(0).entries().getFirst().sourceNativeAddress()),
                s.source(a.loadPlan().plcs().get(3).entries().getFirst().sourceNativeAddress()));
        assertEquals(a.loadPlan().plcs().get(2).entries(),a.loadPlan().plcs().get(15).entries());
        assertEquals(a.loadPlan().plcs().get(2).entries(),a.loadPlan().plcs().get(17).entries());
    }

    @Test void lazyAggregateAndEqualRangeViewsShareCachesAfterTheBorrowedProgramCloses() throws Exception {
        // retail() closes CdProgramAssets before returning; the retained original image remains readable.
        var a=retail();var s=a.sectionSources();
        assertSame(s,a.sectionSources());assertSame(s,CdR12bSectionSources.fromAssets(a));
        assertSame(a.identity(),s.identity());
        for(var source:s.sources()) {
            for(var first:CdR12bSectionSources.ReaderKind.values())for(var second:CdR12bSectionSources.ReaderKind.values())
                if(sourceRange(source,first).equals(sourceRange(source,second)))
                    assertSame(s.view(source.id(),first),s.view(source.id(),second));
        }
        assertSame(a.terrain(),a.terrain());assertSame(a.loadPlan(),a.loadPlan());
    }

    @Test void bindingOnlyBuildsMetadataWithoutReadingOrDecodingFutureHeaders() throws Exception {
        var declared=declared();var sources=CdR12bSectionSources.bind(declared);
        assertEquals(37,sources.sources().size());assertEquals(797,sources.source(ART_LEVEL_TILES).tileCount());
        verify(declared,never()).sectionSourceView(any(),any());
        verify(declared,never()).blocksDecode();verify(declared,never()).levelTilesDecode();
    }

    @Test void publicDescriptiveRecordsCannotConstructAnAuthenticatedAggregate() throws Exception {
        var value=new CdR12bSectionSources.Source(ART_LEVEL_TILES);
        assertEquals(0x23CBA0L,value.nativeAddress());assertEquals(797,value.tileCount());
        assertThrows(NullPointerException.class,()->new CdR12bSectionSources.Source(null));
        for(var constructor:CdR12bSectionSources.class.getDeclaredConstructors())
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        assertEquals(0,CdR12bSectionSources.class.getConstructors().length);
        assertTrue(Modifier.isSynchronized(CdR12bTerrainAssets.class.getMethod("sectionSources").getModifiers()));
    }

    @Test void lookupsRejectNullUnknownAndNonliteralAddressesWithoutRepair() throws Exception {
        var s=retail().sectionSources();
        assertThrows(NullPointerException.class,()->CdR12bSectionSources.fromAssets(null));
        assertThrows(NullPointerException.class,()->s.source((CdR12bSectionSources.SourceId)null));
        assertThrows(NullPointerException.class,()->s.sourceForNativeLabel(null));
        assertThrows(NullPointerException.class,()->s.view(null,CANONICAL));
        assertThrows(NullPointerException.class,()->s.view(ART_BLOCK,null));
        for(long address:new long[]{-1,0,0x200100,0x123CBA0L,0x1_0023CBA0L})
            assertThrows(IllegalArgumentException.class,()->s.source(address));
        assertThrows(IllegalArgumentException.class,()->s.sourceForNativeLabel("art_block"));
        assertThrows(IllegalArgumentException.class,()->s.sourceForNativeLabel("R12A.Art_Block"));
    }

    @Test void inconsistentDeclaredIdentityOrPlanRejectsBeforeAnySourceRead() throws Exception {
        var a=retail();var wrong=declared();var id=a.identity();
        when(wrong.identity()).thenReturn(new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256()));
        assertThrows(IllegalArgumentException.class,()->CdR12bSectionSources.bind(wrong));
        verify(wrong,never()).sectionSourceView(any(),any());
        var missing=declared();var plan=mock(CdR12bLoadPlan.class);
        when(plan.sourcePointers()).thenReturn(a.loadPlan().sourcePointers().subList(0,36));
        when(plan.plcs()).thenReturn(a.loadPlan().plcs());when(plan.sections()).thenReturn(a.loadPlan().sections());when(missing.loadPlan()).thenReturn(plan);
        assertThrows(IllegalArgumentException.class,()->CdR12bSectionSources.bind(missing));
        verify(missing,never()).sectionSourceView(any(),any());
        var foreign=declared();var unknownPlan=mock(CdR12bLoadPlan.class);var lists=new ArrayList<>(a.loadPlan().plcs());
        var first=lists.getFirst();var entries=new ArrayList<>(first.entries());entries.set(0,new CdR12bLoadPlan.PlcEntry(0x200100,0));
        lists.set(0,new CdR12bLoadPlan.PlcList(first.nativeId(),first.bodyRange(),entries));
        when(unknownPlan.sourcePointers()).thenReturn(a.loadPlan().sourcePointers());when(unknownPlan.plcs()).thenReturn(lists);when(unknownPlan.sections()).thenReturn(a.loadPlan().sections());when(foreign.loadPlan()).thenReturn(unknownPlan);
        assertThrows(IllegalArgumentException.class,()->CdR12bSectionSources.bind(foreign));
        verify(foreign,never()).sectionSourceView(any(),any());
    }

    @Test void changedOrShortDeclaredViewsFailWithoutCachingOrFallback() throws Exception {
        var actual=retail().sectionSources();var declared=declared();
        byte[] changed=actual.view(ART_BLOCK,CANONICAL).copy();changed[2]^=1;
        when(declared.sectionSourceView(ART_BLOCK,CANONICAL)).thenReturn(CdAssetBytes.copyOf(changed));
        var s=CdR12bSectionSources.bind(declared);
        assertThrows(IllegalArgumentException.class,()->s.view(ART_BLOCK,CANONICAL));
        when(declared.sectionSourceView(ART_BLOCK,CANONICAL)).thenReturn(actual.view(ART_BLOCK,CANONICAL).slice(0,229));
        assertThrows(IllegalArgumentException.class,()->s.view(ART_BLOCK,CANONICAL));
        when(declared.sectionSourceView(ART_BLOCK,CANONICAL)).thenReturn(actual.view(ART_BLOCK,CANONICAL));
        assertSame(actual.view(ART_BLOCK,CANONICAL),s.view(ART_BLOCK,CANONICAL));
        verify(declared,times(3)).sectionSourceView(ART_BLOCK,CANONICAL);
        verify(declared,never()).sectionSourceView(ART_BLOCK,JAVA);
        verify(declared,never()).sectionSourceView(ART_BLOCK,NATIVE);
    }

    @Test void exportedCopiesCannotMutateOriginalSourceOrTheSharedCachedView() throws Exception {
        var s=retail().sectionSources();
        for(var source:s.sources()) {
            var bytes=s.view(source.id(),NATIVE);byte[] copy=bytes.copy();Arrays.fill(copy,(byte)0xA5);
            assertEquals(source.sha256(NATIVE),hash(bytes.copy()));
            assertSame(bytes,s.view(source.id(),NATIVE));
        }
    }

    @Test void onlyTheFiveJavaAndTwentyOneNativeViewsAddTheirMeasuredOriginalFollowingByte() throws Exception {
        var s=retail().sectionSources();int javaExtra=0,nativeExtra=0;
        var javaIds=new HashSet<CdR12bSectionSources.SourceId>();
        for(var source:s.sources()) {
            int blob=source.blobRange().length();var canonical=s.view(source.id(),CANONICAL);
            for(var kind:List.of(JAVA,NATIVE)) {
                var bytes=s.view(source.id(),kind);assertTrue(bytes.length()==blob || bytes.length()==blob+1);
                assertArrayEquals(canonical.copy(),bytes.slice(0,blob).copy());
                if(bytes.length()>blob) {if(kind==JAVA){javaExtra++;javaIds.add(source.id());}else nativeExtra++;}
            }
        }
        assertEquals(5,javaExtra);assertEquals(21,nativeExtra);
        assertEquals(java.util.Set.of(ART_BLOCK,ART_SPRINGS,ART_MONITOR_TIME_POSTS,ART_POINTS,ART_SPINNING_DISC),javaIds);
        assertEquals(10802,s.view(ART_LEVEL_TILES,CANONICAL).length());
        assertEquals(10802,s.view(ART_LEVEL_TILES,JAVA).length());
        assertEquals(10803,s.view(ART_LEVEL_TILES,NATIVE).length());
        assertEquals(0,s.view(ART_LEVEL_TILES,NATIVE).unsignedByte(10802));
        assertEquals(0x70,s.view(ART_BIG_RING_FLASH,NATIVE).unsignedByte(384));
    }

    @Test void sharedNemesisDecodesAllThirtySevenJavaViewsToLiteralHashesAndSeparateCursors() throws Exception {
        var sources=retail().sectionSources();
        for(var c:columns()) {
            var id=CdR12bSectionSources.SourceId.valueOf(c[0]);int output=Integer.parseInt(c[7])*32;
            var result=CdNemesisAsset.decode(sources.view(id,JAVA).copy(),output,output);
            assertEquals(Integer.parseInt(c[5]),result.consumedInputBytes(),id.toString());
            assertEquals(c[11],hash(result.bytes().copy()),id.toString());
        }
    }

    @Test void everyJavaCursorMinusOneIsAnActualEofRatherThanAZeroPaddedInput() throws Exception {
        var sources=retail().sectionSources();
        for(var c:columns()) {
            var id=CdR12bSectionSources.SourceId.valueOf(c[0]);int output=Integer.parseInt(c[7])*32;
            int cursor=Integer.parseInt(c[5]);
            assertThrows(IOException.class,()->CdNemesisAsset.decode(sources.view(id,JAVA).slice(0,cursor-1).copy(),output,output),id.toString());
            if(cursor>Integer.parseInt(c[2]))
                assertThrows(IOException.class,()->CdNemesisAsset.decode(sources.view(id,CANONICAL).copy(),output,output),id.toString());
        }
    }

    @Test void nativePlcDecoderCompletesAllThirtySevenFiniteViewsWithLiteralHashesAndCursors() throws Exception {
        var sources=retail().sectionSources();
        for(var c:columns()) {
            var id=CdR12bSectionSources.SourceId.valueOf(c[0]);var source=sources.source(id);var output=new ByteArrayOutputStream();
            var state=CdNemesisPlcDecoder.prepare(sources.view(id,NATIVE),source.nativeAddress(),dirtyTable());int tiles=0;
            while(state.remainingTiles()!=0) {var tile=CdNemesisPlcDecoder.decodeTile(state);output.writeBytes(tile.bytes().copy());state=tile.state();assertTrue(++tiles<=Integer.parseInt(c[7]));}
            assertEquals(Integer.parseInt(c[7]),tiles);assertEquals(Integer.parseInt(c[6]),state.inputOffset(),id.toString());
            assertEquals(source.nativeAddress()+Integer.parseInt(c[6]),state.sourcePointer());
            assertEquals(c[11],hash(output.toByteArray()),id.toString());
        }
    }

    @Test void nativeCursorMinusOneReachesFiniteEofWithOnlyEarlierCompleteTilesInTheProduct() throws Exception {
        var sources=retail().sectionSources();
        for(var c:columns()) {
            var id=CdR12bSectionSources.SourceId.valueOf(c[0]);var source=sources.source(id);int limit=Integer.parseInt(c[6])-1;
            int completed=0;var prefix=new ByteArrayOutputStream();
            try {
                var state=CdNemesisPlcDecoder.prepare(sources.view(id,NATIVE).slice(0,limit),source.nativeAddress(),dirtyTable());
                while(state.remainingTiles()!=0){var tile=CdNemesisPlcDecoder.decodeTile(state);prefix.writeBytes(tile.bytes().copy());state=tile.state();assertTrue(++completed<=source.tileCount());}
                fail("Short native view completed: "+id);
            } catch(CdNemesisPlcDecoder.DecodeException fault) {
                assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason(),id.toString());
                assertEquals(limit,fault.diagnostic().inputOffset());assertTrue(completed<source.tileCount());
                var complete=CdNemesisAsset.decode(sources.view(id,JAVA).copy(),source.outputBytes(),source.outputBytes());
                assertEquals(c[11],hash(complete.bytes().copy()));
                assertEquals(completed*32,prefix.size());
                assertArrayEquals(Arrays.copyOf(complete.bytes().copy(),prefix.size()),prefix.toByteArray());
                assertEquals(0,fault.diagnostic().emittedRows().length()%4);
                assertTrue(fault.diagnostic().emittedRows().length()<32,"Attempted partial tile is diagnostic only");
            }
        }
    }

    private CdR12bTerrainAssets retained;
    private CdR12bTerrainAssets retail() throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Configure original CD media root; no silent skip");assertFalse(root.isBlank());
        Path cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            retained=CdR12bTerrainAssets.fromPrograms(programs);
        }
        return retained;
    }
    private CdR12bTerrainAssets declared() throws Exception {
        var actual=retail();var declared=mock(CdR12bTerrainAssets.class);
        when(declared.identity()).thenReturn(actual.identity());when(declared.profile()).thenReturn(actual.profile());when(declared.loadPlan()).thenReturn(actual.loadPlan());return declared;
    }
    private static List<String[]> columns() { return MANIFEST.lines().filter(line->!line.isBlank()).map(line->line.split(" ")).toList(); }
    private static CdR12bProfile.FileRange sourceRange(CdR12bSectionSources.Source source,CdR12bSectionSources.ReaderKind kind) {
        return switch(kind){case CANONICAL->source.blobRange();case JAVA->source.javaReaderRange();case NATIVE->source.nativeReaderRange();};
    }
    private static CdAssetBytes dirtyTable(){byte[] bytes=new byte[512];Arrays.fill(bytes,(byte)0x5A);return CdAssetBytes.copyOf(bytes);}
    private static String hash(byte[] bytes){return CdR11aProfile.sha256(bytes);}
}
