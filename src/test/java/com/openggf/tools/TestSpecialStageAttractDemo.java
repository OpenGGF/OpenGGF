package com.openggf.tools;

import com.openggf.GameLoop;
import com.openggf.InputBindingFactory;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.EmeraldRewardKind;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.internal.AttractDemoPlayback;
import com.openggf.game.internal.TitleAttractDemo;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;

@RequiresRom(SonicGame.SONIC_1)
class TestSpecialStageAttractDemo {
    // Short test timeout. The sampled controls still come from the REV01 cartridge.
    private static final int DEMO_WAITS = 60;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void launchFeedsTheRomStreamAndReturnsToTitle(boolean abort) throws Exception {
        TestEnvironment.resetEngineOnly();
        var services = EngineServices.current();
        services.configuration().setConfigValue(SonicConfiguration.AUDIO_ENABLED, false);
        var title = new DemoTitle();
        var program = new RomProgram();
        try (var boot = new HeadlessGameBoot(320, 224, services, new HeadlessGameBoot.NativeGlLifecycle() {
            @Override public void initialize(HeadlessGameBoot ignored) { services.graphics().initHeadless(); }
            @Override public void close(HeadlessGameBoot ignored) { }
        })) {
            boot.setBootToTitle(true);
            boot.setModuleDecorator(module -> new DelegatingGameModule(module, "test:special-stage-attract") {
                @Override public TitleScreenProvider getTitleScreenProvider() { return title; }
                @Override public <T> T getGameService(Class<T> type) {
                    return type == TitleAttractDemo.class ? type.cast(program) : super.getGameService(type);
                }
            });
            var loop = boot.boot(Path.of(System.getProperty("sonic1.rom.path")), 0, 0);
            var bindings = InputBindingFactory.supplier(services.configuration());
            var input = new InputHandler(bindings);
            loop.setInputHandler(input);
            var titleWorld = GameServices.worldSession();
            advanceUntil(loop, () -> loop.getCurrentGameMode() != GameMode.TITLE_SCREEN);
            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
            var demoWorld = GameServices.worldSession();
            assertNotSame(titleWorld, demoWorld);
            var stage = assertInstanceOf(Sonic1SpecialStageProvider.class, loop.getActiveSpecialStageProvider());
            assertEquals(0, stage.getCurrentStage());

            advanceUntil(loop, () -> program.waits == 1);
            assertEquals(1, stage.pacingState().acceptedSampleOrdinal(), "entry fades spend no demo samples");
            advanceUntil(loop, () -> program.waits == 39);
            assertEquals(0x04, stage.pacingState().player1Held(), "first non-neutral ROM command reaches the stage");
            assertEquals(39, stage.pacingState().acceptedSampleOrdinal());

            if (abort) {
                input.handleKeyEvent(bindings.get().p1Start(), GLFW_PRESS);
            } else {
                advanceUntil(loop, () -> program.waits == DEMO_WAITS - 1);
            }
            assertEquals(GameMode.SPECIAL_STAGE, loop.getCurrentGameMode());
            loop.step();
            assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
            assertNotSame(demoWorld, GameServices.worldSession());
            assertSame(title, loop.getTitleScreenProvider());
            assertEquals(abort ? 40 : DEMO_WAITS, program.waits);
            assertEquals(abort, title.directReturn);
        }
    }

    private static void advanceUntil(GameLoop loop, java.util.function.BooleanSupplier reached) {
        for (int i = 0; i < 1200 && !reached.getAsBoolean(); i++) loop.step();
        assertTrue(reached.getAsBoolean(), "expected transition within 1200 frames");
    }

    private static final class DemoTitle extends Sonic1TitleScreenManager implements TitleAttractDemo.TitleClock {
        private int ticks;
        private boolean directReturn;

        @Override public void initialize() {
            ticks = 0;
            super.initialize();
        }

        @Override public void update(InputHandler input) {
            if (getState() == State.ACTIVE) ticks++;
            super.update(input);
        }

        @Override public int attractTitleTicks() { return ticks; }

        @Override public void initializeDirectTitle() {
            directReturn = true;
            initialize();
        }
    }

    private static final class RomProgram implements TitleAttractDemo, AttractDemoPlayback.Program {
        private int offset;
        private int duration;
        private int waits;
        private boolean abort;

        @Override public void enterTitle() { }

        @Override public Launch onTitleTick() {
            return new Launch(new Destination.SpecialStage(0, EmeraldRewardKind.CHAOS_EMERALD),
                    new AttractDemoPlayback(this), false);
        }

        @Override public void begin() {
            try {
                // GM_Special loads DemoDataPtr[6] (sonic.asm:3275-3281).
                var rom = EngineServices.current().roms().getRom();
                int table = rom.read32BitAddr(0x46DC);
                offset = rom.read32BitAddr(table + 6 * 4);
                duration = Byte.toUnsignedInt(rom.readByte(offset + 1));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override public LogicalInputSnapshot controllers(LogicalInputSnapshot physical) {
            abort = physical.player1().startHeld();
            try {
                var rom = EngineServices.current().roms().getRom();
                int nativeButtons = Byte.toUnsignedInt(rom.readByte(offset));
                int held = nativeButtons & 0x0F;
                int actions = ((nativeButtons & 0x40) != 0 ? InputActionMasks.ACTION_A : 0)
                        | ((nativeButtons & 0x10) != 0 ? InputActionMasks.ACTION_B : 0)
                        | ((nativeButtons & 0x20) != 0 ? InputActionMasks.ACTION_C : 0);
                if (--duration == 0) {
                    offset += 2;
                    duration = Byte.toUnsignedInt(rom.readByte(offset + 1)) + 1;
                }
                return LogicalInputSnapshot.ofPlayers(
                        PlayerInputState.of(held, held, actions, actions, false, false), PlayerInputState.neutral());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }

        @Override public boolean specialStageMainLoopReady(SpecialStageProvider provider) {
            // Entry's palette reveal can finish on the stage owner's final setup wait.
            return ((Sonic1SpecialStageProvider) provider).pacingState().interactive();
        }

        @Override public void serviceWait() { waits++; }

        @Override public AttractDemoPlayback.Transition finishIteration(boolean finished) {
            if (abort) return new AttractDemoPlayback.Transition(0, AttractDemoPlayback.ReturnTarget.TITLE);
            if (waits == DEMO_WAITS || finished) {
                return new AttractDemoPlayback.Transition(0, AttractDemoPlayback.ReturnTarget.BOOT);
            }
            return AttractDemoPlayback.Transition.NONE;
        }

        @Override public void reload() { }
    }
}
