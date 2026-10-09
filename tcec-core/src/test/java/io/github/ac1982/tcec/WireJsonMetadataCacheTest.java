package io.github.ac1982.tcec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ac1982.tcec.codec.WireJson;
import io.github.ac1982.tcec.model.QueryStationsInfoRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Cache regressions use custom records so built-in model changes cannot mask alias/default bugs. */
class WireJsonMetadataCacheTest {
    private static final BigDecimal AMOUNT = new BigDecimal("0.12340000000000000001");
    private static final String TREE_DEFAULT = "{\"items\":[{\"value\":1}]}";
    private static final String ROWS_DEFAULT = "[{\"LegacyLeaf\":{}}]";

    public record Leaf(@JsonProperty(value = "Amount", defaultValue = "0.12340000000000000001") BigDecimal amount) {}
    public record Branch(@JsonProperty(value = "Leaf", defaultValue = "{}") @JsonAlias({"LegacyLeaf", "OldLeaf"}) Leaf leaf) {}
    public record Defaults(
        @JsonProperty(value = "Rows", defaultValue = ROWS_DEFAULT) @JsonAlias({"LegacyRows", "OldRows"}) List<Branch> rows,
        @JsonProperty(value = "Tree", defaultValue = TREE_DEFAULT) ObjectNode tree,
        @JsonProperty(value = "Matrix", defaultValue = "[[{}]]") List<List<Leaf>> matrix,
        @JsonProperty(value = "Flag", defaultValue = "true") Boolean flag,
        @JsonProperty(value = "Nothing", defaultValue = "null") String nothing) {}
    public record MalformedDefault(
        @JsonProperty(value = "Value", defaultValue = "{broken") @JsonAlias("LegacyValue") String value) {}
    public record WhitespaceDefault(@JsonProperty(value = "Value", defaultValue = " ") String value) {}
    public record DuplicateDefault(@JsonProperty(value = "Value", defaultValue = "{\"x\":1,\"x\":2}") ObjectNode value) {}
    public record TrailingDefault(@JsonProperty(value = "Value", defaultValue = "1 2") Integer value) {}
    public record Recursive(@JsonProperty("Next") Recursive next, @JsonProperty(value = "Count", defaultValue = "3") Integer count) {}
    public record Unannotated(Leaf leaf) {}
    public record Wildcard(@JsonProperty("Leaves") List<? extends Leaf> leaves) {}
    public record Generic<T>(@JsonProperty("Item") T item) {}
    @SuppressWarnings("rawtypes")
    public record GenericHolder(@JsonProperty("Generic") Generic<Leaf> generic, @JsonProperty("Raw") List raw) {}
    public record EmptyProperty(@JsonProperty(defaultValue = "7") Integer count) {}

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"LegacyRows\":[{\"LegacyLeaf\":{}}]}", "{\"OldRows\":[{\"OldLeaf\":{}}]}"})
    void nestedDefaultsAliasesAndMutableContainersAreIsolated(String json) {
        for (int repeat = 0; repeat < 5; repeat++) {
            Defaults first = WireJson.read(json, Defaults.class);
            assertEquals(AMOUNT, first.rows().getFirst().leaf().amount());
            assertEquals(AMOUNT, first.matrix().getFirst().getFirst().amount());
            assertTrue(first.flag());
            assertNull(first.nothing());
            assertEquals(1, first.tree().get("items").get(0).get("value").intValue());
            first.rows().clear();
            first.matrix().getFirst().clear();
            ((ObjectNode) first.tree().get("items").get(0)).put("value", 999);
            ((ArrayNode) first.tree().get("items")).add(123);
            Defaults next = WireJson.read(json, Defaults.class);
            assertEquals(1, next.rows().size());
            assertEquals(1, next.matrix().getFirst().size());
            assertEquals(1, next.tree().get("items").size());
            assertEquals(1, next.tree().get("items").get(0).get("value").intValue());
            assertNotSame(first.tree(), next.tree());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"Rows\":null,\"Tree\":null,\"Matrix\":null,\"Flag\":null}",
        "{\"LegacyRows\":null,\"Tree\":null,\"Matrix\":null,\"Flag\":null}",
        "{\"OldRows\":null,\"Tree\":null,\"Matrix\":null,\"Flag\":null}"
    })
    void explicitNullDoesNotReadCachedDefaults(String json) {
        for (int repeat = 0; repeat < 3; repeat++) {
            Defaults parsed = WireJson.read(json, Defaults.class);
            assertNull(parsed.rows()); assertNull(parsed.tree()); assertNull(parsed.matrix()); assertNull(parsed.flag());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"Rows\":null,\"LegacyRows\":null}", "{\"LegacyRows\":[],\"OldRows\":[]}",
        "{\"Rows\":[{\"Leaf\":{},\"LegacyLeaf\":{}}]}",
        "{\"Rows\":[{\"OldLeaf\":null,\"LegacyLeaf\":null}]}",
        "{\"Rows\":[],\"Rows\":[]}", "{\"rows\":[]}", "{\"Unknown\":1}", "{} {}",
        "{\"Rows\":{}}", "{\"Rows\":[{\"Leaf\":{\"Amount\":\"1\"}}]}",
        "{\"Rows\":[{\"Leaf\":{\"Amount\":true}}]}", "{\"Matrix\":[[1]]}",
        "{\"Rows\":[{\"Leaf\":{\"serialVersionUID\":1}}]}", "{\"serialVersionUID\":1}"
    })
    void warmedCacheStillRejectsStrictJsonViolations(String json) {
        WireJson.read("{}", Defaults.class);
        for (int repeat = 0; repeat < 3; repeat++) assertInvalidJson(json, Defaults.class);
    }

    @Test void malformedDefaultsFailOnlyWhenTheyAreActuallyNeeded() {
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals("supplied", WireJson.read("{\"Value\":\"supplied\"}", MalformedDefault.class).value());
            assertEquals("alias", WireJson.read("{\"LegacyValue\":\"alias\"}", MalformedDefault.class).value());
            assertNull(WireJson.read("{\"Value\":null}", MalformedDefault.class).value());
            assertInvalidJson("{}", MalformedDefault.class);
            assertNull(WireJson.read("{\"Value\":null}", DuplicateDefault.class).value());
            assertInvalidJson("{}", DuplicateDefault.class);
            assertNull(WireJson.read("{\"Value\":null}", WhitespaceDefault.class).value());
            assertEquals("supplied", WireJson.read("{\"Value\":\"supplied\"}", WhitespaceDefault.class).value());
            assertInvalidJson("{}", WhitespaceDefault.class);
            assertEquals(8, WireJson.read("{\"Value\":8}", TrailingDefault.class).value());
            assertInvalidJson("{}", TrailingDefault.class);
        }
    }

    @Test void recursiveAndUnannotatedTypesKeepTheirNormalizationScope() {
        Recursive recursive = WireJson.read("{\"Next\":{\"Next\":{}}}", Recursive.class);
        assertEquals(3, recursive.count()); assertEquals(3, recursive.next().count()); assertEquals(3, recursive.next().next().count());
        assertNull(recursive.next().next().next());
        assertNull(WireJson.read("{\"leaf\":{}}", Unannotated.class).leaf().amount());
        assertNull(WireJson.read("{\"Leaves\":[{}]}", Wildcard.class).leaves().getFirst().amount());
        assertEquals(7, WireJson.read("{}", EmptyProperty.class).count());
        GenericHolder generic = WireJson.read("{\"Generic\":{\"Item\":{}},\"Raw\":[{}]}", GenericHolder.class);
        assertNull(generic.generic().item().amount());
        assertEquals(List.of(java.util.Map.of()), generic.raw());
        assertEquals(new BigDecimal("1234567890123456789.01234567890123456789"),
            WireJson.read("{\"Amount\":1234567890123456789.01234567890123456789}", Leaf.class).amount());
    }

    // Each parameter has its own record, so every run races the first cache publication.
    public interface ConcurrentDefaults { List<Branch> rows(); ObjectNode tree(); }
    public record ColdOne(
        @JsonProperty(value = "Rows", defaultValue = ROWS_DEFAULT) @JsonAlias("LegacyRows") List<Branch> rows,
        @JsonProperty(value = "Tree", defaultValue = TREE_DEFAULT) ObjectNode tree) implements ConcurrentDefaults {}
    public record ColdTwo(
        @JsonProperty(value = "Rows", defaultValue = ROWS_DEFAULT) @JsonAlias("LegacyRows") List<Branch> rows,
        @JsonProperty(value = "Tree", defaultValue = TREE_DEFAULT) ObjectNode tree) implements ConcurrentDefaults {}
    public record ColdThree(
        @JsonProperty(value = "Rows", defaultValue = ROWS_DEFAULT) @JsonAlias("LegacyRows") List<Branch> rows,
        @JsonProperty(value = "Tree", defaultValue = TREE_DEFAULT) ObjectNode tree) implements ConcurrentDefaults {}
    static Stream<Arguments> concurrentTypes() {
        return Stream.of(Arguments.of(ColdOne.class, 2), Arguments.of(ColdTwo.class, 8), Arguments.of(ColdThree.class, 24));
    }

    @ParameterizedTest
    @MethodSource("concurrentTypes")
    void firstUseMetadataAndDefaultTreesAreSafeAcrossThreads(Class<? extends ConcurrentDefaults> type, int threads) throws Exception {
        var ready = new CountDownLatch(threads);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < threads; i++) work.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                for (int repeat = 0; repeat < 40; repeat++) {
                    String json = repeat % 2 == 0 ? "{}" : "{\"LegacyRows\":[{\"OldLeaf\":{}}]}";
                    ConcurrentDefaults value = WireJson.read(json, type);
                    assertEquals(AMOUNT, value.rows().getFirst().leaf().amount());
                    assertEquals(1, value.tree().get("items").size());
                    assertEquals(1, value.tree().get("items").get(0).get("value").intValue());
                    value.rows().clear();
                    ((ObjectNode) value.tree().get("items").get(0)).put("value", repeat);
                    ((ArrayNode) value.tree().get("items")).add(999);
                    assertInvalidJson("{\"Rows\":null,\"LegacyRows\":null}", type);
                    assertNull(WireJson.read("{\"LegacyRows\":null}", type).rows());
                    QueryStationsInfoRequest builtIn = WireJson.read("{\"serialVersionUID\":9223372036854775807}", QueryStationsInfoRequest.class);
                    assertEquals(1, builtIn.pageNo()); assertEquals(10, builtIn.pageSize());
                    assertInvalidJson("{\"serialVersionUID\":9223372036854775808}", QueryStationsInfoRequest.class);
                }
                return null;
            }));
            try { assertTrue(ready.await(10, TimeUnit.SECONDS)); }
            finally { start.countDown(); }
            for (Future<?> task : work) task.get(30, TimeUnit.SECONDS);
        }
    }

    private static void assertInvalidJson(String json, Class<?> type) {
        ProtocolException error = assertThrows(ProtocolException.class, () -> WireJson.read(json, type));
        assertEquals(ProtocolException.INVALID_REQUEST, error.ret());
        assertEquals("Invalid JSON data", error.getMessage());
        assertNull(error.getCause());
    }
}
