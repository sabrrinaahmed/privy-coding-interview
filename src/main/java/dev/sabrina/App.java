package dev.sabrina;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * REST API for Met collection search.
 *
 * Run:   mvn -q compile exec:java
 * Test:  mvn -q test
 * Try:   curl 'localhost:7070/search?q=bread'
 */
public class App {
    private static final Logger logger = LoggerFactory.getLogger(App.class);

    record SearchResponse(List<JsonNode> objects) {
        SearchResponse {
            objects = List.copyOf(objects);
        }
    }
    record ErrorResponse(String error) {}

    @FunctionalInterface
    interface RecentObjectSearcher {
        List<JsonNode> searchRecentObjects(String query) throws IOException, InterruptedException;
    }

    private final RecentObjectSearcher recentObjectSearcher;

    public App() {
        this(new MetSearchService()::searchRecentObjects);
    }

    App(RecentObjectSearcher recentObjectSearcher) {
        this.recentObjectSearcher = recentObjectSearcher;
    }

    public Javalin create() {
        Javalin app = Javalin.create();

        app.get("/search", ctx -> {
            String query = ctx.queryParam("q");
            if (query == null || query.isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("q is required"));
                return;
            }

            try {
                ctx.json(new SearchResponse(recentObjectSearcher.searchRecentObjects(query.trim())));
            } catch (IOException e) {
                logger.error("MET search request failed", e);
                ctx.status(HttpStatus.BAD_GATEWAY).json(new ErrorResponse("MET search request failed"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("MET search request was interrupted", e);
                ctx.status(HttpStatus.BAD_GATEWAY).json(new ErrorResponse("MET search request was interrupted"));
            }
        });

        return app;
    }

    public static void main(String[] args) {
        new App().create().start(7070);
    }
}
