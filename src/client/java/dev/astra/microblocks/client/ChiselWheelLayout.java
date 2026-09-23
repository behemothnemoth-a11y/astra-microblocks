package dev.astra.microblocks.client;

import java.util.ArrayList;
import java.util.List;

/** Shared pixel geometry for painting and hit-testing. Gaps never select a brush. */
final class ChiselWheelLayout {
    static final double STEP = Math.PI / 4;
    static final double HALF_GAP = 0.018;
    final int x, y, radius, inner;
    private final List<List<Span>> sectors;

    record Span(int x, int y, int width, boolean edge) {}

    ChiselWheelLayout(int width, int height) {
        radius = Math.min(116, Math.min((height - 64) / 2, (width - 116) / 2));
        if (radius < 64) throw new IllegalArgumentException("Chisel wheel requires at least 320x240 GUI space");
        inner = Math.max(38, radius * 2 / 5);
        x = (width - 100) / 2;
        y = height / 2 + 6;
        var lists = new ArrayList<List<Span>>();
        for (int i = 0; i < 8; i++) lists.add(new ArrayList<>());
        // Cache contiguous runs. Rendering never recalculates angles or allocates the mesh.
        for (int py = y - radius; py < y + radius; py++) {
            int start = x - radius, previous = -1;
            boolean previousEdge = false;
            for (int px = x - radius; px <= x + radius; px++) {
                int sector = sectorAt(px + 0.5, py + 0.5);
                double distance = Math.hypot(px + 0.5 - x, py + 0.5 - y);
                boolean edge = distance > radius - 1.5 || distance < inner + 1.5;
                if (sector != previous || edge != previousEdge || px == x + radius) {
                    if (previous >= 0) lists.get(previous).add(new Span(start, py, px - start, previousEdge));
                    start = px; previous = sector; previousEdge = edge;
                }
            }
        }
        sectors = lists.stream().map(List::copyOf).toList();
    }

    int sectorAt(double px, double py) {
        double dx = px - x, dy = py - y, distance = Math.hypot(dx, dy);
        if (distance < inner || distance >= radius) return -1;
        double angle = Math.atan2(dy, dx) + Math.PI / 2;
        if (angle < 0) angle += Math.PI * 2;
        double shifted = (angle + STEP / 2) % (Math.PI * 2);
        double offset = shifted % STEP;
        if (offset < HALF_GAP || offset > STEP - HALF_GAP) return -1;
        return (int) (shifted / STEP);
    }

    List<Span> spans(int sector) { return sectors.get(sector); }
    int labelX(int sector) { return x + (int) Math.round(Math.sin(sector * STEP) * labelRadius()); }
    int labelY(int sector) { return y - (int) Math.round(Math.cos(sector * STEP) * labelRadius()); }
    private double labelRadius() { return (inner + radius) * 0.5; }
    int sideX() { return x + radius + 12; }
}
