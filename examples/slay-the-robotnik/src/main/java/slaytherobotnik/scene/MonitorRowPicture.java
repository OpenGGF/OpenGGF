package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;

/**
 * The Monitor Row event: three monitors on the act's level, each on its own {@code Ani_Monitor}
 * animation - a Super Ring (subtype 3), the player's own 1-Up (subtype 1, its icon the life
 * icon the level loads for that character) and a monitor of nothing but static (subtype 0,
 * which in Sonic 3 &amp; Knuckles is Robotnik's: Monitor_Give_Eggman). Shown "0", "1" or "2",
 * the hero hops (sfx_Jump; the player's jump speed, cut to -$400 by Sonic_JumpHeight as on a
 * tap of the button, and gravity) onto that monitor and bounces
 * off it (Touch_Monitor negates y_vel); it breaks as Obj_MonitorBreak breaks it - the break
 * animation to the broken shell, an explosion with sfx_Break and the icon rising
 * ({@link MonitorContents}) - and the icon's effect plays where the game gives it: sfx_RingRight
 * for the rings, the 1-Up jingle, and for the static monitor Robotnik's face (the mod lets it
 * flicker over the static as Ani_Monitor_Eggman2 does). The other two monitors stay.
 */
final class MonitorRowPicture extends StagedPicture {
    private static final int HERO_COL = 14;
    private static final int FIRST_COL = 43;
    private static final int SPACING = 33;
    /** Ani_Monitor animations: the subtypes shown, and the break. */
    private static final int ANIM_STATIC = 0;
    private static final int ANIM_ONE_UP = 1;
    private static final int ANIM_RINGS = 3;
    private static final int ANIM_BREAK = 0x0A;
    /** Map_Monitor frames: the box, the 1-Up icon, Robotnik's icon, the broken shell. */
    private static final int FRAME_ONE_UP = 2;
    private static final int FRAME_ROBOTNIK = 3;
    private static final int FRAME_BROKEN = 0x0B;
    /** Sonic_JumpHeight's cap on a jump whose button is already released: y_vel -$400. */
    private static final int SHORT_HOP = 0x400;
    /** How long the event waits once the icon has gone (the mod's choice). */
    private static final int AFTER = 10;

    private final String hero;
    private final SpriteAnim[] anims = new SpriteAnim[3];
    private int broken = -1;
    private Motion jumper;
    private boolean bounced;
    private boolean fullJump;
    private boolean landed;
    private MonitorContents contents;
    private int after = -1;
    private int ticks;
    /** Icon pieces cut from Map_Monitor frames, by frame. */
    private final SceneImage[] icons = new SceneImage[12];

    MonitorRowPicture(Shell shell) {
        hero = EventHero.id(shell);
        byte[] table = RomTables.read(shell, RomTables.ANI_MONITOR, 0x70);
        if (table != null && shell.art.romFrame("monitor", 0) != null) {
            int[] subtypes = {ANIM_RINGS, ANIM_ONE_UP, ANIM_STATIC};
            for (int i = 0; i < 3; i++) {
                anims[i] = new SpriteAnim(table, subtypes[i]);
            }
        }
    }

    private static int col(int monitor) {
        return FIRST_COL + monitor * SPACING;
    }

    @Override
    void show(Shell shell, String detail) {
        if (anims[0] == null || broken >= 0) {
            return;
        }
        broken = Math.max(0, Math.min(2, Integer.parseInt(detail)));
        // A jump timed to come down on the monitor's top: the hero's centre a rolling radius above it.
        float startY = floor(HERO_COL) - EventHero.standRadius(hero);
        float contactY = monitorCentre(broken) - 16 - EventHero.rollRadius();
        int speed = Motion.jumpSpeed(hero);
        int frames = 1 + Motion.framesToLand(SHORT_HOP, contactY - startY + speed / 256f);
        fullJump = frames <= 0;
        if (fullJump) {
            // The monitor stands too high for a hop: hold the button for the whole jump.
            frames = Motion.framesToLand(speed, contactY - startY);
        }
        jumper = new Motion(HERO_COL, startY);
        jumper.yVel = -speed;
        jumper.xVel = Math.round((col(broken) - HERO_COL) * 256f / Math.max(1, frames));
        shell.sfx(Sounds.SFX_JUMP);
    }

    @Override
    boolean busy() {
        return broken >= 0 && (after < AFTER);
    }

    @Override
    void tick(Shell shell) {
        ticks++;
        for (SpriteAnim anim : anims) {
            if (anim != null) {
                anim.tick();
            }
        }
        if (jumper != null && !landed) {
            moveJumper(shell);
        }
        if (contents != null) {
            contents.tick();
            if (contents.age() == 1) {
                shell.sfx(Sounds.SFX_BREAK);
            }
            if (contents.effectNow()) {
                giveEffect(shell);
            }
            if (contents.gone() && after < AFTER) {
                after++;
            }
        }
    }

