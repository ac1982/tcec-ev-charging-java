# 功能与线格式范围

SDK 直接提供29个端点：15项充电标准相关能力、4项停车、4项地锁、4项H5查询和2项H5回调。全部使用同一 `Endpoint<Q,R>`、客户端和接收认证链路；`Endpoints.standard()`、`extensions()` 仅用于列举，`all()` 返回完整集合。

## 接入契约边界

当前业务字段与路由适用于小桔 1.4.0 接入契约，并非所有 T/CEC 102-2016 实现的通用线格式。CEC 原始第 3 部分表 5、11、12、13、19 使用 `SevicePrice`、`SeviceMoney`、`DetailSeviceMoney`、`TotalSeviceMoney` 等拼写；本 SDK 对应字段使用 `Service*`，充电状态查询与通知的电费字段也有区别。本文描述本 SDK 实际接受的契约，不代表标准认证。

`Endpoints.standard()` 是标准相关能力的分组名称，不表示其中每个线字段与CEC原始文本完全一致。对接前必须逐项核对字段；本 SDK 不自动映射不同版本或不同拼写的线字段。

## 端点

| 常量 | HTTP 路径 | 请求 → 响应 |
|---|---|---|
| `QUERY_TOKEN` | `/evcs/sdk/query_token` | `QueryTokenRequest` → `QueryTokenResponse` |
| `QUERY_STATIONS_INFO` | `/evcs/v1/query_stations_info` | `QueryStationsInfoRequest` → `QueryStationsInfoResponse` |
| `NOTIFICATION_STATION_STATUS` | `/notification_stationStatus` | `NotificationStationStatusRequest` → `NotificationStationStatusResponse` |
| `QUERY_STATION_STATUS` | `/evcs/v1/query_station_status` | `QueryStationStatusRequest` → `QueryStationStatusResponse` |
| `QUERY_STATION_STATS` | `/evcs/v1/query_station_stats` | `QueryStationStatsRequest` → `QueryStationStatsResponse` |
| `QUERY_EQUIP_AUTH` | `/evcs/v1/query_equip_auth` | `QueryEquipAuthRequest` → `QueryEquipAuthResponse` |
| `QUERY_EQUIP_BUSINESS_POLICY` | `/evcs/v1/query_equip_business_policy` | `QueryEquipBusinessPolicyRequest` → `QueryEquipBusinessPolicyResponse` |
| `QUERY_START_CHARGE` | `/evcs/v1/query_start_charge` | `QueryStartChargeRequest` → `QueryStartChargeResponse` |
| `NOTIFICATION_START_CHARGE_RESULT` | `/notification_start_charge_result` | `NotificationStartChargeResultRequest` → `NotificationStartChargeResultResponse` |
| `QUERY_EQUIP_CHARGE_STATUS` | `/evcs/v1/query_equip_charge_status` | `QueryEquipChargeStatusRequest` → `QueryEquipChargeStatusResponse` |
| `NOTIFICATION_EQUIP_CHARGE_STATUS` | `/notification_equip_charge_status` | `ChargingSnapshot` → `NotificationEquipChargeStatusResponse` |
| `QUERY_STOP_CHARGE` | `/evcs/v1/query_stop_charge` | `QueryStopChargeRequest` → `QueryStopChargeResponse` |
| `NOTIFICATION_STOP_CHARGE_RESULT` | `/notification_stop_charge_result` | `NotificationStopChargeResultRequest` → `NotificationStopChargeResultResponse` |
| `NOTIFICATION_CHARGE_ORDER_INFO` | `/notification_charge_order_info` | `ChargeOrderInfo` → `NotificationChargeOrderInfoResponse` |
| `CHECK_CHARGE_ORDERS` | `/check_charge_orders` | `CheckChargeOrdersRequest` → `CheckChargeOrdersResponse` |
| `QUERY_OPEN_LINK` | `/evcs/v1/query_open_link` | `QueryOpenLinkRequest` → `QueryOpenLinkResponse` |
| `QUERY_GROUND_LOCKS` | `/evcs/sdk/query_ground_locks` | `QueryGroundLocksRequest` → `QueryGroundLocksResponse` |
| `QUERY_PARKING_FREE_INFO` | `/evcs/v1/query_parking_free_info` | `QueryParkingFreeInfoRequest` → `QueryParkingFreeInfoResponse` |
| `QUERY_CONFIRM_LINK` | `/evcs/v1/query_confirm_link` | `QueryConfirmLinkRequest` → `QueryConfirmLinkResponse` |
| `NOTIFICATION_USER_AUTH` | `/notification_user_auth` | `NotificationUserAuthRequest` → `NotificationUserAuthResponse` |
| `QUERY_PARKING_STATION` | `/evcs/v1/query_parking_station` | `QueryParkingStationRequest` → `QueryParkingStationResponse` |
| `QUERY_PARKING_FREE_WAVE` | `/evcs/v1/query_parking_free_wave` | `QueryParkingFreeWaveRequest` → `QueryParkingFreeWaveResponse` |
| `MODIFY_PARKING_FEE_WAVE_CAR_NUMBER` | `/evcs/v1/modify_parking_fee_wave_car_number` | `ModifyParkingCarNumberRequest` → `ModifyParkingCarNumberResponse` |
| `QUERY_GROUND_LOCK_AUTH` | `/evcs/sdk/query_ground_lock_auth` | `QueryGroundLockAuthRequest` → `QueryGroundLockAuthResponse` |
| `NOTIFICATION_USER_ORDER_INFO` | `/notification_user_order_info` | `NotificationUserOrderInfoRequest` → `NotificationUserOrderInfoResponse` |
| `QUERY_USER_ORDER` | `/evcs/v1/query_user_order` | `QueryUserOrderRequest` → `QueryUserOrderResponse` |
| `QUERY_ORDER_DETAIL_LINK` | `/evcs/v1/query_order_detail_link` | `QueryOrderDetailLinkRequest` → `QueryOrderDetailLinkResponse` |
| `QUERY_DROP_LOCK` | `/evcs/sdk/query_drop_lock` | `QueryDropLockRequest` → `QueryDropLockResponse` |
| `QUERY_DROP_LOCK_RESULT` | `/evcs/sdk/query_drop_lock_result` | `QueryDropLockResultRequest` → `QueryDropLockResultResponse` |

