package io.github.ac1982.tcec;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.codec.WireJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Intentional strengthening: malformed UTF-16 must not reach lossy UTF-8 boundaries. */
class WireJsonUnicodeTest {
    public record Text(@JsonProperty("Value") String value) {}
    static Stream<String> invalidStrings() {
        return Stream.of("\\uD800", "\\uDBFF", "\\uDC00", "\\uDFFF", "\\uD800a", "a\\uDC00",
            "\\uD800\\uD800", "\\uDC00\\uD800", "\\uD83D\\uDE00\\uD800",
            String.valueOf((char) 0xd800), String.valueOf((char) 0xdc00));
    }
    @ParameterizedTest
    @MethodSource("invalidStrings")
    void rejectsUnpairedSurrogatesInRootAndNestedStringsAndFieldNames(String invalid) {
        for (String json : List.of("\"" + invalid + "\"", "{\"Value\":\"" + invalid + "\"}",
                "{\"outer\":[{\"inner\":\"" + invalid + "\"}]}",
                "{\"" + invalid + "\":1}", "{\"outer\":[{\"" + invalid + "\":1}]}")) {
            ProtocolException error = assertThrows(ProtocolException.class, () -> WireJson.read(json, Object.class));
            assertEquals(ProtocolException.INVALID_REQUEST, error.ret());
            assertEquals("Invalid JSON data", error.getMessage());
            assertNull(error.getCause());
        }
        assertThrows(ProtocolException.class, () -> WireJson.read("{\"Value\":\"" + invalid + "\"}", Text.class));
    }
    static Stream<String> validStrings() {
        return Stream.of("plain ASCII", "充电站", "\\uD83D\\uDE00", "\\uD800\\uDC00", "\\uDBFF\\uDFFF",
            "prefix\\uD83D\\uDE00suffix", "\\\\uD800", "\\\\uDC00", "😀🔌");
    }
    @ParameterizedTest
    @MethodSource("validStrings")
    void acceptsValidUnicodeAndLiteralBackslashEscapes(String encoded) {
        String decoded = WireJson.read("\"" + encoded + "\"", String.class);
        assertEquals(decoded, WireJson.read("{\"Value\":\"" + encoded + "\"}", Text.class).value());
        Object parsed = WireJson.read("{\"" + encoded + "\":[\"" + encoded + "\"]}", Object.class);
        assertEquals(Map.of(decoded, List.of(decoded)), parsed);
        assertEquals(new Text(decoded), WireJson.read(WireJson.write(new Text(decoded)), Text.class));
    }
    public record InvalidDefault(@JsonProperty(value = "Value", defaultValue = "\"\\uD800\"") String value) {}
    @Test void invalidUnicodeDefaultsRejectOnlyWhenAbsent() {
        assertEquals("ok", WireJson.read("{\"Value\":\"ok\"}", InvalidDefault.class).value());
        assertNull(WireJson.read("{\"Value\":null}", InvalidDefault.class).value());
        assertThrows(ProtocolException.class, () -> WireJson.read("{}", InvalidDefault.class));
    }
}
