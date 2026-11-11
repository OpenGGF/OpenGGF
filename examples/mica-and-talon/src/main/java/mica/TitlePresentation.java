package mica;

import com.openggf.game.*;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.io.ModAssetRoot;
import com.openggf.mods.assets.PresentationImages;
import java.io.IOException;
import java.util.*;

/** Generated native-selector bindings for original title art. Timing and logos remain native. */
final class TitlePresentation {
    static Map<Host,TitleMascot> load(ModAssetRoot assets, String character) throws IOException {
        String root="art/"+character+"/titles/";
        int[] rgb=PresentationImages.backing(assets,root+"s1-0.png",0).rgb();
        var result=new EnumMap<Host,TitleMascot>(Host.class);
        for (var host:Host.values()) {
            var parts=new EnumMap<TitlePart,StageSpriteSet>(TitlePart.class);
            var masks=new LinkedHashMap<Integer,TitlePlaneMask>();
            switch(host) {
                case S1 -> {
                    parts.put(TitlePart.BODY,new StageSpriteSet(Map.ofEntries(
                        Map.entry(0,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-0.png"),0,-8)),
                        Map.entry(1,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-1.png"),0,-16)),
                        Map.entry(2,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-2.png"),0,-16)),
                        Map.entry(3,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-3.png"),-8,-8)),
                        Map.entry(4,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-4.png"),-16,-8)),
                        Map.entry(5,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-5.png"),0,0)),
                        Map.entry(6,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-6.png"),0,0)),
                        Map.entry(7,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s1-7.png"),0,0)))));
                }
                case S2 -> {
                    // Keep each creator in its own native role, with native origins and rise paths.
                    // The optional API slot exchange remains available but is disabled here.
                    parts.put(TitlePart.BODY,new StageSpriteSet(Map.ofEntries(
                        Map.entry(0,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-0.png"),-24,-16)),
                        Map.entry(1,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-1.png"),0,-16)),
                        Map.entry(2,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-2.png"),-8,-16)),
                        Map.entry(3,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-3.png"),0,-16)),
                        Map.entry(4,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-4.png"),0,-8)),
                        Map.entry(5,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-5.png"),0,-8)),
                        Map.entry(6,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-6.png"),0,-8)),
                        Map.entry(7,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-7.png"),-16,-8)),
                        Map.entry(18,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-18.png"),-24,-8)))));
                    parts.put(TitlePart.HAND,new StageSpriteSet(Map.ofEntries(
                        Map.entry(9,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-9.png"),character.equals("mica") ? -14 : -8,0)),
                        Map.entry(19,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s2-19.png"),-8,0)))));
                }
                case S3K -> {
                    parts.put(TitlePart.FINGER,new StageSpriteSet(Map.ofEntries(
                        Map.entry(0,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s3k-sprite-0.png"),character.equals("mica") ? 40 : 24,character.equals("mica") ? 32 : 36)),
                        Map.entry(1,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s3k-sprite-1.png"),character.equals("mica") ? 40 : 24,character.equals("mica") ? 32 : 36)),
                        Map.entry(4,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s3k-sprite-4.png"),character.equals("mica") ? 40 : 24,character.equals("mica") ? 32 : 36)))));
                    parts.put(TitlePart.WINK,new StageSpriteSet(Map.ofEntries(
                        Map.entry(2,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s3k-sprite-2.png"),character.equals("mica") ? 12 : -16,40)),
                        Map.entry(3,new StageSpriteSet.Frame(PresentationImages.read(assets,root+"s3k-sprite-3.png"),character.equals("mica") ? 12 : -16,40)))));
                    for(var part:List.of(TitlePart.PLANE_LEFT,TitlePart.PLANE_RIGHT)) {
                        var frames=new LinkedHashMap<Integer,StageSpriteSet.Frame>();
                        String half=part==TitlePart.PLANE_LEFT ? "left" : "right";
                        for(int frame=1;frame<=13;frame++) frames.put(frame,new StageSpriteSet.Frame(
                                PresentationImages.read(assets,root+"s3k-plane-"+frame+"-"+half+".png"),0,0));
                        parts.put(part,new StageSpriteSet(frames));
                    }
                    for(int frame=1;frame<=13;frame++) masks.put(frame,new TitlePlaneMask(
                            assets.readBounded(root+"s3k-plane-"+frame+".mask",320*224),
                            frame == 13 ? assets.readBounded(root+"s3k-plane-13.clear",320*224) : new byte[320*224]));
                }
            }
            int[] hostRgb = host == Host.S3K && character.equals("mica")
                    ? PresentationImages.backing(assets,root+"s3k-plane-13-left.png",0).rgb() : rgb;
            result.put(host,new TitleMascot(parts,masks,hostRgb,false));
        }
        return Map.copyOf(result);
    }
}
