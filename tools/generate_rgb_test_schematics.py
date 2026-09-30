"""RGB studio and explicitly optional geometry stress scenes; numpy + nbtlib required."""
import colorsys
import json
import numpy as np
from generate_material_test_schematics import export, M, OUT

lookup={name:i for i,name in enumerate(M)}
def color(rgb):
    name=f"astra_microblocks:rgb_{rgb:06x}"
    if name not in lookup:lookup[name]=len(M);M.append(name)
    return lookup[name]

hosts={}
for bx in range(4):
    for by in range(4):
        v=np.zeros((16,16,16),np.uint16)
        for y in range(16):
            for x in range(16):
                r,g,b=colorsys.hsv_to_rgb((bx*16+x)/64,1,0.2+0.8*(by*16+y)/63)
                rgb=(round(r*255)<<16)|(round(g*255)<<8)|round(b*255)
                v[y,6:10,x]=color(rgb)
        hosts[(bx+1,by+1,3)]=v
for i,rgb in enumerate([0,0xffffff,0xff0080,0x00ff80,0x0080ff,0xff8000,0x123456,0x01fea3]):
    hosts[(6+(i%2),1+i//2,2)]=np.full((16,16,16),color(rgb),np.uint16)
results=[export('Astra_0.6.0_RGB_Studio',(9,6,6),hosts,'0.6.0')]

v=np.array([color(i*4097) for i in range(4096)],np.uint16).reshape(16,16,16)
positions=[(x*2,1,z*2) for z in range(8) for x in range(8)]
results.append(export('Astra_0.6.0_RGB_Dense_64_Hosts',(16,3,16),dict.fromkeys(positions,v),'0.6.0'))
y,z,x=np.indices((16,16,16));isolated=v.copy();isolated[(x+y+z)%2!=0]=0
results.append(export('Astra_0.6.0_RGB_Isolated_64_OPTIONAL_STRESS',(16,3,16),dict.fromkeys(positions,isolated),'0.6.0'))
(OUT/'rgb-schematic-validation.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results))
