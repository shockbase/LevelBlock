package de.shockbase.levelblock.session;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ExpansionPlannerTest {

    @Test
    void ignoresAlreadyUnlockedAndDuplicateColumns() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));

        ExpansionPlan.Approved plan = assertInstanceOf(
                ExpansionPlan.Approved.class,
                ExpansionPlanner.plan(
                        progress,
                        List.of(
                                new BlockColumn(0, 0),
                                new BlockColumn(1, 0),
                                new BlockColumn(1, 0)
                        )
                )
        );

        assertEquals(List.of(new BlockColumn(1, 0)), plan.columnsToUnlock());
        assertEquals(1, plan.cost());
    }

    @Test
    void allowsAConnectedChainWithinOneMovement() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));
        List<BlockColumn> crossed = List.of(
                new BlockColumn(1, 0),
                new BlockColumn(2, 0),
                new BlockColumn(3, 1)
        );

        ExpansionPlan.Approved plan = assertInstanceOf(
                ExpansionPlan.Approved.class,
                ExpansionPlanner.plan(progress, crossed)
        );

        assertEquals(crossed, plan.columnsToUnlock());
        assertEquals(3, plan.cost());
    }

    @Test
    void rejectsTheFirstDisconnectedColumn() {
        WorldProgress progress = progressWith(new BlockColumn(0, 0));

        ExpansionPlan.Rejected plan = assertInstanceOf(
                ExpansionPlan.Rejected.class,
                ExpansionPlanner.plan(progress, List.of(new BlockColumn(4, 4)))
        );

        assertEquals(new BlockColumn(4, 4), plan.disconnectedColumn());
    }

    private static WorldProgress progressWith(BlockColumn... columns) {
        return new WorldProgress(UUID.randomUUID(), "world", 0, 0, List.of(columns));
    }
}