    private void moveJumper(Shell shell) {
        jumper.step(Motion.PLAYER_GRAVITY);
        // A short hop: the jump button let go at once, so Sonic_JumpHeight caps the rise at -$400.
        if (!bounced && !fullJump && jumper.yVel < -SHORT_HOP) {
            jumper.yVel = -SHORT_HOP;
        }
        float top = monitorCentre(broken) - 16 - EventHero.rollRadius();
        if (!bounced && jumper.yVel > 0 && jumper.y() >= top) {
            // Touch_Monitor .okaytodestroy: the player bounces off and the monitor breaks. The mod
            // stops the hero's run here so the bounce comes down by the monitor it broke.
            jumper.place(jumper.x(), top);
            jumper.yVel = -jumper.yVel;
            jumper.xVel = 0;
            bounced = true;
            anims[broken].set(ANIM_BREAK);
            contents = new MonitorContents();
            return;
        }
        float feet = floor(Math.round(jumper.x())) - EventHero.standRadius(hero);
        if (bounced && jumper.yVel > 0 && jumper.y() >= feet) {
            jumper.place(jumper.x(), feet);
            landed = true;
        }
    }

    /** Monitor_Give_Rings / Monitor_Give_1up / Monitor_Give_Eggman, as far as a picture shows them. */
    private void giveEffect(Shell shell) {
        switch (broken) {
            case 0 -> shell.sfx(Sounds.SFX_RING);
            case 1 -> {
                if (shell.profile.get(SettingsScreen.NO_MUSIC) == 0) {
                    // Played as the game plays it: the driver resumes the room's song afterwards.
                    shell.ctx.audio().playMusic(Sounds.MUSIC_EXTRA_LIFE);
                }
            }
            default -> shell.sfx(Sounds.SFX_HURT);
        }
    }

    private int monitorCentre(int monitor) {
        return floor(col(monitor)) - 16;
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        drawLevel(shell, c, x, y, w, h);
        if (anims[0] == null) {
            String[] faces = {"4", "1UP", "static"};
            for (int i = 0; i < 3; i++) {
                EventArt.monitor(shell, c, x + col(i), y + floor(col(i)), faces[i], t);
            }
            return;
        }
        for (int i = 0; i < 3; i++) {
            drawMonitor(shell, c, x + col(i), y + monitorCentre(i), anims[i].frame());
        }
        drawHero(shell, c, x, y, t);
        if (contents != null) {
            int mx = x + col(broken);
            int my = y + monitorCentre(broken);
            int boom = contents.explosionFrame();
            if (boom >= 0) {
                c.draw(shell.art.romFrame("explosion", boom), mx, my, SceneDraw.plain());
            }
            if (!contents.gone()) {
                drawIcon(shell, c, mx, my + contents.iconOffset());
            }
        }
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, long t) {
        if (jumper == null) {
            Poses.hero(shell, c, hero, Poses.WAIT, t, x + HERO_COL, y + floor(HERO_COL), SceneDraw.plain());
        } else if (!landed) {
            EventHero.at(shell, c, hero, Poses.ROLL, t, x + jumper.x(), y + jumper.y(), SceneDraw.plain());
        } else {
            // Robotnik's monitor hurts whoever breaks it (Monitor_Give_Eggman): the hero flinches
            // while its face shows.
            boolean hurt = broken == 2 && contents != null && contents.risen() && !contents.gone();
            int col = Math.round(jumper.x());
            Poses.hero(shell, c, hero, hurt ? Poses.HURT : Poses.WAIT, t, x + col, y + floor(col), SceneDraw.plain());
        }
    }

    /** A monitor's mapping frame at its centre; the 1-Up frame lays the hero's life icon on the box. */
    private void drawMonitor(Shell shell, SceneCanvas c, float x, float y, int frame) {
        if (frame == FRAME_ONE_UP) {
            c.draw(shell.art.romFrame("monitor", 0), x, y, SceneDraw.plain());
            c.draw(shell.art.romFrame("monitor_life_" + hero, FRAME_ONE_UP), x, y, SceneDraw.plain());
            return;
        }
        c.draw(shell.art.romFrame("monitor", Math.min(frame, FRAME_BROKEN)), x, y, SceneDraw.plain());
    }

    /**
     * The rising icon: Map_Monitor frame anim+1, its first piece only (loc_1D7FC points the
     * mappings past the box). For the static monitor the mod shows Robotnik's face once it has
     * risen, flickering with the static as Ani_Monitor_Eggman2 alternates them.
     */
    private void drawIcon(Shell shell, SceneCanvas c, float x, float y) {
        if (broken == 1) {
            c.draw(shell.art.romFrame("monitor_life_" + hero, FRAME_ONE_UP), x, y, SceneDraw.plain());
            return;
        }
        int frame = broken == 0 ? ANIM_RINGS + 1 : ANIM_STATIC + 1;
        if (broken == 2 && contents.risen() && ticks % 6 >= 2) {
            frame = FRAME_ROBOTNIK;
        }
        if (icons[frame] == null) {
            SceneSprite box = shell.art.romFrame("monitor", frame);
            if (box == null) {
                return;
            }
            // The icon piece: 16x16 at (-8, -13) from the object's centre ($F3, $F8 in Map_Monitor).
            icons[frame] = box.image().crop(box.originX() - 8, box.originY() - 13, 16, 16);
        }
        c.draw(icons[frame], x - 8, y - 13);
    }
}
