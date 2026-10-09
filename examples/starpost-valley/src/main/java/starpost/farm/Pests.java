package starpost.farm;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.state.SnapshotRandom;
import java.util.ArrayList;
import java.util.List;
import starpost.core.CropDef;
import starpost.core.Farm;
import starpost.core.Game;
import starpost.core.PlaceableDef;
import starpost.core.Plot;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Sfx;
import starpost.scene.Shell;

/**
 * Badnik pests (design doc §9.7): some mornings Motobugs crawl in from the wild land at the
 * field's open edge and make for the crops. One that reaches a plant eats it. A jump onto one, or
 * a roll into it, pops it: an animal hops free and joins the valley, and a little scrap drops.
 * Touching one any other way costs rings. Scarecrows keep them clear of the plots they cover.
 */
public final class Pests {
    private Pests() {
    }

    /** The morning's pests: none in winter or the first days, more later in the year. */
    public static List<Actor> spawn(Game game) {
        List<Actor> out = new ArrayList<>();
        int day = game.calendar.dayNumber();
        if (day < 2 || game.calendar.season() == starpost.core.Calendar.WINTER) {
            return out;
        }
        SnapshotRandom rng = new SnapshotRandom(game.rng.snapshot() ^ 0x5EED_BADL ^ day);
        int count = game.weather == Game.SWARM ? 3 + rng.nextInt(3)
                : rng.nextInt(100) < 45 ? 1 + rng.nextInt(day > 28 ? 4 : 2) : 0;
        for (int i = 0; i < count; i++) {
            out.add(new Motobug(game, rng.nextInt(Farm.ROWS), i * 70 + rng.nextInt(40)));
        }
        return out;
    }

    /** A single Motobug at a world x on a row (debug and scripted scenes). */
    public static Actor motobug(Game game, int row, float x) {
        Motobug bug = new Motobug(game, row, 0);
        bug.x = x;
        return bug;
    }

    /** One Motobug, crawling west along its row toward the nearest crop. */
    static final class Motobug implements Actor {
        private static final int EAT_TICKS = 150;
        private final int row;
        private float x;
        private boolean facingLeft = true;
        private int eating;
        private int popped = -1;
        private int age;
        private float animalX;
        private float animalHop;

        Motobug(Game game, int row, int delay) {
            this.row = row;
            this.x = FarmView.FIELD_X + game.farm.open() * 16 + 40 + delay;
        }

        @Override
        public int view() {
            return FARM;
        }

        @Override
        public float x() {
            return x;
        }

        @Override
        public float y() {
            return FarmView.ROW_Y + row * FarmView.ROW_STEP + 1;
        }

        @Override
        public void update(Shell shell, PlayScreen play) {
            age++;
            if (popped >= 0) {
                popped++;
                animalX += 1.2f;
                animalHop = -Math.abs((float) Math.sin(popped / 6.0)) * 10;
                return;
            }
            Game game = shell.game;
            FarmView farm = play.farm();
            BeltRunner sonic = farm.runner;
            boolean sameRow = Math.abs(farm.feetY() - y()) < 9;
            if (play.onFarm() && sameRow && Math.abs(sonic.x - x) < 14) {
                boolean attacking = sonic.rolling || sonic.height > 4 && sonic.ySpeed > 0;
                if (attacking) {
                    pop(shell, game);
                    if (sonic.height > 0) {
                        sonic.ySpeed = -4;        // the bounce off a popped badnik
                    }
                    return;
                }
                hurt(shell, game, sonic);
            }
            int column = (int) Math.floor((x - FarmView.FIELD_X) / 16f);
            Plot plot = game.farm.plot(row, column);
            if (plot != null && plot.crop != null && !plot.dead && !guarded(game, row, column)) {
                if (++eating >= EAT_TICKS) {
                    plot.clearCrop();
                    eating = 0;
                    shell.toast("A MOTOBUG ATE A CROP!");
                }
                return;
            }
            eating = 0;
            x -= 0.5f;
            if (x < FarmView.FIELD_X - 40) {
                x = FarmView.FIELD_X + game.farm.open() * 16 + 40;
            }
        }

        private static boolean guarded(Game game, int row, int column) {
            for (int r = 0; r < Farm.ROWS; r++) {
                for (int c = Math.max(0, column - 6); c < Math.min(Farm.COLUMNS, column + 7); c++) {
                    Plot p = game.farm.raw(r, c);
                    PlaceableDef def = p.object == null ? null : game.catalog.placeable(p.object);
                    if (def != null && def.role() == PlaceableDef.Role.SCARECROW
                            && Math.abs(c - column) <= def.reach() && Math.abs(r - row) <= def.reach()) {
                        return true;
                    }
                }
            }
            return false;
        }

        private void pop(Shell shell, Game game) {
            popped = 0;
            animalX = x;
            shell.sfx(Sfx.BREAK);
            game.inventory.add(game.item("scrap"), 1);
            if (game.free()) {
                shell.toast("AN ANIMAL IS FREE! VALLEY: " + game.population);
            }
        }

        private void hurt(Shell shell, Game game, BeltRunner sonic) {
            if (age % 30 != 0) {
                return;
            }
            int lost = Math.min(game.rings, 10);
            game.rings -= lost;
            sonic.speed = sonic.x < x ? -3 : 3;
            sonic.ySpeed = -3;
            sonic.height = 1;
            shell.sfx(lost > 0 ? Sfx.RING_LOSS : Sfx.ERROR);
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            float sx = x - cx, sy = y();
            if (popped >= 0) {
                if (popped < 24 && shell.art.explosion.frameCount() > 0) {
                    int frame = Math.min(shell.art.explosion.frameCount() - 1, popped / 5);
                    canvas.draw(shell.art.explosion.frame(frame), sx, sy - 12, SceneDraw.plain());
                }
                if (popped < 240) {
                    String[] kinds = {"pocky", "cucky", "picky", "ricky", "pecky"};
                    var set = shell.art.animal(kinds[row % kinds.length]);
                    SceneSprite a = set.frame(animalHop < -4 ? 1 : 0);
                    canvas.draw(a, animalX - cx, sy + animalHop - (a.height() - a.originY()), tint.withFlipX(true));
                }
                return;
            }
            SceneSprite s = shell.art.motobug.frame((int) (age / 8 % 2));
            float shake = eating > 0 ? (age / 3 % 2 == 0 ? 1 : -1) : 0;
            canvas.draw(s, sx + shake, sy - (s.height() - s.originY()), tint.withFlipX(!facingLeft));
            canvas.fill(Math.round(sx) - 9, Math.round(sy) - 1, 18, 3, 0x50000000);
        }
    }
}
