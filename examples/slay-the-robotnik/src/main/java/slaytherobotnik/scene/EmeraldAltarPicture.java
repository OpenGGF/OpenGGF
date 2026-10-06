package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import slaytherobotnik.ui.Colors;

/**
 * The Emerald Altar: the green Chaos Emerald from Angel Island's intro (Map_AIZIntroEmeralds
 * frame 0, as the Emerald Idol's own picture shows it) on a stone pedestal in the act's level,
 * the hero beside it.
 * "take": the emerald lifts into the hero's hands, the pedestal clicks down and one of Angel
 * Island's breakable rocks (Obj_AIZLRZEMZRock, Map_AIZRock) rumbles in. "outrun": the hero
 * flees and the rock rolls after him; "smash:N": the hero rolls into it and it bursts into the
 * ROM's debris, knocking him back; "hide:N": he ducks into a crack and it scrapes past.
 */
final class EmeraldAltarPicture extends EventPicture {
    /** Window columns: the hero's spot, the altar, and where the rock waits. */
    private static final int HERO_X = 34;
    private static final int ALTAR_X = 84;
    private static final int ROCK_X = 112;
    private static final int CRACK_X = 14;
    /** Map_AIZRock frame 1, the middle-sized rock: AIZLRZEMZRock_SizeData gives it height_pixels $17. */
    private static final int ROCK_FRAME = 1;
    private static final int ROCK_HALF_HEIGHT = 0x17;
    /** AIZLRZEMZRock_DebrisMain: debris frames 3-6, a step every 3 frames, $18 gravity. */
    private static final int DEBRIS_GRAVITY = 0x18;
    /** The rolling attack that breaks the rock (AIZLRZEMZRock_TestP1PushSpeed: $480). */
    private static final int ROLL_SPEED = 0x480;

    /** How long each part plays: the ROM has no such scene, so these timings are the mod's own. */
    private static final int TAKE_FRAMES = 96;
    private static final int OUTRUN_FRAMES = 116;
    private static final int HIDE_FRAMES = 104;

    private String phase = "";
    private int age;
    private int amount;
    /** The emerald has been taken and the rock is in the room. */
    private boolean taken;
    private boolean rockGone;
    private float heroX = HERO_X;
    private float rockX = WIDTH + 40;
    private int hitAt = -1;
    private int landAt = -1;
    private int invulnerable;
    private EventActors.Fling knock;
    private EventActors.Fling[] debris;
    private int[] debrisFrames;
    private int rockRolled;
    /** Where the rock broke, for the floor its debris starts from. */
    private int brokeAt = ROCK_X;

    EmeraldAltarPicture(Shell shell) {
    }

    @Override
    void show(Shell shell, String detail) {
        int colon = detail.indexOf(':');
        phase = colon < 0 ? detail : detail.substring(0, colon);
        amount = colon < 0 ? 0 : Integer.parseInt(detail.substring(colon + 1));
        age = 0;
        hitAt = -1;
        landAt = -1;
        if (phase.equals("smash") && !EventActors.heroId(shell).equals("knuckles")) {
            shell.sfx(Sounds.SFX_ROLL);
        }
    }

    @Override
    boolean busy() {
        return switch (phase) {
            case "take" -> age < TAKE_FRAMES;
            case "outrun" -> age < OUTRUN_FRAMES;
            case "smash" -> landAt < 0 || age < landAt + 30;
            case "hide" -> age < HIDE_FRAMES;
            default -> false;
        };
    }

    @Override
    void tick(Shell shell) {
        age++;
        if (invulnerable > 0) {
            invulnerable--;
        }
        stepDebris();
        switch (phase) {
            case "take" -> take(shell);
            case "outrun" -> outrun(shell);
            case "smash" -> smash(shell);
            case "hide" -> hide(shell);
            default -> {
            }
        }
    }

