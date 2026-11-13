# Purpose: fill the empty quill area right of MICA's S2 settled thumbs-up (body
# selector 18) with back-row scales so the emblem no longer shows between the
# glove and the banner. Inputs: source/destination indexed PNG and a list of
# (cx, cy, rx, ry) scale ellipses. Origin: owner clipping report, 2026-11-11.
# Applied as: s2_clip_patch.py s2-18.png s2-18.png "[(63,63,5.5,4.5),(62,71,6.5,5.5)]"
import sys
from PIL import Image
src,dst,scales=sys.argv[1],sys.argv[2],eval(sys.argv[3])
a=Image.open(src); px=a.load(); W,H=a.size
add={}
for (cx,cy,rx,ry) in scales:
  for y in range(H):
    for x in range(W):
      dx=(x-cx)/rx; dy=(y-cy)/ry; d=(dx*dx+dy*dy)**.5
      if d>1.0 or px[x,y]!=0: continue
      if d>0.80: c=1
      elif dy>0.62: c=3
      elif dy>0.38: c=4
      elif dx<-0.25 and dy<-0.25 and d>0.35: c=6
      else: c=5
      if (x,y) not in add or c==1: add[(x,y)]=c if (x,y) not in add else add[(x,y)]
for (x,y),c in add.items(): px[x,y]=c
a.save(dst,transparency=0)
print(len(add),'pixels added')
