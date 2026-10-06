package slaytherobotnik.run;

/**
 * The screen-level state of a run: where the player is and what they can do. The scene
 * draws whichever room is current and forwards input to it; every room is plain logic that
 * tests can drive directly.
 */
public sealed interface Room permits StartRoom, MapRoom, CombatRoom, RewardRoom, EventRoom, ShopRoom, RestRoom,
        TreasureRoom, GameOverRoom, VictoryRoom {
}
