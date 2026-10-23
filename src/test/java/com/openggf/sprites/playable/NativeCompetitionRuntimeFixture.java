package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelGamestate;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.LevelState;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.session.WorldSessionPolicyState;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;

import static org.mockito.Mockito.*;

/** Independent actor/service fixture; no native competition domain implementation is used. */
public final class NativeCompetitionRuntimeFixture implements NativeCompetitionRoster {
    public final LevelManager level = mock(LevelManager.class);
    public final LevelGamestate ordinary = new LevelGamestate();
    public final GameStateManager ordinaryGame = new GameStateManager();
    public final Camera ordinaryCamera = new Camera();
    public final LevelGamestate[] banks = {new LevelGamestate(), new LevelGamestate()};
    public final GameStateManager[] games = {new GameStateManager(), new GameStateManager()};
    public final Camera[] cameras = {new Camera(), new Camera()};
    public final SpriteManager sprites = new SpriteManager(GameServices.configuration());
    public final WorldSession world;
    public Sonic p1;
    public Tails p2;
    private final PlayableRuntimeContext context;

    public NativeCompetitionRuntimeFixture() {
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                return type == NativeCompetitionRoster.class ? type.cast(NativeCompetitionRuntimeFixture.this) : null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return ordinaryGame; }
            @Override public void beforeAssembly(LevelLoadCause cause) { }
            @Override public void bindRoster(SpriteManager sprites) { }
            @Override public void failedAssembly(LevelLoadCause cause) { }
            @Override public void closeScreens() { }
            @Override public void retire() { }
        };
        var module = new Sonic2GameModule() {
            @Override public <T> T getGameService(Class<T> type) {
                return type == WorldSessionPolicyProvider.class
                        ? type.cast((WorldSessionPolicyProvider) ignored -> policy) : super.getGameService(type);
            }
        };
        world = new WorldSession(module);
        when(level.getWorldSession()).thenReturn(world);
        when(level.getLevelGamestate()).thenReturn(ordinary);
        context = new PlayableRuntimeContext(module, ordinaryCamera, level, null, null, sprites,
                ordinaryGame, null, GameServices.audio());
        replacePlayers();
    }

    public void replacePlayers() {
        if (p1 != null) sprites.removeSprite(p1.getCode());
        if (p2 != null) sprites.removeSprite(p2.getCode());
        p1 = context.construct(() -> new Sonic("sonic", (short) 0x100, (short) 0x100));
        p2 = context.construct(() -> new Tails("tails", (short) 0x100, (short) 0x100));
        sprites.addSprite(p1);
        sprites.addSprite(p2);
        cameras[0].setFocusedSprite(p1);
        cameras[1].setFocusedSprite(p2);
    }
    public Tails ordinaryCpu() {
        var cpu = context.construct(() -> new Tails("cpu-tails", (short) 0, (short) 0));
        cpu.setCpuControlled(true);
        return cpu;
    }
    @Override public AbstractPlayableSprite player(int slot) { return slot == 0 ? p1 : p2; }
    @Override public LevelState levelState(int slot) { return banks[slot]; }
    @Override public GameStateManager gameState(int slot) { return games[slot]; }
    @Override public Camera camera(int slot) { return cameras[slot]; }
}
