package mica;

import com.openggf.game.CharacterPresentation;
import com.openggf.game.CharacterPresentation.*;
import com.openggf.game.PresentationImages;
import com.openggf.io.ModAssetRoot;
import java.io.IOException;
import java.util.Map;
import java.util.LinkedHashMap;

final class MicaPresentation {
    private static final String ROOT="art/mica/presentation/";
    static CharacterPresentation load(ModAssetRoot assets) throws IOException {
        var art=new LinkedHashMap<Host,Art>();
        for(var host:Host.values()) {
            String prefix=host.name().toLowerCase(java.util.Locale.ROOT);
            var face=pose(assets,prefix+"-sign");
            art.put(host,new Art(image(assets,prefix+"-hud"), image(assets,prefix+"-monitor"),
                    face.face(), face.face(), new Sign(face,
                    host==Host.S2 ? pose(assets,prefix+"-sign-alternate") : null,
                    pose(assets,prefix+"-spin1"),null,pose(assets,prefix+"-spin3")),MicaScreenFrames.load(assets,host)));
        }
        return new CharacterPresentation("MICA","MICA",art,Map.of(new Font(Host.S1,Style.HUD),
                Map.of('M',image(assets,"s1-name-M"),'A',image(assets,"s1-name-A"))), stages(assets), MicaEndings.load(assets));
    }
    private static Map<com.openggf.game.StageSpriteSet.Surface,com.openggf.game.StageSpriteSet> stages(ModAssetRoot assets) throws IOException {
        return Map.ofEntries(Map.entry(com.openggf.game.StageSpriteSet.Surface.S2_HALF_PIPE, new com.openggf.game.StageSpriteSet(Map.ofEntries(
Map.entry(0, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-00.png"), 16, 28)),
Map.entry(1, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-01.png"), 16, 28)),
Map.entry(2, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-02.png"), 16, 28)),
Map.entry(3, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-03.png"), 16, 28)),
Map.entry(4, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-04.png"), 28, 28)),
Map.entry(5, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-05.png"), 26, 28)),
Map.entry(6, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-06.png"), 25, 28)),
Map.entry(7, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-07.png"), 28, 28)),
Map.entry(8, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-08.png"), 20, 28)),
Map.entry(9, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-09.png"), 24, 28)),
Map.entry(10, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-10.png"), 25, 28)),
Map.entry(11, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-11.png"), 28, 28)),
Map.entry(12, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-12.png"), 24, 16)),
Map.entry(13, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-13.png"), 24, 16)),
Map.entry(14, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-14.png"), 24, 16)),
Map.entry(15, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-15.png"), 24, 17)),
Map.entry(16, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-16.png"), 16, 20)),
Map.entry(17, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s2-halfpipe-17.png"), 16, 20))))),
Map.entry(com.openggf.game.StageSpriteSet.Surface.S3K_BLUE_SPHERE, new com.openggf.game.StageSpriteSet(Map.ofEntries(
Map.entry(0, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-00.png"), 0, 0)),
Map.entry(1, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-01.png"), 16, 35)),
Map.entry(2, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-02.png"), 16, 43)),
Map.entry(3, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-03.png"), 16, 43)),
Map.entry(4, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-04.png"), 16, 43)),
Map.entry(5, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-05.png"), 18, 43)),
Map.entry(6, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-06.png"), 17, 43)),
Map.entry(7, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-07.png"), 17, 43)),
Map.entry(8, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-08.png"), 16, 43)),
Map.entry(9, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-09.png"), 16, 38)),
Map.entry(10, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-10.png"), 16, 38)),
Map.entry(11, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-blue-sphere-11.png"), 16, 38))))),
Map.entry(com.openggf.game.StageSpriteSet.Surface.S3K_SLOT_MACHINE, new com.openggf.game.StageSpriteSet(Map.ofEntries(
Map.entry(150, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-slot-96.png"), 16, 16)),
Map.entry(151, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-slot-97.png"), 16, 16)),
Map.entry(152, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-slot-98.png"), 16, 16)),
Map.entry(153, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-slot-99.png"), 16, 16)),
Map.entry(154, new com.openggf.game.StageSpriteSet.Frame(PresentationImages.read(assets,"art/mica/wave3/s3k-slot-9a.png"), 16, 16))))));
    }
    private static Image image(ModAssetRoot assets,String name) throws IOException {
        return PresentationImages.read(assets,ROOT+name+".png");
    }
    private static SignFrame pose(ModAssetRoot assets,String name) throws IOException {
        return new SignFrame(image(assets,name),PresentationImages.backing(assets,ROOT+name+"-backing.png",0));
    }
}
