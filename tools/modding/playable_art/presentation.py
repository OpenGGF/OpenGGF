#!/usr/bin/env python3
"""Purpose: reproduce MICA's native-size presentation pixel art and Gilded tables.
Inputs: existing MICA palette; optional pinned disassembly art for review only.
Origin: original-character W7 art round 2, 2026-11-08. No runtime/reference assets copied.
Run from repository root with python3 -I tools/modding/playable_art/presentation.py.
"""
import argparse
import importlib.util
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[3]
ART = ROOT/'examples/mica-and-talon/art/mica'
OUT = ART/'presentation'
COLORS = json.loads((ART/'palette.yaml').read_text())['colors']
NEAREST = Image.Resampling.NEAREST
# S1 player/HUD line 0, palette/Sonic.bin: native font uses only 0, 1, 15.
FONT_COLORS = [c[:] for c in COLORS]
FONT_COLORS[1] = [0, 0, 0]
FONT_COLORS[15] = [255, 255, 0]


def indexed(size, colors=COLORS):
    im = Image.new('P', size)
    im.putpalette(sum(colors, []) + [0]*(768-48))
    im.info['transparency'] = 0
    return im


# Native 16x16 hand-pixelled head: hood scales, projecting muzzle, brown eye,
# sand face and coral ear. No generated/downsampled shapes in these icons.
ICON = [
'0000111111000000',
'0011456654110000',
'0146654455541000',
'0155426654411000',
'14522655417e7100',
'1455454217ee7710',
'1326654171cc1c10',
'134554177cc1cc10',
'12422417cc11cc71',
'123445177cc177f1',
'0123341777777710',
'0122341781117100',
'001234177ee71000',
'0001234177710000',
'0000111111100000',
'0000000000000000',
]


def icon(game, monitor=False):
    # Original 14x15 portrait. Three staggered light-over-dark crest rows;
    # four-row near eye, a narrow far eye, pink ear and open smile.
    im = indexed((16,16))
    im.putdata([int(c,16) for row in ICON for c in row])
    if game == 's1':
        im.putpixel((4,2),5); im.putpixel((6,3),6)
    if game == 's3k':
        im.putpixel((5,1),6); im.putpixel((4,5),6)
    if monitor and game=='s1':
        im.putpixel((3,12),2); im.putpixel((5,13),3)
    return im


def sign(game, alternate=False):
    im=indexed((48,32));d=ImageDraw.Draw(im)
    # Large bust, near eye and narrow far eye turned toward viewer.
    d.polygon([(7,30),(9,23),(16,20),(30,20),(38,29),(38,30)],fill=3,outline=1)
    d.polygon([(20,23),(28,23),(32,30),(17,30)],fill=7,outline=1)
    d.line([(20,27),(29,27)],fill=8)
    d.polygon([(6,22),(5,12),(8,6),(13,2),(23,1),(30,3),(34,8),(34,15),(30,24),(17,26)],fill=2,outline=1)
    # Broad overlapping scales rather than crosshatched noise.
    for poly in [
        [(10,6),(14,3),(20,2),(21,5),(17,8),(11,9)],
        [(20,3),(25,2),(30,5),(31,8),(25,9),(20,6)],
        [(7,11),(10,7),(16,7),(18,10),(14,14),(8,15)],
        [(16,9),(21,7),(26,9),(27,12),(23,16),(18,15)],
        [(6,17),(9,13),(14,14),(15,18),(12,22),(7,23)],
        [(13,19),(16,14),(21,15),(23,19),(19,24),(14,25)]]:
        d.polygon(poly,fill=4,outline=2)
        d.line([poly[0],poly[1],poly[2]],fill=6 if game!='s1' else 5)
    # Ear ahead of back crest, far ear at crown.
    d.polygon([(16,11),(15,7),(17,5),(20,6),(22,11),(20,14)],fill=1)
    d.polygon([(17,10),(17,7),(19,7),(20,11)],fill=14)
    d.polygon([(29,7),(29,4),(31,3),(33,5),(33,9)],fill=1)
    d.line([(31,5),(31,7)],fill=14)
    # Cream facial plane and long blunt pangolin snout.
    d.polygon([(22,10),(27,9),(32,11),(34,16),(39,18),(39,21),(35,24),(29,27),(23,26),(18,22),(18,17)],fill=7,outline=1)
    d.line([(19,22),(23,25),(29,26),(35,23)],fill=8)
    d.polygon([(21,11),(24,11),(27,14),(27,20),(21,20),(20,16)],fill=12,outline=1)
    d.rectangle((24,14,25,18),fill=1);d.point((24,14),fill=12)
    d.line([(20,12),(19,10)],fill=1)
    d.polygon([(29,12),(31,12),(33,15),(33,18),(29,18)],fill=12,outline=1)
    d.line([(31,14),(31,17)],fill=1)
    # Jade forelock between eyes.
    d.polygon([(24,8),(28,8),(30,11),(28,15),(26,12)],fill=4,outline=2)
    d.line([(25,9),(27,9)],fill=6)
    d.line([(37,18),(38,19),(37,20)],fill=1)
    d.polygon([(25,22),(33,22),(30,25),(27,25)],fill=1)
    d.line([(26,22),(31,22)],fill=12);d.line([(28,24),(30,24)],fill=14)
    # Peace glove at left, with two separated raised fingers and folded thumb.
    glove=[(8,30),(6,26),(3,22),(2,17),(3,16),(5,17),(9,21),
           (8,14),(9,12),(11,13),(13,21),(16,20),(18,22),(17,26),(14,29),(14,30)]
    d.polygon(glove,fill=12,outline=1)
    d.line([(4,19),(7,23),(9,24)],fill=13)
    d.line([(12,22),(15,22),(16,23)],fill=1)
    d.line([(11,25),(14,26),(16,25)],fill=13)
    d.line([(8,28),(13,28)],fill=13)
    if game=='s1' or alternate: im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return im


