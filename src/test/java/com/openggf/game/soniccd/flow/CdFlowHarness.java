package com.openggf.game.soniccd.flow;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.SonicCdGameModule;
import com.openggf.game.soniccd.backupram.CdBackupRamHost;
import com.openggf.game.soniccd.backupram.CdBackupRamVolume;
import com.openggf.game.soniccd.backupram.CdSaveRecord;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tools.HeadlessGameBoot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Boots Sonic CD from the user's USA disc to the title through the engine's own hosts and drives it
 * frame by frame with recorded pad input; {@link #row()} logs the program-flow state each frame.
 */
final class CdFlowHarness implements AutoCloseable {
    static final int UP = 1, DOWN = 2, LEFT = 4, RIGHT = 8;
    static final int A = 1, B = 2, C = 4;

    final CdDiscDataSource disc;
    final HeadlessGameBoot boot;
    final SonicCdGameModule module;
    final CdSceneCoordinator scenes;
    final GameLoop loop;
    private final RecordedInputOwnership.Lease lease;
    private Bk2FrameInput previous;
    int frame;
    final List<String> log = new ArrayList<>();

    /** One frame's flow state. */
    record Row(int frame, GameMode mode, CdStagePayload payload, long time, int lives, int score, int rings,
               int x, int y, boolean dead, boolean ctrlLocked, boolean fading, boolean timerPaused) { }

    CdFlowHarness(java.util.function.Consumer<SonicCdGameModule> beforeBoot) throws Exception {
        this(320, beforeBoot);
    }

    CdFlowHarness(int width, java.util.function.Consumer<SonicCdGameModule> beforeBoot) throws Exception {
        Path cue = CdTestMedia.usaCue();
        GameServices.configuration().setSessionOverride(SonicConfiguration.AUDIO_ENABLED, false);
        GameServices.configuration().setSessionOverride(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        disc = CdDiscDataSource.open(cue);
        boot = new HeadlessGameBoot(width, 224);
        module = new SonicCdGameModule();
        scenes = module.getGameService(CdSceneCoordinator.class);
        if (beforeBoot != null) beforeBoot.accept(module);
        boot.setBootToTitle(true);
        loop = boot.boot(disc, module, 0, 0);
        lease = RecordedInputOwnership.acquire(loop.getInputHandler());
    }

    CdTimeTravel timeTravel() { return CdTimeTravel.of(module); }

    void step(int directions, int actions, boolean start) {
        Bk2FrameInput current = new Bk2FrameInput(0, directions, actions, start, "flow");
        loop.getInputHandler().setLogicalOverride(RecordedInputSnapshots.fromBk2(current, previous));
        previous = current;
        loop.step();
        frame++;
    }

    void idle(int frames) { for (int i = 0; i < frames; i++) step(0, 0, false); }

    int until(BooleanSupplier condition, int budget, int directions) {
        int f = 0;
        while (!condition.getAsBoolean()) {
            if (f++ >= budget) throw new AssertionError("condition not reached in " + budget + " frames at " + row());
            step(directions, 0, false);
        }
        return f;
    }

    static void attachReadOnlySave(SonicCdGameModule module, CdSaveRecord record) {
        var volume = CdBackupRamVolume.formatted(CdBackupRamVolume.INTERNAL_BYTES);
        if (!volume.write(CdSaveRecord.FILE_NAME, 0, CdSaveRecord.FILE_BLOCKS, record.encode())) {
            throw new AssertionError("Test save did not fit in backup RAM");
        }
        module.getGameService(CdSceneCoordinator.class).attachBackupRamHost(new CdBackupRamHost() {
            @Override public Optional<Images> load() {
                return Optional.of(new Images(volume.image(), null));
            }

            @Override public void commit(Images images) {
                throw new AssertionError("This title visit must preserve the test save");
            }
        }, 0);
    }

    /** Confirm the title option a given number of places to the right of the default. */
    void selectTitleOption(int rightPresses) {
        until(() -> loop.getCurrentGameMode() == GameMode.TITLE_SCREEN, 600, 0);
        var title = (CdTitleScreen) loop.getTitleScreenProvider();
        until(() -> title.program() != null && title.program().acceptsMenuInput(), 3000, 0);
        step(0, 0, true);
        idle(60);
        for (int i = 0; i < rightPresses; i++) {
            step(RIGHT, 0, false);
            idle(60);
        }
        step(0, 0, true);
    }

    /** Starts a new game with the default NEW GAME selection. */
    void newGame() {
        selectTitleOption(0);
        until(() -> loop.getCurrentGameMode() == GameMode.LEVEL, 900, 0);
    }

    CdLevel level() {
        var levels = GameServices.levelOrNull();
        return levels != null && levels.getCurrentLevel() instanceof CdLevel cd ? cd : null;
    }

    AbstractPlayableSprite player() { return GameServices.sprites().getMainPlayable(); }

    Row row() {
        CdLevel level = loop.getCurrentGameMode() == GameMode.LEVEL ? level() : null;
        var state = level == null ? null : GameServices.level().getLevelGamestate();
        var game = GameServices.gameStateOrNull();
        var p = level == null ? null : player();
        return new Row(frame, loop.getCurrentGameMode(), level == null ? null : level.payload(),
                state == null ? -1 : state.getTimerFrames(), game == null ? -1 : game.getLives(),
                game == null ? -1 : game.getScore(), p == null ? -1 : p.getRingCount(),
                p == null ? -1 : p.getCentreX(), p == null ? -1 : p.getCentreY(), p != null && p.getDead(),
                level != null && level.flags().ctrlLocked, GameServices.fadeOrNull() != null && GameServices.fade().isActive(),
                state != null && state.isTimerPaused());
    }

    /** Starts a scenario at a destination with its local placement window loaded. */
    void place(int x, int y, int cameraX, int cameraY) {
        pokePosition(x, y, cameraX, cameraY);
        com.openggf.level.objects.ObjectManagerInternalAccess
                .refreshPlacementAfterRelocation(GameServices.level().getObjectManager());
    }

    /** Reproduces the native run's coordinate writes while retaining its live layout scan. */
    void pokePosition(int x, int y, int cameraX, int cameraY) {
        var p = player();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(p, x);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(p, y);
        var camera = GameServices.camera();
        camera.setX((short) cameraX);
        camera.setY((short) cameraY);
    }

    /**
     * Native setup pokes take effect before BK2 row {@code firstRow}; the engine is brought to the same
     * stage time first (the native {@code time} after row {@code firstRow - 1}). Then each native logic row
     * (a lag row runs no game logic) in [firstRow, toRow) is one engine frame with that row's pad, after
     * {@code beforeRow} applied that row's setup. Returns the engine row after each native row it ran, by row.
     */
    java.util.Map<Integer, Row> replay(CdNativeRun nat, int firstRow, int toRow,
                                        java.util.function.IntConsumer beforeRow,
                                        java.util.function.BiPredicate<Integer, Row> stop) {
        // Native ticks the clock in the next V-INT, reading ctrlLocked there (HUD and Points/Main.asm:255-267,
        // VInt_Level, Level/Main.asm:847); the engine ticks at the end of the frame the title card releases
        // control, so after a title card its clock leads native's by one frame.
        long alignTime = nat.timeFrames(firstRow - 1) + 1;
        until(() -> loop.getCurrentGameMode() == GameMode.LEVEL && row().time() == alignTime, 3000, 0);
        java.util.Map<Integer, Row> out = new java.util.LinkedHashMap<>();
        for (int r = firstRow; r < toRow; r++) {
            beforeRow.accept(r);
            if (nat.lag(r)) continue;
            int[] pad = nat.pad(r);
            step(pad[0], pad[1], pad[2] != 0);
            Row now = row();
            out.put(r, now);
            if (stop != null && stop.test(r, now)) break;
        }
        return out;
    }

    @Override
    public void close() throws Exception {
        try { lease.close(); } finally { try { boot.close(); } finally { disc.close(); } }
    }
}
