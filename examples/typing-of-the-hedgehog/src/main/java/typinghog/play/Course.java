package typinghog.play;

import com.openggf.control.InputActionMasks;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.mode.CourseCheckpoint;
import com.openggf.game.mode.CourseControl;
import com.openggf.game.mode.GameplayFrameController;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.mods.ModStorage;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.SplittableRandom;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;
import typinghog.rails.Rail;
import typinghog.save.ActResult;
import typinghog.save.LaunchIntent;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;

/**
 * The typed act: drives Sonic along the act's rail, stops him at stands where the
 * {@link Encounter} sends badniks, and runs the act's boss, tally and hand-back to the hub.
 *
 * <p>Determinism: rail rows feed the native step only through {@link #nativeInput}; a stand
 * captures a course checkpoint on entry, lets the world live with Sonic held while the player
 * types for as long as it takes, then restores the checkpoint, so the deterministic run resumes
 * exactly where it stopped. The typing layer never touches native physics, RNG or native object
 * slots: its badniks are {@link Stagehand} records drawn by auxiliary {@link Puppet}s.
 *
 * <p>Debug rewind is off; the controller's state is outside the course partition by design (it
 * survives restores), so its rewind adapter captures nothing useful and returns a constant.
 */
public final class Course implements GameplayFrameController, RewindSnapshottable<Integer> {
    enum Phase { IDLE, ENTRY, RUN, STAND, STAND_CLEAR, BOSS, DYING, TALLY, LEAVING }

    static final int START_RINGS = 10;
    static final int START_LIVES = 3;
    static final int STAND_CLEAR_ROWS = 50;
    static final int DYING_ROWS = 120;
    /** Directions and jump: the rail's pad bits ({@code AbstractPlayableSprite.INPUT_*}). */
    static final int PAD_BITS = 0x1F;

    private final ModStorage storage;
    private final CourseAssets assets;
    private final Stagehand stagehand;
    private final Hud hud;

    private Object levelToken;
    private LaunchIntent intent;
    private Rail rail;
    private Rail.Cursor cursor;
    private int nextStand;
    private Phase phase = Phase.IDLE;
    private int phaseRows;
    private TypingField field;
    private Scorecard card;
    private Encounter encounter;
    private int rings;
    private int lives;
    private int previousMask;
    private CourseCheckpoint standCheckpoint;
    /** The pad mask of the row before a stand, so press edges resume exactly after its restore. */
    private int standPreviousMask;
    private boolean paused;
    private boolean titleRequest;
    private boolean advancedThisRow;
    private boolean bossDefeated;
    private boolean shield;
    private int slowRows;
    private int invincibleRows;
    private int slowPhase;
    private long rows;
    private SplittableRandom random;
    private typinghog.core.Lexicon.Picker picker;
    private final KeyStats keyStats = new KeyStats();
    private final RunTags runTags = new RunTags();

    public Course(ModStorage storage, CourseAssets assets, Stagehand stagehand) {
        this.storage = storage;
        this.assets = assets;
        this.stagehand = stagehand;
        this.hud = new Hud(assets);
    }

