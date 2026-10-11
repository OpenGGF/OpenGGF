"""Generate Three Islands' authored Green Hill and Star Light tile maps (32-pixel cells).

Purpose: author the outdoor maps with drawing primitives, check every marker sits on ground
and that the route cannot be passed without fighting its blockers, then emit Maps.java.
Inputs: none. Usage: python3 generate_maps.py            # print maps and route checks
                     python3 generate_maps.py Maps.java  # write the Java constants
Origin: Three Islands Green Hill/Star Light rework, 2026-10-10 (feature/ai-three-islands).
"""
import sys
from collections import deque

class Grid:
    def __init__(self, w, h, fill):
        self.w, self.h = w, h
        self.g = [[fill] * w for _ in range(h)]
    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h: self.g[y][x] = c
    def get(self, x, y): return self.g[y][x] if 0 <= x < self.w and 0 <= y < self.h else '#'
    def rect(self, x0, y0, x1, y1, c):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1): self.put(x, y, c)
    def hp(self, y, x0, x1, c='=', w=1):
        self.rect(x0, y, x1, y + w - 1, c)
    def vp(self, x, y0, y1, c='=', w=1):
        self.rect(x, y0, x + w - 1, y1, c)
    def text(self): return '\n'.join(''.join(r) for r in self.g)

WALK = set('.,=b')

def ghz():
    g = Grid(80, 44, '#')
    # Flicky Village on the western coast; the trail enters at row 10.
    g.rect(1, 5, 13, 15, '.')
    for x, y in [(2, 6), (3, 13), (11, 6), (12, 14), (9, 14)]: g.put(x, y, 'T')
    for x, y in [(2, 9), (3, 9), (10, 12), (11, 12), (10, 7), (2, 12)]: g.put(x, y, ',')
    g.hp(10, 0, 14)
    # Beach and sea to the south.
    g.rect(0, 18, 24, 43, '~')
    g.rect(2, 16, 12, 17, ',')
    g.vp(7, 15, 16, '.')
    # Narrow pass out of the village (forced Motobug).
    g.hp(10, 14, 19)
    # The trail climbs north, then a westward nook with supplies.
    g.vp(19, 3, 10)
    g.hp(3, 14, 28, w=1)
    g.rect(14, 2, 17, 4, '.')
    # Down through the forest to a glade, and on south to the river.
    g.vp(28, 3, 30)
    g.rect(23, 12, 29, 15, '.')
    g.put(25, 13, 'T')
    # A dead-end hollow to the west hides a 1-Up.
    g.hp(17, 21, 28)
    g.rect(20, 16, 22, 19, '.')
    # The river, crossed by a log bridge far downstream (forced Crabmeat).
    g.rect(31, 0, 33, 43, '~')
    g.hp(30, 28, 35)
    g.hp(30, 31, 33, 'b')
    # A switchback climbs the far bank to the shrine plateau (forced Newtron on the stair).
    g.vp(35, 21, 30)
    g.hp(21, 35, 37)
    g.vp(37, 8, 21)
    g.rect(34, 7, 45, 9, '.')
    g.rect(37, 3, 41, 6, 'H')
    g.rect(35, 2, 36, 6, '.')  # side court beside the shrine
    g.rect(42, 2, 44, 6, '.')
    # Bell garden to the north-east, reached across the plateau.
    g.hp(8, 45, 50)
    g.rect(50, 2, 62, 9, '.')
    for x, y in [(51, 2), (62, 2), (57, 9), (50, 9), (62, 9)]: g.put(x, y, 'T')
    for x, y in [(53, 6), (55, 6), (58, 5), (60, 7)]: g.put(x, y, ',')
    # Main trail drops south from the plateau to the lake shore (forced Chopper).
    g.vp(46, 9, 25)
    g.rect(40, 24, 65, 26, '.')
    for x, y in [(41, 24), (57, 24), (63, 26)]: g.put(x, y, 'T')
    # The flooded orchard: a pool with an island and a sunken crossing.
    g.rect(39, 27, 62, 40, '~')
    g.rect(49, 32, 55, 36, '.')
    g.put(50, 32, 'T'); g.put(55, 36, 'T')
    g.rect(51, 27, 52, 31, 'O')
    # Sluice wheels at the ends of a northern and an eastern spur.
    g.vp(42, 20, 24)
    g.rect(40, 18, 43, 20, '.')
    g.vp(64, 26, 37)
    g.rect(63, 37, 66, 39, '.')
    # A switchback east of the lake climbs to the relay district (forced Buzz Bomber).
    g.hp(25, 65, 72)
    g.vp(72, 19, 25)
    g.hp(19, 66, 72)
    g.rect(62, 11, 69, 18, '.')
    for x, y in [(62, 11), (69, 18)]: g.put(x, y, 'T')
    g.vp(70, 8, 12)
    g.hp(8, 70, 72)
    g.rect(72, 4, 78, 13, '.')
    g.hp(10, 72, 79)
    # A cove south of the village overlooking the sea.
    g.put(3, 17, ',')
    marks = {
        'A': (4, 10), 'M': (6, 12), 'F': (8, 8), 'h': (8, 17),
        '1': (16, 10), 'a': (15, 3), 'p': (24, 3), 'q': (26, 14), 'e': (24, 13),
        'g': (21, 18), '2': (32, 30), '3': (37, 14), 'D': (39, 7), 's': (56, 4),
        'n': (54, 8), 'x': (52, 4), 'y': (56, 3), 'z': (60, 4), '4': (46, 18), 'o': (44, 25),
        'u': (41, 19), 'v': (65, 38), 'l': (51, 33), 'P': (53, 35), 'i': (54, 34),
        '5': (72, 22), 'S': (68, 13), 'Z': (64, 15), '6': (70, 10), 'K': (76, 7),
        't': (60, 25), 'W': (43, 8),
    }
    legend = """A STARPOST camp Trail camp
M MERCHANT merchant Pocky's travelling stall
F FRIEND friend Stranded traveller
D DUNGEON memory
S DISCOVERY signal Anchor relay
Z STARPOST sanctuary Sanctuary
W STARPOST waypoint Shrine Starpost
K BOSS boss Rift anchor
a MONITOR cache-a SUPER_RING Medicine cache
e MONITOR cache-b BLUE_SPHERE Supply cache
g MONITOR cache-c ONE_UP Hidden cache
i MONITOR cache-grove BLUE_SPHERE Overgrown monitor
1 BLOCK foe-0 MOTOBUG
2 BLOCK foe-1 CRABMEAT
3 BLOCK foe-2 NEWTRON
4 BLOCK foe-3 CHOPPER
5 BLOCK foe-4 BUZZ_BOMBER
6 BLOCK foe-5 MOTOBUG,CRABMEAT
p PATROL foe-6 BUZZ_BOMBER
q PATROL foe-7 MOTOBUG,NEWTRON
s PATROL foe-8 BUZZ_BOMBER,CHOPPER
t PATROL foe-9 CRABMEAT
n CLUE garden-verse Weathered inscription
x MECHANISM bell-dawn Sunrise bell
y MECHANISM bell-noon High sun bell
z MECHANISM bell-dusk Sunset bell
o CLUE orchard-note Water-stained notebook
u MECHANISM sluice-west Root-bound wheel
v MECHANISM sluice-east Salt-crusted wheel
l CLUE orchard-letter Tin beneath the roots
h CLUE horizon Split reflection
P ANIMAL animal"""
    return g, marks, legend

