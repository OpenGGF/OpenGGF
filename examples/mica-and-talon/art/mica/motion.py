#!/usr/bin/env python3
"""Purpose: editable native-pixel MICA sprint/launch motion source.
Inputs: shared palette and recipe.json; no ROM pixels enter authored frames.
Origin: W7 owner-feedback motion pass, 2026-11-08.
Run from repository root; updates only the selected recipe records. Pipeline bakes PNGs.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent


class Pen:
    """Rasterize authored contours directly in each slope basis, never rotate art."""
    def __init__(self, angle=0):
        self.im = Image.new('P', (80, 80))
        self.d = ImageDraw.Draw(self.im)
        self.a = math.radians(angle)

    def points(self, ps):
        c, s = math.cos(self.a), math.sin(self.a)
        return [(round(40+(x-40)*c+(y-40)*s),
                 round(40-(x-40)*s+(y-40)*c)) for x, y in ps]

    def poly(self, ps, color, ink=True):
        ps = self.points(ps)
        self.d.polygon(ps, fill=color)
        if ink:
            self.d.line(ps+[ps[0]], fill=1, width=1)

    def line(self, ps, color=1, width=1):
        self.d.line(self.points(ps), fill=color, width=width)

    def oval(self, box, color, ink=True):
        x, y, xx, yy = box
        self.poly([((x+xx)/2+(xx-x)/2*math.cos(t*math.pi/16),
                    (y+yy)/2+(yy-y)/2*math.sin(t*math.pi/16)) for t in range(32)], color, ink)


def scale(p, x, y, w=7, h=5):
    p.poly([(x,y),(x+w-2,y-1),(x+w,y+1),(x+w-2,y+h-1),(x+2,y+h)],4)
    p.line([(x+1,y+1),(x+w-3,y)],5)


def head(p, x, y, happy=False):
    # One common head contour: domed overlapping jade hood, cream tapered muzzle,
    # pink ear, large eye and forward forehead scale from the approved concept.
    p.poly([(x-10,y+7),(x-11,y),(x-8,y-6),(x-3,y-9),(x+3,y-8),
            (x+7,y-4),(x+8,y+3),(x+12,y+5),(x+11,y+8),(x+4,y+10),(x-3,y+8)],4)
    for sx,sy in ((x-9,y-3),(x-5,y-7),(x-2,y-5),(x-10,y+2),(x-7,y+4)):
        scale(p,sx,sy,7,4)
    p.oval((x-5,y-5,x-1,y+1),7)
    p.line([(x-4,y-3),(x-3,y-1)],14,2)
    p.poly([(x+1,y-4),(x+6,y-1),(x+7,y+3),(x+12,y+5),
            (x+10,y+8),(x+4,y+9),(x-1,y+6),(x-2,y+1)],7)
    p.poly([(x,y-4),(x+4,y-3),(x+6,y),(x+3,y+3),(x+1,y)],5)
    p.poly([(x+2,y),(x+6,y+1),(x+7,y+5),(x+2,y+5)],12)
    p.line([(x+5,y+1),(x+5,y+4)],15,2)
    if not happy:
        p.line([(x+1,y),(x+6,y+2)],1)
        p.line([(x+6,y+7),(x+10,y+6)],1)
    else:
        p.poly([(x+3,y+6),(x+8,y+6),(x+6,y+9),(x+4,y+8)],15)
        p.line([(x+4,y+8),(x+6,y+8)],14)
    p.line([(x+10,y+5),(x+11,y+5)],15)
    # Swept crest or raised crown; laid over the hood, joined to its silhouette.
    if happy:
        p.poly([(x-8,y-5),(x-7,y-11),(x-2,y-8),(x+1,y-11),(x+4,y-6)],5)
    else:
        p.poly([(x-11,y-3),(x-15,y-6),(x-7,y-8),(x-2,y-8),(x+3,y-5)],5)
        p.line([(x-12,y-5),(x-6,y-6)],6)


# Approved WALK head, retained at native scale: hood, ear and profile muzzle.
WALK_HEAD = ['0000000000111000000000000', '0000000111343111100000000', '0000011313444324311000000', '0000133222222124441000000', '00012224441f1422342111000', '00122244429e914421341b100', '01343244418a9122332441100', '0144411221991479934231100', '135422333212399b88222f110', '0121144442442119199241110', '122224444244319cf17931100', '14441444224441dcf19711100', '144411111122199cf1d781100', '144323232223297dd97777910', '12213444234417777977777f1', '01212444123219777f1977910', '014313412211119878b111100', '014411112122211119f100011']


def walk_head(p, x, y, happy=False):
    if p.a:
        # Redrawn slope hood/face: continuous contours rather than rotated pixels.
        head(p,x+13,y+8,happy)
        return
    for yy,row in enumerate(WALK_HEAD):
        for xx,v in enumerate(row):
            if v != '0':p.im.putpixel((x+xx,y+yy),int(v,16))
    if happy:
        p.poly([(x+15,y+14),(x+21,y+14),(x+19,y+17),(x+16,y+16)],15)
        p.line([(x+17,y+16),(x+19,y+16)],14)
    else:
        # Forward-looking lowered upper lid, leaving the original eye white.
        p.line([(x+15,y+9),(x+19,y+10)],1)


def glove(p,x,y,shade=12):
    p.poly([(x,y+1),(x+2,y),(x+5,y+1),(x+6,y+3),
            (x+5,y+5),(x+1,y+5),(x,y+3)],shade)
    p.line([(x+2,y+3),(x+4,y+3)],13)


def run(phase, angle=0):
    p=Pen(angle)
    bob=(0,-1,0,1)[phase]
    # Short tail leaves the pelvis diagonally down/back, with a moving tip.
    p.poly([(38,43),(33,44),(24,47+bob),(19,52+bob),
            (28,50+bob),(35,49),(40,47)],3)
    for x,y in ((24,48+bob),(29,46+bob),(34,45)):scale(p,x,y,6,3)
    # Far bent arm: exchanged with near arm on alternate phases.
    forward=phase%2
    if forward:
        p.poly([(44,37),(49,40),(50,36),(53,36),(53,43),(49,45),(42,41)],3)
        glove(p,51,39,13)
    else:
        p.poly([(40,37),(34,39),(31,37),(29,39),(34,44),(41,41)],3)
        glove(p,27,35,13)
    # Compact boot wheel shares its top arc with the pelvis; never a floating ring.
    p.oval((33,44,54,58),11)
    p.oval((38,48,49,54),0)
    arcs=[([(35,48),(39,45),(48,45),(52,48)],[(35,54),(42,57),(50,55)]),
          ([(37,45),(42,49),(50,55)],[(35,54),(41,50),(51,47)]),
          ([(34,51),(39,56),(48,57),(53,53)],[(36,47),(43,45),(50,47)]),
          ([(36,56),(42,52),(50,46)],[(35,47),(41,50),(51,54)])]
    for path in arcs[phase]:
        p.line(path,10,3)
        p.line([(x,y+1) for x,y in path],14)
    # Break the wheel at four travelling arc positions, inside the fixed floor.
    cut=[[(52,49),(55,48),(55,52),(53,52)],
         [(34,44),(37,44),(35,48),(32,48)],
         [(32,51),(35,51),(36,54),(32,55)],
         [(50,54),(55,52),(55,56),(52,57)]][phase]
    p.poly(cut,0,False)
    # Upright torso: chest three pixels ahead of pelvis across eleven vertical.
    p.poly([(38,47),(36,42),(39,36),(45,35),(48,39),(46,45),(43,48)],4)
    p.poly([(43,37),(47,38),(46,43),(43,46),(40,45)],7)
    p.line([(41,41),(46,41)],8)
    p.line([(40,44),(44,44)],8)
    if forward:
        p.poly([(41,37),(36,40),(32,39),(30,41),(35,44),(42,41)],4)
        glove(p,27,38)
    else:
        p.poly([(43,38),(48,42),(52,40),(51,36),(48,37),(48,39),(45,36)],4)
        glove(p,51,39)
    walk_head(p,30,21)
    # A small crest sweep joins the WALK hood without flattening the skull.
    p.poly([(32,25),(28,23),(35,22),(38,23)],4)
    p.line([(31,23),(35,23)],5)
    return p.im


def spring(phase):
    p=Pen()
    # Profile: staggered far/near arms run vertically along the torso line.
    p.poly([(40,43),(38,33),(38,20),(41,18),(43,22),(42,34),(44,42)],3)
    glove(p,40,19-phase,13)
    # Hanging tail, separate from the two long pointed boot silhouettes.
    p.poly([(37,43),(36,49),(35+phase,56),(33+phase,62),
            (32,56),(33,47)],3)
    p.line([(34,49),(34,56)],5)
    p.poly([(37,39),(44,38),(46,44),(44,51),(38,51),(35,46)],4)
    p.poly([(41,39),(45,41),(44,48),(41,50),(39,46)],7)
    p.line([(40,44),(44,44)],8)
    # Far boot then nearer boot: cuffs, coral shafts, toe and heavy tan sole.
    for x,y in ((37,49),(44,50)):
        p.poly([(x,y),(x+4,y),(x+3,y+6),(x,y+6)],3)
        p.poly([(x-1,y+4),(x+4,y+4),(x+5,y+10),(x+3,y+13),
                (x,y+12),(x-1,y+9)],11)
        p.poly([(x,y+5),(x+3,y+5),(x+4,y+10),(x+2,y+11),(x,y+9)],10,False)
        p.line([(x,y+6),(x+2,y+7)],5)
        p.line([(x,y+11),(x+3,y+12)],9)
    walk_head(p,27,23,True)
    # Near arm in front of hood; both hands above head, staggered in profile.
    p.poly([(39,42),(34,33),(34,22),(37,21),(38,25),(37,33),(42,40)],4)
    p.line([(35,25),(35,32)],5)
    glove(p,32+phase,18,12)
    p.line([(34+phase,19),(34+phase,16)],12,2)
    return p.im


def store(recipe, ident, im, note, **extra):
    rec=next(f for f in recipe['frames'] if f['id']==ident)
    bbox=im.getbbox(); x,y,xx,yy=bbox
    rows=[''.join(format(im.getpixel((a,b)),'x') for a in range(x,xx)) for b in range(y,yy)]
    keep={k:rec[k] for k in ('id','animation','order','angle_set','ticks') if k in rec}
    keep.update(extra)
    rec.clear();rec.update(keep,notes=note,native_pixels=dict(rows=rows,anchor=[40-x,40-y]))


WAIT_SEEDS = {
    "wait-00": {
        "rows": [
            "0000000000000000111111000000000000",
            "0000000000000011244442111000000000",
            "0000000000000134554533443100000000",
            "0000000000011224324213444411000000",
            "0000000000124441111242244421110000",
            "0000000001344441779244422441b10000",
            "00000000014445297ee122343242100000",
            "00000000012332117eb288924232100000",
            "0000000012311442b92979992242f10000",
            "0000000124424444242119919144110000",
            "000000014432444244211ccf9922910000",
            "000000124442444244219ccf1d91910000",
            "000000014421222244299ccf1c79100000",
            "000000011112212222177cd19c77910000",
            "0000000144244343219777787777781100",
            "0000000144244442211977799987771000",
            "00000001442144211111f878bf97910000",
            "0000000133121111123211f99911100100",
            "00000000112221124444299f1100001d10",
            "000000000132112443334287810001d910",
            "0000000001111145299142b989111dcdd1",
            "00000000000011243d99119778125cccc1",
            "0000000000000129c99cd19777143cccc1",
            "000000000000019d99ccc189991199dd11",
            "0000000000111119dccc9f777810111100",
            "0000000011243119ccc919877910000000",
            "0000001142432221191119889110000000",
            "0000114442224422112318781210000000",
            "0001254324424411233211111210000000",
            "0012222325432213344111123210000000",
            "0125325423422111f12100121110000000",
            "01444233211221aaaa1001ffbab1000000",
            "0123421222111fbf111001baf221000000",
            "1441121111001bb4221001ab2221100000",
            "1421110000001abfbff11faabbbaa10000",
            "111000000001fbaaaaab1fbbbaaaaf1000",
            "00000000000019bbbbbb1b999bbb9b1000",
            "0000000000001b98989911119999991000",
            "0000000000001111111110001111110000"
        ],
        "anchor": [
            22,
            20
        ]
    },
    "wait-02": {
        "rows": [
            "0000000000000000111111000000000000",
            "0000000000000011234442111000000000",
            "0000000000000134554423543100000000",
            "0000000000011123324313444410100000",
            "0000000000124441111242234321910000",
            "0000000001344441779144422441a10000",
            "000000000144442f7eef22453242100000",
            "00000000012332117eb288924222100000",
            "0000000012211442f92979992342f10000",
            "0000000124424444132119918243110000",
            "000000014422444244219ccf9922910000",
            "000000124442454244219ccf1d91910000",
            "000000013421231144399ccf1c79100000",
            "000000011112212222178dd19c77910000",
            "0000000144244343119777787777781100",
            "000000125424445211197778b987771100",
            "00000001452144211111f877bf97910000",
            "0000000132121111123211f99911100100",
            "000000001122211244442f8f1100001d10",
            "000000000142112543234297710001d910",
            "0000000001112145299132f8891119cdd1",
            "00000000000011343d9d119777139cccc1",
            "0000000000000129c99cd19777133cccc1",
            "000000000000001d99ccc189991199dd91",
            "0000000000111119dccc91777710111110",
            "0000000011243119ccc919887f10000000",
            "0000001142432211191118889110000000",
            "0000114542224421112318791110000000",
            "000125332352452122221111fb11000000",
            "001222231443221334411111b121110000",
            "0125315532421111112101fab1faab1000",
            "01444234211221baaa10001aababb89100",
            "12244211121111bf2110001aabb9891000",
            "1441122111001bb42210001bb989110000",
            "1431111000001aabbbf100018bf1100000",
            "1110000000001baaaaab1001b110000000",
            "00000000000019bbbbbb10001000000000",
            "0000000000001b98989910000000000000",
            "0000000000001111111110000000000000"
        ],
        "anchor": [
            22,
            20
        ]
    }
}


def wait(phase):
    seed = WAIT_SEEDS['wait-02' if phase == 4 else 'wait-00']
    base = Image.new('P', (80,80))
    x0, y0 = 40-seed['anchor'][0], 40-seed['anchor'][1]
    for y,row in enumerate(seed['rows']):
        for x,v in enumerate(row):base.putpixel((x0+x,y0+y),int(v,16))
    im=base.copy()
    d=ImageDraw.Draw(im)
    if phase == 1:
        # Raise crest, shoulders and chest one pixel, with boots planted.
        d.rectangle((x0+9,y0-1,x0+34,y0+28),fill=0)
        im.paste(base.crop((x0+9,y0,x0+34,y0+30)),(x0+9,y0-1))
        d.line([(x0+23,y0+22),(x0+24,y0+24)],fill=7)
    if phase in (1,2):
        # Tail tip rises/falls independently of the fixed pelvis and boots.
        d.rectangle((x0,y0+26,x0+10,y0+36),fill=0)
        im.paste(base.crop((x0,y0+26,x0+11,y0+36)),(x0,y0+25 if phase==1 else y0+27))
    if phase == 2:
        d.rectangle((x0+22,y0+10,x0+26,y0+13),fill=7)
        d.line([(x0+22,y0+11),(x0+24,y0+12),(x0+26,y0+11)],fill=1)
    if phase in (3,5):
        # Slow glance toward the camera, a wider nearer eye and inner pupil.
        d.rectangle((x0+21,y0+10,x0+25,y0+13),fill=12)
        d.line([(x0+22,y0+10),(x0+22,y0+13)],fill=15,width=2)
        d.point((x0+22,y0+10),fill=12)
        d.line([(x0+21,y0+9),(x0+24,y0+8)],fill=1)
    if phase == 5:
        # Toe lifts one pixel; rear foot stays on the shared floor.
        d.rectangle((x0+25,y0+32,x0+33,y0+38),fill=0)
        im.paste(base.crop((x0+25,y0+32,x0+34,y0+39)),(x0+25,y0+31))
    return im


def main():
    parser=argparse.ArgumentParser();parser.add_argument('item',choices=['checkpoint','run','spring','wait']);args=parser.parse_args()
    recipe=json.loads((ROOT/'recipe.json').read_text())
    if args.item in ('checkpoint','run'):
        for n in range(4):
            store(recipe,f'run-{n:02}',run(n),'Upright WALK-profile sprint; hip-attached coral wheel, alternating bent arms and diagonal bobbing tail. Flat contact centre+18.')
        if args.item=='run':
            for s,a in [('up45',45),('steep',90),('inverted',135)]:
                for n in range(4):
                    store(recipe,f'run-{s}-{n:02}',run(n,a),'Hand-authored contours rasterized in slope basis about flat hotspot, with matching blur phase; no bitmap rotation. Runtime contact unverified.')
    if args.item in ('checkpoint','spring'):
        for n in range(2):store(recipe,f'spring-{n:02}',spring(n),'Right-profile stretched launch, shared WALK head and delighted grin; staggered overhead gloves, long pointed boots and hanging fluttering tail.',ticks=24)
    if args.item=='wait':
        for n in range(4,6):
            if not any(f['id']==f'wait-{n:02}' for f in recipe['frames']):
                recipe['frames'].insert(n,dict(id=f'wait-{n:02}',animation='WAIT',order=n,ticks=8))
        for n in range(6):
            store(recipe,f'wait-{n:02}',wait(n),
                  ['Rest/exhale, planted boots.','Inhale: chest/shoulders and crest lift 1px; tail rises.',
                   'Blink and falling tail.','Camera glance.','Lift boot for fidget.','Toe tap and camera glance.'][n])
        recipe.setdefault('scripts',{})['WAIT']=dict(
            frames=[f'wait-{n:02}' for n in (0,1,0,1,2,0,1,0,3,4,5,4,5,3,0)],
            durations=[24,24,24,24,6,24,24,36,18,8,8,8,8,18,24],end='loop')
    (ROOT/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n')


if __name__=='__main__':main()
