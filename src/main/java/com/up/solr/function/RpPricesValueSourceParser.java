package com.up.solr.function;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

/**
 * 价格函数的解析器。
 * start 从 0 开始，length 省略时截取到字符串末尾。
 *
 * <p>在 solrconfig.xml 中注册：
 * <pre>
 *   &lt;valueSourceParser name="rp_prices"
 *       class="com.up.solr.function.RpPricesValueSourceParser"/&gt;
 * </pre>
 *
 * @author gaofeng
 */
public class RpPricesValueSourceParser extends ValueSourceParser {
    final String DEFAULT_RP_PRICES = "rate_plan_prices";

    @Override
    public ValueSource parse(FunctionQParser fp) throws SyntaxError {
        //第一个参数
        String fieldStr = fp.parseArg();
        int checkinDateOffset = fp.parseInt();
        int dateNum = fp.parseInt();
        int priceFrom = fp.parseInt();
        int priceTo = fp.parseInt();
        //将参数及需要的文档的值传给自定义的ValueSource方法，打分规则在自定义的ValueSource中定制
        return new RpPricesValueSource(getValueSource(fp, fieldStr), checkinDateOffset, dateNum, priceFrom, priceTo);
    }

    /**
     * 该方法是根据字段名，从FunctionQParser得到文档该字段的相关信息
     */
    public ValueSource getValueSource(FunctionQParser fp, String arg) {
        if (arg == null) {
            arg = DEFAULT_RP_PRICES;
        }
        SchemaField f = fp.getReq().getSchema().getField(arg);
        return f.getType().getValueSource(f, fp);
    }
}
