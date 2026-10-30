package com.up.solr.function;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

/**
 * 特定业态的字节结构工具，同时提供 byte[]/int/String 互转和区间扫描。
 *
 * <p>每个 unit 占 {@link #UNIT_NUM} 字节，存放一个大端序 int：
 * <pre>
 *   bytes[0..3]              距 {@link #BASE_DATE} 的天数偏移
 *   bytes[4..7]              数量 N
 *   bytes[8..11]             第 1 个数值
 *   bytes[12..15]            第 2 个数值
 *   ...
 *   bytes[8 + 4 * (N - 1)..] 第 N 个数值
 * </pre>
 *
 * <p>总长度 = {@code (2 + N) * 4} 字节。热路径方法直接操作 byte[]，不创建
 * RatePlan、int[] 等中间对象，适合在 Solr 中逐文档调用。
 *
 * <p>byte[] 与 String 互转使用 ISO-8859-1（Latin-1），保证一个字节对应一个字符。
 * 不要使用 UTF-8（变长编码）；也不建议使用 US-ASCII（只有 7 位，0x80-0xFF
 * 的字节会被替换成替换字符），否则二进制数据无法原样还原。
 *
 * @author gaofeng
 * @date 2026/10/26
 */
public final class RpByteUtils {

    /**
     * UNIT_NUM 的位移量：{@link #UNIT_NUM} = {@code 1 << UNIT_SHIFT}。
     */
    public static final int UNIT_SHIFT = 2;

    /**
     * 每个 int 占用的字节数（必须是 2 的幂）。
     */
    public static final int UNIT_NUM = 1 << UNIT_SHIFT;

    /**
     * 按位取模使用的掩码：{@link #UNIT_NUM} - 1。
     */
    static final int UNIT_MASK = UNIT_NUM - 1;
    /**
     * byte[] 与 String 互转使用的 1:1 编码。
     */
    public static final Charset BYTE_CHARSET = StandardCharsets.ISO_8859_1;
    /**
     * 日期偏移的基准日期（当前为 1970-01-01）。
     */
    public static final LocalDate BASE_DATE = LocalDate.of(1970, 1, 1);
    /**
     * 最大数量（3650）。
     */
    static final int DATA_MAX_NUM = 3650;
    /**
     * 头部包含的 unit 数：日期偏移 + 数量。
     */
    private static final int HEADER_UNITS = 2;
    /**
     * 数据的起始字节偏移。
     */
    public static final int DATA_OFFSET = HEADER_UNITS << RpByteUtils.UNIT_SHIFT;

    private RpByteUtils() {
    }

    /**
     * 功能说明：将 int 按大端序编码为 4 字节数组。
     * 异常说明：无。
     *
     * @param value 待编码的 int
     * @return 长度为 {@link #UNIT_NUM} 的字节数组
     * @author gaofeng
     * @date 2026/10/26
     */
    public static byte[] intToBytes(int value) {
        byte[] bytes = new byte[UNIT_NUM];
        writeInt(bytes, 0, value);
        return bytes;
    }

    /**
     * 功能说明：从指定偏移量读取一个大端序 int。
     * 输入为 null、offset 为负或越界时返回 0。
     * 异常说明：无，非法输入按 0 处理。
     *
     * @param bytes  源字节数组，允许为 null
     * @param offset 起始下标
     * @return 读取到的 int；输入非法时返回 0
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int bytesToInt(byte[] bytes, int offset) {
        if (bytes == null) {
            return 0;
        }
        if (offset < 0 || offset > bytes.length - UNIT_NUM) {
            return 0;
        }
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    /**
     * 功能说明：使用 ISO-8859-1 将 byte[] 转为 String，保证 1 字节对应 1 字符。
     *
     * @param bytes 源字节数组
     * @return 转换后的字符串
     * @throws NullPointerException bytes 为 null
     * @author gaofeng
     * @date 2026/10/26
     */
    public static String bytesToString(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new String(bytes, BYTE_CHARSET);
    }

