package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;

/**
 * The Archive Terminal, in a room of Robotnik's base: a green-screen terminal wearing the
 * Robotnik monitor's face (Map_Monitor frame 3) over scrolling files, and a server rack with
 * blinking lights. [Read] (after the card is picked): a download bar fills, ticking with
 * sfx_Switch every four frames and ringing up sfx_Register when done, as the act results tally
 * does (Obj_LevelResults loc_2DC7E); then the card scans onto the screen and stays there.
 * [Rest]: the screen dims to standby and the hero curls up by the rack (Tails yawns first, his
 * own AniTails wait frames $B2-$B4) as the Zs drift up.
 */
final class TerminalPicture extends TimedPicture {
    private static final int SCREEN_W = 64;
    private static final int SCREEN_H = 62;
    private static final int GREEN = 0xFF24DA48;
    private static final int SCREEN_BG = 0xFF001800;
    /** Mod timing: 64 frames of download, then the card scans in two rows a frame. */
    private static final int DOWNLOAD = 64;
    private static final int SCAN = DOWNLOAD + 6;
    private static final int READ_LENGTH = SCAN + SCREEN_H / 2 + 10;
    /**
     * Tails' yawn: the end of his wait script (AniTails_Wait: $B2, then $B3/$B4 five times,
     * then $B2), at its delay of 8 (each frame held nine frames).
     */
    private static final String YAWN = "234343434342";
    private static final int YAWN_HOLD = 9;
    /** Mod timing: the screen dims over 20 frames; the sleeper nods off 16 frames after any yawn. */
    private static final int DIM = 20;
    private static final int NOD_OFF = 16;

    private Card card;
    private int restLength;
    private int sleepAt;

    TerminalPicture(Shell shell) {
    }

    @Override
    void begin(Shell shell, String verb, String argument) {
        card = verb.equals("read") ? card(shell, argument) : null;
        boolean tails = hero(shell).equals("tails");
        sleepAt = (tails ? YAWN.length() * YAWN_HOLD : 0) + NOD_OFF;
        restLength = sleepAt + 40;
        if (verb.equals("rest")) {
            shell.sfx(Sounds.SFX_SWITCH);
        }
    }

    @Override
    boolean playing() {
        return verb().equals("read") ? clock < READ_LENGTH : clock < restLength;
    }

