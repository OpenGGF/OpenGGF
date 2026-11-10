package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.CdLevelHud;
import com.openggf.game.soniccd.objects.r4.CdR4ObjectPorts;
import java.util.*;

/** Finite ordinary-object DATA facade on the SAME authenticated private programme reader.
 * Only actual USA R41A operands are admitted. Views/records are inert values, never an
 * invocation, lifecycle, clear, art residency, pool publication or capture authority.
 * Native N1ecc5f3a r4/object_index_1a.asm, r4/main.asm233-249, objects/{ring,hud_points,
 * title_card}.asm and objects/r4/water_surface.asm. No disassembly is read at runtime. */
public final class CdProgrammeObjectSource {
    /** A separate finite TT namespace: existing original R11 Table ordinals are untouched. */
    public enum Table {
        RING_MAPPINGS(0x954C,67,"cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a"),
        RING_ANIMATIONS(0x9544,8,"4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a"),
        WATER_MAPPINGS(0x6986,143,"d6d997aac288567a85b535663c9d6d368380a62a60d6cda8475b1ed4bfe39bc2"),
        WATER_ANIMATIONS(0x6952,52,"76020c801c5b32b9603b6d3e75499078c2a32f29c7af8d67d82a4706ed701bfe"),
        TITLE_MAPPINGS(0xBAEC,256,"be42bc93c57ec225bce3d70cf044f3c58105da08b55612e4c164d868f6e1dd0b"),
        HUD_MAPPINGS(0x9FF6,168,"7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3"),
        MONITOR_MAPPINGS(0x9CDE,450,"00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca"),
        TITLE_CHARACTERS(0xBAAC,64,"5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2"),
        RING_DELTAS(0x90E8,32,"35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86"),
        HUD_RESET(0xA32C,12,"3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c"),
        HUD_DIVISORS(0xA48C,40,"7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea"),
        WATER_OFFSETS(0x6950,2,"218213e6348972fd52dc7b9b62496fc81334384a69bd35cb0da6c4fd4399cee5"),
        DISPATCH(0x3EC4,328,"de69c43e282f9924d23d0f43aa7a17268ffe2e384d7d64d8d911a73dc1e2172b"),
        BUBBLE_MAPPINGS(0x1E9B4,183,"37900daee34193c729877724f417d2c853d7839af3dc23a0f3d63ac1614f9f05"),
        BUBBLE_ANIMATIONS(0xA852,47,"3bbe444a90eca2b61cefcaa3605009c21ced62c0ac43291063eed306d2b9df90"),
        BUBBLE_PATTERNS(0xA7FE,18,"7bdcf551a7b0145d264738caf905f7102a15a0960dd268c51aeddcfa5a432f17"),
        BUBBLE_PATTERN_READS(0xA7FC,26,"08cdd8715e1eb9e83fcb5c1633d80584934d3250f7dbb9f78448d784195594b5"),
        NULL_HANDLER(0x400C,6,"51b330f0a1d88e85ed0f569db0d036a89273aee193b510f46841bae307a33292"),
        HUD_RENDER_CODE(0xA0E6,0x500,"da5f77b7fd104cc556ccf8383804923b7c55ee5cb32f1f0ab5149c49f289a110"),
        TOUCH_DIMENSIONS(0x7502,126,"8794c61b2aa394d30f7e7fa44916529e4e1c0aca52166091fcbf20d01b3c1644"),
        ENEMY_POINTS(0x72F0,8,"75e523a6a18ea8818deee70a328935af02f65b890f34c7cf27b65f0a99e839e5"),
        BLOCK_SIZES(0xEDBC,14,"f70c3868408c7a5057ecabd63d0bf1e353ab8f5557b5f50d665b54ec810a17f2"),
        BLOCK_MAPPINGS(0xEDCA,111,"908a5ceb031420fe4c0ac0dc6fdfbdab8f2fa40de546da7b6fc710fe4b616fd3"),
        SPIKES_MAPPINGS(0xC1DA,16,"88bdf56813b465f06d5909b3026a7eb1cfbeabcd1bb5c9bb76eead7065040107"),
        TONBO_MAPPINGS1(0xD5D4,189,"e8dcd7b95175ec8ff31d6c3bac5a3afd83709b38a26414d5b3e952fdb4b48ab8"),
        TONBO_MAPPINGS2(0xD692,174,"831c3ac32faf8d04a7c49ac51ab36476c16deefe31506224bcda2646f59f6836"),
        TONBO_ANIMATION(0xD5C8,12,"402e2dd679fcfbfaa23046e9f4b789eab0a22722ff2ac7b059379e4b693c7578"),
        ANIMAL_MAPPINGS1(0xE56E,39,"7cdabdab21ec32c5aa5373a1e0e6b71ffe1c0c4fb24997e0c78a45f704f52553"),
        ANIMAL_MAPPINGS2(0xE596,39,"61399d0f394c2e0c64137e14638640681b0c9c1af333328a4705f7185957502d"),
        ANIMAL_ANIMATION(0xE548,38,"5bf35fa79c141fcec25ad20726e8e1cd18941eb9255d74f6be806857995b1762"),
        ANIMAL_TILES(0xE5BE,22,"264f607eed187439efa83a84217f7fdcf98d4898418b2346c61f2dd1390776e1"),
        COLLAPSE_MAPPINGS(0xF3F2,392,"f8883a2759b1b3d6c8ceaa311e96e488c1efcaa9451d1982cdac1c113d2bc6ac"),
        HMOVE_MAPPING_1(0xC376,42,"c613d2f99028b5732d7b41c779bf027e3406d79cdb94484bfc2154826165533a"),
        HMOVE_MAPPING_2(0xC472,13,"6deba6caaa5273dfe76821a0d2ae697fdaa673bbee28632e0469373a9fa580b1"),
        HMOVE_MAPPING_3(0xC5D0,23,"2486593b2c8e2f314977a38c241d757fbb43998f05ad52b33b49c4d36407968e"),
        HMOVE_MAPPING_4(0xC6CC,3,"dbbc7025e89eb9b1ec9e8e2c7a2db6869dbb50fba21bf374c86529dc311cede9"),
        HMOVE_MAPPING_5(0xC7EC,23,"2486593b2c8e2f314977a38c241d757fbb43998f05ad52b33b49c4d36407968e"),
        HMOVE_MAPPING_6(0xC90E,63,"2a3f5226c346a89f4e85a006cc31e2a6482cd31be4450dcd3dea93783fa12d29"),
        HMOVE_H3_OFFSETS(0xC536,36,"6312f63248697ff77ff39a90c6d85c865bd29857ccdc6ff0b67431a176f92884"),
        HMOVE_H5_START(0xC774,14,"9a30f5e3cdcd64c005d3cc5610f8d039f6c8a00078950b8d6e43b6aecb355e24"),
        HMOVE_H5_MOTION(0xC7D4,24,"b0bd1cca88767bdaa0776b476746be3f1ddab437e629da3f225b56c9e3be1f9d"),
        HMOVE_H3_FAILED_OFFSETS(0xC436,6,"39301389039035eadf6c7a79f3c364be18b0d3c9611f6a76f4a47d72f2883110"),
        MONITOR_ANIMATIONS(0x9C56,136,"c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36"),
        SPRING_MAPPINGS(0x8FF2,199,"f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd"),
        SPRING_ANIMATIONS(0x8FE4,14,"7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5"),
        SPRING_SPEEDS(0x8D3A,4,"5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"),
        MOVING_SPRING_MAPPINGS(0x90C0,16,"f06f49cb0a71706485ede6030456b96b0523e69e3545ba6bc7094076698f459d"),
        MOVING_SPRING_ANIMATIONS(0x90BA,6,"e8b2932927462b357fa2e0666e939c3cd3031a290c5be159b9f9321c3021547a"),
        SPRING_TILE_HELPER(0xC050,2,"1ceeabf0c6a5a30bad12cdac0e3ab015a7188a42e6aebb556aad00bb9cd693ad"),
        POWERUP_ANIMATIONS(0x1D400,295,"cadaa3ec586ebef375d0cca4e38af02a27fba03bdc8b4ed20c57e5b0c8e47b61"),
        POWERUP_MAPPINGS(0x1D528,273,"67d5f5c24e0422dd61c283e1ffb934771f6afe4394faff72d2c0d172b8179f2e"),
        POWERUP_ART_INDEX(0x68CE,20,"329a5e1431fedf994529627c5489ea9c5a1786494891827493186d004b76c110"),
        POWERUP_COPY_0(0x364D4,1024,"cbca98fc186fec63ea75c2c0dd8da06ec651c02c37232c15e38cecb749d5b7d0"),
        POWERUP_COPY_1(0x36954,1024,"706736889a2cced446522a69a86853de41d4dfc9204c83ab0a7d2690d3036fcc"),
        POWERUP_COPY_2(0x36DD4,1024,"2094d9156ba35fdb865feb01bee3e64b809ad0297e3d46d7defcf79d94fb5919"),
        POWERUP_COPY_3(0x35D28,1024,"911efa9665098c6bb34bc5b8687df6dca9a998255d72e9e03f065fc8b05c5afd"),
        POWERUP_COPY_4(0x35968,1024,"8830d700eed4f092a9f4329993caf68940ac65bae7656e54a823031711e371f3"),
        EXPLOSION_ANIMATIONS(0x1D700,19,"347e3172cd80aecbfd0d939596d22531d352d12828d35409ceebf15b57da3616"),
        EXPLOSION_MAPPINGS(0x1D714,145,"8c5177a34f04831a64f0205d4a43fc5858a56d19741f90c0bdff338b411cfada"),
        FALL_SPIKES_MAPPINGS(0xEB08,26,"9ac6b3bd706ff901df41a10acb28d87af907c65184717dc3a22c3b355e6dedac"),
        FALL_SPIKES_PARENT_MAP_STORE(0xE994,8,"6bb9df1d5acc087bc014d035216484e87f82d41d0bf8be6e1b20d14aa779e6a6"),
        PLAYER_BUBBLES_ANIMATIONS(0x645A,154,"26ee0eb340903d265d33a1cf44484e2a7f90a4710d2262b8503133448e2713e7"),
        SPLASH_ANIMATIONS(0x1D63A,24,"c7f11aacb360c364e160035e4434f7dd90bc33bdae383e3086b1904233b42bce"),
        SPLASH_MAPPINGS(0x1D652,100,"2b299478fd46998784b63b1609fd5d0926fb5fba2cce73edb3921cab43b83287"),
        PLAYER_BUBBLES_MAP_STORE(0x60B0,8,"89da4a16b7bbc1df9b8ce80939986490ab88dea313786840cfad84d4521a8211"),
        SPLASH_MAP_STORE(0x66F6,8,"f048f8e9f89fef54bfae583ae36ad32d340d8ba98e75f11d657df3a6e675af6f"),
        DOOR_MAPPINGS(0xCF46,61,"38c871d3f06ed834df747384ac2dd3a3765afe6abdd6dc6d16c63f202ab67ac9"),
        DOOR_MAP_STORE(0xCD34,20,"70c6e29a54a9f1f01edc8b83719b19d24c7ff3f9cd71dbf701e0fe3dbe9ef05e"),
        DOOR_SHAPES(0xCD84,20,"5214e6ced2acc58fd976d8b9bee7007dbfac79c0bde20cdf52d0f82dfeedb2ec"),
        SWING_MAPPINGS(0xD136,42,"f3d444b7485646affcae68f1522d611867e068088eb044f1eb31b1a0103ebdeb"),
        SWING_MAP_STORE(0xD000,14,"e675f1cc13d94756f6bb0f7071e6bc9ca1a8191f88766bfddec458d0257dcd45"),
        SWITCH_MAPPINGS(0xCCE4,26,"2a7ee206215551ae197f3355b78d2dd655944b4bbe0b4946d875b20a1ed5ba4e"),
        SWITCH_MAP_STORE(0xCBA8,18,"788ffeb76f2962894a1aef867f7641e7aa479224b47e6b411a1eeda6d1bfdcf7");
        private final int offset,length;private final String sha;
        Table(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
        int fileOffset(){return offset;}
        public int nativeAddress(){return 0x200000+offset;}
        public int length(){return length;}
        public String sha256(){return sha;}
    }
    public record View(Table table,int nativeAddress,CdAssetBytes bytes){
        public View{Objects.requireNonNull(table);Objects.requireNonNull(bytes);}
    }
    public record WaterSurfaceData(int mappingsAddress,CdAssetBytes offsets,CdObjectAnimation.AnimationTable animation){
        public WaterSurfaceData{Objects.requireNonNull(offsets);Objects.requireNonNull(animation);}
    }
    private boolean hudDestinationsVerified,fallSpikesOperandsVerified,waterEntryOperandsVerified;
    private final Map<com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation,CdObjectAnimation.AnimationTable> waterEntryAnimations=new EnumMap<>(com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation.class);
    private final CdProgrammeReader reader;
    private final Map<Table,View> views=new EnumMap<>(Table.class);
    private CdTitleCard.Assets title;private CdReservedHud.Assets hud;private CdRing.SourceData ringData;
    private CdLevelHud.Assets font;private WaterSurfaceData water;private CdObjectMappings mappings;
    private com.openggf.game.soniccd.player.CdR4WaterAirPorts.BubbleData bubble;private CdObjectAnimation.AnimationTable bubbleAnimation;
    private com.openggf.game.soniccd.player.CdPlayerObjectTouch touchScanner;
    private CdR4SolidMover.Data blockData,collapseFloorData,hMoveData;
    private CdR4Door.Data doorData;private CdR4SolidMover.Data swingData;private CdR4Switch.Data switchData;
    private CdMonitor.Assets timePostAssets;
    private CdSpring.FiniteProfile springProfile;private CdMovingSpring.SourceData movingSpringData;
    private CdTimeStars.Assets powerupAssets;private CdExplosion.Assets explosionAssets;
    private com.openggf.game.soniccd.resources.CdPowerupArtRuntime.RawSources powerupArtSources;
    private CdProgrammeObjectSource(CdProgrammeReader reader){this.reader=Objects.requireNonNull(reader);requireRetained();}
    static CdProgrammeObjectSource fromReader(CdProgrammeReader actual){return new CdProgrammeObjectSource(actual);}
    public CdStageKey stage(){return reader.stage();}
    public int nativeZoneActWord(){return reader.ordinaryZoneActWord();}
    public void requireRetained(){
        if(reader.definition().tt().orElse(null)!=CdR4DataLayout.Programme.R41A)
            throw new UnsupportedOperationException("Ordinary object source operands currently bind actual R41A only");
        reader.requireRetained();
    }
    /** Exact canonical retained layout, never equal rows/stage/descriptor values. No clear is inferred.
     * The World still proves that this is its original live placement/Frame on every operation. */
    public void requirePlacement(CdObjectPlacementRuntime placement){
        Objects.requireNonNull(placement);requireRetained();
        if(placement.placements()!=reader.placements())throw new IllegalArgumentException("Foreign ordinary object source/placement reader");
    }
    public synchronized View view(Table table){
        Objects.requireNonNull(table);return views.computeIfAbsent(table,t->new View(t,t.nativeAddress(),reader.objectView(t)));
    }
    /** Immutable source-derived selector. No response, publication or invoker is cached here. */
    public synchronized com.openggf.game.soniccd.player.CdPlayerObjectTouch touchScanner(){
        if(touchScanner==null)touchScanner=com.openggf.game.soniccd.player.CdPlayerObjectTouch.fromProgrammeSource(this);
        return touchScanner;
    }
    /** Actual R41A Block source operands only. Unread family operands never receive defaults. */
    public synchronized CdR4SolidMover.Data blockData(){
        if(blockData==null)blockData=new CdR4SolidMover.Data(){
            public int mapping(CdR4SolidMover.Mapping mapping){
                requireRetained();if(mapping!=CdR4SolidMover.Mapping.BLOCK)throw new UnsupportedOperationException("Unowned original TT Block mapping "+mapping);
                return view(Table.BLOCK_MAPPINGS).nativeAddress();
            }
            public CdAssetBytes table(CdR4SolidMover.Table table){
                requireRetained();if(table!=CdR4SolidMover.Table.BLOCK_SIZES)throw new UnsupportedOperationException("Unowned original TT Block table "+table);
                return view(Table.BLOCK_SIZES).bytes();
            }
            public int address(CdR4SolidMover.Address address){throw new UnsupportedOperationException("Block has no native "+Objects.requireNonNull(address)+" read");}
        };return blockData;
    }
    /** Actual original mapping only. Native dimensions/offsets come from the current subtype,
     * not a synthetic size table; every unrelated reached operand remains unavailable. */
    public synchronized CdR4SolidMover.Data collapseFloorData(){
        if(collapseFloorData==null)collapseFloorData=new CdR4SolidMover.Data(){
            public int mapping(CdR4SolidMover.Mapping mapping){requireRetained();if(mapping!=CdR4SolidMover.Mapping.COLLAPSE)throw new UnsupportedOperationException("Unowned original CollapseFloor mapping "+mapping);return view(Table.COLLAPSE_MAPPINGS).nativeAddress();}
            public CdAssetBytes table(CdR4SolidMover.Table table){requireRetained();throw new UnsupportedOperationException("CollapseFloor has no native table "+Objects.requireNonNull(table));}
            public int address(CdR4SolidMover.Address address){requireRetained();throw new UnsupportedOperationException("CollapseFloor has no native address "+Objects.requireNonNull(address));}
        };return collapseFloorData;
    }
    /** Actual R41A HMove operands; all six distinct roots and reached native tables stay finite. */
    public synchronized CdR4SolidMover.Data hMoveData(){
        if(hMoveData==null)hMoveData=new CdR4SolidMover.Data(){
            public int mapping(CdR4SolidMover.Mapping mapping){requireRetained();var table=switch(Objects.requireNonNull(mapping)){
                case H1->Table.HMOVE_MAPPING_1;case H2->Table.HMOVE_MAPPING_2;case H3->Table.HMOVE_MAPPING_3;
                case H4->Table.HMOVE_MAPPING_4;case H5->Table.HMOVE_MAPPING_5;case H6->Table.HMOVE_MAPPING_6;
                default->throw new UnsupportedOperationException("Unowned original HMove mapping "+mapping);
            };return view(table).nativeAddress();}
            public CdAssetBytes table(CdR4SolidMover.Table table){requireRetained();var selected=switch(Objects.requireNonNull(table)){
                case H3_OFFSETS->Table.HMOVE_H3_OFFSETS;case H3_FAILED_ALLOCATION_OFFSETS->Table.HMOVE_H3_FAILED_OFFSETS;
                case H5_START->Table.HMOVE_H5_START;case H5_MOTION->Table.HMOVE_H5_MOTION;
                default->throw new UnsupportedOperationException("Unowned original HMove table "+table);
            };return view(selected).bytes();}
            public int address(CdR4SolidMover.Address address){requireRetained();throw new UnsupportedOperationException("HMove has no native address "+Objects.requireNonNull(address));}
        };return hMoveData;
    }
    /** Original R41A section helper is literally RTS: no tile selector table or store is borrowed. */
    public void requireSpringTileHelperNoop(){requireRetained();if(view(Table.SPRING_TILE_HELPER).bytes().unsignedWord(0)!=0x4E75)throw new IllegalStateException("Original R41A Spring tile helper differs");}
    /** Authentic199/14/4 source relation. Init already wrote0520 before its diagonal helper RTS. */
    public synchronized CdSpring.FiniteProfile springProfile(){
        requireSpringTileHelperNoop();if(springProfile==null){var m=view(Table.SPRING_MAPPINGS);var a=view(Table.SPRING_ANIMATIONS).bytes();
            springProfile=new CdSpring.FiniteProfile(CdObjectVisualAssets.decodeIndexed(m.bytes().copy(),0,3,12,46,a.copy(),1,36),
                CdObjectVisualAssets.decodeIndexed(m.bytes().copy(),6,3,46,80,a.copy(),1,36),
                CdObjectVisualAssets.decodeIndexed(m.bytes().copy(),80,3,86,199,a.copy(),1,46),m.nativeAddress(),view(Table.SPRING_SPEEDS).bytes(),0x520);
        }return springProfile;
    }
    /** Wheel art is independent; its RTS helper retains the source's dirty TILE, never a guessed value. */
    public synchronized CdMovingSpring.SourceData movingSpringData(){
        requireSpringTileHelperNoop();if(movingSpringData==null){var m=view(Table.MOVING_SPRING_MAPPINGS);var a=view(Table.MOVING_SPRING_ANIMATIONS);
            movingSpringData=new CdMovingSpring.SourceData(CdObjectVisualAssets.decode(m.bytes().copy(),2,a.bytes().copy(),1,8),m.nativeAddress());
        }return movingSpringData;
    }
    public List<Integer> initialFamilyIds(){return List.of(0x04,0x0B,0x10,0x19,0x1C,0x20,0x21,0x22,0x2B,0x2F,0x32,0x34,0x3C);}
    /** Source fact only. The actual World must perform NullObject's one ID-BYTE clear then return,
     * preserving the other63 bytes. This predicate never executes a body or makes an unknown handler RTS. */
    public boolean isNullHandler(int nativeId){
        if(nativeId<1||nativeId>Table.DISPATCH.length()/4)throw new IllegalArgumentException("Original TT dispatch ID");
        view(Table.NULL_HANDLER);return view(Table.DISPATCH).bytes().unsignedLong((nativeId-1)*4)==Integer.toUnsignedLong(Table.NULL_HANDLER.nativeAddress());
    }
    public static final class UnownedBubblePatternRead extends UnsupportedOperationException{
        private final int pointer,index;
        UnownedBubblePatternRead(int pointer,int index){super("Original R41A bubble pattern reached unowned BYTE at raw $"+Integer.toUnsignedString(pointer,16)+" + "+index);this.pointer=pointer;this.index=index;}
        public int pointerBits(){return pointer;}
        public int unsignedIndex(){return index;}
    }
    /** Same existing controller/rise kernel and canonical wobble; no RNG/allocation/state owner here. */
    public synchronized com.openggf.game.soniccd.player.CdR4WaterAirPorts.BubbleData bubbleSpawner(){
        if(bubble==null)bubble=new com.openggf.game.soniccd.player.CdR4WaterAirPorts.BubbleData(Table.BUBBLE_MAPPINGS.nativeAddress(),
                reader.view(CdR4DataLayout.Programme.R41A.range("WobbleTable")),reader::bubblePatternByte,Table.BUBBLE_PATTERNS.nativeAddress());return bubble;
    }
    public synchronized CdObjectAnimation.AnimationTable bubbleSpawnerAnimation(){
        if(bubbleAnimation==null)bubbleAnimation=new CdObjectAnimation.AnimationTable(view(Table.BUBBLE_ANIMATIONS).bytes(),0,7);return bubbleAnimation;
    }
    public synchronized CdTitleCard.Assets titleAssets(){
        if(title==null)title=new CdTitleCard.Assets(view(Table.TITLE_CHARACTERS).bytes(),Table.TITLE_MAPPINGS.nativeAddress());return title;
    }
    public synchronized CdReservedHud.Assets hudAssets(){
        if(hud==null)hud=new CdReservedHud.Assets(Table.HUD_MAPPINGS.nativeAddress(),Table.MONITOR_MAPPINGS.nativeAddress());return hud;
    }
    /** Complete original R41A Monitor/TimePost visual DATA on this reader. The existing
     * mapper already registers all twenty frames; this grants no Monitor/World/PCM admission.
     * N1ecc MonitorTimeObject (objects/monitor_time.asm:230-241), TimePostInit (:78-115),
     * TimePostSpin (:153-169), MonitorTimeAnims/Sprites (:541-547). */
    public synchronized CdMonitor.Assets timePostAssets(){
        requireRetained();
        if(timePostAssets==null){
            if(view(Table.DISPATCH).bytes().unsignedLong((CdTimePost.OBJECT_ID-1)*4)!=0x2098F2L)
                throw new IllegalStateException("Original R41A MonitorTimeObject dispatch differs");
            var mapping=view(Table.MONITOR_MAPPINGS);var animation=view(Table.MONITOR_ANIMATIONS);
            timePostAssets=new CdMonitor.Assets(CdObjectVisualAssets.decode(mapping.bytes().copy(),20,
                    animation.bytes().copy(),10,82),mapping.nativeAddress());
        }return timePostAssets;
    }
    /** Original nine-script data and raw13-frame aliasing mapper; never a uniform visual decode. */
    public synchronized CdTimeStars.Assets powerupAssets(){
        requireRetained();if(powerupAssets==null)powerupAssets=new CdTimeStars.Assets(
            new CdObjectAnimation.AnimationTable(view(Table.POWERUP_ANIMATIONS).bytes(),0,9),view(Table.POWERUP_MAPPINGS).nativeAddress());return powerupAssets;
    }
    /** LoadPowerupArt's exact five original1024-byte reads; native neighbours remain real bytes. */
    public synchronized com.openggf.game.soniccd.resources.CdPowerupArtRuntime.RawSources powerupArtSources(){
        requireRetained();if(powerupArtSources==null)powerupArtSources=slot->{
            requireRetained();Objects.checkIndex(slot,5);Table table=switch(slot){case 0->Table.POWERUP_COPY_0;case 1->Table.POWERUP_COPY_1;case 2->Table.POWERUP_COPY_2;case 3->Table.POWERUP_COPY_3;case 4->Table.POWERUP_COPY_4;default->throw new IllegalArgumentException("Native powerup slot");};
            if(view(Table.POWERUP_ART_INDEX).bytes().unsignedLong(slot*4)!=Integer.toUnsignedLong(table.nativeAddress()))throw new IllegalStateException("Original powerup pointer relation differs");
            return view(table).bytes();};return powerupArtSources;
    }
    /** Original LoadPowerupArt destination ownership only; no known pixels or service authority. */
    public boolean powerupDestination(int destination){requireRetained();return (destination&31)==0&&destination>=0xA880&&destination<=0xACE0;}
    public synchronized CdExplosion.Assets explosionAssets(){
        requireRetained();if(explosionAssets==null)explosionAssets=new CdExplosion.Assets(CdObjectVisualAssets.decode(
            view(Table.EXPLOSION_MAPPINGS).bytes().copy(),7,view(Table.EXPLOSION_ANIMATIONS).bytes().copy(),2,70),Table.EXPLOSION_MAPPINGS.nativeAddress());return explosionAssets;
    }
    public synchronized CdRing.SourceData ringData(){
        if(ringData==null){
            var bytes=view(Table.RING_DELTAS).bytes();var delta=new ArrayList<CdR11aObjectAssets.Delta>();
            for(int n=0;n<16;n++)delta.add(new CdR11aObjectAssets.Delta(bytes.signedByte(n*2),bytes.signedByte(n*2+1)));
            var visual=CdObjectVisualAssets.decode(view(Table.RING_MAPPINGS).bytes().copy(),9,view(Table.RING_ANIMATIONS).bytes().copy(),1,14);
            ringData=new CdRing.SourceData(delta,Table.RING_MAPPINGS.nativeAddress(),0xA7AE,visual);
        }return ringData;
    }
    /** Source-bound factory only; the real World caches its body beside its own actual placement. */
    public synchronized CdRing ring(CdObjectPlacementRuntime placement){
        requirePlacement(placement);return CdRing.fromProgrammeSource(placement,this);
    }
    public synchronized WaterSurfaceData waterSurface(){
        if(water==null)water=new WaterSurfaceData(Table.WATER_MAPPINGS.nativeAddress(),view(Table.WATER_OFFSETS).bytes(),
                new CdObjectAnimation.AnimationTable(view(Table.WATER_ANIMATIONS).bytes(),0,1));return water;
    }
    public synchronized CdLevelHud.Assets font(){
        if(font==null)font=new CdLevelHud.Assets(reader.view(CdR4DataLayout.Programme.R41A.range("HudNumbersGfx")),view(Table.HUD_RESET).bytes(),view(Table.HUD_DIVISORS).bytes());return font;
    }
    /** Original ID28 Door, exact R41A dispatch/STORE and counted three-frame source. */
    public synchronized CdR4Door.Data doorData(){
        requireRetained();if(doorData==null){
            if(view(Table.DISPATCH).bytes().unsignedLong((0x28-1)*4)!=0x20CCFEL
                ||!java.util.Arrays.equals(view(Table.DOOR_MAP_STORE).bytes().copy(),java.util.HexFormat.of().parseHex("317c44400002117c00020018217c0020cf460004")))
                throw new IllegalStateException("Original R41A Door source operand relation changed");
            doorData=new CdR4Door.Data(){
                public int mapping(){requireRetained();return view(Table.DOOR_MAPPINGS).nativeAddress();}
                public int shape(int type,int field){requireRetained();if(type<0||type>=5)throw new UnsupportedOperationException("Original Door shape high-nibble source beyond authenticated five rows: "+type);Objects.checkIndex(field,4);return view(Table.DOOR_SHAPES).bytes().unsignedByte(type*4+field);}
            };
        }return doorData;
    }
    /** Original ID29 Swing and its source-minted linked ID27 Switch borrow only own data. */
    public synchronized CdR4SolidMover.Data swingData(){
        requireRetained();if(swingData==null){
            if(view(Table.DISPATCH).bytes().unsignedLong((0x29-1)*4)!=0x20CF84L||!Arrays.equals(view(Table.SWING_MAP_STORE).bytes().copy(),HexFormat.of().parseHex("217c0020d1360004317c03400002")))throw new IllegalStateException("Original Swing mapping operand relation changed");
            swingData=new CdR4SolidMover.Data(){
                public int mapping(CdR4SolidMover.Mapping m){requireRetained();if(m!=CdR4SolidMover.Mapping.SWING)throw new UnsupportedOperationException("Swing has no mapping "+m);return view(Table.SWING_MAPPINGS).nativeAddress();}
                public CdAssetBytes table(CdR4SolidMover.Table t){requireRetained();throw new UnsupportedOperationException("Swing has no borrowed table "+Objects.requireNonNull(t));}
                public int address(CdR4SolidMover.Address a){requireRetained();throw new UnsupportedOperationException("Swing has no raw address "+Objects.requireNonNull(a));}
            };
        }return swingData;
    }
    public synchronized CdR4Switch.Data switchData(){
        requireRetained();if(switchData==null){
            if(view(Table.DISPATCH).bytes().unsignedLong((0x27-1)*4)!=0x20CB7EL||!Arrays.equals(view(Table.SWITCH_MAP_STORE).bytes().copy(),HexFormat.of().parseHex("217c0020cce40004117c000a001643f8f7e0")))throw new IllegalStateException("Original linked Switch mapping operand relation changed");
            switchData=new CdR4Switch.Data(){public int mapping(){requireRetained();return view(Table.SWITCH_MAPPINGS).nativeAddress();}public int switchBaseWord(){requireRetained();return com.openggf.game.soniccd.objects.r4.CdR4SwitchBank.BASE_WORD;}};
        }return switchData;
    }
    /** Complete original rectangle bytes; a reached switch-byte owner remains the caller's obligation. */
    public CdAssetBytes waterCurrents(){return reader.view(CdR4DataLayout.Programme.R41A.range("WaterCurrents"));}
    /** Existing resolver only: counted same-reader TT roots, without art decode/preload or PPZ roots. */
    public synchronized CdObjectMappings mappings(){
        if(mappings==null){
            var sonic=reader.sonic();var tables=new ArrayList<CdObjectMappings.DeclaredTable>();
            tables.add(new CdObjectMappings.DeclaredTable(reader.playerMappingAddress(),sonic.mappings(),0,sonic.mappingFrames().size()));
            for(var binding:List.of(Map.entry(Table.RING_MAPPINGS,9),Map.entry(Table.WATER_MAPPINGS,8),
                    Map.entry(Table.TITLE_MAPPINGS,9),Map.entry(Table.HUD_MAPPINGS,4),Map.entry(Table.MONITOR_MAPPINGS,20),Map.entry(Table.BUBBLE_MAPPINGS,23),Map.entry(Table.BLOCK_MAPPINGS,7),Map.entry(Table.SPIKES_MAPPINGS,2),Map.entry(Table.TONBO_MAPPINGS1,3),Map.entry(Table.TONBO_MAPPINGS2,3),Map.entry(Table.ANIMAL_MAPPINGS1,3),Map.entry(Table.ANIMAL_MAPPINGS2,3),Map.entry(Table.COLLAPSE_MAPPINGS,9),Map.entry(Table.POWERUP_MAPPINGS,13),Map.entry(Table.EXPLOSION_MAPPINGS,7),Map.entry(Table.FALL_SPIKES_MAPPINGS,2),Map.entry(Table.SPLASH_MAPPINGS,7),Map.entry(Table.DOOR_MAPPINGS,3),Map.entry(Table.SWING_MAPPINGS,3),Map.entry(Table.SWITCH_MAPPINGS,2),Map.entry(Table.HMOVE_MAPPING_1,2),Map.entry(Table.HMOVE_MAPPING_2,1),Map.entry(Table.HMOVE_MAPPING_3,1),Map.entry(Table.HMOVE_MAPPING_4,1),Map.entry(Table.HMOVE_MAPPING_5,1),Map.entry(Table.HMOVE_MAPPING_6,4))){
                var v=view(binding.getKey());tables.add(new CdObjectMappings.DeclaredTable(v.nativeAddress(),v.bytes(),0,binding.getValue()));
            }
            var spring=view(Table.SPRING_MAPPINGS);for(int offset:new int[]{0,6,80})tables.add(new CdObjectMappings.DeclaredTable(spring.nativeAddress(),spring.bytes(),offset,3));
            var wheel=view(Table.MOVING_SPRING_MAPPINGS);tables.add(new CdObjectMappings.DeclaredTable(wheel.nativeAddress(),wheel.bytes(),0,2));
            var monitor=view(Table.MONITOR_MAPPINGS);mappings=CdObjectMappings.declaredSourceViews(tables,CdObjectMappings.monitorItemStaticPieces(monitor.bytes(),monitor.nativeAddress()));
        }return mappings;
    }
    /** Actual R41A UpdateHudNumbers follows aligned HUD mapping end +72-byte AddPoints.
     * All six literal destination operands are authenticated in this finite original read view.
     * Native N1ecc hud_points.asm shared font writer; this grants DATA relation only. */
    public synchronized boolean hudDestination(int destination){
        requireRetained();
        if(!hudDestinationsVerified){
            var code=view(Table.HUD_RENDER_CODE).bytes();
            if(code.unsignedWord(0)!=0x4A79)throw new IllegalStateException("Actual original HUD updater entry differs");
            String encoded=HexFormat.of().formatHex(code.copy());
            for(String operand:List.of("203c70600002","203c73600002","203c72200002","203c72600002","203c72e00002","203c74a00002"))
                if(!encoded.contains(operand))throw new IllegalStateException("Actual original HUD destination operand differs");
            hudDestinationsVerified=true;
        }
        return CdLevelHud.isFontDestination(destination);
    }
    private final Map<CdR4ObjectPorts.Data,CdObjectAnimation.AnimationTable> ecologyAnimations=new EnumMap<>(CdR4ObjectPorts.Data.class);
    /** Finite same-reader DATA selector; no caller/World/invocation authority is issued. */
    public int ecologyPointer(CdR4ObjectPorts.Data field){
        Objects.requireNonNull(field);if(field==CdR4ObjectPorts.Data.FALL_SPIKES_MAP)return fallSpikesMappingAddress();
        var table=switch(field){
            case SPIKES_MAP->Table.SPIKES_MAPPINGS;case TONBO_MAP1->Table.TONBO_MAPPINGS1;case TONBO_MAP2->Table.TONBO_MAPPINGS2;case TONBO_ANIM->Table.TONBO_ANIMATION;
            case ANIMAL_MAP1->Table.ANIMAL_MAPPINGS1;case ANIMAL_MAP2->Table.ANIMAL_MAPPINGS2;case ANIMAL_ANIM->Table.ANIMAL_ANIMATION;
            default->throw new UnsupportedOperationException("Original R41A ecology DATA is unbound: "+field);
        };return view(table).nativeAddress();
    }
    /** Original hex-ID$32 is decimal50, FallSpikesObject. Its native init operand and
     * two counted mapping frames are read from this same original, never literal fallback art.
     * N1ecc r4/object_index_1a.asm:53; objects/r4/fall_spikes.asm:40-77,194-196.
     * Actual R41A addresses differ from the historical numeric loc_* label text. */
    private synchronized int fallSpikesMappingAddress(){
        requireRetained();
        if(!fallSpikesOperandsVerified){
            if(view(Table.DISPATCH).bytes().unsignedLong((0x32-1)*4)!=0x20E928L)
                throw new IllegalStateException("Original R41A FallSpikesObject dispatch differs");
            var store=view(Table.FALL_SPIKES_PARENT_MAP_STORE).bytes();var mappings=view(Table.FALL_SPIKES_MAPPINGS);
            if(store.unsignedWord(0)!=0x217C||store.unsignedLong(2)!=Integer.toUnsignedLong(mappings.nativeAddress())||store.unsignedWord(6)!=4)
                throw new IllegalStateException("Original R41A FallSpikes mapping operand differs");
            fallSpikesOperandsVerified=true;
        }
        return view(Table.FALL_SPIKES_MAPPINGS).nativeAddress();
    }
    /** Same-reader actual first-water graph DATA; inert pointer/table values grant no invocation. */
    public synchronized int waterEntryMappings(com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation kind){
        requireRetained();Objects.requireNonNull(kind);
        if(!waterEntryOperandsVerified){
            var d=view(Table.DISPATCH).bytes();
            if(d.unsignedLong((0x21-1)*4)!=0x20608CL||d.unsignedLong((0x0B-1)*4)!=0x2066D2L)throw new IllegalStateException("Original TT air/splash dispatch differs");
            for(var pair:List.of(Map.entry(Table.PLAYER_BUBBLES_MAP_STORE,Table.BUBBLE_MAPPINGS),Map.entry(Table.SPLASH_MAP_STORE,Table.SPLASH_MAPPINGS))){var operand=view(pair.getKey()).bytes();var map=view(pair.getValue());if(operand.unsignedWord(0)!=0x217C||operand.unsignedLong(2)!=Integer.toUnsignedLong(map.nativeAddress())||operand.unsignedWord(6)!=4)throw new IllegalStateException("Original TT air/splash mapping operand differs");}
            waterEntryOperandsVerified=true;
        }
        return switch(kind){case PLAYER_BUBBLES->view(Table.BUBBLE_MAPPINGS).nativeAddress();case SPLASH->view(Table.SPLASH_MAPPINGS).nativeAddress();default->throw new UnsupportedOperationException("No first-water mapping "+kind);};
    }
    public synchronized CdObjectAnimation.AnimationTable waterEntryAnimation(com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation kind){
        waterEntryMappings(kind);return waterEntryAnimations.computeIfAbsent(kind,k->new CdObjectAnimation.AnimationTable(view(k==com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation.PLAYER_BUBBLES?Table.PLAYER_BUBBLES_ANIMATIONS:Table.SPLASH_ANIMATIONS).bytes(),0,k==com.openggf.game.soniccd.player.CdR4WaterAirPorts.Animation.PLAYER_BUBBLES?16:3));
    }
    public synchronized CdObjectAnimation.AnimationTable ecologyAnimation(CdR4ObjectPorts.Data field){
        if(field!=CdR4ObjectPorts.Data.TONBO_ANIM&&field!=CdR4ObjectPorts.Data.ANIMAL_ANIM)throw new UnsupportedOperationException("No original R41A ecology animation: "+field);
        return ecologyAnimations.computeIfAbsent(field,f->new CdObjectAnimation.AnimationTable(view(f==CdR4ObjectPorts.Data.TONBO_ANIM?Table.TONBO_ANIMATION:Table.ANIMAL_ANIMATION).bytes(),0,2));
    }
    /** Native byte-computed WORD index; literal zero words are real data, missing neighbours are not. */
    public int ecologyAnimalTile(int byteOffset){
        if(byteOffset<0||(byteOffset&1)!=0||byteOffset>Table.ANIMAL_TILES.length()-2)throw new UnsupportedOperationException("Original R41A Animal tile WORD at byte "+byteOffset);
        return view(Table.ANIMAL_TILES).bytes().unsignedWord(byteOffset);
    }
}
