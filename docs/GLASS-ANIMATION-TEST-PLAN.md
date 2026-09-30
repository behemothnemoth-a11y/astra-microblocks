# Glass, animation and distance — 0.5.0 test package

Use Astra 0.5.0 with Minecraft 26.2. Existing worlds and sculptures require no migration.
New-material sculptures require 0.5.0; do not open them with an older Astra version.

## Main checks

1. Revisit the Huracan from the video. Back away beyond 64, 96 and 128 blocks with an effective render distance of at least 10 chunks. Its microblock surfaces should remain with the loaded structure. Turn away/back and cross chunk boundaries. Repeat at lower and higher chunk distances. Distant Horizons terrain beyond loaded vanilla chunks is not an Astra renderer.
2. Load the supplied glass/animation gallery into a disposable clear area. Compare transparent samples against the stone center behind each pane, then rotate the view. Clear and colored panes should remain transparent, without losing the solid object behind them. Try combining two different glass colors and cutting a recess through them.
3. Observe magma, prismarine, sea lanterns, sculk and crimson/warped stems long enough to see their texture cycles; some are subtle. Compare against an uncarved vanilla block. Animation is texture animation, not moving sculpture geometry.
4. Use B to find glass, tinted glass, ice, copper grate, resin, coral, froglight and concrete powder. Convert a placed vanilla block, then exercise Cut/Add/Replace, sampling and Original. Undo and redo each edit.
5. Export a mixed transparent/opaque/animated sculpture item, place it, and compare the world, hand and inventory models. Copy/stamp, rotate and mirror it. Save it in a Litematica schematic and paste into a fresh area.
6. Save, fully reload and repeat a small edit/undo/redo. Check that color, transparency, log axis and animation persist.
7. Use the all-state gallery to inspect all 468 states. Its coordinate index identifies every sample. Each sample is carved, so it exercises Astra instead of displaying an ordinary full block.

## Expected limits

The renderer still draws only loaded chunk sections and honors frustum/section visibility. Increasing viewing distance can increase cost because more sculpture surfaces are visible. The update does not add a Distant Horizons microblock representation.

These are material appearances: no flowing fluids, machinery, growth, weathering, explosions, block light emission, inventory contents or redstone behavior are carried into the host. Transparent cells still have collision where occupied. Adjacent full-block host boundaries can show glass seams; transparency sorting follows Minecraft/Fabric's renderer. Vanilla opacity controls surface visibility; resource packs that change opacity categories are not yet supported for topology.

Automated gates cover transparent interfaces, greedy geometry, real atlas animation/layers, distance policy, the full material catalog, mixed palettes, undo/redo, sculpture items, transforms and three world boots. In-game frame rate and modpack-specific visual ordering remain live-test items.

## Verified locally

All three isolated server lifecycle phases passed, including conversion of all 468
material states and preservation of palettes, history, items and transforms. The client
passed real atlas/layer/distance checks. Two fixed-camera screenshots 2.15 seconds
apart showed changed pixels in crimson stem, prismarine, sea lantern and magma
samples, with zero changed pixels in the sampled static grate and glass controls.
This verifies visible texture animation without interpreting sky or camera motion as animation.

Generate the supplied galleries with `python tools/generate_material_test_schematics.py OUTPUT_DIRECTORY`
(requires numpy and nbtlib). Optional developer visual fixture: set the
`astra.materialScreenshot` JVM property on the `clientRenderTest` Loom run; it creates
a disposable world and saves two images before closing.
