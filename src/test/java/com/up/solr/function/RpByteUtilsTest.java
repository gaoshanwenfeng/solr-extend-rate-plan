package com.up.solr.function;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link RpByteUtils} 单元测试。
 *
 * @author gaofeng
 * @date 2026/10/26
 */
public class RpByteUtilsTest {
    public static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 1);


    @Test
    public void encodedRatePlan() throws IOException {
        LocalDate localDate = LocalDate.of(2026, 10, 27);
        byte[] data = RpByteUtils.encode(localDate, new int[]{299, 399, 499});
        assertEquals(3, RpByteUtils.getDataCount(RpByteUtils.bytesToString(data)));
        printBytes(data);
        String str = RpByteUtils.bytesToString(data);
        printBytes(str.getBytes(RpByteUtils.BYTE_CHARSET));
    }

    static void printBytes(byte[] data) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            bos.write(data);
            System.out.println("***data[byte]***:" + Arrays.toString(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void printIntArr(int[] array) {
        System.out.println("***data[int]***:" + Arrays.toString(array));
    }


    @Test
    public void charSequencePriceCountReturnsZeroForNullOrShortData() {
        assertEquals(0, RpByteUtils.getDataCount((CharSequence) null));
        assertEquals(0, RpByteUtils.getDataCount(""));
        assertEquals(0, RpByteUtils.getDataCount("abcd"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void charSequencePriceCountRejectsUnalignedLength() {
        RpByteUtils.getDataCount("abcdefghi");
    }

    @Test(expected = IllegalArgumentException.class)
    public void charSequencePriceCountRejectsCountMismatch() {
        byte[] data = RpByteUtils.encode(
                RpByteUtils.BASE_DATE.plusDays(30), new int[]{100, 200, 300});
        byte[] truncated = Arrays.copyOf(data, data.length - RpByteUtils.UNIT_NUM);
        RpByteUtils.getDataCount(RpByteUtils.bytesToString(truncated));
    }

    // ------------------------------------------------------------------
    // int 与 byte[] 互转
    // ------------------------------------------------------------------

    @Test
    public void intToBytesWritesBigEndian() {
        assertArrayEquals(new byte[]{0, 0, 0, 42}, RpByteUtils.intToBytes(42));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, RpByteUtils.intToBytes(0x01020304));
        assertArrayEquals(new byte[]{-1, -1, -1, -1}, RpByteUtils.intToBytes(-1));
        assertArrayEquals(new byte[]{(byte) 0x80, 0, 0, 0},
                RpByteUtils.intToBytes(Integer.MIN_VALUE));
    }

    @Test
    public void intToBytesRoundTripsThroughBytesToInt() {
        int[] values = {0, 1, -1, 255, 256, 65535, -123456789,
                Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (int value : values) {
            byte[] bytes = RpByteUtils.intToBytes(value);
            assertEquals(RpByteUtils.UNIT_NUM, bytes.length);
            assertEquals(value, RpByteUtils.bytesToInt(bytes, 0));
        }
    }

    @Test
    public void bytesToIntReturnsZeroForNullOrIllegalOffset() {
        assertEquals(0, RpByteUtils.bytesToInt(null, 0));
        byte[] bytes = RpByteUtils.intToBytes(0x01020304);
        assertEquals(0, RpByteUtils.bytesToInt(bytes, -1));
        assertEquals(0, RpByteUtils.bytesToInt(bytes, 1));
        assertEquals(0, RpByteUtils.bytesToInt(new byte[0], 0));
        assertEquals(0, RpByteUtils.bytesToInt(new byte[3], 0));
    }

    @Test
    public void bytesToIntReadsAtOffsetAndTreatsBytesAsUnsigned() {
        byte[] bytes = {0x01, (byte) 0xFF, (byte) 0x80, 0x00,
                0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertEquals(0x01FF8000, RpByteUtils.bytesToInt(bytes, 0));
        assertEquals(0x7FFFFFFF, RpByteUtils.bytesToInt(bytes, 4));
        assertEquals(0x7FFFFFFF,
                RpByteUtils.bytesToInt(bytes, bytes.length - RpByteUtils.UNIT_NUM));
    }

    // ------------------------------------------------------------------
    // byte[] 与 String 互转
    // ------------------------------------------------------------------

    @Test
    public void bytesToStringAndStringToBytesRoundTripEveryByteValue() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        String text = RpByteUtils.bytesToString(bytes);
        assertEquals(bytes.length, text.length());
        assertArrayEquals(bytes, RpByteUtils.stringToBytes(text));
    }

    @Test
    public void stringToBytesUsesOneBytePerLatin1Char() {
        assertArrayEquals(new byte[]{0x41, (byte) 0xFF, 0x00},
                RpByteUtils.stringToBytes("Aÿ\u0000"));
    }

    @Test
    public void stringToBytesReplacesUnmappableCharWithQuestionMark() {
        assertArrayEquals(new byte[]{'?'}, RpByteUtils.stringToBytes("中"));
    }

    @Test(expected = NullPointerException.class)
    public void bytesToStringRejectsNull() {
        RpByteUtils.bytesToString(null);
    }

    @Test(expected = NullPointerException.class)
    public void stringToBytesRejectsNull() {
        RpByteUtils.stringToBytes(null);
    }

    // ------------------------------------------------------------------
    // int[] 解析与对齐检查
    // ------------------------------------------------------------------

    @Test(expected = NullPointerException.class)
    public void bytesToIntArrayRejectsNull() {
        RpByteUtils.bytesToIntArray(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void bytesToIntArrayRejectsUnalignedLength() {
        RpByteUtils.bytesToIntArray(new byte[5]);
    }

    @Test
    public void bytesToIntArrayReturnsTwoZerosForShortInput() {
        assertArrayEquals(new int[]{0, 0}, RpByteUtils.bytesToIntArray(new byte[0]));
        assertArrayEquals(new int[]{0, 0}, RpByteUtils.bytesToIntArray(new byte[4]));
    }

    @Test
    public void bytesToIntArrayParsesHeaderAndValues() {
        byte[] bytes = RpByteUtils.encode(123, new int[]{7, 8, 9});
        assertArrayEquals(new int[]{123, 3, 7, 8, 9}, RpByteUtils.bytesToIntArray(bytes));
    }

    @Test
    public void isUnitAlignedOnlyChecksLength() {
        assertFalse(RpByteUtils.isUnitAligned(null));
        assertTrue(RpByteUtils.isUnitAligned(new byte[0]));
        assertTrue(RpByteUtils.isUnitAligned(new byte[4]));
        assertTrue(RpByteUtils.isUnitAligned(new byte[8]));
        assertFalse(RpByteUtils.isUnitAligned(new byte[3]));
        assertFalse(RpByteUtils.isUnitAligned(new byte[5]));
    }

    // ------------------------------------------------------------------
    // encode
    // ------------------------------------------------------------------

    @Test
    public void encodeDateAndDataBuildsDocumentedLayout() {
        LocalDate date = RpByteUtils.BASE_DATE.plusDays(5);
        byte[] bytes = RpByteUtils.encode(date, new int[]{100, 200, 300});
        assertEquals((2 + 3) * RpByteUtils.UNIT_NUM, bytes.length);
        assertArrayEquals(new int[]{5, 3, 100, 200, 300}, RpByteUtils.bytesToIntArray(bytes));
        assertEquals(5, RpByteUtils.getDateOffset(bytes));
        assertEquals(3, RpByteUtils.getDataCount(bytes));
        assertEquals(100, RpByteUtils.getData(bytes, 0));
        assertEquals(200, RpByteUtils.getData(bytes, 1));
        assertEquals(300, RpByteUtils.getData(bytes, 2));
    }

    @Test
    public void encodeDateBeforeBaseDateKeepsNegativeOffset() {
        byte[] bytes = RpByteUtils.encode(RpByteUtils.BASE_DATE.minusDays(1), new int[]{1});
        assertEquals(-1, RpByteUtils.getDateOffset(bytes));
    }

    @Test(expected = NullPointerException.class)
    public void encodeRejectsNullDate() {
        RpByteUtils.encode((LocalDate) null, new int[0]);
    }

    @Test(expected = NullPointerException.class)
    public void encodeDateRejectsNullData() {
        RpByteUtils.encode(RpByteUtils.BASE_DATE.plusDays(9), (int[]) null);
    }

    @Test
    public void encodeDaysOffsetHandlesNullAndEmptyDataAsEmptyPlan() {
        byte[] nullData = RpByteUtils.encode(-3, (int[]) null);
        byte[] emptyData = RpByteUtils.encode(-3, new int[0]);
        assertEquals(RpByteUtils.DATA_OFFSET, nullData.length);
        assertArrayEquals(nullData, emptyData);
        assertEquals(-3, RpByteUtils.getDateOffset(nullData));
        assertEquals(0, RpByteUtils.getDataCount(nullData));
    }

    @Test
    public void encodeDaysOffsetTruncatesDataToMaximumCount() {
        byte[] bytes = RpByteUtils.encode(1, sequentialValues(RpByteUtils.DATA_MAX_NUM + 10));
        assertEquals((2 + RpByteUtils.DATA_MAX_NUM) * RpByteUtils.UNIT_NUM, bytes.length);
        assertEquals(RpByteUtils.DATA_MAX_NUM, RpByteUtils.getDataCount(bytes));
        assertEquals(RpByteUtils.DATA_MAX_NUM - 1,
                RpByteUtils.getData(bytes, RpByteUtils.DATA_MAX_NUM - 1));
    }

    @Test
    public void encodeDateTruncatesDataToMaximumCount() {
        byte[] bytes = RpByteUtils.encode(RpByteUtils.BASE_DATE.plusDays(1),
                sequentialValues(RpByteUtils.DATA_MAX_NUM + 10));
        assertEquals((2 + RpByteUtils.DATA_MAX_NUM) * RpByteUtils.UNIT_NUM, bytes.length);
        assertEquals(RpByteUtils.DATA_MAX_NUM, RpByteUtils.getDataCount(bytes));
    }

    // ------------------------------------------------------------------
    // 头部读取
    // ------------------------------------------------------------------

    @Test
    public void headerReadersReturnZeroForNullOrShortInput() {
        byte[] fourBytes = new byte[4];
        assertEquals(0, RpByteUtils.getDateOffset(null));
        assertEquals(0, RpByteUtils.getDateOffset(fourBytes));
        assertEquals(0, RpByteUtils.getDataCount((byte[]) null));
        assertEquals(0, RpByteUtils.getDataCount(fourBytes));
    }

    @Test
    public void headerReadersReturnEncodedValues() {
        byte[] bytes = RpByteUtils.encode(42, new int[]{1, 2});
        assertEquals(42, RpByteUtils.getDateOffset(bytes));
        assertEquals(2, RpByteUtils.getDataCount(bytes));
    }

    @Test
    public void getDataCountCharSequenceReturnsEncodedCount() {
        String encoded = RpByteUtils.bytesToString(RpByteUtils.encode(5, new int[]{1, 2, 3}));
        assertEquals(3, RpByteUtils.getDataCount(encoded));
    }

    @Test
    public void headerReadersRejectNegativeCount() {
        byte[] bytes = headerBytes(0, -1);
        assertThrows(IllegalArgumentException.class, () -> RpByteUtils.getDateOffset(bytes));
        assertThrows(IllegalArgumentException.class, () -> RpByteUtils.getDataCount(bytes));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.getDataCount(RpByteUtils.bytesToString(bytes)));
    }

    @Test
    public void headerReadersRejectLengthMismatch() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{1, 2, 3});
        byte[] truncated = Arrays.copyOf(bytes, bytes.length - RpByteUtils.UNIT_NUM);
        assertThrows(IllegalArgumentException.class, () -> RpByteUtils.getDateOffset(truncated));
        assertThrows(IllegalArgumentException.class, () -> RpByteUtils.getDataCount(truncated));
    }

    @Test
    public void getDataCountCharSequenceRejectsNonLatin1Count() {
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.getDataCount("\u0000\u0000\u0000\u0000Ā\u0000\u0000\u0000"));
    }

    @Test
    public void getDataReturnsValueByIndex() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{10, -20, 30});
        assertEquals(10, RpByteUtils.getData(bytes, 0));
        assertEquals(-20, RpByteUtils.getData(bytes, 1));
        assertEquals(30, RpByteUtils.getData(bytes, 2));
    }

    @Test
    public void getDataRejectsIndexOutOfBounds() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{10});
        assertThrows(IndexOutOfBoundsException.class, () -> RpByteUtils.getData(bytes, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> RpByteUtils.getData(bytes, 1));
    }

    // ------------------------------------------------------------------
    // intArrToBytes
    // ------------------------------------------------------------------

    @Test
    public void intArrToBytesWithoutValuesProducesEmptyPlan() {
        byte[] fromNull = RpByteUtils.intArrToBytes(17, (int[]) null);
        byte[] fromEmpty = RpByteUtils.intArrToBytes(17);
        assertEquals(RpByteUtils.DATA_OFFSET, fromNull.length);
        assertArrayEquals(fromNull, fromEmpty);
        assertEquals(17, RpByteUtils.getDateOffset(fromNull));
        assertEquals(0, RpByteUtils.getDataCount(fromNull));
    }

    @Test
    public void intArrToBytesMatchesEncodeLayout() {
        byte[] bytes = RpByteUtils.intArrToBytes(17, 1, 2, 3);
        assertArrayEquals(RpByteUtils.encode(17, new int[]{1, 2, 3}), bytes);
        assertArrayEquals(new int[]{17, 3, 1, 2, 3}, RpByteUtils.bytesToIntArray(bytes));
    }

    @Test
    public void intArrToBytesWithSingleValueUsesSameHeader() {
        byte[] bytes = RpByteUtils.intArrToBytes(17, 42);
        assertEquals((2 + 1) * RpByteUtils.UNIT_NUM, bytes.length);
        assertArrayEquals(new int[]{17, 1, 42}, RpByteUtils.bytesToIntArray(bytes));
    }

    @Test
    public void intArrToBytesTruncatesToMaximumCount() {
        byte[] bytes = RpByteUtils.intArrToBytes(0,
                sequentialValues(RpByteUtils.DATA_MAX_NUM + 1));
        assertEquals((2 + RpByteUtils.DATA_MAX_NUM) * RpByteUtils.UNIT_NUM, bytes.length);
        assertEquals(RpByteUtils.DATA_MAX_NUM, RpByteUtils.getDataCount(bytes));
    }

    // ------------------------------------------------------------------
    // containsInRange（有符号 int 比较）
    // ------------------------------------------------------------------

    @Test
    public void containsInRangeMatchesClosedInterval() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{100, 200, 300});
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 100, 100));
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 99, 100));
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 100, 200));
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 300, 301));
        assertFalse(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 301, 400));
        assertFalse(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 101, 199));
    }

    @Test
    public void containsInRangeTreatsReversedBoundsAsClosedInterval() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{100, 200, 300});
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 300, 100));
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 400, 300));
        assertFalse(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 400, 301));
    }

    @Test
    public void containsInRangeScansHeaderWhenOffsetStartsAtZero() {
        byte[] bytes = RpByteUtils.encode(150, new int[]{100, 200});
        assertTrue(RpByteUtils.containsInRange(bytes, 150, 150));
        assertFalse(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 150, 150));
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, 100, 200));
    }

    @Test
    public void containsInRangeFindsNegativeValues() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{-5, 5});
        assertTrue(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, -10, -1));
        assertFalse(RpByteUtils.containsInRange(bytes, RpByteUtils.DATA_OFFSET, -4, -1));
    }

    @Test
    public void containsInRangeOnEmptyArrayReturnsFalse() {
        assertFalse(RpByteUtils.containsInRange(new byte[0], 0, 0));
    }

    @Test
    public void containsInRangeValidatesInput() {
        assertThrows(NullPointerException.class,
                () -> RpByteUtils.containsInRange((byte[]) null, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange(new byte[5], 0, 1));
        byte[] bytes = new byte[8];
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange(bytes, -4, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange(bytes, 2, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange(bytes, 12, 0, 1));
    }

    @Test
    public void containsInRangeCharSequenceMatchesByteArrayResult() {
        String encoded = RpByteUtils.bytesToString(
                RpByteUtils.encode(150, new int[]{100, 200, 300}));
        assertTrue(RpByteUtils.containsInRange(encoded, 150, 150));
        assertTrue(RpByteUtils.containsInRange(encoded, RpByteUtils.DATA_OFFSET, 100, 300));
        assertTrue(RpByteUtils.containsInRange(encoded, RpByteUtils.DATA_OFFSET, 300, 100));
        assertFalse(RpByteUtils.containsInRange(encoded, RpByteUtils.DATA_OFFSET, 101, 199));
    }

    @Test
    public void containsInRangeCharSequenceValidatesInput() {
        assertThrows(NullPointerException.class,
                () -> RpByteUtils.containsInRange((CharSequence) null, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange("abc", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange("Ā\u0000\u0000\u0000", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange("abcdefgh", 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRange("abcdefgh", 12, 0, 1));
    }

    // ------------------------------------------------------------------
    // containsInRangeByBytes（无符号字节字典序比较）
    // ------------------------------------------------------------------

    @Test
    public void containsInRangeByBytesMatchesClosedInterval() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{100, 200, 300});
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 100, 100));
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 99, 100));
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 100, 200));
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 300, 301));
        assertFalse(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 301, 400));
        assertFalse(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 101, 199));
    }

    @Test
    public void containsInRangeByBytesTreatsReversedBoundsAsClosedInterval() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{100, 200, 300});
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 300, 100));
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 400, 300));
        assertFalse(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 400, 301));
    }

    @Test
    public void containsInRangeByBytesScansHeaderWhenOffsetStartsAtZero() {
        byte[] bytes = RpByteUtils.encode(150, new int[]{100, 200});
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, 150, 150));
        assertFalse(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 150, 150));
        assertTrue(RpByteUtils.containsInRangeByBytes(bytes, RpByteUtils.DATA_OFFSET, 100, 200));
    }

    @Test
    public void containsInRangeByBytesAgreesWithSignedScanForNonNegativeData() {
        int[] values = {0, 1, 127, 128, 255, 256, 65535, 1 << 24, Integer.MAX_VALUE};
        byte[] bytes = RpByteUtils.encode(0, values);
        int[][] ranges = {
                {0, 0}, {1, 255}, {128, 255}, {256, 65535},
                {1 << 24, Integer.MAX_VALUE}, {100, 200}
        };
        for (int[] range : ranges) {
            assertEquals(
                    RpByteUtils.containsInRange(
                            bytes, RpByteUtils.DATA_OFFSET, range[0], range[1]),
                    RpByteUtils.containsInRangeByBytes(
                            bytes, RpByteUtils.DATA_OFFSET, range[0], range[1]));
        }
    }

    @Test
    public void containsInRangeByBytesRejectsNegativeBounds() {
        byte[] bytes = RpByteUtils.encode(0, new int[]{1});
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(bytes, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(bytes, 1, -1));
    }

    @Test
    public void containsInRangeByBytesValidatesInput() {
        assertThrows(NullPointerException.class,
                () -> RpByteUtils.containsInRangeByBytes((byte[]) null, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(new byte[5], 0, 1));
        byte[] bytes = new byte[8];
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(bytes, -4, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(bytes, 2, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes(bytes, 12, 0, 1));
    }

    @Test
    public void containsInRangeByBytesCharSequenceMatchesByteArrayResult() {
        String encoded = RpByteUtils.bytesToString(
                RpByteUtils.encode(150, new int[]{100, 200, 300}));
        assertTrue(RpByteUtils.containsInRangeByBytes(encoded, 150, 150));
        assertTrue(RpByteUtils.containsInRangeByBytes(
                encoded, RpByteUtils.DATA_OFFSET, 100, 300));
        assertTrue(RpByteUtils.containsInRangeByBytes(
                encoded, RpByteUtils.DATA_OFFSET, 300, 100));
        assertFalse(RpByteUtils.containsInRangeByBytes(
                encoded, RpByteUtils.DATA_OFFSET, 101, 199));
    }

    @Test
    public void containsInRangeByBytesCharSequenceValidatesInput() {
        assertThrows(NullPointerException.class,
                () -> RpByteUtils.containsInRangeByBytes((CharSequence) null, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes("abc", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes("Ā\u0000\u0000\u0000", 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes("abcdefgh", 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RpByteUtils.containsInRangeByBytes("abcdefgh", 0, -1, 1));
    }

    private static byte[] headerBytes(int daysOffset, int count) {
        byte[] bytes = new byte[RpByteUtils.DATA_OFFSET];
        System.arraycopy(RpByteUtils.intToBytes(daysOffset), 0, bytes, 0, RpByteUtils.UNIT_NUM);
        System.arraycopy(RpByteUtils.intToBytes(count), 0, bytes,
                RpByteUtils.UNIT_NUM, RpByteUtils.UNIT_NUM);
        return bytes;
    }

    private static int[] sequentialValues(int length) {
        int[] values = new int[length];
        for (int i = 0; i < length; i++) {
            values[i] = i;
        }
        return values;
    }
}
