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


def run(phase, angle=0):
    p = Pen(angle)
    # Streaming tapered scale tail, parallel to travel.
    p.poly([(12,40),(22,36),(33,36),(39,40),(32,43),(22,42)],3)
    for x in (18,24,30): scale(p,x,37,8,4)
    # Far swept arm.
    p.poly([(41,36),(34,37),(29,34),(27,36),(32,41),(40,41)],3)
    p.oval((25,33,32,38),13)
    # Fast figure-eight boot smear. Four different arc endpoints and crossing
    # diagonals keep phases legible even at the native one-tick minimum.
    p.oval((30,43,53,58),11)
    holes = [(34,46,49,54),(36,46,47,55),(34,47,49,55),(35,45,48,54)]
    p.oval(holes[phase],0)
    # A moving cut in the outer smear stops it reading as a static red tyre.
    gaps = [[(48,44),(54,44),(54,48),(50,48)],
            [(30,43),(35,43),(35,47),(30,49)],
            [(30,53),(35,53),(36,59),(30,59)],
            [(48,53),(54,51),(54,59),(48,59)]]
    p.poly(gaps[phase],0,False)
    routes = [([(33,47),(38,44),(46,45),(51,49)],[(33,54),(40,57),(48,55)]),
              ([(36,44),(43,48),(49,55)],[(33,53),(39,49),(49,46)]),
              ([(32,51),(37,56),(46,57),(51,53)],[(34,46),(41,44),(48,46)]),
              ([(34,56),(40,51),(48,45)],[(33,47),(40,50),(49,54)])]
    for points in routes[phase]:
        p.line(points,10,3)
        p.line([(x,y+1) for x,y in points],9)
    # Distinct moving boot/toe highlight, kept inside the blur contour.
    bx,by=[(46,46),(45,52),(32,51),(35,44)][phase]
    p.poly([(bx,by),(bx+5,by),(bx+6,by+3),(bx,by+3)],10)
    p.line([(bx+1,by+1),(bx+4,by+1)],14)
    # ~20 degree lean; pelvis back, chest ahead. Opposite arm streams back.
    p.poly([(35,46),(36,39),(41,34),(46,36),(45,43),(40,48)],4)
    p.poly([(41,37),(46,36),(46,42),(41,46),(38,45)],7)
    p.line([(41,41),(44,42)],8)
    p.poly([(40,38),(35,41),(31,40),(29,42),(34,45),(41,42)],4)
    p.oval((26,39,33,44),12)
    p.line([(28,41),(31,42)],13)
    head(p,47,32+(phase%2))
    return p.im


def spring(phase):
    p=Pen()
    # Same head in both phases. Arm reach/glove fingers and boot extension carry
    # the second launch beat, rather than changing the skull/hood.
    p.poly([(34,43),(36,48),(36,55),(34,60),(33,55)],3)
    p.line([(34,48),(34,55)],5)
    for x in (36,42):
        p.poly([(x,43),(x+4,43),(x+3,54),(x,54)],4)
        p.poly([(x-1,51),(x+4,51),(x+4,57+phase),(x+2,59+phase),(x-1,57+phase)],10)
        p.line([(x,57+phase),(x+3,57+phase)],9)
        p.line([(x,53),(x+2,53)],5)
    p.poly([(35,34),(43,34),(46,42),(43,48),(36,47),(33,41)],4)
    p.poly([(39,34),(43,36),(43,44),(40,46),(37,42)],7)
    p.line([(38,39),(42,39)],8)
    for side in (-1,1):
        x=40+side*6
        p.poly([(x,36),(x+side*4,31),(x+side*4,22),(x+side*1,21),
                (x+side*1,29),(x-side*2,33)],4)
        gx=x+side*3; gy=19-phase
        p.poly([(gx-3,gy+4),(gx-4,gy),(gx-3,gy-3),(gx-2,gy),
                (gx-1,gy-5),(gx,gy-5),(gx+1,gy-1),(gx+2,gy-4),
                (gx+3,gy-3),(gx+3,gy),(gx+4,gy-1),(gx+5,gy),
                (gx+3,gy+4)],12)
        p.line([(gx-1,gy+1),(gx+2,gy+2)],13)
    head(p,40,28,True)
    return p.im


def store(recipe, ident, im, note, **extra):
    rec=next(f for f in recipe['frames'] if f['id']==ident)
    bbox=im.getbbox(); x,y,xx,yy=bbox
    rows=[''.join(format(im.getpixel((a,b)),'x') for a in range(x,xx)) for b in range(y,yy)]
    keep={k:rec[k] for k in ('id','animation','order','angle_set','ticks') if k in rec}
    rec.clear();rec.update(keep,notes=note,native_pixels=dict(rows=rows,anchor=[40-x,40-y]),**extra)


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
            store(recipe,f'run-{n:02}',run(n),'Sprint blur phase; swept arms/crest, narrowed eye, streaming tail. Flat contact centre+18.')
        if args.item=='run':
            for s,a in [('up45',45),('steep',90),('inverted',135)]:
                for n in range(4):
                    store(recipe,f'run-{s}-{n:02}',run(n,a),'Hand-authored contours rasterized in slope basis about flat hotspot, with matching blur phase; no bitmap rotation. Runtime contact unverified.')
    if args.item in ('checkpoint','spring'):
        for n in range(2):store(recipe,f'spring-{n:02}',spring(n),'Stretched launch: identical delighted head, open raised gloves, paired pointed boots and hanging tail; reach/extension second beat.')
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
