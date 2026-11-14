package com.openggf.game.soniccd.title;

import com.openggf.control.InputHandler;
import com.openggf.control.PlayerInputState;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Objects;

/**
 * Hosts the title program ({@link CdTitleProgram}) in {@code TITLE_SCREEN}. The program returns an
 * IPX exit code; the {@link CdSceneCoordinator} turns it into the host's title action.
 */
public final class CdTitleScreen implements TitleScreenProvider {
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private CdTitleAssets assets;
    private CdTitleProgram program;
    private State state = State.INACTIVE;
    private TitleScreenAction action = TitleScreenAction.OTHER;

    public CdTitleScreen(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void initialize() {
        if (assets == null) assets = new CdTitleAssets(coordinator.data());
        coordinator.titleStarting();
        // Lane TA: a program loop IPX runs without .GameLoop (TimeAttack, IPX.asm:1214-1238) passes
        // through this host without running the title.
        TitleScreenAction resume = coordinator.consumeResume();
        if (resume != null) {
            program = null;
            action = resume;
            state = State.EXITING;
            return;
        }
        program = new CdTitleProgram(assets, coordinator.flow().titleFlags());
        // SPCmd_LoadTitle plays CDDA_TITLE once with MSCPLAY1 (CD System Program/SPX.asm:1114-1117).
        com.openggf.game.GameServices.audio().playMusic(com.openggf.game.soniccd.audio.CdSubCpuAudio.commandId("SCMD_TITLEMUS"));
        renderer.reset();
        action = TitleScreenAction.OTHER;
        state = State.ACTIVE;
    }

    @Override
    public void update(InputHandler input) {
        if (state != State.ACTIVE || program == null) return;
        var logical = input.logical();
        program.frame(pad(logical.player1()), pad(logical.player2()));
        // ObjMenu_PlayRingSound queues FM_RING for the cheats (Title Screen/Objects/Menu/Main.asm:476-478).
        for (var sound : program.drainSounds()) com.openggf.game.GameServices.audio().playSfx(com.openggf.audio.GameSound.RING);
        if (program.done()) {
            action = coordinator.titleExited(program.exitCode());
            state = State.EXITING;
        }
    }

    /**
     * The native pad byte ReadController builds (Main.asm:582-606): bit 0 Up, 1 Down, 2 Left,
     * 3 Right, 4 B, 5 C, 6 A, 7 Start. Engine action bits are A=1, B=2, C=4.
     */
    public static int pad(PlayerInputState player) {
        int actions = player.actionHeldMask();
        return (player.heldMask() & 0x0F)
                | ((actions & 2) != 0 ? 0x10 : 0)
                | ((actions & 4) != 0 ? 0x20 : 0)
                | ((actions & 1) != 0 ? 0x40 : 0)
                | (player.startHeld() ? 0x80 : 0);
    }

    @Override public void draw() { if (program != null) renderer.draw(program.screen()); }

    @Override public void setClearColor() { if (program != null) renderer.setClearColor(program.screen()); }

    @Override
    public void reset() {
        program = null;
        state = State.INACTIVE;
    }

    @Override public State getState() { return state; }
    @Override public boolean isExiting() { return state == State.EXITING; }
    @Override public boolean isActive() { return state != State.INACTIVE; }

    /** The host may read the action again while its exit fade runs, so reading does not clear it. */
    @Override
    public TitleScreenAction consumeExitAction() {
        return state == State.EXITING ? action : TitleScreenAction.OTHER;
    }

    @Override public int startZoneIndex() { return coordinator.titleStartZone(); }
    @Override public int startActIndex() { return coordinator.titleStartAct(); }

    /** The running program, for tests. */
    public CdTitleProgram program() { return program; }
}
