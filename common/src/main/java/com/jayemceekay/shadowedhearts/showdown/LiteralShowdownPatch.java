package com.jayemceekay.shadowedhearts.showdown;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/** Exact source edits with newline tolerance; never rewrites text outside the matched span. */
record LiteralShowdownPatch(String id, String anchor, String original, String replacement, List<String> alternatives) {
    LiteralShowdownPatch(String id, String anchor, String original, String replacement) {
        this(id, anchor, original, replacement, List.of());
    }

    LiteralShowdownPatch {
        alternatives = List.copyOf(alternatives);
    }

    enum Status { APPLIED, ALREADY_APPLIED, NOT_FOUND, AMBIGUOUS, MISSING_FILE, VERIFICATION_FAILED }

    record Result(Status status, String content, long targets, long originals, long replacements) {}

    Result apply(String content) {
        // Accept only complete, explicitly supported source blocks. Count all variants
        // together so a file containing multiple candidate implementations is rejected.
        Pattern before = Pattern.compile(Stream.concat(Stream.of(original), alternatives.stream())
                .map(source -> "(?:" + literal(source).pattern() + ")")
                .collect(Collectors.joining("|")));
        Pattern after = literal(replacement);
        long targets = literal(anchor).matcher(content).results().count();
        long originals = before.matcher(content).results().count();
        long replacements = after.matcher(content).results().count();
        if (targets > 1 || originals + replacements > 1) {
            return new Result(Status.AMBIGUOUS, content, targets, originals, replacements);
        }
        if (targets == 1 && originals == 0 && replacements == 1) {
            return new Result(Status.ALREADY_APPLIED, content, targets, originals, replacements);
        }
        if (targets != 1 || originals != 1 || replacements != 0) {
            return new Result(Status.NOT_FOUND, content, targets, originals, replacements);
        }
        var match = before.matcher(content);
        if (!match.find()) throw new IllegalStateException("Source match disappeared for " + id);
        String span = match.group();
        int newline = span.indexOf('\n');
        String separator = newline > 0 && span.charAt(newline - 1) == '\r' ? "\r\n" : "\n";
        String inserted = replacement.replace("\r\n", "\n").replace("\n", separator);
        String patched = content.substring(0, match.start()) + inserted + content.substring(match.end());
        if (after.matcher(patched).results().count() != 1 || before.matcher(patched).find()
                || literal(anchor).matcher(patched).results().count() != 1) {
            return new Result(Status.VERIFICATION_FAILED, content, targets, originals, replacements);
        }
        return new Result(Status.APPLIED, patched, targets, originals, replacements);
    }

    Result apply(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return new Result(Status.MISSING_FILE, "", 0, 0, 0);
        Result result = apply(Files.readString(path, StandardCharsets.UTF_8));
        if (result.status() == Status.APPLIED) {
            Files.writeString(path, result.content(), StandardCharsets.UTF_8);
        }
        return result;
    }

    private static Pattern literal(String source) {
        String[] lines = source.replace("\r\n", "\n").split("\n", -1);
        StringBuilder pattern = new StringBuilder("(?m)^");
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) pattern.append("\\r?\\n");
            pattern.append(Pattern.quote(lines[index]));
        }
        // A complete line is required: a marker inside another statement is not a target.
        return Pattern.compile(pattern.append("(?=\\r?$)").toString());
    }
}
