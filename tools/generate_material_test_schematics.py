"""Reproducible 0.5.0 material galleries. Requires numpy and nbtlib; copies no textures."""
import json
import math
import sys
import time
from pathlib import Path
import numpy as np
import nbtlib
from nbtlib import Compound as C, Int as I, Long as J, String as S, List as L, LongArray as LA

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
M = ["air"] + [e["id"] for e in json.loads((ROOT / "src/main/resources/data/astra_microblocks/materials.json").read_text())]

def vec(x,y,z): return C({"x":I(x),"y":I(y),"z":I(z)})

def export(name, dimensions, hosts, version="0.5.0"):
    w,h,d = dimensions
    palette = [C({"Name":S("minecraft:air")}), C({"Name":S("astra_microblocks:test_host"),"Properties":C({"orientation":S("0")})}), C({"Name":S("minecraft:smooth_quartz")})]
    blocks=np.zeros((h,d,w),np.uint8);blocks[0]=2;tiles=[]
    for (x,y,z), volume in hosts.items():
        blocks[y,z,x]=1;ids=[int(i) for i in np.unique(volume) if i]
        bits=max(1,len(ids).bit_length());stride=64//bits;lookup=np.zeros(len(M),np.uint64)
        for i,m in enumerate(ids):lookup[m]=i+1
        values=lookup[volume.ravel()];words=np.zeros(math.ceil(4096/stride),np.uint64)
        for i in range(stride):
            part=values[i::stride];words[:len(part)] |= part << np.uint64(i*bits)
        tag=C({"version":I(2),"original":S(M[ids[0]]),"size":I(len(ids)),"bits":I(bits),"cells":LA(words.view(np.int64))})
        for i,m in enumerate(ids):tag[f"material_{i}"]=S(M[m])
        tiles.append(C({"id":S("astra_microblocks:test_host"),"x":I(x),"y":I(y),"z":I(z),"revision":J(1),"astra_orientation":I(0),"volume_v4":tag}))
    flat=blocks.ravel().astype(np.uint64);ii=np.arange(len(flat),dtype=np.uint64);words=np.zeros(math.ceil(len(flat)/32),np.uint64)
    np.bitwise_or.at(words,ii//32,flat<<((ii%32)*2))
    region=C({"Position":vec(0,0,0),"Size":vec(w,h,d),"BlockStatePalette":L[C](palette),"BlockStates":LA(words.view(np.int64)),"TileEntities":L[C](tiles),"Entities":L[C]([]),"PendingBlockTicks":L[C]([]),"PendingFluidTicks":L[C]([])})
    meta=C({"Name":S(name),"Author":S("Astra Microblocks"),"Description":S(f"Astra {version} material test. Requires {version}."),"RegionCount":I(1),"TotalVolume":I(blocks.size),"TotalBlocks":I(int(np.count_nonzero(blocks))),"EnclosingSize":vec(w,h,d),"TimeCreated":J(int(time.time()*1000)),"TimeModified":J(int(time.time()*1000))})
    path=OUT/(name+".litematic")
    nbtlib.File({"Version":I(7),"SubVersion":I(1),"MinecraftDataVersion":I(4903),"Metadata":meta,"Regions":C({"Gallery":region})},gzipped=True).save(path)
    saved=nbtlib.load(path)["Regions"]["Gallery"]
    packed=np.asarray(saved["BlockStates"],dtype=np.int64).view(np.uint64)
    assert np.array_equal(((packed[ii//32]>>((ii%32)*2))&3).reshape(blocks.shape),blocks)
    for tile in saved["TileEntities"]:
        p=tile["volume_v4"];b=int(p["bits"]);stride=64//b
        lookup=np.array([0]+[M.index(str(p[f"material_{i}"])) for i in range(int(p["size"]))],np.uint16)
        packed=np.asarray(p["cells"],dtype=np.int64).view(np.uint64);idx=np.arange(4096,dtype=np.uint64)
        decoded=lookup[(packed[idx//stride]>>((idx%stride)*b))&((1<<b)-1)].reshape(16,16,16)
        assert np.array_equal(decoded,hosts[tuple(int(tile[k]) for k in ('x','y','z'))])
    return {"name":name,"dimensions":dimensions,"hosts":len(hosts),"roundtrip":"All block states and microcells matched","bytes":path.stat().st_size}

def main():
    hosts={};index=[]
    for i,material in enumerate(M[1:]):
        pos=((i%22)*2,1,(i//22)*2);v=np.full((16,16,16),i+1,np.uint16)
        v[4:12,:8,4:12]=0;hosts[pos]=v
        index.append({"position":pos,"material":material})
    results=[export("Astra_0.5.0_All_468_Material_States",(44,3,44),hosts)]
    (OUT/"material-positions.json").write_text(json.dumps(index,indent=2))
    ids=['glass','blue_stained_glass','red_stained_glass','tinted_glass','ice','copper_grate','magma_block','sea_lantern','prismarine','sculk','crimson_stem[axis=y]','warped_stem[axis=x]']
    hosts={}
    for i,material in enumerate(ids):
        for dy in range(3):
            for dx in range(2):
                v=np.full((16,16,16),M.index('minecraft:'+material),np.uint16)
                v[5:11,:5,5:11]=0
                if i<6:v[3:13,10:14,3:13]=M.index('minecraft:gold_block')
                hosts[(i*3+dx,dy+1,2)]=v
    results.append(export('Astra_0.5.0_Glass_And_Animation_Lab',(36,5,6),hosts))
    (OUT/'lab-material-order.json').write_text(json.dumps(ids,indent=2))
    (OUT/'validation.json').write_text(json.dumps(results,indent=2));print(json.dumps(results))

if __name__ == "__main__": main()
