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

/**
 * {@link RpRoomStsValueSource} 房态房量匹配逻辑测试。
 *
 * @author gaofeng
 * @date 2026/10/28
 */
public class RpRoomStsValueSourceTest {

    /**
     * 房态计划起始日（距 BASE_DATE 的天数）。
     */
    private static final int PLAN_START = 20000;

    @Test
    public void singleNightReturnsStoredValue() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 5, 999), PLAN_START, 1, 1);
        assertEquals(5, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightZeroReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 0, 999), PLAN_START, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightNegativeReturnsMinusTwo() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, -5, 999), PLAN_START, 1, 1);
        assertEquals(-2, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightAnyZeroReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 0, 999), PLAN_START, 2, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightAnyNegativeReturnsMinusTwo() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, -5, 999), PLAN_START, 2, 1);
        assertEquals(-2, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightReturnsMinimumValue() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 5, 8, 3, 999), PLAN_START, 3, 1);
        assertEquals(3, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightIgnoresCheckoutDayValue() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 7, 9, 1, 999), PLAN_START, 2, 1);
        assertEquals(7, evaluate(valueSource, 0));
    }

    @Test
    public void checkInBeforePlanStartReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200), PLAN_START - 1, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void checkInAtLastPlanDayReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200, 300), PLAN_START + 2, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void singleNightStayBeyondPlanEndReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200), PLAN_START + 2, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightStayBeyondPlanEndReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200), PLAN_START + 1, 2, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void multiNightStayExactlyCoversPlanReturnsZero() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200), PLAN_START, 2, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void checkinOffsetNearIntegerMaxReturnsZeroInsteadOfOverflowing() {
        RpRoomStsValueSource valueSource = subSts(
                encode(PLAN_START, 100, 200), Integer.MAX_VALUE, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void nonPositiveDaysReturnsZero() {
        RpRoomStsValueSource zeroDays = subSts(
                encode(PLAN_START, 100, 200), PLAN_START, 0, 1);
        assertEquals(0, evaluate(zeroDays, 0));

        RpRoomStsValueSource negativeDays = subSts(
                encode(PLAN_START, 100, 200), PLAN_START, -1, 1);
        assertEquals(0, evaluate(negativeDays, 0));
    }

    @Test
    public void nullSourceReturnsZeroForEveryDocument() {
        RpRoomStsValueSource valueSource = new RpRoomStsValueSource(
                null, PLAN_START, 1, 1);
        FunctionValues values = values(valueSource);
        assertEquals(0, intValue(values, 0));
        assertEquals(0, intValue(values, 1));
    }

    @Test
    public void nullValueReturnsZero() {
        RpRoomStsValueSource valueSource = new RpRoomStsValueSource(
                strings((String) null), PLAN_START, 1, 1);
        assertEquals(0, evaluate(valueSource, 0));
    }

    @Test
    public void emptyOrShortValueReturnsZero() {
        RpRoomStsValueSource empty = new RpRoomStsValueSource(
                strings(""), PLAN_START, 1, 1);
        assertEquals(0, evaluate(empty, 0));

        RpRoomStsValueSource shortValue = new RpRoomStsValueSource(
                strings("abcd"), PLAN_START, 1, 1);
        assertEquals(0, evaluate(shortValue, 0));
    }

    @Test
    public void documentValuesAreDecodedIndependently() {
        RpRoomStsValueSource valueSource = new RpRoomStsValueSource(
                strings(encode(PLAN_START, 5, 999), encode(PLAN_START, 8, 999)),
                PLAN_START, 1, 1);
        FunctionValues values = values(valueSource);
        assertEquals(5, intValue(values, 0));
        assertEquals(8, intValue(values, 1));
    }

    @Test
    public void roomNumDoesNotAffectComputedValue() {
        String encoded = encode(PLAN_START, 5, 8, 999);
        assertEquals(5, evaluate(subSts(encoded, PLAN_START, 2, 1), 0));
        assertEquals(5, evaluate(subSts(encoded, PLAN_START, 2, 10), 0));
    }

    @Test
    public void descriptionUsesRoomStatusFunctionName() {
        RpRoomStsValueSource valueSource = new RpRoomStsValueSource(
                strings(encode(PLAN_START, 5, 999)), PLAN_START, 2, 3);
        assertEquals("rp_room_sts(test,20000,2,3)", valueSource.description());
    }

    /**
     * 构造使用固定房态计划数据的 {@link RpRoomStsValueSource}。
     */
    private static RpRoomStsValueSource subSts(String encoded, int checkinDateOffset,
                                               int days, int roomNum) {
        return new RpRoomStsValueSource(strings(encoded), checkinDateOffset, days, roomNum);
    }

    /**
     * 按天偏移和房态数组编码字段值。
     */
    private static String encode(int daysOffset, int... values) {
        return RpByteUtils.bytesToString(
                RpByteUtils.encode(RpByteUtils.BASE_DATE.plusDays(daysOffset), values));
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
    private static FunctionValues values(RpRoomStsValueSource valueSource) {
        try {
            return valueSource.getValues(Collections.emptyMap(), null);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * 计算指定文档的函数值。
     */
    private static int evaluate(RpRoomStsValueSource valueSource, int doc) {
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
