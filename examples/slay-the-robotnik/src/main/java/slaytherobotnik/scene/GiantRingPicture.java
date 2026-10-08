package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import slaytherobotnik.core.Relic;
import slaytherobotnik.ui.Colors;

/**
 * The Giant Ring event: a Special Stage ring over the act's level, forming as the room opens
 * and turning as {@code Obj_SSEntryRing} does ({@link GiantRingEntry}), the hero waiting
 * beside it. Shown "jump" (or "jump:relic id"), the hero leaps in (sfx_Jump, the player's own
 * jump speed and gravity), vanishes with sfx_BigRing in the entry flash, and sfx_EnterSS plays
 * $20 frames later as the picture fades to white over Pal_FadeToWhite's $16 frames. Coming
 * back out is the mod's own flourish: the hero drops back in from above in the hurt pose with
 * sfx_Death - the HP the jump costs - falls with Player_Hurt's $30 gravity, lands, and holds
 * up the relic.
 */
final class GiantRingPicture extends StagedPicture {
    private static final int HERO_COL = 22;
    private static final int RING_COL = 82;
    /** Pal_FadeToWhite: $16 frames from the level to white. */
    private static final int FADE = 0x16;
    /** How long the picture stays white, then fades back (the mod's choice). */
    private static final int WHITE_HOLD = 12;
    /** How long the hero shows the relic before the event carries on (the mod's choice). */
    private static final int SHOW_RELIC = 45;

    private final String hero;
    private final byte[] ringScript;
    private final byte[] flashScript;
    private GiantRingEntry entry;
    private Relic relic;
    private boolean showing;
    /** Frames since sfx_EnterSS; -1 before. */
    private int sinceEntered = -1;
    private Motion thrown;
    private int landedFor = -1;

    GiantRingPicture(Shell shell) {
        hero = EventHero.id(shell);
        ringScript = RomTables.read(shell, RomTables.ANIRAW_SS_ENTRY_RING, 18);
        flashScript = RomTables.read(shell, RomTables.ANIRAW_SS_ENTRY_FLASH, 11);
    }

    /** The ring, made on the first frame drawn: it hangs 62 rows over the floor beneath it. */
    private GiantRingEntry entry(Shell shell) {
        if (entry == null && ringScript != null && flashScript != null && shell.art.romFrame("big_ring", 8) != null) {
            // Never higher than Knuckles' jump can reach from where the hero stands.
            int reach = floor(HERO_COL) - EventHero.standRadius(hero) - 70;
            entry = new GiantRingEntry(ringScript, flashScript, RING_COL, Math.max(reach, floor(RING_COL) - 62));
        }
        return entry;
    }

    @Override
    void show(Shell shell, String detail) {
        if (entry(shell) == null || showing) {
            return;
        }
        int colon = detail.indexOf(':');
        if (colon >= 0 && colon + 1 < detail.length() && shell.catalog.hasRelic(detail.substring(colon + 1))) {
            relic = shell.catalog.newRelic(detail.substring(colon + 1));
        }
        float heroY = floor(HERO_COL) - EventHero.standRadius(hero);
        entry.jump(HERO_COL, heroY, Motion.jumpSpeed(hero));
        showing = true;
        shell.sfx(Sounds.SFX_JUMP);
    }

    @Override
    boolean busy() {
        return showing && landedFor < SHOW_RELIC;
    }

    @Override
    void tick(Shell shell) {
        if (entry == null) {
            return;
        }
        entry.tick();
        if (entry.touchedNow()) {
            shell.sfx(Sounds.SFX_BIG_RING);
        }
        if (entry.enteredNow()) {
            shell.sfx(Sounds.SFX_ENTER_SPECIAL);
            sinceEntered = 0;
        } else if (sinceEntered >= 0) {
            sinceEntered++;
        }
        if (sinceEntered == FADE + WHITE_HOLD) {
            // Dropped back in from above, tumbling in the hurt pose and falling as a hurt player
            // does (Player_Hurt's $30 gravity), drifting back towards where the jump started.
            thrown = new Motion(entry.ringX() - 24, -24);
            thrown.xVel = -0x100;
            shell.sfx(Sounds.SFX_HURT);
        }
        if (thrown != null && landedFor < 0) {
            thrown.step(Motion.HURT_GRAVITY);
            float feet = floor(Math.round(thrown.x())) - EventHero.standRadius(hero);
            if (thrown.yVel > 0 && thrown.y() >= feet) {
                thrown.place(thrown.x(), feet);
                landedFor = 0;
            }
        } else if (landedFor >= 0) {
            landedFor++;
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        if (entry(shell) == null) {
            drawnRing(c, x + RING_COL, y + GROUND - 62);
            return;
        }
        float rx = x + entry.ringX();
        float ry = y + entry.ringY();
        int ringFrame = entry.ringFrame();
        if (ringFrame >= 0) {
            c.draw(shell.art.romFrame("big_ring", ringFrame), rx, ry, SceneDraw.plain());
            if (ringFrame >= 8) {
                EventArt.sparkles(shell, c, Math.round(rx), Math.round(ry), 40, t);
            }
        }
        drawHero(shell, c, x, y, t);
        int flash = entry.flashFrame();
        if (flash >= 0) {
            c.draw(shell.art.romFrame("ss_entry_flash", flash), rx, ry,
                    SceneDraw.plain().withFlipX(entry.flashFlipped()));
        }
        float white = whiteness();
        if (white > 0) {
            c.fill(x, y, w, h, Colors.alpha(Colors.WHITE, white));
        }
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, long t) {
        Motion jumper = entry.jumper();
        if (jumper != null) {
            EventHero.at(shell, c, hero, Poses.ROLL, t, x + jumper.x(), y + jumper.y(), SceneDraw.plain());
            return;
        }
        if (!showing) {
            Poses.hero(shell, c, hero, Poses.WAIT, t, x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
            return;
        }
        if (thrown == null) {
            return; // inside the ring
        }
        if (landedFor < 0) {
            EventHero.at(shell, c, hero, Poses.HURT, t, x + thrown.x(), y + thrown.y(), SceneDraw.plain());
            return;
        }
        int col = Math.round(thrown.x());
        int feet = y + floor(col);
        Poses.hero(shell, c, hero, Poses.VICTORY, landedFor, x + col, feet, SceneDraw.plain());
        if (relic != null) {
            int bob = landedFor < 12 ? (12 - landedFor) : 0;
            int top = feet - 64 - bob;
            HudIcons.relic(shell, c, relic, x + col - 10, top, 20);
            EventArt.sparkles(shell, c, x + col, top + 10, 14, t);
        }
    }

    /** 0-1: the fade to white after sfx_EnterSS and back. */
    private float whiteness() {
        if (sinceEntered < 0) {
            return 0;
        }
        if (sinceEntered < FADE) {
            return sinceEntered / (float) FADE;
        }
        if (sinceEntered < FADE + WHITE_HOLD) {
            return 1;
        }
        return Math.max(0, 1 - (sinceEntered - FADE - WHITE_HOLD) / (float) FADE);
    }

    /** Without the ROM's ring: a circle of gold dots where it would hang. */
    private static void drawnRing(SceneCanvas c, int cx, int cy) {
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            c.fill(cx + (int) (Math.cos(a) * 30) - 2, cy + (int) (Math.sin(a) * 30) - 2, 4, 4, EventArt.GOLD);
        }
    }
}
