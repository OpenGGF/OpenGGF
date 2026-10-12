package starfall;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;
import java.io.IOException;

/** Original creator scenery is authored here; recognizable Sonic art stays ROM-backed. */
public final class StarfallMod implements GgfMod {
    @Override public void register(ModContext context) {
        try {
            byte[] font = context.modAssets().readBounded("art/font.txt", 32768);
            context.requireDisplayWidth(528);
            context.registerStartupScene(() -> new FrontierScene(font));
        } catch (IOException e) {
            throw new IllegalStateException("Starfall font unavailable", e);
        }
    }
}
