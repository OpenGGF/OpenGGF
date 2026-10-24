package com.openggf.game.sonic3k.objects;

import com.openggf.GameLoop;
import com.openggf.BonusStageSessionTestAccess;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.BonusStageDebugControls;
import com.openggf.game.BonusStageType;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.mockito.Mockito.*;

/** Exercises the host's actual key predicates and pre-frame repair boundary without a gameplay tick. */
class TestGameLoopBonusStageOwnership {
    @AfterEach
    void cleanup() {
        GumballMachineObjectInstance.resetDebugFiltersForTest();
        TestEnvironment.resetAll();
    }

    @ParameterizedTest
    @EnumSource(value = BonusStageType.class, names = {"GUMBALL", "GLOWING_SPHERE", "SLOT_MACHINE"})
    void nativeDebugFiltersWorkInEveryBonusTypeAndRetainKeyAndModifierGates(BonusStageType type) throws Exception {
        var provider = new Sonic3kBonusStageCoordinator();
        provider.onEnter(type, null);
        for (int key : new int[] {GLFW_KEY_F11, GLFW_KEY_INSERT}) {
            // Both modifier sides and debug-disabled input must retain the existing no-effect behavior.
            for (int modifier : new int[] {-1, GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT,
                    GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL, GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT,
                    GLFW_KEY_LEFT_SUPER, GLFW_KEY_RIGHT_SUPER}) {
                for (boolean enabled : new boolean[] {true, false}) {
                    GumballMachineObjectInstance.resetDebugFiltersForTest();
                    Fixture fixture = fixture(provider);
                    fixture.config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, enabled);
                    if (modifier != -1) fixture.input.handleKeyEvent(modifier, GLFW_PRESS);
                    fixture.input.handleKeyEvent(key, GLFW_PRESS);
                    fixture.update();
                    boolean admitted = enabled && modifier == -1;
                    assertEquals(!(admitted && key == GLFW_KEY_F11),
                            GumballMachineObjectInstance.shouldDebugRender(1, false, "machine-main"));
                    assertEquals(admitted && key == GLFW_KEY_INSERT ? "machine-main" : null,
                            GumballMachineObjectInstance.getCurrentDebugSourceFilterForTest());
                    fixture.input.update();
                    fixture.update();
                    assertEquals(!(admitted && key == GLFW_KEY_F11),
                            GumballMachineObjectInstance.shouldDebugRender(1, false, "machine-main"),
                            "Held F11 must not cycle the bucket again");
                    assertEquals(admitted && key == GLFW_KEY_INSERT ? "machine-main" : null,
                            GumballMachineObjectInstance.getCurrentDebugSourceFilterForTest(),
                            "Held keys must not cycle again without a fresh press");
                }
            }
        }
    }

    @Test
    void pachinkoRepairFollowsOverlayAndPrecedesTheFrozenFrameTailWithoutDuplicating() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var provider = new Sonic3kBonusStageCoordinator();
        provider.onEnter(BonusStageType.GLOWING_SPHERE, null);
        Fixture fixture = fixture(provider);
        set(fixture.loop, "gameplayMode", TestEnvironment.activeGameplayMode());
        ObjectManager objects = mock(ObjectManager.class);
        when(fixture.levels.getRomZoneId()).thenReturn(Sonic3kZoneIds.ZONE_GLOWING_SPHERE);
        when(fixture.levels.getObjectManager()).thenReturn(objects);
        when(objects.getActiveObjects()).thenReturn(List.of());
        when(fixture.title.isOverlayActive()).thenReturn(true);
        fixture.update();
        var order = inOrder(fixture.title, objects, fixture.playback);
        order.verify(fixture.title).update();
        order.verify(objects).getActiveObjects();
        order.verify(objects).createDynamicObject(any());
        order.verify(fixture.playback).onLevelFrameAdvanced();
        clearInvocations(objects);
        when(objects.getActiveObjects()).thenReturn(List.of(provider.bootstrapObject(BonusStageType.GLOWING_SPHERE).create()));
        fixture.update();
        verify(objects, never()).createDynamicObject(any());
    }

    @Test
    void activeProviderRepairsItsDeclaredBootstrapWithoutInferringTypeFromTheLoadedZone() throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        BonusStageProvider provider = mock(BonusStageProvider.class);
        when(provider.getActiveType()).thenReturn(BonusStageType.GUMBALL);
        var spawn = new ObjectSpawn(40, 50, 7, 0, 0, false, 0);
        ObjectInstance object = mock(ObjectInstance.class);
        when(provider.bootstrapObject(BonusStageType.GUMBALL)).thenReturn(
                new BonusStageProvider.BootstrapObject(spawn, object.getClass(), ignored -> object));
        Fixture fixture = fixture(provider);
        set(fixture.loop, "gameplayMode", TestEnvironment.activeGameplayMode());
        ObjectManager objects = mock(ObjectManager.class);
        when(fixture.levels.getRomZoneId()).thenReturn(42);
        when(fixture.levels.getObjectManager()).thenReturn(objects);
        when(objects.getActiveObjects()).thenReturn(List.of());
        when(objects.createDynamicObject(any())).thenAnswer(call -> {
            assertSame(object, ((java.util.function.Supplier<?>) call.getArgument(0)).get());
            return object;
        });
        fixture.update();
        verify(provider).bootstrapObject(BonusStageType.GUMBALL);
        verify(objects).createDynamicObject(any());
        verify(provider, never()).getZoneId(any());
    }

    @Test
    void diagnosticActionsBelongToTheProviderCapabilityAndDoNotChangeOtherStagesGlobals() throws Exception {
        BonusStageProvider provider = mock(BonusStageProvider.class,
                withSettings().extraInterfaces(BonusStageDebugControls.class));
        Fixture fixture = fixture(provider);
        fixture.config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        fixture.input.handleKeyEvent(GLFW_KEY_F11, GLFW_PRESS);
        fixture.input.handleKeyEvent(GLFW_KEY_INSERT, GLFW_PRESS);
        fixture.update();
        var controls = (BonusStageDebugControls) provider;
        var order = inOrder(controls);
        order.verify(controls).cycleObjectRenderFilter();
        order.verify(controls).cycleObjectSourceFilter();
        assertTrue(GumballMachineObjectInstance.shouldDebugRender(1, false));
        assertNull(GumballMachineObjectInstance.getCurrentDebugSourceFilterForTest());
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = BonusStageType.class, names = {"NONE"})
    void inactiveProviderDoesNotRequestABootstrap(BonusStageType type) throws Exception {
        BonusStageProvider provider = mock(BonusStageProvider.class);
        when(provider.getActiveType()).thenReturn(type);
        Fixture fixture = fixture(provider);
        fixture.update();
        verify(provider, never()).bootstrapObject(any());
    }

    @ParameterizedTest
    @EnumSource(value = BonusStageType.class, names = {"GUMBALL", "SLOT_MACHINE"})
    void nativeStagesWithoutDeclaredBootstrapsDoNotAllocate(BonusStageType type) throws Exception {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var provider = new Sonic3kBonusStageCoordinator();
        provider.onEnter(type, null);
        Fixture fixture = fixture(provider);
        set(fixture.loop, "gameplayMode", TestEnvironment.activeGameplayMode());
        ObjectManager objects = mock(ObjectManager.class);
        when(fixture.levels.getObjectManager()).thenReturn(objects);
        fixture.update();
        verify(objects, never()).getActiveObjects();
        verify(objects, never()).createDynamicObject(any());
        verify(objects).advanceVblaCounter();
    }

    @Test
    void absentOrUnsupportedProviderDoesNotTouchNativeDiagnosticState() throws Exception {
        for (var provider : new BonusStageProvider[] {null, NoOpBonusStageProvider.INSTANCE, mock(BonusStageProvider.class)}) {
            Fixture fixture = fixture(provider);
            fixture.config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
            fixture.input.handleKeyEvent(GLFW_KEY_F11, GLFW_PRESS);
            fixture.input.handleKeyEvent(GLFW_KEY_INSERT, GLFW_PRESS);
            fixture.update();
            assertTrue(GumballMachineObjectInstance.shouldDebugRender(1, false));
            assertNull(GumballMachineObjectInstance.getCurrentDebugSourceFilterForTest());
        }
    }

    @Test
    void nativeDiagnosticsRetainTheExecutingProviderWhenTheFrameTailClearsIt() throws Exception {
        var provider = new Sonic3kBonusStageCoordinator();
        provider.onEnter(BonusStageType.GUMBALL, null);
        Fixture fixture = fixture(provider);
        doAnswer(call -> { BonusStageSessionTestAccess.provider(fixture.loop, null); return null; })
                .when(fixture.playback).onLevelFrameAdvanced();
        fixture.config.setConfigValue(SonicConfiguration.DEBUG_VIEW_ENABLED, true);
        fixture.input.handleKeyEvent(GLFW_KEY_F11, GLFW_PRESS);
        fixture.input.handleKeyEvent(GLFW_KEY_INSERT, GLFW_PRESS);
        fixture.update();
        assertFalse(GumballMachineObjectInstance.shouldDebugRender(1, false, "machine-main"));
        assertEquals("machine-main", GumballMachineObjectInstance.getCurrentDebugSourceFilterForTest());
    }

    private static Fixture fixture(BonusStageProvider provider) throws Exception {
        GameLoop loop = mock(GameLoop.class, CALLS_REAL_METHODS);
        var config = SonicConfigurationService.createStandalone();
        var input = new InputHandler();
        var levels = mock(LevelManager.class);
        var title = mock(TitleCardProvider.class);
        var playback = mock(PlaybackDebugManager.class);
        set(loop, "configService", config);
        set(loop, "inputHandler", input);
        set(loop, "levelManager", levels);
        com.openggf.TitleCardLifecycleTestAccess.provider(loop, title);
        set(loop, "playbackDebugManager", playback);
        BonusStageSessionTestAccess.provider(loop, provider);
        BonusStageSessionTestAccess.pending(loop, true);
        set(loop, "audioUpdatedThisStep", true);
        set(loop, "activePlcLifecycleFrame", mock(PlcLifecycleFrame.class));
        return new Fixture(loop, config, input, levels, title, playback);
    }

    private record Fixture(GameLoop loop, SonicConfigurationService config, InputHandler input,
                           LevelManager levels, TitleCardProvider title, PlaybackDebugManager playback) {
        void update() throws Exception {
            var update = GameLoop.class.getDeclaredMethod("updateBonusStageMode", boolean.class);
            update.setAccessible(true);
            update.invoke(loop, false);
        }
    }

    private static void set(GameLoop loop, String name, Object value) throws Exception {
        var field = GameLoop.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(loop, value);
    }
}
