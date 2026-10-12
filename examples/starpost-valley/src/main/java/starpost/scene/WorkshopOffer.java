package starpost.scene;

import java.util.Map;

/**
 * Something another system offers at Tails's workshop (a building, an upgrade): its name, the
 * item whose icon it shows, its price in rings and materials, what buying it does, and its line.
 * Systems list theirs in {@link Systems#workshopOffers}.
 */
public record WorkshopOffer(String name, String icon, int rings, Map<String, Integer> inputs, Runnable effect,
        String text) {
}
