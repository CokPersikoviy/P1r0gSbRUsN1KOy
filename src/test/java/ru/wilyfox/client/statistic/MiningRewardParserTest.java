package ru.wilyfox.client.statistic;

import org.junit.jupiter.api.Test;
import ru.wilyfox.client.hud.config.MiningStatisticsConfig.Source;
import static org.junit.jupiter.api.Assertions.*;

class MiningRewardParserTest {
    @Test void parsesAllReferenceSourcesWithoutConfusingTheBlockCountWithIncome() {
        String[] messages = {"Фиолетовая бочка: 10 блоков, 12,5B", "Руна дала 12,5B", "Вы взорвали 10 блоков, 12,5B",
                "Вы сломали всю шахту: 12,5B", "Вы сломали 10 блоков посохом: 12,5B", "Сокрушающий меч разрубил 10 блоков: 12,5B"};
        Source[] sources = {Source.BARRELS, Source.RUNES, Source.BOMBS, Source.PETS, Source.WANDS, Source.MULTITOOL};
        for (int i = 0; i < messages.length; i++) {
            var result = MiningRewardParser.reward("§7[12:30:01] §a" + messages[i]);
            assertNotNull(result, messages[i]); assertEquals(sources[i], result.source()); assertEquals(12_500_000_000.0, result.money());
        }
        assertEquals(Source.RUNES, MiningRewardParser.reward("Вы сломали 10 блоков с помощью руны: 100K").source());
    }
    @Test void supportsIntegerDecimalAndLargeCurrencySuffixesAndExplicitCurrency() {
        assertEquals(1000, MiningRewardParser.actionBarPrice("+1K"));
        assertEquals(1_500_000, MiningRewardParser.actionBarPrice("+1,5M"));
        assertEquals(2_000_000_000_000_000.0, MiningRewardParser.actionBarPrice("+2Q"));
        assertEquals(125, MiningRewardParser.actionBarPrice("+125 монет"));
        assertEquals(500, MiningRewardParser.reward("Руна принесла 500 денег").money());
        assertEquals(0, MiningRewardParser.actionBarPrice("Комбо +10"));
        assertEquals(0, MiningRewardParser.actionBarPrice("+12.5 здоровья"));
    }
    @Test void onlyServerRewardPhrasesMatchAndShardMultipliersRemainDistinctFromMoney() {
        assertNull(MiningRewardParser.reward("Ⓛ Fox: Руна принесла 12B"));
        assertNull(MiningRewardParser.reward("Руна активировалась!"));
        assertNull(MiningRewardParser.reward("Баланс: 12B"));
        assertEquals(1, MiningRewardParser.reward("Вы нашли шард!").shards());
        assertEquals(3, MiningRewardParser.reward("Вы нашли шард! x3").shards());
        assertEquals(12, MiningRewardParser.reward("Вы нашли шарды: ×12").shards());
    }
}
