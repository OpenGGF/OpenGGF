package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable original USA R12B PLC-source metadata and lazy retained-image views.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Act 2 Past.asm:8-9,74-91;
 * Graphics Lists (Act 2 Past).asm:7-26 and complete bodies; Data (Act 2 Past).asm:171-313,442-498;
 * Level/_Objects/Level End/Main.asm:936; Functions (General).asm:401-490,664-695.
 * Original USA media and independent Python prefix/lookahead/refill evidence:
 * research/r12b-section-source-readers-20261106.md. Expected Java cursors require execution.
 *
 * A descriptor/record is descriptive, not invocation, decode, current-pattern or native-clear authority.
 * Construction reads no future header and decodes nothing. Only retained original authenticated
 * asset bytes supply views; disassembly/research constants are never fallback payloads.
 */
public final class CdR12bSectionSources {
    public enum ReaderKind { CANONICAL, JAVA, NATIVE }

    /** Exact PLC-directory encounter order, distinct from the research report's physical-body S IDs. */
    public enum SourceId {
        ART_LEVEL_TILES("StageGfx",0x3CBA0,10802,0x831D,10802,10802,10803,
                "c47ef93e567aa11c9726a7d2f4db49b9093700989fe685de301e889af137c8af","c47ef93e567aa11c9726a7d2f4db49b9093700989fe685de301e889af137c8af",
                "df9075f7af1c9cd58fb188225e71ac7d829076ea16c6111bd2f39ef3728b446b","5f09700683b979ae263721baffcc8b47b1dbf03d2ef0715bb89bd8f26f355b7d",
                "Art_LevelTiles","StageGfx"),
        ART_SPRING_WHEEL("Art_SpringWheel",0x37272,182,0x8,181,181,182,
                "b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee","b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee",
                "b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee","dae6ce7abe19d2a44be58d37d804aff175e3b0d541983ee782d60176bcaf63f2",
                "Art_SpringWheel"),
        ART_TUNNEL_DOOR("Art_TunnelDoor",0x376BA,154,0x800C,152,153,154,
                "cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b","cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b",
                "cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b","dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0",
                "Art_TunnelDoor"),
        ART_BLOCK("Art_Block",0x3718C,230,0x800F,230,231,231,
                "8ccc2959a0cbebd2fc24ed2f4cc08fd1baaafbf971f13df254784cc65b7c5e76","131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776",
                "131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776","df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362",
                "Art_FloatBlock","Art_Block"),
        ART_TITLE_CARD("Art_TitleCard",0x3413C,940,0x806D,938,939,940,
                "8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",
                "8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",
                "Art_TitleCard"),
        ART_TITLE_CARD_TEXT("Art_TitleCardText",0x36D90,298,0x8034,297,298,298,
                "39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5","39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5",
                "39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5","66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70",
                "Art_TitleCardText"),
        ART_PLATFORM("Art_Platform",0x36EBA,452,0x8039,452,452,453,
                "0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d","0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d",
                "924566bd57e2d30028d458fb15c95034a5c736931c7dbd751a3edce4ed21593b","b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a",
                "Art_Platform"),
        ART_ANIMALS("Art_Animals",0x38D7C,446,0x14,445,445,446,
                "63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",
                "63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",
                "Art_Animals"),
        ART_SPRINGS("Art_Springs",0x354D4,546,0x8024,546,547,547,
                "19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831","52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",
                "52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",
                "Art_Springs"),
        ART_HUD("Art_HUD",0x36856,300,0x801B,300,300,301,
                "d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb",
                "fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",
                "Art_HUD"),
        ART_MONITOR_TIME_POSTS("Art_MonitorTimePosts",0x356F6,1540,0x8052,1540,1541,1541,
                "ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a","a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",
                "a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",
                "Art_MonitorTimePosts"),
        ART_EXPLOSIONS("Art_Explosions",0x35CFA,1256,0x46,1256,1256,1257,
                "1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1",
                "c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",
                "Art_Explosions"),
        ART_POINTS("Art_Points",0x32180,100,0x5,100,101,101,
                "6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363","9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",
                "9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",
                "Art_Points"),
        ART_FLOWER("Art_Flower",0x36B0A,646,0x8027,645,645,646,
                "baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1","baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1",
                "baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1","db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793",
                "Art_Flower"),
        ART_RINGS("Art_Rings",0x361E2,244,0xE,243,244,244,
                "530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",
                "530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",
                "Art_Rings"),
        ART_SPIKES_V2("Art_SpikesV2",0x38BD4,86,0x8008,86,86,87,
                "4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea","4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea",
                "e6b3bfee35febba9961f852f78580f1571883b49453e21c17af008cdab8d4304","75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72",
                "Art_SpikesV2","Art_Spikes"),
        ART_BOULDER("Art_Boulder",0x3707E,270,0x8013,270,270,271,
                "6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f","6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f",
                "bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48","abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d",
                "Art_Boulder"),
        ART_SPINNING_DISC("Art_SpinningDisc",0x37328,166,0x800C,166,167,167,
                "a2bb54013b05cf8b26ae95194cdab0532c8d24530e89259069bf37e5588b9022","2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d",
                "2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d","97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86",
                "Art_SpinningDisc"),
        ART_GOAL_POST("Art_GoalPost",0x32C96,448,0x8018,448,448,449,
                "24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f",
                "1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",
                "Art_GoalPost"),
        ART_ANTON("Art_Anton",0x37A02,484,0x16,484,484,485,
                "d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a","d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a",
                "e0e121da920926a86b5a445ba9df766931ee4cac91d4e611a3b30fcf5ebba556","2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101",
                "Art_Anton"),
        ART_TAMABBOH("Art_Tamabboh",0x3850C,1142,0x803B,1142,1142,1143,
                "39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f","39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f",
                "c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e","aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95",
                "Art_Tamabboh"),
        ART_PATA_BATA("Art_PataBata",0x37F34,714,0x8032,713,714,715,
                "e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f","e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f",
                "a415ffa282fb1e90378a97f458f05e379a6d82d1a006751d1b85c6efcfc736e3","8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943",
                "Art_PataBata"),
        ART_SPRINGBOARD("Art_Springboard",0x38982,312,0x15,311,311,312,
                "ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc","ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc",
                "ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc","89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e",
                "Art_Springboard"),
        ART_LOG_SHADOW("Art_LogShadow",0x36A7C,24,0x10,22,23,24,
                "025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19","025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19",
                "025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19","fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866",
                "Art_LogShadow"),
        ART_SCENERY_B("Art_SceneryB",0x3B8FA,306,0x8011,306,306,307,
                "f1334c4d9219fb54413cde041362fc19e1873eb93b824ddb7218008d209189db","f1334c4d9219fb54413cde041362fc19e1873eb93b824ddb7218008d209189db",
                "c74b8ee9b9e084304a479a26e219552bab90a317d01ac80c1887531bf70ce760","4dc8389222e4ffcd585afd720ef722fe1e9694399ea8f05eefb3c6748999cfbd",
                "Art_Scenery","Art_SceneryB"),
        ART_CHECKPOINT("Art_Checkpoint",0x36982,212,0x800C,211,211,212,
                "e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",
                "e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",
                "Art_Checkpoint"),
        ART_SPRING_45("Art_Spring45",0x351C8,780,0x2E,779,779,780,
                "3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",
                "3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",
                "Art_DiagonalSpring","Art_Spring45"),
        ART_HOLOGRAM_ANIMALS("Art_HologramAnimals",0x3F784,434,0x14,434,434,435,
                "61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef",
                "51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",
                "Art_HologramAnimals","Art_ProjAnimals"),
        ART_MOSQUI("Art_Mosqui",0x37BE6,846,0x46,846,846,847,
                "b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892","b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892",
                "7696cc4d61b4f4607afcafdb693994cd65972efce4949f18a622dd630df8c4b5","230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd",
                "Art_Mosqui"),
        ART_ROBOT_TRANSPORT_B("Art_RobotTransportB",0x3F5D2,434,0x17,433,434,434,
                "4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806","4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806",
                "4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806","672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6",
                "Art_RobotTransport","Art_RobotTransportB","Art_RobotGenerator"),
        ART_SWING("Art_Swing",0x38C2A,338,0xE,337,338,338,
                "1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621","1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621",
                "1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621","d19d3dc9345daa92d6937f38f716008340c820078be7f7bd4355cfbc63937da4",
                "Art_Swing","Art_SwingingPlatform"),
        ART_TAGA_TAGA("Art_TagaTaga",0x381FE,782,0x802D,782,782,783,
                "66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732","66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732",
                "8a1766a33e7c721ea4f348ee8306fe34179805072ca4a2f7f44c80b3960cbaf1","70dc53f69da2592c19459ec9b591980da55eb27e50e5352f9c5172121ab8fa49",
                "Art_TagaTaga"),
        ART_HOLOGRAM("Art_Hologram",0x3F936,1024,0x33,1023,1024,1024,
                "1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",
                "1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",
                "Art_Hologram","Art_Projector"),
        ART_RESULTS("Art_Results",0x33432,1354,0x8078,1353,1354,1354,
                "c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",
                "c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",
                "Art_Results"),
        ART_SIGNPOST("Art_Signpost",0x32E56,1500,0x804C,1499,1500,1501,
                "2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67",
                "c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",
                "Art_Signpost"),
        ART_BIG_RING("Art_BigRing",0x3288C,1034,0x806A,1033,1033,1034,
                "5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",
                "5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",
                "Art_BigRing"),
        ART_BIG_RING_FLASH("Art_BigRingFlash",0xD7F2,384,0x804D,384,384,385,
                "6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7",
                "e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",
                "Art_BigRingFlash");

