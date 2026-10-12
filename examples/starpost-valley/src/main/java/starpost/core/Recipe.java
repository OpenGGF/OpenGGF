package starpost.core;

import java.util.Map;

/**
 * Something Tails can build: rings plus materials for one or more of an item. Recipes whose
 * ingredients are not in the catalogue (a system not installed) are left out of the workshop.
 *
 * @param product item id produced
 * @param count   how many
 * @param rings   ring cost
 * @param inputs  item id to count
 * @param unlock  the story flag that offers it, or null for always
 */
public record Recipe(String product, int count, int rings, Map<String, Integer> inputs, String unlock) {
}
