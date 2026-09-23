# Persistent history and material workflow (0.1.10)

World saves retain the legacy latest-undo fields and add history_v3 as a packed long
array using Codec.LONG_STREAM. The first two longs are undo and redo counts, followed
by each snapshot's 64 occupancy words and 64 oak words. Undo snapshots are oldest first,
including the latest undo last; redo snapshots are oldest first with next redo last.
The sum of counts is limited to 32. Length and nonnegative count bounds are validated
before any stack is replaced. Invalid/missing history falls back to the legacy latest
undo without discarding the current sculpture. Legacy boolean/occupancy/material loads
retain their previous migration rules. The maximum packed history payload is 32 KiB
plus a 16-byte header before NBT compression; empty history is only the header.

Client updates contain current occupancy/materials, revision and history counts only.
Full history remains server-side and is not included in update tags. UI history counts
still use server-provided values. Every edit/undo/redo increments revision and dirties
the block entity, so the cursor and stacks save with the current state.

Replace changes occupied cells whose material differs from the selected material.
It preserves occupancy and uses the same atomic history, obstruction and revision path.
No-op replacements preserve redo. The material-aware preview filters the same selection
by occupancy and material; changes to material selection invalidate the preview cache.
Renderer cache invalidation already uses revision, including shape-preserving edits.

Sampling uses a server-side outline ray at current position/orientation and interaction
range. It reads the exact inward-biased microcell and stores an explicit Stone/Oak tool
setting, preserving other CustomData. Air, ordinary blockers and out-of-reach targets
fail. Sampling creates no history and does not retarget last-edit history.

Z/Y/P are standard rebindable key mappings. Pending clicks are consumed even when a
screen is open or no chisel is held, but commands dispatch only in a world with a held
main-hand chisel, no screen and a live connection. The existing server commands remain
authoritative. Other mods can use the same bindings; users can rebind in Controls.
