"""Offline sculpture preparation, optional TRELLIS.2 generation, and mesh inspection.

Nothing in this module is loaded by Minecraft. GPU imports are deferred until generate.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys

UPSTREAM = "https://github.com/microsoft/TRELLIS.2.git"
REVISION = "75fbf0183001ed9876c8dbb35de6b68552ee08bd"
MODEL = "microsoft/TRELLIS.2-4B"


def sha256(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def prepare(sheet, output):
    """Split our equal-panel magenta turnaround into individual RGBA views.

    TRELLIS.2 takes ONE image. Side/back are retained for review, not passed
    together as a collage which could produce a three-headed sculpture.
    """
    import numpy as np
    from PIL import Image
    output = Path(output)
    output.mkdir(parents=True, exist_ok=False)
    with Image.open(sheet) as source:
        image = source.convert("RGB")
    views = {}
    for i, name in enumerate(("front", "side", "back")):
        panel = image.crop((round(i * image.width / 3), 0,
                            round((i + 1) * image.width / 3), image.height))
        rgb = np.asarray(panel).astype(int)
        key = ((rgb[:, :, 0] > 110) & (rgb[:, :, 2] > 85)
               & (np.minimum(rgb[:, :, 0], rgb[:, :, 2]) - rgb[:, :, 1] > 55))
        alpha = Image.fromarray((~key).astype("uint8") * 255)
        box = alpha.getbbox()
        if box is None:
            raise ValueError(f"{name}: no sculpture found")
        rgb[key] = 0  # Prevent key-color bleed in tools that interpolate hidden RGB.
        rgba = Image.fromarray(rgb.astype("uint8")).convert("RGBA")
        rgba.putalpha(alpha)
        rgba = rgba.crop(box)
        # Keep head/wing tips and base within a transparent square margin.
        side = max(rgba.size)
        margin = max(4, round(side * .08))
        canvas = Image.new("RGBA", (side + margin * 2,) * 2)
        canvas.paste(rgba, ((canvas.width - rgba.width) // 2,
                            (canvas.height - rgba.height) // 2))
        path = output / f"{name}.png"
        canvas.save(path)
        views[name] = {"file": path.name, "sha256": sha256(path)}
    metadata = {"source_sha256": sha256(sheet), "views": views,
                "generation_view": "front", "status": "reference-only"}
    (output / "reference.json").write_text(json.dumps(metadata, indent=2) + "\n")
    return metadata


def inspect_mesh(path, height_blocks=12, max_triangles=1_000_000):
    """Report integrity and conservative bounding-box budgets; never repair silently."""
    import numpy as np
    import trimesh
    if not 0 < height_blocks <= 128:
        raise ValueError("Height must be greater than zero and at most 128 blocks")
    scene = trimesh.load(Path(path), force="scene", process=False)
    mesh = scene.to_mesh()
    if not len(mesh.faces) or not np.isfinite(mesh.vertices).all():
        raise ValueError("Mesh is empty or contains non-finite coordinates")
    if np.any(mesh.extents <= 0):
        raise ValueError("Mesh must have nonzero extent on all axes")
    if len(mesh.faces) > max_triangles:
        raise ValueError(f"Triangle budget exceeded: {len(mesh.faces):,} > {max_triangles:,}")
    # GLB uses Y up. This is a bounding-box estimate, not occupancy or an FPS promise.
    dimensions = mesh.extents / mesh.extents[1] * height_blocks
    cells = np.ceil(dimensions * 16).astype(int)
    hosts = np.ceil(cells / 16).astype(int)
    return {"file": Path(path).name, "sha256": sha256(path),
            "vertices": len(mesh.vertices), "triangles": len(mesh.faces),
            "watertight": bool(mesh.is_watertight),
            "consistent_winding": bool(mesh.is_winding_consistent),
            "bounds": mesh.bounds.tolist(),
            "visual_kinds": sorted({g.visual.kind or "none" for g in scene.geometry.values()}),
            "target_height_blocks": height_blocks,
            "bounding_box_cells": cells.tolist(),
            "bounding_box_host_upper_bound": int(np.prod(hosts)),
            "dense_rgba_and_occupancy_estimate_bytes": int(np.prod(cells)) * 5,
            "review_required": True,
            "notes": "Not a microblock conversion or FPS guarantee. Inspect clay and paint from all sides."}


def runtime_check(upstream):
    upstream = Path(upstream).resolve()
    if platform.system() != "Linux":
        raise RuntimeError("TRELLIS.2's supported runtime requires Linux and >=24 GB NVIDIA VRAM. Preparation and inspection work on Windows.")
    revision = subprocess.check_output(["git", "-C", str(upstream), "rev-parse", "HEAD"], text=True).strip()
    if revision != REVISION:
        raise RuntimeError(f"Expected upstream revision {REVISION}; found {revision}")
    dirty = subprocess.check_output(["git", "-C", str(upstream), "status", "--porcelain", "--untracked-files=no"], text=True)
    if dirty.strip():
        raise RuntimeError("Upstream tracked files are modified; restore the pinned checkout first")
    import torch
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU unavailable")
    # Allow vendor GB/GiB reporting differences for nominal 24 GB cards.
    total = torch.cuda.get_device_properties(0).total_memory / 1024 ** 3
    if total < 23:
        raise RuntimeError(f"GPU has {total:.1f} GiB; official requirement is >=24 GB VRAM")
    return upstream


def generate(args):
    source = Path(args.image).resolve()
    destination = Path(args.output).resolve()
    if not source.is_file():
        raise FileNotFoundError(source)
    if destination.exists():
        raise FileExistsError(destination)
    upstream = runtime_check(args.upstream)
    os.environ.setdefault("OPENCV_IO_ENABLE_OPENEXR", "1")
    os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")
    sys.path.insert(0, str(upstream))
    os.chdir(upstream)
    from PIL import Image
    from trellis2.pipelines import Trellis2ImageTo3DPipeline
    import o_voxel
    pipeline = Trellis2ImageTo3DPipeline.from_pretrained(MODEL)
    pipeline.cuda()
    with Image.open(source) as image:
        mesh = pipeline.run(image.convert("RGBA"), seed=args.seed,
                            pipeline_type=args.resolution)[0]
    mesh.simplify(16777216)
    glb = o_voxel.postprocess.to_glb(
        vertices=mesh.vertices, faces=mesh.faces, attr_volume=mesh.attrs,
        coords=mesh.coords, attr_layout=mesh.layout, voxel_size=mesh.voxel_size,
        aabb=[[-.5, -.5, -.5], [.5, .5, .5]],
        decimation_target=args.triangles, texture_size=args.texture_size,
        remesh=True, remesh_band=1, remesh_project=0, verbose=True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".partial.glb")
    glb.export(str(temporary))
    report = inspect_mesh(temporary, args.height, args.triangles * 2)
    temporary.replace(destination)
    report.update(file=destination.name, source_sha256=sha256(source),
                  upstream_revision=REVISION, model=MODEL, seed=args.seed,
                  resolution=args.resolution, texture_size=args.texture_size,
                  requested_triangles=args.triangles, status="generated-needs-review")
    destination.with_suffix(".json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("prepare")
    p.add_argument("sheet", type=Path)
    p.add_argument("output", type=Path)
    p = sub.add_parser("inspect")
    p.add_argument("mesh", type=Path)
    p.add_argument("--height", type=float, default=12)
    p.add_argument("--max-triangles", type=int, default=1_000_000)
    p = sub.add_parser("generate")
    p.add_argument("image", type=Path)
    p.add_argument("output", type=Path)
    p.add_argument("--upstream", type=Path, required=True)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--resolution", choices=["512", "1024_cascade", "1536_cascade"], default="512")
    p.add_argument("--triangles", type=int, choices=[100000, 250000, 500000], default=250000)
    p.add_argument("--texture-size", type=int, choices=[1024, 2048, 4096], default=2048)
    p.add_argument("--height", type=float, default=12)
    args = parser.parse_args()
    try:
        if args.command == "prepare":
            result = prepare(args.sheet, args.output)
        elif args.command == "inspect":
            result = inspect_mesh(args.mesh, args.height, args.max_triangles)
        else:
            result = generate(args)
        print(json.dumps(result, indent=2))
    except (RuntimeError, ValueError, FileNotFoundError, FileExistsError) as error:
        parser.exit(2, f"{error}\n")


if __name__ == "__main__":
    main()
