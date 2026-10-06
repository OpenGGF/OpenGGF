package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * The Slot Machine event: a cabinet around the Slot Machine bonus stage's own reels, its
 * 32-pixel faces from the ROM ({@link SlotReels}). Shown a face ("3"), it pulls the lever and
 * spins all three reels to that face, clattering with sfx_SlotMachine every 16 frames as the
 * stage does (loc_4C21C); the event pays out when they stop. Without the ROM's reel art the
 * reels show monitor faces and the event carries straight on.
 */
final class SlotMachinePicture extends EventPicture {
    private final SlotReels reels;
    private String face;

    SlotMachinePicture(Shell shell) {
        int[][] strips = shell.art.slotStrips();
        reels = strips == null ? null : new SlotReels(strips, (int) shell.ticks);
    }

    @Override
    void show(Shell shell, String detail) {
        if (reels == null) {
            return;
        }
        face = detail;
        reels.pull(Integer.parseInt(detail), (int) shell.ticks);
        shell.sfx(Sounds.SFX_SWITCH);
    }

    @Override
    boolean busy() {
        return reels != null && reels.running();
    }

    @Override
    void tick(Shell shell) {
        if (reels == null) {
            return;
        }
        boolean wasRunning = reels.running();
        reels.tick();
        if (reels.running() && shell.ticks % 16 == 0) {
            shell.sfx(Sounds.SFX_SLOT_MACHINE);
        }
        if (wasRunning && !reels.running()) {
            // Robotnik's spike balls and the Bar's dud sting; anything else pays out.
            shell.sfx(face.equals("4") || face.equals("6") ? Sounds.SFX_SPIKES : Sounds.SFX_RING);
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int cx = x + w / 2;
        int ground = y + h - 22;
        Gfx.gradient(c, x, y, w, h, 0xFF300848, 0xFF100420);
        EventArt.stars(c, x, y, w, h, t);
        c.fill(x, ground, w, y + h - ground, 0xFF201030);
        c.fill(cx - 56, y + 18, 112, ground - y - 18, 0xFFDA2424);
        c.fill(cx - 52, y + 22, 104, 14, EventArt.GOLD);
        // Chasing lights round the top panel; all of them blink once the reels pay out.
        boolean won = reels != null && reels.sinceStop() >= 0 && reels.sinceStop() < 90;
        for (int i = 0; i < 13; i++) {
            boolean on = won ? (t / 8) % 2 == 0 : (t / 6 + i) % 3 == 0;
            c.fill(cx - 50 + i * 8, y + 26, 5, 5, on ? Colors.WHITE : EventArt.GOLD_DARK);
        }
        int wy = y + 45;
        c.fill(cx - 53, wy - 3, 106, 38, won && (t / 8) % 2 == 0 ? EventArt.GOLD : Colors.BLACK);
        c.fill(cx - 52, wy - 2, 104, 36, Colors.BLACK);
        for (int i = 0; i < 3; i++) {
            int rx = cx - 51 + i * 35;
            if (!drawReel(shell, c, i, rx, wy)) {
                // Without the ROM's reel art, a monitor face stands in.
                c.fill(rx, wy, 32, 32, Colors.WHITE);
                String[] faces = {"3", "4", "10", "5", "9"};
                SceneSprite box = shell.art.romFrame("monitor", 0);
                if (box != null) {
                    float s = 0.8f;
                    HudIcons.monitor(shell, c, faces[i], rx + 16 - (box.width() / 2f - box.originX()) * s,
                            wy + 16 - (box.height() / 2f - box.originY()) * s, s, t);
                }
            }
        }
        // The lever: pulled down for a moment when the player pulls it.
        boolean down = reels != null && reels.sincePull() >= 0 && reels.sincePull() < 12;
        int knobY = down ? y + 58 : y + 34;
        int pivotY = y + 76;
        c.fill(cx + 57, knobY + 4, 4, pivotY - knobY - 4, 0xFFB6B6B6);
        c.fill(cx + 55, knobY, 8, 8, Colors.BLACK);
        c.fill(cx + 56, knobY + 1, 6, 6, 0xFFDA2424);
    }

    /**
     * One reel's window: the 32 rows from the reel's position down, the rest of its top face
     * then the start of the next (the stage copies them the same way). False without ROM art.
     */
    private boolean drawReel(Shell shell, SceneCanvas c, int reel, int x, int y) {
        if (reels == null) {
            return false;
        }
        var top = shell.art.slotFace(reels.face(reel));
        var next = shell.art.slotFace(reels.nextFace(reel));
        if (top == null || next == null) {
            return false;
        }
        int row = reels.row(reel);
        c.drawRegion(top, 0, row, 32, 32 - row, x, y, 32, 32 - row, SceneDraw.plain());
        if (row > 0) {
            c.drawRegion(next, 0, 0, 32, row, x, y + 32 - row, 32, row, SceneDraw.plain());
        }
        return true;
    }
}