    private void take(Shell shell) {
        if (age == 44) {
            shell.sfx(Sounds.SFX_SWITCH);           // the pedestal clicks down
        }
        if (age == 56 || age == 76) {
            shell.sfx(Sounds.SFX_RUMBLE);
        }
        if (age == 40) {
            taken = true;
        }
        if (age >= 60) {
            float k = Math.min(1f, (age - 60) / 32f);
            float eased = 1 - (1 - k) * (1 - k);
            float from = WIDTH + 30;
            float before = rockX;
            rockX = from + (ROCK_X - from) * eased;
            rockRolled += Math.round(Math.abs(before - rockX));
        }
    }

    private void outrun(Shell shell) {
        if (age < 40) {
            heroX -= 3;
        } else if (age >= 80 && heroX < HERO_X) {
            heroX = Math.min(HERO_X, Math.max(heroX, -16) + 1);
        } else if (age == 79) {
            heroX = -16;
        }
        if (age >= 6 && !rockGone) {
            rockX -= 2.75f;
            rockRolled += 3;
            if (rockX < -40) {
                rockGone = true;
            }
        }
        if (age == 6 || age == 28 || age == 50) {
            shell.sfx(Sounds.SFX_RUMBLE);
        }
        if (age == 66) {
            shell.sfx(Sounds.SFX_COLLAPSE);         // it smashes into something out of sight
        }
    }

    private void smash(Shell shell) {
        if (hitAt < 0) {
            // The rock keeps coming as the hero rolls at it at the speed that breaks it.
            heroX += ROLL_SPEED / 256f;
            rockX -= 2.75f;
            rockRolled += 3;
            if (heroX + 12 >= rockX - 22) {
                hitAt = age;
                breakRock();
                shell.sfx(Sounds.SFX_COLLAPSE);     // AIZLRZEMZRock_PlayCollapseSfx
                shell.sfx(Sounds.SFX_HURT);
                knock = new EventActors.Fling(heroX, 0, EventActors.HURT_X_VEL, EventActors.HURT_Y_VEL,
                        EventActors.HURT_GRAVITY);
                invulnerable = EventActors.INVULNERABLE;
            }
        } else if (knock != null) {
            knock.step();
            if (knock.px() < 10) {
                knock.x = 10 * 256;                 // Player_LevelBound: the window's edge stops him
            }
            heroX = knock.px();
            if (knock.yVel > 0 && knock.py() >= 0) {
                knock = null;                       // landed: the hurt routine ends
                landAt = age;
            }
        }
    }

    private void hide(Shell shell) {
        if (age < 14) {
            heroX += (CRACK_X - HERO_X) / 14f;
        } else {
            heroX = CRACK_X;
        }
        if (age >= 18 && !rockGone) {
            rockX -= 3;
            rockRolled += 3;
            if (hitAt < 0 && rockX - 20 < CRACK_X + 6) {
                hitAt = age;
                shell.sfx(Sounds.SFX_HURT);
                invulnerable = EventActors.INVULNERABLE;
            }
            if (rockX < -40) {
                rockGone = true;
            }
        }
        if (age == 18 || age == 40) {
            shell.sfx(Sounds.SFX_RUMBLE);
        }
    }

