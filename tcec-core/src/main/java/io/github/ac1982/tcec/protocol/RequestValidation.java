package io.github.ac1982.tcec.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.model.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Request-boundary checks, separated from nullable/default-aware wire DTOs.
 * Client calls validate before sending; codec calls it after authenticated request
 * decoding. Business authorization, device state and billing remain with handlers.
 */
public final class RequestValidation {
    private record Rule(List<String> required, List<String> sequences, String nonEmptyList, int maximumListSize) {}
    private static final Map<Class<?>, Rule> RULES = Map.ofEntries(
        rule(QueryTokenRequest.class, "OperatorID OperatorSecret", ""),
        rule(QueryEquipAuthRequest.class, "EquipAuthSeq ConnectorID", "EquipAuthSeq"),
        rule(QueryEquipBusinessPolicyRequest.class, "EquipBizSeq ConnectorID", "EquipBizSeq"),
        rule(QueryStartChargeRequest.class, "StartChargeSeq ConnectorID QRCode", "StartChargeSeq"),
        rule(QueryStopChargeRequest.class, "StartChargeSeq ConnectorID", "StartChargeSeq"),
        rule(QueryEquipChargeStatusRequest.class, "StartChargeSeq", "StartChargeSeq"),
        rule(NotificationStartChargeResultRequest.class, "StartChargeSeq StartChargeSeqStat ConnectorID StartTime", "StartChargeSeq"),
        rule(NotificationStopChargeResultRequest.class, "StartChargeSeq StartChargeSeqStat ConnectorID SuccStat FailReason", ""),
        rule(ChargingSnapshot.class, "StartChargeSeq StartChargeSeqStat ConnectorID ConnectorStatus CurrentA VoltageA Soc StartTime EndTime TotalPower", "StartChargeSeq"),
        rule(ChargeOrderInfo.class, "StartChargeSeq", "StartChargeSeq"),
        rule(CheckChargeOrdersRequest.class, "CheckOrderSeq StartTime EndTime OrderCount TotalOrderPower TotalOrderMoney ChargeOrders", "CheckOrderSeq"),
        Map.entry(QueryStationStatusRequest.class, new Rule(List.of("StationIDs"), List.of(), "StationIDs", 50)),
        rule(ModifyParkingCarNumberRequest.class, "StartChargeSeq CarNumber", ""),
        rule(NotificationUserAuthRequest.class, "Mobile OutUserId", ""),
        rule(QueryConfirmLinkRequest.class, "Mobile OutUserId QRCode", ""),
        rule(QueryOpenLinkRequest.class, "Mobile OutUserId", ""),
        rule(QueryOrderDetailLinkRequest.class, "Mobile OutUserId StartChargeSeq", ""),
        rule(QueryUserOrderRequest.class, "OutUserId", ""),
        rule(QueryParkingFreeInfoRequest.class, "StartChargeSeq CarNumber", ""),
        rule(QueryParkingFreeWaveRequest.class, "StartChargeSeq CarNumber", ""),
        rule(QueryParkingStationRequest.class, "StationID", ""),
        rule(QueryGroundLocksRequest.class, "StationID", ""),
        rule(QueryDropLockRequest.class, "StationID LockNum Lat Lng", ""),
        rule(QueryDropLockResultRequest.class, "StationID LockNum", ""),
        Map.entry(QueryGroundLockAuthRequest.class, new Rule(List.of("Lat", "Lng", "StationIDs"), List.of(), "StationIDs", Integer.MAX_VALUE))
    );
    // The plan contains only immutable metadata, not request values. ClassValue keeps
    // custom endpoint classes unloadable even when they have no built-in validation rule.
    private record Check(Method accessor, boolean required, boolean sequence, int maximumListSize) {}
    private static final ClassValue<List<Check>> CHECKS = new ClassValue<>() {
        @Override protected List<Check> computeValue(Class<?> type) {
            Rule rule = RULES.get(type);
            if (rule == null) return List.of();
            try {
                Map<String, Method> accessors = new HashMap<>();
                for (var component : type.getRecordComponents()) {
                    JsonProperty property = type.getDeclaredField(component.getName()).getAnnotation(JsonProperty.class);
                    if (property != null) accessors.put(property.value(), component.getAccessor());
                }
                var names = new LinkedHashSet<>(rule.required());
                names.addAll(rule.sequences());
                if (rule.nonEmptyList() != null) names.add(rule.nonEmptyList());
                var checks = new ArrayList<Check>(names.size());
                for (String name : names) checks.add(new Check(accessors.get(name), rule.required().contains(name),
                    rule.sequences().contains(name), name.equals(rule.nonEmptyList()) ? rule.maximumListSize() : 0));
                return List.copyOf(checks);
            } catch (ReflectiveOperationException e) { throw invalid(); }
        }
    };
    private RequestValidation() {}
    private static Map.Entry<Class<?>, Rule> rule(Class<?> type, String required, String sequences) {
        return Map.entry(type, new Rule(List.of(required.split(" ")), sequences.isEmpty() ? List.of() : List.of(sequences.split(" ")), null, 0));
    }
    public static void validate(Object request) {
        if (request == null) throw invalid();
        try {
            for (Check check : CHECKS.get(request.getClass())) {
                Object value = check.accessor() == null ? null : check.accessor().invoke(request, (Object[]) null);
                if (check.required() && (value == null || value instanceof String text && text.isBlank())) throw invalid();
                if (check.sequence() && (!(value instanceof String text) || text.length() != 27)) throw invalid();
                if (check.maximumListSize() != 0 && (!(value instanceof List<?> list) || list.isEmpty() || list.size() > check.maximumListSize())) throw invalid();
            }
        } catch (ReflectiveOperationException e) { throw invalid(); }
    }
    private static ProtocolException invalid() { return new ProtocolException(ProtocolException.INVALID_PAYLOAD, "Invalid business request"); }
}
