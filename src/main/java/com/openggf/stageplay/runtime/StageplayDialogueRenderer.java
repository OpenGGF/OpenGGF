package com.openggf.stageplay.runtime;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.stageplay.StageplayDialogue;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;

/** Built-in deterministic dialogue presentation; profiles and decorations are plain scene data. */
final class StageplayDialogueRenderer {
    private StageplayDialogueRenderer() { }

    static void draw(StageplayDocument document, StageplayTimeline.Frame frame, SceneCanvas canvas,
                     StageplayResources resources) {
        for (var pose : frame.dialogue()) {
            var cue = pose.cue(); var skin = resources.dialogueSkin(cue.id());
            var layout = resources.dialogueLayout(cue.id());
            if (skin.pointer() && !cue.speakerActor().isEmpty()) pointer(document, frame, canvas, cue, skin);
            if (skin.decorationAsset().isEmpty()) {
                canvas.fill(cue.x(), cue.y(), cue.width(), cue.height(), skin.borderArgb());
                canvas.fill(cue.x() + 1, cue.y() + 1, cue.width() - 2, cue.height() - 2, skin.panelArgb());
            } else {
                canvas.fill(cue.x(), cue.y(), cue.width(), cue.height(), skin.panelArgb());
                nineSlice(canvas, resources.dialogueImage(skin.decorationAsset()), cue.x(), cue.y(),
                        cue.width(), cue.height(), skin.decorationBorder());
            }
            var previousClip = canvas.clipBounds();
            try {
                int left = cue.x() + skin.padding(), top = cue.y() + skin.padding();
                int right = cue.x() + cue.width() - skin.padding(), bottom = cue.y() + cue.height() - skin.padding();
                if (previousClip.isPresent()) {
                    var clip = previousClip.get();
                    left = Math.max(left, clip.x()); top = Math.max(top, clip.y());
                    right = Math.min(right, clip.x() + clip.width());
                    bottom = Math.min(bottom, clip.y() + clip.height());
                }
                canvas.clip(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
                if (!cue.portraitAsset().isEmpty()) {
                    var portrait = resources.dialogueImage(cue.portraitAsset());
                    float scale = Math.min(StageplayDialogue.PORTRAIT_SIZE / (float) portrait.width(),
                            StageplayDialogue.PORTRAIT_SIZE / (float) portrait.height());
                    float width = portrait.width() * scale, height = portrait.height() * scale;
                    canvas.drawRegion(portrait, 0, 0, portrait.width(), portrait.height(),
                            cue.x() + skin.padding() + (StageplayDialogue.PORTRAIT_SIZE - width) / 2,
                            cue.y() + skin.padding() + (StageplayDialogue.PORTRAIT_SIZE - height) / 2,
                            width, height, SceneDraw.plain());
                }
                canvas.text(layout.speakerName(), layout.nameX(), layout.nameY(), skin.nameArgb());
                var rows = StageplayDialogue.visibleRows(layout, pose.visibleText());
                for (int index = 0; index < rows.size(); index++)
                    canvas.text(rows.get(index), layout.textX(), layout.textY() + index * StageplayDialogue.LINE_HEIGHT,
                            skin.textArgb());
            } finally {
                if (previousClip.isPresent()) {
                    var clip = previousClip.get(); canvas.clip(clip.x(), clip.y(), clip.width(), clip.height());
                } else canvas.unclip();
            }
        }
    }

    private static void nineSlice(SceneCanvas canvas, SceneImage image, int x, int y, int width, int height, int border) {
        int[] sourceX = {0, border, image.width() - border, image.width()};
        int[] sourceY = {0, border, image.height() - border, image.height()};
        int[] targetX = {x, x + border, x + width - border, x + width};
        int[] targetY = {y, y + border, y + height - border, y + height};
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            int sw = sourceX[column + 1] - sourceX[column], sh = sourceY[row + 1] - sourceY[row];
            if (sw == 0 || sh == 0) continue;
            canvas.drawRegion(image, sourceX[column], sourceY[row], sw, sh,
                    targetX[column], targetY[row], targetX[column + 1] - targetX[column],
                    targetY[row + 1] - targetY[row], SceneDraw.plain());
        }
    }

    private static void pointer(StageplayDocument document, StageplayTimeline.Frame frame, SceneCanvas canvas,
                                StageplayDocument.DialogueCue cue, StageplayDocument.DialogueSkin skin) {
        var speaker = frame.actors().stream().filter(actor -> actor.actorId().equals(cue.speakerActor())
                && actor.visible()).findFirst().orElse(null);
        double worldX, worldY;
        if (speaker != null) { worldX = speaker.x(); worldY = speaker.y(); }
        else {
            var object = frame.world() == null ? null : frame.world().objects().stream()
                    .filter(value -> value.actorId().equals(cue.speakerActor())).findFirst().orElse(null);
            if (object == null) return; // Unresolved/hidden/removed objects never get an invented tail position.
            worldX = object.x(); worldY = object.y();
        }
        int x = (int) Math.round((worldX - frame.camera().x()) * frame.camera().zoom());
        int y = (int) Math.round((worldY - frame.camera().y()) * frame.camera().zoom());
        if (x < 0 || x >= document.width() || y < 0 || y >= document.height()) return;
        int edgeY, direction;
        if (y < cue.y()) { edgeY = cue.y(); direction = -1; }
        else if (y >= cue.y() + cue.height()) { edgeY = cue.y() + cue.height() - 1; direction = 1; }
        else return;
        int centre = Math.clamp(x, cue.x() + 8, cue.x() + cue.width() - 9);
        for (int row = 1; row <= 8; row++) {
            int half = 8 - row;
            canvas.fill(centre - half, edgeY + direction * row, half * 2 + 1, 1, skin.borderArgb());
            if (half > 1) canvas.fill(centre - half + 1, edgeY + direction * row,
                    half * 2 - 1, 1, skin.panelArgb());
        }
    }
}
