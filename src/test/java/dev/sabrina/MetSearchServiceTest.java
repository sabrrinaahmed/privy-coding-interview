package dev.sabrina;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetSearchServiceTest {

    @Test
    void searchObjectIdsFetchesEveryPageAndEncodesQuery() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            List<String> queries = new ArrayList<>();
            api.search(exchange -> {
                String query = exchange.getRequestURI().getRawQuery();
                queries.add(query);
                respond(exchange, 200, query.contains("offset=0")
                        ? "{\"total\":3,\"objectIDs\":[11,22]}"
                        : "{\"total\":3,\"objectIDs\":[33]}");
            });
            api.start();

            assertEquals(List.of(11, 22, 33), api.service().searchObjectIds("fresh bread"));
            assertEquals(2, queries.size());
            assertTrue(queries.get(0).contains("q=fresh+bread"));
            assertTrue(queries.get(1).contains("offset=2"));
        }
    }

    @Test
    void searchObjectIdsReturnsEmptyWhenNoMatches() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.search(exchange -> respond(exchange, 200, "{\"total\":0,\"objectIDs\":null}"));
            api.start();

            assertTrue(api.service().searchObjectIds("not found").isEmpty());
        }
    }

    @Test
    void searchObjectIdsRejectsInvalidResponse() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.search(exchange -> respond(exchange, 200, "{\"total\":\"invalid\",\"objectIDs\":[]}"));
            api.start();

            assertThrows(IOException.class, () -> api.service().searchObjectIds("bread"));
        }
    }

    @Test
    void searchObjectIdsRejectsHttpFailure() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.search(exchange -> respond(exchange, 503, "unavailable"));
            api.start();

            assertThrows(IOException.class, () -> api.service().searchObjectIds("bread"));
        }
    }

    @Test
    void getRecentObjectsReturnsFiveNewestInOrder() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            Map<Integer, Integer> dates = Map.of(
                    11, 1920, 22, 1960, 33, 1890, 44, 2024, 55, 1880, 66, 1985, 77, 1900);
            api.objects(exchange -> {
                int id = objectId(exchange);
                respond(exchange, 200, "{\"objectID\":" + id + ",\"objectEndDate\":"
                        + dates.get(id) + ",\"title\":\"Test object\"}");
            });
            api.start();

            List<Integer> ids = api.service().getRecentObjects(List.of(11, 22, 33, 44, 55, 66, 77))
                    .stream()
                    .map(object -> object.path("objectID").intValue())
                    .toList();
            assertEquals(List.of(44, 66, 22, 11, 77), ids);
        }
    }

    @Test
    void getRecentObjectsReturnsEmptyForNoIds() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.start();

            assertTrue(api.service().getRecentObjects(List.of()).isEmpty());
        }
    }

    @Test
    void getRecentObjectsRejectsMissingObjectEndDate() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.objects(exchange -> respond(exchange, 200, "{\"objectID\":11}"));
            api.start();

            assertThrows(IOException.class, () -> api.service().getRecentObjects(List.of(11)));
        }
    }

    @Test
    void getRecentObjectsRejectsHttpFailure() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.objects(exchange -> respond(exchange, 404, "not found"));
            api.start();

            assertThrows(IOException.class, () -> api.service().getRecentObjects(List.of(11)));
        }
    }

    @Test
    void searchRecentObjectsCombinesSearchAndDetails() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.search(exchange -> respond(exchange, 200, "{\"total\":1,\"objectIDs\":[11]}"));
            api.objects(exchange -> respond(exchange, 200,
                    "{\"objectID\":11,\"objectEndDate\":2000,\"title\":\"Test object\"}"));
            api.start();

            assertEquals(11, api.service().searchRecentObjects("bread").getFirst()
                    .path("objectID").intValue());
        }
    }

    @Test
    void searchRecentObjectsPropagatesSearchFailure() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            api.search(exchange -> respond(exchange, 500, "error"));
            api.start();

            assertThrows(IOException.class, () -> api.service().searchRecentObjects("bread"));
        }
    }

    @Test
    void searchRecentObjectsSendsApiHeadersToBothEndpoints() throws Exception {
        try (StubMetApi api = new StubMetApi()) {
            List<String> userAgents = new ArrayList<>();
            List<String> acceptHeaders = new ArrayList<>();
            api.search(exchange -> {
                captureHeaders(exchange, userAgents, acceptHeaders);
                respond(exchange, 200, "{\"total\":1,\"objectIDs\":[11]}");
            });
            api.objects(exchange -> {
                captureHeaders(exchange, userAgents, acceptHeaders);
                respond(exchange, 200, "{\"objectID\":11,\"objectEndDate\":2000}");
            });
            api.start();

            api.service().searchRecentObjects("bread");

            assertEquals(2, userAgents.size());
            assertTrue(userAgents.stream().allMatch(agent ->
                    agent.equals("Mozilla/5.0 (compatible; PrivyCollectionClient/1.0)")));
            assertTrue(acceptHeaders.stream().allMatch(header -> header.equals("application/json")));
        }
    }

    private static int objectId(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        return Integer.parseInt(path.substring("/objects/".length()));
    }

    private static void captureHeaders(HttpExchange exchange, List<String> userAgents, List<String> acceptHeaders) {
        userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        acceptHeaders.add(exchange.getRequestHeaders().getFirst("Accept"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    private static final class StubMetApi implements AutoCloseable {
        private final HttpServer server;

        private StubMetApi() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        }

        private void search(HttpHandler handler) {
            server.createContext("/search", handler);
        }

        private void objects(HttpHandler handler) {
            server.createContext("/objects/", handler);
        }

        private void start() {
            server.start();
        }

        private MetSearchService service() {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            return new MetSearchService(HttpClient.newHttpClient(), new ObjectMapper(),
                    base + "/search", base + "/objects");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
