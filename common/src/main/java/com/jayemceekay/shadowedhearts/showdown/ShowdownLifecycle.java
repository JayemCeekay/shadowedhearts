package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;
import com.jayemceekay.shadowedhearts.config.ShadowedHeartsConfigs;
import kotlin.Unit;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/** Coordinates runtime injection on Cobblemon's Showdown thread. */
public final class ShowdownLifecycle {
    private ShowdownLifecycle() {
    }

    public static void initialize() {
        withContext(ShowdownRuntimePatcher.DynamicInjector::inject);
    }

    public static void injectConfig() {
        if (!ShadowedHeartsConfigs.getInstance().getShadowConfig().isLoaded()) {
            return;
        }
        withContext(ShowdownRuntimePatcher.DynamicInjector::injectConfig);
    }

    private static void withContext(Consumer<Object> action) {
        Cobblemon.INSTANCE.getShowdownThread().queue(showdownService -> {
            if (showdownService instanceof GraalShowdownService service) {
                action.accept(contextOf(service));
            }
            return Unit.INSTANCE;
        });
    }

    private static Object contextOf(GraalShowdownService service) {
        try {
            // Keep the context opaque: NeoForge relocates Graal's implementation classes.
            Field field = GraalShowdownService.class.getDeclaredField("context");
            field.setAccessible(true);
            return field.get(service);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
