# Astra Microblocks 0.7.0 — chunk-baked world rendering

0.7.0 promotes the Grand Hypercars performance prototype to the stable baseline.

## Why this release exists

The full Huracan contains 13,436 Astra hosts and the full Chiron contains 12,805.
In 0.6.x, each visible host used a block-entity renderer every frame even though
its greedy mesh was cached. That created roughly thirteen thousand independent
render extractions/submissions for one car and caused severe in-game lag.

## Rendering change

World geometry now uses Fabric's chunk model path.

- TestHostBlockEntity implements RenderDataBlockEntity.
- Each host exposes a cached immutable render snapshot that changes only when host content changes.
- Host block states use RenderShape.MODEL.
- AstraChunkBlockModel emits the existing greedy microblock geometry during chunk compilation.
- Normal gameplay no longer registers the per-frame TestHostBlockEntityRenderer.
- The legacy block-entity renderer remains available only to the synthetic client render gate.
- Material atlas lookups are shared across all host block states for one model reload.
- Item/sculpture rendering, collision, selection, editing, persistence, lighting, materials and save/NBT format are unchanged.

This release deliberately does not add cross-host face culling or distance LOD because
the real-world car stress case no longer shows perceptible lag after the chunk-render change.

## Automated verification

- clean Gradle build: pass
- client renderer smoke suite: pass
- all 128 orientation/light host states use the chunk renderer: pass
- direct chunk-model full/carved mesh emission: pass
- direct chunk-model RGB tint emission: pass
- direct chunk-model gray-glass translucent-layer emission: pass
- immutable render snapshot invalidation: pass
- three-boot world lifecycle/persistence test: pass
- carved-light persistence repair: pass
- no unloaded-position tick warnings: pass
- no server watchdog errors: pass

## In-game validation

The existing saved Grand Hypercars world was used without repasting or regenerating the cars.
The Huracan/Chiron test showed no perceptible lag after switching to the chunk-baked renderer.
RGB paint, glass, recessed lighting, save/reload and host editing/undo were also checked in-game
and behaved correctly.

## Future performance work

Cross-host hidden-face culling, distance LOD and additional batching remain possible future work,
but should only be implemented when a new real-world build demonstrates a measurable need.