def spin(face, flip=False):
    # Project the same bust onto a turning panel, no dither/no new colour noise.
    im=indexed((32,32))
    for y in range(2,30):
        left=5+(y-2)//14
        width=22-(y-2)//7
        row=face.crop((2,y,46,y+1)).resize((width,1),NEAREST)
        im.paste(row,(left,y))
    return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if flip else im


# Separate native-colour backing plane: never reassign a MICA character index.
BACKGROUNDS={'s1':[255,255,0],'s2':[255,146,0],'s3k':[255,146,0]}


def backing(game, alternate=False, turning=False, flip=False):
    colors=[[0,0,0],[36,36,36],[255,255,255],[182,182,182],
            [73,73,73],([36,36,182] if alternate else BACKGROUNDS[game])]
    im=indexed((32 if turning else 48,32),colors+[[0,0,0]]*10)
    d=ImageDraw.Draw(im)
    if turning:
        d.polygon([(4,0),(28,2),(28,30),(4,31),(2,29),(2,2)],fill=1)
        d.polygon([(4,1),(27,3),(27,29),(4,30),(3,28),(3,3)],fill=3)
        d.line([(4,1),(26,3)],fill=2)
        d.polygon([(5,3),(24,5),(24,27),(5,29)],fill=5)
        d.line([(26,4),(26,28)],fill=4)
    else:
        d.rounded_rectangle((0,0,47,31),radius=2,fill=1)
        d.line([(1,29),(1,2),(3,1),(45,1)],fill=2)
        d.line([(2,30),(45,30),(46,29),(46,2)],fill=4)
        d.rectangle((2,2,45,29),fill=3)
        d.rectangle((3,3,44,28),fill=5)
    return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if flip else im


def composed(key,face):
    game='s3k' if key.startswith('s3k') else key[:2]
    bg=backing(game,'alternate' in key,'spin' in key,'spin3' in key).convert('RGBA')
    bg.alpha_composite(face.convert('RGBA'))
    return bg


def glyph(letter):
    # Native SONIC: six ink rows, 2px stems, shadow one right/down; 6px advance
    # for most letters. M requires 7px ink width to retain two heavy stems.
    rows={'M':['1100011','1110111','1111111','1101011','1100011','1100011'],
          'A':['01110','11011','11011','11111','11011','11011']}[letter]
    im=indexed((len(rows[0])+1,8), FONT_COLORS);d=ImageDraw.Draw(im)
    for y,row in enumerate(rows,1):
        for x,v in enumerate(row):
            if v=='1':d.point((x+1,y+1),fill=1)
    for y,row in enumerate(rows,1):
        for x,v in enumerate(row):
            if v=='1':d.point((x,y),fill=15)
    return im


