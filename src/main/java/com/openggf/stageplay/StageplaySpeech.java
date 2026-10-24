package com.openggf.stageplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import static com.openggf.stageplay.StageplayDocument.*;

/** Pure reveal-to-speech plan, shared by forward playback and the directing CLI. Never plays audio. */
public final class StageplaySpeech {
    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private StageplaySpeech() { }

    public record Blip(String dialogueId, int frame, int grapheme, int pitch, int lengthFrames, SpeakerVoice voice) { }

    public static SpeakerVoice voice(StageplayDocument document, String actorId, String offscreenName) {
        return document.voices().stream().filter(v -> actorId.isEmpty()
                        ? v.speakerActor().isEmpty() && v.speakerName().equals(offscreenName)
                        : v.speakerActor().equals(actorId)).findFirst()
                .orElseGet(() -> defaultVoice(document, actorId, offscreenName));
    }

    public static SpeakerVoice defaultVoice(StageplayDocument document, String actorId, String offscreenName) {
        var actor = document.actors().stream().filter(a -> a.id().equals(actorId)).findFirst().orElse(null);
        if (actor != null) return switch (actor.character()) {
            case "tails" -> settings(actorId, "", VoiceTone.CHIME, VoiceRhythm.LETTER, 84, 3, 2, 72);
            case "knuckles" -> settings(actorId, "", VoiceTone.HOLLOW, VoiceRhythm.WORD, 55, 2, 8, 88);
            default -> settings(actorId, "", VoiceTone.SQUARE, VoiceRhythm.WORD, 72, 5, 5, 80);
        };
        var object = document.objectActors().stream().filter(a -> a.id().equals(actorId)).findFirst().orElse(null);
        if (object != null) {
            var entry = StageplayObjectCatalog.entry(object.gameId(), object.zone(), object.act(), object.objectId());
            if (entry.role() == com.openggf.level.objects.ObjectRegistryCatalog.Role.BOSS)
                return settings(actorId, "", VoiceTone.HOLLOW, VoiceRhythm.WORD, 48, 3, 11, 90);
            // Source identity, never editable role/display names, gives ordinary objects a repeatable register.
            int identity = object.objectId() + object.subtype() * 7;
            var tones = new VoiceTone[]{VoiceTone.HOLLOW, VoiceTone.BUZZ, VoiceTone.CHIME};
            boolean letters = (identity & 2) != 0;
            return settings(actorId, "", tones[(identity / 4) % tones.length],
                    letters ? VoiceRhythm.LETTER : VoiceRhythm.WORD, 65 + identity % 12, 4, letters ? 3 : 8, 80);
        }
        if (!actorId.isEmpty()) throw new IllegalArgumentException("Unknown voice speaker: " + actorId);
        return settings("", offscreenName, VoiceTone.SQUARE, VoiceRhythm.WORD, 64, 2, 7, 75);
    }

    private static SpeakerVoice settings(String actor, String name, VoiceTone tone, VoiceRhythm rhythm,
                                         int pitch, int variation, int length, int level) {
        return new SpeakerVoice(actor, name, tone, rhythm, pitch, variation, length, level);
    }

    public static List<Blip> plan(StageplayDocument document) {
        var result = new ArrayList<Blip>();
        for (var cue : document.dialogue()) {
            var voice = voice(document, cue.speakerActor(), cue.speakerActor().isEmpty() ? cue.speakerName() : "");
            if (voice.tone() == VoiceTone.OFF || voice.level() == 0) continue;
            var reveals = StageplayDialogue.revealFrames(document, cue);
            var matcher = GRAPHEME.matcher(cue.text());
            var line = new ArrayList<Blip>();
            boolean inWord = false;
            int index = 0, ordinal = 0, previousFrame = -1;
            while (matcher.find()) {
                String cluster = matcher.group();
                boolean spoken = cluster.codePoints().anyMatch(Character::isLetterOrDigit);
                int at = cue.startFrame() + reveals.get(index);
                if (spoken && (voice.rhythm() == VoiceRhythm.LETTER || !inWord) && at != previousFrame) {
                    // Integer hash: stable across runs, seeking, scene edits and machine RNG state.
                    int seed = cue.text().hashCode() * 31 + ordinal++ * 0x9e3779b9;
                    int spread = voice.variation();
                    int pitch = Math.clamp(voice.pitch() + Math.floorMod(seed ^ (seed >>> 16), 2 * spread + 1) - spread, 48, 96);
                    line.add(new Blip(cue.id(), at, index, pitch, voice.lengthFrames(), voice));
                    previousFrame = at;
                }
                if (spoken) inWord = true;
                else if (!cluster.equals("'") && !cluster.equals("’") && !cluster.equals("-")) inWord = false;
                index++;
            }
            for (int i = 0; i < line.size(); i++) {
                var blip = line.get(i);
                int end = i + 1 < line.size() ? line.get(i + 1).frame() : cue.endFrame();
                // Word/letter runs never stack their own tails, and nothing rings past the dialogue interval.
                result.add(new Blip(blip.dialogueId(), blip.frame(), blip.grapheme(), blip.pitch(),
                        Math.min(blip.lengthFrames(), end - blip.frame()), voice));
                if (result.size() > 100_000) throw new IllegalArgumentException("Scene speech exceeds 100000 blips; split the scene");
            }
        }
        result.sort(Comparator.comparingInt(Blip::frame));
        return List.copyOf(result);
    }
}
