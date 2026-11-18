package dev.sabrina;

import io.javalin.Javalin;
import io.javalin.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal REST API starter.
 *
 * Run:   mvn -q compile exec:java
 * Test:  mvn -q test
 * Try:   curl localhost:7070/health
 */
public class App {

    // Example in-memory store. Replace with whatever the onsite prompt needs.
    record Item(long id, String name) {}
    record CreateItemRequest(String name) {}
    record ErrorResponse(String error) {}

    private final Map<Long, Item> items = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public Javalin create() {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.json(Map.of("status", "ok")));

        // Example: create an item
        app.post("/items", ctx -> {
            CreateItemRequest req = ctx.bodyAsClass(CreateItemRequest.class);
            if (req.name() == null || req.name().isBlank()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("name is required"));
                return;
            }
            Item item = new Item(nextId.getAndIncrement(), req.name().trim());
            items.put(item.id(), item);
            ctx.status(HttpStatus.CREATED).json(item);
        });

        // Example: fetch an item by id
        app.get("/items/{id}", ctx -> {
            long id;
            try {
                id = Long.parseLong(ctx.pathParam("id"));
            } catch (NumberFormatException e) {
                ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("id must be a number"));
                return;
            }
            Item item = items.get(id);
            if (item == null) {
                ctx.status(HttpStatus.NOT_FOUND).json(new ErrorResponse("item not found"));
                return;
            }
            ctx.json(item);
        });

        return app;
    }

    public static void main(String[] args) {
        new App().create().start(7070);
    }
}
