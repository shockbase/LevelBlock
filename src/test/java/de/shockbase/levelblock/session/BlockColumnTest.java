package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockColumnTest {

    @Test
    void roundTripsSerializedCoordinates() {
        BlockColumn column = new BlockColumn(-42, 17);

        assertEquals(column, BlockColumn.parse(column.serialize()));
    }

    @Test
    void acceptsWhitespaceAroundCoordinates() {
        assertEquals(new BlockColumn(-4, 8), BlockColumn.parse(" -4, 8 "));
    }

    @Test
    void rejectsMalformedCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> BlockColumn.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> BlockColumn.parse("1,2,3"));
        assertThrows(IllegalArgumentException.class, () -> BlockColumn.parse("one,2"));
        assertThrows(IllegalArgumentException.class, () -> BlockColumn.parse("1,"));
    }
}
