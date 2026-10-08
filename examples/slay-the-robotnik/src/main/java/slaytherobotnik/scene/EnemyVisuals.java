package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.ArrayList;
import java.util.List;
import slaytherobotnik.core.Enemy;
import slaytherobotnik.ui.Colors;

/**
 * How each enemy looks. Badniks and bosses are assembled from their ROM mapping frames the
 * way the game builds them from child objects: each offset, frame and draw order comes from
 * the object's {@code ChildObjDat}/{@code ObjDat} tables in the S3K disassembly (cited per
 * enemy), with parts listed back to front (the ROM's sprite priority, then object slot
 * order). Elites are scaled up. Enemies without art get a labelled placeholder.
 *
 * <p>Adding an enemy's look: give its {@code Enemy} an art key in {@code content/<Zone>.java},
 * add a {@code case} for that key to {@code compose} listing its parts, add any sprite it needs
 * to {@code art/RomSprites}, and only if it should not stand at normal size on the ground, give
 * it a {@code scale} or a {@code lift}.
 */
final class EnemyVisuals {
    /** Where an enemy was drawn this frame (for hotspots, intents and effects). */
    record Box(int x, int y, int w, int h) {
        int centerX() {
            return x + w / 2;
        }
    }

    /** One sprite of a composition, offset from the enemy's centre (art faces left). */
    private record Part(SceneSprite sprite, float dx, float dy, boolean flipX) {
    }

    private EnemyVisuals() {
    }

    /**
     * Draws {@code enemy} standing on {@code groundY} centred on {@code x}. {@code flash} is
     * 0..1 white flash; {@code alpha} fades it out when dying.
     */
    static Box draw(Shell shell, SceneCanvas c, Enemy enemy, int x, int groundY, long ticks, float flash, float alpha) {
        List<Part> parts = compose(shell, enemy, enemy.art(), ticks);
        if (parts.isEmpty()) {
            return placeholder(shell, c, enemy, x, groundY, flash, alpha);
        }
        float scale = scale(enemy);
        float[] b = bounds(parts);
        // Place the composition so its base sits on the ground (or hovers above it) centred on x.
        float originX = x - (b[0] + b[2]) / 2f * scale;
        float originY = groundY - b[3] * scale - lift(enemy.art(), ticks);
        int flashColor = flash > 0 ? Colors.alpha(Colors.WHITE, Math.min(1f, flash)) : 0;
        if (enemy.halfDead() && !enemy.art().startsWith("ssz:mecha")) {
            alpha *= 0.6f; // reviving: faded until it rises again
        }
        for (Part p : parts) {
            SceneDraw style = SceneDraw.plain().withScale(scale).withFlipX(p.flipX()).withFlash(flashColor)
                    .withAlpha(alpha);
            c.draw(p.sprite(), originX + p.dx() * scale, originY + p.dy() * scale, style);
        }
        return new Box(Math.round(originX + b[0] * scale), Math.round(originY + b[1] * scale),
                Math.round((b[2] - b[0]) * scale), Math.round((b[3] - b[1]) * scale));
    }

