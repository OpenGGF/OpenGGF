package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.*;

/** Original USA Stardust Speedway DATA, independently authenticated at native
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, FixBugs=0.
 * Canonical/read extents, source graphs and consumer proofs are in the external R7 packet.
 * Source-selection constraints never initialize the live retained good-future byte.
 * RunLevel (src/CD Initial Program/IPX.asm:559-572),
 * InitStage (src/common/main.asm:15-25).
 * No loader, reader, decoder, live invocation or terrain/player admission.
 */
final class CdR7DataLayout {
    private CdR7DataLayout() { }
    record Range(String key,int fileOffset,int length,String sha256) { }
    record Operand(int fileOffset,String hex) { }
    record Terrain(int chunks,int blockBytes,int patternBytes,int collisionBytes,
                   int foregroundColumns,int foregroundRows,int backgroundColumns,int backgroundRows) { }
    record LayoutSlot(int slot,int signedOffset,int columns,int rows) { }
    record Dispatch(int id,String handler,int address,boolean nativeNull) { }
    record PaletteRecord(int slot,long sourceAddress,int destination,int counter,long declaredReadBytes,
                         boolean unmapped) { }
    enum Programme {
        R71A(7352,"9e7ae09739eb2d5e502414dcdef131203984cec1c28c6af48f73e196ba18c10b",1280,1,OptionalInt.empty(),new Terrain(117,7392,23520,1024,48,6,18,3),rangesR71A(),operandsR71A(),dispatchR71A(),layoutsR71A(),palettesR71A(),640,477),
        R71B(7480,"a0cc3220cf3c18f64b6f98e1b9e132c42ea3cdc0174f61983b712ee2b7a06bc1",1280,0,OptionalInt.empty(),new Terrain(114,5824,24064,900,48,6,24,3),rangesR71B(),operandsR71B(),dispatchR71B(),layoutsR71B(),palettesR71B(),640,415),
        R71C(7608,"c182036bfdf07b9fc273c8dc61f1b1d75c51e4b8268328e19d6d7415a3995681",1280,2,OptionalInt.of(1),new Terrain(113,5440,24128,800,48,6,2,3),rangesR71C(),operandsR71C(),dispatchR71C(),layoutsR71C(),palettesR71C(),640,470),
        R71D(7736,"267405649fec9e7195d13bc31597064d6737cc1941a8bdbe60916c17fc1b3a84",1280,2,OptionalInt.of(0),new Terrain(107,7744,24128,1024,48,6,18,3),rangesR71D(),operandsR71D(),dispatchR71D(),layoutsR71D(),palettesR71D(),640,470),
        R72A(7864,"e783cf8ec6e7a6ec4d571bf1f71b45ec19494ccaa77db2a1b129737d8316e6c5",1281,1,OptionalInt.empty(),new Terrain(117,7392,23520,1024,48,8,18,3),rangesR72A(),operandsR72A(),dispatchR72A(),layoutsR72A(),palettesR72A(),822,605),
        R72B(7992,"5b15ac9a2aa4916d29dfffde99aed94a335564256ff6e262bc186a451880621e",1281,0,OptionalInt.empty(),new Terrain(114,5824,24064,900,48,8,24,3),rangesR72B(),operandsR72B(),dispatchR72B(),layoutsR72B(),palettesR72B(),822,558),
        R72C(8120,"2543266e61b167a0470b01ce27168e119b82909be1bcc8ac5a09343f5f189c58",1281,2,OptionalInt.of(1),new Terrain(113,5472,24128,800,48,8,2,3),rangesR72C(),operandsR72C(),dispatchR72C(),layoutsR72C(),palettesR72C(),822,579),
        R72D(8248,"944286ebff9cec3faaa6b51853cbc0cdbbf63d1aa853dbb21f8c5bc498fe64e8",1281,2,OptionalInt.of(0),new Terrain(107,7744,24096,1024,48,8,24,3),rangesR72D(),operandsR72D(),dispatchR72D(),layoutsR72D(),palettesR72D(),822,579),
        R73C(8376,"9fd5bf33710a1752caaf8559806c2cab99a5e618529cabf7d2e861f234e985a3",1282,2,OptionalInt.of(1),new Terrain(113,5440,24128,800,64,3,2,3),rangesR73C(),operandsR73C(),dispatchR73C(),layoutsR73C(),palettesR73C(),79,78),
        R73D(8504,"4fd7b5df2574b79a416b18e195c25dd76990ede91ac96d1ce4bd0c880ee0d23f",1282,2,OptionalInt.of(0),new Terrain(107,7744,24096,1024,64,3,60,3),rangesR73D(),operandsR73D(),dispatchR73D(),layoutsR73D(),palettesR73D(),79,78);
        private final int lba,zoneAct,timeZone,placements,active;
        private final OptionalInt goodFutureSelectionConstraint;
        private final String sha;
        private final Terrain terrain;
        private final List<Range> ranges;
        private final List<Operand> operands;
        private final List<Dispatch> dispatch;
        private final List<LayoutSlot> layouts;
        private final List<PaletteRecord> palettes;
        Programme(int lba,String sha,int zoneAct,int timeZone,OptionalInt goodFutureSelectionConstraint,Terrain terrain,
                  List<Range> ranges,List<Operand> operands,List<Dispatch> dispatch,List<LayoutSlot> layouts,
                  List<PaletteRecord> palettes,int placements,int active) {
            this.lba=lba;this.sha=sha;this.zoneAct=zoneAct;this.timeZone=timeZone;this.goodFutureSelectionConstraint=Objects.requireNonNull(goodFutureSelectionConstraint);
            this.terrain=terrain;this.ranges=List.copyOf(ranges);this.operands=List.copyOf(operands);
            this.dispatch=List.copyOf(dispatch);this.layouts=List.copyOf(layouts);this.palettes=List.copyOf(palettes);
            this.placements=placements;this.active=active;
        }
        CdNativeFile file() { return CdNativeFile.valueOf(name()); }
        CdStagePayload payload() { return CdStagePayload.valueOf(name()); }
        CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
        CdSonicVisualAssets.SourceFormat sonicFormat() { return CdSonicVisualAssets.SourceFormat.FRAMES_167; }
        int fileBytes() { return 262144; }
        int isoLba() { return lba; }
        String fileSha256() { return sha; }
        int rawZoneActWord() { return zoneAct; }
        int timeZoneByte() { return timeZone; }
        OptionalInt goodFutureSelectionConstraint() { return goodFutureSelectionConstraint; }
        int placementCount() { return placements; }
        int periodActiveCount() { return active; }
        Terrain terrain() { return terrain; }
        List<Range> ranges() { return ranges; }
        List<Operand> operands() { return operands; }
        List<Dispatch> dispatch() { return dispatch; }
        List<LayoutSlot> layouts() { return layouts; }
        List<PaletteRecord> palettes() { return palettes; }
        Range range(String key) {
            return ranges.stream().filter(r->r.key().equals(key)).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unowned R7 declaration: "+key));
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
            return new Dispatch(Integer.parseInt(w[0]),w[1],Integer.parseInt(w[2],16),Boolean.parseBoolean(w[3]));}).toList();
    }
    private static List<LayoutSlot> layouts(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new LayoutSlot(Integer.parseInt(w[0]),Integer.parseInt(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]));}).toList();
    }
    private static List<PaletteRecord> palettes(String rows) {
        return rows.strip().lines().map(line->{var w=line.strip().split(" ");
            return new PaletteRecord(Integer.parseInt(w[0]),Long.parseLong(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]),Long.parseLong(w[4]),Boolean.parseBoolean(w[5]));}).toList();
    }
    private static List<Range> rangesR71A() { return ranges("""
        PlayerAnims 56B6 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 65F8 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 65FE 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 924C 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9720 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 973C 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 978A 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9798 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 97E8 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9860 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9866 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 9CEA 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 9CF2 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 9D36 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 9DD8 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A3FC 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites A484 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites A69A 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites A79C 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B400 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B40C 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx B5AC 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites B778 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims B8BA 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites B8C8 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C384 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C390 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims C426 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites C42E 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites C528 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C9BE 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites CC6C 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D000 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D128 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D16C 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D1B0 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D2A4 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims D650 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites DB16 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E190 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E1C2 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E1F4 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E240 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E28C 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E294 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E322 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites E46A 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites E636 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites E66A 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E90C 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E94E 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E98A 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 E99E 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims EC54 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites EC98 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims EECE 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites EED6 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F3FC 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 F426 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites F456 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites F5C2 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites F7DE 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 59904 14e7e09831b429d264072950f61b57633562142513cc94ea0c858a6858d64c86
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 1024 0f6f13d9cc185a1c898b53f3354f850825f7b0d103b5f3dd988b3881c0071626
        StageMapFg 368EC 290 6057c0cb3a1989882a3d3042c43faa9c4a994aff9f2cd3bada351a79c2c2e6de
        StageMapBg 36A0E 56 2b03daf681eb9e63b2fe22dee8a2323062d158de18a77be8e3fa6dd86d5ee54f
        StageMapNull 36A46 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B12 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36C38 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36C3C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36C40 5560 2c066e4b0efd8713786c377ce191f9e3617d5264071c7b74ba6bdbae96731fd3
        StageGfx 381F8 12666 7685296e6ed458f8a70d74dc0bab2dd738ea59dab98f4ef58281493e56dbefa6
        PowerupAnims 3B372 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3B49A 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B5AC 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B5C4 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B628 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B636 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B652 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B65A 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B672 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B686 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B718 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B818 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B81E 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B860 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B868 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B918 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B926 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3B9A4 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B9D8 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3BAAA 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3BBF4 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3BCC6 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3BD98 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3BE6A 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3C01A 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3C1C6 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C5C6 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C778 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C8C4 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CF60 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3D006 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3D120 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3D24C 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3D378 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3D488 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D548 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3DA22 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        DangoGfx 3DBF6 978 c7b387e9d6fa890cbf5f74f947c4cbdf3384510a89c9f307915484cbd82d5813
        StageData F87E 16 322b25c995a4c0a2fb1398eac2ed20831bd32337afbb4e39129760225cd0bf6c
        LayoutDirectory 368A4 72 1d6b95de11f25195fee5e772232a07c7a8c8461d03882cd2c38cd554e56c03c5
        LayoutRead0 368EC 290 6057c0cb3a1989882a3d3042c43faa9c4a994aff9f2cd3bada351a79c2c2e6de
        LayoutRead1 36A0E 56 2b03daf681eb9e63b2fe22dee8a2323062d158de18a77be8e3fa6dd86d5ee54f
        LayoutRead2 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 368EC 290 6057c0cb3a1989882a3d3042c43faa9c4a994aff9f2cd3bada351a79c2c2e6de
        LayoutRead13 36A0E 56 2b03daf681eb9e63b2fe22dee8a2323062d158de18a77be8e3fa6dd86d5ee54f
        LayoutRead14 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 368EC 290 6057c0cb3a1989882a3d3042c43faa9c4a994aff9f2cd3bada351a79c2c2e6de
        LayoutRead25 36A0E 56 2b03daf681eb9e63b2fe22dee8a2323062d158de18a77be8e3fa6dd86d5ee54f
        LayoutRead26 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 78E4 5146 b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611
        PlacementRead 78F0 5128 3392ebca8cb4519c9ba9543665d4da496d411d022051cd40997b27849c21eddc
        Dispatch 3B60 240 636a1d0588532dc3866380417e4a0120137c629812c6a829b773b6111a4b8ef7
        PaletteDirectory 4F4 48 10a12e831a582096077b8d3f1f97d2fbe4432d1f55bc5fc5b236ce2cb898a432
        PaletteSource 4F4 400 1a8036d037661be38eca3627e78e078a996f0d380f741447a5569da0ed9df5eb
        PaletteRead0 524 128 bfca2608f0d989add5a8d6d297d8ca96eff74fe9422be98dce9443ba5992b96d
        PaletteRead1 524 128 bfca2608f0d989add5a8d6d297d8ca96eff74fe9422be98dce9443ba5992b96d
        PaletteRead2 5A4 128 dab83c11eeadac120bab4f8e8f96d990ed11b15b9a1fb8cbbafff77d6d61a5eb
        PaletteRead3 5A4 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 5C4 96 abea771d16373a65015157e1c313cb633ddf513d299f26aaa32ed9e00bbfd7ed
        PaletteRead5 624 96 abea771d16373a65015157e1c313cb633ddf513d299f26aaa32ed9e00bbfd7ed
        PlcDirectory F88E 38 8ee7d8930b1d2bcb382f0cbd82755b512d5f369a2a6f689b5a218595785144f2
        PlcRead F88E 324 c97eb2ea7a65b12c285b79bed613268140cc81c9e0a3803c4b85212ef20d8a16
        PlcBody0 F8B4 14 6199f2c688406ad309100ddf607b3f9aff317e38bf8280d8ee98fe9255e80e43
        PlcBody1 F8C2 80 30e70c80a7140fed66d16a46ab0b443b8871cb79bf717507b7fb6058c7874de1
        PlcBody2 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody3 F8B4 14 6199f2c688406ad309100ddf607b3f9aff317e38bf8280d8ee98fe9255e80e43
        PlcBody4 F95C 74 908b56fe5e37c8c5470d8b084fd1828f7076ae7c86c45049319ca7bef301a125
        PlcBody5 F9A6 8 917bd4f1ed9ec0169e9e873d0ea049178ba3c2702df740d37672a027cf063f90
        PlcBody6 F9AE 8 a20312efcdd363cd430cca137696462123308b038132206ee736f98c8a49748a
        PlcBody7 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody8 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody9 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody10 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody11 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody12 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody13 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody14 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody15 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody16 F9B6 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 F912 74 6ca80c2b75899bb2119753a4a8e73635fc5f48a61d641c9dfcd94ddcfe466446
        PlcBody18 F9BE 20 56f4aed7ca7453a487f62be6b177faf102a3741cc40814dfa8a22ca2bba73b14
        Scalar0 202 6 0b437cbd4ff5e8672f6d85020ed1f831c41c89329f08e3de4ed9913dd47a26da
        Scalar1 208 8 6e0e2eb4b0e875cefda2a861d719df9f0c8345dc0fd5e3922ce4aa5678695c4c
        Scalar2 210 6 0707d43204118460a93df02ee289404161d63b14ed85e14f5cabc657a4e72431
        Scalar3 216 8 35673c11da5755c9a2a353b9c23df8d22b73770d7a7305955f7c99f489edef10
        Scalar4 21E 6 6c17a3d4fa1418da7f9de12db0892f91b7c3db00a1d34e521f0c1c9ef9cd297d
        Scalar5 224 8 29e2a8efbafc9d649de9ab87912539e692c7d2176f459e8a3f8ce77072cd118a
        Scalar10 6E2 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar11 9C8 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar13 19F8 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar14 20B6 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar15 27B2 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar16 2830 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar17 2834 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar21 32A0 66 3aabe566457975b1f8dcd34838d66833bea2e0316142b40e20b2cf172af77804
        Scalar23 442C 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar24 5666 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar30 65F4 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar31 6D9E 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar32 6FBC 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar35 988E 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar37 AADA 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar38 AC32 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar39 AC36 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar41 AC3E 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar42 AC42 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar43 AC46 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar44 AC4A 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar47 AC46 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar48 AE22 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar52 B3E0 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar53 BBC0 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar54 BBD6 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar55 BBE0 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar56 BBEA 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar57 BBF4 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar58 BC18 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar59 BC3C 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar60 BE4C 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar61 BE56 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar62 BE60 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar63 BE6A 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar64 BE74 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar66 BE88 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar67 BE92 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar69 BEA6 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar70 BEB0 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar71 BEBA 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar72 BFDC 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar73 C000 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar74 C196 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar75 C1AA 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar77 C1BE 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar78 C9A6 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar79 CC2C 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar80 CDD6 4 a52acdafbab717bb1497bb006be9e9b6247ce3a367f30074d7459a3e8b03963c
        Scalar81 CDDA 4 bd0b879367e7d54a90cae5da9c0bc0de1c4b11f7065196e3c1299f2e0e1d1685
        Scalar82 CDDE 4 572f6a289a69da0087f95cd97bef2376e9c6b575361d95e4ef6c7adbb1902d2b
        Scalar83 CE5E 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar84 D38E 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar85 E2AC 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar86 E316 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar87 E478 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar88 E4A8 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar89 E9C2 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar90 ECDC 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar91 F114 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar92 F154 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar93 F6BC 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar94 F756 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar96 F7A2 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5666 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 2F6A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 32E2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        Sections CDD6 12 476cea4a74da9e0aabdc86693246a71cc1404b8fad22472c9519847efc715dbc
        """); }
    private static List<Operand> operandsR71A() { return operands("""
        53C6 43f9002056b6
        65E8 43f9002065f8
        6582 217c002065fe
        9210 217c0020924c
        9534 43f90020978a
        95DC 43f90020978a
        9646 43f90020978a
        9704 43f90020978a
        BCBA 43f90020978a
        936A 217c00209798
        9434 217c00209798
        949E 217c002097e8
        9346 43f900209860
        929E 217c00209866
        9AF2 43f900209cea
        9CD8 43f900209cea
        99C2 237c00209cf2
        9BB6 237c00209cf2
        A00A 43f90020a3fc
        A18A 43f90020a3fc
        9EB2 217c0020a484
        9F2A 217c0020a484
        A0DE 217c0020a484
        A264 227c0020a484
        A66E 217c0020a69a
        A70A 217c0020a79c
        AF56 43f90020b400
        AF4E 217c0020b40c
        B756 217c0020b778
        B882 43f90020b8ba
        B7E0 217c0020b8c8
        BB16 43f90020c384
        BA1A 217c0020c390
        BABA 237c0020c390
        C210 217c0020c390
        C414 43f90020c426
        C40C 217c0020c42e
        C47C 217c0020c528
        C792 237c0020c9be
        C5A4 217c0020cc6c
        C5D4 237c0020cc6c
        CEAE 217c0020d000
        D06A 45fa00bc
        D082 45fa00e8
        D066 43fa0148
        D07E 43fa0224
        D3C6 43fa0288
        D63A 43fa0014
        D4CE 237c0020db16
        DDC6 45fa03c8
        DDD6 45fa03ea
        DDC2 43fa0430
        DDD2 43fa046c
        E158 43fa0132
        E150 217c0020e294
        E2D8 217c0020e322
        E3D2 217c0020e46a
        E4D2 217c0020e636
        E580 217c0020e66a
        E790 43fa017a
        E7D8 43fa0132
        E8CA 43fa0082
        E72A 217c0020e98a
        E832 217c0020e99e
        EC44 43fa000e
        EA62 217c0020ec98
        EC1A 217c0020ec98
        EE22 43fa00aa
        ED6C 217c0020eed6
        F1E8 45fa0212
        F1F6 45fa022e
        F1E4 43fa0270
        F57A 217c0020f5c2
        F5F8 217c0020f7de
        E58 223c00210000
        3E32 223c00210000
        593E 43f900220000
        3D82 217c0022df20
        71A4 217c0022df20
        590E 45f90022e8ac
        1A36 43f900232f3c
        AAA4 43f9002330bc
        AB06 43f9002330bc
        AB82 43f9002330bc
        ABF2 43f9002330bc
        AC98 43f9002330bc
        5EDA 43f9002336bc
        5E52 217c002336d8
        1056 45f9002343a4
        1116 45f9002343a4
        11C6 45f9002343a4
        1276 45f9002343a4
        1084 45f9002344a4
        1144 45f9002344a4
        12E2 43f9002344a4
        12E8 45f9002344a4
        131C 43f9002344a4
        132A 43f9002344a4
        1330 45f9002344a4
        11F4 45f9002354a4
        12A4 45f9002354a4
        1322 45f9002354a4
        5D40 43f90023b372
        61AC 43f90023b372
        6250 43f90023b372
        5D08 217c0023b49a
        612C 217c0023b49a
        6324 217c0023b49a
        60FC 43f90023b5ac
        60D0 217c0023b5c4
        6058 43f90023b628
        6012 217c0023b636
        5F86 43f90023b652
        5F64 217c0023b65a
        5DE6 43f90023b672
        5DB8 217c0023b686
        5CD0 43f90023b818
        5B90 217c0023b81e
        5BEC 237c0023b81e
        B054 43f90023b860
        AFD0 217c0023b868
        B2DE 43f90023b918
        B10E 217c0023b926
        B27A 217c0023b926
        AD52 43f90023b9a4
        AEAC 43f90023b9a4
        AD32 217c0023b9d8
        AE6E 237c0023b9d8
        374A d040d04043f900203b60227100fc
        18C 43f900200202
        192 45f900200208
        19C 43f900200210
        1A2 45f900200216
        1AC 43f90020021e
        1B2 45f900200224
        6DC 303b0004
        990 103b4036
        19EE 103b1008
        2046 45f9002020b6
        2770 41f9002027b2
        27DE 43f900202830
        2824 43f900202834
        2ED4 41f9002032a0
        327A 41f9002032a0
        43EC 45f90020442c
        5660 103b0004
        65DA 303b0018
        6D42 303b005a
        6C28 45f900206fbc
        990C 43f90020988e
        AA7E 45fa005a
        AB78 45f90020ac32
        AAFC 45f90020ac36
        A8D2 45f90020ac3e
        AB6E 45f90020ac3e
        ABE8 45f90020ac42
        AC8E 45f90020ac42
        AC7A 45f90020ac46
        AC84 45f90020ac46
        AC5C 45f90020ac4a
        ADD0 45fb0050
        B3CE 43f90020b3e0
        BB82 103b003c
        BB32 45f90020bbd6
        BB44 45f90020bbe0
        BB58 45f90020bbea
        BB38 47f90020bbf4
        BB4A 47f90020bc18
        BB5E 47f90020bc3c
        BD06 45f90020be4c
        BD3E 45f90020be4c
        BD0C 47f90020be56
        BD44 47f90020be60
        BD1E 47f90020be6a
        BD56 47f90020be74
        BD4A 49f90020be88
        BD24 49f90020be92
        BD30 49f90020bea6
        BD68 49f90020beb0
        BD96 49f90020beba
        BFA8 47f90020bfdc
        BFBA 47f90020c000
        C0FC 47f90020c196
        BD36 4bf90020c1aa
        BF80 43f90020c1be
        C736 45f90020c9a6
        C5B6 45f90020cc2c
        CD84 43fa0050
        CDA8 43fa002c
        CDCC 303b100c
        CD9E 303b103e
        CF42 43faff1a
        D106 43fa0286
        DC14 43f90020e2ac
        E2EC 43fb0028
        E3F4 43fa0082
        E3E0 43fa00c6
        E8EE 43fa00d2
        EA6A 217c0020ecdc
        ED74 217c0020f114
        ED50 43fa0402
        F6AE 45fa000c
        F748 45fa000c
        F794 45fa000c
        2F04 267b0064
        2F8E 267b00da
        32FA 267b00e6
        100 4ef90020011e
        10C 4ef900201f8e
        112 4ef900201b04
        """); }
    private static List<Dispatch> dispatchR71A() { return dispatch("""
        1 PlayerObject 203CBE false
        2 PlayerObject 203CBE false
        3 PowerupObject 206112 false
        4 MeshObject 20B72C false
        5 NullObject 203C50 true
        6 TestObject 205CDC false
        7 NullObject 203C50 true
        8 BoosterObject 20C1DE false
        9 SparksObject 20C3E2 false
        10 SpringObject 2093A6 false
        11 HDoorSplashObject 2060AC false
        12 HDoorSplashSetObject 206074 false
        13 HDoorObject 205F9C false
        14 TunnelSplashObject 205F46 false
        15 MoveSpringObject 20925A false
        16 RingObject 209876 false
        17 LostRingObject 209B5A false
        18 NullObject 203C50 true
        19 CheckpointObject 205B6A false
        20 BigRingObject 20AF68 false
        21 CapsuleObject 20ACEA false
        22 GoalObject 20B0A0 false
        23 SignpostObject 20B224 false
        24 ExplosionObject 205D82 false
        25 MonitorTimeObject 20A098 false
        26 MonitorItemObject 20A214 false
        27 BoulderObject 2091E2 false
        28 HudPointsObject 20A6EC false
        29 PathSwapObject 20B790 false
        30 SpringLauncherObject 20B972 false
        31 FlowerObject 205E10 false
        32 SwingObject 20CDEA false
        33 KanabunObject 20D01E false
        34 KabasiraObject 20D392 false
        35 HotaruObject 20DBC2 false
        36 BlockObject 20E2BC false
        37 SpikeBallObject 20E380 false
        38 JumpSpikeBallObject 20E4AE false
        39 ProjectorObject 20E9D8 false
        40 AnimalObject 20E6F2 false
        41 RobotGeneratorObject 20ED02 false
        42 DangoObject 20F16A false
        43 RollTunnelObject 2062EE false
        44 NullObject 203C50 true
        45 NullObject 203C50 true
        46 NullObject 203C50 true
        47 NullObject 203C50 true
        48 NullObject 203C50 true
        49 NullObject 203C50 true
        50 NullObject 203C50 true
        51 NullObject 203C50 true
        52 NullObject 203C50 true
        53 SwapPlatformObject 20F558 false
        54 NullObject 203C50 true
        55 NullObject 203C50 true
        56 SearchlightObject 20F5D2 false
        57 NullObject 203C50 true
        58 ResultsObject 20C6E6 false
        59 GameOverObject 20C444 false
        60 TitleCardObject 20C560 false
        """); }
    private static List<LayoutSlot> layoutsR71A() { return layouts("""
        0 72 48 6
        1 362 18 3
        2 418 1 1
        3 422 33 6
        4 916 1 1
        5 622 1 1
        6 626 48 6
        7 916 1 1
        8 916 1 1
        9 920 1 1
        10 920 1 1
        11 920 1 1
        12 72 48 6
        13 362 18 3
        14 418 1 1
        15 422 33 6
        16 916 1 1
        17 622 1 1
        18 626 48 6
        19 916 1 1
        20 916 1 1
        21 920 1 1
        22 920 1 1
        23 920 1 1
        24 72 48 6
        25 362 18 3
        26 418 1 1
        27 422 33 6
        28 916 1 1
        29 622 1 1
        30 626 48 6
        31 916 1 1
        32 916 1 1
        33 920 1 1
        34 920 1 1
        35 920 1 1
        """); }
    private static List<PaletteRecord> palettesR71A() { return palettes("""
        0 2098468 64256 31 128 false
        1 2098468 64256 31 128 false
        2 2098596 64256 31 128 false
        3 2098596 64256 7 32 false
        4 2098628 64288 23 96 false
        5 2098724 64288 23 96 false
        """); }
    private static List<Range> rangesR71B() { return ranges("""
        PlayerAnims 56EC 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 662E 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 6634 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 9282 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9756 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9772 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 97C0 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 97CE 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 981E 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9896 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 989C 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 9D20 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 9D28 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 9D6C 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 9E0E 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A432 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites A4BA 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites A6D0 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites A7D2 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B436 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B442 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx B5E2 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites B7AE 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims B8F0 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites B8FE 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C3BA 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C3C6 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims C45C 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites C464 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites C55E 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites C9F4 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites CCA2 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D036 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D15E 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D1A2 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D1E6 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D2DA 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims D686 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites DB4C 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E1C6 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E1F8 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E22A 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E276 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E2C2 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E2CA 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E358 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites E4A0 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites E66C 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites E6A0 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E942 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E984 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9C0 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 E9D4 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims EC8A 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites ECCE 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims EF04 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites EF0C 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F432 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 F45C 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites F48C 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites F5F8 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites F814 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 58368 f64a70afb794c08d10bf52a895903d0d7c3ca6076807dd01fc216f1bbb6f55a9
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 900 1c7576565a12f62cd681c00479d28dbf62999508e10eae3901636c7303384c86
        StageMapFg 36870 290 aa2251d5f6a1710e3393db37f0906870794b1ba6709ef4824e9ad07d99311bc2
        StageMapBg 36992 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        StageMapNull 369DC 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 369E0 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36AA8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36AAC 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36BCE 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36BD2 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36BD6 4272 74b69e982106f0ad15567e929e063069f0c05dce1239f44fdee4a1dbc8875431
        StageGfx 37C86 12760 689e4affef8b87489babb4c581505add6a4a4bc694e58867b370bd20e957ef41
        PowerupAnims 3AE5E 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3AF86 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B098 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B0B0 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B114 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B122 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B13E 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B146 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B15E 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B172 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B204 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B304 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B30A 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B34C 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B354 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B404 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B412 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3B490 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B4C4 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3B596 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3B6E0 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B7B2 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B884 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B956 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3BB06 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3BCB2 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C0B2 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C264 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C3B0 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CA4C 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3CAF2 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3CC0C 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3CD38 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3CE64 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3CF74 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D034 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3D50E 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        DangoGfx 3D6E2 978 c7b387e9d6fa890cbf5f74f947c4cbdf3384510a89c9f307915484cbd82d5813
        StageData F8B4 16 9adb3eb693a07147949e98524dcf1156c62ee6ddba302002253793e8b064b97c
        LayoutDirectory 36828 72 0e23b7ddc2f970febfcd1ff740613a1ae0fe453a7c4fd03ee3bbe3efbe67235e
        LayoutRead0 36870 290 aa2251d5f6a1710e3393db37f0906870794b1ba6709ef4824e9ad07d99311bc2
        LayoutRead1 36992 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead2 369DC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 369E0 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36AA8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36AAC 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36870 290 aa2251d5f6a1710e3393db37f0906870794b1ba6709ef4824e9ad07d99311bc2
        LayoutRead13 36992 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead14 369DC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 369E0 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36AA8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36AAC 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36870 290 aa2251d5f6a1710e3393db37f0906870794b1ba6709ef4824e9ad07d99311bc2
        LayoutRead25 36992 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead26 369DC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 369E0 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36AA8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36AAC 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36BCE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36BD2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 791A 5146 b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611
        PlacementRead 7926 5128 3392ebca8cb4519c9ba9543665d4da496d411d022051cd40997b27849c21eddc
        Dispatch 3B96 240 631ea16e69e31fdce740d77757669bbcf187b1205d88ce278b91fd798b8e1ebc
        PaletteDirectory 5BA 48 4e582d50b1b9aa7475a4eabd53510974d1fcf8415a234cac3beffad7bd58c040
        PaletteSource 5BA 272 6ab860fa557b6850aac4eb900266924fb0f4cc27c66f449771157f0039499b23
        PaletteRead0 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead1 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead2 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead3 5EA 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 60A 96 b25d495dd8535bfde1ce990cdda59f238001b2ae55609eefcba88c235773b918
        PaletteRead5 66A 96 b25d495dd8535bfde1ce990cdda59f238001b2ae55609eefcba88c235773b918
        PlcDirectory F8C4 38 d49060a8a84852f3aa15ed3c1ec126dd1e15cd264b54109e4780212cc166c967
        PlcRead F8C4 334 4bef78ab70cb7d292932d12b8b80d7a84a0f213e9283e81d42c79998242171e8
        PlcBody0 F8EA 8 43fd6d7ef129f390b146d0691aa16138c23d92ce1806c0d0233fba44da694132
        PlcBody1 F8F2 68 12bbe72cf45805ca51bb4f0310f0982acc33c97d8be340c32c24610e70bdbb88
        PlcBody2 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody3 F8EA 8 43fd6d7ef129f390b146d0691aa16138c23d92ce1806c0d0233fba44da694132
        PlcBody4 F9EE 8 ba921f2c1b97b690bb29295d3110cecf84af3e86dfd34b5ec625266c765d7a59
        PlcBody5 F9F6 8 98ae6a64a9c22b2573a43825f2aef1e618c3be23c9593551734054f7628dd64b
        PlcBody6 F986 80 b175c597c719651f23a23307459451dc67b78941f20b0a6b7346451031c0265d
        PlcBody7 F9D6 8 7f8a346b9b508a23b9f2c58b67cd4bb880a19775d98b06d956486bc00e4db61b
        PlcBody8 F9DE 8 a20312efcdd363cd430cca137696462123308b038132206ee736f98c8a49748a
        PlcBody9 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody10 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody11 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody12 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody13 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody14 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody15 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody16 F9E6 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 F936 80 c7fac4c1fe7f78e9a3566a3148bcf9576d266214ed60fb42edbd0f9bfa60c8e5
        PlcBody18 F9FE 20 1ea688e791d9ce9309edb29aec0dc4d04217e0c62fe154ebab954eec9875dd62
        Scalar0 208 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        Scalar1 210 6 bb3619d7d1f5b3fd9c80b3a3b8d6c2ca3ddd8275a768d461909e1e859fb24fff
        Scalar2 216 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        Scalar3 21E 6 8cf7ade7e8d7d767ebc76c7a40031865c357b1f0068100bf1d04cc0d809a365a
        Scalar4 224 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        Scalar5 22C 6 decdbce3a202ed4b3bb9721a56e7f818e8b1df1fbe26527f7d9241071c7dc42a
        Scalar6 232 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        Scalar7 2EA 8 dd7cb482522a938b461c84c15c43796cadd553dfd14e2f42aff5b000a4f50221
        Scalar11 728 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar12 A0E 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar14 1A3E 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar15 20FC 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar16 27F8 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar17 2876 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar18 287A 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar22 32D6 66 7c2acc2d8eb6f804a154602a8dad8cafc69ee46bd7b46dbdf6c5fb6e2a403e47
        Scalar24 4462 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar25 569C 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar31 662A 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar32 6DD4 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar33 6FF2 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar36 98C4 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar38 AB10 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar39 AC68 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar40 AC6C 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar42 AC74 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar43 AC78 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar44 AC7C 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar45 AC80 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar48 AC7C 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar49 AE58 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar53 B416 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar54 BBF6 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar55 BC0C 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar56 BC16 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar57 BC20 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar58 BC2A 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar59 BC4E 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar60 BC72 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar61 BE82 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar62 BE8C 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar63 BE96 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar64 BEA0 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar65 BEAA 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar67 BEBE 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar68 BEC8 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar70 BEDC 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar71 BEE6 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar72 BEF0 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar73 C012 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar74 C036 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar75 C1CC 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar76 C1E0 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar78 C1F4 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar79 C9DC 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar80 CC62 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar81 CE0C 4 45b48cef1a01a48efb01ecec66c253af4cc86bd0ce623b60d70e9b2097041f32
        Scalar82 CE10 4 39ea676b37dc0bf4f6dc6f32b3cc8069b9847b966e5816abbac9a4e719b57856
        Scalar83 CE14 4 75be46c45ba2112004b1e23c2db378906e8140884c8297a54f1edfdd50724a24
        Scalar84 CE94 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar85 D3C4 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar86 E2E2 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar87 E34C 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar88 E4AE 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar89 E4DE 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar90 E9F8 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar91 ED12 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar92 F14A 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar93 F18A 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar94 F6F2 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar95 F78C 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar97 F7D8 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 569C 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 2FA0 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 3318 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        Sections CE0C 12 ca125080a5804389481079a2d00f1f161989b7d9b3c022a476be07c2f95f69b0
        """); }
    private static List<Operand> operandsR71B() { return operands("""
        53FC 43f9002056ec
        661E 43f90020662e
        65B8 217c00206634
        9246 217c00209282
        956A 43f9002097c0
        9612 43f9002097c0
        967C 43f9002097c0
        973A 43f9002097c0
        BCF0 43f9002097c0
        93A0 217c002097ce
        946A 217c002097ce
        94D4 217c0020981e
        937C 43f900209896
        92D4 217c0020989c
        9B28 43f900209d20
        9D0E 43f900209d20
        99F8 237c00209d28
        9BEC 237c00209d28
        A040 43f90020a432
        A1C0 43f90020a432
        9EE8 217c0020a4ba
        9F60 217c0020a4ba
        A114 217c0020a4ba
        A29A 227c0020a4ba
        A6A4 217c0020a6d0
        A740 217c0020a7d2
        AF8C 43f90020b436
        AF84 217c0020b442
        B78C 217c0020b7ae
        B8B8 43f90020b8f0
        B816 217c0020b8fe
        BB4C 43f90020c3ba
        BA50 217c0020c3c6
        BAF0 237c0020c3c6
        C246 217c0020c3c6
        C44A 43f90020c45c
        C442 217c0020c464
        C4B2 217c0020c55e
        C7C8 237c0020c9f4
        C5DA 217c0020cca2
        C60A 237c0020cca2
        CEE4 217c0020d036
        D0A0 45fa00bc
        D0B8 45fa00e8
        D09C 43fa0148
        D0B4 43fa0224
        D3FC 43fa0288
        D670 43fa0014
        D504 237c0020db4c
        DDFC 45fa03c8
        DE0C 45fa03ea
        DDF8 43fa0430
        DE08 43fa046c
        E18E 43fa0132
        E186 217c0020e2ca
        E30E 217c0020e358
        E408 217c0020e4a0
        E508 217c0020e66c
        E5B6 217c0020e6a0
        E7C6 43fa017a
        E80E 43fa0132
        E900 43fa0082
        E760 217c0020e9c0
        E868 217c0020e9d4
        EC7A 43fa000e
        EA98 217c0020ecce
        EC50 217c0020ecce
        EE58 43fa00aa
        EDA2 217c0020ef0c
        F21E 45fa0212
        F22C 45fa022e
        F21A 43fa0270
        F5B0 217c0020f5f8
        F62E 217c0020f814
        E9E 223c00210000
        3E68 223c00210000
        5974 43f900220000
        3DB8 217c0022df20
        71DA 217c0022df20
        5944 45f90022e8ac
        1A7C 43f900232f3c
        AADA 43f9002330bc
        AB3C 43f9002330bc
        ABB8 43f9002330bc
        AC28 43f9002330bc
        ACCE 43f9002330bc
        5F10 43f9002336bc
        5E88 217c002336d8
        109C 45f9002343a4
        115C 45f9002343a4
        120C 45f9002343a4
        12BC 45f9002343a4
        10CA 45f9002344a4
        118A 45f9002344a4
        1328 43f9002344a4
        132E 45f9002344a4
        1362 43f9002344a4
        1370 43f9002344a4
        1376 45f9002344a4
        123A 45f9002354a4
        12EA 45f9002354a4
        1368 45f9002354a4
        5D76 43f90023ae5e
        61E2 43f90023ae5e
        6286 43f90023ae5e
        5D3E 217c0023af86
        6162 217c0023af86
        635A 217c0023af86
        6132 43f90023b098
        6106 217c0023b0b0
        608E 43f90023b114
        6048 217c0023b122
        5FBC 43f90023b13e
        5F9A 217c0023b146
        5E1C 43f90023b15e
        5DEE 217c0023b172
        29A2 49f90023b204
        5D06 43f90023b304
        5BC6 217c0023b30a
        5C22 237c0023b30a
        B08A 43f90023b34c
        B006 217c0023b354
        B314 43f90023b404
        B144 217c0023b412
        B2B0 217c0023b412
        AD88 43f90023b490
        AEE2 43f90023b490
        AD68 217c0023b4c4
        AEA4 237c0023b4c4
        3780 d040d04043f900203b96227100fc
        18E 43f900200208
        194 45f900200210
        19E 43f900200216
        1A4 45f90020021e
        1AE 43f900200224
        1B4 45f90020022c
        1BE 43f900200232
        1C4 45f9002002ea
        722 303b0004
        9D6 103b4036
        1A34 103b1008
        208C 45f9002020fc
        27B6 41f9002027f8
        2824 43f900202876
        286A 43f90020287a
        2F0A 41f9002032d6
        32B0 41f9002032d6
        4422 45f900204462
        5696 103b0004
        6610 303b0018
        6D78 303b005a
        6C5E 45f900206ff2
        9942 43f9002098c4
        AAB4 45fa005a
        ABAE 45f90020ac68
        AB32 45f90020ac6c
        A908 45f90020ac74
        ABA4 45f90020ac74
        AC1E 45f90020ac78
        ACC4 45f90020ac78
        ACB0 45f90020ac7c
        ACBA 45f90020ac7c
        AC92 45f90020ac80
        AE06 45fb0050
        B404 43f90020b416
        BBB8 103b003c
        BB68 45f90020bc0c
        BB7A 45f90020bc16
        BB8E 45f90020bc20
        BB6E 47f90020bc2a
        BB80 47f90020bc4e
        BB94 47f90020bc72
        BD3C 45f90020be82
        BD74 45f90020be82
        BD42 47f90020be8c
        BD7A 47f90020be96
        BD54 47f90020bea0
        BD8C 47f90020beaa
        BD80 49f90020bebe
        BD5A 49f90020bec8
        BD66 49f90020bedc
        BD9E 49f90020bee6
        BDCC 49f90020bef0
        BFDE 47f90020c012
        BFF0 47f90020c036
        C132 47f90020c1cc
        BD6C 4bf90020c1e0
        BFB6 43f90020c1f4
        C76C 45f90020c9dc
        C5EC 45f90020cc62
        CDBA 43fa0050
        CDDE 43fa002c
        CE02 303b100c
        CDD4 303b103e
        CF78 43faff1a
        D13C 43fa0286
        DC4A 43f90020e2e2
        E322 43fb0028
        E42A 43fa0082
        E416 43fa00c6
        E924 43fa00d2
        EAA0 217c0020ed12
        EDAA 217c0020f14a
        ED86 43fa0402
        F6E4 45fa000c
        F77E 45fa000c
        F7CA 45fa000c
        2F3A 267b0064
        2FC4 267b00da
        3330 267b00e6
        100 4ef90020011e
        10C 4ef900201fd4
        112 4ef900201b4a
        """); }
    private static List<Dispatch> dispatchR71B() { return dispatch("""
        1 PlayerObject 203CF4 false
        2 PlayerObject 203CF4 false
        3 PowerupObject 206148 false
        4 MeshObject 20B762 false
        5 NullObject 203C86 true
        6 TestObject 205D12 false
        7 NullObject 203C86 true
        8 BoosterObject 20C214 false
        9 SparksObject 20C418 false
        10 SpringObject 2093DC false
        11 HDoorSplashObject 2060E2 false
        12 HDoorSplashSetObject 2060AA false
        13 HDoorObject 205FD2 false
        14 TunnelSplashObject 205F7C false
        15 MoveSpringObject 209290 false
        16 RingObject 2098AC false
        17 LostRingObject 209B90 false
        18 NullObject 203C86 true
        19 CheckpointObject 205BA0 false
        20 BigRingObject 20AF9E false
        21 CapsuleObject 20AD20 false
        22 GoalObject 20B0D6 false
        23 SignpostObject 20B25A false
        24 ExplosionObject 205DB8 false
        25 MonitorTimeObject 20A0CE false
        26 MonitorItemObject 20A24A false
        27 BoulderObject 209218 false
        28 HudPointsObject 20A722 false
        29 PathSwapObject 20B7C6 false
        30 SpringLauncherObject 20B9A8 false
        31 FlowerObject 205E46 false
        32 SwingObject 20CE20 false
        33 KanabunObject 20D054 false
        34 KabasiraObject 20D3C8 false
        35 HotaruObject 20DBF8 false
        36 BlockObject 20E2F2 false
        37 SpikeBallObject 20E3B6 false
        38 JumpSpikeBallObject 20E4E4 false
        39 ProjectorObject 20EA0E false
        40 AnimalObject 20E728 false
        41 RobotGeneratorObject 20ED38 false
        42 DangoObject 20F1A0 false
        43 RollTunnelObject 206324 false
        44 NullObject 203C86 true
        45 NullObject 203C86 true
        46 NullObject 203C86 true
        47 NullObject 203C86 true
        48 NullObject 203C86 true
        49 NullObject 203C86 true
        50 NullObject 203C86 true
        51 NullObject 203C86 true
        52 NullObject 203C86 true
        53 SwapPlatformObject 20F58E false
        54 NullObject 203C86 true
        55 NullObject 203C86 true
        56 SearchlightObject 20F608 false
        57 NullObject 203C86 true
        58 ResultsObject 20C71C false
        59 GameOverObject 20C47A false
        60 TitleCardObject 20C596 false
        """); }
    private static List<LayoutSlot> layoutsR71B() { return layouts("""
        0 72 48 6
        1 362 24 3
        2 436 1 1
        3 440 33 6
        4 934 1 1
        5 640 1 1
        6 644 48 6
        7 934 1 1
        8 934 1 1
        9 938 1 1
        10 938 1 1
        11 938 1 1
        12 72 48 6
        13 362 24 3
        14 436 1 1
        15 440 33 6
        16 934 1 1
        17 640 1 1
        18 644 48 6
        19 934 1 1
        20 934 1 1
        21 938 1 1
        22 938 1 1
        23 938 1 1
        24 72 48 6
        25 362 24 3
        26 436 1 1
        27 440 33 6
        28 934 1 1
        29 640 1 1
        30 644 48 6
        31 934 1 1
        32 934 1 1
        33 938 1 1
        34 938 1 1
        35 938 1 1
        """); }
    private static List<PaletteRecord> palettesR71B() { return palettes("""
        0 2098666 64256 31 128 false
        1 2098666 64256 31 128 false
        2 2098666 64256 31 128 false
        3 2098666 64256 7 32 false
        4 2098698 64288 23 96 false
        5 2098794 64288 23 96 false
        """); }
    private static List<Range> rangesR71C() { return ranges("""
        PlayerAnims 5778 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66BA 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66C0 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 930E 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 97E2 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 97FE 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 984C 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 985A 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 98AA 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9922 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9928 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 9DAC 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 9DB4 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 9DF8 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 9E9A 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A4BE 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites A546 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites A75C 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites A85E 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B4C2 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B4CE 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx B66E 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites B83A 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims B97C 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites B98A 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C446 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C452 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims C4E8 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites C4F0 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites C5EA 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites CA80 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites CD2E 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D06E 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D196 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D1DA 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D21E 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D312 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims D6BE 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites DB84 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E1FE 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E230 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E262 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E2AE 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E2FA 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E302 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E390 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites E4D8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites E6A4 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites E6D8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E97A 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E9BC 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9F8 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 EA0C 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims ECC2 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites ED06 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims EF3C 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites EF44 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F46A 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 F494 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites F4C4 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites F630 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites F84C 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 57856 6d7a0076311553dbf6d5cb051031e4048076ab390b02392a26894c77011e6c39
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 800 4f7331a41ffae00a3cb1b878a8e470cf0039bef8bd168262f17c77c3e81de80e
        StageMapFg 3680C 290 cc9711cc432a2205f2fd8e79039a99893b4f41ef9f401301d25cc9c8fe5bbdeb
        StageMapBg 3692E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        StageMapNull 36936 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 3693A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36A02 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36A06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36B28 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36B2C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36B30 4120 24b8e940889bf116907a5d9c49b868d2d69a48702fa873f483782eb77f6add16
        StageGfx 37B48 13100 76b0744f3fb1a3eabc78d159f8606d2371afbc5d812d43708e9e9a210f65d615
        PowerupAnims 3AE74 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3AF9C 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B0AE 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B0C6 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B12A 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B138 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B154 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B15C 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B174 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B188 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B21A 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B31A 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B320 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B362 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B36A 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B41A 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B428 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3B4A6 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B4DA 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3B5AC 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3B6F6 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B7C8 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B89A 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B96C 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3BB1C 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3BCC8 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C0C8 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C27A 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C3C6 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CA62 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3CB08 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3CC22 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3CD4E 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3CE7A 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3CF8A 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D04A 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3D524 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData F8EC 16 1f175bd78f0e5ef399f585e639ad5922e53dc80e43e635337ab10a0d36cf343a
        LayoutDirectory 367C4 72 1ef0afc636da3f6c63a0fe180f1250bdc98b3fd1ca77d9455cef3a75af3fa4fe
        LayoutRead0 3680C 290 cc9711cc432a2205f2fd8e79039a99893b4f41ef9f401301d25cc9c8fe5bbdeb
        LayoutRead1 3692E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead2 36936 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 3693A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36A02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36A06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 3680C 290 cc9711cc432a2205f2fd8e79039a99893b4f41ef9f401301d25cc9c8fe5bbdeb
        LayoutRead13 3692E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead14 36936 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 3693A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36A02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36A06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 3680C 290 cc9711cc432a2205f2fd8e79039a99893b4f41ef9f401301d25cc9c8fe5bbdeb
        LayoutRead25 3692E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead26 36936 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 3693A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36A02 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36A06 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36B28 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36B2C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 79A6 5146 b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611
        PlacementRead 79B2 5128 3392ebca8cb4519c9ba9543665d4da496d411d022051cd40997b27849c21eddc
        Dispatch 3C22 240 6cd0f06d7140e4c7c5ad09ed99174c59597cd3ce000eee260b22f0a3d1612b96
        PaletteDirectory 5E0 56 1991476ddcf139a07f78b4c0e9b4646be5e60e61a2115e477b880661b7a0891d
        PaletteSource 5E0 420 44fe751e32feb512056c27099e59bc5cfe425363935d87c6773acf7af2e82a3b
        PaletteRead0 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead1 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead2 698 128 fb101fd59b4ef4ca8cd77b44164d465cf43cb82893464c2dd2838f056e28d321
        PaletteRead3 698 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B8 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead5 718 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead6 778 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory F8FC 38 2ebf82bfb66cb2158ace42264fe91d5f70796c764d93e100b2e996cf958e0bba
        PlcRead F8FC 222 0cea0801519a22a9e6eae98409025549f1601865974204b255bffb00f69df49d
        PlcBody0 F922 8 c835f5937e357553a13689fcecf8ab82bc079c4501400d0cf2a89e2a37b0646a
        PlcBody1 F92A 74 1df99fe9cf5cc005a253ece359ba80208d044f6d1f9b0032fce6c677c935d602
        PlcBody2 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody3 F922 8 c835f5937e357553a13689fcecf8ab82bc079c4501400d0cf2a89e2a37b0646a
        PlcBody4 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody5 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody6 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody7 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody8 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody9 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody10 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody11 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody12 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody13 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody14 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody15 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody16 F9BE 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 F974 74 56d9bbc873c8923eddafcd8a53703f63501fc501ebdd7090a3b1d62ed05837a3
        PlcBody18 F9C6 20 b6dc3d2b22fa03e9bd5896550f4ffaff9676a97ed1a2528355914971544af02e
        Scalar0 214 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        Scalar1 21C 12 13c4c7dd38ca13a627957d45386cd20d3d97ffef8ca0524be85135323b7d4841
        Scalar2 228 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        Scalar3 230 12 6bbc11d6fddf8d1a60ae8acfcfd0112ed3cf553ae2323a264e5e5374e46c85a9
        Scalar4 23C 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        Scalar5 244 12 028b02d3899a85ca544bf634db1f27ff7525107752ca24be163d79d7d0e25b3e
        Scalar6 250 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        Scalar7 308 16 2e40e700e7c1e0964d2d39fadb0dcf78a2646eca2f9e7374c2e8e18af62739b0
        Scalar13 7E2 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar14 AC8 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar16 1AF8 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar17 21B6 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar18 28B2 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar19 2930 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar20 2934 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar23 3370 66 efbb03b7a7f6fd3c29391d4d0281e1830a85caadd831c3f04716faca4107a42e
        Scalar25 44EE 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar26 5728 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar32 66B6 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar33 6E60 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar34 707E 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar37 9950 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar39 AB9C 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar40 ACF4 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar41 ACF8 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar43 AD00 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar44 AD04 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar45 AD08 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar46 AD0C 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar49 AD08 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar50 AEE4 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar54 B4A2 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar55 BC82 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar56 BC98 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar57 BCA2 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar58 BCAC 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar59 BCB6 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar60 BCDA 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar61 BCFE 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar62 BF0E 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar63 BF18 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar64 BF22 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar65 BF2C 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar66 BF36 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar68 BF4A 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar69 BF54 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar71 BF68 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar72 BF72 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar73 BF7C 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar74 C09E 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar75 C0C2 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar76 C258 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar77 C26C 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar79 C280 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar80 CA68 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar81 CCEE 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar82 CECC 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar83 D3FC 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar84 E31A 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar85 E384 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar86 E4E6 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar87 E516 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar88 EA30 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar89 ED4A 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar90 F182 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar91 F1C2 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar92 F72A 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar93 F7C4 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar95 F810 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5728 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 303A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33B2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR71C() { return operands("""
        5488 43f900205778
        66AA 43f9002066ba
        6644 217c002066c0
        92D2 217c0020930e
        95F6 43f90020984c
        969E 43f90020984c
        9708 43f90020984c
        97C6 43f90020984c
        BD7C 43f90020984c
        942C 217c0020985a
        94F6 217c0020985a
        9560 217c002098aa
        9408 43f900209922
        9360 217c00209928
        9BB4 43f900209dac
        9D9A 43f900209dac
        9A84 237c00209db4
        9C78 237c00209db4
        A0CC 43f90020a4be
        A24C 43f90020a4be
        9F74 217c0020a546
        9FEC 217c0020a546
        A1A0 217c0020a546
        A326 227c0020a546
        A730 217c0020a75c
        A7CC 217c0020a85e
        B018 43f90020b4c2
        B010 217c0020b4ce
        B818 217c0020b83a
        B944 43f90020b97c
        B8A2 217c0020b98a
        BBD8 43f90020c446
        BADC 217c0020c452
        BB7C 237c0020c452
        C2D2 217c0020c452
        C4D6 43f90020c4e8
        C4CE 217c0020c4f0
        C53E 217c0020c5ea
        C854 237c0020ca80
        C666 217c0020cd2e
        C696 237c0020cd2e
        CF1C 217c0020d06e
        D0D8 45fa00bc
        D0F0 45fa00e8
        D0D4 43fa0148
        D0EC 43fa0224
        D434 43fa0288
        D6A8 43fa0014
        D53C 237c0020db84
        DE34 45fa03c8
        DE44 45fa03ea
        DE30 43fa0430
        DE40 43fa046c
        E1C6 43fa0132
        E1BE 217c0020e302
        E346 217c0020e390
        E440 217c0020e4d8
        E540 217c0020e6a4
        E5EE 217c0020e6d8
        E7FE 43fa017a
        E846 43fa0132
        E938 43fa0082
        E798 217c0020e9f8
        E8A0 217c0020ea0c
        ECB2 43fa000e
        EAD0 217c0020ed06
        EC88 217c0020ed06
        EE90 43fa00aa
        EDDA 217c0020ef44
        F256 45fa0212
        F264 45fa022e
        F252 43fa0270
        F5E8 217c0020f630
        F666 217c0020f84c
        F58 223c00210000
        3EF4 223c00210000
        5A00 43f900220000
        3E44 217c0022df20
        7266 217c0022df20
        59D0 45f90022e8ac
        1B36 43f900232f3c
        AB66 43f9002330bc
        ABC8 43f9002330bc
        AC44 43f9002330bc
        ACB4 43f9002330bc
        AD5A 43f9002330bc
        5F9C 43f9002336bc
        5F14 217c002336d8
        1156 45f9002343a4
        1216 45f9002343a4
        12C6 45f9002343a4
        1376 45f9002343a4
        1184 45f9002344a4
        1244 45f9002344a4
        13E2 43f9002344a4
        13E8 45f9002344a4
        141C 43f9002344a4
        142A 43f9002344a4
        1430 45f9002344a4
        12F4 45f9002354a4
        13A4 45f9002354a4
        1422 45f9002354a4
        5E02 43f90023ae74
        626E 43f90023ae74
        6312 43f90023ae74
        5DCA 217c0023af9c
        61EE 217c0023af9c
        63E6 217c0023af9c
        61BE 43f90023b0ae
        6192 217c0023b0c6
        611A 43f90023b12a
        60D4 217c0023b138
        6048 43f90023b154
        6026 217c0023b15c
        5EA8 43f90023b174
        5E7A 217c0023b188
        5D92 43f90023b31a
        5C52 217c0023b320
        5CAE 237c0023b320
        B116 43f90023b362
        B092 217c0023b36a
        B3A0 43f90023b41a
        B1D0 217c0023b428
        B33C 217c0023b428
        AE14 43f90023b4a6
        AF6E 43f90023b4a6
        ADF4 217c0023b4da
        AF30 237c0023b4da
        380C d040d04043f900203c22227100fc
        18E 43f900200214
        194 45f90020021c
        19E 43f900200228
        1A4 45f900200230
        1AE 43f90020023c
        1B4 45f900200244
        1BE 43f900200250
        1C4 45f900200308
        7DC 303b0004
        A90 103b4036
        1AEE 103b1008
        2146 45f9002021b6
        2870 41f9002028b2
        28DE 43f900202930
        2924 43f900202934
        2FA4 41f900203370
        334A 41f900203370
        44AE 45f9002044ee
        5722 103b0004
        669C 303b0018
        6E04 303b005a
        6CEA 45f90020707e
        99CE 43f900209950
        AB40 45fa005a
        AC3A 45f90020acf4
        ABBE 45f90020acf8
        A994 45f90020ad00
        AC30 45f90020ad00
        ACAA 45f90020ad04
        AD50 45f90020ad04
        AD3C 45f90020ad08
        AD46 45f90020ad08
        AD1E 45f90020ad0c
        AE92 45fb0050
        B490 43f90020b4a2
        BC44 103b003c
        BBF4 45f90020bc98
        BC06 45f90020bca2
        BC1A 45f90020bcac
        BBFA 47f90020bcb6
        BC0C 47f90020bcda
        BC20 47f90020bcfe
        BDC8 45f90020bf0e
        BE00 45f90020bf0e
        BDCE 47f90020bf18
        BE06 47f90020bf22
        BDE0 47f90020bf2c
        BE18 47f90020bf36
        BE0C 49f90020bf4a
        BDE6 49f90020bf54
        BDF2 49f90020bf68
        BE2A 49f90020bf72
        BE58 49f90020bf7c
        C06A 47f90020c09e
        C07C 47f90020c0c2
        C1BE 47f90020c258
        BDF8 4bf90020c26c
        C042 43f90020c280
        C7F8 45f90020ca68
        C678 45f90020ccee
        CFB0 43faff1a
        D174 43fa0286
        DC82 43f90020e31a
        E35A 43fb0028
        E462 43fa0082
        E44E 43fa00c6
        E95C 43fa00d2
        EAD8 217c0020ed4a
        EDE2 217c0020f182
        EDBE 43fa0402
        F71C 45fa000c
        F7B6 45fa000c
        F802 45fa000c
        2FD4 267b0064
        305E 267b00da
        33CA 267b00e6
        100 4ef90020011e
        10C 4ef90020208e
        112 4ef900201c04
        """); }
    private static List<Dispatch> dispatchR71C() { return dispatch("""
        1 PlayerObject 203D80 false
        2 PlayerObject 203D80 false
        3 PowerupObject 2061D4 false
        4 MeshObject 20B7EE false
        5 NullObject 203D12 true
        6 TestObject 205D9E false
        7 NullObject 203D12 true
        8 BoosterObject 20C2A0 false
        9 SparksObject 20C4A4 false
        10 SpringObject 209468 false
        11 HDoorSplashObject 20616E false
        12 HDoorSplashSetObject 206136 false
        13 HDoorObject 20605E false
        14 TunnelSplashObject 206008 false
        15 MoveSpringObject 20931C false
        16 RingObject 209938 false
        17 LostRingObject 209C1C false
        18 NullObject 203D12 true
        19 CheckpointObject 205C2C false
        20 BigRingObject 20B02A false
        21 CapsuleObject 20ADAC false
        22 GoalObject 20B162 false
        23 SignpostObject 20B2E6 false
        24 ExplosionObject 205E44 false
        25 MonitorTimeObject 20A15A false
        26 MonitorItemObject 20A2D6 false
        27 BoulderObject 2092A4 false
        28 HudPointsObject 20A7AE false
        29 PathSwapObject 20B852 false
        30 SpringLauncherObject 20BA34 false
        31 FlowerObject 205ED2 false
        32 SwingObject 20CE58 false
        33 KanabunObject 20D08C false
        34 KabasiraObject 20D400 false
        35 HotaruObject 20DC30 false
        36 BlockObject 20E32A false
        37 SpikeBallObject 20E3EE false
        38 JumpSpikeBallObject 20E51C false
        39 ProjectorObject 20EA46 false
        40 AnimalObject 20E760 false
        41 RobotGeneratorObject 20ED70 false
        42 DangoObject 20F1D8 false
        43 RollTunnelObject 2063B0 false
        44 NullObject 203D12 true
        45 NullObject 203D12 true
        46 NullObject 203D12 true
        47 NullObject 203D12 true
        48 NullObject 203D12 true
        49 NullObject 203D12 true
        50 NullObject 203D12 true
        51 NullObject 203D12 true
        52 NullObject 203D12 true
        53 SwapPlatformObject 20F5C6 false
        54 NullObject 203D12 true
        55 NullObject 203D12 true
        56 SearchlightObject 20F640 false
        57 NullObject 203D12 true
        58 ResultsObject 20C7A8 false
        59 GameOverObject 20C506 false
        60 TitleCardObject 20C622 false
        """); }
    private static List<LayoutSlot> layoutsR71C() { return layouts("""
        0 72 48 6
        1 362 2 3
        2 370 1 1
        3 374 33 6
        4 868 1 1
        5 574 1 1
        6 578 48 6
        7 868 1 1
        8 868 1 1
        9 872 1 1
        10 872 1 1
        11 872 1 1
        12 72 48 6
        13 362 2 3
        14 370 1 1
        15 374 33 6
        16 868 1 1
        17 574 1 1
        18 578 48 6
        19 868 1 1
        20 868 1 1
        21 872 1 1
        22 872 1 1
        23 872 1 1
        24 72 48 6
        25 362 2 3
        26 370 1 1
        27 374 33 6
        28 868 1 1
        29 574 1 1
        30 578 48 6
        31 868 1 1
        32 868 1 1
        33 872 1 1
        34 872 1 1
        35 872 1 1
        """); }
    private static List<PaletteRecord> palettesR71C() { return palettes("""
        0 2098712 64256 31 128 false
        1 2098712 64256 31 128 false
        2 2098840 64256 31 128 false
        3 2098840 64256 7 32 false
        4 2098872 64288 23 96 false
        5 2098968 64288 23 96 false
        6 2099064 64288 2 12 false
        """); }
    private static List<Range> rangesR71D() { return ranges("""
        PlayerAnims 5768 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66AA 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66B0 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 92FE 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 97D2 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 97EE 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 983C 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 984A 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 989A 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9912 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9918 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 9D9C 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 9DA4 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 9DE8 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 9E8A 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A4AE 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites A536 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites A74C 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites A84E 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B4B2 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B4BE 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx B65E 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites B82A 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims B96C 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites B97A 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C436 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C442 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims C4D8 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites C4E0 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites C5DA 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites CA70 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites CD1E 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D05E 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D186 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D1CA 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D20E 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D302 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims D6AE 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites DB74 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E1EE 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E220 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E252 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E29E 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E2EA 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E2F2 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E380 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites E4C8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites E694 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites E6C8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E96A 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E9AC 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E9E8 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 E9FC 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims ECB2 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites ECF6 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims EF2C 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites EF34 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F45A 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 F484 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites F4B4 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites F620 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites F83C 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 54784 5143aaadc67c7514eece0187201e9a48a7229da185cb42470a4a23841eed9041
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 1024 cd1fa0926edcedddd45193177ba4e8dd155ca60c087c3f181328965c17b623e3
        StageMapFg 368EC 290 793571d7cbf75caade3cda4a2bd63fa86ca42e2fb93a68740a4930911d992e0e
        StageMapBg 36A0E 56 f07573148d8c3155277f3ba9b21180127905807c5b51830d687115cd07eb028b
        StageMapNull 36A46 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B12 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36C38 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36C3C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36C40 5898 56672393ff3b0a9f5aaf132d3958c339e53534a60aed52da5a6ccee5d91dd7a3
        StageGfx 3834A 12892 d5c807c59e88ca0ea218703aa498bf99b2407717de2721b5d0825e9dddab7833
        PowerupAnims 3B5A6 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3B6CE 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B7E0 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B7F8 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B85C 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B86A 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B886 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B88E 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B8A6 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B8BA 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B94C 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3BA4C 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3BA52 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3BA94 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3BA9C 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3BB4C 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3BB5A 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3BBD8 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3BC0C 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3BCDE 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3BE28 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3BEFA 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3BFCC 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3C09E 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3C24E 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3C3FA 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C7FA 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C9AC 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3CAF8 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3D194 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3D23A 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3D354 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3D480 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3D5AC 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3D6BC 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D77C 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3DC56 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData F8DC 16 7756819d20629c1edb4b7f5a8423cc92ff395ab40deb659fb7cb6d57c2e7f0f4
        LayoutDirectory 368A4 72 1d6b95de11f25195fee5e772232a07c7a8c8461d03882cd2c38cd554e56c03c5
        LayoutRead0 368EC 290 793571d7cbf75caade3cda4a2bd63fa86ca42e2fb93a68740a4930911d992e0e
        LayoutRead1 36A0E 56 f07573148d8c3155277f3ba9b21180127905807c5b51830d687115cd07eb028b
        LayoutRead2 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 368EC 290 793571d7cbf75caade3cda4a2bd63fa86ca42e2fb93a68740a4930911d992e0e
        LayoutRead13 36A0E 56 f07573148d8c3155277f3ba9b21180127905807c5b51830d687115cd07eb028b
        LayoutRead14 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 368EC 290 793571d7cbf75caade3cda4a2bd63fa86ca42e2fb93a68740a4930911d992e0e
        LayoutRead25 36A0E 56 f07573148d8c3155277f3ba9b21180127905807c5b51830d687115cd07eb028b
        LayoutRead26 36A46 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36A4A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B12 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B16 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36C38 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36C3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 7996 5146 b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611
        PlacementRead 79A2 5128 3392ebca8cb4519c9ba9543665d4da496d411d022051cd40997b27849c21eddc
        Dispatch 3C12 240 2d57325e6a93f27977ad5e6a9195a5bafb3d9c64cee88ea02dcf630e7173c9c6
        PaletteDirectory 5BE 56 bcf9e87720156264ec6edeaa95f6bd58acbb3ec4d21ac1023357be07e10f0f9b
        PaletteSource 5BE 452 10bf5854b6fe13dc2cbf0f8d40df8ef5decbfabe5682eaa537e52e70f37bab96
        PaletteRead0 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead1 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead2 676 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        PaletteRead3 6F6 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 716 96 03e3186c8a2f467964a42e666de16d50a7229a3f7cd59b9899f5590bae03df9e
        PaletteRead6 776 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory F8EC 38 2ebf82bfb66cb2158ace42264fe91d5f70796c764d93e100b2e996cf958e0bba
        PlcRead F8EC 222 91b70e2b8de1347db93346daad161e97f271fbde29841b73e5d4b479d6384322
        PlcBody0 F912 8 b701e2426b58195bc51814022da26ce1f35133de54b8ea05cc8df9d7d90a938a
        PlcBody1 F91A 74 e1bd5e9c170eee1644c77e63ec02eb07cbd16c764a1848f4130ca2916a134bcb
        PlcBody2 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody3 F912 8 b701e2426b58195bc51814022da26ce1f35133de54b8ea05cc8df9d7d90a938a
        PlcBody4 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody5 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody6 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody7 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody8 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody9 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody10 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody11 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody12 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody13 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody14 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody15 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody16 F9AE 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 F964 74 4d2edef44e283f34ea2d5aad44bf705ce972173207e5bb01251ee8e0e09e559f
        PlcBody18 F9B6 20 9f5ea34c3c2206a8812b1fa13e279efb9f1a5f96163c2c1ca28cafe39c6797a4
        Scalar0 240 14 947d0b337eb8fc0560bcbf7530f8b5461683f8ef96253688cf56226889ba4e2c
        Scalar1 24E 8 049017fae74bc2e9ad9d02bfe4067634e06bea27aa22a4df876f7aa82199c1b4
        Scalar2 256 8 19b544ee8839682fdad6f1d8f421de168409fb8518e299367bf30955c51b85ee
        Scalar3 25E 12 38daf54631d5f9c56e5880bd31b63b7ac05a68fecf88789b007711a7fadcd2aa
        Scalar4 26A 8 7102e083920b5b502a5bc20b9d5781c381c188f248be01f10a63102fbebcd2ac
        Scalar5 272 12 20c7beffe9b58d212b53b5195f1b5dfd360cd5b40ba47d3cf7b69af84b020498
        Scalar6 27E 8 afdb4c55435ef8df560dc4ec53305def3d00918e6478edc7ba7c32ebfb5e041d
        Scalar7 286 12 5540e51c03435ca733a7c43b4b80043e619447b912d81770b534c02cc72513ef
        Scalar10 2A0 18 efc43adfb3b595f795ee35048f6a1c3c28c5bf36a312cd6da6f904115c32e62b
        Scalar11 2B2 16 04466bf078f1a43a19c9bade54d7bd45b041d4798caea2540f9c9ed3cba57506
        Scalar12 2C2 18 47e68c4223482c2d074e7982c47417793b48e5d3f780a759f43401e4268c17dd
        Scalar13 2D4 16 9281caecc477e3fb891f3a8ab7cdccc7a76afaded4a439b1d7483f8c5eb0f0fc
        Scalar14 2E4 18 a1bf6df4fff3f0efa008631967aecc5ee79823e7f461501d4e0c6676abf41219
        Scalar20 7E0 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar21 AC6 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar23 1AF6 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar24 21B4 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar25 28B0 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar26 292E 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar27 2932 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar30 3360 66 df04f0dccd553d06793b2bd814693c27e3a1a950e3ff5ff44b2b8833a24a8944
        Scalar32 44DE 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar33 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar39 66A6 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar40 6E50 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar41 706E 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar44 9940 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar46 AB8C 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar47 ACE4 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar48 ACE8 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar50 ACF0 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar51 ACF4 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar52 ACF8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar53 ACFC 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar56 ACF8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar57 AED4 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar61 B492 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar62 BC72 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar63 BC88 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar64 BC92 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar65 BC9C 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar66 BCA6 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar67 BCCA 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar68 BCEE 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar69 BEFE 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar70 BF08 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar71 BF12 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar72 BF1C 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar73 BF26 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar75 BF3A 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar76 BF44 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar78 BF58 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar79 BF62 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar80 BF6C 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar81 C08E 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar82 C0B2 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar83 C248 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar84 C25C 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar86 C270 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar87 CA58 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar88 CCDE 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar89 CEBC 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar90 D3EC 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar91 E30A 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar92 E374 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar93 E4D6 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar94 E506 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar95 EA20 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar96 ED3A 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar97 F172 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar98 F1B2 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar99 F71A 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar100 F7B4 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar102 F800 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 302A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33A2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR71D() { return operands("""
        5478 43f900205768
        669A 43f9002066aa
        6634 217c002066b0
        92C2 217c002092fe
        95E6 43f90020983c
        968E 43f90020983c
        96F8 43f90020983c
        97B6 43f90020983c
        BD6C 43f90020983c
        941C 217c0020984a
        94E6 217c0020984a
        9550 217c0020989a
        93F8 43f900209912
        9350 217c00209918
        9BA4 43f900209d9c
        9D8A 43f900209d9c
        9A74 237c00209da4
        9C68 237c00209da4
        A0BC 43f90020a4ae
        A23C 43f90020a4ae
        9F64 217c0020a536
        9FDC 217c0020a536
        A190 217c0020a536
        A316 227c0020a536
        A720 217c0020a74c
        A7BC 217c0020a84e
        B008 43f90020b4b2
        B000 217c0020b4be
        B808 217c0020b82a
        B934 43f90020b96c
        B892 217c0020b97a
        BBC8 43f90020c436
        BACC 217c0020c442
        BB6C 237c0020c442
        C2C2 217c0020c442
        C4C6 43f90020c4d8
        C4BE 217c0020c4e0
        C52E 217c0020c5da
        C844 237c0020ca70
        C656 217c0020cd1e
        C686 237c0020cd1e
        CF0C 217c0020d05e
        D0C8 45fa00bc
        D0E0 45fa00e8
        D0C4 43fa0148
        D0DC 43fa0224
        D424 43fa0288
        D698 43fa0014
        D52C 237c0020db74
        DE24 45fa03c8
        DE34 45fa03ea
        DE20 43fa0430
        DE30 43fa046c
        E1B6 43fa0132
        E1AE 217c0020e2f2
        E336 217c0020e380
        E430 217c0020e4c8
        E530 217c0020e694
        E5DE 217c0020e6c8
        E7EE 43fa017a
        E836 43fa0132
        E928 43fa0082
        E788 217c0020e9e8
        E890 217c0020e9fc
        ECA2 43fa000e
        EAC0 217c0020ecf6
        EC78 217c0020ecf6
        EE80 43fa00aa
        EDCA 217c0020ef34
        F246 45fa0212
        F254 45fa022e
        F242 43fa0270
        F5D8 217c0020f620
        F656 217c0020f83c
        F56 223c00210000
        3EE4 223c00210000
        59F0 43f900220000
        3E34 217c0022df20
        7256 217c0022df20
        59C0 45f90022e8ac
        1B34 43f900232f3c
        AB56 43f9002330bc
        ABB8 43f9002330bc
        AC34 43f9002330bc
        ACA4 43f9002330bc
        AD4A 43f9002330bc
        5F8C 43f9002336bc
        5F04 217c002336d8
        1154 45f9002343a4
        1214 45f9002343a4
        12C4 45f9002343a4
        1374 45f9002343a4
        1182 45f9002344a4
        1242 45f9002344a4
        13E0 43f9002344a4
        13E6 45f9002344a4
        141A 43f9002344a4
        1428 43f9002344a4
        142E 45f9002344a4
        12F2 45f9002354a4
        13A2 45f9002354a4
        1420 45f9002354a4
        5DF2 43f90023b5a6
        625E 43f90023b5a6
        6302 43f90023b5a6
        5DBA 217c0023b6ce
        61DE 217c0023b6ce
        63D6 217c0023b6ce
        61AE 43f90023b7e0
        6182 217c0023b7f8
        610A 43f90023b85c
        60C4 217c0023b86a
        6038 43f90023b886
        6016 217c0023b88e
        5E98 43f90023b8a6
        5E6A 217c0023b8ba
        5D82 43f90023ba4c
        5C42 217c0023ba52
        5C9E 237c0023ba52
        B106 43f90023ba94
        B082 217c0023ba9c
        B390 43f90023bb4c
        B1C0 217c0023bb5a
        B32C 217c0023bb5a
        AE04 43f90023bbd8
        AF5E 43f90023bbd8
        ADE4 217c0023bc0c
        AF20 237c0023bc0c
        37FC d040d04043f900203c12227100fc
        18C 43f900200240
        192 45f90020024e
        19C 43f900200256
        1A2 45f90020025e
        1AC 43f90020026a
        1B2 45f900200272
        1BC 43f90020027e
        1C2 45f900200286
        1CC 43f9002002a0
        1D2 45f9002002b2
        1DC 43f9002002c2
        1F0 45f9002002d4
        1EA 43f9002002e4
        7DA 303b0004
        A8E 103b4036
        1AEC 103b1008
        2144 45f9002021b4
        286E 41f9002028b0
        28DC 43f90020292e
        2922 43f900202932
        2F94 41f900203360
        333A 41f900203360
        449E 45f9002044de
        5712 103b0004
        668C 303b0018
        6DF4 303b005a
        6CDA 45f90020706e
        99BE 43f900209940
        AB30 45fa005a
        AC2A 45f90020ace4
        ABAE 45f90020ace8
        A984 45f90020acf0
        AC20 45f90020acf0
        AC9A 45f90020acf4
        AD40 45f90020acf4
        AD2C 45f90020acf8
        AD36 45f90020acf8
        AD0E 45f90020acfc
        AE82 45fb0050
        B480 43f90020b492
        BC34 103b003c
        BBE4 45f90020bc88
        BBF6 45f90020bc92
        BC0A 45f90020bc9c
        BBEA 47f90020bca6
        BBFC 47f90020bcca
        BC10 47f90020bcee
        BDB8 45f90020befe
        BDF0 45f90020befe
        BDBE 47f90020bf08
        BDF6 47f90020bf12
        BDD0 47f90020bf1c
        BE08 47f90020bf26
        BDFC 49f90020bf3a
        BDD6 49f90020bf44
        BDE2 49f90020bf58
        BE1A 49f90020bf62
        BE48 49f90020bf6c
        C05A 47f90020c08e
        C06C 47f90020c0b2
        C1AE 47f90020c248
        BDE8 4bf90020c25c
        C032 43f90020c270
        C7E8 45f90020ca58
        C668 45f90020ccde
        CFA0 43faff1a
        D164 43fa0286
        DC72 43f90020e30a
        E34A 43fb0028
        E452 43fa0082
        E43E 43fa00c6
        E94C 43fa00d2
        EAC8 217c0020ed3a
        EDD2 217c0020f172
        EDAE 43fa0402
        F70C 45fa000c
        F7A6 45fa000c
        F7F2 45fa000c
        2FC4 267b0064
        304E 267b00da
        33BA 267b00e6
        100 4ef90020011e
        10C 4ef90020208c
        112 4ef900201c02
        """); }
    private static List<Dispatch> dispatchR71D() { return dispatch("""
        1 PlayerObject 203D70 false
        2 PlayerObject 203D70 false
        3 PowerupObject 2061C4 false
        4 MeshObject 20B7DE false
        5 NullObject 203D02 true
        6 TestObject 205D8E false
        7 NullObject 203D02 true
        8 BoosterObject 20C290 false
        9 SparksObject 20C494 false
        10 SpringObject 209458 false
        11 HDoorSplashObject 20615E false
        12 HDoorSplashSetObject 206126 false
        13 HDoorObject 20604E false
        14 TunnelSplashObject 205FF8 false
        15 MoveSpringObject 20930C false
        16 RingObject 209928 false
        17 LostRingObject 209C0C false
        18 NullObject 203D02 true
        19 CheckpointObject 205C1C false
        20 BigRingObject 20B01A false
        21 CapsuleObject 20AD9C false
        22 GoalObject 20B152 false
        23 SignpostObject 20B2D6 false
        24 ExplosionObject 205E34 false
        25 MonitorTimeObject 20A14A false
        26 MonitorItemObject 20A2C6 false
        27 BoulderObject 209294 false
        28 HudPointsObject 20A79E false
        29 PathSwapObject 20B842 false
        30 SpringLauncherObject 20BA24 false
        31 FlowerObject 205EC2 false
        32 SwingObject 20CE48 false
        33 KanabunObject 20D07C false
        34 KabasiraObject 20D3F0 false
        35 HotaruObject 20DC20 false
        36 BlockObject 20E31A false
        37 SpikeBallObject 20E3DE false
        38 JumpSpikeBallObject 20E50C false
        39 ProjectorObject 20EA36 false
        40 AnimalObject 20E750 false
        41 RobotGeneratorObject 20ED60 false
        42 DangoObject 20F1C8 false
        43 RollTunnelObject 2063A0 false
        44 NullObject 203D02 true
        45 NullObject 203D02 true
        46 NullObject 203D02 true
        47 NullObject 203D02 true
        48 NullObject 203D02 true
        49 NullObject 203D02 true
        50 NullObject 203D02 true
        51 NullObject 203D02 true
        52 NullObject 203D02 true
        53 SwapPlatformObject 20F5B6 false
        54 NullObject 203D02 true
        55 NullObject 203D02 true
        56 SearchlightObject 20F630 false
        57 NullObject 203D02 true
        58 ResultsObject 20C798 false
        59 GameOverObject 20C4F6 false
        60 TitleCardObject 20C612 false
        """); }
    private static List<LayoutSlot> layoutsR71D() { return layouts("""
        0 72 48 6
        1 362 18 3
        2 418 1 1
        3 422 33 6
        4 916 1 1
        5 622 1 1
        6 626 48 6
        7 916 1 1
        8 916 1 1
        9 920 1 1
        10 920 1 1
        11 920 1 1
        12 72 48 6
        13 362 18 3
        14 418 1 1
        15 422 33 6
        16 916 1 1
        17 622 1 1
        18 626 48 6
        19 916 1 1
        20 916 1 1
        21 920 1 1
        22 920 1 1
        23 920 1 1
        24 72 48 6
        25 362 18 3
        26 418 1 1
        27 422 33 6
        28 916 1 1
        29 622 1 1
        30 626 48 6
        31 916 1 1
        32 916 1 1
        33 920 1 1
        34 920 1 1
        35 920 1 1
        """); }
    private static List<PaletteRecord> palettesR71D() { return palettes("""
        0 2098678 64256 31 128 false
        1 2098678 64256 31 128 false
        2 2098806 64256 31 128 false
        3 2098934 64256 7 32 false
        4 2098966 64288 23 96 false
        5 0 0 65535 262144 true
        6 2099062 64288 2 12 false
        """); }
    private static List<Range> rangesR72A() { return ranges("""
        PlayerAnims 56B6 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 65F8 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 65FE 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 97FC 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9CD0 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9CEC 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9D3A 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9D48 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9D98 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9E10 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9E16 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims A29A 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A2A2 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A2E6 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A388 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A9AC 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AA34 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites AC4A 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites AD4C 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B9B0 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B9BC 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BB5C 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites BD28 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims BE6A 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites BE78 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C934 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C940 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims C9D6 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites C9DE 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites CAD8 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites CF6E 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites D21C 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D5B0 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D6D8 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D71C 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D760 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D854 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims DC00 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites E0C6 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E740 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E772 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E7A4 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E7F0 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E83C 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E844 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E8D2 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites EA1A 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites EBE6 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites EC1A 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 EEBC 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 EEFE 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EF3A 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 EF4E 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims F204 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites F248 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims F47E 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F486 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F9AC 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 F9D6 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites FA06 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites FB72 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites FD8E 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 59904 02387165982aad6bee5ca6d2dd4106999e355c98c06c124053634330072d6b86
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 1024 26d3693f2a6d2e74568f203e65ea47dfd7ffb394c5e01a74c76b333d0ce6abae
        StageMapFg 368EC 386 79f29e436066e209fa3847dfaef81fef9c7c7697dfa634a14c94f8a1a2c66484
        StageMapBg 36A6E 56 d9e2b68acd0f407e42af835a0fff1df509c5d118aa0724151654932b71734010
        StageMapNull 36AA6 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36AAA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B72 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B76 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36C98 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36C9C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36CA0 5560 52d846a5ca413c3f085513aac0f7456da40fde507f52a389095417a4dec44dc2
        StageGfx 38258 12666 7685296e6ed458f8a70d74dc0bab2dd738ea59dab98f4ef58281493e56dbefa6
        PowerupAnims 3B3D2 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3B4FA 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B60C 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B624 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B688 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B696 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B6B2 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B6BA 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B6D2 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B6E6 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B778 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B878 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B87E 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B8C0 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B8C8 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B978 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B986 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3BA04 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3BA38 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3BB0A 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3BC54 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3BD26 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3BDF8 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3BECA 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3C07A 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3C226 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C626 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C7D8 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C924 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CFC0 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3D066 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3D180 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3D2AC 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3D3D8 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3D4E8 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D5A8 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3DA82 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData FE2E 16 5faf6aba4437c9b13daf98fde516edaf54378dbfe8089135fb7583e16cc5c7d8
        LayoutDirectory 368A4 72 ce20c8058e7c0708b2c38902439a2869c4263e2ede07c2e6a9144416edb03e80
        LayoutRead0 368EC 386 79f29e436066e209fa3847dfaef81fef9c7c7697dfa634a14c94f8a1a2c66484
        LayoutRead1 36A6E 56 d9e2b68acd0f407e42af835a0fff1df509c5d118aa0724151654932b71734010
        LayoutRead2 36AA6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36AAA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B72 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B76 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 368EC 386 79f29e436066e209fa3847dfaef81fef9c7c7697dfa634a14c94f8a1a2c66484
        LayoutRead13 36A6E 56 d9e2b68acd0f407e42af835a0fff1df509c5d118aa0724151654932b71734010
        LayoutRead14 36AA6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36AAA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B72 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B76 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 368EC 386 79f29e436066e209fa3847dfaef81fef9c7c7697dfa634a14c94f8a1a2c66484
        LayoutRead25 36A6E 56 d9e2b68acd0f407e42af835a0fff1df509c5d118aa0724151654932b71734010
        LayoutRead26 36AA6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36AAA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B72 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B76 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36C98 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36C9C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 78E4 6602 7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58
        PlacementRead 78F0 6584 3b858cff8a8d900fc105741bd3823eaa20c1243f3419fc88ead83f7699dc7513
        Dispatch 3B60 240 8a5b9243326d936729dd219b60bf369f7c5ec00aba3e3e656c2353692c2cd067
        PaletteDirectory 4F4 48 10a12e831a582096077b8d3f1f97d2fbe4432d1f55bc5fc5b236ce2cb898a432
        PaletteSource 4F4 400 1a8036d037661be38eca3627e78e078a996f0d380f741447a5569da0ed9df5eb
        PaletteRead0 524 128 bfca2608f0d989add5a8d6d297d8ca96eff74fe9422be98dce9443ba5992b96d
        PaletteRead1 524 128 bfca2608f0d989add5a8d6d297d8ca96eff74fe9422be98dce9443ba5992b96d
        PaletteRead2 5A4 128 dab83c11eeadac120bab4f8e8f96d990ed11b15b9a1fb8cbbafff77d6d61a5eb
        PaletteRead3 5A4 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 5C4 96 abea771d16373a65015157e1c313cb633ddf513d299f26aaa32ed9e00bbfd7ed
        PaletteRead5 624 96 abea771d16373a65015157e1c313cb633ddf513d299f26aaa32ed9e00bbfd7ed
        PlcDirectory FE3E 38 ee6049d76bb3defc2bcb685b37ca523feb4f5c608c34cd019e5915140e2a502d
        PlcRead FE3E 318 266bfcbca4a1502d5c83ca92551e4ed118fe1099c8b46121c4d54e7742f06a76
        PlcBody0 FE64 14 f5025a63aa098a38f7bc8da4aeee8ef36d63a5eabb617fc1fd479299a6ae0c01
        PlcBody1 FE72 74 64a12180b42fc0daf4fe0accbf06f72187b45a2bfcea738ed5823e09013e7f1e
        PlcBody2 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody3 FE64 14 f5025a63aa098a38f7bc8da4aeee8ef36d63a5eabb617fc1fd479299a6ae0c01
        PlcBody4 FF06 74 d9da55f772d5bc8b16effa7250bd518e8385457d15c92d7879b83f18ea516d84
        PlcBody5 FF50 8 8a96caf0e428f2fbe740e1616fa8674af4720bac492fe7193eb626dd992042bc
        PlcBody6 FF58 8 a20312efcdd363cd430cca137696462123308b038132206ee736f98c8a49748a
        PlcBody7 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody8 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody9 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody10 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody11 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody12 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody13 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody14 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody15 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody16 FF60 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FEBC 74 b735afa374d6037921929ff21969af94544ef4e1150072071c8679a0e6a5b349
        PlcBody18 FF68 20 7937780b6b81bcdb8dd4934462505d3141d6aa2c6c116a979abed14696e19910
        Scalar0 202 6 0b437cbd4ff5e8672f6d85020ed1f831c41c89329f08e3de4ed9913dd47a26da
        Scalar1 208 8 6e0e2eb4b0e875cefda2a861d719df9f0c8345dc0fd5e3922ce4aa5678695c4c
        Scalar2 210 6 0707d43204118460a93df02ee289404161d63b14ed85e14f5cabc657a4e72431
        Scalar3 216 8 35673c11da5755c9a2a353b9c23df8d22b73770d7a7305955f7c99f489edef10
        Scalar4 21E 6 6c17a3d4fa1418da7f9de12db0892f91b7c3db00a1d34e521f0c1c9ef9cd297d
        Scalar5 224 8 29e2a8efbafc9d649de9ab87912539e692c7d2176f459e8a3f8ce77072cd118a
        Scalar10 6E2 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar11 9C8 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar13 19F8 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar14 20B6 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar15 27B2 12 228f2e44671a33b5e2a754bc33f1f96b210dda04ff67353b9078333f6cc40310
        Scalar16 2830 4 30231c88bbed9bba14b24e4d04354bb9058797bd5867295cbbc585b2f27fbadb
        Scalar17 2834 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar21 32A0 66 3aabe566457975b1f8dcd34838d66833bea2e0316142b40e20b2cf172af77804
        Scalar23 442C 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar24 5666 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar30 65F4 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar31 6D9E 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar32 6FBC 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar35 9E3E 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar37 B08A 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar38 B1E2 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar39 B1E6 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar41 B1EE 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar42 B1F2 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar43 B1F6 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar44 B1FA 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar47 B1F6 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar48 B3D2 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar52 B990 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar53 C170 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar54 C186 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar55 C190 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar56 C19A 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar57 C1A4 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar58 C1C8 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar59 C1EC 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar60 C3FC 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar61 C406 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar62 C410 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar63 C41A 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar64 C424 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar66 C438 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar67 C442 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar69 C456 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar70 C460 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar71 C46A 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar72 C58C 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar73 C5B0 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar74 C746 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar75 C75A 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar77 C76E 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar78 CF56 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar79 D1DC 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar80 D386 4 e56e6a30c5afb9f3d7fa44566754bfc89ce2833c60f60aad6dd8a583846fa584
        Scalar81 D38A 4 bd0b879367e7d54a90cae5da9c0bc0de1c4b11f7065196e3c1299f2e0e1d1685
        Scalar82 D38E 4 572f6a289a69da0087f95cd97bef2376e9c6b575361d95e4ef6c7adbb1902d2b
        Scalar83 D40E 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar84 D93E 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar85 E85C 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar86 E8C6 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar87 EA28 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar88 EA58 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar89 EF72 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar90 F28C 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar91 F6C4 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar92 F704 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar93 FC6C 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar94 FD06 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar96 FD52 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5666 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 2F6A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 32E2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        Sections D386 12 73ddef7d8d1abfcb653eece4a039488869f6c778153e982ebfdd361195679635
        """); }
    private static List<Operand> operandsR72A() { return operands("""
        53C6 43f9002056b6
        65E8 43f9002065f8
        6582 217c002065fe
        97C0 217c002097fc
        9AE4 43f900209d3a
        9B8C 43f900209d3a
        9BF6 43f900209d3a
        9CB4 43f900209d3a
        C26A 43f900209d3a
        991A 217c00209d48
        99E4 217c00209d48
        9A4E 217c00209d98
        98F6 43f900209e10
        984E 217c00209e16
        A0A2 43f90020a29a
        A288 43f90020a29a
        9F72 237c0020a2a2
        A166 237c0020a2a2
        A5BA 43f90020a9ac
        A73A 43f90020a9ac
        A462 217c0020aa34
        A4DA 217c0020aa34
        A68E 217c0020aa34
        A814 227c0020aa34
        AC1E 217c0020ac4a
        ACBA 217c0020ad4c
        B506 43f90020b9b0
        B4FE 217c0020b9bc
        BD06 217c0020bd28
        BE32 43f90020be6a
        BD90 217c0020be78
        C0C6 43f90020c934
        BFCA 217c0020c940
        C06A 237c0020c940
        C7C0 217c0020c940
        C9C4 43f90020c9d6
        C9BC 217c0020c9de
        CA2C 217c0020cad8
        CD42 237c0020cf6e
        CB54 217c0020d21c
        CB84 237c0020d21c
        D45E 217c0020d5b0
        D61A 45fa00bc
        D632 45fa00e8
        D616 43fa0148
        D62E 43fa0224
        D976 43fa0288
        DBEA 43fa0014
        DA7E 237c0020e0c6
        E376 45fa03c8
        E386 45fa03ea
        E372 43fa0430
        E382 43fa046c
        E708 43fa0132
        E700 217c0020e844
        E888 217c0020e8d2
        E982 217c0020ea1a
        EA82 217c0020ebe6
        EB30 217c0020ec1a
        ED40 43fa017a
        ED88 43fa0132
        EE7A 43fa0082
        ECDA 217c0020ef3a
        EDE2 217c0020ef4e
        F1F4 43fa000e
        F012 217c0020f248
        F1CA 217c0020f248
        F3D2 43fa00aa
        F31C 217c0020f486
        F798 45fa0212
        F7A6 45fa022e
        F794 43fa0270
        FB2A 217c0020fb72
        FBA8 217c0020fd8e
        E58 223c00210000
        3E32 223c00210000
        593E 43f900220000
        3D82 217c0022df20
        71A4 217c0022df20
        590E 45f90022e8ac
        1A36 43f900232f3c
        B054 43f9002330bc
        B0B6 43f9002330bc
        B132 43f9002330bc
        B1A2 43f9002330bc
        B248 43f9002330bc
        5EDA 43f9002336bc
        5E52 217c002336d8
        1056 45f9002343a4
        1116 45f9002343a4
        11C6 45f9002343a4
        1276 45f9002343a4
        1084 45f9002344a4
        1144 45f9002344a4
        12E2 43f9002344a4
        12E8 45f9002344a4
        131C 43f9002344a4
        132A 43f9002344a4
        1330 45f9002344a4
        11F4 45f9002354a4
        12A4 45f9002354a4
        1322 45f9002354a4
        5D40 43f90023b3d2
        61AC 43f90023b3d2
        6250 43f90023b3d2
        5D08 217c0023b4fa
        612C 217c0023b4fa
        6324 217c0023b4fa
        60FC 43f90023b60c
        60D0 217c0023b624
        6058 43f90023b688
        6012 217c0023b696
        5F86 43f90023b6b2
        5F64 217c0023b6ba
        5DE6 43f90023b6d2
        5DB8 217c0023b6e6
        5CD0 43f90023b878
        5B90 217c0023b87e
        5BEC 237c0023b87e
        B604 43f90023b8c0
        B580 217c0023b8c8
        B88E 43f90023b978
        B6BE 217c0023b986
        B82A 217c0023b986
        B302 43f90023ba04
        B45C 43f90023ba04
        B2E2 217c0023ba38
        B41E 237c0023ba38
        374A d040d04043f900203b60227100fc
        18C 43f900200202
        192 45f900200208
        19C 43f900200210
        1A2 45f900200216
        1AC 43f90020021e
        1B2 45f900200224
        6DC 303b0004
        990 103b4036
        19EE 103b1008
        2046 45f9002020b6
        2770 41f9002027b2
        27DE 43f900202830
        2824 43f900202834
        2ED4 41f9002032a0
        327A 41f9002032a0
        43EC 45f90020442c
        5660 103b0004
        65DA 303b0018
        6D42 303b005a
        6C28 45f900206fbc
        9EBC 43f900209e3e
        B02E 45fa005a
        B128 45f90020b1e2
        B0AC 45f90020b1e6
        AE82 45f90020b1ee
        B11E 45f90020b1ee
        B198 45f90020b1f2
        B23E 45f90020b1f2
        B22A 45f90020b1f6
        B234 45f90020b1f6
        B20C 45f90020b1fa
        B380 45fb0050
        B97E 43f90020b990
        C132 103b003c
        C0E2 45f90020c186
        C0F4 45f90020c190
        C108 45f90020c19a
        C0E8 47f90020c1a4
        C0FA 47f90020c1c8
        C10E 47f90020c1ec
        C2B6 45f90020c3fc
        C2EE 45f90020c3fc
        C2BC 47f90020c406
        C2F4 47f90020c410
        C2CE 47f90020c41a
        C306 47f90020c424
        C2FA 49f90020c438
        C2D4 49f90020c442
        C2E0 49f90020c456
        C318 49f90020c460
        C346 49f90020c46a
        C558 47f90020c58c
        C56A 47f90020c5b0
        C6AC 47f90020c746
        C2E6 4bf90020c75a
        C530 43f90020c76e
        CCE6 45f90020cf56
        CB66 45f90020d1dc
        D334 43fa0050
        D358 43fa002c
        D37C 303b100c
        D34E 303b103e
        D4F2 43faff1a
        D6B6 43fa0286
        E1C4 43f90020e85c
        E89C 43fb0028
        E9A4 43fa0082
        E990 43fa00c6
        EE9E 43fa00d2
        F01A 217c0020f28c
        F324 217c0020f6c4
        F300 43fa0402
        FC5E 45fa000c
        FCF8 45fa000c
        FD44 45fa000c
        2F04 267b0064
        2F8E 267b00da
        32FA 267b00e6
        100 4ef90020011e
        10C 4ef900201f8e
        112 4ef900201b04
        """); }
    private static List<Dispatch> dispatchR72A() { return dispatch("""
        1 PlayerObject 203CBE false
        2 PlayerObject 203CBE false
        3 PowerupObject 206112 false
        4 MeshObject 20BCDC false
        5 NullObject 203C50 true
        6 TestObject 205CDC false
        7 NullObject 203C50 true
        8 BoosterObject 20C78E false
        9 SparksObject 20C992 false
        10 SpringObject 209956 false
        11 HDoorSplashObject 2060AC false
        12 HDoorSplashSetObject 206074 false
        13 HDoorObject 205F9C false
        14 TunnelSplashObject 205F46 false
        15 MoveSpringObject 20980A false
        16 RingObject 209E26 false
        17 LostRingObject 20A10A false
        18 NullObject 203C50 true
        19 CheckpointObject 205B6A false
        20 BigRingObject 20B518 false
        21 CapsuleObject 20B29A false
        22 GoalObject 20B650 false
        23 SignpostObject 20B7D4 false
        24 ExplosionObject 205D82 false
        25 MonitorTimeObject 20A648 false
        26 MonitorItemObject 20A7C4 false
        27 BoulderObject 209792 false
        28 HudPointsObject 20AC9C false
        29 PathSwapObject 20BD40 false
        30 SpringLauncherObject 20BF22 false
        31 FlowerObject 205E10 false
        32 SwingObject 20D39A false
        33 KanabunObject 20D5CE false
        34 KabasiraObject 20D942 false
        35 HotaruObject 20E172 false
        36 BlockObject 20E86C false
        37 SpikeBallObject 20E930 false
        38 JumpSpikeBallObject 20EA5E false
        39 ProjectorObject 20EF88 false
        40 AnimalObject 20ECA2 false
        41 RobotGeneratorObject 20F2B2 false
        42 DangoObject 20F71A false
        43 RollTunnelObject 2062EE false
        44 NullObject 203C50 true
        45 NullObject 203C50 true
        46 NullObject 203C50 true
        47 NullObject 203C50 true
        48 NullObject 203C50 true
        49 NullObject 203C50 true
        50 NullObject 203C50 true
        51 NullObject 203C50 true
        52 NullObject 203C50 true
        53 SwapPlatformObject 20FB08 false
        54 NullObject 203C50 true
        55 NullObject 203C50 true
        56 SearchlightObject 20FB82 false
        57 NullObject 203C50 true
        58 ResultsObject 20CC96 false
        59 GameOverObject 20C9F4 false
        60 TitleCardObject 20CB10 false
        """); }
    private static List<LayoutSlot> layoutsR72A() { return layouts("""
        0 72 48 8
        1 458 18 3
        2 514 1 1
        3 518 33 6
        4 1012 1 1
        5 718 1 1
        6 722 48 6
        7 1012 1 1
        8 1012 1 1
        9 1016 1 1
        10 1016 1 1
        11 1016 1 1
        12 72 48 8
        13 458 18 3
        14 514 1 1
        15 518 33 6
        16 1012 1 1
        17 718 1 1
        18 722 48 6
        19 1012 1 1
        20 1012 1 1
        21 1016 1 1
        22 1016 1 1
        23 1016 1 1
        24 72 48 8
        25 458 18 3
        26 514 1 1
        27 518 33 6
        28 1012 1 1
        29 718 1 1
        30 722 48 6
        31 1012 1 1
        32 1012 1 1
        33 1016 1 1
        34 1016 1 1
        35 1016 1 1
        """); }
    private static List<PaletteRecord> palettesR72A() { return palettes("""
        0 2098468 64256 31 128 false
        1 2098468 64256 31 128 false
        2 2098596 64256 31 128 false
        3 2098596 64256 7 32 false
        4 2098628 64288 23 96 false
        5 2098724 64288 23 96 false
        """); }
    private static List<Range> rangesR72B() { return ranges("""
        PlayerAnims 56EC 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 662E 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 6634 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 9832 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D06 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D22 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9D70 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9D7E 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9DCE 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9E46 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9E4C 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims A2D0 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A2D8 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A31C 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A3BE 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims A9E2 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AA6A 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites AC80 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites AD82 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims B9E6 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites B9F2 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BB92 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites BD5E 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims BEA0 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites BEAE 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C96A 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C976 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims CA0C 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites CA14 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites CB0E 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites CFA4 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites D252 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D5E6 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D70E 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D752 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D796 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D88A 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims DC36 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites E0FC 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E776 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E7A8 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E7DA 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E826 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E872 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E87A 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E908 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites EA50 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites EC1C 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites EC50 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 EEF2 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 EF34 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EF70 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 EF84 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims F23A 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites F27E 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims F4B4 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F4BC 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 F9E2 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 FA0C 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites FA3C 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites FBA8 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites FDC4 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 58368 35852068d0984b59b0a224719c5ae2022878b583f570cce523f9497adf469f8d
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 900 7ccedc5e18c370c384ec47d2ec13236da1867bd01d28d015130d113ac40625c3
        StageMapFg 36870 386 6adb066098a3868636cb8a8b8b25337889f4e0239b6ce74eeee6b9e1a5ec60a6
        StageMapBg 369F2 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        StageMapNull 36A3C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36A40 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B08 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B0C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36C2E 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36C32 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36C36 4272 62f23a8c127180e57b0d8658b1ee0e162b28905d0f80e045878aceb5f63c5ac9
        StageGfx 37CE6 12760 689e4affef8b87489babb4c581505add6a4a4bc694e58867b370bd20e957ef41
        PowerupAnims 3AEBE 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3AFE6 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B0F8 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B110 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B174 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B182 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B19E 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B1A6 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B1BE 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B1D2 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B264 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B364 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B36A 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B3AC 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B3B4 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B464 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B472 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3B4F0 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B524 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3B5F6 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3B740 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B812 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B8E4 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B9B6 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3BB66 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3BD12 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C112 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C2C4 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C410 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CAAC 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3CB52 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3CC6C 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3CD98 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3CEC4 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3CFD4 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D094 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3D56E 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData FE64 16 a86fd4e05be937fdab1e2b9b892f92d27df10ee25054b50d1cb2c295f76dcb92
        LayoutDirectory 36828 72 f96813226a5082cf5850bc1b5407276c2aed6c0954244285027150dd65e2788a
        LayoutRead0 36870 386 6adb066098a3868636cb8a8b8b25337889f4e0239b6ce74eeee6b9e1a5ec60a6
        LayoutRead1 369F2 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead2 36A3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36A40 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B08 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B0C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 36870 386 6adb066098a3868636cb8a8b8b25337889f4e0239b6ce74eeee6b9e1a5ec60a6
        LayoutRead13 369F2 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead14 36A3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36A40 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B08 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B0C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 36870 386 6adb066098a3868636cb8a8b8b25337889f4e0239b6ce74eeee6b9e1a5ec60a6
        LayoutRead25 369F2 74 dcf055290788fae0403f837b4cfa244cdba09e8b36f3d6ac4d76bc5d29d9d039
        LayoutRead26 36A3C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36A40 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B08 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B0C 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36C2E 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36C32 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 791A 6602 7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58
        PlacementRead 7926 6584 3b858cff8a8d900fc105741bd3823eaa20c1243f3419fc88ead83f7699dc7513
        Dispatch 3B96 240 2ffda69e921b31a1c496de9d10be2e86341ac8108c22b38055b38a19aa59ae1e
        PaletteDirectory 5BA 48 4e582d50b1b9aa7475a4eabd53510974d1fcf8415a234cac3beffad7bd58c040
        PaletteSource 5BA 272 6ab860fa557b6850aac4eb900266924fb0f4cc27c66f449771157f0039499b23
        PaletteRead0 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead1 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead2 5EA 128 bba3cf887f2b8d9ece474cc69837ac7a21d85b15fce520fb77e511a48e59c8af
        PaletteRead3 5EA 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 60A 96 b25d495dd8535bfde1ce990cdda59f238001b2ae55609eefcba88c235773b918
        PaletteRead5 66A 96 b25d495dd8535bfde1ce990cdda59f238001b2ae55609eefcba88c235773b918
        PlcDirectory FE74 38 d49060a8a84852f3aa15ed3c1ec126dd1e15cd264b54109e4780212cc166c967
        PlcRead FE74 334 7c4d17240d1f864d16776a865ce5c31436a1140b9adbeb50911129dc96ae86cd
        PlcBody0 FE9A 8 e18563573cefd8a4c7f94e44f9e337260c4cb24543b16142a7f401d9d4f850b2
        PlcBody1 FEA2 68 12bbe72cf45805ca51bb4f0310f0982acc33c97d8be340c32c24610e70bdbb88
        PlcBody2 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody3 FE9A 8 e18563573cefd8a4c7f94e44f9e337260c4cb24543b16142a7f401d9d4f850b2
        PlcBody4 FF9E 8 11a1d6c2f2ba6bd5de6bfe31997f32f20450f285e84dcbe90edc3f3e7ccd10c2
        PlcBody5 FFA6 8 b4ef852795602774560cc9ccbefd441bd68c80d3c0d4c62cb3400dd1f0e983c0
        PlcBody6 FF36 80 0f827c00d30f0a121f9b4f68965da22572158b8fcf7026a25633681d472a0427
        PlcBody7 FF86 8 f6d777cb9d4b875ca1f6c825c3504add3c4597f1e657b7d794e0823ed83c8e83
        PlcBody8 FF8E 8 a20312efcdd363cd430cca137696462123308b038132206ee736f98c8a49748a
        PlcBody9 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody10 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody11 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody12 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody13 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody14 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody15 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody16 FF96 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FEE6 80 212d3ebd8eeb78fc86af327c786780be8d423658eb64c5abd85c28abb236195e
        PlcBody18 FFAE 20 beaa04fe14028e805248ccc2c8f756874e6491a5a8010ee3cd27088ccba14529
        Scalar0 208 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        Scalar1 210 6 bb3619d7d1f5b3fd9c80b3a3b8d6c2ca3ddd8275a768d461909e1e859fb24fff
        Scalar2 216 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        Scalar3 21E 6 8cf7ade7e8d7d767ebc76c7a40031865c357b1f0068100bf1d04cc0d809a365a
        Scalar4 224 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        Scalar5 22C 6 decdbce3a202ed4b3bb9721a56e7f818e8b1df1fbe26527f7d9241071c7dc42a
        Scalar6 232 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        Scalar7 2EA 8 dd7cb482522a938b461c84c15c43796cadd553dfd14e2f42aff5b000a4f50221
        Scalar11 728 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar12 A0E 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar14 1A3E 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar15 20FC 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar16 27F8 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar17 2876 4 30231c88bbed9bba14b24e4d04354bb9058797bd5867295cbbc585b2f27fbadb
        Scalar18 287A 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar22 32D6 66 7c2acc2d8eb6f804a154602a8dad8cafc69ee46bd7b46dbdf6c5fb6e2a403e47
        Scalar24 4462 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar25 569C 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar31 662A 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar32 6DD4 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar33 6FF2 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar36 9E74 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar38 B0C0 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar39 B218 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar40 B21C 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar42 B224 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar43 B228 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar44 B22C 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar45 B230 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar48 B22C 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar49 B408 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar53 B9C6 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar54 C1A6 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar55 C1BC 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar56 C1C6 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar57 C1D0 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar58 C1DA 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar59 C1FE 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar60 C222 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar61 C432 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar62 C43C 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar63 C446 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar64 C450 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar65 C45A 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar67 C46E 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar68 C478 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar70 C48C 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar71 C496 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar72 C4A0 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar73 C5C2 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar74 C5E6 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar75 C77C 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar76 C790 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar78 C7A4 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar79 CF8C 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar80 D212 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar81 D3BC 4 78683053b09395ca86daaa5c408b5c45df451e824c02b69e2151a920a7aaf586
        Scalar82 D3C0 4 39ea676b37dc0bf4f6dc6f32b3cc8069b9847b966e5816abbac9a4e719b57856
        Scalar83 D3C4 4 75be46c45ba2112004b1e23c2db378906e8140884c8297a54f1edfdd50724a24
        Scalar84 D444 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar85 D974 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar86 E892 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar87 E8FC 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar88 EA5E 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar89 EA8E 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar90 EFA8 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar91 F2C2 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar92 F6FA 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar93 F73A 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar94 FCA2 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar95 FD3C 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar97 FD88 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 569C 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 2FA0 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 3318 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        Sections D3BC 12 057e77cdc05aee35a17f39d0a7be16fbe1baa32abd1251edf4c9958845dbe7dd
        """); }
    private static List<Operand> operandsR72B() { return operands("""
        53FC 43f9002056ec
        661E 43f90020662e
        65B8 217c00206634
        97F6 217c00209832
        9B1A 43f900209d70
        9BC2 43f900209d70
        9C2C 43f900209d70
        9CEA 43f900209d70
        C2A0 43f900209d70
        9950 217c00209d7e
        9A1A 217c00209d7e
        9A84 217c00209dce
        992C 43f900209e46
        9884 217c00209e4c
        A0D8 43f90020a2d0
        A2BE 43f90020a2d0
        9FA8 237c0020a2d8
        A19C 237c0020a2d8
        A5F0 43f90020a9e2
        A770 43f90020a9e2
        A498 217c0020aa6a
        A510 217c0020aa6a
        A6C4 217c0020aa6a
        A84A 227c0020aa6a
        AC54 217c0020ac80
        ACF0 217c0020ad82
        B53C 43f90020b9e6
        B534 217c0020b9f2
        BD3C 217c0020bd5e
        BE68 43f90020bea0
        BDC6 217c0020beae
        C0FC 43f90020c96a
        C000 217c0020c976
        C0A0 237c0020c976
        C7F6 217c0020c976
        C9FA 43f90020ca0c
        C9F2 217c0020ca14
        CA62 217c0020cb0e
        CD78 237c0020cfa4
        CB8A 217c0020d252
        CBBA 237c0020d252
        D494 217c0020d5e6
        D650 45fa00bc
        D668 45fa00e8
        D64C 43fa0148
        D664 43fa0224
        D9AC 43fa0288
        DC20 43fa0014
        DAB4 237c0020e0fc
        E3AC 45fa03c8
        E3BC 45fa03ea
        E3A8 43fa0430
        E3B8 43fa046c
        E73E 43fa0132
        E736 217c0020e87a
        E8BE 217c0020e908
        E9B8 217c0020ea50
        EAB8 217c0020ec1c
        EB66 217c0020ec50
        ED76 43fa017a
        EDBE 43fa0132
        EEB0 43fa0082
        ED10 217c0020ef70
        EE18 217c0020ef84
        F22A 43fa000e
        F048 217c0020f27e
        F200 217c0020f27e
        F408 43fa00aa
        F352 217c0020f4bc
        F7CE 45fa0212
        F7DC 45fa022e
        F7CA 43fa0270
        FB60 217c0020fba8
        FBDE 217c0020fdc4
        E9E 223c00210000
        3E68 223c00210000
        5974 43f900220000
        3DB8 217c0022df20
        71DA 217c0022df20
        5944 45f90022e8ac
        1A7C 43f900232f3c
        B08A 43f9002330bc
        B0EC 43f9002330bc
        B168 43f9002330bc
        B1D8 43f9002330bc
        B27E 43f9002330bc
        5F10 43f9002336bc
        5E88 217c002336d8
        109C 45f9002343a4
        115C 45f9002343a4
        120C 45f9002343a4
        12BC 45f9002343a4
        10CA 45f9002344a4
        118A 45f9002344a4
        1328 43f9002344a4
        132E 45f9002344a4
        1362 43f9002344a4
        1370 43f9002344a4
        1376 45f9002344a4
        123A 45f9002354a4
        12EA 45f9002354a4
        1368 45f9002354a4
        5D76 43f90023aebe
        61E2 43f90023aebe
        6286 43f90023aebe
        5D3E 217c0023afe6
        6162 217c0023afe6
        635A 217c0023afe6
        6132 43f90023b0f8
        6106 217c0023b110
        608E 43f90023b174
        6048 217c0023b182
        5FBC 43f90023b19e
        5F9A 217c0023b1a6
        5E1C 43f90023b1be
        5DEE 217c0023b1d2
        29A2 49f90023b264
        5D06 43f90023b364
        5BC6 217c0023b36a
        5C22 237c0023b36a
        B63A 43f90023b3ac
        B5B6 217c0023b3b4
        B8C4 43f90023b464
        B6F4 217c0023b472
        B860 217c0023b472
        B338 43f90023b4f0
        B492 43f90023b4f0
        B318 217c0023b524
        B454 237c0023b524
        3780 d040d04043f900203b96227100fc
        18E 43f900200208
        194 45f900200210
        19E 43f900200216
        1A4 45f90020021e
        1AE 43f900200224
        1B4 45f90020022c
        1BE 43f900200232
        1C4 45f9002002ea
        722 303b0004
        9D6 103b4036
        1A34 103b1008
        208C 45f9002020fc
        27B6 41f9002027f8
        2824 43f900202876
        286A 43f90020287a
        2F0A 41f9002032d6
        32B0 41f9002032d6
        4422 45f900204462
        5696 103b0004
        6610 303b0018
        6D78 303b005a
        6C5E 45f900206ff2
        9EF2 43f900209e74
        B064 45fa005a
        B15E 45f90020b218
        B0E2 45f90020b21c
        AEB8 45f90020b224
        B154 45f90020b224
        B1CE 45f90020b228
        B274 45f90020b228
        B260 45f90020b22c
        B26A 45f90020b22c
        B242 45f90020b230
        B3B6 45fb0050
        B9B4 43f90020b9c6
        C168 103b003c
        C118 45f90020c1bc
        C12A 45f90020c1c6
        C13E 45f90020c1d0
        C11E 47f90020c1da
        C130 47f90020c1fe
        C144 47f90020c222
        C2EC 45f90020c432
        C324 45f90020c432
        C2F2 47f90020c43c
        C32A 47f90020c446
        C304 47f90020c450
        C33C 47f90020c45a
        C330 49f90020c46e
        C30A 49f90020c478
        C316 49f90020c48c
        C34E 49f90020c496
        C37C 49f90020c4a0
        C58E 47f90020c5c2
        C5A0 47f90020c5e6
        C6E2 47f90020c77c
        C31C 4bf90020c790
        C566 43f90020c7a4
        CD1C 45f90020cf8c
        CB9C 45f90020d212
        D36A 43fa0050
        D38E 43fa002c
        D3B2 303b100c
        D384 303b103e
        D528 43faff1a
        D6EC 43fa0286
        E1FA 43f90020e892
        E8D2 43fb0028
        E9DA 43fa0082
        E9C6 43fa00c6
        EED4 43fa00d2
        F050 217c0020f2c2
        F35A 217c0020f6fa
        F336 43fa0402
        FC94 45fa000c
        FD2E 45fa000c
        FD7A 45fa000c
        2F3A 267b0064
        2FC4 267b00da
        3330 267b00e6
        100 4ef90020011e
        10C 4ef900201fd4
        112 4ef900201b4a
        """); }
    private static List<Dispatch> dispatchR72B() { return dispatch("""
        1 PlayerObject 203CF4 false
        2 PlayerObject 203CF4 false
        3 PowerupObject 206148 false
        4 MeshObject 20BD12 false
        5 NullObject 203C86 true
        6 TestObject 205D12 false
        7 NullObject 203C86 true
        8 BoosterObject 20C7C4 false
        9 SparksObject 20C9C8 false
        10 SpringObject 20998C false
        11 HDoorSplashObject 2060E2 false
        12 HDoorSplashSetObject 2060AA false
        13 HDoorObject 205FD2 false
        14 TunnelSplashObject 205F7C false
        15 MoveSpringObject 209840 false
        16 RingObject 209E5C false
        17 LostRingObject 20A140 false
        18 NullObject 203C86 true
        19 CheckpointObject 205BA0 false
        20 BigRingObject 20B54E false
        21 CapsuleObject 20B2D0 false
        22 GoalObject 20B686 false
        23 SignpostObject 20B80A false
        24 ExplosionObject 205DB8 false
        25 MonitorTimeObject 20A67E false
        26 MonitorItemObject 20A7FA false
        27 BoulderObject 2097C8 false
        28 HudPointsObject 20ACD2 false
        29 PathSwapObject 20BD76 false
        30 SpringLauncherObject 20BF58 false
        31 FlowerObject 205E46 false
        32 SwingObject 20D3D0 false
        33 KanabunObject 20D604 false
        34 KabasiraObject 20D978 false
        35 HotaruObject 20E1A8 false
        36 BlockObject 20E8A2 false
        37 SpikeBallObject 20E966 false
        38 JumpSpikeBallObject 20EA94 false
        39 ProjectorObject 20EFBE false
        40 AnimalObject 20ECD8 false
        41 RobotGeneratorObject 20F2E8 false
        42 DangoObject 20F750 false
        43 RollTunnelObject 206324 false
        44 NullObject 203C86 true
        45 NullObject 203C86 true
        46 NullObject 203C86 true
        47 NullObject 203C86 true
        48 NullObject 203C86 true
        49 NullObject 203C86 true
        50 NullObject 203C86 true
        51 NullObject 203C86 true
        52 NullObject 203C86 true
        53 SwapPlatformObject 20FB3E false
        54 NullObject 203C86 true
        55 NullObject 203C86 true
        56 SearchlightObject 20FBB8 false
        57 NullObject 203C86 true
        58 ResultsObject 20CCCC false
        59 GameOverObject 20CA2A false
        60 TitleCardObject 20CB46 false
        """); }
    private static List<LayoutSlot> layoutsR72B() { return layouts("""
        0 72 48 8
        1 458 24 3
        2 532 1 1
        3 536 33 6
        4 1030 1 1
        5 736 1 1
        6 740 48 6
        7 1030 1 1
        8 1030 1 1
        9 1034 1 1
        10 1034 1 1
        11 1034 1 1
        12 72 48 8
        13 458 24 3
        14 532 1 1
        15 536 33 6
        16 1030 1 1
        17 736 1 1
        18 740 48 6
        19 1030 1 1
        20 1030 1 1
        21 1034 1 1
        22 1034 1 1
        23 1034 1 1
        24 72 48 8
        25 458 24 3
        26 532 1 1
        27 536 33 6
        28 1030 1 1
        29 736 1 1
        30 740 48 6
        31 1030 1 1
        32 1030 1 1
        33 1034 1 1
        34 1034 1 1
        35 1034 1 1
        """); }
    private static List<PaletteRecord> palettesR72B() { return palettes("""
        0 2098666 64256 31 128 false
        1 2098666 64256 31 128 false
        2 2098666 64256 31 128 false
        3 2098666 64256 7 32 false
        4 2098698 64288 23 96 false
        5 2098794 64288 23 96 false
        """); }
    private static List<Range> rangesR72C() { return ranges("""
        PlayerAnims 5778 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66BA 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66C0 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 98BE 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D92 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9DAE 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9DFC 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9E0A 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9E5A 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9ED2 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9ED8 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims A35C 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A364 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A3A8 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A44A 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AA6E 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AAF6 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites AD0C 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites AE0E 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BA72 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BA7E 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BC1E 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites BDEA 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims BF2C 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites BF3A 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C9F6 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites CA02 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims CA98 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites CAA0 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites CB9A 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites D030 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites D2DE 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D61E 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D746 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D78A 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D7CE 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D8C2 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims DC6E 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites E134 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E7AE 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E7E0 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E812 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E85E 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E8AA 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E8B2 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E940 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites EA88 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites EC54 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites EC88 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 EF2A 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 EF6C 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EFA8 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 EFBC 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims F272 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites F2B6 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims F4EC 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F4F4 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 FA1A 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 FA44 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites FA74 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites FBE0 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites FDFC 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 57856 3f3009662bcc68945aeb5ecd7c1332d64c4a58311549be7e99bb9b1756cfec7e
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 800 80cd5068305a430b98277fbe16d2bb2a4e5ace2d8b7875dbc6b305617b8679cb
        StageMapFg 3680C 386 05679b491f37cae1adc5d23ddb92037ce2b326616da0ff51d1b1ef6efff65cb1
        StageMapBg 3698E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        StageMapNull 36996 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 3699A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36A62 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36A66 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36B88 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36B8C 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36B90 4146 ff64a15ac182d55a97424b9439c78e55d93644546672ca8911665589dde5a71e
        StageGfx 37BC2 13100 76b0744f3fb1a3eabc78d159f8606d2371afbc5d812d43708e9e9a210f65d615
        PowerupAnims 3AEEE 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3B016 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B128 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B140 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B1A4 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B1B2 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B1CE 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B1D6 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B1EE 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B202 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B294 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3B394 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3B39A 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3B3DC 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3B3E4 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3B494 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3B4A2 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3B520 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3B554 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3B626 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3B770 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B842 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B914 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B9E6 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3BB96 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3BD42 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C142 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C2F4 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C440 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CADC 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3CB82 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3CC9C 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3CDC8 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3CEF4 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3D004 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D0C4 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3D59E 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData FE9C 16 df5c3fe4ef3e56f694df1aeb1c1b9fb6b887966940e86b400b8fb146b31251e5
        LayoutDirectory 367C4 72 e9be20612e2daa4056cb092fe44642600b76d407ece4f33a40b7da56f4ada6e4
        LayoutRead0 3680C 386 05679b491f37cae1adc5d23ddb92037ce2b326616da0ff51d1b1ef6efff65cb1
        LayoutRead1 3698E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead2 36996 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 3699A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36A62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36A66 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 3680C 386 05679b491f37cae1adc5d23ddb92037ce2b326616da0ff51d1b1ef6efff65cb1
        LayoutRead13 3698E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead14 36996 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 3699A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36A62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36A66 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 3680C 386 05679b491f37cae1adc5d23ddb92037ce2b326616da0ff51d1b1ef6efff65cb1
        LayoutRead25 3698E 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead26 36996 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 3699A 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36A62 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36A66 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36B88 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36B8C 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 79A6 6602 7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58
        PlacementRead 79B2 6584 3b858cff8a8d900fc105741bd3823eaa20c1243f3419fc88ead83f7699dc7513
        Dispatch 3C22 240 198acc75a928db36ba8b88bcdec8901a34356173ea50a483e470889539ca053a
        PaletteDirectory 5E0 56 1991476ddcf139a07f78b4c0e9b4646be5e60e61a2115e477b880661b7a0891d
        PaletteSource 5E0 420 44fe751e32feb512056c27099e59bc5cfe425363935d87c6773acf7af2e82a3b
        PaletteRead0 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead1 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead2 698 128 fb101fd59b4ef4ca8cd77b44164d465cf43cb82893464c2dd2838f056e28d321
        PaletteRead3 698 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B8 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead5 718 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead6 778 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory FEAC 38 2ebf82bfb66cb2158ace42264fe91d5f70796c764d93e100b2e996cf958e0bba
        PlcRead FEAC 222 8247be04095965586d3362e188851deda307c7e2f78c3b7cdf05cc1e3ea7d725
        PlcBody0 FED2 8 ace33949745b26c9e53ab96bbf00b37b4d1d8335fc078d353bb2218e9eb9e771
        PlcBody1 FEDA 74 020de5f37b9cc26ce48dd374e886bf4ac5d3f6e0c23582d826eb1c909affd5c8
        PlcBody2 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody3 FED2 8 ace33949745b26c9e53ab96bbf00b37b4d1d8335fc078d353bb2218e9eb9e771
        PlcBody4 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody5 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody6 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody7 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody8 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody9 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody10 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody11 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody12 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody13 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody14 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody15 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody16 FF6E 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FF24 74 8c54ab5a0363a0390e4fa679fe85b0d76613993e4c831f4ae418aa3476c2d078
        PlcBody18 FF76 20 65ce31aad4b916f88b65e2fb4999638d0689ef78d1a1331390c51ecb0652e757
        Scalar0 214 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        Scalar1 21C 12 13c4c7dd38ca13a627957d45386cd20d3d97ffef8ca0524be85135323b7d4841
        Scalar2 228 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        Scalar3 230 12 6bbc11d6fddf8d1a60ae8acfcfd0112ed3cf553ae2323a264e5e5374e46c85a9
        Scalar4 23C 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        Scalar5 244 12 028b02d3899a85ca544bf634db1f27ff7525107752ca24be163d79d7d0e25b3e
        Scalar6 250 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        Scalar7 308 16 2e40e700e7c1e0964d2d39fadb0dcf78a2646eca2f9e7374c2e8e18af62739b0
        Scalar13 7E2 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar14 AC8 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar16 1AF8 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar17 21B6 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar18 28B2 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar19 2930 4 30231c88bbed9bba14b24e4d04354bb9058797bd5867295cbbc585b2f27fbadb
        Scalar20 2934 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar23 3370 66 efbb03b7a7f6fd3c29391d4d0281e1830a85caadd831c3f04716faca4107a42e
        Scalar25 44EE 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar26 5728 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar32 66B6 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar33 6E60 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar34 707E 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar37 9F00 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar39 B14C 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar40 B2A4 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar41 B2A8 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar43 B2B0 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar44 B2B4 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar45 B2B8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar46 B2BC 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar49 B2B8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar50 B494 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar54 BA52 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar55 C232 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar56 C248 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar57 C252 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar58 C25C 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar59 C266 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar60 C28A 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar61 C2AE 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar62 C4BE 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar63 C4C8 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar64 C4D2 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar65 C4DC 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar66 C4E6 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar68 C4FA 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar69 C504 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar71 C518 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar72 C522 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar73 C52C 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar74 C64E 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar75 C672 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar76 C808 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar77 C81C 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar79 C830 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar80 D018 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar81 D29E 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar82 D47C 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar83 D9AC 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar84 E8CA 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar85 E934 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar86 EA96 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar87 EAC6 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar88 EFE0 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar89 F2FA 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar90 F732 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar91 F772 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar92 FCDA 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar93 FD74 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar95 FDC0 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5728 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 303A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33B2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR72C() { return operands("""
        5488 43f900205778
        66AA 43f9002066ba
        6644 217c002066c0
        9882 217c002098be
        9BA6 43f900209dfc
        9C4E 43f900209dfc
        9CB8 43f900209dfc
        9D76 43f900209dfc
        C32C 43f900209dfc
        99DC 217c00209e0a
        9AA6 217c00209e0a
        9B10 217c00209e5a
        99B8 43f900209ed2
        9910 217c00209ed8
        A164 43f90020a35c
        A34A 43f90020a35c
        A034 237c0020a364
        A228 237c0020a364
        A67C 43f90020aa6e
        A7FC 43f90020aa6e
        A524 217c0020aaf6
        A59C 217c0020aaf6
        A750 217c0020aaf6
        A8D6 227c0020aaf6
        ACE0 217c0020ad0c
        AD7C 217c0020ae0e
        B5C8 43f90020ba72
        B5C0 217c0020ba7e
        BDC8 217c0020bdea
        BEF4 43f90020bf2c
        BE52 217c0020bf3a
        C188 43f90020c9f6
        C08C 217c0020ca02
        C12C 237c0020ca02
        C882 217c0020ca02
        CA86 43f90020ca98
        CA7E 217c0020caa0
        CAEE 217c0020cb9a
        CE04 237c0020d030
        CC16 217c0020d2de
        CC46 237c0020d2de
        D4CC 217c0020d61e
        D688 45fa00bc
        D6A0 45fa00e8
        D684 43fa0148
        D69C 43fa0224
        D9E4 43fa0288
        DC58 43fa0014
        DAEC 237c0020e134
        E3E4 45fa03c8
        E3F4 45fa03ea
        E3E0 43fa0430
        E3F0 43fa046c
        E776 43fa0132
        E76E 217c0020e8b2
        E8F6 217c0020e940
        E9F0 217c0020ea88
        EAF0 217c0020ec54
        EB9E 217c0020ec88
        EDAE 43fa017a
        EDF6 43fa0132
        EEE8 43fa0082
        ED48 217c0020efa8
        EE50 217c0020efbc
        F262 43fa000e
        F080 217c0020f2b6
        F238 217c0020f2b6
        F440 43fa00aa
        F38A 217c0020f4f4
        F806 45fa0212
        F814 45fa022e
        F802 43fa0270
        FB98 217c0020fbe0
        FC16 217c0020fdfc
        F58 223c00210000
        3EF4 223c00210000
        5A00 43f900220000
        3E44 217c0022df20
        7266 217c0022df20
        59D0 45f90022e8ac
        1B36 43f900232f3c
        B116 43f9002330bc
        B178 43f9002330bc
        B1F4 43f9002330bc
        B264 43f9002330bc
        B30A 43f9002330bc
        5F9C 43f9002336bc
        5F14 217c002336d8
        1156 45f9002343a4
        1216 45f9002343a4
        12C6 45f9002343a4
        1376 45f9002343a4
        1184 45f9002344a4
        1244 45f9002344a4
        13E2 43f9002344a4
        13E8 45f9002344a4
        141C 43f9002344a4
        142A 43f9002344a4
        1430 45f9002344a4
        12F4 45f9002354a4
        13A4 45f9002354a4
        1422 45f9002354a4
        5E02 43f90023aeee
        626E 43f90023aeee
        6312 43f90023aeee
        5DCA 217c0023b016
        61EE 217c0023b016
        63E6 217c0023b016
        61BE 43f90023b128
        6192 217c0023b140
        611A 43f90023b1a4
        60D4 217c0023b1b2
        6048 43f90023b1ce
        6026 217c0023b1d6
        5EA8 43f90023b1ee
        5E7A 217c0023b202
        5D92 43f90023b394
        5C52 217c0023b39a
        5CAE 237c0023b39a
        B6C6 43f90023b3dc
        B642 217c0023b3e4
        B950 43f90023b494
        B780 217c0023b4a2
        B8EC 217c0023b4a2
        B3C4 43f90023b520
        B51E 43f90023b520
        B3A4 217c0023b554
        B4E0 237c0023b554
        380C d040d04043f900203c22227100fc
        18E 43f900200214
        194 45f90020021c
        19E 43f900200228
        1A4 45f900200230
        1AE 43f90020023c
        1B4 45f900200244
        1BE 43f900200250
        1C4 45f900200308
        7DC 303b0004
        A90 103b4036
        1AEE 103b1008
        2146 45f9002021b6
        2870 41f9002028b2
        28DE 43f900202930
        2924 43f900202934
        2FA4 41f900203370
        334A 41f900203370
        44AE 45f9002044ee
        5722 103b0004
        669C 303b0018
        6E04 303b005a
        6CEA 45f90020707e
        9F7E 43f900209f00
        B0F0 45fa005a
        B1EA 45f90020b2a4
        B16E 45f90020b2a8
        AF44 45f90020b2b0
        B1E0 45f90020b2b0
        B25A 45f90020b2b4
        B300 45f90020b2b4
        B2EC 45f90020b2b8
        B2F6 45f90020b2b8
        B2CE 45f90020b2bc
        B442 45fb0050
        BA40 43f90020ba52
        C1F4 103b003c
        C1A4 45f90020c248
        C1B6 45f90020c252
        C1CA 45f90020c25c
        C1AA 47f90020c266
        C1BC 47f90020c28a
        C1D0 47f90020c2ae
        C378 45f90020c4be
        C3B0 45f90020c4be
        C37E 47f90020c4c8
        C3B6 47f90020c4d2
        C390 47f90020c4dc
        C3C8 47f90020c4e6
        C3BC 49f90020c4fa
        C396 49f90020c504
        C3A2 49f90020c518
        C3DA 49f90020c522
        C408 49f90020c52c
        C61A 47f90020c64e
        C62C 47f90020c672
        C76E 47f90020c808
        C3A8 4bf90020c81c
        C5F2 43f90020c830
        CDA8 45f90020d018
        CC28 45f90020d29e
        D560 43faff1a
        D724 43fa0286
        E232 43f90020e8ca
        E90A 43fb0028
        EA12 43fa0082
        E9FE 43fa00c6
        EF0C 43fa00d2
        F088 217c0020f2fa
        F392 217c0020f732
        F36E 43fa0402
        FCCC 45fa000c
        FD66 45fa000c
        FDB2 45fa000c
        2FD4 267b0064
        305E 267b00da
        33CA 267b00e6
        100 4ef90020011e
        10C 4ef90020208e
        112 4ef900201c04
        """); }
    private static List<Dispatch> dispatchR72C() { return dispatch("""
        1 PlayerObject 203D80 false
        2 PlayerObject 203D80 false
        3 PowerupObject 2061D4 false
        4 MeshObject 20BD9E false
        5 NullObject 203D12 true
        6 TestObject 205D9E false
        7 NullObject 203D12 true
        8 BoosterObject 20C850 false
        9 SparksObject 20CA54 false
        10 SpringObject 209A18 false
        11 HDoorSplashObject 20616E false
        12 HDoorSplashSetObject 206136 false
        13 HDoorObject 20605E false
        14 TunnelSplashObject 206008 false
        15 MoveSpringObject 2098CC false
        16 RingObject 209EE8 false
        17 LostRingObject 20A1CC false
        18 NullObject 203D12 true
        19 CheckpointObject 205C2C false
        20 BigRingObject 20B5DA false
        21 CapsuleObject 20B35C false
        22 GoalObject 20B712 false
        23 SignpostObject 20B896 false
        24 ExplosionObject 205E44 false
        25 MonitorTimeObject 20A70A false
        26 MonitorItemObject 20A886 false
        27 BoulderObject 209854 false
        28 HudPointsObject 20AD5E false
        29 PathSwapObject 20BE02 false
        30 SpringLauncherObject 20BFE4 false
        31 FlowerObject 205ED2 false
        32 SwingObject 20D408 false
        33 KanabunObject 20D63C false
        34 KabasiraObject 20D9B0 false
        35 HotaruObject 20E1E0 false
        36 BlockObject 20E8DA false
        37 SpikeBallObject 20E99E false
        38 JumpSpikeBallObject 20EACC false
        39 ProjectorObject 20EFF6 false
        40 AnimalObject 20ED10 false
        41 RobotGeneratorObject 20F320 false
        42 DangoObject 20F788 false
        43 RollTunnelObject 2063B0 false
        44 NullObject 203D12 true
        45 NullObject 203D12 true
        46 NullObject 203D12 true
        47 NullObject 203D12 true
        48 NullObject 203D12 true
        49 NullObject 203D12 true
        50 NullObject 203D12 true
        51 NullObject 203D12 true
        52 NullObject 203D12 true
        53 SwapPlatformObject 20FB76 false
        54 NullObject 203D12 true
        55 NullObject 203D12 true
        56 SearchlightObject 20FBF0 false
        57 NullObject 203D12 true
        58 ResultsObject 20CD58 false
        59 GameOverObject 20CAB6 false
        60 TitleCardObject 20CBD2 false
        """); }
    private static List<LayoutSlot> layoutsR72C() { return layouts("""
        0 72 48 8
        1 458 2 3
        2 466 1 1
        3 470 33 6
        4 964 1 1
        5 670 1 1
        6 674 48 6
        7 964 1 1
        8 964 1 1
        9 968 1 1
        10 968 1 1
        11 968 1 1
        12 72 48 8
        13 458 2 3
        14 466 1 1
        15 470 33 6
        16 964 1 1
        17 670 1 1
        18 674 48 6
        19 964 1 1
        20 964 1 1
        21 968 1 1
        22 968 1 1
        23 968 1 1
        24 72 48 8
        25 458 2 3
        26 466 1 1
        27 470 33 6
        28 964 1 1
        29 670 1 1
        30 674 48 6
        31 964 1 1
        32 964 1 1
        33 968 1 1
        34 968 1 1
        35 968 1 1
        """); }
    private static List<PaletteRecord> palettesR72C() { return palettes("""
        0 2098712 64256 31 128 false
        1 2098712 64256 31 128 false
        2 2098840 64256 31 128 false
        3 2098840 64256 7 32 false
        4 2098872 64288 23 96 false
        5 2098968 64288 23 96 false
        6 2099064 64288 2 12 false
        """); }
    private static List<Range> rangesR72D() { return ranges("""
        PlayerAnims 5768 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66AA 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66B0 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 98AE 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 9D82 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 9D9E 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 9DEC 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 9DFA 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 9E4A 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 9EC2 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 9EC8 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims A34C 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites A354 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites A398 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites A43A 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims AA5E 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites AAE6 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites ACFC 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites ADFE 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims BA62 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites BA6E 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx BC0E 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites BDDA 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims BF1C 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites BF2A 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims C9E6 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites C9F2 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims CA88 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites CA90 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites CB8A 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites D020 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites D2CE 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites D60E 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        KanabunAnims1 D736 68 85f3a636f39a2d3959dc8e0e984ffdcf01effc364f3d3c4efc0a5341e4991e63
        KanabunAnims2 D77A 68 3a1d80b1579e71eda9eaa823be51fe1038d76b3eff63bb30c3144b12aeb16b0e
        KanabunSprites1 D7BE 244 99f1f49a57ae1508cd46675f4e828b1a2c334059462d4db894b887a4a536815e
        KanabunSprites2 D8B2 234 908a86721d0d443d02ed79c6bfea2058d93842ad82afc5476afd09bf12ecd0e9
        KabasiraAnims DC5E 1222 5540c3df3e9b7f3691874f1d67fe4cd40688f6a7e190813a3b947ec148de2fb7
        KabasiraSprites E124 172 b114beb898d84969f59658cb563e776e1728eed3b4b8c35b134b035c1cde6f4a
        HotaruAnims1 E79E 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 E7D0 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 E802 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 E84E 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims E89A 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites E8A2 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites E930 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites EA78 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites EC44 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites EC78 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 EF1A 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 EF5C 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 EF98 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 EFAC 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims F262 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites F2A6 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims F4DC 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites F4E4 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        DangoAnims1 FA0A 41 23feaa4e99cb6ccdd2dfe2fc66c4a7b538c7fcd5ee268c443a28f9eb73df9332
        DangoAnims2 FA34 47 87e9e47fc8d176f7dc4ca1ca8639778bb2486eefa5d6e3aa0b421bcc0f3b650a
        DangoSprites FA64 258 bbe3ed3894c7c9b20ee71a1e11e619f570bc56abe2a57aff38598d648599e474
        SwapPlatformSprites FBD0 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        SearchlightSprites FDEC 153 fe57030efe6071be0734e5f31d75a7fa6c791ec9248f6bb0e7043dafa072118a
        StageChunks 10000 54784 22e1c827f482d5fb27cd2b156f6667d4034cb9e171a9a46c913ea1f9ffa99b68
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 1024 e75fd683690acd0572b309813c17c6970325e25a7c37503888335785fa068201
        StageMapFg 368EC 386 398abf1a4da91a8bad05ec5ba3d637a348dbbf6125f3eb37eb31c88ba77e4257
        StageMapBg 36A6E 74 6e34c1df51737309613e0023534f9dffe90588fa83a22c89ee6272e1612fca1d
        StageMapNull 36AB8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36ABC 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B84 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B88 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36CAA 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36CAE 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36CB2 5900 46dfa016ede49fa7a547bb7ee5ccae92d9994a08c655c3ca4a944298045f68e1
        StageGfx 383BE 12870 54e7c4f1b60e7048aef57d59f7600bbc0ace59a2e63a84dc58c86f2db1a9ffd6
        PowerupAnims 3B604 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 3B72C 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 3B83E 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 3B856 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 3B8BA 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 3B8C8 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 3B8E4 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 3B8EC 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 3B904 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 3B918 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 3B9AA 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 3BAAA 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 3BAB0 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 3BAF2 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 3BAFA 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 3BBAA 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 3BBB8 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 3BC36 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 3BC6A 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        SwingGfx 3BD3C 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3BE86 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3BF58 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3C02A 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3C0FC 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3C2AC 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3C458 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C858 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3CA0A 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3CB56 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3D1F2 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3D298 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3D3B2 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3D4DE 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3D60A 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3D71A 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D7DA 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3DCB4 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        StageData FE8C 16 ffa6038398ec6016a31e1231a13d4a412a1bafd76e48f7e45f1ef70981baf930
        LayoutDirectory 368A4 72 f96813226a5082cf5850bc1b5407276c2aed6c0954244285027150dd65e2788a
        LayoutRead0 368EC 386 398abf1a4da91a8bad05ec5ba3d637a348dbbf6125f3eb37eb31c88ba77e4257
        LayoutRead1 36A6E 74 6e34c1df51737309613e0023534f9dffe90588fa83a22c89ee6272e1612fca1d
        LayoutRead2 36AB8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36ABC 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B84 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B88 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 368EC 386 398abf1a4da91a8bad05ec5ba3d637a348dbbf6125f3eb37eb31c88ba77e4257
        LayoutRead13 36A6E 74 6e34c1df51737309613e0023534f9dffe90588fa83a22c89ee6272e1612fca1d
        LayoutRead14 36AB8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36ABC 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B84 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B88 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 368EC 386 398abf1a4da91a8bad05ec5ba3d637a348dbbf6125f3eb37eb31c88ba77e4257
        LayoutRead25 36A6E 74 6e34c1df51737309613e0023534f9dffe90588fa83a22c89ee6272e1612fca1d
        LayoutRead26 36AB8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36ABC 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B84 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B88 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36CAA 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36CAE 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 7996 6602 7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58
        PlacementRead 79A2 6584 3b858cff8a8d900fc105741bd3823eaa20c1243f3419fc88ead83f7699dc7513
        Dispatch 3C12 240 0832e13acaeb106924b1e105b5be0853d611e52f33bd2ac2b3f643199e55c6b4
        PaletteDirectory 5BE 56 bcf9e87720156264ec6edeaa95f6bd58acbb3ec4d21ac1023357be07e10f0f9b
        PaletteSource 5BE 452 10bf5854b6fe13dc2cbf0f8d40df8ef5decbfabe5682eaa537e52e70f37bab96
        PaletteRead0 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead1 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead2 676 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        PaletteRead3 6F6 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 716 96 03e3186c8a2f467964a42e666de16d50a7229a3f7cd59b9899f5590bae03df9e
        PaletteRead6 776 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory FE9C 38 2ebf82bfb66cb2158ace42264fe91d5f70796c764d93e100b2e996cf958e0bba
        PlcRead FE9C 222 da33020636d822305f39d64c4c5c2676494c0361e1db56e02e678597f1d3fc2f
        PlcBody0 FEC2 8 15f42d6741c506e3859d166ad442246d25162d185fc77a11d56740d6af568282
        PlcBody1 FECA 74 763610d488f9a0a3090fb1515550aba949577c3d7029cf0801cf2ec20f7ade09
        PlcBody2 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody3 FEC2 8 15f42d6741c506e3859d166ad442246d25162d185fc77a11d56740d6af568282
        PlcBody4 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody5 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody6 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody7 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody8 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody9 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody10 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody11 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody12 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody13 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody14 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody15 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody16 FF5E 8 835c41a338670876fd688be0977e74c1f2ae3f2435c5bc98e705db6e220dfc0c
        PlcBody17 FF14 74 b0280e32c7a2e2e331615c5fd0976cf1a361fdff04a39125cef7130d1383a90c
        PlcBody18 FF66 20 376de429c6d35400d604857f3beb374872b8688e6e71c01a003dce53464ff70c
        Scalar0 240 14 947d0b337eb8fc0560bcbf7530f8b5461683f8ef96253688cf56226889ba4e2c
        Scalar1 24E 8 049017fae74bc2e9ad9d02bfe4067634e06bea27aa22a4df876f7aa82199c1b4
        Scalar2 256 8 19b544ee8839682fdad6f1d8f421de168409fb8518e299367bf30955c51b85ee
        Scalar3 25E 12 38daf54631d5f9c56e5880bd31b63b7ac05a68fecf88789b007711a7fadcd2aa
        Scalar4 26A 8 7102e083920b5b502a5bc20b9d5781c381c188f248be01f10a63102fbebcd2ac
        Scalar5 272 12 20c7beffe9b58d212b53b5195f1b5dfd360cd5b40ba47d3cf7b69af84b020498
        Scalar6 27E 8 afdb4c55435ef8df560dc4ec53305def3d00918e6478edc7ba7c32ebfb5e041d
        Scalar7 286 12 5540e51c03435ca733a7c43b4b80043e619447b912d81770b534c02cc72513ef
        Scalar10 2A0 18 efc43adfb3b595f795ee35048f6a1c3c28c5bf36a312cd6da6f904115c32e62b
        Scalar11 2B2 16 04466bf078f1a43a19c9bade54d7bd45b041d4798caea2540f9c9ed3cba57506
        Scalar12 2C2 18 47e68c4223482c2d074e7982c47417793b48e5d3f780a759f43401e4268c17dd
        Scalar13 2D4 16 9281caecc477e3fb891f3a8ab7cdccc7a76afaded4a439b1d7483f8c5eb0f0fc
        Scalar14 2E4 18 a1bf6df4fff3f0efa008631967aecc5ee79823e7f461501d4e0c6676abf41219
        Scalar20 7E0 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar21 AC6 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar23 1AF6 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar24 21B4 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar25 28B0 12 40166806df096c6d86df7cd257f658b6ab3e631566ec3068f0d3b1b49cb7dfc1
        Scalar26 292E 4 30231c88bbed9bba14b24e4d04354bb9058797bd5867295cbbc585b2f27fbadb
        Scalar27 2932 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar30 3360 66 df04f0dccd553d06793b2bd814693c27e3a1a950e3ff5ff44b2b8833a24a8944
        Scalar32 44DE 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar33 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar39 66A6 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar40 6E50 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar41 706E 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar44 9EF0 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar46 B13C 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar47 B294 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar48 B298 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar50 B2A0 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar51 B2A4 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar52 B2A8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar53 B2AC 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar56 B2A8 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar57 B484 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar61 BA42 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar62 C222 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar63 C238 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar64 C242 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar65 C24C 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar66 C256 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar67 C27A 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar68 C29E 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar69 C4AE 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar70 C4B8 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar71 C4C2 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar72 C4CC 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar73 C4D6 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar75 C4EA 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar76 C4F4 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar78 C508 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar79 C512 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar80 C51C 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar81 C63E 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar82 C662 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar83 C7F8 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar84 C80C 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar86 C820 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar87 D008 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar88 D28E 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar89 D46C 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar90 D99C 4 60117fd9a325c0dfcd7f1361485a46cfd9ae216d84e6190716ecc99e093ce899
        Scalar91 E8BA 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar92 E924 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar93 EA86 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar94 EAB6 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar95 EFD0 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar96 F2EA 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar97 F722 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar98 F762 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar99 FCCA 44 fa29d1c7fd9429eeffe9cdf5a8c59ba8d6c74892fbccc082b32221f1f51ecffd
        Scalar100 FD64 32 ce8337818c9dbb107adcdfba4658d01db6d0e105daaf53a5121158dcd065f53a
        Scalar102 FDB0 16 e4cf66785279797a9a1f3f8a6047ff959444f48d51e67862100e2b8f87841c4b
        MiniAnimations 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 302A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33A2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR72D() { return operands("""
        5478 43f900205768
        669A 43f9002066aa
        6634 217c002066b0
        9872 217c002098ae
        9B96 43f900209dec
        9C3E 43f900209dec
        9CA8 43f900209dec
        9D66 43f900209dec
        C31C 43f900209dec
        99CC 217c00209dfa
        9A96 217c00209dfa
        9B00 217c00209e4a
        99A8 43f900209ec2
        9900 217c00209ec8
        A154 43f90020a34c
        A33A 43f90020a34c
        A024 237c0020a354
        A218 237c0020a354
        A66C 43f90020aa5e
        A7EC 43f90020aa5e
        A514 217c0020aae6
        A58C 217c0020aae6
        A740 217c0020aae6
        A8C6 227c0020aae6
        ACD0 217c0020acfc
        AD6C 217c0020adfe
        B5B8 43f90020ba62
        B5B0 217c0020ba6e
        BDB8 217c0020bdda
        BEE4 43f90020bf1c
        BE42 217c0020bf2a
        C178 43f90020c9e6
        C07C 217c0020c9f2
        C11C 237c0020c9f2
        C872 217c0020c9f2
        CA76 43f90020ca88
        CA6E 217c0020ca90
        CADE 217c0020cb8a
        CDF4 237c0020d020
        CC06 217c0020d2ce
        CC36 237c0020d2ce
        D4BC 217c0020d60e
        D678 45fa00bc
        D690 45fa00e8
        D674 43fa0148
        D68C 43fa0224
        D9D4 43fa0288
        DC48 43fa0014
        DADC 237c0020e124
        E3D4 45fa03c8
        E3E4 45fa03ea
        E3D0 43fa0430
        E3E0 43fa046c
        E766 43fa0132
        E75E 217c0020e8a2
        E8E6 217c0020e930
        E9E0 217c0020ea78
        EAE0 217c0020ec44
        EB8E 217c0020ec78
        ED9E 43fa017a
        EDE6 43fa0132
        EED8 43fa0082
        ED38 217c0020ef98
        EE40 217c0020efac
        F252 43fa000e
        F070 217c0020f2a6
        F228 217c0020f2a6
        F430 43fa00aa
        F37A 217c0020f4e4
        F7F6 45fa0212
        F804 45fa022e
        F7F2 43fa0270
        FB88 217c0020fbd0
        FC06 217c0020fdec
        F56 223c00210000
        3EE4 223c00210000
        59F0 43f900220000
        3E34 217c0022df20
        7256 217c0022df20
        59C0 45f90022e8ac
        1B34 43f900232f3c
        B106 43f9002330bc
        B168 43f9002330bc
        B1E4 43f9002330bc
        B254 43f9002330bc
        B2FA 43f9002330bc
        5F8C 43f9002336bc
        5F04 217c002336d8
        1154 45f9002343a4
        1214 45f9002343a4
        12C4 45f9002343a4
        1374 45f9002343a4
        1182 45f9002344a4
        1242 45f9002344a4
        13E0 43f9002344a4
        13E6 45f9002344a4
        141A 43f9002344a4
        1428 43f9002344a4
        142E 45f9002344a4
        12F2 45f9002354a4
        13A2 45f9002354a4
        1420 45f9002354a4
        5DF2 43f90023b604
        625E 43f90023b604
        6302 43f90023b604
        5DBA 217c0023b72c
        61DE 217c0023b72c
        63D6 217c0023b72c
        61AE 43f90023b83e
        6182 217c0023b856
        610A 43f90023b8ba
        60C4 217c0023b8c8
        6038 43f90023b8e4
        6016 217c0023b8ec
        5E98 43f90023b904
        5E6A 217c0023b918
        5D82 43f90023baaa
        5C42 217c0023bab0
        5C9E 237c0023bab0
        B6B6 43f90023baf2
        B632 217c0023bafa
        B940 43f90023bbaa
        B770 217c0023bbb8
        B8DC 217c0023bbb8
        B3B4 43f90023bc36
        B50E 43f90023bc36
        B394 217c0023bc6a
        B4D0 237c0023bc6a
        37FC d040d04043f900203c12227100fc
        18C 43f900200240
        192 45f90020024e
        19C 43f900200256
        1A2 45f90020025e
        1AC 43f90020026a
        1B2 45f900200272
        1BC 43f90020027e
        1C2 45f900200286
        1CC 43f9002002a0
        1D2 45f9002002b2
        1DC 43f9002002c2
        1F0 45f9002002d4
        1EA 43f9002002e4
        7DA 303b0004
        A8E 103b4036
        1AEC 103b1008
        2144 45f9002021b4
        286E 41f9002028b0
        28DC 43f90020292e
        2922 43f900202932
        2F94 41f900203360
        333A 41f900203360
        449E 45f9002044de
        5712 103b0004
        668C 303b0018
        6DF4 303b005a
        6CDA 45f90020706e
        9F6E 43f900209ef0
        B0E0 45fa005a
        B1DA 45f90020b294
        B15E 45f90020b298
        AF34 45f90020b2a0
        B1D0 45f90020b2a0
        B24A 45f90020b2a4
        B2F0 45f90020b2a4
        B2DC 45f90020b2a8
        B2E6 45f90020b2a8
        B2BE 45f90020b2ac
        B432 45fb0050
        BA30 43f90020ba42
        C1E4 103b003c
        C194 45f90020c238
        C1A6 45f90020c242
        C1BA 45f90020c24c
        C19A 47f90020c256
        C1AC 47f90020c27a
        C1C0 47f90020c29e
        C368 45f90020c4ae
        C3A0 45f90020c4ae
        C36E 47f90020c4b8
        C3A6 47f90020c4c2
        C380 47f90020c4cc
        C3B8 47f90020c4d6
        C3AC 49f90020c4ea
        C386 49f90020c4f4
        C392 49f90020c508
        C3CA 49f90020c512
        C3F8 49f90020c51c
        C60A 47f90020c63e
        C61C 47f90020c662
        C75E 47f90020c7f8
        C398 4bf90020c80c
        C5E2 43f90020c820
        CD98 45f90020d008
        CC18 45f90020d28e
        D550 43faff1a
        D714 43fa0286
        E222 43f90020e8ba
        E8FA 43fb0028
        EA02 43fa0082
        E9EE 43fa00c6
        EEFC 43fa00d2
        F078 217c0020f2ea
        F382 217c0020f722
        F35E 43fa0402
        FCBC 45fa000c
        FD56 45fa000c
        FDA2 45fa000c
        2FC4 267b0064
        304E 267b00da
        33BA 267b00e6
        100 4ef90020011e
        10C 4ef90020208c
        112 4ef900201c02
        """); }
    private static List<Dispatch> dispatchR72D() { return dispatch("""
        1 PlayerObject 203D70 false
        2 PlayerObject 203D70 false
        3 PowerupObject 2061C4 false
        4 MeshObject 20BD8E false
        5 NullObject 203D02 true
        6 TestObject 205D8E false
        7 NullObject 203D02 true
        8 BoosterObject 20C840 false
        9 SparksObject 20CA44 false
        10 SpringObject 209A08 false
        11 HDoorSplashObject 20615E false
        12 HDoorSplashSetObject 206126 false
        13 HDoorObject 20604E false
        14 TunnelSplashObject 205FF8 false
        15 MoveSpringObject 2098BC false
        16 RingObject 209ED8 false
        17 LostRingObject 20A1BC false
        18 NullObject 203D02 true
        19 CheckpointObject 205C1C false
        20 BigRingObject 20B5CA false
        21 CapsuleObject 20B34C false
        22 GoalObject 20B702 false
        23 SignpostObject 20B886 false
        24 ExplosionObject 205E34 false
        25 MonitorTimeObject 20A6FA false
        26 MonitorItemObject 20A876 false
        27 BoulderObject 209844 false
        28 HudPointsObject 20AD4E false
        29 PathSwapObject 20BDF2 false
        30 SpringLauncherObject 20BFD4 false
        31 FlowerObject 205EC2 false
        32 SwingObject 20D3F8 false
        33 KanabunObject 20D62C false
        34 KabasiraObject 20D9A0 false
        35 HotaruObject 20E1D0 false
        36 BlockObject 20E8CA false
        37 SpikeBallObject 20E98E false
        38 JumpSpikeBallObject 20EABC false
        39 ProjectorObject 20EFE6 false
        40 AnimalObject 20ED00 false
        41 RobotGeneratorObject 20F310 false
        42 DangoObject 20F778 false
        43 RollTunnelObject 2063A0 false
        44 NullObject 203D02 true
        45 NullObject 203D02 true
        46 NullObject 203D02 true
        47 NullObject 203D02 true
        48 NullObject 203D02 true
        49 NullObject 203D02 true
        50 NullObject 203D02 true
        51 NullObject 203D02 true
        52 NullObject 203D02 true
        53 SwapPlatformObject 20FB66 false
        54 NullObject 203D02 true
        55 NullObject 203D02 true
        56 SearchlightObject 20FBE0 false
        57 NullObject 203D02 true
        58 ResultsObject 20CD48 false
        59 GameOverObject 20CAA6 false
        60 TitleCardObject 20CBC2 false
        """); }
    private static List<LayoutSlot> layoutsR72D() { return layouts("""
        0 72 48 8
        1 458 24 3
        2 532 1 1
        3 536 33 6
        4 1030 1 1
        5 736 1 1
        6 740 48 6
        7 1030 1 1
        8 1030 1 1
        9 1034 1 1
        10 1034 1 1
        11 1034 1 1
        12 72 48 8
        13 458 24 3
        14 532 1 1
        15 536 33 6
        16 1030 1 1
        17 736 1 1
        18 740 48 6
        19 1030 1 1
        20 1030 1 1
        21 1034 1 1
        22 1034 1 1
        23 1034 1 1
        24 72 48 8
        25 458 24 3
        26 532 1 1
        27 536 33 6
        28 1030 1 1
        29 736 1 1
        30 740 48 6
        31 1030 1 1
        32 1030 1 1
        33 1034 1 1
        34 1034 1 1
        35 1034 1 1
        """); }
    private static List<PaletteRecord> palettesR72D() { return palettes("""
        0 2098678 64256 31 128 false
        1 2098678 64256 31 128 false
        2 2098806 64256 31 128 false
        3 2098934 64256 7 32 false
        4 2098966 64288 23 96 false
        5 0 0 65535 262144 true
        6 2099062 64288 2 12 false
        """); }
    private static List<Range> rangesR73C() { return ranges("""
        PlayerAnims 5768 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66AA 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66B0 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 801A 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 84EE 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 850A 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 8558 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 8566 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 85B6 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 862E 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 8634 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 8AB8 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 8AC0 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 8B04 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 8BA6 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims 91CA 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites 9252 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites 9468 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites 956A 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims A1CE 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites A1DA 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx A37A 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites A546 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims A688 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites A696 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims B152 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites B15E 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims B1F4 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites B1FC 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites B2F6 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites B78C 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites BA3A 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites BD6C 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        BossLaserImpactAnims CC34 9 97b721710f2454e23b282772c7c0bcbef1c81314023a30df589cb3c03dd3f886
        EggmanAnims D056 6 dd287e022ea374709dcdeb3e134c04fb7327076eca601a08f18ad6f97eb91b5d
        EggmanSprites D05C 91 7c40c83c79ff5a43589ec73b919e2397e66413375fc7bc5513a67bc7f387d8eb
        BossLaserAnims D0B8 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        BossLaserSprites D0BE 16 952c9bfb04c30ff21db65e4768d9b140c4048f2f7a91a8b21d4e9d012842c2db
        BossExhaustAnims D0CE 16 31dfd8ff0aa390f5fcf51485692e2588525729e586a4a831197006ac5078a700
        BossExhaustSprites D0DE 59 20a0515c7310e930c4a6ebddbe2f58f2309ea9db2722dccf3d1bd5b1b013259d
        MetalSonicAnims D11A 94 56a4eb949b3f1b715080eb7022494188fb1a7cba176ce6941c08747d05365b93
        MetalSonicSprites D178 388 ee4ec4fa02db6cf7b43a731301af72836ace4e6c8f9d9a99f606caed84a89197
        ElectricShieldSprites D2FC 47 5d1ea7b3e647f898d345fd704936bc0907a8b90545d2d5246cf67bbcaba792f0
        MetalSonicDebrisSprites D32C 40 2b2d0c2c95f44df8bdaf73834a50835d141c39569a9ce9697ff0276365ddadfc
        DebrisSprites D354 32 437d02a286915d339d67575b7ce6209fa76bf0126c41ea95b83b99819b871a45
        LampSprites D4EC 52 6d859710193687d58327ca68120a710be3846c6ff97ca44394cee51f77149f7e
        HotaruAnims1 DAEE 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 DB20 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 DB52 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 DB9E 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims DBEA 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites DBF2 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites DC80 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites DDC8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites DF94 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites DFC8 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E26A 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E2AC 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E2E8 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 E2FC 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims E5B2 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites E5F6 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims E82C 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites E834 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        BossBlockSprites EC22 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikesSprites EE72 16 d0ef172509b677db2658614e63dbd93212a29c8591922e1e7091d268193281d4
        SwapPlatformSprites F4A4 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        StageChunks 10000 57856 bfa98685a55315f566093ab66670e6c0022004c15cda9625a5133f58480ca57e
        PowerupAnims 1E200 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 1E328 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 1E43A 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 1E452 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 1E4B6 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 1E4C4 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 1E4E0 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 1E4E8 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 1E500 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 1E514 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 1E5A6 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 1E6A6 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 1E6AC 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 1E6EE 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 1E6F6 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 1E7A6 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 1E7B4 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 1E832 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 1E866 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
        AmyRoseGfx 1E938 3192 00b9182c5b66715c9e285c30442e42905e3d832fbf18386bf9dfb5f9373fed0f
        AmyRoseSprites 1F5B0 341 7a8c41552adc845931198da2189dc12225afce02d8f9109045896da480bd862e
        AmyRoseAnims 1F706 69 8c4738996c0756f94420d141bcaf8d22d0098399ab86cbcfdcabc655d917b4d6
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 800 0152eae690f96a47d214aff2ec3d9992fed9da0c8775e4798be4c9b7a97211a9
        StageMapFg 3680C 194 d4f71a8194d8b09abe649ba58b0ec4c49bd6ca065d1926f974fa46ab85ed438e
        StageMapBg 368CE 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        StageMapNull 368D6 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 368DA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 369A2 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 369A6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36AC8 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36ACC 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36AD0 4122 deb195d61ec77cb5365eb2d352c9f20b4377b479781d7774809fa04f38b406eb
        StageGfx 37AEA 13100 76b0744f3fb1a3eabc78d159f8606d2371afbc5d812d43708e9e9a210f65d615
        SwingGfx 3AE16 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3AF60 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B032 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B104 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B1D6 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3B386 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3B532 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3B932 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3BAE4 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3BC30 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3C2CC 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3C372 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3C48C 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC2 3C5B8 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD 3C6E4 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3C7F4 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3C8B4 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3CD8E 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        DangoGfx 3CF62 978 c7b387e9d6fa890cbf5f74f947c4cbdf3384510a89c9f307915484cbd82d5813
        BlockGfxC 3D334 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        LampGfxC 3D460 416 2f67d11648f2b1c8d1f709fdd2993c8cc9a29da1a05eb99d32a38b9cf7ce40bb
        EggmanGfx 3D600 1800 6f38e27fe42313dd14d479a87b7819a486707f87fd2b904c2d2db90574e8c78d
        MetalSonicGfx 3DD08 6456 3515e157fcc1e433ddedc37b259e848b171e7f96e5ab7338ce35400101391a39
        SpikesV4Gfx 3F640 154 bbc5f9665364f85c4068ad62d12d2aec7f78b603bca0704534b92c4f82298a72
        StageData F4BA 16 15416fae8aa8cf5017412df303e402337c3965592b6c463c8b7c1a55863b8f5e
        LayoutDirectory 367C4 72 2acc1436f5076714d2ebb6b5031f8c24b4371daf43bc00588606e67f7aaf5cf7
        LayoutRead0 3680C 194 d4f71a8194d8b09abe649ba58b0ec4c49bd6ca065d1926f974fa46ab85ed438e
        LayoutRead1 368CE 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead2 368D6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 368DA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 369A2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 369A6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 3680C 194 d4f71a8194d8b09abe649ba58b0ec4c49bd6ca065d1926f974fa46ab85ed438e
        LayoutRead13 368CE 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead14 368D6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 368DA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 369A2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 369A6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 3680C 194 d4f71a8194d8b09abe649ba58b0ec4c49bd6ca065d1926f974fa46ab85ed438e
        LayoutRead25 368CE 8 5a03f216547f84d181f79330414e287339c1944115387f9c5826dda358ae68a3
        LayoutRead26 368D6 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 368DA 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 369A2 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 369A6 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36AC8 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36ACC 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 783A 658 f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b
        PlacementRead 7846 640 01b27aef5cec20504d61104784caaed35b1c1afa2f3cdb15fb0b9c9296586985
        Dispatch 3C12 240 492ada73903d524addd8beccdcc767d9d1942fda30c54774f93048773fcd0f12
        PaletteDirectory 5E0 56 1991476ddcf139a07f78b4c0e9b4646be5e60e61a2115e477b880661b7a0891d
        PaletteSource 5E0 420 44fe751e32feb512056c27099e59bc5cfe425363935d87c6773acf7af2e82a3b
        PaletteRead0 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead1 618 128 80f0738da18a6ea96c5ac396a4092e5297cea77559f7dfe82b105b96ef6ad3c5
        PaletteRead2 698 128 fb101fd59b4ef4ca8cd77b44164d465cf43cb82893464c2dd2838f056e28d321
        PaletteRead3 698 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 6B8 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead5 718 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        PaletteRead6 778 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory F4CA 38 5f7aed82d653050d767969ea28cf99050f802157ac0907806860be46273bfefc
        PlcRead F4CA 172 446d32feb03894d78ea8dd8863fd5e6a9a24f7f325c68df114d978ff2e068608
        PlcBody0 F4F0 8 b22a5ee8b52bf44b91b7c6ca56d8c1e641dfb3fd07d4e251c7f0e836c78df894
        PlcBody1 F4F8 80 e62d6823435ffadc4e26c660cb79db92193723b4e3c04c8a553057bc66a9e0b4
        PlcBody2 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody3 F4F0 8 b22a5ee8b52bf44b91b7c6ca56d8c1e641dfb3fd07d4e251c7f0e836c78df894
        PlcBody4 F566 8 9052bae89b0185b2d39002fb48d24350884cf14a25aab36b39d60025a70697aa
        PlcBody5 F56E 8 e57d3b186606e1d0170af71cc291f7e841de9fc5d857426a640439e6e787175a
        PlcBody6 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody7 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody8 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody9 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody10 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody11 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody12 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody13 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody14 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody15 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody16 F556 8 1ffa1fea83cd67fb6975f3db2051f8e4b120b2432d78024ec4185f00a7a01910
        PlcBody17 F548 14 f7ab675d90870dc949bde8fc3e133f77991fde8452027f7289cc19054f556d49
        PlcBody18 F55E 8 2f3cc65aa26fa21612b5762b61784d4808fe72e60957dcd1efbc2c8e83802900
        Scalar0 214 8 f56c0601cfd373a5a6997cb08ea1de7cb3d8926c73ceca606feda64642568a00
        Scalar1 21C 12 13c4c7dd38ca13a627957d45386cd20d3d97ffef8ca0524be85135323b7d4841
        Scalar2 228 8 d489b17d67cdfdb6832376cd8a020a51d84a4a092b0cc84b2fcd95ded7277457
        Scalar3 230 12 6bbc11d6fddf8d1a60ae8acfcfd0112ed3cf553ae2323a264e5e5374e46c85a9
        Scalar4 23C 8 80dd9c691c488eabbf01179c1213fa8b5e724786b368f4bcb04707eed31c72a1
        Scalar5 244 12 028b02d3899a85ca544bf634db1f27ff7525107752ca24be163d79d7d0e25b3e
        Scalar6 250 184 f0d2b7846d39ef6e3ab02be8e9e783b40dcfff747389d1c207e11787d809e61c
        Scalar7 308 16 2e40e700e7c1e0964d2d39fadb0dcf78a2646eca2f9e7374c2e8e18af62739b0
        Scalar10 6B8 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        Scalar11 6B8 96 142c36c9f73fd703a6db6850c2f2c39d23fa9234c18a3282c828a88a155452b5
        Scalar13 7E2 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar14 AC8 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar16 1AF8 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar17 21B6 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar18 28B2 12 a632390af28ab4156f5f07816c62bed6379a5333f9d555abc5f6b9fd666ce684
        Scalar19 2930 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar20 2934 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar23 3360 66 efbb03b7a7f6fd3c29391d4d0281e1830a85caadd831c3f04716faca4107a42e
        Scalar25 44DE 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar26 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar32 66A6 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar33 6E50 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar34 706E 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar37 865C 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar39 98A8 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar40 9A00 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar41 9A04 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar43 9A0C 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar44 9A10 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar45 9A14 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar46 9A18 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar48 C212 4 5085cb99dbe1e374ccd321e5b58182d2c42cf4840c68cfab17b0618a8285e7d6
        Scalar49 9A14 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar50 9BF0 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar54 A1AE 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar55 A98E 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar56 A9A4 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar57 A9AE 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar58 A9B8 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar59 A9C2 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar60 A9E6 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar61 AA0A 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar62 AC1A 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar63 AC24 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar64 AC2E 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar65 AC38 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar66 AC42 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar68 AC56 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar69 AC60 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar71 AC74 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar72 AC7E 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar73 AC88 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar74 ADAA 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar75 ADCE 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar76 AF64 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar77 AF78 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar79 AF8C 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar80 B774 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar81 B9FA 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar82 BBCA 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar84 C8AE 16 15b409d5b4cb298c22668ca2614583dd9f986b71daec05da85fc07462fc28654
        Scalar85 CA38 86 2f57304367c291ca815295da38da0d9288ce5a4517e5d62bfa7b45495ebc6250
        Scalar86 DC0A 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar87 DC74 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar88 DDD6 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar89 DE06 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar90 E320 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar91 E63A 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar92 EA72 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar93 EAB2 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar94 EB32 12 a035341b8f318e83ce1fb91875bd8bd9e1a8ee16a03eace220b1321743e7abe1
        Scalar95 EE20 32 208076d8ca40166188a871fbab99e45dabd868ee08bd7e3266457d74bedcec1b
        MiniAnimations 5718 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 302A 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33A2 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR73C() { return operands("""
        5478 43f900205768
        669A 43f9002066aa
        6634 217c002066b0
        7FDE 217c0020801a
        8302 43f900208558
        83AA 43f900208558
        8414 43f900208558
        84D2 43f900208558
        AA88 43f900208558
        8138 217c00208566
        8202 217c00208566
        826C 217c002085b6
        8114 43f90020862e
        806C 217c00208634
        88C0 43f900208ab8
        8AA6 43f900208ab8
        8790 237c00208ac0
        8984 237c00208ac0
        8DD8 43f9002091ca
        8F58 43f9002091ca
        8C80 217c00209252
        8CF8 217c00209252
        8EAC 217c00209252
        9032 227c00209252
        943C 217c00209468
        94D8 217c0020956a
        9D24 43f90020a1ce
        9D1C 217c0020a1da
        A524 217c0020a546
        A650 43f90020a688
        A5AE 217c0020a696
        A8E4 43f90020b152
        A7E8 217c0020b15e
        A888 237c0020b15e
        AFDE 217c0020b15e
        B1E2 43f90020b1f4
        B1DA 217c0020b1fc
        B24A 217c0020b2f6
        B560 237c0020b78c
        B372 217c0020ba3a
        B3A2 237c0020ba3a
        BC1A 217c0020bd6c
        CC22 43f90020cc34
        C41C 43f90020d056
        C456 217c0020d05c
        CB74 43f90020d0b8
        CAD6 217c0020d0be
        CD6A 43f90020d0ce
        CCFE 217c0020d0de
        BDA8 43f90020d11a
        BDEC 217c0020d178
        C310 217c0020d2fc
        CDBA 217c0020d32c
        CC66 217c0020d354
        D3A0 217c0020d4ec
        D724 45fa03c8
        D734 45fa03ea
        D720 43fa0430
        D730 43fa046c
        DAB6 43fa0132
        DAAE 217c0020dbf2
        DC36 217c0020dc80
        DD30 217c0020ddc8
        DE30 217c0020df94
        DEDE 217c0020dfc8
        E0EE 43fa017a
        E136 43fa0132
        E228 43fa0082
        E088 217c0020e2e8
        E190 217c0020e2fc
        E5A2 43fa000e
        E3C0 217c0020e5f6
        E578 217c0020e5f6
        E780 43fa00aa
        E6CA 217c0020e834
        EAE4 217c0020ec22
        ECAA 217c0020ee72
        EDDC 237c0020ee72
        F45C 217c0020f4a4
        F58 223c00210000
        3EE4 223c00210000
        5DF2 43f90021e200
        625E 43f90021e200
        6302 43f90021e200
        5DBA 217c0021e328
        61DE 217c0021e328
        63D6 217c0021e328
        61AE 43f90021e43a
        6182 217c0021e452
        610A 43f90021e4b6
        60C4 217c0021e4c4
        6038 43f90021e4e0
        6016 217c0021e4e8
        5E98 43f90021e500
        5E6A 217c0021e514
        CB04 217c0021e514
        5D82 43f90021e6a6
        5C42 217c0021e6ac
        5C9E 237c0021e6ac
        9E22 43f90021e6ee
        9D9E 217c0021e6f6
        A0AC 43f90021e7a6
        9EDC 217c0021e7b4
        A048 217c0021e7b4
        9B20 43f90021e832
        9C7A 43f90021e832
        9B00 217c0021e866
        9C3C 237c0021e866
        EED4 217c0021f5b0
        F3C8 217c0021f5b0
        EF62 43f90021f706
        EFAA 43f90021f706
        F098 43f90021f706
        F0AC 43f90021f706
        59F0 43f900220000
        3E34 217c0022df20
        7256 217c0022df20
        59C0 45f90022e8ac
        1B36 43f900232f3c
        9872 43f9002330bc
        98D4 43f9002330bc
        9950 43f9002330bc
        99C0 43f9002330bc
        9A66 43f9002330bc
        5F8C 43f9002336bc
        5F04 217c002336d8
        1156 45f9002343a4
        1216 45f9002343a4
        12C6 45f9002343a4
        1376 45f9002343a4
        1184 45f9002344a4
        1244 45f9002344a4
        13E2 43f9002344a4
        13E8 45f9002344a4
        141C 43f9002344a4
        142A 43f9002344a4
        1430 45f9002344a4
        12F4 45f9002354a4
        13A4 45f9002354a4
        1422 45f9002354a4
        37FC d040d04043f900203c12227100fc
        18E 43f900200214
        194 45f90020021c
        19E 43f900200228
        1A4 45f900200230
        1AE 43f90020023c
        1B4 45f900200244
        1BE 43f900200250
        1C4 45f900200308
        EEA4 47f9002006b8
        7DC 303b0004
        A90 103b4036
        1AEE 103b1008
        2146 45f9002021b6
        2870 41f9002028b2
        28DE 43f900202930
        2924 43f900202934
        2F94 41f900203360
        333A 41f900203360
        449E 45f9002044de
        5712 103b0004
        668C 303b0018
        6DF4 303b005a
        6CDA 45f90020706e
        86DA 43f90020865c
        984C 45fa005a
        9946 45f900209a00
        98CA 45f900209a04
        96A0 45f900209a0c
        993C 45f900209a0c
        99B6 45f900209a10
        9A5C 45f900209a10
        9A48 45f900209a14
        9A52 45f900209a14
        9A2A 45f900209a18
        C208 47f90020c212
        9B9E 45fb0050
        A19C 43f90020a1ae
        A950 103b003c
        A900 45f90020a9a4
        A912 45f90020a9ae
        A926 45f90020a9b8
        A906 47f90020a9c2
        A918 47f90020a9e6
        A92C 47f90020aa0a
        AAD4 45f90020ac1a
        AB0C 45f90020ac1a
        AADA 47f90020ac24
        AB12 47f90020ac2e
        AAEC 47f90020ac38
        AB24 47f90020ac42
        AB18 49f90020ac56
        AAF2 49f90020ac60
        AAFE 49f90020ac74
        AB36 49f90020ac7e
        AB64 49f90020ac88
        AD76 47f90020adaa
        AD88 47f90020adce
        AECA 47f90020af64
        AB04 4bf90020af78
        AD4E 43f90020af8c
        B504 45f90020b774
        B384 45f90020b9fa
        BCAE 43faff1a
        C8A0 47fb000c
        CA0C 45f90020ca38
        D572 43f90020dc0a
        DC4A 43fb0028
        DD52 43fa0082
        DD3E 43fa00c6
        E24C 43fa00d2
        E3C8 217c0020e63a
        E6D2 217c0020ea72
        E6AE 43fa0402
        EAF8 43fb0038
        EBEC 43f90020eb32
        ED9E 45f90020ee20
        2FC4 267b0064
        304E 267b00da
        33BA 267b00e6
        100 4ef90020011e
        10C 4ef90020208e
        112 4ef900201c04
        """); }
    private static List<Dispatch> dispatchR73C() { return dispatch("""
        1 PlayerObject 203D70 false
        2 PlayerObject 203D70 false
        3 PowerupObject 2061C4 false
        4 MeshObject 20A4FA false
        5 NullObject 203D02 true
        6 TestObject 205D8E false
        7 NullObject 203D02 true
        8 NullObject 203D02 true
        9 NullObject 203D02 true
        10 SpringObject 208174 false
        11 HDoorSplashObject 20615E false
        12 HDoorSplashSetObject 206126 false
        13 HDoorObject 20604E false
        14 TunnelSplashObject 205FF8 false
        15 MoveSpringObject 208028 false
        16 RingObject 208644 false
        17 LostRingObject 208928 false
        18 NullObject 203D02 true
        19 NullObject 203D02 true
        20 BigRingObject 209D36 false
        21 CapsuleObject 209AB8 false
        22 GoalObject 209E6E false
        23 SignpostObject 209FF2 false
        24 ExplosionObject 205E34 false
        25 MonitorTimeObject 208E66 false
        26 MonitorItemObject 208FE2 false
        27 BoulderObject 207FB0 false
        28 HudPointsObject 2094BA false
        29 NullObject 203D02 true
        30 NullObject 203D02 true
        31 FlowerObject 205EC2 false
        32 SwingObject 20BB56 false
        33 NullObject 203D02 true
        34 NullObject 203D02 true
        35 HotaruObject 20D520 false
        36 BlockObject 20DC1A false
        37 SpikeBallObject 20DCDE false
        38 JumpSpikeBallObject 20DE0C false
        39 ProjectorObject 20E336 false
        40 AnimalObject 20E050 false
        41 RobotGeneratorObject 20E660 false
        42 NullObject 203D02 true
        43 BossBlockObject 20EAC8 false
        44 EggmanObject 20C402 false
        45 BossExhaustObject 20CCCE false
        46 BossLaserObject 20CA8E false
        47 MetalSonicObject 20BD8A false
        48 ElectricShieldObject 20C2DC false
        49 MetalSonicDebrisObject 20CD84 false
        50 SpikesObject 20EC80 false
        51 AmyRoseObject 20EE82 false
        52 HeartObject 20F39A false
        53 SwapPlatformObject 20F43A false
        54 LampObject 20D374 false
        55 DebrisObject 20CC3E false
        56 NullObject 203D02 true
        57 NullObject 203D02 true
        58 ResultsObject 20B4B4 false
        59 GameOverObject 20B212 false
        60 TitleCardObject 20B32E false
        """); }
    private static List<LayoutSlot> layoutsR73C() { return layouts("""
        0 72 64 3
        1 266 2 3
        2 274 1 1
        3 278 33 6
        4 772 1 1
        5 478 1 1
        6 482 48 6
        7 772 1 1
        8 772 1 1
        9 776 1 1
        10 776 1 1
        11 776 1 1
        12 72 64 3
        13 266 2 3
        14 274 1 1
        15 278 33 6
        16 772 1 1
        17 478 1 1
        18 482 48 6
        19 772 1 1
        20 772 1 1
        21 776 1 1
        22 776 1 1
        23 776 1 1
        24 72 64 3
        25 266 2 3
        26 274 1 1
        27 278 33 6
        28 772 1 1
        29 478 1 1
        30 482 48 6
        31 772 1 1
        32 772 1 1
        33 776 1 1
        34 776 1 1
        35 776 1 1
        """); }
    private static List<PaletteRecord> palettesR73C() { return palettes("""
        0 2098712 64256 31 128 false
        1 2098712 64256 31 128 false
        2 2098840 64256 31 128 false
        3 2098840 64256 7 32 false
        4 2098872 64288 23 96 false
        5 2098968 64288 23 96 false
        6 2099064 64288 2 12 false
        """); }
    private static List<Range> rangesR73D() { return ranges("""
        PlayerAnims 5776 578 96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9
        LightEdgeAnims 66B8 6 8ffed6e5602026af53e307dabd432f01c8a48072b321e234068661f271114870
        LightEdgeSprites 66BE 56 e60fb9e36dbedd1d8cdf966751064d2c4c74628734209fc21b6fdddc9d61ab42
        BoulderSprites 8028 13 dd926b35e5d149b9d44622d28f34dd320667e9d025bf5ae52324a95a9c32576d
        S1SpringAnims 84FC 28 275c35c1a695c30a41384346ee76d8473aeaa85b11d5c056a459f9b3c85fd86b
        S1SpringSprites 8518 78 9ad078d2f5f21827fe573cef8deef4c61ef63bde447c79c00f692e014390be7b
        SpringAnims 8566 14 7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5
        SpringSprites 8574 80 0d880f1d2d465e2c6019fbfef4738f88d5459ca8ab3976670948474c92662e54
        Spring45Sprites 85C4 119 77dede8766ec3c07df8b44ea93938ed040dac090243052d76bed5ec1e818bf61
        MoveSpringAnims 863C 6 e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a
        MoveSpringSprites 8642 16 f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d
        RingAnims 8AC6 8 4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a
        RingSprites 8ACE 67 cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a
        S1BigRingSprites 8B12 162 bfe81994027eafd81aebe338a296c159ad506241b4caf50526781ce8017d4ad0
        S1BigRingFlashSprites 8BB4 164 35e43e5fe77f43548126d43387ac41727bb9d5fb78c4f92dc12c0d1223a25aa5
        MonitorTimeAnims 91D8 136 c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36
        MonitorTimeSprites 9260 450 00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca
        PointsSprites 9476 82 24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e
        HudSprites 9578 168 7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3
        BigRingFlashAnims A1DC 12 b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f
        BigRingFlashSprites A1E8 416 b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10
        BigRingFlashGfx A388 384 6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7
        MeshSprites A554 23 ad494fca498efc886f47c286dcfa705de0dea58a02296a72c1e62592c0e03528
        PathSwapAnims A696 13 70c6440ca8e2baa9b58309cc56e5b88bf907226d2a889d8915eb36d97d78c6f0
        PathSwapSprites A6A4 169 61da8d658dc42f30d1723dc21bda18a2cf323897fe6a0235391cb31f32f1fc0d
        LaunchAnims B160 12 5987eedb396cf8d00521d2e271aa61029b6370b02eec6d98aeb04fd5eec4d1a1
        LaunchSprites B16C 82 0cd370f5e08b9b00b2ff425695208de1d54611309b504225ad810eeb8b7dc520
        SparksAnims B202 8 60bbff28a2556086f1caa69a7089f16f25ce7b08f4db085e1ce5e96b7be93c6a
        SparksSprites B20A 22 3164a842846ef6c661767c99b949249a500aa83e742921a38bf14a606adfe21d
        OverSprites B304 55 ddca29995456e45f486292f4403e7d92acbd493b61781c0ac9f0f6a7ba5b9012
        ResultsSprites B79A 621 f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621
        TitleCardSprites BA48 280 a21743c5acdf5c6682d835547182e4e3c42ca7571251b3cc18837c44d175cc58
        SwingSprites BD7A 30 df47defe26db1e83e930ea156669f6a801fb7c2fbc948454495e0646c47393c1
        BossLaserImpactAnims CC42 9 97b721710f2454e23b282772c7c0bcbef1c81314023a30df589cb3c03dd3f886
        EggmanAnims D064 6 dd287e022ea374709dcdeb3e134c04fb7327076eca601a08f18ad6f97eb91b5d
        EggmanSprites D06A 91 7c40c83c79ff5a43589ec73b919e2397e66413375fc7bc5513a67bc7f387d8eb
        BossLaserAnims D0C6 6 ed90a9433f443591406879736c496eeb2eec9f1b76df28acb06e77a68319d74c
        BossLaserSprites D0CC 16 952c9bfb04c30ff21db65e4768d9b140c4048f2f7a91a8b21d4e9d012842c2db
        BossExhaustAnims D0DC 16 31dfd8ff0aa390f5fcf51485692e2588525729e586a4a831197006ac5078a700
        BossExhaustSprites D0EC 59 20a0515c7310e930c4a6ebddbe2f58f2309ea9db2722dccf3d1bd5b1b013259d
        MetalSonicAnims D128 94 56a4eb949b3f1b715080eb7022494188fb1a7cba176ce6941c08747d05365b93
        MetalSonicSprites D186 388 ee4ec4fa02db6cf7b43a731301af72836ace4e6c8f9d9a99f606caed84a89197
        ElectricShieldSprites D30A 47 5d1ea7b3e647f898d345fd704936bc0907a8b90545d2d5246cf67bbcaba792f0
        MetalSonicDebrisSprites D33A 40 2b2d0c2c95f44df8bdaf73834a50835d141c39569a9ce9697ff0276365ddadfc
        DebrisSprites D362 32 437d02a286915d339d67575b7ce6209fa76bf0126c41ea95b83b99819b871a45
        LampSprites D4FA 52 6d859710193687d58327ca68120a710be3846c6ff97ca44394cee51f77149f7e
        HotaruAnims1 DAFC 50 405a981f2cd7e299d1188c6c7578fae16eec205e721428010a751e4a56e51996
        HotaruAnims2 DB2E 50 8b859688453a2945d30e14e5a886472eda88133358e9204529cbf90ef44e81d5
        HotaruSprites1 DB60 75 f5e9562aaf0df656a1e61c558436336f5493416fcc27fc8e12ba4a0b87ecffd3
        HotaruSprites2 DBAC 75 3d8d0a4b8b2fd01b3e544524095cb26d73adc37f9636548c8fec63959743e12b
        HotaruSparkAnims DBF8 7 a5b66fb34b21b2c0933bfefa53b5945cbca109d7ee6b59305faf3d80dd98face
        HotaruSparkSprites DC00 24 10a3ef4ebc153ba7b95e5d4ff2109fa79089feb660c33bb640c11d01f7cd9add
        BlockSprites DC8E 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikeBallSprites DDD6 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        SpikeLaunchSprites DFA2 52 ff08ec46b817fee8b78519e18d998c551a5cdc9d1d100dac5992367c96baf8b2
        JumpSpikeBallSprites DFD6 13 138dbf6b8ac4d05153552ea4fe0b32da52dee422081edc0be6e3c69c36c96c0b
        AnimalAnims1 E278 66 57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3
        AnimalAnims2 E2BA 60 b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4
        AnimalSprites1 E2F6 19 9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f
        AnimalSprites2 E30A 35 71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2
        HologramAnims E5C0 68 2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f
        HologramSprites E604 68 2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3
        RobotTransportAnims E83A 8 cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e
        RobotTransportSprites E842 574 69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59
        BossBlockSprites EC30 93 011f951d31b5162a8adbf138252fa7582bb04381ad646ba0de1047118f4e0d5a
        SpikesSprites EE80 16 d0ef172509b677db2658614e63dbd93212a29c8591922e1e7091d268193281d4
        SwapPlatformSprites F4B2 16 fe71216b866294698ab97d07768267502159d7aa086c1659ae62402bbbc3a1be
        StageChunks 10000 54784 089c046f2ecdc137aa787a4d1581a2523024296cd3fa45ce046c7ed24fab95bb
        AmyRoseGfx 1D600 3192 00b9182c5b66715c9e285c30442e42905e3d832fbf18386bf9dfb5f9373fed0f
        AmyRoseSprites 1E278 341 7a8c41552adc845931198da2189dc12225afce02d8f9109045896da480bd862e
        AmyRoseAnims 1E3CE 69 8c4738996c0756f94420d141bcaf8d22d0098399ab86cbcfdcabc655d917b4d6
        BlockGfxD 1E414 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        LampGfxD 1E524 386 fdca11fd9cb351649b6be81400b75a11678d2e3bd733f800584abf26c1456930
        PowerupAnims 1E6A6 295 cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61
        PowerupSprites 1E7CE 273 67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e
        SplashAnims 1E8E0 24 c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce
        SplashSprites 1E8F8 100 2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287
        HDoorAnims 1E95C 14 398c620335d02e870c94e8f8144c33e355b9bd1440fe530eff2031ea16600fb0
        HDoorSprites 1E96A 27 f5c79edd83d5990b5bfb562ab004b6837934b71095de7e6edd3ed5a0d8d1c41e
        TunnelSplashAnims 1E986 7 4aa4a59c69aea578839a613a9d7276de435dab4ffebae43ab53dde93eb59952f
        TunnelSplashSprites 1E98E 24 5e79664e09093742143eb278c09e4bc77c8e6d3203f7cabec947c145413bda8a
        ExplosionAnims 1E9A6 19 347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616
        ExplosionSprites 1E9BA 145 8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada
        WobbleTable 1EA4C 256 2dca8f2df5a879ac88fa474f4f87e3aba075d2c604480dbc334c05d8ea9dd267
        CheckpointAnims 1EB4C 6 9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551
        CheckpointSprites 1EB52 65 f86203c517d5cf44a3e3f9d39d18e9c61e745380ca1086c5497db91ec6489682
        BigRingAnims 1EB94 8 b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6
        BigRingSprites 1EB9C 175 3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66
        SignpostAnims 1EC4C 13 744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686
        SignpostSprites 1EC5A 125 36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec
        CapsuleAnims 1ECD8 52 1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9
        CapsuleSprites 1ED0C 210 9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646
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
        FlowerSprites 336D8 90 e22b1410927cf4bd60b1aff283e4ced4db296ae621cef6a9df11d53dd6ec66e2
        FlowerGfx 33732 640 8b224d674a497a2c227c4c45abc319778799e2a5938625ef0e61fc88f00678f7
        TitleCardTextGfx 339B2 368 dc22bdd54c66d2bb8a2d43f242f79dc6f0342f851cac3d8e1f59b3a6d46342fd
        LaunchGfx 33B22 1052 34550a4acb7c853466eae99af56e283f46ff10aab2a892f57d07e6e8dfe00f1e
        SparksGfx 33F3E 354 2f0ba8320e21ed57e095b8076cd2d9935849e1c929ee28fb94f317938e1cdee6
        LightEdgesGfx 340A0 66 4c01e28a7d020279f23e29933ce7ec79da131a6510dd2b10d49fa2a74e797a9b
        PathSwapGfx 340E2 354 c5c4ba5ab5520a705ce415bb99e765a221e0d967db20ae0aeb202709b816cf16
        MeshGfxA 34244 110 1ec3cad9acf45b50c7ac5f2d5ed539799123bc65944b68ffdcb60874a96a2eb2
        MeshGfxB 342B2 84 82467b8379c4cf80b5345afaff4185ffb960f8618152aadf48e6718dcf8ea19e
        MeshGfxC 34306 64 69e18dc77fbd17a79f172afc6dbcbbf2a440107be5a1ee30e24119d94184a5a0
        MeshGfxD 34346 94 d4ac7de6d21b194d897b5ef460693c456bff2e7891a1eefdb43d7a8f429e9377
        StageCollisionAngles 343A4 256 c05a6e177730d577d6743c1740f636565c3c0061cf0526aaaaf46ac4961ae9fd
        StageCollisionColumns 344A4 4096 103655cfc0a8919807a8a0741a0600771fa7b86838d5eb00f4faaadc42996d9d
        StageCollisionRows 354A4 4096 90f660b66816de8018412c97a47ebd151801493dff92ae6a67dea86711530cc4
        StageCollision 364A4 1024 8b8bd91a9c3c4666a89fcdf60a0ba01002250bb31768ce1c4dbae009283d922e
        StageMapFg 368EC 194 41a4116119f6a829b8541e161056a851729b416c73ecc160797e4f0b861096a8
        StageMapBg 369AE 182 e656ee9b866ae38abe01cafdb77896810253232c5ce324bd2e74d42bdec9ca3b
        StageMapNull 36A64 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk1 36A68 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        StageMapUnk3 36B30 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk4 36B34 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        StageMapUnk2 36C56 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageMapUnk5 36C5A 4 df3f619804a92fdb4057192dc43dd748ea778adc52bc498ce80524c014b81119
        StageBlocks 36C5E 5898 abf3c3ea48bff734501c654e41c8feec3acb18b4bd3bb062eff19bc80541dbaa
        StageGfx 38368 12870 54e7c4f1b60e7048aef57d59f7600bbc0ace59a2e63a84dc58c86f2db1a9ffd6
        SwingGfx 3B5AE 330 aa38108df80fb419de75be4cd5f3d508cc3a1377c73ca439967c16a54aab86bf
        LightsGfxA 3B6F8 210 c25987464834f653f0e1cb8a563c302e0d01ce49244dee5e1a3c22fa30af9660
        LightsGfxC 3B7CA 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        LightsGfxD 3B89C 210 cd2bd1fc1e02d865f7c367703a5a3923fb27028d6798178f1b3c720e8fbae50a
        AnimalsGfx 3B96E 432 6aa691aed9cef36d041f0773aadfa92befe9d326c50c3202b20deb431c142bdb
        HologramAnimalsGfx 3BB1E 428 94be4879a80c6dd99651a9b10795821bba964749f60ded504f635369761be904
        HologramGfx 3BCCA 1024 1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5
        RobotTransportGfxB 3C0CA 434 4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806
        RobotTransportGfxA 3C27C 332 26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda
        KanabunGfx 3C3C8 1692 ca9084cf2ad324c88bfa4b4b8ed68868b44e27912d3de15eff1e5781b9fc0c79
        SpikeLaunchGfx 3CA64 166 1b72bb7768e011f3e5a58c4a13926d699d503a04197be6c223a7c63cccb40aa7
        BlockGfxA 3CB0A 282 01e4c9747212a4964dbb46f91cc6ee60243a5e52b718b797aaf86900e8ae15cc
        BlockGfxB 3CC24 300 93a2bd7f2627506963a3f1b57fd0b5cc4e8d5a1ef7b5f77cbc3d9c5173e7d7ec
        BlockGfxC 3CD50 300 05711c0e6bc6fafba32afecd571743a95dda77a03e28ac7f569653e2abdab234
        BlockGfxD2 3CE7C 272 6ae646231775a42d8ca79f6ac0510efc6cf962de2d81e2e71dd7a3f1bcca1717
        SpikeBallGfx 3CF8C 192 5d16c7a9b6463534ead5aad6c876c2babcad9c08ae9f1983eea91a8cbe0ec006
        HotaruGfx 3D04C 1242 ad3f4b3f55b15185d49495748f392ab1e5bb786fc6aaebc97d51c85d08965607
        KabasiraGfx 3D526 468 c661df055191141a2f5cf19ef7bc25e23d66c48776c5d4cddb73591b3ec7adae
        DangoGfx 3D6FA 978 c7b387e9d6fa890cbf5f74f947c4cbdf3384510a89c9f307915484cbd82d5813
        EggmanGfx 3DACC 1800 6f38e27fe42313dd14d479a87b7819a486707f87fd2b904c2d2db90574e8c78d
        MetalSonicGfx 3E1D4 6456 3515e157fcc1e433ddedc37b259e848b171e7f96e5ab7338ce35400101391a39
        SpikesV4Gfx 3FB0C 154 bbc5f9665364f85c4068ad62d12d2aec7f78b603bca0704534b92c4f82298a72
        StageData F4C8 16 92b832728be8b9c9ef465a8a858341ae93d127747c48eef9504074b62e803aca
        LayoutDirectory 368A4 72 c6fe048431705699a3f9878db50238e4c146b5e04fb855b54dbe6651167765d0
        LayoutRead0 368EC 194 41a4116119f6a829b8541e161056a851729b416c73ecc160797e4f0b861096a8
        LayoutRead1 369AE 182 e656ee9b866ae38abe01cafdb77896810253232c5ce324bd2e74d42bdec9ca3b
        LayoutRead2 36A64 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead3 36A68 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead4 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead5 36B30 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead6 36B34 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead7 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead8 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead9 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead10 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead11 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead12 368EC 194 41a4116119f6a829b8541e161056a851729b416c73ecc160797e4f0b861096a8
        LayoutRead13 369AE 182 e656ee9b866ae38abe01cafdb77896810253232c5ce324bd2e74d42bdec9ca3b
        LayoutRead14 36A64 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead15 36A68 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead16 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead17 36B30 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead18 36B34 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead19 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead20 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead21 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead22 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead23 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead24 368EC 194 41a4116119f6a829b8541e161056a851729b416c73ecc160797e4f0b861096a8
        LayoutRead25 369AE 182 e656ee9b866ae38abe01cafdb77896810253232c5ce324bd2e74d42bdec9ca3b
        LayoutRead26 36A64 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead27 36A68 200 e228027497e88fb4f82ff09b84cecc8cc71b390a88694f0a5dafa9c6082ba7cc
        LayoutRead28 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead29 36B30 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead30 36B34 290 085ae897459309bd0918ccc6d9a1d6ad28ca0d153d2c3332bb0f831610600ac2
        LayoutRead31 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead32 36C56 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead33 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead34 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        LayoutRead35 36C5A 3 709e80c88487a2411e1ee4dfb9f22a861492d20c4765150c0c794abd70f8147c
        PlacementLayout 7848 658 f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b
        PlacementRead 7854 640 01b27aef5cec20504d61104784caaed35b1c1afa2f3cdb15fb0b9c9296586985
        Dispatch 3C20 240 7c77a0845f1c23f42443e5274d5b1de12ce3154388919242168c22c6320387be
        PaletteDirectory 5BE 56 bcf9e87720156264ec6edeaa95f6bd58acbb3ec4d21ac1023357be07e10f0f9b
        PaletteSource 5BE 452 10bf5854b6fe13dc2cbf0f8d40df8ef5decbfabe5682eaa537e52e70f37bab96
        PaletteRead0 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead1 5F6 128 b6a7595fb63d9992a270757687be1a0c386d78e849267c1f67b80ea0b68f828b
        PaletteRead2 676 128 809f6fb01f6ee63d1dfdb230c00cc255f7a6585d23e16a48e04cd4b9dc3fb16d
        PaletteRead3 6F6 32 8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a
        PaletteRead4 716 96 03e3186c8a2f467964a42e666de16d50a7229a3f7cd59b9899f5590bae03df9e
        PaletteRead6 776 12 311f13663bf829a3a1e3bab6ab7be5448a5ecb83fb77e8815e966449098f4d70
        PlcDirectory F4D8 38 5f7aed82d653050d767969ea28cf99050f802157ac0907806860be46273bfefc
        PlcRead F4D8 172 f1c12c0c02f7dcad183fc3a7fd7ebd45149837782940b44e5f2c95a31f2a2465
        PlcBody0 F4FE 8 99ef03ddbbf6e6c74a6cd5c3ca5a93fee00745f5081ba08d1792a9c980b95798
        PlcBody1 F506 80 a3aa5e2418a3329ab3699ad43e490003b235eec8c6cc1fd6e69183ba946e52c2
        PlcBody2 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody3 F4FE 8 99ef03ddbbf6e6c74a6cd5c3ca5a93fee00745f5081ba08d1792a9c980b95798
        PlcBody4 F574 8 f46d401638307f47c0967a64bcfa986b0980ee704e6c8469c44e9c173eaebffc
        PlcBody5 F57C 8 7a9bfc42b551083b790de000c7e81ce8601dc0ba9263b80cbe24a2e7cb26d916
        PlcBody6 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody7 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody8 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody9 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody10 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody11 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody12 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody13 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody14 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody15 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody16 F564 8 1ffa1fea83cd67fb6975f3db2051f8e4b120b2432d78024ec4185f00a7a01910
        PlcBody17 F556 14 f2fc40a8dc201ffe3141593bb3ccb31d021c908739b8c1cc1c82036beac9d769
        PlcBody18 F56C 8 2f3cc65aa26fa21612b5762b61784d4808fe72e60957dcd1efbc2c8e83802900
        Scalar0 240 14 947d0b337eb8fc0560bcbf7530f8b5461683f8ef96253688cf56226889ba4e2c
        Scalar1 24E 8 049017fae74bc2e9ad9d02bfe4067634e06bea27aa22a4df876f7aa82199c1b4
        Scalar2 256 8 19b544ee8839682fdad6f1d8f421de168409fb8518e299367bf30955c51b85ee
        Scalar3 25E 12 38daf54631d5f9c56e5880bd31b63b7ac05a68fecf88789b007711a7fadcd2aa
        Scalar4 26A 8 7102e083920b5b502a5bc20b9d5781c381c188f248be01f10a63102fbebcd2ac
        Scalar5 272 12 20c7beffe9b58d212b53b5195f1b5dfd360cd5b40ba47d3cf7b69af84b020498
        Scalar6 27E 8 afdb4c55435ef8df560dc4ec53305def3d00918e6478edc7ba7c32ebfb5e041d
        Scalar7 286 12 5540e51c03435ca733a7c43b4b80043e619447b912d81770b534c02cc72513ef
        Scalar10 2A0 18 efc43adfb3b595f795ee35048f6a1c3c28c5bf36a312cd6da6f904115c32e62b
        Scalar11 2B2 16 04466bf078f1a43a19c9bade54d7bd45b041d4798caea2540f9c9ed3cba57506
        Scalar12 2C2 18 47e68c4223482c2d074e7982c47417793b48e5d3f780a759f43401e4268c17dd
        Scalar13 2D4 16 9281caecc477e3fb891f3a8ab7cdccc7a76afaded4a439b1d7483f8c5eb0f0fc
        Scalar14 2E4 18 a1bf6df4fff3f0efa008631967aecc5ee79823e7f461501d4e0c6676abf41219
        Scalar18 716 96 03e3186c8a2f467964a42e666de16d50a7229a3f7cd59b9899f5590bae03df9e
        Scalar20 7E0 640 bbde7b84eb90b56a2425842a67a0ff0316b3f3b0ff49b956f9b86c1267d75bb9
        Scalar21 AC6 257 a981a16e050ef47017e44f23c991256a478755f1b415667f56e6788d1b655295
        Scalar23 1AF6 28 6d7c2e0d13ed1ddb7b8f04090f806e86600cc3e5fffe7c68a1e999fb89542932
        Scalar24 21B4 38 e5f0b70061adc9a009af05c55164b675ffa874b839661bfb81954143d01064f8
        Scalar25 28B0 12 a632390af28ab4156f5f07816c62bed6379a5333f9d555abc5f6b9fd666ce684
        Scalar26 292E 4 dd659553a1c9a94a589dc215d10bf31a97d8cf3a23773ac8fb86b2fc1036ccf2
        Scalar27 2932 4 76fa571f4bcf6b16c16dbe3de873731126668e3856d85e327110fa600138b213
        Scalar30 336E 66 df04f0dccd553d06793b2bd814693c27e3a1a950e3ff5ff44b2b8833a24a8944
        Scalar32 44EC 6 203fe6a5ab55693db5a4daca78171984996c54a85119395dbd59d5fae00c7218
        Scalar33 5726 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        Scalar39 66B4 4 68edd9ac794113d870170dff99fb7c842e862a83ac3a4274f8f61539a0a28f11
        Scalar40 6E5E 8 75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5
        Scalar41 707C 126 36b97adbe6a2e0940791b510623534f00662cce579109fc9b54ce4e65836734d
        Scalar44 866A 32 35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86
        Scalar46 98B6 3 074d99c983072816cbc8d980c4a2fd441f6036aca06f83ee14e05cab5afbed85
        Scalar47 9A0E 4 b6f54d6d00b160ef946114b90c9f61182f61e2fbc9eef90d11932a2c00633a0a
        Scalar48 9A12 4 0c26fe5761d8d2bc06694d734f7e182662e6e9c7a907ac62466ae9f1e836d5d2
        Scalar50 9A1A 4 3655ca59b7d566ae06297c200f98d04da2e8e89812d627bc29297c25db60362d
        Scalar51 9A1E 4 e084a105a7b7d599b8346d3cba5ac51b756d7e29c7ef77fd9eff31fdeae31389
        Scalar52 9A22 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar53 9A26 4 6e90b5d2b8ce7b775b3f74bafd0a28d18344b287eff41d0cf938f18344ea8fa2
        Scalar55 C220 4 5085cb99dbe1e374ccd321e5b58182d2c42cf4840c68cfab17b0618a8285e7d6
        Scalar56 9A22 4 b40711a88c7039756fb8a73827eabe2c0fe5a0346ca7e0a104adc0fc764f528d
        Scalar57 9BFE 16 2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1
        Scalar61 A1BC 32 d190639ece42d9fb7cfb15093f8c512e483155efd04a382dc5028cb975f80e9d
        Scalar62 A99C 10 c8baca559928f7a969383bfb15ae961782632bbe0bfc0bfb29d8675552910682
        Scalar63 A9B2 10 e6888c5db0e75fd5f194fcbf6644f8955e7a551b31890c27fe9d5eaa0616f2f3
        Scalar64 A9BC 10 cf950796825e93582a8fe405634a8cb0dae1c3cee36631f56eafbf1b032c0419
        Scalar65 A9C6 10 b825b10d60767552c07ff7b903e93a7c63fb6c53cc75f4c6836730fc9fddd015
        Scalar66 A9D0 36 263c1fb778051c94a469aea0cc7f556711190e910b1adfe08d60c5c3cdbb26e4
        Scalar67 A9F4 36 74e0aafd2775d5661898d6911b25a16390e10f263ca552991ba5059390184a1a
        Scalar68 AA18 36 44477e1e6b11790a9b85e2511cd50791cbfca23b92e57eab86c6be4c12954f27
        Scalar69 AC28 10 a9f0a403100a1d62c1d125d517e648c725af2e2223dbd7168f9d9f0f16f603e4
        Scalar70 AC32 10 8d3aa62ba61db1425f07838c4b5eee7e18cfd01fd81eda5122307d2fbd4ee086
        Scalar71 AC3C 10 5c2f31bdf15814f8fca0b22ca362a613d68e7550a78cf2cc0c121d6c3f9d033f
        Scalar72 AC46 10 e3eba0a3fee3d6499eb198e0b82483d09f01694d5b0d0c31ae1a67d6b552b616
        Scalar73 AC50 10 c8ab647a77d307ca935e8b7ec249550629bfa72248b6577891b6a76a194d29db
        Scalar75 AC64 10 8acd0f169c95994d2c5fdd8523ccb7de497ad5e573f3f8abaa17355f1de3ebc1
        Scalar76 AC6E 10 6f9050c287b99a13cb1de16e53bd596e37cdaba74b8265abb30bebdfdb8da036
        Scalar78 AC82 10 20cd505703436c8f08a3a8b0d3aea336f36abbbe663a069c98399f78e7bafe8d
        Scalar79 AC8C 10 d19761a19a1ab82bfd1e33595e0adf6435d293e0b7a81ad8ea9e065225f52eb0
        Scalar80 AC96 64 0831b5cc2b606f4bbef80ec7b042884acb33b9d362170a364bbaa10bf8d317aa
        Scalar81 ADB8 36 98c54ce4feaaf6d817120aec9f3f26ef445eefa0e30405b4db230e58acd6aaeb
        Scalar82 ADDC 36 042f085c8c95329f80ca279ed42e25b605a3248b0cbaa7a8cedefe28f9665642
        Scalar83 AF72 20 63e9ba8ff3759479de4c2476dddcfe7d1f0923b487913a17b23c247af3a67323
        Scalar84 AF86 10 5be7da788798cd8d5cbffa36e05137126ce7be3381768c49c7b38f9f7c9c468a
        Scalar86 AF9A 32 436b32340c8354cbcc6020b0b94282cb142a878fe98bce6d84789487a1135057
        Scalar87 B782 24 8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8
        Scalar88 BA08 64 5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2
        Scalar89 BBD8 64 0676eb134913d68ceabb6a85371dd76a6882bef02d5d4960115697759b712a4b
        Scalar91 C8BC 16 15b409d5b4cb298c22668ca2614583dd9f986b71daec05da85fc07462fc28654
        Scalar92 CA46 86 2f57304367c291ca815295da38da0d9288ce5a4517e5d62bfa7b45495ebc6250
        Scalar93 DC18 16 e76611a1e7dbba9f3c9444a10766f28bb619fee4999c07ccb1450670944b86bd
        Scalar94 DC82 12 cd67f4ce359581d63ef16d28f9671d278eb0270275d54f380a1de1e0522d09f5
        Scalar95 DDE4 48 3a580f1d00cc27ec60a81450850060b6fa3dda19d4c515a7fb81edce4bf9e025
        Scalar96 DE14 6 debc06f91d9429bad45ef2e100278d6da6ae14e8363b542e09385b4e800f1c51
        Scalar97 E32E 22 56cf3a3b1587b397332bed61f9331d3221bd67ba84eae6abfece06dd57c7dffc
        Scalar98 E648 37 0ea85971b5ed91dc192acca6ff9eeff9674545c04235b734b5758ffee2e29bcd
        Scalar99 EA80 64 1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123
        Scalar100 EAC0 22 e542663bb2e9c8d6c89f85530253d7c71ee1e94426586152cbd08d46aad95e2c
        Scalar101 EB40 12 a035341b8f318e83ce1fb91875bd8bd9e1a8ee16a03eace220b1321743e7abe1
        Scalar102 EE2E 32 208076d8ca40166188a871fbab99e45dabd868ee08bd7e3266457d74bedcec1b
        MiniAnimations 5726 80 3324346eac28b3d6e05a8879c8fead0220a5fc1101fbbcefc3a4caa927c442f8
        CopiedPlanePointers 3038 16 7e699de40282254a8a8fec66e6456d6de737476762a19d87911d02e56262b3a1
        LivePlanePointers 33B0 16 2f5b9f478f4f44230ea76cb1574eca41017e2b09e378ea1be295aaef5f9cc6a1
        """); }
    private static List<Operand> operandsR73D() { return operands("""
        5486 43f900205776
        66A8 43f9002066b8
        6642 217c002066be
        7FEC 217c00208028
        8310 43f900208566
        83B8 43f900208566
        8422 43f900208566
        84E0 43f900208566
        AA96 43f900208566
        8146 217c00208574
        8210 217c00208574
        827A 217c002085c4
        8122 43f90020863c
        807A 217c00208642
        88CE 43f900208ac6
        8AB4 43f900208ac6
        879E 237c00208ace
        8992 237c00208ace
        8DE6 43f9002091d8
        8F66 43f9002091d8
        8C8E 217c00209260
        8D06 217c00209260
        8EBA 217c00209260
        9040 227c00209260
        944A 217c00209476
        94E6 217c00209578
        9D32 43f90020a1dc
        9D2A 217c0020a1e8
        A532 217c0020a554
        A65E 43f90020a696
        A5BC 217c0020a6a4
        A8F2 43f90020b160
        A7F6 217c0020b16c
        A896 237c0020b16c
        AFEC 217c0020b16c
        B1F0 43f90020b202
        B1E8 217c0020b20a
        B258 217c0020b304
        B56E 237c0020b79a
        B380 217c0020ba48
        B3B0 237c0020ba48
        BC28 217c0020bd7a
        CC30 43f90020cc42
        C42A 43f90020d064
        C464 217c0020d06a
        CB82 43f90020d0c6
        CAE4 217c0020d0cc
        CD78 43f90020d0dc
        CD0C 217c0020d0ec
        BDB6 43f90020d128
        BDFA 217c0020d186
        C31E 217c0020d30a
        CDC8 217c0020d33a
        CC74 217c0020d362
        D3AE 217c0020d4fa
        D732 45fa03c8
        D742 45fa03ea
        D72E 43fa0430
        D73E 43fa046c
        DAC4 43fa0132
        DABC 217c0020dc00
        DC44 217c0020dc8e
        DD3E 217c0020ddd6
        DE3E 217c0020dfa2
        DEEC 217c0020dfd6
        E0FC 43fa017a
        E144 43fa0132
        E236 43fa0082
        E096 217c0020e2f6
        E19E 217c0020e30a
        E5B0 43fa000e
        E3CE 217c0020e604
        E586 217c0020e604
        E78E 43fa00aa
        E6D8 217c0020e842
        EAF2 217c0020ec30
        ECB8 217c0020ee80
        EDEA 237c0020ee80
        F46A 217c0020f4b2
        F56 223c00210000
        3EF2 223c00210000
        EEE2 217c0021e278
        F3D6 217c0021e278
        EF70 43f90021e3ce
        EFB8 43f90021e3ce
        F0A6 43f90021e3ce
        F0BA 43f90021e3ce
        5E00 43f90021e6a6
        626C 43f90021e6a6
        6310 43f90021e6a6
        5DC8 217c0021e7ce
        61EC 217c0021e7ce
        63E4 217c0021e7ce
        61BC 43f90021e8e0
        6190 217c0021e8f8
        6118 43f90021e95c
        60D2 217c0021e96a
        6046 43f90021e986
        6024 217c0021e98e
        5EA6 43f90021e9a6
        5E78 217c0021e9ba
        CB12 217c0021e9ba
        5D90 43f90021eb4c
        5C50 217c0021eb52
        5CAC 237c0021eb52
        9E30 43f90021eb94
        9DAC 217c0021eb9c
        A0BA 43f90021ec4c
        9EEA 217c0021ec5a
        A056 217c0021ec5a
        9B2E 43f90021ecd8
        9C88 43f90021ecd8
        9B0E 217c0021ed0c
        9C4A 237c0021ed0c
        59FE 43f900220000
        3E42 217c0022df20
        7264 217c0022df20
        59CE 45f90022e8ac
        1B34 43f900232f3c
        9880 43f9002330bc
        98E2 43f9002330bc
        995E 43f9002330bc
        99CE 43f9002330bc
        9A74 43f9002330bc
        5F9A 43f9002336bc
        5F12 217c002336d8
        1154 45f9002343a4
        1214 45f9002343a4
        12C4 45f9002343a4
        1374 45f9002343a4
        1182 45f9002344a4
        1242 45f9002344a4
        13E0 43f9002344a4
        13E6 45f9002344a4
        141A 43f9002344a4
        1428 43f9002344a4
        142E 45f9002344a4
        12F2 45f9002354a4
        13A2 45f9002354a4
        1420 45f9002354a4
        380A d040d04043f900203c20227100fc
        18C 43f900200240
        192 45f90020024e
        19C 43f900200256
        1A2 45f90020025e
        1AC 43f90020026a
        1B2 45f900200272
        1BC 43f90020027e
        1C2 45f900200286
        1CC 43f9002002a0
        1D2 45f9002002b2
        1DC 43f9002002c2
        1F0 45f9002002d4
        1EA 43f9002002e4
        EEB2 47f900200716
        7DA 303b0004
        A8E 103b4036
        1AEC 103b1008
        2144 45f9002021b4
        286E 41f9002028b0
        28DC 43f90020292e
        2922 43f900202932
        2FA2 41f90020336e
        3348 41f90020336e
        44AC 45f9002044ec
        5720 103b0004
        669A 303b0018
        6E02 303b005a
        6CE8 45f90020707c
        86E8 43f90020866a
        985A 45fa005a
        9954 45f900209a0e
        98D8 45f900209a12
        96AE 45f900209a1a
        994A 45f900209a1a
        99C4 45f900209a1e
        9A6A 45f900209a1e
        9A56 45f900209a22
        9A60 45f900209a22
        9A38 45f900209a26
        C216 47f90020c220
        9BAC 45fb0050
        A1AA 43f90020a1bc
        A95E 103b003c
        A90E 45f90020a9b2
        A920 45f90020a9bc
        A934 45f90020a9c6
        A914 47f90020a9d0
        A926 47f90020a9f4
        A93A 47f90020aa18
        AAE2 45f90020ac28
        AB1A 45f90020ac28
        AAE8 47f90020ac32
        AB20 47f90020ac3c
        AAFA 47f90020ac46
        AB32 47f90020ac50
        AB26 49f90020ac64
        AB00 49f90020ac6e
        AB0C 49f90020ac82
        AB44 49f90020ac8c
        AB72 49f90020ac96
        AD84 47f90020adb8
        AD96 47f90020addc
        AED8 47f90020af72
        AB12 4bf90020af86
        AD5C 43f90020af9a
        B512 45f90020b782
        B392 45f90020ba08
        BCBC 43faff1a
        C8AE 47fb000c
        CA1A 45f90020ca46
        D580 43f90020dc18
        DC58 43fb0028
        DD60 43fa0082
        DD4C 43fa00c6
        E25A 43fa00d2
        E3D6 217c0020e648
        E6E0 217c0020ea80
        E6BC 43fa0402
        EB06 43fb0038
        EBFA 43f90020eb40
        EDAC 45f90020ee2e
        2FD2 267b0064
        305C 267b00da
        33C8 267b00e6
        100 4ef90020011e
        10C 4ef90020208c
        112 4ef900201c02
        """); }
    private static List<Dispatch> dispatchR73D() { return dispatch("""
        1 PlayerObject 203D7E false
        2 PlayerObject 203D7E false
        3 PowerupObject 2061D2 false
        4 MeshObject 20A508 false
        5 NullObject 203D10 true
        6 TestObject 205D9C false
        7 NullObject 203D10 true
        8 NullObject 203D10 true
        9 NullObject 203D10 true
        10 SpringObject 208182 false
        11 HDoorSplashObject 20616C false
        12 HDoorSplashSetObject 206134 false
        13 HDoorObject 20605C false
        14 TunnelSplashObject 206006 false
        15 MoveSpringObject 208036 false
        16 RingObject 208652 false
        17 LostRingObject 208936 false
        18 NullObject 203D10 true
        19 NullObject 203D10 true
        20 BigRingObject 209D44 false
        21 CapsuleObject 209AC6 false
        22 GoalObject 209E7C false
        23 SignpostObject 20A000 false
        24 ExplosionObject 205E42 false
        25 MonitorTimeObject 208E74 false
        26 MonitorItemObject 208FF0 false
        27 BoulderObject 207FBE false
        28 HudPointsObject 2094C8 false
        29 NullObject 203D10 true
        30 NullObject 203D10 true
        31 FlowerObject 205ED0 false
        32 SwingObject 20BB64 false
        33 NullObject 203D10 true
        34 NullObject 203D10 true
        35 HotaruObject 20D52E false
        36 BlockObject 20DC28 false
        37 SpikeBallObject 20DCEC false
        38 JumpSpikeBallObject 20DE1A false
        39 ProjectorObject 20E344 false
        40 AnimalObject 20E05E false
        41 RobotGeneratorObject 20E66E false
        42 NullObject 203D10 true
        43 BossBlockObject 20EAD6 false
        44 EggmanObject 20C410 false
        45 BossExhaustObject 20CCDC false
        46 BossLaserObject 20CA9C false
        47 MetalSonicObject 20BD98 false
        48 ElectricShieldObject 20C2EA false
        49 MetalSonicDebrisObject 20CD92 false
        50 SpikesObject 20EC8E false
        51 AmyRoseObject 20EE90 false
        52 HeartObject 20F3A8 false
        53 SwapPlatformObject 20F448 false
        54 LampObject 20D382 false
        55 DebrisObject 20CC4C false
        56 NullObject 203D10 true
        57 NullObject 203D10 true
        58 ResultsObject 20B4C2 false
        59 GameOverObject 20B220 false
        60 TitleCardObject 20B33C false
        """); }
    private static List<LayoutSlot> layoutsR73D() { return layouts("""
        0 72 64 3
        1 266 60 3
        2 448 1 1
        3 452 33 6
        4 946 1 1
        5 652 1 1
        6 656 48 6
        7 946 1 1
        8 946 1 1
        9 950 1 1
        10 950 1 1
        11 950 1 1
        12 72 64 3
        13 266 60 3
        14 448 1 1
        15 452 33 6
        16 946 1 1
        17 652 1 1
        18 656 48 6
        19 946 1 1
        20 946 1 1
        21 950 1 1
        22 950 1 1
        23 950 1 1
        24 72 64 3
        25 266 60 3
        26 448 1 1
        27 452 33 6
        28 946 1 1
        29 652 1 1
        30 656 48 6
        31 946 1 1
        32 946 1 1
        33 950 1 1
        34 950 1 1
        35 950 1 1
        """); }
    private static List<PaletteRecord> palettesR73D() { return palettes("""
        0 2098678 64256 31 128 false
        1 2098678 64256 31 128 false
        2 2098806 64256 31 128 false
        3 2098934 64256 7 32 false
        4 2098966 64288 23 96 false
        5 0 0 65535 262144 true
        6 2099062 64288 2 12 false
        """); }
}
