package starpost.festivals;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import starpost.art.Art;
import starpost.core.Game;
import starpost.people.PeopleArt;
import starpost.scene.Actor;
import starpost.scene.PlayScreen;
import starpost.scene.Screen;
import starpost.scene.Sfx;
import starpost.scene.Shell;
import starpost.ui.Text;
import starpost.valley.Valley;

/**
 * Festivals and the Signpost Board in the valley (design doc §18), installed on each day's play
 * screen by {@code Systems.install}: the board by the Lamppost Inn (its notes, its little
 * signpost, and the trophy shelf beside it), the day's festival decorations and doorway, the
 * invitation when the farmer walks into the plaza during the posted hours, and the board's
 * morning and finished popping jobs. Two actors: one drawn behind the valley's people (board,
 * shelf, stalls) and one in front (bunting, arches, flowers).
 */
public final class FestivalSystem {
    /** The board's x in the valley: on the plaza, against the Lamppost Inn's west wall. */
    public static final int BOARD_X = 3 * Art.BLOCK + 26;
    /** The trophy stand: between the Lamppost Inn and Tails' Workshop. */
    public static final int TROPHY_X = 4 * Art.BLOCK + 2;
    /** How close to the festival's sign walking in counts as arriving at it. */
    private static final int ARRIVE = 64;

    final Shell shell;
    final PlayScreen play;
    final Festivals festivals;
    final FestivalArt art;
    /** Today's festival (null on an ordinary day). */
    final Festival today;
    private PeopleArt peopleArt;
    private final Deque<String> notices = new ArrayDeque<>();
    private long noticeAt = -1000;
    private boolean morningDone;
    private boolean inZone;
    private long signSpinAt = -1000;
    private int lastPosted;

    private FestivalSystem(Shell shell, PlayScreen play, Festivals festivals) {
        this.shell = shell;
        this.play = play;
        this.festivals = festivals;
        this.art = new FestivalArt(shell.art, shell.ctx.art().rom("s2"));
        this.today = festivals.today(shell.game);
    }

    /** Adds the board, today's festival and their actors to a new play screen. */
    public static void install(Shell shell, PlayScreen play, List<Actor> actors, Map<String, Consumer<Shell>> places) {
        Festivals festivals = shell.game.section(Festivals.class);
        if (festivals == null) {
            return;
        }
        shell.art.icons.addSource("festivals", new FestivalIcons());
        FestivalSystem sys = new FestivalSystem(shell, play, festivals);
        Valley valley = play.valley().valley;
        valley.places.add(new Valley.Place("board", BOARD_X, 14, "SIGNPOST BOARD"));
        places.put("board", s -> s.push(new BoardScreen(sys)));
        if (sys.today != null) {
            valley.places.add(new Valley.Place("festival", sys.anchorX(), 22, sys.today.name));
            places.put("festival", s -> sys.ask());
        }
        actors.add(0, sys.new Behind());
        actors.add(sys.new Front());
    }

    PeopleArt peopleArt() {
        if (peopleArt == null) {
            peopleArt = new PeopleArt(shell.art);
        }
        return peopleArt;
    }

    /** The festival's sign in the valley. */
    int anchorX() {
        return anchorX(today);
    }

    int anchorX(Festival f) {
        for (Valley.Place place : play.valley().valley.places) {
            if (place.id().equals(f.anchor)) {
                return place.x();
            }
        }
        int x = starpost.people.Anchors.valleyX(f.anchor);
        return x < 0 ? starpost.people.Anchors.valleyX("plaza") : x;
    }

    /** The middle of the valley's view (the decoration actors stand there so the view never culls them). */
    private float cameraCentre() {
        return play.valley().cameraX() + shell.width() / 2f;
    }

    private java.util.function.IntUnaryOperator levelFloor;
    private long levelTicks;

    /** Existing presentation reused in an engine act; no director ticks or scene-player reads. */
    public void drawOnLevel(SceneCanvas canvas, int cx, int cy, boolean front,
                            java.util.function.IntUnaryOperator floor, long ticks) {
        levelFloor = floor;
        levelTicks = ticks;
        try {
            if (front) drawFront(canvas, cx, cy, SceneDraw.plain());
            else drawBehind(canvas, cx, cy, SceneDraw.plain());
        } finally { levelFloor = null; }
    }

