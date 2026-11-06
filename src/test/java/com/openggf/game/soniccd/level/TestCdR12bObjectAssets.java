package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static com.openggf.game.soniccd.level.CdR12bObjectAssets.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual original-source metadata and pure decoder controls. ALL JAVA UNRUN; no body/Frame/current-art admission. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCdR12bObjectAssets {
    private static final String MANIFEST="""
OBJECT_INDEX 3c4a 256 256 e487f3d9aa462c50b3cb6f5cf029b2477a64bde6fd32a5c303262aa31dc75595 e487f3d9aa462c50b3cb6f5cf029b2477a64bde6fd32a5c303262aa31dc75595
OBJECT_TILE_IDS cc00 72 72 7b25e293d8155f08891b43228817e782581ed26aaa06a14712adff8c4e3f5762 7b25e293d8155f08891b43228817e782581ed26aaa06a14712adff8c4e3f5762
RING_DELTAS 9a4c 32 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
MAP_SPR_RING 9eb0 67 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
ANI_RING 9ea8 8 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
SPRING_SPEEDS 969e 4 4 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621 5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621
MAP_SPRING 9956 199 200 f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd 09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c
ANI_SPRING 9948 14 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
DIAGONAL_SPRING_HEADER 351c8 2 2 3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd 3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd
MAP_SPR_HUD ab1c 168 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
MAP_SPR_MONITOR_TIME a804 450 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
ANI_MONITOR a77c 136 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
MAP_SPR_COLLAPSE_PLATFORM_1 c2d6 118 118 e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12 e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12
COLLAPSE_PLATFORM_SIZES_1 c34c 24 24 9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1 9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1
MAP_SPR_COLLAPSE_PLATFORM_3 c364 62 62 77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c 77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c
MAP_SPR_COLLAPSE_PLATFORM_2 c3a2 286 286 be1266acacef995f34fa47ff432a198d5bfeddc6af3484949a0f26348d04cb2b be1266acacef995f34fa47ff432a198d5bfeddc6af3484949a0f26348d04cb2b
COLLAPSE_PLATFORM_SIZES_2 c4c0 24 24 c0069f95af27ab8e2b9913aab0991fe10e39feaf4bcaed16e3c7e9595a5b995a c0069f95af27ab8e2b9913aab0991fe10e39feaf4bcaed16e3c7e9595a5b995a
MAP_SPR_COLLAPSE_PLATFORM_4 c4d8 42 42 076fa0be74347e48024976fe775b0b2bc254fa2a1d8b358588406bf48c23126a 076fa0be74347e48024976fe775b0b2bc254fa2a1d8b358588406bf48c23126a
ANI_TAMABBOH_NORMAL b6f8 12 12 ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474 ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474
ANI_TAMABBOH_DAMAGED b704 26 26 09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84 09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84
MAP_SPR_TAMABBOH b71e 62 62 e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847 e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847
ANI_TAMABBOH_MISSILE b81e 8 8 764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5 764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5
MAP_SPR_TAMABBOH_MISSILE b826 42 42 10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb 10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb
ANIMAL_BASE_TILES cf1a 22 22 74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec 74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec
ANI_FLYING_ANIMAL ce64 66 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
ANI_GROUND_ANIMAL cea6 60 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
MAP_SPR_FLYING_ANIMAL cee2 20 20 00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39 00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39
MAP_SPR_GROUND_ANIMAL cef6 36 36 029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2 029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2
SWING_MOTION eb4e 64 64 70b18bc828edc56e0a4157ccb2b124aa274914d0e898a9a852239d5cade58eb8 70b18bc828edc56e0a4157ccb2b124aa274914d0e898a9a852239d5cade58eb8
MAP_SPR_SWING ecec 30 30 22d182c8037a55534a03b0f9cf2d90691c3161180ddd68d54eae822a3cfab68a 22d182c8037a55534a03b0f9cf2d90691c3161180ddd68d54eae822a3cfab68a
ROBOT_GENERATOR_BASE_TILES e700 22 22 3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4 3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4
ANI_ROBOT_GENERATOR e47a 8 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
MAP_SPR_ROBOT_GENERATOR e482 574 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
ROBOT_GENERATOR_EXPLOSION_SCHEDULE e6c0 64 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
ANI_ANTON b154 12 12 623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d 623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d
MAP_SPR_ANTON b160 112 112 898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808 898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808
MAP_SPR_PLATFORM c980 132 132 a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7 a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7
PLATFORM_WIDTHS c5f8 4 4 87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3 87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3
PLATFORM_RANGES c5fc 4 4 ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a
MAP_SPR_BOULDER 940a 13 14 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652
MAP_SPR_BREAKABLE_WALL ee64 312 312 c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226 c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226
BREAKABLE_WALL_PIECE_FRAMES ef9c 64 64 9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f 9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f
BREAKABLE_WALL_PIECE_DELTAS efdc 24 24 86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94 86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94
BREAKABLE_WALL_PIECE_SPEEDS eff4 48 48 7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31 7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31
MAP_SPR_FLOAT_BLOCK a1b0 76 76 98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e 98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e
ANI_FLOAT_BLOCK a192 30 30 3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9 3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9
MAP_SPR_SPIKES bfa6 24 24 db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607 db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607
MAP_SPR_LOG_SHADOW 87a4 16 16 bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b
LOG_SHADOW_BASE_TILES 8794 16 16 b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637 b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637
MAP_SPR_PATA_BATA_NORMAL b2e4 34 34 9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b 9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b
MAP_SPR_PATA_BATA_DAMAGED b306 40 40 96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889 96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889
ANI_PATA_BATA b2d2 18 18 a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3 a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3
MAP_SPR_EXPLOSION 1d714 146 146 0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44 0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44
ANI_EXPLOSION 1d700 20 20 9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649 9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649
MAP_SPR_FLOWER 36ab0 90 90 48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41 48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41
ANI_FLOWER 36a94 28 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
MAP_SPR_POINTS aa1a 82 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
MAP_SPR_MOSQUI_NORMAL b49c 60 60 5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e 5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e
MAP_SPR_MOSQUI_DAMAGED b4d8 66 66 23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313 23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313
ANI_MOSQUI_NORMAL b480 14 14 24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2 24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2
ANI_MOSQUI_DAMAGED b48e 13 14 6718cb74ab9b1ce59a3a122b0dee40b38ece57b7fdacd1f4baf7b76bc38c1a6e cf64e547e8082aac25e26ac28799500505b45169aababaed6d42e9b144990408
MAP_SPR_PROJECTOR e9f0 68 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
ANI_METAL_SONIC_HOLOGRAM e9ac 68 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
PROJECTOR_EXPLOSION_SCHEDULE ea34 38 38 5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3 5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3
MAP_SPR_SCENERY 86d8 62 62 5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12 5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12
SCENERY_BASE_TILES 86cc 12 12 90b119329f4db25def8b3f2b246da7076cf0cee8b236806ad54079b7c5371227 90b119329f4db25def8b3f2b246da7076cf0cee8b236806ad54079b7c5371227
MAP_SPR_SPRINGBOARD be7c 84 84 a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f
ANI_SPRINGBOARD be46 54 54 66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba 66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba
SPRINGBOARD_HITBOXES bb78 32 32 48c0ebee94aed675266f2c3b8ffbb5f609aeac6e40c7d72d792ef851c7e2735c 48c0ebee94aed675266f2c3b8ffbb5f609aeac6e40c7d72d792ef851c7e2735c
            MAP_SPR_SPINNING_DISC 937c 36 36 c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4 c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4
ANI_SPINNING_DISC 9374 8 8 bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260 bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260
SPINNING_DISC_PLAYER_FRAMES 924c 16 16 51995b0191d2a4a3dff61e17629c017c209c1170fbf06d5172a5ce046b325389 51995b0191d2a4a3dff61e17629c017c209c1170fbf06d5172a5ce046b325389
MAP_SPR_TUNNEL_DOOR 1d6c4 28 28 7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92 7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92
ANI_TUNNEL_DOOR 1d6b6 14 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
MAP_SPR_POWERUP 1d528 274 274 d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc
MAP_SPR_TUNNEL_DOOR_VERTICAL cb52 28 28 954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275 954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275
MAP_SPR_BIG_RING 1d8f6 176 176 c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4 c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4
ANI_BIG_RING 1d8ee 8 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
MAP_SPR_BIG_RING_FLASH d652 416 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
ANI_BIG_RING_FLASH d646 12 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
MAP_SPR_GOAL_SIGNPOST 1d9b4 126 126 610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8 610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8
ANI_SIGNPOST 1d9a6 14 14 9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691 9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691
GOAL_POST_BASE_TILES d3fa 112 112 82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724 82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724
LEVEL_END_TIME_BONUSES d5e4 42 42 be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861 be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861
RESULTS_INIT_DATA ded4 24 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
MAP_SPR_RESULTS deec 621 622 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621 3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9
MAP_SPR_TUNNEL_DOOR_SPLASH 1d652 100 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
ANI_TUNNEL_DOOR_SPLASH 1d63a 24 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
""";
    private static final String OPERANDS="""
3d3e 0020b1d0
b234 43fa00ae
b248 43fa00bc
b2b8 43fa0018
3838 43f900203c4a
cbde 43f90020cc00
9aca 43f900209a4c
9b80 237c00209eb0
9cb0 43f900209ea8
9696 317b00060030
95f2 217c00209956
96f2 43f900209948
aa8a 217c0020ab1c
a45e 217c0020a804
a50a 43f90020a77c
c000 43fa02d4
c004 45fa0346
c164 237c0020c364
c00e 43fa0392
c012 45fa04ac
c26c 237c0020c4d8
b594 45fa0162
b5a4 45fa015e
b590 43fa018c
b814 43fa0008
b796 217c0020b826
ce46 43fa00d2
ccf2 43fa0170
ce22 43fa0082
cc80 217c0020cee2
cd90 217c0020cef6
ec2e 43faff1e
eb9e 217c0020ecec
e2fc 43fa0402
e3ce 43fa00aa
e318 217c0020e482
e320 217c0020e6c0
b138 43fa001a
b0a0 217c0020b160
c552 203c0020c980
c58a 117b006c0019
c59c 117b105e002d
963a 217c0020995c
965c 217c002099a6
b5a0 43fa0192
3cb2 002093a0
93ce 217c0020940a0004
3d1e 0020ed0a
ed4c 217c0020ee640004
ed46 317c44be0002
eda2 4bfa01f8
edb0 49fa022a
edb4 47fa023e
3c8e 0020a03a
a080 217c0020a1b00004
a088 70054eb90020cbde
a122 43f90020a192
a150 43f90020a192
a164 43f90020a192
3cde 0020bed0
3d12 00208716
bee6 217c0020bfa60004
befa 700a4eba0ce0
8744 217c002087a40004
8786 317b000c0002
8764 7000103900ff152e0200007f0c0000026606d03900ff156ad040d03900ff1507d040317b000c00020068400000024e75
3c8a 00209d18
9d74 237c00209eb00004
9d82 337ca7ae0002
3ca6 00205e7c
5eb2 217c0021d7140004
5ee0 43f90021d700
3cc2 00205f0a
5f4c 217c00236ab00004
5fd4 43f900236a94
3cb6 0020aa6c
a9ee 217c0020aa1a0004
3cae 0020a594
a5e4 227c0020a804d000d2f10000524921490004
3d3a 0020b32e
b398 43fa0102
b39c 45fa00e2
b3a8 43fa012e
b3ac 45fa00e0
3cfe 0020e716
3d0e 00208652
3ce6 0020bb98
e7ae 217c0020e9f00004
e972 217c0020e9f00004
e7b6 217c0020ea34002c
e99c 43fa000e
867a 217c002086d80004
86bc d040317b000c00020068400000024e75
bbba 217c0020be7c0004
baec 45f90020bb780240
bbe0 70074eba0ffa
bc1a 43fa022a
            3c6a 0020915c
3c7a 00206096
3d16 002063e8
3d1a 0020ca88
3c96 0020d1ae
3c9e 0020d2e6
3ca2 0020d46a
9172 43f900209374
919c 217c0020937c0004
922c 137b001e001b
610c 217c0021d6c40004
6152 43f90021d6b6
641e 217c0021d5280004
cabe 217c0020cb520004
d216 217c0021d8f60004
d29a 43f90021d8ee
d194 217c0020d6520004
d19c 43f90020d646
d354 217c0021d9b40004
d4c0 217c0021d9b40004
d524 43f90021d9a6
d3e2 317b00160002
d5ce 31fb0014f7d2

3d2e 0020dc14
dc22 000a001e00ea012401d8
dc64 45f90020ded4
dca6 237c0020def60004
dcb6 237c0020df0a0004
dcc0 237c0020deec0004
dcd0 237c0020df000004
3c72 002061a6
61b4 000600420052
61ca 217c0021d6520004
61d8 700d4eb90020cbde
61f6 43f90021d63a
""";
    private record VisualCase(String method,View map,View script,int frames,int scripts,int root,int maxEnd,String digest){}
    private static final List<VisualCase> VISUALS=List.of(
        new VisualCase("rings", View.MAP_SPR_RING, View.ANI_RING, 9, 1, 0, 14, "111b9242f93bb7f44806482a9c0d84bcfb855633178d300d8bed9ffa3f310c69"),
        new VisualCase("springVertical", View.MAP_SPRING, View.ANI_SPRING, 3, 1, 0, 18, "30d1bb68e0e386dc033a4b03fb974b646b260a8ce7305e84c7b774ac86a9f2dc"),
        new VisualCase("springHorizontal", View.MAP_SPRING, View.ANI_SPRING, 3, 1, 6, 36, "67abc1b8a69a1067df65f9498a8ca5272b14f3bf82ab45153c48eb1ff03cfe0c"),
        new VisualCase("springDiagonal", View.MAP_SPRING, View.ANI_SPRING, 3, 1, 80, 46, "023154cfc0abda00f383c540e01170e0779fdb076836bce486efdfa12816b02f"),
        new VisualCase("hudVisuals", View.MAP_SPR_HUD, null, 4, 0, 0, 63, "129e50f076dabe13c71e92e77da17d9a11f4873d4ac01a6035baff470bfdba96"),
        new VisualCase("monitorTime", View.MAP_SPR_MONITOR_TIME, View.ANI_MONITOR, 20, 10, 0, 82, "05518483895992326b47e16947e5406dbc960eb0d65d0c9b0840520f5d20fec5"),
        new VisualCase("collapsePlatform1", View.MAP_SPR_COLLAPSE_PLATFORM_1, null, 1, 0, 0, 41, "e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12"),
        new VisualCase("collapsePlatform2", View.MAP_SPR_COLLAPSE_PLATFORM_2, null, 2, 0, 0, 37, "3881864a4a86aeab32fd5e93ad352cfb9ffa5373f7963554737eae09e422480a"),
        new VisualCase("collapsePlatform3", View.MAP_SPR_COLLAPSE_PLATFORM_3, null, 7, 0, 0, 41, "b0c375dda1569db9c1036829bfdc70b154e30bb62270bcb9f8837d5f810ee992"),
        new VisualCase("collapsePlatform4", View.MAP_SPR_COLLAPSE_PLATFORM_4, null, 2, 0, 0, 37, "a1bdd70106a671f628fada7394c4fab76f140ce4c7434ffba1ef892338c2049e"),
        new VisualCase("tamabbohNormal", View.MAP_SPR_TAMABBOH, View.ANI_TAMABBOH_NORMAL, 3, 2, 0, 37, "40e585bd558f46564d30996bbba3d5769841aed305e505af2c85e7085bb0926e"),
        new VisualCase("tamabbohDamaged", View.MAP_SPR_TAMABBOH, View.ANI_TAMABBOH_DAMAGED, 3, 2, 22, 59, "d2c734abaa992d2cb84b10e19093b62e9e7e741238d411c1d215f001dafdc041"),
        new VisualCase("tamabbohMissile", View.MAP_SPR_TAMABBOH_MISSILE, View.ANI_TAMABBOH_MISSILE, 4, 1, 0, 43, "3b188010514455e184716723f1240f7036f8ef776801f4f9abdf385ff46eb50d"),
        new VisualCase("flyingAnimal", View.MAP_SPR_FLYING_ANIMAL, View.ANI_FLYING_ANIMAL, 3, 2, 0, 8, "a8317c3b3110fca3902bd55a8d903f674216ca3d6a7d10217d0f4f9fb76154c5"),
        new VisualCase("groundAnimal", View.MAP_SPR_GROUND_ANIMAL, View.ANI_GROUND_ANIMAL, 5, 1, 0, 20, "4d57dc4cce83b05f5d8b21a68a1da8d2fd74fa75cbe441e74552b3773cb0d0f9"),
        new VisualCase("swing", View.MAP_SPR_SWING, null, 3, 0, 0, 14, "ab377e258ee720c124e24fd254221aba77c806d9555113b027f653e6a0c47c02"),
        new VisualCase("robotGenerator", View.MAP_SPR_ROBOT_GENERATOR, View.ANI_ROBOT_GENERATOR, 8, 1, 0, 23, "3bd8efad099e8c5f657fa0bead62ab07f47730f8e68ecc06bdab5908e749f44a"),
        new VisualCase("anton", View.MAP_SPR_ANTON, View.ANI_ANTON, 4, 2, 0, 22, "223b12a951ec27c19c58f819ffb9457e08bc19ea3f199594606a721872a53b7b"),
        new VisualCase("platform", View.MAP_SPR_PLATFORM, null, 3, 0, 0, 13, "54106a5fb3fff6388d1014b1f15688ef45568d49e9d04dfdc20187277c20d367"),
        new VisualCase("boulder", View.MAP_SPR_BOULDER, null, 1, 0, 0, 19, "dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d"),
        new VisualCase("breakableWall", View.MAP_SPR_BREAKABLE_WALL, null, 14, 0, 0, 57, "0e6119e55720d42fd03ab30cbc410f8ab6c6c354520eee340836c2985eeeefd9"),
        new VisualCase("floatBlock", View.MAP_SPR_FLOAT_BLOCK, View.ANI_FLOAT_BLOCK, 6, 3, 0, 15, "65c5694bceb7a6beb494174e661f380f2b14a1e8578efd7631c62757105f61b8"),
        new VisualCase("spikes", View.MAP_SPR_SPIKES, null, 1, 0, 0, 8, "19bfd226ca0d6adf8dfefef89eed1fb6cf4b7e4c279b68255a2ff726468a1c14"),
        new VisualCase("logShadow", View.MAP_SPR_LOG_SHADOW, null, 2, 0, 0, 16, "4832eeeee66550838053053d5b721a2ff138dc8134d9768b96939f3eac008e11"),
        new VisualCase("pataBataNormal", View.MAP_SPR_PATA_BATA_NORMAL, View.ANI_PATA_BATA, 3, 2, 0, 41, "9bd5004e11869515307a4fd84c6d9e21a04b715b5a0c32fb2732e91c60c106b5"),
        new VisualCase("pataBataDamaged", View.MAP_SPR_PATA_BATA_DAMAGED, View.ANI_PATA_BATA, 3, 2, 0, 50, "9e44f22ff5f65c5bdb9223c2cee3a019db6f7447d991ce2c82d34e0f234da0b6")
,
        new VisualCase("explosion", View.MAP_SPR_EXPLOSION, View.ANI_EXPLOSION, 7, 2, 0, 70, "76bacca4cca6beccb0c2b802b66d3b275bf4b111212204e6ed5ef17c3ef2097d"),
        new VisualCase("flower", View.MAP_SPR_FLOWER, View.ANI_FLOWER, 8, 4, 0, 39, "40f4cc6873b06fab029cd7dfcebbaad730b3429935ab9c1f5fb3d295311f799e"),
        new VisualCase("points", View.MAP_SPR_POINTS, null, 6, 0, 0, 5, "b59208a14a29f197008a87099ab38f83f8d0d25d2e986700256a781b92d16ce0"),
        new VisualCase("normalMosqui", View.MAP_SPR_MOSQUI_NORMAL, View.ANI_MOSQUI_NORMAL, 5, 2, 0, 50, "dfba20fe0fc8a0609978083f80fbc789e5aa04fa26567f22d48c25e2c63e1ae1"),
        new VisualCase("damagedMosqui", View.MAP_SPR_MOSQUI_DAMAGED, View.ANI_MOSQUI_DAMAGED, 5, 2, 0, 70, "ed5cb56ecd6b14d8c6984e8df7d5c4b4515a0e6fd83e68e23a0534d0d3bc18bb")
,
        new VisualCase("projector", View.MAP_SPR_PROJECTOR, View.ANI_METAL_SONIC_HOLOGRAM, 6, 2, 0, 51, "6b24c1eb0c96eff2ce1325abc38d9fb1a9702ca724634fa03cd2eca7b1c90d24"),
        new VisualCase("scenery", View.MAP_SPR_SCENERY, null, 3, 0, 0, 17, "d96816c35b7a2bb1e1d5ea42995436b9326b363a2793aea56227b64c82b0f296"),
        new VisualCase("springboard", View.MAP_SPR_SPRINGBOARD, View.ANI_SPRINGBOARD, 6, 5, 0, 21, "bd92f531f55c1c075d804f8ef6291c87b527f0b1e0275937f8b0f37672dc6405")
,
        new VisualCase("spinningDisc", View.MAP_SPR_SPINNING_DISC, View.ANI_SPINNING_DISC, 3, 1, 0, 12, "b4cf984fe2008e985a2e45cb2fa89e7989fad888e0d90a34702bf7760fe680bf"),
        new VisualCase("tunnelDoor", View.MAP_SPR_TUNNEL_DOOR, View.ANI_TUNNEL_DOOR, 2, 1, 0, 12, "040b1f891414b4a65724107afe5920458533b559191aaf01b333880e00335516"),
        new VisualCase("tunnelDoorVertical", View.MAP_SPR_TUNNEL_DOOR_VERTICAL, null, 2, 0, 0, 12, "6bbe7185af04c1f5f1a727af30da09a63695352568d398d2aeb1c7e16b7ae2f1"),
        new VisualCase("bigRing", View.MAP_SPR_BIG_RING, null, 4, 0, 0, 106, "b5e59a53c86d05fcfa45955e0e356f7b568d19cb7bd93e05afca70e5da00c3a8"),
        new VisualCase("bigRingFlash", View.MAP_SPR_BIG_RING_FLASH, null, 8, 0, 0, 77, "f76319dd2ac404010f79a0d7700a654dcfbd1a26cc55259145cf9463d198f92a"),
        new VisualCase("goalSignpost", View.MAP_SPR_GOAL_SIGNPOST, null, 6, 0, 0, 76, "a63351bbc19655e4e72202c317847a45f32f94af3eba2d21ccb4d9761889d858"),
        new VisualCase("tunnelDoorSplash", View.MAP_SPR_TUNNEL_DOOR_SPLASH, View.ANI_TUNNEL_DOOR_SPLASH, 7, 3, 0, 86, "8160085c1da02016510f5476143a928c5fef846c3e08d03552fa9428ee60098f")
    );

    @Test void publicSurfaceIsOnlyFortyEightMetadataMethodsWithPrivateOwnerConstruction() throws Exception {
        var type=CdR12bObjectAssets.class;assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(1,type.getDeclaredConstructors().length);assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));assertEquals(1,type.getDeclaredConstructors()[0].getParameterCount());
        assertEquals(List.of("anton","bigRing","bigRingFlash","boulder","breakableWall","collapsePlatform1","collapsePlatform2","collapsePlatform3","collapsePlatform4","damagedMosqui","explosion","floatBlock","flower","flyingAnimal","fromAssets","goalSignpost","groundAnimal","hudAssets","hudVisuals","identity","logShadow","monitorTime","normalMosqui","pataBataDamaged","pataBataNormal","placements","platform","points","projector","ringDeltas","rings","robotGenerator","scenery","spikes","spinningDisc","springDiagonal","springHorizontal","springMappings","springVertical","springboard","swing","tamabbohDamaged","tamabbohMissile","tamabbohNormal","tunnelDoor","tunnelDoorSplash","tunnelDoorVertical","view"),Arrays.stream(type.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())).map(java.lang.reflect.Method::getName).sorted().toList());
        assertEquals(88,View.values().length);assertEquals(41,VISUALS.size());
        var getter=CdR12bTerrainAssets.class.getMethod("objects");assertEquals(type,getter.getReturnType());assertTrue(Modifier.isSynchronized(getter.getModifiers()));
        var seam=CdR12bTerrainAssets.class.getDeclaredMethod("objectView",View.class);assertFalse(Modifier.isPublic(seam.getModifiers()));
        assertTrue(Arrays.stream(type.getDeclaredConstructors()).noneMatch(c->Arrays.asList(c.getParameterTypes()).contains(CdMmdImage.class)));
        assertTrue(Arrays.stream(type.getDeclaredFields()).noneMatch(f->f.getType()==CdMmdImage.class||f.getType()==CdNemesisAsset.Decoded.class||f.getType()==CdObjectPlacementRuntime.class));
        assertThrows(NullPointerException.class,()->fromAssets(null));
    }

    @Test void genuineFactoryBindsOnlyTheRetainedR12bOwnerAfterActualProgramSourceClose() throws Exception {
        var f=retail();var a=f.assets();var o=fromAssets(a);assertSame(a.objects(),o);assertSame(o,fromAssets(a));
        assertEquals(a.identity(),o.identity());assertEquals(CdStageKey.parse("R12B"),o.identity().stage());assertEquals(CdNativeFile.R12B,o.identity().file());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,o.identity().domain());assertEquals(CdReleaseVariant.USA,o.identity().variant());
        assertEquals("14de4fdb4979125a8c0c197c926133353d20a2ca5b2d57db2e363c4e94460a64",o.identity().mmdSha256());
        assertSame(a.placements(),o.placements());assertEquals(488,o.placements().records().size());
        assertEquals(a.identity(),f.independent().identity());assertNotSame(o,fromAssets(f.independent()));assertNotSame(o.placements(),fromAssets(f.independent()).placements());
    }

    @Test void everyOrderedFiniteViewPinsItsFullOriginalRangeCanonicalExtentHashAndImmutableCache() throws Exception {
        var o=fromAssets(retail().assets());var lines=MANIFEST.lines().filter(s->!s.isBlank()).toList();assertEquals(88,lines.size());
        for(int i=0;i<lines.size();i++){var c=lines.get(i).strip().split("\\s+");var v=View.values()[i];assertEquals(c[0],v.name());var r=v.range();var canonical=v.canonicalRange();
            assertEquals(Integer.parseInt(c[1],16),r.fileOffset());assertEquals(Integer.parseInt(c[2]),canonical.length());assertEquals(Integer.parseInt(c[3]),r.length());
            assertEquals(r.fileOffset(),canonical.fileOffset());assertEquals(CdNativeFile.R12B,r.file());assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,r.domain());assertEquals(0x200000+r.fileOffset(),r.nativeAddress());
            var bytes=o.view(v);assertSame(bytes,o.view(v));assertEquals(c[4],v.canonicalSha256());assertEquals(c[5],v.sha256());
            assertEquals(c[4],sha(retail().original().readFile(canonical.fileOffset(),canonical.length())));assertEquals(c[5],sha(bytes.copy()));
            assertArrayEquals(retail().original().readFile(r.fileOffset(),r.length()),bytes.copy());assertThrows(ReadOnlyBufferException.class,()->bytes.buffer().put(0,(byte)0));
            byte[] copy=bytes.copy();copy[0]^=0x7F;assertEquals(c[5],sha(o.view(v).copy()));
        }
        assertThrows(NullPointerException.class,()->o.view(null));
    }

    @Test void actualInitAnimateAndScalarOperandsSelectTheDeclaredR12bRootsIncludingNestedOnes() throws Exception {
        var original=retail().original();for(var line:OPERANDS.lines().filter(s->!s.isBlank()).toList()){var c=line.strip().split("\\s+");assertEquals(c[1],HexFormat.of().formatHex(original.readFile(Integer.parseInt(c[0],16),c[1].length()/2)),line);}
        assertEquals(0x20995C,View.MAP_SPRING.range().nativeAddress()+6);assertEquals(0x2099A6,View.MAP_SPRING.range().nativeAddress()+80);
        assertEquals(0x20B734,View.MAP_SPR_TAMABBOH.range().nativeAddress()+22);assertEquals(0x20ECEC,View.MAP_SPR_SWING.range().nativeAddress());
        assertEquals(0x002E,fromAssets(retail().assets()).view(View.DIAGONAL_SPRING_HEADER).unsignedWord(0));
    }

    @Test void allFortyOneVisualsDecodeCompleteSourceRootsScriptsFlipsAndCountedBodiesWithLiteralDigests() throws Exception {
        var o=fromAssets(retail().assets());for(var c:VISUALS){var method=CdR12bObjectAssets.class.getMethod(c.method());var v=(CdObjectVisualAssets)method.invoke(o);assertSame(v,method.invoke(o));
            assertEquals(c.frames(),v.mappingFrames().size(),c.method());assertEquals(c.scripts(),v.animations().size(),c.method());assertEquals(c.root(),v.mappingTableOffset());
            assertArrayEquals(o.view(c.map()).copy(),v.mappings().copy());assertArrayEquals(c.script()==null?new byte[0]:o.view(c.script()).copy(),v.animationTable().copy());
            assertEquals(c.digest(),semanticDigest(v),c.method());assertEquals(c.maxEnd(),v.mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElse(0));
            assertThrows(UnsupportedOperationException.class,()->v.mappingFrames().clear());assertThrows(UnsupportedOperationException.class,()->v.animations().clear());
        }
    }

    @Test void springUsesThreeActualIndicesAndRealParentEvenWithItsSeventyTwoBytePastTileTable() throws Exception {
        var o=fromAssets(retail().assets());assertEquals(199,View.MAP_SPRING.canonicalRange().length());assertEquals(200,o.springMappings().length());assertEquals(0,o.springMappings().unsignedByte(199));assertSame(o.view(View.MAP_SPRING),o.springMappings());
        assertEquals(72,o.view(View.OBJECT_TILE_IDS).length());assertNotNull(new CdSpring.Assets(o.springVertical(),o.springHorizontal(),o.springDiagonal(),View.MAP_SPRING.range().nativeAddress(),o.view(View.SPRING_SPEEDS),o.view(View.OBJECT_TILE_IDS)));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeIndexed(o.springMappings().slice(0,199).copy(),80,3,86,200,o.view(View.ANI_SPRING).copy(),1,46));
        assertArrayEquals(new byte[]{(byte)0xF0,0,(byte)0xF6,0},o.view(View.SPRING_SPEEDS).copy());
    }

    @Test void nestedTamabbohAndAnimalEmptyFramesKeepTheirCompleteUnmodifiedIncludes() throws Exception {
        var o=fromAssets(retail().assets());assertEquals(62,o.tamabbohNormal().mappings().length());assertEquals(22,o.tamabbohDamaged().mappingTableOffset());assertArrayEquals(o.tamabbohNormal().mappings().copy(),o.tamabbohDamaged().mappings().copy());
        assertEquals(26,o.tamabbohDamaged().animationTable().length());assertEquals(42,o.tamabbohMissile().mappings().length());
        assertTrue(o.flyingAnimal().mappingFrames().get(2).pieces().isEmpty());assertTrue(o.groundAnimal().mappingFrames().get(2).pieces().isEmpty());assertEquals(22,o.view(View.ANIMAL_BASE_TILES).length());
        assertTrue(o.rings().mappingFrames().get(8).pieces().isEmpty());assertTrue(o.robotGenerator().mappingFrames().get(7).pieces().isEmpty());
    }

    @Test void existingValidPureAssetsConstructorsReceiveGenuineValuesWithoutBodyOrSourceAdmission() throws Exception {
        var o=fromAssets(retail().assets());assertNotNull(new CdRing.SourceData(o.ringDeltas(),View.MAP_SPR_RING.range().nativeAddress(),0xA7AE,o.rings()));
        assertNotNull(new CdMonitor.Assets(o.monitorTime(),View.MAP_SPR_MONITOR_TIME.range().nativeAddress()));
        assertNotNull(new CdReservedHud.Assets(View.MAP_SPR_HUD.range().nativeAddress(),View.MAP_SPR_MONITOR_TIME.range().nativeAddress()));
        assertNotNull(new CdCollapsePlatform.Assets(o.collapsePlatform1(),o.collapsePlatform2(),o.collapsePlatform3(),o.collapsePlatform4(),o.view(View.COLLAPSE_PLATFORM_SIZES_1),o.view(View.COLLAPSE_PLATFORM_SIZES_2),View.MAP_SPR_COLLAPSE_PLATFORM_1.range().nativeAddress(),View.MAP_SPR_COLLAPSE_PLATFORM_2.range().nativeAddress(),View.MAP_SPR_COLLAPSE_PLATFORM_3.range().nativeAddress(),View.MAP_SPR_COLLAPSE_PLATFORM_4.range().nativeAddress()));
        assertNotNull(new CdRobotGenerator.Assets(o.view(View.ROBOT_GENERATOR_BASE_TILES),o.robotGenerator(),View.MAP_SPR_ROBOT_GENERATOR.range().nativeAddress(),o.view(View.ROBOT_GENERATOR_EXPLOSION_SCHEDULE),View.ROBOT_GENERATOR_EXPLOSION_SCHEDULE.range().nativeAddress()));
        assertNotNull(new CdPlatform.Assets(o.platform(),View.MAP_SPR_PLATFORM.range().nativeAddress(),o.view(View.PLATFORM_WIDTHS),o.view(View.PLATFORM_RANGES)));
    }

    @Test void existingAntonTamabbohAndSwingAdmissionLimitsRemainExplicitRatherThanFabricatingForeignData() throws Exception {
        var o=fromAssets(retail().assets());var tiles=o.view(View.OBJECT_TILE_IDS);
        assertThrows(IllegalArgumentException.class,()->new CdAnton.Assets(tiles,o.anton(),View.MAP_SPR_ANTON.range().nativeAddress()));
        assertThrows(IllegalArgumentException.class,()->new CdTamabboh.Assets(tiles,o.tamabbohNormal(),o.tamabbohDamaged(),o.tamabbohMissile(),View.MAP_SPR_TAMABBOH.range().nativeAddress(),View.MAP_SPR_TAMABBOH.range().nativeAddress()+22,View.MAP_SPR_TAMABBOH_MISSILE.range().nativeAddress(),View.ANI_TAMABBOH_NORMAL.range().nativeAddress(),View.ANI_TAMABBOH_DAMAGED.range().nativeAddress()));
        assertThrows(IllegalArgumentException.class,()->new CdSwing.Assets(tiles,o.view(View.SWING_MOTION),o.swing(),View.MAP_SPR_SWING.range().nativeAddress()));
        assertEquals(72,tiles.length());assertEquals(0x20ECEC,View.MAP_SPR_SWING.range().nativeAddress());
    }

    @Test void existingSectionArtBoundsAndHudRawOwnerAreReusedWithoutAnArtDecodeOrAnotherBank() throws Exception {
        var a=retail().assets();var o=fromAssets(a);var registry=a.sectionSources();var pointers=a.loadPlan().sourcePointers();assertEquals(37,registry.sources().size());
        assertSame(a.directArtSources().hudAssets(),o.hudAssets());assertSame(o.hudAssets(),o.hudAssets());
        assertEquals(14,registry.source(CdR12bSectionSources.SourceId.ART_RINGS).tileCount());assertEquals(36,registry.source(CdR12bSectionSources.SourceId.ART_SPRINGS).tileCount());
        assertEquals(57,registry.source(CdR12bSectionSources.SourceId.ART_PLATFORM).tileCount());assertEquals(23,registry.source(CdR12bSectionSources.SourceId.ART_ROBOT_TRANSPORT_B).tileCount());
        assertEquals(88,View.values().length);assertEquals(37,registry.sources().size());assertSame(pointers,a.loadPlan().sourcePointers());assertEquals(25,CdR12bProfile.Asset.values().length);
        assertEquals(63,o.hudVisuals().mappingFrames().stream().flatMap(f->f.pieces().stream()).mapToInt(p->p.tileOffset()+p.tileCount()).max().orElse(0));
    }

    @Test void literalIndexAliasesKeepTheActualIdOnlyObjNullAndNeverBecomeDeleteOrUnsupportedDispatch() throws Exception {
        var table=fromAssets(retail().assets()).view(View.OBJECT_INDEX);Set<Long> distinct=new HashSet<>();for(int i=0;i<64;i++)distinct.add(table.unsignedLong(i*4));assertEquals(51,distinct.size());
        assertEquals(table.unsignedLong(0),table.unsignedLong(4));for(int id:new int[]{0x1D,0x1E,0x25,0x27,0x2A,0x2B,0x2C,0x2F,0x30,0x31,0x37,0x38,0x39})assertEquals(0x203D4AL,table.unsignedLong((id-1)*4));
        assertEquals("10bc00004e75",HexFormat.of().formatHex(retail().original().readFile(0x3D4A,6)),"Actual MOVE.B0,(a0);RTS, not full-slot delete");
    }

    @Test void privilegedMalformedViewControlIsRejectedBeforeCachingAndAnOriginalRetryRemainsPure() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());
        when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        when(mock.objectView(View.ANI_RING)).thenReturn(CdAssetBytes.copyOf(new byte[8]),real.objectView(View.ANI_RING));
        var o=CdR12bObjectAssets.bind(mock);assertThrows(IllegalArgumentException.class,o::rings);assertNotNull(o.rings());assertSame(o.rings(),o.rings());
        verify(mock,times(2)).objectView(View.ANI_RING);
    }

    @Test void privilegedIdentityOnlyRejectionControlCannotSubstituteAnOriginalProgramHash() throws Exception {
        var real=retail().assets();var id=real.identity();var altered=new CdR12bTerrainAssets.Identity(id.variant(),id.stage(),id.file(),id.domain(),"0".repeat(64),id.observedWholeDiscSha256());
        var fake=mock(CdR12bTerrainAssets.class);when(fake.identity()).thenReturn(altered);when(fake.profile()).thenReturn(real.profile());
        assertThrows(IllegalArgumentException.class,()->CdR12bObjectAssets.bind(fake));verify(fake,never()).objectView(any());
    }

    @Test void metadataConstructionPreservesExistingTerrainDirectArtOldActAndPlacementValues() throws Exception {
        var f=retail();var a=f.assets();byte[] bank=new byte[766];for(int i=0;i<bank.length;i++)bank[i]=(byte)(i*53+0x5B);
        var p=CdObjectPlacementRuntime.fromAssets(a,CdAssetBytes.copyOf(bank));var cut=p.capture();var t=a.terrain();var plan=a.loadPlan();var direct=a.directArtSources();
        var old=f.oldAct().objects();byte[] oldTiles=old.view(CdR12aProfile.Asset.OBJECT_TILE_IDS).copy();var oldRing=old.rings();var o=fromAssets(a);
        for(var view:View.values())o.view(view);for(var c:VISUALS)CdR12bObjectAssets.class.getMethod(c.method()).invoke(o);o.hudAssets();o.ringDeltas();
        assertSame(t,a.terrain());assertSame(plan,a.loadPlan());assertSame(direct,a.directArtSources());assertSame(a.placements(),o.placements());assertSame(o.placements(),p.placements());
        assertEquals(cut.status(),p.status());assertEquals(cut.pool().slots(),p.pool().capture().slots());assertArrayEquals(cut.savedFlags().copy(),p.capture().savedFlags().copy());
        assertArrayEquals(oldTiles,old.view(CdR12aProfile.Asset.OBJECT_TILE_IDS).copy());assertSame(oldRing,old.rings());assertEquals(74,oldTiles.length);
        assertThrows(IllegalArgumentException.class,()->new CdRing(p,new CdRing.SourceData(o.ringDeltas(),View.MAP_SPR_RING.range().nativeAddress(),0xA7AE,o.rings())));
    }

    /** N: Act2Past Object Index:7 -> Present Index:34; Boulder/Main:26-59 and mapping include:1-7. */
    @Test void boulderIndexInitStoreCanonicalIncludeAndRealEvenMatchLiteralOriginalBytes() throws Exception {
        var f=retail();var o=fromAssets(f.assets());var view=View.MAP_SPR_BOULDER;
        assertEquals(39,view.ordinal());assertEquals(0x2093A0L,o.view(View.OBJECT_INDEX).unsignedLong((0x1B-1)*4));
        assertEquals("217c0020940a0004",HexFormat.of().formatHex(f.original().readFile(0x93CE,8)));
        assertEquals(0x20940A,view.range().nativeAddress());assertEquals(0x940A,view.range().fileOffset());
        assertEquals(13,view.canonicalRange().length());assertEquals(14,view.range().length());
        var raw=o.view(view);assertEquals("000202f8020000ecf00f0003f400",HexFormat.of().formatHex(raw.copy()));
        assertSame(raw,o.view(view));assertEquals(0,raw.unsignedByte(13));
        var v=o.boulder();assertSame(v,o.boulder());assertEquals(1,v.mappingFrames().size());assertTrue(v.animations().isEmpty());
        assertEquals(0,v.animationTable().length());assertEquals(14,v.mappings().length());assertEquals(0,v.mappingTableOffset());
        var frame=v.mappingFrames().getFirst();assertEquals(2,frame.relativeOffset());assertEquals(11,frame.bytes().length());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8,2,0,-20),new CdObjectVisualAssets.Piece(-16,15,3,-12)),frame.pieces());
        var tiles=o.view(View.OBJECT_TILE_IDS);assertEquals(72,tiles.length());assertEquals(0x3A,tiles.unsignedWord(11*2));assertEquals(0x035E,tiles.unsignedWord(0x3A));
        var source=f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_BOULDER);
        assertEquals(19,source.tileCount());assertEquals(19,frame.pieces().stream().mapToInt(p->p.tileOffset()+p.tileCount()).max().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->new CdBoulder.Assets(tiles,v,view.range().nativeAddress()),"legacy public Assets remains74-byte");
    }

    @Test void boulderCanonicalWithoutEvenIsCompleteWhileTruncatedBodyBadTailAndShortArtAreRejected() throws Exception {
        var map=fromAssets(retail().assets()).view(View.MAP_SPR_BOULDER).copy();
        var canonical=Arrays.copyOf(map,13);var decoded=CdObjectVisualAssets.decodeStatic(canonical,1,19);
        assertEquals(fromAssets(retail().assets()).boulder().mappingFrames().getFirst().pieces(),decoded.mappingFrames().getFirst().pieces());
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(Arrays.copyOf(map,12),1,19));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(map,1,18));
        var wrongTail=map.clone();wrongTail[13]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(wrongTail,1,19));
        var missingPiece=map.clone();missingPiece[2]=3;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(missingPiece,1,19));
    }

    @Test void boulderBadOriginalViewNeverCachesAndDoesNotChangeAnyHistoricalViewOrVisualCache() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());
        when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        var actual=real.objectView(View.MAP_SPR_BOULDER);var bad=actual.copy();bad[13]=1;
        when(mock.objectView(View.MAP_SPR_BOULDER)).thenReturn(CdAssetBytes.copyOf(bad),actual);
        var o=CdR12bObjectAssets.bind(mock);var oldViews=new ArrayList<CdAssetBytes>();for(int i=0;i<39;i++)oldViews.add(o.view(View.values()[i]));
        var oldVisuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<19;i++)oldVisuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertThrows(IllegalArgumentException.class,o::boulder);assertSame(actual,o.view(View.MAP_SPR_BOULDER));
        var boulder=o.boulder();assertSame(boulder,o.boulder());assertEquals(1,boulder.mappingFrames().size());verify(mock,times(2)).objectView(View.MAP_SPR_BOULDER);
        for(int i=0;i<39;i++)assertSame(oldViews.get(i),o.view(View.values()[i]));
        for(int i=0;i<19;i++)assertSame(oldVisuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertEquals(37,real.sectionSources().sources().size());assertEquals(72,o.view(View.OBJECT_TILE_IDS).length());
    }


    @Test void breakableWallIndexInitAndAllThreeReachedRawTablesUseTheActualSelectedR12bRoot() throws Exception {
        var f=retail();var o=fromAssets(f.assets());var map=View.MAP_SPR_BREAKABLE_WALL;
        assertEquals(40,map.ordinal());assertEquals(41,View.BREAKABLE_WALL_PIECE_FRAMES.ordinal());assertEquals(42,View.BREAKABLE_WALL_PIECE_DELTAS.ordinal());assertEquals(43,View.BREAKABLE_WALL_PIECE_SPEEDS.ordinal());
        assertEquals(0x20ED0AL,o.view(View.OBJECT_INDEX).unsignedLong((0x36-1)*4));assertEquals("217c0020ee640004",HexFormat.of().formatHex(f.original().readFile(0xED4C,8)));
        assertEquals("317c44be0002",HexFormat.of().formatHex(f.original().readFile(0xED46,6)));
        assertEquals("4bfa01f8",HexFormat.of().formatHex(f.original().readFile(0xEDA2,4)));assertEquals("49fa022a",HexFormat.of().formatHex(f.original().readFile(0xEDB0,4)));assertEquals("47fa023e",HexFormat.of().formatHex(f.original().readFile(0xEDB4,4)));
        assertEquals(0x20EE64,map.range().nativeAddress());assertEquals(0xEE64,map.range().fileOffset());assertEquals(312,map.canonicalRange().length());assertEquals(312,map.range().length());
        assertArrayEquals(f.original().readFile(0xF9F0,312),o.view(map).copy(),"Identical unused native copy is not the Init operand or a new admission");
        assertEquals(0x20EF9C,View.BREAKABLE_WALL_PIECE_FRAMES.range().nativeAddress());assertEquals(0x20EFDC,View.BREAKABLE_WALL_PIECE_DELTAS.range().nativeAddress());assertEquals(0x20EFF4,View.BREAKABLE_WALL_PIECE_SPEEDS.range().nativeAddress());
        var values=new CdBreakableWall.Assets(o.view(View.BREAKABLE_WALL_PIECE_FRAMES),o.view(View.BREAKABLE_WALL_PIECE_DELTAS),o.view(View.BREAKABLE_WALL_PIECE_SPEEDS),map.range().nativeAddress());
        assertEquals(64,values.pieceFrames().length());assertEquals(24,values.pieceDeltas().length());assertEquals(48,values.pieceSpeeds().length());assertEquals(0x20EE64,values.mappingsAddress());
        assertEquals(8,o.view(View.BREAKABLE_WALL_PIECE_FRAMES).unsignedByte(0),"No invented EVEN byte between even312 map and native piece-frame table");
        assertEquals(57,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_PLATFORM).tileCount());
    }

    @Test void breakableWallAllFourteenLiteralFramesRetainCompleteEvenSourceWithoutScriptsOrArtHydration() throws Exception {
        var o=fromAssets(retail().assets());var wall=o.breakableWall();assertSame(wall,o.breakableWall());assertEquals(312,wall.mappings().length());assertEquals(0,wall.mappingTableOffset());assertTrue(wall.animations().isEmpty());assertEquals(0,wall.animationTable().length());
        int[] offsets={28,59,90,121,152,183,214,245,276,282,288,294,300,306};
        List<List<CdObjectVisualAssets.Piece>> expected=List.of(
                List.of(new CdObjectVisualAssets.Piece(-24,5,41,-16),new CdObjectVisualAssets.Piece(-8,5,49,-16),new CdObjectVisualAssets.Piece(8,5,41,-16),new CdObjectVisualAssets.Piece(-24,5,45,0),new CdObjectVisualAssets.Piece(-8,5,53,0),new CdObjectVisualAssets.Piece(8,5,45,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,41,-16),new CdObjectVisualAssets.Piece(-8,5,49,-16),new CdObjectVisualAssets.Piece(8,5,41,-16),new CdObjectVisualAssets.Piece(-24,5,51,0),new CdObjectVisualAssets.Piece(-8,5,43,0),new CdObjectVisualAssets.Piece(8,5,51,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,51,-16),new CdObjectVisualAssets.Piece(-8,5,43,-16),new CdObjectVisualAssets.Piece(8,5,51,-16),new CdObjectVisualAssets.Piece(-24,5,51,0),new CdObjectVisualAssets.Piece(-8,5,43,0),new CdObjectVisualAssets.Piece(8,5,51,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,51,-16),new CdObjectVisualAssets.Piece(-8,5,43,-16),new CdObjectVisualAssets.Piece(8,5,51,-16),new CdObjectVisualAssets.Piece(-24,5,45,0),new CdObjectVisualAssets.Piece(-8,5,53,0),new CdObjectVisualAssets.Piece(8,5,45,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,49,-16),new CdObjectVisualAssets.Piece(-8,5,41,-16),new CdObjectVisualAssets.Piece(8,5,49,-16),new CdObjectVisualAssets.Piece(-24,5,53,0),new CdObjectVisualAssets.Piece(-8,5,45,0),new CdObjectVisualAssets.Piece(8,5,53,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,49,-16),new CdObjectVisualAssets.Piece(-8,5,41,-16),new CdObjectVisualAssets.Piece(8,5,49,-16),new CdObjectVisualAssets.Piece(-24,5,43,0),new CdObjectVisualAssets.Piece(-8,5,51,0),new CdObjectVisualAssets.Piece(8,5,43,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,43,-16),new CdObjectVisualAssets.Piece(-8,5,51,-16),new CdObjectVisualAssets.Piece(8,5,43,-16),new CdObjectVisualAssets.Piece(-24,5,43,0),new CdObjectVisualAssets.Piece(-8,5,51,0),new CdObjectVisualAssets.Piece(8,5,43,0)),
                List.of(new CdObjectVisualAssets.Piece(-24,5,43,-16),new CdObjectVisualAssets.Piece(-8,5,51,-16),new CdObjectVisualAssets.Piece(8,5,43,-16),new CdObjectVisualAssets.Piece(-24,5,53,0),new CdObjectVisualAssets.Piece(-8,5,45,0),new CdObjectVisualAssets.Piece(8,5,53,0)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,41,-8)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,49,-8)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,51,-8)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,43,-8)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,45,-8)),
                List.of(new CdObjectVisualAssets.Piece(-8,5,53,-8)));
        String[] hashes={"2c72d69ee4a5e78f5a101599d70e3aa48531db613393b7903a71a6cf293016c5","618545c0f7c8c66838f9617962a8ac057c817a3fa769add9575e35532cbcfc58","e563a8bf2e6c1586adb8e248cede34abc7effd76b6efa5d0c892e152d5be6a7e","74a75f5666dedf35aeb072774c5d2412a01e930ae595833bd9d5ef259ce3a7df","98302d0184179a35c3f58901f0a64f1beaf9c30643b67b13bb7786eda4f17f49","7772bbd7db35b149ca2cdf9b1de0ce975bb2c532726cbb8da259aabbf5201ded","1fcb8f0088eabdb3449aa0b458f14a3c557025df1f224255511e348781428654","ee9e75cd74ce9dc58acf122a0b2f941a3509aeda9a5d301ac1b107ac97b8a08d","52bb2505235d09159148a13d0c829e58c9caee8ec418632994b732783fcac8ba","98e56cebda01d6843b311cb7c4729d10da5b8ee167bf18e89d152cb24b98e2a2","18947d654324ed798a2bd7b5c3391db9ffb8f478cf3f310424bf9c2e5e07e67f","88c2f3b66d9d342c67e3942073eaf3a9c4e4bc8f67aabf8ca4312c5c856a9133","6a5899374c07cc07c79d0c613809a3e93f458bf6924a038c6e3ec06bc293c67e","592b6658f4db05dbce0675323084491ce6498c0b4a0891a36764ad5a759e5660"};
        for(int i=0;i<14;i++){var frame=wall.mappingFrames().get(i);assertEquals(offsets[i],frame.relativeOffset());assertEquals(i<8?6:1,frame.pieces().size());assertEquals(expected.get(i),frame.pieces());assertEquals(hashes[i],sha(frame.bytes().copy()));}
        assertEquals("0e6119e55720d42fd03ab30cbc410f8ab6c6c354520eee340836c2985eeeefd9",semanticDigest(wall));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(Arrays.copyOf(wall.mappings().copy(),311),14,57));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(wall.mappings().copy(),14,56));
        byte[] hull=new byte[376];System.arraycopy(wall.mappings().copy(),0,hull,0,312);System.arraycopy(o.view(View.BREAKABLE_WALL_PIECE_FRAMES).copy(),0,hull,312,64);
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(hull,14,57),"Piece tables cannot become an arbitrary mapping-source hull");
    }

    @Test void breakableWallBadViewCannotCacheOrAlterAnyOfThePreviousFortyViewsAndTwentyVisuals() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());
        when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        var actual=real.objectView(View.MAP_SPR_BREAKABLE_WALL);var bad=actual.copy();bad[0]^=1;when(mock.objectView(View.MAP_SPR_BREAKABLE_WALL)).thenReturn(CdAssetBytes.copyOf(bad),actual);
        var o=CdR12bObjectAssets.bind(mock);var views=new ArrayList<CdAssetBytes>();for(int i=0;i<40;i++)views.add(o.view(View.values()[i]));
        var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<20;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertThrows(IllegalArgumentException.class,o::breakableWall);assertSame(actual,o.view(View.MAP_SPR_BREAKABLE_WALL));var wall=o.breakableWall();assertSame(wall,o.breakableWall());verify(mock,times(2)).objectView(View.MAP_SPR_BREAKABLE_WALL);
        for(int i=0;i<40;i++)assertSame(views.get(i),o.view(View.values()[i]));for(int i=0;i<20;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
    }

    @Test void floatBlockIndexInitAndAnimationOperandsSelectOnlyTheActualR12bViews() throws Exception {
        var f=retail();var o=fromAssets(f.assets());var map=View.MAP_SPR_FLOAT_BLOCK;var ani=View.ANI_FLOAT_BLOCK;
        assertEquals(44,map.ordinal());assertEquals(45,ani.ordinal());assertEquals(0x20A03AL,o.view(View.OBJECT_INDEX).unsignedLong((0x12-1)*4));
        assertEquals("217c0020a1b00004",HexFormat.of().formatHex(f.original().readFile(0xA080,8)));
        assertEquals("70054eb90020cbde",HexFormat.of().formatHex(f.original().readFile(0xA088,8)));
        for(int at:new int[]{0xA122,0xA150,0xA164})assertEquals("43f90020a192",HexFormat.of().formatHex(f.original().readFile(at,6)));
        assertEquals(0x20A1B0,map.range().nativeAddress());assertEquals(0xA1B0,map.range().fileOffset());assertEquals(76,map.canonicalRange().length());assertEquals(76,map.range().length());
        assertEquals(0x20A192,ani.range().nativeAddress());assertEquals(0xA192,ani.range().fileOffset());assertEquals(30,ani.canonicalRange().length());assertEquals(30,ani.range().length());
        var ids=o.view(View.OBJECT_TILE_IDS);assertEquals(72,ids.length());assertEquals(46,(short)ids.unsignedWord(5*2));assertEquals(48,CdObjectTileIds.endOfBody(ids,5));assertEquals(0x434C,ids.unsignedWord(46));
        // Constructed finite DTO ingress only; no body/placement/current readiness is inferred.
        var values=new CdFloatBlock.Assets(ids,o.floatBlock(),map.range().nativeAddress());assertSame(ids,values.objectTileIds());assertSame(o.floatBlock(),values.visual());assertEquals(0x20A1B0,values.mappingsAddress());
        assertEquals(15,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_BLOCK).tileCount());assertEquals("800f",HexFormat.of().formatHex(f.original().readFile(0x3718C,2)));
    }

    @Test void floatBlockRetainsAllSixLiteralFramesThreeScriptsAndRealInternalEvenBytes() throws Exception {
        var o=fromAssets(retail().assets());var visual=o.floatBlock();assertSame(visual,o.floatBlock());assertEquals(0,visual.mappingTableOffset());assertEquals(76,visual.mappings().length());assertEquals(30,visual.animationTable().length());
        int[] offsets={12,14,30,46,58,70};List<List<CdObjectVisualAssets.Piece>> expected=List.of(
                List.of(),
                List.of(new CdObjectVisualAssets.Piece(-12,9,0,-12),new CdObjectVisualAssets.Piece(4,0,0,-4),new CdObjectVisualAssets.Piece(4,0,0,4)),
                List.of(new CdObjectVisualAssets.Piece(-12,9,2048,-12),new CdObjectVisualAssets.Piece(4,0,2048,-12),new CdObjectVisualAssets.Piece(4,0,2048,-4)),
                List.of(new CdObjectVisualAssets.Piece(-12,9,6144,-12),new CdObjectVisualAssets.Piece(4,0,6144,-4)),
                List.of(new CdObjectVisualAssets.Piece(-12,9,4096,-12),new CdObjectVisualAssets.Piece(4,0,4096,-4)),
                List.of(new CdObjectVisualAssets.Piece(-12,10,6,-12)));
        String[] hashes={"6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d","bdcb5b7a4d2533aa0576e5717ff18679869dbb1fe31aa02565f0be8930b327a6","adf8b15590c3cec4f93dd1518ce4b39fffbd9087e41c3d7573d08fe366211ba5","f40feae5be9d68e59b4d09ac1c78b7cde7d56b5d8b0fb7df48bb8215b32fe04f","4c89b88827636f8bbf2be115fa5ad05614066b5448ed4816a0ea86624a94ec31","940c3d1fcafcf760ab0df2d44c17c7a1d20e350f7b2f6863262034740427555a"};
        for(int i=0;i<6;i++){var frame=visual.mappingFrames().get(i);assertEquals(offsets[i],frame.relativeOffset());assertEquals(expected.get(i),frame.pieces());assertEquals(hashes[i],sha(frame.bytes().copy()));}
        assertEquals("65c5694bceb7a6beb494174e661f380f2b14a1e8578efd7631c62757105f61b8",semanticDigest(visual));
        assertTrue(visual.mappingFrames().getFirst().pieces().isEmpty());for(int at:new int[]{13,57,69})assertEquals(0,visual.mappings().unsignedByte(at),"Real internal EVEN byte");
        assertEquals("0006000a00140205ff00020105020503050405fc020100020003000400fc",HexFormat.of().formatHex(visual.animationTable().copy()));
        int[] scripts={6,10,20};var frameBytes=List.of(List.of(5),List.of(1,5,2,5,3,5,4,5),List.of(1,0,2,0,3,0,4,0));
        for(int i=0;i<3;i++){var script=visual.animations().get(i);assertEquals(scripts[i],script.relativeOffset());assertEquals(2,script.delay());assertEquals(frameBytes.get(i),script.frameBytes());
            assertEquals(i==0?CdObjectVisualAssets.Terminal.LOOP_FF:CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,script.terminal());assertEquals(i==0?4:10,script.bytes().length());}
        assertEquals(0,visual.animationTable().unsignedByte(9),"Real Invisible script EVEN byte");
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(Arrays.copyOf(visual.mappings().copy(),75),6,visual.animationTable().copy(),3,15));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(visual.mappings().copy(),6,Arrays.copyOf(visual.animationTable().copy(),29),3,15));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(visual.mappings().copy(),6,visual.animationTable().copy(),3,14));
        byte[] badEven=visual.mappings().copy();badEven[13]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(badEven,6,visual.animationTable().copy(),3,15));
    }

    @Test void floatBlockScriptFaultCannotCacheOrAlterAnyEarlierFortyFourViewsOrTwentyOneVisuals() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());
        when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        var actual=real.objectView(View.ANI_FLOAT_BLOCK);var bad=actual.copy();bad[6]^=1;when(mock.objectView(View.ANI_FLOAT_BLOCK)).thenReturn(CdAssetBytes.copyOf(bad),actual);
        var o=CdR12bObjectAssets.bind(mock);var views=new ArrayList<CdAssetBytes>();for(int i=0;i<44;i++)views.add(o.view(View.values()[i]));
        var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<21;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertThrows(IllegalArgumentException.class,o::floatBlock);assertSame(actual,o.view(View.ANI_FLOAT_BLOCK));var visual=o.floatBlock();assertSame(visual,o.floatBlock());verify(mock,times(2)).objectView(View.ANI_FLOAT_BLOCK);
        for(int i=0;i<44;i++)assertSame(views.get(i),o.view(View.values()[i]));for(int i=0;i<21;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
    }

    @Test void spikesAndLogShadowUseTheActualR12bInitOperandsAndCompleteNativeBaseWordTable() throws Exception {
        var f=retail();var o=fromAssets(f.assets());assertEquals(46,View.MAP_SPR_SPIKES.ordinal());assertEquals(47,View.MAP_SPR_LOG_SHADOW.ordinal());assertEquals(48,View.LOG_SHADOW_BASE_TILES.ordinal());
        assertEquals(0x20BED0L,o.view(View.OBJECT_INDEX).unsignedLong((0x26-1)*4));assertEquals(0x208716L,o.view(View.OBJECT_INDEX).unsignedLong((0x33-1)*4));
        assertEquals("217c0020bfa60004",HexFormat.of().formatHex(f.original().readFile(0xBEE6,8)));assertEquals("700a4eba0ce0",HexFormat.of().formatHex(f.original().readFile(0xBEFA,6)));
        assertEquals("217c002087a40004",HexFormat.of().formatHex(f.original().readFile(0x8744,8)));assertEquals("317b000c0002",HexFormat.of().formatHex(f.original().readFile(0x8786,6)));
        // Literal native period-mask/future-good/act read sequence; this data test never executes the body.
        assertEquals("7000103900ff152e0200007f0c0000026606d03900ff156ad040d03900ff1507d040317b000c00020068400000024e75",HexFormat.of().formatHex(f.original().readFile(0x8764,48)));
        assertEquals(0x20BFA6,View.MAP_SPR_SPIKES.range().nativeAddress());assertEquals(24,o.view(View.MAP_SPR_SPIKES).length());
        assertEquals(0x2087A4,View.MAP_SPR_LOG_SHADOW.range().nativeAddress());assertEquals(16,o.view(View.MAP_SPR_LOG_SHADOW).length());
        assertEquals(0x208794,View.LOG_SHADOW_BASE_TILES.range().nativeAddress());var bases=o.view(View.LOG_SHADOW_BASE_TILES);assertEquals(16,bases.length());
        int[] expected={0x03CB,0x045E,0x0418,0x03F0,0x0428,0x038F,0x0428,0x037F};for(int i=0;i<8;i++)assertEquals(expected[i],bases.unsignedWord(i*2));
        var ids=o.view(View.OBJECT_TILE_IDS);assertEquals(72,ids.length());assertEquals(0x30,(short)ids.unsignedWord(10*2));assertEquals(0x32,CdObjectTileIds.endOfBody(ids,10));assertEquals(0x0330,ids.unsignedWord(0x30));
        // Constructed finite value ingress only; body admission is a separate prerequisite.
        var values=new CdLogShadow.Assets(bases,o.logShadow(),View.MAP_SPR_LOG_SHADOW.range().nativeAddress());assertSame(bases,values.baseTileWords());assertSame(o.logShadow(),values.visual());assertEquals(0x2087A4,values.mappingsAddressBits());
        assertEquals(8,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_SPIKES_V2).tileCount());assertEquals(16,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_LOG_SHADOW).tileCount());
        assertEquals("8008",HexFormat.of().formatHex(f.original().readFile(0x38BD4,2)));assertEquals("0010",HexFormat.of().formatHex(f.original().readFile(0x36A7C,2)));
    }

    @Test void spikesAndLogShadowKeepAllLiteralStaticFramesAndNativeSourceTailsWithoutScripts() throws Exception {
        var o=fromAssets(retail().assets());var spikes=o.spikes();var shadow=o.logShadow();assertSame(spikes,o.spikes());assertSame(shadow,o.logShadow());assertTrue(spikes.animations().isEmpty());assertTrue(shadow.animations().isEmpty());assertEquals(0,spikes.animationTable().length());assertEquals(0,shadow.animationTable().length());
        assertEquals(24,spikes.mappings().length());assertEquals(16,shadow.mappings().length());assertEquals(0,spikes.mappingTableOffset());assertEquals(0,shadow.mappingTableOffset());
        var spikeFrame=spikes.mappingFrames().getFirst();assertEquals(2,spikeFrame.relativeOffset());assertEquals(21,spikeFrame.bytes().length());assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,5,0,-16),new CdObjectVisualAssets.Piece(0,5,4,-16),new CdObjectVisualAssets.Piece(-16,5,0,0),new CdObjectVisualAssets.Piece(0,5,4,0)),spikeFrame.pieces());
        assertEquals("15e618f5e1de5906a2068dc25b3ad67824697845d1cce4a9646475620fb6a5fc",sha(spikeFrame.bytes().copy()));assertEquals(0,spikes.mappings().unsignedByte(23),"Real explicit include byte, not fabricated parent padding");
        assertEquals(4,shadow.mappingFrames().get(0).relativeOffset());assertEquals(10,shadow.mappingFrames().get(1).relativeOffset());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,15,0,-16)),shadow.mappingFrames().get(0).pieces());assertEquals(List.of(new CdObjectVisualAssets.Piece(-8,13,0,-16)),shadow.mappingFrames().get(1).pieces());
        assertEquals("9ba739f1090e886bd2b6fc335a265b364efc035b84e9e254d414b13bf28abb04",sha(shadow.mappingFrames().get(0).bytes().copy()));assertEquals("cc9c1a07b2640e191c21b4519a5b600c4a4ace7258c6584e217992d7e4fcefa7",sha(shadow.mappingFrames().get(1).bytes().copy()));
        assertEquals("19bfd226ca0d6adf8dfefef89eed1fb6cf4b7e4c279b68255a2ff726468a1c14",semanticDigest(spikes));assertEquals("4832eeeee66550838053053d5b721a2ff138dc8134d9768b96939f3eac008e11",semanticDigest(shadow));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(Arrays.copyOf(spikes.mappings().copy(),22),1,8));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(spikes.mappings().copy(),1,7));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(Arrays.copyOf(shadow.mappings().copy(),15),2,16));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(shadow.mappings().copy(),2,15));
        byte[] badTail=spikes.mappings().copy();badTail[23]=1;assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(badTail,1,8));
    }

    @Test void spikesAndLogShadowSourceFaultsCannotCacheOrAlterThePreviousFortySixViewsAndTwentyTwoVisuals() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());
        when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        var spike=real.objectView(View.MAP_SPR_SPIKES);var shadow=real.objectView(View.MAP_SPR_LOG_SHADOW);var bases=real.objectView(View.LOG_SHADOW_BASE_TILES);
        byte[] badShadow=shadow.copy();badShadow[0]^=1;byte[] badBases=bases.copy();badBases[0]^=1;
        when(mock.objectView(View.MAP_SPR_SPIKES)).thenReturn(CdAssetBytes.copyOf(Arrays.copyOf(spike.copy(),23)),spike);when(mock.objectView(View.MAP_SPR_LOG_SHADOW)).thenReturn(CdAssetBytes.copyOf(badShadow),shadow);when(mock.objectView(View.LOG_SHADOW_BASE_TILES)).thenReturn(CdAssetBytes.copyOf(badBases),bases);
        var o=CdR12bObjectAssets.bind(mock);var views=new ArrayList<CdAssetBytes>();for(int i=0;i<46;i++)views.add(o.view(View.values()[i]));
        var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<22;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertThrows(IllegalArgumentException.class,o::spikes);assertSame(spike,o.view(View.MAP_SPR_SPIKES));assertSame(o.spikes(),o.spikes());
        assertThrows(IllegalArgumentException.class,o::logShadow);assertSame(shadow,o.view(View.MAP_SPR_LOG_SHADOW));assertSame(o.logShadow(),o.logShadow());
        assertThrows(IllegalArgumentException.class,()->o.view(View.LOG_SHADOW_BASE_TILES));assertSame(bases,o.view(View.LOG_SHADOW_BASE_TILES));assertSame(bases,o.view(View.LOG_SHADOW_BASE_TILES));
        for(var view:List.of(View.MAP_SPR_SPIKES,View.MAP_SPR_LOG_SHADOW,View.LOG_SHADOW_BASE_TILES))verify(mock,times(2)).objectView(view);
        for(int i=0;i<46;i++)assertSame(views.get(i),o.view(View.values()[i]));for(int i=0;i<22;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
    }

    @Test void pataBataLiteralRootsSharedScriptsAndFiftyTileHullPreserveTheOriginalFortyNineViews() throws Exception {
        var o=fromAssets(retail().assets());assertEquals(49,View.MAP_SPR_PATA_BATA_NORMAL.ordinal());assertEquals(50,View.MAP_SPR_PATA_BATA_DAMAGED.ordinal());assertEquals(51,View.ANI_PATA_BATA.ordinal());
        var n=o.pataBataNormal();var d=o.pataBataDamaged();assertSame(n,o.pataBataNormal());assertSame(d,o.pataBataDamaged());assertNotSame(n,d);
        assertEquals(0x20B1D0L,o.view(View.OBJECT_INDEX).unsignedLong((0x3E-1)*4));assertEquals(50,retail().assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_PATA_BATA).tileCount());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,7,0,-16),new CdObjectVisualAssets.Piece(-16,7,8,0)),n.mappingFrames().get(0).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-8,13,16,-16),new CdObjectVisualAssets.Piece(8,0,24,0)),n.mappingFrames().get(1).pieces());assertEquals(n.mappingFrames().get(1).pieces(),d.mappingFrames().get(1).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,15,25,-16)),n.mappingFrames().get(2).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(-16,7,0,-16),new CdObjectVisualAssets.Piece(-16,7,41,0)),d.mappingFrames().get(0).pieces());
        assertEquals(List.of(new CdObjectVisualAssets.Piece(0,0,49,0),new CdObjectVisualAssets.Piece(-16,15,25,-16)),d.mappingFrames().get(2).pieces());
        assertArrayEquals(o.view(View.ANI_PATA_BATA).copy(),n.animationTable().copy());assertArrayEquals(n.animationTable().copy(),d.animationTable().copy());assertEquals(2,n.animations().size());assertEquals(4,n.animations().get(0).relativeOffset());assertEquals(11,n.animations().get(1).relativeOffset());
        assertEquals("070000010201ff",HexFormat.of().formatHex(n.animations().get(0).bytes().copy()));assertEquals("030000010201ff",HexFormat.of().formatHex(n.animations().get(1).bytes().copy()));assertEquals(0,d.mappings().unsignedByte(39));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(n.mappings().copy(),3,n.animationTable().copy(),2,40));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(d.mappings().copy(),3,d.animationTable().copy(),2,49));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(Arrays.copyOf(d.mappings().copy(),38),3,d.animationTable().copy(),2,50));
    }
    @Test void pataBataBadSourceIsNotCachedAndCannotReplaceAnyEarlierMetadataIdentity() throws Exception {
        var real=retail().assets();var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));
        var actual=real.objectView(View.ANI_PATA_BATA);var bad=actual.copy();bad[6]^=1;when(mock.objectView(View.ANI_PATA_BATA)).thenReturn(CdAssetBytes.copyOf(bad),actual);
        var o=CdR12bObjectAssets.bind(mock);var old=new ArrayList<CdAssetBytes>();for(int i=0;i<49;i++)old.add(o.view(View.values()[i]));var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<24;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        assertThrows(IllegalArgumentException.class,o::pataBataNormal);assertSame(actual,o.view(View.ANI_PATA_BATA));assertSame(o.pataBataNormal(),o.pataBataNormal());assertSame(o.pataBataDamaged(),o.pataBataDamaged());verify(mock,times(2)).objectView(View.ANI_PATA_BATA);
        for(int i=0;i<49;i++)assertSame(old.get(i),o.view(View.values()[i]));for(int i=0;i<24;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
    }

    @Test void effectsAndMosquiHaveOnlyTheAuthenticNativeRootsAndCompleteSourceExtents()throws Exception{
        var f=retail();var o=fromAssets(f.assets());int[] offsets={0x1D714,0x1D700,0x36AB0,0x36A94,0xAA1A,0xB49C,0xB4D8,0xB480,0xB48E};int[] canonical={146,20,90,28,82,60,66,14,13};int[] reads={146,20,90,28,82,60,66,14,14};
        for(int i=0;i<9;i++){var v=View.values()[52+i];assertEquals(offsets[i],v.range().fileOffset());assertEquals(canonical[i],v.canonicalRange().length());assertEquals(reads[i],o.view(v).length());}
        // Both original Flower includes occur twice; only these actual Init/LEA operands select the admitted pair.
        assertEquals("217c00236ab00004",HexFormat.of().formatHex(f.original().readFile(0x5F4C,8)));assertEquals("43f900236a94",HexFormat.of().formatHex(f.original().readFile(0x5FD4,6)));
        assertArrayEquals(f.original().readFile(0x1DF92,90),o.view(View.MAP_SPR_FLOWER).copy());assertArrayEquals(f.original().readFile(0x1DF76,28),o.view(View.ANI_FLOWER).copy());assertNotEquals(0x21DF92,View.MAP_SPR_FLOWER.range().nativeAddress());
        assertEquals(70,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_EXPLOSIONS).tileCount());assertEquals(39,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_FLOWER).tileCount());assertEquals(5,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_POINTS).tileCount());assertEquals(70,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_MOSQUI).tileCount());
    }
    @Test void allNewScriptDelaysFramesAndRealAlignmentBytesMatchNativeLiterals()throws Exception{
        var o=fromAssets(retail().assets());assertArrayEquals(HexFormat.of().parseHex("0004000c030005060304fc00030001020304fc00"),o.view(View.ANI_EXPLOSION).copy());assertArrayEquals(HexFormat.of().parseHex("0008000c00120018030001ff0302030203fc0105050406fc130607ff"),o.view(View.ANI_FLOWER).copy());
        assertArrayEquals(HexFormat.of().parseHex("00040008010001ff06020304fc00"),o.view(View.ANI_MOSQUI_NORMAL).copy());assertArrayEquals(HexFormat.of().parseHex("00040008040001ff0e020304fc00"),o.view(View.ANI_MOSQUI_DAMAGED).copy());
        assertEquals(List.of(3,3),o.explosion().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());assertEquals(List.of(3,3,1,19),o.flower().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());assertEquals(List.of(1,6),o.normalMosqui().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());assertEquals(List.of(4,14),o.damagedMosqui().animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());
        assertEquals(List.of(0,5,6,3,4),o.explosion().animations().getFirst().frameBytes());assertEquals(List.of(5,5,4,6),o.flower().animations().get(2).frameBytes());assertEquals(List.of(2,3,4),o.normalMosqui().animations().get(1).frameBytes());assertEquals(List.of(2,3,4),o.damagedMosqui().animations().get(1).frameBytes());
        assertEquals(CdObjectVisualAssets.Terminal.ADVANCE_ROUTINE_FC,o.explosion().animations().getFirst().terminal());assertEquals(CdObjectVisualAssets.Terminal.LOOP_FF,o.flower().animations().getFirst().terminal());assertEquals(0,o.view(View.MAP_SPR_EXPLOSION).unsignedByte(145));assertEquals(0,o.view(View.MAP_SPR_MOSQUI_DAMAGED).unsignedByte(65));assertEquals(0,o.view(View.ANI_MOSQUI_DAMAGED).unsignedByte(13));assertEquals(14,o.damagedMosqui().animationTable().length());
    }
    @Test void sharedBodyAssetFormatsReuseRingAndMonitorRootsWithoutRelaxingMosqui74Contract()throws Exception{
        var o=fromAssets(retail().assets());assertSame(o.explosion(),new CdExplosion.Assets(o.explosion(),0x21D714).explosion());assertSame(o.flower(),new CdFlower.Assets(o.flower(),0x236AB0).flower());assertSame(o.points(),new CdPoints.Assets(o.points(),0x20AA1A).points());
        var lost=new CdLostRing.Assets(o.rings(),View.MAP_SPR_RING.range().nativeAddress(),0xA7AE);assertSame(o.rings(),lost.rings());assertEquals(0x209EB0,lost.mappingsAddress());assertEquals(0xA7AE,lost.baseTileWord());
        var monitor=CdMonitor.Assets.fromAct2PastAssets(o);assertSame(o.monitorTime(),monitor.visual());assertEquals(8,CdObjectMappings.act2PastMonitorItemStaticPieces(o).size());assertEquals(72,o.view(View.OBJECT_TILE_IDS).length());assertEquals(0x24,o.view(View.OBJECT_TILE_IDS).unsignedWord(0));assertEquals(0x23EA,o.view(View.OBJECT_TILE_IDS).unsignedWord(0x24));
        assertThrows(IllegalArgumentException.class,()->new CdMosqui.Assets(o.view(View.OBJECT_TILE_IDS),o.normalMosqui(),o.damagedMosqui(),0x20B49C,0x20B480,0x20B4D8,0x20B48E));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(View.MAP_SPR_EXPLOSION).copy(),7,o.view(View.ANI_EXPLOSION).copy(),2,69));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(o.view(View.MAP_SPR_FLOWER).copy(),8,o.view(View.ANI_FLOWER).copy(),4,38));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(o.view(View.MAP_SPR_POINTS).copy(),6,4));
    }
    @Test void everyNewRawViewFaultRetainsAllFiftyTwoPriorViewAndTwentySixVisualIdentities()throws Exception{
        var real=retail().assets();for(int target=52;target<61;target++){var source=View.values()[target];var actual=real.objectView(source);byte[] bad=actual.copy();bad[0]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));when(mock.objectView(source)).thenReturn(CdAssetBytes.copyOf(bad),actual);
            var o=CdR12bObjectAssets.bind(mock);var prior=new ArrayList<CdAssetBytes>();for(int i=0;i<52;i++)prior.add(o.view(View.values()[i]));var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<26;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
            assertThrows(IllegalArgumentException.class,()->o.view(source));assertSame(actual,o.view(source));assertSame(actual,o.view(source));verify(mock,times(2)).objectView(source);for(int i=0;i<52;i++)assertSame(prior.get(i),o.view(View.values()[i]));for(int i=0;i<26;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));
        }
    }
    @Test void eachNewVisualFailureCannotPublishItsCacheBeforeTheActualSourceValidates()throws Exception{
        var real=retail().assets();for(int target=26;target<31;target++){var c=VISUALS.get(target);var source=c.script()==null?c.map():c.script();var actual=real.objectView(source);byte[] bad=actual.copy();bad[bad.length-1]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(call->real.objectView(call.getArgument(0,View.class)));when(mock.objectView(source)).thenReturn(CdAssetBytes.copyOf(bad),actual);
            var o=CdR12bObjectAssets.bind(mock);var method=CdR12bObjectAssets.class.getMethod(c.method());var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(o));assertInstanceOf(IllegalArgumentException.class,error.getCause());var field=CdR12bObjectAssets.class.getDeclaredField("visuals");field.setAccessible(true);assertNull(((CdObjectVisualAssets[])field.get(o))[target]);var first=method.invoke(o);assertSame(first,method.invoke(o));verify(mock,times(2)).objectView(source);
        }
    }

    @Test void projectorSceneryAndSpringboardUseOriginalSelectedPointersAndAllEightFiniteSpans()throws Exception{
        var f=retail();var o=fromAssets(f.assets());int[] offsets={0xE9F0,0xE9AC,0xEA34,0x86D8,0x86CC,0xBE7C,0xBE46,0xBB78};int[] lengths={68,68,38,62,12,84,54,32};for(int i=0;i<8;i++){var source=View.values()[61+i];assertEquals(offsets[i],source.range().fileOffset());assertEquals(lengths[i],source.canonicalRange().length());assertEquals(lengths[i],o.view(source).length());}
        assertEquals(0x20E716L,o.view(View.OBJECT_INDEX).unsignedLong((0x2E-1)*4));assertEquals(0x208652L,o.view(View.OBJECT_INDEX).unsignedLong((0x32-1)*4));assertEquals(0x20BB98L,o.view(View.OBJECT_INDEX).unsignedLong((0x28-1)*4));
        assertEquals("217c0020e9f00004",HexFormat.of().formatHex(f.original().readFile(0xE7AE,8)));assertEquals("217c0020e9f00004",HexFormat.of().formatHex(f.original().readFile(0xE972,8)));assertEquals("217c0020ea34002c",HexFormat.of().formatHex(f.original().readFile(0xE7B6,8)));assertEquals("45f90020bb780240",HexFormat.of().formatHex(f.original().readFile(0xBAEC,8)));
    }
    @Test void projectorKeepsAllSixCountedFramesIncludingTheRealEmptyFlickerAndOriginalSchedule()throws Exception{
        var o=fromAssets(retail().assets());var v=o.projector();assertSame(v,o.projector());assertEquals(List.of(12,18,19,25,31,47),v.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());assertEquals(List.of(1,0,1,1,3,4),v.mappingFrames().stream().map(f->f.pieces().size()).toList());assertArrayEquals(new byte[]{0},v.mappingFrames().get(1).bytes().copy());assertEquals(List.of(0,0),v.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());assertEquals(List.of(2,1,3,1),v.animations().getFirst().frameBytes());assertEquals(56,v.animations().get(1).frameBytes().size());assertTrue(v.animations().stream().allMatch(a->a.terminal()==CdObjectVisualAssets.Terminal.LOOP_FF));
        var schedule=o.view(View.PROJECTOR_EXPLOSION_SCHEDULE);assertArrayEquals(HexFormat.of().parseHex("01000005eef60af60a0f00ee14f612160817190df61cfde71e0a1420f602230df628f60aff00"),schedule.copy());int[] ticks={1,5,10,15,20,22,25,28,30,32,35,40};for(int i=0;i<12;i++)assertEquals(ticks[i],schedule.unsignedByte(i*3));assertEquals(-18,schedule.signedByte(4));assertEquals(-25,schedule.signedByte(23));assertEquals(255,schedule.unsignedByte(36));assertEquals(0,schedule.unsignedByte(37));assertThrows(IndexOutOfBoundsException.class,()->schedule.unsignedByte(38));
        assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(v.mappings().copy(),6,v.animationTable().copy(),2,50));
    }
    @Test void sceneryPreservesThreeCompleteNativeFramesAndThePastSeventeenTileSource()throws Exception{
        var o=fromAssets(retail().assets());var v=o.scenery();assertSame(v,o.scenery());assertEquals(List.of(6,18,40),v.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());assertEquals(List.of(2,4,4),v.mappingFrames().stream().map(f->f.pieces().size()).toList());assertTrue(v.animations().isEmpty());assertEquals(0,v.animationTable().length());assertEquals(62,v.mappings().length());assertEquals(0,v.mappings().unsignedByte(61));
        assertArrayEquals(HexFormat.of().parseHex("03db046e0438039f0438038f"),o.view(View.SCENERY_BASE_TILES).copy());assertEquals(0x46E,o.view(View.SCENERY_BASE_TILES).unsignedWord(2));assertEquals(17,retail().assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_SCENERY_B).tileCount());assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decodeStatic(v.mappings().copy(),3,16));
    }
    @Test void springboardPreservesSixFramesFiveScriptsAndEverySignedHitboxByte()throws Exception{
        var o=fromAssets(retail().assets());var v=o.springboard();assertSame(v,o.springboard());assertEquals(List.of(12,24,36,48,60,72),v.mappingFrames().stream().map(CdObjectVisualAssets.MappingFrame::relativeOffset).toList());assertTrue(v.mappingFrames().stream().allMatch(f->f.pieces().size()==2));assertEquals(List.of(2,2,2,0,0),v.animations().stream().map(CdObjectVisualAssets.AnimationScript::delay).toList());assertTrue(v.animations().stream().allMatch(a->a.terminal()==CdObjectVisualAssets.Terminal.LOOP_FF));assertEquals(List.of(0,1,0,2,0,1,0,3,4,3,5,3,4,3),v.animations().getFirst().frameBytes());assertEquals(List.of(0),v.animations().get(3).frameBytes());assertEquals(List.of(3),v.animations().get(4).frameBytes());
        assertArrayEquals(HexFormat.of().parseHex("10f010f010f004fc09f7381000e804fc00e80c00180004fc18000c0020e020e0"),o.view(View.SPRINGBOARD_HITBOXES).copy());assertEquals(-24,o.view(View.SPRINGBOARD_HITBOXES).signedByte(13));assertEquals(56,o.view(View.SPRINGBOARD_HITBOXES).unsignedByte(10));assertThrows(IndexOutOfBoundsException.class,()->o.view(View.SPRINGBOARD_HITBOXES).unsignedByte(32));assertEquals(0x32,o.view(View.OBJECT_TILE_IDS).unsignedWord(14));assertEquals(0x041E,o.view(View.OBJECT_TILE_IDS).unsignedWord(0x32));assertThrows(IllegalArgumentException.class,()->CdObjectVisualAssets.decode(v.mappings().copy(),6,v.animationTable().copy(),5,20));
    }
    @Test void onlyExistingGenuineArtAliasesAndTwoExistingAnimalMappingFamiliesAreReused()throws Exception{
        var a=retail().assets();var o=fromAssets(a);var registry=a.sectionSources();assertEquals(37,registry.sources().size());var hologram=registry.source(CdR12bSectionSources.SourceId.ART_HOLOGRAM);var regular=registry.source(CdR12bSectionSources.SourceId.ART_ANIMALS);var projected=registry.source(CdR12bSectionSources.SourceId.ART_HOLOGRAM_ANIMALS);assertEquals(51,hologram.tileCount());assertEquals(20,regular.tileCount());assertEquals(20,projected.tileCount());assertEquals(21,registry.source(CdR12bSectionSources.SourceId.ART_SPRINGBOARD).tileCount());
        var load=a.loadPlan().plcs().get(5).entries();var restore=a.loadPlan().plcs().get(4).entries();assertEquals(1,load.size());assertEquals(1,restore.size());assertEquals(projected.nativeAddress(),load.getFirst().sourceNativeAddress());assertEquals(regular.nativeAddress(),restore.getFirst().sourceNativeAddress());assertEquals(0x9EE0,load.getFirst().vramAddress());assertEquals(0x9EE0,restore.getFirst().vramAddress());assertTrue(a.loadPlan().plcs().get(9).entries().stream().anyMatch(e->e.sourceNativeAddress()==hologram.nativeAddress()&&e.vramAddress()==0x75E0));
        assertEquals(3,o.flyingAnimal().mappingFrames().size());assertEquals(5,o.groundAnimal().mappingFrames().size());assertArrayEquals(o.view(View.MAP_SPR_FLYING_ANIMAL).copy(),o.flyingAnimal().mappings().copy());assertArrayEquals(o.view(View.MAP_SPR_GROUND_ANIMAL).copy(),o.groundAnimal().mappings().copy());assertSame(o.flyingAnimal(),o.flyingAnimal());assertSame(o.groundAnimal(),o.groundAnimal());
    }
    @Test void newViewAndVisualFailuresCannotReplaceAnyOfThePriorSixtyOneViewsOrThirtyOneVisuals()throws Exception{
        var real=retail().assets();for(int target=61;target<69;target++){var source=View.values()[target];var actual=real.objectView(source);byte[] bad=actual.copy();bad[0]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));when(mock.objectView(source)).thenReturn(CdAssetBytes.copyOf(bad),actual);var o=CdR12bObjectAssets.bind(mock);var old=new ArrayList<CdAssetBytes>();for(int i=0;i<61;i++)old.add(o.view(View.values()[i]));var visuals=new ArrayList<CdObjectVisualAssets>();for(int i=0;i<31;i++)visuals.add((CdObjectVisualAssets)CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));assertThrows(IllegalArgumentException.class,()->o.view(source));assertSame(actual,o.view(source));verify(mock,times(2)).objectView(source);for(int i=0;i<61;i++)assertSame(old.get(i),o.view(View.values()[i]));for(int i=0;i<31;i++)assertSame(visuals.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));}
        for(int target=31;target<34;target++){var c=VISUALS.get(target);var source=c.script()==null?c.map():c.script();var actual=real.objectView(source);byte[] bad=actual.copy();bad[bad.length-1]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(call->real.objectView(call.getArgument(0,View.class)));when(mock.objectView(source)).thenReturn(CdAssetBytes.copyOf(bad),actual);var o=CdR12bObjectAssets.bind(mock);var method=CdR12bObjectAssets.class.getMethod(c.method());assertInstanceOf(IllegalArgumentException.class,assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(o)).getCause());var field=CdR12bObjectAssets.class.getDeclaredField("visuals");field.setAccessible(true);assertNull(((CdObjectVisualAssets[])field.get(o))[target]);assertSame(method.invoke(o),method.invoke(o));verify(mock,times(2)).objectView(source);}
    }

    @Test void remainingRouteViewsUseOnlyReachedOriginalOperandsAndCompleteNativeIncludes()throws Exception{
        var o=fromAssets(retail().assets());var original=retail().original();String[] names={"MAP_SPR_SPINNING_DISC","ANI_SPINNING_DISC","SPINNING_DISC_PLAYER_FRAMES","MAP_SPR_TUNNEL_DOOR","ANI_TUNNEL_DOOR","MAP_SPR_POWERUP","MAP_SPR_TUNNEL_DOOR_VERTICAL","MAP_SPR_BIG_RING","ANI_BIG_RING","MAP_SPR_BIG_RING_FLASH","ANI_BIG_RING_FLASH","MAP_SPR_GOAL_SIGNPOST","ANI_SIGNPOST","GOAL_POST_BASE_TILES","LEVEL_END_TIME_BONUSES"};
        for(int i=0;i<names.length;i++){var v=View.valueOf(names[i]);assertEquals(69+i,v.ordinal());assertEquals(v.canonicalRange(),v.range());assertArrayEquals(original.readFile(v.range().fileOffset(),v.range().length()),o.view(v).copy());}
        assertEquals("00000001010202020303030404050505",HexFormat.of().formatHex(o.view(View.SPINNING_DISC_PLAYER_FRAMES).copy()));
        assertEquals("0700010203ff",HexFormat.of().formatHex(o.view(View.ANI_BIG_RING).slice(2,6).copy()));assertEquals("020001020304050607fc",HexFormat.of().formatHex(o.view(View.ANI_BIG_RING_FLASH).slice(2,10).copy()));
        assertEquals(0x209374,View.ANI_SPINNING_DISC.range().nativeAddress());assertEquals(0x21D6B6,View.ANI_TUNNEL_DOOR.range().nativeAddress());assertEquals(0x21D528,View.MAP_SPR_POWERUP.range().nativeAddress());
    }
    @Test void endRawTablesRetainAllSourceWordsAndReachedPastActTwoSelectorWithoutCurrentPolicy()throws Exception{
        var o=fromAssets(retail().assets());var base=o.view(View.GOAL_POST_BASE_TILES);assertEquals(112,base.length());int[] pp={0x35A,0x4F7,0x4F7,0x4F7,0x381,0x4F7,0x4F7,0x4F7};for(int i=0;i<pp.length;i++)assertEquals(pp[i],base.unsignedWord(2*i));assertEquals(0x381,base.unsignedWord(8));
        int[] bonus={50000,50000,10000,5000,4000,4000,3000,3000,2000,2000,2000,2000,1000,1000,1000,1000,500,500,500,500,0};var table=o.view(View.LEVEL_END_TIME_BONUSES);assertEquals(42,table.length());for(int i=0;i<bonus.length;i++)assertEquals(bonus[i],table.unsignedWord(2*i));
        assertEquals(0xD3FA,View.GOAL_POST_BASE_TILES.range().fileOffset());assertEquals(0xD5E4,View.LEVEL_END_TIME_BONUSES.range().fileOffset());
    }
    @Test void sharedGoalSignpostHullKeepsSeparateNativeSignpostAndGoalProducerBounds()throws Exception{
        var a=retail().assets();var o=fromAssets(a);var v=o.goalSignpost();assertSame(v,o.goalSignpost());assertEquals(76,a.sectionSources().source(CdR12bSectionSources.SourceId.ART_SIGNPOST).tileCount());assertEquals(24,a.sectionSources().source(CdR12bSectionSources.SourceId.ART_GOAL_POST).tileCount());
        for(int frame=0;frame<6;frame++){int bound=frame==5?24:76;int max=v.mappingFrames().get(frame).pieces().stream().mapToInt(p->p.tileOffset()+p.tileCount()).max().orElse(0);assertEquals(bound,max);for(var piece:v.mappingFrames().get(frame).pieces())assertTrue(piece.tileOffset()<=bound-piece.tileCount());}
        assertEquals(0x21D9B4,View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress());assertEquals(6,v.mappingFrames().size());assertTrue(v.animations().isEmpty());
    }
    @Test void existingPureBodyAssetFormatsKeepActualRaw72AndLegacyVertical74Barrier()throws Exception{
        var o=fromAssets(retail().assets());var tiles=o.view(View.OBJECT_TILE_IDS);
        assertNotNull(new CdSpinningDisc.Assets(tiles,o.spinningDisc(),View.MAP_SPR_SPINNING_DISC.range().nativeAddress(),o.view(View.SPINNING_DISC_PLAYER_FRAMES)));
        assertNotNull(new CdTunnelDoor.Assets(o.tunnelDoor(),View.MAP_SPR_TUNNEL_DOOR.range().nativeAddress(),tiles));assertNotNull(new CdSpinTunnelTag.Assets(View.MAP_SPR_POWERUP.range().nativeAddress()));
        assertThrows(IllegalArgumentException.class,()->new CdTunnelDoorVertical.Assets(o.tunnelDoorVertical(),View.MAP_SPR_TUNNEL_DOOR_VERTICAL.range().nativeAddress(),tiles));
        assertNotNull(new CdBigRing.Assets(o.bigRing(),View.MAP_SPR_BIG_RING.range().nativeAddress(),o.view(View.ANI_BIG_RING),o.bigRingFlash(),View.MAP_SPR_BIG_RING_FLASH.range().nativeAddress(),o.view(View.ANI_BIG_RING_FLASH)));
        assertNotNull(new CdGoalPost.Assets(o.goalSignpost(),View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress(),o.view(View.GOAL_POST_BASE_TILES)));
        assertNotNull(new CdSignpost.Assets(o.goalSignpost(),View.MAP_SPR_GOAL_SIGNPOST.range().nativeAddress(),o.view(View.ANI_SIGNPOST),o.view(View.LEVEL_END_TIME_BONUSES)));
    }
    @Test void everyNewRouteViewFaultRejectsBeforeCachingAndRetainsAllSixtyNineHistoricalViews()throws Exception{
        var real=retail().assets();for(int target=69;target<84;target++){var selected=View.values()[target];var actual=real.objectView(selected);byte[] bad=actual.copy();bad[bad.length-1]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(call->real.objectView(call.getArgument(0,View.class)));when(mock.objectView(selected)).thenReturn(CdAssetBytes.copyOf(bad),actual);var o=CdR12bObjectAssets.bind(mock);var retained=new ArrayList<CdAssetBytes>();for(int i=0;i<69;i++)retained.add(o.view(View.values()[i]));assertThrows(IllegalArgumentException.class,()->o.view(selected));var field=CdR12bObjectAssets.class.getDeclaredField("views");field.setAccessible(true);assertNull(((CdAssetBytes[])field.get(o))[target]);assertSame(o.view(selected),o.view(selected));for(int i=0;i<69;i++)assertSame(retained.get(i),o.view(View.values()[i]));verify(mock,times(2)).objectView(selected);}
    }
    @Test void newRouteVisualFailureNeverPublishesCacheAndCannotReplaceEarlierThirtyFourVisuals()throws Exception{
        var real=retail().assets();for(int target=34;target<40;target++){var c=VISUALS.get(target);var source=c.script()==null?c.map():c.script();var actual=real.objectView(source);byte[] bad=actual.copy();bad[bad.length-1]^=1;var mock=mock(CdR12bTerrainAssets.class);when(mock.identity()).thenReturn(real.identity());when(mock.profile()).thenReturn(real.profile());when(mock.sectionSources()).thenReturn(real.sectionSources());when(mock.objectView(any(View.class))).thenAnswer(call->real.objectView(call.getArgument(0,View.class)));when(mock.objectView(source)).thenReturn(CdAssetBytes.copyOf(bad),actual);var o=CdR12bObjectAssets.bind(mock);var prior=new ArrayList<Object>();for(int i=0;i<34;i++)prior.add(CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));var method=CdR12bObjectAssets.class.getMethod(c.method());assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(o));var field=CdR12bObjectAssets.class.getDeclaredField("visuals");field.setAccessible(true);assertNull(((CdObjectVisualAssets[])field.get(o))[target]);assertSame(method.invoke(o),method.invoke(o));for(int i=0;i<34;i++)assertSame(prior.get(i),CdR12bObjectAssets.class.getMethod(VISUALS.get(i).method()).invoke(o));verify(mock,times(2)).objectView(source);}
    }


    // BEGIN R12B RESULTS METADATA TESTS
    @Test void resultsUsesTheDispatchSelectedInitAndWholeFourRootIncludeRatherThanItsEqualUnusedCopy()throws Exception{
        var f=retail();var o=fromAssets(f.assets());var init=o.view(View.RESULTS_INIT_DATA);var map=o.view(View.MAP_SPR_RESULTS);
        assertEquals(84,View.RESULTS_INIT_DATA.ordinal());assertEquals(85,View.MAP_SPR_RESULTS.ordinal());assertEquals(0x20DC14L,o.view(View.OBJECT_INDEX).unsignedLong((0x3A-1)*4));
        assertEquals(0x20DED4,View.RESULTS_INIT_DATA.range().nativeAddress());assertEquals(24,init.length());assertEquals("00cc0000012000000110020000f0000100cc000001200002",HexFormat.of().formatHex(init.copy()));
        assertEquals(0x20DEEC,View.MAP_SPR_RESULTS.range().nativeAddress());assertEquals(621,View.MAP_SPR_RESULTS.canonicalRange().length());assertEquals(622,map.length());assertEquals(0,map.unsignedByte(621));
        int[][] offsets={{40,478,82,134,186},{30,540,72,124,176},{218,458,290,346,402},{208,520,280,336,392}};
        for(int root=0;root<4;root++)for(int frame=0;frame<5;frame++)assertEquals(offsets[root][frame],map.unsignedWord(root*10+frame*2));
        assertEquals(120,f.assets().sectionSources().source(CdR12bSectionSources.SourceId.ART_RESULTS).tileCount());assertEquals(37,f.assets().sectionSources().sources().size());
        assertEquals(0x01C5,map.unsignedWord(478+1+13*5+2));assertEquals(0x0297,map.unsignedWord(550+1+13*5+2));
        assertSame(init,o.view(View.RESULTS_INIT_DATA));assertSame(map,o.view(View.MAP_SPR_RESULTS));assertEquals(72,o.view(View.OBJECT_TILE_IDS).length());
    }
    @Test void eachResultsRawViewRejectsChangedOrMissingBytesBeforeCachingWithoutReplacingAnyPriorView()throws Exception{
        var real=retail().assets();for(var target:List.of(View.RESULTS_INIT_DATA,View.MAP_SPR_RESULTS)){
            var actual=real.objectView(target);byte[] changed=actual.copy();changed[changed.length-1]^=1;
            for(var bad:List.of(CdAssetBytes.copyOf(changed),actual.slice(0,actual.length()-1))){
                var fake=mock(CdR12bTerrainAssets.class);when(fake.identity()).thenReturn(real.identity());when(fake.profile()).thenReturn(real.profile());when(fake.sectionSources()).thenReturn(real.sectionSources());
                when(fake.objectView(any(View.class))).thenAnswer(c->real.objectView(c.getArgument(0,View.class)));when(fake.objectView(target)).thenReturn(bad,actual);
                var o=CdR12bObjectAssets.bind(fake);var old=new ArrayList<CdAssetBytes>();for(int i=0;i<84;i++)old.add(o.view(View.values()[i]));
                assertThrows(IllegalArgumentException.class,()->o.view(target));var field=CdR12bObjectAssets.class.getDeclaredField("views");field.setAccessible(true);assertNull(((CdAssetBytes[])field.get(o))[target.ordinal()]);
                assertSame(actual,o.view(target));for(int i=0;i<84;i++)assertSame(old.get(i),o.view(View.values()[i]));
            }
        }
    }
    // END R12B RESULTS METADATA TESTS

    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String semanticDigest(CdObjectVisualAssets v)throws Exception{var d=MessageDigest.getInstance("SHA-256");for(var f:v.mappingFrames()){d.update((byte)(f.relativeOffset()>>>8));d.update((byte)f.relativeOffset());d.update(f.bytes().copy());}for(var a:v.animations()){d.update((byte)(a.relativeOffset()>>>8));d.update((byte)a.relativeOffset());d.update(a.bytes().copy());}return HexFormat.of().formatHex(d.digest());}
    private record Retail(CdR12bTerrainAssets assets,CdR12bTerrainAssets independent,CdR12aTerrainAssets oldAct,CdMmdImage original){}
    private Retail retained;
    private Retail retail()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"optional actual original CD media root");Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=new Retail(CdR12bTerrainAssets.fromPrograms(source),CdR12bTerrainAssets.fromPrograms(source),CdR12aTerrainAssets.fromPrograms(source),source.mmd(CdNativeFile.R12B));}return retained;}
}