    /**
     * 功能说明：使用 ISO-8859-1 将 String 转为 byte[]，保证 1 字符对应 1 字节。
     *
     * <p>注意：当前实现不对字符范围做校验，无法映射到 ISO-8859-1 的字符
     * （例如中文）会被替换为 '?'，不会抛出异常；需要严格校验时请先检查字符范围。
     *
     * @param text 源字符串
     * @return 编码后的字节数组
     * @throws NullPointerException text 为 null
     * @author gaofeng
     * @date 2026/10/26
     */
    public static byte[] stringToBytes(String text) {
        Objects.requireNonNull(text, "text");
        return text.getBytes(BYTE_CHARSET);
    }

    /**
     * 功能说明：将按 4 字节 unit 编码的 byte[] 解析为 int[]。
     *
     * <p>当前实现要求字节长度是 {@link #UNIT_NUM} 的整数倍；
     * 长度小于 8 时直接返回 {@code {0, 0}}，否则按 unit 顺序全部解析。
     *
     * @param bytes 源字节数组
     * @return 解析出的 int 数组；长度小于 8 时返回 {@code {0, 0}}
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 字节长度不是 {@link #UNIT_NUM} 的整数倍
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int[] bytesToIntArray(byte[] bytes) {
        if ((bytes.length & UNIT_MASK) != 0) {
            throw new IllegalArgumentException("字节长度必须是 " + UNIT_NUM + " 的整数倍，实际为 " + bytes.length);
        }
        if (bytes.length < 8) {
            return new int[]{0, 0};
        }
        int[] values = new int[bytes.length >> UNIT_SHIFT];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytesToInt(bytes, i << UNIT_SHIFT);
        }
        return values;
    }


    /**
     * 功能说明：判断字节数组长度是否为 {@link #UNIT_NUM} 的整数倍。
     * 异常说明：无，null 返回 false。
     *
     * @param bytes 待判断的字节数组，允许为 null
     * @return true 表示长度对齐；null 或长度不对齐返回 false
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean isUnitAligned(byte[] bytes) {
        return bytes != null && (bytes.length & UNIT_MASK) == 0;
    }

    /**
     * 功能说明：将一个 int 按大端序写入 byte[] 的指定偏移量，不创建中间对象。
     * 包内可见，调用方必须保证 offset 合法；无返回值。
     *
     * @param bytes  目标字节数组
     * @param offset 起始下标
     * @param value  待写入的 int
     * @author gaofeng
     * @date 2026/10/26
     */
    static void writeInt(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 24);
        bytes[offset + 1] = (byte) (value >>> 16);
        bytes[offset + 2] = (byte) (value >>> 8);
        bytes[offset + 3] = (byte) value;
    }

    /**
     * 功能说明：计算时间偏移
     * 天数偏移由 {@link #BASE_DATE} 计算并强转为 int，未做范围校验。
     *
     * @param date 业务日期
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int calOffsetBaseDate(LocalDate date) {
        int days = (int) ChronoUnit.DAYS.between(BASE_DATE, date);
        if (days < 0) {
            throw new IllegalArgumentException("时间不正确，距离BaseDate偏移为负数");
        }
        return days;
    }

    /**
     * 功能说明： BASE_DATE 偏移天数的转换LocalDate
     * LocalDate instance 必须是实例，返回结果基于实例时间的增加
     *
     * @param bytes 字节数组
     * @author gaofeng
     * @date 2026/10/26
     */
    public static LocalDate getLocalDate(byte[] bytes) {
        int offset = getDateOffset(bytes);
        LocalDate instance = LocalDate.of(1970, 1, 1);
        return instance.plusDays(offset);
    }

    /**
     * 功能说明：按业务日期编码节数组，布局为
     * {@code [daysOffset, count, num...]}。
     *
     * <p>数量超过 {@link #DATA_MAX_NUM} 时会截断到该上限；
     * 天数偏移由 {@link #BASE_DATE} 计算并强转为 int，未做范围校验。
     *
     * @param date    业务日期
     * @param dataArr 数组
     * @return 编码后的字节数组
     * @author gaofeng
     * @date 2026/10/26
     */
    public static byte[] encode(LocalDate date, int[] dataArr) {
        Objects.requireNonNull(date, "date");
        int days = (int) ChronoUnit.DAYS.between(BASE_DATE, date);
        if (dataArr.length > DATA_MAX_NUM) {
            dataArr = Arrays.copyOf(dataArr, DATA_MAX_NUM);
        }
        return encode(days, dataArr);
    }

    /**
     * 功能说明：按天数偏移编码字节数组。
     *
     * <p>数量超过 {@link #DATA_MAX_NUM} 时会截断到该上限。
     *
     * @param daysOffset 距 {@link #BASE_DATE} 的天数偏移
     * @param dataArr    数组，允许为 null
     * @return 编码后的字节数组
     * @author gaofeng
     * @date 2026/10/26
     */
    public static byte[] encode(int daysOffset, int[] dataArr) {
        if (dataArr == null) {
            byte[] data = new byte[HEADER_UNITS << RpByteUtils.UNIT_SHIFT];
            RpByteUtils.writeInt(data, 0, daysOffset);
            RpByteUtils.writeInt(data, UNIT_NUM, 0);
            return data;
        }
        if (dataArr.length > DATA_MAX_NUM) {
            dataArr = Arrays.copyOf(dataArr, DATA_MAX_NUM);
        }
        int totalUnits = HEADER_UNITS + dataArr.length;
        byte[] data = new byte[totalUnits << RpByteUtils.UNIT_SHIFT];
        RpByteUtils.writeInt(data, 0, daysOffset);
        RpByteUtils.writeInt(data, UNIT_NUM, dataArr.length);
        for (int i = 0; i < dataArr.length; i++) {
            RpByteUtils.writeInt(data, DATA_OFFSET + (i << RpByteUtils.UNIT_SHIFT), dataArr[i]);
        }
        return data;
    }

    /**
     * 功能说明：直接读取日期偏移，不创建对象。
     *
     * @param bytes 字节数组
     * @return 距 {@link #BASE_DATE} 的天数偏移；data 为 null 或长度不足 8 时返回 0
     * @throws IllegalArgumentException 数量为负或字节长度与数量不一致
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int getDateOffset(byte[] bytes) {
        validate(bytes);
        return RpByteUtils.bytesToInt(bytes, 0);
    }

    /**
     * 功能说明：直接读取数量，不创建对象。
     *
     * @param bytes 字节数组
     * @return data 为 null 或长度不足 8 时返回 0 ,否则返回实际的数量
     * @throws IllegalArgumentException 数量为负或字节长度与数量不一致
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int getDataCount(byte[] bytes) {
        return validate(bytes);
    }

    /**
     * 功能说明：校验 ISO-8859-1 编码并返回数量，不创建 byte[]。
     * 适用于 Solr 中 strVal(doc) 直接返回字符串的场景。
     *
     * @param encoded ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @return 数量；encoded 为 null 或长度小于 {@link #DATA_OFFSET} 时返回 0
     * @throws IllegalArgumentException 字符长度未对齐、数量为负、
     *                                  字符长度与数量不一致，或字符超出 ISO-8859-1 范围
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int getDataCount(CharSequence encoded) {
        return validate(encoded);
    }

    /**
     * 功能说明：直接读取第 index 个数据（从 0 开始），不创建对象。
     *
     * @param bytes 字节数组
     * @param index 下标，从 0 开始
     * @return 对应位置的
     * @throws IllegalArgumentException  数量为负或字节长度与数量不一致
     * @throws IndexOutOfBoundsException index 不在 {@code [0, count)} 内
     * @author gaofeng
     * @date 2026/10/26
     */
    public static int getData(byte[] bytes, int index) {
        int count = validate(bytes);
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("index=" + index + ", Count=" + count);
        }
        return RpByteUtils.bytesToInt(bytes, DATA_OFFSET + (index << RpByteUtils.UNIT_SHIFT));
    }


    /**
     * 功能说明：校验结构并返回数量，只读头部，不创建对象。
     * data 为 null 或长度小于 {@link #DATA_OFFSET} 时返回 0。
     *
     * @param bytes 字节数组
     * @return 数量；null 或长度不足时返回 0
     * @throws IllegalArgumentException 数量为负，或字节长度不等于{@code (2 + count) * 4}
     * @author gaofeng
     * @date 2026/10/26
     */
    private static int validate(byte[] bytes) {
        if (bytes == null || bytes.length < DATA_OFFSET) {
            return 0;
        }
        int count = RpByteUtils.bytesToInt(bytes, UNIT_NUM);
        if (count < 0) {
            throw new IllegalArgumentException("数量不能为负数: " + count);
        }

        long expectedLength = ((long) HEADER_UNITS + count) << RpByteUtils.UNIT_SHIFT;
        if (bytes.length != expectedLength) {
            throw new IllegalArgumentException("数量与字节长度不一致: count=" + count
                    + ", length=" + bytes.length + ", expected=" + expectedLength);
        }
        return count;
    }

    /**
     * 功能说明：校验 ISO-8859-1 编码结构并返回数量，
     * 只读头部，不创建 byte[]。
     *
     * @param encoded ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @return 数量；null 或长度小于 {@link #DATA_OFFSET} 时返回 0
     * @throws IllegalArgumentException 字符长度未对齐、数量为负、
     *                                  字符长度与数量不一致，或字符超出 ISO-8859-1 范围
     * @author gaofeng
     * @date 2026/10/26
     */
    private static int validate(CharSequence encoded) {
        if (encoded == null || encoded.length() < DATA_OFFSET) {
            return 0;
        }
        validateChars(encoded, 0);
        int count = readInt(encoded, UNIT_NUM);
        if (count < 0) {
            throw new IllegalArgumentException("数量不能为负数: " + count);
        }
        long expectedLength = ((long) HEADER_UNITS + count) << UNIT_SHIFT;
        if (encoded.length() != expectedLength) {
            throw new IllegalArgumentException("数量与字符长度不一致: count=" + count
                    + ", length=" + encoded.length() + ", expected=" + expectedLength);
        }
        return count;
    }

    /**
     * 功能说明：按 {@code [daysOffset, count, num...]} 布局编码字节数组。
     *
     * <p>数量超过 {@link #DATA_MAX_NUM} 时会截断到该上限。
     *
     * @param offsetDte 天数偏移
     * @param values    数组，允许为 null 或空数组
     * @return 编码后的字节数组，长度为 {@code (2 + 数量) * 4}
     * @author gaofeng
     * @date 2026/10/26
     */
    public static byte[] intArrToBytes(int offsetDte, int... values) {
        if (values == null || values.length < 1) {
            byte[] bytes = new byte[HEADER_UNITS << RpByteUtils.UNIT_SHIFT];
            RpByteUtils.writeInt(bytes, 0, offsetDte);
            RpByteUtils.writeInt(bytes, UNIT_NUM, 0);
            return bytes;
        }
        if (values.length > DATA_MAX_NUM) {
            values = Arrays.copyOf(values, DATA_MAX_NUM);
        }
        byte[] bytes = new byte[(HEADER_UNITS + values.length) << RpByteUtils.UNIT_SHIFT];
        RpByteUtils.writeInt(bytes, 0, offsetDte);
        RpByteUtils.writeInt(bytes, UNIT_NUM, values.length);
        for (int i = 0; i < values.length; i++) {
            RpByteUtils.writeInt(bytes, DATA_OFFSET + (i << RpByteUtils.UNIT_SHIFT), values[i]);
        }
        return bytes;
    }

    /**
     * 功能说明：扫描全部 unit，判断是否存在 int 值落在 {@code [pf, ps]} 闭区间内。
     *
     * @param bytes 按 {@link #UNIT_NUM} 字节分组的字节数组
     * @param from  区间端点之一
     * @param to    区间端点之一；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 字节长度不是 {@link #UNIT_NUM} 的整数倍
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRange(byte[] bytes, int from, int to) {
        return containsInRange(bytes, 0, from, to);
    }

    /**
     * 功能说明：从 fromByteOffset 开始扫描，判断是否存在 unit 的 int 值
     * 落在 {@code [pf, ps]} 闭区间内。
     *
     * @param bytes          按 {@link #UNIT_NUM} 字节分组的字节数组
     * @param fromByteOffset 起始字节偏移，必须是 {@link #UNIT_NUM} 的整数倍；
     *                       跳过 8 字节头部时传 {@link #DATA_OFFSET}
     * @param from           区间端点之一
     * @param to             区间端点之一；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 字节长度或起始偏移未对齐，或偏移越界
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRange(byte[] bytes, int fromByteOffset, int from, int to) {
        validateBytes(bytes, fromByteOffset);
        int lower = from < to ? from : to;
        int upper = to < from ? from : to;
        for (int offset = fromByteOffset; offset < bytes.length; offset += UNIT_NUM) {
            int value = readInt(bytes, offset);
            if (value >= lower && value <= upper) {
                return true;
            }
        }
        return false;
    }

    /**
     * 功能说明：直接扫描 ISO-8859-1 编码的 CharSequence，判断是否存在 unit 的
     * int 值落在 {@code [pf, ps]} 闭区间内，避免先转成 byte[]。
     *
     * @param encoded ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @param from    区间端点之一
     * @param to      区间端点之一；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     encoded 为 null
     * @throws IllegalArgumentException 字符长度未对齐，或扫描到大于 0xFF 的字符
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRange(CharSequence encoded, int from, int to) {
        return containsInRange(encoded, 0, from, to);
    }

    /**
     * 功能说明：从 fromCharOffset 开始扫描 ISO-8859-1 编码的字符序列，
     * 判断是否存在 unit 的 int 值落在 {@code [pf, ps]} 闭区间内。
     *
     * @param encoded        ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @param fromCharOffset 起始字符偏移，必须是 {@link #UNIT_NUM} 的整数倍
     * @param from           区间端点之一
     * @param to             区间端点之一；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     encoded 为 null
     * @throws IllegalArgumentException 字符长度或起始偏移未对齐、偏移越界，
     *                                  或扫描到大于 0xFF 的字符
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRange(CharSequence encoded, int fromCharOffset, int from, int to) {
        validateChars(encoded, fromCharOffset);
        int lower = from < to ? from : to;
        int upper = to < from ? from : to;
        for (int offset = fromCharOffset; offset < encoded.length(); offset += UNIT_NUM) {
            int value = readInt(encoded, offset);
            if (value >= lower && value <= upper) {
                return true;
            }
        }
        return false;
    }

    /**
     * 功能说明：按 pf/ps 的 4 个字节做无符号字典序比较，判断是否存在 unit
     * 落在 {@code [pf, ps]} 闭区间内，不创建边界 byte[]。
     *
     * <p>业务数据非负，因此不需要翻转符号位；pf、ps 必须非负。
     * 该方法主要用于验证两种思路结果一致；逐文档执行的热路径请使用
     * {@link #containsInRange(byte[], int, int)}。
     *
     * @param bytes 按 {@link #UNIT_NUM} 字节分组的字节数组
     * @param pf    区间端点之一，必须非负
     * @param ps    区间端点之一，必须非负；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 字节长度未对齐，或 pf/ps 为负
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRangeByBytes(byte[] bytes, int pf, int ps) {
        return containsInRangeByBytes(bytes, 0, pf, ps);
    }

    /**
     * 功能说明：从 fromByteOffset 开始，按无符号字节字典序比较，判断是否存在
     * unit 落在 {@code [pf, ps]} 闭区间内；pf、ps 必须非负。
     *
     * @param bytes          按 {@link #UNIT_NUM} 字节分组的字节数组
     * @param fromByteOffset 起始字节偏移，必须是 {@link #UNIT_NUM} 的整数倍
     * @param pf             区间端点之一，必须非负
     * @param ps             区间端点之一，必须非负；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 字节长度或起始偏移未对齐、偏移越界，
     *                                  或 pf/ps 为负
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRangeByBytes(byte[] bytes, int fromByteOffset, int pf, int ps) {
        validateBytes(bytes, fromByteOffset);
        int lower = pf < ps ? pf : ps;
        int upper = pf < ps ? ps : pf;
        validateNonNegativeBounds(lower, pf, ps);

        for (int offset = fromByteOffset; offset < bytes.length; offset += UNIT_NUM) {
            if (compareUnit(bytes, offset, lower) >= 0
                    && compareUnit(bytes, offset, upper) <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 功能说明：直接扫描 ISO-8859-1 编码的 CharSequence，按无符号字节字典序比较，
     * 判断是否存在 unit 落在 {@code [pf, ps]} 闭区间内；不创建边界数组。
     *
     * @param encoded ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @param pf      区间端点之一，必须非负
     * @param ps      区间端点之一，必须非负；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     encoded 为 null
     * @throws IllegalArgumentException 字符长度未对齐、扫描到大于 0xFF 的字符，
     *                                  或 pf/ps 为负
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRangeByBytes(CharSequence encoded, int pf, int ps) {
        return containsInRangeByBytes(encoded, 0, pf, ps);
    }

    /**
     * 功能说明：从 fromCharOffset 开始，直接扫描 ISO-8859-1 编码的 CharSequence，
     * 按无符号字节字典序比较，判断是否存在 unit 落在 {@code [pf, ps]} 闭区间内。
     *
     * @param encoded        ISO-8859-1 编码的字符序列，每个字符应在 0x00-0xFF
     * @param fromCharOffset 起始字符偏移，必须是 {@link #UNIT_NUM} 的整数倍
     * @param pf             区间端点之一，必须非负
     * @param ps             区间端点之一，必须非负；pf、ps 顺序不敏感
     * @return 存在 unit 落在闭区间内返回 true，否则返回 false
     * @throws NullPointerException     encoded 为 null
     * @throws IllegalArgumentException 字符长度或起始偏移未对齐、偏移越界、
     *                                  扫描到大于 0xFF 的字符，或 pf/ps 为负
     * @author gaofeng
     * @date 2026/10/26
     */
    public static boolean containsInRangeByBytes(CharSequence encoded, int fromCharOffset, int pf, int ps) {
        validateChars(encoded, fromCharOffset);
        int lower = pf < ps ? pf : ps;
        int upper = pf < ps ? ps : pf;
        validateNonNegativeBounds(lower, pf, ps);

        for (int offset = fromCharOffset; offset < encoded.length(); offset += UNIT_NUM) {
            if (compareUnit(encoded, offset, lower) >= 0 && compareUnit(encoded, offset, upper) <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * 功能说明：从 byte[] 指定偏移量读取一个大端序 int，不做边界检查。
     *
     * @param bytes  源字节数组
     * @param offset 起始下标
     * @return 读取到的 int
     * @throws NullPointerException           bytes 为 null
     * @throws ArrayIndexOutOfBoundsException offset 越界
     * @author gaofeng
     * @date 2026/10/26
     */
    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);
    }

    /**
     * 功能说明：从 ISO-8859-1 字符序列指定偏移量读取一个大端序 int。
     *
     * <p>包内热路径使用，不分配对象。调用方需保证 offset 已按
     * {@link #UNIT_NUM} 对齐，且数据已通过 {@link #validate(CharSequence)} 校验。
     *
     * @param encoded 字符序列
     * @param offset  起始下标
     * @return 读取到的 int
     * @throws NullPointerException           encoded 为 null
     * @throws ArrayIndexOutOfBoundsException offset 越界
     * @throws IllegalArgumentException       4 个字符中存在大于 0xFF 的字符
     * @author gaofeng
     * @date 2026/10/26
     */
    static int readInt(CharSequence encoded, int offset) {
        int c0 = encoded.charAt(offset);
        int c1 = encoded.charAt(offset + 1);
        int c2 = encoded.charAt(offset + 2);
        int c3 = encoded.charAt(offset + 3);
        if ((c0 | c1 | c2 | c3) > 0xFF) {
            throw new IllegalArgumentException(
                    "位置 " + offset + " 起的 4 个字符超出 ISO-8859-1 范围");
        }
        return (c0 << 24) | (c1 << 16) | (c2 << 8) | c3;
    }

    /**
     * 功能说明：按无符号字节字典序比较 byte[] 中一个 unit 与 int 边界。
     *
     * @param bytes  源字节数组
     * @param offset unit 起始下标
     * @param bound  非负边界值
     * @return 负数表示 unit 小于 bound，0 表示相等，正数表示 unit 大于 bound
     * @throws NullPointerException           bytes 为 null
     * @throws ArrayIndexOutOfBoundsException offset 越界
     * @author gaofeng
     * @date 2026/10/26
     */
    private static int compareUnit(byte[] bytes, int offset, int bound) {
        int a = bytes[offset] & 0xFF;
        int b = (bound >>> 24) & 0xFF;
        if (a != b) {
            return a - b;
        }
        a = bytes[offset + 1] & 0xFF;
        b = (bound >>> 16) & 0xFF;
        if (a != b) {
            return a - b;
        }
        a = bytes[offset + 2] & 0xFF;
        b = (bound >>> 8) & 0xFF;
        if (a != b) {
            return a - b;
        }
        a = bytes[offset + 3] & 0xFF;
        b = bound & 0xFF;
        return a - b;
    }

    /**
     * 功能说明：按无符号字节字典序比较 ISO-8859-1 字符序列中一个 unit 与 int 边界。
     *
     * @param encoded 字符序列
     * @param offset  unit 起始下标
     * @param bound   非负边界值
     * @return 负数表示 unit 小于 bound，0 表示相等，正数表示 unit 大于 bound
     * @throws NullPointerException           encoded 为 null
     * @throws ArrayIndexOutOfBoundsException offset 越界
     * @throws IllegalArgumentException       4 个字符中存在大于 0xFF 的字符
     * @author gaofeng
     * @date 2026/10/26
     */
    private static int compareUnit(CharSequence encoded, int offset, int bound) {
        int c0 = encoded.charAt(offset);
        int c1 = encoded.charAt(offset + 1);
        int c2 = encoded.charAt(offset + 2);
        int c3 = encoded.charAt(offset + 3);
        if ((c0 | c1 | c2 | c3) > 0xFF) {
            throw new IllegalArgumentException(
                    "位置 " + offset + " 起的 4 个字符超出 ISO-8859-1 范围");
        }

        int b0 = (bound >>> 24) & 0xFF;
        if (c0 != b0) {
            return c0 - b0;
        }
        int b1 = (bound >>> 16) & 0xFF;
        if (c1 != b1) {
            return c1 - b1;
        }
        int b2 = (bound >>> 8) & 0xFF;
        if (c2 != b2) {
            return c2 - b2;
        }
        return c3 - (bound & 0xFF);
    }

    /**
     * 功能说明：校验区间下界非负；无返回值。
     *
     * @param lower 归一化后的下界
     * @param pf    原始端点之一
     * @param ps    原始端点之一
     * @throws IllegalArgumentException lower 为负
     * @author gaofeng
     * @date 2026/10/26
     */
    private static void validateNonNegativeBounds(int lower, int pf, int ps) {
        if (lower < 0) {
            throw new IllegalArgumentException("containsInRangeByBytes 仅支持非负边界: pf=" + pf + ", ps=" + ps);
        }
    }

    /**
     * 功能说明：校验 byte[] 长度和起始偏移是否按 {@link #UNIT_NUM} 对齐；无返回值。
     *
     * @param bytes          待校验字节数组
     * @param fromByteOffset 起始字节偏移
     * @throws NullPointerException     bytes 为 null
     * @throws IllegalArgumentException 长度未对齐，或偏移为负、未对齐、越界
     * @author gaofeng
     * @date 2026/10/26
     */
    private static void validateBytes(byte[] bytes, int fromByteOffset) {
        Objects.requireNonNull(bytes, "bytes");
        if ((bytes.length & UNIT_MASK) != 0) {
            throw new IllegalArgumentException(
                    "字节长度必须是 " + UNIT_NUM + " 的整数倍，实际为 " + bytes.length);
        }
        if (fromByteOffset < 0 || (fromByteOffset & UNIT_MASK) != 0
                || fromByteOffset > bytes.length) {
            throw new IllegalArgumentException(
                    "fromByteOffset 必须是 [0, " + bytes.length + "] 内 "
                            + UNIT_NUM + " 的整数倍: " + fromByteOffset);
        }
    }

    /**
     * 功能说明：校验 CharSequence 长度和起始偏移是否按 {@link #UNIT_NUM} 对齐；无返回值。
     *
     * @param encoded        待校验字符序列
     * @param fromCharOffset 起始字符偏移
     * @throws NullPointerException     encoded 为 null
     * @throws IllegalArgumentException 长度未对齐，或偏移为负、未对齐、越界
     * @author gaofeng
     * @date 2026/10/26
     */
    private static void validateChars(CharSequence encoded, int fromCharOffset) {
        Objects.requireNonNull(encoded, "encoded");
        if ((encoded.length() & UNIT_MASK) != 0) {
            throw new IllegalArgumentException(
                    "字符长度必须是 " + UNIT_NUM + " 的整数倍，实际为 " + encoded.length());
        }
        if (fromCharOffset < 0 || (fromCharOffset & UNIT_MASK) != 0
                || fromCharOffset > encoded.length()) {
            throw new IllegalArgumentException(
                    "fromCharOffset 必须是 [0, " + encoded.length() + "] 内 "
                            + UNIT_NUM + " 的整数倍: " + fromCharOffset);
        }
    }

}
