package net.ledok.mobs_ld.mob.combat;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Where summon runes may go: a solid floor, open blocks above it, no fluid, apart from the other runes. */
class RuneSpotsTest {
    /** One column of blocks (x = z = 0) built from the ground up; everything else is air. */
    private static final class Column implements RuneSpots.Terrain {
        private final Map<Integer, Character> blocks = new HashMap<>();

        /** 's' solid, 'h' half slab, 'w' water, 'l' lava; y is the block's height. */
        Column set(int y, char block) {
            blocks.put(y, block);
            return this;
        }

        Column fill(int from, int to, char block) {
            for (int y = from; y <= to; y++) {
                set(y, block);
            }
            return this;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            char block = blocks.getOrDefault(y, 'a');
            return block == 's' || block == 'h';
        }

        @Override
        public double top(int x, int y, int z) {
            return blocks.getOrDefault(y, 'a') == 'h' ? 0.5 : 1.0;
        }

        @Override
        public boolean fluid(int x, int y, int z) {
            char block = blocks.getOrDefault(y, 'a');
            return block == 'w' || block == 'l';
        }
    }

    /** Scans as the summon does: from one block above the centre's feet (64), at most 6 down, 3 blocks of clearance. */
    private static double floor(Column column) {
        return RuneSpots.floor(column, 0, 0, 65, 6, 3);
    }

    @Test
    void flatGroundAndSmallSteps() {
        assertEquals(64.0, floor(new Column().fill(0, 63, 's')));
        assertEquals(65.0, floor(new Column().fill(0, 64, 's')), "one block up is fine");
        assertEquals(64.5, floor(new Column().fill(0, 63, 's').set(64, 'h')), "a slab");
        assertEquals(60.0, floor(new Column().fill(0, 59, 's')), "a few blocks down");
    }

    @Test
    void invalidSpots() {
        assertTrue(Double.isNaN(floor(new Column())), "the void");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 50, 's'))), "a drop deeper than the scan");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 62, 's').set(63, 'w'))), "water");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 62, 's').set(63, 'l'))), "lava");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 66, 's'))), "inside a hill");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 63, 's').set(66, 's'))), "a 2-block gap is too low");
        assertTrue(Double.isNaN(floor(new Column().fill(0, 63, 's').set(65, 'w'))), "fluid above the floor");
    }

    @Test
    void aTunnelRoofIsNotTheFloor() {
        // The player stands in a tunnel 3 high: floor at 63, roof at 67. The scan starts inside the tunnel.
        Column tunnel = new Column().fill(0, 63, 's').fill(67, 80, 's');
        assertEquals(64.0, floor(tunnel));
        // A tunnel only 2 high has no room for a 2.4-block add.
        assertTrue(Double.isNaN(floor(new Column().fill(0, 63, 's').fill(66, 80, 's'))));
    }

    @Test
    void openAirAndGaps() {
        assertTrue(RuneSpots.open(new Column(), 0, 64, 0, 3));
        assertFalse(RuneSpots.open(new Column().set(66, 's'), 0, 64, 0, 3));
        List<Vec3> taken = List.of(new Vec3(0.5, 64.0, 0.5));
        assertFalse(RuneSpots.apart(taken, new Vec3(1.5, 64.0, 1.5), 2.0));
        assertTrue(RuneSpots.apart(taken, new Vec3(2.5, 64.0, 0.5), 2.0));
        assertTrue(RuneSpots.apart(List.of(), new Vec3(0.5, 64.0, 0.5), 2.0));
    }
}
