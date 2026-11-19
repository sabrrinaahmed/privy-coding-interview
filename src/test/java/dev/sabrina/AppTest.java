package dev.sabrina;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private Javalin app;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        app = new App(query -> List.of(
                mapper.readTree("{\"objectID\":123,\"objectEndDate\":1900}"),
                mapper.readTree("{\"objectID\":456,\"objectEndDate\":2000}")))
                .create().start(0); // port 0 = pick any free port
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void searchRequiresQuery() throws Exception {
        HttpResponse<String> res = get("/search");
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("\"error\":\"q is required\""));
    }

    @Test
    void searchRejectsBlankQuery() throws Exception {
        HttpResponse<String> res = get("/search?q=%20%20");
        assertEquals(400, res.statusCode());
        assertTrue(res.body().contains("\"error\":\"q is required\""));
    }

    @Test
    void searchReturnsRecentObjects() throws Exception {
        HttpResponse<String> res = get("/search?q=bread");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"objectID\":123"));
        assertTrue(res.body().contains("\"objectEndDate\":2000"));
    }
}
