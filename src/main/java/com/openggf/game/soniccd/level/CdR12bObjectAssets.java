package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.level.CdR12bSectionSources.SourceId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Detached original-USA R12B metadata for the retained object families: Ring, Spring, HUD, TimeIcon,
 * Monitor/TimePost, CollapsePlatform, Tamabboh, Animal, Swing, RobotGenerator, Anton, Platform, Boulder, BreakableWall, FloatBlock, Spikes, LogShadow, PataBata, Explosion, Flower, Points, LostRing, MonitorItem, Mosqui, Projector, Scenery, Springboard, SpinningDisc, TunnelDoor, SpinTunnel, TunnelDoorVertical, BigRing/Flash, GoalPost/Signpost.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: actual Act2Past includes and Init/Animate
 * operands are pinned in the companion report/tests. Authored by OpenAI Codex GPT-6.1 Sol.
 * Every byte is read from the authenticated terrain owner's retained original image.
 * Existing pure mapping/animation/delta decoders and the 37-source art metadata are reused.
 * No body, programme, Frame, clock, queue, decoder service, art/current bank or drawing is owned.
 */
public final class CdR12bObjectAssets {
    /** Fixed original spans. Spring199/200 and Boulder13/14 retain their actual parent EVEN bytes. */
    public enum View {
        OBJECT_INDEX(0x3C4A,256,256,"e487f3d9aa462c50b3cb6f5cf029b2477a64bde6fd32a5c303262aa31dc75595","e487f3d9aa462c50b3cb6f5cf029b2477a64bde6fd32a5c303262aa31dc75595"),
        OBJECT_TILE_IDS(0xCC00,72,72,"7b25e293d8155f08891b43228817e782581ed26aaa06a14712adff8c4e3f5762","7b25e293d8155f08891b43228817e782581ed26aaa06a14712adff8c4e3f5762"),
        RING_DELTAS(0x9A4C,32,32,"35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86","35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86"),
        MAP_SPR_RING(0x9EB0,67,67,"cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a","cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a"),
        ANI_RING(0x9EA8,8,8,"4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a","4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a"),
        SPRING_SPEEDS(0x969E,4,4,"5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621","5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"),
        MAP_SPRING(0x9956,199,200,"f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd","09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c"),
        ANI_SPRING(0x9948,14,14,"7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5","7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5"),
        DIAGONAL_SPRING_HEADER(0x351C8,2,2,"3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd","3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd"),
        MAP_SPR_HUD(0xAB1C,168,168,"7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3","7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3"),
        MAP_SPR_MONITOR_TIME(0xA804,450,450,"00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca","00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca"),
        ANI_MONITOR(0xA77C,136,136,"c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36","c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36"),
        MAP_SPR_COLLAPSE_PLATFORM_1(0xC2D6,118,118,"e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12","e11e6733195a4700631f68f369278f7335279b756d615bd64b1ead95e6dcca12"),
        COLLAPSE_PLATFORM_SIZES_1(0xC34C,24,24,"9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1","9f05c5080c46f62408a9b56946844ef02d6f5b41cf9e1acb5f6b561cd83aafb1"),
        MAP_SPR_COLLAPSE_PLATFORM_3(0xC364,62,62,"77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c","77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c"),
        MAP_SPR_COLLAPSE_PLATFORM_2(0xC3A2,286,286,"be1266acacef995f34fa47ff432a198d5bfeddc6af3484949a0f26348d04cb2b","be1266acacef995f34fa47ff432a198d5bfeddc6af3484949a0f26348d04cb2b"),
        COLLAPSE_PLATFORM_SIZES_2(0xC4C0,24,24,"c0069f95af27ab8e2b9913aab0991fe10e39feaf4bcaed16e3c7e9595a5b995a","c0069f95af27ab8e2b9913aab0991fe10e39feaf4bcaed16e3c7e9595a5b995a"),
        MAP_SPR_COLLAPSE_PLATFORM_4(0xC4D8,42,42,"076fa0be74347e48024976fe775b0b2bc254fa2a1d8b358588406bf48c23126a","076fa0be74347e48024976fe775b0b2bc254fa2a1d8b358588406bf48c23126a"),
        ANI_TAMABBOH_NORMAL(0xB6F8,12,12,"ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474","ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474"),
        ANI_TAMABBOH_DAMAGED(0xB704,26,26,"09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84","09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84"),
        MAP_SPR_TAMABBOH(0xB71E,62,62,"e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847","e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847"),
        ANI_TAMABBOH_MISSILE(0xB81E,8,8,"764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5","764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5"),
        MAP_SPR_TAMABBOH_MISSILE(0xB826,42,42,"10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb","10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb"),
        ANIMAL_BASE_TILES(0xCF1A,22,22,"74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec","74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec"),
        ANI_FLYING_ANIMAL(0xCE64,66,66,"5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3","5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3"),
        ANI_GROUND_ANIMAL(0xCEA6,60,60,"b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4","b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4"),
        MAP_SPR_FLYING_ANIMAL(0xCEE2,20,20,"00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39","00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39"),
        MAP_SPR_GROUND_ANIMAL(0xCEF6,36,36,"029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2","029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2"),
        SWING_MOTION(0xEB4E,64,64,"70b18bc828edc56e0a4157ccb2b124aa274914d0e898a9a852239d5cade58eb8","70b18bc828edc56e0a4157ccb2b124aa274914d0e898a9a852239d5cade58eb8"),
        MAP_SPR_SWING(0xECEC,30,30,"22d182c8037a55534a03b0f9cf2d90691c3161180ddd68d54eae822a3cfab68a","22d182c8037a55534a03b0f9cf2d90691c3161180ddd68d54eae822a3cfab68a"),
        ROBOT_GENERATOR_BASE_TILES(0xE700,22,22,"3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4","3948236aa2a83a827ef6a7fb0f0e863b4a1ffc2b743355f954aed558420992b4"),
        ANI_ROBOT_GENERATOR(0xE47A,8,8,"cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e","cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e"),
        MAP_SPR_ROBOT_GENERATOR(0xE482,574,574,"69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59","69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59"),
        ROBOT_GENERATOR_EXPLOSION_SCHEDULE(0xE6C0,64,64,"1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123","1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123"),
        ANI_ANTON(0xB154,12,12,"623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d","623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d"),
        MAP_SPR_ANTON(0xB160,112,112,"898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808","898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808"),
        MAP_SPR_PLATFORM(0xC980,132,132,"a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7","a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7"),
        PLATFORM_WIDTHS(0xC5F8,4,4,"87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3","87d0fad66b7f6fe31844c828bbfa818f478c885f0f7672d1bb38d89ed6bfb9d3"),
        PLATFORM_RANGES(0xC5FC,4,4,"ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a","ce513997025ae549282209565f4c74aae2258cd6c391acd643d3b7f1b8071c9a"),
        MAP_SPR_BOULDER(0x940A,13,14,"dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d","f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652"),
        MAP_SPR_BREAKABLE_WALL(0xEE64,312,312,"c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226","c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226"),
        BREAKABLE_WALL_PIECE_FRAMES(0xEF9C,64,64,"9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f","9ea247159cb7c1830c8392d11b18cd1ce1de576fa33e4451179201fbe800ac7f"),
        BREAKABLE_WALL_PIECE_DELTAS(0xEFDC,24,24,"86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94","86444e752d8300afadd9aa1b5e2fa7a3252febf156c023d05f7bcf152f706e94"),
        BREAKABLE_WALL_PIECE_SPEEDS(0xEFF4,48,48,"7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31","7a1571aadadf44de0c59b260de3ec3464b1b3676a4ea2cef273b069284032b31"),
        MAP_SPR_FLOAT_BLOCK(0xA1B0,76,76,"98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e","98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e"),
        ANI_FLOAT_BLOCK(0xA192,30,30,"3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9","3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9"),
        MAP_SPR_SPIKES(0xBFA6,24,24,"db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607","db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607"),
        MAP_SPR_LOG_SHADOW(0x87A4,16,16,"bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b","bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b"),
        LOG_SHADOW_BASE_TILES(0x8794,16,16,"b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637","b9a30c86ce81139722854af604422196b077c78a0e79dcac3cca3b506c06d637"),
        MAP_SPR_PATA_BATA_NORMAL(0xB2E4,34,34,"9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b","9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b"),
        MAP_SPR_PATA_BATA_DAMAGED(0xB306,40,40,"96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889","96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889"),
        ANI_PATA_BATA(0xB2D2,18,18,"a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3","a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3"),
        MAP_SPR_EXPLOSION(0x1D714,146,146,"0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44","0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44"),
        ANI_EXPLOSION(0x1D700,20,20,"9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649","9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649"),
        MAP_SPR_FLOWER(0x36AB0,90,90,"48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41","48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41"),
        ANI_FLOWER(0x36A94,28,28,"74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9","74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9"),
        MAP_SPR_POINTS(0xAA1A,82,82,"24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e","24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e"),
        MAP_SPR_MOSQUI_NORMAL(0xB49C,60,60,"5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e","5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e"),
        MAP_SPR_MOSQUI_DAMAGED(0xB4D8,66,66,"23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313","23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313"),
        ANI_MOSQUI_NORMAL(0xB480,14,14,"24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2","24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2"),
        ANI_MOSQUI_DAMAGED(0xB48E,13,14,"6718cb74ab9b1ce59a3a122b0dee40b38ece57b7fdacd1f4baf7b76bc38c1a6e","cf64e547e8082aac25e26ac28799500505b45169aababaed6d42e9b144990408"),
        MAP_SPR_PROJECTOR(0xE9F0,68,68,"2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3","2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3"),
        ANI_METAL_SONIC_HOLOGRAM(0xE9AC,68,68,"2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f","2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f"),
        PROJECTOR_EXPLOSION_SCHEDULE(0xEA34,38,38,"5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3","5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3"),
        MAP_SPR_SCENERY(0x86D8,62,62,"5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12","5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12"),
        SCENERY_BASE_TILES(0x86CC,12,12,"90b119329f4db25def8b3f2b246da7076cf0cee8b236806ad54079b7c5371227","90b119329f4db25def8b3f2b246da7076cf0cee8b236806ad54079b7c5371227"),
        MAP_SPR_SPRINGBOARD(0xBE7C,84,84,"a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f","a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f"),
        ANI_SPRINGBOARD(0xBE46,54,54,"66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba","66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba"),
        SPRINGBOARD_HITBOXES(0xBB78,32,32,"48c0ebee94aed675266f2c3b8ffbb5f609aeac6e40c7d72d792ef851c7e2735c","48c0ebee94aed675266f2c3b8ffbb5f609aeac6e40c7d72d792ef851c7e2735c"),
        MAP_SPR_SPINNING_DISC(0x937C,36,36,"c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4","c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4"),
        ANI_SPINNING_DISC(0x9374,8,8,"bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260","bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260"),
        SPINNING_DISC_PLAYER_FRAMES(0x924C,16,16,"51995b0191d2a4a3dff61e17629c017c209c1170fbf06d5172a5ce046b325389","51995b0191d2a4a3dff61e17629c017c209c1170fbf06d5172a5ce046b325389"),
        MAP_SPR_TUNNEL_DOOR(0x1D6C4,28,28,"7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92","7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92"),
        ANI_TUNNEL_DOOR(0x1D6B6,14,14,"398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0","398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0"),
        MAP_SPR_POWERUP(0x1D528,274,274,"d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc","d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc"),
        MAP_SPR_TUNNEL_DOOR_VERTICAL(0xCB52,28,28,"954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275","954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275"),
        MAP_SPR_BIG_RING(0x1D8F6,176,176,"c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4","c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4"),
        ANI_BIG_RING(0x1D8EE,8,8,"b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6","b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6"),
        MAP_SPR_BIG_RING_FLASH(0xD652,416,416,"b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10","b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10"),
        ANI_BIG_RING_FLASH(0xD646,12,12,"b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f","b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f"),
        MAP_SPR_GOAL_SIGNPOST(0x1D9B4,126,126,"610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8","610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8"),
        ANI_SIGNPOST(0x1D9A6,14,14,"9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691","9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691"),
        GOAL_POST_BASE_TILES(0xD3FA,112,112,"82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724","82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724"),
        LEVEL_END_TIME_BONUSES(0xD5E4,42,42,"be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861","be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861"),
        RESULTS_INIT_DATA(0xDED4,24,24,"8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8","8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8"),
        MAP_SPR_RESULTS(0xDEEC,621,622,"f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621","3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9"),
        MAP_SPR_TUNNEL_DOOR_SPLASH(0x1D652,100,100,"2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287","2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287"),
        ANI_TUNNEL_DOOR_SPLASH(0x1D63A,24,24,"c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce","c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce");
        private final int offset,canonicalLength,length;
        private final String canonicalSha,sha;
        View(int offset,int canonicalLength,int length,String canonicalSha,String sha) {
            if(offset<0||canonicalLength<=0||length<canonicalLength||offset>CdR12bProfile.FILE_BYTES-length
                    ||!canonicalSha.matches("[0-9a-f]{64}")||!sha.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid finite R12B object metadata");
            this.offset=offset;this.canonicalLength=canonicalLength;this.length=length;this.canonicalSha=canonicalSha;this.sha=sha;
        }
        public CdR12bProfile.FileRange canonicalRange(){return range(canonicalLength);}
        public CdR12bProfile.FileRange range(){return range(length);}
        private CdR12bProfile.FileRange range(int count){return new CdR12bProfile.FileRange(CdNativeFile.R12B,CdMmdImage.Domain.ORIGINAL_WORD_RAM,offset,count);}
        public String canonicalSha256(){return canonicalSha;}
        public String sha256(){return sha;}
    }
    private enum Key {
        RINGS(View.MAP_SPR_RING,9,View.ANI_RING,1,14,0,0,0,SourceId.ART_RINGS),
        SPRINGVERTICAL(View.MAP_SPRING,3,View.ANI_SPRING,1,36,0,12,46,SourceId.ART_SPRINGS),
        SPRINGHORIZONTAL(View.MAP_SPRING,3,View.ANI_SPRING,1,36,6,46,80,SourceId.ART_SPRINGS),
        SPRINGDIAGONAL(View.MAP_SPRING,3,View.ANI_SPRING,1,46,80,86,200,null),
        HUDVISUALS(View.MAP_SPR_HUD,4,null,0,63,0,0,0,null),
        MONITORTIME(View.MAP_SPR_MONITOR_TIME,20,View.ANI_MONITOR,10,82,0,0,0,SourceId.ART_MONITOR_TIME_POSTS),
        COLLAPSEPLATFORM1(View.MAP_SPR_COLLAPSE_PLATFORM_1,1,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        COLLAPSEPLATFORM2(View.MAP_SPR_COLLAPSE_PLATFORM_2,2,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        COLLAPSEPLATFORM3(View.MAP_SPR_COLLAPSE_PLATFORM_3,7,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        COLLAPSEPLATFORM4(View.MAP_SPR_COLLAPSE_PLATFORM_4,2,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        TAMABBOHNORMAL(View.MAP_SPR_TAMABBOH,3,View.ANI_TAMABBOH_NORMAL,2,59,0,0,0,SourceId.ART_TAMABBOH),
        TAMABBOHDAMAGED(View.MAP_SPR_TAMABBOH,3,View.ANI_TAMABBOH_DAMAGED,2,59,22,28,62,SourceId.ART_TAMABBOH),
        TAMABBOHMISSILE(View.MAP_SPR_TAMABBOH_MISSILE,4,View.ANI_TAMABBOH_MISSILE,1,59,0,0,0,SourceId.ART_TAMABBOH),
        FLYINGANIMAL(View.MAP_SPR_FLYING_ANIMAL,3,View.ANI_FLYING_ANIMAL,2,20,0,0,0,SourceId.ART_ANIMALS),
        GROUNDANIMAL(View.MAP_SPR_GROUND_ANIMAL,5,View.ANI_GROUND_ANIMAL,1,20,0,0,0,SourceId.ART_ANIMALS),
        SWING(View.MAP_SPR_SWING,3,null,0,14,0,0,0,SourceId.ART_SWING),
        ROBOTGENERATOR(View.MAP_SPR_ROBOT_GENERATOR,8,View.ANI_ROBOT_GENERATOR,1,23,0,0,0,SourceId.ART_ROBOT_TRANSPORT_B),
        ANTON(View.MAP_SPR_ANTON,4,View.ANI_ANTON,2,22,0,0,0,SourceId.ART_ANTON),
        PLATFORM(View.MAP_SPR_PLATFORM,3,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        BOULDER(View.MAP_SPR_BOULDER,1,null,0,19,0,0,0,SourceId.ART_BOULDER),
        BREAKABLEWALL(View.MAP_SPR_BREAKABLE_WALL,14,null,0,57,0,0,0,SourceId.ART_PLATFORM),
        FLOATBLOCK(View.MAP_SPR_FLOAT_BLOCK,6,View.ANI_FLOAT_BLOCK,3,15,0,0,0,SourceId.ART_BLOCK),
        SPIKES(View.MAP_SPR_SPIKES,1,null,0,8,0,0,0,SourceId.ART_SPIKES_V2),
        LOGSHADOW(View.MAP_SPR_LOG_SHADOW,2,null,0,16,0,0,0,SourceId.ART_LOG_SHADOW),
        PATABATANORMAL(View.MAP_SPR_PATA_BATA_NORMAL,3,View.ANI_PATA_BATA,2,50,0,0,0,SourceId.ART_PATA_BATA),
        PATABATADAMAGED(View.MAP_SPR_PATA_BATA_DAMAGED,3,View.ANI_PATA_BATA,2,50,0,0,0,SourceId.ART_PATA_BATA),
        EXPLOSION(View.MAP_SPR_EXPLOSION,7,View.ANI_EXPLOSION,2,70,0,0,0,SourceId.ART_EXPLOSIONS),
        FLOWER(View.MAP_SPR_FLOWER,8,View.ANI_FLOWER,4,39,0,0,0,SourceId.ART_FLOWER),
        POINTS(View.MAP_SPR_POINTS,6,null,0,5,0,0,0,SourceId.ART_POINTS),
        MOSQUINORMAL(View.MAP_SPR_MOSQUI_NORMAL,5,View.ANI_MOSQUI_NORMAL,2,70,0,0,0,SourceId.ART_MOSQUI),
        MOSQUIDAMAGED(View.MAP_SPR_MOSQUI_DAMAGED,5,View.ANI_MOSQUI_DAMAGED,2,70,0,0,0,SourceId.ART_MOSQUI),
        PROJECTOR(View.MAP_SPR_PROJECTOR,6,View.ANI_METAL_SONIC_HOLOGRAM,2,51,0,0,0,SourceId.ART_HOLOGRAM),
        SCENERY(View.MAP_SPR_SCENERY,3,null,0,17,0,0,0,SourceId.ART_SCENERY_B),
        SPRINGBOARD(View.MAP_SPR_SPRINGBOARD,6,View.ANI_SPRINGBOARD,5,21,0,0,0,SourceId.ART_SPRINGBOARD),
        SPINNINGDISC(View.MAP_SPR_SPINNING_DISC,3,View.ANI_SPINNING_DISC,1,12,0,0,0,SourceId.ART_SPINNING_DISC),
        TUNNELDOOR(View.MAP_SPR_TUNNEL_DOOR,2,View.ANI_TUNNEL_DOOR,1,12,0,0,0,SourceId.ART_TUNNEL_DOOR),
        TUNNELDOORVERTICAL(View.MAP_SPR_TUNNEL_DOOR_VERTICAL,2,null,0,12,0,0,0,SourceId.ART_TUNNEL_DOOR),
        BIGRING(View.MAP_SPR_BIG_RING,4,null,0,106,0,0,0,SourceId.ART_BIG_RING),
        BIGRINGFLASH(View.MAP_SPR_BIG_RING_FLASH,8,null,0,77,0,0,0,SourceId.ART_BIG_RING_FLASH),
        GOALSIGNPOST(View.MAP_SPR_GOAL_SIGNPOST,6,null,0,76,0,0,0,null),
        TUNNELDOORSPLASH(View.MAP_SPR_TUNNEL_DOOR_SPLASH,7,View.ANI_TUNNEL_DOOR_SPLASH,3,86,0,0,0,null);
        final View map,script;final int frames,scripts,bound,root,start,end;final SourceId art;
        Key(View map,int frames,View script,int scripts,int bound,int root,int start,int end,SourceId art){
            this.map=map;this.frames=frames;this.script=script;this.scripts=scripts;this.bound=bound;this.root=root;this.start=start;this.end=end;this.art=art;
        }
    }
    private final CdR12bTerrainAssets assets;
    private final CdR12bTerrainAssets.Identity identity;
    private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];
    private final CdObjectVisualAssets[] visuals=new CdObjectVisualAssets[Key.values().length];
    private List<CdR11aObjectAssets.Delta> deltas;

    private CdR12bObjectAssets(CdR12bTerrainAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual original R12B terrain");
        identity=Objects.requireNonNull(assets.identity(),"identity");
        var profile=Objects.requireNonNull(assets.profile(),"profile");
        if(identity.variant()!=CdReleaseVariant.USA||identity.file()!=CdNativeFile.R12B
                ||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||!identity.stage().equals(CdStagePayload.R12B.stage())
                ||!identity.mmdSha256().equals(profile.expectedMmdSha256()))
            throw new IllegalArgumentException("Object metadata requires the actual original USA R12B owner");
    }
    public static CdR12bObjectAssets fromAssets(CdR12bTerrainAssets assets){return Objects.requireNonNull(assets).objects();}
    static CdR12bObjectAssets bind(CdR12bTerrainAssets assets){return new CdR12bObjectAssets(assets);}
    public CdR12bTerrainAssets.Identity identity(){return identity;}
    public CdObjectPlacements placements(){return assets.placements();}
    public synchronized CdAssetBytes view(View source){
        Objects.requireNonNull(source,"fixed metadata source");var value=views[source.ordinal()];if(value!=null)return value;
        value=Objects.requireNonNull(assets.objectView(source),"retained original object view");
        if(value.length()!=source.length||!source.sha.equals(CdR11aProfile.sha256(value.copy())))
            throw new IllegalArgumentException("Original R12B object source differs: "+source);
        views[source.ordinal()]=value;return value;
    }
    public CdAssetBytes springMappings(){return view(View.MAP_SPRING);}
    public synchronized List<CdR11aObjectAssets.Delta> ringDeltas(){
        if(deltas==null)deltas=CdR11aObjectAssets.decodeRingDeltas(view(View.RING_DELTAS).copy());return deltas;
    }
    /** Existing raw digit/reset/divisor owner; no duplicate source cache or upload/current authority. */
    public CdLevelHud.Assets hudAssets(){return assets.directArtSources().hudAssets();}
    public CdObjectVisualAssets rings() { return visual(Key.RINGS); }
    public CdObjectVisualAssets springVertical() { return visual(Key.SPRINGVERTICAL); }
    public CdObjectVisualAssets springHorizontal() { return visual(Key.SPRINGHORIZONTAL); }
    public CdObjectVisualAssets springDiagonal() { return visual(Key.SPRINGDIAGONAL); }
    public CdObjectVisualAssets hudVisuals() { return visual(Key.HUDVISUALS); }
    public CdObjectVisualAssets monitorTime() { return visual(Key.MONITORTIME); }
    public CdObjectVisualAssets collapsePlatform1() { return visual(Key.COLLAPSEPLATFORM1); }
    public CdObjectVisualAssets collapsePlatform2() { return visual(Key.COLLAPSEPLATFORM2); }
    public CdObjectVisualAssets collapsePlatform3() { return visual(Key.COLLAPSEPLATFORM3); }
    public CdObjectVisualAssets collapsePlatform4() { return visual(Key.COLLAPSEPLATFORM4); }
    public CdObjectVisualAssets tamabbohNormal() { return visual(Key.TAMABBOHNORMAL); }
    public CdObjectVisualAssets tamabbohDamaged() { return visual(Key.TAMABBOHDAMAGED); }
    public CdObjectVisualAssets tamabbohMissile() { return visual(Key.TAMABBOHMISSILE); }
    public CdObjectVisualAssets flyingAnimal() { return visual(Key.FLYINGANIMAL); }
    public CdObjectVisualAssets groundAnimal() { return visual(Key.GROUNDANIMAL); }
    public CdObjectVisualAssets swing() { return visual(Key.SWING); }
    public CdObjectVisualAssets robotGenerator() { return visual(Key.ROBOTGENERATOR); }
    public CdObjectVisualAssets anton() { return visual(Key.ANTON); }
    public CdObjectVisualAssets platform() { return visual(Key.PLATFORM); }
    /** Native Boulder/Main:26-59; one counted frame, actual parent EVEN, existing19-tile source. */
    public CdObjectVisualAssets boulder() { return visual(Key.BOULDER); }
    public CdObjectVisualAssets breakableWall() { return visual(Key.BREAKABLEWALL); }
    public CdObjectVisualAssets floatBlock() { return visual(Key.FLOATBLOCK); }
    public CdObjectVisualAssets spikes() { return visual(Key.SPIKES); }
    public CdObjectVisualAssets logShadow() { return visual(Key.LOGSHADOW); }
    public CdObjectVisualAssets pataBataNormal() { return visual(Key.PATABATANORMAL); }
    public CdObjectVisualAssets pataBataDamaged() { return visual(Key.PATABATADAMAGED); }
    public CdObjectVisualAssets explosion() { return visual(Key.EXPLOSION); }
    public CdObjectVisualAssets flower() { return visual(Key.FLOWER); }
    public CdObjectVisualAssets points() { return visual(Key.POINTS); }
    public CdObjectVisualAssets normalMosqui() { return visual(Key.MOSQUINORMAL); }
    public CdObjectVisualAssets damagedMosqui() { return visual(Key.MOSQUIDAMAGED); }
    public CdObjectVisualAssets projector() { return visual(Key.PROJECTOR); }
    public CdObjectVisualAssets scenery() { return visual(Key.SCENERY); }
    public CdObjectVisualAssets springboard() { return visual(Key.SPRINGBOARD); }

    public CdObjectVisualAssets spinningDisc() { return visual(Key.SPINNINGDISC); }
    public CdObjectVisualAssets tunnelDoor() { return visual(Key.TUNNELDOOR); }
    public CdObjectVisualAssets tunnelDoorVertical() { return visual(Key.TUNNELDOORVERTICAL); }
    /** Static counted mappings; the reached manual animation owner reads ANI_BIG_RING separately. */
    public CdObjectVisualAssets bigRing() { return visual(Key.BIGRING); }
    public CdObjectVisualAssets bigRingFlash() { return visual(Key.BIGRINGFLASH); }
    /** Shared metadata hull only: frames0..4 native Signpost76, frame5 native GoalPost24. */
    public CdObjectVisualAssets goalSignpost() { return visual(Key.GOALSIGNPOST); }
    /** Native selector D stores tile0 in R12B; the finite86 mapping bound creates no art/residency claim. */
    public CdObjectVisualAssets tunnelDoorSplash() { return visual(Key.TUNNELDOORSPLASH); }

    private void validateGoalSignpostBounds(CdObjectVisualAssets result) {
        int signpost = assets.sectionSources().source(SourceId.ART_SIGNPOST).tileCount();
        int goal = assets.sectionSources().source(SourceId.ART_GOAL_POST).tileCount();
        if (signpost != 76 || goal != 24) throw new IllegalArgumentException("Original R12B end-item bounds differ");
        for (int frame = 0; frame < 6; frame++) {
            int bound = frame == 5 ? goal : signpost;
            for (var piece : result.mappingFrames().get(frame).pieces())
                if (piece.tileOffset() > bound - piece.tileCount())
                    throw new IllegalArgumentException("Goal/Signpost frame exceeds its actual source");
        }
    }

    private synchronized CdObjectVisualAssets visual(Key key){
        var cached=visuals[key.ordinal()];if(cached!=null)return cached;
        int bound=key.art==null?key.bound:assets.sectionSources().source(key.art).tileCount();
        if(key==Key.SPRINGDIAGONAL)bound=view(View.DIAGONAL_SPRING_HEADER).unsignedWord(0)&0x7FFF;
        if(bound!=key.bound)throw new IllegalArgumentException("Original R12B visual bound differs: "+key);
        // HUD's 63-tile metadata hull combines the 27-tile source and existing native raw digit/life slots
        // ($8568 base, last digit at $B4A0+64). It is not a 63-tile art source or residency claim.
        byte[] map=view(key.map).copy();byte[] script=key.script==null?new byte[0]:view(key.script).copy();
        CdObjectVisualAssets result;
        if(key.end!=0)result=CdObjectVisualAssets.decodeIndexed(map,key.root,key.frames,key.start,key.end,script,key.scripts,bound);
        else if(key.script==null)result=CdObjectVisualAssets.decodeStatic(map,key.frames,bound);
        else result=CdObjectVisualAssets.decode(map,key.frames,script,key.scripts,bound,key==Key.TAMABBOHNORMAL?Map.of(0,12):Map.of());
        if(key==Key.GOALSIGNPOST)validateGoalSignpostBounds(result);
        visuals[key.ordinal()]=result;return result;
    }
}
