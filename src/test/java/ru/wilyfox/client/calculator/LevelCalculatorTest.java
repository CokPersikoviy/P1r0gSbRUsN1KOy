package ru.wilyfox.client.calculator;

import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static ru.wilyfox.client.calculator.LevelCalculator.*;

class LevelCalculatorTest {
    @Test void matchesPublishedWebsiteAtEveryLevelAndAcrossDiscountTiers() throws Exception {
        int cases = 0;
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/calculator/evoplus-reference.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.contains("#") || line.isBlank()) continue;
                var v = line.split(",");
                Discount discount = switch (v[2]) {
                    case "5" -> Discount.FIVE; case "10" -> Discount.TEN; case "15" -> Discount.FIFTEEN;
                    case "20" -> Discount.TWENTY; case "max" -> Discount.MAX; default -> Discount.NONE;
                };
                var result = calculate(Integer.parseInt(v[0]), Integer.parseInt(v[1]),
                        v[3].equals("EvoFast") ? Server.EVO_FAST : Server.EVO, discount);
                assertNotNull(result, line);
                assertEquals(Long.parseLong(v[4]), result.money(), line);
                assertEquals(Long.parseLong(v[5]), result.blocks(), line);
                assertEquals(Integer.parseInt(v[6]), result.levels(), line);
                cases++;
            }
        }
        assertTrue(cases > 800, "Reference fixtures must cover all 599 transitions and tier boundaries");
    }

    @Test void blocksAreTargetTotalAndFastRoundsUpWithoutChangingMoney() {
        var evo = calculate(124, 125, Server.EVO, Discount.NONE);
        var fast = calculate(1, 125, Server.EVO_FAST, Discount.NONE);
        assertEquals(15_200, evo.blocks());
        assertEquals(10_134, fast.blocks());
        assertEquals(calculate(1, 125, Server.EVO, Discount.NONE).money(), fast.money());
    }

    @Test void discountStartsAt482AndEachTierHasItsOwnCap() {
        assertEquals(0, calculate(480, 481, Server.EVO, Discount.MAX).savings());
        assertEquals(10, calculate(481, 482, Server.EVO, Discount.MAX).middleDiscount());
        var result = calculate(539, 541, Server.EVO, Discount.MAX);
        assertEquals(10, result.middleDiscount()); assertEquals(20, result.highDiscount());
        assertEquals(0, calculate(539, 540, Server.EVO, Discount.TWENTY).savings());
        assertEquals(List.of(Discount.NONE), discounts(480));
        assertEquals(List.of(Discount.NONE, Discount.FIVE, Discount.TEN, Discount.MAX), discounts(481));
        assertTrue(discounts(541).contains(Discount.TWENTY));
    }

    @Test void invalidRangesAreNotCalculated() {
        for (int[] range : new int[][]{{0,2},{1,601},{-1,2},{2,2},{600,1},{999,600}})
            assertNull(calculate(range[0], range[1], Server.EVO, Discount.NONE));
    }
}
