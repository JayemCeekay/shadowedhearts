package com.jayemceekay.shadowedhearts.showdown;

import com.cobblemon.mod.common.battles.runner.ShowdownService;
import com.cobblemon.mod.common.battles.runner.graal.GraalShowdownService;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * ShadowedHearts micro-battle wrapper (initial scaffold).
 *
 * Context: This is a Minecraft Cobblemon mod; all shadow/purity/corruption/capture terms are gameplay mechanics.
 *
 * The goal of this runner is to execute a single deterministic action in a minimal Showdown battle using
 * the custom "gen9micro" format injected by ShowdownRuntimePatcher. For this first iteration, we
 * bootstrap a JS helper in the Graal Showdown context and expose a simple API.
 *
 * NOTE: This is an MVP scaffold. The current JS helper is a placeholder that prepares the Showdown
 * environment and returns a basic echo payload. Subsequent iterations will wire the full BattleStreams
 * flow to resolve one move and extract HP/status/item deltas per "Lets_investigate_creating_a_custom_forma.md".
 */
public final class MicroBattleRunner {

    private static final String JS_HELPER_NAME = "sh_runMicro";

    private MicroBattleRunner() {}

    /**
     * Runs a single-action micro-battle using the Showdown JS context.
     *
     * @param request Request containing seed, attacker/defender sets, env, and move
     * @return MicroResult containing placeholder summary (to be expanded with real battle deltas)
     */
    public static MicroResult runMicro(MicroRequest request) {
        Objects.requireNonNull(request, "request");

        // Ensure Showdown Graal context is ready
        ShowdownService service = ShowdownService.Companion.getService();
        if (!(service instanceof GraalShowdownService)) {
            throw new IllegalStateException("Unsupported ShowdownService implementation: " + service.getClass().getName());
        }
        GraalShowdownService graal = (GraalShowdownService) service;
        try {
            // Access the context field via reflection to avoid compile-time dependency on Graal classes
            Object ctx = getField(graal, "context");
            if (ctx == null) {
                graal.openConnection();
                ctx = getField(graal, "context");
            }
            if (ctx == null) {
                throw new IllegalStateException("Graal context is not available");
            }

            // Install JS helper once per context
            Object bindings = invoke(ctx, "getBindings", new Class<?>[]{String.class}, new Object[]{"js"});
            Object helper = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{JS_HELPER_NAME});
            if (helper == null || isNullValue(helper)) {
                invoke(ctx, "eval", new Class<?>[]{String.class, CharSequence.class}, new Object[]{"js", BUILD_JS_HELPER});
                helper = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{JS_HELPER_NAME});
            }
            if (helper == null || isNullValue(helper)) {
                throw new IllegalStateException("Failed to install JS micro runner helper (" + JS_HELPER_NAME + ")");
            }

