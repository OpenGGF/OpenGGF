package sitarhero;

import com.openggf.mods.scene.SceneSprite;
import java.util.List;
import static sitarhero.PerformerCutout.Mask;

/** Reference geometry in native mapping-origin pixels, never replacement character art. */
final class PerformerRig {
    final PerformerCutout.Split pixels;
    final int contactY;
    final int armCount;
    final boolean foot;

    private record Geometry(int contactY, int armCount, boolean foot, Mask... masks) { }

    private PerformerRig(List<PerformerCutout.Positioned> sources, Geometry geometry, String performer, String game) {
        contactY = geometry.contactY(); armCount = geometry.armCount(); foot = geometry.foot();
        int armLayer = switch (performer) {
            case "egg-robo" -> 1;
            case "robotnik" -> game.equals("s3k") ? 1 : game.equals("s2") ? 2 : 0;
            default -> 0;
        };
        armLayer = Math.min(armLayer, sources.size() - 1);
        int[] owners = new int[geometry.masks().length];
        java.util.Arrays.fill(owners, armLayer);
        if (foot && performer.equals("egg-robo")) owners[armCount] = 0;
        pixels = PerformerCutout.splitLayers(sources, geometry.masks(), owners);
    }

    static PerformerRig of(SceneSprite sprite, String performer, String game) {
        return of(List.of(new PerformerCutout.Positioned(sprite, 0, 0)), performer, game);
    }

    static PerformerRig of(List<PerformerCutout.Positioned> sources, String performer, String game) {
        return new PerformerRig(sources, geometry(performer, game), performer, game);
    }

    private static Geometry geometry(String performer, String game) {
        return switch (performer) {
            case "sonic" -> {
                Mask left = switch (game) {
                    case "s1" -> box(-3, 4, -11, 3, -2, 11);
                    case "s2" -> box(-3, 3, -10, 2, -2, 11);
                    default -> box(-1, 3, -9, 2, 0, 11);
                };
                yield new Geometry(8, 2, true, left,
                        box(5, 3, 3, 1, 13, 9), box(3, 14, 1, 13, 14, 20));
            }
            case "tails" -> new Geometry(10, 2, true,
                    box(-5, 5, -10, 3, -4, 11), box(4, 5, 3, 4, 7, 11),
                    box(2, 11, 0, 10, 11, 16));
            case "knuckles" -> new Geometry(7, 2, true,
                    new Mask(0, 0, -6, -1, 1, -1, 3, 7, -1, 10, -7, 7),
                    new Mask(9, 0, 8, -2, 14, -2, 19, 4, 15, 9, 8, 8),
                    box(7, 14, 5, 13, 18, 20));
            case "silver-sonic" -> new Geometry(13, 2, true,
                    new Mask(-13, -1, -21, -2, -13, -2, -12, 8, -14, 17, -24, 17, -24, 7),
                    new Mask(13, -1, 13, -2, 21, -2, 24, 7, 24, 17, 14, 17, 12, 8),
                    box(11, 20, 7, 18, 21, 27));
            case "mecha-sonic" -> new Geometry(9, 2, true,
                    new Mask(-10, -6, -16, -7, -7, -7, -8, 5, -11, 16, -16, 13),
                    new Mask(10, -6, 7, -7, 16, -7, 16, 13, 11, 16, 8, 5),
                    box(7, 20, 3, 18, 12, 29));
            // The body mapping already contains the head and one hanging glove. Frame 2
            // is a gun/hand assembly, not a second head; replacing that weapon with the
            // selected musical prop avoids manufacturing a second arm from its barrel.
            case "egg-robo" -> new Geometry(14, 1, true,
                    new Mask(3, 1, 0, -1, 5, -1, 9, 6, -1, 13, -4, 22, -18, 22, -18, 11, -7, 9, 0, 4),
                    box(-12, 23, -24, 22, 0, 29));
            case "robotnik" -> switch (game) {
                // S1's native FZ frame 0 has two gloves. S2's three-bank WFZ frame 0
                // exposes one: animate that glove minimally rather than transplanting S1 art.
                case "s1" -> new Geometry(-1, 2, true,
                        box(-17, -5, -23, -7, -16, 1), box(-7, -5, -12, -7, -3, 1),
                        box(1, 19, -6, 18, 8, 30));
                case "s2" -> new Geometry(-4, 1, true,
                        box(-2, -6, -9, -9, 4, -1), box(1, 19, -3, 18, 9, 29));
                // S3K's eggmobile occupant has no exposed standing arms in frame 0.
                // Frame 2 supplies native raised sleeves, pivoted at their shoulders.
                default -> new Geometry(0, 2, false,
                        new Mask(-9, -8, -18, -16, -14, -16, -7, -9, -6, -6, -12, -6),
                        new Mask(9, -8, 7, -9, 14, -16, 18, -16, 12, -6, 6, -6));
            };
            default -> throw new IllegalArgumentException("Unknown performer " + performer);
        };
    }

    private static Mask box(int pivotX, int pivotY, int x0, int y0, int x1, int y1) {
        return new Mask(pivotX, pivotY, x0, y0, x1, y0, x1, y1, x0, y1);
    }
}
