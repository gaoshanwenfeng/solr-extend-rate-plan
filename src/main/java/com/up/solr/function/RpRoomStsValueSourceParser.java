package com.up.solr.function;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

/**
 * 房态房量函数的解析器。
 *
 * <p>函数签名：{@code rp_room_sts(field, start, days)}。其中 {@code start} 为入住日距
 * {@link RpByteUtils#BASE_DATE} 的天数偏移，{@code days} 为入住晚数。
 * 房量阈值由外部 {@code frange} 对函数返回值完成过滤。
 *
 * <p>在 solrconfig.xml 中注册：
 * <pre>
 *   &lt;valueSourceParser name="rp_room_sts"
 *       class="com.up.solr.function.RpRoomStsValueSourceParser"/&gt;
 * </pre>
 *
 * @author gaofeng
 */
public class RpRoomStsValueSourceParser extends ValueSourceParser {
    final String DEFAULT_RP_ROOM_STS = "rate_plan_room_sts";

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        //第一个参数
        String fieldStr = fp.parseArg();
        int checkinDateOffset = fp.parseInt();
        int days = fp.parseInt();
        //将参数及需要的文档的值传给自定义的ValueSource方法，打分规则在自定义的ValueSource中定制
        return new RpRoomStsValueSource(getValueSource(fp, fieldStr), checkinDateOffset, days);
    }

    /**
     * 该方法是根据字段名，从FunctionQParser得到文档该字段的相关信息
     */
    public ValueSource getValueSource(FunctionQParser fp, String arg) {
        if (arg == null) {
            arg = DEFAULT_RP_ROOM_STS;
        }
        SchemaField f = fp.getReq().getSchema().getField(arg);
        return f.getType().getValueSource(f, fp);
    }
}