def gilded():
    levels=[0,36,73,109,146,182,219,255]
    # Warm amber shadows, saturated yellow midtones and cream specular light.
    gold=[[182,73,0],[255,146,0],[255,219,0],[255,255,73],[255,255,219]]
    frames=[]
    for phase in [0,1,2,3,4,3,2,1,0]:
        table=[c[:] for c in COLORS]
        for n,idx in enumerate(range(2,7)):
            table[idx]=gold[n][:]
            if n == phase:
                table[idx]=[levels[min(7,levels.index(v)+1)] for v in gold[n]]
        frames.append(table)
    fade=[]
    # Preserve the approved six native fade tables, including their endpoint.
    fade_target=[[146,109,36],[146,109,0],[182,146,36],[219,182,73],[255,219,146]]
    for step in range(6):
        table=[c[:] for c in COLORS]
        for idx in range(2,7):
            table[idx]=[min(levels,key=lambda v:abs(v-(a+(b-a)*step/5))) for a,b in zip(COLORS[idx],fade_target[idx-2])]
        fade.append(table)
    return dict(format='openggf-mica-gilded-1',palette='../palette.yaml',changed_indices=list(range(2,7)),
        note='Dry Super only. Full 16-entry index tables; all other slots preserved. No Hyper or underwater claim. Warm gold steady ramp; original fade tables retained.',
        fade_frames=fade,cycles={
        's2':dict(native_source_label='PalCycle_SuperSonic.normal / CyclingPal_SSTransformation',source='docs/s2disasm/s2.asm',
            native_offsets=list(range(0x30,0x78,8)),fix_bugs=0,frame_count=9,ticks_per_frame=8,fade_ticks_per_frame=4,frames=frames),
        's3k':dict(native_source_label='SuperHyper_PalCycle_SuperSonic / PalCycle_SuperSonic',source='docs/skdisasm/sonic3k.asm',
            native_offsets=[0x24,0x2a,0x30],fix_bugs=0,frame_count=3,ticks_per_frame=7,fade_ticks_per_frame=2,frames=[frames[i] for i in (0,2,4)])})


