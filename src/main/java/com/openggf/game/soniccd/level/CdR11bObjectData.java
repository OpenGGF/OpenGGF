package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.objects.CdObjectAnimation;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdRing;
import com.openggf.game.soniccd.objects.CdReservedHud;
import com.openggf.game.soniccd.objects.CdTitleCard;
import com.openggf.game.soniccd.objects.CdTimeStars;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.resources.CdLevelHud;
import java.io.IOException;
import java.util.*;

/** Original USA R11B physical object DATA over its same retained PPZ reader.
 * Native1ecc: Object Index(Act1Past):7-79, Object Functions:11-32,327-343 and
 * the exact source manifests in the external handoff. No object execution, source invocation,
 * slot/secondary state, World/Frame, clear, current art, draw or restore authority is issued.
 * A cached immutable DATA facade owns these finite new role views; existing registered
 * dispatch/art/player bytes still borrow the actual Reader cache. No image is reopened. */
public final class CdR11bObjectData {
    /** Finite original-role keys, never equal A or TT declarations. */
    public enum Table {
        DISPATCH(0x3c38,256,"d46ec4888d5befcc4e9a4f00d3a10de6b70fe326322e3b7085800951c1577f5b"),
        NULL_HANDLER(0x3d38,6,"51b330f0a1d88e85ed0f569db0d036a89273aee193b510f46841bae307a33292"),
        ANI_WATERFALL(0x6fea,6,"8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870"),
        MAP_SPR_WATERFALL(0x6ff0,68,"5419f6735255517ef1edce8eb5ebd6d14ddf1185018de4b507180f136a7aaf7c"),
        MAP_SPR_SCENERY(0x877a,62,"5c73de36900992cd776411f9b2b2e9c2007ee3fc070d74cf1a842db6e4217a12"),
        MAP_SPR_LOG_SHADOW(0x8846,16,"bb3b5b1a67fd3665a520a4a6c1d7ff89212992a766df61ec42b6c41aa1d38f7b"),
        MAP_SPR_SONIC_HOLE(0x892c,18,"294c5d0eb7d5d0a19a3799ebf5bed56036e7f842b7d50da8488a5cfccaddce35"),
        ANI_SPINNING_DISC(0x9416,8,"bebbcc2ef135a5349ad7b24690b63ab06bdf5ddae18b774d7fcde1824b340260"),
        MAP_SPR_SPINNING_DISC(0x941e,36,"c74d267322eb765b6dc9677a3b53824fbeeb48328d419631199798257f7ddeb4"),
        MAP_SPR_BOULDER(0x94ac,14,"f1613b80dc72cd1d153d190f3a660e3990f92648d54e65c989aed93bc9189652"),
        ANI_S1_SPRING(0x9980,28,"275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b"),
        MAP_SPR_S1_SPRING(0x999c,78,"9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b"),
        ANI_SPRING(0x99ea,14,"7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5"),
        MAP_SPR_SPRING1(0x99f8,200,"09fbb070ca8ab3354808535b21668a1c6faeac9fa88fb78628753fe9c8f8377c"),
        ANI_MOVING_SPRING(0x9ac0,6,"e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a"),
        MAP_SPR_MOVING_SPRING(0x9ac6,16,"f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d"),
        ANI_RING(0x9f4a,8,"4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a"),
        MAP_SPR_RING(0x9f52,68,"306667dca113be405fd1d78fd69b09152b853236db8ad8fbeb6a73bbde3be7e8"),
        MAP_SPR_S1_BIG_RING(0x9f96,162,"bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0"),
        MAP_SPR_S1_BIG_RING_FLASH(0xa038,164,"35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5"),
        ANI_FLOAT_BLOCK(0xa234,30,"3c6451c3f6719a3765334aa1c13d2864ae4b92a523fb5a7e335c6dc362f8d6b9"),
        MAP_SPR_FLOAT_BLOCK(0xa252,76,"98fa7f8f57a4f5d8a00cd60ac795f7331e5a9f6009af31fcfaf33b85b8ff6e5e"),
        ANI_MONITOR(0xa81e,136,"c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36"),
        MAP_SPR_MONITOR_TIME(0xa8a6,450,"00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca"),
        MAP_SPR_POINTS(0xaabc,82,"24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e"),
        MAP_SPR_HUD(0xabbe,168,"7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3"),
        ANI_ANTON(0xb1f6,12,"623c70ccd1429002e1d4e85929b53064bf0a1d7e0e56537d8d86c0bc94a7785d"),
        MAP_SPR_ANTON(0xb202,112,"898f3a56606b9c82c0c0a4c2197b0860353cbce320baf1026bd7f717b300e808"),
        ANI_PATA_BATA(0xb374,18,"a68b2c9721811294f0f2ae43e0f78acc91ed8211a5d0da2d08d2ba230b6a25f3"),
        MAP_SPR_PATA_BATA1(0xb386,34,"9165ad8d9f95bda2639172fe900f16dad44924af5f896a0223e6b9cb8bb7087b"),
        MAP_SPR_PATA_BATA2(0xb3a8,40,"96d4c533000d636452afa838266996b9c4853824e7b3377868f7876492858889"),
        ANI_MOSQUI1(0xb522,14,"24b00d5597a3482b4bf9f5e692be5ea38534c1f5265408b8c7273d672949c1a2"),
        ANI_MOSQUI2(0xb530,14,"cf64e547e8082aac25e26ac28799500505b45169aababaed6d42e9b144990408"),
        MAP_SPR_MOSQUI1(0xb53e,60,"5a10487e5e92e1c60e5885d832b90239c22d45f3c84bb49cd8886c590d8aba2e"),
        MAP_SPR_MOSQUI2(0xb57a,66,"23f8799c277fc89e4f94ccf75e0b488de9fca30a05a658cde5eaf0be8fec7313"),
        ANI_TAMABBOH1(0xb79a,12,"ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474"),
        ANI_TAMABBOH2(0xb7a6,26,"09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84"),
        MAP_SPR_TAMABBOH1(0xb7c0,62,"e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847"),
        ANI_TAMABBOH_MISSILE(0xb8c0,8,"764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5"),
        MAP_SPR_TAMABBOH_MISSILE(0xb8c8,42,"10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb"),
        ANI_TAGA_TAGA1(0xba1c,28,"f6d22518aa0c7cc06cecc15632fbc0f5111f4e51680ab41bf8bafe9bc628c790"),
        ANI_TAGA_TAGA2(0xba38,32,"eb0d5a0c603df7cc03b4b2cd7ded13585e493e91bdfceeea7a7df10cf9fd7f86"),
        MAP_SPR_TAGA_TAGA1(0xba58,274,"59ec05b9c6b9cedec1bd68da7ccc93edc24113fb6a9519c90153564999c36763"),
        ANI_SPRING_BOARD(0xbee8,54,"66b7d5d2f75e5d7ff1977d7d3f1cc4ec7635c1f1db4da5b7568f72ea142067ba"),
        MAP_SPR_SPRING_BOARD(0xbf1e,84,"a5b367d30a33cd6d503930c42c8ac86f8155235475b57094740a62ee242b0a3f"),
        MAP_SPR_SPIKES(0xc048,24,"db6b4414c086240e34231326d9832e5785550bdd914ef9d933cea9079bc78607"),
        MAP_SPR_COLLAPSE_PLATFORM1(0xc378,202,"eea06694c49702bbe30130b462bd797d000721083bb4273f870e2636f36863fb"),
        MAP_SPR_COLLAPSE_PLATFORM3(0xc46a,62,"77e4add5588163b238981a19f8715f1033c2c07ea2e639c223486ecc1c8e7f2c"),
        MAP_SPR_COLLAPSE_PLATFORM2(0xc4a8,782,"e8d169901d047ca9f3a76b31ed77d7d042160ff3da746284bf2d3d4bdb883f53"),
        MAP_SPR_COLLAPSE_PLATFORM4(0xc7f0,172,"f372b9f9d23c8ba0ebc6dc22993f60078891ea2843910a197cfe43e45a075a76"),
        MAP_SPR_PLATFORM(0xcd1a,132,"a8b6e5695090a558c67e7e776db8c4b642731a7ef7445608264b29f15529afd7"),
        MAP_SPR_PLATFORM2(0xcd9e,132,"99255f57e1ce478e78aa5a85f634f55a46febb767404e1e2eefd248120f48fad"),
        ANI_FLAP_DOOR_V(0xcede,14,"398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0"),
        MAP_SPR_FLAP_DOOR_V(0xceec,28,"954bf4fd9df8e85b782482c17f65e68d32101f1e43e21fd4a7fe4058a91b5275"),
        ANI_FLYING_ANIMAL(0xd1f8,66,"5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3"),
        ANI_GROUND_ANIMAL(0xd23a,60,"b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4"),
        MAP_SPR_FLYING_ANIMAL(0xd276,20,"00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39"),
        MAP_SPR_GROUND_ANIMAL(0xd28a,36,"029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2"),
        ANI_BIG_RING_FLASH(0xd9da,12,"b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f"),
        MAP_SPR_BIG_RING_FLASH(0xd9e6,416,"b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10"),
        MAP_SPR_GAME_OVER(0xddea,56,"ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b"),
        MAP_SPR_RESULTS_BAD(0xe280,622,"3421ca10ca8d30d20ebc792104211ebc360321c4e6a4509abf2fa0b9a6ea73e9"),
        MAP_SPR_TITLE_CARD(0xe52e,276,"a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11"),
        ANI_ROBOT_GENERATOR(0xe80e,8,"cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e"),
        MAP_SPR_ROBOT_GENERATOR(0xe816,574,"69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59"),
        ANI_METAL_SONIC_HOLOGRAM(0xed40,68,"2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f"),
        MAP_SPR_PROJECTOR(0xed84,68,"2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3"),
        MAP_SPR_BREAKABLE_WALL(0xefc2,312,"c561a76556cc21a46155e135205ab7c39cc8b007b9730c66e0a34a707d671226"),
        ANI_POWERUP(0x1d400,296,"ecd3e5667b53fd56bf9b9c7fa3e9e09cbb957efe90232419060f8197f0f50141"),
        MAP_SPR_POWERUP(0x1d528,274,"d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc"),
        ANI_TUNNEL_DOOR_SPLASH(0x1d63a,24,"c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce"),
        MAP_SPR_TUNNEL_DOOR_SPLASH(0x1d652,100,"2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287"),
        ANI_TUNNEL_DOOR(0x1d6b6,14,"398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0"),
        MAP_SPR_TUNNEL_DOOR(0x1d6c4,28,"7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92"),
        ANI_TUNNEL_WATERFALL(0x1d6e0,8,"8dd6dfce410856a8d39936416a5ef8fd0ec663197bd69d9d62305d2a70ccfb20"),
        MAP_SPR_TUNNEL_WATERFALL(0x1d6e8,24,"5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a"),
        ANI_EXPLOSION(0x1d700,20,"9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649"),
        MAP_SPR_EXPLOSION(0x1d714,146,"0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44"),
        ANI_CHECKPOINT(0x1d8a6,6,"9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551"),
        MAP_SPR_CHECKPOINT(0x1d8ac,66,"709d0648dfb32425f46281c236ededaef80750fc6250062f3bce77f9427a446f"),
        ANI_BIG_RING(0x1d8ee,8,"b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6"),
        MAP_SPR_BIG_RING(0x1d8f6,176,"c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4"),
        ANI_SIGNPOST(0x1d9a6,14,"9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691"),
        MAP_SPR_GOAL_SIGNPOST(0x1d9b4,126,"610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8"),
        ANI_FLOWER_CAPSULE(0x1da32,52,"1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9"),
        MAP_SPR_FLOWER_CAPSULE(0x1da66,210,"9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646"),
        ANI_FLOWER(0x36a94,28,"74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9"),
        MAP_SPR_FLOWER(0x36ab0,90,"48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41"),
        OBJECT_TILE_IDS(0xcf94,72,"7f6759c3ab336c2eee70a2fea5128e9920c86d418590ae557e0310948c563da1"),
        OBJ_COL_SIZES(0x6eca,126,"658a42cd58f3c61fc034da9e81c25656b9dc40d923ceed515e14b56672b1581d"),
        ENEMY_POINTS(0x6ce0,8,"75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5"),
        OBJ_RING_DELTAS(0x9aee,32,"35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86"),
        OBJ_SPRING_SPEEDS(0x9740,4,"5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"),
        OBJ_ANIMAL_BASE_TILE_LIST(0xd2ae,22,"74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec"),
        // Native Title Card Data8-15; HUD/Main412-420,607-617; existing B raw-number role.
        // Hashes authenticate reads from the retained B image; no literal payload is substituted.
        TITLE_CHARACTERS(0xe4ee,64,"5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2"),
        HUD_NUMBERS(0x36456,1024,"843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        HUD_RESET(0xaef4,12,"3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c"),
        HUD_DIVISORS(0xb054,40,"7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea");
        private final int offset,bytes;private final String sha;
        Table(int offset,int bytes,String sha){this.offset=offset;this.bytes=bytes;this.sha=sha;}
        public int fileOffset(){return offset;}public int nativeAddress(){return 0x200000+offset;}
        public int bytes(){return bytes;}public String sha256(){return sha;}
    }
    /** Every physical original dispatch entry, including nulls, aliases and scrapped handlers. */
    public enum Family {
        ID_01(1,0x203da6,"ObjSonic",false,false,8),
        ID_02(2,0x203da6,"ObjSonic",false,false,9),
        ID_03(3,0x2061fa,"ObjPowerup",false,false,10),
        ID_04(4,0x206f48,"ObjWaterfall",false,true,11),
        ID_05(5,0x206fe8,"ObjEarthquake",false,true,12),
        ID_06(6,0x205dc4,"ObjTestBadnik",false,false,13),
        ID_07(7,0x20893e,"ObjTunnelPath",false,false,14),
        ID_08(8,0x206fe6,"ObjEarthquakeSet",false,true,15),
        ID_09(9,0x2091fe,"ObjSpinningDisc",false,false,16),
        ID_0A(10,0x209606,"ObjSpring",false,false,17),
        ID_0B(11,0x206194,"ObjTunnelDoorSplash",false,false,18),
        ID_0C(12,0x20615c,"ObjTunnelDoorSplashSet",false,true,19),
        ID_0D(13,0x206084,"ObjTunnelDoor",false,false,20),
        ID_0E(14,0x20602e,"ObjSpinSplash",false,false,21),
        ID_0F(15,0x2094ba,"ObjMovingSpring",false,false,22),
        ID_10(16,0x209ad6,"ObjRing",false,false,23),
        ID_11(17,0x209dba,"ObjLostRing",false,false,24),
        ID_12(18,0x20a0dc,"ObjFloatBlock",false,false,25),
        ID_13(19,0x203d38,"ObjNull",true,false,26),
        ID_14(20,0x20d542,"ObjBigRing",false,false,27),
        ID_15(21,0x20d2c4,"ObjCapsule",false,false,28),
        ID_16(22,0x20d67a,"ObjGoalPost",false,false,29),
        ID_17(23,0x20d7fe,"ObjSignpost",false,false,30),
        ID_18(24,0x205e6a,"ObjExplosion",false,false,31),
        ID_19(25,0x20a4ba,"ObjMonitorTimePost",false,false,32),
        ID_1A(26,0x20a636,"ObjMonitorItem",false,false,33),
        ID_1B(27,0x209442,"ObjBoulder",false,false,34),
        ID_1C(28,0x20ab0e,"ObjHUDPoints",false,false,35),
        ID_1D(29,0x203d38,"ObjNull",true,false,36),
        ID_1E(30,0x203d38,"ObjNull",true,false,37),
        ID_1F(31,0x205ef8,"ObjFlower",false,false,38),
        ID_20(32,0x20c060,"ObjCollapsePlatform",false,false,39),
        ID_21(33,0x20c89c,"ObjPlatform",false,false,40),
        ID_22(34,0x20b5bc,"ObjTamabboh",false,false,41),
        ID_23(35,0x20bb6a,"ObjMissile",false,true,42),
        ID_24(36,0x20cfdc,"ObjAnimal",false,false,43),
        ID_25(37,0x203d38,"ObjNull",true,false,44),
        ID_26(38,0x20bf72,"ObjSpikes",false,false,45),
        ID_27(39,0x203d38,"ObjNull",true,false,46),
        ID_28(40,0x20bc3a,"ObjSpringBoard",false,false,47),
        ID_29(41,0x203d38,"ObjNull",true,false,48),
        ID_2A(42,0x203d38,"ObjNull",true,false,49),
        ID_2B(43,0x205c52,"ObjCheckpoint",false,false,50),
        ID_2C(44,0x203d38,"ObjNull",true,false,51),
        ID_2D(45,0x20e642,"ObjRobotGenerator",false,false,52),
        ID_2E(46,0x20eaaa,"ObjProjector",false,false,53),
        ID_2F(47,0x203d38,"ObjNull",true,false,54),
        ID_30(48,0x203d38,"ObjNull",true,false,55),
        ID_31(49,0x208856,"ObjSonicHole",false,false,56),
        ID_32(50,0x2086f4,"ObjScenery",false,false,57),
        ID_33(51,0x2087b8,"ObjLogShadow",false,false,58),
        ID_34(52,0x2063d6,"ObjSpinTunnel",false,false,59),
        ID_35(53,0x20ce22,"ObjTunnelDoorV",false,false,60),
        ID_36(54,0x20ee68,"ObjBreakableWall",false,false,61),
        ID_37(55,0x203d38,"ObjNull",true,false,62),
        ID_38(56,0x203d38,"ObjNull",true,false,63),
        ID_39(57,0x203d38,"ObjNull",true,false,64),
        ID_3A(58,0x20dfa8,"ObjResults",false,false,65),
        ID_3B(59,0x20dd06,"ObjGameOver",false,false,66),
        ID_3C(60,0x20de22,"ObjTitleCard",false,false,67),
        ID_3D(61,0x20b3d0,"ObjMosqui",false,false,68),
        ID_3E(62,0x20b272,"ObjPataBata",false,false,69),
        ID_3F(63,0x20b10c,"ObjAnton",false,false,70),
        ID_40(64,0x20b8f2,"ObjTagaTaga",false,false,71);
        private final int id,address,line;private final String label;private final boolean nativeNull,scrapped;
        Family(int id,int address,String label,boolean nativeNull,boolean scrapped,int line){this.id=id;this.address=address;this.label=label;this.nativeNull=nativeNull;this.scrapped=scrapped;this.line=line;}
        public int objectId(){return id;}public int handlerAddress(){return address;}public String nativeLabel(){return label;}
        public boolean nativeNull(){return nativeNull;}public boolean scrapped(){return scrapped;}
        public int objectIndexSourceLine(){return line;}
    }
    /** Only metadata for already-defined reserved handler roles; no slot/state is constructed. */
    public enum ReservedRole { PRIMARY_SONIC,SECONDARY_SONIC,SHIELD_AND_STARS,HUD_POINTS,TITLE_CARD }
    public enum Mapping {
        MAP_SPR_WATERFALL(Table.MAP_SPR_WATERFALL,0,2),
        MAP_SPR_SCENERY(Table.MAP_SPR_SCENERY,0,3),
        MAP_SPR_LOG_SHADOW(Table.MAP_SPR_LOG_SHADOW,0,2),
        MAP_SPR_SONIC_HOLE(Table.MAP_SPR_SONIC_HOLE,0,1),
        MAP_SPR_SPINNING_DISC(Table.MAP_SPR_SPINNING_DISC,0,3),
        MAP_SPR_BOULDER(Table.MAP_SPR_BOULDER,0,1),
        MAP_SPR_S1_SPRING(Table.MAP_SPR_S1_SPRING,0,6),
        MAP_SPR_SPRING1(Table.MAP_SPR_SPRING1,0,3),
        MAP_SPR_SPRING2(Table.MAP_SPR_SPRING1,6,3),
        MAP_SPR_SPRING3(Table.MAP_SPR_SPRING1,80,3),
        MAP_SPR_MOVING_SPRING(Table.MAP_SPR_MOVING_SPRING,0,2),
        MAP_SPR_RING(Table.MAP_SPR_RING,0,9),
        MAP_SPR_S1_BIG_RING(Table.MAP_SPR_S1_BIG_RING,0,4),
        MAP_SPR_S1_BIG_RING_FLASH(Table.MAP_SPR_S1_BIG_RING_FLASH,0,8),
        MAP_SPR_FLOAT_BLOCK(Table.MAP_SPR_FLOAT_BLOCK,0,6),
        MAP_SPR_MONITOR_TIME(Table.MAP_SPR_MONITOR_TIME,0,20),
        MAP_SPR_POINTS(Table.MAP_SPR_POINTS,0,6),
        MAP_SPR_HUD(Table.MAP_SPR_HUD,0,4),
        MAP_SPR_ANTON(Table.MAP_SPR_ANTON,0,4),
        MAP_SPR_PATA_BATA1(Table.MAP_SPR_PATA_BATA1,0,3),
        MAP_SPR_PATA_BATA2(Table.MAP_SPR_PATA_BATA2,0,3),
        MAP_SPR_MOSQUI1(Table.MAP_SPR_MOSQUI1,0,5),
        MAP_SPR_MOSQUI2(Table.MAP_SPR_MOSQUI2,0,5),
        MAP_SPR_TAMABBOH1(Table.MAP_SPR_TAMABBOH1,0,3),
        MAP_SPR_TAMABBOH2(Table.MAP_SPR_TAMABBOH1,22,3),
        MAP_SPR_TAMABBOH_MISSILE(Table.MAP_SPR_TAMABBOH_MISSILE,0,4),
        MAP_SPR_TAGA_TAGA1(Table.MAP_SPR_TAGA_TAGA1,0,7),
        MAP_SPR_TAGA_TAGA2(Table.MAP_SPR_TAGA_TAGA1,98,7),
        MAP_SPR_SPRING_BOARD(Table.MAP_SPR_SPRING_BOARD,0,6),
        MAP_SPR_SPIKES(Table.MAP_SPR_SPIKES,0,1),
        MAP_SPR_COLLAPSE_PLATFORM1(Table.MAP_SPR_COLLAPSE_PLATFORM1,0,2),
        MAP_SPR_COLLAPSE_PLATFORM3(Table.MAP_SPR_COLLAPSE_PLATFORM3,0,7),
        MAP_SPR_COLLAPSE_PLATFORM2(Table.MAP_SPR_COLLAPSE_PLATFORM2,0,6),
        MAP_SPR_COLLAPSE_PLATFORM4(Table.MAP_SPR_COLLAPSE_PLATFORM4,0,7),
        MAP_SPR_PLATFORM(Table.MAP_SPR_PLATFORM,0,3),
        MAP_SPR_PLATFORM2(Table.MAP_SPR_PLATFORM2,0,3),
        MAP_SPR_FLAP_DOOR_V(Table.MAP_SPR_FLAP_DOOR_V,0,2),
        MAP_SPR_FLYING_ANIMAL(Table.MAP_SPR_FLYING_ANIMAL,0,3),
        MAP_SPR_GROUND_ANIMAL(Table.MAP_SPR_GROUND_ANIMAL,0,5),
        MAP_SPR_BIG_RING_FLASH(Table.MAP_SPR_BIG_RING_FLASH,0,8),
        MAP_SPR_GAME_OVER(Table.MAP_SPR_GAME_OVER,0,2),
        MAP_SPR_TIME_OVER(Table.MAP_SPR_GAME_OVER,4,2),
        MAP_SPR_RESULTS_BAD(Table.MAP_SPR_RESULTS_BAD,0,5),
        MAP_SPR_RESULTS_BAD_SSZ3(Table.MAP_SPR_RESULTS_BAD,10,5),
        MAP_SPR_RESULTS_GOOD(Table.MAP_SPR_RESULTS_BAD,20,5),
        MAP_SPR_RESULTS_GOOD_SSZ3(Table.MAP_SPR_RESULTS_BAD,30,5),
        MAP_SPR_TITLE_CARD(Table.MAP_SPR_TITLE_CARD,0,9),
        MAP_SPR_ROBOT_GENERATOR(Table.MAP_SPR_ROBOT_GENERATOR,0,8),
        MAP_SPR_PROJECTOR(Table.MAP_SPR_PROJECTOR,0,6),
        MAP_SPR_BREAKABLE_WALL(Table.MAP_SPR_BREAKABLE_WALL,0,14),
        MAP_SPR_POWERUP(Table.MAP_SPR_POWERUP,0,13),
        MAP_SPR_TUNNEL_DOOR_SPLASH(Table.MAP_SPR_TUNNEL_DOOR_SPLASH,0,7),
        MAP_SPR_TUNNEL_DOOR(Table.MAP_SPR_TUNNEL_DOOR,0,2),
        MAP_SPR_TUNNEL_WATERFALL(Table.MAP_SPR_TUNNEL_WATERFALL,0,3),
        MAP_SPR_EXPLOSION(Table.MAP_SPR_EXPLOSION,0,7),
        MAP_SPR_CHECKPOINT(Table.MAP_SPR_CHECKPOINT,0,4),
        MAP_SPR_BIG_RING(Table.MAP_SPR_BIG_RING,0,4),
        MAP_SPR_GOAL_SIGNPOST(Table.MAP_SPR_GOAL_SIGNPOST,0,6),
        MAP_SPR_FLOWER_CAPSULE(Table.MAP_SPR_FLOWER_CAPSULE,0,9),
        MAP_SPR_FLOWER(Table.MAP_SPR_FLOWER,0,8);
        private final Table table;private final int offset,frames;
        Mapping(Table table,int offset,int frames){this.table=table;this.offset=offset;this.frames=frames;}
        public Table table(){return table;}public int tableOffset(){return offset;}public int frames(){return frames;}
        public int nativeAddress(){return table.nativeAddress()+offset;}
    }
    public enum Animation {
        ANI_WATERFALL(Table.ANI_WATERFALL,0,1),
        ANI_SPINNING_DISC(Table.ANI_SPINNING_DISC,0,1),
        ANI_S1_SPRING(Table.ANI_S1_SPRING,0,2),
        ANI_SPRING(Table.ANI_SPRING,0,1),
        ANI_MOVING_SPRING(Table.ANI_MOVING_SPRING,0,1),
        ANI_RING(Table.ANI_RING,0,1),
        ANI_FLOAT_BLOCK(Table.ANI_FLOAT_BLOCK,0,3),
        ANI_MONITOR(Table.ANI_MONITOR,0,10),
        ANI_ANTON(Table.ANI_ANTON,0,2),
        ANI_PATA_BATA(Table.ANI_PATA_BATA,0,2),
        ANI_MOSQUI1(Table.ANI_MOSQUI1,0,2),
        ANI_MOSQUI2(Table.ANI_MOSQUI2,0,2),
        ANI_TAMABBOH1(Table.ANI_TAMABBOH1,0,2),
        ANI_TAMABBOH2(Table.ANI_TAMABBOH2,0,2),
        ANI_TAMABBOH_MISSILE(Table.ANI_TAMABBOH_MISSILE,0,1),
        ANI_TAGA_TAGA1(Table.ANI_TAGA_TAGA1,0,2),
        ANI_TAGA_TAGA2(Table.ANI_TAGA_TAGA2,0,2),
        ANI_SPRING_BOARD(Table.ANI_SPRING_BOARD,0,5),
        ANI_FLAP_DOOR_V(Table.ANI_FLAP_DOOR_V,0,1),
        ANI_FLYING_ANIMAL(Table.ANI_FLYING_ANIMAL,0,2),
        ANI_GROUND_ANIMAL(Table.ANI_GROUND_ANIMAL,0,1),
        ANI_BIG_RING_FLASH(Table.ANI_BIG_RING_FLASH,0,1),
        ANI_ROBOT_GENERATOR(Table.ANI_ROBOT_GENERATOR,0,1),
        ANI_METAL_SONIC_HOLOGRAM(Table.ANI_METAL_SONIC_HOLOGRAM,0,2),
        ANI_POWERUP(Table.ANI_POWERUP,0,9),
        ANI_TUNNEL_DOOR_SPLASH(Table.ANI_TUNNEL_DOOR_SPLASH,0,3),
        ANI_TUNNEL_DOOR(Table.ANI_TUNNEL_DOOR,0,1),
        ANI_TUNNEL_WATERFALL(Table.ANI_TUNNEL_WATERFALL,0,1),
        ANI_EXPLOSION(Table.ANI_EXPLOSION,0,2),
        ANI_CHECKPOINT(Table.ANI_CHECKPOINT,0,1),
        ANI_BIG_RING(Table.ANI_BIG_RING,0,1),
        ANI_SIGNPOST(Table.ANI_SIGNPOST,0,1),
        ANI_FLOWER_CAPSULE(Table.ANI_FLOWER_CAPSULE,0,2),
        ANI_FLOWER(Table.ANI_FLOWER,0,4);
        private final Table table;private final int offset,scripts;
        Animation(Table table,int offset,int scripts){this.table=table;this.offset=offset;this.scripts=scripts;}
        public Table table(){return table;}public int tableOffset(){return offset;}public int scripts(){return scripts;}
    }
    public record IdWrite(String sourcePath,int line,String nativeLabel,int objectId) { }
    private static final List<IdWrite> ID_WRITES=List.of(
        new IdWrite("src/Level/_Objects/Sonic/Main.asm",184,"ObjSonic_MakeWaterfallSplash",14),
        new IdWrite("src/Level/_Objects/Sonic/Main.asm",522,"ObjSonic_Water",11),
        new IdWrite("src/Level/_Objects/Sonic/Main.asm",2437,"ObjSonic_DeadChkGone",59),
        new IdWrite("src/Level/_Objects/Checkpoint/Main.asm",94,"ObjCheckpoint_Init",19),
        new IdWrite("src/Level/_Objects/Explosion/Main.asm",33,"SpawnPoints",28),
        new IdWrite("src/Level/_Objects/Explosion/Main.asm",104,"ObjExplosion_Done",31),
        new IdWrite("src/Level/_Objects/Spring/Main.asm",47,"ObjMovingSpring_Init",10),
        new IdWrite("src/Level/_Objects/Ring/Main.asm",151,"ObjRing_Init",16),
        new IdWrite("src/Level/_Objects/Ring/Main.asm",353,"ObjLostRing_Init",17),
        new IdWrite("src/Level/_Objects/Monitor and Time Post/Main.asm",389,"ObjMonitor_Break",26),
        new IdWrite("src/Level/_Objects/Monitor and Time Post/Main.asm",398,"ObjMonitor_Break",24),
        new IdWrite("src/Level/Palmtree Panic/Objects/Platform/Main.asm",65,"ObjPlatform_Init",10),
        new IdWrite("src/Level/_Objects/Level End/Main.asm",235,"ObjCapsule_Explode",24),
        new IdWrite("src/Level/_Objects/Level End/Main.asm",285,"ObjCapsule_SpawnSeeds",21),
        new IdWrite("src/Level/_Objects/Level End/Main.asm",331,"ObjCapsule_Seed",31),
        new IdWrite("src/Level/_Objects/Level End/Main.asm",511,"ObjBigRing_Main",20),
        new IdWrite("src/Level/_Objects/Level End/Main.asm",841,"StartResults",58),
        new IdWrite("src/Level/_Objects/Game Over/Main.asm",76,"ObjGameOver_Init",59),
        new IdWrite("src/Level/_Objects/Title Card/Main.asm",55,"ObjTitleCard_Init",60),
        new IdWrite("src/Level/_Objects/Results/Main.asm",112,"ObjResults_WaitPLC",58),
        new IdWrite("src/Level/_Objects/Robot Generator/Main.asm",143,"ObjRobotGenerator_Exploding",24),
        new IdWrite("src/Level/Palmtree Panic/Objects/Projector/Main.asm",193,"ObjProjector_Exploding",24));
    /** Exact private-source view, never a caller-constructible ownership record. */
    public static final class View {
        private final CdR11bObjectData owner;private final Table table;private final CdAssetBytes bytes;
        private View(CdR11bObjectData owner,Table table,CdAssetBytes bytes){this.owner=owner;this.table=table;this.bytes=bytes;}
        public Table table(){return table;}public int nativeAddress(){return table.nativeAddress();}
        public CdAssetBytes bytes(){return bytes;}public boolean belongsTo(CdR11bObjectData actual){return owner==actual;}
    }
    public static final class ArtOwner {
        private final CdR11bObjectData owner;private final CdPpzDataLayout.Range range;
        private final int plc,ordinal,destination,output;
        private ArtOwner(CdR11bObjectData owner,CdPpzDataLayout.Range range,int plc,int ordinal,int destination,int output){this.owner=owner;this.range=range;this.plc=plc;this.ordinal=ordinal;this.destination=destination;this.output=output;}
        public CdPpzDataLayout.Range range(){return range;}public int plcId(){return plc;}public int entryOrdinal(){return ordinal;}
        public long nativeSourceAddress(){return 0x200000L+range.fileOffset();}
        public int vramDestination(){return destination;}public int outputBytes(){return output;}
    }
    public static final class UnownedDispatchRead extends UnsupportedOperationException {
        private final int id;private final long address;
        private UnownedDispatchRead(int id){super("Unowned R11B physical ObjectIndex read for byte$"+Integer.toHexString(id));this.id=id;this.address=0x203C38L+(id-1L)*4;}
        public int objectId(){return id;}public long nativeAddress(){return address;}
    }
    private final CdPpzProgrammeData data;private final CdProgrammeReader reader;
    private final Map<Table,View> views=new EnumMap<>(Table.class);
    private final Map<Animation,CdObjectAnimation.AnimationTable> animations=new EnumMap<>(Animation.class);
    private final List<Family> families=List.of(Family.values());
    private List<ArtOwner> artOwners;private CdObjectMappings mappings;
    private List<CdObjectMappings.DeclaredStaticPiece> monitorPieces;
    private CdRing.SourceData ringData;
    private CdReservedHud.Assets hud;
    private CdTitleCard.Assets title;
    private CdLevelHud.Assets font;
    private CdTimeStars.Assets powerup;
    private CdObjectVisualAssets anton;
    private CdObjectVisualAssets pataNormal,pataDamaged,mosquiNormal,mosquiDamaged,tamabbohNormal,tamabbohDamaged,tamabbohMissile;
    private CdPlayerObjectTouch scanner;
    private CdPlayerTouchResponse response;
    private CdR11bObjectData(CdPpzProgrammeData data,CdProgrammeReader reader){this.data=data;this.reader=reader;requireRetained();}
    static CdR11bObjectData fromReader(CdPpzProgrammeData data,CdProgrammeReader reader){
        Objects.requireNonNull(data);Objects.requireNonNull(reader);
        if(data.programme()!=CdPpzDataLayout.Programme.R11B||reader.definition()!=CdProgrammeDefinition.select(CdStagePayload.R11B.stage()))
            throw new IllegalArgumentException("Canonical original USA R11B object DATA required");
        if(!data.ownsReader(reader))throw new IllegalArgumentException("Foreign original R11B object reader");
        return new CdR11bObjectData(data,reader);
    }
    public void requireRetained(){
        if(!data.ownsReader(reader)||reader.definition()!=CdProgrammeDefinition.select(CdStagePayload.R11B.stage()))
            throw new IllegalArgumentException("Foreign original R11B object source relation");
        reader.retainedStandardPpzImage();
    }
    boolean ownsReader(CdProgrammeReader actual){return reader==actual;}

