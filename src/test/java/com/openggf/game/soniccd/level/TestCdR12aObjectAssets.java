package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ReadOnlyBufferException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.openggf.game.soniccd.level.CdR12aProfile.Asset.*;
import static org.junit.jupiter.api.Assertions.*;

/** ALL JAVA UNRUN. Original-USA finite data only. Native1ecc5f3a PP Data/Graphics Lists (Act2 Present),
 * Object Spawner, Ring/Spring/Monitor/HUD data, palette scripts and General.Nemesis/LoadPLC.
 * Expected metadata derives from the authenticated original-file closure, not executed Java/native output.
 * No World, current patterns, art bank, PLC service, resident bytes, transition, route or pixel claim. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12aObjectAssets {
    private static final String MANIFEST = """
LEVEL_CHUNKS 10000 54272 543b274ee37af99c708def3a77b955af7de0bb647cf29e30449073846e8bfa05
LEVEL_COLLISION 3b274 900 792dc7e15353c46072e4666690ed56ef4c7b5e286e3997ba50b80d8f462c9279
LAYOUT_FG 3b640 170 043ab212228a731bb767b78252025829f50b9b2a8a55e643f02116d561ad3dd6
LAYOUT_BG 3b6ea 22 117166a3f48d7116f098af950ff923311c4062bc004c6ae33165f2edd75e6963
LEVEL_BLOCKS 3b8fa 4600 cdd9e6df1b00f252d4ce05024dd7d9c8b435447889b94432594c3789e223552e
ART_LEVEL_TILES 3caf2 10722 69ed4922c635f4517652327f45f1a6425ef6dee34b5df7c4b27ceb6d5a2ff8f3
COL_ANGLE_MAP 39174 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
COL_HEIGHT_MAP 39274 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
COL_WIDTH_MAP 3a274 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
LEVEL_DATA_INDEX f09e 16 a75ccfbd24d59d96c0b932625f5e2c059d92c0ad8125454a110c91f951b76bb8
LEVEL_LAYOUTS 3b5f8 72 0c5ca7cb062310b35fc7f9d039c397ad3f4f0ad1bf1eff5724e2237906918d77
LEVEL_COL_INDEX 1a9a 32 d787e684f62191c78644c363e4b512117c29e1b1ab9153ea925b6708fc2d79a7
CAM_BOUNDS 2954 12 fadb413a244c2a9cc5336b23ff20a78505d3353f4cd32a51c9b2ca63b16afa97
LEVEL_START_LOC 2a18 4 9d2ad78f5ddd7b07d1a2145471d7331829111f8e9a8cbda83855db181580f6d0
PALETTE_TABLE 5a4 48 91c31f279be8dc9bfa64456b2d9a726bec6a86ea52d726079ea3a094b7a14b20
PAL_SONIC 6d4 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
PAL_LEVEL 6f4 96 436a0046a269e03d256e48ea8d5ad4965bae9127e9ba4ba1fef3df29b2f155b0
ART_SONIC 20000 69472 94edeaff466c9e0c8ddd0b090a99b24bca663b8c40ac1dcd45c530de60e9ce64
MAP_SPR_SONIC 30f60 3004 88968a4a46556ad579811c935d1ad2f9eb2df08f4658c424a20f75c265edec0c
DPLC_SONIC 31b1c 1636 0c5298383621ae7515a6f3f4b6cdca6704d3a446ef15264f002f9e9fe5fbaaea
ANI_SONIC 582e 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
SONIC_MINI_ANIMATIONS 57de 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
SONIC_RAMP_CHUNKS 45a4 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
SPEC_CHUNKS 2a1c 4 674d922d5583edfb5fba68eb6619dd121fe0387c3bbf2e0504d95420827b2f2f
SINE_TABLE 872 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
ARC_TAN_TABLE b58 258 7d0ff986365e0596080368cab93935822e1f39220f50071db5dd075c0cabe66f
BG_CAMERA_SECT_IDS 3428 34 81e548cbfa2e4fc9719ba3b2009a8abb80bd2a54e3f3e8ec2187589e62e4801c
BG_CAMERA_SECTS 344a 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
PLC_LISTS f0ae 564 d474876c0e16e914e971b4f735a14e09834be52ee37cfeeebba6b016a90efe11
ART_ANIMALS 38d7c 446 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab
ART_ANTON 37a02 484 d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a
ART_BIG_RING 3288c 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
ART_BIG_RING_FLASH d866 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
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
ART_RESULTS 33432 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
ART_RINGS 361e2 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
ART_ROBOT_GEN_WITHERED 39028 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
ART_SIGNPOST 32e56 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
ART_SPIKES 38bd4 86 4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea
ART_SPINNING_DISC 37328 166 a2bb54013b05cf8b26ae95194cdab0532c8d24530e89259069bf37e5588b9022
ART_SPRING_WHEEL 37272 182 b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee
ART_SPRINGBOARD 38982 312 ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc
ART_SPRINGS 354d4 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
ART_SWING 38c2a 338 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621
ART_TAGA_TAGA 381fe 782 66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732
ART_TAMABBOH 3850c 1142 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f
ART_TITLE_CARD 3413c 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
ART_TITLE_CARD_TEXT 36d90 298 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5
ART_TUNNEL_DOOR 376ba 154 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b
ART_TUNNEL_DOOR_SPLASH 37754 686 af531639acf1f86a401fb7de3bba9946eecb42bdc88a8ec4f7d13ee476eb6072
OBJECT_LAYOUTS 776a 3930 1e32466be6a13b758700341964febb20d5deee1bb50a915e836a56aa0f3558c2
OBJECT_INDEX 3cc8 256 41515ac6ac198653e70b735f5dcc49d488f7731e36897093a6193d1abaa8bd99
MAP_SPR_RING 9f22 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
ANI_RING 9f1a 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
MAP_SPRING 99c8 199 f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd
ANI_SPRING 99ba 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
MAP_SPR_HUD ab8e 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
ANI_MONITOR a7ee 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
MAP_SPR_MONITOR_TIME a876 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
SECTION_DATA cc32 30 6de0f5b35a5aa6b2e0f0639f19b6ef9cebd058ed44e4c4d2e4043a9fb6649c6e
OBJECT_TILE_IDS cc72 74 b65e184ba251551d45646d87b6651dd76f04c8bb1024622a8b8a77b404cd966b
RING_DELTAS 9abe 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
SPRING_SPEEDS 9710 4 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621
TOUCH_DIMENSIONS 6f5a 126 658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d
ENEMY_POINTS 6d70 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
HUD_RESET_TILES aec4 12 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c
HUD_DIVISORS b024 40 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea
PALETTE_CYCLE_DATA 2c0 84 10944706086aebf5803d68a3c5f4e8d3a6036520eacd9ecbd9d005f1b8d225f7
ART_LIFE_ICON 362d6 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
ART_HUD_NUMBERS 36456 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
ART_SHIELD 344e8 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
ART_INV_STARS 34968 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
ART_TIME_STARS 34de8 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
TITLE_CARD_DATA e1ce 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
TITLE_CARD_MAPPINGS e20e 276 a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11
MAP_SPR_BREAKABLE_WALL eed8 312 c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226
BREAKABLE_WALL_PIECE_FRAMES f010 64 9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f
BREAKABLE_WALL_PIECE_DELTAS f050 24 86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94
BREAKABLE_WALL_PIECE_SPEEDS f068 48 7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31
MAP_SPR_BOULDER 947c 14 f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652
MAP_SPR_PLATFORM c9f2 132 a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7
PLATFORM_WIDTHS c66a 4 87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3
PLATFORM_RANGES c66e 4 ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a
ANI_ANTON b1c6 12 623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d
MAP_SPR_ANTON b1d2 112 898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808
ANI_PATA_BATA b344 18 a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3
MAP_SPR_PATA_BATA1 b356 34 9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b
MAP_SPR_PATA_BATA2 b378 40 96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889
ANI_TAMABBOH_NORMAL b76a 12 ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474
ANI_TAMABBOH_DAMAGED b776 26 09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84
MAP_SPR_TAMABBOH b790 62 e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847
ANI_TAMABBOH_MISSILE b890 8 764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5
MAP_SPR_TAMABBOH_MISSILE b898 42 10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb
ANI_FLOAT_BLOCK a204 30 3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9
MAP_SPR_FLOAT_BLOCK a222 76 98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e
ANI_CHECKPOINT 1d8a6 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
MAP_SPR_CHECKPOINT 1d8ac 66 709d0648dfb32425f46281c236ededaef80750fc6250062f3bce77f9427a446f
MAP_SPR_COLLAPSE_PLATFORM_1 c348 118 e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12
COLLAPSE_PLATFORM_SIZES_1 c3be 24 9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1
MAP_SPR_COLLAPSE_PLATFORM_3 c3d6 62 77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c
MAP_SPR_COLLAPSE_PLATFORM_2 c414 286 be1266acacef995f34fa47ff432a198d5bfeddc6af3484949a0f26348d04cb2b
COLLAPSE_PLATFORM_SIZES_2 c532 24 c0069f95af27ab8e2b9913aab0991fe10e39feaf4bcaed16e3c7e9595a5b995a
MAP_SPR_COLLAPSE_PLATFORM_4 c54a 42 076fa0be74347e48024976fe775b0b2bc254fa2a1d8b358588406bf48c23126a
LOG_SHADOW_BASE_TILES 8806 16 b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637
MAP_SPR_LOG_SHADOW 8816 16 bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b
ANI_MOSQUI_NORMAL b4f2 14 24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2
ANI_MOSQUI_DAMAGED b500 14 cf64e547e8082aac25e26ac28799500505b45169aababaed6d42e9b144990408
MAP_SPR_MOSQUI_NORMAL b50e 60 5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e
MAP_SPR_MOSQUI_DAMAGED b54a 66 23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313
ANI_EXPLOSION 1d700 20 9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649
MAP_SPR_EXPLOSION 1d714 146 0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44
MAP_SPR_POINTS aa8c 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
ANI_FLOWER 36a94 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
MAP_SPR_FLOWER 36ab0 90 48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41
ANI_TUNNEL_DOOR 1d6b6 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
MAP_SPR_TUNNEL_DOOR 1d6c4 28 7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92
TUNNEL_PATH_GROUND_SPEEDS 8bb8 8 6421e780611a8a1f2d7c46b123246ca2072d8d8115454f62cdae39738a6e9836
TUNNEL_PATH_COORDINATES 8bc0 284 ffdca0eca125c54bf14c076060a27657bf82bcc3ce52d3e912c4ac14e5b5cd93
TUNNEL_PATH_MAPPING_ADDRESS 8994 4 d8dbb822f69324f4e22b24211ac6e036ae11d483ad9940f613f0d575bb36446a
ANI_POWERUP 1d400 296 ecd3e5667b53fd56bf9b9c7fa3e9e09cbb957efe90232419060f8197f0f50141
MAP_SPR_POWERUP 1d528 274 d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc
            """;
    private static final String READERS = """
ART_ANIMALS 640 446 446 445 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab 63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1
ART_ANTON 704 484 485 484 d51cfb7ef7c2bf4d6fe52da4042f09eae26130838ed503c3e64956607cce3d2a e0e121da920926a86b5a445ba9df766931ee4cac91d4e611a3b30fcf5ebba556 2e0a7e3f6719c8afa2c569d5078824dd7c522a04eeee75fa773830ceea95b101
ART_BIG_RING 3392 1034 1034 1033 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6 31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c
ART_BIG_RING_FLASH 2464 384 385 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7 e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b 39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6
ART_BOULDER 608 270 271 270 6335b858cc043dcce7de3f716a506c5d393342298729c04739673331f0e4e55f bf9f0cf2200b515b5f84c8daf70b4d78dcbcd92d432e2f305aebdc7aa0fb2f48 abdbe20715a2c4784fae851622bb276dc1630abc339efb1e13b99a5ddc4d0c7d
ART_CHECKPOINT 384 212 212 211 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3
ART_DIAGONAL_SPRING 1472 780 780 779 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a 1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b
ART_EXPLOSIONS 2240 1256 1257 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1 c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe 434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664
ART_FLOAT_BLOCK 480 231 231 231 131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776 131147276dd39d62a3537cd9ed2390b53ece4ab2cc5e2caa5e39c890d4de5776 df65b0523d19f52de3f61737c9d130bd493c1591077767651012ac94047e8362
ART_FLOWER 1248 646 646 645 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 baae7501cf372a57007db352ffcfe404cc8d2066a9cc639e64d1f2796cfe9af1 db06551b90412becaec9aa45ccfd14dca62c94e1f165718629553f8fdaac6793
ART_GOAL_POST 768 448 449 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f 1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250 68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a
ART_HUD 864 300 301 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5 0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc
ART_LEVEL_TILES 25952 10723 10723 10723 fd9621723b2df180af75d6757cd048ae16fc0522d6c7b19d2319d25e3388a498 fd9621723b2df180af75d6757cd048ae16fc0522d6c7b19d2319d25e3388a498 8f4effe8f6355149077d0fcef7d6f933150a28f073ae7b08ac40148b9012cd97
ART_LOG_SHADOW 512 24 24 23 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19 025a909ae3b46a5005c04784773e032147799851c0fe4a105a9c0ee9e3279b19 fa208fd33608e8a21ed13a7c9a92cdbbd6a936acd1a377f4ac10e9d333113866
ART_MONITOR_TIME_POSTS 2624 1541 1541 1541 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902 7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f
ART_MOSQUI 2240 846 847 846 b4ab1cf236c3fc6b4a1c44b7d5f2a04a3c4d9d200f50d08ad3f051a408966892 7696cc4d61b4f4607afcafdb693994cd65972efce4949f18a622dd630df8c4b5 230205b2348e61dece2c63201ba1c46285dd2d0bb74d45fb4daa402ca91c62bd
ART_PATA_BATA 1600 714 715 714 e492d968000f8097ed65ddaaf3734c11dce4d389ea74488610defa87f923f34f a415ffa282fb1e90378a97f458f05e379a6d82d1a006751d1b85c6efcfc736e3 8e7cc0d2af986240146ffd214038f1b2715c71227226261be477cb689eac8943
ART_PLATFORM 1824 452 453 452 0d6cb948568e1e1c82c94d57b3bf089175026ed05b21d9b477603c5a7725928d 924566bd57e2d30028d458fb15c95034a5c736931c7dbd751a3edce4ed21593b b33a50d46c89a5115a18548e6a3b083428c8a5bd2bcafc3c4c45e2718c5c768a
ART_POINTS 160 101 101 101 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176 2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db
ART_RESULTS 3840 1354 1354 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40 fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d
ART_RINGS 448 244 244 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2 c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb
ART_ROBOT_GEN_WITHERED 480 332 332 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67
ART_SIGNPOST 2432 1500 1501 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67 c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d 7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c
ART_SPIKES 256 86 87 86 4d6ff2ea465d6436ca113d8305d91ea8bca8f699b7621410d1d257679e3923ea e6b3bfee35febba9961f852f78580f1571883b49453e21c17af008cdab8d4304 75e3391d4045ec96fa925a79bba808a88425444e43d4694f105e7085057c2f72
ART_SPINNING_DISC 384 167 167 167 2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d 2ffa2baba308902cc6db0c11ac238ffe47c08fd1737d0fa6fd64967adc649c6d 97ff27ee8b9ef0f8903cf420bafd651f954e4dd1f3468bd71e829dc932f24e86
ART_SPRING_WHEEL 256 182 182 181 b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee b308180df8365237f56f7a1a8fdd1137d381f517b1de5745ae5074ce46208eee dae6ce7abe19d2a44be58d37d804aff175e3b0d541983ee782d60176bcaf63f2
ART_SPRINGBOARD 672 312 312 311 ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc ba93dcc624f5f68fb44a75c64efe6e25d9bf2723abc547061b899d59b6e40bdc 89c1122854699aa7cf8f11633d1512e0bf7acdc84eda2cfd945e9132e657594e
ART_SPRINGS 1152 547 547 547 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847 8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416
ART_SWING 448 338 338 338 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621 1a519480f29f88b56ddec7c1e881281a098705e194f9ca4e0f451fe7f85ca621 d19d3dc9345daa92d6937f38f716008340c820078be7f7bd4355cfbc63937da4
ART_TAGA_TAGA 1440 782 783 782 66a8d8eff846c3f0a3b8ce1d4f5e8d648714d53cfdf36a062b0f4ded115c3732 8a1766a33e7c721ea4f348ee8306fe34179805072ca4a2f7f44c80b3960cbaf1 70dc53f69da2592c19459ec9b591980da55eb27e50e5352f9c5172121ab8fa49
ART_TAMABBOH 1888 1142 1143 1142 39b42a53ad2a2b1e50c73d2f0abdb918304fd8987499af1a4d8773b9239c309f c90255c16ce2a453a6dad66e1b9ff641e12b2fc87e5b639429e1ef1962978b0e aa544f679799481cc55fe2f329ef30be56ee2c8f8a655e5509f02d4035cbed95
ART_TITLE_CARD 3488 940 940 939 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8 68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a
ART_TITLE_CARD_TEXT 1664 298 298 298 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 39ec1617bda91d34562b315729f003e09c907ec95da5bffb31333b2c4dead3e5 66b74c71966af3b2bdf42828b3a0b5e5fcf73f05c8cae89fa2ff3f28eb01ee70
ART_TUNNEL_DOOR 384 154 154 153 cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b cbbd577ac9af46007b2618fd0481753435aae38e0f68fe0ae7ed17df74b0cb7b dc7be7cdc86c642af5aafd7462269397a6d901678241fde33a35b9209eaf22a0
ART_TUNNEL_DOOR_SPLASH 2752 687 687 687 07f932856a04af0dd72285fc949e06a5e0269a13809cbcc8cd74c3956b003b3a 07f932856a04af0dd72285fc949e06a5e0269a13809cbcc8cd74c3956b003b3a b38d090045f8bf7488b0bdd78cfb244c008764293bc64f275a3f7716d5918f38
            """;
    private static final String PLCS = """
0 f0d4 ART_LEVEL_TILES:0,ART_CHECKPOINT:d960
1 f0e2 ART_SPIKES:6600,ART_SPRING_WHEEL:6700,ART_TUNNEL_DOOR:69c0,ART_TITLE_CARD:6c00,ART_TITLE_CARD_TEXT:7a00,ART_PLATFORM:97c0,ART_GOAL_POST:9ee0,ART_SPRINGS:a400,ART_HUD:ad00,ART_MONITOR_TIME_POSTS:b500,ART_EXPLOSIONS:d000,ART_POINTS:d8c0,ART_FLOWER:dae0,ART_RINGS:f5c0
2 f138 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
3 f0d4 ART_LEVEL_TILES:0,ART_CHECKPOINT:d960
4 f17c ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_MOSQUI:74e0,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
5 f1ba ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_TUNNEL_DOOR_SPLASH:74e0,ART_ROBOT_GEN_WITHERED:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
6 f1f2 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_ANTON:7b60,ART_ROBOT_GEN_WITHERED:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
7 f230 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_TAGA_TAGA:7b60,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
8 f268 ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_DIAGONAL_SPRING:9200
9 f288 ART_MOSQUI:74e0,ART_LOG_SHADOW:7e00,ART_ANTON:8000
10 f29c ART_TUNNEL_DOOR_SPLASH:74e0,ART_ROBOT_GEN_WITHERED:8000
11 f2aa ART_PATA_BATA:74e0,ART_ANTON:7b60,ART_ROBOT_GEN_WITHERED:8000
12 f2be ART_TAGA_TAGA:7b60
13 f138 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
14 f138 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
15 f138 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
16 f2c6 ART_RESULTS:7880
17 f138 ART_FLOAT_BLOCK:6b40,ART_BOULDER:6d80,ART_SPINNING_DISC:7000,ART_ANIMALS:71e0,ART_PATA_BATA:74e0,ART_SPRINGBOARD:7b60,ART_LOG_SHADOW:7e00,ART_ANTON:8000,ART_SWING:8300,ART_TAMABBOH:8580,ART_DIAGONAL_SPRING:9200
18 f2ce ART_SIGNPOST:8780,ART_BIG_RING:9100,ART_BIG_RING_FLASH:7de0
            """;

    @Test void retainedTerrainOwnsOneLazyObjectSourceAfterTheActualCatalogueCueCloses() throws Exception {
        var a = retail().terrain(); var objects = a.objects();
        assertSame(objects, a.objects()); assertSame(a.identity(), objects.identity()); assertSame(a.profile(), objects.profile());
        assertEquals(CdNativeFile.R12A, objects.identity().file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, objects.identity().domain());
        assertEquals("6091e19b34358cf241e9b51f222b6ea2202230ff0b684b44f9ed050cf840b5e7", objects.identity().mmdSha256());
        assertEquals(objects.identity().mmdSha256(), CdR11aProfile.sha256(retail().original().readFile(0, 262144)));
        assertNotNull(objects.view(OBJECT_LAYOUTS)); assertNotNull(objects.rings());
    }

    @Test void complete134RowManifestPreservesTheOriginal132AndPinsEveryAdditionalOriginalDomainSpan() throws Exception {
        var objects = retail().terrain().objects(); var rows = MANIFEST.lines().filter(s -> !s.isBlank()).toList();
        assertEquals(134, CdR12aProfile.Asset.values().length); assertEquals(134, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var c = rows.get(i).split(" "); var a = CdR12aProfile.Asset.values()[i]; var r = objects.profile().range(a);
            assertEquals(c[0], a.name()); assertEquals(Integer.parseInt(c[1], 16), r.fileOffset()); assertEquals(Integer.parseInt(c[2]), r.length());
            assertEquals(CdNativeFile.R12A, r.file()); assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM, r.domain());
            assertEquals(0x200000 + r.fileOffset(), r.nativeAddress()); assertEquals(c[3], CdR11aProfile.sha256(objects.view(a).copy()), a.name());
            assertArrayEquals(retail().original().readFile(r.fileOffset(), r.length()), objects.view(a).copy());
            assertSame(objects.view(a), objects.view(a));
        }
        assertEquals(26, BG_CAMERA_SECT_IDS.ordinal()); assertEquals(27, BG_CAMERA_SECTS.ordinal()); assertEquals(28, PLC_LISTS.ordinal());
        assertThrows(NullPointerException.class, () -> objects.view(null));
    }

    @Test void all35CanonicalJavaAndNativeReaderExtentsRetainActualHashesAndLazySharedNemesisProducts() throws Exception {
        var a = retail().terrain(); var objects = a.objects(); var rows = READERS.lines().filter(s -> !s.isBlank()).toList();
        assertEquals(35, objects.readerProfiles().size()); assertEquals(35, rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var c = rows.get(i).split(" "); var asset = CdR12aProfile.Asset.valueOf(c[0]); var p = objects.readerProfile(asset);
            assertSame(p, objects.readerProfiles().get(i)); assertEquals(objects.profile().range(asset), p.sourceBlobRange());
            assertEquals(Integer.parseInt(c[1]), p.outputBytes()); assertEquals(Integer.parseInt(c[2]), p.javaInputRange().length());
            assertEquals(Integer.parseInt(c[3]), p.nativeInputRange().length()); assertEquals(Integer.parseInt(c[4]), p.javaConsumedInputBytes());
            var javaBytes = objects.readerView(asset, CdR12aObjectAssets.ReaderKind.JAVA);
            var nativeBytes = objects.readerView(asset, CdR12aObjectAssets.ReaderKind.NATIVE);
            assertEquals(c[5], CdR11aProfile.sha256(javaBytes.copy())); assertEquals(c[6], CdR11aProfile.sha256(nativeBytes.copy()));
            assertArrayEquals(retail().original().readFile(asset.fileOffset(), p.javaInputRange().length()), javaBytes.copy());
            assertArrayEquals(retail().original().readFile(asset.fileOffset(), p.nativeInputRange().length()), nativeBytes.copy());
            var decoded = objects.decode(asset); assertSame(decoded, objects.decode(asset)); assertSame(p, decoded.profile());
            assertEquals(p.javaConsumedInputBytes(), decoded.consumedInputBytes()); assertEquals(p.outputBytes(), decoded.bytes().length());
            assertEquals(c[7], CdR11aProfile.sha256(decoded.bytes().copy()));
        }
        assertSame(a.levelTilesDecode().bytes(), objects.decode(ART_LEVEL_TILES).bytes());
        assertThrows(UnsupportedOperationException.class, () -> objects.readerProfiles().clear());
    }

    @Test void all19NativeListsPreserveOrderedPointersDestinationsAndStageSectionAliasesWithoutBuildingAQueue() throws Exception {
        var objects = retail().terrain().objects(); var lists = objects.plcLists(); var rows = PLCS.lines().filter(s -> !s.isBlank()).toList();
        assertSame(lists, objects.plcLists()); assertEquals(19, lists.size()); assertEquals(19, rows.size());
        for (int i = 0; i < 19; i++) {
            var c = rows.get(i).split(" "); var p = lists.get(i); var entries = c[2].split(",");
            assertEquals(i, p.nativeId()); assertEquals(Integer.parseInt(c[1], 16), p.bodyRange().fileOffset());
            assertEquals(entries.length, p.entries().size()); assertEquals(2 + entries.length * 6, p.bodyRange().length());
            for (int n = 0; n < entries.length; n++) {
                var expected = entries[n].split(":"); var e = p.entries().get(n);
                assertEquals(CdR12aProfile.Asset.valueOf(expected[0]), e.source()); assertEquals(Integer.parseInt(expected[1], 16), e.destinationWord());
                assertEquals(objects.profile().range(e.source()), e.sourceRange());
                assertEquals(e.sourceRange().nativeAddress(), e.descriptor().source()); assertEquals(e.destinationWord(), e.descriptor().destination());
                assertTrue(e.destinationWord() + objects.readerProfile(e.source()).outputBytes() <= 65536);
            }
        }
        assertEquals(lists.get(0).bodyRange(), lists.get(3).bodyRange());
        for (int id : new int[]{13, 14, 15, 17}) assertEquals(lists.get(2).bodyRange(), lists.get(id).bodyRange());
        assertEquals(ART_RESULTS, lists.get(16).entries().getFirst().source());
        assertEquals(ART_SIGNPOST, lists.get(18).entries().getFirst().source());
        assertThrows(UnsupportedOperationException.class, () -> lists.clear());
        assertThrows(UnsupportedOperationException.class, () -> lists.getFirst().entries().clear());
    }

    @Test void componentPlcFormatStopsAtChangedSignedDirectoryCountsTaggedSourcesAndDestinations() throws Exception {
        byte[] raw = retail().terrain().objects().view(PLC_LISTS).copy(); int firstBody = ((raw[0] & 255) << 8) | (raw[1] & 255);
        for (int kind = 0; kind < 5; kind++) {
            byte[] changed = raw.clone();
            switch (kind) {
                case 0 -> { changed[0] = (byte) 0xFF; changed[1] = (byte) 0xFE; }
                case 1 -> changed[1] |= 1;
                case 2 -> { changed[firstBody] = (byte) 0xFF; changed[firstBody + 1] = (byte) 0xFF; }
                case 3 -> changed[firstBody + 2] = (byte) 0x80;
                case 4 -> changed[firstBody + 7] |= 1;
            }
            assertThrows(IllegalArgumentException.class, () -> CdR12aObjectAssets.decodePlcLists(changed), "component malformed kind " + kind);
        }
        assertThrows(IllegalArgumentException.class, () -> CdR12aObjectAssets.decodePlcLists(Arrays.copyOf(raw, 563)));
        assertThrows(NullPointerException.class, () -> CdR12aObjectAssets.decodePlcLists(null));
        assertArrayEquals(raw, retail().terrain().objects().view(PLC_LISTS).copy());
    }

    @Test void sharedVisualDecodersRetainRingMonitorTimeAndThreeSpringRootsWithTheRealParentEvenByte() throws Exception {
        var o = retail().terrain().objects(); var rings = o.rings(); var monitor = o.monitorTime();
        assertSame(rings, o.rings()); assertEquals(9, rings.mappingFrames().size());
        assertEquals("00120018001e0024002a00300036003c0042", java.util.HexFormat.of().formatHex(o.view(MAP_SPR_RING).slice(0,18).copy()));
        assertTrue(rings.mappingFrames().get(8).pieces().isEmpty(), "Native ninth root is the invisible frame"); assertEquals(1, rings.animations().size());
        assertEquals(20, monitor.mappingFrames().size()); assertEquals(10, monitor.animations().size()); assertSame(monitor, o.monitorTime());
        assertEquals(450, monitor.mappings().length()); assertEquals(136, monitor.animationTable().length());
        var springs = List.of(o.springVertical(), o.springHorizontal(), o.springDiagonal());
        assertEquals(List.of(0, 6, 80), springs.stream().map(CdObjectVisualAssets::mappingTableOffset).toList());
        assertEquals(199, o.view(MAP_SPRING).length()); assertEquals(200, o.springMappingsReadRange().length());
        assertEquals(0, o.springMappings().unsignedByte(199)); assertEquals("09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c", CdR11aProfile.sha256(o.springMappings().copy()));
        for (var spring : springs) { assertEquals(3, spring.mappingFrames().size()); assertEquals(1, spring.animations().size());
            assertArrayEquals(o.springMappings().copy(), spring.mappings().copy()); assertArrayEquals(o.view(ANI_SPRING).copy(), spring.animationTable().copy()); }
        assertSame(springs.getFirst(), o.springVertical()); assertSame(springs.getLast(), o.springDiagonal());
    }

    @Test void existingHudAndRawReservedViewsUseTheGenuineNumberResetDivisorAndCompleteSharedMappingSources() throws Exception {
        var o = retail().terrain().objects(); var hud = o.hudAssets(); assertSame(hud, o.hudAssets());
        assertSame(o.view(ART_HUD_NUMBERS), hud.numbers()); assertSame(o.view(HUD_RESET_TILES), hud.resetTiles()); assertSame(o.view(HUD_DIVISORS), hud.divisors());
        assertEquals(1024, hud.numbers().length()); assertEquals(12, hud.resetTiles().length()); assertEquals(40, hud.divisors().length());
        assertEquals(168, o.view(MAP_SPR_HUD).length()); assertEquals(450, o.view(MAP_SPR_MONITOR_TIME).length());
        assertEquals(384, o.view(ART_LIFE_ICON).length()); assertEquals(8, o.view(ENEMY_POINTS).length());
    }

    @Test void existingPlacementDeltaTouchAndPaletteFormatsDecodeImmutableFiniteValuesWithoutRunningAnOwner() throws Exception {
        var o = retail().terrain().objects(); var placements = o.placements();
        assertSame(placements, o.placements()); assertEquals(488, placements.records().size()); assertEquals(279, placements.forPeriod(CdPeriod.PRESENT).size());
        assertEquals(9, placements.records().getFirst().centreX()); assertEquals(0x0A, placements.records().getFirst().objectId());
        assertEquals(256, o.view(OBJECT_INDEX).length()); assertEquals(16, o.ringDeltas().size()); assertSame(o.ringDeltas(), o.ringDeltas());
        assertEquals(63, o.touchDimensions().size()); assertSame(o.touchDimensions(), o.touchDimensions());
        assertEquals(5, o.sections().size()); assertEquals(List.of(0xA00, 0x1500, 0x1800, 0x1D00, 0xFFFF), o.sections().stream().map(CdR11aLoadPlan.SectionArt::rightThresholdWord).toList());
        assertEquals(List.of(2, 4, 5, 6, 7), o.sections().stream().map(CdR11aLoadPlan.SectionArt::initialPlcId).toList());
        assertEquals(List.of(8, 9, 10, 11, 12), o.sections().stream().map(CdR11aLoadPlan.SectionArt::updatePlcId).toList());
        assertEquals(6, o.paletteCycles().size()); assertSame(o.paletteCycles(), o.paletteCycles());
        for (int i = 0; i < 6; i++) { var s = o.paletteCycles().get(i); assertEquals(8, s.script().length()); assertEquals(6, s.colours().length());
            assertArrayEquals(o.view(PALETTE_CYCLE_DATA).slice(i * 14, 8).copy(), s.script().copy()); }
        assertThrows(UnsupportedOperationException.class, () -> o.ringDeltas().clear());
        assertThrows(UnsupportedOperationException.class, () -> o.paletteCycles().clear());
    }

    @Test void rawPowerupSourcesNeverExtendTimeStarsOrInventItsFollowing32BytesOrResidentUploadTail() throws Exception {
        var o = retail().terrain().objects();
        assertEquals(1152, o.view(ART_SHIELD).length()); assertEquals(1152, o.view(ART_INV_STARS).length()); assertEquals(992, o.view(ART_TIME_STARS).length());
        assertThrows(IndexOutOfBoundsException.class, () -> o.view(ART_TIME_STARS).slice(0, 1024));
        assertThrows(IllegalArgumentException.class, () -> o.readerProfile(ART_TIME_STARS));
        assertThrows(IllegalArgumentException.class, () -> o.decode(ART_TIME_STARS));
        assertThrows(NullPointerException.class, () -> o.readerProfile(null));
        assertThrows(NullPointerException.class, () -> o.readerView(ART_RINGS, null));
    }

    @Test void everyCachedExportRemainsImmutableAndWholeFileIdentityRejectsChangedNewResources() throws Exception {
        var a = retail().terrain(); var o = a.objects();
        for (var asset : CdR12aProfile.Asset.values()) { byte[] before = o.view(asset).copy(), changed = o.view(asset).copy(); Arrays.fill(changed, (byte) 0xFF);
            assertArrayEquals(before, o.view(asset).copy()); assertThrows(ReadOnlyBufferException.class, () -> o.view(asset).buffer().put(0, (byte) 0)); }
        for (var asset : new CdR12aProfile.Asset[]{PLC_LISTS, OBJECT_LAYOUTS, ART_TAGA_TAGA, ART_TIME_STARS}) {
            byte[] changed = retail().original().readFile(0, 262144); changed[asset.fileOffset()] ^= 1;
            assertThrows(IOException.class, () -> a.profile().verifyProgram(CdNativeFile.R12A, a.profile().domain(), CdMmdImage.parse(changed)));
        }
    }

    @Test void actualTitleDataUsesLiveOperandsRatherThanEqualLegacyDuplicatesAndOneSharedVisual() throws Exception {
        var o=retail().terrain().objects();var profile=o.profile();
        assertEquals(86,TITLE_CARD_DATA.ordinal());assertEquals(87,TITLE_CARD_MAPPINGS.ordinal());
        assertEquals(0x20E1CE,profile.range(TITLE_CARD_DATA).nativeAddress());assertEquals(0x20E20E,profile.range(TITLE_CARD_MAPPINGS).nativeAddress());
        assertEquals(64,o.view(TITLE_CARD_DATA).length());assertEquals(276,o.view(TITLE_CARD_MAPPINGS).length());
        assertEquals(4,o.view(TITLE_CARD_DATA).unsignedByte(5*8+6));
        var visual=o.titleCard();assertSame(visual,o.titleCard());assertEquals(9,visual.mappingFrames().size());
        assertTrue(visual.animations().isEmpty());assertArrayEquals(o.view(TITLE_CARD_MAPPINGS).copy(),visual.mappings().copy());
        assertEquals("5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2",CdR11aProfile.sha256(o.view(TITLE_CARD_DATA).copy()));
        assertEquals("a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11",CdR11aProfile.sha256(o.view(TITLE_CARD_MAPPINGS).copy()));
        // Actual InitDB1C stores liveE20E; equalF742/F782 copies do not select a source.
        assertTrue(java.util.HexFormat.of().formatHex(retail().original().readFile(0xDB1C,96)).contains("217c0020e20e0004"));
    }

    @Test void wallUsesActualLiveOperandsFullTablesAndSharedPlatformArtWithoutAnotherReader() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        assertEquals(88,MAP_SPR_BREAKABLE_WALL.ordinal());assertEquals(91,BREAKABLE_WALL_PIECE_SPEEDS.ordinal());
        assertEquals("217c0020eed80004",java.util.HexFormat.of().formatHex(file.readFile(0xEDC0,8)));
        assertEquals("4bfa01f8",java.util.HexFormat.of().formatHex(file.readFile(0xEE16,4)));
        assertEquals("49fa022a",java.util.HexFormat.of().formatHex(file.readFile(0xEE24,4)));
        assertEquals("47fa023e",java.util.HexFormat.of().formatHex(file.readFile(0xEE28,4)));
        assertArrayEquals(file.readFile(0xF9F0,312),o.view(MAP_SPR_BREAKABLE_WALL).copy(),"equal padding bytes do not change the selected live root");
        assertEquals(0x20EED8,o.profile().range(MAP_SPR_BREAKABLE_WALL).nativeAddress());
        var visual=o.breakableWall();assertSame(visual,o.breakableWall());assertEquals(14,visual.mappingFrames().size());
        assertEquals(List.of(28,59,90,121,152,183,214,245,276,282,288,294,300,306),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(6,6,6,6,6,6,6,6,1,1,1,1,1,1),visual.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertTrue(visual.animations().isEmpty());assertArrayEquals(o.view(MAP_SPR_BREAKABLE_WALL).copy(),visual.mappings().copy());
        for(var frame:visual.mappingFrames())for(var piece:frame.pieces()){
            assertEquals(4,piece.tileCount());assertTrue(piece.tileOffset()>=0x29);assertTrue(piece.tileOffset()+piece.tileCount()<=57);
        }
        var hex=java.util.HexFormat.of();
        assertArrayEquals(hex.parseHex("0809080c0d0c00000809080a0b0a00000a0b0a0a0b0a00000a0b0a0c0d0c00000908090d0c0d00000908090b0a0b00000b0a0b0b0a0b00000b0a0b0d0c0d0000"),o.view(BREAKABLE_WALL_PIECE_FRAMES).copy());
        assertArrayEquals(hex.parseHex("fff8fff00000001000000020001000000010001000100020"),o.view(BREAKABLE_WALL_PIECE_DELTAS).copy());
        assertArrayEquals(hex.parseHex("fffd097cfffeb750fffc25ee00000000fffd097c000148b0fffd097cfffe4445fffc97b500000000fffd097c0001bbbb"),o.view(BREAKABLE_WALL_PIECE_SPEEDS).copy());
        assertEquals(35,o.readerProfiles().size());assertEquals(57*32,o.readerProfile(ART_PLATFORM).outputBytes());
        for(var raw:new CdR12aProfile.Asset[]{MAP_SPR_BREAKABLE_WALL,BREAKABLE_WALL_PIECE_FRAMES,BREAKABLE_WALL_PIECE_DELTAS,BREAKABLE_WALL_PIECE_SPEEDS}){
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(raw));
            byte[] changed=file.readFile(0,262144);changed[raw.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(changed)));
        }
    }

    @Test void wallFactoryBorrowsOnlyTheSameAuthenticatedRetainedPlacementAndRejectsConstructedIngress() throws Exception {
        var a=retail().terrain();var o=a.objects();
        var placement=com.openggf.game.soniccd.objects.CdObjectPlacementRuntime.fromAssets(a,CdAssetBytes.copyOf(new byte[766]));
        var shared=new com.openggf.game.soniccd.objects.CdSolidObject(com.openggf.game.soniccd.player.CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromAct2(a,retail().player())));
        assertNotNull(com.openggf.game.soniccd.objects.CdBreakableWall.fromAssets(placement,o,shared,null,null,null,null));
        var constructed=new com.openggf.game.soniccd.objects.CdObjectPlacementRuntime(o.placements());
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdBreakableWall.fromAssets(constructed,o,shared,null,null,null,null));
        var data=com.openggf.game.soniccd.objects.CdBreakableWall.Assets.fromObjectAssets(o);
        assertEquals(0x20EED8,data.mappingsAddress());assertSame(o.view(BREAKABLE_WALL_PIECE_FRAMES),data.pieceFrames());
        assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdBreakableWall(placement,data,shared,null,null,null,null));
    }

    @Test void boulderAndPlatformMetadataAreSelectedByActualActTwoDispatchAndCompleteInitOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(92,MAP_SPR_BOULDER.ordinal());assertEquals(95,PLATFORM_RANGES.ordinal());
        assertEquals(0x209412,o.view(OBJECT_INDEX).unsignedLong((0x1B-1)*4));
        assertEquals(0x20C574,o.view(OBJECT_INDEX).unsignedLong((0x21-1)*4));
        assertEquals("217c0020947c0004",hex.formatHex(file.readFile(0x9440,8)));
        assertEquals("700b4eb90020cc50",hex.formatHex(file.readFile(0x945A,8)));
        for(int operand:new int[]{0xC5C4,0xC5D4,0xC5E4})
            assertEquals("203c0020c9f2",hex.formatHex(file.readFile(operand,6)));
        assertEquals("117b006c0019",hex.formatHex(file.readFile(0xC5FC,6)));
        assertEquals("117b105e002d",hex.formatHex(file.readFile(0xC60E,6)));
        // PC base is its extension address; the low displacement is signed BYTE, not an old label suffix.
        assertEquals(PLATFORM_WIDTHS.fileOffset(),0xC5FE+(byte)file.readFile(0xC5FF,1)[0]);
        assertEquals(PLATFORM_RANGES.fileOffset(),0xC610+(byte)file.readFile(0xC611,1)[0]);
        assertEquals(0x20947C,o.profile().range(MAP_SPR_BOULDER).nativeAddress());
        assertEquals(0x20C9F2,o.profile().range(MAP_SPR_PLATFORM).nativeAddress());
        assertFalse(Arrays.equals(file.readFile(0xCA76,132),o.view(MAP_SPR_PLATFORM).copy()),"unused second include is not the selected primary table");
    }

    @Test void bothCompleteStaticVisualsRetainEveryCountedPieceRootAndNativePaddingWithinExistingArtBounds() throws Exception {
        var o=retail().terrain().objects();var hex=java.util.HexFormat.of();
        var boulder=o.boulder();assertSame(boulder,o.boulder());assertTrue(boulder.animations().isEmpty());
        assertEquals(List.of(2),boulder.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(2,boulder.mappingFrames().getFirst().pieces().size());
        assertArrayEquals(hex.parseHex("000202f8020000ecf00f0003f400"),boulder.mappings().copy());
        assertEquals(0,boulder.mappings().unsignedByte(13),"parent EVEN byte belongs to the complete original table span");
        assertArrayEquals(o.view(MAP_SPR_BOULDER).copy(),boulder.mappings().copy());
        var platform=o.platform();assertSame(platform,o.platform());assertTrue(platform.animations().isEmpty());
        assertEquals(List.of(6,28,70),platform.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(4,8,12),platform.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertArrayEquals(o.view(MAP_SPR_PLATFORM).copy(),platform.mappings().copy());
        assertEquals("a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7",CdR11aProfile.sha256(platform.mappings().copy()));
        for(int padding:new int[]{27,69,131})assertEquals(0,platform.mappings().unsignedByte(padding));
        for(var frame:boulder.mappingFrames())for(var piece:frame.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=19);
        for(var frame:platform.mappingFrames())for(var piece:frame.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=57);
        assertEquals(3,platform.mappingFrames().size(),"the fourth raw width entry does not invent a fourth map root");
        assertArrayEquals(hex.parseHex("10203000"),o.view(PLATFORM_WIDTHS).copy());
        assertArrayEquals(hex.parseHex("02030406"),o.view(PLATFORM_RANGES).copy());
    }

    @Test void actTwoFactoriesBorrowOnlyTheExistingAuthenticatedRawViewsAndCachedVisuals() throws Exception {
        var o=retail().terrain().objects();var before=o.readerProfiles();
        var boulder=com.openggf.game.soniccd.objects.CdBoulder.Assets.fromAct2Assets(o);
        assertSame(o.boulder(),boulder.visual());assertSame(o.view(OBJECT_TILE_IDS),boulder.objectTileIds());
        assertEquals(0x20947C,boulder.mappingsAddress());assertEquals(74,boulder.objectTileIds().length());
        assertEquals(0x36C,CdR11aObjectAssets.decodeTileWord(boulder.objectTileIds().copy(),0xB,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(boulder.objectTileIds().copy(),0xB,1));
        var platform=com.openggf.game.soniccd.objects.CdPlatform.Assets.fromAct2Assets(o);
        assertSame(o.platform(),platform.visual());assertSame(o.view(PLATFORM_WIDTHS),platform.widths());
        assertSame(o.view(PLATFORM_RANGES),platform.ranges());assertEquals(0x20C9F2,platform.mappingsAddress());
        assertSame(before,o.readerProfiles());assertEquals(35,before.size());
        assertEquals(19*32,o.readerProfile(ART_BOULDER).outputBytes());assertEquals(57*32,o.readerProfile(ART_PLATFORM).outputBytes());
        assertEquals(3,com.openggf.game.soniccd.objects.CdBoulder.Assets.class.getRecordComponents().length);
        assertEquals(4,com.openggf.game.soniccd.objects.CdPlatform.Assets.class.getRecordComponents().length);
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdBoulder.Assets.fromAct2Assets(null));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdPlatform.Assets.fromAct2Assets(null));
    }

    @Test void eachNewRawSpanStaysDetachedOriginalSourceAndCannotBeReclassifiedAsAnArtReader() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var raw:new CdR12aProfile.Asset[]{MAP_SPR_BOULDER,MAP_SPR_PLATFORM,PLATFORM_WIDTHS,PLATFORM_RANGES}) {
            var bytes=o.view(raw);var original=bytes.copy();var changed=bytes.copy();changed[0]^=1;
            assertArrayEquals(original,o.view(raw).copy());assertSame(bytes,o.view(raw));
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(raw));
            byte[] corrupt=file.readFile(0,262144);corrupt[raw.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        assertThrows(UnsupportedOperationException.class,()->CdR12aProfile.forStage(CdReleaseVariant.USA,com.openggf.game.soniccd.CdStagePayload.R11A.stage()));
        assertThrows(UnsupportedOperationException.class,()->CdR12aProfile.forStage(CdReleaseVariant.EUROPE,a.identity().stage()));
    }

    @Test void antonAndPataDataAreSelectedByTheirActualActTwoInitAndAnimationOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(96,ANI_ANTON.ordinal());assertEquals(100,MAP_SPR_PATA_BATA2.ordinal());
        assertEquals(0x20B0DC,o.view(OBJECT_INDEX).unsignedLong((0x3F-1)*4));
        assertEquals(0x20B242,o.view(OBJECT_INDEX).unsignedLong((0x3E-1)*4));
        assertEquals("217c0020b1d20004",hex.formatHex(file.readFile(0xB112,8)));
        assertEquals("70024eb90020cc50",hex.formatHex(file.readFile(0xB138,8)));
        assertEquals("70014eb90020cc50",hex.formatHex(file.readFile(0xB28A,8)));
        assertEquals("43fa001a",hex.formatHex(file.readFile(0xB1AA,4)));
        assertEquals("43fa00ae",hex.formatHex(file.readFile(0xB2A6,4)));
        assertEquals("43fa00bc",hex.formatHex(file.readFile(0xB2BA,4)));
        assertEquals("43fa0018",hex.formatHex(file.readFile(0xB32A,4)));
        assertEquals(ANI_ANTON.fileOffset(),0xB1AC+(short)CdAssetBytes.copyOf(file.readFile(0xB1AC,2)).unsignedWord(0));
        assertEquals(MAP_SPR_PATA_BATA1.fileOffset(),0xB2A8+(short)CdAssetBytes.copyOf(file.readFile(0xB2A8,2)).unsignedWord(0));
        assertEquals(MAP_SPR_PATA_BATA2.fileOffset(),0xB2BC+(short)CdAssetBytes.copyOf(file.readFile(0xB2BC,2)).unsignedWord(0));
        assertEquals(ANI_PATA_BATA.fileOffset(),0xB32C+(short)CdAssetBytes.copyOf(file.readFile(0xB32C,2)).unsignedWord(0));
        assertEquals("4eb90020085a",hex.formatHex(file.readFile(0xB314,6)),"CalcSine remains the existing source math call");
    }

    @Test void completeAntonAndBothPataMappingIncludesKeepOddRootsAllPiecesAndTheSharedRawScripts() throws Exception {
        var o=retail().terrain().objects();var anton=o.anton();var normal=o.pataBataNormal();var damaged=o.pataBataDamaged();
        assertEquals(List.of(8,34,60,86),anton.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(5,5,5,5),anton.mappingFrames().stream().map(f->f.pieces().size()).toList());
        for(var visual:List.of(normal,damaged))assertEquals(List.of(6,17,28),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2,2,1),normal.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(2,2,2),damaged.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(3,7),anton.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(0,1),List.of(2,3)),anton.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        for(var visual:List.of(normal,damaged)) {
            assertEquals(List.of(4,11),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
            assertEquals(List.of(7,3),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
            assertEquals(List.of(List.of(0,0,1,2,1),List.of(0,0,1,2,1)),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
            assertArrayEquals(o.view(ANI_PATA_BATA).copy(),visual.animationTable().copy());
        }
        assertArrayEquals(o.view(ANI_ANTON).copy(),anton.animationTable().copy());
        assertArrayEquals(o.view(MAP_SPR_ANTON).copy(),anton.mappings().copy());
        assertArrayEquals(o.view(MAP_SPR_PATA_BATA1).copy(),normal.mappings().copy());
        assertArrayEquals(o.view(MAP_SPR_PATA_BATA2).copy(),damaged.mappings().copy());
        assertEquals(112,anton.mappings().length());assertEquals(34,normal.mappings().length());assertEquals(40,damaged.mappings().length());
        assertEquals(List.of(26,26,26,26),anton.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(List.of(14,18,14,18),anton.mappingFrames().stream().map(f->f.bytes().unsignedByte(25)).toList(),"last Anton bytes are counted piece X values, not padding");
        assertEquals(0,damaged.mappings().unsignedByte(39),"the damaged include retains its actual literal trailing zero");
        assertEquals(22,anton.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(v->v.tileOffset()+v.tileCount()).max().orElseThrow());
        assertEquals(41,normal.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(v->v.tileOffset()+v.tileCount()).max().orElseThrow());
        assertEquals(50,damaged.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(v->v.tileOffset()+v.tileCount()).max().orElseThrow());
        for(var visual:List.of(anton,normal,damaged))for(var script:visual.animations())assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,script.terminal());
    }

    @Test void actTwoAntonAndPataFactoriesBorrowCachedViewsOnePataScriptRootAndTheActualActTwoMathBytes() throws Exception {
        var o=retail().terrain().objects();var player=retail().player();var readers=o.readerProfiles();
        var anton=com.openggf.game.soniccd.objects.CdAnton.Assets.fromAct2Assets(o);
        var pata=com.openggf.game.soniccd.objects.CdPataBata.Assets.fromAct2Assets(o,player);
        assertSame(o.anton(),anton.visual());assertSame(o.anton(),o.anton());assertEquals(0x20B1D2,anton.mappingsAddress());
        assertSame(o.view(OBJECT_TILE_IDS),anton.objectTileIds());assertSame(anton.objectTileIds(),pata.objectTileIds());
        assertSame(o.pataBataNormal(),pata.normal());assertSame(o.pataBataDamaged(),pata.damaged());assertNotSame(pata.normal(),pata.damaged());
        assertSame(o.view(ANI_PATA_BATA),pata.animations().bytes());assertEquals(0,pata.animations().tableOffset());assertEquals(2,pata.animations().scriptCount());
        assertEquals(0x20B356,pata.normalMappingsAddress());assertEquals(0x20B378,pata.damagedMappingsAddress());
        assertEquals(o.identity(),player.identity());assertSame(player.sonic(),player.player().sonic());
        for(int angle=0;angle<256;angle++) {
            var actual=pata.math().calcSine(angle);var bytes=o.view(SINE_TABLE);
            assertEquals((short)bytes.unsignedWord(angle*2),actual.sine());
            assertEquals((short)bytes.unsignedWord((angle+64)*2),actual.cosine());
        }
        assertEquals(0x2400,CdR11aObjectAssets.decodeTileWord(anton.objectTileIds().copy(),2,0));
        assertEquals(0x23DB,CdR11aObjectAssets.decodeTileWord(anton.objectTileIds().copy(),2,1));
        assertEquals(0x23A7,CdR11aObjectAssets.decodeTileWord(pata.objectTileIds().copy(),1,0));
        assertSame(readers,o.readerProfiles());assertEquals(35,readers.size());
        assertEquals(22*32,o.readerProfile(ART_ANTON).outputBytes());assertEquals(50*32,o.readerProfile(ART_PATA_BATA).outputBytes());
        assertEquals(3,com.openggf.game.soniccd.objects.CdAnton.Assets.class.getRecordComponents().length);
        assertEquals(7,com.openggf.game.soniccd.objects.CdPataBata.Assets.class.getRecordComponents().length);
    }

    @Test void newEnemyViewsRejectChangedOriginalSourceForeignMathLineageAndUnrepresentedReadsWithoutAnArtReader() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();var player=retail().player();
        for(var raw:new CdR12aProfile.Asset[]{ANI_ANTON,MAP_SPR_ANTON,ANI_PATA_BATA,MAP_SPR_PATA_BATA1,MAP_SPR_PATA_BATA2}) {
            var bytes=o.view(raw);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;
            assertArrayEquals(before,o.view(raw).copy());assertSame(bytes,o.view(raw));
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(raw));
            byte[] corrupt=file.readFile(0,262144);corrupt[raw.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdAnton.Assets.fromAct2Assets(null));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdPataBata.Assets.fromAct2Assets(null,player));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdPataBata.Assets.fromAct2Assets(o,null));
        var foreign=org.mockito.Mockito.mock(CdR12aPlayerAssets.class);var identity=player.identity();
        org.mockito.Mockito.when(foreign.identity()).thenReturn(new CdR12aTerrainAssets.Identity(identity.variant(),identity.stage(),identity.file(),identity.domain(),identity.mmdSha256(),"foreign-disc"));
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdPataBata.Assets.fromAct2Assets(o,foreign));
        // Matching scalar identity cannot substitute a different borrowed Sonic/player relation.
        org.mockito.Mockito.when(foreign.identity()).thenReturn(identity);org.mockito.Mockito.when(foreign.player()).thenReturn(player.player());
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdPataBata.Assets.fromAct2Assets(o,foreign));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(o.view(OBJECT_TILE_IDS).copy(),1,1));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(o.view(OBJECT_TILE_IDS).copy(),2,2));
        var table=new com.openggf.game.soniccd.objects.CdObjectAnimation.AnimationTable(o.view(ANI_PATA_BATA),0,2);
        assertThrows(UnsupportedOperationException.class,()->com.openggf.game.soniccd.objects.CdObjectAnimation.step(
                new com.openggf.game.soniccd.objects.CdObjectAnimation.State(2,2,0,0,0,4,0,2,0),table));
    }

    @Test void tamabbohAndFloatBlockDataUseTheActualDispatchInitAndAnimationOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(101,ANI_TAMABBOH_NORMAL.ordinal());assertEquals(107,MAP_SPR_FLOAT_BLOCK.ordinal());
        assertEquals(0x20B58C,o.view(OBJECT_INDEX).unsignedLong((0x22-1)*4));
        assertEquals(0x20A0AC,o.view(OBJECT_INDEX).unsignedLong((0x12-1)*4));
        assertEquals("70044eb90020cc50",hex.formatHex(file.readFile(0xB5F4,8)));
        assertEquals("70054eb90020cc50",hex.formatHex(file.readFile(0xA0FA,8)));
        int[][] leAs={{0xB602,0xB790,0x43},{0xB606,0xB76A,0x45},{0xB612,0xB7A6,0x43},{0xB616,0xB776,0x45},{0xB886,0xB890,0x43}};
        for(var row:leAs) {
            var raw=CdAssetBytes.copyOf(file.readFile(row[0],4));assertEquals(row[2],raw.unsignedByte(0));assertEquals(0xFA,raw.unsignedByte(1));
            assertEquals(row[1],row[0]+2+(short)raw.unsignedWord(2),"actual PC extension base + signed displacement");
        }
        assertEquals("217c0020b8980004",hex.formatHex(file.readFile(0xB808,8)));
        assertEquals("217c0020a2220004",hex.formatHex(file.readFile(0xA0F2,8)));
        for(int instruction:new int[]{0xA194,0xA1C2,0xA1D6})assertEquals("43f90020a204",hex.formatHex(file.readFile(instruction,6)));
        assertEquals(0x20B790,o.profile().range(MAP_SPR_TAMABBOH).nativeAddress());
        assertEquals(0x20A222,o.profile().range(MAP_SPR_FLOAT_BLOCK).nativeAddress());
    }

    @Test void tamabbohBodyViewsKeepTheWholeNestedIncludeSharedBodiesAndEveryNormalDamagedMissileScript() throws Exception {
        var o=retail().terrain().objects();var normal=o.tamabbohNormal();var damaged=o.tamabbohDamaged();var missile=o.tamabbohMissile();
        assertEquals(0,normal.mappingTableOffset());assertEquals(22,damaged.mappingTableOffset());
        assertEquals(62,normal.mappings().length());assertEquals(62,damaged.mappings().length());
        assertArrayEquals(o.view(MAP_SPR_TAMABBOH).copy(),normal.mappings().copy());assertArrayEquals(normal.mappings().copy(),damaged.mappings().copy());
        assertEquals(List.of(6,34,50),normal.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(6,12,28),damaged.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(3,3,2),normal.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(1,3,2),damaged.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(16,normal.mappingFrames().getFirst().bytes().length());assertEquals(12,34-(6+16),"nested directory/body is a real unconsumed tail");
        assertArrayEquals(normal.mappingFrames().get(1).bytes().copy(),damaged.mappingFrames().get(1).bytes().copy());
        assertArrayEquals(normal.mappingFrames().get(2).bytes().copy(),damaged.mappingFrames().get(2).bytes().copy());
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_TAMABBOH).copy(),3,o.view(ANI_TAMABBOH_NORMAL).copy(),2,59));
        assertEquals(List.of(29,59),normal.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(0,1),List.of(2)),normal.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC),normal.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        assertEquals(List.of(5,11),damaged.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(0,1,0,1,0,1,0,1,0,1,0,1,0,1,0,1),damaged.animations().getFirst().frameBytes());
        assertEquals(List.of(2),damaged.animations().get(1).frameBytes());
        assertTrue(damaged.animations().stream().allMatch(a->a.terminal()==CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC));
        assertArrayEquals(o.view(ANI_TAMABBOH_NORMAL).copy(),normal.animationTable().copy());assertArrayEquals(o.view(ANI_TAMABBOH_DAMAGED).copy(),damaged.animationTable().copy());
        assertEquals(42,missile.mappings().length());assertEquals(List.of(8,14,20,31),missile.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(1,1,2,2),missile.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertArrayEquals(o.view(MAP_SPR_TAMABBOH_MISSILE).copy(),missile.mappings().copy());assertArrayEquals(o.view(ANI_TAMABBOH_MISSILE).copy(),missile.animationTable().copy());
        assertEquals(List.of(0,1,2,3),missile.animations().getFirst().frameBytes());assertEquals(2,missile.animations().getFirst().delay());
        assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,missile.animations().getFirst().terminal());
        assertEquals(59,damaged.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        for(var visual:List.of(normal,damaged,missile))for(var frame:visual.mappingFrames())for(var piece:frame.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=59);
    }

    @Test void floatBlockPreservesItsEmptyNativeFramePaddingAndCompleteFfFcScripts() throws Exception {
        var o=retail().terrain().objects();var visual=o.floatBlock();assertEquals(6,visual.mappingFrames().size());assertEquals(3,visual.animations().size());
        assertEquals(List.of(12,14,30,46,58,70),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(0,3,3,2,2,1),visual.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertTrue(visual.mappingFrames().getFirst().pieces().isEmpty());assertEquals(1,visual.mappingFrames().getFirst().bytes().length());
        assertArrayEquals(o.view(MAP_SPR_FLOAT_BLOCK).copy(),visual.mappings().copy());assertEquals(76,visual.mappings().length());
        for(int padding:new int[]{13,57,69})assertEquals(0,visual.mappings().unsignedByte(padding));
        assertEquals(List.of(6,10,20),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        assertEquals(List.of(2,2,2),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(5),List.of(1,5,2,5,3,5,4,5),List.of(1,0,2,0,3,0,4,0)),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        assertArrayEquals(o.view(ANI_FLOAT_BLOCK).copy(),visual.animationTable().copy());assertEquals(30,visual.animationTable().length());assertEquals(0,visual.animationTable().unsignedByte(9));
        assertEquals(15,visual.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
    }

    @Test void tamabbohAndFloatFactoriesBorrowOnlyTheirCachedOriginalViewsAndPreserveSelectorNegatives() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var tam=com.openggf.game.soniccd.objects.CdTamabboh.Assets.fromAct2Assets(o);
        var block=com.openggf.game.soniccd.objects.CdFloatBlock.Assets.fromAct2Assets(o);
        assertSame(o.view(OBJECT_TILE_IDS),tam.objectTileIds());assertSame(tam.objectTileIds(),block.objectTileIds());assertEquals(74,tam.objectTileIds().length());
        assertSame(o.tamabbohNormal(),tam.normal());assertSame(o.tamabbohDamaged(),tam.damaged());assertSame(o.tamabbohMissile(),tam.missile());assertSame(o.floatBlock(),block.visual());
        assertEquals(0x20B790,tam.normalMappingsAddress());assertEquals(tam.normalMappingsAddress()+22,tam.damagedMappingsAddress());
        assertEquals(0x20B898,tam.missileMappingsAddress());assertEquals(0x20B76A,tam.normalAnimationAddress());assertEquals(0x20B776,tam.damagedAnimationAddress());
        assertEquals(0x20A222,block.mappingsAddress());assertEquals(0x242C,CdR11aObjectAssets.decodeTileWord(tam.objectTileIds().copy(),4,0));
        assertEquals(0x435A,CdR11aObjectAssets.decodeTileWord(block.objectTileIds().copy(),5,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(tam.objectTileIds().copy(),4,1));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(block.objectTileIds().copy(),5,1));
        assertSame(readers,o.readerProfiles());assertEquals(35,readers.size());assertEquals(59*32,o.readerProfile(ART_TAMABBOH).outputBytes());assertEquals(15*32,o.readerProfile(ART_FLOAT_BLOCK).outputBytes());
        assertEquals(9,com.openggf.game.soniccd.objects.CdTamabboh.Assets.class.getRecordComponents().length);assertEquals(3,com.openggf.game.soniccd.objects.CdFloatBlock.Assets.class.getRecordComponents().length);
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdTamabboh.Assets.fromAct2Assets(null));assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdFloatBlock.Assets.fromAct2Assets(null));
    }

    @Test void everyNewTamabbohFloatRawViewRejectsChangedOriginalSourceAndCannotBecomeANewArtReader() throws Exception {
        var a=retail().terrain();var o=a.objects();var original=retail().original();
        for(var raw:new CdR12aProfile.Asset[]{ANI_TAMABBOH_NORMAL,ANI_TAMABBOH_DAMAGED,MAP_SPR_TAMABBOH,ANI_TAMABBOH_MISSILE,MAP_SPR_TAMABBOH_MISSILE,ANI_FLOAT_BLOCK,MAP_SPR_FLOAT_BLOCK}) {
            var bytes=o.view(raw);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;assertSame(bytes,o.view(raw));assertArrayEquals(before,o.view(raw).copy());
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(raw));byte[] corrupt=original.readFile(0,262144);corrupt[raw.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var normal=o.tamabbohNormal();var damaged=o.tamabbohDamaged();var before=damaged.mappings().copy();var changed=normal.mappings().copy();changed[22]^=1;
        assertArrayEquals(before,damaged.mappings().copy());assertArrayEquals(before,o.view(MAP_SPR_TAMABBOH).copy());
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeIndexed(o.view(MAP_SPR_TAMABBOH).copy(),22,3,28,60,o.view(ANI_TAMABBOH_DAMAGED).copy(),2,59));
        var scripts=o.view(ANI_FLOAT_BLOCK).copy();scripts[9]=1;
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_FLOAT_BLOCK).copy(),6,scripts,3,15));
    }

    @Test void checkpointUsesItsActualInitBallAndAnimationOperandsInsteadOfEqualLegacyCopies() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(108,ANI_CHECKPOINT.ordinal());assertEquals(109,MAP_SPR_CHECKPOINT.ordinal());
        assertEquals(0x205CE2,o.view(OBJECT_INDEX).unsignedLong((0x13-1)*4));
        assertEquals("217c0021d8ac0004",hex.formatHex(file.readFile(0x5D08,8)));
        assertEquals("237c0021d8ac0004",hex.formatHex(file.readFile(0x5D64,8)));
        assertEquals("43f90021d8a6",hex.formatHex(file.readFile(0x5E48,6)));
        assertEquals("317c06cb0002",hex.formatHex(file.readFile(0x5D10,6)));
        assertEquals("337c06cb0002",hex.formatHex(file.readFile(0x5D6C,6)));
        assertEquals(0x21D8A6,o.profile().range(ANI_CHECKPOINT).nativeAddress());assertEquals(0x21D8AC,o.profile().range(MAP_SPR_CHECKPOINT).nativeAddress());
        assertArrayEquals(file.readFile(0x1DCA6,6),o.view(ANI_CHECKPOINT).copy(),"equal data does not select the live animation root");
        assertArrayEquals(file.readFile(0x1DCAC,66),o.view(MAP_SPR_CHECKPOINT).copy(),"Init and Ball select the shared live mapping root");
    }

    @Test void checkpointCachesEveryCountedPieceScriptAndLiteralPaddingUsingItsExistingTwelveTileSource() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var visual=o.checkpoint();assertSame(visual,o.checkpoint());
        assertEquals(0,visual.mappingTableOffset());assertEquals(66,visual.mappings().length());assertEquals(6,visual.animationTable().length());
        assertArrayEquals(o.view(MAP_SPR_CHECKPOINT).copy(),visual.mappings().copy());assertArrayEquals(o.view(ANI_CHECKPOINT).copy(),visual.animationTable().copy());
        assertEquals(List.of(8,30,42,54),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(4,2,2,2),visual.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(21,11,11,11),visual.mappingFrames().stream().map(f->f.bytes().length()).toList());
        for(int padding:new int[]{29,41,53,65})assertEquals(0,visual.mappings().unsignedByte(padding));
        assertEquals(1,visual.animations().size());var script=visual.animations().getFirst();assertEquals(2,script.relativeOffset());assertEquals(3,script.delay());
        assertEquals(List.of(2,3),script.frameBytes());assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,script.terminal());
        assertArrayEquals(java.util.HexFormat.of().parseHex("0002030203ff"),visual.animationTable().copy());
        assertEquals(12,visual.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertSame(readers,o.readerProfiles());assertEquals(35,readers.size());assertEquals(12*32,o.readerProfile(ART_CHECKPOINT).outputBytes());
        var before=visual.mappings().copy();var changed=visual.mappings().copy();changed[0]^=1;assertArrayEquals(before,o.checkpoint().mappings().copy());
        assertThrows(UnsupportedOperationException.class,()->visual.mappingFrames().clear());assertThrows(UnsupportedOperationException.class,()->script.frameBytes().clear());
    }

    @Test void checkpointViewsRejectChangedOriginalBytesNewArtReadersAndMalformedCompleteTables() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var raw:new CdR12aProfile.Asset[]{ANI_CHECKPOINT,MAP_SPR_CHECKPOINT}) {
            var before=o.view(raw);assertSame(before,o.view(raw));var copy=before.copy();copy[0]^=1;assertArrayEquals(file.readFile(raw.fileOffset(),raw.length()),o.view(raw).copy());
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(raw));assertThrows(IllegalArgumentException.class,()->o.readerView(raw,CdR12aObjectAssets.ReaderKind.JAVA));
            byte[] corrupt=file.readFile(0,262144);corrupt[raw.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var maps=o.view(MAP_SPR_CHECKPOINT).copy();maps[65]=1;
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(maps,4,o.view(ANI_CHECKPOINT).copy(),1,12));
        var script=o.view(ANI_CHECKPOINT).copy();script[4]=4;
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_CHECKPOINT).copy(),4,script,1,12));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_CHECKPOINT).copy(),4,o.view(ANI_CHECKPOINT).copy(),1,11));
        assertThrows(NullPointerException.class,()->o.view(null));
    }

    @Test void collapseUsesTheActualActTwoDataBlockAndAllParentChildAndSizeOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(110,MAP_SPR_COLLAPSE_PLATFORM_1.ordinal());assertEquals(115,MAP_SPR_COLLAPSE_PLATFORM_4.ordinal());
        assertEquals(0x20C030,o.view(OBJECT_INDEX).unsignedLong((0x20-1)*4));
        int[][] pc={{0xC072,0xC348,0x43},{0xC076,0xC3BE,0x45},{0xC080,0xC414,0x43},{0xC084,0xC532,0x45},{0xC164,0xC3BE,0x4D},{0xC25C,0xC532,0x4D}};
        for(var row:pc) {var raw=CdAssetBytes.copyOf(file.readFile(row[0],4));assertEquals(row[2],raw.unsignedByte(0));assertEquals(0xFA,raw.unsignedByte(1));assertEquals(row[1],row[0]+2+(short)raw.unsignedWord(2));}
        assertEquals("237c0020c3d60004",hex.formatHex(file.readFile(0xC1D6,8)));
        assertEquals("237c0020c54a0004",hex.formatHex(file.readFile(0xC2DE,8)));
        assertEquals("317c44be0002",hex.formatHex(file.readFile(0xC06C,6)));
        for(int at:new int[]{0xC1D0,0xC2D2})assertEquals("337c44be0002",hex.formatHex(file.readFile(at,6)));
        var ordered=List.of(MAP_SPR_COLLAPSE_PLATFORM_1,COLLAPSE_PLATFORM_SIZES_1,MAP_SPR_COLLAPSE_PLATFORM_3,MAP_SPR_COLLAPSE_PLATFORM_2,COLLAPSE_PLATFORM_SIZES_2,MAP_SPR_COLLAPSE_PLATFORM_4);
        int at=0xC348;for(var key:ordered){assertEquals(at,key.fileOffset());at+=key.length();}assertEquals(0xC574,at);
        assertEquals("2c75d993368d72994b799b1066ab51d710c3b5655ef17cdbb9ea61a6694d4425",CdR11aProfile.sha256(file.readFile(0xC348,556)));
    }

    @Test void actualActTwoCollapseHasOneTwoSevenTwoStaticRootsAndKeepsOddBodiesAndLiteralPadding() throws Exception {
        var o=retail().terrain().objects();var visuals=List.of(o.collapsePlatform1(),o.collapsePlatform2(),o.collapsePlatform3(),o.collapsePlatform4());
        var raw=List.of(MAP_SPR_COLLAPSE_PLATFORM_1,MAP_SPR_COLLAPSE_PLATFORM_2,MAP_SPR_COLLAPSE_PLATFORM_3,MAP_SPR_COLLAPSE_PLATFORM_4);
        var roots=List.of(List.of(2),List.of(4,165),List.of(14,20,26,32,38,44,50),List.of(4,25));
        var counts=List.of(List.of(23),List.of(32,24),List.of(1,1,1,1,1,1,2),List.of(4,3));
        int[] lengths={118,286,62,42},maximum={41,37,41,37};
        for(int i=0;i<visuals.size();i++) {var visual=visuals.get(i);assertEquals(0,visual.mappingTableOffset());assertTrue(visual.animations().isEmpty());
            assertEquals(lengths[i],visual.mappings().length());assertArrayEquals(o.view(raw.get(i)).copy(),visual.mappings().copy());
            assertEquals(roots.get(i),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(counts.get(i),visual.mappingFrames().stream().map(f->f.pieces().size()).toList());
            assertEquals(maximum[i],visual.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
            for(var frame:visual.mappingFrames())for(var piece:frame.pieces())assertTrue(piece.tileOffset()+piece.tileCount()<=57);
        }
        assertEquals(116,visuals.getFirst().mappingFrames().getFirst().bytes().length());
        assertEquals(0,visuals.get(2).mappings().unsignedByte(61));assertEquals(0,visuals.get(3).mappings().unsignedByte(41));
        assertEquals(121,visuals.get(1).mappingFrames().get(1).bytes().length(),"the second wide body starts at the real odd165 root");
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(MAP_SPR_COLLAPSE_PLATFORM_2).copy(),6,57));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(MAP_SPR_COLLAPSE_PLATFORM_4).copy(),6,57));
    }

    @Test void collapseRawSizeScriptsRetainNegativeEntriesFiniteAliasesAndExactReachedExtents() throws Exception {
        var o=retail().terrain().objects();var one=o.view(COLLAPSE_PLATFORM_SIZES_1);var two=o.view(COLLAPSE_PLATFORM_SIZES_2);var hex=java.util.HexFormat.of();
        assertArrayEquals(hex.parseHex("00020403ffff000000010203030400050505050606060606"),one.copy());
        assertArrayEquals(hex.parseHex("0004000e0702000000000000000007010101010101010101"),two.copy());
        assertEquals(2,one.unsignedWord(0));assertEquals(4,one.unsignedByte(2));assertEquals(3,one.unsignedByte(3));assertEquals(0xFF,one.unsignedByte(4));assertEquals(0xFF,one.unsignedByte(5));
        assertEquals(24,2+2+5*4,"native ledge header and its twenty raw piece selectors end at the owned24-byte boundary");
        assertEquals(List.of(4,14),List.of(two.unsignedWord(0),two.unsignedWord(2)));
        for(int frame=0;frame<2;frame++) {int body=two.unsignedWord(frame*2);assertEquals(7,two.unsignedByte(body));assertEquals(frame==0?2:1,two.unsignedByte(body+1));
            for(int column=0;column<8;column++)assertEquals(frame,two.unsignedByte(body+2+column));
            assertEquals(frame==0?14:24,body+2+8);
        }
        assertEquals(24,one.length());assertEquals(24,two.length());
        assertThrows(IndexOutOfBoundsException.class,()->two.unsignedByte(24));
    }

    @Test void collapseActTwoFactoryBorrowsCachedVisualsRawScriptsAndOnlyItsOwnValidatedSourceShape() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var assets=com.openggf.game.soniccd.objects.CdCollapsePlatform.Assets.fromAct2Assets(o);
        assertSame(o.collapsePlatform1(),assets.collapsePlatform1());assertSame(o.collapsePlatform2(),assets.collapsePlatform2());
        assertSame(o.collapsePlatform3(),assets.collapsePlatform3());assertSame(o.collapsePlatform4(),assets.collapsePlatform4());
        assertSame(o.view(COLLAPSE_PLATFORM_SIZES_1),assets.sizes1());assertSame(o.view(COLLAPSE_PLATFORM_SIZES_2),assets.sizes2());
        assertEquals(0x20C348,assets.mappings1());assertEquals(0x20C414,assets.mappings2());assertEquals(0x20C3D6,assets.mappings3());assertEquals(0x20C54A,assets.mappings4());
        assertEquals(10,com.openggf.game.soniccd.objects.CdCollapsePlatform.Assets.class.getRecordComponents().length);
        assertSame(readers,o.readerProfiles());assertEquals(35,readers.size());assertEquals(57*32,o.readerProfile(ART_PLATFORM).outputBytes());
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdCollapsePlatform.Assets.fromAct2Assets(null));
        var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);org.mockito.Mockito.when(foreign.identity()).thenReturn(o.identity());
        org.mockito.Mockito.when(foreign.collapsePlatform1()).thenReturn(o.collapsePlatform1());org.mockito.Mockito.when(foreign.collapsePlatform2()).thenReturn(o.collapsePlatform1());
        org.mockito.Mockito.when(foreign.collapsePlatform3()).thenReturn(o.collapsePlatform3());org.mockito.Mockito.when(foreign.collapsePlatform4()).thenReturn(o.collapsePlatform4());
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdCollapsePlatform.Assets.fromAct2Assets(foreign));
        org.mockito.Mockito.when(foreign.collapsePlatform2()).thenReturn(o.collapsePlatform2());
        org.mockito.Mockito.when(foreign.view(COLLAPSE_PLATFORM_SIZES_1)).thenReturn(o.view(COLLAPSE_PLATFORM_SIZES_1));
        org.mockito.Mockito.when(foreign.view(COLLAPSE_PLATFORM_SIZES_2)).thenReturn(o.view(COLLAPSE_PLATFORM_SIZES_2).slice(0,23));
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdCollapsePlatform.Assets.fromAct2Assets(foreign));
    }

    @Test void collapseNewViewsRemainDetachedOriginalDataWithoutNewArtReadersOrPaddingRepairs() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{MAP_SPR_COLLAPSE_PLATFORM_1,COLLAPSE_PLATFORM_SIZES_1,MAP_SPR_COLLAPSE_PLATFORM_3,MAP_SPR_COLLAPSE_PLATFORM_2,COLLAPSE_PLATFORM_SIZES_2,MAP_SPR_COLLAPSE_PLATFORM_4}) {
            var bytes=o.view(key);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;assertSame(bytes,o.view(key));assertArrayEquals(before,o.view(key).copy());
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var map=o.view(MAP_SPR_COLLAPSE_PLATFORM_4).copy();map[41]=1;
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(map,2,57));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(MAP_SPR_COLLAPSE_PLATFORM_3).copy(),7,40));
    }

    @Test void logShadowAndMosquiViewsFollowTheirActualDispatchInitAndAnimationOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(116,LOG_SHADOW_BASE_TILES.ordinal());assertEquals(121,MAP_SPR_MOSQUI_DAMAGED.ordinal());
        assertEquals(0x208788,o.view(OBJECT_INDEX).unsignedLong((0x33-1)*4));
        assertEquals(0x20B3A0,o.view(OBJECT_INDEX).unsignedLong((0x3D-1)*4));
        assertEquals("217c002088160004",hex.formatHex(file.readFile(0x87B6,8)));
        assertEquals("317b000c0002",hex.formatHex(file.readFile(0x87F8,6)));
        assertEquals(LOG_SHADOW_BASE_TILES.fileOffset(),0x87FA+(byte)file.readFile(0x87FB,1)[0]);
        assertEquals("70004eb90020cc50",hex.formatHex(file.readFile(0xB3FC,8)),"both native variants use existing selector0");
        int[][] pc={{0xB40A,0xB50E,0x43},{0xB40E,0xB4F2,0x45},{0xB41A,0xB54A,0x43},{0xB41E,0xB500,0x45}};
        for(var row:pc) {var raw=CdAssetBytes.copyOf(file.readFile(row[0],4));assertEquals(row[2],raw.unsignedByte(0));assertEquals(0xFA,raw.unsignedByte(1));assertEquals(row[1],row[0]+2+(short)raw.unsignedWord(2));}
        assertEquals("21490004214a00302140002c",hex.formatHex(file.readFile(0xB428,12)),"selected map/script pointers remain full native stored LONGs");
        for(int call:new int[]{0xB470,0xB4AC})assertEquals("226800304ef900205b8e",hex.formatHex(file.readFile(call,10)),"AnimateObject uses the retained oVar30 pointer");
        assertEquals(0x208816,o.profile().range(MAP_SPR_LOG_SHADOW).nativeAddress());
        assertEquals(0x20B50E,o.profile().range(MAP_SPR_MOSQUI_NORMAL).nativeAddress());
        assertEquals(0x20B54A,o.profile().range(MAP_SPR_MOSQUI_DAMAGED).nativeAddress());
    }

    @Test void logShadowKeepsAllEightBaseWordsAndBothCompleteStaticCountedFrames() throws Exception {
        var o=retail().terrain().objects();var visual=o.logShadow();var hex=java.util.HexFormat.of();
        assertEquals("03cb045e041803f00428038f0428037f",hex.formatHex(o.view(LOG_SHADOW_BASE_TILES).copy()));
        assertEquals("0004000a01f00f0000f001f80d0000f0",hex.formatHex(o.view(MAP_SPR_LOG_SHADOW).copy()));
        assertEquals(List.of(0x3CB,0x45E,0x418,0x3F0,0x428,0x38F,0x428,0x37F),
                java.util.stream.IntStream.range(0,8).map(i->o.view(LOG_SHADOW_BASE_TILES).unsignedWord(i*2)).boxed().toList());
        assertEquals(0x43F0,o.view(LOG_SHADOW_BASE_TILES).unsignedWord(2*(2*1+1))|0x4000,"SetBaseTile raw ordinary period1/act1 selects offset6, not Act1's offset4");
        assertEquals(List.of(4,10),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(1,1),visual.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(6,6),visual.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,15,0,-16),new CdObjectVisualAssets.Piece(-8,13,0,-16)),
                visual.mappingFrames().stream().map(f->f.pieces().getFirst()).toList());
        assertTrue(visual.animations().isEmpty());assertEquals(0,visual.animationTable().length());assertEquals(0,visual.mappingTableOffset());
        assertEquals(16,visual.mappings().length());assertArrayEquals(o.view(MAP_SPR_LOG_SHADOW).copy(),visual.mappings().copy());
        assertEquals(16,visual.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertThrows(IndexOutOfBoundsException.class,()->o.view(LOG_SHADOW_BASE_TILES).unsignedWord(16));
    }

    @Test void bothCompleteMosquiIncludesKeepOddRootsAllPiecesFfFcScriptsAndDistinctPaddingOrigins() throws Exception {
        var o=retail().terrain().objects();var normal=o.normalMosqui();var damaged=o.damagedMosqui();var hex=java.util.HexFormat.of();
        assertEquals("00040008010001ff06020304fc00",hex.formatHex(o.view(ANI_MOSQUI_NORMAL).copy()));
        assertEquals("00040008040001ff0e020304fc00",hex.formatHex(o.view(ANI_MOSQUI_DAMAGED).copy()));
        assertEquals("000a001a00250030003603f4080000f8fc050003f0fc0500070002fc050003f0fc05000b0002f00a000ff808040018f001f00b001af801f00b0026f4",hex.formatHex(o.view(MAP_SPR_MOSQUI_NORMAL).copy()));
        assertEquals("000a001a00250030003603f4080000f8fc050032f0fc0500070002fc050032f0fc05000b0002f00a000ff808040036f001f00b0038f80200010044fcf00b0026f400",hex.formatHex(o.view(MAP_SPR_MOSQUI_DAMAGED).copy()));
        for(var visual:List.of(normal,damaged)) {
            assertEquals(0,visual.mappingTableOffset());assertEquals(5,visual.mappingFrames().size());assertEquals(2,visual.animations().size());
            assertEquals(List.of(10,26,37,48,54),visual.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
            assertEquals(List.of(4,8),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
            assertEquals(List.of(List.of(0,1),List.of(2,3,4)),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
            assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC),visual.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
            assertEquals(List.of(4,6),visual.animations().stream().map(a->a.bytes().length()).toList());
            assertEquals(0,visual.animationTable().unsignedByte(13),"normal literal final00 versus damaged parent EVEN byte, both actual source bytes");
        }
        assertEquals(List.of(1,6),normal.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(4,14),damaged.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(3,2,2,1,1),normal.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(3,2,2,1,2),damaged.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(16,11,11,6,6),normal.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(List.of(16,11,11,6,11),damaged.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(60,normal.mappings().length());assertEquals(66,damaged.mappings().length());assertEquals(0,damaged.mappings().unsignedByte(65),"literal mapping include byte after the final counted piece");
        assertEquals(50,normal.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertEquals(70,damaged.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertArrayEquals(o.view(MAP_SPR_MOSQUI_NORMAL).copy(),normal.mappings().copy());assertArrayEquals(o.view(ANI_MOSQUI_NORMAL).copy(),normal.animationTable().copy());
        assertArrayEquals(o.view(MAP_SPR_MOSQUI_DAMAGED).copy(),damaged.mappings().copy());assertArrayEquals(o.view(ANI_MOSQUI_DAMAGED).copy(),damaged.animationTable().copy());
    }

    @Test void actTwoLogShadowAndMosquiFactoriesBorrowOnlyTheirCachedSourceViewsAndExistingReaderPlcValues() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var lists=o.plcLists();
        var shadow=com.openggf.game.soniccd.objects.CdLogShadow.Assets.fromAct2Assets(o);
        var mosqui=com.openggf.game.soniccd.objects.CdMosqui.Assets.fromAct2Assets(o);
        assertSame(o.logShadow(),o.logShadow());assertSame(o.normalMosqui(),o.normalMosqui());assertSame(o.damagedMosqui(),o.damagedMosqui());assertNotSame(mosqui.normal(),mosqui.damaged());
        assertSame(o.view(LOG_SHADOW_BASE_TILES),shadow.baseTileWords());assertSame(o.logShadow(),shadow.visual());assertEquals(0x208816,shadow.mappingsAddressBits());
        assertSame(o.view(OBJECT_TILE_IDS),mosqui.objectTileIds());assertSame(o.normalMosqui(),mosqui.normal());assertSame(o.damagedMosqui(),mosqui.damaged());
        assertEquals(0x20B50E,mosqui.normalMappingsAddress());assertEquals(0x20B4F2,mosqui.normalScriptAddress());assertEquals(0x20B54A,mosqui.damagedMappingsAddress());assertEquals(0x20B500,mosqui.damagedScriptAddress());
        assertEquals(0x23A7,CdR11aObjectAssets.decodeTileWord(mosqui.objectTileIds().copy(),0,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(mosqui.objectTileIds().copy(),0,1));
        assertSame(readers,o.readerProfiles());assertSame(lists,o.plcLists());assertEquals(35,readers.size());assertEquals(19,lists.size());
        assertEquals(16*32,o.readerProfile(ART_LOG_SHADOW).outputBytes());assertEquals(70*32,o.readerProfile(ART_MOSQUI).outputBytes());
        assertEquals(24,o.readerProfile(ART_LOG_SHADOW).sourceBlobRange().length());assertEquals(23,o.readerProfile(ART_LOG_SHADOW).javaConsumedInputBytes());
        assertEquals(846,o.readerProfile(ART_MOSQUI).sourceBlobRange().length());assertEquals(846,o.readerProfile(ART_MOSQUI).javaInputRange().length());assertEquals(847,o.readerProfile(ART_MOSQUI).nativeInputRange().length());
        for(int id:new int[]{4,9}) {
            assertEquals(0x74E0,lists.get(id).entries().stream().filter(e->e.source()==ART_MOSQUI).findFirst().orElseThrow().destinationWord());
            assertEquals(0x7E00,lists.get(id).entries().stream().filter(e->e.source()==ART_LOG_SHADOW).findFirst().orElseThrow().destinationWord());
        }
        assertEquals(3,com.openggf.game.soniccd.objects.CdLogShadow.Assets.class.getRecordComponents().length);assertEquals(7,com.openggf.game.soniccd.objects.CdMosqui.Assets.class.getRecordComponents().length);
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdLogShadow.Assets.fromAct2Assets(null));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdMosqui.Assets.fromAct2Assets(null));
        // Constructed negative only: an invalid declared identity is not an actual source admission.
        var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);var identity=org.mockito.Mockito.mock(CdR12aTerrainAssets.Identity.class);
        org.mockito.Mockito.when(identity.variant()).thenReturn(CdReleaseVariant.EUROPE);org.mockito.Mockito.when(foreign.identity()).thenReturn(identity);
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdLogShadow.Assets.fromAct2Assets(foreign));
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdMosqui.Assets.fromAct2Assets(foreign));
    }

    @Test void newLogShadowMosquiViewsStayDetachedAndRejectChangedOriginalsUnrepresentedReadsAndMalformedTables() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{LOG_SHADOW_BASE_TILES,MAP_SPR_LOG_SHADOW,ANI_MOSQUI_NORMAL,ANI_MOSQUI_DAMAGED,MAP_SPR_MOSQUI_NORMAL,MAP_SPR_MOSQUI_DAMAGED}) {
            var bytes=o.view(key);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;assertSame(bytes,o.view(key));assertArrayEquals(before,o.view(key).copy());
            assertArrayEquals(file.readFile(key.fileOffset(),key.length()),o.view(key).copy());assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));assertThrows(IllegalArgumentException.class,()->o.readerView(key,CdR12aObjectAssets.ReaderKind.NATIVE));
            byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var shadow=o.view(MAP_SPR_LOG_SHADOW).copy();shadow[1]=5;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(shadow,2,16));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(MAP_SPR_LOG_SHADOW).copy(),2,15));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_MOSQUI_NORMAL).copy(),5,o.view(ANI_MOSQUI_NORMAL).copy(),2,49));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_MOSQUI_DAMAGED).copy(),5,o.view(ANI_MOSQUI_DAMAGED).copy(),2,69));
        var maps=o.view(MAP_SPR_MOSQUI_DAMAGED).copy();maps[65]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(maps,5,o.view(ANI_MOSQUI_DAMAGED).copy(),2,70));
        var script=o.view(ANI_MOSQUI_DAMAGED).copy();script[13]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_MOSQUI_DAMAGED).copy(),5,script,2,70));
        var table=new com.openggf.game.soniccd.objects.CdObjectAnimation.AnimationTable(o.view(ANI_MOSQUI_NORMAL),0,2);
        assertThrows(UnsupportedOperationException.class,()->com.openggf.game.soniccd.objects.CdObjectAnimation.step(new com.openggf.game.soniccd.objects.CdObjectAnimation.State(2,2,0,0,0,4,0,2,0),table));
    }

    @Test void childEffectViewsUseActualActTwoDispatchMappingAnimationAndTileOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(122,ANI_EXPLOSION.ordinal());assertEquals(123,MAP_SPR_EXPLOSION.ordinal());assertEquals(124,MAP_SPR_POINTS.ordinal());assertEquals(125,ANI_FLOWER.ordinal());assertEquals(126,MAP_SPR_FLOWER.ordinal());
        assertEquals(0x205EFA,o.view(OBJECT_INDEX).unsignedLong((0x18-1)*4));assertEquals(0x20AADE,o.view(OBJECT_INDEX).unsignedLong((0x1C-1)*4));assertEquals(0x205F88,o.view(OBJECT_INDEX).unsignedLong((0x1F-1)*4));
        assertEquals("217c0021d7140004",hex.formatHex(file.readFile(0x5F30,8)));assertEquals("43f90021d700",hex.formatHex(file.readFile(0x5F5E,6)));
        assertEquals("217c0020aa8c0004",hex.formatHex(file.readFile(0xAA60,8)));
        assertEquals("217c00236ab00004",hex.formatHex(file.readFile(0x5FCA,8)));assertEquals("43f900236a94",hex.formatHex(file.readFile(0x6052,6)));
        assertEquals("317c86800002",hex.formatHex(file.readFile(0x5F1E,6)));assertEquals("317c06c60002",hex.formatHex(file.readFile(0xAA5A,6)));assertEquals("317ca6d70002",hex.formatHex(file.readFile(0x5FB8,6)));
        assertEquals(0x21D714,o.profile().range(MAP_SPR_EXPLOSION).nativeAddress());assertEquals(0x20AA8C,o.profile().range(MAP_SPR_POINTS).nativeAddress());assertEquals(0x236AB0,o.profile().range(MAP_SPR_FLOWER).nativeAddress());
        // Equal legacy Flower includes exist, but its reached actual operands select the later source.
        assertArrayEquals(file.readFile(0x1DF76,28),o.view(ANI_FLOWER).copy());assertArrayEquals(file.readFile(0x1DF92,90),o.view(MAP_SPR_FLOWER).copy());
        assertNotEquals(0x21DF76,o.profile().range(ANI_FLOWER).nativeAddress());assertNotEquals(0x21DF92,o.profile().range(MAP_SPR_FLOWER).nativeAddress());
    }

    @Test void completeExplosionAndPointsTablesRetainEveryBodyScriptRootAndRealPaddingByte() throws Exception {
        var o=retail().terrain().objects();var explosion=o.explosion();var points=o.points();var hex=java.util.HexFormat.of();
        assertEquals("0004000c030005060304fc00030001020304fc00",hex.formatHex(o.view(ANI_EXPLOSION).copy()));
        assertEquals("000e001a002a00400056006c007c02f8050000f0f8050800000003f00d0004f00005000cf00005080c0004f0050010f0f00500140000050018f000051810000004e80a001ce8e80a081c00000a101ce8000a181c000004e80a0025e8e80a082500000a1025e8000a1825000003f00d002ef000050036f0000508360004f005003af0f005003e0000050042f00005183a0000",hex.formatHex(o.view(MAP_SPR_EXPLOSION).copy()));
        assertEquals("000c001800240030003c00420200000000f80000000300000200000001f80000000300000200000002f80000000300000200000004f80000000300000100000004fc0300000000f400000003fc0000000304",hex.formatHex(o.view(MAP_SPR_POINTS).copy()));
        assertEquals(List.of(14,26,42,64,86,108,124),explosion.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2,3,4,4,4,3,4),explosion.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(11,16,21,21,21,16,21),explosion.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(List.of(4,12),explosion.animations().stream().map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        assertEquals(List.of(3,3),explosion.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(0,5,6,3,4),List.of(0,1,2,3,4)),explosion.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        for(var script:explosion.animations()) {assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,script.terminal());assertEquals(8,script.bytes().length());assertEquals(0,script.bytes().unsignedByte(7));}
        for(int at:new int[]{25,63,85,107,145})assertEquals(0,explosion.mappings().unsignedByte(at));
        assertEquals(List.of(12,24,36,48,60,66),points.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2,2,2,2,1,3),points.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(11,11,11,11,6,16),points.mappingFrames().stream().map(f->f.bytes().length()).toList());
        for(int at:new int[]{23,35,47,59})assertEquals(0,points.mappings().unsignedByte(at));
        assertTrue(points.animations().isEmpty());assertEquals(0,points.animationTable().length());assertEquals(0,points.mappingTableOffset());
        assertEquals(146,explosion.mappings().length());assertEquals(20,explosion.animationTable().length());assertEquals(82,points.mappings().length());
        assertEquals(70,explosion.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertEquals(5,points.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertArrayEquals(o.view(MAP_SPR_EXPLOSION).copy(),explosion.mappings().copy());assertArrayEquals(o.view(ANI_EXPLOSION).copy(),explosion.animationTable().copy());assertArrayEquals(o.view(MAP_SPR_POINTS).copy(),points.mappings().copy());
    }

    @Test void completeSelectedFlowerIncludesKeepEightFramesFourScriptsAndAllCountedTiles() throws Exception {
        var o=retail().terrain().objects();var flower=o.flower();var hex=java.util.HexFormat.of();
        assertEquals("0008000c00120018030001ff0302030203fc0105050406fc130607ff",hex.formatHex(o.view(ANI_FLOWER).copy()));
        assertEquals("00100016001c002200280034003a004a01f0010000fc01f0010800fc01f0050002f801f0050006f802e809001cf4f8000022fc0001f0050023f803d00a000af4e809001cf4f8000022fc03d00a0013f4e809001cf4f8000022fc",hex.formatHex(o.view(MAP_SPR_FLOWER).copy()));
        assertEquals(List.of(16,22,28,34,40,52,58,74),flower.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(1,1,1,1,2,1,3,3),flower.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(List.of(6,6,6,6,11,6,16,16),flower.mappingFrames().stream().map(f->f.bytes().length()).toList());
        assertEquals(List.of(8,12,18,24),flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::relativeOffset).toList());
        assertEquals(List.of(3,3,1,19),flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(List.of(0,1),List.of(2,3,2,3),List.of(5,5,4,6),List.of(6,7)),flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::frameBytes).toList());
        assertEquals(List.of(CdObjectVisualAssets.Terminal.LOOP_FF,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,CdObjectVisualAssets.Terminal.LOOP_FF),flower.animations().stream().map(CdObjectVisualAssets.AnimationScript::terminal).toList());
        assertEquals(List.of(4,6,6,4),flower.animations().stream().map(a->a.bytes().length()).toList());
        assertEquals(0,flower.mappings().unsignedByte(51));assertEquals(90,flower.mappings().length());assertEquals(28,flower.animationTable().length());assertEquals(0,flower.mappingTableOffset());
        assertEquals(39,flower.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertArrayEquals(o.view(MAP_SPR_FLOWER).copy(),flower.mappings().copy());assertArrayEquals(o.view(ANI_FLOWER).copy(),flower.animationTable().copy());
    }

    @Test void actualChildEffectFactoriesBorrowCachedVisualsAndKeepExistingStdArtReaderPlcOwners() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var lists=o.plcLists();
        var explosion=com.openggf.game.soniccd.objects.CdExplosion.Assets.fromAct2Assets(o);var points=com.openggf.game.soniccd.objects.CdPoints.Assets.fromAct2Assets(o);var flower=com.openggf.game.soniccd.objects.CdFlower.Assets.fromAct2Assets(o);
        assertSame(o.explosion(),explosion.explosion());assertSame(o.points(),points.points());assertSame(o.flower(),flower.flower());assertSame(o.explosion(),o.explosion());assertSame(o.points(),o.points());assertSame(o.flower(),o.flower());
        assertEquals(0x21D714,explosion.mappingsAddress());assertEquals(0x20AA8C,points.mappingsAddress());assertEquals(0x236AB0,flower.mappingsAddress());
        assertEquals(2,com.openggf.game.soniccd.objects.CdExplosion.Assets.class.getRecordComponents().length);assertEquals(2,com.openggf.game.soniccd.objects.CdPoints.Assets.class.getRecordComponents().length);assertEquals(2,com.openggf.game.soniccd.objects.CdFlower.Assets.class.getRecordComponents().length);
        assertSame(readers,o.readerProfiles());assertSame(lists,o.plcLists());assertEquals(35,readers.size());assertEquals(19,lists.size());
        assertEquals(70*32,o.readerProfile(ART_EXPLOSIONS).outputBytes());assertEquals(5*32,o.readerProfile(ART_POINTS).outputBytes());assertEquals(39*32,o.readerProfile(ART_FLOWER).outputBytes());
        assertEquals(0xD000,lists.get(1).entries().stream().filter(e->e.source()==ART_EXPLOSIONS).findFirst().orElseThrow().destinationWord());
        assertEquals(0xD8C0,lists.get(1).entries().stream().filter(e->e.source()==ART_POINTS).findFirst().orElseThrow().destinationWord());
        assertEquals(0xDAE0,lists.get(1).entries().stream().filter(e->e.source()==ART_FLOWER).findFirst().orElseThrow().destinationWord());
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdExplosion.Assets.fromAct2Assets(null));assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdPoints.Assets.fromAct2Assets(null));assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdFlower.Assets.fromAct2Assets(null));
        // Constructed negatives exercise typed source rejection; they are not a World or actual media admission.
        var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);var identity=org.mockito.Mockito.mock(CdR12aTerrainAssets.Identity.class);org.mockito.Mockito.when(identity.variant()).thenReturn(CdReleaseVariant.EUROPE);org.mockito.Mockito.when(foreign.identity()).thenReturn(identity);
        assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdExplosion.Assets.fromAct2Assets(foreign));assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdPoints.Assets.fromAct2Assets(foreign));assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdFlower.Assets.fromAct2Assets(foreign));
    }

    @Test void childEffectRawViewsRejectChangedOriginalSourcesNewReadersFiniteOverreadsAndMalformedTables() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{ANI_EXPLOSION,MAP_SPR_EXPLOSION,MAP_SPR_POINTS,ANI_FLOWER,MAP_SPR_FLOWER}) {
            var bytes=o.view(key);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;assertSame(bytes,o.view(key));assertArrayEquals(before,o.view(key).copy());assertArrayEquals(file.readFile(key.fileOffset(),key.length()),o.view(key).copy());
            assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));assertThrows(IllegalArgumentException.class,()->o.readerView(key,CdR12aObjectAssets.ReaderKind.JAVA));
            byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var explosion=o.view(MAP_SPR_EXPLOSION).copy();explosion[145]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(explosion,7,o.view(ANI_EXPLOSION).copy(),2,70));
        var points=o.view(MAP_SPR_POINTS).copy();points[1]=13;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(points,6,5));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_EXPLOSION).copy(),7,o.view(ANI_EXPLOSION).copy(),2,69));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(MAP_SPR_POINTS).copy(),6,4));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_FLOWER).copy(),8,o.view(ANI_FLOWER).copy(),4,38));
        var script=o.view(ANI_EXPLOSION).copy();script[11]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_EXPLOSION).copy(),7,script,2,70));
        var flower=o.view(ANI_FLOWER).copy();flower[26]=8;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_FLOWER).copy(),8,flower,4,39));
        assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdExplosion.Assets(o.flower(),0x21D714));assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdPoints.Assets(o.explosion(),0x20AA8C));assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdFlower.Assets(o.explosion(),0x236AB0));
    }

    @Test void horizontalDoorUsesItsActualActTwoAbsoluteOperandsAndCompleteRawIncludes() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(127,ANI_TUNNEL_DOOR.ordinal());assertEquals(128,MAP_SPR_TUNNEL_DOOR.ordinal());
        assertEquals(0x206114,o.view(OBJECT_INDEX).unsignedLong((0x0D-1)*4));
        assertEquals("217c0021d6c40004",hex.formatHex(file.readFile(0x618A,8)));
        assertEquals("43f90021d6b6",hex.formatHex(file.readFile(0x61D0,6)));
        assertEquals("700c4eb90020cc50",hex.formatHex(file.readFile(0x61B8,8)));
        assertArrayEquals(file.readFile(0xCBB6,14),o.view(ANI_TUNNEL_DOOR).copy());
        assertNotEquals(0x20CBB6,o.profile().range(ANI_TUNNEL_DOOR).nativeAddress());
        assertFalse(Arrays.equals(file.readFile(0xCBC4,28),o.view(MAP_SPR_TUNNEL_DOOR).copy()));
        assertEquals("00020000000101010101010101fc",hex.formatHex(o.view(ANI_TUNNEL_DOOR).copy()));
        assertEquals("0010000402d0030000e4f0010004e40002f80c0006e8f804000a0800",hex.formatHex(o.view(MAP_SPR_TUNNEL_DOOR).copy()));
    }

    @Test void doorFactoryBorrowsOnlyItsCachedTwoFramesOneScriptAndExistingTwelveTileSource() throws Exception {
        var o=retail().terrain().objects();var v=o.tunnelDoor();assertSame(v,o.tunnelDoor());
        assertEquals(List.of(16,4),v.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());
        assertEquals(List.of(2,2),v.mappingFrames().stream().map(f->f.pieces().size()).toList());
        assertEquals(1,v.animations().size());assertArrayEquals(o.view(ANI_TUNNEL_DOOR).copy(),v.animationTable().copy());
        assertArrayEquals(o.view(MAP_SPR_TUNNEL_DOOR).copy(),v.mappings().copy());
        var a=com.openggf.game.soniccd.objects.CdTunnelDoor.Assets.fromAct2Assets(o);
        assertSame(v,a.visual());assertSame(o.view(OBJECT_TILE_IDS),a.objectTileIds());assertEquals(0x21D6C4,a.mappingsAddress());
        assertEquals(0x834E,CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(),0xC,0));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(),0xC,1));
        assertThrows(UnsupportedOperationException.class,()->CdR11aObjectAssets.decodeTileWord(a.objectTileIds().copy(),0xD,0));
        assertEquals(35,o.readerProfiles().size());assertEquals(19,o.plcLists().size());
        var reader=o.readerProfile(ART_TUNNEL_DOOR);assertEquals(154,reader.sourceBlobRange().length());
        assertEquals(154,reader.javaInputRange().length());assertEquals(154,reader.nativeInputRange().length());
        assertEquals(153,reader.javaConsumedInputBytes());assertEquals(384,reader.outputBytes());
        assertEquals(0x69C0,o.plcLists().get(1).entries().stream().filter(e->e.source()==ART_TUNNEL_DOOR).findFirst().orElseThrow().destinationWord());
    }

    @Test void doorRawViewsRejectForeignOriginalsOverreadsNewReadersAndMalformedVisualFallbacks() throws Exception {
        var a=retail().terrain();var o=a.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{ANI_TUNNEL_DOOR,MAP_SPR_TUNNEL_DOOR}) {
            var bytes=o.view(key);var before=bytes.copy();var changed=bytes.copy();changed[0]^=1;
            assertSame(bytes,o.view(key));assertArrayEquals(before,bytes.copy());
            assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));
            assertThrows(IllegalArgumentException.class,()->o.readerView(key,CdR12aObjectAssets.ReaderKind.NATIVE));
            byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;
            assertThrows(IOException.class,()->a.profile().verifyProgram(CdNativeFile.R12A,a.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var mapping=o.view(MAP_SPR_TUNNEL_DOOR).copy();mapping[27]=1;
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(mapping,2,o.view(ANI_TUNNEL_DOOR).copy(),1,12));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(MAP_SPR_TUNNEL_DOOR).copy(),2,o.view(ANI_TUNNEL_DOOR).copy(),1,11));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdTunnelDoor.Assets.fromAct2Assets(null));
    }

    @Test void tunnelPathHasActualIdSevenDispatchAndItsOwnPcAndRawLongOperands() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(129,TUNNEL_PATH_GROUND_SPEEDS.ordinal());assertEquals(130,TUNNEL_PATH_COORDINATES.ordinal());assertEquals(131,TUNNEL_PATH_MAPPING_ADDRESS.ordinal());
        assertEquals(0x20890E,o.view(OBJECT_INDEX).unsignedLong((7-1)*4));
        assertEquals("323b00164ebb1012",hex.formatHex(file.readFile(0x8972,8)));
        assertEquals("0008004e00ee0102",hex.formatHex(file.readFile(0x898A,8)));assertEquals(0x8992,0x898A+(short)0x0008);
        assertEquals("217c0021d5280004",hex.formatHex(file.readFile(0x8992,8)));
        assertEquals("45fa01fe",hex.formatHex(file.readFile(0x89C0,4)));assertEquals(0x8BC0,0x89C2+(short)0x01FE);
        assertEquals("303b000e",hex.formatHex(file.readFile(0x8BA8,4)));assertEquals(0x8BB8,0x8BAA+(byte)0x0E);
        assertEquals("10000c000c000800",hex.formatHex(o.view(TUNNEL_PATH_GROUND_SPEEDS).copy()));
        assertEquals("0006009000d60088144000f01478010814900140149001e0144001f8140001e013f001c013f00180140001701420016814400170146801a81660021816a0021016c001f816d001c816c001a816800198165801a0164001c8165001f01680020016c0020016d0021016d0028816c002c0168002d8165002c0165002a01680029017000290172802a0172802e0170002f000440f0801a00f9001a00fc801b80fe001f00fe0026010000290103002a0106802881080025010680218103002000ff002200fe0026010000290103002a010680288113001c8004416300290163003181638033816d003d0170003e0173803c8175803901738035816f8034016c0036016a8039016d003d0170003e0173803c817b8034817d0032017d00268",hex.formatHex(o.view(TUNNEL_PATH_COORDINATES).copy()));
        assertEquals("0021d528",hex.formatHex(o.view(TUNNEL_PATH_MAPPING_ADDRESS).copy()));
        assertEquals("00144e75",hex.formatHex(file.readFile(0x8BB4,4)));assertEquals("0828000300226768",hex.formatHex(file.readFile(0x8CDC,8)));
    }

    @Test void completeTunnelPathDirectoryAndAllThreeBodiesHaveNoInventedPaddingOrAliases() throws Exception {
        var o=retail().terrain().objects();var raw=o.view(TUNNEL_PATH_COORDINATES);var speeds=o.view(TUNNEL_PATH_GROUND_SPEEDS);
        assertEquals(284,raw.length());assertEquals(8,speeds.length());assertEquals(0x8BC0,TUNNEL_PATH_GROUND_SPEEDS.fileOffset()+speeds.length());
        int[] starts={6,144,214},bytes={136,68,68},firstX={0x1440,0x0F08,0x1630},firstY={0xF0,0x1A0,0x290},lastX={0x1700,0x1130,0x17D0},lastY={0x2F0,0x1C8,0x268};
        for(int i=0;i<3;i++) {
            assertEquals(starts[i],(short)raw.unsignedWord(2*i));assertEquals(bytes[i],raw.unsignedWord(starts[i]));
            assertEquals(firstX[i],raw.unsignedWord(starts[i]+2));assertEquals(firstY[i],raw.unsignedWord(starts[i]+4));
            int end=starts[i]+2+bytes[i];assertEquals(i==2?284:starts[i+1],end);assertEquals(0,bytes[i]%4);
            assertEquals(lastX[i],raw.unsignedWord(end-4));assertEquals(lastY[i],raw.unsignedWord(end-2));
        }
        assertEquals(List.of(0x1000,0xC00,0xC00,0x800),java.util.stream.IntStream.range(0,4).map(i->speeds.unsignedWord(i*2)).boxed().toList());
        // Native low-nibble GVel reads4..15 alias into the immediately following real path bytes.
        assertEquals(List.of(0x6,0x90,0xD6,0x88,0x1440,0xF0,0x1478,0x108,0x1490,0x140,0x1490,0x1E0),
                java.util.stream.IntStream.range(0,12).map(i->raw.unsignedWord(i*2)).boxed().toList());
        assertEquals(0x800,speeds.unsignedWord(6));assertEquals(0x268,raw.unsignedWord(282));
        assertThrows(IndexOutOfBoundsException.class,()->speeds.unsignedWord(7));assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(283));
        assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedByte(284));
        for(var added:new CdR12aProfile.Asset[]{TUNNEL_PATH_GROUND_SPEEDS,TUNNEL_PATH_COORDINATES,TUNNEL_PATH_MAPPING_ADDRESS})
            for(var old:Arrays.asList(CdR12aProfile.Asset.values()).subList(0,129))
                assertTrue(added.fileOffset()+added.length()<=old.fileOffset() || old.fileOffset()+old.length()<=added.fileOffset(),old+" overlaps "+added);
    }

    @Test void tunnelFactoryBorrowsOnlyThreeCachedRawViewsAndKeepsMappingLongOpaque() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var lists=o.plcLists();
        var a=com.openggf.game.soniccd.objects.CdTunnelPath.Assets.fromAct2Assets(o);
        assertSame(o.view(TUNNEL_PATH_GROUND_SPEEDS),a.groundSpeeds());assertSame(o.view(TUNNEL_PATH_COORDINATES),a.paths());
        assertEquals(0x208BB8,a.groundSpeedsAddressBits());assertEquals(0x208BC0,a.pathsAddressBits());assertEquals(0x21D528,a.mappingAddressBits());
        assertSame(a.paths(),com.openggf.game.soniccd.objects.CdTunnelPath.Assets.fromAct2Assets(o).paths());
        assertNotEquals(CdR11aObjectAssets.Table.TUNNEL_PATH_GROUND_SPEEDS.range().nativeAddress(),a.groundSpeedsAddressBits());
        assertNotEquals(CdR11aObjectAssets.Table.TUNNEL_PATH_COORDINATES.range().nativeAddress(),a.pathsAddressBits());
        assertEquals(5,com.openggf.game.soniccd.objects.CdTunnelPath.Assets.class.getRecordComponents().length);
        // The raw Powerup table is now registered separately; this factory still borrows only its Init LONG.
        assertEquals(MAP_SPR_POWERUP.nativeAddress(),a.mappingAddressBits());
        assertSame(readers,o.readerProfiles());assertSame(lists,o.plcLists());assertEquals(35,readers.size());assertEquals(19,lists.size());
        for(var key:new CdR12aProfile.Asset[]{TUNNEL_PATH_GROUND_SPEEDS,TUNNEL_PATH_COORDINATES,TUNNEL_PATH_MAPPING_ADDRESS}) {
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(key));assertThrows(IllegalArgumentException.class,()->o.readerView(key,CdR12aObjectAssets.ReaderKind.NATIVE));
        }
    }

    @Test void tunnelRawViewsAndBridgeRejectFiniteOverreadsChangedOriginalsAndForeignSourceIdentities() throws Exception {
        var assets=retail().terrain();var o=assets.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{TUNNEL_PATH_GROUND_SPEEDS,TUNNEL_PATH_COORDINATES,TUNNEL_PATH_MAPPING_ADDRESS}) {
            var bytes=o.view(key);var before=bytes.copy();var copy=bytes.copy();copy[0]^=1;
            assertSame(bytes,o.view(key));assertArrayEquals(before,bytes.copy());assertArrayEquals(file.readFile(key.fileOffset(),key.length()),bytes.copy());
            assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));
            byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;
            assertThrows(IOException.class,()->assets.profile().verifyProgram(CdNativeFile.R12A,assets.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        var mapping=o.view(TUNNEL_PATH_MAPPING_ADDRESS);assertEquals(0x21D528,mapping.unsignedLong(0));assertThrows(IndexOutOfBoundsException.class,()->mapping.unsignedLong(1));
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdTunnelPath.Assets.fromAct2Assets(null));
        // Constructed source negatives only; no fixture supplies World or programme authority.
        for(int variant=0;variant<4;variant++) {
            var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);var id=org.mockito.Mockito.mock(CdR12aTerrainAssets.Identity.class);
            org.mockito.Mockito.when(foreign.identity()).thenReturn(id);
            org.mockito.Mockito.when(id.variant()).thenReturn(variant==0?CdReleaseVariant.EUROPE:CdReleaseVariant.USA);
            org.mockito.Mockito.when(id.file()).thenReturn(variant==1?CdNativeFile.R11A:CdNativeFile.R12A);
            org.mockito.Mockito.when(id.domain()).thenReturn(variant==2?CdMmdImage.Domain.RELOCATED_COPY:CdMmdImage.Domain.ORIGINAL_WORD_RAM);
            org.mockito.Mockito.when(id.stage()).thenReturn(variant==3?com.openggf.game.soniccd.CdStagePayload.R11A.stage():com.openggf.game.soniccd.CdStagePayload.R12A.stage());
            assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdTunnelPath.Assets.fromAct2Assets(foreign));
            org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).view(org.mockito.ArgumentMatchers.any(CdR12aProfile.Asset.class));
        }
    }

    @Test void actualPowerupDispatchInitAndBothAnimationSitesSelectTheFullOriginalViews() throws Exception {
        var o=retail().terrain().objects();var file=retail().original();var hex=java.util.HexFormat.of();
        assertEquals(132,ANI_POWERUP.ordinal());assertEquals(133,MAP_SPR_POWERUP.ordinal());assertEquals(0x20628A,o.view(OBJECT_INDEX).unsignedLong((3-1)*4));
        assertEquals("323b00064efb1002",hex.formatHex(file.readFile(0x6290,8)));assertEquals("0008004400a400bc",hex.formatHex(file.readFile(0x6298,8)));
        assertEquals("54280024217c0021d5280004",hex.formatHex(file.readFile(0x62A0,12)));
        assertEquals("43f90021d400",hex.formatHex(file.readFile(0x6324,6)));assertEquals("43f90021d400",hex.formatHex(file.readFile(0x63C8,6)));
        assertEquals("0012001a0020003a0054006e007a00b400ee01010002000300ff0504050607ff00040400040400050500050500060600060600070700070700ff00040400040000050500050000060600060000070700070000ff00040000040000050000050000060000060000070000070000ff0008090a0b0c0b0a090800ff000808000808000909000909000a0a000a0a000b0b000b0b000c0c000c0c000b0b000b0b000a0a000a0a0009090009090008080008080000ff00000808000800000909000900000a0a000a00000b0b000b00000c0c000c00000b0b000b00000a0a000a000009090009000008080008000000ff00000800000800000900000900000a00000a00000b00000b00000c00000c00000b00000b00000a00000a000009000009000008000008000000ff00",hex.formatHex(o.view(ANI_POWERUP).copy()));assertEquals("0025001a002f00440059006e0083009800ae00c400da00f0010604e80a0000e8e80a000900000a1000e8000a10090004e80a0012e8e80a001b00000a1012e8000a101b0004e80a0809e8e80a080000000a1809e8000a18000004e80a0000e8e80a000900000a1809e8000a18000004e80a0809e8e80a080000000a1000e8000a10090004e80a0012e8e80a001b00000a181be8000a18120004e80a081be8e80a081200000a1012e8000a101b000004f0050000f0f00508000000051000f000051800000004f0050004f0f00508040000051004f000051804000004e80a0008e8e80a080800000a1008e8000a1808000004f0050011f0f00500150000051815f000051811000002f4060019f0f40608190000",hex.formatHex(o.view(MAP_SPR_POWERUP).copy()));
        assertEquals(o.profile().range(MAP_SPR_POWERUP).nativeAddress(),o.view(TUNNEL_PATH_MAPPING_ADDRESS).unsignedLong(0));
        assertEquals(0x1D528,ANI_POWERUP.fileOffset()+ANI_POWERUP.length());assertEquals(0x1D63A,MAP_SPR_POWERUP.fileOffset()+MAP_SPR_POWERUP.length());
    }

    @Test void allNineRawPowerupScriptsKeepTheirRealDirectoryDelaysFramesAndEvenPadding() throws Exception {
        var raw=retail().terrain().objects().view(ANI_POWERUP);
        int[] starts={18,26,32,58,84,110,122,180,238},delays={1,5,0,0,0,0,0,0,0},frames={6,4,24,24,24,10,55,55,55},terminals={25,31,57,83,109,121,178,236,294};
        for(int i=0;i<9;i++) {
            assertEquals(starts[i],(short)raw.unsignedWord(2*i));assertEquals(delays[i],raw.unsignedByte(starts[i]));
            assertEquals(frames[i],terminals[i]-starts[i]-1);assertEquals(0xFF,raw.unsignedByte(terminals[i]));
            for(int at=starts[i]+1;at<terminals[i];at++)assertTrue(raw.unsignedByte(at)<=12,"source map frame at "+at);
            int end=i==8?296:starts[i+1];assertEquals(i<6?0:1,end-terminals[i]-1);
            for(int at=terminals[i]+1;at<end;at++)assertEquals(0,raw.unsignedByte(at));
        }
        assertEquals(List.of(1,0,2,0,3,0),java.util.stream.IntStream.range(19,25).map(raw::unsignedByte).boxed().toList());
        assertEquals(List.of(8,9,10,11,12,11,10,9,8,0),java.util.stream.IntStream.range(111,121).map(raw::unsignedByte).boxed().toList());
        assertEquals(296,raw.length());assertEquals(0,raw.unsignedByte(295));assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedByte(296));
        assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(295));
    }

    @Test void allThirteenRawPowerupFramesRetainTheEmptyInteriorAliasAndExactTilesAndPadding() throws Exception {
        var raw=retail().terrain().objects().view(MAP_SPR_POWERUP);
        int[] roots={37,26,47,68,89,110,131,152,174,196,218,240,262},counts={0,4,4,4,4,4,4,4,4,4,4,4,2},maxTiles={0,18,36,18,18,18,36,36,4,8,17,25,31};
        for(int i=0;i<13;i++) {
            assertEquals(roots[i],(short)raw.unsignedWord(2*i));assertEquals(counts[i],raw.unsignedByte(roots[i]));int maximum=0;
            for(int piece=0;piece<counts[i];piece++) {
                int at=roots[i]+1+5*piece;int size=raw.unsignedByte(at+1);int tile=raw.unsignedWord(at+2)&0x7FF;
                maximum=Math.max(maximum,tile+(((size>>>2)&3)+1)*((size&3)+1));assertTrue(at+4<raw.length());
            }
            assertEquals(maxTiles[i],maximum,"source frame "+i);
        }
        assertTrue(roots[1]<roots[0] && roots[0]<roots[1]+1+5*counts[1]);assertEquals(37,roots[0]);assertEquals(0,raw.unsignedByte(37));
        for(int padding:new int[]{173,195,217,239,261,273})assertEquals(0,raw.unsignedByte(padding));
        assertEquals(274,raw.length());assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedByte(274));assertThrows(IndexOutOfBoundsException.class,()->raw.unsignedWord(273));
    }

    @Test void timeStarsBridgeBorrowsOnlyRawAnimationBytesAndKeepsAllArtReaderAndPlcOwnersUnchanged() throws Exception {
        var o=retail().terrain().objects();var readers=o.readerProfiles();var lists=o.plcLists();
        var a=com.openggf.game.soniccd.objects.CdTimeStars.Assets.fromAct2Assets(o);var again=com.openggf.game.soniccd.objects.CdTimeStars.Assets.fromAct2Assets(o);
        assertSame(o.view(ANI_POWERUP),a.animations().bytes());assertSame(a.animations().bytes(),again.animations().bytes());assertEquals(0,a.animations().tableOffset());assertEquals(9,a.animations().scriptCount());
        assertEquals(0x21D528,a.mappingAddressBits());assertEquals(2,com.openggf.game.soniccd.objects.CdTimeStars.Assets.class.getRecordComponents().length);
        assertSame(o.view(MAP_SPR_POWERUP),o.view(MAP_SPR_POWERUP));assertSame(readers,o.readerProfiles());assertSame(lists,o.plcLists());assertEquals(35,readers.size());assertEquals(19,lists.size());
        for(var source:new CdR12aProfile.Asset[]{ANI_POWERUP,MAP_SPR_POWERUP,ART_SHIELD,ART_INV_STARS,ART_TIME_STARS}) {
            assertThrows(IllegalArgumentException.class,()->o.readerProfile(source));assertThrows(IllegalArgumentException.class,()->o.readerView(source,CdR12aObjectAssets.ReaderKind.JAVA));
            assertTrue(lists.stream().flatMap(l->l.entries().stream()).noneMatch(e->e.source()==source));
        }
        assertEquals(1152,o.view(ART_INV_STARS).length());assertEquals(0x234968,o.profile().range(ART_INV_STARS).nativeAddress());
        assertEquals(992,o.view(ART_TIME_STARS).length());assertEquals(0x234DE8,o.profile().range(ART_TIME_STARS).nativeAddress());
    }

    @Test void powerupRawViewsAndTimeStarAssetsKeepFiniteBoundariesAndRejectForeignOriginalSources() throws Exception {
        var assets=retail().terrain();var o=assets.objects();var file=retail().original();
        for(var key:new CdR12aProfile.Asset[]{ANI_POWERUP,MAP_SPR_POWERUP}) {
            var bytes=o.view(key);var before=bytes.copy();var copy=bytes.copy();copy[0]^=1;assertSame(bytes,o.view(key));assertArrayEquals(before,bytes.copy());
            assertArrayEquals(file.readFile(key.fileOffset(),key.length()),bytes.copy());assertThrows(IndexOutOfBoundsException.class,()->bytes.unsignedByte(bytes.length()));
            byte[] corrupt=file.readFile(0,262144);corrupt[key.fileOffset()]^=1;assertThrows(IOException.class,()->assets.profile().verifyProgram(CdNativeFile.R12A,assets.profile().domain(),CdMmdImage.parse(corrupt)));
        }
        assertThrows(NullPointerException.class,()->com.openggf.game.soniccd.objects.CdTimeStars.Assets.fromAct2Assets(null));
        assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdTimeStars.Assets(new com.openggf.game.soniccd.objects.CdObjectAnimation.AnimationTable(o.view(ANI_POWERUP),0,8),0x21D528));
        assertThrows(IllegalArgumentException.class,()->new com.openggf.game.soniccd.objects.CdTimeStars.Assets(new com.openggf.game.soniccd.objects.CdObjectAnimation.AnimationTable(o.view(ANI_POWERUP),1,9),0x21D528));
        for(int kind=0;kind<4;kind++) {
            var foreign=org.mockito.Mockito.mock(CdR12aObjectAssets.class);var identity=org.mockito.Mockito.mock(CdR12aTerrainAssets.Identity.class);org.mockito.Mockito.when(foreign.identity()).thenReturn(identity);
            org.mockito.Mockito.when(identity.variant()).thenReturn(kind==0?CdReleaseVariant.EUROPE:CdReleaseVariant.USA);org.mockito.Mockito.when(identity.file()).thenReturn(kind==1?CdNativeFile.R11A:CdNativeFile.R12A);
            org.mockito.Mockito.when(identity.domain()).thenReturn(kind==2?CdMmdImage.Domain.RELOCATED_COPY:CdMmdImage.Domain.ORIGINAL_WORD_RAM);
            org.mockito.Mockito.when(identity.stage()).thenReturn(kind==3?com.openggf.game.soniccd.CdStagePayload.R11A.stage():com.openggf.game.soniccd.CdStagePayload.R12A.stage());
            assertThrows(IllegalArgumentException.class,()->com.openggf.game.soniccd.objects.CdTimeStars.Assets.fromAct2Assets(foreign));org.mockito.Mockito.verify(foreign,org.mockito.Mockito.never()).view(org.mockito.ArgumentMatchers.any(CdR12aProfile.Asset.class));
        }
    }

    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original, CdR12aPlayerAssets player) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media root"); assertFalse(root.isBlank()); Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) { var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst(); }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) { retained = new Retail(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A), CdR12aPlayerAssets.fromPrograms(source)); }
        return retained;
    }
}
