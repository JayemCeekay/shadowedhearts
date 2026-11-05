package com.jayemceekay.shadowedhearts.showdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Resource and invocation boundaries shared by both Graal package layouts. */
final class ShowdownInjectionSupport {
    private static final Pattern TEMPLATE = Pattern.compile(
            "\\Amodule\\.exports\\s*=\\s*`([\\s\\S]*)`\\s*;?\\s*\\z");

    private ShowdownInjectionSupport() {}

    static String fallbackScript() throws IOException {
        return readResource("/data/shadowedhearts/showdown/injection-fallbacks.js");
    }

    static String resourceExpression(String path) throws IOException {
        return expression(readResource(path));
    }

    static String expression(String source) throws IOException {
        String trimmed = source.trim();
        if (trimmed.isEmpty()) throw new IOException("Empty Showdown injection resource");
        if (!trimmed.startsWith("module.exports")) return source;
        var template = TEMPLATE.matcher(trimmed);
        if (!template.matches()) throw new IOException("Unsupported Showdown template export");
        // Preserve JavaScript comments, URLs, regexes, and line boundaries for the JS parser.
        return template.group(1);
    }

    private static String readResource(String path) throws IOException {
        try (var input = ShowdownInjectionSupport.class.getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing Showdown resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static void execute(Object function, Object... arguments) throws ReflectiveOperationException {
        if (function == null) throw new NoSuchMethodException("Missing Showdown injection function");
        // Retain InvocationTargetException and its original JS cause for the caller's error log.
        function.getClass().getMethod("execute", Object[].class).invoke(function, (Object) arguments);
    }
}
