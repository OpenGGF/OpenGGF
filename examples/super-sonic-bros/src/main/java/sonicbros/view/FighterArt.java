package sonicbros.view;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import sonicbros.content.Roster;

/**
 * Draws a fighter's pose. The heroes and Mecha Sonic are single ROM frames; Robotnik is his
 * Egg Mobile with his head behind the dome (the Hydrocity boss arrangement: head at (0,-$10), Egg
 * Mobile at (0,$C)), and Egg Robo is his body over his animated legs with his gun arm beside
 * him (Obj_EggRobo's children: arm at (-$1C,-4), legs at (-$C,$1C)). Tails gets his separately
 * drawn tails object, animated the way {@code Tails_Tail}'s selection table picks it.
 */
public final class FighterArt {
    private final Art art;
    /** Set while drawing a fighter in Super form: Sonic switches to his Super sprites. */
    public boolean superForm;

    public FighterArt(Art art) {
        this.art = art;
    }

    /** Whether the fighter's sprites face right before mirroring. */
    static boolean facesRight(String id, int frame) {
        return switch (id) {
            case "rhinobot" -> frame == 3; // Map_Rhinobot's opposite-facing turn pose.
            case "mecha", "eggrobo", "robotnik" -> false;
            default -> true;
        };
    }

    /** The sprite the menus and portraits use for a fighter. */
    static SceneSprite portraitSprite(Art art, String id, int costume) {
        return switch (id) {
            case "sonic" -> art.costumed("sonic", 0xBA, id, costume);
            case "tails" -> art.costumed("tails", 0xAD, id, costume);
            case "knuckles" -> art.costumed("knuckles", 0x56, id, costume);
            case "mecha" -> art.costumed("mecha", 0, id, costume);
            case "eggrobo" -> art.costumed("eggrobo", 1, id, costume);
            case "rhinobot" -> art.costumed("rhinobot", 0, id, costume);
            default -> art.costumed("ship", 0, id, costume);
        };
    }

    /** Horizontal nudge for the portrait crop so faces sit in the middle. */
    static int portraitShift(String id) {
        return switch (id) {
            case "rhinobot" -> -6;
            case "sonic", "knuckles" -> 2;
            default -> 0;
        };
    }

    /**
     * Draws one pose.
     *
     * @param x       screen x of the fighter's centre
     * @param feet    screen y of the fighter's feet
     * @param center  screen y of the body centre (used for airborne single-frame poses)
     * @param onFeet  anchor the sprite's bottom to the feet rather than its origin to the centre
     * @param facing  1 right, -1 left
     * @param flash   flash colour (alpha 0 for none)
     */
    public void draw(SceneCanvas c, String id, int pose, double x, double feet, double center, boolean onFeet,
            int facing, double scale, int costume, int flash, float alpha, long tick) {
        boolean flipPose = (pose & Roster.FLIP) != 0;
        int p = pose & 0xFFFF;
        boolean right = facing > 0;
        boolean mirror = facesRight(id, p) != right;
        if (flipPose) mirror = !mirror;
        SceneDraw style = SceneDraw.plain().withScale((float) scale).withFlipX(mirror).withFlash(flash).withAlpha(alpha);
        int dir = right ? 1 : -1;
        switch (id) {
            case "robotnik" -> drawRobotnik(c, p, x, feet, dir, scale, costume, style, tick);
            case "eggrobo" -> drawEggRobo(c, p, x, feet, scale, costume, style, tick);
            default -> {
                String key = id;
                if (id.equals("tails")) drawTails(c, p, x, feet, center, onFeet, scale, costume, style, tick);
                SceneSprite s = art.costumed(key, p, id, costume);
                if (superForm && id.equals("sonic")) {
                    SceneSprite gold = art.frame("super_sonic", p);
                    // Super Sonic's set has no ball or a few special frames; keep Sonic's there.
                    if (gold.width() > 1) {
                        s = gold;
                        style = style.withFlash(0);
                    }
                }
                double oy = onFeet ? feet - (s.height() - s.originY()) * scale : center;
                if (id.equals("rhinobot")) oy = feet - (s.height() - s.originY()) * scale;
                c.draw(s, (float) x, (float) oy, style);
            }
        }
    }

