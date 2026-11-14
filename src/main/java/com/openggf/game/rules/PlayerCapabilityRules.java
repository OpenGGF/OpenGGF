package com.openggf.game.rules;

import com.openggf.game.ModApi;

import java.util.Arrays;

@ModApi
public record PlayerCapabilityRules(
        boolean spindashEnabled,
        short[] spindashSpeedTable,
        boolean elementalShieldsEnabled,
        boolean instaShieldEnabled,
        boolean tailsFlightEnabled,
        boolean jumpRepressClearsRollJumpBeforeAbility,
        boolean lightningShieldEnabled,
        short[] superSpindashSpeedTable,
        boolean glideAttacksEnabled,
        boolean timedChargeMoves,
        boolean rollEntryRaisesForwardSpeedToMinimum) {

    /**
     * Sonic CD's charge model: a frame timer ({@code oPlayerCharge}) that the object
     * entry advances every frame once armed and caps at 45 for a spin dash or 30 for a
     * super peel-out (scddisasm src/Level/_Objects/Sonic/Main.asm:69-86). A spin dash
     * is armed by A/B/C while ducking and charges ground speed by 75 per frame; a
     * peel-out is armed by A/B/C while looking up and charges by 100 per frame, both to
     * twice top speed; each releases at full charge only (Main.asm:946-1121,
     * 1366-1427). The same standing tail owns the double-tap look shift, so this flag
     * also selects it. Independent of {@link #spindashEnabled()}, S2's rev counter.
     */
    public boolean timedChargeMoves() {
        return timedChargeMoves;
    }

    /**
     * Roll entry raises any non-negative ground speed below $200 to $200, and an
     * already-rolling entry still applies it. Sonic CD's {@code ObjSonic_StartRoll}
     * tests {@code bmi} then {@code cmpi.w #$200 / bcc} (scddisasm
     * src/Level/_Objects/Sonic/Main.asm:1744-1777); Sonic 1/2/3&amp;K write $200 only when
     * the speed is exactly zero and return early when already rolling. It sits beside
     * the timed charge model, whose spin dash start ($16 toward the facing) relies on
     * it; PlayerMovementRules is at its component ceiling and PlayerGroundPoseRules
     * keeps a three-component record-pattern contract. Only Sonic CD sets it.
     */
    public boolean rollEntryRaisesForwardSpeedToMinimum() {
        return rollEntryRaisesForwardSpeedToMinimum;
    }

    public PlayerCapabilityRules {
        spindashSpeedTable = copy(spindashSpeedTable);
        superSpindashSpeedTable = copy(superSpindashSpeedTable);
    }

    @Override
    public short[] spindashSpeedTable() {
        return copy(spindashSpeedTable);
    }

    @Override
    public short[] superSpindashSpeedTable() {
        return copy(superSpindashSpeedTable);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PlayerCapabilityRules other)) {
            return false;
        }
        return spindashEnabled == other.spindashEnabled
                && elementalShieldsEnabled == other.elementalShieldsEnabled
                && instaShieldEnabled == other.instaShieldEnabled
                && tailsFlightEnabled == other.tailsFlightEnabled
                && jumpRepressClearsRollJumpBeforeAbility == other.jumpRepressClearsRollJumpBeforeAbility
                && lightningShieldEnabled == other.lightningShieldEnabled
                && glideAttacksEnabled == other.glideAttacksEnabled
                && timedChargeMoves == other.timedChargeMoves
                && rollEntryRaisesForwardSpeedToMinimum == other.rollEntryRaisesForwardSpeedToMinimum
                && Arrays.equals(spindashSpeedTable, other.spindashSpeedTable)
                && Arrays.equals(superSpindashSpeedTable, other.superSpindashSpeedTable);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(spindashEnabled);
        result = 31 * result + Arrays.hashCode(spindashSpeedTable);
        result = 31 * result + Boolean.hashCode(elementalShieldsEnabled);
        result = 31 * result + Boolean.hashCode(instaShieldEnabled);
        result = 31 * result + Boolean.hashCode(tailsFlightEnabled);
        result = 31 * result + Boolean.hashCode(jumpRepressClearsRollJumpBeforeAbility);
        result = 31 * result + Boolean.hashCode(lightningShieldEnabled);
        result = 31 * result + Boolean.hashCode(glideAttacksEnabled);
        result = 31 * result + Arrays.hashCode(superSpindashSpeedTable);
        result = 31 * result + Boolean.hashCode(timedChargeMoves);
        result = 31 * result + Boolean.hashCode(rollEntryRaisesForwardSpeedToMinimum);
        return result;
    }

    @Override
    public String toString() {
        return "PlayerCapabilityRules["
                + "spindashEnabled=" + spindashEnabled
                + ", spindashSpeedTable=" + Arrays.toString(spindashSpeedTable)
                + ", elementalShieldsEnabled=" + elementalShieldsEnabled
                + ", instaShieldEnabled=" + instaShieldEnabled
                + ", tailsFlightEnabled=" + tailsFlightEnabled
                + ", jumpRepressClearsRollJumpBeforeAbility=" + jumpRepressClearsRollJumpBeforeAbility
                + ", lightningShieldEnabled=" + lightningShieldEnabled
                + ", glideAttacksEnabled=" + glideAttacksEnabled
                + ", superSpindashSpeedTable=" + Arrays.toString(superSpindashSpeedTable)
                + ", timedChargeMoves=" + timedChargeMoves
                + ", rollEntryRaisesForwardSpeedToMinimum=" + rollEntryRaisesForwardSpeedToMinimum
                + "]";
    }

    private static short[] copy(short[] table) {
        return table == null ? null : table.clone();
    }
}
