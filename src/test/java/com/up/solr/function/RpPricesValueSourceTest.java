package com.up.solr.function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.StrDocValues;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * {@link RpPricesValueSource} 单晚价格匹配逻辑测试。
 *
 * @author gaofeng
 * @date 2026/10/26
 */
public class RpPricesValueSourceTest {

    /**
     * 价格计划起始日（距 BASE_DATE 的天数）。
     */
    private static final int PLAN_START = 20000;

    @Test
    public void singleNightPriceInRangeReturnsPrice() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200, 300), PLAN_START + 1, 1, 150, 250);
        assertEquals(200, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightPriceAtRangeBoundsIsMatched() {
        RpPricesValueSource atLowerBound = subPrice(
                encode(PLAN_START, 100, 999), PLAN_START, 1, 100, 200);
        assertEquals(100, evaluate(atLowerBound, 0));

        RpPricesValueSource atUpperBound = subPrice(
                encode(PLAN_START, 200, 999), PLAN_START, 1, 100, 200);
        assertEquals(200, evaluate(atUpperBound, 0));
    }

    @Test
    public void checkoutBeforePlanEndIsValid() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200), PLAN_START, 1, 0, 1000);
        assertEquals(100, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightPriceOutsideRangeReturnsMinusOne() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200, 300), PLAN_START, 1, 201, 299);
        assertEquals(-1, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightZeroPriceReturnsZero() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 0, 999), PLAN_START, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void reversedPriceRangeIsTreatedAsClosedInterval() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 200, 999), PLAN_START, 1, 250, 150);
        assertEquals(200, evaluate(valueSource, 0));
    }

    @Test
    public void checkInBeforePlanStartReturnsZero() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200), PLAN_START - 1, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void checkInAtLastPlanDayReturnsPrice() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200, 300), PLAN_START + 2, 1, 0, 1000);
        assertEquals(300, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightStayBeyondPlanEndReturnsZero() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200), PLAN_START + 2, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightAllPricesInRangeReturnsFirstNightPrice() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200, 300, 999), PLAN_START, 3, 0, 1000);
        assertEquals(100, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightReturnsFirstPriceInRange() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 300, 500, 999), PLAN_START, 3, 300, 500);
        assertEquals(300, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightNoPriceInRangeReturnsMinusOne() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200, 300, 999), PLAN_START, 3, 400, 500);
        assertEquals(-1, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightAnyZeroPriceReturnsZeroEvenWhenAnotherNightMatches() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 0, 300, 999), PLAN_START, 3, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void zeroPriceOutsideStayRangeIsIgnored() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 0, 100, 200, 999), PLAN_START + 1, 2, 0, 1000);
        assertEquals(100, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightStayBeyondPlanEndReturnsZero() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200), PLAN_START + 1, 2, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightStayExactlyCoversPlanReturnsFirstNightPrice() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 200), PLAN_START, 2, 0, 1000);
        assertEquals(100, evaluate(valueSource, 0));
    }

    @Test
    public void nonPositiveDaysReturnsZero() {
        RpPricesValueSource zeroDays = subPrice(
                encode(PLAN_START, 100), PLAN_START, 0, 0, 1000);
        assertEquals(0, evaluate(zeroDays, 0));

        RpPricesValueSource negativeDays = subPrice(
                encode(PLAN_START, 100), PLAN_START, -1, 0, 1000);
        assertEquals(0, evaluate(negativeDays, 0));
    }

    @Test
    public void checkinOffsetNearIntegerMaxReturnsZeroInsteadOfOverflowing() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100), Integer.MAX_VALUE, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightReversedRangeIsTreatedAsClosedInterval() {
        RpPricesValueSource valueSource = subPrice(
                encode(PLAN_START, 100, 300, 500, 999), PLAN_START, 3, 500, 300);
        assertEquals(300, evaluate(valueSource, 0));
    }

    @Test
    public void nullSourceReturnsZeroForEveryDocument() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                null, PLAN_START, 1, 0, 1000);
        FunctionValues values = values(valueSource);
        assertEquals(0, intValue(values, 0));
        assertEquals(0, intValue(values, 1));
    }

    @Test
    public void nullValueReturnsZero() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                strings((String) null), PLAN_START, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void emptyValueReturnsZero() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                strings(""), PLAN_START, 1, 0, 1000);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void documentValuesAreDecodedIndependently() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                strings(encode(PLAN_START, 100, 200), encode(PLAN_START, 300, 400)),
                PLAN_START, 1, 0, 1000);
        FunctionValues values = values(valueSource);
        assertEquals(100, intValue(values, 0));
        assertEquals(300, intValue(values, 1));
    }

    @Test
    public void equalParametersProduceEqualValueSources() {
        ValueSource source = strings(encode(PLAN_START, 100));
        RpPricesValueSource first = new RpPricesValueSource(source, PLAN_START, 1, 10, 20);
        RpPricesValueSource second = new RpPricesValueSource(source, PLAN_START, 1, 10, 20);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new RpPricesValueSource(source, PLAN_START, 1, 10, 21));
    }

    @Test
    public void descriptionContainsAllParameters() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                strings(encode(PLAN_START, 100)), PLAN_START, 2, 10, 20);
        assertEquals("subPrice(test,20000,2,10,20)", valueSource.description());
    }

    @Test
    public void nullSourceProducesEqualValueSources() {
        RpPricesValueSource first = new RpPricesValueSource(null, PLAN_START, 1, 10, 20);
        RpPricesValueSource second = new RpPricesValueSource(null, PLAN_START, 1, 10, 20);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new RpPricesValueSource(null, PLAN_START, 1, 10, 21));
    }

    @Test
    public void descriptionOfNullSourceContainsNull() {
        RpPricesValueSource valueSource = new RpPricesValueSource(
                null, PLAN_START, 2, 10, 20);
        assertEquals("subPrice(null,20000,2,10,20)", valueSource.description());
    }

    /**
     * 构造使用固定价格计划数据的 {@link RpPricesValueSource}。
     */
    private static RpPricesValueSource subPrice(String encoded, int offsetDayNum,
                                                int checkinNum, int priceFrom, int priceTo) {
        return new RpPricesValueSource(strings(encoded), offsetDayNum, checkinNum, priceFrom, priceTo);
    }

    /**
     * 按天偏移和价格数组编码价格计划字段值。
     */
    private static String encode(int daysOffset, int... prices) {
        return RpByteUtils.bytesToString(
                RpByteUtils.encode(RpByteUtils.BASE_DATE.plusDays(daysOffset), prices));
    }

    /**
     * 构造按文档返回不同字段值的 {@link ValueSource}，模拟 Solr 字符串字段。
     */
    private static ValueSource strings(final String... encodedByDoc) {
        return new ValueSource() {
            @Override
            public FunctionValues getValues(Map context, LeafReaderContext readerContext) {
                return new StrDocValues(this) {
                    @Override
                    public String strVal(int doc) {
                        return encodedByDoc[doc];
                    }
                };
            }

            @Override
            public String description() {
                return "test";
            }

            @Override
            public boolean equals(Object other) {
                return this == other;
            }

            @Override
            public int hashCode() {
                return System.identityHashCode(this);
            }
        };
    }

    /**
     * 取得 {@link FunctionValues}，把受检异常转为断言失败。
     */
    private static FunctionValues values(RpPricesValueSource valueSource) {
        try {
            return valueSource.getValues(Collections.emptyMap(), null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 计算指定文档的函数值。
     */
    private static int evaluate(RpPricesValueSource valueSource, int doc) {
        return intValue(values(valueSource), doc);
    }

    /**
     * 读取指定文档的 int 值，把受检异常转为断言失败。
     */
    private static int intValue(FunctionValues values, int doc) {
        try {
            return values.intVal(doc);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
