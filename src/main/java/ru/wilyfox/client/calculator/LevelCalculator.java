package ru.wilyfox.client.calculator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Numeric level requirements from https://evoplus.pro/data/evo-levels.yml (2026-10-12). */
public final class LevelCalculator {
    public static final int MAX_LEVEL = 600;
    public enum Server { EVO, EVO_FAST }
    public enum Discount {
        NONE(0), FIVE(5), TEN(10), FIFTEEN(15), TWENTY(20), MAX(20);
        private final int percent;
        Discount(int percent) { this.percent = percent; }
        public int percent() { return percent; }
    }
    public record Result(int levels, long money, long blocks, long savings,
                         int middleDiscount, int highDiscount) { }

    private static final long[][] MONEY = new long[3][MAX_LEVEL + 1];
    private static final long[] BLOCKS = new long[MAX_LEVEL + 1];
    static {
        try (var stream = LevelCalculator.class.getResourceAsStream("/assets/froghelper/data/evo-levels.csv")) {
            if (stream == null) throw new IOException("Missing level requirements");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                int expected = 2;
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.startsWith("#") || line.isBlank()) continue;
                    String[] values = line.split(",");
                    int level = Integer.parseInt(values[0]);
                    long money = Long.parseLong(values[1]), blocks = Long.parseLong(values[2]);
                    if (values.length != 3 || level != expected++ || level > MAX_LEVEL || money < 0 || blocks < 0)
                        throw new IOException("Invalid level requirements: " + level);
                    MONEY[level >= 541 ? 2 : level >= 482 ? 1 : 0][level] = money;
                    BLOCKS[level] = blocks;
                }
                if (expected != MAX_LEVEL + 1) throw new IOException("Incomplete level requirements");
                for (long[] tier : MONEY) for (int level = 1; level <= MAX_LEVEL; level++)
                    tier[level] = Math.addExact(tier[level], tier[level - 1]);
            }
        } catch (IOException | RuntimeException failure) { throw new ExceptionInInitializerError(failure); }
    }

    private LevelCalculator() { }

    public static List<Discount> discounts(int target) {
        if (target >= 541) return List.of(Discount.NONE, Discount.FIVE, Discount.TEN,
                Discount.FIFTEEN, Discount.TWENTY, Discount.MAX);
        if (target >= 481) return List.of(Discount.NONE, Discount.FIVE, Discount.TEN, Discount.MAX);
        return List.of(Discount.NONE);
    }

    public static Result calculate(int current, int target, Server server, Discount discount) {
        if (current < 1 || current > MAX_LEVEL || target < 1 || target > MAX_LEVEL || current >= target)
            return null;
        if (!discounts(target).contains(discount)) discount = Discount.NONE;
        long base = MONEY[0][target] - MONEY[0][current];
        long middle = MONEY[1][target] - MONEY[1][current];
        long high = MONEY[2][target] - MONEY[2][current];
        int middlePercent = Math.min(10, discount.percent()), highPercent = discount.percent();
        long money = base + middle * (100 - middlePercent) / 100 + high * (100 - highPercent) / 100;
        long blocks = BLOCKS[target];
        // The website reports the target's total block requirement, not the blocks gained between levels.
        if (server == Server.EVO_FAST) blocks = (blocks * 2 + 2) / 3;
        return new Result(target - current, money, blocks, base + middle + high - money,
                middle > 0 ? middlePercent : 0, high > 0 ? highPercent : 0);
    }
}
