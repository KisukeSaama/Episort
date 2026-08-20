package com.episort.ui;

/**
 * How much room the shell has, and what it gives up at each step.
 *
 * <p>The shell used to hold a single threshold at 1200 while the window
 * refused to go below 1180: the narrow layout existed inside a twenty-pixel
 * band and was, in practice, never seen. What follows is the same idea with
 * room to work in — three states, each giving up the next least useful thing.
 *
 * <p>Order matters. The detail panel moves under the table before the
 * navigation loses its labels, because a panel that has moved still says
 * everything it said, and a rail of bare glyphs does not.
 */
public enum ShellLayout {
    /** Everything at once: navigation labels, table and detail panel side by side. */
    WIDE,
    /** The detail panel moves below the table so both keep a usable measure. */
    MEDIUM,
    /** The navigation collapses to its glyphs and the chrome tightens. */
    COMPACT;

    /** Below this the detail panel can no longer sit beside the table. */
    static final double MEDIUM_BREAKPOINT = 1200;

    /**
     * Below this a 230 px sidebar is more than a fifth of the window, and the
     * top bar can no longer hold every action at full width.
     */
    static final double COMPACT_BREAKPOINT = 1000;

    public static ShellLayout forWidth(double width) {
        if (width >= MEDIUM_BREAKPOINT) {
            return WIDE;
        }
        return width >= COMPACT_BREAKPOINT ? MEDIUM : COMPACT;
    }

    /** The detail panel sits below the table instead of beside it. */
    public boolean stacksDetailPanel() {
        return this != WIDE;
    }

    /** The sidebar keeps its glyphs and drops its labels. */
    public boolean collapsesSidebar() {
        return this == COMPACT;
    }

    /**
     * The top bar drops the two mirror actions. They are the only controls in
     * the bar that exist twice: {@code Réanalyser} and {@code Réinitialiser}
     * are the Analyse menu's own entries, sharing its enabled state, so
     * dropping them costs reach rather than availability. Nothing that exists
     * only in the bar is ever removed from it.
     */
    public boolean foldsMirrorActionsIntoMenu() {
        return this == COMPACT;
    }

    /** The style class the shell root carries, so CSS can tighten with it. */
    public String styleClass() {
        return "layout-" + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Every class this enum can put on the root, for removal before the swap. */
    public static java.util.List<String> allStyleClasses() {
        return java.util.List.of(WIDE.styleClass(), MEDIUM.styleClass(), COMPACT.styleClass());
    }
}