    public static FestivalSystem forPlay(PlayScreen play) { return of(play); }

    int floor(int x) {
        if (levelFloor != null) return levelFloor.applyAsInt(x);
        int y = play.valley().valley.floorBelow(x, 0);
        return y >= Art.BLOCK ? Art.FLOOR : y;
    }

    // ------------------------------------------------------------------ joining

    /** At the festival's sign: join if it's open, else say when. */
    void ask() {
        Game game = shell.game;
        Festival f = today;
        if (f == null) {
            return;
        }
        int year = game.calendar.year();
        if (festivals.joined(f.id, year)) {
            shell.toast("THE " + f.name + " IS OVER. SEE YOU NEXT YEAR!");
        } else if (game.calendar.minutes() < f.open) {
            shell.toast("THE " + f.name + " STARTS AT " + Festival.clock(f.open));
        } else if (game.calendar.minutes() >= f.close) {
            shell.toast("THE " + f.name + " HAS PACKED UP FOR THE YEAR");
        } else {
            shell.push(new Ask("JOIN THE " + f.name + "?", () -> start(f)));
        }
    }

    /** Starts a festival's event as its own screen. */
    void start(Festival f) {
        shell.toast("");         // a notice from the day must not ride over the festival's title card
        Screen screen = switch (f.id) {
            case FestivalBook.RING_HUNT -> new RingHuntScreen(this, f);
            case FestivalBook.PARADE -> new ParadeScreen(this, f);
            case FestivalBook.RACE -> new RaceScreen(this, f);
            case FestivalBook.FLICKIES -> new FlickyScreen(this, f);
            case FestivalBook.FAIR -> new FairScreen(this, f);
            case FestivalBook.SCRAP_BRAIN -> new MazeScreen(this, f);
            case FestivalBook.ICE_CAP -> new IceCapScreen(this, f);
            default -> new FeastScreen(this, f);
        };
        shell.go(screen);
    }

    /** Called as a festival's screen hands back to the day. */
    void afterFestival(Festival f) {
        inZone = true;          // standing at the sign: no fresh invitation
    }

    // ------------------------------------------------------------------ the director's tick

    private void tick() {
        Game game = shell.game;
        if (!morningDone) {
            morningDone = true;
            notices.addAll(festivals.morning(game));
            lastPosted = festivals.board.posted().size();
        }
        if (shell.ticks % 30 == 0) {
            notices.addAll(festivals.board.checkPops(game, game.section(starpost.people.People.class)));
        }
        if (festivals.board.posted().size() > lastPosted) {
            signSpinAt = shell.ticks;
        }
        lastPosted = festivals.board.posted().size();
        if (!notices.isEmpty() && shell.ticks - noticeAt > 140 && !shell.hasOverlay() && !play.clockStopped
                && !shell.transitioning()) {
            shell.toast(notices.poll());
            noticeAt = shell.ticks;
        }
        // Walking into the plaza during the posted hours is an invitation (once per arrival).
        if (today != null && !play.onFarm() && !play.folding() && !play.clockStopped) {
            boolean near = Math.abs(play.valley().runner.x - anchorX()) < ARRIVE;
            if (near && !inZone && today.openAt(game.calendar) && !festivals.joined(today.id, game.calendar.year())
                    && !shell.hasOverlay() && !shell.transitioning() && play.valley().runner.onGround) {
                play.valley().runner.speed = 0;
                shell.push(new Ask("THE " + today.name + " IS ON!\nJOIN IN?", () -> start(today)));
            }
            inZone = near;
        }
    }

    // ------------------------------------------------------------------ drawing

    /** Is the valley showing a festival's dressing now (from the morning of the day). */
    private long renderTicks() { return levelFloor == null ? shell.ticks : levelTicks; }

    private boolean decorated() {
        return today != null;
    }

