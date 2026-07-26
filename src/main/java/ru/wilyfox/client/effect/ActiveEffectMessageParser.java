package ru.wilyfox.client.effect;

import ru.wilyfox.client.chat.ChatMessageSanitizer;
import ru.wilyfox.utils.Formatting;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActiveEffectMessageParser {
    private static final int PATTERN_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final List<Rule> RULES = List.of(
            new Rule(
                    Pattern.compile("^Вам отключили регенерацию на\\s+(\\d{1,6})\\s+сек\\.?$", PATTERN_FLAGS),
                    "regeneration_disabled",
                    "Regeneration Disabled",
                    ActiveEffectKind.DEBUFF
            ),
            new Rule(
                    Pattern.compile("^Ваша магическая сила снижена на\\s+(\\d{1,6})\\s+сек\\.?$", PATTERN_FLAGS),
                    "magic_power_reduced",
                    "Magic Power Reduced",
                    ActiveEffectKind.DEBUFF
            )
    );

    private ActiveEffectMessageParser() {
    }

    public static Optional<ParsedEffect> parse(String rawText) {
        String text = Formatting.stripMinecraftFormatting(ChatMessageSanitizer.forLogic(rawText))
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        for (Rule rule : RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            if (!matcher.matches()) {
                continue;
            }

            long seconds = Long.parseLong(matcher.group(1));
            return Optional.of(new ParsedEffect(
                    rule.id(),
                    rule.displayName(),
                    rule.kind(),
                    seconds * 1000L
            ));
        }
        return Optional.empty();
    }

    private record Rule(
            Pattern pattern,
            String id,
            String displayName,
            ActiveEffectKind kind
    ) {
    }

    public record ParsedEffect(
            String id,
            String displayName,
            ActiveEffectKind kind,
            long durationMillis
    ) {
    }
}