            // Execute helper via Graal. Helper returns a Promise<string> from BattleStreams runner.
            String argsJson = request.toJson();
            Object result = invoke(helper, "execute", new Class<?>[]{Object.class}, new Object[]{argsJson});
            String json;
            if (result != null && isStringValue(result)) {
                json = (String) invoke(result, "asString", new Class<?>[0], new Object[0]);
            } else if (result != null && isPromiseValue(result)) {
                json = awaitPromise(getField(graal, "context"), result, 5000L);
            } else if (result != null) {
                json = String.valueOf(result);
            } else {
                json = "";
            }
            return MicroResult.fromJson(json);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to run micro battle via Graal context", e);
        }
    }

    // Reflection helpers to avoid compile-time dependency on org.graalvm.polyglot.*
    private static Object getField(Object target, String name) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object invoke(Object target, String method, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method m = target.getClass().getMethod(method, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static boolean isNullValue(Object polyglotValue) {
        try {
            Object res = invoke(polyglotValue, "isNull", new Class<?>[0], new Object[0]);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isStringValue(Object polyglotValue) {
        try {
            Object res = invoke(polyglotValue, "isString", new Class<?>[0], new Object[0]);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasMember(Object polyglotValue, String name) {
        try {
            Object res = invoke(polyglotValue, "hasMember", new Class<?>[]{String.class}, new Object[]{name});
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean canExecute(Object polyglotValue) {
        try {
            Object res = invoke(polyglotValue, "canExecute", new Class<?>[0], new Object[0]);
            return res instanceof Boolean && (Boolean) res;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isPromiseValue(Object polyglotValue) {
        try {
            if (!hasMember(polyglotValue, "then")) return false;
            Object then = invoke(polyglotValue, "getMember", new Class<?>[]{String.class}, new Object[]{"then"});
            return then != null && canExecute(then);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String awaitPromise(Object ctx, Object promise, long timeoutMs) throws Exception {
        // Define resolve/reject handlers in the JS context
        invoke(ctx, "eval", new Class<?>[]{String.class, CharSequence.class}, new Object[]{"js",
                "globalThis.__shMicroDone=false;" +
                "globalThis.__shMicroOut='';" +
                "globalThis.__shMicroErr='';" +
                "globalThis.__shResolve=(res)=>{ try{ globalThis.__shMicroOut=(typeof res==='string'?res:JSON.stringify(res)); }catch(e){ globalThis.__shMicroOut=String(res);} globalThis.__shMicroDone=true; };" +
                "globalThis.__shReject=(e)=>{ globalThis.__shMicroErr=String((e&&e.stack)||e); globalThis.__shMicroDone=true; };"
        });
        Object bindings = invoke(ctx, "getBindings", new Class<?>[]{String.class}, new Object[]{"js"});
        Object resolveFn = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{"__shResolve"});
        Object rejectFn = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{"__shReject"});
        // Attach then/catch to the Promise
        invoke(promise, "invokeMember", new Class<?>[]{String.class, Object[].class}, new Object[]{"then", new Object[]{resolveFn}});
        invoke(promise, "invokeMember", new Class<?>[]{String.class, Object[].class}, new Object[]{"catch", new Object[]{rejectFn}});
        long deadline = System.currentTimeMillis() + Math.max(1L, timeoutMs);
        while (System.currentTimeMillis() < deadline) {
            Object doneVal = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{"__shMicroDone"});
            boolean done = false;
            if (doneVal != null) {
                try {
                    Object b = invoke(doneVal, "asBoolean", new Class<?>[0], new Object[0]);
                    done = (b instanceof Boolean) && (Boolean) b;
                } catch (Throwable ignore) {
                    done = "true".equalsIgnoreCase(String.valueOf(doneVal));
                }
            }
            if (done) break;
            try { Thread.sleep(2L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
        Object errVal = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{"__shMicroErr"});
        String err = null;
        if (errVal != null) {
            try { err = (String) invoke(errVal, "asString", new Class<?>[0], new Object[0]); } catch (Throwable ignored) { err = String.valueOf(errVal); }
        }
        if (err != null && !err.isEmpty()) {
            return "{\"ok\":false,\"error\":" + quoteJson(err) + "}";
        }
        Object outVal = invoke(bindings, "getMember", new Class<?>[]{String.class}, new Object[]{"__shMicroOut"});
        String out = null;
        if (outVal != null) {
            try { out = (String) invoke(outVal, "asString", new Class<?>[0], new Object[0]); } catch (Throwable ignored) { out = String.valueOf(outVal); }
        }
        return out == null ? "" : out;
    }

    private static String quoteJson(String s) {
        if (s == null) return "\"\"";
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + esc + "\"";
    }

    // JS helper using Showdown BattleStreams. Returns a Promise that resolves to a JSON string.
    private static final String BUILD_JS_HELPER = String.join("\n",
            "(function(){",
            "  const PS = require('pokemon-showdown');",
            "  const {BattleStreams, Teams} = PS;",
            "  function safePack(set){",
            "    try { return Teams.pack([set]); } catch (e) { return ''; }",
            "  }",
            "  async function runMicroInternal(args){",
            "    const seed = Array.isArray(args.seed) ? args.seed : [1,2,3,4];",
            "    const formatid = args.formatid || 'gen9micro';",
            "    const streams = new BattleStreams.BattleStream();",
            "    const {omniscient, p1, p2} = BattleStreams.getPlayerStreams(streams);",
            "    // Start battle",
            "    omniscient.write('>start ' + JSON.stringify({formatid, seed, forceTies: false}));",
            "    // Players and teams",
            "    const p1Team = safePack(args.attacker || {});",
            "    const p2Team = safePack(args.defender || {});",
            "    p1.write('>player p1 ' + JSON.stringify({name: 'Attacker', team: p1Team}));",
            "    p2.write('>player p2 ' + JSON.stringify({name: 'Defender', team: p2Team}));",
            "    // Moves: attacker uses provided move; defender defaults (do nothing/struggle as per engine)",
            "    const move = args.move || 'tackle';",
            "    p1.write('>choose move ' + move);",
            "    p2.write('>choose default');",
            "    // Collect full log until stream ends",
            "    let log = '';",
            "    for await (const chunk of streams) { log += chunk; }",
            "    return JSON.stringify({ ok: true, seed, formatid, move, log });",
            "  }",
            "  globalThis." + JS_HELPER_NAME + " = function(argsJson){",
            "    let args = {};",
            "    try { args = JSON.parse(argsJson || '{}'); } catch(e) { args = {}; }",
            "    return runMicroInternal(args).catch(e => JSON.stringify({ ok:false, error: String(e && e.stack || e) }));",
            "  };",
            "})();"
    );
}
