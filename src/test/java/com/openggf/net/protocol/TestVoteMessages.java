package com.openggf.net.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestVoteMessages {
    @Test
    void voteMessagesRoundTripThroughCodec() {
        ControlMessage[] messages = {
                new ControlMessage.TrackVoteOffer(
                        List.of("s3k:0:0", "s3k:0:1", "s3k:1:0"), 123_456L),
                new ControlMessage.TrackVoteTally(List.of(
                        new ControlMessage.VoteCount("s3k:0:0", 2),
                        new ControlMessage.VoteCount("s3k:0:1", 0))),
                new ControlMessage.TrackVoteResult("s3k:0:1"),
                new ControlMessage.RoomTrackUpdate("r-1", 0, 1)
        };
        for (ControlMessage message : messages) {
            assertEquals(message, ControlCodec.decode(
                    ControlCodec.encode("tok", message)).message());
        }
    }

    @Test
    void roomCreateCarriesAndBoundsVotePool() {
        ControlMessage.RoomDescriptor descriptor = new ControlMessage.RoomDescriptor(
                "room", "s3k", 0, 0, "OPEN", null, 16, false);
        ControlMessage.RoomCreate create = new ControlMessage.RoomCreate(descriptor,
                "RELAY", 0, "0.6:cafe1234", List.of("s3k:0:0", "s3k:0:1"));
        ControlMessage.RoomCreate decoded = (ControlMessage.RoomCreate) ControlCodec.decode(
                ControlCodec.encode("tok", create)).message();
        assertEquals(create, decoded);
        assertEquals(2, decoded.voteTrackKeys().size());
        assertThrows(IllegalArgumentException.class, () -> new ControlMessage.RoomCreate(
                descriptor, "RELAY", 0, "fp", java.util.Collections.nCopies(33, "s3k:0:0")));
    }
}
