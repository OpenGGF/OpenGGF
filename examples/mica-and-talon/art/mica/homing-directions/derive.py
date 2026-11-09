"""Use the owner-approved indexed rotation tool, no generated directional art."""
import sys,json,shutil
from pathlib import Path
from PIL import Image
sys.path.insert(0,str(Path(__file__).resolve().parents[5]/'tools/modding/playable_art'));from derive_slopes import rotate
import pipeline
A=Path(__file__).resolve().parent.parent;D=A/'homing-directions';D.mkdir(exist_ok=True);m=json.loads((A/'frames.yaml').read_text());fs=[f for f in m['frames'] if f['animation']=='HOMING_DIVE'];recipe={'palette':'../palette.yaml','frames':[]};legacy=[]
for direction,angle in [('FORWARD',45),('FORWARD_UP',90),('UP',135),('FORWARD_DOWN',0),('DOWN',-45)]:
 for i,f in enumerate(fs):
  im=Image.open(A/f['file']);canvas=Image.new('P',(128,128));canvas.putpalette(im.getpalette());canvas.paste(im,(64-f['hotspot'][0],64-f['hotspot'][1]))
  out=canvas if angle==0 else rotate(canvas,45).transpose(Image.Transpose.ROTATE_270) if angle==-45 else rotate(canvas,angle)
  b=out.getbbox();cut=out.crop(b);hot=[64-b[0],64-b[1]];key=f'homing_dive_{direction.lower()}-{i:02}'
  recipe['frames'].append({'id':key,'animation':'HOMING_DIVE_'+direction,'order':i,'ticks':2,'notes':f'Rotate A forward-down pose {angle} degrees CCW with derive_slopes.rotate; no regeneration/painting.','native_pixels':{'rows':[''.join(format(cut.getpixel((x,y)),'x') for x in range(cut.width)) for y in range(cut.height)],'anchor':hot}})
(D/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n');pipeline.build(D/'recipe.json');handoff=json.loads((D/'frames.yaml').read_text());handoff['aliases']={'HOMING_DIVE':'HOMING_DIVE_FORWARD_DOWN'};(D/'frames.yaml').write_text(json.dumps(handoff,indent=2)+'\n')
