package starpost.valley;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import starpost.art.Anim;
import starpost.art.Art;
import java.util.List;
import starpost.scene.Actor;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Controls;
import starpost.ui.Text;

/**
 * The valley in side view: Green Hill's blocks and collision, Green Hill's parallax, and the
 * farmer on the ported Sonic controller with the spring and the loop. Doorways are entered by
 * pressing up; walking west past the gate returns to the farm.
 */
public final class ValleyView {
    private static final float LOOP_CX_OFFSET = 126;
    private static final float LOOP_CY = 111;
    private static final float LOOP_R = 60;      // the ball's centre, inside the loop's 75-pixel surface

    private final Shell shell;
    public final Valley valley;
    public final Runner runner = new Runner(200, Art.FLOOR);
    private float camX;
    private float camY;
    private long springFiredAt = -100;
    private boolean looping;
    private float loopAngle;
    private final Anim anim = new Anim();
    private final starpost.art.Dust dust;
    private final starpost.art.TailsTails tails;
    /** Set by the play screen: the valley's actors, and the action button offered to them. */
    public java.util.function.IntFunction<List<Actor>> actors = view -> List.of();
    public java.util.function.BooleanSupplier interact = () -> false;
    /** Doorway labels over the farmer's head (cutscenes turn them off). */
    public boolean labels = true;
    /** Another sky for an evening (the Star Light Feast's), drawn instead of Green Hill's; null for Green Hill's. */
    public com.openggf.mods.scene.SceneBackdrop sky;

    public enum Request {
        NONE,
        TO_FARM,
        ENTER
    }

    /** The place entered by the last {@link Request#ENTER}. */
    public Valley.Place entered;

    public ValleyView(Shell shell) {
        this.shell = shell;
        this.valley = new Valley(shell.art);
        this.dust = shell.art.newDust();
        this.tails = shell.art.newTails();
    }

    /** Arrives from the farm gate, running east. */
    public void arriveFromFarm() {
        runner.x = 190;
        runner.y = valley.floorBelow(190, 0);
        runner.onGround = true;
        runner.speed = 3;
        runner.ySpeed = 0;
        runner.facingLeft = false;
        looping = false;
        camX = clampX(runner.x - shell.width() / 2f);
        camY = clampY(runner.y - 150);
    }

    public float cameraX() {
        return camX;
    }

    public float cameraY() {
        return camY;
    }

    public void snapCamera() {
        camX = clampX(runner.x - shell.width() / 2f);
        camY = clampY(runner.y - 150);
    }

    public Request update(Controls in) {
        if (looping) {
            stepLoop();
        } else {
            Valley.Place place = valley.placeAt(runner.x);
            if (place != null && runner.onGround && in.upPressed && !place.id().equals("farm_gate")) {
                entered = place;
                return Request.ENTER;
            }
            if (in.act && runner.onGround && interact.getAsBoolean()) {
                in.consume();
            }
            runner.character = shell.game.farmer;
            runner.climbUp = in.up;
            if (runner.step(valley, in.left, in.right, in.down, in.jump, in.jumpHeld, (int) shell.ticks)) {
                shell.sfx(Sfx.JUMP);
            }
            if (Math.abs(runner.x - valley.springX) < 12 && runner.y >= Art.FLOOR - 2
                    && (runner.onGround || runner.ySpeed > 0)) {
                runner.spring(10);
                springFiredAt = shell.ticks;
                shell.sfx(Sfx.SPRING);
            }
            if (runner.onGround && runner.speed >= 4 && runner.x >= valley.loopX + 96 && runner.x <= valley.loopX + 124) {
                looping = true;
                loopAngle = -0.35f;
            }
            if (runner.x < 110 && runner.speed <= 0) {
                return Request.TO_FARM;
            }
        }
        animate();
        dust.update(anim.id() == Anim.SPINDASH, false, false, false, runner.x, originY(runner.y));
        // Round the loop his velocity is the circle's tangent (the angle grows anticlockwise on screen).
        tails.update(anim.id(), runner.facingLeft, looping ? (float) Math.cos(loopAngle) : runner.speed,
                looping ? -(float) Math.sin(loopAngle) : runner.onGround ? 0 : runner.ySpeed);
        float targetX = runner.x - shell.width() / 2f + (runner.facingLeft ? -24 : 24);
        camX += (clampX(targetX) - camX) * 0.18f;
        camY += (clampY(runner.y - 150) - camY) * 0.15f;
        return Request.NONE;
    }