        private final String label;
        private final int offset, blobBytes, header, prefixCursor, javaCursor, nativeCursor;
        private final String blobSha, javaSha, nativeSha, decodedSha;
        private final List<String> labels;

        SourceId(String label,int offset,int blobBytes,int header,int prefixCursor,int javaCursor,int nativeCursor,
                 String blobSha,String javaSha,String nativeSha,String decodedSha,String... labels) {
            if(offset<0 || (offset&1)!=0 || blobBytes<3 || offset>CdR12bProfile.FILE_BYTES-blobBytes-1
                    || (header&~0xFFFF)!=0 || (header&0x7FFF)==0 || (header&0x7FFF)>2048
                    || prefixCursor<3 || prefixCursor>javaCursor || javaCursor>nativeCursor
                    || javaCursor>blobBytes+1 || nativeCursor<blobBytes || nativeCursor>blobBytes+1)
                throw new IllegalArgumentException("Invalid fixed R12B source/reader metadata");
            this.label=Objects.requireNonNull(label);
            this.offset=offset;this.blobBytes=blobBytes;this.header=header;
            this.prefixCursor=prefixCursor;this.javaCursor=javaCursor;this.nativeCursor=nativeCursor;
            this.blobSha=hash(blobSha);this.javaSha=hash(javaSha);this.nativeSha=hash(nativeSha);this.decodedSha=hash(decodedSha);
            this.labels=List.of(labels);
            if(this.labels.isEmpty() || !this.labels.contains(label) || new HashSet<>(this.labels).size()!=this.labels.size()
                    || this.labels.stream().anyMatch(String::isBlank)
                    || (javaCursor<=blobBytes && !blobSha.equals(javaSha))
                    || (nativeCursor==blobBytes && !blobSha.equals(nativeSha)))
                throw new IllegalArgumentException("Inconsistent fixed R12B labels/reader hashes");
        }