    @Override
    void step(Shell shell) {
        if (!verb().equals("read")) {
            return;
        }
        if (clock > 0 && clock < DOWNLOAD && clock % 4 == 0) {
            shell.sfx(Sounds.SFX_SWITCH);
        } else if (clock == DOWNLOAD) {
            shell.sfx(Sounds.SFX_REGISTER);
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        BaseInterior.draw(c, x, y, w, h, ground, 0xFF14202C, 0xFF243444);
        boolean resting = verb().equals("rest");
        boolean asleep = resting && clock >= sleepAt;

        // A server rack on the left wall, its lights blinking (dimmed once the room sleeps).
        int rx = x + 4;
        c.fill(rx, y + 26, 26, ground - y - 31, 0xFF101820);
        c.fill(rx + 1, y + 27, 24, ground - y - 33, 0xFF384858);
        for (int i = 0; i < 9; i++) {
            int ly = y + 31 + i * 8;
            c.fill(rx + 3, ly, 20, 5, 0xFF1C2630);
            boolean on = ((t / 10) + i * 7) % 5 != 0;
            c.fill(rx + 5, ly + 2, 2, 1, on ? (i % 3 == 0 ? 0xFFFF4848 : GREEN) : 0xFF0C3018);
            c.fill(rx + 9, ly + 2, 2, 1, ((t / 6) + i) % 3 == 0 ? 0xFFFFDA24 : 0xFF3C3010);
        }

        // The terminal: a steel cabinet, its screen, and a desk with a keyboard.
        int tx = x + 50;
        int sx = tx + 4;
        int sy = y + 16;
        c.fill(tx - 2, sy - 6, SCREEN_W + 12, SCREEN_H + 12, 0xFF101820);
        c.fill(tx - 1, sy - 5, SCREEN_W + 10, SCREEN_H + 10, 0xFF6C7C8C);
        c.fill(tx, sy - 4, SCREEN_W + 8, 1, 0xFFB6C6D6);
        c.fill(sx, sy, SCREEN_W, SCREEN_H, SCREEN_BG);
        drawScreen(shell, c, sx, sy, t, x, y, w, h);
        if (resting) {
            float k = Math.min(1f, clock / (float) DIM);
            c.fill(sx, sy, SCREEN_W, SCREEN_H, Colors.alpha(Colors.BLACK, 0.75f * k));
            if ((t / 30) % 2 == 0) {
                c.fill(sx + SCREEN_W - 6, sy + SCREEN_H - 5, 3, 2, 0xFF0C6C24);
            }
        }
        int deskTop = sy + SCREEN_H + 14;
        c.fill(tx - 6, deskTop, SCREEN_W + 18, ground - 5 - deskTop, 0xFF384858);
        c.fill(tx - 6, deskTop, SCREEN_W + 18, 2, 0xFF8C9CB0);
        c.fill(tx + 2, deskTop - 3, SCREEN_W, 3, 0xFF242C34);
        for (int k = 0; k < 10; k++) {
            c.fill(tx + 4 + k * 6, deskTop - 2, 4, 1, 0xFF8C9CB0);
        }

        // The hero, by the rack.
        String hero = hero(shell);
        int heroX = x + 30;
        if (!resting) {
            Poses.hero(shell, c, hero, Poses.WAIT, t, heroX, ground, SceneDraw.plain());
        } else {
            drawResting(shell, c, hero, heroX, ground, t);
        }
        if (asleep) {
            // Zs drift up from the sleeper, one every 20 frames (a drawn flourish).
            for (int i = 0; i < 3; i++) {
                int start = sleepAt + i * 20;
                if (clock >= start) {
                    int age = (clock - start) % 60;
                    shell.font.draw(c, "Z", heroX + 6 + age / 6, ground - 30 - age / 2,
                            Colors.alpha(Colors.WHITE, 1f - age / 60f));
                }
            }
        }
    }

    /** The screen: the archive's files scrolling under Robotnik's face, or the download and the card. */
    private void drawScreen(Shell shell, SceneCanvas c, int sx, int sy, long t, int wx, int wy, int ww, int wh) {
        boolean reading = verb().equals("read");
        if (!reading) {
            BaseInterior.robotnikIcon(shell, c, sx + 12, sy + 11, 1f, wx, wy, ww, wh);
            shell.font.draw(c, "ARCHIVE", sx + 24, sy + 6, GREEN);
            for (int i = 0; i < 6; i++) {
                int len = 8 + (int) (((i * 37 + t / 8) * 13) % 44);
                c.fill(sx + 4, sy + 24 + i * 6, len, 2, 0xFF189030);
            }
            if ((t / 20) % 2 == 0) {
                c.fill(sx + 4, sy + 24 + 36, 4, 2, GREEN);
            }
            return;
        }
        if (clock < SCAN) {
            shell.font.draw(c, "DOWNLOAD", sx + 6, sy + 8, GREEN);
            String name = card == null ? "" : card.name().toUpperCase();
            int line = 0;
            for (String part : shell.font.wrap(name, SCREEN_W - 10)) {
                if (line < 2) {
                    shell.font.draw(c, part, sx + 6, sy + 18 + line * 8, 0xFFB6FFB6);
                }
                line++;
            }
            int filled = Math.min(DOWNLOAD, clock) * (SCREEN_W - 12) / DOWNLOAD;
            c.fill(sx + 5, sy + 38, SCREEN_W - 10, 8, GREEN);
            c.fill(sx + 6, sy + 39, SCREEN_W - 12, 6, SCREEN_BG);
            c.fill(sx + 6, sy + 39, filled, 6, GREEN);
            String pct = (Math.min(DOWNLOAD, clock) * 100 / DOWNLOAD) + "%";
            shell.font.draw(c, pct, sx + SCREEN_W / 2 - shell.font.width(pct) / 2, sy + 50, GREEN);
            return;
        }
        // The card scans onto the screen, two rows a frame, a bright line at the edge.
        int rows = Math.min(SCREEN_H, (clock - SCAN) * 2);
        drawCard(shell, c, card, sx + SCREEN_W / 2, sy + 1);
        if (rows < SCREEN_H) {
            c.fill(sx, sy + rows, SCREEN_W, SCREEN_H - rows, SCREEN_BG);
            c.fill(sx, sy + rows, SCREEN_W, 1, 0xFFB6FFB6);
        }
        // Scanlines over the screen.
        for (int i = 0; i < SCREEN_H; i += 2) {
            c.fill(sx, sy + i, SCREEN_W, 1, 0x28000000);
        }
    }

    /** The hero settling down to sleep: Tails yawns first; everyone ends crouched (the duck pose). */
    private void drawResting(Shell shell, SceneCanvas c, String hero, int heroX, int ground, long t) {
        int yawn = hero.equals("tails") ? YAWN.length() * YAWN_HOLD : 0;
        if (clock < yawn) {
            SceneSpriteSet set = shell.art.character("tails");
            SceneSprite pose = set == null ? null : set.frame(0xB0 + YAWN.charAt(clock / YAWN_HOLD) - '0');
            if (pose != null) {
                // His idle tail swish behind him, as Poses.hero draws it for his wait.
                SceneSpriteSet tails = shell.art.rom().characterAccessory("tails");
                float originY = ground - (pose.height() - pose.originY());
                if (tails != null) {
                    c.draw(tails.frame(0x22 + (int) ((t / 8) % 5)), heroX, originY, SceneDraw.plain());
                }
                c.draw(pose, heroX, originY, SceneDraw.plain());
                return;
            }
        }
        Poses.hero(shell, c, hero, Poses.DUCK, t, heroX, ground, SceneDraw.plain());
    }
}
