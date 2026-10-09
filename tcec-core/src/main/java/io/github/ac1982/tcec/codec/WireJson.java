package io.github.ac1982.tcec.codec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.ProtocolException;
import io.github.ac1982.tcec.protocol.Endpoints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Exact case, duplicate-field rejection, no trailing JSON, no scalar coercion. */
public final class WireJson {
    private static final JsonMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
        .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxNumberLength(128).build()).build())
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .withCoercionConfig(LogicalType.Textual, config -> config
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
        .build();
    private static final Set<Class<?>> BUILT_IN_REQUESTS = Endpoints.all().stream()
        .map(endpoint -> (Class<?>) endpoint.requestType()).collect(Collectors.toUnmodifiableSet());

    // ClassValue does not pin application-defined records through a global Class-keyed map.
    // Only metadata is cached: no request nodes or DTO instances enter this cache.
    private static final ClassValue<List<Property>> PROPERTIES = new ClassValue<>() {
        @Override protected List<Property> computeValue(Class<?> type) {
            var properties = new ArrayList<Property>();
            try {
                for (var component : type.getRecordComponents()) {
                    var field = type.getDeclaredField(component.getName());
                    JsonProperty property = field.getAnnotation(JsonProperty.class);
                    if (property == null) continue;
                    String name = property.value().isEmpty() ? component.getName() : property.value();
                    JsonAlias aliases = field.getAnnotation(JsonAlias.class);
                    properties.add(new Property(name, aliases == null ? List.of() : List.of(aliases.value()),
                        valueType(component.getGenericType()), property.defaultValue().isEmpty()
                            ? null : new DefaultValue(property.defaultValue())));
                }
            } catch (ReflectiveOperationException e) { throw new IllegalArgumentException(e); }
            return List.copyOf(properties);
        }
    };
    private record Property(String name, List<String> aliases, ValueType type, DefaultValue defaultValue) {}
    private record ValueType(Class<?> recordType, ValueType listElement) {}
    private static final ValueType SCALAR = new ValueType(null, null);

    /** Privately owned parsed template; mutable containers are copied before every insertion. */
    private static final class DefaultValue {
        private final JsonNode template;
        private final boolean valid;
        private DefaultValue(String json) {
            JsonNode parsed = null;
            boolean parsedSuccessfully = false;
            try {
                parsed = MAPPER.readTree(json);
                if (parsed != null) validateUnicode(parsed);
                parsedSuccessfully = true;
            } catch (RuntimeException ignored) {
                // A malformed annotation matters only when the property is absent. Supplied
                // values (including null and aliases) must retain their original behavior.
            }
            template = parsed;
            valid = parsedSuccessfully;
        }
        private JsonNode copy() {
            if (!valid) throw new IllegalArgumentException();
            return template == null ? null : template.deepCopy();
        }
    }
    private static ValueType valueType(Type type) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == List.class)
            return new ValueType(null, valueType(parameterized.getActualTypeArguments()[0]));
        return type instanceof Class<?> recordType && recordType.isRecord()
            ? new ValueType(recordType, null) : SCALAR;
    }
    private WireJson() {}
    public static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (RuntimeException e) { throw invalid(); }
    }
    public static <T> T read(String json, Class<T> type) {
        try {
            JsonNode root = MAPPER.readTree(json);
            if (root == null || root.isNull()) throw new IllegalArgumentException();
            validateUnicode(root);
            // The real SDK emits this static field for built-in request DTOs. It is inert
            // numeric metadata, accepted at the payload root only and never emitted by us.
            if (root instanceof ObjectNode object && object.has("serialVersionUID")) {
                boolean builtInRequest = BUILT_IN_REQUESTS.contains(type);
                JsonNode metadata = object.get("serialVersionUID");
                if (!builtInRequest || !metadata.isIntegralNumber() || !metadata.canConvertToLong()) throw new IllegalArgumentException();
                object.remove("serialVersionUID");
            }
            if (type.isRecord() && root instanceof ObjectNode object) normalize(object, type);
            T result = MAPPER.treeToValue(root, type);
            if (result == null) throw new IllegalArgumentException();
            return result;
        } catch (RuntimeException e) { throw invalid(); }
    }
    /** Absent defaults are explicit model facts; explicit JSON null is never defaulted. */
    private static void normalize(ObjectNode object, Class<?> recordType) {
        for (Property property : PROPERTIES.get(recordType)) {
            String name = property.name();
            String suppliedName = object.has(name) ? name : null;
            for (String alias : property.aliases()) if (object.has(alias)) {
                if (suppliedName != null) throw new IllegalArgumentException();
                suppliedName = alias;
            }
            if (suppliedName == null && property.defaultValue() != null) {
                object.set(name, property.defaultValue().copy());
                suppliedName = name;
            }
            if (suppliedName != null) normalize(object.get(suppliedName), property.type());
        }
    }
    private static void normalize(JsonNode node, ValueType type) {
        if (node == null || node.isNull() || type == SCALAR) return;
        if (type.listElement() != null) {
            if (node.isArray()) for (JsonNode item : node) normalize(item, type.listElement());
        } else if (node instanceof ObjectNode object) {
            normalize(object, type.recordType());
        }
    }
    // Reject escaped unpaired UTF-16 surrogates before they can be silently replaced
    // by String.getBytes(UTF_8) on a later signing, encryption or transport boundary.
    private static void validateUnicode(JsonNode node) {
        if (node.isString()) {
            validateUnicode(node.asText());
        } else if (node.isObject()) {
            for (var property : node.properties()) {
                validateUnicode(property.getKey());
                validateUnicode(property.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) validateUnicode(item);
        }
    }
    private static void validateUnicode(String text) {
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (++i == text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException();
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException();
            }
        }
    }
    private static ProtocolException invalid() { return new ProtocolException(ProtocolException.INVALID_REQUEST, "Invalid JSON data"); }
}
