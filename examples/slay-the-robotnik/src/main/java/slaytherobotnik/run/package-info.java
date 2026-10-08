/**
 * One run from character choice to the final boss: the rooms, rewards, shop, rest sites,
 * events and the save format.
 *
 * <p>Plain Java. A {@link slaytherobotnik.run.Run} holds the current
 * {@link slaytherobotnik.run.Room}; each room is a small state machine the presentation reads and
 * drives. {@link slaytherobotnik.run.SaveCodec} writes and reads a run as properties text.
 */
package slaytherobotnik.run;