    private void drawBehind(SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        int season = shell.game.calendar.season();
        // The trophy shelf: logs on posts west of the board, a prize on each.
        drawShelf(canvas, cx, cy, tint);
        // The board: notes pinned for each posting; its little signpost spins when a new one goes up.
        SceneImage board = art.board(season);
        int bx = BOARD_X - board.width() / 2, by = floor(BOARD_X) - board.height() + 2;
        canvas.draw(board, bx - cx, by - cy, tint);
        List<Request> posted = festivals.board.posted();
        for (int i = 0; i < Math.min(4, posted.size()); i++) {
            int nx = bx + 8 + i * 13, ny = by + 16 + (i % 2) * 6;
            boolean robotnik = posted.get(i).type == Request.SPECIAL;
            canvas.fill(nx - cx, ny - cy, 10, 12, robotnik ? 0xFFFFDB00 : 0xFFFFFFFF);
            canvas.fill(nx + 2 - cx, ny + 4 - cy, 6, 1, 0xFF6D6D6D);
            canvas.fill(nx + 2 - cx, ny + 7 - cy, 5, 1, 0xFF6D6D6D);
            canvas.fill(nx + 4 - cx, ny - 1 - cy, 2, 2, robotnik ? 0xFF000000 : 0xFFDB0000);
        }
        if (festivals.board.posted().isEmpty() && festivals.board.active().isEmpty()) {
            canvas.fill(bx + 20 - cx, by + 18 - cy, 24, 14, 0xFFFFFFB6);   // the calendar alone
        }
        SceneSpriteSet sign = shell.art.signpost;
        if (sign != null && sign.frameCount() > 4) {
            long age = renderTicks() - signSpinAt;
            boolean special = hasSpecial();
            int frame = age < 48 ? 1 + (int) (age / 4 % 3) : special ? 0 : 4;   // Map_Sign: 0 Robotnik, 4 Sonic
            SceneSprite s = sign.frame(frame);
            canvas.draw(s, BOARD_X - cx, by - 8 - (s.height() - s.originY()) + 8 - cy, tint);
        }
        if (decorated()) {
            drawFestivalBehind(canvas, cx, cy, tint);
        }
    }

    private boolean hasSpecial() {
        for (Request r : festivals.board.posted()) {
            if (r.type == Request.SPECIAL) {
                return true;
            }
        }
        return false;
    }

    /**
     * The trophy stand between the Lamppost Inn and the Workshop: two tiers of bridge log on log
     * posts, three prizes a tier, in the order the festivals come round.
     */
    private void drawShelf(SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        List<String> trophies = festivals.trophies();
        if (trophies.isEmpty()) {
            return;
        }
        SceneImage shelf = art.shelf(shell.game.calendar.season());
        int floor = floor(TROPHY_X);
        int x0 = TROPHY_X - FestivalArt.SHELF_W / 2, y0 = floor - FestivalArt.SHELF_H + 2;
        canvas.draw(shelf, x0 - cx, y0 - cy, tint);
        for (int i = 0; i < Math.min(6, trophies.size()); i++) {
            int tx = x0 + 20 + (i % 3) * 36;
            int tier = y0 + (i < 3 ? FestivalArt.SHELF_LOWER : FestivalArt.SHELF_UPPER);
            drawTrophy(canvas, trophies.get(i), tx - cx, tier + 1 - cy, tint);
        }
    }

    /** A trophy standing on (x, top). */
    private void drawTrophy(SceneCanvas canvas, String festival, float x, float top, SceneDraw tint) {
        switch (festival) {
            case FestivalBook.RING_HUNT -> standSprite(canvas, shell.art.ring, (int) (renderTicks() / 8 % 4), x, top - 2, tint);
            case FestivalBook.PARADE -> {
                SceneImage f = art.flower((int) (renderTicks() / 16 % 2), 0);
                if (f != null) {
                    canvas.draw(f, x - 16, top - 32, tint);
                }
            }
            case FestivalBook.RACE -> standSprite(canvas, shell.art.lamppost, 3, x, top, tint);
            case FestivalBook.FAIR -> standSprite(canvas, art.bumper(), 0, x, top, tint);
            case FestivalBook.SCRAP_BRAIN -> standSprite(canvas, art.mechaSonic(), MazeScreen.MECHA_STAND, x, top, tint);
            case FestivalBook.ICE_CAP -> standSprite(canvas, art.snowboard(), IceCapScreen.BOARD_FLAT, x, top, tint);
            default -> {
            }
        }
    }

