package dev.sabrina;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    private Javalin app;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void start() {
        app = new App().create().start(0); // port 0 = pick any free port
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

    private HttpResponse<String> post(String path, String json) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthReturnsOk() throws Exception {
        HttpResponse<String> res = get("/health");
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"status\":\"ok\""));
    }

    @Test
    void createThenFetchItem() throws Exception {
        HttpResponse<String> created = post("/items", "{\"name\":\"first\"}");
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("\"name\":\"first\""));

        HttpResponse<String> fetched = get("/items/1");
        assertEquals(200, fetched.statusCode());
        assertTrue(fetched.body().contains("\"id\":1"));
    }

    @Test
    void createRejectsBlankName() throws Exception {
        assertEquals(400, post("/items", "{\"name\":\"  \"}").statusCode());
    }

    @Test
    void unknownItemReturns404() throws Exception {
        assertEquals(404, get("/items/999").statusCode());
    }
}
