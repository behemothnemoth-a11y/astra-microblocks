# Schematic transformations — 0.4.0

## Contract

Astra host block states now carry an eight-value horizontal orientation. Vanilla
BlockState rotate/mirror hooks compose that orientation. When NBT is loaded, Astra
compares the saved `astra_orientation` with the destination block state and applies
only their difference to current cells, original material axes, and all undo/redo
snapshots. Saving/networking records the destination orientation, preventing a second
rotation on reload. Legacy schematics without the field start at identity.

This is a vanilla placement contract, with no runtime dependency on Litematica and no
patches to its code. Tools that transform block states and then load NBT can use it.
It is not a claim that arbitrary third-party editors or state-only world changes work.
Horizontal placement transformations are supported; X/Z design rotations remain the
existing clipboard/item operations. Old files remain readable. Downgrading newly
transformed or expanded-material worlds to an older Astra version is not supported.

A transient content version invalidates renderer/inspector caches after every NBT
load, including replacement by different cells with the same saved edit revision.
The edit revision still retains its existing interaction/history meaning. Cached
physical shapes continue to compare occupancy and preserve the 0.3.2 performance fix.

## Automated verification

- 96 combinations of previously oriented hosts and new vanilla mirror/rotation hooks,
  checked with an independent Minecraft StructureTemplate coordinate oracle.
- Exact cells, materials, directional axes, undo/redo, network update, repeated load,
  legacy NBT and three real server boots.
- All 325 material states: conversion, sampling, history, palette/item codecs, client
  atlas textures and mesh UVs. Existing lifecycle and Foundry performance gates remain.
- Optional actual Litematica 0.28.8 + MaLiLib 0.29.6 integration: all 144 whole-placement
  and subregion mirror/rotation combinations. Loads data in WorldSchematic, pastes to
  an integrated server, reloads NBT and overwrites edited hosts. This verifies preview
  world data; visual GPU presentation and user-facing controls still need in-game testing.

Run the optional integration gate from the repository (Java 25 / Gradle 9.5.1):

```text
gradle -I tools/litematica-integration.init.gradle -PastraLitematicaJar=/absolute/path/litematica.jar -PastraMalilibJar=/absolute/path/malilib.jar runClientRenderTest --console=plain
```

Require `ASTRA_TEST: LITEMATICA_INTEGRATION_PASS`, `LITEMATICA_TRANSFORMS=144`, and
`CLIENT_RENDER_PASS`, with no `ASTRA_TEST` failure marker. The disposable client uses
`run-litematica-integration`; it does not load the user's Minecraft saves or settings.
The optional jars are supplied locally, not redistributed or required by CI.

## Large in-game testing package

The companion **Astra Compass Copper Workshop 0.4.0** schematic has a 20 x 18 footprint,
7-block height, 736 sculpture blocks and 965,784 occupied microcells. It contains a
furnished courtyard house, copper roof, asymmetric gold roof mark and front arrow,
curved screen, staircase, directional bone posts and a notched sample of every one of
the 75 newly added material states. Corner colors make orientation easy to compare.

1. Load the existing house or Foundry. Check 0/90/180/270 degrees and both mirror axes
   in the hologram. Doorways, curves and stairs should remain joined across host borders.
2. Paste the Copper Workshop unrotated, then paste a 90-degree and a mirrored copy in
   separate clear areas. Compare the front arrow, roof mark, screen and staircase.
   Four quarter turns and two identical mirrors should return to the original layout.
3. Save a rotated copy as a NEW schematic, unload it, reload it, rotate again and paste.
   This tests transforms of already transformed data. Also try a subregion transform.
4. In B, search copper, ore, bone, hay and sand. Cut several vanilla examples; use P to
   sample each, then Add/Replace to mix them inside one host. Try both horizontal bone
   axes. Inspect end grain and side textures before and after design rotations.
5. Edit a pasted sample using line, plane and large brushes. Undo/redo several times;
   save a sculpture item, place it, copy/stamp and check its shape/materials.
6. Save and quit, reopen, then compare all pasted copies and continue undo/redo on edited
   hosts. Shapes, materials and directional textures must survive without a second turn.
7. Fly around the Workshop and previous Foundry, look at dense sections, walk against
   carved edges and keep editing. Report any freeze, persistent stutter, visible seam,
   wrong material or change after reload, including which transform was active.

Use existing undo/redo controls. Litematica preview is separate from the unrelated blue
Easy Place overlay. Material samples are visual sculpting media, not vanilla machines,
falling blocks, ore loot or automatic copper weathering.