    static void standSprite(SceneCanvas canvas, SceneSpriteSet set, int frame, float x, float feet, SceneDraw tint) {
        if (set == null || frame >= set.frameCount()) {
            return;
        }
        SceneSprite s = set.frame(frame);
        canvas.draw(s, x, feet - (s.height() - s.originY()), tint);
    }

    /** Stalls and tables, drawn behind the crowd. */
    private void drawFestivalBehind(SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        int ax = anchorX();
        int floor = floor(ax);
        switch (today.id) {
            case FestivalBook.FEAST -> drawTable(canvas, ax, floor, cx, cy, tint);
            case FestivalBook.FAIR -> drawStalls(canvas, ax, floor, cx, cy, tint);
            default -> {
            }
        }
    }

    /** Clementine's long table: bridge-log planks on legs, laid with food. */
    private void drawTable(SceneCanvas canvas, int ax, int floor, int cx, int cy, SceneDraw tint) {
        int x0 = ax + 40, x1 = ax + 170, top = floor - 18;
        canvas.fill(x0 - cx, top - cy, x1 - x0, 5, 0xFFB66D24);
        canvas.fill(x0 - cx, top + 5 - cy, x1 - x0, 2, 0xFF6D2400);
        for (int lx = x0 + 6; lx < x1; lx += 40) {
            canvas.fill(lx - cx, top + 7 - cy, 3, floor - top - 7, 0xFF6D2400);
        }
        String[] food = {"chili_dog", "loop_pie", "radish_soup", "chili_dog", "loop_pie", "radish_soup", "robo_cola"};
        for (int i = 0; i < food.length; i++) {
            if (shell.catalog.hasItem(food[i])) {
                shell.art.icons.draw(canvas, shell.catalog.item(food[i]), x0 + 4 + i * 18 - cx, top - 13 - cy, tint);
            }
        }
    }

    /** The fair's booths: striped awnings over plank counters, a Spring Yard bumper on each awning. */
    private void drawStalls(SceneCanvas canvas, int ax, int ignored, int cx, int cy, SceneDraw tint) {
        SceneSpriteSet bumper = art.bumper();
        for (int i = 0; i < 3; i++) {
            int bx = FairScreen.boothX(i);
            int floor = floor(bx);
            int top = floor - 46;
            for (int s = 0; s < 6; s++) {
                canvas.fill(bx - 24 + s * 8 - cx, top - cy, 8, 8, s % 2 == 0 ? 0xFFDB0000 : 0xFFFFFFFF);
            }
            canvas.fill(bx - 24 - cx, top + 8 - cy, 48, 2, 0xFF240000);
            canvas.fill(bx - 22 - cx, top + 10 - cy, 2, 36, 0xFF6D2400);
            canvas.fill(bx + 20 - cx, top + 10 - cy, 2, 36, 0xFF6D2400);
            canvas.fill(bx - 24 - cx, floor - 16 - cy, 48, 16, 0xFF924900);
            canvas.fill(bx - 24 - cx, floor - 16 - cy, 48, 2, 0xFFDB9249);
            String label = FairScreen.boothName(i);
            canvas.text(label, bx - canvas.textWidth(label) / 2 - cx, floor - 12 - cy, Text.WHITE);
            standSprite(canvas, bumper, (renderTicks() / 12 + i) % 4 == 0 ? 1 : 0, bx - cx, top - cy, tint);
        }
    }

