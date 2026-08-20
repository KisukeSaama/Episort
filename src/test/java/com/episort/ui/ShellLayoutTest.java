package com.episort.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.episort.EpisortApplication;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The shell's layout decisions, away from a JavaFX toolkit. */
class ShellLayoutTest {

    @Test
    void widthsMapToTheThreeStates() {
        assertEquals(ShellLayout.WIDE, ShellLayout.forWidth(1920));
        assertEquals(ShellLayout.WIDE, ShellLayout.forWidth(1200));
        assertEquals(ShellLayout.MEDIUM, ShellLayout.forWidth(1199));
        assertEquals(ShellLayout.MEDIUM, ShellLayout.forWidth(1000));
        assertEquals(ShellLayout.COMPACT, ShellLayout.forWidth(999));
        assertEquals(ShellLayout.COMPACT, ShellLayout.forWidth(0));
    }

    @Test
    void theDetailPanelMovesBeforeTheNavigationLosesItsLabels() {
        assertFalse(ShellLayout.WIDE.stacksDetailPanel());
        assertTrue(ShellLayout.MEDIUM.stacksDetailPanel());
        // The whole point of the ordering: MEDIUM has already given up the side
        // by side layout and still shows every label.
        assertFalse(ShellLayout.MEDIUM.collapsesSidebar());
        assertTrue(ShellLayout.COMPACT.collapsesSidebar());
    }

    @Test
    void onlyTheCompactStateFoldsTheMirrorActions() {
        assertFalse(ShellLayout.WIDE.foldsMirrorActionsIntoMenu());
        assertFalse(ShellLayout.MEDIUM.foldsMirrorActionsIntoMenu());
        assertTrue(ShellLayout.COMPACT.foldsMirrorActionsIntoMenu());
    }

    /**
     * The regression this whole type exists for: the window's floor used to sit
     * at 1180 against a single threshold at 1200, so everything below the
     * threshold lived inside a twenty-pixel band. A floor above the compact
     * breakpoint would put it back there.
     */
    @Test
    void everyLayoutIsReachableWithinTheWindowsOwnLimits() {
        assertTrue(EpisortApplication.MIN_WIDTH < ShellLayout.COMPACT_BREAKPOINT,
                "the compact layout must be reachable, not just declared");
        Set<ShellLayout> reachable = new HashSet<>();
        for (double width = EpisortApplication.MIN_WIDTH; width <= 2560; width += 10) {
            reachable.add(ShellLayout.forWidth(width));
        }
        assertEquals(Set.of(ShellLayout.values()).size(), reachable.size(),
                "layouts a window can never reach: they are documentation, not behaviour");
    }

    @Test
    void styleClassesAreDistinctAndFullyEnumerated() {
        assertEquals(ShellLayout.values().length,
                Set.copyOf(ShellLayout.allStyleClasses()).size());
        for (ShellLayout layout : ShellLayout.values()) {
            assertTrue(ShellLayout.allStyleClasses().contains(layout.styleClass()),
                    layout + " is missing from allStyleClasses(), so the shell would "
                            + "stack its class on top of the previous one");
        }
    }
}
