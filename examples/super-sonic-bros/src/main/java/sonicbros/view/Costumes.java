package sonicbros.view;

/**
 * Alternate colours, so the same fighter can appear twice and teams read at a glance. Each
 * recipe recolours one hue band: {hue centre, range, shift, saturation x, brightness x}, with an
 * optional second band.
 */
public final class Costumes {
    public static final int COUNT = 4;

    private Costumes() { }

    public static String name(String fighter, int costume) {
        return switch (fighter + ":" + costume) {
            case "sonic:1" -> "EMERALD";
            case "sonic:2" -> "SHADOW";
            case "sonic:3" -> "GOLDEN";
            case "tails:1" -> "SILVER";
            case "tails:2" -> "SKY";
            case "tails:3" -> "ROSE";
            case "knuckles:1" -> "2P GREEN";
            case "knuckles:2" -> "AMETHYST";
            case "knuckles:3" -> "MIDNIGHT";
            default -> costume == 0 ? "CLASSIC" : costume == 1 ? "VERDANT" : costume == 2 ? "COBALT" : "OBSIDIAN";
        };
    }

    static double[] recipe(String fighter, int costume) {
        return switch (fighter) {
            case "sonic" -> switch (costume) {
                case 1 -> new double[] {225, 40, -95, 1.0, 0.95};
                case 2 -> new double[] {225, 40, 0, 0.25, 0.45};
                default -> new double[] {225, 40, -175, 1.0, 1.25};
            };
            case "tails" -> switch (costume) {
                case 1 -> new double[] {32, 22, 0, 0.1, 1.1};
                case 2 -> new double[] {32, 22, 170, 0.9, 1.0};
                default -> new double[] {32, 22, -60, 0.8, 1.15};
            };
            case "knuckles" -> switch (costume) {
                case 1 -> new double[] {355, 25, 125, 1.0, 0.95, 120, 30, 230, 1.0, 1.0};
                case 2 -> new double[] {355, 25, -75, 1.0, 1.0};
                default -> new double[] {355, 25, 0, 0.3, 0.45};
            };
            case "mecha" -> switch (costume) {
                case 1 -> new double[] {245, 40, -120, 1.0, 1.0};
                case 2 -> new double[] {245, 40, 120, 1.0, 1.1};
                default -> new double[] {245, 40, 0, 0.2, 0.6};
            };
            case "robotnik" -> switch (costume) {
                case 1 -> new double[] {0, 30, 120, 1.0, 1.0};
                case 2 -> new double[] {0, 30, 220, 1.0, 1.0};
                default -> new double[] {0, 30, 0, 0.2, 0.5};
            };
            case "eggrobo" -> switch (costume) {
                case 1 -> new double[] {0, 35, 120, 1.0, 1.0};
                case 2 -> new double[] {0, 35, 215, 1.0, 1.05};
                default -> new double[] {0, 35, 0, 0.15, 0.5};
            };
            default -> switch (costume) {
                case 1 -> new double[] {10, 40, 110, 1.0, 1.0};
                case 2 -> new double[] {10, 40, 200, 1.0, 1.0};
                default -> new double[] {10, 40, 0, 0.2, 0.55};
            };
        };
    }

    /** Player colours: P1 red, P2 blue, P3 yellow, P4 green; CPUs grey. */
    public static int playerColor(int port, boolean cpu) {
        if (cpu) return 0xFF9098A8;
        return switch (port) {
            case 0 -> 0xFFF03838;
            case 1 -> 0xFF3880F8;
            case 2 -> 0xFFF8D020;
            default -> 0xFF38D050;
        };
    }

    /** Team colours: red, blue, green. */
    public static int teamColor(int team) {
        return switch (team % 3) {
            case 0 -> 0xFFF03838;
            case 1 -> 0xFF3880F8;
            default -> 0xFF38D050;
        };
    }
}
