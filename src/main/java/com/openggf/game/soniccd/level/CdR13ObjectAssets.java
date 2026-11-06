package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.CdTitleCard;
import com.openggf.game.soniccd.resources.CdLevelHud;

import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR13SectionSources.SourceId.*;

/**
 * Detached original-USA R13C/D Ring/Spring/Monitor/Animal/HUD/TitleCard metadata.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: actual Act3 includes and native Init
 * operands, independently matched C/D ranges and hashes in the companion tests/report.
 * Only the authentic future aggregate supplies an original image. Existing pure mapping,
 * animation, delta and tile-directory formats are retained. No body, dispatch, art decode,
 * bank/current, queue, service, clock, Frame, World, capture or display is created.
 */
public final class CdR13ObjectAssets {
    /** Exact original includes; Spring is199 bytes and the Act3 tile directory is68, without padding. */
    public enum View {
        OBJECT_TILE_IDS(0xABEA,0xAC02,68,"41e75f9fbefde560f302406a0e2f7d3ebbc6bad37b05fdfc7b196ae18dc06927","41e75f9fbefde560f302406a0e2f7d3ebbc6bad37b05fdfc7b196ae18dc06927"),
        RING_DELTAS(0x81AA,0x81C2,32,"35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86","35a686e2b0201c8422a64ded0a656e400b929d3fdeab89343e725e103ac55e86"),
        MAP_SPR_RING(0x860E,0x8626,67,"cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a","cc2e508c63b86fdff0c5fda4c67518574533d54f47e59a86ff3cde106340c15a"),
        ANI_RING(0x8606,0x861E,8,"4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a","4718d601ad9887e43a5091a017284dc4669f6ca9cb70f3458f4b2f543f26ff7a"),
        SPRING_SPEEDS(0x7DFC,0x7E14,4,"5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621","5eda02f69ae1d12606f936787ccebdac8133522694513985847a115cc00f2621"),
        MAP_SPRING(0x80B4,0x80CC,199,"f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd","f1c0f43ac8925447d88d33fed86d98062d4156d7430802e4c0de37ed620753fd"),
        ANI_SPRING(0x80A6,0x80BE,14,"7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5","7f641336673d1419b30d9555fa0e2ff3a12a95811b26f2d12d96e8b32befb6b5"),
        MAP_SPR_MONITOR_TIME(0x8DA0,0x8DB8,450,"00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca","00a6aaefb0d6dc3051a20c1d484d45dde3fb893e404f4c17b826fff8b1d74bca"),
        ANI_MONITOR(0x8D18,0x8D30,136,"c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36","c3c1fb2135835d0fd96bff9202c7c0be8cddd2bf97caf0209582e5cf03d37d36"),
        MAP_SPR_HUD(0x90B8,0x90D0,168,"7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3","7ad6f096c380ac75da7a805036fac76a5d275bf8b89354756de53fdb7b489ef3"),
        ART_HUD_NUMBERS(0x330BC,0x330BC,1024,"843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42","843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42"),
        ART_LIFE_ICON(0x32F3C,0x32F3C,384,"0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a","0c66185fd6da132daf7e47d85311a4f8d067272f34a11431acdd3f5273e4629a"),
        HUD_RESET_TILES(0x93EE,0x9406,12,"3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c","3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c"),
        HUD_DIVISORS(0x954E,0x9566,40,"7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea","7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea"),
        ANIMAL_BASE_TILES(0xAF00,0xAF18,22,"74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec","74f162e81af4734e829c1fe941e38c37942d86ffa9fed1900e1b4a1456f9aaec"),
        ANI_FLYING_ANIMAL(0xAE4A,0xAE62,66,"5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3","5eb555e70004a7714a9f4b146170b33be43c34bbb46ee359bf5b091b26dcafa3"),
        ANI_GROUND_ANIMAL(0xAE8C,0xAEA4,60,"b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4","b5cbc3329c7c081277af758830696be52033138602651018d3f59804b96a1ea4"),
        MAP_SPR_FLYING_ANIMAL(0xAEC8,0xAEE0,20,"00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39","00b6a9f05b668026330706ca5cf0f91562d716016b184afd49dbc0510088cb39"),
        MAP_SPR_GROUND_ANIMAL(0xAEDC,0xAEF4,36,"029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2","029c42155f452eb888ec8529324376de67cfb457f471fd4aa24830e2502c16e2"),
        TITLE_CARD_DATA(0xE13C,0xE154,64,"5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2","5633d1c528434db1c370012bc40f8ce07b8833517b42fa4adc1a994b4f04e1b2"),
        MAP_SPR_TITLE_CARD(0xE17C,0xE194,276,"a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11","a1ecc1fe6282052137b611db71bd39b055b0255eba937de1259ae8c5b7f08c11"),
        OBJECT_INDEX(0x3B82,0x3B9A,256,"430ff3d7defba4d3c7da091a430c95024d9a58c318b6174d704c9e7386ff8f86","a1e9e48e9cf08f0b2ba01e7d4d781b9a23082702f84305e94f318f990b24fcb2"),
        PAL_SONIC(0x61C,0x688,32,"8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a","8391d8af82c19043c89e32abf87bdd057dbe2a845c58a3a58761edceaeeb9f8a"),
        PAL_LEVEL(0x63C,0x6A8,96,"29d86b702295ed28d6d6c4fe4e829333036d0bb2b3c69f4ddb71e38b71a8e8cd","7a34893db95e1d9c0e4ab5acf2a492857d7b660f3e34263b60bd889fb8d03cb2"),
        DIAGONAL_SPRING_HEADER(0x31E2E,0x31E2E,2,"3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd","3639618e90314d0feb7ce289d97468263ca4af996ffc621399d092e1f12aa1dd");
        private final int goodOffset,badOffset,length;
        private final String goodSha,badSha;
        View(int goodOffset,int badOffset,int length,String goodSha,String badSha) {
            if(goodOffset<0 || badOffset<0 || length<1 || goodOffset>0x40000-length || badOffset>0x40000-length
                    || !goodSha.matches("[0-9a-f]{64}") || !badSha.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("Invalid finite R13 object source");
            this.goodOffset=goodOffset;this.badOffset=badOffset;this.length=length;this.goodSha=goodSha;this.badSha=badSha;
        }
        public CdR13FutureAssets.FileRange range(CdNativeFile file) {
            return new CdR13FutureAssets.FileRange(file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,bad(file)?badOffset:goodOffset,length);
        }
        public String sha256(CdNativeFile file){return bad(file)?badSha:goodSha;}
    }

