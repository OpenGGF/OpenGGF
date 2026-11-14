package com.openggf.level.objects;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.animation.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.tools.modsdk.PlayableSheetWriter;
import java.io.*;
import java.util.*;

/** Synthetic canonical sheet: no MICA assets or ROM input. Exercises the real writer/loader/binder. */
public final class HomingSheetFixture {
    private HomingSheetFixture() { }
    public static SpriteArtSet sheet(CanonicalAnimation... strikes) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFS"); out.writeShort(1); out.writeInt(1); out.write(new byte[32]);
            out.writeShort(24);
            for (int f = 0; f < 24; f++) {
                out.writeShort(1); out.writeShort(1);
                out.writeShort(f); out.writeShort(0); out.writeByte(1); out.writeByte(1);
                out.writeInt(0); out.writeByte(0); out.writeByte(0);
            }
            out.writeByte(1); out.writeByte(0); out.write(new byte[32]);
        }
        var frames = new ArrayList<PlayableSheetReader.Frame>();
        for (int f = 0; f < 24; f++) frames.add(new PlayableSheetReader.Frame(0, 0, 1, 1,
                List.of(new PlayableSheetReader.DplcRun(0, 1, 0))));
        var table = new TreeMap<String, PlayableSheetReader.Animation>();
        var poses = new ArrayList<>(List.of(CanonicalAnimation.WAIT));
        poses.addAll(List.of(strikes));
        for (var pose : poses) {
            int base = pose == CanonicalAnimation.WAIT ? 0 :
                    3 * (pose.ordinal() - CanonicalAnimation.HOMING_DIVE.ordinal() + 1);
            table.put(pose.name(), new PlayableSheetReader.Animation(List.of(
                    new PlayableSheetReader.AnimationStep(base, 3, false),
                    new PlayableSheetReader.AnimationStep(base + 1, 3, false),
                    new PlayableSheetReader.AnimationStep(base + 2, 3, false)),
                    false, SpriteAnimationEndAction.LOOP, "WAIT", List.of()));
        }
        var source = PlayableSheetMaterializer.read(PlayableSheetWriter.write(new PlayableSheetReader.PlayableSheet(
                bytes.toByteArray(), new PlayableSheetReader.Meta(0, 1, 0), frames, table, Map.of()))).art();
        return PlayableSheetAnimationBinding.bind(source, new Sonic2GameModule().getDonorCapabilities(),
                new ScriptedVelocityAnimationProfile());
    }
}
