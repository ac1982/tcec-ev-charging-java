# 构建、测试与验证边界

## 本项目测试

需要完整 JDK 21 和 Maven 3.9+。普通构建验证全部模块的单元测试和HTTP/Spring集成测试：

```sh
mvn --batch-mode --no-transfer-progress clean verify
```

真实Redis测试另需Redis可执行文件，显式开启测试profile；它只决定是否运行测试，不是业务运行模式：

```sh
mvn --batch-mode --no-transfer-progress -Predis-integration \
  -Dredis.server.executable=/absolute/path/to/redis-server clean verify
```

CI下载固定Redis7.2.16官方源码，校验SHA-256后构建，在同一次全量验证中运行Redis集成测试。测试只绑定loopback、启动临时进程并清理，缺少Redis或启动失败会使验证失败，不会跳过。详见[Redis测试说明](../tcec-store-redis/README.md)。

### 模型与协议

- `model-field-contract.json` 描述74个业务record的329个字段、类型和已知默认值。
- `aligned-model-examples.json` 提供全部模型的独立合成全字段样例；逐模型验证字段集合、数字/字符串/列表类型、序列化、反序列化、缺省默认值和显式null。
- `endpoint-examples.json` 覆盖全部29个端点的请求与响应、嵌套内容和加密往返。样例使用合成运营商、订单和保留域名，不调用真实设备。
- `maximal-model-examples.json` 保留独立全字段样例，覆盖设备可选字段、相线电流电压、操作码、非空争议订单和运营商信息。
- 数字保持BigDecimal精度，字符串ID保留前导零；没有按模型字段顺序比较JSON。
- `RequestValidation` 独立测试请求边界：DTO可缺省，发送/解码后的关键请求标识不可缺省，序列长度与站点列表上限必须满足。

### 密码与安全

公开固定向量来自协议附录和公开算法示例，包含明文/密文字节及HMAC；测试密钥不是生产凭证。

测试覆盖Unicode/分组边界/长数据、Ret/Msg/Data/Sig篡改、错误OperatorID、严格Base64、无效密钥、重复键、未知字段、大小写、尾随JSON、非法类型、嵌套与数字长度限制。响应OperatorID仅作为可选一致性元数据；即使缺省或null仍必须验MAC。

已知请求根部的整数serialVersionUID被丢弃；非整数、嵌套、重复或其他未知字段仍拒绝。相同秒/Seq的不同已认证报文可通过，精确重放被原子拒绝，跨令牌路由别名重放同样拒绝。

### 客户端与接收端

- 真正的JDK HTTP Client和嵌入式Boot4 servlet服务器往返
- 全29个内置路径、普通查询/地锁/根回调路由分组、三种令牌接收入口和可配置出站令牌路径
- 缺少关键请求字段时不发业务请求、不进入处理器；未注册端点不生成业务成功
- 令牌获取、缓存、并发合并、旧令牌失败与新令牌竞争、短有效期、身份/状态/有效期错误拒绝
- HTTP错误、禁止重定向、响应大小、完整body超时、UTF-8检查、请求MAC及bearer验证、通用错误响应
- 不自动重试业务POST，包括token错误后的充电动作
- 共享存储配置检查、自定义存储故障、令牌容量、有效期复用、过期拒绝

### Redis

使用真实Redis7.2.16临时进程和多套独立客户端验证原子签发/复用、到期更换、对端/命名空间隔离、并发重放唯一通过、完整指纹、重复不延长TTL、到期清理、超长保护拒绝、连接失败及损坏状态。测试没有生产连接或Docker依赖。

## 尚未验证的边界

仓库测试使用隔离的本地服务与合成数据，不覆盖生产运营商、充电桩、支付或真实停车/地锁/H5系统。通过测试不代表生产认证、全面压测、渗透测试或任意设备组合保证。

Redis真实进程测试覆盖单一Redis端点；不宣称验证Sentinel/Cluster、TLS部署、复制丢失、灾难恢复或故障切换。运行期配置、设备权限、业务幂等、金额计算与账务持久化须由应用独立验证。