    /**
     * The festival's dressing over the crowd: two of Green Hill's palms with bunting strung
     * between them across the street, the festival's banner hanging in the middle, and two of
     * Sonic 1's lampposts by the doorway (Map_Lamp 0 by day, 3 lit at night; Scrap Brain Night's
     * flicker).
     */
    private void drawFront(SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        if (!decorated()) {
            return;
        }
        int ax = anchorX();
        boolean night = shell.game.calendar.light() == 2;
        boolean haunted = today.id.equals(FestivalBook.SCRAP_BRAIN);
        for (int lx : new int[] {ax - 50, ax + 36}) {
            int frame = haunted ? (renderTicks() / 6 % 7 == 0 ? 3 : 1) : night ? 3 : 0;
            standSprite(canvas, shell.art.lamppost, frame, lx - cx, floor(lx) - cy, tint);
        }
        if (today.id.equals(FestivalBook.FLICKIES)) {
            return;      // a quiet night: lamps lit along the meadow, nothing strung across the sky
        }
        // Two of Green Hill's palms carry the bunting across the street, tied a third of the way up.
        // Each stands on ground level with the sign (walked in from its spot past any ledge or
        // pillar), and the rope hangs from the sign's own ground, so the banner sits at the same
        // height over every gathering place.
        int base = floor(ax);
        int left = level(ax - 152, 8, base), right = level(ax + 118, -8, base);
        SceneImage palm = shell.art.season(shell.game.calendar.season()).palm;
        for (int px : new int[] {left, right}) {
            canvas.draw(palm, px - palm.width() / 2f - cx, floor(px) - palm.height() + 2 - cy, tint);
        }
        int top = base - 150;
        int[] colours = switch (today.id) {
            case FestivalBook.ICE_CAP -> new int[] {0xFFFFFFFF, 0xFF6DB6FF, 0xFFB6DBFF};
            case FestivalBook.SCRAP_BRAIN -> new int[] {0xFF6D6D6D, 0xFFB60000, 0xFF242424};
            case FestivalBook.FEAST -> new int[] {0xFFFFDB00, 0xFFFFFFFF, 0xFF6DB6FF};
            default -> new int[] {0xFFDB0000, 0xFFFFDB00, 0xFF2449DB, 0xFFFFFFFF};
        };
        int ropeTop = top + 10;
        int mid = 0, midY = 0;
        for (int x = left; x <= right; x += 2) {
            float f = (x - left) / (float) (right - left);
            int y = ropeTop + Math.round((float) Math.sin(f * Math.PI) * 22);
            canvas.fill(x - cx, y - cy, 2, 1, 0xFF240000);
            if (Math.abs(f - 0.5f) < 0.004f) {
                mid = x;
                midY = y;
            }
            if ((x - left) % 14 == 0 && x > left && x < right && Math.abs(f - 0.5f) > 0.12f) {
                int c = colours[(x - left) / 14 % colours.length];
                float sway = (float) Math.sin((renderTicks() + x) / 18.0) * 1.5f;
                for (int r = 0; r < 7; r++) {
                    int half = Math.max(0, 3 - r / 2);
                    canvas.fill(Math.round(x - half + sway * r / 7f) - cx, y + 1 + r - cy, half * 2 + 1, 1, c);
                }
            }
        }
        // The banner: a cloth in the festival's colours with its name, hanging from the rope.
        String name = today.name;
        int bw = canvas.textWidth(name) + 14, bx = mid - bw / 2, by = midY + 2;
        canvas.fill(bx - cx, by - cy, bw, 15, colours[0] == 0xFFFFFFFF ? 0xFF2449DB : 0xFF920000);
        canvas.fill(bx - cx, by + 13 - cy, bw, 2, 0xFF240000);
        canvas.fill(bx + 2 - cx, by - 2 - cy, 2, 3, 0xFF240000);
        canvas.fill(bx + bw - 4 - cx, by - 2 - cy, 2, 3, 0xFF240000);
        canvas.text(name, bx + 7 - cx, by + 3 - cy, haunted ? 0xFFFF4949 : Text.YELLOW);
        if (today.id.equals(FestivalBook.PARADE)) {
            drawSunflowers(canvas, cx, cy, tint);
        }
    }

