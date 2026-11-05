package com.jayemceekay.shadowedhearts.showdown;

/**
 * Accesses Cobblemon's existing Graal context without linking to a loader-specific package.
 * Cobblemon 1.8 uses org.graalvm on Fabric and relocates it under
 * com.cobblemon.mod.relocations.graalvm on NeoForge.
 */
final class ShowdownContextAccess {
    private final Object context;

    ShowdownContextAccess(Object context) {
        this.context = context;
    }

    Bindings getBindings(String language) throws ReflectiveOperationException {
        Object bindings = context.getClass().getMethod("getBindings", String.class)
                .invoke(context, language);
        return new Bindings(bindings);
    }

    void eval(String language, CharSequence source) throws ReflectiveOperationException {
        context.getClass().getMethod("eval", String.class, CharSequence.class)
                .invoke(context, language, source);
    }

    static final class Bindings {
        private final Object value;

        private Bindings(Object value) {
            this.value = value;
        }

        boolean hasMember(String name) throws ReflectiveOperationException {
            return (boolean) value.getClass().getMethod("hasMember", String.class)
                    .invoke(value, name);
        }

        Object getMember(String name) throws ReflectiveOperationException {
            return value.getClass().getMethod("getMember", String.class)
                    .invoke(value, name);
        }
    }
}
