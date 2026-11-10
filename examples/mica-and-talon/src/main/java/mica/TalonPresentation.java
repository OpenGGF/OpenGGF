package mica;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.mods.assets.PresentationImages;
import com.openggf.io.ModAssetRoot;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;

final class TalonPresentation {
    private static final String ROOT="art/talon/presentation/";
    static CharacterPresentation load(ModAssetRoot assets) throws IOException {
        var art=new LinkedHashMap<Host,Art>();
        for(var host:Host.values()) {
            String prefix=host.name().toLowerCase(java.util.Locale.ROOT);
            var face=pose(assets,prefix+"-sign");
            art.put(host,new Art(image(assets,prefix+"-hud"), image(assets,prefix+"-monitor"),
                    face.face(), face.face(), new Sign(face,
                    host==Host.S2 ? pose(assets,prefix+"-sign-alternate") : null,
                    pose(assets,prefix+"-spin1"),null,pose(assets,prefix+"-spin3")),TalonScreenFrames.load(assets,host)));
        }
        return new CharacterPresentation("TALON","TALO",art,Map.of(new Font(Host.S1,Style.HUD),
                Map.of('T',image(assets,"s1-name-T"),'A',image(assets,"s1-name-A"),'L',image(assets,"s1-name-L"))), OrientedStageArt.load(assets,"talon",stages(assets)),
                TalonEndings.load(assets));
    }
    // FixBugs=0 slot goal clears character_id: Sonic roll also selects Tails duck/balance
    // mappings $99/$9A while the Tails resident bank is held. Supply those selectors too.
    private static Map<com.openggf.game.StageSpriteSet.Surface,com.openggf.game.StageSpriteSet> stages(ModAssetRoot assets) throws IOException {
        return Map.ofEntries(Map.entry(com.openggf.game.StageSpriteSet.Surface.S3K_SLOT_MACHINE, new com.openggf.game.StageSpriteSet(Map.ofEntries(Map.entry(150, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-slot-96.png"), 14, 14)),Map.entry(151, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-slot-97.png"), 14, 14)),Map.entry(152, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-slot-98.png"), 14, 14)),Map.entry(153, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-slot-99.png"), 16, 1)),Map.entry(154, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-slot-9a.png"), 13, 12))))),
Map.entry(com.openggf.game.StageSpriteSet.Surface.S2_HALF_PIPE, new com.openggf.game.StageSpriteSet(Map.ofEntries(Map.entry(0, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-00.png"), 12, 24)),
Map.entry(1, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-01.png"), 12, 23)),
Map.entry(2, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-02.png"), 13, 23)),
Map.entry(3, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-03.png"), 15, 23)),
Map.entry(4, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-04.png"), 16, 25)),
Map.entry(5, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-05.png"), 16, 24)),
Map.entry(6, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-06.png"), 14, 24)),
Map.entry(7, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-07.png"), 14, 23)),
Map.entry(8, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-08.png"), 14, 24)),
Map.entry(9, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-09.png"), 16, 24)),
Map.entry(10, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-10.png"), 18, 24)),
Map.entry(11, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-11.png"), 20, 23)),
Map.entry(12, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-12.png"), 20, 10)),
Map.entry(13, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-13.png"), 20, 12)),
Map.entry(14, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-14.png"), 20, 14)),
Map.entry(15, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-15.png"), 22, 16)),
Map.entry(16, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-16.png"), 14, 16)),
Map.entry(17, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s2-halfpipe-17.png"), 14, 16))))),
Map.entry(com.openggf.game.StageSpriteSet.Surface.S3K_BLUE_SPHERE, new com.openggf.game.StageSpriteSet(Map.ofEntries(Map.entry(1, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-01.png"), 10, 28)),
Map.entry(2, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-02.png"), 12, 35)),
Map.entry(3, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-03.png"), 12, 35)),
Map.entry(4, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-04.png"), 12, 35)),
Map.entry(5, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-05.png"), 11, 35)),
Map.entry(6, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-06.png"), 13, 35)),
Map.entry(7, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-07.png"), 14, 35)),
Map.entry(8, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-08.png"), 14, 35)),
Map.entry(9, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-09.png"), 12, 34)),
Map.entry(10, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-10.png"), 12, 34)),
Map.entry(11, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets, "art/talon/wave3/s3k-blue-sphere-11.png"), 12, 34))))));
    }
    private static Image image(ModAssetRoot assets,String name) throws IOException {
        return PresentationImages.read(assets,ROOT+name+".png");
    }
    private static SignFrame pose(ModAssetRoot assets,String name) throws IOException {
        return new SignFrame(image(assets,name),PresentationImages.backing(assets,ROOT+name+"-backing.png",0));
    }
}
