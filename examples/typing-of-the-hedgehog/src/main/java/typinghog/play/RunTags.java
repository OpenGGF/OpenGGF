package typinghog.play;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.level.objects.ObjectInstance;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import typinghog.core.Prompt;
import typinghog.core.Scorecard;
import typinghog.core.TypingField;

/**
 * Bonus words on the act's own badniks while Sonic runs. The rail run is fixed, so Sonic smashes
 * these badniks whatever the player types; typing a badnik's word before he reaches it tags it,
 * and a tagged badnik pays a bonus when it is destroyed. Only reads native objects: tagging never
 * changes the run.
 */
final class RunTags {
    /** At most this many badniks carry words at once. */
    static final int MAX_TAGS = 3;
    /** Look ahead of the camera's right edge for badniks to label. */
    static final int LOOKAHEAD = 32;
    static final int TAG_POINTS = 50;
    static final int SMASH_BONUS = 200;

    /** A labelled badnik. */
    static final class Tag {
        final ObjectInstance badnik;
        final int targetId;
        final Prompt prompt;
        boolean tagged;
        int x;
        int y;
        Tag(ObjectInstance badnik, int targetId, Prompt prompt) { this.badnik = badnik; this.targetId = targetId; this.prompt = prompt; }
    }

    private final Map<ObjectInstance, Tag> tags = new IdentityHashMap<>();
    private final List<int[]> bursts = new ArrayList<>();

    List<Tag> tags() { return List.copyOf(tags.values()); }
    List<int[]> bursts() { return bursts; }

    void clear(TypingField field) {
        for (Tag tag : tags.values()) if (!tag.tagged) field.remove(tag.targetId);
        tags.clear();
        bursts.clear();
    }

    /** One advanced run row: label new badniks ahead, settle ones that left. */
    void advance(Course course) {
        var camera = GameServices.camera();
        int left = camera.getX(), right = camera.getX() + camera.getWidth() + LOOKAHEAD;
        int top = camera.getY() - 32, bottom = camera.getY() + 224 + 32;
        var field = course.field();
        List<ObjectInstance> seen = new ArrayList<>();
        for (ObjectInstance object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (!isBadnik(object) || object.isDestroyed()) continue;
            int x = object.getX(), y = object.getY();
            if (x < left || x > right || y < top || y > bottom) continue;
            seen.add(object);
            Tag tag = tags.get(object);
            if (tag == null && tags.size() < MAX_TAGS && x > course.playerX() + 24) {
                Prompt prompt = Encounter.pickPrompt(course, 1, false);
                int id = field.add(prompt, Math.max(1, (x - course.playerX()) / 4));
                tag = new Tag(object, id, prompt);
                tags.put(object, tag);
            }
            if (tag != null) {
                tag.x = x;
                tag.y = y;
                if (!tag.tagged) field.setUrgency(tag.targetId, Math.max(1, Math.abs(x - course.playerX()) / 4));
            }
        }
        for (Tag tag : List.copyOf(tags.values())) {
            if (seen.contains(tag.badnik)) continue;
            tags.remove(tag.badnik);
            if (tag.tagged && tag.badnik.isDestroyed()) {
                course.card().add(SMASH_BONUS);
                bursts.add(new int[] {tag.x, tag.y, 0});
            } else if (!tag.tagged) {
                field.remove(tag.targetId);
            }
        }
        bursts.removeIf(burst -> ++burst[2] > 30);
    }

    /** A typed character's outcome during a run. */
    void onEvent(Course course, TypingField.Event event, int codePoint) {
        switch (event.kind()) {
            case LOCK, HIT -> {
                course.card().keystroke();
                course.keyStats().hit(codePoint);
                course.play(event.kind() == TypingField.Kind.LOCK ? Sounds.Cue.LOCK : Sounds.Cue.KEY);
            }
            case COMPLETE -> {
                course.card().keystroke();
                course.keyStats().hit(codePoint);
                for (Tag tag : tags.values()) {
                    if (tag.targetId != event.target()) continue;
                    tag.tagged = true;
                    course.card().add(Math.round(TAG_POINTS * Scorecard.multiplier(course.card().chain())));
                    course.chainMilestone(course.card().chain());
                    course.play(Sounds.Cue.RING);
                }
            }
            case ERROR -> {
                course.card().error();
                course.play(Sounds.Cue.ERROR);
                course.hud().shake();
            }
            case RELEASE, IGNORED -> { }
        }
    }

    static boolean isBadnik(ObjectInstance object) {
        return object instanceof AbstractBadnikInstance || object.getClass().getName().contains(".badniks.");
    }
}
