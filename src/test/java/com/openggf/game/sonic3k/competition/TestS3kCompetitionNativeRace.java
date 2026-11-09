package com.openggf.game.sonic3k.competition;

import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.arena.MonitorRule;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import openggf.timeattack.TimeAttackRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Native observations are assertions only. No RAM row supplies engine gameplay state. */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCompetitionNativeRace {
    @Test void sonicTraversesFiveDesertPalaceLapsExactlyAsNative() throws Exception {
        try (var fixture = new HostedArenaFixture();
             var stream = getClass().getResourceAsStream("/competition/s3k/dpz-sonic-native.csv.gz");
             var rows = new BufferedReader(new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.US_ASCII))) {
            var arena = new S3kHostedArenaSession(fixture.host, new HostedArenaRequest(3, "sonic", false), () -> {});
            fixture.host.world().context().beginGameplayRun(TimeAttackRuntime.competitionPolicy(MonitorRule.OFF), 3, 0);
            for (int i = 0; i < 240; i++) fixture.iteration(() -> arena.step(TestS3kArenaZoomFraming.input(0, 0)));
            rows.readLine(); // Named, frozen native RAM columns; see the capture provenance.
            int previous = 0, count = 0;
            String row;
            while ((row = rows.readLine()) != null) {
                String[] columns = row.split(",");
                int[] n = java.util.Arrays.stream(columns).mapToInt(Integer::parseInt).toArray();
                assertEquals(count, n[0], "native row continuity");
                assertEquals(0, n[2], "this fixture has no lag rows");
                var input = TestS3kArenaZoomFraming.input(n[5], previous);
                previous = n[5];
                fixture.iteration(() -> arena.step(input));
                var p = fixture.host.session().player(0);
                // Native SST addresses translate to the engine's existing semantic slot identity.
                int interact = n[18] == 0 ? -1 : (n[18] - 0xB000) / 0x4A;
                int[] expected = {n[6],n[7],n[8],n[9],n[10],n[11],n[12],n[13],n[14],n[15],n[16],n[17],interact,n[19],n[20]};
                int[] actual = {p.centreX(),p.xFraction(),p.centreY(),p.yFraction(),p.xVelocity(),p.yVelocity(),
                        p.groundVelocity(),p.angle(),p.status(),p.statusSecondary(),p.routine(),p.objectControl(),
                        p.standingObject(),p.animation(),p.mappingFrame()};
                assertArrayEquals(expected, actual, "race frame " + count + ": x,xf,y,yf,xv,yv,gsp,angle,status,secondary,routine,control,interact,anim,mapping");
                count++;
            }
            assertEquals(2528, count);
            assertTrue(arena.trackFinished(), "full native five-lap traversal must finish");
        }
    }
}
