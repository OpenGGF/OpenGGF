package com.openggf.editor.project;

import com.openggf.editor.persistence.FullLevelExporter;
import com.openggf.game.GameId;
import com.openggf.game.modzone.ModObjectZoneSet;
import com.openggf.level.Level;
import java.util.Objects;

/** Authored identity and launch properties, independent of stock gameplay saves. */
public record EditorProjectMetadata(GameId gameId, String name, int sourceZone, int sourceAct,
        String sourceFingerprint, int startX, int startY, int minX, int maxX, int minY, int maxY,
        FullLevelExporter.ExportMusic music, ModObjectZoneSet objectZoneSet) {
    public EditorProjectMetadata {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sourceFingerprint, "sourceFingerprint");
        Objects.requireNonNull(music, "music");
        if (gameId.cartridgeGame().isEmpty()) throw new IllegalArgumentException("Choose Sonic 1, Sonic 2 or Sonic 3&K");
        if (name.isBlank() || name.length() > 256 || sourceFingerprint.length() > 512
                || sourceZone < 0 || sourceAct < 0 || sourceAct > 255) {
            throw new IllegalArgumentException("Project name/source identity exceeds its bounded format");
        }
        EditorProjectLevel.validateBounds(minX, maxX, minY, maxY);
        if (startX < minX || startX > maxX || startY < minY || startY > maxY) {
            throw new IllegalArgumentException("Project start must lie inside level bounds");
        }
        if (gameId == GameId.S3K && objectZoneSet == null) {
            throw new IllegalArgumentException("Sonic 3&K projects require an explicit S3KL or SKL object family");
        }
        if (gameId != GameId.S3K && objectZoneSet != null) {
            throw new IllegalArgumentException("Object-zone family applies only to Sonic 3&K projects");
        }
    }

    public EditorProjectMetadata withName(String value) {
        return new EditorProjectMetadata(gameId, value, sourceZone, sourceAct, sourceFingerprint,
                startX, startY, minX, maxX, minY, maxY, music, objectZoneSet);
    }

    public EditorProjectMetadata withStart(int x, int y) {
        return new EditorProjectMetadata(gameId, name, sourceZone, sourceAct, sourceFingerprint,
                x, y, minX, maxX, minY, maxY, music, objectZoneSet);
    }

    /** Resizing keeps the start valid by clamping it to the new bounds. */
    public EditorProjectMetadata withBounds(int x0, int x1, int y0, int y1) {
        return new EditorProjectMetadata(gameId, name, sourceZone, sourceAct, sourceFingerprint,
                Math.clamp(startX, x0, x1), Math.clamp(startY, y0, y1), x0, x1, y0, y1, music, objectZoneSet);
    }

    public EditorProjectMetadata withMusic(FullLevelExporter.ExportMusic value) {
        return new EditorProjectMetadata(gameId, name, sourceZone, sourceAct, sourceFingerprint,
                startX, startY, minX, maxX, minY, maxY, value, objectZoneSet);
    }

    public void validateAgainst(Level level) {
        Objects.requireNonNull(level, "level");
        int side = gameId == GameId.S1 ? 16 : 8;
        if (level.getChunksPerBlockSide() != side || level.getBlockPixelSize() != side * 16) {
            throw new IllegalArgumentException("Project block geometry does not match " + gameId.code());
        }
        if (maxX >= (long) level.getMap().getWidth() * level.getBlockPixelSize()
                || maxY >= (long) level.getMap().getHeight() * level.getBlockPixelSize()) {
            throw new IllegalArgumentException("Project bounds must fit its map dimensions");
        }
    }
}
