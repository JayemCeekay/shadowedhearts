package com.jayemceekay.shadowedhearts.client.purification;

import java.util.function.BiPredicate;

/** Tracks only client screen ownership; the server remains authoritative for chamber access. */
public final class PurificationScreenSession {
    private Object connection;
    private Object chamber;
    private Object selection;

    /** Install before replacing a screen so an older screen cannot close the new session. */
    public void open(Object connection, Object chamber) {
        this.connection = connection;
        this.chamber = chamber;
        this.selection = null;
    }

    public void select(Object chamber, Object selection) {
        if (this.chamber == chamber) this.selection = selection;
    }

    public void returnTo(Object chamber) {
        if (this.chamber == chamber) selection = null;
    }

    public boolean ownsScreen(Object connection, Object screen) {
        return connection != null && this.connection == connection && chamber != null
                && (screen == chamber || (selection != null && screen == selection));
    }

    /** True means the current connection needs one unlink after leaving all owned screens. */
    public boolean tick(Object connection, Object screen) {
        return tick(connection, screen, (viewer, origin) -> false);
    }

    /**
     * Reconcile after screen transitions: removed() runs before the destination is available,
     * and viewer APIs only expose their return target once the viewer is actually open.
     */
    public boolean tick(Object connection, Object screen, BiPredicate<Object, Object> viewerReturnsTo) {
        if (chamber == null) return false;
        if (this.connection != connection || connection == null) {
            clear();
            return false;
        }
        if (screen == chamber || (selection != null && screen == selection)) return false;
        if (screen != null && (viewerReturnsTo.test(screen, chamber)
                || (selection != null && viewerReturnsTo.test(screen, selection)))) return false;
        clear();
        return true;
    }

    private void clear() {
        connection = null;
        chamber = null;
        selection = null;
    }
}