    /** Exact original-layout relation only; equal physical rows do not confer source or World authority. */
    public void requirePlacement(CdObjectPlacementRuntime actual){
        Objects.requireNonNull(actual);requireRetained();
        if(actual.placements()!=reader.placements())throw new IllegalArgumentException("Foreign original Past placement reader");
    }
    /** Same retained Reader product; pure immutable terrain DATA, never a current-floor permit. */
    public CdStageTerrain terrain()throws IOException{requireRetained();return reader.terrain();}
    /** ObjAnton uses the same native algorithm in A/B. These original B roots remain
     * distinct: ANI20B1F6, MAP20B202, four frames/two scripts and the real22-tile bound.
     * Tile selection separately borrows the existing72-byte directory at its reached read. */
    public synchronized CdObjectVisualAssets anton(){
        requireRetained();if(anton==null)anton=CdObjectVisualAssets.decode(
            view(Table.MAP_SPR_ANTON).bytes().copy(),4,view(Table.ANI_ANTON).bytes().copy(),2,22);
        return anton;
    }
    /** Same retained Reader player/cache; DATA only, never an invocation or current-art permit. */
    public CdStagePlayerSource playerSource()throws IOException{requireRetained();return reader.playerSource();}
    /** Original B finite roles, decoded once through the existing shared visual decoder.
     * The 18-byte Pata table is shared by its two native mapping roots. */
    public synchronized CdObjectVisualAssets pataBataNormal(){
        requireRetained();if(pataNormal==null)pataNormal=CdObjectVisualAssets.decode(view(Table.MAP_SPR_PATA_BATA1).bytes().copy(),3,view(Table.ANI_PATA_BATA).bytes().copy(),2,50);return pataNormal;
    }
    public synchronized CdObjectVisualAssets pataBataDamaged(){
        requireRetained();if(pataDamaged==null)pataDamaged=CdObjectVisualAssets.decode(view(Table.MAP_SPR_PATA_BATA2).bytes().copy(),3,view(Table.ANI_PATA_BATA).bytes().copy(),2,50);return pataDamaged;
    }
    public synchronized CdObjectVisualAssets normalMosqui(){
        requireRetained();if(mosquiNormal==null)mosquiNormal=CdObjectVisualAssets.decode(view(Table.MAP_SPR_MOSQUI1).bytes().copy(),5,view(Table.ANI_MOSQUI1).bytes().copy(),2,70);return mosquiNormal;
    }
    public synchronized CdObjectVisualAssets damagedMosqui(){
        requireRetained();if(mosquiDamaged==null)mosquiDamaged=CdObjectVisualAssets.decode(view(Table.MAP_SPR_MOSQUI2).bytes().copy(),5,view(Table.ANI_MOSQUI2).bytes().copy(),2,70);return mosquiDamaged;
    }
    /** The native damaged root is inside the same62-byte body, at +22; do not trim/rebase it. */
    public synchronized CdObjectVisualAssets tamabbohNormal(){
        requireRetained();if(tamabbohNormal==null)tamabbohNormal=CdObjectVisualAssets.decode(view(Table.MAP_SPR_TAMABBOH1).bytes().copy(),3,view(Table.ANI_TAMABBOH1).bytes().copy(),2,59,Map.of(0,12));return tamabbohNormal;
    }
    public synchronized CdObjectVisualAssets tamabbohDamaged(){
        requireRetained();if(tamabbohDamaged==null)tamabbohDamaged=CdObjectVisualAssets.decodeIndexed(view(Table.MAP_SPR_TAMABBOH1).bytes().copy(),22,3,28,62,view(Table.ANI_TAMABBOH2).bytes().copy(),2,59);return tamabbohDamaged;
    }
    public synchronized CdObjectVisualAssets tamabbohMissile(){
        requireRetained();if(tamabbohMissile==null)tamabbohMissile=CdObjectVisualAssets.decode(view(Table.MAP_SPR_TAMABBOH_MISSILE).bytes().copy(),4,view(Table.ANI_TAMABBOH_MISSILE).bytes().copy(),1,59);return tamabbohMissile;
    }
    /** Native Ring/Main20-35,150-174, using the existing visual decoder and immutable source roles. */
    public synchronized CdRing.SourceData ringData(){
        requireRetained();if(ringData==null){
            var raw=view(Table.OBJ_RING_DELTAS).bytes();var deltas=new ArrayList<CdR11aObjectAssets.Delta>();
            for(int i=0;i<16;i++)deltas.add(new CdR11aObjectAssets.Delta(raw.signedByte(i*2),raw.signedByte(i*2+1)));
            var visuals=CdObjectVisualAssets.decode(view(Table.MAP_SPR_RING).bytes().copy(),9,view(Table.ANI_RING).bytes().copy(),1,14);
            ringData=new CdRing.SourceData(deltas,Table.MAP_SPR_RING.nativeAddress(),0xA7AE,visuals);
        }return ringData;
    }
    public synchronized CdReservedHud.Assets hudAssets(){
        requireRetained();if(hud==null)hud=new CdReservedHud.Assets(Table.MAP_SPR_HUD.nativeAddress(),Table.MAP_SPR_MONITOR_TIME.nativeAddress());return hud;
    }
    public synchronized CdTitleCard.Assets titleAssets(){
        requireRetained();if(title==null)title=new CdTitleCard.Assets(view(Table.TITLE_CHARACTERS).bytes(),Table.MAP_SPR_TITLE_CARD.nativeAddress());return title;
    }
    /** Borrow the actual B directory/root through the existing shared Shield/animation kernel.
     * A value is DATA only: it cannot create slot6, carry a flag or authorize World invocation. */
    public synchronized CdTimeStars.Assets powerupAssets(){
        requireRetained();if(powerup==null)powerup=new CdTimeStars.Assets(
            animation(Animation.ANI_POWERUP),Table.MAP_SPR_POWERUP.nativeAddress());return powerup;
    }
    public synchronized CdLevelHud.Assets font(){
        requireRetained();if(font==null)font=new CdLevelHud.Assets(view(Table.HUD_NUMBERS).bytes(),view(Table.HUD_RESET).bytes(),view(Table.HUD_DIVISORS).bytes());return font;
    }
    /** These are immutable common semantic consumers, not a publication or handler permit. */
    public synchronized CdPlayerObjectTouch touchScanner(){
        requireRetained();if(scanner==null){var raw=view(Table.OBJ_COL_SIZES).bytes();var sizes=new ArrayList<CdR11aObjectAssets.TouchSize>();
            for(int i=0;i<63;i++)sizes.add(new CdR11aObjectAssets.TouchSize(raw.unsignedByte(i*2),raw.unsignedByte(i*2+1)));
            scanner=new CdPlayerObjectTouch(sizes);
        }return scanner;
    }
    public synchronized CdPlayerTouchResponse touchResponse(){
        requireRetained();if(response==null)response=new CdPlayerTouchResponse(view(Table.ENEMY_POINTS).bytes());return response;
    }
    public String wholeDiscSha256(){requireRetained();return reader.discSha();}
    public String mmdSha256(){requireRetained();return reader.mmdSha();}
    public synchronized View view(Table role){
        Objects.requireNonNull(role);requireRetained();var old=views.get(role);if(old!=null)return old;
        var bytes=role==Table.DISPATCH?reader.view(CdPpzProgrammeProfiles.R11PastLoadingView.OBJECT_INDEX)
            :CdProgrammeReader.retainedSpan(reader.retainedStandardPpzImage(),role.fileOffset(),role.bytes(),"Original R11B object role");
        if(bytes.length()!=role.bytes()||!role.sha256().equals(CdR11aProfile.sha256(bytes.copy())))
            throw new IllegalArgumentException("Original R11B object role differs: "+role);
        var selected=new View(this,role,bytes);views.put(role,selected);return selected;
    }
    public synchronized void requireView(View actual){
        Objects.requireNonNull(actual);requireRetained();
        if(actual.owner!=this||views.get(actual.table)!=actual)throw new IllegalArgumentException("Foreign R11B object view");
    }
    public List<Family> families(){requireRetained();return families;}
    public Optional<Family> dispatch(int objectIdByte){
        if((objectIdByte&~255)!=0)throw new IllegalArgumentException("Physical object ID BYTE required");
        requireRetained();if(objectIdByte==0)return Optional.empty();
        // Native RunObjects: add.w twice then -4(a1,d0.w). NO placement/bit7 mask here.
        if(objectIdByte>64)throw new UnownedDispatchRead(objectIdByte);
        var selected=families.get(objectIdByte-1);long raw=view(Table.DISPATCH).bytes().unsignedLong((objectIdByte-1)*4);
        if(raw!=Integer.toUnsignedLong(selected.handlerAddress()))throw new IllegalArgumentException("R11B dispatch pointer differs");
        return Optional.of(selected);
    }
    public Family reservedHandler(ReservedRole role){
        int id=switch(Objects.requireNonNull(role)){case PRIMARY_SONIC->1;case SECONDARY_SONIC->2;case SHIELD_AND_STARS->3;case HUD_POINTS->0x1C;case TITLE_CARD->0x3C;};
        return dispatch(id).orElseThrow();
    }
    public List<IdWrite> idWrites(){requireRetained();return ID_WRITES;}
    public synchronized CdObjectAnimation.AnimationTable animation(Animation role){
        Objects.requireNonNull(role);requireRetained();var old=animations.get(role);if(old!=null)return old;
        var selected=new CdObjectAnimation.AnimationTable(view(role.table()).bytes(),role.tableOffset(),role.scripts());
        animations.put(role,selected);return selected;
    }
    public synchronized List<CdObjectMappings.DeclaredStaticPiece> monitorItemPieces(){
        requireRetained();if(monitorPieces==null){var source=view(Table.MAP_SPR_MONITOR_TIME);monitorPieces=CdObjectMappings.monitorItemStaticPieces(source.bytes(),source.nativeAddress());}
        return monitorPieces;
    }
    public synchronized CdObjectMappings mappings(){
        requireRetained();if(mappings!=null)return mappings;
        var tables=new ArrayList<CdObjectMappings.DeclaredTable>();
        var sonic=CdPpzDataLayout.Programme.R11B.range("PlayerMappings");var sonicBytes=reader.view(sonic);
        // Borrow the already-decoded actual198 format; no Genesis167 or TT188 relabel.
        tables.add(new CdObjectMappings.DeclaredTable(0x200000+sonic.fileOffset(),sonicBytes,0,reader.sonic().mappingFrames().size()));
        for(var mapping:Mapping.values())tables.add(new CdObjectMappings.DeclaredTable(mapping.table().nativeAddress(),view(mapping.table()).bytes(),mapping.tableOffset(),mapping.frames()));
        var selected=CdObjectMappings.declaredSourceViews(tables,monitorItemPieces());mappings=selected;return selected;
    }
    public synchronized List<ArtOwner> artOwners(){
        requireRetained();if(artOwners!=null)return artOwners;
        var owners=new ArrayList<ArtOwner>();
        for(var list:reader.loadTables().plcs())for(int ordinal=0;ordinal<list.body().entries().size();ordinal++){
            var entry=list.body().entries().get(ordinal);var key=reader.loadSource(entry.sourceNativeAddress());
            if(!(key instanceof CdPpzDataLayout.Range range)||CdPpzDataLayout.Programme.R11B.ranges().stream().noneMatch(original->original==range))
                throw new IllegalArgumentException("Foreign original R11B PLC source declaration");
            owners.add(new ArtOwner(this,range,list.nativeId(),ordinal,entry.vramAddress(),reader.loadOutputBytes(key)));
        }
        artOwners=List.copyOf(owners);return artOwners;
    }
    private void requireArt(ArtOwner actual){
        Objects.requireNonNull(actual);requireRetained();
        if(actual.owner!=this||artOwners==null||!artOwners.contains(actual))throw new IllegalArgumentException("Foreign R11B art owner");
    }
    public CdAssetBytes nativeArt(ArtOwner actual){requireArt(actual);return reader.nativeInput(actual.range);}
    public CdNemesisAsset.Decoded decodedArt(ArtOwner actual)throws IOException{requireArt(actual);return reader.decoded(actual.range);}
}