只有令牌属于内置服务。站点目录、停车减免、落锁、H5链接生成、用户认证、充电动作和订单结果均由应用处理器完成。未注册端点不执行业务。

## 模型与默认值

74个不可变业务模型覆盖329个对应的业务字段；两种信封另计。字段契约与全字段合成样例保存在测试资源中。数量表示字段覆盖，不能代替业务路径或生产兼容性覆盖率。

- CEC原始文本中的 `SevicePrice/SeviceMoney/DetailSeviceMoney/TotalSeviceMoney` 与当前对应模型的线字段有差异，不作为这些模型的线格式别名接受，收到会拒绝。
- 当前对应模型的精确大小写和拼写：`ServicePrice`、`ServiceMoney`、`DetailServiceMoney`、`TotalServiceMoney`、`BusineHours`。
- 查询充电状态使用 `QueryEquipChargeStatusResponse.ElectMoney`；状态通知使用 `ChargingSnapshot.ElecMoney`。两者独立建模，不能统一重命名。
- 车牌、站点扩展、停车、地锁及H5数据采用明确字段和嵌套模型，没有任意未知字段收集器。
- 金额、坐标等JSON数字用 `BigDecimal`，保持十进制精度，不进行隐式舍入。
- `WireJson.read` 只对缺省字段填充已声明默认值，例如分页1/10、设备类型5、坐标0.0；显式 `null` 保持为空，写出时省略。无参构造器提供同样的已声明默认值，全参数构造器保留传入值。
- 数组防御性复制。DTO负责结构；`RequestValidation` 在客户端发送前和认证后的请求解码边界检查所需业务标识、27位序列、站点列表非空及50项上限。设备状态、权限、日期业务规则、金额范围与订单一致性由业务应用继续验证。
- 字符串ID保留前导零；数字/布尔不能悄然转换成字符串，浮点不能转换成整数。

## 信封、元数据与认证

请求必含 `OperatorID/Data/TimeStamp/Seq/Sig`。响应必含 `Ret/Msg/Data/Sig`，可附带 `OperatorID`：缺省/null视为未提供，非null值必须匹配预配置身份，但它不在响应MAC覆盖范围内，不能用于选择凭证或授予信任。

唯一附加元数据规则：已知内置请求DTO根对象的 `serialVersionUID` 可为64位整数，会被丢弃且不会由本SDK输出。字符串、浮点、嵌套字段、重复字段、任意其他未知字段仍然拒绝。没有全局忽略未知字段开关。

加密使用AES-128/CBC/PKCS5Padding、规范Base64和HMAC-MD5。请求签名输入为 `OperatorID+Data+TimeStamp+Seq`；响应签名输入为十进制 `Ret+Msg+Data`，规则见下方公开服务端协议与算法示例。UTF-8，无分隔符，摘要为32位大写十六进制。先验签再解密。

## 明确边界

- 站点状态通知路径为 `/notification_stationStatus`。
- 停车减免查询 `QUERY_PARKING_FREE_INFO` 返回 `QueryParkingFreeInfoResponse`。
- 不实现实际桩驱动、支付结算、运营商生产配置、后续协议版本或未公开接口；不声称通过生产认证、所有设备组合、压测或渗透测试。

## 公开参考

- [小桔SDK文档](https://open-energy.xiaojukeji.com/home/document/11/56)
- [服务端协议与算法示例](https://open-energy.xiaojukeji.com/home/document/11/14)
- CEC原发布链接：[第1部分](https://www.cec.org.cn/upload/1/editor/1594101379357.pdf)、[第2部分](https://www.cec.org.cn/upload/1/editor/1594102732908.pdf)、[第3部分](https://www.cec.org.cn/upload/1/editor/1594102826531.pdf)、[第4部分](https://www.cec.org.cn/upload/1/editor/1594103338207.pdf)

测试使用公开密码向量和合成业务数据；公开向量在测试资源中保留来源说明。
