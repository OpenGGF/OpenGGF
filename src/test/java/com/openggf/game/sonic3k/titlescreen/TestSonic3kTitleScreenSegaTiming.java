package com.openggf.game.sonic3k.titlescreen;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsConstants;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * When S3K {@code Title_Screen} issues {@code cmd_SEGA}, counted in title updates
 * (one {@code Wait_VSync} each), straight from the ROM: {@code Pal_FadeToBlack}
 * (sonic3k.asm:5399; {@code move.w #$15,d4} / {@code dbf}, 5042-5052), the
 * interrupt-masked decompression span (5400-5483, no VSync, not modelled), then
 * {@code Pal_FadeFromBlack} (5484; 4903-4913) and {@code cmd_SEGA} before
 * {@code Wait_SegaS3K} first reads Start (5485-5494). Neither fade reads Start.
 * Round-2 lane pcm-output, 2026-10-17.
 */
@RequiresRom(SonicGame.SONIC_3K)
@ExtendWith(SingletonResetExtension.class)
class TestSonic3kTitleScreenSegaTiming {

    /** {@code move.w #$15,d4} then {@code dbf d4}: 22 iterations, one VSync each. */
    private static final int FADE_ITERATIONS = 0x15 + 1;
    /** The update in which {@code cmd_SEGA} is issued: after both fades' VSyncs (0-based). */
    private static final int CMD_SEGA_UPDATE = 2 * FADE_ITERATIONS - 1;

    private record Request(int update, int id) { }

    private final List<Request> requests = new ArrayList<>();
    private int update;
    private Sonic3kTitleScreenManager manager;
    private InputHandler input;
    private int jumpKey;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(RomTestUtils.ensureSonic3kRomAvailable() != null, "S3K ROM required");
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GameServices.graphics().initHeadless();
        GameServices.audio().setRequestObserver((type, id) -> requests.add(new Request(update, id)));
        manager = new Sonic3kTitleScreenManager();
        input = new InputHandler();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.neutral());
        jumpKey = GameServices.configuration().getInt(SonicConfiguration.JUMP);
    }

    @AfterEach
    void tearDown() {
        GameServices.audio().setRequestObserver(AudioRequestObserver.NONE);
        SessionManager.clear();
    }

    private void step(boolean start) {
        if (start) {
            input.handleKeyEvent(jumpKey, GLFW_PRESS);
        }
        manager.update(input);
        input.update();
        if (start) {
            input.handleKeyEvent(jumpKey, GLFW_RELEASE);
        }
        update++;
    }

    private List<Integer> ids() {
        return requests.stream().map(Request::id).toList();
    }

    private int firstUpdateOf(int id) {
        return requests.stream().filter(r -> r.id() == id).mapToInt(Request::update).findFirst().orElse(-1);
    }

    @Test
    void coldEntryIssuesCmdSegaAfterBothTwentyTwoFrameFades() {
        manager.initialize();
        for (int i = 0; i <= CMD_SEGA_UPDATE + 1; i++) {
            step(false);
        }
        assertEquals(CMD_SEGA_UPDATE, firstUpdateOf(Sonic3kSmpsConstants.CMD_SEGA),
                "cmd_SEGA follows 22 Pal_FadeToBlack and 22 Pal_FadeFromBlack VSyncs; requests=" + requests);
    }

    @Test
    void startDuringTheFadesIsNotRead() {
        manager.initialize();
        for (int i = 0; i <= CMD_SEGA_UPDATE; i++) {
            step(i == 5 || i == FADE_ITERATIONS + 3 || i == CMD_SEGA_UPDATE);
        }
        assertEquals(List.of(Sonic3kSmpsConstants.CMD_SEGA), ids(),
                "the fade loops never read Start, so only cmd_SEGA is issued");
        assertEquals(CMD_SEGA_UPDATE, firstUpdateOf(Sonic3kSmpsConstants.CMD_SEGA));
    }

    @Test
    void startOnTheFirstWaitSegaFrameStopsTheChantItAlreadyStarted() {
        manager.initialize();
        // Press on the first update after the SEGA hold state is entered: Wait_SegaS3K's first read of Start.
        boolean pressed = false;
        for (int i = 0; i < 400 && !pressed; i++) {
            boolean holdEntered = manager.getState() == com.openggf.game.TitleScreenProvider.State.INTRO_TEXT_HOLD;
            step(holdEntered);
            pressed = holdEntered;
        }
        assertTrue(pressed, "the SEGA hold was reached");
        assertEquals(List.of(Sonic3kSmpsConstants.CMD_SEGA, Sonic3kSmpsConstants.CMD_STOP_SEGA), ids(),
                "cmd_SEGA precedes the first Start check, so a press there issues the pair (loc_3FE4)");
    }

    @Test
    void everyTitleEntryRestartsTheSameSequence() {
        manager.initialize();
        for (int i = 0; i < CMD_SEGA_UPDATE + 20; i++) {
            step(false);
        }
        // A later Game_mode = title entry runs Title_Screen from its start again.
        requests.clear();
        update = 0;
        manager.initialize();
        for (int i = 0; i <= CMD_SEGA_UPDATE; i++) {
            step(false);
        }
        assertEquals(CMD_SEGA_UPDATE, firstUpdateOf(Sonic3kSmpsConstants.CMD_SEGA),
                "re-entry repeats both fades before cmd_SEGA; requests=" + requests);
    }
}
