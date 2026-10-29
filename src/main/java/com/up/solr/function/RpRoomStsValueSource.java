package com.up.solr.function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.IntDocValues;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 房态房量（rate plan room status）字段的自定义 {@link ValueSource}。
 *
 * <p>字段值由 {@link RpByteUtils} 编码为
 * {@code [起始日偏移, 房态数量 N, 每日房态...]}。按入住日偏移与入住晚数取出房态：
 * 数据非法、入住区间超出房态计划、或任一晚房态为 0 时返回 0；
 * 任一晚房态为负时返回 -2；否则返回各晚房态的最小值。
 *
 * <p>{@code roomNum} 不参与取值计算，仅参与 {@link #equals(Object)}、
 * {@link #hashCode()} 与 {@link #description()}；房量阈值过滤由外部
 * {@code frange} 完成。
 *
 * @author gaofeng
 * @date 2026/10/26-12:16
 */
public class RpRoomStsValueSource extends ValueSource {

    private final ValueSource source;
    private final int checkinDateOffset;
    private final int days;
    private final int roomNum;

    /**
     * @param source       字段属性值
     * @param checkinDateOffset 入住日距 {@link RpByteUtils#BASE_DATE} 的天数偏移
     * @param days         入住晚数
     * @param roomNum      房间数，仅参与 equals/hashCode/description，不参与取值计算
     */
    public RpRoomStsValueSource(ValueSource source, int checkinDateOffset, int days, int roomNum) {
        this.source = source;
        this.checkinDateOffset = checkinDateOffset;
        this.days = days;
        this.roomNum = roomNum;
    }

    /**
     * 逐文档读取字段值并计算匹配到的房态，无法匹配时返回 0 或 -2。
     * 字段 ValueSource 为 null 时，所有文档都返回 0。
     *
     * @param context       函数执行上下文
     * @param readerContext 当前 segment 的读取上下文
     * @return 按文档计算房态结果的 {@link FunctionValues}
     * @throws IOException 读取字段值失败
     */
    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException {
        if (source == null) {
            // 没有可读取的字段值，所有文档都视为不匹配
            return new IntDocValues(this) {
                @Override
                public int intVal(int doc) {
                    return 0;
                }
            };
        }
        final FunctionValues sourceValues = source.getValues(context, readerContext);
        return new IntDocValues(this) {
            @Override
            public int intVal(int doc) {
                if (days < 1) {
                    return 0;
                }
                final String encoded;
                try {
                    encoded = sourceValues.strVal(doc);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (encoded == null) {
                    return 0;
                }
                // 数据为空，检索时间范围不在数据范围内，判断为不匹配的数据
                if (encoded.length() < RpByteUtils.DATA_OFFSET) {
                    return 0;
                }
                // 每个文档只校验一次结构，之后按字符偏移直接读取，避免逐晚重复校验
                final int dataCount = RpByteUtils.getDataCount(encoded);
                final int daysOffset = RpByteUtils.readInt(encoded, 0);
                // 数据覆盖 [daysOffset, daysOffset + dataCount)，入住覆盖
                // [checkinDateOffset, checkinDateOffset + days)，要求入住区间完全落在数据区间内：
                // checkinDateOffset >= daysOffset 且 checkinDateOffset + days <= daysOffset + dataCount。
                // 使用 long 计算，避免 checkinDateOffset + days 整数溢出。
                final long stayEnd = (long) checkinDateOffset + days;
                final long dataEnd = (long) daysOffset + dataCount;
                if (checkinDateOffset < daysOffset || stayEnd > dataEnd) {
                    // 入住区间超出房态计划，视为不匹配的数据
                    return 0;
                }
                final int priceIndex = checkinDateOffset - daysOffset;
                final int startOffset = RpByteUtils.DATA_OFFSET
                        + (priceIndex << RpByteUtils.UNIT_SHIFT);
                // checkinNum = 1，直接取该晚价格
                if (days == 1) {
                    final int val = RpByteUtils.readInt(encoded, startOffset);
                    if (val == 0) {
                        return 0;
                    }
                    if (val < 0) {
                        return -2;
                    }
                    return val;
                }
                // checkinNum > 1：所有晚都必须大于 0，返回各晚房态的最小值
                final int endOffset = startOffset + (days << RpByteUtils.UNIT_SHIFT);
                int min = Integer.MAX_VALUE;
                for (int offset = startOffset; offset < endOffset; offset += RpByteUtils.UNIT_NUM) {
                    final int val = RpByteUtils.readInt(encoded, offset);
                    if (val == 0) {
                        return 0;
                    }
                    if (val < 0) {
                        return -2;
                    }
                    if (val < min) {
                        min = val;
                    }
                }
                return min;
            }
        };
    }

    @Override
    public int hashCode() {
        int result = source == null ? 0 : source.hashCode();
        result = 31 * result + checkinDateOffset;
        result = 31 * result + days;
        result = 31 * result + roomNum;
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RpRoomStsValueSource that = (RpRoomStsValueSource) o;
        return checkinDateOffset == that.checkinDateOffset
                && days == that.days
                && roomNum == that.roomNum
                && Objects.equals(source, that.source);
    }

    @Override
    public String description() {
        final String sourceDescription = source == null ? "null" : source.description();
        return "rp_room_sts(" + sourceDescription + ',' + checkinDateOffset + ',' + days
                + ',' + roomNum + ')';
    }

    public ValueSource getSource() {
        return source;
    }

    public int getCheckinDateOffset() {
        return checkinDateOffset;
    }

    public int getDays() {
        return days;
    }

    public int getRoomNum() {
        return roomNum;
    }
}
