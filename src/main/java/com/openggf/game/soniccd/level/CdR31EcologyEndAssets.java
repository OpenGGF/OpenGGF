package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import java.util.*;

/** Finite P6 operand adapter; no media reader, source identity minting, art service or World admission.
 * Root supplies the actual authenticated source's finite views. These checked content/operand values
 * cannot prove their custody or original invocation. Independent A/B/C/D roots are never inferred by delta.
 * Native N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Exact provenance and consumer observations:
 * E/research/ccz-ecology-stage-closure-20261107.md. */
public final class CdR31EcologyEndAssets {
    public enum View {
        CHECKPOINT_MAP(66,"709d0648dfb32425f46281c236ededaef80750fc6250062f3bce77f9427a446f",0x3a84c,-1,-1,-1),
        CHECKPOINT_ANI(6,"9034c893821cd15055e34cddb2388653690958995d7118f7c78896e6ccb5c551",0x3a846,-1,-1,-1),
        FLOWER_MAP(90,"48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41",0x336d8,0x336d8,0x336d8,0x336d8),
        FLOWER_ANI(28,"74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9",0x336bc,0x336bc,0x336bc,0x336bc),
        BIG_RING_MAP(175,"3885f272ff1d200754078015536c48c499f12f5585ec89d41c3fd205c702aa66",0x3a896,0x3b030,0x3d760,0x3d68e),
        BIG_RING_ANI(8,"b8997affb40b5e6a6e2aff4f5013c56226e1ce0ff2d25c8396f6193939ba1ba6",0x3a88e,0x3b028,0x3d758,0x3d686),
        FLASH_MAP(416,"b48a9c17b2359210ba747bf79d3c07a7af111639eef836d7315811b285226e10",0xdb30,0xd992,0xdac0,0xda70),
        FLASH_ANI(12,"b4dbc8e16c7b27313dd1d0443cc0a9f6226470e751fd826adaac47dcc12a4d0f",0xdb24,0xd986,0xdab4,0xda64),
        CAPSULE_MAP(210,"9b514ca72871299d60d279df88ee8a6e428fa7025237865c73f15f3c8257a646",0x3aa06,0x3b1a0,0x3d8d0,0x3d7fe),
        CAPSULE_ANI(52,"1712d58acc2f897eebe01c51d763c0808aae416f5484fbf96d520c1857725ed9",0x3a9d2,0x3b16c,0x3d89c,0x3d7ca),
        SIGNPOST_MAP(125,"36fb57cd5827145b5516dcaaf6e4c2c43f975b6825bd0451e4ad1ab5136bffec",0x3a954,0x3b0ee,0x3d81e,0x3d74c),
        SIGNPOST_ANI(13,"744a08e8e193ac3c6115b9a760327b49a363c2404ac5a9f40a0b41f306fe2686",0x3a946,0x3b0e0,0x3d810,0x3d73e),
        ROBOT_MAP(574,"69588f2f53733d8a90cf9c1cd3d06f6c5158a507839be0b5eee13d9a957d8e59",0xeb46,0xe178,0xe2a6,0xe256),
        ROBOT_ANI(8,"cd243d872bccdc1c849dab79119738c4f44de8739f702aea6fd401e5cd2d119e",0xeb3e,0xe170,0xe29e,0xe24e),
        ROBOT_TILES(22,"ceaee5afbf205a5d45302bf0c5eea2be4ad1958e0fcb0518cc16ada97e471d69",0xedc4,0xe3f6,0xe524,0xe4d4),
        PROJECTOR_MAP(68,"2e180977b04b20230e5aced68385346b6f992bed19139df617477bd30780cda3",0xf09a,0xe6cc,0xe7fa,0xe7aa),
        PROJECTOR_ANI(68,"2b431fb6fc08fbccef96299a1c83c99327b680924c8420b2eb56ef98eff4aa6f",0xf056,0xe688,0xe7b6,0xe766),
        ANIMAL_FLY_MAP(19,"9ccd8926c887d4851d583d9bbd82937efe3a10bfa873f89be43aa65c221b175f",0xe924,0xdf56,0xe084,0xe034),
        ANIMAL_FLY_ANI(66,"57a637fefafe6cb7ee812d34139509586a6cf63ec27ca920746a2013fd5981b3",0xe8a6,0xded8,0xe006,0xdfb6),
        ANIMAL_GROUND_MAP(35,"71546dc77cda1f22834ef83a2b9ecd4837e25a378d19e5fff24c10f9f0ec9cc2",0xe938,0xdf6a,0xe098,0xe048),
        ANIMAL_GROUND_ANI(60,"b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4",0xe8e8,0xdf1a,0xe048,0xdff8),
        RESULTS_MAP(621,"f6c8b2d7fb6daadaff8d8111268383b7a6a68ae2ca65fdbcfc51339e409d3621",0xf6f8,0xed2a,0xee58,0xee08),
        GAMEOVER_MAP(56,"ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b",0xf262,0xe894,0xe9c2,0xe972),
        EXPLOSION_POSITIONS(16,"2741b21af7a576b53e2d8c5bc1d356f2ab360662c03654658bc60083125b02f1",0xd546,0xd3a8,0xd4d6,0xd486),
        SEED_VELOCITIES(22,"826f8fb8b7b2e24319abfa2ee4631e03a8a611e7158c6f02954ea4fb07cadd59",0xd5ba,0xd41c,0xd54a,0xd4fa),
        GOAL_TILES(112,"82fd77609648d39ac2f50bc4c0e3762fccf3ef0986bf6bc8cef7f4cdeb3b3724",0xd8d8,0xd73a,0xd868,0xd818),
        TIME_BONUSES(42,"be40c9a172da6d18e694f0b8cfe698fa674eb88ab210958f4fa82713e0cf0861",0xdac2,0xd924,0xda52,0xda02),
        ANIMAL_TILES(22,"516ed0365c6703f3ffdf3f7a27809c5a5746e35ca85c93e45ec83344bd117c8e",0xe95c,0xdf8e,0xe0bc,0xe06c),
        PROJECTOR_SCHEDULE(38,"5830294609ca462a39688a5a11982198b43d6b04058c4335d0b9d923064a74a3",0xf0de,0xe710,0xe83e,0xe7ee),
        ROBOT_SCHEDULE(64,"1827c7470bb98b70240de9237aeb30aec8841ae3cc1f2e7f51332b3b18155123",0xed84,0xe3b6,0xe4e4,0xe494),
        RESULTS_INIT(24,"8fdebd3a15a4a74f401c329bbbaf9750596828e9578df9121565cd24f4c165f8",0xf6e0,0xed12,0xee40,0xedf0);
        private final int length;private final String sha;private final int[] offsets;
        View(int length,String sha,int a,int b,int c,int d){this.length=length;this.sha=sha;offsets=new int[]{a,b,c,d};}
        public int length(){return length;}public String sha256(){return sha;}
        public int fileOffset(CdNativeFile file){int n=offsets[variant(file)];if(n<0)throw new IllegalArgumentException("No admitted native view "+this+" in "+file);return n;}
        public int nativeAddress(CdNativeFile file){return 0x200000+fileOffset(file);}
    }
    /** Integration owner implements a finite view borrow, never an arbitrary-address reader. */
    public interface Source {CdNativeFile file();CdAssetBytes borrow(View view);}
    private final Source source;
    public CdR31EcologyEndAssets(Source source){this.source=Objects.requireNonNull(source);variant(source.file());}
    public CdNativeFile file(){return source.file();}
    public CdAssetBytes view(View view){
        view.fileOffset(file());var b=Objects.requireNonNull(source.borrow(view));
        if(b.length()!=view.length()||!CdR11aProfile.sha256(b.copy()).equals(view.sha256()))throw new IllegalArgumentException("P6 finite source view differs: "+view);
        return b;
    }
    private int address(View v){return v.nativeAddress(file());}
    private CdObjectVisualAssets visual(View map,int frames,View ani,int scripts,int tiles){return CdObjectVisualAssets.decode(view(map).copy(),frames,view(ani).copy(),scripts,tiles);}
    private CdObjectVisualAssets picture(View map,int frames,int tiles){return CdObjectVisualAssets.decodeStatic(view(map).copy(),frames,tiles);}
    public CdCheckpoint.Assets checkpoint(CdPlayerMath math){return new CdCheckpoint.Assets(address(View.CHECKPOINT_MAP),visual(View.CHECKPOINT_MAP,4,View.CHECKPOINT_ANI,1,12),math);}
    public CdFlower.Assets flower(){return new CdFlower.Assets(visual(View.FLOWER_MAP,8,View.FLOWER_ANI,4,39),address(View.FLOWER_MAP));}
    public CdBigRing.Assets bigRing(){return new CdBigRing.Assets(picture(View.BIG_RING_MAP,4,106),address(View.BIG_RING_MAP),view(View.BIG_RING_ANI),picture(View.FLASH_MAP,8,77),address(View.FLASH_MAP),view(View.FLASH_ANI));}
    public CdGoalPost.Assets goal(){return new CdGoalPost.Assets(picture(View.SIGNPOST_MAP,6,76),address(View.SIGNPOST_MAP),view(View.GOAL_TILES));}
    public CdSignpost.Assets signpost(){return new CdSignpost.Assets(picture(View.SIGNPOST_MAP,6,76),address(View.SIGNPOST_MAP),view(View.SIGNPOST_ANI),view(View.TIME_BONUSES));}
    public CdResults.Assets results(){return new CdResults.Assets(view(View.RESULTS_INIT),view(View.RESULTS_MAP),address(View.RESULTS_MAP));}
    public CdGameOver.Assets gameOver(){return new CdGameOver.Assets(view(View.GAMEOVER_MAP),address(View.GAMEOVER_MAP));}
    public CdRobotGenerator.Assets robot(){return new CdRobotGenerator.Assets(view(View.ROBOT_TILES),visual(View.ROBOT_MAP,8,View.ROBOT_ANI,1,23),address(View.ROBOT_MAP),view(View.ROBOT_SCHEDULE),address(View.ROBOT_SCHEDULE));}
    public CdProjector.Assets projector(){return new CdProjector.Assets(visual(View.PROJECTOR_MAP,6,View.PROJECTOR_ANI,2,51),address(View.PROJECTOR_MAP),view(View.PROJECTOR_SCHEDULE),address(View.PROJECTOR_SCHEDULE));}
    public CdCcAnimal.Assets animal(){return new CdCcAnimal.Assets(view(View.ANIMAL_TILES),visual(View.ANIMAL_FLY_MAP,3,View.ANIMAL_FLY_ANI,2,8),address(View.ANIMAL_FLY_MAP),visual(View.ANIMAL_GROUND_MAP,5,View.ANIMAL_GROUND_ANI,1,20),address(View.ANIMAL_GROUND_MAP));}
    public CdObjectVisualAssets capsuleVisual(){return visual(View.CAPSULE_MAP,9,View.CAPSULE_ANI,2,104);}
    public CdR13Capsule.SourceData capsule(CdR13Capsule.PaletteOperands originalRestoredPalette){
        Objects.requireNonNull(originalRestoredPalette);
        return new CdR13Capsule.SourceData(){
            public int mappingAddress(){return address(View.CAPSULE_MAP);}public int tileWord(){return 0x2481;}
            public CdR13Capsule.PaletteOperands restoredPalette(){return originalRestoredPalette;}
            public CdAssetBytes view(CdR13CapsuleAssets.View v){return CdR31EcologyEndAssets.this.view(switch(v){
                case ANIMATIONS->View.CAPSULE_ANI;case MAPPINGS->View.CAPSULE_MAP;
                case EXPLOSION_POSITIONS->View.EXPLOSION_POSITIONS;case SEED_VELOCITIES->View.SEED_VELOCITIES;
                case TIME_BONUSES->View.TIME_BONUSES;default->throw new IllegalArgumentException("Not a Capsule algorithm operand: "+v);
            });}
        };
    }
    private static int variant(CdNativeFile file){return switch(Objects.requireNonNull(file)){case R31A->0;case R31B->1;case R31C->2;case R31D->3;default->throw new IllegalArgumentException("P6 requires R31A/B/C/D original USA source");};}
}
