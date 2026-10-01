package dev.astra.microblocks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Defers derived light-state repairs until the end of a server level tick.
 *
 * Block entities are attached while chunks are still deserializing, where world
 * lookups can block or log unloaded-position errors. Queueing only the position
 * keeps load/import hooks side-effect free until the level is safe to query.
 */
final class MicroblockLightRepairQueue {
    private static final ConcurrentHashMap<ServerLevel, Set<BlockPos>> PENDING =
            new ConcurrentHashMap<>();

    private MicroblockLightRepairQueue() {}

    static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(MicroblockLightRepairQueue::flush);
    }

    static void request(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        PENDING.computeIfAbsent(server, ignored -> ConcurrentHashMap.newKeySet())
                .add(pos.immutable());
    }

    static void flush(ServerLevel level) {
        Set<BlockPos> positions = PENDING.remove(level);
        if (positions == null) return;

        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof TestHostBlockEntity host) {
                host.refreshLight();
            }
        }
    }
}