    // ---- frame controller ------------------------------------------------------------------

    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot live) {
        rows++;
        detectLevel();
        if (intent == null || rail == null) return true;
        if (phase == Phase.IDLE) enter(Phase.ENTRY);
        if (course.keyPressed(GLFW_KEY_ESCAPE) && phase != Phase.TALLY && phase != Phase.LEAVING) paused = !paused;
        String typed = course.typedText();
        boolean backspace = course.keyPressed(GLFW_KEY_BACKSPACE);
        if (paused) {
            hud.pauseInput(this, typed, course.keyPressed(GLFW_KEY_ENTER), course.keyPressed(GLFW_KEY_UP),
                    course.keyPressed(GLFW_KEY_DOWN));
            return false;
        }
        phaseRows++;
        typeRow(typed, backspace);
        if (invincibleRows > 0) invincibleRows--;
        if (slowRows > 0 || intent.slowMotion()) {
            if (slowRows > 0) slowRows--;
            // Slow motion advances the native world (and everything on it) every other row; the
            // run is the same sequence of native steps, so it stays deterministic.
            if (phase == Phase.RUN || phase == Phase.STAND || phase == Phase.BOSS) {
                if ((++slowPhase & 1) == 0) return false;
            }
        }
        advancedThisRow = switch (phase) {
            case ENTRY -> entry(course);
            case RUN -> run(course);
            case STAND, BOSS -> stand(course);
            case STAND_CLEAR -> standClear(course);
            case DYING -> dying(course);
            case TALLY -> tally();
            case LEAVING, IDLE -> false;
        };
        return advancedThisRow;
    }

    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (intent == null || rail == null) return;
        if (advanced && encounter != null) encounter.advance(this);
        if (advanced && phase == Phase.RUN) runTags.advance(this);
        spawnPuppets();
    }

    @Override public LogicalInputSnapshot nativeInput(CourseControl course) {
        int mask = phase == Phase.RUN || phase == Phase.ENTRY ? cursor.next() & PAD_BITS : 0;
        int action = (mask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
        int previousAction = (previousMask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
        var p1 = PlayerInputState.of(mask, mask & ~previousMask, action, action & ~previousAction, false, false);
        previousMask = mask;
        return LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
    }

    @Override public boolean capturesKeyboardText() { return intent != null; }
    @Override public boolean allowsDebugRewind() { return false; }
    @Override public boolean presentationPaused() { return paused; }

    @Override public boolean consumeTitleRequest() {
        boolean requested = titleRequest;
        titleRequest = false;
        return requested;
    }

    @Override public void drawOverlay() {
        if (intent == null || rail == null) return;
        hud.draw(this);
    }

    // ---- rewind adapter: the controller's own state deliberately survives course restores -----

    @Override public String key() { return "typing-course"; }
    @Override public Integer capture() { return 0; }
    @Override public void restore(Integer value) { }

    // ---- phases -----------------------------------------------------------------------------

    private void detectLevel() {
        Object token = GameServices.level().getCurrentLevel();
        if (token == levelToken) return;
        levelToken = token;
        intent = storage.read(LaunchIntent.FILE).map(LaunchIntent::decode).orElse(null);
        storage.delete(LaunchIntent.FILE);
        String game = GameServices.module().getGameCode();
        if (intent != null && !intent.game().equals(game)) intent = null;
        rail = intent == null ? null : assets.rail(game, intent.zone(), intent.act());
        phase = Phase.IDLE;
        phaseRows = 0;
        stagehand.clear();
        stagehand.invalidatePuppets();
        runTags.clear(new TypingField(false));
        if (intent == null || rail == null) return;
        cursor = new Rail.Cursor(rail);
        nextStand = 0;
        field = new TypingField(intent.capitals());
        card = new Scorecard();
        random = new SplittableRandom(intent.seed());
        picker = assets.lexicon().new Picker(intent.seed() ^ 0x5EED, 48);
        keyStats.clear();
        encounter = null;
        rings = START_RINGS;
        lives = START_LIVES;
        previousMask = 0;
        standCheckpoint = null;
        bossDefeated = false;
        shield = false;
        slowRows = 0;
        invincibleRows = 0;
        paused = false;
        GameServices.level().setForceHudSuppressed(true);
    }

    private void enter(Phase next) {
        phase = next;
        phaseRows = 0;
    }

    private boolean entry(CourseControl course) {
        if (!course.presentationReady()) return true;
        if (intent.mode() == LaunchIntent.Mode.BOSS_RUSH) {
            // Boss Rush skips the run: the boss arrives where the act begins.
            beginStand(course, true);
            return stand(course);
        }
        enter(Phase.RUN);
        return true;
    }

    /** Applies the row's typing to whatever is on screen: run tags while running, the stand's targets in a stand. */
    private void typeRow(String typed, boolean backspace) {
        if (phase == Phase.RUN) {
            if (backspace) field.release();
            typed.codePoints().forEach(cp -> runTags.onEvent(this, field.type(cp), cp));
        } else if ((phase == Phase.STAND || phase == Phase.BOSS) && encounter != null) {
            if (backspace) encounter.onRelease(this, field.release());
            typed.codePoints().forEach(cp -> encounter.onEvent(this, field.type(cp), cp));
        } else {
            return;
        }
        if (!field.targets().isEmpty()) card.activeRow();
    }

    private boolean run(CourseControl course) {
        if (cursor.row() >= rail.bossRow()) {
            beginStand(course, true);
            return stand(course);
        }
        if (nextStand < rail.stands().size() && cursor.row() >= rail.stands().get(nextStand)) {
            nextStand++;
            beginStand(course, false);
            return stand(course);
        }
        if (cursor.finished()) {
            beginStand(course, true);
            return stand(course);
        }
        return true;
    }

    private void beginStand(CourseControl course, boolean boss) {
        runTags.clear(field);
        standCheckpoint = course.capture();
        standPreviousMask = previousMask;
        enter(boss ? Phase.BOSS : Phase.STAND);
        encounter = boss ? Encounter.boss(this)
                : intent.mode() == LaunchIntent.Mode.ENDLESS ? Encounter.endless(this) : Encounter.stand(this, nextStand);
        field.clear();
        hud.banner(boss ? "WARNING!" : "STAND " + nextStand, boss ? 0xFFFF4040 : 0xFFFFD000);
        play(Sounds.Cue.STAND_START);
        if (boss) playMusic(Sounds.Cue.BOSS_MUSIC);
    }

    private boolean stand(CourseControl course) {
        holdPlayer();
        if (encounter.finished()) {
            encounter.clearPickups(this);
            if (phase == Phase.BOSS) {
                bossDefeated = true;
                playMusic(Sounds.Cue.ACT_CLEAR_MUSIC);
                enter(Phase.TALLY);
                hud.beginTally(this);
                return false;
            }
            enter(Phase.STAND_CLEAR);
            hud.banner("WAVE CLEAR!", 0xFF60FF60);
            play(Sounds.Cue.STAND_CLEAR);
        }
        return true;
    }

    private boolean standClear(CourseControl course) {
        holdPlayer();
        if (phaseRows < STAND_CLEAR_ROWS) return true;
        course.restore(standCheckpoint);
        previousMask = standPreviousMask;
        stagehand.clear();
        stagehand.invalidatePuppets();
        encounter = null;
        hud.flash(0xFFFFFFFF, 12);
        hud.banner("GO!", 0xFFFFFFFF);
        enter(Phase.RUN);
        return true;
    }

    private boolean dying(CourseControl course) {
        if (phaseRows < DYING_ROWS) return true;
        if (lives <= 0 && !intent.noFail()) {
            finish(false);
            return false;
        }
        course.restore(standCheckpoint);
        previousMask = standPreviousMask;
        stagehand.clear();
        stagehand.invalidatePuppets();
        rings = START_RINGS;
        field.clear();
        boolean boss = encounter != null && encounter.isBoss();
        enter(boss ? Phase.BOSS : Phase.STAND);
        encounter = boss ? Encounter.boss(this) : Encounter.stand(this, nextStand);
        hud.banner("TRY AGAIN!", 0xFFFFFFFF);
        return true;
    }

    private boolean tally() {
        if (hud.tallyDone(phaseRows)) {
            finish(true);
            return false;
        }
        return false;
    }

    private void finish(boolean cleared) {
        var tally = card.tally(Math.max(0, rings));
        if (cleared) card.add(tally.total());
        var result = new ActResult(intent.mode(), intent.game(), intent.zone(), intent.act(), cleared, card.score(),
                card.wpm(), card.accuracy(), card.bestChain(), card.kills(), card.hitsTaken(), card.rescues(),
                Math.max(0, rings), tally.grade(), tally.total(), cleared && bossDefeated && intent.mode() == LaunchIntent.Mode.STORY,
                keyStats.hits(), keyStats.misses());
        storage.write(ActResult.FILE, result.encode());
        enter(Phase.LEAVING);
        titleRequest = true;
    }

    /** Quits to the hub without clearing the act (pause menu). */
    void quit() {
        paused = false;
        finish(false);
    }

    /** Restarts the current stand from its checkpoint (pause menu). */
    void retryStand() {
        paused = false;
        if (standCheckpoint == null) return;
        enter(Phase.DYING);
        phaseRows = DYING_ROWS;
    }

    // ---- player ----------------------------------------------------------------------------------

    /** Holds Sonic still while the world lives on; the stand's restore undoes it. */
    private static void holdPlayer() {
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite player)) return;
        player.setObjectControlled(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
    }

    /** Grants a monitor's power. */
    void grant(Pickup.Kind kind) {
        switch (kind) {
            case SHIELD -> { shield = true; play(Sounds.Cue.SHIELD); hud.banner("SHIELD!", 0xFF60C0FF); }
            case SPEED -> { slowRows = 600; hud.banner("SLOW MOTION!", 0xFF60C0FF); }
            case INVINCIBLE -> { invincibleRows = 600; playMusic(Sounds.Cue.INVINCIBLE_MUSIC); hud.banner("INVINCIBLE!", 0xFFFFD000); }
            case SUPER_RING -> { addRings(10); hud.banner("SUPER RING!", 0xFFFFD000); }
            case EXTRA_LIFE -> { lives++; playMusic(Sounds.Cue.EXTRA_LIFE_MUSIC); hud.banner("1-UP!", 0xFF60FF60); }
            case DECOY, RESCUE -> { }
        }
    }

    boolean invincible() { return invincibleRows > 0; }
    boolean shielded() { return shield; }
    int slowRows() { return slowRows; }

    /** A badnik reached Sonic. */
    void hitSonic() {
        if (invincibleRows > 0) return;
        if (shield) {
            shield = false;
            play(Sounds.Cue.HIT);
            hud.banner("SHIELD LOST", 0xFF60C0FF);
            return;
        }
        card.hitTaken();
        if (rings > 0) {
            int spill = Math.min(rings, 20);
            rings -= spill;
            if (encounter != null) encounter.effects().spill(this, spill);
            play(Sounds.Cue.RING_SPILL);
            hud.banner("OUCH!", 0xFFFF6060);
            return;
        }
        // No-Fail retries the stand without spending a life.
        if (!intent.noFail()) lives--;
        play(Sounds.Cue.HIT);
        enter(Phase.DYING);
        hud.banner(lives > 0 || intent.noFail() ? "TOO SLOW!" : "GAME OVER", 0xFFFF4040);
    }

    void addRings(int count) {
        rings += count;
        play(Sounds.Cue.RING);
    }

    /** Plays a cue through the running game's own sound driver (silent when the table lacks it). */
    void play(Sounds.Cue cue) {
        int id = assets.sounds(intent.game()).id(cue);
        if (id >= 0) GameServices.audio().playSfx(id);
    }

    void playMusic(Sounds.Cue cue) {
        int id = assets.sounds(intent.game()).id(cue);
        if (id >= 0) GameServices.audio().playMusic(id);
    }

    // ---- a read-only view for tests, captures and debugging ----------------------------------------

    /** One target on screen: its typed form, progress and whether it is locked. */
    public record TargetView(int id, String typed, int progress, boolean locked) { }

    /** The course at a glance; {@code railRow} is the next program row the rail will play. */
    public record View(String phase, int railRow, int nextStand, int rings, int lives, long score, int chain,
                       int kills, double wpm, java.util.List<TargetView> targets, int visuals, boolean paused) { }

    public View view() {
        java.util.List<TargetView> targets = new java.util.ArrayList<>();
        if (field != null) {
            for (var target : field.targets()) {
                targets.add(new TargetView(target.id(), target.prompt().typed(), target.progress(),
                        field.locked() == target.id()));
            }
        }
        return new View(phase.name(), cursor == null ? -1 : cursor.row(), nextStand, rings, lives,
                card == null ? 0 : card.score(), card == null ? 0 : card.chain(), card == null ? 0 : card.kills(),
                card == null ? 0 : card.wpm(), java.util.List.copyOf(targets), stagehand.all().size(), paused);
    }

    // ---- views for the encounter and HUD -------------------------------------------------------

    void spawnPuppets() {
        var services = GameServices.level().getObjectManager().getObjectServices();
        for (var visual : stagehand.takeUnpuppeted()) Puppet.spawn(services, visual, stagehand.generation());
    }

    Stagehand stagehand() { return stagehand; }
    CourseAssets assets() { return assets; }
    TypingField field() { return field; }
    Scorecard card() { return card; }
    LaunchIntent intent() { return intent; }
    SplittableRandom random() { return random; }
    typinghog.core.Lexicon.Picker picker() { return picker; }
    KeyStats keyStats() { return keyStats; }
    Hud hud() { return hud; }
    Encounter encounter() { return encounter; }
    RunTags runTags() { return runTags; }
    int rings() { return rings; }
    int lives() { return lives; }
    long rows() { return rows; }
    boolean paused() { return paused; }
    Phase phase() { return phase; }
    int phaseRows() { return phaseRows; }

    /** Sonic's centre in world pixels. */
    int playerX() { return GameServices.camera().getFocusedSprite() == null ? 0 : GameServices.camera().getFocusedSprite().getCentreX(); }
    int playerY() { return GameServices.camera().getFocusedSprite() == null ? 0 : GameServices.camera().getFocusedSprite().getCentreY(); }
}
