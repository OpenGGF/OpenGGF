package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import java.util.ArrayList;
import java.util.List;
import starpost.core.Game;
import starpost.core.Item;
import starpost.people.Bodies;
import starpost.people.People;
import starpost.ui.Text;
import starpost.valley.ValleyView;

/**
 * The Star Light Feast in the plaza at evening: Star Light Zone's night city over the valley (its
 * level kit's own backdrop) and Star Light's music, everyone round Clementine's long table, then
 * the secret gifts. The secret friend named by letter on Winter 18 steps up for the farmer's gift
 * (chosen from the monitors; their taste counts three times over), someone else brings the farmer
 * a present, and the feast ends under fireworks made of Sonic 3 &amp; Knuckles' ring sparkles.
 */
final class FeastScreen extends FestivalScreen {
    private static final int S1_SLZ = 0x84;           // bgm_SLZ

    private final List<String> guests = new ArrayList<>();
    private String friend;
    private String giver;
    private Item gift;
    private int phase;
    private int phaseAt;
    private boolean asked;
    private final List<float[]> sparks = new ArrayList<>();
    private String stepping;     // who has stepped up from the table
    private float stepX;

    FeastScreen(FestivalSystem sys, Festival festival) {
        super(sys, festival);
    }

    @Override
    void begin() {
        int ax = sys.anchorX(festival);
        holdTown(ax - 10);
        offstage(true);
        play.valley().sky = sys.art.starLight();
        guests.addAll(crowd());
        friend = festivals.secretFriend(shell.game);
        giver = festivals.secretGiver(shell.game);
        if (friend != null && !guests.contains(friend)) {
            friend = null;
        }
        shell.music.want("s1", S1_SLZ);
    }

    private int ax() {
        return sys.anchorX(festival);
    }

    @Override
    void step() {
        freezeInput();
        play.update(shell);
        int since = t - phaseAt;
        switch (phase) {
            case 0 -> {
                if (since == 1) {
                    caption(Festivals.host(festival, shell.game), "THE STAR LIGHT FEAST! EAT, EVERYONE. THEN THE SECRET GIFTS!",
                            "item:chili_dog", "heart", "gift", "!");
                }
                if (since > 170) {
                    next(friend == null ? 3 : 1);
                    if (friend == null) {
                        caption(null, "NO SECRET FRIEND THIS YEAR. THE FEAST COMMITTEE'S LETTER COMES ON WINTER 18.");
                    }
                }
            }
            case 1 -> {
                stepping = friend;
                stepX = Math.min(stepX + 1.2f, 60);
                if (since == 1) {
                    stepX = 0;
                }
                if (since > 60 && !asked) {
                    asked = true;
                    chooseGift();
                }
            }
            case 2 -> {
                if (since > 200) {
                    next(3);
                }
            }
            case 3 -> {
                stepping = giver;
                if (since == 1) {
                    stepX = 0;
                }
                stepX = Math.min(stepX + 1.2f, 60);
                if (since == 70 && giver != null) {
                    caption(giver, "AND THIS IS FOR YOU, {FARMER}. FROM YOUR SECRET FRIEND. HAPPY STAR LIGHT!",
                            "gift", "farmer", "heart", "sparkle");
                }
                if (since > (giver == null ? 30 : 230)) {
                    stepping = null;
                    next(4);
                }
            }
            case 4 -> {
                if (since % 14 == 0) {
                    sparks.add(new float[] {40 + Board.mix(t, 1) % 320, 40 + Board.mix(t, 2) % 50, t});
                    shell.sfx(starpost.scene.Sfx.RING);
                }
                if (since > 260) {
                    next(5);
                    end();
                }
            }
            default -> {
            }
        }
        sparks.removeIf(s -> t - s[2] > 40);
    }

    private void next(int p) {
        phase = p;
        phaseAt = t;
    }

