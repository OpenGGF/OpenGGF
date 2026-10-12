package hardened;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectServices;

/**
 * World-space hazard marks drawn through the engine primitive queue around native ROM art.
 * Every shape is queued first in a dark outline pass and then in its colour, so the mark
 * reads against both MHZ's bright grass and its dark canopy. No ROM art is synthesized.
 */
final class Marks {
    static final int OUTLINE = 0x1A0C06;
    static final int SIGHT = 0xFFD23C, LOCK = 0xFF3A2A, LOCK_FLASH = 0xFFF4E0, SPENT = 0xD8604A;
    static final int CROWN = 0xFFDDA0, CROWN_TELL = 0xFFF4C8;
    private final int[] shapes = new int[4 * 48];
    private int count;

    private Marks() { }

    private Marks box(int x, int y, int w, int h) {
        if (w > 0 && h > 0 && count < shapes.length / 4) {
            shapes[count * 4] = x; shapes[count * 4 + 1] = y;
            shapes[count * 4 + 2] = w; shapes[count * 4 + 3] = h; count++;
        }
        return this;
    }

    private void draw(ObjectServices services, int rgb) {
        for (int i = 0; i < count; i++) emit(services, shapes[i * 4] - 1, shapes[i * 4 + 1] - 1,
                shapes[i * 4 + 2] + 2, shapes[i * 4 + 3] + 2, OUTLINE);
        for (int i = 0; i < count; i++) emit(services, shapes[i * 4], shapes[i * 4 + 1],
                shapes[i * 4 + 2], shapes[i * 4 + 3], rgb);
    }

    private static void emit(ObjectServices services, int x, int y, int w, int h, int rgb) {
        services.graphicsManager().registerCommand(new GLCommand(GLCommand.CommandType.RECTI, 0,
                (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, x, y, x + w, y + h));
    }

    /**
     * Four corner brackets around the aim point, {@code halfWidth x halfHeight} from the
     * centre. Keep the box outside the target's sprite: the player draws above object
     * marks, so a body-sized sight disappears behind Sonic. A committed lock adds four
     * outward side ticks, so the lock is a shape change as well as a colour change.
     */
    static void sight(ObjectServices services, int x, int y, int halfWidth, int halfHeight, boolean locked, int rgb) {
        var marks = new Marks();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                int cornerX = sx < 0 ? x - halfWidth : x + halfWidth - 1;
                int cornerY = sy < 0 ? y - halfHeight : y + halfHeight - 1;
                marks.box(sx < 0 ? cornerX : cornerX - 5, sy < 0 ? cornerY : cornerY - 1, 6, 2);
                marks.box(sx < 0 ? cornerX : cornerX - 1, sy < 0 ? cornerY : cornerY - 5, 2, 6);
            }
        }
        if (locked) {
            marks.box(x - halfWidth - 5, y - 1, 6, 2).box(x + halfWidth - 1, y - 1, 6, 2);
            marks.box(x - 1, y - halfHeight - 5, 2, 6).box(x - 1, y + halfHeight - 1, 2, 6);
        }
        marks.draw(services, rgb);
    }

    /** Three spikes on a band: the always-visible "cannot be bopped" crown of the sentry. */
    static void crown(ObjectServices services, int x, int baseY, int lift, int rgb) {
        var marks = new Marks().box(x - 8, baseY - 2, 17, 2);
        spikeUp(marks, x - 6, baseY - 2, 3 + lift);
        spikeUp(marks, x, baseY - 2, 5 + lift);
        spikeUp(marks, x + 6, baseY - 2, 3 + lift);
        marks.draw(services, rgb);
    }

    private static void spikeUp(Marks marks, int x, int baseY, int height) {
        for (int row = 0; row < height; row++) {
            int half = row < height / 2 ? 1 : 0;
            marks.box(x - half, baseY - 1 - row, 1 + 2 * half, 1);
        }
    }

    /**
     * Spinning spikes around a {@code halfWidth x halfHeight} shell: orthogonal and
     * diagonal sets alternate every four admitted ticks, so the shot reads as a spiked,
     * moving hazard rather than a second mushroom.
     */
    static void spikes(ObjectServices services, int x, int y, int halfWidth, int halfHeight, int age, int rgb) {
        var marks = new Marks();
        if ((age >> 2 & 1) == 0) {
            for (int step = 0; step < 4; step++) {
                int span = step < 2 ? 1 : 0;
                marks.box(x + halfWidth + step, y - span, 1, 1 + 2 * span);
                marks.box(x - halfWidth - 1 - step, y - span, 1, 1 + 2 * span);
                marks.box(x - span, y - halfHeight - 1 - step, 1 + 2 * span, 1);
                marks.box(x - span, y + halfHeight + step, 1 + 2 * span, 1);
            }
        } else {
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int cx = x + sx * (halfWidth - 2), cy = y + sy * (halfHeight - 1);
                    marks.box(cx + (sx < 0 ? -2 : 0), cy + (sy < 0 ? -2 : 0), 2, 2);
                    marks.box(cx + sx * 2 + (sx < 0 ? -1 : 0), cy + sy * 2 + (sy < 0 ? -1 : 0), 1, 1);
                    marks.box(cx + sx * 3 + (sx < 0 ? -1 : 0), cy + sy * 3 + (sy < 0 ? -1 : 0), 1, 1);
                }
            }
        }
        marks.draw(services, rgb);
    }
}