    private enum Key {
        RINGS(View.MAP_SPR_RING,9,View.ANI_RING,1,14,0,0,0,ART_RINGS),
        SPRING_VERTICAL(View.MAP_SPRING,3,View.ANI_SPRING,1,36,0,12,46,ART_SPRINGS),
        SPRING_HORIZONTAL(View.MAP_SPRING,3,View.ANI_SPRING,1,36,6,46,80,ART_SPRINGS),
        SPRING_DIAGONAL(View.MAP_SPRING,3,View.ANI_SPRING,1,46,80,86,199,null),
        HUD(View.MAP_SPR_HUD,4,null,0,63,0,0,0,null),
        MONITOR(View.MAP_SPR_MONITOR_TIME,20,View.ANI_MONITOR,10,82,0,0,0,ART_MONITOR_TIME_POSTS),
        FLYING_ANIMAL(View.MAP_SPR_FLYING_ANIMAL,3,View.ANI_FLYING_ANIMAL,2,20,0,0,0,ART_ANIMALS),
        GROUND_ANIMAL(View.MAP_SPR_GROUND_ANIMAL,5,View.ANI_GROUND_ANIMAL,1,20,0,0,0,ART_ANIMALS),
        TITLE_CARD(View.MAP_SPR_TITLE_CARD,9,null,0,164,0,0,0,null);
        final View map,script;
        final int frames,scripts,bound,root,start,end;
        final CdR13SectionSources.SourceId art;
        Key(View map,int frames,View script,int scripts,int bound,int root,int start,int end,CdR13SectionSources.SourceId art) {
            this.map=map;this.frames=frames;this.script=script;this.scripts=scripts;this.bound=bound;
            this.root=root;this.start=start;this.end=end;this.art=art;
        }
    }

    private final CdR13FutureAssets assets;
    private final CdR13FutureAssets.Identity identity;
    private final CdMmdImage image;
    private final CdR13SectionSources sources;
    private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];
    private final CdObjectVisualAssets[] visuals=new CdObjectVisualAssets[Key.values().length];
    private List<CdR11aObjectAssets.Delta> deltas;
    private CdLevelHud.Assets hud;
    private CdTitleCard.Assets title;

    private CdR13ObjectAssets(CdR13FutureAssets assets) {
        this.assets=Objects.requireNonNull(assets,"actual original R13 future assets");
        assets.requireAuthenticatedLayout();
        identity=Objects.requireNonNull(assets.identity(),"actual source identity");
        image=Objects.requireNonNull(assets.originalImage(),"retained original programme");
        boolean good=identity.file()==CdNativeFile.R13C && identity.stage().equals(CdStagePayload.R13C.stage());
        boolean bad=identity.file()==CdNativeFile.R13D && identity.stage().equals(CdStagePayload.R13D.stage());
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f"
                :"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA || (!good&&!bad) || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || image.fileBytes()!=0x40000 || !expected.equals(identity.mmdSha256())
                || !expected.equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IllegalArgumentException("Original R13 object source/image relation differs");
        // Only existing metadata counts are borrowed; decoded(), source readers and art services are never called.
        sources=CdR13SectionSources.fromAssets(assets);
    }

