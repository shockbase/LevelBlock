package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockColumnTest {

    @Test
    void roundTripsPackedCoordinates() {
        BlockColumn column = new BlockColumn(-42, 17);
        assertEquals(column, BlockColumn.fromPacked(column.packed()));
        assertEquals(column.packed(), BlockColumn.pack(-42, 17));
    }
}
