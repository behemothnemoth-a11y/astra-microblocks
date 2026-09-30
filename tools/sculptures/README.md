# Painted reference → editable 3D sculpture

This is an **offline authoring tool**, independent of the Minecraft mod. It prepares
single-view inputs for Microsoft's TRELLIS.2, exports textured GLB models, and
reports mesh integrity and a conservative microblock size budget. No AI model runs
inside Minecraft. No schematic is installed automatically.

## Choice and current validation

[TRELLIS.2](https://github.com/microsoft/TRELLIS.2) was selected for textured geometry,
PBR materials, MIT-licensed model/code, and substantial community adoption (about
11,400 GitHub stars when evaluated on 2026-09-30). Stars are not a quality score;
the sculpture references still require a visual comparison after inference.

The official runtime requires Linux and at least 24 GB NVIDIA VRAM. The development
laptop has an 8 GB RTX 5050 and no WSL installation, so **GPU inference has not been
executed or verified here**. CPU preparation, scene inspection, and failure guards
are tested. The runner follows the pinned upstream API but still needs an end-to-end
run on suitable hardware. The previous silhouette meshes are not TRELLIS outputs.

Compared alternatives: [Hunyuan3D 2.1](https://github.com/Tencent-Hunyuan/Hunyuan3D-2.1)
offers shape and texture generation with separate community license terms;
[Stable Fast 3D](https://github.com/Stability-AI/stable-fast-3d) documents approximately
6 GB VRAM, experimental Windows support, and gated model access. Neither is installed
or silently used as a lower-quality fallback.

## Prepare and inspect on Windows or Linux

### Free hosted evaluation, 2026-09-30

The [official Microsoft demo](https://huggingface.co/spaces/microsoft/TRELLIS.2)
successfully generated a rendered preview from `01_Cthulhu_Votive/front.png`
at resolution 512, seed 42, randomization disabled. The preview showed improved
limb and wing volume compared with our silhouette reconstruction. This is visual
evidence only; no model geometry could be inspected locally from this run.

GLB extraction failed with a generic `Error` at both 300,000 faces / 2048 texture
and 100,000 faces / 1024 texture. No cause was supplied by the page. No GLB was
downloaded, and no paid service was used. See `free-demo-preview.png` for the
rendered evidence; this image is not an editable 3D asset. The collection remains
awaiting a successful export and artistic review before microblock conversion.

The embedded upload chooser failed in the browser integration; opening the
demo's own `https://microsoft-trellis-2.hf.space/` page allowed the normal upload
control to work. This was a UI workaround, not a quota or authentication bypass.

### Local commands

Python 3.11+:

```sh
python -m pip install -r tools/sculptures/requirements.txt
python tools/sculptures/sculpture_pipeline.py prepare turnaround.png build/sculptures/my-statue
python tools/sculptures/sculpture_pipeline.py inspect statue.glb --height 12
python -m unittest discover -s tools/sculptures -p 'test_*.py'
```

`prepare` expects three equal-width FRONT / SIDE / BACK panels on magenta, as used
by our painted references. It crops each view, removes the key background, preserves
transparent margins, and records source/output hashes. Review masks before generation:
magenta-colored sculpture details cannot be distinguished from this key background.
Use a new output directory for each revision. **Feed one view to the model, not the
three-view sheet**. Side and back images are independent review references; this is
not a multi-view reconstruction adapter.

The committed `references/` collection contains all twelve prepared subjects and
their generation prompts. No home-directory paths or model weights are committed.

## Generate on a suitable GPU machine

Install upstream in a separate environment, following its official instructions.
This operation downloads large dependencies and weights; it does not require an
OpenAI API key. A cloud machine and its cost must be arranged separately.

```sh
git clone --recursive https://github.com/microsoft/TRELLIS.2.git
cd TRELLIS.2
git checkout 75fbf0183001ed9876c8dbb35de6b68552ee08bd
git submodule update --init --recursive
. ./setup.sh --new-env --basic --flash-attn --nvdiffrast --nvdiffrec --cumesh --o-voxel --flexgemm
conda activate trellis2
```

Then from the Astra checkout, in that environment (ensure Pillow, NumPy and trimesh
are installed; retain the versions required by upstream for GPU compatibility):

```sh
python tools/sculptures/sculpture_pipeline.py generate \
  tools/sculptures/references/01_Cthulhu_Votive/front.png \
  build/sculptures/cthulhu-votive.glb \
  --upstream /absolute/path/to/TRELLIS.2 --seed 42 \
  --resolution 512 --triangles 250000 --texture-size 2048 --height 12
```

Start with one 512-resolution model. Evaluate paint AND clay from front, side,
back, and oblique angles before using `1024_cascade` or `1536_cascade`. Higher
resolution consumes more memory and does not guarantee better anatomy.
Each successful export gets a JSON report with input hash, seed, upstream revision,
generation settings and output hash. We pin integration code, not every transitive
dependency or Hugging Face model revision; bitwise reproducibility is not promised.

Open GLBs in a PBR-capable 3D editor/viewer. The earlier vertex-color-only study
viewer does not display these texture maps. Blender is suitable for sculpt refinement
and material inspection. Review separated tentacles, eyelids, mouths, undercuts,
wing thickness, symmetry, base attachment, and painted-versus-sculpted detail.

## Microblock handoff

GLB remains the source asset. Once its shape passes review, derive the display,
gallery and monument variants from that same model. This tool does not yet voxelize
textured GLBs; it does not label generated meshes as ready for Minecraft.

The inspection report assumes GLB's Y-up coordinates. It includes transformed scene
instances, nonzero bounds, triangle limits, winding, watertightness, and visual type.
Open surfaces are reported for review rather than silently filled. The host count is
a bounding-box upper bound, not occupied hosts, render cost, or an FPS guarantee.
The dense memory estimate covers only RGBA + occupancy, not conversion overhead.
Actual conversions still need bounded occupancy, palette, face and memory checks,
followed by representative in-game testing. Large textures and AI weights are not
packaged in the mod jar.

## Licensing

TRELLIS.2 is an external optional dependency. Its MIT notice is retained in
`TRELLIS-NOTICE.txt` because the runner follows its export example. Upstream rendering
dependencies have their own licenses; consult upstream before redistributing them.
Generated references in this directory were created for this project using image
generation. They are design inputs, not scans or recovered historical artifacts.