        CdR12bProfile.FileRange range(ReaderKind kind) {
            int length=switch(Objects.requireNonNull(kind)) {
                case CANONICAL -> blobBytes;
                case JAVA -> Math.max(blobBytes,javaCursor);
                case NATIVE -> nativeCursor;
            };
            return new CdR12bProfile.FileRange(CdNativeFile.R12B,CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,length);
        }
        private static String hash(String value) {
            if(!Objects.requireNonNull(value).matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Expected source SHA-256");
            return value;
        }
    }

    /** Metadata only; constructing this record cannot construct or rebind an authenticated aggregate. */
    public record Source(SourceId id) {
        public Source { Objects.requireNonNull(id); }
        public String nativeLabel() { return id.label; }
        /** Complete literal native names, including the primary label; aliases share one aggregate record. */
        public List<String> nativeLabels() { return id.labels; }
        public CdR12bProfile.FileRange blobRange() { return id.range(ReaderKind.CANONICAL); }
        public CdR12bProfile.FileRange javaReaderRange() { return id.range(ReaderKind.JAVA); }
        public CdR12bProfile.FileRange nativeReaderRange() { return id.range(ReaderKind.NATIVE); }
        public long nativeAddress() { return Integer.toUnsignedLong(blobRange().nativeAddress()); }
        public int headerWord() { return id.header; }
        public int tileCount() { return id.header&0x7FFF; }
        public int outputBytes() { return tileCount()*32; }
        public int prefixMinimumConsumedBytes() { return id.prefixCursor; }
        /** Independent research expectation, separate from blob-preserving Java input length. */
        public int expectedJavaConsumedBytes() { return id.javaCursor; }
        /** Independent refill-model expectation, not an executed native cursor. */
        public int expectedNativeConsumedBytes() { return id.nativeCursor; }
        public String sha256(ReaderKind kind) {
            return switch(Objects.requireNonNull(kind)) {
                case CANONICAL -> id.blobSha;
                case JAVA -> id.javaSha;
                case NATIVE -> id.nativeSha;
            };
        }
        /** Independent complete decoded-product witness; no product is decoded/cached here. */
        public String decodedSha256() { return id.decodedSha; }
    }

