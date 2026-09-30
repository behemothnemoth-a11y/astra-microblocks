# Native RGB colors — 0.6.0

## Use

Hold the Astra Chisel, press B and choose Colors. Move Red, Green and Blue sliders or enter #RRGGBB. The swatch updates locally; Apply selects the material for Add/Replace. Get block gives a full colored sculpture item in Creative. Existing P sampling and Recent retain exact colors. RGB material IDs have the form `astra_microblocks:rgb_ff0080`; `/astra material astra_microblocks:rgb_ff0080` selects one, and `/astra colorblock ff0080` gives a full block in Creative while holding the chisel.

All 16,777,216 opaque 24-bit RGB colors are available. They use a flat white texture tinted per face, so normal world lighting still shades faces. This release does not add arbitrary-alpha glass or emissive RGB light. Vanilla glass and animated materials from 0.5.0 remain available and can be mixed with RGB cells.

Colors are saved in the existing local volume palette format. Legacy materials remain readable. New RGB-containing worlds, schematics and items require 0.6.0; older versions do not understand RGB IDs.

## Performance design and limits

- One shared texture and no per-color block registrations or per-color texture atlas uploads.
- A bounded 8,192-entry color-object reuse cache. This fits a full 4,096-color palette plus a distinct Original color without the 4,096-entry boundary causing repeated eviction. Evicted colors remain valid values in existing worlds and histories.
- No network traffic, block updates or 3D mesh creation from slider movement; only Apply/Get block send commands.
- Palette validation uses a HashSet, avoiding quadratic duplicate scans for thousands of colors.
- Unchanged world meshes and item meshes keep using existing caches. Equal RGB values merge even when represented by different objects after eviction.
- A uniform full colored host remains six quads. A full host with 4,096 distinct cell colors uses 1,536 exterior quads. A checkerboard of 2,048 isolated cells has 12,288 quads, because every disconnected cell is exposed.
- The largest local palette's cell indices occupy 1,024 longs / 8,192 bytes, plus palette descriptors and normal NBT overhead. This is not the full in-memory host cost.

No implementation can guarantee zero lag for unlimited visible geometry, histories, shaders and other mods. Different colors can prevent face merging; disconnected geometry is especially expensive. The gates preserve geometry/caching bounds and report benchmark timings. Timings are diagnostic, not portable FPS guarantees. A renderer cache does not remove the GPU cost of drawing visible surfaces.

## Test sequence

1. Open B → Colors. Test black, white, #FF0080 and a custom hex color. Invalid/incomplete hex must not apply. Confirm slider movement alone leaves the selected tool material/world unchanged.
2. Apply a color and use Add/Replace with single, plane and cube brushes. P-sample it and retrieve it from Recent. Get block in Creative, place it, pick it, export it and compare the inventory appearance.
3. Mix RGB cells with stone, glass and an animated material. Undo/redo, copy/stamp, rotate and mirror. Save/reload; colors and history must remain exact.
4. Save as a Litematica schematic, rotate/mirror and paste it. Compare RGB, glass, animation and orientation.
5. Load the RGB studio first, then the 64-host dense color stress fixture. The disconnected 64-host fixture is optional and deliberately more expensive: approximately 786,432 visible cell faces before any renderer-specific culling. Load one stress fixture at a time and compare with ordinary geometry at the same view distance.
6. Revisit the Huracan distance test to verify the prior distance fix remains intact.

## Automated regression gates

Each CI run retains all existing lifecycle, shape, mesh, hit, brush, palette, item and renderer tests. RGB gates additionally check malformed IDs, canonical hex, cache bounds/eviction, no-op replacements, equal-color merging, the 4,096-color codec, mixed saved histories, rotation cycles, exact vertex tints, loaded flat texture, UI bounds and command-on-Apply behavior. The real local Litematica integration fixture includes RGB alongside glass and animated materials.

Related mod references: [Flat Colored Blocks](https://www.curseforge.com/minecraft/mc-mods/flat-colored-blocks-forge), [Colorful Blocks](https://modrinth.com/mod/colorful-blocks). Astra implements native RGB material data; neither mod is required and no source or assets were copied from them.
