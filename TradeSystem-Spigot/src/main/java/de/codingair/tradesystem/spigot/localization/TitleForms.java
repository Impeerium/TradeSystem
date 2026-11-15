package de.codingair.tradesystem.spigot.localization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ItemsAdder offsets and Nexo shifts are two spellings of the same horizontal move. The Nexo migration (2026-11-09)
 * rewrote {@code :offset_N:} into runs of Nexo shift characters and {@code {img_offset_N}} into {@code {nexo_shift_N}};
 * comparing canonical forms keeps the owned trade artwork title recognised. Ported from Funds 1.9.1 {@code TitleForms}.
 */
final class TitleForms {
    /** PlaceholderAPI move tokens in either bracket style; they join the surrounding run like any other move. */
    private static final Pattern PAPI_MOVE = Pattern.compile("\\{(?:img_offset|nexo_shift)_(-?\\d+)\\}|%(?:img_offset|nexo_shift)_(-?\\d+)%");
    private static final Pattern IA_OFFSET = Pattern.compile(":offset_(-?\\d+):");
    /** Nexo's generated {@code nexo:shift} font, referenced from {@code minecraft:default}. */
    private static final Map<Character, Integer> NEXO_SHIFT = Map.ofEntries(
            Map.entry('', -1), Map.entry('', -2), Map.entry('', -4), Map.entry('', -8),
            Map.entry('', -16), Map.entry('', -32), Map.entry('', -64), Map.entry('', -128),
            Map.entry('', -256), Map.entry('', -512),
            Map.entry('', 1), Map.entry('', 2), Map.entry('', 4), Map.entry('', 8),
            Map.entry('', 16), Map.entry('', 32), Map.entry('', 64), Map.entry('', 128));
    private static final Pattern MOVE_MARK = Pattern.compile("\u0000move:(-?\\d+)\u0000");
    private static final int CACHE_LIMIT = 4096;
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private TitleForms() { }

    static boolean same(String first, String second) {
        if (first == null || second == null) return first == second;
        return first.equals(second) || canonical(first).equals(canonical(second));
    }

    static String canonical(String raw) {
        String cached = CACHE.get(raw);
        if (cached != null) return cached;
        String form = compute(raw);
        if (CACHE.size() < CACHE_LIMIT) CACHE.put(raw, form);
        return form;
    }

    /** The same title with every run of moves written as one ItemsAdder offset, so offset patterns match either spelling. */
    static String offsets(String raw) {
        return raw == null ? null : MOVE_MARK.matcher(canonical(raw)).replaceAll(":offset_$1:");
    }

    private static String compute(String raw) {
        String text = PAPI_MOVE.matcher(raw).replaceAll(token ->
                ":offset_" + (token.group(1) != null ? token.group(1) : token.group(2)) + ":");
        Matcher offset = IA_OFFSET.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        long run = 0;
        boolean moving = false;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            Integer shift = NEXO_SHIFT.get(c);
            if (shift != null) {
                run += shift;
                moving = true;
                i++;
                continue;
            }
            if (c == ':' && offset.region(i, text.length()).lookingAt()) {
                run += Long.parseLong(offset.group(1));
                moving = true;
                i = offset.end();
                continue;
            }
            if (moving) {
                out.append("\u0000move:").append(run).append('\u0000');
                run = 0;
                moving = false;
            }
            out.append(c);
            i++;
        }
        if (moving) out.append("\u0000move:").append(run).append('\u0000');
        return out.toString();
    }
}
