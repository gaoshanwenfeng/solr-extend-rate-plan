package com.up.solr.function;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * {@link RpByteUtils} 的 CharSequence 读取测试。
 *
 * @author gaofeng
 * @date 2026/10/26
 */
public class RpByteUtilsTest {
    public static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 1);


    @Test
    public void encodedRatePlan() throws IOException {
        LocalDate localDate = LocalDate.of(2026, 10, 27);
        byte[] data = RpByteUtils.encodeRatePlan(localDate, new int[]{299, 399, 499});
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
    public void decodedRatePlan() {
        LocalDate localDate = LocalDate.of(2026, 10, 27);
        byte[] data = RpByteUtils.encodeRatePlan(localDate, new int[]{299, -399, 499, 0});
        assertEquals(4, RpByteUtils.getDataCount(RpByteUtils.bytesToString(data)));
        int[] dates = RpByteUtils.bytesToIntArr(data);
        printIntArr(dates);
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
        byte[] data = RpByteUtils.encodeRatePlan(
                RpByteUtils.BASE_DATE.plusDays(30), new int[]{100, 200, 300});
        byte[] truncated = Arrays.copyOf(data, data.length - RpByteUtils.UNIT_NUM);
        RpByteUtils.getDataCount(RpByteUtils.bytesToString(truncated));
    }


}
