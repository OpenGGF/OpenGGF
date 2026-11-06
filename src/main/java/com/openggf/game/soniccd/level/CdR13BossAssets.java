package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.util.*;

/**
 * Authenticated original-USA R13 C/D Eggman/limb DATA, not an object/World/renderer/PLC service.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Objects/Boss/Main compatibility aliases;
 * objects/r1/boss.asm:3-2792; anims/r1/eggman.asm; sprites/r1/{eggman,boss_*}.asm.
 * All bytes come through the immutable original programme owner. No native-source fallback,
 * new art source/bank, body algorithm, actor/default bossFight, physics, clock or capture owner.
 * Code/routine addresses and child declarations are comparison metadata, never executable actions,
 * allocation receipts, object links or authority to access native object RAM. Future components
 * must translate the source algorithm over real semantic ports; no CPU/RAM emulation is supplied.
 */
public final class CdR13BossAssets {
    /** Complete source includes, selected wrapper bodies and exact native raw-read windows. */
    public enum Table {
        OBJECT_INDEX(0x3B82,256,"430ff3d7defba4d3c7da091a430c95024d9a58c318b6174d704c9e7386ff8f86",0x3B9A,256,"a1e9e48e9cf08f0b2ba01e7d4d781b9a23082702f84305e94f318f990b24fcb2"),
        EGGMAN_ANIMATIONS(0xD7AE,32,"574ec280182149c8cb09d0fcafd49b093bfd74ffff2f1b7c07380763099aee7d",0xD7C6,32,"574ec280182149c8cb09d0fcafd49b093bfd74ffff2f1b7c07380763099aee7d"),
        EGGMAN_MAPPINGS(0xD7CE,210,"9f3adf5fd7463dc7bcd46f3d6df3261dd76ee8802e231ee426bda2c6d3dfe50e",0xD7E6,210,"9f3adf5fd7463dc7bcd46f3d6df3261dd76ee8802e231ee426bda2c6d3dfe50e"),
        BODY_MAPPINGS(0xD8A0,44,"8eb161752252733ec4c036a5ec9d43533b309e9ddd255f28f69737b638ad4b8e",0xD8B8,44,"8eb161752252733ec4c036a5ec9d43533b309e9ddd255f28f69737b638ad4b8e"),
        THIGH_MAPPINGS(0xD8CC,8,"0ad72266014509e84274314c79c15db91ae68ab308626f03d40f6f3265123bbf",0xD8E4,8,"0ad72266014509e84274314c79c15db91ae68ab308626f03d40f6f3265123bbf"),
        CALF_MAPPINGS(0xD8D4,14,"1294e48fd2416c7bc86032aee6fea98d50682826c94aa098e2a908ff52382a5a",0xD8EC,14,"1294e48fd2416c7bc86032aee6fea98d50682826c94aa098e2a908ff52382a5a"),
        FOOT_MAPPINGS(0xD8E2,18,"b897c10e038df917d23156510d9ec7b62ab3e53c5999eba3fa98218b1ee5b286",0xD8FA,18,"b897c10e038df917d23156510d9ec7b62ab3e53c5999eba3fa98218b1ee5b286"),
        SHOULDER_MAPPINGS(0xD8F4,14,"2814f37e4d450ecfd8df248e3ae38e0e56b45fb0302217ce9d159218e2a85d87",0xD90C,14,"2814f37e4d450ecfd8df248e3ae38e0e56b45fb0302217ce9d159218e2a85d87"),
        ARM_MAPPINGS(0xD902,8,"84b993b73c468036c93dea7c9d96250c129261b9b212f389110ca29d939d4b63",0xD91A,8,"84b993b73c468036c93dea7c9d96250c129261b9b212f389110ca29d939d4b63"),
        FOREARM1_MAPPINGS(0xD90A,42,"c6c92cc85d55b6757720025474e3b7847e769a20107e86af0e9dd1b053b03a0e",0xD922,42,"c6c92cc85d55b6757720025474e3b7847e769a20107e86af0e9dd1b053b03a0e"),
        FOREARM2_MAPPINGS(0xD934,24,"ac7342ce34d1928743fc3f276524ad3bd97ebf256ab7f108c884f1e4fd4ae9db",0xD94C,24,"ac7342ce34d1928743fc3f276524ad3bd97ebf256ab7f108c884f1e4fd4ae9db"),
        BUMPER_MAPPINGS(0xD94C,8,"98fff22aedcedd0161a60ede06313a6d822c388d9ba5f3df7b89d85aa47e5a56",0xD964,8,"98fff22aedcedd0161a60ede06313a6d822c388d9ba5f3df7b89d85aa47e5a56"),
        POSE_SEQUENCE(0xC040,20,"72893fa799a0eb1639b598130b30c55426e33ab24f59c46bae0ca0c199259c55",0xC058,20,"72893fa799a0eb1639b598130b30c55426e33ab24f59c46bae0ca0c199259c55"),
        EXPLOSION_OFFSETS(0xD778,40,"cb6207863848651255a6b96bdec9d5284dc4b9a8698b6d35e886b61a0419c975",0xD790,40,"cb6207863848651255a6b96bdec9d5284dc4b9a8698b6d35e886b61a0419c975"),
        CHILD_LINK_SOURCE(0xBD28,526,"4d2e6be7f382819137d6f3685dc027a6bb334b07438ac3bb267fbfae27235cb8",0xBD40,526,"915dcf411b64b7bf84a66b0904c6bac8bed48759cdda3fc814fa244eb50ee22b"),
        PALETTE_DIRECTORY(0x4EC,48,"f1876d5b0543264ada2250129ec31058d160444292ddf58ccc6d3abbee7e2315",0x558,48,"85cafdce8b5782bf0af5b33ff400b6adb56f78c39b544951e8a167f61e8898e9"),
        BOSS_PALETTE(0x69C,32,"97e599f66768a8d98855edaf2f2b98e08030c1215808cac9e8c301419127c81d",0x708,32,"43930988a8af6ab423f836a01fd644a5484a1fe58d03844b4621d36f3b78b852"),
        CODE_EGGMAN(0xB958,2144,"16a7dad2e86c8936a71e3317219fd734475ba1e1eecbe2785e9a0c3f48718a21",0xB970,2144,"d0658cbb43cf12e06d433c53b96025dcf0b9f7fc37b18d9b5cc5928c6ca2347b"),
        CODE_BODY(0xC1B8,1988,"ee97762f893c7e22d5cbad45a083a60061c71bc634b1091e3da83919ee3be99f",0xC1D0,1988,"b2ad97401097cafbf6e128857ddd60e9daa4313086fef1d722cc47ee554e9d5e"),
        CODE_THIGH(0xCE18,1064,"eeba93336b218a201d6f0e43726209ba4feafcf2e64f4bf4056f5fef6baca4e5",0xCE30,1064,"5e80120a00709be7e1a1bd23f78a79ab888164d395ec4af6a24ffcdd0220d024"),
        CODE_CALF(0xD240,574,"d2b08d73e83c089521d877406c08ccfab4bd22b56d9e434ac5ca4f005dacd411",0xD258,574,"f16f799bb9ae4bec33135e0b11227561e84e5156435887ac0f66378d1fd39b3c"),
        CODE_FOOT(0xD47E,816,"5dad3142f1dbab73b39a5928f7f84c86f81ff3163ba8e3e5dde5dc563fa50561",0xD496,816,"4a215ef416811234de7d95e9651cea5d04cd2c7dda68e51d924069682215f912"),
        CODE_SHOULDER(0xC97C,238,"53e08600a566118d29e20d52271103b454dd1d8bc52d7883b87ef2ddf202c22d",0xC994,238,"8a4dc2d8d3240d8886f7bf10a48d27ff0fd6eafb43ff28928f9f12ad3a5dfff1"),
        CODE_ARM(0xCA6A,306,"0495405bce17a33264552ab9b6a61dde3e242d5117ad2903380cf395500d6c22",0xCA82,306,"a539cfebb90566e052011ef08d9b45a7388943cede47814034be791a830cdd3d"),
        CODE_FOREARM(0xCB9C,414,"ea2c371fbaa8a608eeacc2a9f9931e060a5eb21b2185c24120f969f6332adec8",0xCBB4,414,"a8f9ba9cb152a62bd3197fdd8e1e27f26c2fd28a7a8fae260d1c8da935e135f5"),
        CODE_BUMPER(0xCD3A,222,"2f2ff1fad82c012bc31f8b6d5bf7d102aad09a7034b8fab5182f1983d86b5ac9",0xCD52,222,"c3629d821fb29bc5fef006ac957c45fdfc6331c3ca3f90a69b0fb01ce7915c03"),
        ROUTINES_EGGMAN(0xB988,16,"d22372701470f05d6b8a158abf2fe40ab7e79e08d1732450858b5d81f35bc83d",0xB9A0,16,"d22372701470f05d6b8a158abf2fe40ab7e79e08d1732450858b5d81f35bc83d"),
        ROUTINES_BODY(0xC1CC,18,"1617d22c6a3c80aeb3797dd11ed86e9a94246079b91d58c00924bcd4af340ecf",0xC1E4,18,"1617d22c6a3c80aeb3797dd11ed86e9a94246079b91d58c00924bcd4af340ecf"),
        ROUTINES_THIGH(0xCE2C,26,"2134144ccdafb590ddda6f9366bc95712725f3908b1d81fc22f3c31c782a13bb",0xCE44,26,"2134144ccdafb590ddda6f9366bc95712725f3908b1d81fc22f3c31c782a13bb"),
        ROUTINES_CALF(0xD254,16,"02052097524335c380f7acfca8e762084a8e9c414f988ed089548b8d49d0a0cd",0xD26C,16,"02052097524335c380f7acfca8e762084a8e9c414f988ed089548b8d49d0a0cd"),
        ROUTINES_FOOT(0xD492,14,"e4ce7b568da49d9cc26b88714373d9463a3ce413db6bf4287f74d6de76b30bc8",0xD4AA,14,"e4ce7b568da49d9cc26b88714373d9463a3ce413db6bf4287f74d6de76b30bc8"),
        ROUTINES_SHOULDER(0xC990,6,"bdb2ab7021fdd7fdc74f93656e6deff7a558194ba45863b22fc31037d280e967",0xC9A8,6,"bdb2ab7021fdd7fdc74f93656e6deff7a558194ba45863b22fc31037d280e967"),
        ROUTINES_ARM(0xCA88,12,"9a3947b9c28f4c5c69ae99115f65f6720a6089d0a0e3c5974c17b2b5591adbfd",0xCAA0,12,"9a3947b9c28f4c5c69ae99115f65f6720a6089d0a0e3c5974c17b2b5591adbfd"),
        ROUTINES_FOREARM(0xCBB0,12,"b7d838239b653de42346675e86a1b6be1da9274421d39984c7ab981e76c87ab3",0xCBC8,12,"b7d838239b653de42346675e86a1b6be1da9274421d39984c7ab981e76c87ab3"),
        ROUTINES_BUMPER(0xCD4E,6,"f123a2a59b9cd2378258286e373554f59db1df8d16c55768ef56cde30a1cf095",0xCD66,6,"f123a2a59b9cd2378258286e373554f59db1df8d16c55768ef56cde30a1cf095");
        private final int cOffset,cLength,dOffset,dLength;private final String cHash,dHash;
        Table(int cOffset,int cLength,String cHash,int dOffset,int dLength,String dHash){this.cOffset=cOffset;this.cLength=cLength;this.cHash=cHash;this.dOffset=dOffset;this.dLength=dLength;this.dHash=dHash;}
        private Range range(boolean good,CdNativeFile file){return new Range(this,file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,good?cOffset:dOffset,good?cLength:dLength,good?cHash:dHash);}
    }
    /** Native ObjectIndex order. Physical source-code order is retained by each separate range. */
    public enum Part {
        EGGMAN(0x2A,8,Table.CODE_EGGMAN,Table.ROUTINES_EGGMAN,22,0xBCCA,0xBCE2,0x3FD),
        BODY(0x2B,9,Table.CODE_BODY,Table.ROUTINES_BODY,6,0xC1FA,0xC212,0x359),
        THIGH(0x2C,13,Table.CODE_THIGH,Table.ROUTINES_THIGH,6,0xCE5C,0xCE74,0x2359),
        CALF(0x2D,8,Table.CODE_CALF,Table.ROUTINES_CALF,6,0xD280,0xD298,0x2359),
        FOOT(0x2E,7,Table.CODE_FOOT,Table.ROUTINES_FOOT,6,0xD4B6,0xD4CE,0x2359),
        SHOULDER(0x2F,3,Table.CODE_SHOULDER,Table.ROUTINES_SHOULDER,6,0xC9AC,0xC9C4,0x2359),
        ARM(0x30,6,Table.CODE_ARM,Table.ROUTINES_ARM,6,0xCAAA,0xCAC2,0x2359),
        FOREARM(0x31,6,Table.CODE_FOREARM,Table.ROUTINES_FOREARM,6,0xCBD2,0xCBEA,0x2359),
        BUMPER(0x32,3,Table.CODE_BUMPER,Table.ROUTINES_BUMPER,6,0xCD6A,0xCD82,0x2359);
        private final int id,count,indexDelta,tileC,tileD,tile;private final Table code,routines;
        Part(int id,int count,Table code,Table routines,int indexDelta,int tileC,int tileD,int tile){this.id=id;this.count=count;this.code=code;this.routines=routines;this.indexDelta=indexDelta;this.tileC=tileC;this.tileD=tileD;this.tile=tile;}
        public int nativeId(){return id;}public int routineCount(){return count;}
    }
    /** Two forearm families are source-written by parent allocation; no default choice is inferred. */
    public enum Visual {
        EGGMAN(Part.EGGMAN,Table.EGGMAN_MAPPINGS,11,112,0x3FD,0xBCD0,0xBCE8,0x217C),
        BODY(Part.BODY,Table.BODY_MAPPINGS,1,164,0x359,0xC200,0xC218,0x217C),
        THIGH(Part.THIGH,Table.THIGH_MAPPINGS,1,164,0x2359,0xCE62,0xCE7A,0x217C),
        CALF(Part.CALF,Table.CALF_MAPPINGS,1,164,0x2359,0xD286,0xD29E,0x217C),
        FOOT(Part.FOOT,Table.FOOT_MAPPINGS,1,164,0x2359,0xD4BC,0xD4D4,0x217C),
        SHOULDER(Part.SHOULDER,Table.SHOULDER_MAPPINGS,1,164,0x2359,0xC9B2,0xC9CA,0x217C),
        ARM(Part.ARM,Table.ARM_MAPPINGS,1,164,0x2359,0xCAB0,0xCAC8,0x217C),
        FOREARM1(Part.FOREARM,Table.FOREARM1_MAPPINGS,3,164,0x2359,0xBE72,0xBE8A,0x237C),
        FOREARM2(Part.FOREARM,Table.FOREARM2_MAPPINGS,3,164,0x2359,0xBEEA,0xBF02,0x237C),
        BUMPER(Part.BUMPER,Table.BUMPER_MAPPINGS,1,164,0x2359,0xCD70,0xCD88,0x217C);
        private final Part part;private final Table mapping;private final int frames,tiles,tile,storeC,storeD,opcode;
        Visual(Part part,Table mapping,int frames,int tiles,int tile,int storeC,int storeD,int opcode){this.part=part;this.mapping=mapping;this.frames=frames;this.tiles=tiles;this.tile=tile;this.storeC=storeC;this.storeD=storeD;this.opcode=opcode;}
        public Table mappingTable(){return mapping;}public int frameCount(){return frames;}public int artTileCount(){return tiles;}public int baseTileWord(){return tile;}
    }
    public record Range(Table table,CdNativeFile file,CdMmdImage.Domain domain,int fileOffset,int length,String sha256){
        public Range{Objects.requireNonNull(table);Objects.requireNonNull(file);Objects.requireNonNull(sha256);if(domain!=CdMmdImage.Domain.ORIGINAL_WORD_RAM||fileOffset<0||length<=0||fileOffset>0x40000-length)throw new IllegalArgumentException("Finite original boss-data range required");}
        public int nativeAddress(){return CdMmdHeader.WORD_RAM_ADDRESS+fileOffset;}
    }
    /** All original directory words survive, including Arm routine8's alias of routine4. */
    public record Body(Part part,Range codeRange,CdAssetBytes code,Range routineRange,List<Integer> routineTargets,int tileWord,int tileInstructionNativeAddress){
        public Body{Objects.requireNonNull(part);Objects.requireNonNull(codeRange);Objects.requireNonNull(code);Objects.requireNonNull(routineRange);routineTargets=List.copyOf(routineTargets);}
    }
    public record MappingStore(Part producer,Visual visual,int instructionNativeAddress,int opcode,int mappingNativeAddress){}
    public record ChildDeclaration(int ordinal,int instructionNativeAddress,int objectId){}
    /** PaletteTable ID5 (legacy label Pal_UnkCapsule) actually selected by Eggman Init; data only. */
    public record Palette(int nativeId,int destinationWord,int longwordCounter,int sourceNativeAddress,CdAssetBytes colours,int selectorInstructionNativeAddress,int loadRoutineNativeAddress){
        public Palette{Objects.requireNonNull(colours);}
    }
    private static final CdMmdHeader HEADER=new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112);
    private final CdR13FutureAssets.Identity identity;
    private final Map<Table,CdAssetBytes> views;
    private final List<Range> ranges;
    private final Map<Part,Body> bodies;
    private final Map<Visual,CdObjectVisualAssets> visuals;
    private final List<MappingStore> mappingStores;
    private final List<ChildDeclaration> children;
    private final Palette palette;

    private CdR13BossAssets(CdR13FutureAssets owner){
        Objects.requireNonNull(owner,"actual original future owner");owner.requireAuthenticatedLayout();identity=Objects.requireNonNull(owner.identity());var image=Objects.requireNonNull(owner.originalImage());
        boolean good=identity.file()==CdNativeFile.R13C;
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                ||identity.file()!=(good?CdNativeFile.R13C:CdNativeFile.R13D)||!identity.stage().equals(good?CdStagePayload.R13C.stage():CdStagePayload.R13D.stage())
                ||!expected.equals(identity.mmdSha256())||image.fileBytes()!=0x40000||!HEADER.equals(image.header())||image.fileBackedCopyBytes()!=0||image.unresolvedCopyBytes()!=0
                ||!expected.equals(CdR11aProfile.sha256(image.readFile(0,0x40000))))throw new IllegalArgumentException("Not original USA C/D boss data");
        var data=new EnumMap<Table,CdAssetBytes>(Table.class);var allRanges=new ArrayList<Range>();
        for(var t:Table.values()){var r=t.range(good,identity.file());var bytes=CdAssetBytes.copyOf(image.readFile(r.fileOffset(),r.length()));if(!r.sha256().equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Boss source window differs: "+t);data.put(t,bytes);allRanges.add(r);}
        views=Collections.unmodifiableMap(data);ranges=List.copyOf(allRanges);
        var bodyData=new EnumMap<Part,Body>(Part.class);var index=view(Table.OBJECT_INDEX);
        for(var p:Part.values()){
            var code=range(p.code);var directory=range(p.routines);if(index.unsignedLong((p.id-1)*4)!=code.nativeAddress())throw new IllegalArgumentException("Boss ObjectIndex selected another wrapper");
            int op=code.fileOffset()+p.indexDelta;requireWord(image,op,0x303B);int ext=u16(image,op+2);requireWord(image,op+4,0x4EBB);int jsr=u16(image,op+6);
            if((ext&0xFF00)!=0||(jsr&0xFF00)!=0||op+2+(byte)ext!=directory.fileOffset()||op+6+(byte)jsr!=directory.fileOffset())throw new IllegalArgumentException("Boss PC-indexed routine directory differs");
            var targets=new ArrayList<Integer>();for(int n=0;n<p.count;n++){int target=directory.nativeAddress()+(short)view(p.routines).unsignedWord(n*2);if(target<code.nativeAddress()||target>=code.nativeAddress()+code.length())throw new IllegalArgumentException("Boss routine outside its selected wrapper body");targets.add(target);}
            int tile=good?p.tileC:p.tileD;requireWord(image,tile,0x317C);requireWord(image,tile+2,p.tile);requireWord(image,tile+4,2);
            if(tile<targets.getFirst()-0x200000||tile>=code.fileOffset()+code.length())throw new IllegalArgumentException("Boss tile store lacks its original initializer");
            bodyData.put(p,new Body(p,code,view(p.code),directory,targets,p.tile,0x200000+tile));
        }
        bodies=Collections.unmodifiableMap(bodyData);
        var decoded=new EnumMap<Visual,CdObjectVisualAssets>(Visual.class);var stores=new ArrayList<MappingStore>();
        for(var v:Visual.values()){
            if(v.tile!=body(v.part).tileWord()||v.tiles!=(u16(image,v.part==Part.EGGMAN?0x1DF06:0x1D400)&0x7FFF))throw new IllegalArgumentException("Boss visual tile/header budget differs from its actual source");
            int op=good?v.storeC:v.storeD;requireWord(image,op,v.opcode);requireLong(image,op+2,range(v.mapping).nativeAddress());requireWord(image,op+6,4);
            var producer=v.part==Part.FOREARM?Part.EGGMAN:v.part;var code=body(producer).codeRange();if(op<code.fileOffset()||op+8>code.fileOffset()+code.length())throw new IllegalArgumentException("Boss mapping store belongs to another producer");
            stores.add(new MappingStore(producer,v,0x200000+op,v.opcode,range(v.mapping).nativeAddress()));
            decoded.put(v,v==Visual.EGGMAN?CdObjectVisualAssets.decode(view(v.mapping).copy(),v.frames,view(Table.EGGMAN_ANIMATIONS).copy(),5,v.tiles):CdObjectVisualAssets.decodeStatic(view(v.mapping).copy(),v.frames,v.tiles));
        }
        visuals=Collections.unmodifiableMap(decoded);mappingStores=List.copyOf(stores);
        int anim=good?0xB976:0xB98E;requireWord(image,anim,0x43F9);requireLong(image,anim+2,range(Table.EGGMAN_ANIMATIONS).nativeAddress());
        requireLea(image,good?0xC01E:0xC036,Table.POSE_SEQUENCE);requireLea(image,good?0xD746:0xD75E,Table.EXPLOSION_OFFSETS);
        int link=good?0xBFCC:0xBFE4;requireWord(image,link,0x6100);if(link+2+(short)u16(image,link+2)!=range(Table.CHILD_LINK_SOURCE).fileOffset())throw new IllegalArgumentException("Boss child-source BSR differs");
        int[] offsets={0x16,0x34,0x50,0x6C,0x8E,0xB2,0xD6,0x104,0x12C,0x152,0x170,0x19E,0x1CA,0x1EE},ids={0x2B,0x2C,0x2D,0x2E,0x2C,0x2D,0x2E,0x2F,0x30,0x31,0x32,0x30,0x31,0x32};
        var declarations=new ArrayList<ChildDeclaration>();for(int n=0;n<ids.length;n++){int op=range(Table.CHILD_LINK_SOURCE).fileOffset()+offsets[n];requireWord(image,op,0x137C);requireWord(image,op+2,ids[n]);requireWord(image,op+4,0);declarations.add(new ChildDeclaration(n,0x200000+op,ids[n]));}children=List.copyOf(declarations);
        // Borrow only existing PLC4 metadata/header cardinalities. No art blob/source/bank is created.
        var plc=owner.plcs().get(4);if(plc.nativeId()!=4||plc.entries().size()!=3||plc.entries().get(0).sourceNativeAddress()!=0x22EE4AL||plc.entries().get(0).vramAddress()!=0x9020
                ||plc.entries().get(1).sourceNativeAddress()!=0x21D400L||plc.entries().get(1).vramAddress()!=0x6B20||plc.entries().get(2).sourceNativeAddress()!=0x21DF06L||plc.entries().get(2).vramAddress()!=0x7FA0
                ||(u16(image,0x1D400)&0x7FFF)!=164||(u16(image,0x1DF06)&0x7FFF)!=112)throw new IllegalArgumentException("Existing boss PLC art relation differs");
        var pal=view(Table.PALETTE_DIRECTORY);int at=40;int address=(int)pal.unsignedLong(at),destination=pal.unsignedWord(at+4),counter=pal.unsignedWord(at+6);
        if(address!=range(Table.BOSS_PALETTE).nativeAddress()||destination!=0xFB20||counter!=7||view(Table.BOSS_PALETTE).length()!=(counter+1)*4)throw new IllegalArgumentException("Actual boss palette5 binding differs");
        int selector=good?0xBCFA:0xBD12,loader=good?0x20049C:0x200508;requireWord(image,selector,0x303C);requireWord(image,selector+2,5);requireLong(image,selector+4,0x48E701FF);requireWord(image,selector+8,0x4EB9);requireLong(image,selector+10,loader);
        palette=new Palette(5,destination,counter,address,view(Table.BOSS_PALETTE),0x200000+selector,loader);
    }
    public static CdR13BossAssets bind(CdR13FutureAssets owner){return new CdR13BossAssets(owner);}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public List<Range> ranges(){return ranges;}
    public Range range(Table table){return Objects.requireNonNull(table).range(identity.file()==CdNativeFile.R13C,identity.file());}
    public CdAssetBytes view(Table table){return views.get(Objects.requireNonNull(table));}
    public Body body(Part part){return bodies.get(Objects.requireNonNull(part));}
    public CdObjectVisualAssets visual(Visual visual){return visuals.get(Objects.requireNonNull(visual));}
    public List<MappingStore> mappingStores(){return mappingStores;}
    public List<ChildDeclaration> children(){return children;}
    public Palette palette(){return palette;}
    private void requireLea(CdMmdImage image,int at,Table table){requireWord(image,at,0x45F9);requireLong(image,at+2,range(table).nativeAddress());}
    private static int u16(CdMmdImage image,int at){var b=image.readFile(at,2);return(b[0]&255)<<8|(b[1]&255);}
    private static int u32(CdMmdImage image,int at){return u16(image,at)<<16|u16(image,at+2);}
    private static void requireWord(CdMmdImage image,int at,int value){if(u16(image,at)!=value)throw new IllegalArgumentException("Boss original WORD/operand differs at "+Integer.toHexString(at));}
    private static void requireLong(CdMmdImage image,int at,int value){if(u32(image,at)!=value)throw new IllegalArgumentException("Boss original LONG/operand differs at "+Integer.toHexString(at));}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