    private void chooseGift() {
        Game game = shell.game;
        List<String> labels = new ArrayList<>();
        List<Item> icons = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < game.inventory.size(); i++) {
            String id = game.inventory.id(i);
            if (id != null && !ids.contains(id) && People.giftable(game.item(id))) {
                ids.add(id);
                labels.add(game.item(id).name());
                icons.add(game.item(id));
            }
        }
        String name = BoardScreen.name(friend, game);
        if (ids.isEmpty()) {
            caption(friend, "NO GIFT? THAT'S ALL RIGHT. YOU CAME. THAT'S THE GIFT.", "gift", "no", "heart");
            next(2);
            return;
        }
        shell.push(new Choose("A GIFT FOR " + name, labels, icons, i -> {
            gift = i >= 0 && i < ids.size() ? game.item(ids.get(i)) : null;
            react();
        }));
    }

    private void react() {
        Game game = shell.game;
        People people = people();
        next(2);
        if (gift == null || people == null) {
            caption(friend, "OH! YOU WERE MY SECRET FRIEND? THANK YOU FOR COMING.");
            return;
        }
        var taste = people.cast.get(friend).taste(gift);
        String line = switch (taste) {
            case LOVE -> "A " + gift.name() + "! HOW DID YOU KNOW? IT'S PERFECT!";
            case LIKE -> "A " + gift.name() + ". THAT'S REALLY KIND OF YOU.";
            case NEUTRAL -> "OH, A " + gift.name() + ". THANK YOU!";
            default -> "A... " + gift.name() + ". IT'S THE THOUGHT THAT COUNTS. IT IS.";
        };
        caption(friend, line, "gift", "item:" + gift.id(), taste == starpost.people.Taste.LOVE ? "heart"
                : taste == starpost.people.Taste.HATE || taste == starpost.people.Taste.DISLIKE ? "sad" : "sparkle");
    }

    private void end() {
        Game game = shell.game;
        List<String> lines = new ArrayList<>();
        if (friend != null) {
            lines.add("SECRET FRIEND: " + BoardScreen.name(friend, game)
                    + (gift == null ? " (NO GIFT)" : " - " + gift.name()));
        }
        lines.addAll(Feast.reward(game, festivals, friend, gift, giver));
        lines.add("EVERYONE ATE. MOMENTUM FULL!");
        finish("HAPPY STAR LIGHT", lines);
    }

    @Override
    void restore() {
        super.restore();
        offstage(false);
        play.valley().sky = null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    void paint(SceneCanvas canvas) {
        play.draw(shell, canvas);
        ValleyView view = play.valley();
        int cx = Math.round(view.cameraX()), cy = Math.round(view.cameraY());
        int ax = ax();
        // Guests along the plaza and behind Clementine's long table (drawn again over them).
        float farmerX = view.runner.x;
        for (int i = 0; i < guests.size(); i++) {
            String id = guests.get(i);
            float seat = seat(i, ax, farmerX);
            float gx = id.equals(stepping) ? seat + (farmerX + 34 - seat) * Math.min(1f, stepX / 60f) : seat;
            int pose = id.equals(stepping) ? (stepX < 60 ? Bodies.WALK : Bodies.HAPPY)
                    : phase == 4 ? Bodies.LOOK_UP : Bodies.IDLE;
            drawVillager(canvas, id, gx - cx, sys.floor(Math.round(gx)) - cy, pose, gx > farmerX, 0, SceneDraw.plain());
        }
        drawTable(canvas, ax, cx, cy);
        // The fireworks: ring sparkles bursting in a circle.
        for (float[] s : sparks) {
            float age = t - s[2];
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                float r = age * 1.4f;
                int frame = 4 + (int) Math.min(3, age / 10);
                canvas.draw(shell.art.ring.frame(frame), s[0] + (float) Math.cos(a) * r, s[1] + (float) Math.sin(a) * r,
                        SceneDraw.plain());
            }
        }
    }

    /** A guest's place: either side of the farmer, the far ones behind the table. */
    private static float seat(int i, int ax, float farmerX) {
        float x = ax - 150 + i * 28;
        return Math.abs(x - farmerX) < 22 ? x + 44 : x;
    }

    /** Clementine's long table over the guests behind it: planks on legs, laid with food. */
    private void drawTable(SceneCanvas canvas, int ax, int cx, int cy) {
        int x0 = ax + 40, x1 = ax + 170, floor = sys.floor(ax + 100), top = floor - 18;
        canvas.fill(x0 - cx, top - cy, x1 - x0, 5, 0xFFB66D24);
        canvas.fill(x0 - cx, top + 5 - cy, x1 - x0, 2, 0xFF6D2400);
        canvas.fill(x0 - cx, top + 7 - cy, x1 - x0, floor - top - 7, 0xFF924900);
        String[] food = {"chili_dog", "loop_pie", "radish_soup", "chili_dog", "loop_pie", "radish_soup", "robo_cola"};
        for (int i = 0; i < food.length; i++) {
            if (shell.catalog.hasItem(food[i]) && (phase < 4 || (i + t / 30) % 3 != 0)) {
                shell.art.icons.draw(canvas, shell.catalog.item(food[i]), x0 + 4 + i * 18 - cx, top - 13 - cy,
                        SceneDraw.plain());
            }
        }
    }

    @Override
    void paintOver(SceneCanvas canvas) {
        Text.shadow(canvas, "STAR LIGHT FEAST", 16, 10, Text.YELLOW);
        if (friend != null) {
            Text.right(canvas, "SECRET FRIEND: " + BoardScreen.name(friend, shell.game), canvas.width() - 10, 10,
                    Text.WHITE);
        }
    }
}