    public static CdR13ObjectAssets fromAssets(CdR13FutureAssets assets){return new CdR13ObjectAssets(assets);}
    public boolean ownsAssets(CdR13FutureAssets assets){return this.assets==assets;}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public CdObjectPlacements placements(){return assets.placements();}
    public synchronized CdAssetBytes view(View source) {
        Objects.requireNonNull(source,"fixed original metadata source");var cached=views[source.ordinal()];if(cached!=null)return cached;
        var value=source.range(identity.file()).read(image);
        if(!source.sha256(identity.file()).equals(CdR11aProfile.sha256(value.copy())))
            throw new IllegalArgumentException("Original R13 object bytes differ: "+source);
        views[source.ordinal()]=value;return value;
    }
    public CdAssetBytes springMappings(){return view(View.MAP_SPRING);}
    public synchronized List<CdR11aObjectAssets.Delta> ringDeltas() {
        if(deltas==null)deltas=CdR11aObjectAssets.decodeRingDeltas(view(View.RING_DELTAS).copy());return deltas;
    }
    public synchronized CdLevelHud.Assets hudAssets() {
        if(hud==null)hud=new CdLevelHud.Assets(view(View.ART_HUD_NUMBERS),view(View.HUD_RESET_TILES),view(View.HUD_DIVISORS));return hud;
    }
    public synchronized CdTitleCard.Assets titleCardAssets() {
        if(title==null)title=new CdTitleCard.Assets(view(View.TITLE_CARD_DATA),View.MAP_SPR_TITLE_CARD.range(identity.file()).nativeAddress());return title;
    }
    public CdObjectVisualAssets rings(){return visual(Key.RINGS);}
    public CdObjectVisualAssets springVertical(){return visual(Key.SPRING_VERTICAL);}
    public CdObjectVisualAssets springHorizontal(){return visual(Key.SPRING_HORIZONTAL);}
    public CdObjectVisualAssets springDiagonal(){return visual(Key.SPRING_DIAGONAL);}
    public CdObjectVisualAssets hudVisuals(){return visual(Key.HUD);}
    public CdObjectVisualAssets monitorTime(){return visual(Key.MONITOR);}
    public CdObjectVisualAssets flyingAnimal(){return visual(Key.FLYING_ANIMAL);}
    public CdObjectVisualAssets groundAnimal(){return visual(Key.GROUND_ANIMAL);}
    public CdObjectVisualAssets titleCard(){return visual(Key.TITLE_CARD);}

    private synchronized CdObjectVisualAssets visual(Key key) {
        var cached=visuals[key.ordinal()];if(cached!=null)return cached;
        int bound=key.art==null?key.bound:sources.source(key.art).tileCount();
        // This actual two-byte header is outside the19 submitted-source registry. It grants no current art availability.
        if(key==Key.SPRING_DIAGONAL)bound=view(View.DIAGONAL_SPRING_HEADER).unsignedWord(0)&0x7FFF;
        if(bound!=key.bound)throw new IllegalArgumentException("Original R13 visual bound differs: "+key);
        // HUD63 is a metadata hull over the27-tile source plus native raw digit/life slots, not a new art source.
        if(key==Key.HUD && sources.source(ART_HUD).tileCount()!=27)throw new IllegalArgumentException("Original HUD source count differs");
        byte[] map=view(key.map).copy();byte[] script=key.script==null?new byte[0]:view(key.script).copy();
        CdObjectVisualAssets result;
        if(key.end!=0)result=CdObjectVisualAssets.decodeIndexed(map,key.root,key.frames,key.start,key.end,script,key.scripts,bound);
        else if(key.script==null)result=CdObjectVisualAssets.decodeStatic(map,key.frames,bound);
        else result=CdObjectVisualAssets.decode(map,key.frames,script,key.scripts,bound);
        if(key==Key.TITLE_CARD)validateTitleBounds(result);
        visuals[key.ordinal()]=result;return result;
    }

    private void validateTitleBounds(CdObjectVisualAssets visual) {
        if(sources.source(ART_TITLE_CARD).tileCount()!=109 || sources.source(ART_TITLE_CARD_TEXT).tileCount()!=52)
            throw new IllegalArgumentException("Original R13 title source counts differ");
        // Native PLC_Main: card6C00, text7A00, offset112. Slots109..111 remain outside either source.
        for(int frame=0;frame<9;frame++)for(var piece:visual.mappingFrames().get(frame).pieces()) {
            int start=frame>=7?112:0,end=frame>=7?164:109;
            if(piece.tileOffset()<start || piece.tileOffset()>end-piece.tileCount())
                throw new IllegalArgumentException("R13 title mapping exceeds its actual card/text source");
        }
    }
    private static boolean bad(CdNativeFile file) {
        if(file==CdNativeFile.R13C)return false;if(file==CdNativeFile.R13D)return true;
        throw new IllegalArgumentException("Only original R13C/D object metadata");
    }
}