    /**
     * AIZLRZEMZRock_BreakFromPush with the player to the rock's left: the pieces of
     * AIZLRZEMZRock_DebrisOffsets1 fly with AIZLRZEMZRock_DebrisVelocitiesRight0, frames
     * counting 3, 4, 5, 6 then round again.
     */
    private void breakRock() {
        int[][] offsets = {{-4, -0xC}, {0xB, -0xC}, {-4, -4}, {-0xC, 0xC}, {0xC, 0xC}};
        int[][] velocities = {{0x2C0, -0x280}, {0x300, -0x300}, {0x280, -0x200}, {0x2C0, -0x280},
                {0x240, -0x180}};
        brokeAt = Math.round(rockX);
        debris = new EventActors.Fling[offsets.length];
        debrisFrames = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            debris[i] = new EventActors.Fling(rockX + offsets[i][0], -ROCK_HALF_HEIGHT + offsets[i][1],
                    velocities[i][0], velocities[i][1], DEBRIS_GRAVITY);
            debrisFrames[i] = 3 + i % 4;
        }
        rockGone = true;
    }

    private void stepDebris() {
        if (debris == null) {
            return;
        }
        for (int i = 0; i < debris.length; i++) {
            debris[i].step();
            if ((age - hitAt) % 3 == 0) {
                debrisFrames[i] = debrisFrames[i] + 1 >= 7 ? 3 : debrisFrames[i] + 1;
            }
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        int ground = y + h - 22;
        LevelStages.Placement placement = EventArt.level(shell, c, x, y, w, h, ground);
        boolean shaking = phase.equals("take") && age >= 56 && age < 96 || phase.equals("outrun") && age >= 66
                && age < 78;
        int shake = shaking && (t / 2) % 2 == 0 ? 1 : 0;

        if (phase.equals("hide")) {
            crack(c, x + CRACK_X, EventActors.floor(placement, x, y, x + CRACK_X, ground));
        }
        int altarFloor = EventActors.floor(placement, x, y, x + ALTAR_X, ground) + shake;
        int sink = phase.equals("take") ? Math.max(0, Math.min(6, age - 44)) : taken ? 6 : 0;
        int top = pedestal(c, x + ALTAR_X, altarFloor, sink);
        int heroFeet = EventActors.floor(placement, x, y, x + Math.round(heroX), ground);
        drawEmerald(shell, c, x, top, heroFeet - 44, t);

        drawHero(shell, c, x, y, ground, placement, t);
        drawRock(shell, c, x, y, ground, placement, shake);
        if (debris != null) {
            int base = EventActors.floor(placement, x, y, x + brokeAt, ground);
            for (int i = 0; i < debris.length; i++) {
                SceneSprite piece = shell.art.romFrame("aiz_rock", debrisFrames[i]);
                if (piece != null) {
                    c.draw(piece, x + debris[i].px(), base + debris[i].py(), SceneDraw.plain());
                }
            }
        }
        if (hitAt >= 0 && amount > 0) {
            int heroFloor = EventActors.floor(placement, x, y, x + Math.round(heroX), ground);
            EventActors.number(shell, c, "-" + amount, phase.equals("hide") ? "MAX HP" : null, Colors.TEXT_BAD,
                    x + Math.round(heroX), heroFloor - 56, x, age - hitAt, 60);
        }
    }

    /** The emerald on its pedestal top {@code top}, or on its way to the hero's hands at row {@code hands}. */
    private void drawEmerald(Shell shell, SceneCanvas c, int x, int top, int hands, long t) {
        float ex = x + ALTAR_X;
        float ey = top - 12 + EventArt.bob(t, 2);
        if (phase.equals("take")) {
            if (age >= 40) {
                EventActors.sparkle(shell, c, x + heroX, hands, age - 40);
                EventActors.sparkle(shell, c, x + heroX - 8, hands + 10, age - 44);
                EventActors.sparkle(shell, c, x + heroX + 8, hands + 6, age - 48);
                return;
            }
            float lift = Math.min(1f, age / 20f);
            ey = top - 12 - 10 * lift;
            if (age >= 20) {
                float k = (age - 20) / 20f;
                ex = ex + (x + heroX - ex) * k;
                ey = ey + (hands - ey) * k - (float) Math.sin(k * Math.PI) * 12;
            }
        } else if (taken) {
            return;
        }
        EventArt.emerald(shell, c, Math.round(ex), Math.round(ey), 0, t);
        // A glint now and then, as a ring sparkles.
        EventActors.sparkle(shell, c, ex + 6, ey - 6, (int) (t % 90) - 60);
    }

    private void drawHero(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            long t) {
        int hx = Math.round(heroX);
        if (hx < -20 || !EventActors.blinkVisible(invulnerable)) {
            return;
        }
        float feet = EventActors.floor(placement, x, y, x + hx, ground);
        int anim = Poses.WAIT;
        long ticks = t;
        boolean left = false;
        switch (phase) {
            case "take" -> anim = age >= 20 && age < 60 ? Poses.LOOK_UP : Poses.WAIT;
            case "outrun" -> {
                if (age < 40) {
                    anim = Poses.RUN;
                    left = true;
                } else if (heroX < HERO_X) {
                    anim = Poses.WALK;
                    ticks = t / 2;                   // a limp, not a stroll
                }
            }
            case "smash" -> {
                if (hitAt < 0) {
                    anim = EventActors.heroId(shell).equals("knuckles") ? Poses.RUN : Poses.ROLL;
                } else if (knock != null) {
                    anim = Poses.HURT;
                    feet += knock.py();
                }
            }
            case "hide" -> {
                if (age < 14) {
                    anim = Poses.WALK;
                    left = true;
                } else if (age < 84) {
                    anim = Poses.DUCK;
                }
            }
            default -> {
            }
        }
        EventActors.hero(shell, c, anim, ticks, x + hx, feet, left, SceneDraw.plain());
    }

    private void drawRock(Shell shell, SceneCanvas c, int x, int y, int ground, LevelStages.Placement placement,
            int shake) {
        if (!taken || rockGone || rockX > WIDTH + 28) {
            return;
        }
        SceneSprite rock = shell.art.romFrame("aiz_rock", ROCK_FRAME);
        int rx = Math.round(rockX);
        float floor = EventActors.floor(placement, x, y, x + rx, ground);
        // Rolling: the rock has no turning frames, so it tumbles by mirroring every 8 pixels and
        // bumps along (a mod flourish); waiting, it trembles with the rumble.
        int step = (rockRolled / 8) % 4;
        boolean moving = phase.equals("take") && age >= 60 && age < 92 || phase.equals("outrun")
                || phase.equals("hide") && age >= 18 || phase.equals("smash");
        float bump = moving ? Math.abs((float) Math.sin(rockRolled / 8f * Math.PI)) * -2 : 0;
        float jitter = moving ? 0 : (shellTicksOdd(shell) ? 1 : 0);
        if (rock != null) {
            c.draw(rock, x + rx + jitter, floor - ROCK_HALF_HEIGHT + bump + shake,
                    SceneDraw.plain().withFlipX(step == 1 || step == 2).withFlipY(step >= 2));
        } else {
            c.fill(x + rx - 22, Math.round(floor) - 44, 44, 44, EventArt.STONE_DARK);
        }
    }

    private static boolean shellTicksOdd(Shell shell) {
        return (shell.ticks / 3) % 2 == 0;
    }

    /** A tidy stone pedestal; returns the row its top surface is on. */
    private static int pedestal(SceneCanvas c, int cx, int floor, int sink) {
        int height = 26 - sink;
        c.fill(cx - 15, floor - 5, 30, 5, EventArt.STONE_DARK);
        c.fill(cx - 10, floor - height, 20, height - 5, EventArt.STONE);
        c.fill(cx - 8, floor - height + 2, 2, height - 9, 0xFFA89888);
        c.fill(cx - 13, floor - height - 4, 26, 5, EventArt.STONE_DARK);
        c.fill(cx - 12, floor - height - 4, 24, 1, 0xFFA89888);
        return floor - height - 4;
    }

    /** The gap in the wall the hero squeezes into: a dark, jagged crack behind him. */
    private static void crack(SceneCanvas c, int cx, int floor) {
        int[] widths = {6, 9, 12, 14, 15, 16, 16, 15, 16, 14};
        for (int i = 0; i < widths.length; i++) {
            int w = widths[i];
            c.fill(cx - w / 2 + (i % 3) - 1, floor - 40 + i * 4, w, 4, 0xD0100C10);
        }
    }
}