def main(review=None):
    OUT.mkdir(exist_ok=True); records=[]; images={}
    def emit(key,im,surface,game,label,source,pieces,line=0,**extra):
        path=OUT/(key+'.png'); im.save(path,transparency=0,bits=4,optimize=True);images[key]=im
        records.append(dict(surface=surface,game=game,native_source_label=label,source=source,
            dimensions=list(im.size),file=path.name,palette_line=line,pieces=pieces,**extra))
    sources={
    's1':dict(hud=('Map_HUD_internal.allyellow','docs/s1disasm/_maps/HUD.asm'),monitor=('Map_Monitor_internal.sonic','docs/s1disasm/_maps/Monitor.asm'),sign=('Map_Sign_internal.sonic','docs/s1disasm/_maps/Signpost.asm')),
    's2':dict(hud=('Map_hud_a_0008','docs/s2disasm/mappings/sprite/hud_a.asm'),monitor=('Map_obj26_0034','docs/s2disasm/mappings/sprite/obj26.asm'),sign=('Map_obj0D_a_000C / Map_obj0D_b_000C','docs/s2disasm/mappings/sprite/obj0D_a.asm')),
    's3k':dict(hud=('Map_HUD_ / word_DBC2','docs/skdisasm/General/Sprites/HUD Icon/Map - HUD.asm'),monitor=('Map_Monitor_OneUpIcon','docs/skdisasm/General/Sprites/Monitors/Map - Monitor.asm'),sign=('Map_EndSigns_ / word_83BAC','docs/skdisasm/General/Sprites/Signpost/Map - End Signs.asm'))}
    for game in sources:
        for surface in ('hud','monitor'):
            label,source=sources[game][surface]
            emit(game+'-'+surface,icon(game,surface=='monitor'),surface,game,label,source,[[0,0,2,2]])
        label,source=sources[game]['sign'];face=sign(game)
        emit(game+'-sign',face,'sign_face',game,label,source,[[0,0,3,4],[24,0,3,4]],
             composition='Large bust over separate native-colour backing plane; retain pole. Coordinates are canvas-local pixels then tile width/height.')
        for n,flip in [(1,False),(3,True)]:
            label={'s1':f'Map_Sign_internal.spin{n}','s2':f'Map_obj0D_a_{"0062" if n==1 else "0086"}','s3k':f'Map_EndSigns_ / {"word_83BE4" if n==1 else "word_83BF4"}'}[game]
            emit(game+f'-spin{n}',spin(face,flip),'sign_spin',game,label,source,[[0,0,4,4]],
                 composition='Projected bust and separate framed backing. Preflipped spin3; apply flip only once. Retain native edge-on frame.')
    emit('s2-sign-alternate',sign('s2',True),'sign_face_alternate','s2','Map_obj0D_a_0026 / Map_obj0D_b_0026',
         sources['s2']['sign'][1],[[0,0,1,4],[8,0,4,4],[40,0,1,4]],composition='Optional alternate bust with native blue backing plane; retain pole.')
    for letter in ('M','A'):
        emit('s1-name-'+letter,glyph(letter),'hud_name_glyph','s1','Nem_Lives / Map_HUD_internal.allyellow',
             'docs/s1disasm/artnem/HUD - Life Counter Icon.nem',[],palette='native S1 player line 0; index 15 yellow, index 1 shadow',advance=8 if letter=='M' else 6)
    for rec in records:
        game = rec['game']
        if rec['surface'] in ('hud', 'monitor'):
            rec['native_art_label'] = {'s1': 'Nem_Lives' if rec['surface']=='hud' else 'Nem_Monitors', 's2': 'ArtNem_Sonic_life_counter', 's3k': 'ArtNem_SonicLifeIcon'}[game]
            rec['native_tile_offset'] = '0x1c' if game=='s1' and rec['surface']=='monitor' else '0x0'
            if rec['surface']=='monitor' and game!='s1':
                rec['native_tile_note'] = 'Shares life-icon tiles at VRAM 0x7d4: S2 0x680+0x154; S3K 0x4c4+0x310.'
        elif rec['surface'].startswith('sign_'):
            rec['native_art_label'] = {'s1':'Nem_SignPost','s2':'ArtNem_Signpost','s3k':'ArtUnc_EndSigns'}[game]
            key=Path(rec['file']).stem
            bg=backing(game,'alternate' in key,'spin' in key,'spin3' in key)
            name=key+'-backing.png';bg.save(OUT/name,transparency=0,bits=4,optimize=True)
            assert bg.size==tuple(rec['dimensions']) and max(bg.get_flattened_data())<=5
            rec['backing'] = dict(file=name,native_palette_line=0,
                native_color_index=2 if 'alternate' in key else (9 if game=='s3k' else 15),
                rgb=[36,36,182] if 'alternate' in key else BACKGROUNDS[game],
                composition='Draw backing first, character second, pole separately. Backing has its own explicit palette; runtime needs a distinct available palette line from the MICA line. No runtime binding supplied.')
    (OUT/'presentation.yaml').write_text(json.dumps(dict(format='openggf-presentation-art-handoff-1',palette='../palette.yaml',
        transparent_index=0,palette_cycles=[dict(surface='super_palette',game=g,native_source_label=c['native_source_label'],source=c['source'],file='gilded.yaml',palette_line=0,entries_per_frame=16,frame_count=c['frame_count'],ticks_per_frame=c['ticks_per_frame']) for g,c in gilded()['cycles'].items()],notes='Round 3: original busts with separate native-colour backing planes. Shared character palette unchanged. No runtime/GGFP integration.',surfaces=records),indent=2)+'\n')
    cycle=gilded();(OUT/'gilded.yaml').write_text(json.dumps(cycle,indent=2)+'\n')
    for rec in records:
        im=Image.open(OUT/rec['file']);assert im.mode=='P' and im.size==tuple(rec['dimensions']) and max(im.get_flattened_data())<16
        assert im.getpalette()[:48]==sum(FONT_COLORS if rec['surface']=='hud_name_glyph' else COLORS,[]) and im.info['transparency']==0
    if review: make_review(Path(review),images,cycle)
    print(f'PASS: {len(records)} indexed presentation surfaces; fixed indices, native dimensions; Gilded 9/3-frame cycles')


