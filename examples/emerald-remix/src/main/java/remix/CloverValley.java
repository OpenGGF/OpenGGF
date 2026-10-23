package remix;

import com.openggf.game.modzone.ModZoneRuntimeContext;
import com.openggf.game.modzone.ModZoneRuntimeServices;
import com.openggf.level.scroll.ZoneScrollHandler;

/**
 * Clover Valley's only runtime contribution: background parallax. The sky and clouds scroll at
 * an eighth of the camera, the mountains and hills at a quarter, and the background rises a
 * sixteenth of the camera's height so the horizon stays in view.
 */
final class CloverValley {
    /** Background rows above this line are sky and clouds; below it, mountains and hills. */
    static final int HORIZON = 150;

    private CloverValley() { }

    static ModZoneRuntimeServices runtime(ModZoneRuntimeContext context) {
        return ModZoneRuntimeServices.builder().scroll(new Scroll()).build();
    }

    /** Stateless: every value derives from the camera, so restored frames recompute identically. */
    static final class Scroll implements ZoneScrollHandler {
        private short vscroll;

        @Override public void update(int[] lines, int cameraX, int cameraY, int frameCounter, int actId) {
            vscroll = (short) (cameraY / 16);
            int fg = (-cameraX) & 0xFFFF;
            for (int line = 0; line < lines.length; line++) {
                int bg = line + vscroll < HORIZON ? cameraX / 8 : cameraX / 4;
                lines[line] = fg << 16 | ((-bg) & 0xFFFF);
            }
        }
        @Override public short getVscrollFactorBG() { return vscroll; }
        @Override public int getMinScrollOffset() { return 0; }
        @Override public int getMaxScrollOffset() { return 0; }
    }
}
