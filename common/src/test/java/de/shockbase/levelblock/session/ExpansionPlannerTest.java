package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ExpansionPlannerTest {

    @Test
    void ignoresUnlockedColumnsAndAllowsConnectedChain() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));
        ExpansionPlan.Approved plan = assertInstanceOf(
                ExpansionPlan.Approved.class,
                ExpansionPlanner.plan(progress, List.of(
                        new BlockColumn(0, 0),
                        new BlockColumn(1, 0),
                        new BlockColumn(2, 0),
                        new BlockColumn(2, 0)
                ))
        );
        assertEquals(List.of(new BlockColumn(1, 0), new BlockColumn(2, 0)), plan.columnsToUnlock());
    }

    @Test
    void rejectsDisconnectedColumn() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));
        ExpansionPlan.Rejected plan = assertInstanceOf(
                ExpansionPlan.Rejected.class,
                ExpansionPlanner.plan(progress, List.of(new BlockColumn(4, 4)))
        );
        assertEquals(new BlockColumn(4, 4), plan.disconnectedColumn());
    }

    private static WorldProgress progressWith(BlockColumn... columns) {
        return new WorldProgress("minecraft:overworld", 0, 0, List.of(columns));
    }
}
