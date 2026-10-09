# TCEC EV Charging Java

基于 **Java 21 / Spring Boot 4** 的电动汽车充电互联 SDK，提供充电、停车、地锁和 H5 类型化接口，以及加密通信、HTTP 客户端、回调接收端和可替换存储。

包名为 `io.github.ac1982.tcec`。业务执行、设备权限、计费、订单持久化和幂等由应用实现。接入前请按[协议范围](docs/protocol-coverage.md)核对对端版本、路径和字段；本 SDK 不保证与所有 T/CEC 102-2016 实现通用。

## 模块

- `tcec-core`：74 个不可变业务 record、29 个类型化端点、明确的字段默认值、请求边界校验、严格 Jackson 3 JSON、AES-128-CBC / HMAC-MD5、令牌和防重放 SPI
- `tcec-client`：JDK HTTP Client、类型化调用、每对端令牌会话、超时和响应体上限
- `tcec-spring-boot-starter`：Spring Boot **4.1.1** MVC 接收端、运营商注册表、认证与类型化业务处理器
- `tcec-store-redis`：可选 Redis 令牌与防重放实现，不给其他模块强制引入 Redis

金额、电量、坐标、费率使用 `BigDecimal`；ID 使用字符串，保留前导零。

## 构建

需要完整 JDK 21 和 Maven 3.9+：

```sh
mvn --batch-mode --no-transfer-progress clean verify
mvn install
```

当前版本 `1.0.0`，尚未发布到 Maven Central。先从源码安装，再引用所需模块：

```xml
<dependency>
  <groupId>io.github.ac1982</groupId>
  <artifactId>tcec-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

只需客户端时引用 `tcec-client`。集群部署可以另加 `tcec-store-redis`，或实现两个存储 SPI。

## 客户端

密钥从受保护的部署配置读取；按对端及通信方向分别配置，不能提交到仓库。

```java
var keys = new PartnerCredentials(
    System.getenv("TCEC_OPERATOR_ID"), System.getenv("TCEC_OPERATOR_SECRET"),
    System.getenv("TCEC_DATA_SECRET"), System.getenv("TCEC_DATA_IV"),
    System.getenv("TCEC_SIG_SECRET"));

try (var client = new TcecClient(URI.create("https://partner.example/"), keys)) {
    var session = new TcecSession(client);
    var page = session.execute(Endpoints.QUERY_STATIONS_INFO,
        new QueryStationsInfoRequest(null, 1, 10));
}
```

类型来自 `io.github.ac1982.tcec.client`、`.security`、`.protocol`、`.model`；URI 来自 `java.net`。内置端点使用完整路径：普通查询 `/evcs/v1/*`、令牌及地锁 `/evcs/sdk/*`、回调位于根路径。基地址建议只包含 origin；内置绝对路径不会追加到 base URI 的路径后。

若对端回调服务器在 `/query_token` 签发令牌，显式配置会话的令牌端点：

```java
var callbackToken = new Endpoint<>("query_token", QueryTokenRequest.class,
    QueryTokenResponse.class, false, "/query_token");
var callbackSession = new TcecSession(client, callbackToken);
```

按对端复用一个 client/session；会话验证令牌身份、状态和有效期，并合并并发刷新。不自动重放业务 POST。已有令牌系统可以调用 `client.execute(endpoint, request, accessToken)`。

HTTP 200、外层 `Ret=0` 和业务成功是不同层次。应用仍需检查 `SuccStat`、`ConfirmResult` 等业务字段。

## Spring Boot 接收端

```yaml
tcec:
  server:
    enabled: true
    base-path: ""
    protocol-zone: Asia/Shanghai
    allowed-clock-skew: 5m
    token-ttl: 2h
    max-request-bytes: 2097152
    replay-capacity: 100000
    token-capacity: 100000
    require-shared-stores: false
```

注册 `PartnerRegistry` Bean，例如 `new MapPartnerRegistry(List.of(keys))`，再声明实际使用的处理器：

```java
@Bean
TcecEndpointHandler<QueryStationsInfoRequest, QueryStationsInfoResponse> stations(
        StationDirectory directory) {
    return TcecEndpointHandler.of(Endpoints.QUERY_STATIONS_INFO,
        (request, context) -> directory.query(request, context.operatorId()));
}
```

`StationDirectory` 是应用自己的服务。SDK 只内置令牌处理，其余端点没有注册处理器便不会返回业务成功。接收端默认关闭；启用后缺少运营商注册表则启动失败。

默认路由匹配内置端点；令牌接收支持 `/evcs/sdk/query_token`、`/evcs/v1/query_token` 和 `/query_token`，三个入口共用认证及重放存储。`base-path` 是这些路由的整体部署前缀，路径、模型构造和存储契约见 [API 使用说明](docs/api-guide.md)。

## 文档

- [全部端点、模型与线格式](docs/protocol-coverage.md)
- [API 使用说明](docs/api-guide.md)
- [构建、测试与验证边界](docs/testing.md)
- [安全与部署](docs/security.md)
- [Redis 存储配置](tcec-store-redis/README.md)

## 许可证

本项目采用 [MIT License](LICENSE)。第三方依赖按各自许可证分发。
