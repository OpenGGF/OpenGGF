package com.openggf.game.soniccd;
import com.openggf.game.*;
import com.openggf.GameLoop;
import com.openggf.game.save.SaveManager;
import com.openggf.game.session.*;
import com.openggf.control.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
/** Tests FIRST; actual public selected attempt/session and original engine controller.
 * SOURCE/UNRUN, configured EGL required. No synthetic context/Frame/snapshot or DEV binding. */
@Isolated class TestCdOriginalR11LoadingWorld {
 @TempDir Path root;
 @BeforeEach void setup(){TestEnvironment.resetAll();SessionManager.clear();var c=GameServices.configuration();c.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,"NATIVE_4_3");c.setSessionOverride(SonicConfiguration.SCREEN_WIDTH,320);c.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT,224);c.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");c.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");c.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);c.setSessionOverride(SonicConfiguration.AUDIO_ENABLED,false);c.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,false);c.setSessionOverride(SonicConfiguration.LIVE_REWIND_DETERMINISM_AUDIT,false);c.resolveDisplayAspect();}
 @AfterEach void cleanup(){try{SessionManager.clear();}finally{try{GameServices.graphics().cleanup();}finally{GraphicsManager.destroyForReinit();TestEnvironment.resetAll();}}}
 // R3 replaces the three R2 terminal-prefix tests with TestCdOriginalR11LoadingReady.
 // Shared real-public-ingress fixture only; no unsupported cut is a positive Ready result.
 static HeadlessGameBoot boot(){assumeTrue(Boolean.getBoolean("soniccd.opening.egl"),"Original selected R11 EGL opt-in");assertEquals("surfaceless-egl",System.getProperty("openggf.headless.gl"));assertNull(System.getenv("DISPLAY"));assertNull(System.getenv("WAYLAND_DISPLAY"));return new HeadlessGameBoot(320,224);}
 static void step(GameLoop loop,int held){loop.getInputHandler().setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(held&15,0,(held>>>4)&7,0,(held&128)!=0,false),PlayerInputState.neutral()));loop.step();}
}
