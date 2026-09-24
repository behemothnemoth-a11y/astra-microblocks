"""Generate material descriptors from an installed Minecraft client jar; never copies textures.
Usage: python tools/generate_material_catalog.py /path/to/26.2-client.jar
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAMES = (ROOT / "tools/material_blocks.txt").read_text().splitlines()

with zipfile.ZipFile(sys.argv[1]) as archive:
    def model(name):
        name = name.removeprefix("minecraft:")
        data = json.loads(archive.read("assets/minecraft/models/" + name + ".json"))
        parent = model(data["parent"]) if data.get("parent") and not data["parent"].startswith("builtin") else {}
        return {**parent, **data, "textures": {**parent.get("textures", {}), **data.get("textures", {})}}

    entries = []
    for name in NAMES:
        variants = json.loads(archive.read("assets/minecraft/blockstates/" + name + ".json"))["variants"]
        for properties, variant in variants.items():
            assert not properties or properties in ("axis=x", "axis=y", "axis=z"), (name, properties)
            if isinstance(variant, list):
                variant = variant[0]
            assert not variant.get("uvlock", False), (name, "UV-locked model needs additional support")
            data = model(variant["model"])
            elements = data.get("elements", [])
            assert len(elements) == 1 and elements[0]["from"] == [0, 0, 0] and elements[0]["to"] == [16, 16, 16], name
            assert not elements[0].get("rotation"), name
            faces = {}
            for direction, face in elements[0]["faces"].items():
                assert "tintindex" not in face, (name, "Tint needs additional support")
                uv = face.get("uv", [0, 0, 16, 16])
                assert all(value in (0, 16) for value in uv) and abs(uv[2]-uv[0]) == 16 and abs(uv[3]-uv[1]) == 16, (name, "Partial face UV")
                texture = face["texture"]
                while texture.startswith("#"):
                    texture = data["textures"][texture[1:]]
                if ":" not in texture:
                    texture = "minecraft:" + texture
                texture_path = "assets/minecraft/textures/" + texture.split(":")[1] + ".png"
                assert texture_path in archive.namelist(), (name, texture)
                assert texture_path + ".mcmeta" not in archive.namelist(), (name, "Animated texture")
                faces[direction] = {"texture": texture, "rotation": face.get("rotation", 0), "uv": uv}
            assert len(faces) == 6, name
            entries.append({"id": "minecraft:" + name + ("[" + properties + "]" if properties else ""),
                            "x": variant.get("x", 0), "y": variant.get("y", 0), "faces": faces})

output = ROOT / "src/main/resources/data/astra_microblocks/materials.json"
output.write_text("[\n" + ",\n".join("  " + json.dumps(entry, separators=(",", ":")) for entry in entries) + "\n]\n", encoding="utf-8", newline="\n")
print(f"Generated {len(NAMES)} blocks / {len(entries)} supported states")
