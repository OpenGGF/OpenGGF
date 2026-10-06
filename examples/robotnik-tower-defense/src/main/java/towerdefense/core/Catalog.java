package towerdefense.core;

import java.util.ArrayList;
import java.util.List;

/** Mod balancing, not an attempt to reproduce the original badniks' behavior. */
public final class Catalog {
    public static final int SNALE = 0, MORTAR = 1, BUGGERNAUT = 2, SPIKER = 3, ORBINAUT = 4, EGG_ROBO = 5;
    public static final int PICKETER = 0, COURIER = 1, SHIELD = 2, FLYER = 3, ORGANISER = 4, SABOTEUR = 5;
    public static final int WAVES = 15;

    private Catalog() { }

    public record Defense(String name, String art, int cost, int damage, int cooldown,
            int range, int color, String description) { }

    public static Defense defense(int kind) {
        return switch (kind) {
            case SNALE -> new Defense("SNALE BLASTER", "snale", 30, 7, 38, 82, 0xFFFFC857,
                    "CHEAP DIRECT FIRE. GROUND + AIR.");
            case MORTAR -> new Defense("MONKEY MORTAR", "monkey", 45, 12, 80, 102, 0xFFFF9060,
                    "GROUND SPLASH. BREAKS UP CROWDS.");
            case BUGGERNAUT -> new Defense("BUGGERNAUT", "buggernaut", 55, 5, 15, 110, 0xFF59CFFF,
                    "RAPID ANTI-AIR. IGNORES GROUND.");
            case SPIKER -> new Defense("TURBO SPIKER", "spiker", 65, 23, 68, 110, 0xFFF895D0,
                    "PIERCING SHOTS. BEATS SHIELDS.");
            case ORBINAUT -> new Defense("ORBINAUT", "orbinaut", 60, 3, 40, 68, 0xFF8EF0AF,
                    "AREA DAMAGE + SLOW. SHORT RANGE.");
            case EGG_ROBO -> new Defense("EGG ROBO", "egg_robo", 100, 14, 35, 130, 0xFFE7C8FF,
                    "LONG RANGE. CHAINS TO 3 TARGETS.");
            default -> throw new IllegalArgumentException("Unknown defense " + kind);
        };
    }

    public static String birdName(int kind) {
        return switch (kind) {
            case COURIER -> "COURIER";
            case SHIELD -> "SHIELD CARRIER";
            case FLYER -> "FLYING PICKET";
            case ORGANISER -> "ORGANISER";
            case SABOTEUR -> "SABOTEUR";
            default -> "PICKETER";
        };
    }

    public static int birdColor(int kind) {
        return switch (kind) {
            case COURIER -> 0xFFFFD86C;
            case SHIELD -> 0xFFD1DAE5;
            case FLYER -> 0xFF9DE3FF;
            case ORGANISER -> 0xFFFF9292;
            case SABOTEUR -> 0xFFB5F084;
            default -> 0xFFFFFFFF;
        };
    }

    public record Spawn(int kind, int tick) { }
    public record Wave(String name, String tip, List<Spawn> spawns) { }

    /** A fixed learnable campaign; later waves combine already introduced roles. */
    public static Wave wave(int number) {
        if (number < 1 || number > WAVES) throw new IllegalArgumentException("Wave " + number);
        String name = switch (number) {
            case 1 -> "THE PICKET LINE";
            case 2 -> "EXPRESS DELIVERY";
            case 3 -> "SAFETY EQUIPMENT";
            case 4 -> "AIRBORNE SOLIDARITY";
            case 5 -> "WORK TO RULE";
            case 6 -> "THE SHOP STEWARD";
            case 7 -> "UNPLUG THE MACHINES";
            case 8 -> "FLYING SQUAD";
            case 9 -> "CLOSED RANKS";
            case 10 -> "OVERTIME REFUSED";
            case 11 -> "COORDINATED ACTION";
            case 12 -> "SPECIAL DELIVERY";
            case 13 -> "SIT-IN AT THE DOOR";
            case 14 -> "ALL OUT TOMORROW";
            default -> "THE GENERAL STRIKE";
        };
        String tip = switch (number) {
            case 1 -> "PICKETERS: START WITH DIRECT FIRE.";
            case 2 -> "COURIERS ARE FAST. SLOW THEM DOWN.";
            case 3 -> "SHIELDS: PIERCING SHOTS IGNORE ARMOR.";
            case 4, 8 -> "FLYERS: HIGH SITES + ANTI-AIR.";
            case 6 -> "ORGANISERS SPEED UP NEARBY COMRADES.";
            case 7 -> "SABOTEURS JAM MACHINES THEY PASS.";
            case 9 -> "SHIELD LINES: SPIKERS + MORTARS.";
            case 15 -> "EVERY ROLE. KEEP YOUR BOMB READY!";
            default -> "MIX YOUR DEFENSES. CHECK BOTH ROUTES.";
        };
        List<Spawn> spawns = new ArrayList<>();
        int count = 7 + number * 3;
        int spacing = Math.max(22, 51 - number * 2);
        for (int i = 0; i < count; i++) {
            int kind = PICKETER;
            if (number == 2 && i % 3 == 2) kind = COURIER;
            if (number == 3 && i % 3 == 0) kind = SHIELD;
            if (number == 4 && i % 2 == 0) kind = FLYER;
            if (number >= 5) {
                int slot = (i + number) % 8;
                if (slot == 0) kind = SHIELD;
                if (slot == 2) kind = COURIER;
                if (slot == 4 || number == 8 && slot == 5) kind = FLYER;
                if (number >= 6 && slot == 6) kind = ORGANISER;
                if (number >= 7 && slot == 7) kind = SABOTEUR;
                if (number == 9 && i % 2 == 0) kind = SHIELD;
            }
            spawns.add(new Spawn(kind, i * spacing));
        }
        return new Wave(name, tip, List.copyOf(spawns));
    }
}
