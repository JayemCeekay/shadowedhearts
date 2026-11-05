package com.jayemceekay.shadowedhearts.common.shadow;

import java.util.function.Supplier;

/** Scoped item attribution for native friendship items and cooperating integrations. */
public final class ShadowFriendshipSources {
    private static final ThreadLocal<Integer> ITEM_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> DIRECT = ThreadLocal.withInitial(() -> false);
    private ShadowFriendshipSources() {}
    public static boolean isItem() { return ITEM_DEPTH.get() > 0; }
    static boolean isDirect() { return DIRECT.get(); }
    public static <T> T withItem(Supplier<T> action) {
        int previous = ITEM_DEPTH.get();
        ITEM_DEPTH.set(previous + 1);
        try { return action.get(); }
        finally {
            if (previous == 0) ITEM_DEPTH.remove();
            else ITEM_DEPTH.set(previous);
        }
    }

    static <T> T withDirectAssignment(Supplier<T> action) {
        boolean previous = DIRECT.get();
        DIRECT.set(true);
        try { return action.get(); }
        finally { if (previous) DIRECT.set(true); else DIRECT.remove(); }
    }
}
