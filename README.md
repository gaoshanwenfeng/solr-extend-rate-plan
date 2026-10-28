# solr-extend-rateplan

Solr 8.0.0 自定义函数（`ValueSourceParser`）插件工程骨架。

当前包含构建环境、字节结构工具（`RpByteUtils`）以及 Solr 函数骨架。

通过自定义函数，改写Field的解析算法，从而实现特定业务场景下的召回实现。


## 1. 环境要求

| 项目 | 版本 / 路径                                                                              |
|------|--------------------------------------------------------------------------------------|
| JDK | 1.8（默认 `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_341.jdk/Contents/Home`） |
| Gradle | 7.6.3（本地环境 工程自带的 `./gradlew`）                                                        |
| Solr | 8.0.0，`solr-8.0.0`                                                                   |
| Maven 本地仓库 | 由`~/.m2/settings.xml` 指定）                                                            |

工程不绑定任何已有 core，安装时按第 5 节指定目标 core 即可。

## 2. 依赖仓库配置

`build.gradle` 中的仓库顺序：

1. `mavenLocal()`：优先使用本地 Maven 仓库（即 `~/.m2/settings.xml` 的 `<localRepository>`）。
2. 阿里云公共仓库：`https://maven.aliyun.com/repository/public`
3. 腾讯云公共镜像：`https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`

依赖说明：

- `org.apache.solr:solr-core:8.0.0` 使用 `compileOnly`，只参与编译，不会打进插件 jar。
- 测试依赖：JUnit 4.13.2、`lucene-queries:8.0.0`。
- 修改 Solr 版本时，请同步修改 `build.gradle` 中的 `solrVersion` 和 `luceneVersion`，两者必须匹配。

## 3. 目录结构

```text
solr_extend/
├── build.gradle                              # 构建、依赖、部署任务
├── settings.gradle
├── gradle.properties                         # 可选：solrInstanceDir
├── gradlew / gradlew.bat
├── gradle/wrapper/                           # Gradle 7.6.3 wrapper
├── src/main/java/com/up/solr/function/
│   ├── RpByteUtils.java                      # byte[]/int/String 互转
│   ├── RpPricesValueSource.java              # 价格值的获取
│   ├── RpPricesValueSourceParser.java        # 价格值的解析
│   ├── RpRoomStsValueSource.java             # 房态值的获取
│   └── RpRoomStsValueSourceParser.java       # 房态值的解析
├── src/test/java/com/up/solr/function/       # 测试工具类
└── README.md
```

## 4. 构建

```bash
# 编译 + 执行测试
gradle clean build

# 只编译
gradle compileJava

# 本地install 
gradle install
```

产物：

```text
build/libs/solr-extend-rate-plan-1.0.0.jar
```

首次构建会从阿里云/腾讯云下载 `solr-core:8.0.0` 及其传递依赖，耗时取决于网络。

## 5. 安装插件 jar

下面以 standalone 模式的 Solr 为例。先设置变量：

```bash
export SOLR_HOME=solr-8.0.0
export CORE=mycore
export INSTANCE_DIR="$SOLR_HOME/server/solr/$CORE"
```

### 5.1 准备 core

如果目标 core 已存在，跳过本步。没有 core 时先创建（默认使用 `_default` configset）：

```bash
"$SOLR_HOME/bin/solr" start
"$SOLR_HOME/bin/solr" create -c "$CORE"
```

创建后 core 的 instanceDir 为：

```text
$SOLR_HOME/server/solr/$CORE
```

### 5.2 复制 jar

```bash
mkdir -p "$INSTANCE_DIR/lib"
cp build/libs/solr-extend-rate-plan-1.0.0.jar "$INSTANCE_DIR/lib/"
```


确认文件已复制


> Solr 8 会自动加载 core 的 `instanceDir/lib` 目录，无需在 `solrconfig.xml` 中添加
> `<lib>` 指令。该目录不存在时，`deployPlugin` 会自动创建。

## 6. 注册函数与声明域值

### 6.1 `solrconfig.xml` 修改配置

配置文件： `$INSTANCE_DIR/conf/solrconfig.xml`
在末尾的 `</config>` 之前有`<valueSourceParser>` 的注释示例加入
```xml
  <!-- 自定义 substr 函数 -->
<valueSourceParser name="rp_prices" class="com.up.solr.function.RpPricesValueSourceParser" />

<valueSourceParser name="rp_room_sts"   class="com.up.solr.function.RpRoomStsValueSourceParser" />

```

说明：

- `name="rp_prices"` 是查询中使用的函数名，不要与 Solr 内置函数重名。 `class` 必须是完整类名，且该类已随 jar 放入 core 的 `lib/` 目录。
- 只需要注册一次；修改 `solrconfig.xml` 后必须重载 core 或重启 Solr。
- 如果使用 SolrCloud，则需要把 jar 放到各节点的 `lib`，并修改对应 configset 的`solrconfig.xml` 后重新上传配置。

### 6.2 `managed-schema`修改配置

配置文件： `$INSTANCE_DIR/conf/managed-schema`

```xml
<field name="rate_plan_prices" type="string" indexed="false" stored="true" multiValued="false" docValues="true"/>

<field name="rate_plan_room_sts" type="string" indexed="false" stored="true" multiValued="false" docValues="true"/>
```

<field name="rate_plan_room_sts" type="string"
indexed="false" stored="true" docValues="true" multiValued="false"/>

说明：

