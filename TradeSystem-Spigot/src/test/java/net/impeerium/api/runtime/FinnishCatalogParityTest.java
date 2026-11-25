package net.impeerium.api.runtime;

import net.impeerium.api.localization.Language;
import net.impeerium.api.localization.LanguageRegistry;
import net.impeerium.api.localization.PluralCategory;
import net.impeerium.api.localization.TranslationArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Finnish rollout (2026-11-23) proof for this plugin's API catalogs. Lives in the runtime package so it can
 * compile catalogs with the exact 1.24 runtime compiler (test scope only, nothing ships).
 * <ul>
 *   <li>ET and EN render byte-identically (component JSON) to the catalog before the fi column was added
 *       ({@code l10n-baseline/}, a verbatim copy of the base commit's file), for every key and synthetic
 *       arguments of every declared type, including both plural forms;</li>
 *   <li>a Finnish viewer gets the fi template wherever it exists, else en, else et (per key);</li>
 *   <li>a key without fi falls back to en.</li>
 * </ul>
 */
class FinnishCatalogParityTest {
    /** Current catalog resource -> baseline copy taken from the base commit. */
    private static final Map<String, String> CATALOGS = Map.of(
            "/locales/messages.yml", "/l10n-baseline/messages.yml");
    /** fi rows imported by phase 5 (import/IMPORT-REPORT.md); guards against a silently dropped column. */
    private static final int FI_ROWS = 80;

    private static final LanguageRegistry LANGUAGES = LanguageRegistry.standard();
    private static final GsonComponentSerializer JSON = GsonComponentSerializer.gson();

    @Test void currentCatalogsPassTheStrictRuntimeValidator() throws IOException {
        for (String current : CATALOGS.keySet())
            try (InputStream in = open(current)) { LocalizationCatalogValidator.validate(in); }
    }

    @Test void estonianAndEnglishRenderExactlyAsBeforeTheFinnishColumn() throws IOException {
        int checked = 0;
        for (var pair : CATALOGS.entrySet()) {
            Map<String, CatalogCompiler.Message> now = compile(pair.getKey());
            Map<String, CatalogCompiler.Message> before = compile(pair.getValue());
            assertEquals(before.keySet(), now.keySet(), pair.getKey());
            for (var old : before.entrySet()) {
                String key = old.getKey();
                for (Map<String, TranslationArgument> args : samples(old.getValue())) {
                    for (Language language : List.of(Language.ET, Language.EN)) {
                        assertEquals(JSON.serialize(old.getValue().render(language, args)),
                                JSON.serialize(now.get(key).render(language, args)), key + " " + language);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 0);
    }

    @Test void finnishViewerGetsFinnishElseEnglishElseEstonianPerKey() throws IOException {
        int finnish = 0;
        for (String current : CATALOGS.keySet()) {
            for (var row : compile(current).entrySet()) {
                CatalogCompiler.Message message = row.getValue();
                if (message.templates().containsKey(Language.FI) || message.plurals().containsKey(Language.FI)) finnish++;
                for (Map<String, TranslationArgument> args : samples(message))
                    assertEquals(JSON.serialize(expected(message, args)),
                            JSON.serialize(message.render(Language.FI, args)), row.getKey());
            }
        }
        if (FI_ROWS > 0) assertEquals(FI_ROWS, finnish, "fi rows in the catalogs");
        else assertTrue(finnish > 0, "no fi rows found");
    }

    @Test void keyWithoutFinnishFallsBackToEnglish() throws IOException {
        int checked = 0;
        for (String current : CATALOGS.keySet()) {
            for (var row : compile(current).entrySet()) {
                CatalogCompiler.Message message = row.getValue();
                if (!message.templates().containsKey(Language.FI) || !message.templates().containsKey(Language.EN)) continue;
                Map<Language, String> withoutFi = new EnumMap<>(message.templates());
                withoutFi.remove(Language.FI);
                var stripped = new CatalogCompiler.Message(withoutFi, message.plurals(), message.signature());
                var englishOnly = new CatalogCompiler.Message(Map.of(Language.ET, message.templates().get(Language.EN)),
                        Map.of(), message.signature());
                for (Map<String, TranslationArgument> args : samples(message))
                    assertEquals(JSON.serialize(englishOnly.render(Language.FI, args)),
                            JSON.serialize(stripped.render(Language.FI, args)), row.getKey());
                checked++;
            }
        }
        assertTrue(checked > 0);
    }

    /** The first language of the Finnish chain that has the key, rendered as the only text with Finnish numbers. */
    private static Component expected(CatalogCompiler.Message message, Map<String, TranslationArgument> args) {
        for (Language source : LANGUAGES.chain(Language.FI)) {
            String template = message.templates().get(source);
            if (template != null)
                return new CatalogCompiler.Message(Map.of(Language.ET, template), Map.of(), message.signature())
                        .render(Language.FI, args);
            Map<PluralCategory, String> forms = message.plurals().get(source);
            if (forms != null)
                return new CatalogCompiler.Message(Map.of(), Map.of(Language.ET, forms), message.signature())
                        .render(Language.FI, args);
        }
        throw new AssertionError("no template");
    }

    /** One argument map per integer sample (1 and 1234 cover both plural forms and digit grouping). */
    private static List<Map<String, TranslationArgument>> samples(CatalogCompiler.Message message) {
        List<Map<String, TranslationArgument>> result = new ArrayList<>();
        for (long integer : new long[] {1, 1234}) {
            Map<String, TranslationArgument> args = new LinkedHashMap<>();
            message.signature().forEach((name, type) -> args.put(name, switch (type) {
                case TEXT -> TranslationArgument.text("<b>T-" + name + "</b>");
                case COMPONENT -> TranslationArgument.component(Component.text("C-" + name));
                case INTEGER -> TranslationArgument.integer(integer);
                case NUMBER -> TranslationArgument.number(new BigDecimal("1234.5"));
                default -> throw new AssertionError(type);
            }));
            result.add(args);
            if (message.signature().values().stream().noneMatch(t -> t == TranslationArgument.Type.INTEGER)) break;
        }
        return result;
    }

    private static Map<String, CatalogCompiler.Message> compile(String resource) throws IOException {
        try (InputStream in = open(resource)) {
            return new HashMap<>(CatalogCompiler.compile(CatalogCompiler.read(in)));
        }
    }

    private static InputStream open(String resource) {
        return Objects.requireNonNull(FinnishCatalogParityTest.class.getResourceAsStream(resource), resource);
    }

    @Test void baselineCopiesExist() {
        for (var pair : CATALOGS.entrySet()) assertNotNull(FinnishCatalogParityTest.class.getResource(pair.getValue()));
    }
}