def slz():
    g = Grid(80, 42, '~')
    # Rooftops of the sleeping city below the highways.
    for x0, y0, x1, y1 in [(0, 0, 9, 3), (16, 0, 22, 5), (50, 0, 60, 6), (64, 0, 69, 3), (14, 15, 22, 24),
                           (34, 16, 42, 26), (52, 18, 60, 30), (66, 20, 79, 30), (0, 38, 30, 41), (33, 34, 46, 41),
                           (62, 33, 79, 41), (24, 31, 30, 36)]:
        g.rect(x0, y0, x1, y1, '#')
    # Arrival promenade.
    g.rect(1, 6, 13, 14, '.')
    g.hp(10, 0, 14)
    for x, y in [(3, 7), (11, 13)]: g.put(x, y, 'r')
    # A narrow catwalk to the junction (forced Bomb).
    g.hp(10, 14, 23, 'b')
    g.hp(10, 14, 16, '=')
    # Junction plaza beneath the observatory.
    g.rect(24, 5, 33, 14, '.')
    g.rect(26, 1, 30, 4, 'H')
    g.rect(24, 2, 25, 4, '.'); g.rect(31, 2, 33, 4, '.')
    g.put(24, 14, 'r'); g.put(33, 5, 'r')
    # Upper feeder: a catwalk east to a maintenance platform (forced Orbinaut).
    g.hp(3, 34, 42, 'b')
    g.rect(43, 1, 47, 5, '.')
    # Lower feeder: a long road south, then a catwalk west (forced Caterkiller).
    g.vp(28, 15, 30, '=', w=2)
    g.vp(29, 19, 25, '~')
    g.hp(30, 13, 27, 'b')
    g.rect(7, 27, 12, 33, '.')
    g.put(7, 27, 'r')
    # A seal hides on a ledge below the lower platform.
    g.vp(9, 34, 36, 'b')
    g.rect(8, 37, 10, 37, '.')
    # Main skyway east to the gate (forced Buzz Bomber and Bomb).
    g.hp(10, 34, 50, '=', w=2)
    g.hp(11, 39, 43, '~')
    g.rect(44, 12, 50, 17, '.')
    g.vp(48, 18, 26, 'b')
    g.rect(46, 27, 50, 30, '.')
    # The unpowered skywalk gate and the far district.
    g.rect(51, 10, 52, 11, 'R')
    g.hp(10, 53, 61, 'b')
    g.rect(62, 6, 70, 16, '.')
    g.put(62, 6, 'r'); g.put(70, 16, 'r')
    g.hp(10, 71, 72, 'b')
    g.rect(73, 4, 79, 14, '.')
    g.hp(10, 72, 79, '=')
    marks = {
        'A': (4, 10), 'M': (6, 12), 'F': (8, 8), '1': (19, 10), 'D': (28, 5), 'a': (25, 7),
        '2': (38, 3), 'x': (45, 3), '3': (28, 22), 'y': (10, 30), 'p': (9, 31), 'P': (9, 37),
        '4': (41, 10), 'N': (46, 13), 'e': (49, 15), 'q': (48, 28), 'g': (47, 29),
        '5': (57, 10), 'S': (67, 8), 'Z': (64, 13), '6': (71, 10), 'K': (76, 7), 'k': (32, 12), 'W': (45, 15),
    }
    legend = """A STARPOST camp Trail camp
M MERCHANT merchant Pocky's travelling stall
F FRIEND friend Stranded traveller
D DUNGEON memory
S DISCOVERY signal Anchor relay
Z STARPOST sanctuary Sanctuary
W STARPOST waypoint Skywalk Starpost
K BOSS boss Rift anchor
N NOTE
x SWITCH 0
y SWITCH 1
P ANIMAL animal
a MONITOR cache-a SUPER_RING Expedition supplies
e MONITOR cache-b BLUE_SPHERE Expedition supplies
g MONITOR cache-c ONE_UP Expedition supplies
1 BLOCK foe-0 BOMB
2 BLOCK foe-1 ORBINAUT_S1
3 BLOCK foe-2 CATERKILLER
4 BLOCK foe-3 BUZZ_BOMBER,BOMB
5 BLOCK foe-4 ORBINAUT_S1,CATERKILLER
6 BLOCK foe-5 BOMB,BUZZ_BOMBER
p PATROL foe-6 CATERKILLER
q PATROL foe-7 BUZZ_BOMBER,ORBINAUT_S1
k PATROL foe-8 BOMB"""
    return g, marks, legend

