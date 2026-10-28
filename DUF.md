# Solr 自定义函数
Solr 自定义函数主要通过实现 ValueSourceParser 和 ValueSource 两个类来完成，用于在查询时动态计算字段值、评分或排序依据。

## 1. Java 代码实现
   ‌步骤一：创建 ValueSourceParser（解析器入口）‌
   负责从查询语句中解析参数，并实例化具体的 ValueSource。

```java
package com.example.solr.function;

import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;
import org.apache.lucene.queries.function.ValueSource;

public class CustomFuncParser extends ValueSourceParser {
@Override
public ValueSource parse(FunctionQParser fp) throws SyntaxError {
// 解析第一个参数，通常是字段名
String fieldName = fp.parseArg();
// 如果有更多参数，继续调用 fp.parseArg()
return new CustomValueSource(fieldName);
}
}
```

‌步骤二：创建 ValueSource（核心逻辑）‌
负责根据文档 ID 获取具体值并进行计算。以下示例演示如何读取一个字符串字段并返回其长度作为数值。
```java

package com.example.solr.function;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.IntDocValues;

import java.io.IOException;
import java.util.Map;

public class CustomValueSource extends ValueSource {
private final String fieldName;

    public CustomValueSource(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) throws IOException {
        // 获取底层字段的 Values 对象
        // 注意：实际生产中建议通过 SchemaField 获取更稳健的 ValueSource
        final ValueSource source = getValueSourceFromSchema(context, fieldName);         final FunctionValues vals = source.getValues(context, readerContext);

        return new IntDocValues(this) {
            @Override
            public int intVal(int doc) {
                String str = vals.strVal(doc);
                if (str == null) return 0;
                return str.length(); // 自定义逻辑：返回字符串长度
            }
        };
    }
    
    // 辅助方法：从 Schema 获取基础 ValueSource
    private ValueSource getValueSourceFromSchema(LeafReaderContext context, String field) {
         // 简化处理，实际需通过 SolrIndexSearcher 或 FunctionQParser 上下文获取 SchemaField
         // 此处仅为示意结构，具体获取方式依赖 Solr 版本内部 API
         throw new UnsupportedOperationException("需结合 FunctionQParser 上下文实现");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomValueSource)) return false;
        CustomValueSource other = (CustomValueSource) o;
        return this.fieldName.equals(other.fieldName);
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }
        @Override
    public String description() {
        return "customFunc(" + fieldName + ")";
    }
}
```

## 2. 配置 solrconfig.xml
   将编译好的 JAR 包放入 Solr 的 lib 目录（如 server/solr-webapp/webapp/WEB-INF/lib/ 或配置 <lib> 指向的目录），并在对应 Core 的 solrconfig.xml 中注册：

xml
<!-- 加载包含自定义类的 JAR 包 -->
<lib dir="${solr.install.dir:../../../..}/contrib/custom/lib/" regex=".*\.jar" />

<!-- 注册自定义函数，name 为查询时使用的函数名 -->
<valueSourceParser name="myCustomLen" class="com.example.solr.function.CustomFuncParser" />

## 3. 使用方式
重启 Solr 后，即可在查询中使用该函数：

‌作为排序依据：‌
http://localhost:8983/solr/core/select?q=*:*&sort=myCustomLen(title) desc
‌作为过滤条件：‌
http://localhost:8983/solr/core/select?q=*:*&fq={!frange l=10}myCustomLen(title)
‌作为返回字段（伪字段）：‌
http://localhost:8983/solr/core/select?q=*:*&fl=id,title,_val_:myCustomLen(title)