    /** Block 53's collision holds only the entry ramp, the right inner wall and the top (Sonic 1 swaps paths). */
    private void stepLoop() {
        loopAngle += Math.max(0.07f, runner.speed / LOOP_R);
        if (loopAngle >= (float) (Math.PI * 2) - 0.1f) {
            looping = false;
            runner.x = valley.loopX + 184;
            runner.y = Art.FLOOR;
            runner.onGround = true;
            return;
        }
        runner.x = valley.loopX + LOOP_CX_OFFSET + (float) Math.sin(loopAngle) * LOOP_R;
        runner.y = LOOP_CY + (float) Math.cos(loopAngle) * LOOP_R;
    }

    private float clampX(float x) {
        return Math.max(0, Math.min(valley.width() - shell.width(), x));
    }

    private float clampY(float y) {
        return Math.max(-64, Math.min(Art.BLOCK - shell.height(), y));
    }

    private void animate() {
        float speed = Math.abs(runner.speed);
        if (runner.flying) {
            anim.set(Anim.flying(runner.flyTimer == 0, runner.ySpeed), 0x0B);           // AniTails24's delay
        } else if (runner.gliding) {
            anim.set(0x20, 3);                                                         // Knuckles's glide
        } else if (runner.climbing) {
            anim.set(0x22, 8);                                                         // GLIDE_LAND frames on the wall
        } else if (looping || runner.rolling || !runner.onGround && !runner.sprung) {
            anim.set(Anim.ROLL, Math.max(0, 4 - (int) Math.max(speed, looping ? 6 : 0)));
        } else if (runner.sprung) {
            anim.set(Anim.SPRING, 2);
        } else if (runner.dashing) {
            anim.set(Anim.SPINDASH, 0);
        } else if (runner.ducking) {
            anim.set(Anim.DUCK, 6);
        } else if (runner.pushing) {
            anim.set(Anim.PUSH, 8);
        } else if (speed > 0.05f) {
            anim.set(speed >= Runner.TOP ? Anim.RUN : Anim.WALK, Math.max(0, 8 - (int) speed));
        } else {
            anim.set(Anim.WAIT, 6);
        }
        anim.tick();
    }

    // ------------------------------------------------------------------ drawing

    public void draw(SceneCanvas canvas, Art.Seasonal look, int light, SceneDraw tint) {
        int w = canvas.width(), h = canvas.height();
        int cx = Math.round(camX), cy = Math.round(camY);
        canvas.drawBackdrop(sky != null ? sky : look.backdrop(light), 0, 0, w, h,
                Math.max(0, Math.min(32, Math.round(8 + camY * 0.1f))), camX, shell.ticks);
        for (int column = Math.max(0, cx / Art.BLOCK); column <= Math.min(valley.blocks.length - 1, (cx + w) / Art.BLOCK);
                column++) {
            SceneImage block = look.block(valley.blocks[column]);
            if (block == null) {
                continue;
            }
            int x = column * Art.BLOCK - cx;
            canvas.draw(block, x, -cy, tint);
            canvas.drawRegion(block, 0, Art.BLOCK - 32, Art.BLOCK, 32, x, Art.BLOCK - cy, Art.BLOCK, 32, tint);
        }
        drawTown(canvas, look, cx, cy, tint);
        int frame = shell.ticks - springFiredAt < 12 ? 1 : 0;
        drawSprite(canvas, shell.art.spring, frame, valley.springX - cx, valley.floorBelow(valley.springX, 0) - cy, tint, false);
        drawSprite(canvas, shell.art.starpost, 0, 150 - cx, valley.floorBelow(150, 0) - cy, tint, false);
        for (Actor actor : actors.apply(Actor.VALLEY)) {
            if (Math.abs(actor.x() - camX - w / 2f) < w) {
                actor.draw(shell, canvas, cx, cy, tint);
            }
        }
        drawFarmer(canvas, tint, cx, cy);
        for (Actor actor : actors.apply(Actor.VALLEY)) {
            if (Math.abs(actor.x() - camX - w / 2f) < w) {
                actor.drawOver(shell, canvas, cx, cy);
            }
        }
        Valley.Place place = valley.placeAt(runner.x);
        if (labels && place != null && runner.onGround && !looping) {
            String label = place.id().equals("farm_gate") ? "< " + place.label() : "UP: " + place.label();
            int x = Math.round(place.x() - cx) - canvas.textWidth(label) / 2;
            Text.shadow(canvas, label, Math.max(4, Math.min(w - 4 - canvas.textWidth(label), x)),
                    Math.round(runner.y - cy) - 58, Text.YELLOW);
        }
    }

