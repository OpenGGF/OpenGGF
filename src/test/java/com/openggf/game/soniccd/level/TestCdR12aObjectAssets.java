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

    @Test void complete88RowManifestPreservesTheOriginal28AndPinsEveryAdditionalOriginalDomainSpan() throws Exception {
        var objects = retail().terrain().objects(); var rows = MANIFEST.lines().filter(s -> !s.isBlank()).toList();
        assertEquals(88, CdR12aProfile.Asset.values().length); assertEquals(88, rows.size());
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

    private record Retail(CdR12aTerrainAssets terrain, CdMmdImage original) { }
    private Retail retained;
    private Retail retail() throws Exception {
        if (retained != null) return retained;
        String root = System.getProperty("soniccd.media.root"); assertNotNull(root, "Configure original CD media root"); assertFalse(root.isBlank()); Path cue;
        try (var entries = Files.list(Path.of(root).resolve("USA"))) { var cues = entries.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList(); assertEquals(1, cues.size()); cue = cues.getFirst(); }
        try (var source = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) { retained = new Retail(CdR12aTerrainAssets.fromPrograms(source), source.mmd(CdNativeFile.R12A)); }
        return retained;
    }
}