def make_review(dest,images,cycle):
    dest.mkdir(parents=True,exist_ok=True)
    # Read existing Nemesis oracle by absolute path under isolated Python (-I).
    spec=importlib.util.spec_from_file_location('native',ROOT/'tools/timing/s3k-instruction-ledgers/vint/nem/nem_reference.py')
    nem=importlib.util.module_from_spec(spec);spec.loader.exec_module(nem)
    def tiles(path):
        b=(ROOT/path).read_bytes()
        if path.endswith('.nem'):
            # Oracle's bit lookahead may read past the final code; zero guard only,
            # header still bounds the exact number of output rows.
            b=b''.join(r.to_bytes(4,'big') for r in nem.decode(b+bytes(8),0))
        result=[]
        for n in range(0,len(b),32):
            im=indexed((8,8));im.putdata([p for v in b[n:n+32] for p in (v>>4,v&15)]);result.append(im)
        return result
    def piece(ts,start,w,h):
        im=indexed((w*8,h*8))
        for x in range(w):
            for y in range(h):im.paste(ts[start+x*h+y],(x*8,y*8))
        return im
    refs={};native_pals={}
    for game,life,sgn,pal in [
        ('s1','docs/s1disasm/artnem/HUD - Life Counter Icon.nem','docs/s1disasm/artnem/Signpost.nem','docs/s1disasm/palette/Sonic.bin'),
        ('s2','docs/s2disasm/art/nemesis/Sonic lives counter.nem','docs/s2disasm/art/nemesis/Signpost.nem','docs/s2disasm/art/palettes/SonicAndTails.bin'),
        ('s3k','docs/skdisasm/General/Sprites/HUD Icon/Sonic Life Icon.nem','docs/skdisasm/General/Sprites/Signpost/End Signs.unc','docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')]:
        b=(ROOT/pal).read_bytes();colors=[]
        for i in range(0,32,2):
            w=int.from_bytes(b[i:i+2],'big');colors.append([round(((w>>s)&14)*255/14) for s in (0,4,8)])
        native_pals[game]=colors
        ts=tiles(life);refs[game+'-hud']=piece(ts,0,2,2)
        refs[game+'-monitor']=piece(tiles('docs/s1disasm/artnem/Monitors.nem'),0x1c,2,2) if game=='s1' else piece(ts,0,2,2)
        ts=tiles(sgn);start=0x22 if game=='s2' else 0x20
        im=indexed((48,32));im.paste(piece(ts,start,3,4),(0,0));im.paste(piece(ts,start+12,3,4),(24,0));refs[game+'-sign']=im
        im=piece(ts,0xc,4,4);refs[game+'-spin1']=im;refs[game+'-spin3']=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if game=='s2':
            im=indexed((48,32));im.paste(piece(ts,0x3a,1,4),(0,0));im.paste(piece(ts,0x3e,4,4),(8,0));im.paste(piece(ts,0x3a,1,4).transpose(Image.Transpose.FLIP_LEFT_RIGHT),(40,0));refs['s2-sign-alternate']=im
        if game=='s1':
            name=piece(tiles(life),4,4,2)
            # Decoded original N and O provide the native stroke/height reference.
            refs['s1-name-M']=name.crop((13,0,20,8));refs['s1-name-A']=name.crop((6,0,12,8))
    for scale in (1,4):
        sheet=Image.new('RGB',(160*scale, len(images)*43*scale),(109,109,109));d=ImageDraw.Draw(sheet)
        for n,(key,mica) in enumerate(images.items()):
            native=refs[key].copy();game='s3k' if key.startswith('s3k') else key[:2]
            native.putpalette(sum(native_pals[game],[])+[0]*(768-48));native.info['transparency']=0
            for x,im in [(0,composed(key,mica) if 'sign' in key or 'spin' in key else mica),(54,native)]:
                rgba=im.convert('RGBA').resize((im.width*scale,im.height*scale),NEAREST);sheet.paste(rgba,(x*scale,n*43*scale),rgba)
            d.text((106*scale,n*43*scale),key,fill='white')
        sheet.save(dest/f'presentation-{scale}x.png')
        for group,predicate in [('faces',lambda k:'hud' in k or 'monitor' in k),('signs',lambda k:'sign' in k),('spins',lambda k:'spin' in k)]:
            rows=[n for n,k in enumerate(images) if predicate(k)]
            strip=Image.new('RGB',(sheet.width,len(rows)*43*scale),(109,109,109))
            for j,n in enumerate(rows):strip.paste(sheet.crop((0,n*43*scale,sheet.width,(n+1)*43*scale)),(0,j*43*scale))
            strip.save(dest/f'{group}-{scale}x.png')
    wait=Image.open(ART/'frames/wait-00.png');frames=[]
    for table in cycle['cycles']['s2']['frames']:
        im=wait.copy();im.putpalette(sum(table,[])+[0]*(768-48));rgba=im.convert('RGBA');bg=Image.new('RGBA',im.size,(109,109,109));bg.alpha_composite(rgba)
        frames.append(bg.convert('RGB').resize((im.width*4,im.height*4),NEAREST))
    for scale in (1,4):
        strip=Image.new('RGB',(wait.width*9*scale,wait.height*scale),(109,109,109))
        for n,frame in enumerate(frames):
            strip.paste(frame.resize((wait.width*scale,wait.height*scale),NEAREST),(n*wait.width*scale,0))
        strip.save(dest/f'gilded-wait-{scale}x.png')
    frames[0].save(dest/'gilded-wait.gif',save_all=True,append_images=frames[1:],duration=133,loop=0,disposal=2)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--review',type=Path);main(p.parse_args().review)