- type="string"（StrField）, 不能是 text_*；分词、小写化、trim 都会破坏二进制字符串
- multiValued="false", SchemaField.checkFieldCacheSource() 对多值字段直接抛,BAD_REQUEST: can not use FieldCache on multivalued field
- docValues="true" ，StrFieldSource 底层是 DocValues.getSorted(...)；没有 docValues时只能靠 indexed=true + uninvertible=true 反索引，堆开销大且在会删掉首尾 <= U+0020 的字符，包括 NUL  


只 stored="true" 是不够的：函数查询读的是 docValues/索引，不是 stored fields。

## 7. 重启或重载 Solr

### 新增 jar 后

新增 jar 到 `lib/` 后，建议重启 Solr，让 core 重新创建类加载器：

```bash
"$SOLR_HOME/bin/solr" restart
```

### 只修改了 solrconfig.xml

Solr 已在运行时，重载目标 core 即可：

```bash
curl "http://localhost:8983/solr/admin/cores?action=RELOAD&core=$CORE"
```

查看状态：

```bash
"$SOLR_HOME/bin/solr" status
```

## 8. 验证安装

### 8.1 函数约定-房价
**rp_prices(field, start, days, priceFrom, priceTo)**

- **field**     域值的名称，在 `managed-schema` 中定义的`rate_plan_prices`
- **start**     业务语义，日期的偏移量
- **days**      业务语义，天数 
- **priceFrom、priceTo** 价格的范围

使用案例  

dq={!frange l=10 u=50}rp_prices(rate_plan_prices, 20753, 10, 10, 50)

dq={!frange l=100 u=500}rp_prices(rate_plan_prices, 20753, 2, 100, 500)

### 8.1 函数约定-房态房量
**rp_room_sts(field, start, days, num)**

- **field**     域值的名称，在 `managed-schema` 中定义的`rate_plan_prices`
- **start**     业务语义，日期的偏移量
- **days**      业务语义，天数
- **num**       业务数量或状态,

使用案例  

dq={!frange l=1 }rp_prices(rp_room_sts, 20753, 2, 1)

dq={!frange l=10 }rp_prices(rp_room_sts, 20753, 2, 10)


## 9. 字节数据结构（`rate_plan_prices`和`rate_plan_room_sts`）

### 9.1 布局

`unitNum = 4`（`UNIT_NUM = 1 << UNIT_SHIFT`，当前 `UNIT_SHIFT = 2`），
每个 int 占 4 字节，大端序：

| 字节下标 | unit | 含义 |
|----------|------|------|
| `bytes[0..3]` | 0 | 距离 `BASE_DATE`（当前 `1970-01-01`）的天数偏移 |
| `bytes[4..7]` | 1 | 价格数量 N |
| `bytes[8..11]` | 2 | 第 1 个价格 |
| `bytes[12..15]` | 3 | 第 2 个价格 |
| ... | ... | ... |

总长度：`(2 + N) * 4` 字节。

- 实现中 `UNIT_NUM` 的乘、除、取模分别改为左移、右移和 `& UNIT_MASK`；
`UNIT_NUM = 1 << UNIT_SHIFT` 保证它一定是 2 的幂。

### 9.2 RpByteUtils 
#### 9.2.1 编码说明
- `byte[] <-> String` 使用 **ISO-8859-1（Latin-1）**，保证 1 字节对应 1 字符。
- 不要用 UTF-8：变长编码，字节数会变化。
- 不要用 `US-ASCII`：只有 7 位，`0x80-0xFF` 会被替换，二进制数据会损坏。
- `stringToBytes` 当前不做严格校验，大于 `0xFF` 的字符会被替换为 `?`；
  如果需要避免静默损坏，调用前应自行检查字符范围。
- 如果需要把该字符串写入 Solr 字段并通过 JSON/XML 传输，控制字符会被转义；
  对传输链路不确定时，建议在字节串外层再做 Base64。

#### 9.2.2 编码过程
```java
int[] values = {347, 512, 0, 423, 56, 234, 461, 128, 35, 542};

LocalDate date = LocalDate.parse(data.getDate().trim());

RpByteUtils.bytesToString(RpByteUtils.encodeRatePlan(date, values));
```

解码
```java
int[] prices = RpByteUtils.bytesToIntArr(RpByteUtils.decodeToBytes(h.getRatePlanPrices()));
int[] roomSts = RpByteUtils.bytesToIntArr(RpByteUtils.decodeToBytes(h.getRatePlanRoomSts()));
```
#### 9.2.3 特殊值语义的定义

- 0 值表示不可用, 业务语义，业态下出现空值, 举例说明 0代表的是某天的价格的缺失、房态房量的缺失。
- 负数越界数据或异常数据

## 10.为什么 int[] → byte[] → String 经过 Solr 存储后还能原样读回

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




## 11.容易踩的坑

1. 不要改用 bytesVal(doc, builder)。它返回的是 Lucene 内部的 UTF-8 字节，不是原始字节；对 >= 0x80
   的 byte 会变成两个字节，readInt 会读错。用 strVal 才是正确的。
2. wt=xml / 日志 / fl 返回原始值：NUL 和 0x80–0xFF 在 XML 1.0 下不安全（README 第 11
   节已记录）。用 wt=json/javabin，且不要把原始编码串放进 fl。
3. 长度上限：若字段同时 indexed="true"，字符串作为 term 受 Lucene 单 term 32766 字节限制；UTF-8 下
   0x80–0xFF 字符占 2 字节，3650 天的价格计划最坏接近 29KB，再大就会写入失败。只做函数取值时建议
   indexed="false"。
4. 确认字段类型：以上结论基于 string（StrField）。如果 rate_plan_room_sts 用的是自定义 FieldType
   或 text 类型，getValueSource 的行为会不同，需要单独确认。
