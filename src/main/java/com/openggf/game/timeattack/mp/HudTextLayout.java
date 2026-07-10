package com.openggf.game.timeattack.mp;

import com.openggf.game.timeattack.TimeAttackTimeFormat;
import com.openggf.net.protocol.ControlMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Pure text composition for the multiplayer HUD. */
public final class HudTextLayout {
    private HudTextLayout() {
    }

    public static String characterBadge(String policy, String character) {
        if (!"OPEN".equals(policy)) {
            return "";
        }
        return character == null || character.isBlank() ? "[?] "
                : "[" + Character.toUpperCase(character.charAt(0)) + "] ";
    }

    public static String standingsLine(ControlMessage.StandingsRow row, String policy) {
        return "%2d %-8s %s%s".formatted(row.rank(), row.displayName(),
                characterBadge(policy, row.character()),
                TimeAttackTimeFormat.frames(row.bestTimeFrames()));
    }

    public static List<String> podiumLines(List<ControlMessage.StandingsRow> podiumRows,
                                           int localRank,
                                           List<ControlMessage.StandingsRow> standings,
                                           int localSlot, String policy) {
        List<String> lines = new ArrayList<>();
        lines.add("ROUND OVER");
        podiumRows.stream().limit(3).map(row -> standingsLine(row, policy))
                .forEach(lines::add);
        lines.add("");
        if (localRank < 0) {
            lines.add("YOU: no time");
        } else {
            ControlMessage.StandingsRow local = standings.stream()
                    .filter(row -> localSlot >= 0 ? row.slot() == localSlot
                            : row.rank() == localRank)
                    .findFirst().orElse(null);
            lines.add("YOU: #" + localRank + (local == null ? ""
                    : " " + TimeAttackTimeFormat.frames(local.bestTimeFrames())));
        }
        return List.copyOf(lines);
    }

    public static List<String> voteLines(List<String> options,
                                         List<ControlMessage.VoteCount> counts,
                                         long remainingMillis,
                                         Function<String, String> labeler) {
        List<String> lines = new ArrayList<>();
        lines.add("NEXT TRACK - VOTE 1-3 (" + Math.max(0, remainingMillis / 1000) + "s)");
        for (int index = 0; index < options.size(); index++) {
            String key = options.get(index);
            int votes = counts.stream().filter(count -> count.trackKey().equals(key))
                    .mapToInt(ControlMessage.VoteCount::votes).findFirst().orElse(0);
            lines.add((index + 1) + " " + labeler.apply(key) + "   " + votes + " votes");
        }
        return List.copyOf(lines);
    }

    public static String voteResultLine(String key, Function<String, String> labeler) {
        return key == null ? "" : "NEXT: " + labeler.apply(key);
    }
}
