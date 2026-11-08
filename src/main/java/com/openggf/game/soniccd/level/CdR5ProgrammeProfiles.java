package com.openggf.game.soniccd.level;

import java.util.*;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;

/**
 * Finite R5 bindings for the existing common reader/parser. No new reader/decoder or lifecycle.
 * Native common/nemesis.asm:15-105 owns prefetch; its final byte can be the next real include.
 * Native objects/player.asm LoadPlayerGfx:2890-2928 and sprites/player[_gfx].asm own FRAMES_167.
 * Native r5/gfx_lists_*.asm, palettes_*.asm and section_gfx_*.asm own per-image ordered operands.
 * Native r5/palette_cycle_*.asm uses BYTE dest/steps/words/delay headers, unlike R3/R4 scripts.
 * No terrain Shape or player/World/live service binding is supplied. Public descriptive ranges
 * never become source owners; only the closed definition selects these canonical key identities.
 */
final class CdR5ProgrammeProfiles {
    private CdR5ProgrammeProfiles() { }
    record Product(CdProgrammeDefinition.JavaProduct javaProduct,ReadExtent nativeInput,
                   int nativePrepared,int nativeConsumed) { }
    record SourceFact(String key,int header,int output,int javaLength,int table,int consumed,
                      String javaSha,String outputSha,int nativeLength,int prepared,int nativeConsumed,String nativeSha,boolean plc) {
        Product product() { return new Product(new JavaProduct(new ReadExtent(javaLength,javaSha),table,consumed,outputSha),
            new ReadExtent(nativeLength,nativeSha),prepared,nativeConsumed); }
    }
    record PaletteFact(int id,int destination,int counter) { }
    record Facts(List<SourceFact> sources,List<Integer> roots,int bodies,int maximumCount,List<String> encountered,
                 List<PaletteFact> palettes,List<String> cycles,CycleProgramme cycleProgramme,List<SectionRow> sections) {
        Facts { sources=List.copyOf(sources);roots=List.copyOf(roots);encountered=List.copyOf(encountered);
            palettes=List.copyOf(palettes);cycles=List.copyOf(cycles);sections=List.copyOf(sections); }
    }
    static Product contract(CdR5DataLayout.Programme p,CdR5DataLayout.Range key) {
        return facts(p).sources().stream().filter(f->p.range(f.key())==key).findFirst()
            .orElseThrow(()->new IllegalArgumentException("Foreign R5 compressed key")).product();
    }
    static void configure(Builder b,CdR5DataLayout.Programme p) {
        b.dataOnly=true;var f=facts(p);
        for(var r:p.ranges())b.raw(r,r.fileOffset(),r.length(),r.sha256());
        for(var o:p.operands())b.operand(o.fileOffset(),o.hex());
        for(var s:f.sources()) {
            var r=p.range(s.key());var product=s.product();
            b.sources.add(new Compressed(r,new Span(r,r.fileOffset(),r.length(),r.sha256()),OptionalInt.of(s.header()),s.output(),
                s.key().equals("StageBlocks")?8192:65536,Optional.of(product.javaProduct()),Optional.of(product.nativeInput()),s.plc()));
        }
        // The real R5 raw art suffices for every DPLC read. No synthetic alignment/read padding.
        b.visual=new Visual(CdSonicVisualAssets.SourceFormat.FRAMES_167,p.range("PlayerSprites"),p.range("PlayerGfxScript"),p.range("PlayerGfx"));
        b.loadRole(CdProgrammeReader.LoadRole.PLC,p.range("GfxLists"));
        b.loadRole(CdProgrammeReader.LoadRole.PALETTE,p.range("PaletteDirectory"));
        b.loadRole(CdProgrammeReader.LoadRole.SECTIONS,p.range("SectionGfxRanges"));
        var palettes=f.palettes().stream().map(v->new Palette(p.range("Palette"+v.id()),v.destination(),v.counter())).toList();
        var cycles=f.cycles().stream().map(k->{var r=p.range(k);return new Cycle(CycleKind.BYTE_HEADER_4,
            new Slice(r,0,4),new Slice(r,4,r.length()-4));}).toList();
        // InitSectionGfx/UpdateSectionGfx independently index these actual labels.
        // N1ecc5f3 r5/section_gfx_3c.asm:3-65 and section_gfx_3d.asm:3-65:
        // two thresholds, but three initial/update WORDs; preserve the unused third pair.
        var sections=switch(p) {
            case R53C,R53D -> new CameraSections(f.sections(),new SectionColumns(2,
                new SectionWords(new Slice(p.range("SectionGfxRangesRead"),0,4),List.of(0x1800,0xFFFF)),
                new SectionWords(new Slice(p.range("SectionGfxInitListsRead"),0,6),List.of(2,4,2)),
                new SectionWords(new Slice(p.range("SectionGfxUpdateListsRead"),0,6),List.of(5,6,5))));
            default -> new CameraSections(f.sections());
        };
        // Native stage_data_load_1a/1b LoadStageMapLayer reads the real counter-sized
        // views, not the shorter FG include. common/block_collide indexes collision
        // bytes by the full low11 block ID; R51B's initial FG read hull is830 bytes.
        switch(p) {
            case R51A -> b.terrain=terrain(p,6240,22912,57344,55,3,800,"StageCollision");
            case R51B -> b.terrain=terrain(p,6688,22080,55296,2,2,830,"Map0Collision");
            default -> { } // Other programmes retain explicit unsupported terrain DATA admission.
        }
        b.load=new Load(19,f.maximumCount(),f.roots(),f.bodies(),f.encountered().stream().map(k->(Object)p.range(k)).toList(),
            palettes,cycles,f.cycleProgramme(),sections,
            new PointerTables(List.of(new Pointers(PointerKind.LIVE,p.range("BackgroundPointers"),
                List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718))),Optional.of(p.range("BackgroundRows"))));
    }
    private static Terrain terrain(CdR5DataLayout.Programme p,int blocks,int patterns,int chunks,
                                   int bgColumns,int bgRows,int collisionBytes,String collision) {
        var profile=new CdStageTerrain.Profile(blocks,patterns,chunks,44,5,bgColumns,bgRows,collisionBytes,
            CdStageTerrain.LayoutEncoding.EXACT_READ_VIEW,CdStageTerrain.ReferencePolicy.SELECTED_KNOWN_SOURCE);
        return new Terrain(profile,Optional.empty(),Map.of(
            CdProgrammeReader.Role.CHUNKS,p.range("StageChunks"),CdProgrammeReader.Role.FG,p.range("StageMaps0Read"),
            CdProgrammeReader.Role.BG,p.range("StageMaps1Read"),CdProgrammeReader.Role.COLLISION,p.range(collision),
            CdProgrammeReader.Role.ANGLE,p.range("StageCollisionAngles"),CdProgrammeReader.Role.HEIGHT,p.range("StageCollisionColumns"),
            CdProgrammeReader.Role.WIDTH,p.range("StageCollisionRows")),p.range("StageBlocks"),p.range("StageGfx"));
    }
    static Facts facts(CdR5DataLayout.Programme p) { return switch(Objects.requireNonNull(p)) {
        case R51A -> factsR51A();
        case R51B -> factsR51B();
        case R51C -> factsR51C();
        case R51D -> factsR51D();
        case R52A -> factsR52A();
        case R52B -> factsR52B();
        case R52C -> factsR52C();
        case R52D -> factsR52D();
        case R53C -> factsR53C();
        case R53D -> factsR53D();
    }; }
    private static Facts factsR51A() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",true),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80C3,6240,4556,63,4556,"ab9ebd1fb88ff8ee7ce5b43ceca3a0770ffe5ed60c87261884d65af6be1d0680","a615e970ef492a44ad3f0d19522d89dbeb15e5a0fdb6a3e706b165b527149030",4557,65,4557,"2b82b4d1c40cf9e0c1baaad77bb649963476c6e9e849451bdd33256f92bb4ba5",false),
            new SourceFact("StageGfx",0x82CC,22912,12072,101,12072,"2001a3e3f637fef76aa62527ea1152ab357ec3ab9fa1520a65eab006f06dc37c","ea619c3857d9caeaee1ce3b6e29184c97d023249fd4ac4c897a0bf48a0744dc2",12072,103,12072,"2001a3e3f637fef76aa62527ea1152ab357ec3ab9fa1520a65eab006f06dc37c",true),
            new SourceFact("BgInsideGfxA",0x8072,3648,1650,79,1650,"2e5210c41fa81a3927565221ec5a7ca255f707b11d2c0ba8a6c2a89a7256d0a4","9b704845af2901001dd81b016eebe90342ba287235aca7cf474b24d2285dbd07",1650,81,1650,"2e5210c41fa81a3927565221ec5a7ca255f707b11d2c0ba8a6c2a89a7256d0a4",true),
            new SourceFact("BgOutsideGfxA",0x8054,2688,1408,61,1408,"2302bcef30c35b187308e5a9245e36d1c29ac85fe1cad07771c1566b2d0be92a","4fbe6c7f5fa4b3fadc01e0889086ad66cce672b228fceda629c68dbdec39cdbf",1409,63,1409,"6847bd9ad75808656139cf39818cfdd80522000a8de3f67177b08b99d3596c31",true),
            new SourceFact("BridgeGfxA",0x0004,128,130,43,129,"0822c8851ddea5256200e5a3fe1900a9d5aa55360ee7bb3c6ff6404cad03099d","0c22fc5a97a8209ad29cabb868e710b84d00e740df5facb636f690c0d0247db0",130,45,130,"0822c8851ddea5256200e5a3fe1900a9d5aa55360ee7bb3c6ff6404cad03099d",true),
            new SourceFact("ConveyorCtrlGfxA",0x800F,480,364,76,364,"520ce9cc1166437b8012e4380b87ef42bb4e3029debb5f16817245754a9da735","8ddcfdf6049e3696ea8ea130eaa12eee4a64e11e899ee6cf8ca94f446a854faf",364,78,364,"520ce9cc1166437b8012e4380b87ef42bb4e3029debb5f16817245754a9da735",true),
            new SourceFact("PlatformGfxA",0x0028,1280,794,90,793,"ef06e166f5474d7b2e2d381dbb622eb50c7e630554f9b609dcd722495c1488e2","068f9f965f4c821059b4386a0ce8aa58305b5695b734b0d888f7b488a0cce3c9",794,92,794,"ef06e166f5474d7b2e2d381dbb622eb50c7e630554f9b609dcd722495c1488e2",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxA",0x801C,896,396,77,396,"f085d068e605b11c8496fbb9b412dcc0a93d476576961d4c47aba5b0106216f9","6a6f5bf1507fd9390d388737f52817d6b016448923429ffdc951b9990713da8b",397,79,397,"f8cd0fca08d5d90e14b2f7d5f567150d78a611a6e939cbc888bbec5b7c6f960b",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxA",0x0008,256,134,39,133,"2e815968deead7eb87a66f4fdbd1ed7a70c38656deedfdcfcea7090670fee0d7","0cb322e653b6ea4556311fff41a23dd2aa25bfd3f9ca6a06f0c75e7e6d574f68",134,41,134,"2e815968deead7eb87a66f4fdbd1ed7a70c38656deedfdcfcea7090670fee0d7",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxAC",0x800C,384,196,40,196,"e67316fdc91db9039c6b1220f56fac812d9c5e0fc208f641688b4818a861aab8","04393ed9dc951e47a0265d881e03317c707f50957fe951bb180a2ebaf7223fb8",197,42,197,"61de9b480ce3688f840b8fb470631e565852ca66fe6b22c3be94943527230b30",true),
            new SourceFact("RockGfxA",0x8010,512,322,67,322,"78edc607174bfcf7fbd70801bbad0e9adc8cbe02c9092438e51c121ba5705a87","d5ed73f7494c9336f400dedf0220b0ad6a06e5faaac7a9372003e6099cf0776a",322,69,322,"78edc607174bfcf7fbd70801bbad0e9adc8cbe02c9092438e51c121ba5705a87",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"2f1310eacde10f2bebac92ee48424ab6b6cf78b5370293c193dc48e2cdbb03a6",true),
            new SourceFact("RobotTransportGfxA",0x000F,480,332,77,332,"26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda","ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67",332,79,332,"26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda",true),
            new SourceFact("SinePlatformGfxA",0x0020,1024,612,103,612,"1fb6fdb5f1ccb05b37c915bf287f59de17b3e183bafdfb75b1af77cd63df29ca","aa68d6c3bf0234bef8407e814213e79fa52ef74e566c2f77bf8eabece51762e7",613,105,613,"8527f6e48c814a3952b52a9b376fe9473a58201db1ca349cab72e78f3244f6f3",true),
            new SourceFact("CrackFloorGfxA",0x8010,512,250,53,249,"bce4eecb8089bd7f8443e4946f96912d36c1c2176fbaee403aaf64a3ce1fd7bb","d599d7fb4f721801a99aa2c680b829c8cc2424e22dead55fabe00ac029e908a4",250,55,250,"bce4eecb8089bd7f8443e4946f96912d36c1c2176fbaee403aaf64a3ce1fd7bb",true)
        ),
        List.of(64142,64162,64248,64142,64248,64248,64248,64248,64248,64248,64248,64248,64248,64248,64342,64350,64334,64248,64358),7,14,List.of("StageGfx","BgInsideGfxA","CheckpointGfx","BridgeGfxA","PlatformGfxA","ConveyorCtrlGfxA","RockGfxA","TitleCardGfx","TitleCardTextGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxAC","CollapseFloorGfxA","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxA","SinePlatformGfxA","CrackFloorGfxA","AnimalsGfx","RobotTransportGfxA","BgOutsideGfxA","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23)
        ),
        List.of("Cycle0","Cycle1","Cycle2","Cycle3"),CycleProgramme.R5_PRESENT_BACKGROUND_CONVEYOR,
        List.of(
            new SectionRow(5600,2,5),
            new SectionRow(6912,4,6),
            new SectionRow(65535,2,5)
        )); }
    private static Facts factsR51B() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80D1,6688,4990,63,4989,"c9cff58f027ff13b586baef8292930e2b25be383414e4ea0e5f48d591b08338b","99323cc173bdc9b87c8b433a710afc13badb280c8f81f2efa1e0da2d13d6d484",4990,65,4990,"c9cff58f027ff13b586baef8292930e2b25be383414e4ea0e5f48d591b08338b",false),
            new SourceFact("StageGfx",0x82B2,22080,10864,103,10864,"1d18f3d4eb429340b94e85875fd22cdb80ffc6186da2489ac343a0228b8210f5","7dca52adfd393fea56dde1854b2056878af08ec3715b7d294a7e481c1b014030",10865,105,10865,"791c84b76f62e59202bd1a5f654396d7ae38dcfc16f5045ed9ffce612604b360",true),
            new SourceFact("BgInsideGfxB",0x80A7,5344,2336,79,2336,"9644dcea80b700e2d62cdfef3b5f2277eb78ec8f39f04975e3c266968d8f0438","aae028300735c2b0e4fbee61d46fd5b940334b827890232e413d93076eea0bee",2337,81,2337,"63fec60ecfede51bf024e251b6bed1c5166de84a4d7080f8273822c4c5998c68",true),
            new SourceFact("BgOutsideGfxB",0x80AF,5600,2614,95,2613,"04607ee9416ccd02bb5a82ee93c5aea0ddac5459a113dec16d8bc1be028014dd","971a34aa44bfba4ccdd865f5ff37fef8df6559f09a6a8a6c147b3a2e2a6105d0",2614,97,2614,"04607ee9416ccd02bb5a82ee93c5aea0ddac5459a113dec16d8bc1be028014dd",true),
            new SourceFact("BridgeGfxB",0x0004,128,104,36,104,"e433b838c95fb55866ad54ffc4eb60294da4332950e17be9dc07287ff83b06b1","7e1711b4f320ea50483bd71edd54559c4d1f2a668f98a04b9908ad714ddd6acf",105,38,105,"23464cc62dc0d09a2c94087ac1b7e4d8ed34a226be7747451acdac88925939de",true),
            new SourceFact("ConveyorCtrlGfxB",0x800F,480,369,73,369,"152174437bcb3c465ad19513edb83946d5f2e5c2eb9148ab7cc9c38312a4d588","c4c99e066729ff2d9033803141267ba3bf185bfe7dc9b005c72c7466857a4c79",369,75,369,"152174437bcb3c465ad19513edb83946d5f2e5c2eb9148ab7cc9c38312a4d588",true),
            new SourceFact("PlatformGfxB",0x0028,1280,828,95,828,"50807d3cf58f817fa5ca36d724f9dff8c5223d6a5254a548fe0dac942554b44b","db8e55801b368fe1a62de888ce6ecfdc8ca21ef9bae475d274161de6982fd174",829,97,829,"8a635eed3aff6191483fa40382e618ed9278703a459d2099506a1c090cf71393",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",true),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",true),
            new SourceFact("BreakWallGfxB",0x801C,896,340,75,340,"e9d73b6fcce3b237a75b93a7e8d358364b151a6d8144b16251795687f81b7945","e90d9ecc672c5f46fcff2845586d058b7a8a0c5576cb9c51d796231ed482ba96",340,77,340,"e9d73b6fcce3b237a75b93a7e8d358364b151a6d8144b16251795687f81b7945",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxB",0x8008,256,112,34,112,"01414fe9bf0de2ddf9644b3b5e1d7cde652da7e89aee89acacf118c4b4a88197","5f7b9178b21c5c407f04b05abeb8eef3fba1db27391ecef8827c612ae7f2a799",113,36,113,"3009a8d47ba5b845e7e20e4b9212d0bd01dd3cd5cc16fc5c07142b2bfa303a95",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxB",0x800C,384,188,55,187,"b259b0cd267d12e830664d4203c308fcf530e64edc17df5fb3624d8c03e79aa1","9520fb77468952279fc12e8a52bee91d17c0b9b298069d64dacf9cf535dea551",188,57,188,"b259b0cd267d12e830664d4203c308fcf530e64edc17df5fb3624d8c03e79aa1",true),
            new SourceFact("RockGfxB",0x8010,512,276,59,276,"45e8687ab9923e279c75086112cda613ef618fc4d62e0c616ca584806f2426f0","3a88b003946cfc4939dfab9cc80a6db7e06fabd2bb387e35751038cec6cde71e",277,61,277,"cb65437da6df84b7cbdda53e0d7208e177ec4caacdf509b2b051072f83c083c0",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"2f1310eacde10f2bebac92ee48424ab6b6cf78b5370293c193dc48e2cdbb03a6",true),
            new SourceFact("RobotTransportGfxB",0x0017,736,434,92,434,"4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806","672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6",434,94,434,"4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806",true),
            new SourceFact("SinePlatformGfxB",0x8020,1024,436,70,435,"401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca","ba1cb917e5e0e1353b383f59e2f5ab60842d3b34de78202cf961d71e51fc82b1",436,72,436,"401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca",true),
            new SourceFact("CrackFloorGfxB",0x8010,512,250,53,250,"feb698ed7e976af97b7746f243640fab416fef73c83867a37e64f574ec3d5c28","7d1dd19603982419f71c4ce0e916da76fac8f771b3ce462394cb6fa7d2f664da",251,55,251,"7f2ab3e00167cc1ff2c789febb0bdd44028c4cec65fc7d9b1f72dbb01947f682",true)
        ),
        List.of(63852,63866,63958,63852,64038,64118,64126,64178,64186,63958,63958,63958,63958,63958,64142,64150,64134,63958,64158),12,15,List.of("StageGfx","BgInsideGfxB","BridgeGfxB","PlatformGfxB","ConveyorCtrlGfxB","RockGfxB","TitleCardGfx","TitleCardTextGfx","AnimalsGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxB","CollapseFloorGfxB","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxB","SinePlatformGfxB","CrackFloorGfxB","RobotTransportGfxB","HologramGfx","HologramAnimalsGfx","BgOutsideGfxB","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23)
        ),
        List.of("Cycle0","Cycle1","Cycle2"),CycleProgramme.R5_PAST_BACKGROUND_CONVEYOR,
        List.of(
            new SectionRow(5600,2,5),
            new SectionRow(6912,4,6),
            new SectionRow(65535,2,5)
        )); }
    private static Facts factsR51C() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80BE,6080,4490,63,4489,"56a9499d48509edb9e2bafc19cd9f0eb7bbe15569c4ccb027881c97fe85b4423","d326748d13deccdfb660a84675572934b669e8711a9cc38efe7a2eb63e73c361",4490,65,4490,"56a9499d48509edb9e2bafc19cd9f0eb7bbe15569c4ccb027881c97fe85b4423",false),
            new SourceFact("StageGfx",0x82B0,22016,11308,105,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b",11308,107,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c",true),
            new SourceFact("BgInsideGfxC",0x805F,3040,1654,86,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1","b828db38ce1ebac8ff39ce7d8f72dfd1cca91e545afc8e6a3ed32c4f32f55d03",1654,88,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1",true),
            new SourceFact("BgOutsideGfxC",0x8061,3104,1662,93,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d","84898fece5dd9dcc6a1c12f0645119406fb1a4fcdc93727e42c9f708d7103643",1662,95,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d",true),
            new SourceFact("BridgeGfxC",0x8004,128,124,43,123,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5","0b6fdedb84f512951a49d1064294a66c6e93d89a931360a89296b28ec3cca9be",124,45,124,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5",true),
            new SourceFact("ConveyorCtrlGfxC",0x800F,480,369,79,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7","36fa0fd666f14a4874a03d800f9f586de0b8dbb4f8a098d91d1a359cf816a982",369,81,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7",true),
            new SourceFact("PlatformGfxC",0x0028,1280,830,113,830,"7c453665e68f5254d074e2720930aa683f62e5aafddebb784559febf8bf24597","94a79b97e6f58b0179e91513191672e6ebc9e99fe08b247e05ff5c709a0bea03",831,115,831,"c8c7c3c821ec2bfd107058b5fc3df3b299f59900055e699ddd3b08dfa4647840",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxC",0x801C,896,459,59,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c","bd5f7c43dfe9271327d5eb9cd71bbe68a552aac7464406214781e7bbd417741c",459,61,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxC",0x0008,256,124,36,123,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce","7a331bfaaaa5e5e9b144e989e67c016f5bc0353735b95d9f8232228cf36d9159",124,38,124,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxAC",0x800C,384,196,40,196,"e67316fdc91db9039c6b1220f56fac812d9c5e0fc208f641688b4818a861aab8","04393ed9dc951e47a0265d881e03317c707f50957fe951bb180a2ebaf7223fb8",197,42,197,"61de9b480ce3688f840b8fb470631e565852ca66fe6b22c3be94943527230b30",true),
            new SourceFact("RockGfxC",0x8010,512,286,61,285,"b23173590b0cd7dbe737505ffa0c210b489bcad78a5e9b7a2f82f63e218acfa6","0cff4757eebe52d0e2d56b3893802653126d2e5986fa97314cf72c133279df31",286,63,286,"b23173590b0cd7dbe737505ffa0c210b489bcad78a5e9b7a2f82f63e218acfa6",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"fc524022cf7edd6348e3d8123c954623f9882944d6920cfc5092371cecc8211c",true),
            new SourceFact("SinePlatformGfxC",0x8020,1024,495,83,495,"86b55ca4393cc80cf8508d81022bcec3c705a0ce673722af6a953e291af47773","13b287f0cfd68b798f1d0f74c47f29e791da2bda0031fad0879096a769e3eb54",495,85,495,"86b55ca4393cc80cf8508d81022bcec3c705a0ce673722af6a953e291af47773",true),
            new SourceFact("CrackFloorGfxC",0x8010,512,172,36,172,"953d061880f96ee9cf22ad807a026fe2b5c406b2d1d522967c4b7fc862f055ed","c406f66f445a0ca64530b3491d056ad914c1a3d983d5533397fc51b55b15ff9e",172,38,172,"953d061880f96ee9cf22ad807a026fe2b5c406b2d1d522967c4b7fc862f055ed",true)
        ),
        List.of(63628,63642,63728,63628,63728,63728,63728,63728,63728,63728,63728,63728,63728,63728,63816,63824,63808,63728,63832),7,14,List.of("StageGfx","BgInsideGfxC","BridgeGfxC","PlatformGfxC","ConveyorCtrlGfxC","RockGfxC","TitleCardGfx","TitleCardTextGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxAC","CollapseFloorGfxC","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxC","SinePlatformGfxC","CrackFloorGfxC","AnimalsGfx","BgOutsideGfxC","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(5600,2,5),
            new SectionRow(6912,4,6),
            new SectionRow(65535,2,5)
        )); }
    private static Facts factsR51D() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80C6,6336,4738,65,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a","01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26",4738,67,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a",false),
            new SourceFact("StageGfx",0x82B3,22112,11854,109,11854,"dcb3fa802fad98f566f909f7dfbc136aa2232cd40e784b79c4ebde94e5358a30","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf",11855,111,11855,"9a0f1f89560843e6a3cdfe65adfbb6b19f558ca4d3f9017ea865adba6f66a93a",true),
            new SourceFact("BgInsideGfxD",0x805F,3040,1794,111,1792,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5","9d18ee1f35e4d74d2ba5370e92d303120afbbbb77fcd9239f696106e8dfcf35f",1794,113,1794,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5",true),
            new SourceFact("BgOutsideGfxD",0x8057,2784,1478,63,1477,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699","a75c4a185336b89f67a9d18a6eab39b1737e7f71ffae8abeb4a65e6208008aea",1478,65,1478,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699",true),
            new SourceFact("BridgeGfxD",0x0004,128,116,38,116,"b4221a4cb7ac24cbb8cceea42cc8b241b191e9442f41d6284bcecdd9116d6524","e88a09b55ef60fd34b887dbbfd0d878485fbf9b184e5d62f3b8bb275dfa6498a",117,40,117,"6ee13f9ee529e3cf3522aa758b910d59cc9c16b36ab0ee0a1f9bd424303b3333",true),
            new SourceFact("ConveyorCtrlGfxD",0x800F,480,349,79,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc","73107202079a2d510c6b127f224c8474f7f39df167d5d31539676e503a577301",349,81,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc",true),
            new SourceFact("PlatformGfxD",0x0028,1280,784,99,784,"115a2a224145a64ae990756b953ffcbf891954266b3856d693d9a2a2abdb0f0e","72583167b72e6b2f50aaa16df60c7acc69eaea381d36836e3d89c3dada837f01",785,101,785,"e3d1df2bf947233989f0a674f42481edcba51db1cf35ce997b63523a3a1ae861",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxD",0x801C,896,408,83,407,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30","e44bb6f92ffaef293651caa05ddd27e581e0e067320b6f5a2101a120f6f0f536",408,85,408,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxD",0x8008,256,112,34,112,"c75cc362a60b81e6968c5adc66c6d059734dbb690de2775b4be130c6f0c56fe3","a8ed121a44326fe64d05dd06835fc51e2d6189caaf136d1a09376f7f54bab83a",113,36,113,"8da74604df1662e4a8fdf32d5fc7e9dd088da392492435be1a5eda1b7b51cc25",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxD",0x800C,384,278,75,277,"e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd","c643d5d7d93a2341e31eb797fca20763da4144073c342ce8f7e42a4c3635defc",278,77,278,"e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd",true),
            new SourceFact("RockGfxD",0x8010,512,256,52,256,"0578a33bff801847439bd1321fbbb3873f825a72de869789864298992e7180dd","fc95595d62e27e83d4f24f5fa8f0379d62934c02e698ea5d5f4592b72507efe6",257,54,257,"7b85a6f3756693b30a4f1129879324dc14c77a919370859d5718ec7ce2553e1e",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"fc524022cf7edd6348e3d8123c954623f9882944d6920cfc5092371cecc8211c",true),
            new SourceFact("SinePlatformGfxD",0x8020,1024,460,77,460,"ed5988bf0619f46577a62cd7cf60782055f1747995567d085891bfc4c9123b0c","bd4b21660730d36fa84135eb3236c49f148f639f6cdabf1c5b0e9e57047333e2",461,79,461,"511c46bb1b9747468bf18a3c6d5710c1a6f1818a38369a57bc20223b66105c3d",true),
            new SourceFact("CrackFloorGfxD",0x0010,512,282,56,282,"cd5805de0d82bdc5e578cd095f77519a6c6d06ac198038e700b6fb4a28f517be","d533fc724751cd74522c2d86ed194cd8f2878e372869990324756d88b0b72862",284,58,284,"b4daf4bb431dbbb6910a388f01d1ff388cbeebc72d197e787542680311a028e7",true)
        ),
        List.of(63876,63890,63976,63876,63976,63976,63976,63976,63976,63976,63976,63976,63976,63976,64064,64072,64056,63976,64080),7,14,List.of("StageGfx","BgInsideGfxD","BridgeGfxD","PlatformGfxD","ConveyorCtrlGfxD","RockGfxD","TitleCardGfx","TitleCardTextGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxD","CollapseFloorGfxD","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxD","SinePlatformGfxD","CrackFloorGfxD","AnimalsGfx","BgOutsideGfxD","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1","Cycle2","Cycle3"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(5600,2,5),
            new SectionRow(6912,4,6),
            new SectionRow(65535,2,5)
        )); }
    private static Facts factsR52A() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",true),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80C3,6240,4556,63,4556,"910640d1a7f790e94e00b2f3286e8951f6f17e410ba5043d452670fdaf7bac2e","88cb05e79353c57c571a87bdc4da24028168d0aca43e7fb70543f48ed49babdd",4557,65,4557,"396a97e0d9f52cfa244b86eaa0631377dce99552444018dc5b6ba4fe8e751c79",false),
            new SourceFact("StageGfx",0x82CC,22912,12072,101,12072,"2001a3e3f637fef76aa62527ea1152ab357ec3ab9fa1520a65eab006f06dc37c","ea619c3857d9caeaee1ce3b6e29184c97d023249fd4ac4c897a0bf48a0744dc2",12072,103,12072,"2001a3e3f637fef76aa62527ea1152ab357ec3ab9fa1520a65eab006f06dc37c",true),
            new SourceFact("BgInsideGfxA",0x8072,3648,1650,79,1650,"2e5210c41fa81a3927565221ec5a7ca255f707b11d2c0ba8a6c2a89a7256d0a4","9b704845af2901001dd81b016eebe90342ba287235aca7cf474b24d2285dbd07",1650,81,1650,"2e5210c41fa81a3927565221ec5a7ca255f707b11d2c0ba8a6c2a89a7256d0a4",true),
            new SourceFact("BgOutsideGfxA",0x8054,2688,1408,61,1408,"2302bcef30c35b187308e5a9245e36d1c29ac85fe1cad07771c1566b2d0be92a","4fbe6c7f5fa4b3fadc01e0889086ad66cce672b228fceda629c68dbdec39cdbf",1409,63,1409,"6847bd9ad75808656139cf39818cfdd80522000a8de3f67177b08b99d3596c31",true),
            new SourceFact("BridgeGfxA",0x0004,128,130,43,129,"0822c8851ddea5256200e5a3fe1900a9d5aa55360ee7bb3c6ff6404cad03099d","0c22fc5a97a8209ad29cabb868e710b84d00e740df5facb636f690c0d0247db0",130,45,130,"0822c8851ddea5256200e5a3fe1900a9d5aa55360ee7bb3c6ff6404cad03099d",true),
            new SourceFact("ConveyorCtrlGfxA",0x800F,480,364,76,364,"520ce9cc1166437b8012e4380b87ef42bb4e3029debb5f16817245754a9da735","8ddcfdf6049e3696ea8ea130eaa12eee4a64e11e899ee6cf8ca94f446a854faf",364,78,364,"520ce9cc1166437b8012e4380b87ef42bb4e3029debb5f16817245754a9da735",true),
            new SourceFact("PlatformGfxA",0x0028,1280,794,90,793,"ef06e166f5474d7b2e2d381dbb622eb50c7e630554f9b609dcd722495c1488e2","068f9f965f4c821059b4386a0ce8aa58305b5695b734b0d888f7b488a0cce3c9",794,92,794,"ef06e166f5474d7b2e2d381dbb622eb50c7e630554f9b609dcd722495c1488e2",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxA",0x801C,896,396,77,396,"f085d068e605b11c8496fbb9b412dcc0a93d476576961d4c47aba5b0106216f9","6a6f5bf1507fd9390d388737f52817d6b016448923429ffdc951b9990713da8b",397,79,397,"f8cd0fca08d5d90e14b2f7d5f567150d78a611a6e939cbc888bbec5b7c6f960b",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxA",0x0008,256,134,39,133,"2e815968deead7eb87a66f4fdbd1ed7a70c38656deedfdcfcea7090670fee0d7","0cb322e653b6ea4556311fff41a23dd2aa25bfd3f9ca6a06f0c75e7e6d574f68",134,41,134,"2e815968deead7eb87a66f4fdbd1ed7a70c38656deedfdcfcea7090670fee0d7",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxAC",0x800C,384,196,40,196,"e67316fdc91db9039c6b1220f56fac812d9c5e0fc208f641688b4818a861aab8","04393ed9dc951e47a0265d881e03317c707f50957fe951bb180a2ebaf7223fb8",197,42,197,"61de9b480ce3688f840b8fb470631e565852ca66fe6b22c3be94943527230b30",true),
            new SourceFact("RockGfxA",0x8010,512,322,67,322,"78edc607174bfcf7fbd70801bbad0e9adc8cbe02c9092438e51c121ba5705a87","d5ed73f7494c9336f400dedf0220b0ad6a06e5faaac7a9372003e6099cf0776a",322,69,322,"78edc607174bfcf7fbd70801bbad0e9adc8cbe02c9092438e51c121ba5705a87",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"2f1310eacde10f2bebac92ee48424ab6b6cf78b5370293c193dc48e2cdbb03a6",true),
            new SourceFact("RobotTransportGfxA",0x000F,480,332,77,332,"26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda","ce19033d1380d75b2e7b816bd76bfdae7453c77dea728280dd3d2d90cfaaaa67",332,79,332,"26fb61222365ec1dc3a6c442401cdbc52e5eaf7e2a77903cb93570c7d325fcda",true),
            new SourceFact("SinePlatformGfxA",0x0020,1024,612,103,612,"1fb6fdb5f1ccb05b37c915bf287f59de17b3e183bafdfb75b1af77cd63df29ca","aa68d6c3bf0234bef8407e814213e79fa52ef74e566c2f77bf8eabece51762e7",613,105,613,"8527f6e48c814a3952b52a9b376fe9473a58201db1ca349cab72e78f3244f6f3",true),
            new SourceFact("CrackFloorGfxA",0x8010,512,250,53,249,"bce4eecb8089bd7f8443e4946f96912d36c1c2176fbaee403aaf64a3ce1fd7bb","d599d7fb4f721801a99aa2c680b829c8cc2424e22dead55fabe00ac029e908a4",250,55,250,"bce4eecb8089bd7f8443e4946f96912d36c1c2176fbaee403aaf64a3ce1fd7bb",true)
        ),
        List.of(63574,63594,63680,63574,63680,63680,63680,63680,63680,63680,63680,63680,63680,63680,63774,63782,63766,63680,63790),7,14,List.of("StageGfx","BgInsideGfxA","CheckpointGfx","BridgeGfxA","PlatformGfxA","ConveyorCtrlGfxA","RockGfxA","TitleCardGfx","TitleCardTextGfx","SpringGfx","Spring45Gfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxAC","CollapseFloorGfxA","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxA","SinePlatformGfxA","CrackFloorGfxA","AnimalsGfx","RobotTransportGfxA","BgOutsideGfxA","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23)
        ),
        List.of("Cycle0","Cycle1","Cycle2","Cycle3"),CycleProgramme.R5_PRESENT_BACKGROUND_CONVEYOR,
        List.of(
            new SectionRow(1024,2,5),
            new SectionRow(65535,4,6)
        )); }
    private static Facts factsR52B() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80D1,6688,4990,63,4989,"c9cff58f027ff13b586baef8292930e2b25be383414e4ea0e5f48d591b08338b","99323cc173bdc9b87c8b433a710afc13badb280c8f81f2efa1e0da2d13d6d484",4990,65,4990,"c9cff58f027ff13b586baef8292930e2b25be383414e4ea0e5f48d591b08338b",false),
            new SourceFact("StageGfx",0x82B2,22080,10864,103,10864,"1d18f3d4eb429340b94e85875fd22cdb80ffc6186da2489ac343a0228b8210f5","7dca52adfd393fea56dde1854b2056878af08ec3715b7d294a7e481c1b014030",10865,105,10865,"791c84b76f62e59202bd1a5f654396d7ae38dcfc16f5045ed9ffce612604b360",true),
            new SourceFact("BgInsideGfxB",0x80A7,5344,2336,79,2336,"9644dcea80b700e2d62cdfef3b5f2277eb78ec8f39f04975e3c266968d8f0438","aae028300735c2b0e4fbee61d46fd5b940334b827890232e413d93076eea0bee",2337,81,2337,"63fec60ecfede51bf024e251b6bed1c5166de84a4d7080f8273822c4c5998c68",true),
            new SourceFact("BgOutsideGfxB",0x80AF,5600,2614,95,2613,"04607ee9416ccd02bb5a82ee93c5aea0ddac5459a113dec16d8bc1be028014dd","971a34aa44bfba4ccdd865f5ff37fef8df6559f09a6a8a6c147b3a2e2a6105d0",2614,97,2614,"04607ee9416ccd02bb5a82ee93c5aea0ddac5459a113dec16d8bc1be028014dd",true),
            new SourceFact("BridgeGfxB",0x0004,128,104,36,104,"e433b838c95fb55866ad54ffc4eb60294da4332950e17be9dc07287ff83b06b1","7e1711b4f320ea50483bd71edd54559c4d1f2a668f98a04b9908ad714ddd6acf",105,38,105,"23464cc62dc0d09a2c94087ac1b7e4d8ed34a226be7747451acdac88925939de",true),
            new SourceFact("ConveyorCtrlGfxB",0x800F,480,369,73,369,"152174437bcb3c465ad19513edb83946d5f2e5c2eb9148ab7cc9c38312a4d588","c4c99e066729ff2d9033803141267ba3bf185bfe7dc9b005c72c7466857a4c79",369,75,369,"152174437bcb3c465ad19513edb83946d5f2e5c2eb9148ab7cc9c38312a4d588",true),
            new SourceFact("PlatformGfxB",0x0028,1280,828,95,828,"50807d3cf58f817fa5ca36d724f9dff8c5223d6a5254a548fe0dac942554b44b","db8e55801b368fe1a62de888ce6ecfdc8ca21ef9bae475d274161de6982fd174",829,97,829,"8a635eed3aff6191483fa40382e618ed9278703a459d2099506a1c090cf71393",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",true),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",true),
            new SourceFact("BreakWallGfxB",0x801C,896,340,75,340,"e9d73b6fcce3b237a75b93a7e8d358364b151a6d8144b16251795687f81b7945","e90d9ecc672c5f46fcff2845586d058b7a8a0c5576cb9c51d796231ed482ba96",340,77,340,"e9d73b6fcce3b237a75b93a7e8d358364b151a6d8144b16251795687f81b7945",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxB",0x8008,256,112,34,112,"01414fe9bf0de2ddf9644b3b5e1d7cde652da7e89aee89acacf118c4b4a88197","5f7b9178b21c5c407f04b05abeb8eef3fba1db27391ecef8827c612ae7f2a799",113,36,113,"3009a8d47ba5b845e7e20e4b9212d0bd01dd3cd5cc16fc5c07142b2bfa303a95",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxB",0x800C,384,188,55,187,"b259b0cd267d12e830664d4203c308fcf530e64edc17df5fb3624d8c03e79aa1","9520fb77468952279fc12e8a52bee91d17c0b9b298069d64dacf9cf535dea551",188,57,188,"b259b0cd267d12e830664d4203c308fcf530e64edc17df5fb3624d8c03e79aa1",true),
            new SourceFact("RockGfxB",0x8010,512,276,59,276,"45e8687ab9923e279c75086112cda613ef618fc4d62e0c616ca584806f2426f0","3a88b003946cfc4939dfab9cc80a6db7e06fabd2bb387e35751038cec6cde71e",277,61,277,"cb65437da6df84b7cbdda53e0d7208e177ec4caacdf509b2b051072f83c083c0",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"2f1310eacde10f2bebac92ee48424ab6b6cf78b5370293c193dc48e2cdbb03a6",true),
            new SourceFact("RobotTransportGfxB",0x0017,736,434,92,434,"4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806","672db05e55714cc5efa6a3f2d5cc21fa455e8d031aebc9f7d4fc14aa001565d6",434,94,434,"4e655639807a4b3203e9b5e723e484bd95a38f832babb3f82ba44215d7d27806",true),
            new SourceFact("SinePlatformGfxB",0x8020,1024,436,70,435,"401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca","ba1cb917e5e0e1353b383f59e2f5ab60842d3b34de78202cf961d71e51fc82b1",436,72,436,"401dcbd11e18780c4381ff634c064b36013dffdfebb27b9ff2674db91a9f18ca",true),
            new SourceFact("CrackFloorGfxB",0x8010,512,250,53,250,"feb698ed7e976af97b7746f243640fab416fef73c83867a37e64f574ec3d5c28","7d1dd19603982419f71c4ce0e916da76fac8f771b3ce462394cb6fa7d2f664da",251,55,251,"7f2ab3e00167cc1ff2c789febb0bdd44028c4cec65fc7d9b1f72dbb01947f682",true)
        ),
        List.of(63320,63334,63426,63320,63506,63586,63594,63646,63654,63426,63426,63426,63426,63426,63610,63618,63602,63426,63626),12,15,List.of("StageGfx","BgInsideGfxB","BridgeGfxB","PlatformGfxB","ConveyorCtrlGfxB","RockGfxB","TitleCardGfx","TitleCardTextGfx","AnimalsGfx","SpringGfx","Spring45Gfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxB","CollapseFloorGfxB","ChainGfx","NoroNoroGfx","HologramGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxB","SinePlatformGfxB","CrackFloorGfxB","RobotTransportGfxB","SasoriGfx","HologramAnimalsGfx","BgOutsideGfxB","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23)
        ),
        List.of("Cycle0","Cycle1","Cycle2"),CycleProgramme.R5_PAST_BACKGROUND_CONVEYOR,
        List.of(
            new SectionRow(1024,2,5),
            new SectionRow(65535,4,6)
        )); }
    private static Facts factsR52C() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80BE,6080,4490,63,4489,"56a9499d48509edb9e2bafc19cd9f0eb7bbe15569c4ccb027881c97fe85b4423","d326748d13deccdfb660a84675572934b669e8711a9cc38efe7a2eb63e73c361",4490,65,4490,"56a9499d48509edb9e2bafc19cd9f0eb7bbe15569c4ccb027881c97fe85b4423",false),
            new SourceFact("StageGfx",0x82B0,22016,11308,105,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b",11308,107,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c",true),
            new SourceFact("BgInsideGfxC",0x805F,3040,1654,86,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1","b828db38ce1ebac8ff39ce7d8f72dfd1cca91e545afc8e6a3ed32c4f32f55d03",1654,88,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1",true),
            new SourceFact("BgOutsideGfxC",0x8061,3104,1662,93,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d","84898fece5dd9dcc6a1c12f0645119406fb1a4fcdc93727e42c9f708d7103643",1662,95,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d",true),
            new SourceFact("BridgeGfxC",0x8004,128,124,43,123,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5","0b6fdedb84f512951a49d1064294a66c6e93d89a931360a89296b28ec3cca9be",124,45,124,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5",true),
            new SourceFact("ConveyorCtrlGfxC",0x800F,480,369,79,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7","36fa0fd666f14a4874a03d800f9f586de0b8dbb4f8a098d91d1a359cf816a982",369,81,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7",true),
            new SourceFact("PlatformGfxC",0x0028,1280,830,113,830,"7c453665e68f5254d074e2720930aa683f62e5aafddebb784559febf8bf24597","94a79b97e6f58b0179e91513191672e6ebc9e99fe08b247e05ff5c709a0bea03",831,115,831,"c8c7c3c821ec2bfd107058b5fc3df3b299f59900055e699ddd3b08dfa4647840",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxC",0x801C,896,459,59,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c","bd5f7c43dfe9271327d5eb9cd71bbe68a552aac7464406214781e7bbd417741c",459,61,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxC",0x0008,256,124,36,123,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce","7a331bfaaaa5e5e9b144e989e67c016f5bc0353735b95d9f8232228cf36d9159",124,38,124,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxAC",0x800C,384,196,40,196,"e67316fdc91db9039c6b1220f56fac812d9c5e0fc208f641688b4818a861aab8","04393ed9dc951e47a0265d881e03317c707f50957fe951bb180a2ebaf7223fb8",197,42,197,"61de9b480ce3688f840b8fb470631e565852ca66fe6b22c3be94943527230b30",true),
            new SourceFact("RockGfxC",0x8010,512,286,61,285,"b23173590b0cd7dbe737505ffa0c210b489bcad78a5e9b7a2f82f63e218acfa6","0cff4757eebe52d0e2d56b3893802653126d2e5986fa97314cf72c133279df31",286,63,286,"b23173590b0cd7dbe737505ffa0c210b489bcad78a5e9b7a2f82f63e218acfa6",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"fc524022cf7edd6348e3d8123c954623f9882944d6920cfc5092371cecc8211c",true),
            new SourceFact("SinePlatformGfxC",0x8020,1024,495,83,495,"86b55ca4393cc80cf8508d81022bcec3c705a0ce673722af6a953e291af47773","13b287f0cfd68b798f1d0f74c47f29e791da2bda0031fad0879096a769e3eb54",495,85,495,"86b55ca4393cc80cf8508d81022bcec3c705a0ce673722af6a953e291af47773",true),
            new SourceFact("CrackFloorGfxC",0x8010,512,172,36,172,"953d061880f96ee9cf22ad807a026fe2b5c406b2d1d522967c4b7fc862f055ed","c406f66f445a0ca64530b3491d056ad914c1a3d983d5533397fc51b55b15ff9e",172,38,172,"953d061880f96ee9cf22ad807a026fe2b5c406b2d1d522967c4b7fc862f055ed",true)
        ),
        List.of(63096,63110,63196,63096,63196,63196,63196,63196,63196,63196,63196,63196,63196,63196,63284,63292,63276,63196,63300),7,14,List.of("StageGfx","BgInsideGfxC","BridgeGfxC","PlatformGfxC","ConveyorCtrlGfxC","RockGfxC","TitleCardGfx","TitleCardTextGfx","SpringGfx","Spring45Gfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxAC","CollapseFloorGfxC","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxC","SinePlatformGfxC","CrackFloorGfxC","AnimalsGfx","BgOutsideGfxC","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(1024,2,5),
            new SectionRow(65535,4,6)
        )); }
    private static Facts factsR52D() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",true),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",false),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",true),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",true),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",true),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x80B7,5856,2556,95,2556,"dc9b2811fa82af652984f7551c824c6c92112f2c334bc0911ad880794f227b10","4f4b4870ea8d20d38d5365181ba45c945c5199ae53e34ff148a20ea201665329",2557,97,2557,"226cf51e9555acadcce091312316bf2897e712dbb654963ae41c09dc56b8b868",false),
            new SourceFact("BossGfx2",0x810A,8512,4106,101,4105,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d","357b58b3e68a9575d6aab35209afbc4f4ecb04176fc9f859e3c7d7702a884d3b",4106,103,4106,"42b736562a7086651a1da578b6319db0acbf258df219fd69c6d65a7c9ddd621d",false),
            new SourceFact("StageBlocks",0x80C6,6336,4738,65,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a","01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26",4738,67,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a",false),
            new SourceFact("StageGfx",0x82B3,22112,11854,109,11854,"dcb3fa802fad98f566f909f7dfbc136aa2232cd40e784b79c4ebde94e5358a30","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf",11855,111,11855,"9a0f1f89560843e6a3cdfe65adfbb6b19f558ca4d3f9017ea865adba6f66a93a",true),
            new SourceFact("BgInsideGfxD",0x805F,3040,1794,111,1792,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5","9d18ee1f35e4d74d2ba5370e92d303120afbbbb77fcd9239f696106e8dfcf35f",1794,113,1794,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5",true),
            new SourceFact("BgOutsideGfxD",0x8057,2784,1478,63,1477,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699","a75c4a185336b89f67a9d18a6eab39b1737e7f71ffae8abeb4a65e6208008aea",1478,65,1478,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699",true),
            new SourceFact("BridgeGfxD",0x0004,128,116,38,116,"b4221a4cb7ac24cbb8cceea42cc8b241b191e9442f41d6284bcecdd9116d6524","e88a09b55ef60fd34b887dbbfd0d878485fbf9b184e5d62f3b8bb275dfa6498a",117,40,117,"6ee13f9ee529e3cf3522aa758b910d59cc9c16b36ab0ee0a1f9bd424303b3333",true),
            new SourceFact("ConveyorCtrlGfxD",0x800F,480,349,79,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc","73107202079a2d510c6b127f224c8474f7f39df167d5d31539676e503a577301",349,81,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc",true),
            new SourceFact("PlatformGfxD",0x0028,1280,784,99,784,"115a2a224145a64ae990756b953ffcbf891954266b3856d693d9a2a2abdb0f0e","72583167b72e6b2f50aaa16df60c7acc69eaea381d36836e3d89c3dada837f01",785,101,785,"e3d1df2bf947233989f0a674f42481edcba51db1cf35ce997b63523a3a1ae861",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",true),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxD",0x801C,896,408,83,407,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30","e44bb6f92ffaef293651caa05ddd27e581e0e067320b6f5a2101a120f6f0f536",408,85,408,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30",true),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",true),
            new SourceFact("CollapseFloorGfxD",0x8008,256,112,34,112,"c75cc362a60b81e6968c5adc66c6d059734dbb690de2775b4be130c6f0c56fe3","a8ed121a44326fe64d05dd06835fc51e2d6189caaf136d1a09376f7f54bab83a",113,36,113,"8da74604df1662e4a8fdf32d5fc7e9dd088da392492435be1a5eda1b7b51cc25",true),
            new SourceFact("SpikesHV4Gfx",0x8020,1024,370,69,369,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941","b5cfbdca94a689be5ebd7e2527e60f10bd2eb1120c1c5915bb854f83e4954caf",370,71,370,"06349704636cb711fd4d4b598ef4d960435c47dde8627a293dfd635caeed3941",true),
            new SourceFact("StalactiteGfxD",0x800C,384,278,75,277,"e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd","c643d5d7d93a2341e31eb797fca20763da4144073c342ce8f7e42a4c3635defc",278,77,278,"e8f676cd384e334b075623ad57a9f4ec4dc8447eded16d49d3de18d9cda783bd",true),
            new SourceFact("RockGfxD",0x8010,512,256,52,256,"0578a33bff801847439bd1321fbbb3873f825a72de869789864298992e7180dd","fc95595d62e27e83d4f24f5fa8f0379d62934c02e698ea5d5f4592b72507efe6",257,54,257,"7b85a6f3756693b30a4f1129879324dc14c77a919370859d5718ec7ce2553e1e",true),
            new SourceFact("KumoKumoGfx",0x8037,1760,923,91,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef","c1a612adb8d96362259e9bf942e76676b20c1fa76ca6b5ba443e46bbfde2181b",923,93,923,"c0f1322a12c7ecbdbb2843f4cbdf169f38b49d7b5d319cba575fa46044a633ef",true),
            new SourceFact("KemusiGfx",0x8016,704,438,83,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892","c8ac7b88ad0228a4e1ecd747035513290988adc485b96317a55858d5ecbe9037",438,85,438,"2f44661dc36059834cab9e07c43449547d34c1b2fbcae77f19fa88bde945c892",true),
            new SourceFact("SasoriGfx",0x8048,2304,1174,95,1174,"b60f09fcc26f0d1a2cee5c36e96aa8951f2d35ece14acfad13c9de322d7564ef","8b4cb33dc26482af4aeafd1b78c26c79a2e477c02f367e1cb1e1c0e86f9a07e8",1175,97,1175,"12e4154e64a3f84c90e28c782fd6795d02c0579d8c7f0f6d040fc5599d228397",true),
            new SourceFact("NoroNoroGfx",0x8026,1216,788,83,788,"8b282918cf923213f5899c14a37497c19b582fdc06ab74d71e5584cfa2310001","ff79390749d1a91f4aac5141a35097cf664d705b8f3183951ce8fc397c39b0ee",789,85,789,"fc524022cf7edd6348e3d8123c954623f9882944d6920cfc5092371cecc8211c",true),
            new SourceFact("SinePlatformGfxD",0x8020,1024,460,77,460,"ed5988bf0619f46577a62cd7cf60782055f1747995567d085891bfc4c9123b0c","bd4b21660730d36fa84135eb3236c49f148f639f6cdabf1c5b0e9e57047333e2",461,79,461,"511c46bb1b9747468bf18a3c6d5710c1a6f1818a38369a57bc20223b66105c3d",true),
            new SourceFact("CrackFloorGfxD",0x0010,512,282,56,282,"cd5805de0d82bdc5e578cd095f77519a6c6d06ac198038e700b6fb4a28f517be","d533fc724751cd74522c2d86ed194cd8f2878e372869990324756d88b0b72862",284,58,284,"b4daf4bb431dbbb6910a388f01d1ff388cbeebc72d197e787542680311a028e7",true)
        ),
        List.of(63344,63358,63444,63344,63444,63444,63444,63444,63444,63444,63444,63444,63444,63444,63532,63540,63524,63444,63548),7,14,List.of("StageGfx","BgInsideGfxD","BridgeGfxD","PlatformGfxD","ConveyorCtrlGfxD","RockGfxD","TitleCardGfx","TitleCardTextGfx","SpringGfx","Spring45Gfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","GoalGfx","StalactiteGfxD","CollapseFloorGfxD","ChainGfx","NoroNoroGfx","SasoriGfx","KemusiGfx","KumoKumoGfx","SpikesHV4Gfx","BreakWallGfxD","SinePlatformGfxD","CrackFloorGfxD","AnimalsGfx","BgOutsideGfxD","ResultsGfx","SignpostGfx","BigRingGfx","BigRingFlashGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1","Cycle2","Cycle3"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(1024,2,5),
            new SectionRow(65535,4,6)
        )); }
    private static Facts factsR53C() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",false),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",true),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",false),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",false),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",false),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x809F,5088,2322,99,2322,"935ab5edfcfd22dc333a62e7d7d1383f5e42245138e88639c20077457d471184","4216cc9aa8ac0c4bd2222541c8b0a76d295f549a44202131f1093df40f21a3c9",2323,101,2323,"08b125f698f0f71c04dd14a4a4e56deb865c9d938fd412f94d335fdd34603ba0",true),
            new SourceFact("BossGfx2",0x810A,8512,4112,99,4112,"795105072fec0415c6beebe014abb29c0c9e64d6ae07d1b77146a833d872c92b","30e17bff1e9bbf36ae64f1c7c4cd10094fae47e473673378f2d9e3992c254961",4113,101,4113,"0814190e7bc2456d51de4fc10847d7a2064b76fbbfd74ad5f7bf2708a8745b3e",true),
            new SourceFact("BossSpikesGfxC",0x8018,768,512,81,511,"7c294ee4cde2812817a18798c2227899362b2974ce2f24f1d01554c36871b752","467e496d767df221952a45ea0308dafd1fbbd6f3e10bb8a541622d77a6339100",512,83,512,"7c294ee4cde2812817a18798c2227899362b2974ce2f24f1d01554c36871b752",true),
            new SourceFact("StageBlocks",0x80BE,6080,4488,63,4487,"5989d8278c4635cd0c729bc08e1a3f279ee13ffe5629710105c50f146bb87874","cb9e2086805cac16084f0c53b31b5f13bd8548025a099bad8193a25a2c536a4d",4488,65,4488,"5989d8278c4635cd0c729bc08e1a3f279ee13ffe5629710105c50f146bb87874",false),
            new SourceFact("StageGfx",0x82B0,22016,11308,105,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c","853c75234035355c460741c3ef267079f69f115290c316d90c5600e8b7d5e87b",11308,107,11308,"fb02d382439b8d94a2f8bd803229771cf158c7e8edd4fca9f8d3fc24b70f9f9c",true),
            new SourceFact("BgInsideGfxC",0x805F,3040,1654,86,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1","b828db38ce1ebac8ff39ce7d8f72dfd1cca91e545afc8e6a3ed32c4f32f55d03",1654,88,1654,"708738f8d35f79c7f31e26608f7f4be84701226926276c406510ec3f13239ae1",false),
            new SourceFact("BgOutsideGfxC",0x8061,3104,1662,93,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d","84898fece5dd9dcc6a1c12f0645119406fb1a4fcdc93727e42c9f708d7103643",1662,95,1662,"7e994fd3efff2a62d4813fa921c4bcfe703ab876b0d40c20ae984a19172fc11d",true),
            new SourceFact("BridgeGfxC",0x8004,128,124,43,123,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5","0b6fdedb84f512951a49d1064294a66c6e93d89a931360a89296b28ec3cca9be",124,45,124,"b92da5a58384dc7b4421d85514dc36317e2f0fa7b2d47b800ed50462160173f5",false),
            new SourceFact("ConveyorCtrlGfxC",0x800F,480,369,79,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7","36fa0fd666f14a4874a03d800f9f586de0b8dbb4f8a098d91d1a359cf816a982",369,81,369,"aa3a55cd15c7ad09eac69880e1e88ab0aead810dd37c1fcf3b9bee47b30835a7",false),
            new SourceFact("PlatformGfxC",0x0028,1280,830,113,830,"7c453665e68f5254d074e2720930aa683f62e5aafddebb784559febf8bf24597","94a79b97e6f58b0179e91513191672e6ebc9e99fe08b247e05ff5c709a0bea03",831,115,831,"c8c7c3c821ec2bfd107058b5fc3df3b299f59900055e699ddd3b08dfa4647840",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",false),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxC",0x801C,896,459,59,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c","bd5f7c43dfe9271327d5eb9cd71bbe68a552aac7464406214781e7bbd417741c",459,61,459,"f6dc2a7ce75739ae6991675a6bd146c6290e38ab09221c0716ccc796480ec59c",false),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",false),
            new SourceFact("CollapseFloorGfxC",0x0008,256,124,36,123,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce","7a331bfaaaa5e5e9b144e989e67c016f5bc0353735b95d9f8232228cf36d9159",124,38,124,"f8af5d8d14e89f32e67dd6222cffc835e063f49130dee227bd461be5fad7c5ce",true)
        ),
        List.of(65048,65062,65124,65048,65150,65124,65124,65124,65124,65124,65124,65124,65124,65124,65124,65124,65164,65124,65172),6,10,List.of("StageGfx","BgOutsideGfxC","TitleCardGfx","TitleCardTextGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BossGfx1","BossSpikesGfxC","PlatformGfxC","CollapseFloorGfxC","BossGfx2","CapsuleGfx","ResultsGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(6144,2,5),
            new SectionRow(65535,4,6)
        )); }
    private static Facts factsR53D() { return new Facts(
List.of(
            new SourceFact("BigRingFlashGfx",0x804D,2464,384,36,384,"6106d2692b34f73a6d928e651c9547e5dfe2dc00bdb0b4136dc42e603ce0efa7","39e7bb895c1492e9847de7a67ddc61108dc553b9443ad17d61ea5c8b47fdf8b6",385,38,385,"e59af60b6dd221ca0b6c993894db9e69567814126686e9f2270bba02e2adf25b",false),
            new SourceFact("PointsGfx",0x0005,160,101,29,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176","2270d6453b2d2526412afab1ba19c3cf175b571a41766dc4fe0da422283d57db",101,31,101,"9c01f8d17db4a5a30267f66d3c1e13d17223af14c6312963b69ab36fb9356176",true),
            new SourceFact("CapsuleGfx",0x8068,3328,1704,99,1703,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808","b61c6d944dce535ccb74d0a374ac873e95a5e0b3911b465f62bb025b03660236",1704,101,1704,"da055a9f3c79f60b0bf6a784e89785bdc65eb37c7a598d95cf6a8f5fdc9ab808",true),
            new SourceFact("BigRingGfx",0x806A,3392,1034,59,1033,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6","31cec10e677637f390c0a2b25d09f4ee9e3fe39f651801546e69b13150cee70c",1034,61,1034,"5bfa84d7d4335e190e3287337f1595b3808fd1d3592615bfbc684d9a2bbdebf6",false),
            new SourceFact("GoalGfx",0x8018,768,448,86,448,"24aac969ce8508df0f412a0ad98d87102adc2029b3ae1f28b972eb7b6646979f","68359c6e3dc65e9cf72db3d20ce887acc3728fdd32de20abc1fd5af185ff4c9a",449,88,449,"1ba1c7e9a34a071c8786cc2eb94eaf2adf0e58b480ef8d62f6501bb7f3006250",false),
            new SourceFact("SignpostGfx",0x804C,2432,1500,115,1500,"2fb00eeee801d1f46cc06eae234fb15fa3dd984f325315c430f13d1e0d3fac67","7ffb01a6c7ffc333ade235f0c945561970c5b98eb0904a15fec3c164fc8ccb7c",1501,117,1501,"c44aa3a5ef0fe775973ebd9f915c6b78b521a2107b4b69af6fa182de1a152c1d",false),
            new SourceFact("ResultsGfx",0x8078,3840,1354,65,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40","fdb13e161a5862fe4f8c550291bba36589028b8a7cc0394638c019a0ab35760d",1354,67,1354,"c88b40a5d2d17ddd96fb2b28dbdbdbe5f1a67f7b02671c6410f7d64a306c3c40",true),
            new SourceFact("TitleCardGfx",0x806D,3488,940,71,939,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8","68b70b1e8189073c24642b8939420ac4d3c3ba1d6a1c9c00d6538390d1764f7a",940,73,940,"8d70c7bedf08d9d0c4a4d01290fb78032820933ab43d1636262e16e601347bc8",true),
            new SourceFact("Spring45Gfx",0x002E,1472,780,75,779,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a","1ae40f48422bc8b884ae4c85288d3f363737cd3e6979c458b93aac15ed581c8b",780,77,780,"3cf30cb03a63f3d160c0b2dbbd0cf1f7803871f860125a5b44bf0a1cc0dc362a",true),
            new SourceFact("SpringGfx",0x8024,1152,547,80,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847","8e3c6d91a395d24872b4ae5da293cd21d8e68230687ef9f07feb62ec2612f416",547,82,547,"52aa905cb632245c5922a198776a7ec46936102616db34a002648b45132a9847",true),
            new SourceFact("MonitorTimeGfx",0x8052,2624,1541,105,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902","7575dcf197dbc2f246838cfedcb95af40100d449f0a0c5a48b7294c27c9a924f",1541,107,1541,"a11f69add660dbe82c86dd3227efe6154dc2de2f3ac3f82f7806fb3698a0b902",true),
            new SourceFact("ExplosionGfx",0x0046,2240,1256,89,1256,"1f642f6b44332f7ffa63d84970ff1abbacd72bbbd400417c5b3800215d7ec5c1","434f2415b56d7623e57fd94533506804d7ee1fe3a0321f77bfe86f58d8ff6664",1257,91,1257,"c37077d4a4bf8aab8c50bd454137dba54a054c5a756b0f3a00515468d81fcdfe",true),
            new SourceFact("RingGfx",0x000E,448,244,46,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2","c1f35c87c98d27990926ad515264ab1e8a5c9385b087931d79ff24709afd6abb",244,48,244,"530877e432ceab7df543eaae5d3a6f705137526f54cdae8251b7fec288119ea2",true),
            new SourceFact("HudGfx",0x801B,864,300,49,300,"d3df1675439d1bb043f747ba8d3a975b31af8d423a09df30da9a9cae22084deb","0a448ef2c11a1414886684523d551d65af43e4f0c02e08c73e869437b15b40fc",301,51,301,"fb31ef2bca6df6e547b6e578c1455730a6665226befb550fd6c03e2a9a1a88b5",true),
            new SourceFact("CheckpointGfx",0x800C,384,212,51,211,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf","b376ac203a8c3cf754d2d9bcc4a55dbcb287ead82bbb304b19b7fb5181f2c3a3",212,53,212,"e6af441ac5e051484bcd2993ad2736262b9c8f9a379bc714711e045f3f83e7bf",false),
            new SourceFact("FlowerGfx",0x8027,1248,690,66,689,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda","6f6d9b6ae046e2b8a8c621de7beed33656d81c7dbee0bc2db20baeec923715a4",690,68,690,"52c0a547b6869abde441c66cb2699754b55fc97a60602c05b928161b76935cda",true),
            new SourceFact("TitleCardTextGfx",0x8049,2336,330,59,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51","21eaa229fc5357af7b81447ed40820cc5cacb719d28b156161079dec69bca3c3",330,61,330,"b66b881482f03265023fac4806f908bae6e66a6a3bdd71df0edeab46b1d2cb51",true),
            new SourceFact("BossGfx1",0x809F,5088,2322,99,2322,"935ab5edfcfd22dc333a62e7d7d1383f5e42245138e88639c20077457d471184","4216cc9aa8ac0c4bd2222541c8b0a76d295f549a44202131f1093df40f21a3c9",2323,101,2323,"08b125f698f0f71c04dd14a4a4e56deb865c9d938fd412f94d335fdd34603ba0",true),
            new SourceFact("BossGfx2",0x810A,8512,4112,99,4112,"795105072fec0415c6beebe014abb29c0c9e64d6ae07d1b77146a833d872c92b","30e17bff1e9bbf36ae64f1c7c4cd10094fae47e473673378f2d9e3992c254961",4113,101,4113,"98b40653e51d31faa8119a7a4156626e90d93ab59e950205a3d9aa6a7abc71a9",true),
            new SourceFact("BossSpikesGfxD",0x0018,768,488,87,487,"c7e410ddb4b023caba547f1f5987935b24cf7125646a0a063869027362482c50","efee439031da21b474835f3c8dc2d7cc6f03526b9f6f89fd5d53d4410d59e4b2",488,89,488,"c7e410ddb4b023caba547f1f5987935b24cf7125646a0a063869027362482c50",true),
            new SourceFact("StageBlocks",0x80C6,6336,4738,65,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a","01edc91b0b03a47dc717d92498da7225529601222786d79adbf457e2ff5c5d26",4738,67,4738,"6b0798a393e9a921e16e54b1956492c5abe633b2f82f5e9fc61b5ce166abdb7a",false),
            new SourceFact("StageGfx",0x82B3,22112,11854,109,11854,"dcb3fa802fad98f566f909f7dfbc136aa2232cd40e784b79c4ebde94e5358a30","d90e2b1e27fa3b605f69db2237c88539afcbdf0dc8798500a256be88db1829cf",11855,111,11855,"9a0f1f89560843e6a3cdfe65adfbb6b19f558ca4d3f9017ea865adba6f66a93a",true),
            new SourceFact("BgInsideGfxD",0x805F,3040,1794,111,1792,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5","9d18ee1f35e4d74d2ba5370e92d303120afbbbb77fcd9239f696106e8dfcf35f",1794,113,1794,"6333dafc85e4e765eb34fb248e3be5bc2dae897b510ba682cbdcac9b311f8da5",true),
            new SourceFact("BgOutsideGfxD",0x8057,2784,1478,63,1477,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699","a75c4a185336b89f67a9d18a6eab39b1737e7f71ffae8abeb4a65e6208008aea",1478,65,1478,"e1cfc948d2f8c45b4af496472d5145792c2ac368d59ec3e57e0145df6164d699",false),
            new SourceFact("BridgeGfxD",0x0004,128,116,38,116,"b4221a4cb7ac24cbb8cceea42cc8b241b191e9442f41d6284bcecdd9116d6524","e88a09b55ef60fd34b887dbbfd0d878485fbf9b184e5d62f3b8bb275dfa6498a",117,40,117,"6ee13f9ee529e3cf3522aa758b910d59cc9c16b36ab0ee0a1f9bd424303b3333",false),
            new SourceFact("ConveyorCtrlGfxD",0x800F,480,349,79,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc","73107202079a2d510c6b127f224c8474f7f39df167d5d31539676e503a577301",349,81,349,"4144c58f06c6830c7a870f95412e1bb62b36b8328c9566d46f452240f20ae1cc",false),
            new SourceFact("PlatformGfxD",0x0028,1280,784,99,784,"115a2a224145a64ae990756b953ffcbf891954266b3856d693d9a2a2abdb0f0e","72583167b72e6b2f50aaa16df60c7acc69eaea381d36836e3d89c3dada837f01",785,101,785,"e3d1df2bf947233989f0a674f42481edcba51db1cf35ce997b63523a3a1ae861",true),
            new SourceFact("AnimalsGfx",0x0014,640,446,87,445,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab","ac1edab54e2b767578c6b3c2b828dc9821ef02c7a3bed9079dc7dded4a26e8d1",446,89,446,"63ae88feb8fcd2bb07d896715dc6a0974d289947114ab25064d43fea1e7b52ab",false),
            new SourceFact("HologramAnimalsGfx",0x0014,640,434,83,434,"61295f839e825617673137b4861d791d0361d7a6b3e465b1ad4545497b3edfef","608ee7b58e8aae521fda3eb07b348c8027cbdac0f5046081df1d0f6930606a3a",435,85,435,"51ff276f6591e7285a1243e6c010b5bced9b5bacfdd3e1e12990c2c43bc702df",false),
            new SourceFact("HologramGfx",0x0033,1632,1024,106,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5","4f1061c0e3a9256e6fbdfe721e35b0021c91909a52815036c6456498ac2b7d77",1024,108,1024,"1df8b669f556ef15e4c3c687bc890f5e037c90e5366097db036aff136a6c45e5",false),
            new SourceFact("BreakWallGfxD",0x801C,896,408,83,407,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30","e44bb6f92ffaef293651caa05ddd27e581e0e067320b6f5a2101a120f6f0f536",408,85,408,"090050bf83f9b1f9235a1ec48132bbfc462b50fe7af6a46ea59d7039bda29c30",false),
            new SourceFact("ChainGfx",0x0008,256,162,39,161,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b","d8ec3695235800e3fcc5bc7be45ac463125cac4852ef2dc2885ef41a97e1ab87",162,41,162,"48d66771ed0561c49dace9415db18cc5289f98cfffa5462f7099dabf2d18888b",false),
            new SourceFact("CollapseFloorGfxD",0x8008,256,112,34,112,"c75cc362a60b81e6968c5adc66c6d059734dbb690de2775b4be130c6f0c56fe3","a8ed121a44326fe64d05dd06835fc51e2d6189caaf136d1a09376f7f54bab83a",113,36,113,"8da74604df1662e4a8fdf32d5fc7e9dd088da392492435be1a5eda1b7b51cc25",true)
        ),
        List.of(65268,65282,65344,65268,65370,65344,65344,65344,65344,65344,65344,65344,65344,65344,65344,65344,65384,65344,65392),6,10,List.of("StageGfx","BgInsideGfxD","TitleCardGfx","TitleCardTextGfx","Spring45Gfx","SpringGfx","HudGfx","MonitorTimeGfx","ExplosionGfx","PointsGfx","FlowerGfx","RingGfx","BossGfx1","BossSpikesGfxD","PlatformGfxD","CollapseFloorGfxD","BossGfx2","CapsuleGfx","ResultsGfx"),
        List.of(
            new PaletteFact(0,64256,31),
            new PaletteFact(1,64256,31),
            new PaletteFact(2,64256,31),
            new PaletteFact(3,64256,7),
            new PaletteFact(4,64288,23),
            new PaletteFact(5,64288,23),
            new PaletteFact(6,64288,7)
        ),
        List.of("Cycle0","Cycle1","Cycle2","Cycle3"),CycleProgramme.R5_FUTURE_BOSS_GATED_CONVEYOR,
        List.of(
            new SectionRow(6144,2,5),
            new SectionRow(65535,4,6)
        )); }
}
