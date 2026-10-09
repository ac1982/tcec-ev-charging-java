# API使用说明

SDK 按协议、HTTP 调用、Spring 接收端和可选 Redis 存储分为四个模块。支持的路径和字段见[协议范围](protocol-coverage.md)，接入前须与对端逐项核对。应用只需引入实际使用的模块；Spring接收端的运行期不依赖HTTP客户端模块。

## 端点与路径

- `Endpoints.all()` 返回29个类型化端点，`standard()` 返回15个标准相关端点，`extensions()` 返回14个扩展端点。
- 内置端点使用绝对 `outboundPath`。客户端base URI建议为 `https://host/`，内置路径不会追加到base URI的路径后。
- 普通查询位于 `/evcs/v1/`，令牌及地锁位于 `/evcs/sdk/`，通知与对账位于根路径。
- 自定义 `Endpoint` 可指定相对路径；相对路径基于客户端base URI解析。
- 接收端 `base-path` 是整体部署前缀。例如 `/integration` 对应 `/integration/evcs/v1/query_stations_info`。
- 接收端令牌路由为 `/evcs/sdk/query_token`、`/evcs/v1/query_token` 和 `/query_token`，三个入口共用认证及重放存储。客户端会话默认使用第一个；可通过 `TcecSession` 构造器指定其他类型化令牌端点。

## 模型与字段

- 业务模型使用不可变record和完整规范构造器。列表会防御性复制；无参构造器提供各模型声明的默认值。
- DTO可以表示缺省或显式null字段。`WireJson.read` 只为缺少的字段应用声明默认值，显式null保持null；完整构造器保留调用者传入的值。
- 发送和请求解码时执行 `RequestValidation`。应用处理器还需验证业务权限、设备状态、金额范围、日期范围和订单一致性。
- 金额、电量、费率和坐标使用 `BigDecimal`；标识符使用字符串，保留前导零。
- 当前模型使用 `ServicePrice`、`ServiceMoney`、`DetailServiceMoney` 和 `TotalServiceMoney`。本项目Java访问器使用对应的 `servicePrice()`、`serviceMoney()` 等名称。CEC 原始标准中的 `Sevice*` 字段与当前线格式有差异；当前模型不接受其作为别名。
- `QUERY_EQUIP_CHARGE_STATUS` 返回 `QueryEquipChargeStatusResponse`，其中电费字段为 `ElectMoney`、访问器为 `electMoney()`。充电状态通知 `ChargingSnapshot` 的对应字段为 `ElecMoney`、访问器为 `elecMoney()`。
- JSON字段顺序和无损数字的文本表示不是业务差异。完整字段清单见[协议范围](protocol-coverage.md)。

## 会话与存储

- 按对端和通信方向复用一个 `TcecClient` 和可选的 `TcecSession`；关闭客户端以释放HTTP资源。会话不拥有客户端生命周期。
- `TcecSession` 验证令牌身份、状态和有效期，合并并发刷新；业务POST不会自动重试。
- `TokenStore` 原子复用尚有效的令牌，并返回实际到期时间；重复签发不会延长已有令牌的有效期。
- `ReplayStore.claim` 的第三个参数是已认证报文的SHA-256指纹。实现必须完整保存这个不透明标识，不应截断成四位Seq。重放键不含未被协议MAC覆盖的HTTP路径。
- 不同已认证报文可以使用同一秒和Seq；完全相同报文会被拒绝。业务操作仍需独立幂等标识。
- 集群部署应设置 `require-shared-stores: true`，并显式提供共享的 `TokenStore` 和 `ReplayStore` Bean。默认内存实现仅适用于单进程。

详细认证顺序、TLS要求、错误策略和部署边界见[安全说明](security.md)。
