package openggf.timeattack.mp;

import com.openggf.net.chat.EnglishQuickChat;
import com.openggf.net.chat.QuickChatContent;
import openggf.timeattack.TimeAttackTrackCatalog;

/** Local English presentation of semantic room messages; the host owns their track context. */
public final class TimeAttackQuickChat {
    private TimeAttackQuickChat() { }

    public static String format(QuickChatContent message) {
        return EnglishQuickChat.text(message.phrase(), zoneName(message.gameId(), message.zone(), message.act()));
    }

    static String zoneName(String game, int zone, int act) {
        String label = TimeAttackTrackCatalog.eligible(game, zone, act)
                .map(TimeAttackTrackCatalog.Track::label).orElseGet(() ->
                        TimeAttackTrackCatalog.eligibleFor(game).stream().filter(track -> track.zone() == zone)
                                .map(TimeAttackTrackCatalog.Track::label).findFirst().orElse(""));
        if (label.isEmpty()) return "the zone";
        // Catalogue labels end in the act number, or BOSS for a separately loaded boss act.
        String name = label.replaceFirst(" (?:[0-9]+|BOSS)$", "");
        StringBuilder title = new StringBuilder();
        for (String word : name.toLowerCase(java.util.Locale.ROOT).split(" ")) {
            if (!title.isEmpty()) title.append(' ');
            if (!word.isEmpty()) title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return title.toString();
    }
}
