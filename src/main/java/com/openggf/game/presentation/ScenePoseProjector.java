package com.openggf.game.presentation;

import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.sprites.animation.ScriptedVelocityAnimationProfile;
import com.openggf.sprites.art.SpriteArtSet;
import java.util.List;
import java.util.Map;

/** Locally decoded native mappings selected from immutable pose values, never a playable object. */
final class ScenePoseProjector {
    private final Map<String, SpriteArtSet> players, dust;
    ScenePoseProjector(Map<String, SpriteArtSet> players, Map<String, SpriteArtSet> dust) {
        this.players = Map.copyOf(players); this.dust = Map.copyOf(dust);
    }

    void project(List<ScenePresentationFrame.Tile> tiles, ScenePlayerPose player, int cameraX, int cameraY, boolean priority) {
        var pose = player.pose();
        var set = players.get(player.character());
        if (set == null || !(set.animationProfile() instanceof ScriptedVelocityAnimationProfile profile)) {
            throw new IllegalStateException("Native pose art unavailable");
        }
        int animation = pose.kind() == PlayerPresentationPose.Kind.DUCK ? profile.getDuckAnimId() : profile.getSpindashAnimId();
        var script = set.animationSet().getScript(animation);
        if (script == null || script.frames().isEmpty()) throw new IllegalStateException("Native pose script unavailable");
        long elapsedFrames = pose.tick() / Math.max(1, script.delay() + 1);
        // A held duck stops at its settled mapping. S2 SonAni_Duck's $FE,1
        // repeats only $4D after $4C; TailsAni_Duck has the single $5B frame.
        int frameIndex = pose.kind() == PlayerPresentationPose.Kind.DUCK
                ? (int) Math.min(elapsedFrames, script.frames().size() - 1)
                : (int) (elapsedFrames % script.frames().size());
        int frame = script.frames().get(frameIndex);
        projectArtFrame(tiles, "player/" + player.character(), set, frame, player.centreX() - cameraX,
                player.centreY() - cameraY, pose.facing() < 0, priority);
        if (pose.kind() == PlayerPresentationPose.Kind.SPINDASH) {
            // Existing SpindashDustController DASH_FRAMES $A..$10, FRAME_DELAY=1.
            int dustFrame = 0xA + (int) ((pose.tick() / 2) % 7);
            var dustSet = dust.get(player.character());
            if (dustSet != null) projectArtFrame(tiles, "dust/" + player.character(), dustSet, dustFrame,
                    player.centreX() - cameraX, player.centreY() - cameraY
                            + ("tails".equals(player.character()) ? -4 : 0), pose.facing() < 0, priority);
        }
    }

    private void projectArtFrame(List<ScenePresentationFrame.Tile> tiles, String recipe, SpriteArtSet set,
                                 int frame, int x, int y, boolean flip, boolean priority) {
        if (frame < 0 || frame >= set.mappingFrames().size() || frame >= set.dplcFrames().size()) {
            throw new IllegalStateException("Native pose mapping out of range");
        }
        int[] slots = new int[Math.max(1, set.bankSize())];
        java.util.Arrays.fill(slots, -1);
        var requests = set.dplcFrames().get(frame).requests();
        if (requests.isEmpty()) {
            requests = set.dplcFrames().stream().filter(value -> !value.requests().isEmpty()).findFirst().orElseThrow().requests();
        }
        int destination = 0;
        for (var request : requests) {
            int first = request.destinationOffset() < 0 ? destination : request.destinationOffset();
            for (int index = 0; index < request.count(); index++) {
                if (first + index >= slots.length) throw new IllegalStateException("Pose DPLC exceeds bank");
                slots[first + index] = request.startTile() + index;
            }
            destination = first + request.count();
        }
        SpritePieceRenderer.renderPieces(set.mappingFrames().get(frame).pieces(), x, y, 0, set.paletteIndex(), flip, false,
                (id, h, v, pal, drawX, drawY) -> {
                    if (id >= slots.length || slots[id] < 0) throw new IllegalStateException("Missing pose DPLC tile");
                    tiles.add(new ScenePresentationFrame.Tile(ScenePresentationFrame.Layer.PLAYER,
                            new ScenePresentationFrame.ArtReference(recipe, slots[id]), pal, h, v, priority,
                            drawX, drawY, 8, 8, 0, 8, 15, 255));
                });
    }

}
