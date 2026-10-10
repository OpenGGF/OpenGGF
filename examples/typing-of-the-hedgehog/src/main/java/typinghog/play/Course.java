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
import typinghog.audio.Announcer;
import typinghog.audio.VoiceLine;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;
import typinghog.rails.CombatHandoff;
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
    enum Phase { IDLE, ENTRY, COUNTDOWN, RUN, STAND, STAND_CLEAR, BOSS, DYING, TALLY, LEAVING, FAULT }

    /** Native rows each count of the countdown spans (the rail's 180 charge rows in three). */
    static final int FORWARD_COUNTDOWN_BEAT = Rail.COUNTDOWN_ROWS / 3;
    /**
     * Native rows a forward rail's live fight may run before the world holds still: the fight goes
     * on (combat clock) on held rows until victory. The rail builder proves this many rows safe from
     * the stand's conversion with Sonic held, so a fight never outlives its validation.
     */
    static final int FIGHT_NATIVE_CAP = 3600;
    /** Held rows a forward rail waits at its countdown row for the act's presentation to finish. */
    static final int ENTRY_HOLD_LIMIT = 600;
    /** Native unload rows after retiring the stock boss, before the world holds. */
    static final int BOSS_RETIRE_ROWS = 2;
    /** Rows a rail fault shows before the course hands back to the hub. */
    static final int FAULT_ROWS = 180;
    /** Most rows Boss Rush waits for Sonic to land before the boss arrives anyway. */
    static final int BOSS_RUSH_LANDING_ROWS = 600;
    /** Rows a demo word must be on screen before the demo typist starts it. */
    static final int DEMO_REACTION_ROWS = 20;

    static final int START_RINGS = 10;
    /** Rows of invincibility after a No-Fail knockout. */
    static final int NO_FAIL_GRACE_ROWS = 120;
    static final int START_LIVES = 3;
    static final int STAND_CLEAR_ROWS = 50;
    static final int DYING_ROWS = 120;
    /** Directions and jump: the rail's pad bits ({@code AbstractPlayableSprite.INPUT_*}). */
    static final int PAD_BITS = 0x1F;

    private final ModStorage storage;
    private final CourseAssets assets;
    private final Stagehand stagehand;
    private final Homing homing = new Homing();
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
    /** The camera's frozen flag at the stand's capture: the restore puts the captured value back. */
    private boolean standCameraFrozen;
    /** A forward stand's converted native bodies, reacquired and converted again after each restore. */
    private java.util.List<CombatHandoff.Body> standPool = java.util.List.of();
    private java.util.List<LoadedBadnik> standLoaded = java.util.List.of();
    /** Native rows the current forward fight has run (the world holds still past the cap). */
    private int fightNativeRows;
    /** This row advanced the fight on the combat clock: a native row, or a held one past the cap. */
    private boolean combatTick;
    private boolean fightHeld;
    private int entryHoldRows;
    /** Why a forward rail stopped (a native fault or a mismatch), or null. */
    private String fault;
    private boolean paused;
    private boolean titleRequest;
    private boolean advancedThisRow;
    private boolean bossDefeated;
    private int bossArenaX;
    private boolean shield;
    private int slowRows;
    private int invincibleRows;
    private int slowPhase;
    private long rows;
    private SplittableRandom random;
    private SplittableRandom demoRandom;
    private int demoWait;
    private long demoStartRow;
    /** Row each target was first seen on screen, for the demo typist's reaction delay. */
    private final java.util.Map<Integer, Long> demoSeen = new java.util.HashMap<>();
    private typinghog.core.Lexicon.Picker picker;
    private final KeyStats keyStats = new KeyStats();
    private final Announcer announcer = new Announcer();
    private int lastChainCall;

    public Course(ModStorage storage, CourseAssets assets, Stagehand stagehand) {
        this.storage = storage;
        this.assets = assets;
        this.stagehand = stagehand;
        this.hud = new Hud(assets);
    }

    // ---- frame controller ------------------------------------------------------------------

    @Override public boolean beforeTick(CourseControl course, LogicalInputSnapshot live) {
        rows++;
        combatTick = false;
        fightHeld = false;
        detectLevel();
        if (intent == null || rail == null) return true;
        announcer.update(rows, asset -> GameServices.audio().playNamespacedSfx(
                new com.openggf.audio.StreamedMusicPort.SfxRef(TypingIds.MOD, asset)));
        if (phase == Phase.IDLE) enter(Phase.ENTRY);
        String typed = course.typedText();
        boolean backspace = course.keyPressed(GLFW_KEY_BACKSPACE);
        if (intent.demo()) {
            // An attract demo ends on any key (or after its demoRows, 0 for none) and records nothing.
            if (phase == Phase.LEAVING) return false;
            if (!typed.isEmpty() || backspace || course.keyPressed(GLFW_KEY_ENTER) || course.keyPressed(GLFW_KEY_ESCAPE)
                    || (intent.demoRows() > 0 && rows - demoStartRow > intent.demoRows())) {
                enter(Phase.LEAVING);
                titleRequest = true;
                return false;
            }
            typed = "";
        }
        if (course.keyPressed(GLFW_KEY_ESCAPE) && phase != Phase.TALLY && phase != Phase.LEAVING) paused = !paused;
        if (paused) {
            hud.pauseInput(this, typed, course.keyPressed(GLFW_KEY_ENTER), course.keyPressed(GLFW_KEY_UP),
                    course.keyPressed(GLFW_KEY_DOWN));
            return false;
        }
        phaseRows++;
        typeRow(typed + demoTyped(), backspace);
        if (invincibleRows > 0) invincibleRows--;
        if (slowRows > 0 || intent.slowMotion()) {
            if (slowRows > 0) slowRows--;
            // Slow motion advances the native world (and everything on it) every other row; the
            // run is the same sequence of native steps, so it stays deterministic.
            if (phase == Phase.RUN || phase == Phase.STAND || phase == Phase.BOSS || phase == Phase.COUNTDOWN) {
                if ((++slowPhase & 1) == 0) return false;
            }
        }
        advancedThisRow = switch (phase) {
            case ENTRY -> entry(course);
            case COUNTDOWN -> forwardCountdown(course);
            case RUN -> forwardRun(course);
            case STAND, BOSS -> stand(course);
            case STAND_CLEAR -> standClear(course);
            case DYING -> dying(course);
            case TALLY -> tally();
            case FAULT -> faulted();
            case LEAVING, IDLE -> false;
        };
        combatTick = advancedThisRow || fightHeld;
        return advancedThisRow;
    }

    @Override public void afterTick(CourseControl course, boolean advanced) {
        if (intent == null || rail == null) return;
        // The wave holds while Sonic is down. A fight runs on the combat clock, which keeps going on
        // the rows the world holds still past the cap.
        if (combatTick && encounter != null && phase != Phase.DYING) encounter.advance(this);
        if (advanced) watchNative();
        spawnPuppets();
    }

    @Override public LogicalInputSnapshot nativeInput(CourseControl course) {
        boolean railed = (phase == Phase.RUN || phase == Phase.ENTRY || phase == Phase.COUNTDOWN)
                && intent.mode() != LaunchIntent.Mode.BOSS_RUSH;
        int mask = railed ? cursor.next() & PAD_BITS : 0;
        int action = (mask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
        int previousAction = (previousMask & AbstractPlayableSprite.INPUT_JUMP) != 0 ? InputActionMasks.ACTION_A : 0;
        var p1 = PlayerInputState.of(mask, mask & ~previousMask, action, action & ~previousAction, false, false);
        previousMask = mask;
        return LogicalInputSnapshot.ofPlayers(p1, PlayerInputState.neutral());
    }

    @Override public boolean capturesKeyboardText() { return intent != null; }
    @Override public boolean allowsDebugRewind() { return false; }
    @Override public boolean retainRolling() { return homing.rolling(); }
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
        if (intent == null || rail == null) return;
        cursor = new Rail.Cursor(rail);
        nextStand = 0;
        field = new TypingField(intent.capitals());
        card = new Scorecard();
        random = new SplittableRandom(intent.seed());
        demoRandom = new SplittableRandom(intent.seed() ^ 0xDE30L);
        demoWait = 30;
        demoStartRow = rows;
        demoSeen.clear();
        picker = assets.lexicon().new Picker(intent.seed() ^ 0x5EED, 48);
        keyStats.clear();
        announcer.clear();
        announcer.mode(Announcer.Mode.parse(intent.voice().name()));
        lastChainCall = 0;
        encounter = null;
        rings = START_RINGS;
        lives = START_LIVES;
        previousMask = 0;
        standCheckpoint = null;
        standPool = java.util.List.of();
        standLoaded = java.util.List.of();
        fightNativeRows = 0;
        entryHoldRows = 0;
        fault = null;
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
        hud.ready(next == Phase.RUN);
    }

    private boolean entry(CourseControl course) {
        if (intent.mode() != LaunchIntent.Mode.BOSS_RUSH) return forwardEntry(course);
        // Boss Rush skips the run: the boss arrives where the act begins, once presentation is
        // ready and Sonic has landed (some acts drop him in), with the pad left alone meanwhile.
        if (!course.presentationReady()) return true;
        var player = GameServices.camera().getFocusedSprite();
        if (player != null && player.getAir() && phaseRows < BOSS_RUSH_LANDING_ROWS) return true;
        beginStand(course, true);
        return stand(course);
    }

    /**
     * A demo intent's typist plays like a quick human, so a demo shows the badniks: about sixty
     * words a minute, and a new word only once its badnik has been on screen for a third of a
     * second (the locked word's next letter, else the boss quiz's answer, else the most urgent
     * word). It draws on its own random stream so a demo plays the same course a player would.
     */
    private String demoTyped() {
        if (!intent.demo() || field == null || demoRandom == null) return "";
        if (encounter != null) {
            for (TypingField.Target t : field.targets()) {
                if (encounter.onScreen(t.id())) demoSeen.putIfAbsent(t.id(), rows);
                else demoSeen.remove(t.id());
            }
        }
        if (--demoWait > 0) return "";
        demoWait = 9 + demoRandom.nextInt(7);
        TypingField.Target target = field.locked() >= 0 ? field.target(field.locked()) : null;
        int preferred = encounter == null ? -1 : encounter.preferredTarget();
        if (target == null && preferred >= 0) target = field.target(preferred);
        if (target == null) {
            target = field.targets().stream().filter(TypingField.Target::typeable)
                    .filter(t -> encounter == null || rows - demoSeen.getOrDefault(t.id(), rows) >= DEMO_REACTION_ROWS)
                    .min(java.util.Comparator.comparingInt(TypingField.Target::urgency)).orElse(null);
        }
        if (target == null) return "";
        String text = target.prompt().typed();
        int progress = target.progress();
        return progress < text.length() ? new String(Character.toChars(text.codePointAt(progress))) : "";
    }

    /** Types one key into the field and lights it on Sonic's keyboard (gold right, red wrong). */
    private TypingField.Event typeKey(int codePoint) {
        TypingField.Event event = field.type(codePoint);
        hud.keyPressed(codePoint, event.kind() != TypingField.Kind.ERROR);
        return event;
    }

    /** Applies the row's typing to whatever is on screen: run tags while running, the stand's targets in a stand. */
    private void typeRow(String typed, boolean backspace) {
        // Between fights there is nothing to type: Sonic leaves the badniks alone.
        if ((phase == Phase.STAND || phase == Phase.BOSS) && encounter != null) {
            if (backspace) encounter.onRelease(this, field.release());
            typed.codePoints().forEach(cp -> encounter.onEvent(this, typeKey(cp), cp));
        } else {
            return;
        }
        if (!field.targets().isEmpty()) card.activeRow();
    }

    private void beginStand(CourseControl course, boolean boss) {
        standCheckpoint = course.capture();
        standPreviousMask = previousMask;
        standCameraFrozen = GameServices.camera().getFrozen();
        if (boss) {
            var camera = GameServices.camera();
            // Decoded maxX and the current arena maxX are native camera origins (+320px art).
            // The typing stage owns a full viewport, rather than the retired native boss's walls.
            // Keep its right edge inside that decoded domain, widening the mod's left wall as needed.
            bossArenaX = Math.max(GameServices.level().getCurrentLevel().getMinX(),
                    Math.min(camera.getX(), Math.min(camera.getMaxX(), course.courseMaxX())
                            + 320 - camera.getWidth()));
            frameBossArena();
            CombatHandoff.retireBoss();
        }
        enter(boss ? Phase.BOSS : Phase.STAND);
        encounter = boss ? Encounter.boss(this)
                : intent.mode() == LaunchIntent.Mode.ENDLESS ? Encounter.endless(this)
                : Encounter.stand(this, nextStand, standLoaded);
        field.clear();
        hud.banner(boss ? "WARNING!" : "FIGHT " + nextStand, boss ? 0xFFFF4040 : 0xFFFFD000);
        play(Sounds.Cue.STAND_START);
        if (boss) {
            playMusic(Sounds.Cue.BOSS_MUSIC);
            say(VoiceLine.ANNOUNCER_BOSS_WARNING);
        } else {
            VoiceLine[] calls = {VoiceLine.ANNOUNCER_STAND_GROUND, VoiceLine.ANNOUNCER_HERE_THEY_COME, VoiceLine.ANNOUNCER_INCOMING};
            say(calls[random.nextInt(calls.length)]);
        }
    }

    private boolean stand(CourseControl course) {
        if (!guardFightWorld(course)) return false;
        if (phase == Phase.BOSS) frameBossArena();
        holdPlayer();
        homing.anchor(playerX(), playerY());
        // The view holds still for the stand, so homing strikes don't drag the camera around;
        // the stand's restore returns the camera to the rail.
        GameServices.camera().setFrozen(true);
        homing.advance();
        if (encounter.finished() && !homing.busy()) {
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
            say(VoiceLine.ANNOUNCER_WAVE_CLEAR);
        }
        return fightAdvances();
    }

    /** Mod-only bounds; the captured native camera/walls return on any course restore. */
    private void frameBossArena() {
        var camera = GameServices.camera();
        camera.setX((short) bossArenaX);
        camera.setMinX((short) bossArenaX);
        camera.setMaxX((short) (bossArenaX + camera.getWidth() - 320));
    }

    /**
     * Whether a fight row steps the native world. A fight's world lives (Sonic held) for
     * {@link #FIGHT_NATIVE_CAP} native rows, then holds still while the fight goes on; the victory
     * restore undoes all of it either way.
     */
    private boolean fightAdvances() {
        // A forward endpoint needs two native unload rows after retiring its stock boss.
        // Boss Rush stages at act start and holds immediately: its mod camera walls must
        // not run native end-of-act events while startup PLC decoding is still active.
        boolean boss = phase == Phase.BOSS || (encounter != null && encounter.isBoss());
        int bossNativeCap = intent.mode() == LaunchIntent.Mode.BOSS_RUSH ? 0 : BOSS_RETIRE_ROWS;
        if ((boss && fightNativeRows >= bossNativeCap) || fightNativeRows >= FIGHT_NATIVE_CAP) {
            fightHeld = true;
            return false;
        }
        fightNativeRows++;
        return true;
    }

    private boolean standClear(CourseControl course) {
        if (!guardFightWorld(course)) return false;
        holdPlayer();
        if (phaseRows < STAND_CLEAR_ROWS) return fightAdvances();
        course.restore(standCheckpoint);
        homing.release();
        GameServices.camera().setFrozen(standCameraFrozen);
        if (!reconvert("after stand " + nextStand + "'s victory")) return false;
        previousMask = standPreviousMask;
        stagehand.clear();
        stagehand.invalidatePuppets();
        encounter = null;
        hud.flash(0xFFFFFFFF, 12);
        hud.banner("GO!", 0xFFFFFFFF);
        say(VoiceLine.ANNOUNCER_GO_GO_GO);
        enter(Phase.RUN);
        return true;
    }

    private boolean dying(CourseControl course) {
        if (phaseRows < DYING_ROWS) return fightAdvances();
        if (lives <= 0 && !intent.noFail()) {
            finish(false);
            return false;
        }
        course.restore(standCheckpoint);
        homing.release();
        GameServices.camera().setFrozen(standCameraFrozen);
        if (!reconvert("for stand " + nextStand + "'s retry")) return false;
        if (encounter != null && encounter.isBoss()) CombatHandoff.retireBoss();
        fightNativeRows = 0;
        previousMask = standPreviousMask;
        stagehand.clear();
        stagehand.invalidatePuppets();
        rings = START_RINGS;
        field.clear();
        boolean boss = encounter != null && encounter.isBoss();
        enter(boss ? Phase.BOSS : Phase.STAND);
        encounter = boss ? Encounter.boss(this) : Encounter.stand(this, nextStand, standLoaded);
        hud.banner("TRY AGAIN!", 0xFFFFFFFF);
        return true;
    }

    // ---- forward rails (format 2) ----------------------------------------------------------------

    /**
     * Plays the rail's authored entry rows; at the countdown row, holds the world (no native step,
     * so neither the rail nor the native clocks move) until the act's presentation is ready.
     */
    private boolean forwardEntry(CourseControl course) {
        if (cursor.row() == 0 && phaseRows == 1) {
            String launchFault = launchFault(course);
            if (launchFault != null) return fault(launchFault);
        }
        if (cursor.row() < rail.plan().countdownRow()) return true;
        if (!course.presentationReady()) {
            if (++entryHoldRows > ENTRY_HOLD_LIMIT) return fault("the act's presentation was not ready by the countdown row");
            return false;
        }
        enter(Phase.COUNTDOWN);
        say(VoiceLine.ANNOUNCER_GET_READY);
        return forwardCountdown(course);
    }

    /** The rail's launch profile against the running session; a description of the mismatch, or null. */
    private String launchFault(CourseControl course) {
        String sha1 = course.romSha1();
        if (sha1 != null && !sha1.equalsIgnoreCase(rail.plan().romSha1())) return "this rail was made for another ROM";
        String intros = rail.plan().profile().get("intros");
        if (intros != null && intent.game().equals("s3k")) {
            boolean skip = GameServices.configuration().getBoolean(com.openggf.configuration.SonicConfiguration.S3K_SKIP_INTROS);
            if (skip != intros.equals("skip")) return "this rail needs act intros " + (intros.equals("skip") ? "skipped" : "played");
        }
        String width = rail.plan().profile().get("width");
        if (width != null && course.viewportWidth() != Integer.parseInt(width)) return "this rail needs a " + width + "-pixel view";
        return null;
    }

    /**
     * "3, 2, 1, START!": S1 runs in place under object control; S2 charges its native spindash.
     */
    private boolean forwardCountdown(CourseControl course) {
        int i = cursor.row() - rail.plan().countdownRow();
        boolean heldRun = rail.plan().launch().equals("held-run");
        if (i == 0 || i == FORWARD_COUNTDOWN_BEAT || i == 2 * FORWARD_COUNTDOWN_BEAT) {
            play(heldRun ? Sounds.Cue.REV : Sounds.Cue.LOCK);
        }
        if (i < Rail.COUNTDOWN_ROWS) {
            if (heldRun) typinghog.rails.Launch.holdRun(rail.plan().burst());
            return true;
        }
        if (heldRun) typinghog.rails.Launch.release(rail.plan().burst());
        play(Sounds.Cue.STAND_START);
        hud.start();
        enter(Phase.RUN);
        return forwardRun(course);
    }

    private boolean forwardRun(CourseControl course) {
        var plan = rail.plan();
        int row = cursor.row();
        if (row >= plan.endpoint().row()) {
            standPool = java.util.List.of();
            standLoaded = java.util.List.of();
            fightNativeRows = 0;
            beginStand(course, true);
            return stand(course);
        }
        if (nextStand < plan.stands().size() && row >= plan.stands().get(nextStand).row()) {
            Rail.Stand spec = plan.stands().get(nextStand);
            if (row > spec.row()) return fault("passed stand " + spec.id() + "'s row " + spec.row());
            nextStand++;
            var pool = CombatHandoff.pool(assets.cast(intent.game())::convertible);
            String mismatch = poolMismatch(spec.pool(), pool);
            if (mismatch != null) return fault("stand " + spec.id() + " at row " + row + ": " + mismatch);
            standPool = pool;
            standLoaded = pool.stream().map(b -> new LoadedBadnik(b.className(), b.x(), b.y(), b.facingRight())).toList();
            fightNativeRows = 0;
            // The checkpoint is the world before the conversion (beginStand captures it); the
            // conversion then takes effect on this row's native step.
            beginStand(course, false);
            CombatHandoff.convert(pool);
            return stand(course);
        }
        if (cursor.finished()) return fault("the rail ended before its endpoint");
        return true;
    }

    /** The live pool against the rail's manifest, by class and layout (slots may differ with the clock phase). */
    static String poolMismatch(java.util.List<Rail.Body> expected, java.util.List<CombatHandoff.Body> live) {
        java.util.List<String> want = new java.util.ArrayList<>(expected.stream().map(Rail.Body::key).toList());
        java.util.List<String> got = new java.util.ArrayList<>(live.stream()
                .map(b -> b.className() + "/L" + b.layoutIndex()).toList());
        java.util.Collections.sort(want);
        java.util.Collections.sort(got);
        return want.equals(got) ? null : "the rail expected loaded badniks " + want + " but found " + got;
    }

    /**
     * A forward fight's living world must not touch the held Sonic: the rail builder soaks every
     * stand for {@link #FIGHT_NATIVE_CAP} held rows, but a homing strike moves him through the
     * world. Should a native hazard still hurt (or kill) him, the stand's checkpoint goes back at
     * once, with the same conversion, and the fight carries on; false only when that fails.
     */
    private boolean guardFightWorld(CourseControl course) {
        if (standCheckpoint == null) return true;
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic)) return true;
        if (!sonic.getDead() && !sonic.isHurt()) return true;
        course.restore(standCheckpoint);
        GameServices.camera().setFrozen(standCameraFrozen);
        fightNativeRows = 0;
        return reconvert("after a native hit in stand " + nextStand);
    }

    /** After a stand's restore: the same bodies, by identity, converted again. */
    private boolean reconvert(String when) {
        try {
            CombatHandoff.convert(standPool);
            return true;
        } catch (CombatHandoff.Mismatch mismatch) {
            fault("the loaded badniks differ " + when + ": " + mismatch.getMessage());
            return false;
        }
    }

    /** A forward rail's native rows outside fights must be clean: a hurt, death or level change is a rail fault. */
    private void watchNative() {
        if (phase != Phase.RUN && phase != Phase.COUNTDOWN && phase != Phase.ENTRY) return;
        if (!(GameServices.camera().getFocusedSprite() instanceof AbstractPlayableSprite sonic)) return;
        int row = cursor.row() - 1;
        if (sonic.getDead()) fault("native death at rail row " + row);
        else if (sonic.isHurt()) fault("native hurt at rail row " + row);
        else if (GameServices.level().getCurrentZone() != intent.zone() || GameServices.level().getCurrentAct() != intent.act()) {
            fault("the level changed at rail row " + row);
        }
    }

    /** Stops the rail: shows the fault, then hands back to the hub. */
    private boolean fault(String reason) {
        if (fault == null) fault = reason;
        homing.release();
        enter(Phase.FAULT);
        hud.banner("RAIL FAULT", 0xFFFF4040);
        return false;
    }

    private boolean faulted() {
        if (phaseRows >= FAULT_ROWS) finish(false);
        return false;
    }

    /** The countdown beat (0 to 2) and rows into it, or null outside the countdown. */
    int[] countdownBeat() {
        if (phase != Phase.COUNTDOWN) return null;
        int i = Math.max(0, cursor.row() - rail.plan().countdownRow());
        return new int[] {i / FORWARD_COUNTDOWN_BEAT, i % FORWARD_COUNTDOWN_BEAT};
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
        if (!intent.demo()) storage.write(ActResult.FILE, result.encode());
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
            case SHIELD -> { shield = true; play(Sounds.Cue.SHIELD); hud.banner("SHIELD!", 0xFF60C0FF); say(VoiceLine.ANNOUNCER_SHIELD); }
            case SPEED -> { slowRows = 600; hud.banner("SLOW MOTION!", 0xFF60C0FF); say(VoiceLine.ANNOUNCER_SPEED_SHOES); }
            case INVINCIBLE -> { invincibleRows = 600; playMusic(Sounds.Cue.INVINCIBLE_MUSIC); hud.banner("INVINCIBLE!", 0xFFFFD000); say(VoiceLine.ANNOUNCER_INVINCIBLE); }
            case SUPER_RING -> { addRings(10); hud.banner("SUPER RING!", 0xFFFFD000); say(VoiceLine.ANNOUNCER_SUPER_RING); }
            case EXTRA_LIFE -> { lives++; playMusic(Sounds.Cue.EXTRA_LIFE_MUSIC); hud.banner("1-UP!", 0xFF60FF60); say(VoiceLine.ANNOUNCER_EXTRA_LIFE); }
            case DECOY, RESCUE -> { }
        }
    }

    boolean invincible() { return invincibleRows > 0; }
    boolean shielded() { return shield; }
    int slowRows() { return slowRows; }

    /** A badnik reached Sonic. */
    void hitSonic() {
        // Only a fighting Sonic can be hit: a hit while he is already down restarted DYING's
        // countdown, so a crowd that kept reaching him held him down for good.
        if (invincibleRows > 0 || (phase != Phase.STAND && phase != Phase.BOSS && phase != Phase.RUN)) return;
        homing.cancel();
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
            say(rings == 0 ? VoiceLine.ANNOUNCER_NO_RINGS : random.nextBoolean() ? VoiceLine.ANNOUNCER_OUCH : VoiceLine.ANNOUNCER_RINGS_LOST);
            hud.banner("OUCH!", 0xFFFF6060);
            return;
        }
        if (intent.noFail()) {
            // No-Fail: Sonic shrugs the knockout off, rings refilled and briefly invincible, and
            // the fight goes on. Restarting the whole wave on every knockout made fights loop for
            // minutes (EHZ2 forward capture: one fight ran almost four minutes).
            rings = START_RINGS;
            invincibleRows = NO_FAIL_GRACE_ROWS;
            play(Sounds.Cue.HIT);
            hud.banner("TOO SLOW!", 0xFFFF4040);
            say(VoiceLine.ANNOUNCER_OUCH);
            return;
        }
        lives--;
        play(Sounds.Cue.HIT);
        enter(Phase.DYING);
        hud.banner(lives > 0 ? "TOO SLOW!" : "GAME OVER", 0xFFFF4040);
        say(lives <= 0 ? VoiceLine.ANNOUNCER_GAME_OVER : lives == 1 ? VoiceLine.ANNOUNCER_LAST_LIFE
                : VoiceLine.ANNOUNCER_OUCH);
    }

    /** Queues a voice line through the shared announcer (silent when voice is off). */
    void say(VoiceLine line) {
        if (line != null) announcer.say(line.clip());
    }

    /** Calls chain milestones once each as the chain climbs. */
    void chainMilestone(int chain) {
        int[] marks = {10, 20, 30, 50};
        VoiceLine[] calls = {VoiceLine.ANNOUNCER_CHAIN_TEN, VoiceLine.ANNOUNCER_CHAIN_TWENTY,
                VoiceLine.ANNOUNCER_CHAIN_THIRTY, VoiceLine.ANNOUNCER_CHAIN_FIFTY};
        if (chain < lastChainCall) lastChainCall = 0;
        for (int i = 0; i < marks.length; i++) {
            if (chain >= marks[i] && lastChainCall < marks[i]) {
                lastChainCall = marks[i];
                say(calls[i]);
            }
        }
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
    public record TargetView(int id, String typed, int progress, boolean locked, boolean typeable) { }

    /** The course at a glance; {@code railRow} is the next program row the rail will play. */
    public record View(String phase, int railRow, int nextStand, int rings, int lives, long score, int chain,
                       int kills, double wpm, java.util.List<TargetView> targets, int visuals, int sprites,
                       boolean paused, int fightNativeRows, int loaded, String fault) { }

    public View view() {
        java.util.List<TargetView> targets = new java.util.ArrayList<>();
        if (field != null) {
            for (var target : field.targets()) {
                targets.add(new TargetView(target.id(), target.prompt().typed(), target.progress(),
                        field.locked() == target.id(), target.typeable()));
            }
        }
        return new View(phase.name(), cursor == null ? -1 : cursor.row(), nextStand, rings, lives,
                card == null ? 0 : card.score(), card == null ? 0 : card.chain(), card == null ? 0 : card.kills(),
                card == null ? 0 : card.wpm(), java.util.List.copyOf(targets), stagehand.all().size(),
                stagehand.all().stream().mapToInt(v -> v.sprites.isEmpty() ? 1 : v.sprites.size()).sum(), paused,
                fightNativeRows, standLoaded.size(), fault);
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
    int rings() { return rings; }
    int lives() { return lives; }
    long rows() { return rows; }
    boolean paused() { return paused; }
    Phase phase() { return phase; }
    int phaseRows() { return phaseRows; }

    /** Sonic's centre in world pixels. */
    int playerX() { return GameServices.camera().getFocusedSprite() == null ? 0 : GameServices.camera().getFocusedSprite().getCentreX(); }
    /** Where badniks and the boss aim: Sonic's stand spot while he is away on a homing strike. */
    int anchorX() { return homing.anchored() ? homing.homeX() : playerX(); }
    int anchorY() { return homing.anchored() ? homing.homeY() : playerY(); }
    Homing homing() { return homing; }
    int playerY() { return GameServices.camera().getFocusedSprite() == null ? 0 : GameServices.camera().getFocusedSprite().getCentreY(); }
}
