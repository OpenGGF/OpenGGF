package echoes;

import com.openggf.game.GameId;
import com.openggf.graphics.RenderContext;
import com.openggf.level.objects.ObjectServices;
import com.openggf.mods.run.CharacterLooks;

/** Derived presentation only. Each body owns an ordinary engine sidekick palette context. */
final class EchoLooks {
    static int colour(int slot) { return switch (slot) { case 0 -> 0xEE0; case 1 -> 0xE0E; case 2 -> 0x0AE; case 3 -> 0x2E6; default -> 0xE6A; }; }
    private final RenderContext[] contexts = new RenderContext[5];
    void reset() { java.util.Arrays.fill(contexts, null); }
    void refresh(ObjectServices services, int frame) {
        var level = services.levelManager().getCurrentLevel();
        var bodies = services.spriteManager().getSidekicks();
        for (int i = 0; i < Math.min(5, bodies.size()); i++) {
            var renderer = bodies.get(i).getSpriteRenderer();
            if (renderer == null) continue;
            if (contexts[i] == null) contexts[i] = RenderContext.createSidekickContext(GameId.S2);
            for (int line = 0; line < 4; line++) {
                var palette = level.getPalette(line).deepCopy();
                if (line == 0) for (var entry : CharacterLooks.withBaseColour("s2", "sonic", colour(i)).palette()) {
                    var c = palette.getColor(entry.index());
                    c.fromSegaFormat(entry.colour());
                    if (((frame / 12 + i) & 1) != 0) {
                        c.r = (byte) Math.min(255, (c.r & 255) + 10);
                        c.g = (byte) Math.min(255, (c.g & 255) + 10);
                        c.b = (byte) Math.min(255, (c.b & 255) + 10);
                    }
                }
                contexts[i].setPalette(line, palette);
            }
            renderer.setRenderContext(contexts[i]);
        }
        // Contexts created after level art initialization need explicit GPU publication.
        RenderContext.uploadDonorPalettes(services.graphicsManager());
    }
}
