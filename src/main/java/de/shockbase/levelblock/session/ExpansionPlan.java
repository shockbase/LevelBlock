package de.shockbase.levelblock.session;

import java.util.List;

/** Result of evaluating all columns crossed by one player movement. */
public sealed interface ExpansionPlan permits ExpansionPlan.Approved, ExpansionPlan.Rejected {

    record Approved(List<BlockColumn> columnsToUnlock) implements ExpansionPlan {

        public Approved {
            columnsToUnlock = List.copyOf(columnsToUnlock);
        }

        public int cost() {
            return columnsToUnlock.size();
        }
    }

    record Rejected(BlockColumn disconnectedColumn) implements ExpansionPlan {
    }
}
