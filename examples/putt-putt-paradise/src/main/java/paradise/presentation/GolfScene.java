package paradise.presentation;

import com.openggf.game.presentation.ScenePresentationFrame;
import java.util.ArrayList;
import static com.openggf.game.presentation.ScenePresentationFrame.*;

/** Creator-owned finish marker. Course coordinates become immutable scene geometry before publication. */
public final class GolfScene {
    private GolfScene() { }

    public static ScenePresentationFrame withFinishFlag(ScenePresentationFrame frame, int finishX, int finishY) {
        return withFinishFlag(frame, finishX, finishY, 0);
    }

    /**
     * wave is a presentation clock: the pennant ripples while the pole stays fixed. Recorded
     * replay frames keep the phase they were captured with, so a rewind plays the ripple backward.
     */
    public static ScenePresentationFrame withFinishFlag(ScenePresentationFrame frame, int finishX, int finishY, long wave) {
        int x = finishX - frame.cameraX(), base = finishY + 32 - frame.cameraY();
        var vertices = new ArrayList<Vertex>();
        // A small gold pennant remains distinct from the native signpost/capsule.
        vertices.add(new Vertex(x - 6, base - 2, x + 8, base + 2, 0x90000000));
        vertices.add(new Vertex(x - 3, base - 76, x + 4, base, 0xFF6E430A));
        vertices.add(new Vertex(x - 2, base - 76, x + 2, base, 0xFFE5B73B));
        vertices.add(new Vertex(x - 1, base - 74, x + 1, base - 2, 0xFFFFE8A1));
        vertices.add(new Vertex(x - 4, base - 80, x + 4, base - 75, 0xFFFFD14D));
        for (int row = 0; row < 16; row++) {
            // The ripple grows toward the free end; the hoist stays attached to the pole.
            int ripple = (int) Math.round(2.5 * Math.sin(wave * 0.11 - row * 0.42));
            int length = 32 - 3 * Math.abs(row - 7) + ripple;
            int shade = ripple > 0 ? (row < 8 ? 0xFFFFE07A : 0xFFE2AC34) : row < 8 ? 0xFFFFD14D : 0xFFD49B22;
            vertices.add(new Vertex(x + 2, base - 73 + row, x + 2 + length, base - 72 + row, shade));
        }
        vertices.add(new Vertex(x + 4, base - 69, x + 8, base - 61, 0xFFFFF2BF));
        vertices.add(new Vertex(x - 5, base - 3, x + 5, base, 0xFFFFD14D));
        var primitives = new ArrayList<>(frame.primitives());
        primitives.add(new Primitive(frame.tiles().size(), PrimitiveKind.RECTANGLE, 0, vertices));
        return new ScenePresentationFrame(frame.revision(), frame.act(), frame.width(), frame.height(),
                frame.cameraX(), frame.cameraY(), frame.backdropArgb(), frame.paletteArgb(), frame.tiles(), primitives);
    }
}
