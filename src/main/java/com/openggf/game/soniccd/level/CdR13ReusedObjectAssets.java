package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.util.Objects;
/** Detached finite original C/D reuse metadata. Live Init/LEA operands select these exact views,
 * including actual EVEN bytes; matching dormant copies are not selected. N1ecc5f3af Act3 Data,
 * Tamabboh, Explosion, Flower, HUD/Points and Collision Chaos Roll Tunnel native consumers.
 * No source art/bank/current/decoder/PLC/service, gameplay state or invocation is created. */
public final class CdR13ReusedObjectAssets {
    public enum View {
        ANI_TAMABBOH_NORMAL(0x9C94,0x9CAC,12,"ad1878521436e3e2ad03488f523fb96cda2ce64229265514a4bb6972a7e50474"),
        ANI_TAMABBOH_DAMAGED(0x9CA0,0x9CB8,26,"09a26ea9ee38de8e1799d02b191d160beac335155b9d6bae6c3d1ed678577f84"),
        MAP_SPR_TAMABBOH(0x9CBA,0x9CD2,62,"e30906f183ff4995fb9bfb0661be6003d3cd0431caa10b9d784ecf64a5b36847"),
        ANI_TAMABBOH_MISSILE(0x9DBA,0x9DD2,8,"764bc1e99533aeb906c6b6ecf4627f5fc11904307d477b1827a49776f41c57a5"),
        MAP_SPR_TAMABBOH_MISSILE(0x9DC2,0x9DDA,42,"10cba8d961bb868885a64ddb360f0182e18b4b5256eebac3665363c984e992cb"),
        ANI_EXPLOSION(0x3C5DC,0x3C7DA,20,"9119f6ae5b68628549bb895b3bb7dcc3a6d4d9cc81918efc848846f745359649"),
        MAP_SPR_EXPLOSION(0x3C5F0,0x3C7EE,146,"0b509dfbd51d21babeb6f551b2032e8e82325c5a518f8c5e976b88b039624d44"),
        ANI_FLOWER(0x336FA,0x336FA,28,"74105bb36227383768d51cd9084560633d8315a9c940e23e80c707532a5353e9"),
        MAP_SPR_FLOWER(0x33716,0x33716,90,"48b7482f83af1300d834e07ec7326e670740d8c5f89f5c8a2253de123e46bb41"),
        MAP_SPR_POINTS(0x8FB6,0x8FCE,82,"24a65d4f2732e077fa1d9c881479a5e52cb46c7a9a371f6f30f0efa67ecf459e"),
        MAP_SPR_POWERUP(0x3C404,0x3C602,274,"d79b2da45d7a63478f2f38d9288704bea375982e1d35d8aa97863cfdffb5f3fc");
        final int c,d,length;final String sha;
        View(int c,int d,int length,String sha){this.c=c;this.d=d;this.length=length;this.sha=sha;}
        public CdR13FutureAssets.FileRange range(CdNativeFile file){return new CdR13FutureAssets.FileRange(file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,select(file,c,d),length);}
        public String sha256(CdNativeFile file){select(file,c,d);return sha;}
    }
    private final CdR13FutureAssets assets;private final CdR13ObjectAssets startup;
    private final CdMmdImage image;private final CdR13SectionSources sources;
    private final CdAssetBytes[] views=new CdAssetBytes[View.values().length];
    private final CdObjectVisualAssets[] visuals=new CdObjectVisualAssets[6];
    private CdR13ReusedObjectAssets(CdR13FutureAssets assets,CdR13ObjectAssets startup){
        this.assets=Objects.requireNonNull(assets,"actual original C/D owner");this.startup=Objects.requireNonNull(startup,"actual same-source startup metadata");
        if(!startup.ownsAssets(assets)||!startup.identity().equals(assets.identity()))throw new IllegalArgumentException("R13 reuse metadata requires retained exact aggregate/startup");
        assets.requireAuthenticatedLayout();image=Objects.requireNonNull(assets.originalImage(),"retained original C/D image");
        if(!assets.identity().mmdSha256().equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))throw new IllegalArgumentException("Changed original programme image");
        sources=CdR13SectionSources.fromAssets(assets);
    }
    public static CdR13ReusedObjectAssets fromAssets(CdR13FutureAssets assets,CdR13ObjectAssets startup){return new CdR13ReusedObjectAssets(assets,startup);}
    public boolean ownsAssets(CdR13FutureAssets a,CdR13ObjectAssets o){return assets==a&&startup==o;}
    public CdR13ObjectAssets startup(){return startup;}
    public CdR13FutureAssets.Identity identity(){return assets.identity();}
    public synchronized CdAssetBytes view(View source){Objects.requireNonNull(source);var old=views[source.ordinal()];if(old!=null)return old;var bytes=source.range(identity().file()).read(image);if(!source.sha256(identity().file()).equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Changed original C/D reuse view: "+source);views[source.ordinal()]=bytes;return bytes;}
    public CdObjectVisualAssets tamabbohNormal(){return visual(0);}
    public CdObjectVisualAssets tamabbohDamaged(){return visual(1);}
    public CdObjectVisualAssets tamabbohMissile(){return visual(2);}
    public CdObjectVisualAssets explosion(){return visual(3);}
    public CdObjectVisualAssets flower(){return visual(4);}
    public CdObjectVisualAssets points(){return visual(5);}
    private synchronized CdObjectVisualAssets visual(int kind){var old=visuals[kind];if(old!=null)return old;CdObjectVisualAssets value;
        switch(kind){
        case 0->{bound(CdR13SectionSources.SourceId.ART_TAMABBOH,59);value=CdObjectVisualAssets.decode(view(View.MAP_SPR_TAMABBOH).copy(),3,view(View.ANI_TAMABBOH_NORMAL).copy(),2,59,java.util.Map.of(0,12));}
        case 1->{bound(CdR13SectionSources.SourceId.ART_TAMABBOH,59);value=CdObjectVisualAssets.decodeIndexed(view(View.MAP_SPR_TAMABBOH).copy(),22,3,28,62,view(View.ANI_TAMABBOH_DAMAGED).copy(),2,59);}
        case 2->{bound(CdR13SectionSources.SourceId.ART_TAMABBOH,59);value=CdObjectVisualAssets.decode(view(View.MAP_SPR_TAMABBOH_MISSILE).copy(),4,view(View.ANI_TAMABBOH_MISSILE).copy(),1,59);}
        case 3->{bound(CdR13SectionSources.SourceId.ART_EXPLOSIONS,70);value=CdObjectVisualAssets.decode(view(View.MAP_SPR_EXPLOSION).copy(),7,view(View.ANI_EXPLOSION).copy(),2,70);}
        case 4->{bound(CdR13SectionSources.SourceId.ART_FLOWER,39);value=CdObjectVisualAssets.decode(view(View.MAP_SPR_FLOWER).copy(),8,view(View.ANI_FLOWER).copy(),4,39);}
        case 5->{bound(CdR13SectionSources.SourceId.ART_POINTS,5);value=CdObjectVisualAssets.decodeStatic(view(View.MAP_SPR_POINTS).copy(),6,5);}
        default->throw new IllegalArgumentException("Finite visual");}
        visuals[kind]=value;return value;
    }
    private void bound(CdR13SectionSources.SourceId source,int count){if(sources.source(source).tileCount()!=count)throw new IllegalArgumentException("Original source bound differs");}
    private static int select(CdNativeFile file,int c,int d){if(file==CdNativeFile.R13C)return c;if(file==CdNativeFile.R13D)return d;throw new IllegalArgumentException("Only original C/D reuse metadata");}
}