    /** From {@code x}, steps of {@code step} toward the sign until the ground is within 16 pixels of {@code base}. */
    private int level(int x, int step, int base) {
        for (int i = 0; i < 12 && Math.abs(floor(x) - base) > 16; i++) {
            x += step;
        }
        return x;
    }

    /**
     * The parade day's sunflowers: Green Hill's big flower (frames swapped every 16 ticks, as
     * AniArt_GHZ_Bigflower does) along the street, its petals cycling through the parade's
     * colours in a wave, and in the spots where Green Hill's own blocks grow them.
     */
    void drawSunflowers(SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
        int ax = anchorX();
        int frame = (int) (renderTicks() / 16 % 2);
        for (int x = ax - 290; x <= ax + 290; x += 58) {
            if (Math.abs(x - ax) < 40) {
                continue;
            }
            int floor = floor(x);
            canvas.fill(x - cx, floor - 30 - cy, 2, 30, 0xFF006D00);
            canvas.fill(x - 4 - cx, floor - 14 - cy, 4, 2, 0xFF49B600);
            SceneImage f = art.flower(frame, (int) ((renderTicks() / 8 + x / 58) % FestivalArt.PETAL_CYCLE_LENGTH));
            if (f != null) {
                canvas.draw(f, x - 15 - cx, floor - 58 - cy, tint);
            }
        }
        for (int[] spot : romFlowerSpots()) {
            SceneImage f = art.flower(frame, (int) ((renderTicks() / 8 + spot[0] / 58) % FestivalArt.PETAL_CYCLE_LENGTH));
            if (f != null) {
                canvas.draw(f, spot[0] - cx, spot[1] - cy, tint);
            }
        }
    }

    /**
     * Where Green Hill's blocks grow the big flower in this valley (the top-left of each, from the
     * blocks' chunk layout: block 1 at cells (0,3) and (6,4), block 3 at (6,2) and (0,7), block 16
     * at (8,4), 16 pixels a cell). The level kit leaves those tiles empty: AniArt fills them.
     */
    private int[][] romFlowerSpots() {
        int[] blocks = play.valley().valley.blocks;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < blocks.length; i++) {
            int[][] cells = switch (blocks[i]) {
                case 1 -> new int[][] {{0, 3}, {6, 4}};
                case 3 -> new int[][] {{6, 2}, {0, 7}};
                case 4 -> new int[][] {{9, 2}};
                case 7 -> new int[][] {{0, 7}};
                case 16 -> new int[][] {{8, 4}};
                default -> new int[0][];
            };
            for (int[] c : cells) {
                out.add(new int[] {i * Art.BLOCK + c[0] * 16, c[1] * 16});
            }
        }
        return out.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ the two actors

    /** The system of a play screen, or null (debug commands). */
    static FestivalSystem of(PlayScreen play) {
        for (Actor actor : play.actors) {
            if (actor instanceof Behind behind) {
                return behind.system();
            }
        }
        return null;
    }

    /** Drawn first: the board, the shelf, stalls and tables. Also runs the system's tick. */
    private final class Behind implements Actor {
        FestivalSystem system() {
            return FestivalSystem.this;
        }

        @Override
        public int view() {
            return VALLEY;
        }

        @Override
        public float x() {
            return cameraCentre();     // never culled: it draws the board and the whole festival
        }

        @Override
        public float y() {
            return floor(BOARD_X);
        }

        @Override
        public float reach() {
            return -1;
        }

        @Override
        public void update(Shell shell, PlayScreen p) {
            tick();
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            drawBehind(canvas, cx, cy, tint);
        }
    }

    /** Drawn after the valley's people: bunting, arches, lamps and flowers. */
    private final class Front implements Actor {
        @Override
        public int view() {
            return VALLEY;
        }

        @Override
        public float x() {
            return cameraCentre();
        }

        @Override
        public float y() {
            return 0;
        }

        @Override
        public float reach() {
            return -1;
        }

        @Override
        public void update(Shell shell, PlayScreen p) {
        }

        @Override
        public void draw(Shell shell, SceneCanvas canvas, int cx, int cy, SceneDraw tint) {
            drawFront(canvas, cx, cy, tint);
        }
    }
}