    private void drawTails(SceneCanvas c, int p, double x, double feet, double center, boolean onFeet, double scale,
            int costume, SceneDraw style, long tick) {
        int frame = -1;
        if (p == 0xAD || p == 0x99 || p == 0xA4 || p == 0x9A || p == 0x9B || (p >= 0xB0 && p <= 0xB3) || p == 0xA7 || p == 0xA8) {
            frame = 0x22 + (int) ((tick / 8) % 5);
        } else if (p >= 0x96 && p <= 0x98 || p == 0xC3 || p == 0xC4) {
            frame = 5 + (int) ((tick / 2) % 4);
        } else if (p >= 0xBD && p <= 0xC2 || p == 0xCD || p == 0xCE) {
            frame = 0x27 + (int) ((tick / 2) % 2);
        } else if (p == 0x8E || p == 0x8F) {
            frame = 0x1A + (int) ((tick / 3) % 4);
        }
        if (frame < 0) return;
        SceneSprite body = art.costumed("tails", p, "tails", costume);
        double oy = onFeet ? feet - (body.height() - body.originY()) * scale : center;
        SceneSprite tail = art.costumed("tails_tails", frame, "tails", costume);
        c.draw(tail, (float) x, (float) oy, style);
    }

    private void drawRobotnik(SceneCanvas c, int p, double x, double feet, int dir, double scale, int costume,
            SceneDraw style, long tick) {
        SceneSprite mobile = art.costumed("ship", 5, "robotnik", costume);
        double bob = Math.sin(tick * 0.09) * 1.5;
        double mobileY = feet - (mobile.height() - mobile.originY()) * scale - 3 * scale + bob * scale;
        int head = switch (p) {
            case Roster.EGG_LAUGH -> (tick / 6) % 2 == 0 ? 3 : 0;
            case Roster.EGG_HURT, Roster.EGG_DIZZY -> 2;
            default -> (tick % 120) < 6 ? 1 : 0;
        };
        SceneSprite headSprite = art.costumed("ship", head, "robotnik", costume);
        c.draw(headSprite, (float) x, (float) (mobileY - 0x1C * scale), style);
        if (p == Roster.EGG_THRUST) {
            SceneSprite flame = art.prop("bigflame" + (tick / 2) % 2);
            c.draw(flame, (float) x, (float) (mobileY + 14 * scale), SceneDraw.plain().withScale((float) scale)
                    .withAlpha(style.tint() >>> 24 == 255 ? 1f : 0.6f));
        }
        c.draw(mobile, (float) x, (float) mobileY, style);
    }

    private void drawEggRobo(SceneCanvas c, int p, double x, double feet, double scale, int costume,
            SceneDraw style, long tick) {
        int legs = 4;
        if (p == Roster.ROBO_WALK) legs = 4 + (int) ((tick / 5) % 3);
        SceneSprite legSprite = art.costumed("eggrobo", legs, "eggrobo", costume);
        double legY = feet - (legSprite.height() - legSprite.originY()) * scale;
        double bodyY = legY - 0x1C * scale;
        // Mirror the whole assembly around its origin, including pose/spin overrides.
        // Obj_EggRobo's children inherit parent render_flags, not logical fighter facing.
        if (p == Roster.ROBO_SPIN && (tick / 3) % 2 == 0) {
            style = style.withFlipX(!style.flipX());
        }
        double m = style.flipX() ? -1 : 1;
        double legX = x + (-0xC) * m * scale;
        double gunDx = -0x1C;
        double bodyDx = 0;
        double legDx = 0;
        switch (p) {
            case Roster.ROBO_AIM -> gunDx = -0x26;
            case Roster.ROBO_PUNCH -> { gunDx = -0x2C; bodyDx = -3; }
            case Roster.ROBO_KICK -> legDx = -10;
            case Roster.ROBO_HURT -> bodyDx = 3;
            default -> { }
        }
        int bodyFrame = p == Roster.ROBO_THRUST ? 3 : 1;
        if (p == Roster.ROBO_THRUST && tick % 2 == 0) bodyFrame = 1;
        SceneSprite gun = art.costumed("eggrobo", 2, "eggrobo", costume);
        SceneSprite body = art.costumed("eggrobo", bodyFrame, "eggrobo", costume);
        c.draw(legSprite, (float) (legX + legDx * m * scale), (float) legY, style);
        c.draw(body, (float) (x + bodyDx * m * scale), (float) bodyY, style);
        c.draw(gun, (float) (x + gunDx * m * scale), (float) (bodyY - 4 * scale), style);
    }
}
