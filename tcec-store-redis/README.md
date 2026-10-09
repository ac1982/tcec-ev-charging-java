# 可选 Redis 令牌与防重放存储

本模块提供 `TokenStore` / `ReplayStore` 的 Redis 实现，独立于 Spring，由应用显式配置连接。仅添加 `tcec-client` / `tcec-spring-boot-starter` 时不会引入 Redis。

## 显式接入

应用额外声明本模块依赖：

```xml
<dependency>
  <groupId>io.github.ac1982</groupId>
  <artifactId>tcec-store-redis</artifactId>
  <version>1.0.0</version>
</dependency>
```

使用应用管理的、带连接池的 Jedis `RedisClient`。以下是 Spring 配置示例；Redis URI、认证、TLS、连接超时和连接池应由应用部署配置决定。不要把认证信息写进源码、日志或异常响应。

```java
import io.github.ac1982.tcec.redis.RedisReplayStore;
import io.github.ac1982.tcec.redis.RedisTokenStore;
import io.github.ac1982.tcec.security.ReplayStore;
import io.github.ac1982.tcec.security.TokenStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import java.net.URI;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
class TcecRedisConfiguration {
    @Bean(destroyMethod = "close")
    RedisClient tcecRedisClient() {
        var uri = URI.create(System.getenv("TCEC_REDIS_URI"));
        var pool = new GenericObjectPoolConfig<Connection>();
        pool.setMaxTotal(32);
        pool.setMaxWait(Duration.ofSeconds(2));
        return RedisClient.builder().fromURI(uri).poolConfig(pool)
            .clientConfig(DefaultJedisClientConfig.builder(uri)
                .connectionTimeoutMillis(2000).socketTimeoutMillis(2000).build())
            .build();
    }

    @Bean
    TokenStore tokenStore(RedisClient tcecRedisClient) {
        return new RedisTokenStore(tcecRedisClient, "charging-service:inbound:v1");
    }

    @Bean
    ReplayStore replayStore(RedisClient tcecRedisClient) {
        return new RedisReplayStore(tcecRedisClient, "charging-service:inbound:v1");
    }
}
```

集群部署应同时设置 `tcec.server.require-shared-stores=true`，以免缺少自定义 Bean 时悄悄退回单机存储。应用需为连接、命令和连接池等待设置有限超时；本模块复用应用客户端，不修改其连接配置。

同一服务同一凭证方向的所有实例必须使用相同 Redis 数据库和命名空间；不同服务、租户、凭证方向使用不同命名空间。每个命名空间内部再按对端 `operatorId` 隔离。客户端由应用关闭，存储类不会关闭共享客户端。

## 行为与部署约束

- Jedis **8.0.1**，使用新的 `RedisClient` API；要求 Java 21。真实进程测试使用 Redis **7.2.16**，生产目标 Redis 7.2+。当前构造器接入 standalone / 单一 Redis 端点；未声明 Redis Cluster、Sentinel、TLS 或故障切换已经过集成测试。
- 令牌使用 256 位安全随机数、URL-safe Base64。`issue` 单键 Lua 原子返回尚未过期的令牌及原始到期时间，不延长到期；过期后生成新令牌。TTL 必须为整数秒，范围 1～604800 秒。
- 令牌有效期由 Redis `TIME` 和 Redis 到期键控制；`validate` 原子读取有效令牌，在 Java 中进行常量时间比较。存储异常导致 `validate=false`；`issue` 返回不含 Redis 地址、命令参数或底层异常的通用 `ProtocolException`。
- 防重放使用原子 `SET NX PXAT`。重复请求不会延长已有保护；过期记录自动清理。请求指纹由核心验签器在验签通过后生成，不能使用四位 `Seq` 代替。
- 默认最大防重放 TTL 为 **2 天 + 1 秒**，对应核心最大一天双向时钟偏差。可在构造器中设置更小上限（最小 1 毫秒）。过期、超长及非法请求直接拒绝，不截短保护期；Redis 故障或内存拒绝写入也返回 `false`。
- 每次只访问一个键，键由带长度的 SHA-256 摘要组成，避免分隔符碰撞和把操作员/令牌/原始报文直接写入键名。参数长度受限；命名空间只接受 1～64 个 ASCII 字母、数字、冒号、下划线或连字符。
- 必须配置 Redis 内存上限和 **noeviction**，并监控容量与可用性；不能使用会淘汰未过期记录的缓存策略。生产配置属于部署运维职责，本模块不会执行 `CONFIG SET` 或修改服务器设置。
- Redis 是安全状态存储：限制网络访问，使用 ACL 和 TLS，避免可读取令牌的调试命令/命令日志。应用账号需要 `EVAL` 及脚本所用的 `TIME`、`EXISTS`、`HGET`、`HSET`、`PTTL`、`DEL`、`PEXPIREAT`、`SET` 权限，并将键访问限制到服务命名空间。
- Redis 与应用节点必须保持时钟同步。Redis 数据丢失、异步复制回退或恢复旧快照会丢失重放记录/令牌状态；原子操作不等于跨故障零丢失。根据安全要求配置持久化、备份恢复和故障切换，并在丢失重放状态后阻断接入直到原接受时间窗口结束。SDK 不保证 Redis 灾难恢复后的防重放完整性。

## 测试

普通 `mvn verify` 执行输入/命名空间/键编码单元测试，不要求 Redis。真实集成测试显式启用：

```sh
mvn --batch-mode --no-transfer-progress -pl tcec-store-redis -am \
  -Predis-integration -Dredis.server.executable=/absolute/path/to/redis-server verify
```

也可省略 `redis.server.executable`，使用 PATH 上的 `redis-server`。该 profile 不连接外部 Redis：测试创建临时目录、启动只绑定 `127.0.0.1` 的临时进程，分配临时端口，使用 `noeviction` 和 64 MiB 内存上限，关闭持久化；结束后关闭进程。无需 Docker，不创建系统服务、不修改主机设置。缺少可执行文件或启动失败会使测试失败，不会跳过或伪报通过。

真实进程测试覆盖两套独立连接池间并发令牌签发/复用、到期重新签发、对端/命名空间隔离、并发防重放唯一成功、指纹差异、键分隔符碰撞、TTL 清理、重复不延长、超长保护拒绝、真实 `noeviction` 内存耗尽与恢复、连接失败和损坏令牌状态。

没有已安装 Redis 时，可从 [Redis 官方源码下载目录](https://download.redis.io/releases/)取得版本固定的源码，与 [官方 redis-hashes](https://github.com/redis/redis-hashes) 中的 SHA-256 比对，在临时目录运行 `make MALLOC=libc redis-server`。CI 固定使用的 `redis-7.2.16.tar.gz` SHA-256：`960a8ec15e34ff40e57ff16837b26b33bd81f2da6d24497bb63de532a323a18e`。不把下载或编译生成的二进制加入 SDK。

版本/API 来源：[Jedis 8.0.1 正式发布](https://github.com/redis/jedis/releases/tag/v8.0.1)、[JDK 兼容表](https://github.com/redis/jedis#supported-redis-versions)、[新连接 API](https://redis.io/docs/latest/develop/clients/jedis/connect/)、[Redis SET](https://redis.io/docs/latest/commands/set/)、[Redis EVAL](https://redis.io/docs/latest/commands/eval/)。
