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


## 为什么 int[] → byte[] → String 经过 Solr 存储后还能原样读回

关键在于 Lucene 内部虽然用 UTF-8 存 term/docValues，但 UTF-8 编解码是可逆的，不会改变 Java String
的字符内容：

int[] ──大端 writeInt──> byte[] 0x00~0xFF
──ISO-8859-1───> Java String（每 char = 0x00~0xFF）
──Lucene UTF-8 编码──> 存储字节（中间形式）
──Lucene UTF-8 解码──> 同一个 Java String
──charAt()──────> char 0x00~0xFF，等价于原始 byte

包括 U+0000 也能保留。RpByteUtils.readInt(CharSequence, ...) 里对 > 0xFF
的检查，正好能兜住写侧编码不对的情况（会抛 IllegalArgumentException，而不是静默算错）。

但前提是写侧必须用 ISO-8859-1（Latin-1）生成 String。如果用 new String(bytes)（平台默认编码，通常
UTF-8）或 new String(bytes, UTF_8)，非法字节会变成
U+FFFD，读出来就错了；RpByteUtils.bytesToString/stringToBytes 已经固定用 ISO-8859-1，这是对的。

三、必须满足的 schema 条件

rate_plan_room_sts 的字段定义应类似：

<field name="rate_plan_room_sts" type="string"
indexed="false" stored="true" docValues="true" multiValued="false"/>

┌───────────────────────────┬──────────────────────────────────────────────────────────────────┐
│           条件            │                               原因                               │
├───────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ type="string"（StrField） │ 不能是 text_*；分词、小写化、trim 都会破坏二进制字符串           │
├───────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ multiValued="false"       │ SchemaField.checkFieldCacheSource() 对多值字段直接抛             │
│                           │ BAD_REQUEST: can not use FieldCache on multivalued field         │
├───────────────────────────┼──────────────────────────────────────────────────────────────────┤
│                           │ StrFieldSource 底层是 DocValues.getSorted(...)；没有 docValues   │
│ docValues="true"          │ 时只能靠 indexed=true + uninvertible=true 反索引，堆开销大且在   │
│                           │ Solr 8 已不推荐；两者都没有则查询时直接报错                      │
├───────────────────────────┼──────────────────────────────────────────────────────────────────┤
│ 写入串不能用 trim()       │ 会删掉首尾 <= U+0020 的字符，包括 NUL                            │
└───────────────────────────┴──────────────────────────────────────────────────────────────────┘

只 stored="true" 是不够的：函数查询读的是 docValues/索引，不是 stored fields。

四、容易踩的坑

1. 不要改用 bytesVal(doc, builder)。它返回的是 Lucene 内部的 UTF-8 字节，不是原始字节；对 >= 0x80
   的 byte 会变成两个字节，readInt 会读错。用 strVal 才是正确的。
2. wt=xml / 日志 / fl 返回原始值：NUL 和 0x80–0xFF 在 XML 1.0 下不安全（README 第 11
   节已记录）。用 wt=json/javabin，且不要把原始编码串放进 fl。
3. 长度上限：若字段同时 indexed="true"，字符串作为 term 受 Lucene 单 term 32766 字节限制；UTF-8 下
   0x80–0xFF 字符占 2 字节，3650 天的价格计划最坏接近 29KB，再大就会写入失败。只做函数取值时建议
   indexed="false"。
4. 性能：strVal 每个文档都会 copyUTF8Bytes(...).toString() 分配一个 String。功能正确，但高 QPS 下
   GC 压力不小。若要优化，可以自定义 ValueSource 直接拿 SortedDocValues，复用
   CharsRefBuilder/char[] 逐文档解码，避免 String 分配——但这是优化，不是正确性问题。
5. 确认字段类型：以上结论基于 string（StrField）。如果 rate_plan_room_sts 用的是自定义 FieldType
   或 text 类型，getValueSource 的行为会不同，需要单独确认。

五、顺带提醒：当前 RoomStsValueSource 编译不过

它是从 SubPriceValueSource 复制来的半成品，与读取方式无关，但当前状态编译失败：

- RoomStsValueSource.java:84 引用了未定义的 priceCount
- :99、:112 引用了未定义的 lower/upper
- description() 仍返回 "subPrice(...)"

需要的话我可以按 room_sts 的业务语义把这几个变量补上（比如 roomNum 参与判断），并补测试。