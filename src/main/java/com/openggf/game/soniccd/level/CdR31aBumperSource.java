package com.openggf.game.soniccd.level;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.objects.CdObjectAnimation;
import com.openggf.game.soniccd.objects.CdObjectMappings;
import java.util.*;

/** Original same-owner R31A P3 DATA prerequisite. Only Bumper is registered for live dispatch.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff CC Bumper/Flipper, Glass, Platform, Pocket includes.
 * Canonical/read extents remain literal. No current-art, other-body or regional admission. */
public final class CdR31aBumperSource {
    enum View {
        BUMPER_MAPPINGS(0xB0AA,49,"1457505865e8ace2f1ddd839b79de427dadb0e81f1fd6f6681463a56287ab0fc"),
        FLIPPER_MAPPINGS(0xB0EA,61,"76f66835672139075de3eda4fbfb6fd0a23eed4f3acf34ee7c99365ae70bc5a2"),
        GLASS_BREAK_MAPPINGS(0xB30E,26,"a00c6e3c1cda44f0d81c30948f442893df31a13aa304db508740f8e0ce9f6992"),
        BUMPER_PLATFORM_MAPPINGS(0xB26C,33,"9f3285bf8f25646ee95178fb2b4d832affec6a41a1d6f0b4bffb842d58c9e51c"),
        POCKET_1_MAPPINGS(0xB880,42,"7c0e87ca5ef5fb7457c7d4305789b38c45968abbebb8978a028e86d7979bb0d4"),
        POCKET_2_MAPPINGS(0xB918,16,"724f36d2c178f081557e68d20cab6a65c33a948d97ebacba2a02d75348c14534"),
        BUMPER_ANIMATIONS(0xB09A,15,"da522ac3e6ecd46d6f1a334bfa8f3a6c8ea2a1f9db88cc829a8bf89a69bc0739"),
        FLIPPER_ANIMATIONS(0xB0DC,14,"6f9574db4542aee9a5e545fbbc958d1c2c37c1e1a994784f84c66f0cd096d408"),
        GLASS_BREAK_ANIMATIONS(0xB304,9,"0475253553c44e33ad81495d235e83fe632c3a7b74c54e284c4d0ca9c44b962b"),
        POCKET_ANIMATIONS(0xB912,6,"afeed3ae14c968c5a47925c5f2c76941f6034534aba04af7e65091fa44039f8a"),
        FLIPPER_SLOPE_READ_HULL(0xB01A,129,"1523ac8274d5ddd7908400056722ae9121ac77d7d58fce268e3686c90a2f34dd");
        final int offset,length;final String sha;
        View(int offset,int length,String sha){this.offset=offset;this.length=length;this.sha=sha;}
    }
    private final CdR31aAssets owner;private final CdR31PinballAssets assets;
    private CdR31aBumperSource(CdR31aAssets owner){
        this.owner=Objects.requireNonNull(owner);owner.requireAuthenticated();
        if(owner.view(CdR31aAssets.Asset.OBJECT_INDEX).unsignedLong((0x1d-1)*4)!=0x20AD9EL)
            throw new IllegalArgumentException("Original Bumper handler differs");
        assets=new CdR31PinballAssets(CdNativeFile.R31A,
            visual(View.BUMPER_MAPPINGS,3,View.BUMPER_ANIMATIONS,2),
            visual(View.FLIPPER_MAPPINGS,3,View.FLIPPER_ANIMATIONS,2),
            visual(View.GLASS_BREAK_MAPPINGS,2,View.GLASS_BREAK_ANIMATIONS,1),
            staticVisual(View.BUMPER_PLATFORM_MAPPINGS,1),staticVisual(View.POCKET_1_MAPPINGS,4),
            visual(View.POCKET_2_MAPPINGS,2,View.POCKET_ANIMATIONS,1),read(View.FLIPPER_SLOPE_READ_HULL));
    }
    private CdAssetBytes read(View v){return owner.internalReader().view(v);}
    private CdR31PinballAssets.Visual staticVisual(View map,int frames){
        return new CdR31PinballAssets.Visual(CdObjectVisualAssets.decodeStatic(read(map).copy(),frames,0x800),0x200000+map.offset,Optional.empty());
    }
    private CdR31PinballAssets.Visual visual(View map,int frames,View animation,int scripts){
        var v=staticVisual(map,frames);return new CdR31PinballAssets.Visual(v.visual(),v.mappingAddress(),
            Optional.of(new CdObjectAnimation.AnimationTable(read(animation),0,scripts)));
    }
    static CdR31aBumperSource create(CdR31aAssets owner){return new CdR31aBumperSource(owner);}
    public static CdR31aBumperSource fromAssets(CdR31aAssets owner){return Objects.requireNonNull(owner).bumperSource();}
    public boolean owns(CdR31aAssets candidate){return owner==candidate;}
    public CdR31PinballAssets.Visual data(){return assets.bumper();}
    public CdR31PinballAssets assets(){return assets;}
    public CdObjectMappings.DeclaredTable mapping(){return new CdObjectMappings.DeclaredTable(data().mappingAddress(),data().visual().mappings(),0,3);}
}
