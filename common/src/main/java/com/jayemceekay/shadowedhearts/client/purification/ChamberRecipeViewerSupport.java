package com.jayemceekay.shadowedhearts.client.purification;

import com.jayemceekay.shadowedhearts.Shadowedhearts;
import net.minecraft.client.gui.screens.Screen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/** Optional viewers register their exact return-screen contract; no viewer classes load here. */
public final class ChamberRecipeViewerSupport {
    private static final Map<String, BiPredicate<Screen, Screen>> VIEWERS = new LinkedHashMap<>();

    private ChamberRecipeViewerSupport() {}

    public static void register(String id, BiPredicate<Screen, Screen> returnsTo) {
        VIEWERS.put(id, returnsTo); // Viewer reloads replace their callback instead of retaining old runtimes.
    }

    public static boolean returnsTo(Object viewer, Object origin) {
        if (!(viewer instanceof Screen screen) || !(origin instanceof Screen parent)) return false;
        var entries = VIEWERS.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            try {
                if (entry.getValue().test(screen, parent)) return true;
            } catch (RuntimeException | LinkageError error) {
                Shadowedhearts.LOGGER.warn("Disabling {} Chamber return support after an incompatible viewer callback",
                        entry.getKey(), error);
                entries.remove(); // Fail closed without logging every tick; registration can restore support.
            }
        }
        return false;
    }
}