    /** {minX, minY, maxX, maxY} of a composition relative to its origin. */
    private static float[] bounds(List<Part> parts) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (Part p : parts) {
            SceneSprite s = p.sprite();
            float left = p.flipX() ? p.dx() - (s.width() - s.originX()) : p.dx() - s.originX();
            float top = p.dy() - s.originY();
            minX = Math.min(minX, left);
            minY = Math.min(minY, top);
            maxX = Math.max(maxX, left + s.width());
            maxY = Math.max(maxY, top + s.height());
        }
        return new float[] {minX, minY, maxX, maxY};
    }

    /** Elites and the final boss are drawn at double size. */
    private static float scale(Enemy enemy) {
        return switch (enemy.id()) {
            case "aiz:mega_rhinobot", "aiz:caterkiller_sr", "hcz:frenzy_chopper", "hcz:buggernaut_queen",
                    "lbz:ribot_commander", "lbz:star_orbinaut", "lbz:snale_mother" -> 2f;
            case "ssz:mecha_sonic" -> 1.5f;
            default -> 1f;
        };
    }

    /** Pixels above the ground for flyers and swimmers (negative sinks a tall boss into the floor). */
    private static float lift(String art, long ticks) {
        float bob = (float) Math.sin(ticks * 0.07) * 2;
        return switch (art) {
            case "hcz:jawz", "hcz:mega_chopper" -> 10 + bob;
            case "hcz:buggernaut", "hcz:baby_buggernaut", "lbz:flybot" -> 26 + bob;
            case "hcz:pointdexter" -> 8;
            case "lbz:corkey" -> 40;
            case "ssz:egg_robo" -> 8;
            case "ssz:super_mecha_sonic" -> 10 + bob;
            case "lbz:beam_rocket" -> -24;
            default -> 0;
        };
    }

    private static SceneSprite frame(Shell shell, String key, int frame) {
        return shell.art.romFrame(key, frame);
    }

    private static List<Part> compose(Shell shell, Enemy enemy, String art, long ticks) {
        List<Part> parts = new ArrayList<>();
        String id = enemy == null ? art : enemy.id();
        switch (art) {
            // ------------------------------------------------------------ Angel Island
            case "aiz:rhinobot", "aiz:mega_rhinobot" -> {
                // Frames 0 (slow) and 1 (run): the Rhinobot revs in place.
                add(parts, frame(shell, "rhinobot", (ticks / 10) % 2 == 0 ? 0 : 1), 0, 0, false);
            }
            case "aiz:bloominator", "aiz:grove_bloominator" -> {
                // Idle frame 0; every few seconds it blooms through 1, 2, 3 (byte_86E42).
                long cycle = ticks % 150;
                int f = cycle < 110 ? 0 : cycle < 120 ? 1 : cycle < 130 ? 2 : 3;
                add(parts, frame(shell, "bloominator", f), 0, 0, false);
            }
            case "aiz:monkey_dude" -> {
                add(parts, frame(shell, "monkey_dude", (ticks / 8) % 2 == 0 ? 0 : 1), 0, 0, false);
                // Arm chain: root at (-14,-2) (MonkeyDudeArm_SetAnchor), three ball links, then the
                // hand holding a coconut (frame 6), swinging slowly.
                double swing = Math.sin(ticks * 0.05) * 0.4;
                float ax = -14;
                float ay = -2;
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI * 0.6 + swing * (i + 1) / 4.0;
                    ax += (float) Math.cos(angle) * 5;
                    ay += (float) Math.sin(angle) * 5;
                    add(parts, frame(shell, "monkey_dude", i == 3 ? 6 : 3), ax, ay, false);
                }
            }
            case "aiz:caterkiller_jr", "aiz:big_caterkiller", "aiz:caterkiller_sr" -> {
                // Head plus six trailing children (ChildObjDat_CaterKillerJrBodySegments): three tall
                // segments, one thin, and two coconut-ball tail links from the Monkey Dude art.
                int[] trail = {11, 23, 35, 47, 55, 63};
                for (int i = trail.length - 1; i >= 0; i--) {
                    float dy = (float) Math.sin(ticks * 0.08 - i * 0.7) * 3;
                    SceneSprite s = i < 3 ? frame(shell, "caterkiller_jr", 1)
                            : i == 3 ? frame(shell, "caterkiller_jr", 2) : frame(shell, "monkey_dude", 3);
                    add(parts, s, trail[i], dy, false);
                }
                add(parts, frame(shell, "caterkiller_jr", 0), 0, (float) Math.sin(ticks * 0.08) * 3, false);
            }
            case "aiz:fire_breath" -> {
                // Obj_AIZMiniboss: body (0), jets (1/2, line 0 in ROM), arm (6) and three barrels (3).
                add(parts, frame(shell, "aiz_miniboss", 3), 0, -0x20, false);
                add(parts, frame(shell, "aiz_miniboss", 3), 9, -0x1C, false);
                add(parts, frame(shell, "aiz_miniboss", 3), 0x12, -0x18, false);
                add(parts, frame(shell, "aiz_miniboss", 0), 0, 0, false);
                add(parts, frame(shell, "aiz_miniboss", 6), -0x24, 8, false);
                add(parts, frame(shell, "aiz_miniboss", (ticks / 2) % 2 == 0 ? 1 : 2), 0, 0x20, false);
            }
            case "aiz:flame_craft" -> {
                // Obj_AIZEndBoss: Robotnik's head and cockpit (ship frame 8) above the body (0) and two
                // arms (1 and $2A) with propellers (4..6).
                add(parts, frame(shell, "robotnik_ship", (ticks / 6) % 2 == 0 ? 0 : 1), 0, -0x14 - 0x1C, false);
                add(parts, frame(shell, "robotnik_ship", 8), 0, -0x14, false);
                add(parts, frame(shell, "aiz_end_boss", 0), 0, 0, false);
                add(parts, frame(shell, "aiz_end_boss", 1), 0x14, -4, false);
                add(parts, frame(shell, "aiz_end_boss", 0x2A), -0x14, -4, false);
                int prop = 4 + (int) ((ticks / 3) % 3);
                add(parts, frame(shell, "aiz_end_boss", prop), 0x14 - 0x1C, -4, false);
                add(parts, frame(shell, "aiz_end_boss", prop), -0x14 + 0x1C, -4, true);
            }

            // ------------------------------------------------------------ Hydrocity
            // Jawz: AniRaw_Jawz alternates its two tail frames.
            case "hcz:jawz" -> add(parts, frame(shell, "jawz", (int) ((ticks / 3) % 2)), 0, 0, false);
            case "hcz:blastoid" -> {
                // Sits on frame 0; AniRaw_BlastoidAttack opens the cannon (1) for each of three shots.
                long cycle = ticks % 150;
                boolean open = cycle >= 100 && cycle % 15 < 5;
                add(parts, frame(shell, "blastoid", open ? 1 : 0), 0, 0, false);
            }
            case "hcz:buggernaut" -> {
                // AniRaw_Buggernaut: 0,1,2 one frame each (a 3-frame wing buzz); babies are 3,4,5.
                boolean baby = id.equals("hcz:baby_buggernaut");
                add(parts, frame(shell, "buggernaut", (baby ? 3 : 0) + (int) ((ticks / 2) % 3)), 0, 0, false);
            }
            case "hcz:baby_buggernaut" -> add(parts, frame(shell, "buggernaut", 3 + (int) ((ticks / 2) % 3)), 0, 0,
                    false);
            case "hcz:turbo_spiker" -> {
                // Spike shell (frame 3) at (+4,0) behind the walker (0-2, 6 frames each):
                // TurboSpiker_SpikeChild dc.b 4,0; TurboSpiker_PatrolAnim.
                add(parts, frame(shell, "turbo_spiker", 3), 4, 0, false);
                add(parts, frame(shell, "turbo_spiker", (int) ((ticks / 6) % 3)), 0, 0, false);
            }
            case "hcz:mega_chopper" -> {
                if (id.equals("hcz:frenzy_chopper")) {
                    // MegaChopper_Carry: jaws 0/2 toggling every 4 frames while it eats.
                    add(parts, frame(shell, "mega_chopper", (ticks / 4) % 2 == 0 ? 0 : 2), 0, 0, false);
                } else {
                    add(parts, frame(shell, "mega_chopper", (int) ((ticks / 3) % 2)), 0, 0, false); // AniRaw_MegaChopper
                }
            }
            case "hcz:pointdexter" -> {
                // AniRaw_Poindexter: 0 for 128 frames, 1 for 5, 2 (spiky) for 64, 1 for 5.
                long cycle = ticks % 202;
                int f = cycle < 128 ? 0 : cycle < 133 ? 1 : cycle < 197 ? 2 : 1;
                float bob = (float) Math.sin(ticks * Math.PI * 2 / 128) * 2;
                add(parts, frame(shell, "pointdexter", f), 0, bob, false);
            }
            case "hcz:big_shaker" -> bigShaker(shell, parts, ticks);
            case "hcz:screw_mobile" -> screwMobile(shell, parts, ticks);
            case "hcz:depth_charge" -> add(parts, frame(shell, "hcz_end_boss", (ticks / 6) % 2 == 0 ? 6 : 7), 0, 0,
                    false);

            // ------------------------------------------------------------ Launch Base
            case "lbz:snale_blaster" -> {
                // ChildObjDat_8C28A: cannons (7) at (-8,0) and (-8,7) behind the body, hatch cover (5) at
                // (-8,4) in front. It crawls on frames 4/3 and opens fire with cover 10 (empty), cannons 8.
                long cycle = ticks % 240;
                boolean firing = cycle >= 190;
                int cannon = firing && cycle % 24 < 4 ? 8 : 7;
                add(parts, frame(shell, "snale_blaster", cannon), -8, 0, false);
                add(parts, frame(shell, "snale_blaster", cannon), -8, 7, false);
                add(parts, frame(shell, "snale_blaster", (ticks / 16) % 2 == 0 ? 4 : 3), 0, 0, false);
                add(parts, frame(shell, "snale_blaster", firing ? 10 : 5), -8, 4, false);
            }
            case "lbz:orbinaut" -> {
                // Four orbs (frame 1) at radius 16 (ChildObjDat_8C704), turning 8 units a frame, behind the body.
                for (int i = 0; i < 4; i++) {
                    double a = ((i << 6) + ticks * 4) * Math.PI * 2 / 256;
                    add(parts, frame(shell, "orbinaut", 1), (float) Math.sin(a) * 16, (float) Math.cos(a) * 16, false);
                }
                add(parts, frame(shell, "orbinaut", 0), 0, 0, false);
            }
            case "lbz:ribot" -> ribot(shell, parts, ticks, id.equals("lbz:ribot_commander"));
            case "lbz:corkey" -> {
                // Body (0) with the emitter (1/3 while charging) hanging at (0,$C): ChildObjDat_8C90E.
                add(parts, frame(shell, "corkey", (ticks / 8) % 2 == 0 ? 1 : 3), 0, 0xC, false);
                add(parts, frame(shell, "corkey", 0), 0, 0, false);
            }
            // Flybot767: byte_8CB2A flaps through frames 0-9, 5 frames each.
            case "lbz:flybot" -> add(parts, frame(shell, "flybot", (int) ((ticks / 5) % 10)), 0, 0, false);
            case "lbz:beam_rocket", "lbz:beam_rocket_core" -> beamRocket(shell, parts, ticks,
                    art.equals("lbz:beam_rocket_core"));
            case "lbz:big_arm" -> bigArm(shell, parts, ticks);

            // ------------------------------------------------------------ Sky Sanctuary
            case "ssz:egg_robo" -> {
                // ChildObjDat_919D0: gun arm (2) at (-$1C,-4) and legs (4-6) at (-$C,$1C), both behind
                // the body, which alternates 1/3 (backpack flame) every frame.
                double phase = ticks * Math.PI * 2 / 126;
                int legs = Math.cos(phase) > 0.2 ? 6 : Math.cos(phase) < -0.2 ? 4 : 5;
                float bob = (float) Math.sin(phase) * 4;
                add(parts, frame(shell, "egg_robo_ssz", 2), -0x1C, -4 + bob, false);
                add(parts, frame(shell, "egg_robo_ssz", legs), -0xC, 0x1C + bob, false);
                add(parts, frame(shell, "egg_robo_ssz", ticks % 2 == 0 ? 1 : 3), 0, bob, false);
            }
            case "ssz:mecha_sonic" -> {
                // One DPLC sprite. Standing frame 0; slumped (frame $F, byte_7D5E4) while it reaches
                // for the Master Emerald.
                boolean down = enemy != null && enemy.halfDead();
                add(parts, frame(shell, "mecha_sonic", down ? 0xF : 0), 0, 0, false);
            }
            case "ssz:super_mecha_sonic" -> {
                // Same frames; line 1 cycles Super1/2/3/2 every 6 frames (word_7DA60 .headr2). It
                // hovers with the chest cannon open (frame $14, byte_7D57C).
                String[] cycle = {"mecha_super1", "mecha_super2", "mecha_super3", "mecha_super2"};
                add(parts, frame(shell, cycle[(int) ((ticks / 6) % 4)], 0x14), 0, 0, false);
            }
            default -> {
            }
        }
        return parts;
    }

    /**
     * The HCZ miniboss: body (0) with its thruster ($15, line 0) and four rockets orbiting on
     * the twist path (sub_6AB1A, HCZMiniboss_RocketTwistLookup), each with an exhaust flame
     * (byte_6AC18/byte_6AC38, line 0). Rockets in the back half of the orbit go behind the body.
     */
    private static void bigShaker(Shell shell, List<Part> parts, long ticks) {
        int[] rocketFrames = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0x1A, 0x1A, 0x1A, 0xC, 0xD};
        int[] flameFrames = {0xE, 0xF, 0x10, 0x11, 0x12, 0x11, 0x10, 0xF, 0xE, 0x1A, 0x1A, 0x1A, 0xE, 0xE, 0x1A, 0x1A};
        int[] flameOffset = {3, 0, 6, 0xC, 0x12, 0xC, 8, 0, 3, 0, 0, 0, -6, -0xA, 0, 0};
        int[] flamePriority = {0x280, 0x200, 0x200, 0x200, 0x200, 0x180, 0x180, 0x180, 0x180, 0x180, 0x180, 0x280,
                0x280, 0x280, 0x280, 0x280};
        // Hover phases at speed 4 (HCZMiniboss_Rockets_InitialAngleData after the intro).
        int[] phaseY = {0xC0, 0x40, 0x80, 0x00};
        int[] phaseX = {0xC0, 0x40, 0x00, 0x80};
        boolean[] flipped = {false, false, true, true};
        boolean flameOn = ticks % 2 == 0;
        List<Part> back = new ArrayList<>();
        List<Part> mid = new ArrayList<>();
        List<Part> front = new ArrayList<>();
        // Slot order puts lower subtypes in front, so walk the rockets from the last.
        for (int i = 3; i >= 0; i--) {
            int py = (phaseY[i] + (int) ticks * 4) & 0xFF;
            int px = (phaseX[i] + (int) ticks * 4) & 0xFF;
            int n = py >> 4;
            float dx = twist(px);
            float dy = twist(py);
            Part rocket = part(frame(shell, "hcz_miniboss", rocketFrames[n]), dx, dy, flipped[i]);
            (n < 8 ? mid : back).add(rocket);
            if (flameOn) {
                int off = flameOffset[n];
                Part flame = part(frame(shell, "hcz_miniboss_l0", flameFrames[n]), dx + (flipped[i] ? -off : off),
                        dy + off, flipped[i]);
                (flamePriority[n] == 0x180 ? front : flamePriority[n] == 0x200 ? mid : back).add(0, flame);
            }
        }
        if (flameOn) {
            back.add(0, part(frame(shell, "hcz_miniboss_l0", 0x15), 0, 0x24, false));
        }
        back.add(part(frame(shell, "hcz_miniboss", 0), 0, 0, false));
        addAll(parts, back);
        addAll(parts, mid);
        addAll(parts, front);
    }

    /** HCZMiniboss_RocketTwistLookup: +24 at phase 0 easing to -24 at $80 and back. */
    private static float twist(int phase) {
        return (float) Math.round(24 * Math.cos(phase * Math.PI * 2 / 256));
    }

    /**
     * The HCZ end boss (HCZEndBoss_MainChildren, HCZEndBoss_ShipChild): bomb rack body (0) in
     * front, three depth charges (6), the lower housing (1) and screw propeller (2-5), the Egg
     * Mobile (ship frame 5, line 0) at (0,$C) and Robotnik's head behind it at (0,-$10). The
     * whole machine bobs 4 px (Swing_UpAndDown).
     */
    private static void screwMobile(Shell shell, List<Part> parts, long ticks) {
        float bob = (float) Math.sin(ticks * Math.PI * 2 / 46) * 4;
        add(parts, frame(shell, "robotnik_ship_hcz", (ticks / 6) % 2 == 0 ? 0 : 1), 0, -0x10 + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 6), 0x23, 0x12 + bob, false);
        add(parts, frame(shell, "robotnik_ship_hcz", 5), 0, 0xC + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 2 + (int) ((ticks / 2) % 4)), 0, 0x24 + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 1), 0, 0x1C + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 6), 0x1B, 0xA + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 6), 0x13, 0xA + bob, false);
        add(parts, frame(shell, "hcz_end_boss", 0), 0, bob, false);
    }

    /**
     * Ribot (ObjDat_Ribot): body 0,1,2,1 every 8 frames with two spiked balls (7) and their
     * chain links (6). Subtype 0 hangs the balls below at (+/-$C,$C); the commander uses the
     * subtype 2 layout, balls out to the sides at (+/-$18,0).
     */
    private static void ribot(Shell shell, List<Part> parts, long ticks, boolean wide) {
        int[] body = {0, 1, 2, 1};
        add(parts, frame(shell, "ribot", body[(int) ((ticks / 8) % 4)]), 0, 0, false);
        float swing = (float) Math.sin(ticks * 0.06) * 2;
        for (int side = -1; side <= 1; side += 2) {
            float bx = wide ? side * 0x18 : side * 0xC;
            float by = wide ? swing * side : 0xC + swing;
            // Links sit at 1/4, 1/2 and 3/4 of the way from the anchor to the ball (loc_8C502).
            float ax = wide ? side * 0xC : bx;
            float ay = wide ? 0 : 0;
            for (int k = 3; k >= 1; k--) {
                add(parts, frame(shell, "ribot", 6), ax + (bx - ax) * k / 4f, ay + (by - ay) * k / 4f, false);
            }
            add(parts, frame(shell, "ribot", 7), bx, by, false);
        }
    }

    /**
     * LBZ2's first boss (Obj_LBZFinalBoss1): the Egg Mobile dome (ship $C) with Robotnik's head
     * behind it, the top plate (2), column segments (0 and 1) with laser turrets (3-8) circling
     * them, and the spiked orb (C-E) orbiting behind. Drawn as after its first segment has gone,
     * so it fits the screen; after its second stage only the bottom segment and thrusters remain.
     */
    private static void beamRocket(Shell shell, List<Part> parts, long ticks, boolean core) {
        double a = ticks * Math.PI * 2 / 256;
        int[] orb = {0xC, 0xD, 0xE, 0xD, 0xC};
        add(parts, frame(shell, "lbz_final_boss1", orb[(int) ((ticks / 2) % 5)]), (float) Math.sin(a) * 32,
                -0x14 + (float) Math.cos(a) * 32, false);
        if (core) {
            int flame = 9 + (int) ((ticks / 3) % 3);
            add(parts, frame(shell, "lbz_final_boss1_l0", flame), -0x14, 0x3C, false);
            add(parts, frame(shell, "lbz_final_boss1_l0", flame), 0x14, 0x3C, true);
        }
        add(parts, frame(shell, "robotnik_ship_lbz", (ticks / 6) % 2 == 0 ? 0 : 1), 0, -0x1C, false);
        add(parts, frame(shell, "robotnik_ship_lbz", 0xC), 0, 0, false);
        if (core) {
            add(parts, frame(shell, "lbz_final_boss1", 1), 0, 0xC, false);
        } else {
            add(parts, frame(shell, "lbz_final_boss1", 1), 0, 0x34, false);
            add(parts, frame(shell, "lbz_final_boss1", 0), 0, 8, false);
        }
        add(parts, frame(shell, "lbz_final_boss1", 2), 0, -0x14, false);
        if (!core) {
            laserHead(shell, parts, ticks, 1);
            laserHead(shell, parts, ticks, 9);
        }
    }

    /** A laser turret walking round its column (byte_734AA frames, word_734B6 holds, byte_734CE x). */
    private static void laserHead(Shell shell, List<Part> parts, long ticks, int startStep) {
        int[] frames = {3, 4, 5, 6, 7, 8, 7, 6, 5, 4, 3, 0x2D};
        int[] holds = {9, 0x61, 9, 9, 9, 9, 9, 9, 9, 0x61, 9, 0x29};
        int[] xs = {0x27, 0x24, 0x24, 0x14, 0xC, 0, -0xC, -0x14, -0x24, -0x24, -0x27, 0};
        int period = 0;
        for (int h : holds) {
            period += h;
        }
        int start = 0;
        for (int i = 0; i < startStep; i++) {
            start += holds[i];
        }
        int t = (int) ((ticks + start) % period);
        int step = 0;
        while (t >= holds[step]) {
            t -= holds[step];
            step++;
        }
        add(parts, frame(shell, "lbz_final_boss1", frames[step]), xs[step], 0, step <= 5);
    }

    /**
     * Big Arm (Obj_LBZFinalBoss2, ChildObjDat_75122/75144) at rest: rear hazard ($C, line 0,
     * flickering) and inner piece (1) behind, the Egg Mobile's upper half (ship 8) with
     * Robotnik's head, the housing (0) over the cockpit, then the arm: joint (3), claw tip
     * (8-B), upper arm (2) and forearm (4-7) in front.
     */
    private static void bigArm(Shell shell, List<Part> parts, long ticks) {
        float bob = (float) Math.sin(ticks * 0.05) * 2;
        if (ticks % 2 == 0) {
            add(parts, frame(shell, "lbz_final_boss2_l0", 0xC), 0x38, -0x14 + bob, false);
        }
        add(parts, frame(shell, "lbz_final_boss2", 1), 0, -0x18 + bob, false);
        add(parts, frame(shell, "robotnik_ship_lbz", (ticks / 6) % 2 == 0 ? 0 : 1), 0, -0x1C + bob, false);
        add(parts, frame(shell, "robotnik_ship_lbz", 8), 0, bob, false);
        add(parts, frame(shell, "lbz_final_boss2", 0), 0xC, -0x14 + bob, false);
        int[] tip = {8, 9, 0xA, 9, 8, 0xB};
        int[] fore = {4, 5, 6, 5, 4, 7};
        int step = (int) ((ticks / 10) % 6);
        add(parts, frame(shell, "lbz_final_boss2", 3), 0x21, 0xB + bob, false);
        add(parts, frame(shell, "lbz_final_boss2", tip[step]), -0x36, 0x20 + bob, false);
        add(parts, frame(shell, "lbz_final_boss2", 2), 0x14, 0x22 + bob, false);
        add(parts, frame(shell, "lbz_final_boss2", fore[step]), -0x16, 0x20 + bob, false);
    }

    private static Part part(SceneSprite sprite, float dx, float dy, boolean flip) {
        return sprite != null && sprite.width() > 1 ? new Part(sprite, dx, dy, flip) : null;
    }

    private static void addAll(List<Part> parts, List<Part> more) {
        for (Part p : more) {
            if (p != null) {
                parts.add(p);
            }
        }
    }

    private static void add(List<Part> parts, SceneSprite sprite, float dx, float dy, boolean flip) {
        if (sprite != null && sprite.width() > 1) {
            parts.add(new Part(sprite, dx, dy, flip));
        }
    }

    private static Box placeholder(Shell shell, SceneCanvas c, Enemy enemy, int x, int groundY, float flash,
            float alpha) {
        boolean big = enemy.maxHp() >= 150;
        boolean elite = enemy.maxHp() >= 80;
        int w = big ? 72 : elite ? 52 : 36;
        int h = big ? 80 : elite ? 52 : 34;
        int hue = Math.abs(enemy.id().hashCode());
        int body = 0xFF000000 | ((0x60 + hue % 0x80) << 16) | ((0x30 + (hue >> 8) % 0x60) << 8) | (0x40 + (hue >> 16) % 0x80);
        int left = x - w / 2;
        int top = groundY - h;
        c.fill(left, top, w, h, Colors.alpha(Colors.BLACK, alpha));
        c.fill(left + 1, top + 1, w - 2, h - 2, Colors.alpha(Colors.mix(body, Colors.WHITE, flash), alpha));
        String label = enemy.name().toUpperCase();
        if (shell.font.width(label) > w - 4) {
            label = label.split(" ")[0];
        }
        shell.font.drawOutlined(c, label, x - shell.font.width(label) / 2, top + h / 2 - 2,
                Colors.alpha(Colors.WHITE, alpha), 1);
        return new Box(left, top, w, h);
    }

    /** The boss shown at the top of the map, shrunk to fit a 50x36 window; false when there is no art. */
    static boolean drawPortrait(Shell shell, SceneCanvas c, String encounterId, int x, int bottomY) {
        String art = switch (encounterId) {
            case "aiz:fire_breath", "aiz:flame_craft", "hcz:big_shaker", "hcz:screw_mobile", "lbz:big_arm",
                    "lbz:beam_rocket" -> encounterId;
            case "ssz:mecha_sonic" -> "ssz:mecha_sonic";
            default -> null;
        };
        if (art == null) {
            return false;
        }
        List<Part> parts = compose(shell, null, art, shell.ticks);
        if (parts.isEmpty()) {
            return false;
        }
        float[] b = bounds(parts);
        float scale = Math.min(0.75f, Math.min(48f / (b[2] - b[0]), 34f / (b[3] - b[1])));
        float ox = x - (b[0] + b[2]) / 2f * scale;
        float oy = bottomY + 2 - b[3] * scale;
        c.clip(x - 25, bottomY - 34, 50, 38);
        for (Part p : parts) {
            c.draw(p.sprite(), ox + p.dx() * scale, oy + p.dy() * scale,
                    SceneDraw.plain().withScale(scale).withFlipX(p.flipX()));
        }
        c.unclip();
        return true;
    }

    /**
     * An enemy's composition (by encounter or enemy art id) scaled to fit {@code maxW} x
     * {@code maxH} (never above {@code maxScale}), centred on {@code x} with its base on
     * {@code bottomY}, without clipping; false when it has no art.
     */
    static boolean drawFitted(Shell shell, SceneCanvas c, String art, float x, float bottomY, float maxW, float maxH,
            float maxScale, long ticks, int flash) {
        List<Part> parts = compose(shell, null, art, ticks);
        if (parts.isEmpty()) {
            return false;
        }
        float[] b = bounds(parts);
        float scale = Math.min(maxScale, Math.min(maxW / (b[2] - b[0]), maxH / (b[3] - b[1])));
        float ox = x - (b[0] + b[2]) / 2f * scale;
        float oy = bottomY - b[3] * scale;
        for (Part p : parts) {
            c.draw(p.sprite(), ox + p.dx() * scale, oy + p.dy() * scale,
                    SceneDraw.plain().withScale(scale).withFlipX(p.flipX()).withFlash(flash));
        }
        return true;
    }
}
