package com.openggf.sprites.playable;

import com.openggf.audio.GameMusic;
import com.openggf.camera.Camera;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.*;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.competition.*;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.*;
import com.openggf.level.rings.*;
import com.openggf.sprites.managers.SpriteManager;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;

/** Actual native session, actors and object allocation; no global active-player substitution. */
public final class Sonic2CompetitionObjectFixture {
    public final Sonic2CompetitionSession session = new Sonic2CompetitionSession();
    public final LevelManager level = mock(LevelManager.class);
    public final LevelGamestate ordinary = new LevelGamestate();
    public final GameStateManager ordinaryGame = new GameStateManager();
    public final Camera[] cameras = {new Camera(), new Camera()};
    public final SpriteManager sprites = new SpriteManager(GameServices.configuration());
    public final List<String> events = new ArrayList<>();
    public final WorldSession world;
    public final ObjectManager objects;
    public final RingManager rings;
    public final ObjectServices services;
    public Sonic p1;
    public Tails p2;
    public int levelFrame;
    public int act;
    public int teleports;
    public boolean teleportOnly;
    public boolean hostPresent = true;
    private final PlayableRuntimeContext context;

    public Sonic2CompetitionObjectFixture() {
        session.updateMenu(LogicalInputSnapshot.ofPlayers(
                PlayerInputState.of(0, 0, 0, 0, true, true), PlayerInputState.neutral()), true);
        var host = new Sonic2CompetitionObjectHost() {
            @Override public boolean teleportOnly() { return teleportOnly; }
            @Override public void requestTeleport(Sonic2CompetitionSession owner) {
                if (owner != session) throw new AssertionError("Foreign session");
                teleports++; events.add("teleport");
            }
        };
        var policy = new WorldSessionPolicyState() {
            @Override public <T> T getService(Class<T> type) {
                if (type == Sonic2CompetitionObjectHost.class) return hostPresent ? type.cast(host) : null;
                return type.isInstance(session) ? type.cast(session) : null;
            }
            @Override public RewindSnapshottable<?> rewindAdapter() { return session; }
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
        when(level.getFrameCounter()).thenAnswer(ignored -> levelFrame);
        context = new PlayableRuntimeContext(module, cameras[0], level, null, null, sprites,
                ordinaryGame, new com.openggf.timer.TimerManager(), GameServices.audio());
        replacePlayers();
        session.bindCameras(cameras[0], cameras[1]);
        services = new StubObjectServices() {
            @Override public WorldSession worldSession() { return world; }
            @Override public LevelManager levelManager() { return level; }
            @Override public ObjectManager objectManager() { return objects; }
            @Override public SpriteManager spriteManager() { return sprites; }
            @Override public RingManager ringManager() { return rings; }
            @Override public void spawnLostRings(PlayableEntity player, int vIntRunCount) {
                rings.spawnLostRings((AbstractPlayableSprite) player, player.getRingCount(), vIntRunCount);
            }
            @Override public Camera camera() { return cameras[0]; }
            @Override public LevelState levelGamestate() { return ordinary; }
            @Override public GameStateManager gameState() { return ordinaryGame; }
            @Override public List<PlayableEntity> sidekicks() { return List.of(p2); }
            @Override public int currentAct() { return act; }
            @Override public GameModule gameModule() { return module; }
            @Override public com.openggf.configuration.SonicConfigurationService configuration() { return GameServices.configuration(); }
            @Override public void playSfx(int id) { events.add("sfx:" + id); }
            @Override public void playMusic(int id) { events.add("music:" + id); }
            @Override public boolean playMusic(GameMusic music) { events.add("music:" + music); return true; }
            @Override public void playMusicMailboxNativeRequest(int id) { events.add("mailbox:" + id); }
        };
        var registry = mock(ObjectRegistry.class);
        when(registry.objectSlotLayout()).thenReturn(ObjectSlotLayout.SONIC_2);
        objects = new ObjectManager(List.of(), registry, 0, null, null, null, cameras[0], services);
        objects.reset(0);
        when(level.getObjectManager()).thenReturn(objects);
        var frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        var sheet = new RingSpriteSheet(new com.openggf.level.Pattern[]{new com.openggf.level.Pattern()},
                List.of(frame, frame, frame), 1, 1, 1, 2);
        rings = new RingManager(List.of(), sheet, level, null, mock(com.openggf.audio.AudioManager.class));
        when(level.getRingManager()).thenReturn(rings);
    }

    public void replacePlayers() {
        if (p1 != null) sprites.removeSprite(p1.getCode());
        if (p2 != null) sprites.removeSprite(p2.getCode());
        p1 = context.construct(() -> new Sonic("sonic", (short) 0x100, (short) 0x100));
        p2 = context.construct(() -> new Tails("tails", (short) 0x100, (short) 0x100));
        sprites.addSprite(p1); sprites.addSprite(p2);
        session.bindPlayers(p1, p2);
        for (int i = 0; i < 2; i++) {
            cameras[i].setFocusedSprite(session.player(i));
            cameras[i].setMaxX((short) (0x5000 + i * 0x100));
        }
    }
}
