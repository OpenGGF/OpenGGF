package com.openggf.game.sonic3k.competition;

import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSoloSwapItem {
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void unspawnedSlotReceivesShoesAndRestoresThemOnAnotherSwapIncludingAfterRewind() throws Exception {
        for (var character : S3kCompetitionPlayer.Character.values()) {
            for (var shoeSpeeds : java.util.List.of(new S3kCompetitionPlayer.Speeds(0xC00, 0x18, 0x80),
                    new S3kCompetitionPlayer.Speeds(0x300, 8, 0x20))) {
                var world = new CompetitionTestWorld(S3kCompetitionSession.Mode.TIME_ATTACK);
                world.players.set(0, new S3kCompetitionPlayer(0, character, 984, 200));
                var config = new S3kCompetitionSession.Config(S3kCompetitionSession.Mode.MATCH_RACE,
                        S3kCompetitionSession.Arena.AZURE_LAKE, true, 1);
                var session = new S3kCompetitionSession(GameServices.rom().getRom(), config, world);
                var player = world.players.getFirst();
                var base = S3kCompetitionPlayer.readSpeeds(GameServices.rom().getRom(), player.character());
                player.setStatusSecondary(0x84);
                player.setSpeedShoesTimer(23);
                player.setSpeeds(shoeSpeeds);
                // loc_3652A (sonic3k.asm:73480-73525): unconditional full-byte exchanges.
                session.applyImmediateItem(0, 2);
                assertEquals(0, player.statusSecondary());
                assertEquals(0, player.speedShoesTimer());
                assertEquals(base, player.speeds());
                var snapshot = session.capture();
                for (int frame = 0; frame < 600; frame++) session.beforeFrame(frame);
                session.applyImmediateItem(0, 2);
                assertEquals(0x84, player.statusSecondary());
                assertEquals(23, player.speedShoesTimer(), "unspawned P2 does not execute shoe expiry");
                assertEquals(shoeSpeeds, player.speeds());
                var expected = session.capture();
                session.restore(snapshot);
                for (int frame = 0; frame < 600; frame++) session.beforeFrame(frame);
                session.applyImmediateItem(0, 2);
                assertEquals(expected, session.capture());
                assertEquals(1, session.players().size(), "no second participant is spawned");
            }
        }
    }
}