    private final CdR12bTerrainAssets assets;
    private final CdR12bTerrainAssets.Identity identity;
    private final List<Source> sources;
    private final Map<Long,Source> byAddress;
    private final Map<String,Source> byLabel;
    // Lazy immutable byte values only; no queue, decoder, residency, lifecycle or runtime owner.
    private final CdAssetBytes[][] views=new CdAssetBytes[SourceId.values().length][ReaderKind.values().length];

    private CdR12bSectionSources(CdR12bTerrainAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual R12B terrain assets");
        identity=Objects.requireNonNull(assets.identity(),"original source identity");
        var profile=Objects.requireNonNull(assets.profile(),"original R12B profile");
        var plan=Objects.requireNonNull(assets.loadPlan(),"original R12B load plan");
        if(identity.variant()!=CdReleaseVariant.USA || identity.file()!=CdNativeFile.R12B
                || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || !identity.stage().equals(CdStagePayload.R12B.stage())
                || !identity.mmdSha256().equals(profile.expectedMmdSha256())
                || profile.variant()!=identity.variant() || profile.file()!=identity.file()
                || profile.domain()!=identity.domain() || !profile.stage().equals(identity.stage())
                || plan.plcs().size()!=19 || plan.sections().size()!=5)
            throw new IllegalArgumentException("Actual original USA R12B assets/profile/plan required");
        var ordered=new ArrayList<Source>();var addresses=new HashMap<Long,Source>();var labels=new HashMap<String,Source>();
        for(var id:SourceId.values()) {
            var source=new Source(id);ordered.add(source);
            if(addresses.putIfAbsent(source.nativeAddress(),source)!=null)
                throw new IllegalArgumentException("Duplicate fixed R12B native source");
            for(var label:source.nativeLabels())if(labels.putIfAbsent(label,source)!=null)
                throw new IllegalArgumentException("Ambiguous fixed R12B native label");
        }
        if(!plan.sourcePointers().equals(ordered.stream().map(Source::nativeAddress).toList()))
            throw new IllegalArgumentException("R12B pointer inventory/order and source registry differ");
        var covered=new HashSet<Long>();
        for(var list:plan.plcs())for(var row:list.entries()) {
            if(!addresses.containsKey(row.sourceNativeAddress()))
                throw new IllegalArgumentException("R12B PLC row has no fixed original source");
            covered.add(row.sourceNativeAddress());
        }
        if(!covered.equals(addresses.keySet()))throw new IllegalArgumentException("Incomplete R12B PLC/source join");
        sources=List.copyOf(ordered);byAddress=Map.copyOf(addresses);byLabel=Map.copyOf(labels);
    }

    /** Returns the actual asset's single lazy registry. No public image/Identity/range ingress exists. */
    public static CdR12bSectionSources fromAssets(CdR12bTerrainAssets assets) {
        return Objects.requireNonNull(assets,"actual R12B terrain assets").sectionSources();
    }
    /** Package-local cache construction, not a public asserted-identity factory. */
    static CdR12bSectionSources bind(CdR12bTerrainAssets assets) { return new CdR12bSectionSources(assets); }

    public CdR12bTerrainAssets.Identity identity() { return identity; }
    public List<Source> sources() { return sources; }
    public Source source(SourceId id) { return sources.get(Objects.requireNonNull(id).ordinal()); }
    public Source source(long nativeAddress) {
        var source=byAddress.get(nativeAddress);
        if(source==null)throw new IllegalArgumentException("Unknown literal R12B PLC source address");
        return source;
    }
    public Source sourceForNativeLabel(String nativeLabel) {
        var source=byLabel.get(Objects.requireNonNull(nativeLabel));
        if(source==null)throw new IllegalArgumentException("Unknown literal R12B PLC source label");
        return source;
    }

    /** Finite original bytes only; equal ranges reuse one immutable value, with no decode or padding. */
    public synchronized CdAssetBytes view(SourceId id,ReaderKind kind) {
        var source=source(id);Objects.requireNonNull(kind);var cache=views[id.ordinal()];
        if(cache[kind.ordinal()]!=null)return cache[kind.ordinal()];
        var range=id.range(kind);
        for(var existing:ReaderKind.values())if(cache[existing.ordinal()]!=null && range.equals(id.range(existing))) {
            cache[kind.ordinal()]=cache[existing.ordinal()];return cache[kind.ordinal()];
        }
        var bytes=Objects.requireNonNull(assets.sectionSourceView(id,kind),"retained R12B source bytes");
        if(bytes.length()!=range.length() || bytes.unsignedWord(0)!=source.headerWord()
                || !source.sha256(kind).equals(CdR11aProfile.sha256(bytes.copy())))
            throw new IllegalArgumentException("Retained R12B finite source bytes differ: "+id+" "+kind);
        cache[kind.ordinal()]=bytes;return bytes;
    }
}
