#!/usr/bin/env python3
"""Purpose: full-rate, exact-palette TALON family/native reviews and identity lineup.
Inputs: scratch baseline/ or after/ audit JSON; pinned native disassemblies; indexed
TALON recipe/handoff. Native art is written only to supplied scratch outside Git.
Origin: owner-requested missing-family art review, 2026-11-09.
Run from repository root: python3 examples/mica-and-talon/art/talon/family_review.py SCRATCH
"""
import argparse,html,json,sys,importlib.util,math
from pathlib import Path
from PIL import Image,ImageDraw,GifImagePlugin
ROOT=Path(__file__).resolve().parents[4];ART=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'tools/modding/playable_art'));import native_pose as n
N=Image.Resampling.NEAREST

def guide_boxes(draw, x, y):
    # Fixed WAIT anatomy at the shared engine centre; never fit boxes to wings.
    for box, color in [((36,32,47,42),'#f4a0b0'), ((38,44,47,53),'#70d0f0')]:
        draw.rectangle(tuple((x if i%2==0 else y)+v*4 for i,v in enumerate(box)), outline=color, width=2)


def tails(game,index):
    import re
    if game=='S2':
        base=ROOT/'docs/s2disasm';maps=(base/'mappings/sprite/Tails.asm').read_text();plc=(base/'mappings/spriteDPLC/Tails.asm').read_text()
        def block(t):
            labels=re.findall(r'mappingsTableEntry.w\s+(\w+)',t);return t.split(labels[index]+':')[1].split(labels[index]+'_End')[0]
        raw=(base/"art/uncompressed/Tails's art.bin").read_bytes();data=b''.join(raw[n.num(t)*32:(n.num(t)+n.num(k))*32] for k,t in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',block(plc)));pieces=[list(map(n.num,a.split(','))) for a in re.findall(r'spritePiece\s+([^;\n]+)',block(maps))];colors=n.palette(base/'art/palettes/SonicAndTails.bin')
    else:
        base=ROOT/'docs/skdisasm/General/Sprites/Tails';maps=(base/'Map - Tails.asm').read_text();plc=(base/'DPLC - Tails.asm').read_text()
        def block(t):
            labels=re.findall(r'dc.w\s+(\w+)-',t);return re.split(r'\n[A-Za-z_]\w*:',t.split(labels[index]+':')[1])[0]
        # Tails_Load_PLC2 selects the extra art bank at mapping $D1.
        raw=(base/('Art/Tails Extra.unc' if index >= 0xD1 else 'Art/Tails.unc')).read_bytes();words=[n.num(v) for v in re.findall(r'dc.w\s+([^\n;]+)',block(plc))][1:];data=b''.join(raw[(v&4095)*32:((v&4095)+(v>>12)+1)*32] for v in words);pieces=[];colors=n.palette(ROOT/'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')
        for a in re.findall(r'dc.b\s+([^\n;]+)',block(maps)):
            y,size,hi,lo,xh,xl=map(n.num,a.split(','));x=xh*256+xl;t=hi*256+lo;pieces.append([x-65536 if x>32767 else x,y-256 if y>127 else y,(size>>2)+1,(size&3)+1,t&2047,bool(t&2048),bool(t&4096)])
    c=Image.new('RGBA',(128,128))
    for x,y,w,h,t,fx,fy,*_ in pieces:
        im=n.piece(data,t,w,h,colors)
        if fx:im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if fy:im=im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        c.alpha_composite(im,(64+x,64+y))
    box=c.getbbox();return (c.crop(box),[64-box[0],64-box[1]]) if box else (Image.new('RGBA',(1,1)),[0,0])

def placed(im,hot):
    c=Image.new('RGBA',(80,80));c.alpha_composite(im.convert('RGBA'),(40-hot[0],40-hot[1]));return c

def gif(path,pictures,steps,ticks):
    # Explicit full frames and an exact union palette. Never optimize/difference.
    rgbs=[]
    for pic in pictures:
        bg=Image.new('RGB',pic.size,'#888888');bg.paste(pic,mask=pic.getchannel('A'));rgbs.append(bg.resize((bg.width*2,bg.height*2),N))
    colors=sorted(set(c for im in rgbs for c in im.get_flattened_data()));assert len(colors)<=256
    lookup={c:i for i,c in enumerate(colors)};pal=[v for c in colors for v in c]+[0]*(768-len(colors)*3);indexed=[]
    for im in rgbs:
        idx=Image.new('P',im.size);idx.putpalette(pal);idx.putdata([lookup[c] for c in im.get_flattened_data()]);indexed.append(idx)
    with path.open('wb') as fp:
        header,_=GifImagePlugin.getheader(indexed[0],info={'loop':0,'optimize':False})
        for b in header:fp.write(b)
        for clock,step in enumerate(steps):
            dt=(round((clock+1)*100/60)-round(clock*100/60))*10
            for b in GifImagePlugin.getdata(indexed[step],duration=dt,disposal=2):fp.write(b)
        fp.write(b';')
    with Image.open(path) as check:
        assert check.n_frames==len(steps)
        expected=[im.tobytes() for im in rgbs]
        for clock,step in enumerate(steps):
            check.seek(clock)
            assert check.convert('RGB').tobytes()==expected[step], (path.name,clock,'palette/pixel drift')
            assert check.info['duration']==(round((clock+1)*100/60)-round(clock*100/60))*10

    return len(steps)

def review(dest):
    dest=dest.resolve();assert not dest.is_relative_to(ROOT),'Native review pixels must stay outside repository'
    dest.mkdir(parents=True,exist_ok=True);audit=dest/('after' if (dest/'after/talon-S2.json').exists() else 'baseline');man=json.loads((ART/'frames.yaml').read_text());by={f['id']:f for f in man['frames']};aliases=man['nativeFrames'];gen=json.loads((ART/'family-generation.json').read_text());groups={}
    for f in man['frames']:groups.setdefault(f['animation'],[]).append(f)
    page='''<!doctype html><meta charset="utf-8"><title>TALON families</title><style>body{color:var(--foreground,#111);font:16px var(--font-sans,system-ui);margin:0}img{image-rendering:pixelated}section{overflow:auto}table{border-collapse:collapse}td,th{border:1px solid var(--border,#678);padding:6px}a{color:var(--primary,#926020)}</style><h1>TALON animation families</h1><p>Left: TALON. Right: native body drawing. Exact native script timing, repetitions and terminators; LOOP_BACK includes two tail repeats. Each 60Hz tick is a full GIF frame, with an exact union palette and cumulative centisecond rounding. Browsers may clamp 10ms delays. Speed-driven clocks use explicit samples from the native reload formula: PUSH at ground speed 0 (33 ticks), roll at $0600 (1 tick), and walk at $0100 (8 ticks). Pose/angle selection is a static review input. No gameplay or route certification. Tails' separate appendage/rotor is excluded; wings are integrated into TALON. S1 has no native Tails: its Sonic route is labelled separately.</p>'''
    groups.setdefault('BURNT', [by[aliases['family-burnt/0']]])
    before=dest/'baseline/totals.json';after=dest/'after/totals.json'
    if before.exists() and after.exists():
        a=json.loads(before.read_text());b=json.loads(after.read_text());page+='<h2>Fallback obligations before and after</h2><p>Includes inherited presentation gaps. Gray: before. Blue: after.</p><svg viewBox="0 0 420 170" role="img" aria-label="TALON fallback obligations before and after" style="max-width:600px;width:100%">'
        for row,game in enumerate(('S1','S2','S3K')):
            old=a['TALON/'+game]['fallback'];new=b['TALON/'+game]['fallback'];y=15+row*50;page+=f'<text x="0" y="{y+20}" fill="currentColor">{game}</text><rect x="45" y="{y}" width="{old*2.5}" height="12" fill="var(--muted-foreground,#888)"/><rect x="45" y="{y+16}" width="{new*2.5}" height="12" fill="var(--chart-1,#4695ee)"/><text x="310" y="{y+20}" fill="currentColor">{old} → {new}</text>'
        page+='</svg>'
    scale = gen.get('scaleReview20261110', {}).get('areaComparison', {})
    if scale:
        page += '<h2>Opaque-area sanity check</h2><p>sqrt(mean TALON area / mean original source-reference area). Gray before, blue after; green line is 1.0. Source references include Sonic for families absent from native Tails. Head widths are in the anatomical table below.</p><svg viewBox="0 0 650 450" style="max-width:900px;width:100%" role="img" aria-label="Before and after family size ratios">'
        for row, (family, frames) in enumerate(scale.items()):
            old, new = [math.sqrt(sum(f[k] for f in frames) / sum(f['nativeArea'] for f in frames)) for k in ('beforeArea','afterArea')]
            y = row * 38 + 10
            page += f'<text x="0" y="{y+16}" fill="currentColor" font-size="13">{family}</text><rect x="180" y="{y}" width="{old*280}" height="10" fill="#999"/><rect x="180" y="{y+12}" width="{new*280}" height="10" fill="#4695ee"/><text x="500" y="{y+16}" fill="currentColor" font-size="13">{old:.3f} → {new:.3f}</text>'
        page += '<path d="M460 0V430" stroke="#22884a" stroke-width="2"/></svg>'
    cache={}
    for family in groups:
        page+=f'<h2>{family}</h2>'
        for game,character,profile in [('S1','mica','s1'),('S2','talon','s2-tails'),('S3K','talon','s3k-tails')]:
            au=json.loads((audit/f'{character}-{game}.json').read_text());pr=json.loads((ROOT/f'src/main/resources/animation-families/{profile}.json').read_text());matched=[(sid,b) for sid,b in pr.items() if b['family']==family]
            if not matched:page+=f'<p>{game}: no native {"Tails" if game!="S1" else "Sonic"} script for this family.</p>';continue
            for sid,b in matched:
                script=au['nativeScripts'][sid];pictures=[]
                for i,idx in enumerate(script['frames']):
                    key=(game,idx)
                    if key not in cache:
                        if game=='S1':
                            p=dest/'host-native'/f's1-{idx:02x}-native.png';rr=json.loads((dest/'host-native/references.json').read_text())[f's1-{idx:02x}'];cache[key]=(Image.open(p).convert('RGBA'),rr['hotspot'])
                        else:cache[key]=tails(game,idx)
                    native,hot=cache[key];alias=b['phases'][i];name=aliases.get(f'family-{family.lower().replace("_","-")}/{alias}')
                    if name is None:
                        ids=man.get('scripts',{}).get(family,{}).get('frames',[f['id'] for f in groups[family]]);name=ids[i%len(ids)]
                    f=by[name];left=placed(Image.open(ART/f['file']),f['hotspot']);right=placed(native,hot);pair=Image.new('RGBA',(160,80));pair.alpha_composite(left);pair.alpha_composite(right,(80,0));pictures.append(pair)
                dt=script['delay']+1 if script['delay']<128 else (33 if script['delay']==0xFD else 1 if script['delay']==0xFE else 8);seq=[i for i in range(len(pictures)) for _ in range(dt)]
                if script['endAction']=='LOOP_BACK':seq+=seq[-script['endParam']*dt:]*2
                wing_note=''
                if family.startswith('TAILS_FLY') and family in man.get('scripts',{}):
                    wing=man['scripts'][family];wing_seq=[key for key,duration in zip(wing['frames'],wing['durations']) for _ in range(duration)]
                    native_seq=seq;seq=[];combined=[];seen={}
                    for clock in range(math.lcm(len(native_seq),len(wing_seq))):
                        native_step=native_seq[clock%len(native_seq)];frame_id=wing_seq[clock%len(wing_seq)];pair_key=(native_step,frame_id)
                        if pair_key not in seen:
                            f=by[frame_id];pair=Image.new('RGBA',(160,80));pair.alpha_composite(placed(Image.open(ART/f['file']),f['hotspot']));pair.alpha_composite(pictures[native_step].crop((80,0,160,80)),(80,0));seen[pair_key]=len(combined);combined.append(pair)
                        seq.append(seen[pair_key])
                    pictures=combined;wing_note=' Native body timing at right; TALON integrated wings use the approved authored wing clock at left. Window is the clocks’ least common multiple.'
                name=f'{family}-{game}-{sid}.gif';count=gif(dest/name,pictures,seq,dt);page+=f'<h3>{game} {"Sonic reference (no native Tails)" if game=="S1" else "Tails"} ${int(sid):02X}</h3><img src="{name}"><p>{count} ticks; delay {script["delay"]}; end {script["endAction"]} / {script["endParam"]}.{wing_note}</p>'
    # Every source phase, including slopes, shares one engine centre and reference guides.
    fs=man['frames'];w=320;h=360;out=Image.new('RGB',(w*8,h*((len(fs)+7)//8)),'#888888');draw=ImageDraw.Draw(out)
    for i,f in enumerate(fs):
        x=i%8*w;y=i//8*h;im=placed(Image.open(ART/f['file']),f['hotspot']).resize((320,320),N);out.paste(im,(x,y),im);draw.text((x+3,y+325),f['id'],fill='white');guide_boxes(draw,x,y)
        for yy,col in [(28,'#f4a0b0'),(38,'#70d0f0'),(53,'#eec16c'),(59,'#88d897')]:draw.line((x,y+yy*4,x+w,y+yy*4),fill=col)
    out.save(dest/'lineup-source.png')
    sheet=json.loads((ROOT/'examples/mica-and-talon/src/main/mod/talon-sheet.yaml').read_text())
    rows=sum(math.ceil(len(set(script['frames']))/8) for script in sheet['animations'].values())
    out=Image.new('RGB',(320*8,360*rows),'#888888');draw=ImageDraw.Draw(out);row=0
    for animation,script in sheet['animations'].items():
        indices=list(dict.fromkeys(script['frames']));block_rows=math.ceil(len(indices)/8)
        for i,index in enumerate(indices):
            f=fs[index];x=i%8*320;y=(row+i//8)*360;im=placed(Image.open(ART/f['file']),f['hotspot']).resize((320,320),N);out.paste(im,(x,y),im);draw.text((x+3,y+325),animation+' / '+f['id'],fill='white');guide_boxes(draw,x,y)
        for subrow in range(block_rows):
            for yy,color in [(28,'#f4a0b0'),(38,'#70d0f0'),(53,'#eec16c'),(59,'#88d897')]:draw.line((0,(row+subrow)*360+yy*4,out.width,(row+subrow)*360+yy*4),fill=color)
        row+=block_rows
    out.save(dest/'lineup-all.png');page+='<h2>Whole-character baseline</h2><p>All 47 canonical animations, one block per key with long slope banks wrapped, repeated steps collapsed only in this static lineup. All source frames including slope banks are below. Pink skull and cyan torso guide boxes show the fixed 11px skull width and 9px torso of approved WAIT; guides are reference positions, not measurements of pitched poses. Pink skull top, cyan eye, amber waist, green exclusive foot baseline from approved WAIT. All frames share the engine centre; airborne and rotated poses retain their pose-relative offsets.</p><section><img src="lineup-all.png"></section><h2>All source drawings and slope banks</h2><section><img src="lineup-source.png"></section><h2>Landmark reduction table</h2><p>Source forehead rectangle and uniform source pixels per native pixel are measured reduction inputs. Anatomical manual review uses skull width excluding crest/strap and neck-to-waist distance along the torso axis. Hidden/curled/flash anatomy is not measurable. The previous approved core remains unchanged.</p><table><tr><th>Frame</th><th>Attempt</th><th>Forehead source rectangle</th><th>Uniform reduction</th><th>Hotspot</th></tr>'
    for key,rec in gen['frames'].items():page+=f'<tr><td>{key}</td><td>{rec["attempt"]}</td><td>{rec["goggleSourceBox"]}</td><td>{rec.get("finalScale",rec["scale"]):.4f}</td><td>{by[key]["hotspot"]}</td></tr>'
    landmarks=json.loads((ART/'family-landmarks.json').read_text())
    page+='</table><h2>Anatomical landmarks</h2><p>'+html.escape(landmarks['method'])+'</p><table><tr><th>Frame</th><th>Skull width</th><th>Torso height</th><th>Exception</th></tr>'
    for key,rec in landmarks['frames'].items():page+=f'<tr><td>{key}</td><td>{rec["skullWidth"]}</td><td>{rec["torsoHeight"]}</td><td>{rec["exception"] or ""}</td></tr>'
    page+='</table><p>Speed reloads use PlayableSpriteAnimation.computeSpeedDelay: max(0, base − abs(gsp)) shifted right, plus the timer-underflow tick. PUSH uses base $0800 / shift 6; roll $0400 / shift 8; walk $0800 / shift 8.</p><h2>Limits</h2><p>Native Tails flight variants have one body phase. Their native body reference remains still while TALON uses six integrated wing phases at the approved authored wing clock; native body bindings and terminators remain unchanged. No detached rotor clock or gameplay values are imported. Exact anatomical ±1px matching is a visual annotation tolerance, not a native-ROM geometric identity claim. Runtime carrying handhold/contact and slope contact still need gameplay review.</p>'
    (dest/'review.html').write_text(page)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('scratch',type=Path);review(parser.parse_args().scratch)
