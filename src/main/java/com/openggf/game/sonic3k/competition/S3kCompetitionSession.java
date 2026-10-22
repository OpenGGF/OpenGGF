package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.managers.SpriteManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * World-owned native competition state. Objects report native events here; only
 * this owner asks the host to change arena or show results.
 *
 * <p>ROM owners: Obj_2PGoalMarker/sub_36DFC (sonic3k.asm:73950-74207),
 * sub_36FAA (74259-74287), sub_371BE (74394-74616), Sonic_ChkShoesCompetition
 * (22097-22109). All state belongs to one competition world, including P2.
 */
public final class S3kCompetitionSession implements WorldSessionPolicyState,
        RewindSnapshottable<S3kCompetitionSession.State> {
    public static final int TOTAL_LAPS = 5;
    public static final int FALSE_START_TICKS = 120;
    public static final int ITEM_SEQUENCE_ADDRESS = 0x36FDA;
    private static final int MAX_TIME = 9 * 60 * 100 + 59 * 100 + 99;
    private static final int SPEED_SHOES = 1 << 2;

    public enum Mode {
        GRAND_PRIX(0), MATCH_RACE(3), TIME_ATTACK(-1);
        private final int nativeValue;
        Mode(int nativeValue) { this.nativeValue = nativeValue; }
        public int nativeValue() { return nativeValue; }
        public static Mode fromNative(int value) {
            for (Mode mode : values()) if (mode.nativeValue == (byte) value) return mode;
            throw new IllegalArgumentException("Unsupported competition mode: " + value);
        }
    }

    public enum Arena {
        AZURE_LAKE(0x0E), BALLOON_PARK(0x0F), DESERT_PALACE(0x10),
        CHROME_GADGET(0x11), ENDLESS_MINE(0x12);
        private final int zoneId;
        Arena(int zoneId) { this.zoneId = zoneId; }
        public int zoneId() { return zoneId; }
        public static Arena forZone(int zoneId) {
            for (Arena arena : values()) if (arena.zoneId == zoneId) return arena;
            throw new IllegalArgumentException("Not a competition arena: " + zoneId);
        }
    }

    /** Both the menu order at Comp_ZoneList and GP byte_3739A follow this order. */
    public static final List<Arena> GRAND_PRIX_ORDER = List.of(Arena.AZURE_LAKE,
            Arena.BALLOON_PARK, Arena.CHROME_GADGET, Arena.DESERT_PALACE, Arena.ENDLESS_MINE);

    public record Config(Mode mode, Arena arena, boolean itemsEnabled, int participantCount) {
        public Config(Mode mode, Arena arena, boolean itemsEnabled) {
            this(mode, arena, itemsEnabled, mode == Mode.TIME_ATTACK ? 1 : 2);
        }
        public Config {
            Objects.requireNonNull(mode); Objects.requireNonNull(arena);
            if (participantCount < 1 || participantCount > 2 || mode == Mode.TIME_ATTACK && participantCount != 1)
                throw new IllegalArgumentException("Native competition has one or two humans; Time Attack has P1 only");
        }
    }

    /** Internal host boundary; no creator/public API or ordinary co-op boot. */
    public interface Host {
        List<S3kCompetitionPlayer> players();
        /** Live wheels in native dynamic-SST order, including empty wheels. */
        List<CompetitionItemObjectInstance> itemWheels();
        /** AllocateObjectAfterCurrent. Null means native object RAM is full. */
        AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
                Supplier<? extends AbstractObjectInstance> factory);
        /** Item 4 writes the reserved Breathing_bubbles SST even when dynamic RAM is full. */
        AbstractObjectInstance overwriteProjectile(Supplier<? extends AbstractObjectInstance> factory);
        CompetitionArenaObjectInstance.NativeContext nativeObjectContext();
        ItemTerrain itemTerrain();
        int xWrapMask();
        /** Raw Screen_Y_wrap_value; the rolling projectile applies it unconditionally. */
        int projectileYWrapMask();
        boolean playerHighPriority(int slot);
        int oscillation(int offset);
        void requestArena(Arena arena);
        void requestResults(Result result);
        /** sub_37430 runs immediately on a Time Attack finish, before the result-screen wait. */
        default void saveTimeAttackRecord(PlayerResult result, Arena arena) { }
        default NativeCompetitionRoster roster() {
            return this instanceof NativeCompetitionRoster roster ? roster : null;
        }
        /** Relink host-owned terrain/view adapters after SpriteManager recreates the native actors. */
        default void rebindPlayers(List<S3kCompetitionPlayer> players) { }
        default void playSfx(Sonic3kSfx sound) { }
    }

    public record ItemGround(int centreX, int centreY, int angle, boolean airborne) { }
    /** Production ROM terrain probes used by loc_366C4/sub_36926. */
    public interface ItemTerrain {
        int floorDistance(int centreX, int centreY, int yRadius, int topSolidBit);
        int leftWallDistance(int centreX, int centreY, int yRadius, int lrbSolidBit);
        ItemGround followGround(int centreX, int centreY, int xRadius, int yRadius,
                int angle, int groundVelocity, int topSolidBit, int lrbSolidBit);
    }

    public record PlayerResult(int slot, S3kCompetitionPlayer.Character character,
            int laps, List<Integer> lapCentiseconds, int totalCentiseconds,
            boolean finished, int rings) {
        public PlayerResult { lapCentiseconds = List.copyOf(lapCentiseconds); }
    }

    public record Result(Mode mode, Arena arena, List<PlayerResult> players,
            List<List<Integer>> grandPrixTimes) {
        public Result {
            players = List.copyOf(players);
            grandPrixTimes = grandPrixTimes.stream().map(List::copyOf).toList();
        }
        /** Equal native packed times remain a tie; player identity never breaks it. */
        public int winner() {
            if (players.size() == 1) return players.getFirst().finished() ? 0 : -1;
            if (mode == Mode.GRAND_PRIX) {
                int wins0 = arenaWins(0), wins1 = arenaWins(1);
                return wins0 == wins1 ? -1 : wins0 > wins1 ? 0 : 1;
            }
            int a = players.get(0).finished() ? players.get(0).totalCentiseconds() : MAX_TIME;
            int b = players.get(1).finished() ? players.get(1).totalCentiseconds() : MAX_TIME;
            return a == b ? -1 : a < b ? 0 : 1;
        }
        /** sub_BA04 awards one arena win, and awards neither player a tie. */
        public int arenaWins(int slot) {
            int wins = 0;
            for (int i = 0; i < 5; i++) if (grandPrixTimes.get(slot).get(i)
                    < grandPrixTimes.get(slot ^ 1).get(i)) wins++;
            return wins;
        }
        public int grandPrixTotalCentiseconds(int slot) {
            return Math.min(99 * 6000 + 59 * 100 + 99,
                    grandPrixTimes.get(slot).stream().mapToInt(Integer::intValue).sum());
        }
    }

    public record PlayerState(S3kCompetitionPlayer.State body, int lap, int highestLap,
            boolean rightOfGoal, boolean respawnPending, boolean timerRunning,
            boolean finished, int lapFrames, int completedCentiseconds,
            List<Integer> lapTimes, int rings) {
        public PlayerState { lapTimes = List.copyOf(lapTimes); }
    }
    public record StartPosition(int centreX, int centreY, int topSolidBit, int lrbSolidBit, boolean highPriority) { }

    public record State(Arena arena, int levelFrameCounter, boolean go, int falseStartTicks, int falseStartFrame,
            int falseStarter, int replenishmentOffset, int finishWait, boolean resultSent,
            List<PlayerState> players, List<StartPosition> starts, List<Integer> triggers,
            List<List<Integer>> grandPrixTimes, int goalTimerMask) {
        public State {
            players = List.copyOf(players);
            starts = List.copyOf(starts);
            triggers = List.copyOf(triggers);
            grandPrixTimes = grandPrixTimes.stream().map(List::copyOf).toList();
        }
    }

    private final Config config;
    private final Host host;
    private final byte[] itemSequence;
    private final byte[] itemAnimation;
    private final byte[] goalAnimation;
    private List<S3kCompetitionPlayer> players;
    private List<StartPosition> starts;
    private final int[] laps = new int[2];
    private final int[] highestLaps = new int[2];
    private final boolean[] rightOfGoal = new boolean[2];
    private final boolean[] respawnPending = new boolean[2];
    private final boolean[] timerRunning = new boolean[2];
    private final boolean[] finished = new boolean[2];
    private final int[] lapFrames = new int[2];
    private final int[] completedCentiseconds = new int[2];
    private final int[][] lapTimes = new int[2][TOTAL_LAPS];
    private final int[] rings = new int[2];
    private final int[] triggers = new int[16];
    private final int[][] grandPrixTimes = new int[2][5];
    private Arena arena;
    private int levelFrameCounter;
    private boolean go;
    private int falseStartTicks;
    private int falseStartFrame;
    private int falseStarter = -1;
    private int replenishmentOffset;
    private int finishWait = -1;
    private boolean resultSent;
    private int goalTimerMask = -1;

    public S3kCompetitionSession(Rom rom, Config config, Host host) throws IOException {
        this(config, host, rom.readBytes(ITEM_SEQUENCE_ADDRESS, 24),
                rom.readBytes(0x36A58, 80), rom.readBytes(0x36FF2, 8));
    }

    /** Parsed ROM bytes are injectable for a bounded independent native oracle. */
    public S3kCompetitionSession(Config config, Host host, byte[] itemSequence) {
        this(config, host, itemSequence, new byte[0], new byte[0]);
    }

    public S3kCompetitionSession(Config config, Host host, byte[] itemSequence,
            byte[] itemAnimation, byte[] goalAnimation) {
        this.config = Objects.requireNonNull(config);
        this.host = Objects.requireNonNull(host);
        this.arena = config.arena();
        this.itemSequence = itemSequence.clone();
        this.itemAnimation = itemAnimation.clone();
        this.goalAnimation = goalAnimation.clone();
        if (this.itemSequence.length != 24) throw new IllegalArgumentException("24 ROM item bytes required");
        for (byte value : this.itemSequence) if ((value & 0xFF) > 6)
            throw new IllegalArgumentException("Invalid ROM item sequence");
        this.players = List.copyOf(host.players());
        int expected = config.participantCount();
        if (players.size() != expected) throw new IllegalArgumentException("Native mode requires " + expected + " players");
        for (int slot = 0; slot < expected; slot++) if (players.get(slot).slot() != slot)
            throw new IllegalArgumentException("Participants must occupy native P1/P2 order");
        this.starts = saveStartPositions();
        for (int[] times : grandPrixTimes) Arrays.fill(times, MAX_TIME);
    }

    public Config config() { return config; }
    public Arena arena() { return arena; }
    public List<S3kCompetitionPlayer> players() { return players; }
    public S3kCompetitionPlayer player(int slot) { return players.get(slot); }
    public int levelFrameCounter() { return levelFrameCounter; }
    public int oscillation(int offset) { return host.oscillation(offset) & 0xFF; }
    public CompetitionArenaObjectInstance.NativeContext nativeObjectContext() {
        return host.nativeObjectContext();
    }
    public ItemTerrain itemTerrain() { return host.itemTerrain(); }
    public int xWrapMask() { return host.xWrapMask() & 0xFFFF; }
    public int yWrapMask() { return nativeObjectContext().wrapYMask(); }
    public int projectileYWrapMask() { return host.projectileYWrapMask() & 0xFFFF; }
    public boolean playerHighPriority(int slot) { return host.playerHighPriority(slot); }
    public int bottomY() { return nativeObjectContext().bottomY(); }
    public int trigger(int index) { return triggers[index & 0xF]; }
    public void setTrigger(int index, int value) { triggers[index & 0xF] = value & 0xFF; }
    public int lap(int slot) { return laps[slot]; }
    public int rings(int slot) { return rings[slot]; }
    public void giveRing(int slot) { rings[slot] = (rings[slot] + 1) & 0xFFFF; host.playSfx(Sonic3kSfx.RING_RIGHT); }
    public boolean controlsLocked() { return falseStartTicks != 0; }
    public int falseStartTicks() { return falseStartTicks; }
    public int falseStarter() { return falseStarter; }
    public boolean started() { return go; }
    public boolean finished(int slot) { return finished[slot]; }
    /** Goal init clears timers before allocating the two native HUD owners. */
    public void beginGoalHud() { goalTimerMask = 0; }
    public void installGoalTimer(int slot) { goalTimerMask |= 1 << slot; }
    public int timeCentiseconds(int slot) { return Math.min(MAX_TIME, completedCentiseconds[slot] + frameTime(lapFrames[slot])); }
    public int packedTime(int slot) {
        int time = timeCentiseconds(slot);
        return (time / 6000 << 16) | (time / 100 % 60 << 8) | time % 100;
    }

    /** Called by native neon-display animation when mapping frame 3 is reached. */
    public void signalGo() {
        if (falseStartTicks != 0 || resultSent) return;
        go = true;
        for (int slot = 0; slot < players.size(); slot++) timerRunning[slot] = !finished[slot];
    }

    /** Called before native participant slots; the clock is Level_frame_counter. */
    public void beforeFrame(int nativeLevelFrameCounter) {
        levelFrameCounter = nativeLevelFrameCounter & 0xFFFF;
        if ((levelFrameCounter & 7) != 0) return;
        for (S3kCompetitionPlayer player : players) {
            if ((player.statusSecondary() & SPEED_SHOES) == 0 || player.speedShoesTimer() == 0) continue;
            player.setSpeedShoesTimer(player.speedShoesTimer() - 1);
            if (player.speedShoesTimer() == 0) {
                player.resetSpeeds();
                player.setStatusSecondary(player.statusSecondary() & ~SPEED_SHOES);
            }
        }
    }

    /** Called after native objects, once per executed competition frame. */
    public void afterFrame() {
        if (goalTimerMask >= 0) return; // Native goal/neon/timer SSTs own their update phase.
        tickFalseStart();
        if (falseStartTicks != 0) return;
        updateFinish();
        for (int slot = 0; slot < players.size(); slot++) tickGoalTimer(slot);
    }

    /** loc_36D3E runs in the goal SST, before its children and the HUD time records. */
    public boolean tickGoalHold() {
        boolean held = falseStartTicks != 0;
        tickFalseStart();
        return held;
    }
    private void tickFalseStart() {
        if (falseStartTicks != 0) {
            if (levelFrameCounter == falseStartFrame) return;
            if (--falseStartTicks == 0) resetFalseStart();
        }
    }
    public void finishFromNeon() { updateFinish(); }
    public void advanceResultWait() { updateFinish(false); }
    /** The allocated secondary neon runs after the timer SSTs, unlike the first-finisher owner. */
    public boolean finishFromLoser(int slot) {
        if (finished[slot] || laps[slot] <= TOTAL_LAPS) return false;
        finished[slot] = true; timerRunning[slot] = false; finishWait = 0x78;
        host.playSfx(Sonic3kSfx.GOAL);
        return true;
    }
    /** loc_37D96 increments the HUD time before rebuilding the digit mappings. */
    public void tickGoalTimer(int slot) {
        if (timerRunning[slot] && !finished[slot] && falseStartTicks == 0) {
            lapFrames[slot]++;
            if (timeCentiseconds(slot) >= MAX_TIME) {
                timerRunning[slot] = false;
                finished[slot] = true;
            }
        }
    }

    /** Native loc_124C8 calls this after restoring the saved start position. */
    public void notifyRespawn(int slot) { respawnPending[slot] = true; rings[slot] = 0; }

    /**
     * sub_123C2 / loc_124C8, called by the miniature death terrain callback before
     * its MoveSprite_TestGravity. FixBugs=0 reads P1 Camera_Y_pos for BOTH slots.
     * The shared host retains the appropriate scroll lock while this returns false.
     */
    public boolean resolveDeadPlayer(int slot, int playerOneCameraY) {
        S3kCompetitionPlayer player = player(slot);
        if (player.routine() != 6) return false;
        player.setSpinDashFlag(0);
        if ((short) player.centreY() <= (short) (playerOneCameraY + 0x90)) return false;
        StartPosition start = starts.get(slot);
        player.respawn(start.centreX(), start.centreY(), start.topSolidBit(), start.lrbSolidBit());
        player.setHighPriority(start.highPriority());
        notifyRespawn(slot);
        return true;
    }

    /**
     * One native goal object call, processing P1 then P2. The side latch changes
     * before Y/layer validation, exactly like sub_36DFC (74097-74207).
     */
    public void observeGoal(int goalX, int goalY, int subtype) {
        observeGoal(goalX, goalY, subtype, null);
    }
    public void observeGoal(int goalX, int goalY, int subtype, AbstractObjectInstance goalOwner) {
        if (controlsLocked() || resultSent) return;
        int top = (short) (goalY - (((subtype & 0xFF) << 3) - 0x14));
        int bottom = (short) (goalY + 0x14);
        for (S3kCompetitionPlayer player : players) {
            int slot = player.slot();
            if (respawnPending[slot]) {
                respawnPending[slot] = false;
                rightOfGoal[slot] = false;
                laps[slot] = Math.max(0, laps[slot] - 1);
                continue;
            }
            boolean right = (player.centreX() & 0xFFFF) >= (goalX & 0xFFFF);
            if (right == rightOfGoal[slot]) continue;
            rightOfGoal[slot] = right;
            int y = (short) player.centreY();
            int dx = (short) (player.centreX() - goalX);
            if (y < top || y >= bottom || Math.abs(dx) >= 0x20 || player.topSolidBit() != 0xC) continue;
            if (!right) {
                laps[slot] = Math.max(0, laps[slot] - 1);
                continue;
            }
            // Update_HUD_timer=$80 after a finish is nonzero, even though it no
            // longer runs. P2's first crossing after P1 finishes is not a false start.
            if (laps[slot] == 0 && highestLaps[slot] == 0 && !timerRunning[0] && !finished[0]) {
                falseStartTicks = FALSE_START_TICKS;
                falseStartFrame = levelFrameCounter;
                falseStarter = slot;
                if (goalOwner != null && allocateAfter(goalOwner,
                        () -> new CompetitionHudObjectInstance(CompetitionHudObjectInstance.Kind.FALSE_START, slot)) != null)
                    host.playSfx(Sonic3kSfx.ERROR);
                continue; // Native still invokes P2's goal routine after P1 locks both controllers.
            }
            int lap = laps[slot];
            if (lap > highestLaps[slot]) {
                highestLaps[slot] = lap;
                int completedLap = lap - 1;
                if (completedLap >= 0 && completedLap < TOTAL_LAPS) {
                    int time = frameTime(lapFrames[slot]);
                    lapTimes[slot][completedLap] = time;
                    completedCentiseconds[slot] += time;
                    lapFrames[slot] = 0;
                }
                // Native replenishment only for a new highest lap near the post head.
                if (goalY - y < 0x14) replenishItems();
            }
            laps[slot] = (lap + 1) & 0xFF;
        }
    }

    private void replenishItems() {
        if (config.itemsEnabled()) {
            int offset = replenishmentOffset & 0xF;
            List<CompetitionItemObjectInstance> wheels = new ArrayList<>(host.itemWheels());
            wheels.sort(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex));
            for (CompetitionItemObjectInstance wheel : wheels) {
                if (wheel.isDestroyed() || !wheel.isWheel()) continue;
                if (offset >= itemSequence.length) throw new IllegalStateException("Native wheel table exceeds ROM sequence");
                wheel.replenish(itemSequence[offset++] & 0xFF);
            }
        }
        replenishmentOffset = (replenishmentOffset + 2) & 0xFFFF;
    }

    /** Item effects 0..3 are immediate even if their cosmetic child allocation fails. */
    public void applyImmediateItem(int slot, int effect) {
        S3kCompetitionPlayer player = player(slot);
        switch (effect) {
            case 0, 1 -> {
                player.setStatusSecondary(player.statusSecondary() | SPEED_SHOES);
                player.setSpeedShoesTimer(75);
                if (effect == 0) player.setSpeeds(0xC00, 0x18, 0x80);
                else player.setSpeeds(0x300, 8, 0x20);
            }
            case 2 -> swapShoes();
            case 3 -> giveRing(slot);
            default -> throw new IllegalArgumentException("Not an immediate native item: " + effect);
        }
    }

    /** Full status_secondary bytes swap, not only the speed-shoes bit (FixBugs=0). */
    private void swapShoes() {
        if (players.size() != 2) return;
        S3kCompetitionPlayer a = players.get(0), b = players.get(1);
        int status = a.statusSecondary(), timer = a.speedShoesTimer();
        S3kCompetitionPlayer.Speeds speeds = a.speeds(), otherSpeeds = b.speeds();
        a.setStatusSecondary(b.statusSecondary()); b.setStatusSecondary(status);
        a.setSpeedShoesTimer(b.speedShoesTimer()); b.setSpeedShoesTimer(timer);
        a.resetSpeeds(); b.resetSpeeds();
        if ((a.statusSecondary() & SPEED_SHOES) != 0) a.setSpeeds(otherSpeeds);
        if ((b.statusSecondary() & SPEED_SHOES) != 0) b.setSpeeds(speeds);
    }

    public AbstractObjectInstance allocateAfter(AbstractObjectInstance parent,
            Supplier<? extends AbstractObjectInstance> factory) {
        return host.allocateAfter(parent, factory);
    }
    public AbstractObjectInstance overwriteProjectile(Supplier<? extends AbstractObjectInstance> factory) {
        return host.overwriteProjectile(factory);
    }
    public void playSfx(Sonic3kSfx sound) { host.playSfx(sound); }
    public int itemAnimationByte(int offset) { return itemAnimation[offset] & 0xFF; }
    public int goalAnimationByte(int offset) { return goalAnimation[offset] & 0xFF; }

    private void resetFalseStart() {
        for (int slot = 0; slot < players.size(); slot++) {
            // Native resets position, plane, velocity/status and move lock, keeping
            // character, shoes and other secondary flags already owned by the slot.
            S3kCompetitionPlayer player = players.get(slot);
            StartPosition start = starts.get(slot);
            player.setCentreNative(start.centreX(),start.centreY());
            player.setTopSolidBit(start.topSolidBit()); player.setLrbSolidBit(start.lrbSolidBit());
            player.setHighPriority(start.highPriority());
            player.setXVelocity(0); player.setYVelocity(0); player.setGroundVelocity(0);
            player.setStatus(2); player.setMoveLock(0);
            laps[slot] = 0; lapFrames[slot] = 0; completedCentiseconds[slot] = 0;
            timerRunning[slot] = false; finished[slot] = false;
            Arrays.fill(lapTimes[slot], 0);
            // $34/$35 side and $36/$37 highest-lap bytes survive native false-start reset.
        }
        go = false; falseStarter = -1; finishWait = -1;
    }

    private void updateFinish() { updateFinish(true); }
    private void updateFinish(boolean markNewFinishes) {
        if (resultSent || !go) return;
        boolean firstFinish = false;
        for (int slot = 0; slot < players.size(); slot++) {
            if (markNewFinishes && !finished[slot] && laps[slot] > TOTAL_LAPS) {
                finished[slot] = true; timerRunning[slot] = false; firstFinish = true;
                if (slot == 0 && config.mode() == Mode.TIME_ATTACK)
                    host.saveTimeAttackRecord(result().players().getFirst(), arena);
            }
        }
        // loc_37256 plays once; its same-pass P2 branch only switches to the tie display.
        if (firstFinish) host.playSfx(Sonic3kSfx.GOAL);
        if (finishWait < 0) {
            boolean timerStopped = false;
            for (int slot = 0; slot < players.size(); slot++) timerStopped |= finished[slot];
            if (!firstFinish && !timerStopped) return;
            finishWait = config.mode() == Mode.GRAND_PRIX && players.size() == 2 && !allFinished() ? 0x168 : 0x78;
            return;
        }
        if (config.mode() != Mode.GRAND_PRIX && !allFinished()) return;
        if (firstFinish && allFinished()) finishWait = 0x78;
        // FixBugs=0 loc_37308 tests P1's timer twice: only an unfinished P1
        // on its final lap pauses GP grace. A finished P1 never pauses for P2.
        boolean pauseGpGrace = config.mode() == Mode.GRAND_PRIX && !finished[0]
                && laps[0] == TOTAL_LAPS;
        if (!pauseGpGrace && --finishWait < 0) completeRace();
    }

    private boolean allFinished() {
        for (int slot = 0; slot < players.size(); slot++) if (!finished[slot]) return false;
        return true;
    }

    private void completeRace() {
        int arenaIndex = GRAND_PRIX_ORDER.indexOf(arena);
        for (int slot = 0; slot < players.size(); slot++) grandPrixTimes[slot][arenaIndex]
                = finished[slot] ? timeCentiseconds(slot) : MAX_TIME;
        if (config.mode() == Mode.GRAND_PRIX && arenaIndex + 1 < GRAND_PRIX_ORDER.size()) {
            resultSent = true;
            host.requestArena(GRAND_PRIX_ORDER.get(arenaIndex + 1));
        } else {
            resultSent = true;
            host.requestResults(result());
        }
    }

    /** Explicit level-load boundary, called after native participants have new start positions. */
    public void enterArena(Arena nextArena) {
        arena = Objects.requireNonNull(nextArena);
        starts = saveStartPositions();
        Arrays.fill(laps, 0); Arrays.fill(highestLaps, 0); Arrays.fill(rightOfGoal, false);
        Arrays.fill(respawnPending, false); Arrays.fill(timerRunning, false); Arrays.fill(finished, false);
        Arrays.fill(lapFrames, 0); Arrays.fill(completedCentiseconds, 0); Arrays.fill(rings, 0);
        for (int[] times : lapTimes) Arrays.fill(times, 0);
        Arrays.fill(triggers, 0);
        go = false; falseStartTicks = 0; falseStarter = -1; replenishmentOffset = 0;
        finishWait = -1; resultSent = false; levelFrameCounter = 0;
    }

    public Result result() {
        List<PlayerResult> values = new ArrayList<>();
        for (S3kCompetitionPlayer player : players) {
            int slot = player.slot();
            values.add(new PlayerResult(slot, player.character(), laps[slot],
                    Arrays.stream(lapTimes[slot]).boxed().toList(), timeCentiseconds(slot),
                    laps[slot] > TOTAL_LAPS, rings[slot]));
        }
        return new Result(config.mode(), arena, values, Arrays.stream(grandPrixTimes)
                .map(times -> Arrays.stream(times).boxed().toList()).toList());
    }

    private static int frameTime(int frames) {
        // sub_B0E4's ROM frame→hundredths table truncates at each lap boundary.
        return frames / 60 * 100 + frames % 60 * 100 / 60;
    }
    private List<StartPosition> saveStartPositions() {
        return players.stream().map(player -> new StartPosition(player.centreX(),player.centreY(),
                player.topSolidBit(),player.lrbSolidBit(),player.highPriority())).toList();
    }

    @Override public String key() { return "s3k.native-competition"; }
    @Override public State capture() {
        List<PlayerState> states = new ArrayList<>();
        for (S3kCompetitionPlayer player : players) {
            int slot = player.slot();
            states.add(new PlayerState(player.capture(), laps[slot], highestLaps[slot],
                    rightOfGoal[slot], respawnPending[slot], timerRunning[slot], finished[slot],
                    lapFrames[slot], completedCentiseconds[slot],
                    Arrays.stream(lapTimes[slot]).boxed().toList(), rings[slot]));
        }
        return new State(arena, levelFrameCounter, go, falseStartTicks, falseStartFrame, falseStarter,
                replenishmentOffset, finishWait, resultSent, states, starts,
                Arrays.stream(triggers).boxed().toList(), Arrays.stream(grandPrixTimes)
                .map(times -> Arrays.stream(times).boxed().toList()).toList(), goalTimerMask);
    }
    @Override public void restore(State state) {
        if (state.players().size() != players.size()) throw new IllegalArgumentException("Competition roster changed across restore");
        arena = state.arena(); levelFrameCounter = state.levelFrameCounter(); go = state.go();
        falseStartTicks = state.falseStartTicks(); falseStartFrame = state.falseStartFrame(); falseStarter = state.falseStarter();
        replenishmentOffset = state.replenishmentOffset(); finishWait = state.finishWait(); resultSent = state.resultSent();
        goalTimerMask = state.goalTimerMask();
        starts = state.starts();
        for (int slot = 0; slot < players.size(); slot++) {
            PlayerState p = state.players().get(slot);
            players.get(slot).restore(p.body()); laps[slot] = p.lap(); highestLaps[slot] = p.highestLap();
            rightOfGoal[slot] = p.rightOfGoal(); respawnPending[slot] = p.respawnPending();
            timerRunning[slot] = p.timerRunning(); finished[slot] = p.finished();
            lapFrames[slot] = p.lapFrames(); completedCentiseconds[slot] = p.completedCentiseconds(); rings[slot] = p.rings();
            for (int lap = 0; lap < TOTAL_LAPS; lap++) lapTimes[slot][lap] = p.lapTimes().get(lap);
        }
        for (int i = 0; i < triggers.length; i++) triggers[i] = state.triggers().get(i);
        for (int slot = 0; slot < 2; slot++) for (int i = 0; i < 5; i++)
            grandPrixTimes[slot][i] = state.grandPrixTimes().get(slot).get(i);
    }

    @Override public <T> T getService(Class<T> type) {
        if (type.isInstance(this)) return type.cast(this);
        return type == NativeCompetitionRoster.class && host.roster() != null ? type.cast(host.roster()) : null;
    }
    @Override public RewindSnapshottable<?> rewindAdapter() { return this; }
    @Override public void beforeAssembly(LevelLoadCause cause) { }
    @Override public void bindRoster(SpriteManager sprites) {
        Objects.requireNonNull(sprites, "sprites");
        var restored = new S3kCompetitionPlayer[config.participantCount()];
        for (var sprite : sprites.getAllSprites()) {
            if (!(sprite instanceof S3kMiniatureSprite miniature)) continue;
            int slot = miniature.nativeSlot();
            if (slot >= restored.length || restored[slot] != null)
                throw new IllegalStateException("Unexpected or duplicate native competition slot " + slot);
            if (miniature.nativePlayer().character() != players.get(slot).character())
                throw new IllegalStateException("Competition character identity changed during restore");
            restored[slot] = miniature.nativePlayer();
        }
        for (int slot = 0; slot < restored.length; slot++) if (restored[slot] == null)
            throw new IllegalStateException("Missing restored native competition slot " + slot);
        players = List.of(restored);
        host.rebindPlayers(players);
    }
    @Override public void failedAssembly(LevelLoadCause cause) { }
    @Override public void closeScreens() { }
    @Override public void retire() { }
}
