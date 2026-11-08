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
                    pose(assets,prefix+"-spin1"),null,pose(assets,prefix+"-spin3"))));
        }
        return new CharacterPresentation("MICA","MICA",art,Map.of(new Font(Host.S1,Style.HUD),
                Map.of('M',image(assets,"s1-name-M"),'A',image(assets,"s1-name-A"))));
    }
    private static Image image(ModAssetRoot assets,String name) throws IOException {
        return PresentationImages.read(assets,ROOT+name+".png");
    }
    private static SignFrame pose(ModAssetRoot assets,String name) throws IOException {
        return new SignFrame(image(assets,name),PresentationImages.backing(assets,ROOT+name+"-backing.png",0));
    }
}
