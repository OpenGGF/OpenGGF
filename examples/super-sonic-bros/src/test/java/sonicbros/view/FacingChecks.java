package sonicbros.view;

import com.openggf.mods.scene.*;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import sonicbros.content.Roster;
import sonicbros.sim.*;

/** ROM-backed draw checks; invoked by TestSuperSonicBrosScene with the real packaged mod. */
public final class FacingChecks {
    private record Draw(SceneSprite sprite, float x, float y, SceneDraw style) { }
    private static final List<Draw> draws = new ArrayList<>();
    private static final SceneCanvas canvas = (SceneCanvas) Proxy.newProxyInstance(
            SceneCanvas.class.getClassLoader(), new Class<?>[] { SceneCanvas.class }, (proxy, method, args) -> {
                if (method.getName().equals("draw") && args[0] instanceof SceneSprite sprite) {
                    draws.add(new Draw(sprite, (float) args[1], (float) args[2], (SceneDraw) args[3]));
                }
                return null;
            });

    public static void check(SceneRomArt rom) {
        Art art = new Art(rom);
        FighterArt painter = new FighterArt(art);
        Roster roster = new Roster();
        for (int phase = 0; phase < 3; phase++) {
            SceneSprite flame = art.prop("flame" + phase);
            require(flame.width() == 18 && flame.height() == 10 && flame.originX() == 0,
                    "horizontal flame pivots at its base");
            require((flame.image().pixel(0, 5) >>> 24) == 255
                    && (flame.image().pixel(17, 5) >>> 24) < 255, "flame fades toward rightward tip");
        }
        for (FighterDef def : roster.all()) for (int side : new int[] {-1, 1}) {
            Rules rules = new Rules();
            rules.itemFrequency = 0;
            Match match = new Match(rules, Arena.flat(288, false), List.of(
                    new Match.Entrant(def, 0, 0, 0), new Match.Entrant(roster.byId("sonic"), 0, 1, 0)), 1);
            match.countdown = 0;
            Fighter fighter = match.fighters.get(0);
            fighter.state = Fighter.State.AIR;
            fighter.ground = -1;
            fighter.x = match.arena.ledgeX(side) + side * (fighter.halfWidth() + 2);
            fighter.y = match.arena.ledgeY(side) + 15;
            fighter.vy = 1;
            fighter.facing = side;
            match.step(new Pad[] {Pad.none(), Pad.none()});
            require(fighter.state == Fighter.State.LEDGE && fighter.facing == -side,
                    def.id + " faces inward on first catch at side " + side);
        }
        for (int facing : new int[] {-1, 1}) {
            // Raw-ROM directions were checked in the decoded mappings, independently of renderer defaults.
            for (boolean superForm : new boolean[] {false, true}) {
                painter.superForm = superForm;
                checkPose(painter, "sonic", roster.byId("sonic").poses.ledge()[0], facing, facing > 0);
            }
            painter.superForm = false;
            checkPose(painter, "knuckles", roster.byId("knuckles").poses.ledge()[0], facing, facing > 0);
            for (int frame : new int[] {0, 1, 8, 9, 16, 18, 20, 21}) {
                checkPose(painter, "mecha", frame, facing, facing > 0);
            }
            for (int frame : new int[] {0, 1, 3}) {
                checkPose(painter, "rhinobot", frame, facing, frame == 3 ? facing < 0 : facing > 0);
            }
            for (var slot : new FighterDef.Slot[] {FighterDef.Slot.JAB, FighterDef.Slot.FTILT,
                    FighterDef.Slot.FSMASH, FighterDef.Slot.FAIR, FighterDef.Slot.NSPEC, FighterDef.Slot.BAIR}) {
                for (int pose : roster.byId("knuckles").move(slot).pose) {
                    if ((pose & 0xFFFF) == 0xDE) checkPose(painter, "knuckles", pose, facing,
                            slot == FighterDef.Slot.BAIR ? facing > 0 : facing < 0);
                }
            }
            for (var slot : new FighterDef.Slot[] {FighterDef.Slot.DTILT, FighterDef.Slot.BAIR, FighterDef.Slot.DSPEC}) {
                for (int pose : roster.byId("rhinobot").move(slot).pose) {
                    checkPose(painter, "rhinobot", pose, facing, facing > 0);
                }
            }
            for (String id : new String[] {"sonic", "tails", "knuckles", "mecha", "rhinobot"}) {
                int pose = roster.byId(id).poses.skid()[0];
                checkPose(painter, id, pose, facing, id.equals("mecha") || id.equals("rhinobot") ? facing < 0 : facing > 0);
            }
            for (String id : new String[] {"sonic", "tails"}) {
                checkPose(painter, id, roster.byId(id).poses.lying()[0], facing, facing > 0);
            }
            // Arm, body and legs must all share the final mirror, including translated origins.
            for (int pose : new int[] {Roster.ROBO_AIM, Roster.ROBO_PUNCH, Roster.ROBO_KICK, Roster.ROBO_SPIN}) {
                for (int flag : new int[] {0, Roster.FLIP}) for (long tick : new long[] {0, 3}) {
                    draw(painter, "eggrobo", pose | flag, facing, tick);
                    boolean flip = (facing > 0) ^ (flag != 0) ^ (pose == Roster.ROBO_SPIN && tick == 0);
                    double sign = flip ? -1 : 1;
                    require(draws.size() == 3, "Egg Robo pieces");
                    for (Draw d : draws) require(d.style.flipX() == flip, "Egg Robo piece flip");
                    double gun = pose == Roster.ROBO_AIM ? -38 : pose == Roster.ROBO_PUNCH ? -44 : -28;
                    require(draws.get(2).x == 100 + gun * sign, "Egg Robo arm origin");
                    require(draws.get(0).x == 100 + (-12 + (pose == Roster.ROBO_KICK ? -10 : 0)) * sign,
                            "Egg Robo leg origin");
                }
            }
        }
        // A spinning pose must retain its turn, even if a static use of that mapping is corrected.
        require((roster.byId("sonic").move(FighterDef.Slot.DSMASH).pose[17] & Roster.FLIP) == 0,
                "Sonic full rotation is not globally normalised");
    }

    private static void checkPose(FighterArt painter, String id, int pose, int facing, boolean flip) {
        draw(painter, id, pose, facing, 0);
        require(!draws.isEmpty(), id + " draws");
        for (Draw d : draws) {
            require(d.sprite.width() > 1, id + " real ROM art");
            require(d.style.flipX() == flip, id + " pose " + Integer.toHexString(pose) + " facing " + facing);
        }
    }
    private static void draw(FighterArt painter, String id, int pose, int facing, long tick) {
        draws.clear();
        painter.draw(canvas, id, pose, 100, 100, 80, true, facing, 1, 0, 0, 1, tick);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
