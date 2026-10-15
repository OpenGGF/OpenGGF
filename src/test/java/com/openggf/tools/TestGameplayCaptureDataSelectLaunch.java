package com.openggf.tools;

import com.openggf.control.InputActionMasks;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.dataselect.AbstractDataSelectProvider;
import com.openggf.game.dataselect.DataSelectActionType;
import com.openggf.game.save.SaveManager;
import com.openggf.game.save.SavePaths;
import com.openggf.game.save.SaveSlotState;
import com.openggf.game.save.SelectedTeam;
import com.openggf.game.session.SessionManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

/** Native menu confirmation must replace the title world and execute the real slot launch. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureDataSelectLaunch {
    @TempDir Path saves;

    @Test
    void captureRelaunchRegistersFreshCreatorStateAgainstTheChosenNativeTeam() throws Exception {
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.resolve("saved").toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.applyMod(fixtureJar());
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            var previousWorld = SessionManager.getCurrentWorldSession();
            assertTrue(com.openggf.game.session.WorldSessionPolicyAccess.hasPolicies(previousWorld));
            var previousObserver = previousWorld.getGameModule()
                    .getGameService(com.openggf.mods.code.GameplayObserverRegistry.class).entries().getFirst();
            assertEquals(1, previousObserver.adapter().capture());
            assertEquals("s3k", previousWorld.rootGameModule().getGameId().code());
            var menu = assertInstanceOf(AbstractDataSelectProvider.class, session.loop().getDataSelectProvider());
            menu.initialize();
            session.loop().setGameMode(GameMode.DATA_SELECT);
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                for (int frame = 0; frame < 60; frame++) session.stepPresentation(null, input);
                session.stepPresentation(press(AbstractPlayableSprite.INPUT_DOWN, 0), input);
                session.stepPresentation(null, input);
                session.stepPresentation(press(0, InputActionMasks.ACTION_A), input);
                for (int frame = 0; frame < 180 && session.loop().getCurrentGameMode() != GameMode.LEVEL; frame++) {
                    session.stepPresentation(null, input);
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                var current = SessionManager.getCurrentWorldSession();
                var observer = current.getGameModule()
                        .getGameService(com.openggf.mods.code.GameplayObserverRegistry.class).entries().getFirst();
                assertEquals("knuckles-ready", current.getGameModule().getIdentifier(),
                        "patch activation uses the exact selected launch team");
                assertNotSame(previousObserver.observer(), observer.observer());
                assertEquals(-1, previousObserver.adapter().capture(), "old-world observer was closed");
                assertEquals(2, observer.adapter().capture(), "a new registration transaction supplied fresh state");
                session.stepPresentation(null, input);
                assertEquals(2, observer.adapter().capture(), "new owner remains available after presentation dispatch");
                assertSame(GameServices.camera().getFocusedSprite(), session.player(),
                        "capture observations follow the fresh world's player");
            }
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void physicalNativeConfirmationLaunchesNoSaveAndNewSlotWithSelectedTeam(boolean saving) throws Exception {
        String previous = System.getProperty(SavePaths.ROOT_PROPERTY);
        System.setProperty(SavePaths.ROOT_PROPERTY, saves.toString());
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off",
                null, null, null, null, false, false);
        try (var session = new GameplayCaptureSession(settings)) {
            session.startAtTitle();
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 0, settings);
            var before = SessionManager.getCurrentWorldSession();
            var oldRoster = GameServices.sprites();
            var nativeMenu = assertInstanceOf(AbstractDataSelectProvider.class, session.loop().getDataSelectProvider());
            nativeMenu.initialize();
            session.loop().setGameMode(GameMode.DATA_SELECT);
            try (var input = MutatorGameplayCaptureTool.PresentationInput.install(session.loop())) {
                var physicalOwner = session.loop().getInputHandler();
                for (int frame = 0; frame < 60; frame++) session.stepPresentation(null, input);
                if (saving) {
                    session.stepPresentation(press(AbstractPlayableSprite.INPUT_RIGHT, 0), input);
                    // Confirmation is supported during the native 13-frame selector animation.
                    session.stepPresentation(null, input);
                }
                session.stepPresentation(press(AbstractPlayableSprite.INPUT_DOWN, 0), input);
                session.stepPresentation(null, input);
                var choice = nativeMenu.getSessionController().confirmSelection();
                assertEquals(saving ? DataSelectActionType.NEW_SLOT_START : DataSelectActionType.NO_SAVE_START,
                        choice.type());
                assertEquals(new SelectedTeam("knuckles", List.of()), choice.team(),
                        "native vertical input selects a different character from title boot");
                session.stepPresentation(press(0, InputActionMasks.ACTION_A), input);
                for (int frame = 0; frame < 180 && session.loop().getCurrentGameMode() != GameMode.LEVEL; frame++) {
                    session.stepPresentation(null, input);
                }
                assertEquals(GameMode.LEVEL, session.loop().getCurrentGameMode());
                var world = SessionManager.getCurrentWorldSession();
                assertNotSame(before, world);
                assertSame(before.rootGameModule(), world.rootGameModule());
                assertSame(before.getDataSource(), world.getDataSource());
                assertNotSame(oldRoster, GameServices.sprites());
                assertSame(physicalOwner, session.loop().getInputHandler());
                assertFalse(physicalOwner.hasLogicalOverride());
                input.requireOwnership(session.loop());
                assertEquals(choice.team(), world.getSaveSessionContext().selectedTeam());
                assertEquals("knuckles", GameServices.sprites().getMainPlayable().getCode());
                assertEquals(0, world.getCurrentZone());
                assertEquals(0, world.getCurrentAct());
                assertNotNull(GameServices.level().getCurrentLevel());
                var slot = new SaveManager(saves).readSlotSummary("s3k", 1);
                if (saving) {
                    assertEquals(1, world.getSaveSessionContext().activeSlot().orElseThrow());
                    assertTrue(slot.isLoadable(), "new-slot launch commits synchronously through native save ownership");
                    assertEquals("knuckles", slot.payload().get("mainCharacter"));
                    assertEquals(List.of(), slot.payload().get("sidekicks"));
                } else {
                    assertTrue(world.getSaveSessionContext().activeSlot().isEmpty());
                    assertEquals(SaveSlotState.EMPTY, slot.state());
                }
            }
        } finally {
            if (previous == null) System.clearProperty(SavePaths.ROOT_PROPERTY);
            else System.setProperty(SavePaths.ROOT_PROPERTY, previous);
        }
    }

    private static Bk2FrameInput press(int direction, int action) {
        return new Bk2FrameInput(0, direction, action, false, "native data select capture regression");
    }

    private Path fixtureJar() throws Exception {
        Path source = saves.resolve("FixtureMod.java"), classes = saves.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                package fixture;
                import com.openggf.game.*;
                import com.openggf.game.patch.*;
                import com.openggf.game.rewind.*;
                import com.openggf.mods.code.*;
                import com.openggf.mods.mutators.*;
                import java.util.*;
                public final class FixtureMod implements GgfMod {
                  static int registrations;
                  public void register(ModContext context) {
                    var observer = new Observer(++registrations);
                    context.registerServiceBundle("state", () -> GameServiceBundle.builder().gameplayObserver("state", observer).build());
                    context.registerService("support", MutatorSupportProfile.class, new MutatorSupportProfile() {
                      public Set<MutatorCapability> capabilities(int zone, int act) { return Set.of(MutatorCapability.BIG_HEAD); }
                      public boolean supportsPlayer(String character, boolean leader) { return true; }
                    });
                    context.registerMutator(new MutatorDefinition("heads", "Heads", "", MutatorScope.LIVE, MutatorScope.LIVE,
                      List.of(), Set.of(MutatorCapability.BIG_HEAD), options -> List.of(new MutatorPolicy.BigHead(110, MutatorPolicy.Target.LEADER))));
                    context.registerGamePatch(new Patch());
                  }
                  public static final class Observer implements GameplayObserver, RewindSnapshottable<Integer> {
                    private final int registration; private boolean closed;
                    Observer(int registration) { this.registration=registration; }
                    public String key() { return "state"; }
                    public Integer capture() { return closed ? -registration : registration; }
                    public void restore(Integer ignored) { }
                    public void onPresentation(GameplayObservation observation, TemporaryMutators mutators) {
                      if(closed) throw new IllegalStateException("closed creator state reused");
                    }
                    public void close() { closed=true; }
                  }
                  public static final class Patch implements GamePatch {
                    public String id() { return "fixture:team"; }
                    public String displayName() { return "Selected team"; }
                    public String baseGameId() { return "s3k"; }
                    public boolean activatesFor(GameplayLaunchRequest request) { return request.mainCharacter().equals("knuckles"); }
                    public Set<LogicalRom> romPrerequisites() { return Set.of(); }
                    public List<String> providedMainCharacters() { return List.of(); }
                    public GameModule apply(GameModule base, PatchContext context) {
                      return new DelegatingGameModule(base, "fixture:team") {
                        public String getIdentifier() { return "knuckles-ready"; }
                      };
                    }
                  }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "25", "-classpath", System.getProperty("java.class.path"),
                "-d", classes.toString(), source.toString()));
        Path jar = saves.resolve("fixture.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new JarEntry("META-INF/openggf-mod.yaml"));
            out.write("""
                    formatVersion: 1
                    id: fixture
                    name: Capture fixture
                    version: 1.0.0
                    authors: [OpenGGF]
                    description: Native launch ownership regression
                    engineApiRange: ">=0.7.0 <0.8.0"
                    type: patch
                    baseGame: s3k
                    entrypoint: fixture.FixtureMod
                    dependencies: []
                    audioOverrides: {}
                    artOverrides: {}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            try (var files = Files.walk(classes)) {
                for (var file : files.filter(Files::isRegularFile).toList()) {
                    out.putNextEntry(new JarEntry(classes.relativize(file).toString()));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
        }
        return jar;
    }
}
