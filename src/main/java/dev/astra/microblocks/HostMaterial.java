package dev.astra.microblocks;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Validated vanilla states and value-based RGB materials with a bounded allocation cache. */
public final class HostMaterial {
    private static final Map<String,HostMaterial> CATALOG=load();
    public static final HostMaterial STONE=CATALOG.get("minecraft:stone"), OAK_PLANKS=CATALOG.get("minecraft:oak_planks");
    private static final int COLOR_CACHE_LIMIT=8192;
    private static final Map<Integer,HostMaterial> COLORS=new LinkedHashMap<>(256,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,HostMaterial> entry) {return size()>COLOR_CACHE_LIMIT;}
    };
    private static final Identifier FLAT_TEXTURE=Identifier.parse("astra_microblocks:block/flat_color");
    private final int rgb;
    private final String id;
    private final boolean transparent, animated;
    private final int rotationX,rotationY;
    private final Map<Direction,Boolean> translucent;
    private final Map<Direction,Identifier> textures;
    private final Map<Direction,Integer> rotations;
    private final Map<Direction,float[]> bounds;
    private HostMaterial(int color) {
        rgb=color;id="astra_microblocks:rgb_"+HexFormat.of().toHexDigits(color).substring(2);
        textures=Map.of();rotations=Map.of();bounds=Map.of();translucent=Map.of();
        rotationX=rotationY=0;transparent=animated=false;
    }
    public static synchronized HostMaterial color(int rgb) {
        if(rgb<0 || rgb>0xffffff) throw new IllegalArgumentException("RGB must be 000000..ffffff");
        return COLORS.computeIfAbsent(rgb,HostMaterial::new);
    }
    public boolean isColor() {return rgb>=0;}
    public int argb() {return isColor()?0xff000000|rgb:0xffffffff;}
    static synchronized int colorCacheSize() {return COLORS.size();}
    private HostMaterial(JsonObject data) {
        rgb=-1;
        textures=new EnumMap<>(Direction.class);rotations=new EnumMap<>(Direction.class);
        bounds=new EnumMap<>(Direction.class);translucent=new EnumMap<>(Direction.class);
        transparent=data.has("transparent") && data.get("transparent").getAsBoolean();
        animated=data.has("animated") && data.get("animated").getAsBoolean();
        id=data.get("id").getAsString(); rotationX=data.get("x").getAsInt(); rotationY=data.get("y").getAsInt();
        for(var face:Direction.values()) {
            var value=data.getAsJsonObject("faces").getAsJsonObject(face.getName());
            translucent.put(face,value.has("force_translucent") && value.get("force_translucent").getAsBoolean());
            textures.put(face,Identifier.parse(value.get("texture").getAsString()));
            rotations.put(face,value.get("rotation").getAsInt());
            var uv=value.getAsJsonArray("uv");bounds.put(face,new float[]{uv.get(0).getAsFloat()/16,uv.get(1).getAsFloat()/16,uv.get(2).getAsFloat()/16,uv.get(3).getAsFloat()/16});
        }
    }
    private static Map<String,HostMaterial> load() {
        try(var input=HostMaterial.class.getResourceAsStream("/data/astra_microblocks/materials.json")) {
            var result=new LinkedHashMap<String,HostMaterial>();
            for(var element:JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(input),StandardCharsets.UTF_8)).getAsJsonArray()) {
                var material=new HostMaterial(element.getAsJsonObject());
                if(result.put(material.id,material)!=null) throw new IllegalStateException("Duplicate material "+material.id);
            }
            return Collections.unmodifiableMap(result);
        } catch(java.io.IOException error) {throw new ExceptionInInitializerError(error);}
    }
    /** The historical two-host test matrix; the complete supported palette is catalog(). */
    public static HostMaterial[] values() {return new HostMaterial[]{STONE,OAK_PLANKS};}
    public static List<HostMaterial> catalog() {return List.copyOf(CATALOG.values());}
    public static Optional<HostMaterial> find(String id) {
        if(id.startsWith("astra_microblocks:rgb_")) {
            String hex=id.substring("astra_microblocks:rgb_".length());
            if(hex.length()!=6) return Optional.empty();
            for(int i=0;i<6;i++) {char c=hex.charAt(i);if(!(c>='0'&&c<='9'||c>='a'&&c<='f'||c>='A'&&c<='F')) return Optional.empty();}
            return Optional.of(color(Integer.parseInt(hex,16)));
        }
        return Optional.ofNullable(CATALOG.get(id.startsWith("minecraft:")?id:"minecraft:"+id));
    }
    public String id() {return id;}
    public boolean transparent() {return transparent;}
    public boolean animated() {return animated;}
    public String blockId() {return id.contains("[")?id.substring(0,id.indexOf('[')):id;}
    public String label() {
        if(isColor()) return String.format(Locale.ROOT,"Color #%06X",rgb);
        String text=id.substring("minecraft:".length()).replace("[axis="," (").replace("]",")").replace('_',' ');
        return Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    public BlockState state() {
        if(isColor()) throw new IllegalStateException("RGB colors are sculpture materials, not registered vanilla states");
        var state=BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId())).defaultBlockState();
        if(id.contains("[axis=")) state=state.setValue(BlockStateProperties.AXIS,Direction.Axis.valueOf(id.substring(id.length()-2,id.length()-1).toUpperCase(Locale.ROOT)));
        return state;
    }
    public static Optional<HostMaterial> supported(BlockState state) {
        String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if(state.hasProperty(BlockStateProperties.AXIS)) id+="[axis="+state.getValue(BlockStateProperties.AXIS).getName()+"]";
        var material=CATALOG.get(id);
        return material!=null && material.state()==state?Optional.of(material):Optional.empty();
    }
    public static HostMaterial of(BlockState state) {
        if(state.is(AstraMicroblocks.OAK_HOST)) return OAK_PLANKS;
        if(state.is(AstraMicroblocks.TEST_HOST)) return STONE;
        return supported(state).orElseThrow(() -> new IllegalArgumentException("Unsupported material: "+state));
    }
    private float[] inverse(float x,float y,float z) {
        for(int i=0;i<rotationY/90;i++) {float t=x;x=-z;z=t;}
        for(int i=0;i<rotationX/90;i++) {float t=y;y=-z;z=t;}
        return new float[]{x,y,z};
    }
    private Direction sourceFace(Direction face) {
        var v=inverse(face.getStepX(),face.getStepY(),face.getStepZ());
        for(var candidate:Direction.values()) if(candidate.getStepX()==v[0] && candidate.getStepY()==v[1] && candidate.getStepZ()==v[2]) return candidate;
        throw new IllegalStateException("Invalid material rotation");
    }
    public boolean forceTranslucent(Direction face) {return !isColor() && translucent.get(sourceFace(face));}
    public Identifier texture() {return texture(Direction.NORTH);}
    public Identifier texture(Direction face) {return isColor()?FLAT_TEXTURE:textures.get(sourceFace(face));}
    /** Unrotated vanilla cube UV projection, transformed with the block-state model. */
    public float[] uv(Direction face,MicroblockRenderMesh.Vertex vertex) {
        var p=inverse(vertex.x()/16f-.5f,vertex.y()/16f-.5f,vertex.z()/16f-.5f);
        float x=p[0]+.5f,y=p[1]+.5f,z=p[2]+.5f,u,v;
        var source=sourceFace(face);
        switch(source) {
            case DOWN -> {u=x;v=1-z;} case UP -> {u=x;v=z;}
            case NORTH -> {u=1-x;v=1-y;} case SOUTH -> {u=x;v=1-y;}
            case WEST -> {u=z;v=1-y;} default -> {u=1-z;v=1-y;}
        }
        if(isColor()) return new float[]{u,v};
        for(int i=0;i<rotations.get(source)/90;i++) {float t=u;u=v;v=1-t;}
        var rectangle=bounds.get(source);
        return new float[]{rectangle[0]+u*(rectangle[2]-rectangle[0]),rectangle[1]+v*(rectangle[3]-rectangle[1])};
    }
    public HostMaterial transformed(Direction.Axis rotation,boolean mirror) {
        if(mirror || !id.contains("[axis=")) return this;
        var axis=state().getValue(BlockStateProperties.AXIS);
        if(axis==rotation) return this;
        var next=Arrays.stream(Direction.Axis.values()).filter(a -> a!=axis && a!=rotation).findFirst().orElseThrow();
        return CATALOG.get(blockId()+"[axis="+next.getName()+"]");
    }
    @Override public boolean equals(Object other) {return this==other || other instanceof HostMaterial m && id.equals(m.id);}
    @Override public int hashCode() {return id.hashCode();}
    @Override public String toString() {return id;}
}
