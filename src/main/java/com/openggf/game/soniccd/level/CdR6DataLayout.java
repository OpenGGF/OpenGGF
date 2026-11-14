package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;

/**
 * Independently authenticated original USA Wacky Workbench DATA declarations.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Source/media reverse proofs and reproducible audits live in the external R6 DATA packet.
 * Ranges describe exact canonical includes or separately named finite read views.
 * No raw assets, decoder, stage loader, live invocation or terrain admission.
 */
final class CdR6DataLayout {
    private CdR6DataLayout() { }
    record Range(String key,int fileOffset,int length,String sha256) { }
    record Operand(int fileOffset,String hex) { }
    record Terrain(int blockBytes,int patternBytes,int chunkBytes,int foregroundColumns,
                   int foregroundRows,int backgroundColumns,int backgroundRows,int collisionIds) { }
    record LayoutSlot(int slot,int signedOffset,int columns,int rows,int canonicalBytes) { }
    enum HandlerState { NATIVE_NULL, SOURCE_HANDLER }
    record Dispatch(int id,String handler,int address,HandlerState state) { }
    enum Programme {
        R61A(6072,"1c5617246f4de14aab548bf3663d27a34f566399ede381f1ceec1b1feaa01bce",1024,1,OptionalInt.empty(),new Terrain(5440,17728,59904,36,8,24,3,600),rangesR61A(),operandsR61A(),dispatchR61A(),layoutsR61A(),461,306),
        R61B(6200,"8b93ba00d5e78789295c2562429770bcebf73a95fa0a54aad3a6b41bc05a7c22",1024,0,OptionalInt.empty(),new Terrain(5600,18944,58880,36,8,24,3,800),rangesR61B(),operandsR61B(),dispatchR61B(),layoutsR61B(),461,307),
        R61C(6328,"c54acef7bd85f3f89785b370d5da86a120b87dd8ee4a84ac3749e3d588989e32",1024,2,OptionalInt.of(1),new Terrain(5440,18176,59392,36,8,25,3,700),rangesR61C(),operandsR61C(),dispatchR61C(),layoutsR61C(),461,302),
        R61D(6456,"6ced25b1aa80c59c27e6fb6daf765eb20b7390a0b20de6b9f2adbd769c300654",1024,2,OptionalInt.of(0),new Terrain(6656,21056,59392,36,8,24,3,900),rangesR61D(),operandsR61D(),dispatchR61D(),layoutsR61D(),461,302),
        R62A(6584,"45e0531e07b70840f5ece558fdb50cfd9b16621275e2fa6bdc9caa4ad33f9b8b",1025,1,OptionalInt.empty(),new Terrain(5920,20736,59904,37,8,24,3,800),rangesR62A(),operandsR62A(),dispatchR62A(),layoutsR62A(),358,299),
        R62B(6712,"9b732fdccc47126a58730f4b8ffc7f9285f79e671abce4d730fc27e1b94ab829",1025,0,OptionalInt.empty(),new Terrain(6048,20064,58880,37,8,24,3,800),rangesR62B(),operandsR62B(),dispatchR62B(),layoutsR62B(),358,269),
        R62C(6840,"6bd11e15bf21532fb9c2757e119319f24cac6f4a50f191d9c64046aa2d1c4cdc",1025,2,OptionalInt.of(1),new Terrain(5920,21248,59392,37,8,25,3,800),rangesR62C(),operandsR62C(),dispatchR62C(),layoutsR62C(),358,286),
        R62D(6968,"eaafbb4daa98ef9231b4e072618fa7b1e0e24228f5c09a4b04c9b590d1a8b301",1025,2,OptionalInt.of(0),new Terrain(6688,22336,59904,37,8,24,3,900),rangesR62D(),operandsR62D(),dispatchR62D(),layoutsR62D(),358,286),
        R63C(7096,"9708c99810fa001eb5593497f8e96d1008391f73348e672c94354c804e4d0c13",1026,2,OptionalInt.of(1),new Terrain(5920,21440,60416,15,8,10,3,900),rangesR63C(),operandsR63C(),dispatchR63C(),layoutsR63C(),72,72),
        R63D(7224,"e3799bdd69c593996af6ad245e1b3d354d3ba4cedcc49503389f19e730fe7173",1026,2,OptionalInt.of(0),new Terrain(6528,21024,60416,15,8,12,3,900),rangesR63D(),operandsR63D(),dispatchR63D(),layoutsR63D(),72,72);
        private final int isoLba,rawZoneAct,timeZone,placementCount,periodActiveCount;
        private final OptionalInt goodFuture;
        private final String fileSha256;
        private final Terrain terrain;
        private final List<Range> ranges;
        private final List<Operand> operands;
        private final List<Dispatch> dispatch;
        private final List<LayoutSlot> layouts;
        Programme(int lba,String sha,int rawZoneAct,int timeZone,OptionalInt goodFuture,Terrain terrain,List<Range> ranges,List<Operand> operands,
                  List<Dispatch> dispatch,List<LayoutSlot> layouts,int placements,int active) {
            isoLba=lba;fileSha256=sha;this.rawZoneAct=rawZoneAct;this.timeZone=timeZone;this.goodFuture=Objects.requireNonNull(goodFuture);this.terrain=terrain;this.ranges=List.copyOf(ranges);
            this.operands=List.copyOf(operands);this.dispatch=List.copyOf(dispatch);this.layouts=List.copyOf(layouts);
            placementCount=placements;periodActiveCount=active;
        }
        CdNativeFile file() { return CdNativeFile.valueOf(name()); }
        CdStagePayload payload() { return CdStagePayload.valueOf(name()); }
        int fileBytes() { return 262144; }
        int isoLba() { return isoLba; }
        int rawZoneActWord() { return rawZoneAct; }
        int timeZoneByte() { return timeZone; }
        /** Selection constrains only C/D; A/B retain the live progress owner's BYTE. */
        OptionalInt goodFutureConstraint() { return goodFuture; }
        int goodFutureByte() { return goodFuture.orElseThrow(()->new UnsupportedOperationException(
                "Present/Past identity does not constrain retained goodFuture")); }
        String fileSha256() { return fileSha256; }
        CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
        Terrain terrain() { return terrain; }
        List<Range> ranges() { return ranges; }
        List<Operand> operands() { return operands; }
        List<Dispatch> dispatch() { return dispatch; }
        List<LayoutSlot> layouts() { return layouts; }
        int placementCount() { return placementCount; }
        int periodActiveCount() { return periodActiveCount; }
        Range range(String key) {
            return ranges.stream().filter(r->r.key().equals(key)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unowned R6 declaration: "+key));
        }
    }
    private static List<Range> ranges(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new Range(w[0],Integer.parseInt(w[1],16),Integer.parseInt(w[2]),w[3]);}).toList();
    }
    private static List<Operand> operands(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new Operand(Integer.parseInt(w[0],16),w[1]);}).toList();
    }
    private static List<Dispatch> dispatch(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new Dispatch(Integer.parseInt(w[0]),w[1],Integer.parseInt(w[2],16),HandlerState.valueOf(w[3]));}).toList();
    }
    private static List<LayoutSlot> layouts(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new LayoutSlot(Integer.parseInt(w[0]),Integer.parseInt(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]),Integer.parseInt(w[4]));}).toList();
    }
    private static List<Range> rangesR61A() { return ranges("""
        Pal_S1Title 5E8 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        Pal_S1LevSel 668 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        Pal_Sonic 6E8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        Pal_Level 708 96 b054ef40e428fdd09dbe54833c8459de568953f5cf772c137368012e14b229f9
        Pal_LevelUnk 768 96 cc58aa9df8487f55bac536037063a28d69817ce141b23870e2ddfdd352a25bf4
        LevelStartLoc 29CC 4 0ff22b0ebc98234a94e2af8ed46c0cac7c355f5914b918801dab610520642f8f
        Ani_Sonic 5B0E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include7 79D8 3696 2198042d18a51b5d9a8e19d47fb07631478d3dc3708dbfdcf9bf768d13428f17
        Ani_Freezer 8D12 124 7dd843ba59f15a8d83143747c0514fcd1a15443349d945639b7c6cecfd7a6196
        MapSpr_Freezer 8D8E 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        MapSpr_Launcher 8FFA 32 7d408bb6389cdd03bcc8fe5e1401a9f48baa71be09d936a4b21356509f6c4101
        MapSpr_Boulder 9C14 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        Ani_S1Spring A0E8 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        MapSpr_S1Spring A104 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        Ani_Spring A152 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        Include15 A160 199 f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd
        Ani_MovingSpring A228 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        Ani_Ring A6B2 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        MapSpr_Ring A6BA 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        MapSpr_S1BigRing A6FE 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        MapSpr_S1BigRingFlash A7A0 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        Ani_Monitor ADC4 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MapSpr_MonitorTime AE4C 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        MapSpr_HUD B164 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        Pal_FlowerCapsule BDA8 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Ani_BigRingFlash BDC8 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        MapSpr_BigRingFlash BDD4 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        Art_BigRingFlash BF74 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        Include29 C1D8 56 ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b
        ObjResults_InitDataEnd C66E 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include31 C8DC 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        MapSpr_TitleCard C91C 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        MapSpr_Piston CB86 38 0f64fcdfc83bf8f1e0bd0b5bd583454630feddb87ff1c8a399bf5a16d2efcc19
        MapSpr_ElectricBeams CE7C 28 fb7718f7c401526890cd0f7bc0e9fba1941847031aad93f436d41a3ba26b57e9
        MapSpr_Platform CF9A 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        MapSpr_BouncePlatform D0DC 14 d683c689a8db89ee3c5d619a208fd4cfd4986f79d828bf406ca0afe706ba9176
        MapSpr_Switch D244 28 f6192af7512814e2b4bfa3b7798168e00ee58393139140ec5dd09ea59c23e501
        MapSpr_Door D3D0 38 48c82f3fc3d5cfffcb78727cadef9cc1d41fe255a09f7a58899a29d6e55512d0
        MapSpr_TubeDoor D564 44 60fb39b6d052b43321f600bc7e433a3966719e7e7636479504cd71d82bcdc677
        Ani_SpinPlatform D870 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        MapSpr_SpinPlatform D87A 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        MapSpr_BataPyon DB24 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        MapSpr_BataPyonDamaged DB48 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        Ani_PohBee DE0A 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        MapSpr_PohBee DE16 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        MapSpr_PohBeeDamaged DEFE 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        Ani_PohBeeMissile E002 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        MapSpr_PohBeeMissile E008 68 a3b3819c4afc0ec8f373d9872b3e15195c2632174cd1c2049c1339359d5748b3
        Ani_Semi E1F8 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        MapSpr_Semi E200 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        MapSpr_SemiDamaged E2E8 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        Ani_Minomusi E74A 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        Include55 E798 362 679f33af0ed8e296c5804dfb488a0e0bd01e8d7ff3c9a931d6b2edb34bbba217
        Ani_FlyingAnimal EB26 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        Ani_GroundAnimal EB68 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        MapSpr_FlyingAnimal EBA4 20 31f30e63c2869e4c31f1b5e29130605a93afa8f0fbc35140341017422e99267a
        MapSpr_GroundAnimal EBB8 36 1b283488ac2fb0b949d9909cd08810121a61df987e0c8db3ca27dd7f6f15dce5
        MapSpr_SnakeBlocks EE78 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        MapSpr_EggmanStatue F060 32 c27abfc66d1a331254315b05cfcb2b31a3cdb2f84f8fead7e4a2a29b4c08176d
        Ani_Seesaw F3C0 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        MapSpr_Seesaw F3E8 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        Ani_RobotGenerator F65E 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        MapSpr_RobotGenerator F666 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        Include68 F8E4 22 5db48832c26bbeef89ddc90398e0fa63226a24a94a7369696ecb7dbd1ed5d0c4
        Ani_MetalSonicHologram FB90 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        MapSpr_Projector FBD4 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        Include71 FF3E 194 af4296c79c01f04b311a26a47b7c548ca513b1a9cb84877c63fe2609434b4df0
        LevelChunks 10000 59904 dfa6ad4619a869f983a5f2e4a18377247b850b650cbacf1c9b0c25a65b77e72a
        Include76 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include77 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include78 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include85 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        Art_Sonic 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        MapSpr_Sonic 2DF20 2444 9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77
        DPLC_Sonic 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        Art_Points 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        Art_FlowerCapsule 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        Art_BigRing 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        Art_GoalPost 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        Art_Signpost 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        Art_Results 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        Art_TimeOver 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        Art_GameOver 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        Art_TitleCard 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        Art_Shield 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        Art_InvStars 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        Art_TimeStars 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Art_DiagonalSpring 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        Art_Springs 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        Art_MonitorTimePosts 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        Art_Explosions 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        Art_Rings 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        Art_LifeIcon 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        Art_HUDNumbers 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        Art_HUD 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        Art_Checkpoint 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        Ani_Flower 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        MapSpr_Flower 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        Art_Flower 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        Art_TitleCardText 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        Art_ElecSparkOrb 33B0C 256 c7dd195c1e03f562414d424ed6bb86ad98481aab1a08d9c6bd3e1a5bd0f463a8
        Art_ElectricSparks 33C0C 768 c905a527ec0937a9bbdceee6c8bfa6da50bf8198c451a5e886e65165795edd36
        Art_Siren 33F0C 512 33788d455245ddc6efee17cafb2fd7e6e82479c540f334f7d15407706bbb70e5
        Art_Launcher 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        Art_Freezer 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        Art_IceBlock 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        ColAngleMap 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        ColHeightMap 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        ColWidthMap 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        LevelCollision 367F6 600 f9aef419f768683bc0aa1021644404bc63db75af3f7c4d24d8c9e7632cbd94ab
        Include124 36A96 290 25626797bd64bf0c615d7bd8e70cabe7416766c455c45bc4e58dc870a89efa13
        Include125 36BB8 74 b168695a7d63cef98326173df650db621ea0ea3a188a87dacbd95a753974aa31
        LevelBlocks 36DFC 3808 b4772fecd56f425cb07ebb1a14e88401f8e5570ed1bf5b0057df2cbab9fc70fa
        Art_LevelTiles 37CDC 7728 93ae25c9709bafe996c6d63fc9d1b21e82d08e37f4aeb90b99d2e15fde411474
        Ani_Powerup 39B0C 296 ecd3e5667b53fd56bf9b9c7fa3e9e09cbb957efe90232419060f8197f0f50141
        MapSpr_Powerup 39C34 274 d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc
        Ani_TunnelDoorSplash 39D46 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        MapSpr_TunnelDoorSplash 39D5E 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        Ani_TunnelDoor 39DC2 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        MapSpr_TunnelDoor 39DD0 28 7260a75372713a37c9acb8b62f653a3d17c824b5e2eb88d6661789dd9cdaca92
        Ani_TunnelWaterfall 39DEC 8 8dd6dfce410856a8d39936416a5ef8fd0ec663197bd69d9d62305d2a70ccfb20
        MapSpr_TunnelWaterfall 39DF4 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        Ani_Explosion 39E0C 20 9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649
        MapSpr_Explosion 39E20 146 0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44
        Include140 39EB2 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        Ani_Checkpoint 39FB2 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        MapSpr_Checkpoint 39FB8 66 709d0648dfb32425f46281c236ededaef80750fc6250062f3bce77f9427a446f
        Ani_BigRing 39FFA 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        MapSpr_BigRing 3A002 176 c6edd39fb1e1cc977c264d87e2301816d01ff2c10660fb63f89d9148afdb30c4
        Ani_Signpost 3A0B2 14 9912394f852b914651884191ea8532d60421a3bd40293510a3f356f86c69e691
        MapSpr_GoalSignpost 3A0C0 126 610a02baa99d911f509b5fc06a1e4c0c72bc7ef71fa56ecd72640e409734bad8
        Art_Piston 3A244 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        Art_PlatformPresent 3A382 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        Art_PlatformPast 3A728 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        Art_PlatformFuture 3AAC8 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        Art_BouncePlatform 3AE68 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        Art_Switch 3AF48 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        Art_Door 3AF9A 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        Art_BossDoor 3B06E 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        Art_Crusher 3B136 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        Art_CrusherGoodFuture 3B2EC 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        Art_TubeDoor 3B41E 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        Art_ElecOrbPresent 3B63E 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        Art_ElecOrbPast 3B75E 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        Art_ElecOrbGoodFuture 3B88E 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        Art_ElecOrbBadFuture 3B9BE 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        Art_BataPyon 3BAF0 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        Art_PohBee 3BD64 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        Art_Minomusi 3C0C4 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        Art_MinomusiBomb 3C386 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        Art_SnakeBlock 3C448 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        Art_Semi 3C528 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        Art_Seesaw 3C780 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        Art_EggmanStatue 3CA10 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        Art_SpikeBall 3CC4A 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        Art_Animals 3CD22 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        Art_ProjAnimals 3CF16 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        Art_RobotGenWithered 3D104 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        Art_RobotGenerator 3D250 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        Include178 3D802 94 93586c5c7bdf6d3f40dcea2908849288f8f73077965a8011df7ce48cb22e8c4b
        Include179 3D860 434 61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef
        Include182 3DE12 408 090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30
        Include183 3DFAA 162 48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b
        Include184 3E04C 112 c75cc362a60b81e6968c5adc66c6d059734dbb690de2775b4be130c6f0c56fe3
        Include185 3E0BC 370 06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941
        Include186 3E22E 278 e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd
        Include187 3E344 256 0578a33bff801847439bd1321fbbb3873f825a72de869789864298992e7180dd
        Include188 3E444 922 01240ff3f0680660fe841c0dac29c27d3f68e2ef4924046aa3779a7c1d45db5e
        Include189 3E7DE 438 2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892
        Include190 3E994 1174 b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef
        Include191 3EE2A 788 8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001
        Include192 3F13E 460 ed5988bf0619f46577a62cd7cf60782055f1747995567d085891bfc4c9123b0c
        Include193 3F30A 282 cd5805de0d82bdc5e578cd095f77519a6c6d06ac198038e700b6fb4a28f517be
        Include196 3F5A4 436 401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca
        Include197 3F758 250 feb698ed7e976af97b7746f243640fab416fef73c83867a37e64f574ec3d5c28
        Include198 3F852 778 b1fc4a9076b37ee82138effe60b948aa109549d0b2020b80c6f142d9aac00aaa
        Include204 3FEFA 262 a74df89250bc6eea1da5fc994bc2fe3b681c42e4771bb21623bd798bd317f04f
        MapSpr_MovingSpring A22E 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Ani_SemiBomb E47C 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        MapSpr_SemiBomb E482 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Ani_SpikeBomb F14E 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        MapSpr_SpikeBomb F154 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include73 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include74 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include75 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include79 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include80 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include81 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include82 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include83 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include84 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        Include126 36C06 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include127 36CD2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Ani_FlowerCapsule 3A13E 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        MapSpr_FlowerCapsule 3A172 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        Art_Projector 3D402 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        Include180 3DA12 800 2bf36603d2073fa5a3efd17a37a25dcc89871590609ebc1fcedd89ab3019f705
        Include181 3DD32 224 0282f436f8e01c1d14a3bdf40205b01c609f974d37dd0b2ff73f05162ba79e22
        Include194 3F426 306 d4044b489b14f1cb3a10c6147a82ab4ddfa227ca3baac11f2e86ed8f6c6660f6
        Include195 3F558 76 3887fb88ae462b07268a8ecc5d8168330b6513c728bcb17884cd4a0f0ce7deff
        Include199 3FB5C 462 cc7b32160bf4b2e0d855463e9bfd70c0145c613bac4347924a764fe07407d1bb
        Include200 3FD2A 106 a0954ff284e484377e62310d9f1727e0fe7ede03947e4bb190b2937319b3d929
        Include201 3FD94 8 a25b4c948749f079fcc82da8dd4c4fd06ce85150281185df2e43f9bb4d764e42
        Include202 3FD9C 278 556430a544ed986a5a6937637f17f9f3d180a19bd2b8fedae7513e8d97a75013
        Include203 3FEB2 72 966e345e0a34ef74e15a217a429cc6e9d92f5d95f2303d6358fcb75c847274f3
        DataChunks 10000 59904 dfa6ad4619a869f983a5f2e4a18377247b850b650cbacf1c9b0c25a65b77e72a
        DataMap 2DF20 2444 9836b535d15ee481a9a663bf651377791569f3ea9d91c6cd657ddc83cca3ae77
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5B0E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5ABE 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3C2A 240 d3247f9e9b6b829c53c5447c456f550bd0d49a4390fd441fdb1d53ab0eb89763
        DataPaletteDirectory 5B8 48 1fd34564e66442a3803c4bcf53c4ec675f2e1b5cf8a564688fe914ccf0e8b985
        DataLayoutDirectory 36A4E 72 0e23b7ddc2f970febfcd1ff740613a1ae0fe453a7c4fd03ee3bbe3efbe67235e
        DataStageData FE2C 16 36048b31959356d2f3faa65accfb787ef8b8ac9368bfc7c903a0676ba4d32062
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 79D8 3696 2198042d18a51b5d9a8e19d47fb07631478d3dc3708dbfdcf9bf768d13428f17
        PlcRead FE3C 258 018b0e7f27a77d5bea56d5e15e258a6e136bcdb4a0f26b2184c806959624d5e2
        PlcDirectory FE3C 38 77f3d0f40f66649c04eaa746345bc67641da8328448fab1e2a21f1b6be009e75
        PlcBody0 FE62 14 741be4fca28ffcc7bed9a3eab9cee3161a347ed51306b2f20b2b446e4fa453fe
        PlcBody1 FE70 92 664dc19ffbf06252f9139224b039342e1581c58db7a3ca570723f700bab38801
        PlcBody2 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody3 FE62 14 741be4fca28ffcc7bed9a3eab9cee3161a347ed51306b2f20b2b446e4fa453fe
        PlcBody4 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody5 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody6 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody7 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody8 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody9 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody10 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody11 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody12 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody13 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody14 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody15 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody16 FF22 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FECC 86 645d3059eda1111fd5a3911aec49e98a666e9bc324051fdbe32d8c8b5874e4d8
        PlcBody18 FF2A 20 0508b60af2d0d26d82ae17f9ae453a3108617312562bb33c8b978de9cdd89211
        PaletteRead0 5E8 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead1 5E8 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead2 668 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        PaletteRead3 6E8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 708 96 b054ef40e428fdd09dbe54833c8459de568953f5cf772c137368012e14b229f9
        PaletteRead5 768 96 cc58aa9df8487f55bac536037063a28d69817ce141b23870e2ddfdd352a25bf4
        LayoutRead0 36A96 290 25626797bd64bf0c615d7bd8e70cabe7416766c455c45bc4e58dc870a89efa13
        LayoutRead1 36BB8 74 b168695a7d63cef98326173df650db621ea0ea3a188a87dacbd95a753974aa31
        LayoutRead2 36C02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36C06 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36CCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36CD2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36A96 290 25626797bd64bf0c615d7bd8e70cabe7416766c455c45bc4e58dc870a89efa13
        LayoutRead13 36BB8 74 b168695a7d63cef98326173df650db621ea0ea3a188a87dacbd95a753974aa31
        LayoutRead14 36C02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36C06 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36CCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36CD2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36A96 290 25626797bd64bf0c615d7bd8e70cabe7416766c455c45bc4e58dc870a89efa13
        LayoutRead25 36BB8 74 b168695a7d63cef98326173df650db621ea0ea3a188a87dacbd95a753974aa31
        LayoutRead26 36C02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36C06 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36CCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36CD2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36DF4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36DF8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 206 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 20E 6 bb3619d7d1f5b3fd9c80b3a3b8d6c2ca3ddd8275a768d461909e1e859fb24fff
        CycleScript2 214 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 21C 6 8cf7ade7e8d7d767ebc76c7a40031865c357b1f0068100bf1d04cc0d809a365a
        CycleScript3 222 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 22A 6 decdbce3a202ed4b3bb9721a56e7f818e8b1df1fbe26527f7d9241071c7dc42a
        CycleScript4 230 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        CycleColors4 2E8 8 dd7cb482522a938b461c84c15c43796cadd553dfd14e2f42aff5b000a4f50221
        .CameraSects 3052 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        BGCameraSects 33BA 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        BGCameraSectIDs 3388 50 fbb1a3cfed993ae2860736696ddc974a29eba5e5c986566c32d3323a18b6ddec
        FiniteTable10 2908 44 77c28148b0ab750920e119faca0a22d718c9890c975070855a2cf0316f87ab86
        FiniteTable11 29D0 4 365475d471c68745e3881c3743e1669911103607f18d88d9db7292e29177c0bf
        FiniteTable12 2AE2 10 5ecb42b938a27d82df50a535a98f886e6a3d00b172c39a2376b90e6749aadce9
        InitialBounds 2908 12 d25187382f4deb1da60b2a3b5e2dd441bdbb8ea14d9b3cdb23a564a53640977f
        """); }
    private static List<Operand> operandsR61A() { return operands("""
        2FEC 267b0064
        3076 267b00da
        33D2 267b00e6
        BA1C 43f900239ffa
        B91E 43f90020bdc8
        6122 43f900239fb2
        6238 43f900239e0c
        632C 43f9002336bc
        E9AC 43fa0178
        E9F4 43fa0130
        89EE 43f900208d12
        EAE4 43fa0082
        FB80 43fa000e
        E666 43fa00e2
        A9D2 43f90020adc4
        AB52 43f90020adc4
        9D0E 43f90020a228
        DB88 43fa0280
        DFF8 43fa0008
        6192 43f900239b0c
        65FE 43f900239b0c
        66A2 43f900239b0c
        A4BA 43f90020a6b2
        A6A0 43f90020a6b2
        F5B2 43fa00aa
        F2A8 43fa0116
        E162 43fa0094
        E1EE 43fa0008
        BCA6 43f90023a0b2
        581E 43f900205b0e
        D5FA 43fa0274
        9EFC 43f90020a152
        9FA4 43f90020a152
        A00E 43f90020a152
        A0CC 43f90020a152
        64AA 43f900239dc2
        654E 43f900239d46
        63D8 43f900239dec
        B46C 43f9002330bc
        B4CE 43f9002330bc
        B54A 43f9002330bc
        B5BA 43f9002330bc
        B660 43f9002330bc
        1B7A 43f900232f3c
        5D9A 43f900220000
        119A 45f9002346f6
        125A 45f9002346f6
        130A 45f9002346f6
        13BA 45f9002346f6
        11C8 45f9002347f6
        1288 45f9002347f6
        1426 43f9002347f6
        142C 45f9002347f6
        1460 43f9002347f6
        146E 43f9002347f6
        1474 45f9002347f6
        1338 45f9002357f6
        13E8 45f9002357f6
        1466 45f9002357f6
        5D6A 45f90022e8ac
        1672 45f90020fe2c
        3404 45f90020fe2c
        2954 43f9002029cc
        E516 43fa0280
        E520 43fa02ce
        DBE6 43fa022e
        DBF6 43fa0306
        E0B6 43fa0148
        E0C0 43fa0226
        F4E0 43fa0402
        C266 45f90020c8dc
        24DC 43f90020fe3c
        250E 43f90020fe3c
        264A 43f90020fe3c
        BD96 43f90020bda8
        5930 43f900205cea
        5A0A 43f900205ba6
        59FC 43f900205c7e
        59B6 43f900205b96
        59A8 43f900205d18
        598C 43f900205b9e
        597E 43f900205c6c
        593C 43f900205b8e
        5922 43f900205d10
        5A8A 43f900205c66
        5948 43f900205b86
        5A96 43f900205c60
        100 4ef90020011e
        10C 4ef9002020d2
        112 4ef900201c48
        3818 43f900203c2a227100fc4e91
        """); }
    private static List<Dispatch> dispatchR61A() { return dispatch("""
        1 ObjSonic 203D88 SOURCE_HANDLER
        2 ObjSonic 203D88 SOURCE_HANDLER
        3 ObjPowerup 206564 SOURCE_HANDLER
        4 ObjLauncher 208E4C SOURCE_HANDLER
        5 ObjFreezer 208954 SOURCE_HANDLER
        6 ObjTestBadnik 20612E SOURCE_HANDLER
        7 ObjTunnelPath 20901A SOURCE_HANDLER
        8 ObjGoddessStatue 20884E SOURCE_HANDLER
        9 ObjNull 203D1A NATIVE_NULL
        10 ObjSpring 209D6E SOURCE_HANDLER
        11 ObjTunnelDoorSplash 2064FE SOURCE_HANDLER
        12 ObjTunnelDoorSplashSet 2064C6 SOURCE_HANDLER
        13 ObjTunnelDoor 2063EE SOURCE_HANDLER
        14 ObjSpinSplash 206398 SOURCE_HANDLER
        15 ObjMovingSpring 209C22 SOURCE_HANDLER
        16 ObjRing 20A23E SOURCE_HANDLER
        17 ObjLostRing 20A522 SOURCE_HANDLER
        18 ObjNull 203D1A NATIVE_NULL
        19 ObjCheckpoint 205FBC SOURCE_HANDLER
        20 ObjBigRing 20B930 SOURCE_HANDLER
        21 ObjCapsule 20B6B2 SOURCE_HANDLER
        22 ObjGoalPost 20BA68 SOURCE_HANDLER
        23 ObjSignpost 20BBEC SOURCE_HANDLER
        24 ObjExplosion 2061D4 SOURCE_HANDLER
        25 ObjMonitorTimePost 20AA60 SOURCE_HANDLER
        26 ObjMonitorItem 20ABDC SOURCE_HANDLER
        27 ObjBoulder 209BAA SOURCE_HANDLER
        28 ObjHUDPoints 20B0B4 SOURCE_HANDLER
        29 ObjNull 203D1A NATIVE_NULL
        30 ObjSpinTunnel 206740 SOURCE_HANDLER
        31 ObjFlower 206262 SOURCE_HANDLER
        32 ObjPiston 20CA26 SOURCE_HANDLER
        33 ObjElectricBeams 20CBAC SOURCE_HANDLER
        34 ObjPlatform 20CE98 SOURCE_HANDLER
        35 ObjBouncePlatform 20CFAC SOURCE_HANDLER
        36 ObjSwitch 20D184 SOURCE_HANDLER
        37 ObjDoor 20D260 SOURCE_HANDLER
        38 ObjNull 203D1A NATIVE_NULL
        39 ObjTubeDoor 20D3F6 SOURCE_HANDLER
        40 ObjSpinPlatform 20D590 SOURCE_HANDLER
        41 ObjAnimal 20E902 SOURCE_HANDLER
        42 ObjSnakeBlocks 20EBF2 SOURCE_HANDLER
        43 ObjEggmanStatue 20EEE8 SOURCE_HANDLER
        44 ObjSeesaw 20F164 SOURCE_HANDLER
        45 ObjNull 203D1A NATIVE_NULL
        46 ObjRobotGenerator 20F492 SOURCE_HANDLER
        47 ObjProjector 20F8FA SOURCE_HANDLER
        48 ObjBataPyon 20D8E6 SOURCE_HANDLER
        49 ObjPohBee 20DB6C SOURCE_HANDLER
        50 ObjSemi 20E04C SOURCE_HANDLER
        51 ObjMinomusi 20E492 SOURCE_HANDLER
        52 ObjNull 203D1A NATIVE_NULL
        53 ObjNull 203D1A NATIVE_NULL
        54 ObjNull 203D1A NATIVE_NULL
        55 ObjNull 203D1A NATIVE_NULL
        56 ObjNull 203D1A NATIVE_NULL
        57 ObjNull 203D1A NATIVE_NULL
        58 ObjResults 20C396 SOURCE_HANDLER
        59 ObjGameOver 20C0F4 SOURCE_HANDLER
        60 ObjTitleCard 20C210 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR61A() { return layouts("""
        0 72 36 8 290
        1 362 24 3 74
        2 436 1 1 4
        3 440 33 6 200
        4 934 1 1 4
        5 640 1 1 4
        6 644 48 6 290
        7 934 1 1 4
        8 934 1 1 4
        9 938 1 1 4
        10 938 1 1 4
        11 938 1 1 4
        12 72 36 8 290
        13 362 24 3 74
        14 436 1 1 4
        15 440 33 6 200
        16 934 1 1 4
        17 640 1 1 4
        18 644 48 6 290
        19 934 1 1 4
        20 934 1 1 4
        21 938 1 1 4
        22 938 1 1 4
        23 938 1 1 4
        24 72 36 8 290
        25 362 24 3 74
        26 436 1 1 4
        27 440 33 6 200
        28 934 1 1 4
        29 640 1 1 4
        30 644 48 6 290
        31 934 1 1 4
        32 934 1 1 4
        33 938 1 1 4
        34 938 1 1 4
        35 938 1 1 4
        """); }
    private static List<Range> rangesR61B() { return ranges("""
        PlayerAnims 5AB8 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 796A 3714 bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a
        FreezerAnims 8CB0 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 8D2C 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8F98 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9BB2 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims A086 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites A0A2 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims A0F0 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites A0FE 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites A14E 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims A1C6 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A650 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A658 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A69C 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A73E 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AD62 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites ADEA 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites B000 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites B102 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BD66 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BD72 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BF12 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites C176 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C60C 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C87A 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C8BA 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites CB24 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CE1A 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CF38 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites D07A 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites D1E2 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D36E 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        TubeDoorSprites D502 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D80E 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D818 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 DAC2 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 DAE6 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DDA8 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DDB4 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DE9C 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DFA0 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DFA6 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims E196 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 E19E 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E286 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E6E8 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E736 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 EAC4 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 EB06 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EB42 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 EB56 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EE16 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites EFFE 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F35E 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F386 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        RobotTransportAnims F5FC 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F604 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FB2E 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FB72 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        Include66 FF3E 194 af4296c79c01f04b311a26a47b7c548ca513b1a9cb84877c63fe2609434b4df0
        StageChunks 10000 58880 f33997bda8ae5f77a014ba6654cfa683b3a3f99b264e0e43e6754486be196711
        Padding2 1E600 1024 60d255c2b1410a147340265092e3dc804a4800a345d1c87a5fdf84951a790c4c
        Include72 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include73 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include74 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include81 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 800 e33e1dc18f152b41b9450324305e1489147685cdf9c87f27de5a6ef74a41b068
        StageMapFg 36B5E 290 225821544314913fd730a2f156b54d722838b684e5c9c32ff91d55d0c6837950
        StageMapBg 36C80 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        StageBlocks 36EC4 3852 4c37e545562bee7a0163b46df366ae98a24fb676fffbaeacee4e3cca26c934ca
        StageGfx 37DD0 8812 5b0db5c277e385e5eb6db3865c74dbd5b8c66b73ce828c66b7188dfa581424b3
        PowerupAnims 3A03C 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A164 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A276 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A28E 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A2F2 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A300 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A31C 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A324 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A33C 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A350 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A3E2 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A4E2 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A4E8 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A52A 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A532 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3A5E2 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3A5F0 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3A774 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3A8B2 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3AC58 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3AFF8 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B398 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B478 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B4CA 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3B59E 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3B666 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3B81C 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3B94E 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3BB6E 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3BC8E 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3BDBE 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3BEEE 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C020 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C294 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3C5F4 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3C8B6 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3C978 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3CA58 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3CCB0 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3CF40 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D17A 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D252 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        HologramAnimalsGfx 3D446 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        RobotTransportGfxA 3D634 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        RobotTransportGfxB 3D780 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        HologramGfx 3D932 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        Include185 3DE12 408 090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30
        Include186 3DFAA 162 48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b
        Include187 3E04C 112 c75cc362a60b81e6968c5adc66c6d059734dbb690de2775b4be130c6f0c56fe3
        Include188 3E0BC 370 06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941
        Include189 3E22E 278 e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd
        Include190 3E344 256 0578a33bff801847439bd1321fbbb3873f825a72de869789864298992e7180dd
        Include191 3E444 922 01240ff3f0680660fe841c0dac29c27d3f68e2ef4924046aa3779a7c1d45db5e
        Include192 3E7DE 438 2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892
        Include193 3E994 1174 b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef
        Include194 3EE2A 788 8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001
        Include195 3F13E 460 ed5988bf0619f46577a62cd7cf60782055f1747995567d085891bfc4c9123b0c
        Include196 3F30A 282 cd5805de0d82bdc5e578cd095f77519a6c6d06ac198038e700b6fb4a28f517be
        Include199 3F5A4 436 401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca
        Include200 3F758 250 feb698ed7e976af97b7746f243640fab416fef73c83867a37e64f574ec3d5c28
        Include201 3F852 778 b1fc4a9076b37ee82138effe60b948aa109549d0b2020b80c6f142d9aac00aaa
        Include207 3FEFA 262 a74df89250bc6eea1da5fc994bc2fe3b681c42e4771bb21623bd798bd317f04f
        MoveSpringSprites A1CC 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E41A 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E420 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims F0EC 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites F0F2 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include69 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include70 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include71 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include75 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include76 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include77 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include78 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include79 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include80 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36CCA 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36CCE 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D96 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36D9A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36EBC 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36EC0 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3A66E 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3A6A2 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        Include184 3DD32 224 0282f436f8e01c1d14a3bdf40205b01c609f974d37dd0b2ff73f05162ba79e22
        Include197 3F426 306 d4044b489b14f1cb3a10c6147a82ab4ddfa227ca3baac11f2e86ed8f6c6660f6
        Include198 3F558 76 3887fb88ae462b07268a8ecc5d8168330b6513c728bcb17884cd4a0f0ce7deff
        Include202 3FB5C 462 cc7b32160bf4b2e0d855463e9bfd70c0145c613bac4347924a764fe07407d1bb
        Include203 3FD2A 106 a0954ff284e484377e62310d9f1727e0fe7ede03947e4bb190b2937319b3d929
        Include204 3FD94 8 a25b4c948749f079fcc82da8dd4c4fd06ce85150281185df2e43f9bb4d764e42
        Include205 3FD9C 278 556430a544ed986a5a6937637f17f9f3d180a19bd2b8fedae7513e8d97a75013
        Include206 3FEB2 72 966e345e0a34ef74e15a217a429cc6e9d92f5d95f2303d6358fcb75c847274f3
        DataChunks 10000 58880 f33997bda8ae5f77a014ba6654cfa683b3a3f99b264e0e43e6754486be196711
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AB8 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A68 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BD4 240 1fa2707d9293ec7981f8fdce6f3307b3cdd2ef42df4477fa856b74bbd880d038
        DataPaletteDirectory 57A 48 18a5153cb02951e82b42b483fb3b88ff6f31c657e9f9f027775347246c39d022
        DataLayoutDirectory 36B16 72 0e23b7ddc2f970febfcd1ff740613a1ae0fe453a7c4fd03ee3bbe3efbe67235e
        DataStageData FDCA 16 d54e6f7d2da4ba0c27582e8672d75c5db74d7668190cdbc183554e40b9ccd985
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 7976 3696 2198042d18a51b5d9a8e19d47fb07631478d3dc3708dbfdcf9bf768d13428f17
        PlcRead FDDA 274 1158d19333ee5a3d724ccd28a3c5d55ec3de28459090e785c7843f1e0a94ec45
        PlcDirectory FDDA 38 84fed39e9ccf654ac26a798840d8ccaacd17435cd009bbdf08aefc4e3b69a448
        PlcBody0 FE00 8 daebeae4c4014715067ec8c0be2fbb8dc194ebdf585e6f6c033240bda72b639b
        PlcBody1 FE08 92 93799ce40023bfdbab13fbcd70747a0ce56cbd5da22725492370f68ee21c63d1
        PlcBody2 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody3 FE00 8 daebeae4c4014715067ec8c0be2fbb8dc194ebdf585e6f6c033240bda72b639b
        PlcBody4 FEC0 8 4b62b554d7b99df54d2d615484beec45ce284c1972616b54a0b9125f6c8347a9
        PlcBody5 FEC8 8 eae186944188ad6740f659d2a747b14f841daa21067a087b3e43a724ab7d345a
        PlcBody6 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody7 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody8 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody9 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody10 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody11 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody12 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody13 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody14 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody15 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody16 FED0 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FE64 92 c75a67149370dd3f64bbd58dd94da5617d94cdb6b0d7f32adc61453c4186eee2
        PlcBody18 FED8 20 f57d768f03e912a6fadde315b08c21faa0ab302f1994c9c2acafaba3e595cc03
        PaletteRead0 5AA 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead1 5AA 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead2 62A 128 6cc3e42b8727f092bfcc2411a5ec97a5942b415e89e79bdaaf48f505c95e5941
        PaletteRead3 62A 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 64A 96 1d780558bf19a26ac7c3399d183b3fbfda6218acfa70e0c5d6545f949a6f0266
        PaletteRead5 6AA 96 8ce0b58737e15e7949cce791bebb8259083eb73685cba0c7bf7d6bf44e473834
        LayoutRead0 36B5E 290 225821544314913fd730a2f156b54d722838b684e5c9c32ff91d55d0c6837950
        LayoutRead1 36C80 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead2 36CCA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36CCE 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D96 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36D9A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36B5E 290 225821544314913fd730a2f156b54d722838b684e5c9c32ff91d55d0c6837950
        LayoutRead13 36C80 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead14 36CCA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36CCE 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D96 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36D9A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36B5E 290 225821544314913fd730a2f156b54d722838b684e5c9c32ff91d55d0c6837950
        LayoutRead25 36C80 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead26 36CCA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36CCE 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D96 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36D9A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36EBC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36EC0 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 204 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 20C 6 233fb8fa9c185033f62d4069c50dfb38a168be8533bdc71cfabfe05e201f5236
        CycleScript2 212 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 21A 6 e9e1ad16081004ee78be374dff239c998408e790b8849731ff65caac860f1157
        CycleScript3 220 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 228 6 7b5ceabc3359a8230bc1373a5d3bfd7284c7e8b9816f5aa006d3671b2b41391f
        CycleScript4 22E 124 182aac25c7750031e2ad87d3f8e3b78e741726600bdf58c8fb793cc58ca090c3
        CycleColors4 2AA 8 ba904c47b8d32157da9f80f09d4fed95c0d23293d094e4aee13b787715cf37af
        R6StageDrawScrollLayerPointers 2FFC 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3364 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3332 50 1a87c72b96e4cbc106e30ba47b7548f8ebf49ea91dbbd0537cb1693dfc3e3e5a
        FiniteTable0 204 174 8623ce53534d7e9e9a948926d04da6e6259b7d4bfea6db509a87d997ac73aed8
        FiniteTable10 284A 44 77c28148b0ab750920e119faca0a22d718c9890c975070855a2cf0316f87ab86
        FiniteTable11 290E 8 8f05aa3ee1557fb49ffd348ec1d7b5fff1ed7bb4b7b94e9a5dba467cc9f7d59e
        FiniteTable12 2A86 18 656194d9dfef7cbbac1f308339233873f2f557a6ba74c87cf0105e7ad6d91f5d
        InitialBounds 284A 12 d25187382f4deb1da60b2a3b5e2dd441bdbb8ea14d9b3cdb23a564a53640977f
        """); }
    private static List<Operand> operandsR61B() { return operands("""
        2F96 267b0064
        3020 267b00da
        337C 267b00e6
        E94A 43fa0178
        E992 43fa0130
        EA82 43fa0082
        B9BA 43f90023a52a
        B8BC 43f90020bd66
        60CC 43f90023a4e2
        61E2 43f90023a33c
        62D6 43f9002336bc
        898C 43f900208cb0
        241E 43f90020fdda
        2450 43f90020fdda
        258C 43f90020fdda
        6454 43f90023a2f2
        FB1E 43fa000e
        B40A 43f9002330bc
        B46C 43f9002330bc
        B4E8 43f9002330bc
        B558 43f9002330bc
        B5FE 43f9002330bc
        1ABC 43f900232f3c
        E604 43fa00e2
        E4B4 43fa0280
        A970 43f90020ad62
        AAF0 43f90020ad62
        9CAC 43f90020a1c6
        57C8 43f900205ab8
        5D44 43f900220000
        5D14 45f90022e8ac
        58DA 43f900205c94
        59B4 43f900205b50
        59A6 43f900205c28
        5952 43f900205cc2
        5960 43f900205b40
        5936 43f900205b48
        5928 43f900205c16
        58CC 43f900205cba
        58E6 43f900205b38
        5A34 43f900205c10
        58F2 43f900205b30
        5A40 43f900205c0a
        DB26 43fa0280
        DB84 43fa022e
        DB94 43fa0306
        DF96 43fa0008
        613C 43f90023a03c
        65A8 43f90023a03c
        664C 43f90023a03c
        A458 43f90020a650
        A63E 43f90020a650
        F550 43fa00aa
        F246 43fa0116
        E100 43fa0094
        E18C 43fa0008
        E054 43fa0148
        E05E 43fa0226
        BC44 43f90023a5e2
        D598 43fa0274
        64F8 43f90023a276
        9E9A 43f90020a0f0
        9F42 43f90020a0f0
        9FAC 43f90020a0f0
        A06A 43f90020a0f0
        10DC 45f9002346f6
        119C 45f9002346f6
        124C 45f9002346f6
        12FC 45f9002346f6
        110A 45f9002347f6
        11CA 45f9002347f6
        1368 43f9002347f6
        136E 45f9002347f6
        13A2 43f9002347f6
        13B0 43f9002347f6
        13B6 45f9002347f6
        127A 45f9002357f6
        132A 45f9002357f6
        13A8 45f9002357f6
        15B4 45f90020fdca
        33AE 45f90020fdca
        76D2 41f90020796a
        C204 45f90020c87a
        6382 43f90023a31c
        100 4ef90020011e
        10C 4ef900202014
        112 4ef900201b8a
        37C2 43f900203bd4227100fc4e91
        """); }
    private static List<Dispatch> dispatchR61B() { return dispatch("""
        1 PlayerObject 203D32 SOURCE_HANDLER
        2 PlayerObject 203D32 SOURCE_HANDLER
        3 PowerupObject 20650E SOURCE_HANDLER
        4 LauncherObject 208DEA SOURCE_HANDLER
        5 FreezerObject 2088F2 SOURCE_HANDLER
        6 TestObject 2060D8 SOURCE_HANDLER
        7 TunnelPathObject 208FB8 SOURCE_HANDLER
        8 GoddessStatueObject 2087EC SOURCE_HANDLER
        9 NullObject 203CC4 NATIVE_NULL
        10 SpringObject 209D0C SOURCE_HANDLER
        11 HDoorSplashObject 2064A8 SOURCE_HANDLER
        12 HDoorSplashSetObject 206470 SOURCE_HANDLER
        13 HDoorObject 206398 SOURCE_HANDLER
        14 TunnelSplashObject 206342 SOURCE_HANDLER
        15 MoveSpringObject 209BC0 SOURCE_HANDLER
        16 RingObject 20A1DC SOURCE_HANDLER
        17 LostRingObject 20A4C0 SOURCE_HANDLER
        18 NullObject 203CC4 NATIVE_NULL
        19 CheckpointObject 205F66 SOURCE_HANDLER
        20 BigRingObject 20B8CE SOURCE_HANDLER
        21 CapsuleObject 20B650 SOURCE_HANDLER
        22 GoalObject 20BA06 SOURCE_HANDLER
        23 SignpostObject 20BB8A SOURCE_HANDLER
        24 ExplosionObject 20617E SOURCE_HANDLER
        25 MonitorTimeObject 20A9FE SOURCE_HANDLER
        26 MonitorItemObject 20AB7A SOURCE_HANDLER
        27 BoulderObject 209B48 SOURCE_HANDLER
        28 HudPointsObject 20B052 SOURCE_HANDLER
        29 NullObject 203CC4 NATIVE_NULL
        30 RollTunnelObject 2066EA SOURCE_HANDLER
        31 FlowerObject 20620C SOURCE_HANDLER
        32 PistonObject 20C9C4 SOURCE_HANDLER
        33 ElectricBeamsObject 20CB4A SOURCE_HANDLER
        34 PlatformObject 20CE36 SOURCE_HANDLER
        35 BouncePlatformObject 20CF4A SOURCE_HANDLER
        36 SwitchObject 20D122 SOURCE_HANDLER
        37 DoorObject 20D1FE SOURCE_HANDLER
        38 NullObject 203CC4 NATIVE_NULL
        39 TubeDoorObject 20D394 SOURCE_HANDLER
        40 SpinPlatformObject 20D52E SOURCE_HANDLER
        41 AnimalObject 20E8A0 SOURCE_HANDLER
        42 SnakeBlocksObject 20EB90 SOURCE_HANDLER
        43 EggmanStatueObject 20EE86 SOURCE_HANDLER
        44 SeesawObject 20F102 SOURCE_HANDLER
        45 NullObject 203CC4 NATIVE_NULL
        46 RobotGeneratorObject 20F430 SOURCE_HANDLER
        47 ProjectorObject 20F898 SOURCE_HANDLER
        48 BataPyonObject 20D884 SOURCE_HANDLER
        49 PohBeeObject 20DB0A SOURCE_HANDLER
        50 SemiObject 20DFEA SOURCE_HANDLER
        51 MinomusiObject 20E430 SOURCE_HANDLER
        52 NullObject 203CC4 NATIVE_NULL
        53 NullObject 203CC4 NATIVE_NULL
        54 NullObject 203CC4 NATIVE_NULL
        55 NullObject 203CC4 NATIVE_NULL
        56 NullObject 203CC4 NATIVE_NULL
        57 NullObject 203CC4 NATIVE_NULL
        58 ResultsObject 20C334 SOURCE_HANDLER
        59 GameOverObject 20C092 SOURCE_HANDLER
        60 TitleCardObject 20C1AE SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR61B() { return layouts("""
        0 72 36 8 290
        1 362 24 3 74
        2 436 1 1 4
        3 440 33 6 200
        4 934 1 1 4
        5 640 1 1 4
        6 644 48 6 290
        7 934 1 1 4
        8 934 1 1 4
        9 938 1 1 4
        10 938 1 1 4
        11 938 1 1 4
        12 72 36 8 290
        13 362 24 3 74
        14 436 1 1 4
        15 440 33 6 200
        16 934 1 1 4
        17 640 1 1 4
        18 644 48 6 290
        19 934 1 1 4
        20 934 1 1 4
        21 938 1 1 4
        22 938 1 1 4
        23 938 1 1 4
        24 72 36 8 290
        25 362 24 3 74
        26 436 1 1 4
        27 440 33 6 200
        28 934 1 1 4
        29 640 1 1 4
        30 644 48 6 290
        31 934 1 1 4
        32 934 1 1 4
        33 938 1 1 4
        34 938 1 1 4
        35 938 1 1 4
        """); }
    private static List<Range> rangesR61C() { return ranges("""
        PlayerAnims 5AD6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 797C 3714 bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a
        FreezerAnims 8CC2 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 8D3E 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8FAA 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9BC4 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims A098 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites A0B4 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims A102 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites A110 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites A160 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims A1D8 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A662 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A66A 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A6AE 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A750 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AD74 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites ADFC 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites B012 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites B114 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BD78 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BD84 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BF24 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites C188 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C61E 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C88C 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C8CC 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites CB36 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CE2C 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CF4A 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites D08C 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites D1F4 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D380 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        TubeDoorSprites D514 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D820 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D82A 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 DAD4 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 DAF8 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DDBA 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DDC6 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DEAE 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DFB2 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DFB8 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims E1A8 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 E1B0 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E298 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E6FA 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E748 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 EAD6 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 EB18 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EB54 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 EB68 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EE28 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites F010 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F370 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F398 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        RobotTransportAnims F60E 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F616 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FB40 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FB84 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        Include66 FF3E 194 af4296c79c01f04b311a26a47b7c548ca513b1a9cb84877c63fe2609434b4df0
        StageChunks 10000 59392 944500e6ebf325ba158203631d1361ae3384a065c31eb878fc9c752cc1c8f753
        Padding2 1E800 512 131ee61f2ef09317d68cd7e14e8c423e8c9c2a47fbadb30577fc9965c27d10c7
        Include72 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include73 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include74 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include81 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 768 b01116b5d244fd257f4825a0589f64d409abd5701303da9c00257f2d048daa8e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 700 7a2e94b334d2211bf6a50d33e51e77b67b7c17e9ba83ea517433fd8192aac2f7
        StageMapFg 36AFA 290 9a857d46a80390b36ffc4eece6423cd9b7fdc4319965c8a47aa69f0de46559d9
        StageMapBg 36C1C 78 dc94e53307bfb5c2486f92d5c1468ad277a0caf71088554cb001837e521bba88
        StageBlocks 36E64 3774 6d6cd5b0310e48e1b4a4e097d31056d0719f26ef648e9650fa3936f1f819f614
        StageGfx 37D22 8278 6923da80a56ce05528daab459b1f5b5424e5963140a6e3ae4242e816c0dcea7f
        PowerupAnims 39D78 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 39EA0 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 39FB2 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 39FCA 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A02E 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A03C 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A058 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A060 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A078 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A08C 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A11E 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A21E 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A224 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A266 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A26E 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3A31E 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3A32C 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3A4B0 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3A5EE 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3A994 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3AD34 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B0D4 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B1B4 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B206 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3B2DA 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3B3A2 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3B558 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3B68A 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3B8AA 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3B9CA 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3BAFA 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3BC2A 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3BD5C 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3BFD0 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3C330 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3C5F2 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3C6B4 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3C794 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3C9EC 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3CC7C 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3CEB6 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3CF8E 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        Padding3 3D182 1619 45579e1bf41db34fcbd789e4f2047c6676f6e83cfa437979e0f2e203297d8262
        Padding3_180 3DD32 1882 3f041275fa15def8db37ba0f919dda24fa1ed48d777804aa78be02f4a3f83c0c
        Padding3_181 3E48C 6770 f75271abee82c6d7cb251b37194e9f3ca05325184c558217185da9e10c695e6b
        Include182 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites A1DE 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E42C 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E432 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims F0FE 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites F104 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include69 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include70 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include71 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include75 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include76 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include77 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include78 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include79 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include80 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36C6A 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36C6E 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D36 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36D3A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36E5C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36E60 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3A3AA 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3A3DE 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        Padding3_178 3DA6E 43 455e696905acec7569d11bd08c9a8f106cb6eb7a1ee08c512767c189b1cf6579
        DataChunks 10000 59392 944500e6ebf325ba158203631d1361ae3384a065c31eb878fc9c752cc1c8f753
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AD6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A86 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BF2 240 52965ae5944efcd21bcd5c53c7c4578e448e2cef3842e340292e0a27df75d01e
        DataPaletteDirectory 658 56 572439398ace3882b6286958f329a4fd87cd5459e26e62af3f4d12b092338ff2
        DataLayoutDirectory 36AB2 72 753ad93dbfa51fb7a5a5b679888af83e65bc5268f867416f3fd03ed0cdd5ee07
        DataStageData FD9A 16 778eeaa5015f00cbcaea6ab7dfe125917733330d5703062df8d9e5e620796828
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 7988 3696 2198042d18a51b5d9a8e19d47fb07631478d3dc3708dbfdcf9bf768d13428f17
        PlcRead FDAA 252 e57514825992d2780ad21d9c8c56a5a4845eb37d626e47dc87b80000c71d3309
        PlcDirectory FDAA 38 050ae88449a59bb3f585bd57d62ba7f7ef575e3906029866810b129b61f18741
        PlcBody0 FDD0 8 58afd2825f09951f13cd67f9017d239c03f4aa8ff5dd04fa6399ceec4bebb059
        PlcBody1 FDD8 92 3b99c7eda5561122b17ccee1c4cd95d897976c1d9353a7fe922495e6a30c54f4
        PlcBody2 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody3 FDD0 8 58afd2825f09951f13cd67f9017d239c03f4aa8ff5dd04fa6399ceec4bebb059
        PlcBody4 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody5 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody6 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody7 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody8 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody9 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody10 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody11 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody12 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody13 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody14 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody15 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody16 FE8A 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FE34 86 be4b6ea7a1ab1fe304e0862c88c5bf3bfed2427617a7a4f1b76faa0cccc47f09
        PlcBody18 FE92 20 8c5072d952f83ea1b7701ed531ee622593b3a6ba757ed96d568d67e4e56a5eb4
        PaletteRead0 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead1 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead2 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead3 690 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B0 96 bbeb532298cbf07c3f15b49f71be2e54da40985b83da4e7f21324ea50bba918e
        PaletteRead5 710 96 0ca572dacebf4a938307941acb242dd4165ee58b66663bf0c6114a5ba8dc1a75
        PaletteRead6 770 32 516677dca3a2a00076a8ec4d0c1078990a2a47f74c281b2bd116f61ba90834ed
        LayoutRead0 36AFA 290 9a857d46a80390b36ffc4eece6423cd9b7fdc4319965c8a47aa69f0de46559d9
        LayoutRead1 36C1C 77 7c62f66f94d697da086121c753fff1e3c44535c8fcaf23f0d89b2fb6fe19834d
        LayoutRead2 36C6A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36C6E 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36D3A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36AFA 290 9a857d46a80390b36ffc4eece6423cd9b7fdc4319965c8a47aa69f0de46559d9
        LayoutRead13 36C1C 77 7c62f66f94d697da086121c753fff1e3c44535c8fcaf23f0d89b2fb6fe19834d
        LayoutRead14 36C6A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36C6E 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36D3A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36AFA 290 9a857d46a80390b36ffc4eece6423cd9b7fdc4319965c8a47aa69f0de46559d9
        LayoutRead25 36C1C 77 7c62f66f94d697da086121c753fff1e3c44535c8fcaf23f0d89b2fb6fe19834d
        LayoutRead26 36C6A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36C6E 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36D3A 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36E5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36E60 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 c660f9d2402fe9560d567ad84d958fb9957a0627f75e785a17feae82da9f724a
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 3d44320917a6aa51cd48df2f534cdb6af5704a8eb7e7c5c6eda39a1fbcd99640
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 1f962831888e887b9918d2707d8b06f25957d545577673f6efe897605e525c3a
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 173fa6a788a2f7dbd46e257f5c0426b4937bcafe5817aacf5a48e45226a55e9f
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 301A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3382 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3350 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 3d68df71db6b7b3afe494c7171606be307653e61e478f284c4949cda9d8d6202
        FiniteTable11 28D0 44 77c28148b0ab750920e119faca0a22d718c9890c975070855a2cf0316f87ab86
        FiniteTable12 2994 8 b6026110b96b1402a4f71f7ec8422e92c38f27f8eefa061ae6de18e883a17b96
        FiniteTable13 2AAA 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28D0 12 d25187382f4deb1da60b2a3b5e2dd441bdbb8ea14d9b3cdb23a564a53640977f
        """); }
    private static List<Operand> operandsR61C() { return operands("""
        2FB4 267b0064
        303E 267b00da
        339A 267b00e6
        E95C 43fa0178
        E9A4 43fa0130
        EA94 43fa0082
        B9CC 43f90023a266
        B8CE 43f90020bd78
        60EA 43f90023a21e
        6200 43f90023a078
        62F4 43f9002336bc
        899E 43f900208cc2
        24A4 43f90020fdaa
        24D6 43f90020fdaa
        2612 43f90020fdaa
        6472 43f90023a02e
        FB30 43fa000e
        B41C 43f9002330bc
        B47E 43f9002330bc
        B4FA 43f9002330bc
        B56A 43f9002330bc
        B610 43f9002330bc
        1B42 43f900232f3c
        E616 43fa00e2
        E4C6 43fa0280
        A982 43f90020ad74
        AB02 43f90020ad74
        9CBE 43f90020a1d8
        57E6 43f900205ad6
        5D62 43f900220000
        5D32 45f90022e8ac
        58F8 43f900205cb2
        59D2 43f900205b6e
        59C4 43f900205c46
        5970 43f900205ce0
        597E 43f900205b5e
        5954 43f900205b66
        5946 43f900205c34
        58EA 43f900205cd8
        5904 43f900205b56
        5A52 43f900205c2e
        5910 43f900205b4e
        5A5E 43f900205c28
        DB38 43fa0280
        DB96 43fa022e
        DBA6 43fa0306
        DFA8 43fa0008
        615A 43f900239d78
        65C6 43f900239d78
        666A 43f900239d78
        A46A 43f90020a662
        A650 43f90020a662
        F562 43fa00aa
        F258 43fa0116
        E112 43fa0094
        E19E 43fa0008
        E066 43fa0148
        E070 43fa0226
        BC56 43f90023a31e
        D5AA 43fa0274
        6516 43f900239fb2
        9EAC 43f90020a102
        9F54 43f90020a102
        9FBE 43f90020a102
        A07C 43f90020a102
        1162 45f9002346f6
        1222 45f9002346f6
        12D2 45f9002346f6
        1382 45f9002346f6
        1190 45f9002347f6
        1250 45f9002347f6
        13EE 43f9002347f6
        13F4 45f9002347f6
        1428 43f9002347f6
        1436 43f9002347f6
        143C 45f9002347f6
        1300 45f9002357f6
        13B0 45f9002357f6
        142E 45f9002357f6
        163A 45f90020fd9a
        33CC 45f90020fd9a
        76E4 41f90020797c
        C216 45f90020c88c
        63A0 43f90023a058
        100 4ef90020011e
        10C 4ef90020209a
        112 4ef900201c10
        37E0 43f900203bf2227100fc4e91
        """); }
    private static List<Dispatch> dispatchR61C() { return dispatch("""
        1 PlayerObject 203D50 SOURCE_HANDLER
        2 PlayerObject 203D50 SOURCE_HANDLER
        3 PowerupObject 20652C SOURCE_HANDLER
        4 LauncherObject 208DFC SOURCE_HANDLER
        5 FreezerObject 208904 SOURCE_HANDLER
        6 TestObject 2060F6 SOURCE_HANDLER
        7 TunnelPathObject 208FCA SOURCE_HANDLER
        8 GoddessStatueObject 2087FE SOURCE_HANDLER
        9 NullObject 203CE2 NATIVE_NULL
        10 SpringObject 209D1E SOURCE_HANDLER
        11 HDoorSplashObject 2064C6 SOURCE_HANDLER
        12 HDoorSplashSetObject 20648E SOURCE_HANDLER
        13 HDoorObject 2063B6 SOURCE_HANDLER
        14 TunnelSplashObject 206360 SOURCE_HANDLER
        15 MoveSpringObject 209BD2 SOURCE_HANDLER
        16 RingObject 20A1EE SOURCE_HANDLER
        17 LostRingObject 20A4D2 SOURCE_HANDLER
        18 NullObject 203CE2 NATIVE_NULL
        19 CheckpointObject 205F84 SOURCE_HANDLER
        20 BigRingObject 20B8E0 SOURCE_HANDLER
        21 CapsuleObject 20B662 SOURCE_HANDLER
        22 GoalObject 20BA18 SOURCE_HANDLER
        23 SignpostObject 20BB9C SOURCE_HANDLER
        24 ExplosionObject 20619C SOURCE_HANDLER
        25 MonitorTimeObject 20AA10 SOURCE_HANDLER
        26 MonitorItemObject 20AB8C SOURCE_HANDLER
        27 BoulderObject 209B5A SOURCE_HANDLER
        28 HudPointsObject 20B064 SOURCE_HANDLER
        29 NullObject 203CE2 NATIVE_NULL
        30 RollTunnelObject 206708 SOURCE_HANDLER
        31 FlowerObject 20622A SOURCE_HANDLER
        32 PistonObject 20C9D6 SOURCE_HANDLER
        33 ElectricBeamsObject 20CB5C SOURCE_HANDLER
        34 PlatformObject 20CE48 SOURCE_HANDLER
        35 BouncePlatformObject 20CF5C SOURCE_HANDLER
        36 SwitchObject 20D134 SOURCE_HANDLER
        37 DoorObject 20D210 SOURCE_HANDLER
        38 NullObject 203CE2 NATIVE_NULL
        39 TubeDoorObject 20D3A6 SOURCE_HANDLER
        40 SpinPlatformObject 20D540 SOURCE_HANDLER
        41 AnimalObject 20E8B2 SOURCE_HANDLER
        42 SnakeBlocksObject 20EBA2 SOURCE_HANDLER
        43 EggmanStatueObject 20EE98 SOURCE_HANDLER
        44 SeesawObject 20F114 SOURCE_HANDLER
        45 NullObject 203CE2 NATIVE_NULL
        46 RobotGeneratorObject 20F442 SOURCE_HANDLER
        47 ProjectorObject 20F8AA SOURCE_HANDLER
        48 BataPyonObject 20D896 SOURCE_HANDLER
        49 PohBeeObject 20DB1C SOURCE_HANDLER
        50 SemiObject 20DFFC SOURCE_HANDLER
        51 MinomusiObject 20E442 SOURCE_HANDLER
        52 NullObject 203CE2 NATIVE_NULL
        53 NullObject 203CE2 NATIVE_NULL
        54 NullObject 203CE2 NATIVE_NULL
        55 NullObject 203CE2 NATIVE_NULL
        56 NullObject 203CE2 NATIVE_NULL
        57 NullObject 203CE2 NATIVE_NULL
        58 ResultsObject 20C346 SOURCE_HANDLER
        59 GameOverObject 20C0A4 SOURCE_HANDLER
        60 TitleCardObject 20C1C0 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR61C() { return layouts("""
        0 72 36 8 290
        1 362 25 3 78
        2 440 1 1 4
        3 444 33 6 200
        4 938 1 1 4
        5 644 1 1 4
        6 648 48 6 290
        7 938 1 1 4
        8 938 1 1 4
        9 942 1 1 4
        10 942 1 1 4
        11 942 1 1 4
        12 72 36 8 290
        13 362 25 3 78
        14 440 1 1 4
        15 444 33 6 200
        16 938 1 1 4
        17 644 1 1 4
        18 648 48 6 290
        19 938 1 1 4
        20 938 1 1 4
        21 942 1 1 4
        22 942 1 1 4
        23 942 1 1 4
        24 72 36 8 290
        25 362 25 3 78
        26 440 1 1 4
        27 444 33 6 200
        28 938 1 1 4
        29 644 1 1 4
        30 648 48 6 290
        31 938 1 1 4
        32 938 1 1 4
        33 942 1 1 4
        34 942 1 1 4
        35 942 1 1 4
        """); }
    private static List<Range> rangesR61D() { return ranges("""
        PlayerAnims 5AFE 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 79A4 3714 bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a
        FreezerAnims 8CEA 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 8D66 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8FD2 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9BEC 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims A0C0 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites A0DC 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims A12A 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites A138 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites A188 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims A200 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A68A 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A692 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A6D6 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A778 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AD9C 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AE24 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites B03A 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites B13C 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BDA0 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BDAC 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BF4C 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites C1B0 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C646 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C8B4 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C8F4 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites CB5E 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CE54 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CF72 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites D0B4 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites D21C 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D3A8 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        TubeDoorSprites D53C 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D848 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D852 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 DAFC 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 DB20 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DDE2 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DDEE 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DED6 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DFDA 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DFE0 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims E1D0 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 E1D8 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E2C0 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E722 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E770 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 EAFE 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 EB40 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EB7C 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 EB90 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EE50 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites F038 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F398 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F3C0 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        RobotTransportAnims F636 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F63E 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FB68 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FBAC 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        Include66 FF3E 194 af4296c79c01f04b311a26a47b7c548ca513b1a9cb84877c63fe2609434b4df0
        StageChunks 10000 59392 b7f2428eb2b10df92a27c941f577666b115fad4d46d7753477f9e7cd704e7062
        Padding2 1E800 512 131ee61f2ef09317d68cd7e14e8c423e8c9c2a47fbadb30577fc9965c27d10c7
        Include72 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include73 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include74 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include81 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 900 0ca334929c0c25076c2126d696fb719579daf50e63a7d3d5622d432428daaeb6
        StageMapFg 36BC2 290 d70a831ec68016f283dda7b57b19c65b0a5917ee689c439d57bb0cdce2b8186f
        StageMapBg 36CE4 74 e92a7e2afa2228b6be23e1cd438c72e9502194823f9e6984fc4657aeff145208
        StageBlocks 36F28 4744 72e5b72e4e80093136b02194d635d2a89ad14625881e6e1f6fc5924ece25a887
        StageGfx 381B0 9702 78f9db695f53557954b475043edd30d20a26675db0e4a148169fb065a61ce1a7
        PowerupAnims 3A796 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A8BE 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A9D0 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A9E8 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3AA4C 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3AA5A 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3AA76 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3AA7E 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3AA96 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3AAAA 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3AB3C 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3AC3C 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3AC42 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3AC84 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3AC8C 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3AD3C 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3AD4A 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3AECE 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3B00C 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3B3B2 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B752 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3BAF2 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3BBD2 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3BC24 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3BCF8 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3BDC0 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3BF76 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3C0A8 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3C2C8 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3C3E8 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C518 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C648 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C77A 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C9EE 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3CD4E 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3D010 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3D0D2 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3D1B2 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3D40A 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D69A 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D8D4 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D9AC 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        Padding3 3DBA0 1619 45579e1bf41db34fcbd789e4f2047c6676f6e83cfa437979e0f2e203297d8262
        Padding3_181 3E1F3 665 7a0b78da087d9ddcbd930caecc0922969645ad49ed817dfe67c5f89e80a533a2
        Padding3_182 3E48C 6770 f75271abee82c6d7cb251b37194e9f3ca05325184c558217185da9e10c695e6b
        Include183 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites A206 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E454 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E45A 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims F126 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites F12C 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include69 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include70 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include71 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include75 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include76 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include77 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include78 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include79 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include80 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36D2E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36D32 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36DFA 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36DFE 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36F20 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36F24 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3ADC8 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3ADFC 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        DataChunks 10000 59392 b7f2428eb2b10df92a27c941f577666b115fad4d46d7753477f9e7cd704e7062
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AFE 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5AAE 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3C1A 240 f3fcf07ba707479cc5019679e8e9543110d2a98c7e60044eac1ec6f1bbc2d263
        DataPaletteDirectory 670 56 05ba7100b3b9feba802b9b61cdef4ad55dedb871664828713c9c21bbd6022145
        DataLayoutDirectory 36B7A 72 0e23b7ddc2f970febfcd1ff740613a1ae0fe453a7c4fd03ee3bbe3efbe67235e
        DataStageData FE04 16 a2d00e4145dc62bfc2eff87bddec689b00be26e6c0da3b7c5a2c91e9823f01f0
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 79B0 3696 2198042d18a51b5d9a8e19d47fb07631478d3dc3708dbfdcf9bf768d13428f17
        PlcRead FE14 252 240e1241f1cce9bbe9d891d7a60c5b900ca26ccc6a1ebcc95c8657085d62a11b
        PlcDirectory FE14 38 050ae88449a59bb3f585bd57d62ba7f7ef575e3906029866810b129b61f18741
        PlcBody0 FE3A 8 f7ffc4bc581e2cbd2a61d10610dbd06dde1c6caeb16427cdb77838ecbbfa9adf
        PlcBody1 FE42 92 ee0c1ea96be03cbfa08cccfc3bbded1a07730180a147419fa22735f5e5f6790c
        PlcBody2 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody3 FE3A 8 f7ffc4bc581e2cbd2a61d10610dbd06dde1c6caeb16427cdb77838ecbbfa9adf
        PlcBody4 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody5 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody6 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody7 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody8 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody9 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody10 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody11 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody12 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody13 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody14 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody15 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody16 FEF4 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FE9E 86 8f3885cc6fa4f4495b521efcaff52fb398d396ae4d04f8096d6de7615447113d
        PlcBody18 FEFC 20 a86120b0e8b6d5f086b873659e3392761ffaea2ccd9d938cb7daf7d3f855dc6c
        PaletteRead0 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead1 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead2 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead3 6A8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6C8 96 92913bec27c270a76b29b121ef932157c3e4bf23adee2f3857dedd1a2a1dd6ed
        PaletteRead5 738 96 67777001952483d7e76e4b55c1828afd70db82a3baf8e2e02c158636a1298adb
        PaletteRead6 798 32 f4bd33166dece487a24e428a6907c1fa945a825de5dcd4e4f5ae8dfd4957e0f3
        LayoutRead0 36BC2 290 d70a831ec68016f283dda7b57b19c65b0a5917ee689c439d57bb0cdce2b8186f
        LayoutRead1 36CE4 74 e92a7e2afa2228b6be23e1cd438c72e9502194823f9e6984fc4657aeff145208
        LayoutRead2 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36D32 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36DFA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36DFE 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36BC2 290 d70a831ec68016f283dda7b57b19c65b0a5917ee689c439d57bb0cdce2b8186f
        LayoutRead13 36CE4 74 e92a7e2afa2228b6be23e1cd438c72e9502194823f9e6984fc4657aeff145208
        LayoutRead14 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36D32 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36DFA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36DFE 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36BC2 290 d70a831ec68016f283dda7b57b19c65b0a5917ee689c439d57bb0cdce2b8186f
        LayoutRead25 36CE4 74 e92a7e2afa2228b6be23e1cd438c72e9502194823f9e6984fc4657aeff145208
        LayoutRead26 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36D32 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36DFA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36DFE 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36F20 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36F24 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 de0b7e36c5abccb757e43f62a09469e5e08c848e2f728efcc86cb1b6bdc2d7c4
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 dbccd11579c8608c7b701f70c3cee86ad5ab2a70cbf075bdd4c9eddca3bb478e
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 abe68fcfa20618add82b91db86ef4be932c11f6f120f94648b75a62a0940c895
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 e7c64d45a16325976c83a617e285a4ee0e7f8cdaa2028c512e6262d7dc934dba
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 3042 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 33AA 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3378 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 8baa3ec49a6d39417cb94fe7676b0b80660b459f22454a51148e0ee78557319b
        FiniteTable11 28F8 44 77c28148b0ab750920e119faca0a22d718c9890c975070855a2cf0316f87ab86
        FiniteTable12 29BC 8 b6026110b96b1402a4f71f7ec8422e92c38f27f8eefa061ae6de18e883a17b96
        FiniteTable13 2AD2 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28F8 12 d25187382f4deb1da60b2a3b5e2dd441bdbb8ea14d9b3cdb23a564a53640977f
        """); }
    private static List<Operand> operandsR61D() { return operands("""
        2FDC 267b0064
        3066 267b00da
        33C2 267b00e6
        E984 43fa0178
        E9CC 43fa0130
        EABC 43fa0082
        B9F4 43f90023ac84
        B8F6 43f90020bda0
        6112 43f90023ac3c
        6228 43f90023aa96
        631C 43f9002336bc
        89C6 43f900208cea
        24CC 43f90020fe14
        24FE 43f90020fe14
        263A 43f90020fe14
        649A 43f90023aa4c
        FB58 43fa000e
        B444 43f9002330bc
        B4A6 43f9002330bc
        B522 43f9002330bc
        B592 43f9002330bc
        B638 43f9002330bc
        1B6A 43f900232f3c
        E63E 43fa00e2
        E4EE 43fa0280
        A9AA 43f90020ad9c
        AB2A 43f90020ad9c
        9CE6 43f90020a200
        580E 43f900205afe
        5D8A 43f900220000
        5D5A 45f90022e8ac
        5920 43f900205cda
        59FA 43f900205b96
        59EC 43f900205c6e
        5998 43f900205d08
        59A6 43f900205b86
        597C 43f900205b8e
        596E 43f900205c5c
        5912 43f900205d00
        592C 43f900205b7e
        5A7A 43f900205c56
        5938 43f900205b76
        5A86 43f900205c50
        DB60 43fa0280
        DBBE 43fa022e
        DBCE 43fa0306
        DFD0 43fa0008
        6182 43f90023a796
        65EE 43f90023a796
        6692 43f90023a796
        A492 43f90020a68a
        A678 43f90020a68a
        F58A 43fa00aa
        F280 43fa0116
        E13A 43fa0094
        E1C6 43fa0008
        E08E 43fa0148
        E098 43fa0226
        BC7E 43f90023ad3c
        D5D2 43fa0274
        653E 43f90023a9d0
        9ED4 43f90020a12a
        9F7C 43f90020a12a
        9FE6 43f90020a12a
        A0A4 43f90020a12a
        118A 45f9002346f6
        124A 45f9002346f6
        12FA 45f9002346f6
        13AA 45f9002346f6
        11B8 45f9002347f6
        1278 45f9002347f6
        1416 43f9002347f6
        141C 45f9002347f6
        1450 43f9002347f6
        145E 43f9002347f6
        1464 45f9002347f6
        1328 45f9002357f6
        13D8 45f9002357f6
        1456 45f9002357f6
        1662 45f90020fe04
        33F4 45f90020fe04
        770C 41f9002079a4
        C23E 45f90020c8b4
        63C8 43f90023aa76
        100 4ef90020011e
        10C 4ef9002020c2
        112 4ef900201c38
        3808 43f900203c1a227100fc4e91
        """); }
    private static List<Dispatch> dispatchR61D() { return dispatch("""
        1 PlayerObject 203D78 SOURCE_HANDLER
        2 PlayerObject 203D78 SOURCE_HANDLER
        3 PowerupObject 206554 SOURCE_HANDLER
        4 LauncherObject 208E24 SOURCE_HANDLER
        5 FreezerObject 20892C SOURCE_HANDLER
        6 TestObject 20611E SOURCE_HANDLER
        7 TunnelPathObject 208FF2 SOURCE_HANDLER
        8 GoddessStatueObject 208826 SOURCE_HANDLER
        9 NullObject 203D0A NATIVE_NULL
        10 SpringObject 209D46 SOURCE_HANDLER
        11 HDoorSplashObject 2064EE SOURCE_HANDLER
        12 HDoorSplashSetObject 2064B6 SOURCE_HANDLER
        13 HDoorObject 2063DE SOURCE_HANDLER
        14 TunnelSplashObject 206388 SOURCE_HANDLER
        15 MoveSpringObject 209BFA SOURCE_HANDLER
        16 RingObject 20A216 SOURCE_HANDLER
        17 LostRingObject 20A4FA SOURCE_HANDLER
        18 NullObject 203D0A NATIVE_NULL
        19 CheckpointObject 205FAC SOURCE_HANDLER
        20 BigRingObject 20B908 SOURCE_HANDLER
        21 CapsuleObject 20B68A SOURCE_HANDLER
        22 GoalObject 20BA40 SOURCE_HANDLER
        23 SignpostObject 20BBC4 SOURCE_HANDLER
        24 ExplosionObject 2061C4 SOURCE_HANDLER
        25 MonitorTimeObject 20AA38 SOURCE_HANDLER
        26 MonitorItemObject 20ABB4 SOURCE_HANDLER
        27 BoulderObject 209B82 SOURCE_HANDLER
        28 HudPointsObject 20B08C SOURCE_HANDLER
        29 NullObject 203D0A NATIVE_NULL
        30 RollTunnelObject 206730 SOURCE_HANDLER
        31 FlowerObject 206252 SOURCE_HANDLER
        32 PistonObject 20C9FE SOURCE_HANDLER
        33 ElectricBeamsObject 20CB84 SOURCE_HANDLER
        34 PlatformObject 20CE70 SOURCE_HANDLER
        35 BouncePlatformObject 20CF84 SOURCE_HANDLER
        36 SwitchObject 20D15C SOURCE_HANDLER
        37 DoorObject 20D238 SOURCE_HANDLER
        38 NullObject 203D0A NATIVE_NULL
        39 TubeDoorObject 20D3CE SOURCE_HANDLER
        40 SpinPlatformObject 20D568 SOURCE_HANDLER
        41 AnimalObject 20E8DA SOURCE_HANDLER
        42 SnakeBlocksObject 20EBCA SOURCE_HANDLER
        43 EggmanStatueObject 20EEC0 SOURCE_HANDLER
        44 SeesawObject 20F13C SOURCE_HANDLER
        45 NullObject 203D0A NATIVE_NULL
        46 RobotGeneratorObject 20F46A SOURCE_HANDLER
        47 ProjectorObject 20F8D2 SOURCE_HANDLER
        48 BataPyonObject 20D8BE SOURCE_HANDLER
        49 PohBeeObject 20DB44 SOURCE_HANDLER
        50 SemiObject 20E024 SOURCE_HANDLER
        51 MinomusiObject 20E46A SOURCE_HANDLER
        52 NullObject 203D0A NATIVE_NULL
        53 NullObject 203D0A NATIVE_NULL
        54 NullObject 203D0A NATIVE_NULL
        55 NullObject 203D0A NATIVE_NULL
        56 NullObject 203D0A NATIVE_NULL
        57 NullObject 203D0A NATIVE_NULL
        58 ResultsObject 20C36E SOURCE_HANDLER
        59 GameOverObject 20C0CC SOURCE_HANDLER
        60 TitleCardObject 20C1E8 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR61D() { return layouts("""
        0 72 36 8 290
        1 362 24 3 74
        2 436 1 1 4
        3 440 33 6 200
        4 934 1 1 4
        5 640 1 1 4
        6 644 48 6 290
        7 934 1 1 4
        8 934 1 1 4
        9 938 1 1 4
        10 938 1 1 4
        11 938 1 1 4
        12 72 36 8 290
        13 362 24 3 74
        14 436 1 1 4
        15 440 33 6 200
        16 934 1 1 4
        17 640 1 1 4
        18 644 48 6 290
        19 934 1 1 4
        20 934 1 1 4
        21 938 1 1 4
        22 938 1 1 4
        23 938 1 1 4
        24 72 36 8 290
        25 362 24 3 74
        26 436 1 1 4
        27 440 33 6 200
        28 934 1 1 4
        29 640 1 1 4
        30 644 48 6 290
        31 934 1 1 4
        32 934 1 1 4
        33 938 1 1 4
        34 938 1 1 4
        35 938 1 1 4
        """); }
    private static List<Range> rangesR62A() { return ranges("""
        PlayerAnims 5AB6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 7980 2890 e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49
        FreezerAnims 898E 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 8A0A 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8C76 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9890 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D64 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D80 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9DCE 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9DDC 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9E2C 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9EA4 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A32E 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A336 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A37A 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A41C 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AA40 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AAC8 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites ACDE 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites ADE0 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BA44 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BA50 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BBF0 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites BE54 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C2EA 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C558 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C598 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites C802 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CAF8 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CC16 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites CD58 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites CEC0 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D04C 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites D1DA 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites D396 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D6A2 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D6AC 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 D956 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 D97A 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DC3C 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DC48 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DD30 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DE34 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DE3A 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims E02A 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 E032 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E11A 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E57C 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E5CA 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 E958 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 E99A 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9D6 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 E9EA 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites ECAA 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites EE92 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F1F2 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F21A 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 F4BA 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 F51E 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims F6F2 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F6FA 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FC24 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FC68 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        StageChunks 10000 59904 3b6827a42ff04b73d3c78abd2c3931cdef40654ef3e2bc632c7ad7ad35d40342
        Include73 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include74 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include75 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include82 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 800 e5b75cd24f5fe1aa59b263e9fe7a45c95088dc47b8d81dfd2f728cae5dbf1d82
        StageMapFg 36B5E 298 2626e018ccf120312f246491cc9e9af6e99f41073985a35ac740ecee7874583a
        StageMapBg 36C88 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        StageBlocks 36ECC 4186 3f2187e8d747fd4a61715a745b10970081ca491ef7bf54114436edb22640e055
        StageGfx 37F26 9054 d0ad3289e0e950c7372a457da48ddcfd695387f769c224b487ac7ca2983e202b
        PowerupAnims 3A284 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A3AC 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A4BE 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A4D6 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A53A 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A548 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A564 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A56C 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A584 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A598 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A62A 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A72A 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A730 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A772 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A77A 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3A82A 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3A838 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3A9BC 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3AAFA 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3AEA0 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B240 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B5E0 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B6C0 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B712 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3B7E6 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3B8AE 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3BA64 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3BB96 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3BDB6 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3BED6 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C006 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C136 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C268 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C4DC 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3C83C 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3CAFE 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3CBC0 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3CCA0 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3CEF8 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D188 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D3C2 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D49A 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        HologramAnimalsGfx 3D68E 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        RobotTransportGfxA 3D87C 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        Padding3 3D9C8 1456 f44e0f6ef633c916e12b35c6e5d71e3b9d5fbc44deb79814dc03cadbfd7002cd
        Padding3_184 3DF78 7994 8250f383b71833564254cfae5986555fc2dc1488a258a733ca1d81e3048c9088
        Padding3_185 3FEB2 76 7e912a6bb154e72bc0c61027ed1ab2e6c35791edc3fcafead86ac78b72ca6512
        Padding3_186 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites 9EAA 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E2AE 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E2B4 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims EF80 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites EF86 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include70 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include71 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include72 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include76 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include77 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include78 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include79 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include80 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include81 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36CD2 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D9E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36EC4 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36EC8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3A8B6 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3A8EA 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        DataChunks 10000 59904 3b6827a42ff04b73d3c78abd2c3931cdef40654ef3e2bc632c7ad7ad35d40342
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AB6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A66 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BD2 240 c8a4fcd283c5abc4448791354559c2ca06146409d7b69297826ac5a911e09afc
        DataPaletteDirectory 5B8 48 1fd34564e66442a3803c4bcf53c4ec675f2e1b5cf8a564688fe914ccf0e8b985
        DataLayoutDirectory 36B16 72 35aebc23736fc7baa26c800d824270a42934de6ddde847f129ece186327d9107
        DataStageData FEC0 16 2612b48dde4de1af06973caec90ef6747247c6af7d4112699213cec319b857e6
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 798C 2872 5a71aa2d9a830e90dbdd4bfab31550a2c1c9dc42a30359d75a74372b21316274
        PlcRead FED0 270 82cace7acd50e8e0f9ceccc23866038d82c57a83561b6e19d9d1815dc4068f5a
        PlcDirectory FED0 38 848355b9bcf0f2fefc84cafedc17af9a4169f0a030b4552f7781bb6c5cbb7e8c
        PlcBody0 FEF6 20 2aac9849b619b5dcd9f88ad3e6d9d6c05a5cd24c08cf502e6c5a3fd7ed4e1c84
        PlcBody1 FF0A 92 37b5929f20db059889b24d517e82008d3d4c830bdd5d7ef18ae0c04772b1b98f
        PlcBody2 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody3 FEF6 20 2aac9849b619b5dcd9f88ad3e6d9d6c05a5cd24c08cf502e6c5a3fd7ed4e1c84
        PlcBody4 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody5 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody6 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody7 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody8 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody9 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody10 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody11 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody12 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody13 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody14 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody15 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody16 FFC2 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FF66 92 f0a201f1f4f4501b8093a25ebe24a434bdaeca6489f1347d82ce15967f6bee58
        PlcBody18 FFCA 20 2906861b88ac9cb64eb427ddd049bfd51f4c5de2dfae6df68f99a08e06df3fa4
        PaletteRead0 5E8 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead1 5E8 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead2 668 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        PaletteRead3 6E8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 708 96 b054ef40e428fdd09dbe54833c8459de568953f5cf772c137368012e14b229f9
        PaletteRead5 768 96 cc58aa9df8487f55bac536037063a28d69817ce141b23870e2ddfdd352a25bf4
        LayoutRead0 36B5E 298 2626e018ccf120312f246491cc9e9af6e99f41073985a35ac740ecee7874583a
        LayoutRead1 36C88 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead2 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36B5E 298 2626e018ccf120312f246491cc9e9af6e99f41073985a35ac740ecee7874583a
        LayoutRead13 36C88 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead14 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36B5E 298 2626e018ccf120312f246491cc9e9af6e99f41073985a35ac740ecee7874583a
        LayoutRead25 36C88 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead26 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 206 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 20E 6 bb3619d7d1f5b3fd9c80b3a3b8d6c2ca3ddd8275a768d461909e1e859fb24fff
        CycleScript2 214 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 21C 6 8cf7ade7e8d7d767ebc76c7a40031865c357b1f0068100bf1d04cc0d809a365a
        CycleScript3 222 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 22A 6 decdbce3a202ed4b3bb9721a56e7f818e8b1df1fbe26527f7d9241071c7dc42a
        CycleScript4 230 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        CycleColors4 2E8 8 dd7cb482522a938b461c84c15c43796cadd553dfd14e2f42aff5b000a4f50221
        R6StageDrawScrollLayerPointers 2FFA 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3362 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3330 50 fbb1a3cfed993ae2860736696ddc974a29eba5e5c986566c32d3323a18b6ddec
        FiniteTable0 206 234 f3571ee3dc2ebe1bfea25b9df9c0564af9788f79cccddc8535bff6d4fa4f6788
        FiniteTable10 28F6 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        FiniteTable11 2974 8 1a304cf962600bec3953c388a78a639e6bd0de81486301729aee33757379d6fd
        FiniteTable12 2A8A 10 5ecb42b938a27d82df50a535a98f886e6a3d00b172c39a2376b90e6749aadce9
        InitialBounds 28F6 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        """); }
    private static List<Operand> operandsR62A() { return operands("""
        2F94 267b0064
        301E 267b00da
        337A 267b00e6
        E7DE 43fa0178
        E826 43fa0130
        E916 43fa0082
        B698 43f90023a772
        B59A 43f90020ba44
        60CA 43f90023a72a
        61E0 43f90023a584
        62D4 43f9002336bc
        866A 43f90020898e
        24DC 43f90020fed0
        250E 43f90020fed0
        264A 43f90020fed0
        6452 43f90023a53a
        FC14 43fa000e
        B0E8 43f9002330bc
        B14A 43f9002330bc
        B1C6 43f9002330bc
        B236 43f9002330bc
        B2DC 43f9002330bc
        1B7A 43f900232f3c
        E498 43fa00e2
        E348 43fa0280
        A64E 43f90020aa40
        A7CE 43f90020aa40
        998A 43f900209ea4
        57C6 43f900205ab6
        5D42 43f900220000
        5D12 45f90022e8ac
        58D8 43f900205c92
        59B2 43f900205b4e
        59A4 43f900205c26
        5950 43f900205cc0
        595E 43f900205b3e
        5934 43f900205b46
        5926 43f900205c14
        58CA 43f900205cb8
        58E4 43f900205b36
        5A32 43f900205c0e
        58F0 43f900205b2e
        5A3E 43f900205c08
        D9BA 43fa0280
        DA18 43fa022e
        DA28 43fa0306
        DE2A 43fa0008
        613A 43f90023a284
        65A6 43f90023a284
        664A 43f90023a284
        A136 43f90020a32e
        A31C 43f90020a32e
        F646 43fa00aa
        F0DA 43fa0116
        DF94 43fa0094
        E020 43fa0008
        DEE8 43fa0148
        DEF2 43fa0226
        B922 43f90023a82a
        D42C 43fa0274
        64F6 43f90023a4be
        9B78 43f900209dce
        9C20 43f900209dce
        9C8A 43f900209dce
        9D48 43f900209dce
        119A 45f9002346f6
        125A 45f9002346f6
        130A 45f9002346f6
        13BA 45f9002346f6
        11C8 45f9002347f6
        1288 45f9002347f6
        1426 43f9002347f6
        142C 45f9002347f6
        1460 43f9002347f6
        146E 43f9002347f6
        1474 45f9002347f6
        1338 45f9002357f6
        13E8 45f9002357f6
        1466 45f9002357f6
        1672 45f90020fec0
        33AC 45f90020fec0
        76E8 41f900207980
        BEE2 45f90020c558
        6380 43f90023a564
        100 4ef90020011e
        10C 4ef9002020d2
        112 4ef900201c48
        37C0 43f900203bd2227100fc4e91
        """); }
    private static List<Dispatch> dispatchR62A() { return dispatch("""
        1 PlayerObject 203D30 SOURCE_HANDLER
        2 PlayerObject 203D30 SOURCE_HANDLER
        3 PowerupObject 20650C SOURCE_HANDLER
        4 LauncherObject 208AC8 SOURCE_HANDLER
        5 FreezerObject 2085D0 SOURCE_HANDLER
        6 TestObject 2060D6 SOURCE_HANDLER
        7 TunnelPathObject 208C96 SOURCE_HANDLER
        8 NullObject 203CC2 NATIVE_NULL
        9 NullObject 203CC2 NATIVE_NULL
        10 SpringObject 2099EA SOURCE_HANDLER
        11 HDoorSplashObject 2064A6 SOURCE_HANDLER
        12 HDoorSplashSetObject 20646E SOURCE_HANDLER
        13 HDoorObject 206396 SOURCE_HANDLER
        14 TunnelSplashObject 206340 SOURCE_HANDLER
        15 MoveSpringObject 20989E SOURCE_HANDLER
        16 RingObject 209EBA SOURCE_HANDLER
        17 LostRingObject 20A19E SOURCE_HANDLER
        18 NullObject 203CC2 NATIVE_NULL
        19 CheckpointObject 205F64 SOURCE_HANDLER
        20 BigRingObject 20B5AC SOURCE_HANDLER
        21 CapsuleObject 20B32E SOURCE_HANDLER
        22 GoalObject 20B6E4 SOURCE_HANDLER
        23 SignpostObject 20B868 SOURCE_HANDLER
        24 ExplosionObject 20617C SOURCE_HANDLER
        25 MonitorTimeObject 20A6DC SOURCE_HANDLER
        26 MonitorItemObject 20A858 SOURCE_HANDLER
        27 BoulderObject 209826 SOURCE_HANDLER
        28 HudPointsObject 20AD30 SOURCE_HANDLER
        29 NullObject 203CC2 NATIVE_NULL
        30 RollTunnelObject 2066E8 SOURCE_HANDLER
        31 FlowerObject 20620A SOURCE_HANDLER
        32 PistonObject 20C6A2 SOURCE_HANDLER
        33 ElectricBeamsObject 20C828 SOURCE_HANDLER
        34 PlatformObject 20CB14 SOURCE_HANDLER
        35 BouncePlatformObject 20CC28 SOURCE_HANDLER
        36 SwitchObject 20CE00 SOURCE_HANDLER
        37 DoorObject 20CEDC SOURCE_HANDLER
        38 CrusherObject 20D072 SOURCE_HANDLER
        39 TubeDoorObject 20D228 SOURCE_HANDLER
        40 SpinPlatformObject 20D3C2 SOURCE_HANDLER
        41 AnimalObject 20E734 SOURCE_HANDLER
        42 SnakeBlocksObject 20EA24 SOURCE_HANDLER
        43 EggmanStatueObject 20ED1A SOURCE_HANDLER
        44 SeesawObject 20EF96 SOURCE_HANDLER
        45 SpikeChainObject 20F2C4 SOURCE_HANDLER
        46 RobotGeneratorObject 20F526 SOURCE_HANDLER
        47 ProjectorObject 20F98E SOURCE_HANDLER
        48 BataPyonObject 20D718 SOURCE_HANDLER
        49 PohBeeObject 20D99E SOURCE_HANDLER
        50 SemiObject 20DE7E SOURCE_HANDLER
        51 MinomusiObject 20E2C4 SOURCE_HANDLER
        52 NullObject 203CC2 NATIVE_NULL
        53 NullObject 203CC2 NATIVE_NULL
        54 NullObject 203CC2 NATIVE_NULL
        55 NullObject 203CC2 NATIVE_NULL
        56 NullObject 203CC2 NATIVE_NULL
        57 NullObject 203CC2 NATIVE_NULL
        58 ResultsObject 20C012 SOURCE_HANDLER
        59 GameOverObject 20BD70 SOURCE_HANDLER
        60 TitleCardObject 20BE8C SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR62A() { return layouts("""
        0 72 37 8 298
        1 370 24 3 74
        2 444 1 1 4
        3 448 33 6 200
        4 942 1 1 4
        5 648 1 1 4
        6 652 48 6 290
        7 942 1 1 4
        8 942 1 1 4
        9 946 1 1 4
        10 946 1 1 4
        11 946 1 1 4
        12 72 37 8 298
        13 370 24 3 74
        14 444 1 1 4
        15 448 33 6 200
        16 942 1 1 4
        17 648 1 1 4
        18 652 48 6 290
        19 942 1 1 4
        20 942 1 1 4
        21 946 1 1 4
        22 946 1 1 4
        23 946 1 1 4
        24 72 37 8 298
        25 370 24 3 74
        26 444 1 1 4
        27 448 33 6 200
        28 942 1 1 4
        29 648 1 1 4
        30 652 48 6 290
        31 942 1 1 4
        32 942 1 1 4
        33 946 1 1 4
        34 946 1 1 4
        35 946 1 1 4
        """); }
    private static List<Range> rangesR62B() { return ranges("""
        PlayerAnims 5A9C 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 7966 2890 e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49
        FreezerAnims 8974 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 89F0 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8C5C 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9876 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D4A 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D66 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9DB4 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9DC2 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9E12 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9E8A 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A314 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A31C 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A360 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A402 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AA26 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AAAE 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites ACC4 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites ADC6 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BA2A 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BA36 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BBD6 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites BE3A 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C2D0 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C53E 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C57E 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites C7E8 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CADE 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CBFC 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites CD3E 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites CEA6 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D032 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites D1C0 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites D37C 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D688 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D692 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 D93C 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 D960 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DC22 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DC2E 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DD16 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DE1A 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DE20 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims E010 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 E018 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E100 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E562 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E5B0 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 E93E 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 E980 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9BC 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 E9D0 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EC90 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites EE78 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F1D8 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F200 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 F456 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 F4BA 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims F68E 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F696 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FBC0 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FC04 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        StageChunks 10000 58880 cc87f4e0de535a9633b003b26e5f1f364d3a177c804184657cd8a3f0a69b99e0
        Padding2 1E600 1024 e9ba825c0a1aedfb6116e91990d3ca2d856311b611557c0438430100adcaa435
        Include74 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include75 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include76 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include83 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 800 0b1a75e753487b2aa7b4c31dd133d52dd95898412838ece370f8cb2cf6147f22
        StageMapFg 36B5E 298 456204aaf2bc919d32a77b47eb3acabc32af45da50e6b6fcd5d6230cc4767b49
        StageMapBg 36C88 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        StageBlocks 36ECC 4232 0ec3ce4ac8f9b5e5a9dc9abfe49f078b9fd5c37fe6ebff47b6f979d70e3ca009
        StageGfx 37F54 9006 9e1b49d0754461ebdc798e17c1ed480fc791707f302edfd8d3a1ef88d585f7dd
        PowerupAnims 3A282 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A3AA 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A4BC 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A4D4 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A538 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A546 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A562 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A56A 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A582 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A596 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A628 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A728 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A72E 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A770 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A778 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3A828 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3A836 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3A9BA 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3AAF8 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3AE9E 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B23E 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B5DE 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B6BE 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B710 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3B7E4 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3B8AC 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3BA62 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3BB94 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3BDB4 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3BED4 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C004 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C134 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C266 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C4DA 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3C83A 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3CAFC 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3CBBE 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3CC9E 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3CEF6 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D186 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D3C0 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D498 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        HologramAnimalsGfx 3D68C 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        RobotTransportGfxA 3D87A 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        RobotTransportGfxB 3D9C6 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        HologramGfx 3DB78 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        Padding3 3DF78 7994 8250f383b71833564254cfae5986555fc2dc1488a258a733ca1d81e3048c9088
        Padding3_187 3FEB2 76 7e912a6bb154e72bc0c61027ed1ab2e6c35791edc3fcafead86ac78b72ca6512
        Padding3_188 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites 9E90 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E294 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E29A 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims EF66 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites EF6C 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include71 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include72 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include73 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include77 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include78 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include79 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include80 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include81 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include82 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36CD2 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D9E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36EC4 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36EC8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3A8B4 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3A8E8 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        DataChunks 10000 58880 cc87f4e0de535a9633b003b26e5f1f364d3a177c804184657cd8a3f0a69b99e0
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5A9C 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A4C 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BB8 240 c691e2190a465d6b21602c1aede7e777232d4dc48798bc29ef0ce2cbbdc583ee
        DataPaletteDirectory 57A 48 18a5153cb02951e82b42b483fb3b88ff6f31c657e9f9f027775347246c39d022
        DataLayoutDirectory 36B16 72 35aebc23736fc7baa26c800d824270a42934de6ddde847f129ece186327d9107
        DataStageData FE5C 16 51e664c715b45684ada49b477d529e2fe1e6b01f1d321013bfcdbf7fddaea0f6
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 7972 2872 5a71aa2d9a830e90dbdd4bfab31550a2c1c9dc42a30359d75a74372b21316274
        PlcRead FE6C 274 1a7d2acf8868fe4277bb768e8b09a6d7e963e39fb4c94f3f5dfabbcfd63f427d
        PlcDirectory FE6C 38 84fed39e9ccf654ac26a798840d8ccaacd17435cd009bbdf08aefc4e3b69a448
        PlcBody0 FE92 8 bc65ddf7a638abf70cf15c9eb23768317ed6543de4a1d24141e958b9aa9cab65
        PlcBody1 FE9A 92 f025e9b1c0bf562307b5763b28e80474d732941d0710ec756ef63a54a6f97b2f
        PlcBody2 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody3 FE92 8 bc65ddf7a638abf70cf15c9eb23768317ed6543de4a1d24141e958b9aa9cab65
        PlcBody4 FF52 8 a524a4ffde53236b76c46585251451d4cfc7f80465391fc5389f453f320622fb
        PlcBody5 FF5A 8 2a1f1bd569c60c5086f22df9a9316f54f81ab85adc65f09410224ab2683fe769
        PlcBody6 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody7 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody8 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody9 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody10 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody11 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody12 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody13 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody14 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody15 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody16 FF62 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FEF6 92 10998d82fc745727a5784521fca2de2533f0af1d394dbf8b259dc28f9244c9f6
        PlcBody18 FF6A 20 0c192453ce60178121e2139fda95920aeace2941949ccf9cf9a8a06c1eaa95d6
        PaletteRead0 5AA 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead1 5AA 128 65c0b92651204534c77b8709197ad1b1c67bb9aad8776029846ba01ab1388759
        PaletteRead2 62A 128 6cc3e42b8727f092bfcc2411a5ec97a5942b415e89e79bdaaf48f505c95e5941
        PaletteRead3 62A 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 64A 96 1d780558bf19a26ac7c3399d183b3fbfda6218acfa70e0c5d6545f949a6f0266
        PaletteRead5 6AA 96 8ce0b58737e15e7949cce791bebb8259083eb73685cba0c7bf7d6bf44e473834
        LayoutRead0 36B5E 298 456204aaf2bc919d32a77b47eb3acabc32af45da50e6b6fcd5d6230cc4767b49
        LayoutRead1 36C88 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead2 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36B5E 298 456204aaf2bc919d32a77b47eb3acabc32af45da50e6b6fcd5d6230cc4767b49
        LayoutRead13 36C88 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead14 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36B5E 298 456204aaf2bc919d32a77b47eb3acabc32af45da50e6b6fcd5d6230cc4767b49
        LayoutRead25 36C88 74 3e66e2f99798a42648c6d4a700992ea29eb9624f08558fdd43e7cde5685aa524
        LayoutRead26 36CD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36CD6 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D9E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36DA2 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36EC4 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 204 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 20C 6 233fb8fa9c185033f62d4069c50dfb38a168be8533bdc71cfabfe05e201f5236
        CycleScript2 212 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 21A 6 e9e1ad16081004ee78be374dff239c998408e790b8849731ff65caac860f1157
        CycleScript3 220 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 228 6 7b5ceabc3359a8230bc1373a5d3bfd7284c7e8b9816f5aa006d3671b2b41391f
        CycleScript4 22E 124 182aac25c7750031e2ad87d3f8e3b78e741726600bdf58c8fb793cc58ca090c3
        CycleColors4 2AA 8 ba904c47b8d32157da9f80f09d4fed95c0d23293d094e4aee13b787715cf37af
        R6StageDrawScrollLayerPointers 2FE0 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3348 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3316 50 7db553a5daf343e9611a4e15c2e8bbb01372625a388767d4c037f1cdffe0790e
        FiniteTable0 204 174 8623ce53534d7e9e9a948926d04da6e6259b7d4bfea6db509a87d997ac73aed8
        FiniteTable10 284A 44 159e9db84a2d6a56efc343ad6d65140d875447ae40ce40f97b7cf15e219f9585
        FiniteTable11 290E 8 1a304cf962600bec3953c388a78a639e6bd0de81486301729aee33757379d6fd
        FiniteTable12 2A6A 18 656194d9dfef7cbbac1f308339233873f2f557a6ba74c87cf0105e7ad6d91f5d
        InitialBounds 284A 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        """); }
    private static List<Operand> operandsR62B() { return operands("""
        2F7A 267b0064
        3004 267b00da
        3360 267b00e6
        E7C4 43fa0178
        E80C 43fa0130
        E8FC 43fa0082
        B67E 43f90023a770
        B580 43f90020ba2a
        60B0 43f90023a728
        61C6 43f90023a582
        62BA 43f9002336bc
        8650 43f900208974
        241E 43f90020fe6c
        2450 43f90020fe6c
        258C 43f90020fe6c
        6438 43f90023a538
        FBB0 43fa000e
        B0CE 43f9002330bc
        B130 43f9002330bc
        B1AC 43f9002330bc
        B21C 43f9002330bc
        B2C2 43f9002330bc
        1ABC 43f900232f3c
        E47E 43fa00e2
        E32E 43fa0280
        A634 43f90020aa26
        A7B4 43f90020aa26
        9970 43f900209e8a
        57AC 43f900205a9c
        5D28 43f900220000
        5CF8 45f90022e8ac
        58BE 43f900205c78
        5998 43f900205b34
        598A 43f900205c0c
        5936 43f900205ca6
        5944 43f900205b24
        591A 43f900205b2c
        590C 43f900205bfa
        58B0 43f900205c9e
        58CA 43f900205b1c
        5A18 43f900205bf4
        58D6 43f900205b14
        5A24 43f900205bee
        D9A0 43fa0280
        D9FE 43fa022e
        DA0E 43fa0306
        DE10 43fa0008
        6120 43f90023a282
        658C 43f90023a282
        6630 43f90023a282
        A11C 43f90020a314
        A302 43f90020a314
        F5E2 43fa00aa
        F0C0 43fa0116
        DF7A 43fa0094
        E006 43fa0008
        DECE 43fa0148
        DED8 43fa0226
        B908 43f90023a828
        D412 43fa0274
        64DC 43f90023a4bc
        9B5E 43f900209db4
        9C06 43f900209db4
        9C70 43f900209db4
        9D2E 43f900209db4
        10DC 45f9002346f6
        119C 45f9002346f6
        124C 45f9002346f6
        12FC 45f9002346f6
        110A 45f9002347f6
        11CA 45f9002347f6
        1368 43f9002347f6
        136E 45f9002347f6
        13A2 43f9002347f6
        13B0 43f9002347f6
        13B6 45f9002347f6
        127A 45f9002357f6
        132A 45f9002357f6
        13A8 45f9002357f6
        15B4 45f90020fe5c
        3392 45f90020fe5c
        76CE 41f900207966
        BEC8 45f90020c53e
        6366 43f90023a562
        100 4ef90020011e
        10C 4ef900202014
        112 4ef900201b8a
        37A6 43f900203bb8227100fc4e91
        """); }
    private static List<Dispatch> dispatchR62B() { return dispatch("""
        1 PlayerObject 203D16 SOURCE_HANDLER
        2 PlayerObject 203D16 SOURCE_HANDLER
        3 PowerupObject 2064F2 SOURCE_HANDLER
        4 LauncherObject 208AAE SOURCE_HANDLER
        5 FreezerObject 2085B6 SOURCE_HANDLER
        6 TestObject 2060BC SOURCE_HANDLER
        7 TunnelPathObject 208C7C SOURCE_HANDLER
        8 NullObject 203CA8 NATIVE_NULL
        9 NullObject 203CA8 NATIVE_NULL
        10 SpringObject 2099D0 SOURCE_HANDLER
        11 HDoorSplashObject 20648C SOURCE_HANDLER
        12 HDoorSplashSetObject 206454 SOURCE_HANDLER
        13 HDoorObject 20637C SOURCE_HANDLER
        14 TunnelSplashObject 206326 SOURCE_HANDLER
        15 MoveSpringObject 209884 SOURCE_HANDLER
        16 RingObject 209EA0 SOURCE_HANDLER
        17 LostRingObject 20A184 SOURCE_HANDLER
        18 NullObject 203CA8 NATIVE_NULL
        19 CheckpointObject 205F4A SOURCE_HANDLER
        20 BigRingObject 20B592 SOURCE_HANDLER
        21 CapsuleObject 20B314 SOURCE_HANDLER
        22 GoalObject 20B6CA SOURCE_HANDLER
        23 SignpostObject 20B84E SOURCE_HANDLER
        24 ExplosionObject 206162 SOURCE_HANDLER
        25 MonitorTimeObject 20A6C2 SOURCE_HANDLER
        26 MonitorItemObject 20A83E SOURCE_HANDLER
        27 BoulderObject 20980C SOURCE_HANDLER
        28 HudPointsObject 20AD16 SOURCE_HANDLER
        29 NullObject 203CA8 NATIVE_NULL
        30 RollTunnelObject 2066CE SOURCE_HANDLER
        31 FlowerObject 2061F0 SOURCE_HANDLER
        32 PistonObject 20C688 SOURCE_HANDLER
        33 ElectricBeamsObject 20C80E SOURCE_HANDLER
        34 PlatformObject 20CAFA SOURCE_HANDLER
        35 BouncePlatformObject 20CC0E SOURCE_HANDLER
        36 SwitchObject 20CDE6 SOURCE_HANDLER
        37 DoorObject 20CEC2 SOURCE_HANDLER
        38 CrusherObject 20D058 SOURCE_HANDLER
        39 TubeDoorObject 20D20E SOURCE_HANDLER
        40 SpinPlatformObject 20D3A8 SOURCE_HANDLER
        41 AnimalObject 20E71A SOURCE_HANDLER
        42 SnakeBlocksObject 20EA0A SOURCE_HANDLER
        43 EggmanStatueObject 20ED00 SOURCE_HANDLER
        44 SeesawObject 20EF7C SOURCE_HANDLER
        45 SpikeChainObject 20F2AA SOURCE_HANDLER
        46 RobotGeneratorObject 20F4C2 SOURCE_HANDLER
        47 ProjectorObject 20F92A SOURCE_HANDLER
        48 BataPyonObject 20D6FE SOURCE_HANDLER
        49 PohBeeObject 20D984 SOURCE_HANDLER
        50 SemiObject 20DE64 SOURCE_HANDLER
        51 MinomusiObject 20E2AA SOURCE_HANDLER
        52 NullObject 203CA8 NATIVE_NULL
        53 NullObject 203CA8 NATIVE_NULL
        54 NullObject 203CA8 NATIVE_NULL
        55 NullObject 203CA8 NATIVE_NULL
        56 NullObject 203CA8 NATIVE_NULL
        57 NullObject 203CA8 NATIVE_NULL
        58 ResultsObject 20BFF8 SOURCE_HANDLER
        59 GameOverObject 20BD56 SOURCE_HANDLER
        60 TitleCardObject 20BE72 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR62B() { return layouts("""
        0 72 37 8 298
        1 370 24 3 74
        2 444 1 1 4
        3 448 33 6 200
        4 942 1 1 4
        5 648 1 1 4
        6 652 48 6 290
        7 942 1 1 4
        8 942 1 1 4
        9 946 1 1 4
        10 946 1 1 4
        11 946 1 1 4
        12 72 37 8 298
        13 370 24 3 74
        14 444 1 1 4
        15 448 33 6 200
        16 942 1 1 4
        17 648 1 1 4
        18 652 48 6 290
        19 942 1 1 4
        20 942 1 1 4
        21 946 1 1 4
        22 946 1 1 4
        23 946 1 1 4
        24 72 37 8 298
        25 370 24 3 74
        26 444 1 1 4
        27 448 33 6 200
        28 942 1 1 4
        29 648 1 1 4
        30 652 48 6 290
        31 942 1 1 4
        32 942 1 1 4
        33 946 1 1 4
        34 946 1 1 4
        35 946 1 1 4
        """); }
    private static List<Range> rangesR62C() { return ranges("""
        PlayerAnims 5A7E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 7924 2890 e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49
        FreezerAnims 8932 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 89AE 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8C1A 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 9834 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D08 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D24 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9D72 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9D80 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9DD0 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9E48 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A2D2 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A2DA 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A31E 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A3C0 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A9E4 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AA6C 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites AC82 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites AD84 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B9E8 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B9F4 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BB94 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites BDF8 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C28E 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C4FC 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C53C 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites C7A6 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CA9C 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CBBA 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites CCFC 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites CE64 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites CFF0 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites D17E 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites D33A 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D646 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D650 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 D8FA 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 D91E 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DBE0 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DBEC 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DCD4 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DDD8 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DDDE 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims DFCE 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 DFD6 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E0BE 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E520 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E56E 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 E8FC 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 E93E 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E97A 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 E98E 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EC4E 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites EE36 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F196 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F1BE 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 F4A8 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 F50C 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims F6E0 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F6E8 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FC12 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FC56 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        StageChunks 10000 59392 4bb677e312cccb6b1aaa79ff3c2778a470569372f91f941cc1cae05278370dfa
        Padding2 1E800 512 b712b1b36f1e6d8ae3f6d9cf75cda61907982847e4424826a1ac41ce70c28b99
        Include74 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include75 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include76 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include83 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 768 b01116b5d244fd257f4825a0589f64d409abd5701303da9c00257f2d048daa8e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 800 3d2a5165c60f4547e64047feb332e58f2fb2a84c29cd7d441c5a2163da407353
        StageMapFg 36B5E 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        StageMapBg 36C88 78 7fae7c99245de121073e1ccc60f40a3b83b8f95a1c7143976d1d3c469453e381
        StageBlocks 36ED0 4156 d3cc5dffb07a6fb527333e59da7ed7b894a6c2eed2e5b5ce1c6a2472daf7c6d0
        StageGfx 37F0C 9712 c5d13357dec5313f665b1a3a114a4a2d8bdde52476362af2b77228fa59eff8da
        PowerupAnims 3A4FC 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A624 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A736 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A74E 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A7B2 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A7C0 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A7DC 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A7E4 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A7FC 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A810 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A8A2 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A9A2 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A9A8 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A9EA 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A9F2 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3AAA2 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3AAB0 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3AC34 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3AD72 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3B118 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B4B8 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B858 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B938 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B98A 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3BA5E 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3BB26 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3BCDC 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3BE0E 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3C02E 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3C14E 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C27E 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C3AE 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C4E0 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C754 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3CAB4 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3CD76 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3CE38 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3CF18 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3D170 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D400 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D63A 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D712 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        Padding3 3D906 1576 ab395ac93fba198715863b348b7639a5962035b5341257ef6b3baf0aaece83dc
        Include179 3DF2E 43 455e696905acec7569d11bd08c9a8f106cb6eb7a1ee08c512767c189b1cf6579
        Include182 3E48C 6770 f75271abee82c6d7cb251b37194e9f3ca05325184c558217185da9e10c695e6b
        Include183 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites 9E4E 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E252 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E258 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims EF24 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites EF2A 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include71 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include72 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include73 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include77 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include78 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include79 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include80 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include81 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include82 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36CD6 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36CDA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36DA2 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36DA6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36EC8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36ECC 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3AB2E 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3AB62 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        DataChunks 10000 59392 4bb677e312cccb6b1aaa79ff3c2778a470569372f91f941cc1cae05278370dfa
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5A7E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A2E 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3B9A 240 19f071f6064ea07ce78be60d069ad2e01749810273967edce4ea2b32f10e6bc5
        DataPaletteDirectory 658 56 572439398ace3882b6286958f329a4fd87cd5459e26e62af3f4d12b092338ff2
        DataLayoutDirectory 36B16 72 e2953cb6a2b0bc315f9f5052d6de44e7ed2589f0ce83205ef3fff83fed060e27
        DataStageData FE6C 16 3f6c88f707d0256782a8355993324fde57ebf6cfd3b8918f494901cf223063a9
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 7930 2872 5a71aa2d9a830e90dbdd4bfab31550a2c1c9dc42a30359d75a74372b21316274
        PlcRead FE7C 258 c1f00da6864ec899f51f273072cfa3233583222faa40fc44ad4a2026402bb23f
        PlcDirectory FE7C 38 b27fb43a292fb78a709f81b06a2f3322f92505d3134685bb227663c0d63b0f39
        PlcBody0 FEA2 8 b6995bc0669865d93e37329b14fa78e006de7c8787d7bf82c778497dd5c981e2
        PlcBody1 FEAA 92 ef34bf682141986c27d0252ac40fad8077ab05f73737edd219e3cb3e11eec859
        PlcBody2 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody3 FEA2 8 b6995bc0669865d93e37329b14fa78e006de7c8787d7bf82c778497dd5c981e2
        PlcBody4 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody5 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody6 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody7 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody8 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody9 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody10 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody11 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody12 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody13 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody14 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody15 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody16 FF62 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FF06 92 887d8ff24577050b3e30721d4ba495d8b80a0fc22bb6300b6704f8627e750556
        PlcBody18 FF6A 20 686a47a1a623c6cec08849694bd44d2d74c0cf16e3f4c475d8b32c6a6b44d6fe
        PaletteRead0 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead1 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead2 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead3 690 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B0 96 bbeb532298cbf07c3f15b49f71be2e54da40985b83da4e7f21324ea50bba918e
        PaletteRead5 710 96 0ca572dacebf4a938307941acb242dd4165ee58b66663bf0c6114a5ba8dc1a75
        PaletteRead6 770 32 516677dca3a2a00076a8ec4d0c1078990a2a47f74c281b2bd116f61ba90834ed
        LayoutRead0 36B5E 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead1 36C88 77 6d42824299510eaba820fe47036cf7b73904db5e333a44b06ba8407234cec3b6
        LayoutRead2 36CD6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36CDA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36DA2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36DA6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36B5E 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead13 36C88 77 6d42824299510eaba820fe47036cf7b73904db5e333a44b06ba8407234cec3b6
        LayoutRead14 36CD6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36CDA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36DA2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36DA6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36B5E 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead25 36C88 77 6d42824299510eaba820fe47036cf7b73904db5e333a44b06ba8407234cec3b6
        LayoutRead26 36CD6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36CDA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36DA2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36DA6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36EC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36ECC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 c660f9d2402fe9560d567ad84d958fb9957a0627f75e785a17feae82da9f724a
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 3d44320917a6aa51cd48df2f534cdb6af5704a8eb7e7c5c6eda39a1fbcd99640
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 1f962831888e887b9918d2707d8b06f25957d545577673f6efe897605e525c3a
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 173fa6a788a2f7dbd46e257f5c0426b4937bcafe5817aacf5a48e45226a55e9f
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 2FC2 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 332A 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 32F8 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 3d68df71db6b7b3afe494c7171606be307653e61e478f284c4949cda9d8d6202
        FiniteTable11 28BE 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        FiniteTable12 293C 8 1a304cf962600bec3953c388a78a639e6bd0de81486301729aee33757379d6fd
        FiniteTable13 2A52 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28BE 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        """); }
    private static List<Operand> operandsR62C() { return operands("""
        2F5C 267b0064
        2FE6 267b00da
        3342 267b00e6
        E782 43fa0178
        E7CA 43fa0130
        E8BA 43fa0082
        B63C 43f90023a9ea
        B53E 43f90020b9e8
        6092 43f90023a9a2
        61A8 43f90023a7fc
        629C 43f9002336bc
        860E 43f900208932
        24A4 43f90020fe7c
        24D6 43f90020fe7c
        2612 43f90020fe7c
        641A 43f90023a7b2
        FC02 43fa000e
        B08C 43f9002330bc
        B0EE 43f9002330bc
        B16A 43f9002330bc
        B1DA 43f9002330bc
        B280 43f9002330bc
        1B42 43f900232f3c
        E43C 43fa00e2
        E2EC 43fa0280
        A5F2 43f90020a9e4
        A772 43f90020a9e4
        992E 43f900209e48
        578E 43f900205a7e
        5D0A 43f900220000
        5CDA 45f90022e8ac
        58A0 43f900205c5a
        597A 43f900205b16
        596C 43f900205bee
        5918 43f900205c88
        5926 43f900205b06
        58FC 43f900205b0e
        58EE 43f900205bdc
        5892 43f900205c80
        58AC 43f900205afe
        59FA 43f900205bd6
        58B8 43f900205af6
        5A06 43f900205bd0
        D95E 43fa0280
        D9BC 43fa022e
        D9CC 43fa0306
        DDCE 43fa0008
        6102 43f90023a4fc
        656E 43f90023a4fc
        6612 43f90023a4fc
        A0DA 43f90020a2d2
        A2C0 43f90020a2d2
        F634 43fa00aa
        F07E 43fa0116
        DF38 43fa0094
        DFC4 43fa0008
        DE8C 43fa0148
        DE96 43fa0226
        B8C6 43f90023aaa2
        D3D0 43fa0274
        64BE 43f90023a736
        9B1C 43f900209d72
        9BC4 43f900209d72
        9C2E 43f900209d72
        9CEC 43f900209d72
        1162 45f9002346f6
        1222 45f9002346f6
        12D2 45f9002346f6
        1382 45f9002346f6
        1190 45f9002347f6
        1250 45f9002347f6
        13EE 43f9002347f6
        13F4 45f9002347f6
        1428 43f9002347f6
        1436 43f9002347f6
        143C 45f9002347f6
        1300 45f9002357f6
        13B0 45f9002357f6
        142E 45f9002357f6
        163A 45f90020fe6c
        3374 45f90020fe6c
        768C 41f900207924
        BE86 45f90020c4fc
        6348 43f90023a7dc
        100 4ef90020011e
        10C 4ef90020209a
        112 4ef900201c10
        3788 43f900203b9a227100fc4e91
        """); }
    private static List<Dispatch> dispatchR62C() { return dispatch("""
        1 PlayerObject 203CF8 SOURCE_HANDLER
        2 PlayerObject 203CF8 SOURCE_HANDLER
        3 PowerupObject 2064D4 SOURCE_HANDLER
        4 LauncherObject 208A6C SOURCE_HANDLER
        5 FreezerObject 208574 SOURCE_HANDLER
        6 TestObject 20609E SOURCE_HANDLER
        7 TunnelPathObject 208C3A SOURCE_HANDLER
        8 NullObject 203C8A NATIVE_NULL
        9 NullObject 203C8A NATIVE_NULL
        10 SpringObject 20998E SOURCE_HANDLER
        11 HDoorSplashObject 20646E SOURCE_HANDLER
        12 HDoorSplashSetObject 206436 SOURCE_HANDLER
        13 HDoorObject 20635E SOURCE_HANDLER
        14 TunnelSplashObject 206308 SOURCE_HANDLER
        15 MoveSpringObject 209842 SOURCE_HANDLER
        16 RingObject 209E5E SOURCE_HANDLER
        17 LostRingObject 20A142 SOURCE_HANDLER
        18 NullObject 203C8A NATIVE_NULL
        19 CheckpointObject 205F2C SOURCE_HANDLER
        20 BigRingObject 20B550 SOURCE_HANDLER
        21 CapsuleObject 20B2D2 SOURCE_HANDLER
        22 GoalObject 20B688 SOURCE_HANDLER
        23 SignpostObject 20B80C SOURCE_HANDLER
        24 ExplosionObject 206144 SOURCE_HANDLER
        25 MonitorTimeObject 20A680 SOURCE_HANDLER
        26 MonitorItemObject 20A7FC SOURCE_HANDLER
        27 BoulderObject 2097CA SOURCE_HANDLER
        28 HudPointsObject 20ACD4 SOURCE_HANDLER
        29 NullObject 203C8A NATIVE_NULL
        30 RollTunnelObject 2066B0 SOURCE_HANDLER
        31 FlowerObject 2061D2 SOURCE_HANDLER
        32 PistonObject 20C646 SOURCE_HANDLER
        33 ElectricBeamsObject 20C7CC SOURCE_HANDLER
        34 PlatformObject 20CAB8 SOURCE_HANDLER
        35 BouncePlatformObject 20CBCC SOURCE_HANDLER
        36 SwitchObject 20CDA4 SOURCE_HANDLER
        37 DoorObject 20CE80 SOURCE_HANDLER
        38 CrusherObject 20D016 SOURCE_HANDLER
        39 TubeDoorObject 20D1CC SOURCE_HANDLER
        40 SpinPlatformObject 20D366 SOURCE_HANDLER
        41 AnimalObject 20E6D8 SOURCE_HANDLER
        42 SnakeBlocksObject 20E9C8 SOURCE_HANDLER
        43 EggmanStatueObject 20ECBE SOURCE_HANDLER
        44 SeesawObject 20EF3A SOURCE_HANDLER
        45 SpikeChainObject 20F268 SOURCE_HANDLER
        46 RobotGeneratorObject 20F514 SOURCE_HANDLER
        47 ProjectorObject 20F97C SOURCE_HANDLER
        48 BataPyonObject 20D6BC SOURCE_HANDLER
        49 PohBeeObject 20D942 SOURCE_HANDLER
        50 SemiObject 20DE22 SOURCE_HANDLER
        51 MinomusiObject 20E268 SOURCE_HANDLER
        52 NullObject 203C8A NATIVE_NULL
        53 NullObject 203C8A NATIVE_NULL
        54 NullObject 203C8A NATIVE_NULL
        55 NullObject 203C8A NATIVE_NULL
        56 NullObject 203C8A NATIVE_NULL
        57 NullObject 203C8A NATIVE_NULL
        58 ResultsObject 20BFB6 SOURCE_HANDLER
        59 GameOverObject 20BD14 SOURCE_HANDLER
        60 TitleCardObject 20BE30 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR62C() { return layouts("""
        0 72 37 8 298
        1 370 25 3 78
        2 448 1 1 4
        3 452 33 6 200
        4 946 1 1 4
        5 652 1 1 4
        6 656 48 6 290
        7 946 1 1 4
        8 946 1 1 4
        9 950 1 1 4
        10 950 1 1 4
        11 950 1 1 4
        12 72 37 8 298
        13 370 25 3 78
        14 448 1 1 4
        15 452 33 6 200
        16 946 1 1 4
        17 652 1 1 4
        18 656 48 6 290
        19 946 1 1 4
        20 946 1 1 4
        21 950 1 1 4
        22 950 1 1 4
        23 950 1 1 4
        24 72 37 8 298
        25 370 25 3 78
        26 448 1 1 4
        27 452 33 6 200
        28 946 1 1 4
        29 652 1 1 4
        30 656 48 6 290
        31 946 1 1 4
        32 946 1 1 4
        33 950 1 1 4
        34 950 1 1 4
        35 950 1 1 4
        """); }
    private static List<Range> rangesR62D() { return ranges("""
        PlayerAnims 5AA6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 794C 2890 e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49
        FreezerAnims 895A 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites 89D6 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites 8C42 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        BoulderSprites 985C 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D30 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D4C 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9D9A 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9DA8 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9DF8 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9E70 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        RingAnims A2FA 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A302 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A346 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A3E8 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AA0C 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AA94 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites ACAA 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites ADAC 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BA10 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BA1C 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BBBC 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites BE20 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C2B6 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include27 C524 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites C564 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        PistonSprites C7CE 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites CAC4 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites CBE2 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites CD24 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites CE8C 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites D018 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites D1A6 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites D362 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims D66E 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites D678 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        BataPyonSprites1 D922 36 7326fd82430171bdb477583a92aff2b488235f641c6891423bb6c76cd85e57bc
        BataPyonSprites2 D946 36 db5621cfcda68fd6f78a225d39fc8ebdc7b5382853b401967bc4b021d1f798f1
        PohBeeAnims DC08 12 c86e33e6f1996609e11febe1933dff5d5c5a427d4ad07b4a936b7472a08b6cd7
        PohBeeSprites1 DC14 232 9eb78a364f4507fa39e790c73f936255440ad9aef3ff7caf6460605ad651cc8c
        PohBeeSprites2 DCFC 126 5fce6f0be29590775094c1f71dade4cdc358b25708710d9af00d485e27989322
        PohMissileAnims DE00 6 e54c0d9e39e4ffa2468634a3dfb77989127f4e20cdd6896bf7d76dbc8cd218b5
        PohMissileSprite DE06 67 e9736b603419bdba9830607712a810a49a5326b73bf7e9f4d5850996d8e2fa31
        SemiAnims DFF6 8 ace8805c21d329ec5610f8ee7491565574b8d1591287c4e197138af6561f714f
        SemiSprites1 DFFE 232 258c6231a34715e9194be8eda2003cc831daee653cda3b1eddd8d51b22fad2a2
        SemiSprites2 E0E6 202 2f17280c62d8118fda215b46ff2e1a7f8b2afcd1a6dcf8bb0d6ade5e0dedab83
        MinomusiAnims E548 78 69d8e498d0fd58e5224b753fac8cad64bb5fe75edb7d0d13f19b02beb82b8263
        MinomusiSprites E596 361 e86dff8643665150c22631619a8748f8d3b77718ecfdd68542633e6682e9f595
        AnimalAnims1 E924 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 E966 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9A2 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 E9B6 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites EC76 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites EE5E 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        SeesawAnims F1BE 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites F1E6 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 F4D0 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 F534 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims F708 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F710 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims FC3A 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites FC7E 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        StageChunks 10000 59904 4f89384da3c64e3f9dfc4294dea3b3fdc9924b839416eeced4115daa3a28dca0
        Include73 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include74 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include75 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include82 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 900 6833af533a4649ce1635fc966c8c8393ab8aa3cec5db173d7e5cd4e54b12c174
        StageMapFg 36BC2 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        StageMapBg 36CEC 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        StageBlocks 36F30 4780 78d0226264c0a1823c045338344c269e3620ca10eba8c3dd1dabbfcd26e61537
        StageGfx 381DC 10336 64bf4dc3ff3519a0e7d60fdb28c77cd4436bd6dd3116f0185354224a5a168180
        PowerupAnims 3AA3C 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3AB64 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3AC76 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3AC8E 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3ACF2 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3AD00 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3AD1C 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3AD24 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3AD3C 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3AD50 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3ADE2 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3AEE2 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3AEE8 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3AF2A 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3AF32 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3AFE2 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3AFF0 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3B174 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3B2B2 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3B658 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B9F8 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3BD98 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3BE78 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3BECA 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3BF9E 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3C066 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3C21C 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3C34E 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3C56E 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3C68E 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C7BE 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C8EE 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3CA20 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3CC94 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3CFF4 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3D2B6 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3D378 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3D458 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3D6B0 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D940 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3DB7A 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3DC52 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        Padding3 3DE46 8300 68b4af0d601dbd97988d78ccee3ae58c18839ec5ccaefa0bcde888c52a02721b
        Padding3_182 3FEB2 76 7e912a6bb154e72bc0c61027ed1ab2e6c35791edc3fcafead86ac78b72ca6512
        Padding3_183 3FEFE 258 c108a9166f9d02003e2cbe79784145b6957330a99c9c05c791435306f8553cc4
        MoveSpringSprites 9E76 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        SemiBombAnims E27A 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SemiBombSprites E280 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombAnims EF4C 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        StatueBombSprites EF52 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        Include70 1EA00 512 2fb0136233f3ef16d783747ec5e529b5b12d45845f1b9bbdbf8f6a316714e806
        Include71 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include72 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include76 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include77 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include78 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include79 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include80 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include81 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36D36 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36D3A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36E02 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36E06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36F28 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36F2C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3B06E 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B0A2 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        DataChunks 10000 59904 4f89384da3c64e3f9dfc4294dea3b3fdc9924b839416eeced4115daa3a28dca0
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AA6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A56 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BC2 240 2a92ba281930759bd761ef1e6961fa318110382d13e5117cc8757b90b8d0805b
        DataPaletteDirectory 670 56 05ba7100b3b9feba802b9b61cdef4ad55dedb871664828713c9c21bbd6022145
        DataLayoutDirectory 36B7A 72 35aebc23736fc7baa26c800d824270a42934de6ddde847f129ece186327d9107
        DataStageData FED6 16 b368a2fb78d722eadd40c344eb822dbae5222263b664ca982923ec66f4f4aea4
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 7958 2872 5a71aa2d9a830e90dbdd4bfab31550a2c1c9dc42a30359d75a74372b21316274
        PlcRead FEE6 258 041f93d606b1a4356cc84149b94baec8d84eed3baa5f644da150ef3ecebb19b8
        PlcDirectory FEE6 38 b27fb43a292fb78a709f81b06a2f3322f92505d3134685bb227663c0d63b0f39
        PlcBody0 FF0C 8 cd27c6a1d0bb91b5143afeea4efcefde50754b5661bf8aee2652b71bc1a807c9
        PlcBody1 FF14 92 4733465503d75774dcfe4a275789a57a2c3c4c0b90a40def2a80d2690cf61f38
        PlcBody2 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody3 FF0C 8 cd27c6a1d0bb91b5143afeea4efcefde50754b5661bf8aee2652b71bc1a807c9
        PlcBody4 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody5 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody6 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody7 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody8 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody9 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody10 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody11 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody12 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody13 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody14 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody15 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody16 FFCC 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FF70 92 4a1578efd5ec27552e5a48f7b5755d20b6951a2296502a6956b744274e8ef94a
        PlcBody18 FFD4 20 a4fe3f39791839e7b7da33a3157ba67ed09649a9d226083e92a31027abffe192
        PaletteRead0 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead1 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead2 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead3 6A8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6C8 96 92913bec27c270a76b29b121ef932157c3e4bf23adee2f3857dedd1a2a1dd6ed
        PaletteRead5 738 96 67777001952483d7e76e4b55c1828afd70db82a3baf8e2e02c158636a1298adb
        PaletteRead6 798 32 f4bd33166dece487a24e428a6907c1fa945a825de5dcd4e4f5ae8dfd4957e0f3
        LayoutRead0 36BC2 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead1 36CEC 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead2 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36D3A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36E02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36E06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36BC2 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead13 36CEC 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead14 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36D3A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36E02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36E06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36BC2 298 9402250cde510bcd4a88152a4396c1751887d83f19c0e6dca641ca34086dca8d
        LayoutRead25 36CEC 74 b249929cb2a61ec90246aff2b70ec42659e7871acc86eb64b17e259c25ab5f31
        LayoutRead26 36D36 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36D3A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36E02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36E06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36F28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36F2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 de0b7e36c5abccb757e43f62a09469e5e08c848e2f728efcc86cb1b6bdc2d7c4
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 dbccd11579c8608c7b701f70c3cee86ad5ab2a70cbf075bdd4c9eddca3bb478e
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 abe68fcfa20618add82b91db86ef4be932c11f6f120f94648b75a62a0940c895
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 e7c64d45a16325976c83a617e285a4ee0e7f8cdaa2028c512e6262d7dc934dba
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 2FEA 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3352 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3320 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 8baa3ec49a6d39417cb94fe7676b0b80660b459f22454a51148e0ee78557319b
        FiniteTable11 28E6 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        FiniteTable12 2964 8 1a304cf962600bec3953c388a78a639e6bd0de81486301729aee33757379d6fd
        FiniteTable13 2A7A 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28E6 12 cf063653b562a822f97c7be24b904af41a96a71120fd9f4ecec18b085ae459cf
        """); }
    private static List<Operand> operandsR62D() { return operands("""
        2F84 267b0064
        300E 267b00da
        336A 267b00e6
        E7AA 43fa0178
        E7F2 43fa0130
        E8E2 43fa0082
        B664 43f90023af2a
        B566 43f90020ba10
        60BA 43f90023aee2
        61D0 43f90023ad3c
        62C4 43f9002336bc
        8636 43f90020895a
        24CC 43f90020fee6
        24FE 43f90020fee6
        263A 43f90020fee6
        6442 43f90023acf2
        FC2A 43fa000e
        B0B4 43f9002330bc
        B116 43f9002330bc
        B192 43f9002330bc
        B202 43f9002330bc
        B2A8 43f9002330bc
        1B6A 43f900232f3c
        E464 43fa00e2
        E314 43fa0280
        A61A 43f90020aa0c
        A79A 43f90020aa0c
        9956 43f900209e70
        57B6 43f900205aa6
        5D32 43f900220000
        5D02 45f90022e8ac
        58C8 43f900205c82
        59A2 43f900205b3e
        5994 43f900205c16
        5940 43f900205cb0
        594E 43f900205b2e
        5924 43f900205b36
        5916 43f900205c04
        58BA 43f900205ca8
        58D4 43f900205b26
        5A22 43f900205bfe
        58E0 43f900205b1e
        5A2E 43f900205bf8
        D986 43fa0280
        D9E4 43fa022e
        D9F4 43fa0306
        DDF6 43fa0008
        612A 43f90023aa3c
        6596 43f90023aa3c
        663A 43f90023aa3c
        A102 43f90020a2fa
        A2E8 43f90020a2fa
        F65C 43fa00aa
        F0A6 43fa0116
        DF60 43fa0094
        DFEC 43fa0008
        DEB4 43fa0148
        DEBE 43fa0226
        B8EE 43f90023afe2
        D3F8 43fa0274
        64E6 43f90023ac76
        9B44 43f900209d9a
        9BEC 43f900209d9a
        9C56 43f900209d9a
        9D14 43f900209d9a
        118A 45f9002346f6
        124A 45f9002346f6
        12FA 45f9002346f6
        13AA 45f9002346f6
        11B8 45f9002347f6
        1278 45f9002347f6
        1416 43f9002347f6
        141C 45f9002347f6
        1450 43f9002347f6
        145E 43f9002347f6
        1464 45f9002347f6
        1328 45f9002357f6
        13D8 45f9002357f6
        1456 45f9002357f6
        1662 45f90020fed6
        339C 45f90020fed6
        76B4 41f90020794c
        BEAE 45f90020c524
        6370 43f90023ad1c
        100 4ef90020011e
        10C 4ef9002020c2
        112 4ef900201c38
        37B0 43f900203bc2227100fc4e91
        """); }
    private static List<Dispatch> dispatchR62D() { return dispatch("""
        1 PlayerObject 203D20 SOURCE_HANDLER
        2 PlayerObject 203D20 SOURCE_HANDLER
        3 PowerupObject 2064FC SOURCE_HANDLER
        4 LauncherObject 208A94 SOURCE_HANDLER
        5 FreezerObject 20859C SOURCE_HANDLER
        6 TestObject 2060C6 SOURCE_HANDLER
        7 TunnelPathObject 208C62 SOURCE_HANDLER
        8 NullObject 203CB2 NATIVE_NULL
        9 NullObject 203CB2 NATIVE_NULL
        10 SpringObject 2099B6 SOURCE_HANDLER
        11 HDoorSplashObject 206496 SOURCE_HANDLER
        12 HDoorSplashSetObject 20645E SOURCE_HANDLER
        13 HDoorObject 206386 SOURCE_HANDLER
        14 TunnelSplashObject 206330 SOURCE_HANDLER
        15 MoveSpringObject 20986A SOURCE_HANDLER
        16 RingObject 209E86 SOURCE_HANDLER
        17 LostRingObject 20A16A SOURCE_HANDLER
        18 NullObject 203CB2 NATIVE_NULL
        19 CheckpointObject 205F54 SOURCE_HANDLER
        20 BigRingObject 20B578 SOURCE_HANDLER
        21 CapsuleObject 20B2FA SOURCE_HANDLER
        22 GoalObject 20B6B0 SOURCE_HANDLER
        23 SignpostObject 20B834 SOURCE_HANDLER
        24 ExplosionObject 20616C SOURCE_HANDLER
        25 MonitorTimeObject 20A6A8 SOURCE_HANDLER
        26 MonitorItemObject 20A824 SOURCE_HANDLER
        27 BoulderObject 2097F2 SOURCE_HANDLER
        28 HudPointsObject 20ACFC SOURCE_HANDLER
        29 NullObject 203CB2 NATIVE_NULL
        30 RollTunnelObject 2066D8 SOURCE_HANDLER
        31 FlowerObject 2061FA SOURCE_HANDLER
        32 PistonObject 20C66E SOURCE_HANDLER
        33 ElectricBeamsObject 20C7F4 SOURCE_HANDLER
        34 PlatformObject 20CAE0 SOURCE_HANDLER
        35 BouncePlatformObject 20CBF4 SOURCE_HANDLER
        36 SwitchObject 20CDCC SOURCE_HANDLER
        37 DoorObject 20CEA8 SOURCE_HANDLER
        38 CrusherObject 20D03E SOURCE_HANDLER
        39 TubeDoorObject 20D1F4 SOURCE_HANDLER
        40 SpinPlatformObject 20D38E SOURCE_HANDLER
        41 AnimalObject 20E700 SOURCE_HANDLER
        42 SnakeBlocksObject 20E9F0 SOURCE_HANDLER
        43 EggmanStatueObject 20ECE6 SOURCE_HANDLER
        44 SeesawObject 20EF62 SOURCE_HANDLER
        45 SpikeChainObject 20F290 SOURCE_HANDLER
        46 RobotGeneratorObject 20F53C SOURCE_HANDLER
        47 ProjectorObject 20F9A4 SOURCE_HANDLER
        48 BataPyonObject 20D6E4 SOURCE_HANDLER
        49 PohBeeObject 20D96A SOURCE_HANDLER
        50 SemiObject 20DE4A SOURCE_HANDLER
        51 MinomusiObject 20E290 SOURCE_HANDLER
        52 NullObject 203CB2 NATIVE_NULL
        53 NullObject 203CB2 NATIVE_NULL
        54 NullObject 203CB2 NATIVE_NULL
        55 NullObject 203CB2 NATIVE_NULL
        56 NullObject 203CB2 NATIVE_NULL
        57 NullObject 203CB2 NATIVE_NULL
        58 ResultsObject 20BFDE SOURCE_HANDLER
        59 GameOverObject 20BD3C SOURCE_HANDLER
        60 TitleCardObject 20BE58 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR62D() { return layouts("""
        0 72 37 8 298
        1 370 24 3 74
        2 444 1 1 4
        3 448 33 6 200
        4 942 1 1 4
        5 648 1 1 4
        6 652 48 6 290
        7 942 1 1 4
        8 942 1 1 4
        9 946 1 1 4
        10 946 1 1 4
        11 946 1 1 4
        12 72 37 8 298
        13 370 24 3 74
        14 444 1 1 4
        15 448 33 6 200
        16 942 1 1 4
        17 648 1 1 4
        18 652 48 6 290
        19 942 1 1 4
        20 942 1 1 4
        21 946 1 1 4
        22 946 1 1 4
        23 946 1 1 4
        24 72 37 8 298
        25 370 24 3 74
        26 444 1 1 4
        27 448 33 6 200
        28 942 1 1 4
        29 648 1 1 4
        30 652 48 6 290
        31 942 1 1 4
        32 942 1 1 4
        33 946 1 1 4
        34 946 1 1 4
        35 946 1 1 4
        """); }
    private static List<Range> rangesR63C() { return ranges("""
        PlayerAnims 5AE6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 78D8 602 b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7
        BoulderSprites 8080 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 8554 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 8570 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 85BE 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 85CC 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 861C 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        RingAnims 8B1E 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 8B26 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 8B6A 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 8C0C 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims 9230 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites 92B8 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites 94CE 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites 95D0 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims A234 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites A240 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx A3E0 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites A644 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites AADA 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include24 AD48 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites AD88 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        FreezerAnims B34C 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites B3C8 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites B634 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        PistonSprites BE62 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites C158 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites C276 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites C3B8 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites C520 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites C6AC 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites C83A 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites C9F6 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims CD02 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites CD0C 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        AnimalAnims1 CF9C 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 CFDE 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 D01A 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 D02E 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites D2EE 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites D4D6 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        StatueBombAnims D5C4 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SeesawAnims D836 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites D85E 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 DB48 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 DBAC 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims DD80 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites DD88 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims E2B2 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites E2F6 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        EggmanAnims F94E 55 1b8ac387de17c66de217d6275a5b5ae667f89ebabe8d0e17eaa8b65403470887
        EggmanSprites F986 408 65d01de0ac514019730975ac79bc860a5c512cb5069d7a4785ed35f0828eb571
        BossMachineSprites FB1E 77 44ca063d3e68c3b0791ff8833ad6ac1346ad5a921f18af2cc4eaeee402daec3b
        BossSpikesSprites FB72 32 1837d09f2775731bf46db8d6f5550c6af15fffe4361b3d564e81ee068c1064e7
        BossExhaustAnims FB92 14 57887f6697ed4c593aeea81fd34100c68de3a8c06f992cc39132f4d0ae08ecd0
        BossExhaustSprites FBA0 32 056aa78a051b93c91fc16e7d6c5b6bb26bc4ffdc2d621a3524aaf03010767705
        FallSpikeSprites FBC0 8 5152bfbd66156ee4f8751918fdae066b2a27873964bd9787d464fcf6378e70ad
        BossSmokeAnims FBC8 21 684898f0213704c458400fbd5a6179b2d346749119a9961dcd224570314e7aae
        BossSmokeSprites FBDE 58 1fa6e4e52a156b3396170905adbb3033ee14c5931709793d8c709ee5d99c149a
        ConductorSprites2 FC18 16 d464e009c601c5ea9302bdc6cd3be4add1eace9fac8473812f64613224c8942b
        ConductorSprites1 FC28 41 7dde76851238ff5b785e570f1a5abcb317ae6f17014e36d511dcb43541eea2d3
        DebrisSprites1 FC58 16 cda6a02c1db7bbda08d6bb70a40b194a17a615b48c4077cadc61a2ce9c3976c6
        DebrisSprites2 FC68 32 a2ec8be73e408f92c53fb60398e249665996c2393278d196bbd397a052db517d
        FloorPieceSprites FC88 13 7b6d0c3973a8419261590e9b38a7dd65fa663c5ffa498b5be8388b9d82b0eaa5
        ElectricityAnims FC96 20 ef863551f1764bc86848dfd1730ab05a3760598e948716d00673a99ee3e4bd64
        ElectricitySprites FCAA 169 c08a9181abb1a8c55ee023982ae139f2279089aaa1e49617e9e7da3b8f087cf8
        StageChunks 10000 47430 578b1d086b3fc43d606afc577c03994b3246e5cb4a11504363a2e20db5b912d8
        Include74 1B94A 28 c0538270fe4549fd10a13cc87c00bb0d62f478fedea4eedf3a9cb52cc18c62c3
        Include75 1B96A 28 001d37660e060e823a10aa0edb4a996a28b2a561db2fec5b6b8cd44200432e4f
        Include76 1B98A 12918 9066d101d186ef771824da0f78bb3b891209f42ca5d42254fe0e40da7d604de9
        Include79 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include80 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include81 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include88 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 768 b01116b5d244fd257f4825a0589f64d409abd5701303da9c00257f2d048daa8e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 900 4c66f5c87acb97e57abb7d10871270be3cba9cd8342f481f67cc07738b006a25
        StageMapFg 36BC2 122 aca099590357f32b3253fce02ba7692a904cf4784eba02c653164b7aaf8a763a
        StageMapBg 36C3C 32 703d4c7211a097467a9e02ceba49aa7995312f931d2ca8073a01adc0461f16b4
        StageBlocks 36E56 4158 1ca7083a6db93e2d54cd9b076eaafbc7549231caf76c0ddc7ac30b5c16ed604d
        StageGfx 37E94 9832 840ac9d0e50ed39596bd4e21a1e5a1e2a8f77db3e9a7416832cf2d4644d2b3da
        PowerupAnims 3A4FC 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3A624 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3A736 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3A74E 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3A7B2 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3A7C0 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3A7DC 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3A7E4 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3A7FC 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3A810 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3A8A2 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3A9A2 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3A9A8 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3A9EA 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3A9F2 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3AAA2 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3AAB0 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        PistonGfx 3AC34 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3AD72 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3B118 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3B4B8 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B858 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B938 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B98A 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3BA5E 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3BB26 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3BCDC 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3BE0E 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3C02E 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3C14E 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3C27E 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3C3AE 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3C4E0 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C754 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3CAB4 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3CD76 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3CE38 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3CF18 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3D170 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3D400 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D63A 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D712 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        HologramAnimalsGfx 3D906 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        RobotTransportGfxA 3DAF4 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        RobotTransportGfxB 3DC40 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        HologramGfx 3DDF2 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        BossMachineGfx 3E1F2 2812 8c8f31a88de30a5e73daf8152132ccaed401965047958793db9307e4a5c03aa9
        EggmanGfx 3ECEE 1254 2b6276cf6f752760e7f06e1c2400d5e3bbe2895bc4614d130441ee73bfe12777
        EggmanEscapeGfx 3F1D4 1518 70a6d91caf68da8ce7d7608322a41bc909d43dc86bf82ed71e5df28686e71b15
        BossDestroyGfx 3F7C2 714 5946441b9f01a65a90ee607a3e3c22d5d92aaee0b2edf83d5057539966af632f
        BossFloorGfxC 3FA8C 448 fd886c9e2ae536f86c79937e7beff3812e62935d0f4eabf457d34991839ac112
        BossPiecesGfxC 3FC4C 336 61cb7a7ee70c51c9264a4610a21f0fc3f15678aced1c7f2fb7181926c3df65e1
        Include195 3FEFA 262 a74df89250bc6eea1da5fc994bc2fe3b681c42e4771bb21623bd798bd317f04f
        MoveSpringAnims 8694 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 869A 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombSprites D5CA 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        BossSpikesAnim FB6C 5 a63a2cb7482eff131ed8f035c257d0d2b933283f3a0dddf4df380357fba8f9d0
        DebrisAnims FC52 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        Include77 1EC00 1848 e37ecee7f3191deb44b60032ac7b355af3ce617c6b9ea87470132356a2c3e76c
        Include78 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include82 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include83 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include84 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include85 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include86 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include87 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36C5C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36C60 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D28 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36D2C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36E4E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36E52 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        CapsuleAnims 3AB2E 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3AB62 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        Include193 3FD9C 278 556430a544ed986a5a6937637f17f9f3d180a19bd2b8fedae7513e8d97a75013
        Include194 3FEB2 72 966e345e0a34ef74e15a217a429cc6e9d92f5d95f2303d6358fcb75c847274f3
        DataChunks 10000 60416 ff4664329c024b1aeef39b4571978e41b158d8315f9dadc59481cafae2065e42
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5AE6 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5A96 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3BF2 256 dbd75e5b9bd63c3d27f848813ae4e58f7f6d402cc7c48871e65b6ec13f5a052a
        DataPaletteDirectory 658 56 572439398ace3882b6286958f329a4fd87cd5459e26e62af3f4d12b092338ff2
        DataLayoutDirectory 36B7A 72 1909889a64be0a02be63b83ff1bf28bacfbddf321ba768ec768c4749c754383d
        DataStageData E51C 16 b95ee7a0acf8d8c8781ba085994b10df924c49787c6eba4d770fdb3cb6afed02
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 78E4 584 4ff4271b493c99826b22aa564dbdf11883d8fa516ed4ccbb5b5ea923d4983f12
        PlcRead E52C 260 b9e99c12a7c1e3fb7b0446f794d36b726f1f974438b2ff14ec92a421b82c0107
        PlcDirectory E52C 42 935e13faca92084a7215c97e59b29e8ac45c5836644efa2a36a8c370b8d85c51
        PlcBody0 E556 8 b482ad78a0f8e8e451767a21311c944fd2ad45152fac5e13ec45db5a6820a9b8
        PlcBody1 E55E 92 ef34bf682141986c27d0252ac40fad8077ab05f73737edd219e3cb3e11eec859
        PlcBody2 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody3 E556 8 b482ad78a0f8e8e451767a21311c944fd2ad45152fac5e13ec45db5a6820a9b8
        PlcBody4 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody5 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody6 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody7 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody8 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody9 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody10 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody11 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody12 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody13 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody14 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody15 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody16 E5FE 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 E5BA 68 45f82e9657fcfd0705886d8dff6af26fd92a5ae6d07994e20c850317cb714305
        PlcBody18 E606 8 2f3cc65aa26fa21612b5762b61784d4808fe72e60957dcd1efbc2c8e83802900
        PlcBody19 E60E 20 3c2a6289bd0bd46e9ab6009933fb124d8ddd0e3f15ca638e72a7005fabe63c9e
        PlcBody20 E622 14 55e4a3d512d0c100a08241703192e67a86d75c10b597253800e9a16913b30709
        PaletteRead0 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead1 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead2 690 128 5ab5000425a5982e73ed53ba981d9525d59b8d75d5e2615371f92d5095dd90e3
        PaletteRead3 690 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B0 96 bbeb532298cbf07c3f15b49f71be2e54da40985b83da4e7f21324ea50bba918e
        PaletteRead5 710 96 0ca572dacebf4a938307941acb242dd4165ee58b66663bf0c6114a5ba8dc1a75
        PaletteRead6 770 32 516677dca3a2a00076a8ec4d0c1078990a2a47f74c281b2bd116f61ba90834ed
        LayoutRead0 36BC2 122 aca099590357f32b3253fce02ba7692a904cf4784eba02c653164b7aaf8a763a
        LayoutRead1 36C3C 32 703d4c7211a097467a9e02ceba49aa7995312f931d2ca8073a01adc0461f16b4
        LayoutRead2 36C5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36C60 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36D2C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36BC2 122 aca099590357f32b3253fce02ba7692a904cf4784eba02c653164b7aaf8a763a
        LayoutRead13 36C3C 32 703d4c7211a097467a9e02ceba49aa7995312f931d2ca8073a01adc0461f16b4
        LayoutRead14 36C5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36C60 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36D2C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36BC2 122 aca099590357f32b3253fce02ba7692a904cf4784eba02c653164b7aaf8a763a
        LayoutRead25 36C3C 32 703d4c7211a097467a9e02ceba49aa7995312f931d2ca8073a01adc0461f16b4
        LayoutRead26 36C5C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36C60 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36D2C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36E4E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36E52 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 c660f9d2402fe9560d567ad84d958fb9957a0627f75e785a17feae82da9f724a
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 3d44320917a6aa51cd48df2f534cdb6af5704a8eb7e7c5c6eda39a1fbcd99640
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 1f962831888e887b9918d2707d8b06f25957d545577673f6efe897605e525c3a
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 173fa6a788a2f7dbd46e257f5c0426b4937bcafe5817aacf5a48e45226a55e9f
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 301A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 3382 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3350 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 3d68df71db6b7b3afe494c7171606be307653e61e478f284c4949cda9d8d6202
        FiniteTable11 28D0 44 f99bee7f4957e703122e02b57276f5763bcd73e5a5cbce1b8e6aa934c666509d
        FiniteTable12 2994 8 619ae91aae9c6e179d488f861a036133461b9e1907a641eb365b9b634d080cbd
        FiniteTable13 2AAA 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28D0 12 cfec5298b11a781264ddad6df240bba70b55e77b5d71b42667e76b64da64d779
        """); }
    private static List<Operand> operandsR63C() { return operands("""
        2FB4 267b0064
        303E 267b00da
        339A 267b00e6
        CE22 43fa0178
        CE6A 43fa0130
        CF5A 43fa0082
        9E88 43f90023a9ea
        9D8A 43f90020a234
        EE54 43f90020fb92
        EF44 43f90020fbc8
        EF7A 43f90020fbc8
        60FA 43f90023a9a2
        E64A 43f90020f94e
        6210 43f90023a7fc
        6304 43f9002336bc
        B028 43f90020b34c
        24A4 43f90020e52c
        24D6 43f90020e52c
        2612 43f90020e52c
        6482 43f90023a7b2
        E2A2 43fa000e
        98D8 43f9002330bc
        993A 43f9002330bc
        99B6 43f9002330bc
        9A26 43f9002330bc
        9ACC 43f9002330bc
        1B42 43f900232f3c
        8E3E 43f900209230
        8FBE 43f900209230
        57F6 43f900205ae6
        5D72 43f900220000
        5D42 45f90022e8ac
        5908 43f900205cc2
        59E2 43f900205b7e
        59D4 43f900205c56
        5980 43f900205cf0
        598E 43f900205b6e
        5964 43f900205b76
        5956 43f900205c44
        58FA 43f900205ce8
        5914 43f900205b66
        5A62 43f900205c3e
        5920 43f900205b5e
        5A6E 43f900205c38
        616A 43f90023a4fc
        65D6 43f90023a4fc
        667A 43f90023a4fc
        8926 43f900208b1e
        8B0C 43f900208b1e
        DCD4 43fa00aa
        D71E 43fa0116
        A112 43f90023aaa2
        CA8C 43fa0274
        6526 43f90023a736
        8368 43f9002085be
        8410 43f9002085be
        847A 43f9002085be
        8538 43f9002085be
        1162 45f9002346f6
        1222 45f9002346f6
        12D2 45f9002346f6
        1382 45f9002346f6
        1190 45f9002347f6
        1250 45f9002347f6
        13EE 43f9002347f6
        13F4 45f9002347f6
        1428 43f9002347f6
        1436 43f9002347f6
        143C 45f9002347f6
        1300 45f9002357f6
        13B0 45f9002357f6
        142E 45f9002357f6
        163A 45f90020e51c
        33CC 45f90020e51c
        7640 41f9002078d8
        D59E 43fa0024
        A6D2 45f90020ad48
        63B0 43f90023a7dc
        100 4ef90020011e
        10C 4ef90020209a
        112 4ef900201c10
        37E0 43f900203bf2227100fc4e91
        """); }
    private static List<Dispatch> dispatchR63C() { return dispatch("""
        1 PlayerObject 203D60 SOURCE_HANDLER
        2 PlayerObject 203D60 SOURCE_HANDLER
        3 PowerupObject 20653C SOURCE_HANDLER
        4 NullObject 203CF2 NATIVE_NULL
        5 NullObject 203CF2 NATIVE_NULL
        6 TestObject 206106 SOURCE_HANDLER
        7 TunnelPathObject 20B654 SOURCE_HANDLER
        8 NullObject 203CF2 NATIVE_NULL
        9 NullObject 203CF2 NATIVE_NULL
        10 SpringObject 2081DA SOURCE_HANDLER
        11 HDoorSplashObject 2064D6 SOURCE_HANDLER
        12 HDoorSplashSetObject 20649E SOURCE_HANDLER
        13 HDoorObject 2063C6 SOURCE_HANDLER
        14 TunnelSplashObject 206370 SOURCE_HANDLER
        15 MoveSpringObject 20808E SOURCE_HANDLER
        16 RingObject 2086AA SOURCE_HANDLER
        17 LostRingObject 20898E SOURCE_HANDLER
        18 NullObject 203CF2 NATIVE_NULL
        19 NullObject 203CF2 NATIVE_NULL
        20 BigRingObject 209D9C SOURCE_HANDLER
        21 CapsuleObject 209B1E SOURCE_HANDLER
        22 GoalObject 209ED4 SOURCE_HANDLER
        23 SignpostObject 20A058 SOURCE_HANDLER
        24 ExplosionObject 2061AC SOURCE_HANDLER
        25 MonitorTimeObject 208ECC SOURCE_HANDLER
        26 MonitorItemObject 209048 SOURCE_HANDLER
        27 BoulderObject 208016 SOURCE_HANDLER
        28 HudPointsObject 209520 SOURCE_HANDLER
        29 NullObject 203CF2 NATIVE_NULL
        30 RollTunnelObject 206718 SOURCE_HANDLER
        31 FlowerObject 20623A SOURCE_HANDLER
        32 PistonObject 20BD02 SOURCE_HANDLER
        33 ElectricBeamsObject 20BE88 SOURCE_HANDLER
        34 PlatformObject 20C174 SOURCE_HANDLER
        35 BouncePlatformObject 20C288 SOURCE_HANDLER
        36 SwitchObject 20C460 SOURCE_HANDLER
        37 DoorObject 20C53C SOURCE_HANDLER
        38 CrusherObject 20C6D2 SOURCE_HANDLER
        39 TubeDoorObject 20C888 SOURCE_HANDLER
        40 SpinPlatformObject 20CA22 SOURCE_HANDLER
        41 AnimalObject 20CD78 SOURCE_HANDLER
        42 SnakeBlocksObject 20D068 SOURCE_HANDLER
        43 EggmanStatueObject 20D35E SOURCE_HANDLER
        44 SeesawObject 20D5DA SOURCE_HANDLER
        45 SpikeChainObject 20D908 SOURCE_HANDLER
        46 RobotGeneratorObject 20DBB4 SOURCE_HANDLER
        47 ProjectorObject 20E01C SOURCE_HANDLER
        48 NullObject 203CF2 NATIVE_NULL
        49 NullObject 203CF2 NATIVE_NULL
        50 NullObject 203CF2 NATIVE_NULL
        51 NullObject 203CF2 NATIVE_NULL
        52 EggmanObject 20E630 SOURCE_HANDLER
        53 BossMachineObject 20ECF2 SOURCE_HANDLER
        54 BossSpikesObject 20ED76 SOURCE_HANDLER
        55 BossExhaustObject 20EE3A SOURCE_HANDLER
        56 FallSpikeObject 20EF92 SOURCE_HANDLER
        57 FloorDebrisObject 20F03A SOURCE_HANDLER
        58 ResultsObject 20A802 SOURCE_HANDLER
        59 GameOverObject 20A560 SOURCE_HANDLER
        60 TitleCardObject 20A67C SOURCE_HANDLER
        61 FloorPieceObject 20F128 SOURCE_HANDLER
        62 ConductorObject 20F380 SOURCE_HANDLER
        63 BossSmokeObject 20EEC0 SOURCE_HANDLER
        64 ElectricityObject 20F0B4 SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR63C() { return layouts("""
        0 72 15 8 122
        1 194 10 3 32
        2 226 1 1 4
        3 230 33 6 200
        4 724 1 1 4
        5 430 1 1 4
        6 434 48 6 290
        7 724 1 1 4
        8 724 1 1 4
        9 728 1 1 4
        10 728 1 1 4
        11 728 1 1 4
        12 72 15 8 122
        13 194 10 3 32
        14 226 1 1 4
        15 230 33 6 200
        16 724 1 1 4
        17 430 1 1 4
        18 434 48 6 290
        19 724 1 1 4
        20 724 1 1 4
        21 728 1 1 4
        22 728 1 1 4
        23 728 1 1 4
        24 72 15 8 122
        25 194 10 3 32
        26 226 1 1 4
        27 230 33 6 200
        28 724 1 1 4
        29 430 1 1 4
        30 434 48 6 290
        31 724 1 1 4
        32 724 1 1 4
        33 728 1 1 4
        34 728 1 1 4
        35 728 1 1 4
        """); }
    private static List<Range> rangesR63D() { return ranges("""
        PlayerAnims 5B0E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        Include2 7900 602 b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7
        BoulderSprites 80A8 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 857C 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 8598 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 85E6 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 85F4 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 8644 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        RingAnims 8B46 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 8B4E 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 8B92 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 8C34 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims 9258 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites 92E0 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites 94F6 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites 95F8 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims A25C 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites A268 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx A408 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        OverSprites A66C 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites AB02 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        Include24 AD70 64 0f494ad8802282c8e5b15ecb7e4fbe51f94d9e59164aa4088d26d274b6a67dd3
        TitleCardSprites ADB0 256 adb41a7ab6509a8bf5ca76544ca45fd28f0c7ec2bac2a3372734afc237ed91b5
        FreezerAnims B374 123 aec6f3ac5ad72ffe48269c9a0345c1caf24ec625391142d402577fc9ef0a9e32
        FreezerSprites B3F0 190 fc5d5b732e61cb31524ae73653843bd8d09092707953e02494c5105641eb8ae0
        LaunchSprites B65C 31 00a185c61a5abfd2abaad61dbc24f9de9a5cd33d8f92ff633270ca16bb71a5a6
        PistonSprites BE8A 37 6e2eff4d4fe49bc73efcebd42239dd3f276632488202df3b522cca48731f300f
        ElectricBeamsSprites C180 27 0fbbae19698a336916d0fbbdae9b21131e4dc5c0971570910e714940f049bb1b
        PlatformSprites C29E 18 fb44a7af4ad1250e6c37cdd60f74ccd1827f4360fb5083f4b7d130b9cee18224
        BouncePlatformSprites C3E0 13 62d1b311227c83e158ec91eb116f9071f7a272d352a70ba92ed4daa495056012
        SwitchSprites C548 27 1778ef458dfc80a18e67acc80a2c2d32648a00e1f4e10b5edf3e744f97c7e338
        DoorSprites C6D4 37 54812bb1d6a6aff7f33ee9cafdce4909024be201e68b9350924c8c393fa01ee6
        CrusherSprites C862 78 e9dbc11076ae87ddc431dc1f72a74142d3a9f591bc3635ebda6cc8c15e7334f3
        TubeDoorSprites CA1E 43 f2a16864dea09b21245929addce4081363b764827e2f5f68b3333c2670759ff2
        SpinPlatformAnims CD2A 10 3c30d3920157a6cbc83077c3e985479ea953854bea08c8ae9ec7fa6bd47cbcab
        SpinPlatformSprites CD34 108 65a6e5f8a8d6c4fc8ea82c56914a1d3e10ac3b108b0ecaa392379b84771fa30e
        AnimalAnims1 CFC4 66 5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3
        AnimalAnims2 D006 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 D042 19 2bb5f02e22ba89d4981e493057dd44d14eaf0f2c645299b6963618b553e9fdad
        AnimalSprites2 D056 35 facf6bf174bd667107f90f95309a5d243521aba09c1f266030af28d7238f6c34
        SnakeBlocksSprites D316 8 95a204bed4ca65eb87868612f2b7b7ee8e5ce1dd7e9337b0a625bbb35f9bdcf5
        EggmanStatueSprites D4FE 31 127e48260fc0ef9cebf3c27b82f15fc3d2969ed072d64f7e6ba3ceb3a0e6be7e
        StatueBombAnims D5EC 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        SeesawAnims D85E 40 799c7fc6458e348b74d16f15c000949a63b4cf9f6ffa0e94e7472d1f5fe5db7f
        SeesawSprites D886 170 a8fb8a5a5fd152fb168f53071d172407f9ce361866fa4aee89000988241b5d95
        SpikeChainSprites1 DB70 8 14c4c06b57c48c8ae80933182ebf7bec1fb1323f51b7016d81c172902c333c0f
        SpikeChainSprites2 DBD4 8 423bc0cb7960ea41e41b662441c5049a2777809d6a73660664d071f9b6b327bc
        RobotTransportAnims DDA8 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites DDB0 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        HologramAnims E2DA 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites E31E 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        EggmanAnims FA16 55 1b8ac387de17c66de217d6275a5b5ae667f89ebabe8d0e17eaa8b65403470887
        EggmanSprites FA4E 408 65d01de0ac514019730975ac79bc860a5c512cb5069d7a4785ed35f0828eb571
        BossMachineSprites FBE6 77 44ca063d3e68c3b0791ff8833ad6ac1346ad5a921f18af2cc4eaeee402daec3b
        BossSpikesSprites FC3A 32 1837d09f2775731bf46db8d6f5550c6af15fffe4361b3d564e81ee068c1064e7
        BossExhaustAnims FC5A 14 57887f6697ed4c593aeea81fd34100c68de3a8c06f992cc39132f4d0ae08ecd0
        BossExhaustSprites FC68 32 056aa78a051b93c91fc16e7d6c5b6bb26bc4ffdc2d621a3524aaf03010767705
        FallSpikeSprites FC88 8 5152bfbd66156ee4f8751918fdae066b2a27873964bd9787d464fcf6378e70ad
        BossSmokeAnims FC90 21 684898f0213704c458400fbd5a6179b2d346749119a9961dcd224570314e7aae
        BossSmokeSprites FCA6 58 1fa6e4e52a156b3396170905adbb3033ee14c5931709793d8c709ee5d99c149a
        ConductorSprites2 FCE0 16 d464e009c601c5ea9302bdc6cd3be4add1eace9fac8473812f64613224c8942b
        ConductorSprites1 FCF0 41 7dde76851238ff5b785e570f1a5abcb317ae6f17014e36d511dcb43541eea2d3
        DebrisSprites1 FD20 16 cda6a02c1db7bbda08d6bb70a40b194a17a615b48c4077cadc61a2ce9c3976c6
        DebrisSprites2 FD30 32 a2ec8be73e408f92c53fb60398e249665996c2393278d196bbd397a052db517d
        FloorPieceSprites FD50 13 7b6d0c3973a8419261590e9b38a7dd65fa663c5ffa498b5be8388b9d82b0eaa5
        ElectricityAnims FD5E 20 ef863551f1764bc86848dfd1730ab05a3760598e948716d00673a99ee3e4bd64
        ElectricitySprites FD72 169 c08a9181abb1a8c55ee023982ae139f2279089aaa1e49617e9e7da3b8f087cf8
        StageChunks 10000 60416 be85ff0f04d11b62578fb61a5e2478a862c03d21fef0e07b7727bc2aab31036b
        PowerupAnims 1EC00 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 1ED28 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 1EE3A 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 1EE52 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 1EEB6 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 1EEC4 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 1EEE0 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 1EEE8 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 1EF00 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 1EF14 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 1EFA6 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 1F0A6 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 1F0AC 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 1F0EE 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 1F0F6 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 1F1A6 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 1F1B4 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        Include94 1F500 700 839c68b093a5924e181363f192175a17094c1066bed711dd88f6e14e45273c1b
        Include95 1F804 122 5c37d7a442d89eec6cee5f92e271f983377fd2fce4aff12efa6fed41516aa433
        Include96 1F87E 34 9d9f61f1037e9125ac7c5f1d90294f2c42e4e2c0f187590eb4ed43651e04f8fd
        Include103 1FD9C 612 89f3f91c6133e083e0a0184d190a768f1b1a9862ae3021786be5dbed17051ac5
        PlayerGfx 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        PlayerSprites 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlayerGfxScript 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        PointsGfx 2EDE6 100 6923bd2ac955ac11b88bb75b036d2fbd24f02fe523cd401e30bb1520426ad363
        CapsuleGfx 2EE4A 1704 da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808
        BigRingGfx 2F4F2 1034 5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6
        GoalGfx 2F8FC 448 24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f
        SignpostGfx 2FABC 1500 2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67
        ResultsGfx 30098 1354 c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40
        TimeOverGfx 305E2 960 a1c0e5a3a7afcfac0fd1b18b277bcde2f561d2453fd65fa1e7256a269fcf9241
        GameOverGfx 309A2 1024 911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd
        TitleCardGfx 30DA2 940 8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8
        ShieldGfx 3114E 1152 43f5e17251643e11e8b40657c1b5382649040af22c19c9f144b7fa98b6805e8e
        InvincibleGfx 315CE 1152 486c2b711a084aad4b784fc4530b3f2ebf36e8c455091e145666268e19ea3d75
        WarpGfx 31A4E 992 1c71ded6bf1df0fd59db83dfd03541dcbc70335344bddf87b6f18f63fb0903fa
        Spring45Gfx 31E2E 780 3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a
        SpringGfx 3213A 546 19212b9e7b64237a58a6bfa64566338930fd82a71555dbfab61e9bc45e165831
        MonitorTimeGfx 3235C 1540 ee98713d147c62128c7af02e250a6bf733c49c2c86a90332ecc4d77be29ed05a
        ExplosionGfx 32960 1256 1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1
        RingGfx 32E48 244 530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2
        LivesIconsGfx 32F3C 384 0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a
        HudNumbersGfx 330BC 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
        HudGfx 334BC 300 d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb
        CheckpointGfx 335E8 212 e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf
        FlowerAnims 336BC 28 74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9
        FlowerSprites 336D8 90 56b0e5e6d876d8f8ed9f64e8b41782418d1b62e27f26e596540d349b80268d08
        FlowerGfx 33732 650 a41e835713166841799d952b4e11c09b7ef51f199a0f182f9b15ad89a6ead9e2
        TitleCardTextGfx 339BC 336 e11d085cfd1a252230299b304591e7e7b6c8a0b75c492b8e5b080a978539ca7b
        R6StageGfxTwoFrameTiles0 33B0C 128 6f1f1fe3362e62e4a2514ce5dd35e70c9253daa61fc03547f22ea17bd44edc0f
        R6StageGfxTwoFrameTiles1 33B8C 128 0c6d79d2e4a880107e8e68ce58d19c62d40d00ed2dd1197a2b6a579e179dd34e
        R6StageGfxThreeFrameTiles0 33C0C 256 c564693d37ddf0eda10754172004eb8d50601abf544d3e0b5e34f322884e6180
        R6StageGfxThreeFrameTiles1 33D0C 256 debdcdc379d846600e369d494e3ee58e72a90856cdd6e6e1f2bd013f5840a76f
        R6StageGfxThreeFrameTiles2 33E0C 256 0185347050a3d393d681aa6bf8f795b972cb2b62e1b3aa12fcd5c806ca25eb40
        R6StageGfxScriptTiles0 33F0C 128 096ce20840ce006f3c0788a33c280ba89084911e05c869634c3c6c6cda85c4f3
        R6StageGfxScriptTiles1 33F8C 128 cb4a68d3e1d1c8cc314a9520240d848af539553fc84b281669a10a9bc23b1bea
        R6StageGfxScriptTiles2 3400C 128 21e181ce3edacb73d3d83be499e6d2eb5dab2414811e2411640ac0f9bcaf74a9
        R6StageGfxScriptTiles3 3408C 128 6b2d3d83c5c08d78dea9c915333af862ca52ecbefac40eac0a675e97047f818e
        LaunchGfx 3410C 276 364dc73ec887d18b5d4a81c02f9afbba30c186f1950f068d519d13fb54070780
        FreezerGfx 34220 344 29812c959d8fde963e3afbbe05d70db73d018e4d5d8aed8838c998bb487fa04c
        IceBlockGfx 34378 894 9134c8c5f7dba69933a9e6bbcf208cf37814dfd0755bb4e24bba3133363f9a49
        StageCollisionAngles 346F6 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 347F6 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 357F6 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 367F6 900 4bf2afc3a35fa7f7eba1f5e5523a71db86c7c5bb8e6faaa549ca58058c28a735
        StageMapFg 36BC2 122 535feb347b8bbd28e09c54bcdde2a781301f84b8a593fa348a4b18e45c16adea
        StageMapBg 36C3C 38 32f4df3f9b9d53ebc220f68e6e6a5fd047750e05fe8ad3733eb9e44fddf7d0b5
        StageBlocks 36E5C 4656 23b776d75d0c6a62cdbf041244632ef79fdf211d20fe6c2bf0f7dde9573c585a
        StageGfx 3808C 9740 9dd34d47c38db29c102cfc080412f991f20d9073a0fc60d94e8ea10d4beae1e8
        PistonGfx 3A698 318 94113f5cecdcd4b52f736e2022be9dc51551e8e43474d244e5d037b727a8e71b
        PlatformGfxA 3A7D6 934 379bcc728c64a6f219d602ad7e29c76137b40313a80616d694542dad5d0d7a88
        PlatformGfxB 3AB7C 928 b965a0deec5b1e473f2072a1bb3907a5a4c4f64daf75e9a2756710499126047c
        PlatformGfxCD 3AF1C 928 748f78b5fa9ffc7ad11e3f5ec8afb0ef1cdf8d68d2aa95bffdd82302cdfe71ff
        BouncePlatformGfx 3B2BC 224 6512cef8c7321c47777e3a1de9012facf9ff9b50e8fc3105fdc1dad72e12fc56
        SwitchGfx 3B39C 82 a7c595cdd4d541ac97948c563f81f056b14b2d0c40c38f4dfe12d267942642e7
        DoorGfx 3B3EE 212 c9e2b3ea6ed1d8c544fb7467aa5b9fced375e14a2e5480e6c03c7e075211121a
        BossDoorGfx 3B4C2 200 1ebd976238994fb4d8a1461ce8bf3e500aff37d8d290d613f74fa4e92fe958f5
        CrusherGfxABD 3B58A 438 d369111fb17f9ed883fff8ef9dfc6c6df7e4853ac77b4fe4ca9c18901d5d5732
        CrusherGfxC 3B740 306 8a44acf34b3dbdaca620f4c4942797dd148a371077f113b84238c497703fc9fe
        TubeDoorGfx 3B872 544 b7919c4e6a28fff0cdf007da47e3550d09a983fdc16a146db24b64fb90b1eb6b
        ElectricOrbA 3BA92 288 c214606782e1a7d95f53b94ff56a47f4219f63a0d1c601e78ba8be698ccc7875
        ElectricOrbB 3BBB2 304 e5a1aa28c66af5cfd6607368a354cd13d99b5e4423b7c1233d92401477ec3b38
        ElectricOrbC 3BCE2 304 d549aba755ff58c5c1d8e9c1436df3e903ad24a0d4a39e4bb42557b6e1f072fd
        ElectricOrbD 3BE12 306 a3423e55d1ee6d7e6209ff3695041c3bd5c84171ec2d68b9c2551d8eb0141f85
        BataPyonGfx 3BF44 628 e032ba91a4f495b9e895323d15e3b7b495681cb35e989fe7f4063bf7f124ffc0
        PohBeeGfx 3C1B8 864 5b389a5ffe5a5d82ecd01948bf348821645833e9cd922d604d46b468dcdfeff6
        MinomusiGfx 3C518 706 fab4da66e6f96d59f095ed32f72ce5ce7611a0f61d2789d05ed477fafa831d0d
        SpikeBombGfx 3C7DA 194 fd5d956e1afc51b7788258dece2b7048585bfac9a87718a5d1bcb6f05dfffc70
        SnakeBlocksGfx 3C89C 224 032234138544a80a5d0beb11d92aed121425ec1e39a125e23d53be5f0e0ed563
        SemiGfx 3C97C 600 91e3d1065d34cd6f4860f66906ceb834884ae8eeb15836c32e1cfcdbc484a48a
        SeesawGfx 3CBD4 656 bdddf04f5a7996322dcd2e40ce07e1158886d13f2682dea8c53046ca37327115
        EggmanStatueGfx 3CE64 570 d75608b15c318928d0656280d8b45f3a29f4f492d3bba205899d8af3348fe661
        SpikeChainGfx 3D09E 216 a1c4617b975f6dea5b752ce4814503e30ddb8cde14786ea46d46b2b5eb72a226
        AnimalsGfx 3D176 500 94e5e59c22787910ceefa158989f186faac7b2d3999d5d776466d002db9d7dc7
        HologramAnimalsGfx 3D36A 494 38af7b7b14311f9cf0e42059bb007f219c4727d4d9b637714f9d52f0387e2d34
        RobotTransportGfxA 3D558 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        RobotTransportGfxB 3D6A4 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        HologramGfx 3D856 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        BossMachineGfx 3DC56 2812 8c8f31a88de30a5e73daf8152132ccaed401965047958793db9307e4a5c03aa9
        EggmanGfx 3E752 1254 2b6276cf6f752760e7f06e1c2400d5e3bbe2895bc4614d130441ee73bfe12777
        EggmanEscapeGfx 3EC38 1518 70a6d91caf68da8ce7d7608322a41bc909d43dc86bf82ed71e5df28686e71b15
        BossDestroyGfx 3F226 714 5946441b9f01a65a90ee607a3e3c22d5d92aaee0b2edf83d5057539966af632f
        BossFloorGfxD 3F4F0 468 65e5e53d949e0676971d1bcfdd6e25b5eae4dc765530cc482fbe2ab238e77b4c
        BossPiecesGfxD 3F6C4 324 78150111f395155e7e8b535a5b5ae47c1f192406e1cd1fa348404edc54bc072d
        Padding3 3F808 2040 ba7f0658f65bc8cc1a7c70ce6d4e25f38540d7320e90a16553b2404c9a15206d
        MoveSpringAnims 86BC 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 86C2 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        StatueBombSprites D5F2 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        BossSpikesAnim FC34 5 a63a2cb7482eff131ed8f035c257d0d2b933283f3a0dddf4df380357fba8f9d0
        DebrisAnims FD1A 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        CapsuleAnims 1F232 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 1F266 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        Include93 1F338 456 603e62572155373a892680106c64dcde7064eea891b3938d552c8399f18bfb6d
        Include97 1F8A4 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        Include98 1F970 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        Include99 1FA9A 192 71a11c6fbb522b31a36b2001b0546aa65ce13f938b2e70ab3289d03ccf996f9b
        Include100 1FB62 116 b2d0ad813d0ab2daf4f5885f50c589ff36063ca9e81cf06bdcabc76e7e1e8dd1
        Include101 1FBD6 316 56bc0b4d5d3da4fcfa39697ac25242f1546a51a920c24689785c3b469d8be5d6
        Include102 1FD12 130 21ffae658d9161527c5ae0c60bd09f3e3e4b709de78b798723699b0190858c91
        StageMapNull 36C62 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36C66 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36D2E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36D32 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36E54 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36E58 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        DataChunks 10000 60416 be85ff0f04d11b62578fb61a5e2478a862c03d21fef0e07b7727bc2aab31036b
        DataMap 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        DataDplc 2E8AC 1338 313ed65d39608dd95f97927a8e142f72974fa5adbf7285502db7961d55ec5eae
        DataArt 20000 57120 a7c15a954d293ee56d009c6a1e1c9cc1b6d5b7c2353f21c1cbd3a02666dca276
        DataAnimation 5B0E 582 1eb0199c5399069c3c00f1a4ba5f0e0839f7d78c5a45e4707446088c2e39ab78
        DataMini 5ABE 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        DataDispatch 3C1A 256 c4cd8992ee96f0d370cc14da6802e18136279b18fb3b667cd623b999752ddd87
        DataPaletteDirectory 670 56 05ba7100b3b9feba802b9b61cdef4ad55dedb871664828713c9c21bbd6022145
        DataLayoutDirectory 36B7A 72 06d92f62f96c965b2cff235c44dfe712ce848dbe42c546c25914c1b57426cbae
        DataStageData E5E4 16 ef95312caaf53f6a23326c13143c9a41f59994eafa788050979ebdaf04adcfd4
        PlayerMapRead 2DF20 2443 4a14351433145ea4a0dfbb8542b27da17ec216d324a65b64ba5c480f58360965
        PlacementRead 790C 584 4ff4271b493c99826b22aa564dbdf11883d8fa516ed4ccbb5b5ea923d4983f12
        PlcRead E5F4 260 4e65a1f42fb1b027d685f9880df48da9ae36ef88c72ad9cac5e4413e8f7f9c93
        PlcDirectory E5F4 42 935e13faca92084a7215c97e59b29e8ac45c5836644efa2a36a8c370b8d85c51
        PlcBody0 E61E 8 1472abbe23692be987e8b14a6c63a4ee9a8116bf8c555c93a2897663ce20ac6f
        PlcBody1 E626 92 2e3effc0f9f54ee466b17e3ceb1874ba4bc1a354101b1e4afbd3fc4904217008
        PlcBody2 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody3 E61E 8 1472abbe23692be987e8b14a6c63a4ee9a8116bf8c555c93a2897663ce20ac6f
        PlcBody4 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody5 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody6 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody7 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody8 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody9 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody10 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody11 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody12 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody13 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody14 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody15 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody16 E6C6 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 E682 68 97b1c769555fe3a815b454ff5df378c745545ee3ed9f2f4ad256dc25acbc50e5
        PlcBody18 E6CE 8 2f3cc65aa26fa21612b5762b61784d4808fe72e60957dcd1efbc2c8e83802900
        PlcBody19 E6D6 20 3078d72ac58e23e28fe0eba27f4f589668c2946a6104db26f42158f141cc60f5
        PlcBody20 E6EA 14 3be73a10a4b815c68be8816c84fdeadb876e517ba7e3cd9fb5771c2c31712320
        PaletteRead0 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead1 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead2 6A8 128 ee1028ceab339b471cd1cfcb5e2e506e086279d7c4ccd931b9da2977ac98fb81
        PaletteRead3 6A8 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6C8 96 92913bec27c270a76b29b121ef932157c3e4bf23adee2f3857dedd1a2a1dd6ed
        PaletteRead5 738 96 67777001952483d7e76e4b55c1828afd70db82a3baf8e2e02c158636a1298adb
        PaletteRead6 798 32 f4bd33166dece487a24e428a6907c1fa945a825de5dcd4e4f5ae8dfd4957e0f3
        LayoutRead0 36BC2 122 535feb347b8bbd28e09c54bcdde2a781301f84b8a593fa348a4b18e45c16adea
        LayoutRead1 36C3C 38 32f4df3f9b9d53ebc220f68e6e6a5fd047750e05fe8ad3733eb9e44fddf7d0b5
        LayoutRead2 36C62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36C66 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36D32 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36BC2 122 535feb347b8bbd28e09c54bcdde2a781301f84b8a593fa348a4b18e45c16adea
        LayoutRead13 36C3C 38 32f4df3f9b9d53ebc220f68e6e6a5fd047750e05fe8ad3733eb9e44fddf7d0b5
        LayoutRead14 36C62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36C66 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36D32 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36BC2 122 535feb347b8bbd28e09c54bcdde2a781301f84b8a593fa348a4b18e45c16adea
        LayoutRead25 36C3C 38 32f4df3f9b9d53ebc220f68e6e6a5fd047750e05fe8ad3733eb9e44fddf7d0b5
        LayoutRead26 36C62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36C66 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36D2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36D32 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36E54 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36E58 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        CycleScript1 212 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        CycleColors1 21A 6 de0b7e36c5abccb757e43f62a09469e5e08c848e2f728efcc86cb1b6bdc2d7c4
        CycleScript2 220 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        CycleColors2 228 6 dbccd11579c8608c7b701f70c3cee86ad5ab2a70cbf075bdd4c9eddca3bb478e
        CycleScript3 22E 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        CycleColors3 236 6 abe68fcfa20618add82b91db86ef4be932c11f6f120f94648b75a62a0940c895
        CycleScript4 23C 14 3973556f3daed1952ccc178f60f89eeb56849eb88ca4bf9b22225030823bbcdb
        CycleColors4 24A 8 e7c64d45a16325976c83a617e285a4ee0e7f8cdaa2028c512e6262d7dc934dba
        CycleBossColors 252 8 af5570f5a1810b7af78caf4bc70a660f0df51e42baf91d4de5b2328de0e83dfc
        R6StageDrawScrollLayerPointers 3042 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        R6StageDrawInitScrollLayerPointers 33AA 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        R6StageDrawScrollLayerTable 3378 50 7a12eb5d42ab162128f0f6bf20a42892bc38080a3fd4413b98b715428c267e48
        FiniteTable0 212 72 8baa3ec49a6d39417cb94fe7676b0b80660b459f22454a51148e0ee78557319b
        FiniteTable11 28F8 44 f99bee7f4957e703122e02b57276f5763bcd73e5a5cbce1b8e6aa934c666509d
        FiniteTable12 29BC 8 619ae91aae9c6e179d488f861a036133461b9e1907a641eb365b9b634d080cbd
        FiniteTable13 2AD2 10 6769e086cf31b478b72954fd27b00f2e7d375da962b744ada930ce3c62cbf2fd
        InitialBounds 28F8 12 cfec5298b11a781264ddad6df240bba70b55e77b5d71b42667e76b64da64d779
        """); }
    private static List<Operand> operandsR63D() { return operands("""
        2FDC 267b0064
        3066 267b00da
        33C2 267b00e6
        CE4A 43fa0178
        CE92 43fa0130
        CF82 43fa0082
        9EB0 43f90021f0ee
        9DB2 43f90020a25c
        EF1C 43f90020fc5a
        F00C 43f90020fc90
        F042 43f90020fc90
        6122 43f90021f0a6
        E712 43f90020fa16
        6238 43f90021ef00
        632C 43f9002336bc
        B050 43f90020b374
        24CC 43f90020e5f4
        24FE 43f90020e5f4
        263A 43f90020e5f4
        64AA 43f90021eeb6
        E2CA 43fa000e
        9900 43f9002330bc
        9962 43f9002330bc
        99DE 43f9002330bc
        9A4E 43f9002330bc
        9AF4 43f9002330bc
        1B6A 43f900232f3c
        8E66 43f900209258
        8FE6 43f900209258
        581E 43f900205b0e
        5D9A 43f900220000
        5D6A 45f90022e8ac
        5930 43f900205cea
        5A0A 43f900205ba6
        59FC 43f900205c7e
        59A8 43f900205d18
        59B6 43f900205b96
        598C 43f900205b9e
        597E 43f900205c6c
        5922 43f900205d10
        593C 43f900205b8e
        5A8A 43f900205c66
        5948 43f900205b86
        5A96 43f900205c60
        6192 43f90021ec00
        65FE 43f90021ec00
        66A2 43f90021ec00
        894E 43f900208b46
        8B34 43f900208b46
        DCFC 43fa00aa
        D746 43fa0116
        A13A 43f90021f1a6
        CAB4 43fa0274
        654E 43f90021ee3a
        8390 43f9002085e6
        8438 43f9002085e6
        84A2 43f9002085e6
        8560 43f9002085e6
        118A 45f9002346f6
        124A 45f9002346f6
        12FA 45f9002346f6
        13AA 45f9002346f6
        11B8 45f9002347f6
        1278 45f9002347f6
        1416 43f9002347f6
        141C 45f9002347f6
        1450 43f9002347f6
        145E 43f9002347f6
        1464 45f9002347f6
        1328 45f9002357f6
        13D8 45f9002357f6
        1456 45f9002357f6
        1662 45f90020e5e4
        33F4 45f90020e5e4
        7668 41f900207900
        D5C6 43fa0024
        A6FA 45f90020ad70
        63D8 43f90021eee0
        100 4ef90020011e
        10C 4ef9002020c2
        112 4ef900201c38
        3808 43f900203c1a227100fc4e91
        """); }
    private static List<Dispatch> dispatchR63D() { return dispatch("""
        1 PlayerObject 203D88 SOURCE_HANDLER
        2 PlayerObject 203D88 SOURCE_HANDLER
        3 PowerupObject 206564 SOURCE_HANDLER
        4 NullObject 203D1A NATIVE_NULL
        5 NullObject 203D1A NATIVE_NULL
        6 TestObject 20612E SOURCE_HANDLER
        7 TunnelPathObject 20B67C SOURCE_HANDLER
        8 NullObject 203D1A NATIVE_NULL
        9 NullObject 203D1A NATIVE_NULL
        10 SpringObject 208202 SOURCE_HANDLER
        11 HDoorSplashObject 2064FE SOURCE_HANDLER
        12 HDoorSplashSetObject 2064C6 SOURCE_HANDLER
        13 HDoorObject 2063EE SOURCE_HANDLER
        14 TunnelSplashObject 206398 SOURCE_HANDLER
        15 MoveSpringObject 2080B6 SOURCE_HANDLER
        16 RingObject 2086D2 SOURCE_HANDLER
        17 LostRingObject 2089B6 SOURCE_HANDLER
        18 NullObject 203D1A NATIVE_NULL
        19 NullObject 203D1A NATIVE_NULL
        20 BigRingObject 209DC4 SOURCE_HANDLER
        21 CapsuleObject 209B46 SOURCE_HANDLER
        22 GoalObject 209EFC SOURCE_HANDLER
        23 SignpostObject 20A080 SOURCE_HANDLER
        24 ExplosionObject 2061D4 SOURCE_HANDLER
        25 MonitorTimeObject 208EF4 SOURCE_HANDLER
        26 MonitorItemObject 209070 SOURCE_HANDLER
        27 BoulderObject 20803E SOURCE_HANDLER
        28 HudPointsObject 209548 SOURCE_HANDLER
        29 NullObject 203D1A NATIVE_NULL
        30 RollTunnelObject 206740 SOURCE_HANDLER
        31 FlowerObject 206262 SOURCE_HANDLER
        32 PistonObject 20BD2A SOURCE_HANDLER
        33 ElectricBeamsObject 20BEB0 SOURCE_HANDLER
        34 PlatformObject 20C19C SOURCE_HANDLER
        35 BouncePlatformObject 20C2B0 SOURCE_HANDLER
        36 SwitchObject 20C488 SOURCE_HANDLER
        37 DoorObject 20C564 SOURCE_HANDLER
        38 CrusherObject 20C6FA SOURCE_HANDLER
        39 TubeDoorObject 20C8B0 SOURCE_HANDLER
        40 SpinPlatformObject 20CA4A SOURCE_HANDLER
        41 AnimalObject 20CDA0 SOURCE_HANDLER
        42 SnakeBlocksObject 20D090 SOURCE_HANDLER
        43 EggmanStatueObject 20D386 SOURCE_HANDLER
        44 SeesawObject 20D602 SOURCE_HANDLER
        45 SpikeChainObject 20D930 SOURCE_HANDLER
        46 RobotGeneratorObject 20DBDC SOURCE_HANDLER
        47 ProjectorObject 20E044 SOURCE_HANDLER
        48 NullObject 203D1A NATIVE_NULL
        49 NullObject 203D1A NATIVE_NULL
        50 NullObject 203D1A NATIVE_NULL
        51 NullObject 203D1A NATIVE_NULL
        52 EggmanObject 20E6F8 SOURCE_HANDLER
        53 BossMachineObject 20EDBA SOURCE_HANDLER
        54 BossSpikesObject 20EE3E SOURCE_HANDLER
        55 BossExhaustObject 20EF02 SOURCE_HANDLER
        56 FallSpikeObject 20F05A SOURCE_HANDLER
        57 FloorDebrisObject 20F102 SOURCE_HANDLER
        58 ResultsObject 20A82A SOURCE_HANDLER
        59 GameOverObject 20A588 SOURCE_HANDLER
        60 TitleCardObject 20A6A4 SOURCE_HANDLER
        61 FloorPieceObject 20F1F0 SOURCE_HANDLER
        62 ConductorObject 20F448 SOURCE_HANDLER
        63 BossSmokeObject 20EF88 SOURCE_HANDLER
        64 ElectricityObject 20F17C SOURCE_HANDLER
        """); }
    private static List<LayoutSlot> layoutsR63D() { return layouts("""
        0 72 15 8 122
        1 194 12 3 38
        2 232 1 1 4
        3 236 33 6 200
        4 730 1 1 4
        5 436 1 1 4
        6 440 48 6 290
        7 730 1 1 4
        8 730 1 1 4
        9 734 1 1 4
        10 734 1 1 4
        11 734 1 1 4
        12 72 15 8 122
        13 194 12 3 38
        14 232 1 1 4
        15 236 33 6 200
        16 730 1 1 4
        17 436 1 1 4
        18 440 48 6 290
        19 730 1 1 4
        20 730 1 1 4
        21 734 1 1 4
        22 734 1 1 4
        23 734 1 1 4
        24 72 15 8 122
        25 194 12 3 38
        26 232 1 1 4
        27 236 33 6 200
        28 730 1 1 4
        29 436 1 1 4
        30 440 48 6 290
        31 730 1 1 4
        32 730 1 1 4
        33 734 1 1 4
        34 734 1 1 4
        35 734 1 1 4
        """); }
}