def build(fn):
    g, marks, legend = fn()
    for m, (x, y) in marks.items():
        assert g.get(x, y) in WALK, (fn.__name__, m, x, y, g.get(x, y))
        g.put(x, y, m)
    return g, marks, legend

def flood(g, start, blocked=set(), gates_open=False):
    seen = {start}; q = deque([start]); dist = {start: 0}
    while q:
        x, y = q.popleft()
        for dx, dy in ((1,0),(-1,0),(0,1),(0,-1)):
            n = (x+dx, y+dy); c = g.get(*n)
            if n in seen or not (0 <= n[0] < g.w and 0 <= n[1] < g.h): continue
            ok = c in WALK or c.isalnum() and c not in 'HTORr' and c not in '#~'
            if c in 'OR' and gates_open: ok = True
            if n in blocked: ok = False
            if ok: seen.add(n); dist[n] = dist[(x, y)] + 1; q.append(n)
    return dist

def java(fn, name):
    g, marks, legend = build(fn)
    return '    static final String ' + name + ' = """\n' + '\n'.join('        ' + r for r in g.text().split('\n')) + '\n        ---\n' + '\n'.join('        ' + l for l in legend.split('\n')) + '\n        """;\n'

if __name__ == '__main__' and len(sys.argv) > 1:
    out = ['package threeislands.field;', '', '/**', ' * Authored tile maps (32-pixel cells) for the hand-built outdoor areas. Terrain: {@code .} and',
           ' * {@code ,} ground, {@code =} path, {@code b} bridge or catwalk, {@code #} cliff or rooftop,',
           ' * {@code ~} water or open sky, {@code T} tree, {@code r} rock or barrier, {@code H} a building',
           ' * facade, {@code O} the orchard crossing and {@code R} the powered gate. Other characters are',
           ' * markers defined in the legend after {@code ---}. Generated by',
           ' * {@code examples/three-islands/tools/generate_maps.py}; edit the script, not these strings.', ' */',
           'final class Maps {', '    private Maps() {', '    }', '']
    out.append(java(ghz, 'GREEN_HILL')); out.append(java(slz, 'STAR_LIGHT')); out.append('}')
    open(sys.argv[1], 'w').write('\n'.join(out) + '\n')
    sys.exit()

if __name__ == '__main__':
    for fn in (ghz, slz):
        g, marks, legend = build(fn)
        print(fn.__name__); print(g.text()); print()
        blockers = {marks[k] for k in marks if k.isdigit()}
        d0 = flood(g, (2, 10), blockers)
        d1 = flood(g, (2, 10), set(), True)
        for k, p in marks.items():
            print(k, 'reach-without-fights' if p in d0 else '-', 'dist', d1.get(p))
        print('exit', d1.get((79, 10)), 'without fights', (79, 10) in d0)
