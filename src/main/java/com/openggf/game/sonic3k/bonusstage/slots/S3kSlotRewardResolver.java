package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRewardResolver {

    private S3kSlotRewardResolver() {
    }

    public static int pickFixedRow(int rolledByte) {
        return pickFixedRow(S3kSlotResources.fromActiveRom(), rolledByte);
    }

    public static int pickFixedRow(S3kSlotResources romData, int rolledByte) {
        byte[] rows = romData.targetRows();
        int remaining = rolledByte & 0xFF;
        for (int row = 0; row < rows.length; row += 3) {
            int threshold = rows[row] & 0xFF;
            if (threshold == 0xFF) {
                return -1;
            }
            if (remaining < threshold) {
                return row / 3;
            }
            remaining -= threshold;
        }
        return -1;
    }
}
