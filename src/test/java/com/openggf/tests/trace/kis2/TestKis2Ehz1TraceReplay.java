package com.openggf.tests.trace.kis2;

import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.kis2.Kis2GamePatch;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import com.openggf.trace.TraceData;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Trace replay for Knuckles in Sonic 2, Emerald Hill Zone Act 1.
 *
 * <p>The fixture was recorded from the user-supplied Sonic &amp; Knuckles
 * lock-on dump over Sonic 2 World REV01 through the TraceChaser S2 recorder
 * (the chip's patched Sonic 2 program keeps the S2 RAM map). Its metadata
 * names {@code knuckles} as the recorded main character, so the shared
 * bootstrap resolves the built-in {@code kis2} patch through the
 * deterministic launch policy and reopens the session on the patched module
 * before the level loads. The base game is Sonic 2: the Sonic 2 ROM supplies
 * the level data and the Sonic 3 &amp; Knuckles image supplies the S&amp;K
 * window the patch requires, so both ROM properties must be set.
 *
 * <p>Set {@code -Dopenggf.trace.candidate.dir=<abs dir>} to replay a scratch
 * capture instead of the committed fixture.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestKis2Ehz1TraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() { return SonicGame.SONIC_2; }

    @Override
    protected int zone() { return 0; }

    @Override
    protected int act() { return 0; }

    /**
     * The comparison is meaningless unless the recorded team activated the
     * KiS2 patch, so fail here rather than report Sonic-versus-Knuckles
     * physics as a trace divergence.
     */
    @Override
    protected void afterFixtureBuild(TraceData trace) {
        GameModule module = SessionManager.requireCurrentGameModule();
        assertInstanceOf(DelegatingGameModule.class, module,
                "the recorded knuckles team must resolve a built-in patch");
        assertEquals(Kis2GamePatch.ID, ((DelegatingGameModule) module).patchId(),
                "the recorded knuckles team must activate the kis2 patch");
        AbstractPlayableSprite main = GameServices.sprites().getMainPlayable();
        assertInstanceOf(Knuckles.class, main, "the replayed main sprite must be Knuckles");
    }

    @Override
    protected Path traceDirectory() {
        String candidate = System.getProperty("openggf.trace.candidate.dir");
        return candidate != null
                ? Path.of(candidate)
                : Path.of("src/test/resources/traces/kis2/ehz1");
    }
}