    /** The town's buildings (assembled from Green Hill's pieces by {@code Facades}), the capsule on the plateau. */
    private void drawTown(SceneCanvas canvas, Art.Seasonal look, int cx, int cy, SceneDraw tint) {
        for (Valley.Place place : valley.places) {
            SceneImage building = switch (place.id()) {
                case "seed_stall" -> look.seedStall;
                case "inn" -> look.inn;
                case "workshop" -> look.workshop;
                case "robomart" -> look.robomart;
                default -> null;
            };
            int floor = valley.floorBelow(place.x(), 0);
            int x = place.x() - cx, y = floor - cy;
            if (x < -100 || x > canvas.width() + 100) {
                continue;
            }
            if (building != null) {
                canvas.draw(building, x - building.width() / 2f, y - building.height() + 2, tint);
                int sw = canvas.textWidth(place.label()) + 8;
                int sy = y - building.height() - 8;
                canvas.fill(x - sw / 2, sy, sw, 12, 0xFF240000);
                canvas.fill(x - sw / 2 + 1, sy + 1, sw - 2, 10, 0xFF6D2400);
                canvas.text(place.label(), x - sw / 2 + 4, sy + 2, Text.YELLOW);
            } else if (place.id().equals("capsule")) {
                drawSprite(canvas, shell.art.capsule, 0, x, y, tint, false);
            }
        }
    }

    /** The farmer's body origin (the ROM's x_pos/y_pos row) for feet at {@code feet}. */
    private float originY(float feet) {
        SceneSprite pose = anim.pose(shell.art.farmer(shell.game.farmer));
        return looping ? feet : anim.id() == Anim.ROLL ? feet - 15 : feet - (pose.height() - pose.originY());
    }

    private void drawFarmer(SceneCanvas canvas, SceneDraw tint, int cx, int cy) {
        SceneSpriteSet set = shell.art.farmer(shell.game.farmer);
        SceneSprite pose = anim.pose(set);
        SceneDraw style = tint.withFlipX(runner.facingLeft);
        float originY = originY(runner.y) - cy;
        if (shell.game.farmer.equals("tails")) {
            tails.draw(canvas, shell.art.tailsTails, runner.x - cx, originY, style);
        }
        canvas.draw(pose, runner.x - cx, originY, style);
        SceneSpriteSet puffs = shell.art.dust(shell.game.farmer);
        dust.drawDash(canvas, puffs, runner.x - cx, originY, runner.facingLeft, tint);
        dust.drawPuffs(canvas, puffs, cx, cy, tint);
    }

    static void drawSprite(SceneCanvas canvas, SceneSpriteSet set, int frame, float x, float feet, SceneDraw style,
            boolean flip) {
        if (set == null || frame >= set.frameCount()) {
            return;
        }
        SceneSprite sprite = set.frame(frame);
        canvas.draw(sprite, x, feet - (sprite.height() - sprite.originY()), style.withFlipX(flip));
    }
}
