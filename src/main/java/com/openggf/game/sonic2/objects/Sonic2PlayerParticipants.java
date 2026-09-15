package com.openggf.game.sonic2.objects;

import com.openggf.game.PlayableEntity;
import java.util.ArrayList;
import java.util.List;

/** Fallback-list plumbing; callers retain participation policies and guards. */
final class Sonic2PlayerParticipants {
    private Sonic2PlayerParticipants() {}

    static List<PlayableEntity> prependIfAbsent(List<PlayableEntity> participants,
                                               PlayableEntity updatePlayer) {
        if (updatePlayer == null || participants.contains(updatePlayer)) {
            return participants;
        }
        ArrayList<PlayableEntity> result = new ArrayList<>(participants.size() + 1);
        result.add(updatePlayer);
        result.addAll(participants);
        return result;
    }
}
