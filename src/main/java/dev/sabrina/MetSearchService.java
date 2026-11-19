package dev.sabrina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class MetSearchService {
    private static final String SEARCH_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1.1/search";
    private static final String OBJECT_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1/objects";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PrivyCollectionClient/1.0)";
    private static final int PAGE_SIZE = 500;
    private static final int RESULT_LIMIT = 5;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String searchEndpoint;
    private final String objectEndpoint;

    public MetSearchService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), SEARCH_ENDPOINT, OBJECT_ENDPOINT);
    }

    MetSearchService(HttpClient httpClient, ObjectMapper objectMapper,
                     String searchEndpoint, String objectEndpoint) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.searchEndpoint = searchEndpoint;
        this.objectEndpoint = objectEndpoint;
    }

    public List<JsonNode> searchRecentObjects(String query) throws IOException, InterruptedException {
        return getRecentObjects(searchObjectIds(query));
    }

    public List<JsonNode> getRecentObjects(List<Integer> objectIds) throws IOException, InterruptedException {
        PriorityQueue<JsonNode> recentObjects = new PriorityQueue<>(
                Comparator.comparingInt(object -> object.path("objectEndDate").intValue()));

        for (int objectId : objectIds) {
            JsonNode object = fetchObject(objectId);
            int objectEndDate = objectEndDate(object, objectId);

            if (recentObjects.size() < RESULT_LIMIT) {
                recentObjects.add(object);
            } else if (objectEndDate > recentObjects.peek().path("objectEndDate").intValue()) {
                recentObjects.remove();
                recentObjects.add(object);
            }
        }

        return recentObjects.stream()
                .sorted(Comparator.comparingInt((JsonNode object) -> object.path("objectEndDate").intValue())
                        .reversed())
                .toList();
    }

    public List<Integer> searchObjectIds(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<Integer> objectIds = new ArrayList<>();
        int total;
        int offset = 0;

        do {
            URI uri = URI.create(searchEndpoint + "?q=" + encodedQuery
                    + "&offset=" + offset + "&limit=" + PAGE_SIZE);
            JsonNode body = objectMapper.readTree(get(uri));
            JsonNode totalNode = body.get("total");
            if (totalNode == null || !totalNode.canConvertToInt() || totalNode.intValue() < 0) {
                throw new IOException("MET search response has an invalid total");
            }
            total = totalNode.intValue();

            JsonNode idsNode = body.get("objectIDs");
            if ((idsNode == null || idsNode.isNull()) && total == 0) {
                return List.of();
            }
            if (idsNode == null || !idsNode.isArray()) {
                throw new IOException("MET search response has no objectIDs array");
            }

            List<Integer> pageIds = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                if (!idNode.canConvertToInt()) {
                    throw new IOException("MET search response contains an invalid object ID");
                }
                pageIds.add(idNode.intValue());
            }
            if (pageIds.isEmpty() && offset < total) {
                throw new IOException("MET search returned an empty page before all IDs were retrieved");
            }

            objectIds.addAll(pageIds);
            offset += pageIds.size();
        } while (offset < total);

        return List.copyOf(objectIds);
    }

    private JsonNode fetchObject(int objectId) throws IOException, InterruptedException {
        return objectMapper.readTree(get(URI.create(objectEndpoint + "/" + objectId)));
    }

    private String get(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MET request returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private int objectEndDate(JsonNode object, int objectId) throws IOException {
        JsonNode date = object.get("objectEndDate");
        if (date == null || !date.canConvertToInt()) {
            throw new IOException("MET object " + objectId + " has an invalid objectEndDate");
        }
        return date.intValue();
    }
}
