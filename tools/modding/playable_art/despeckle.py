#!/usr/bin/env python3
"""Purpose: material-aware speckle cleanup for indexed pixel art, shared by flat cleanups and slope turns.
Inputs: a grid of palette indices, the palette's luminance per index, and a map from index to
material name. Only mapped indices are ever rewritten, and only to another index of the same
material; transparency, ink and unmapped detail (goggles, shoes, eyes) are never touched.
Origin: TALON walk cleanup, 2026-11-12 (examples/mica-and-talon/production/talon/walk_cleanup.py).

Rules, repeated for at most `rounds` rounds until nothing changes:
  1. An isolated pixel (no 4-neighbour of its index) with two or more neighbours of its own material
     takes the commonest of them; a tie goes to the nearer luminance, then the lower index.
  2. Otherwise, an isolated pixel that is a one-pixel luminance step between its two neighbours on
     an axis (an anti-alias fringe) takes the nearer neighbour of its own material.
  3. A two-pixel fleck whose surround is at least four pixels and 60% its own material takes the
     commonest of them.
Materials named in `protect` are never darkened: a lone lighter pixel there is a highlight, and the
speckle to remove is the darker fringe. A pixel alone in its material is a feature and stays.
`lines=True` is for larger art whose detail is drawn in one-pixel diagonal lines (scale edges on a
title portrait): a pixel or fleck that continues diagonally into its own index is a line, not speckle.
"""
from collections import Counter

N4 = ((-1, 0), (1, 0), (0, -1), (0, 1))
DIAGONAL = ((-1, -1), (1, -1), (-1, 1), (1, 1))


def despeckle(grid, lum, material, protect=(), rounds=3, lines=False):
    height, width = len(grid), len(grid[0])
    grid = [row[:] for row in grid]

    def pick(value, candidates):
        counts = Counter(candidates)
        top = max(counts.values())
        return min((k for k, n in counts.items() if n == top), key=lambda k: (abs(lum[k] - lum[value]), k))

    def allowed(value, target):
        return material[value] not in protect or lum[target] >= lum[value]

    for _ in range(rounds):
        def at(x, y):
            return grid[y][x] if 0 <= x < width and 0 <= y < height else 0

        changed = 0
        after = [row[:] for row in grid]
        for y in range(height):
            for x in range(width):
                value = grid[y][x]
                kind = material.get(value)
                if not kind or any(at(x + a, y + b) == value for a, b in N4):
                    continue
                if lines and any(at(x + a, y + b) == value for a, b in DIAGONAL):
                    continue
                own = [at(x + a, y + b) for a, b in N4 if material.get(at(x + a, y + b)) == kind]
                if len(own) >= 2:  # rule 1
                    target = pick(value, own)
                    if allowed(value, target):
                        after[y][x] = target
                        changed += 1
                    continue
                for a, b in ((1, 0), (0, 1)):  # rule 2
                    p, q = at(x - a, y - b), at(x + a, y + b)
                    if p < 2 or q < 2 or p == q or not (lum[p] < lum[value] < lum[q] or lum[p] > lum[value] > lum[q]):
                        continue
                    near = [k for k in (p, q) if material.get(k) == kind and allowed(value, k)]
                    if near:
                        after[y][x] = min(near, key=lambda k: (abs(lum[k] - lum[value]), k))
                        changed += 1
                        break
        grid = after
        after = [row[:] for row in grid]
        seen = set()
        for y in range(height):
            for x in range(width):
                value = grid[y][x]
                kind = material.get(value)
                if not kind or (x, y) in seen:
                    continue
                patch, todo = [(x, y)], [(x, y)]
                seen.add((x, y))
                while todo:
                    cx, cy = todo.pop()
                    for a, b in N4:
                        near = (cx + a, cy + b)
                        if near not in seen and at(*near) == value:
                            seen.add(near)
                            patch.append(near)
                            todo.append(near)
                if len(patch) != 2:
                    continue
                if lines and any(at(cx + a, cy + b) == value for cx, cy in patch for a, b in DIAGONAL
                                 if (cx + a, cy + b) not in patch):
                    continue
                ring = [at(cx + a, cy + b) for cx, cy in patch for a, b in N4 if (cx + a, cy + b) not in patch]
                own = [k for k in ring if material.get(k) == kind]
                if len(own) >= 4 and len(own) >= 0.6 * len([k for k in ring if k]):  # rule 3
                    target = pick(value, own)
                    if allowed(value, target):
                        for cx, cy in patch:
                            after[cy][cx] = target
                        changed += 2
        grid = after
        if not changed:
            break
    return grid
