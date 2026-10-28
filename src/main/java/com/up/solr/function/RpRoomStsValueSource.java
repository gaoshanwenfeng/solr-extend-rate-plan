package com.up.solr.function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.IntDocValues;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 价格计划（rate plan）字段的自定义 {@link ValueSource}。
 *
 * <p>字段值由 {@link RpByteUtils} 编码为
 * {@code [起始日偏移, 价格数量 N, 每日价格...]}。按入住日偏移与入住晚数取出价格：
 * 数据非法、入住区间超出价格计划、或任一晚价格为 0 时返回 0；
 * 所有晚的价格都不在 {@code [priceFrom, priceTo]} 内时返回 -1；
 * 否则返回第一晚落在价格范围内的价格。
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
     * @param roomNum      房间数
     */
    public RpRoomStsValueSource(ValueSource source, int checkinDateOffset, int days, int roomNum) {
        this.source = source;
        this.checkinDateOffset = checkinDateOffset;
        this.days = days;
        this.roomNum = roomNum;
    }

    /**
     * 逐文档读取字段值并计算匹配到的价格，无法匹配时返回 0 或 -1。
     * 字段 ValueSource 为 null 时，所有文档都返回 0。
     *
     * @param context       函数执行上下文
     * @param readerContext 当前 segment 的读取上下文
     * @return 按文档计算价格结果的 {@link FunctionValues}
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
                // 数据覆盖 [daysOffset, daysOffset + dataCount)，前闭后开区间 入住覆盖
                // [checkinDateOffset, checkinDateOffset + days)，要求两个端点都严格落在数据区间内：
                // checkinDateOffset >= daysOffset 且 checkinDateOffset + days < daysOffset + dataCount。
                if (checkinDateOffset < daysOffset
                        || checkinDateOffset + days > daysOffset + dataCount - 1) {
                    return -1;
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
                // checkinNum > 1：所有的都需要大于0
                final int endOffset = startOffset + (days << RpByteUtils.UNIT_SHIFT);
                int matched = -1;
                for (int offset = startOffset; offset < endOffset; offset += RpByteUtils.UNIT_NUM) {
                    final int val = RpByteUtils.readInt(encoded, offset);
                    if (val == 0) {
                        return 0;
                    }
                    if (val < 0) {
                        return -2;
                    }
                }
                return matched;
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
        return "subPrice(" + sourceDescription + ',' + checkinDateOffset + ',' + days
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
