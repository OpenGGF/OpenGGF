package openggf.timeattack.mp;

import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.RunArea;
import com.openggf.mods.run.RunSpec;

/**
 * The placeholder lobby level of each game: a fenced stretch of a stock act that the players of
 * a room spawn into between rounds, behind the lobby menu. Each room uses its own game, so nobody
 * needs a ROM their room does not race. A bespoke lobby later replaces one entry here.
 *
 * <p>Positions are ROM centre coordinates; camera bounds are the camera's top-left corner. The
 * fences come from the acts' layout, start tables and level events (2026-10-22 lobby-level work):
 *
 * <ul>
 *   <li><b>S3K: Hidden Palace, the arena where Knuckles fights Sonic</b> (engine zone 22, act 1).
 *   The fight is {@code Obj_CutsceneKnuckles} subtype $28 at (0x11F0, 0x042C)
 *   ({@code HPZ_Sprites}, sonic3k.asm:203793-203794); it starts once the camera enters
 *   {@code word_63CEC} (camera X 0xFE0-0x11E0, Y 0x180-0x480; sonic3k.asm:131316-131319, tested by
 *   {@code loc_63D1A}, sonic3k.asm:131334-131375, through {@code Check_CameraInRange},
 *   sonic3k.asm:180524-180557) and then locks the camera at X 0x10E0, Y 0x380
 *   ({@code loc_85CA4}, sonic3k.asm:180575-180645). The lobby holds the same floor (flat top at
 *   Y 0x440 from X 0xEC0) with the camera at Y 0x380 and X 0xEB0-0xFD0, so the fight never starts
 *   and its music never fades. {@code HPZ_ScreenInit} (sonic3k.asm:120039-120054) rewrites the X
 *   bounds on the first event update; the engine re-applies the fence after it.</li>
 *   <li><b>S1: Green Hill 1 start</b> (zone 0, act 0). Sonic's start is (0x0050, 0x03B0)
 *   ({@code StartLocArray}, _inc/LevelSizeLoad &amp; BgScrollSpeed.asm:178). The camera's X range is
 *   exactly 0x100 wide (0-0x100) on purpose: {@code SignpostArtLoad} (sonic.asm:3186-3203) locks
 *   the left boundary at {@code v_limitright2 - $100} and queues the signpost art once the camera
 *   reaches it, unless the left boundary already sits there, so a fence any wider would be
 *   re-locked (and the art re-queued) every frame. The player's walls are 0x10-0x268, well short
 *   of the bridge pit at X 0x440; Y 0x300 is the bottom boundary {@code DLE_GHZ1} writes every
 *   frame there (_inc/DynamicLevelEvents.asm:84-98). The stretch keeps its first monitor; its
 *   Motobug starts just outside.</li>
 *   <li><b>S2: Emerald Hill 1 start</b> (zone 0, act 0). Sonic's start is (0x0060, 0x028F)
 *   (s2.asm:14827); act 1 has no level events ({@code LevEvents_EHZ}, s2.asm:20390-20393). Camera
 *   X 0-0x220 and Y 0x1C0-0x240 hold the player (walls 0x10-0x388) on the hills before the first
 *   bridge, with no pit; its Coconuts and Buzzer stay.</li>
 * </ul>
 */
public final class LobbyAreas {
    private LobbyAreas() {
    }

    /** One game's lobby: the act and fence a lobby run plays. */
    public record LobbyArea(String gameId, int zone, int act, RunArea area, String label) {
    }

    /** The lobby level of {@code gameId}, or null for a game without one. */
    public static LobbyArea forGame(String gameId) {
        if (gameId == null) {
            return null;
        }
        return switch (gameId) {
            case "s3k" -> new LobbyArea("s3k", 22, 1,
                    new RunArea(0x0F80, 0x042C, 0x0EB0, 0x0FD0, 0x0380, 0x0380), "HIDDEN PALACE");
            case "s1" -> new LobbyArea("s1", 0, 0,
                    new RunArea(0x0050, 0x03B0, 0x0000, 0x0100, 0x0300, 0x0300), "GREEN HILL");
            case "s2" -> new LobbyArea("s2", 0, 0,
                    new RunArea(0x0060, 0x028F, 0x0000, 0x0220, 0x01C0, 0x0240), "EMERALD HILL");
            default -> null;
        };
    }

    /**
     * The run a lobby plays: no stages, no rewind or editor, and an act completion restarts the
     * act instead of ending the lobby. Deaths fast-restart (no lives, game over or title card) at
     * the lobby spawn, which the fence re-arms for every load.
     */
    public static RunSpec runSpec(LobbyArea lobby, String character) {
        return new RunSpec(lobby.gameId(), lobby.zone(), lobby.act(), character,
                // Lobby deaths reload at once at the fence spawn, with no lives, game over or intro.
                GameplayRunPolicy.isolatedAct().withFastRestarts().withoutZoneIntros(), lobby.area());
    }

    /** {@code character} when it is a stock character of the lobby's game, otherwise Sonic. */
    public static String characterFor(String gameId, String character) {
        if (character == null) {
            return "sonic";
        }
        return switch (gameId) {
            case "s1" -> "sonic";
            case "s2" -> character.equals("tails") ? "tails" : "sonic";
            default -> switch (character) {
                case "sonic", "tails", "knuckles" -> character;
                default -> "sonic";
            };
        };
    }
}
