package dev.sabrina;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MetSearchService {
    private static final Logger logger = LoggerFactory.getLogger(MetSearchService.class);
    private static final String SEARCH_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1.1/search";
    private static final String OBJECT_ENDPOINT = "https://collectionapi.metmuseum.org/public/collection/v1/objects";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PrivyCollectionClient/1.0)";
    private static final int PAGE_SIZE = 500;
    private static final int RESULT_LIMIT = 5;
    private static final int DETAIL_WORKERS = 5;
    private static final long REQUEST_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(2000);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String searchEndpoint;
    private final String objectEndpoint;
    private final ThreadLocal<Boolean> workerHasMadeRequest = ThreadLocal.withInitial(() -> false);

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
        List<Integer> uniqueObjectIds = objectIds.stream().distinct().toList();
        ExecutorService executor = Executors.newFixedThreadPool(DETAIL_WORKERS);
        CompletionService<Optional<DatedObject>> completed = new ExecutorCompletionService<>(executor);
        List<Future<Optional<DatedObject>>> requests = new ArrayList<>(uniqueObjectIds.size());
        ObjectFetchProgress progress = new ObjectFetchProgress(
                new AtomicInteger(), uniqueObjectIds.size(), new AtomicInteger());
        PriorityQueue<DatedObject> recentObjects = new PriorityQueue<>(
                Comparator.comparingInt(DatedObject::objectEndDate));

        try {
            for (int objectId : uniqueObjectIds) {
                requests.add(completed.submit(() -> {
                    Optional<JsonNode> object = fetchObject(objectId, progress);
                    if (object.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(new DatedObject(object.get(), objectEndDate(object.get(), objectId)));
                }));
            }

            for (int i = 0; i < uniqueObjectIds.size(); i++) {
                Optional<DatedObject> maybeResult;
                try {
                    maybeResult = completed.take().get();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw interrupted;
                    }
                    throw new IOException("MET object request failed", cause);
                }
                if (maybeResult.isEmpty()) {
                    continue;
                }
                DatedObject result = maybeResult.get();

                if (recentObjects.size() < RESULT_LIMIT) {
                    recentObjects.add(result);
                } else if (result.objectEndDate() > recentObjects.peek().objectEndDate()) {
                    recentObjects.remove();
                    recentObjects.add(result);
                }
            }
        } finally {
            requests.forEach(request -> request.cancel(true));
            executor.shutdownNow();
        }

        return recentObjects.stream()
                .sorted(Comparator.comparingInt(DatedObject::objectEndDate).reversed())
                .map(DatedObject::object)
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
            Optional<String> responseBody = get(uri);
            if (responseBody.isEmpty()) {
                return List.of();
            }
            JsonNode body = objectMapper.readTree(responseBody.get());
            JsonNode totalNode = body.get("total");
            if (totalNode == null || !totalNode.canConvertToInt() || totalNode.intValue() < 0) {
                throw new IOException("MET search response has an invalid total");
            }
            total = totalNode.intValue();

            JsonNode idsNode = body.get("objectIDs");
            if ((idsNode == null || idsNode.isNull()) && total == 0) {
                logger.info("MET search page received at {}: offset={}, returned=0, total=0",
                        Instant.now(), offset);
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
            logger.info("MET search page received at {}: offset={}, returned={}, total={}",
                    Instant.now(), offset, pageIds.size(), total);
            if (pageIds.isEmpty() && offset < total) {
                throw new IOException("MET search returned an empty page before all IDs were retrieved");
            }

            objectIds.addAll(pageIds);
            offset += pageIds.size();
        } while (offset < total);

        return List.copyOf(objectIds);
    }

    private Optional<JsonNode> fetchObject(int objectId, ObjectFetchProgress progress)
            throws IOException, InterruptedException {
        URI uri = URI.create(objectEndpoint + "/" + objectId);
        Optional<String> responseBody = get(uri, progress);
        if (responseBody.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readTree(responseBody.get()));
    }

    private Optional<String> get(URI uri) throws IOException, InterruptedException {
        return get(uri, null);
    }

    private Optional<String> get(URI uri, ObjectFetchProgress progress)
            throws IOException, InterruptedException {
        waitForRequestSlot(progress);
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        logger.info("Starting MET GET: timestamp={}, path={}", startedAt, uri.getPath());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            if (progress == null) {
                logger.warn("MET GET failed: timestamp={}, path={}, durationMs={}",
                        Instant.now(), uri.getPath(), durationMs, e);
            } else {
                logger.warn("MET GET failed: timestamp={}, path={}, objectsFetched={}/{}, durationMs={}",
                        Instant.now(), uri.getPath(), progress.fetched().get(), progress.total(), durationMs, e);
            }
            throw e;
        }
        int status = response.statusCode();
        int objectsFetched = progress == null ? 0 : progress.fetched().get();
        if (progress != null && status >= 200 && status < 300) {
            objectsFetched = progress.fetched().incrementAndGet();
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        if (progress == null) {
            logger.info("Finished MET GET: timestamp={}, path={}, status={}, durationMs={}",
                    Instant.now(), uri.getPath(), status, durationMs);
        } else {
            logger.info("Finished MET GET: timestamp={}, path={}, status={}, objectsFetched={}/{}, durationMs={}",
                    Instant.now(), uri.getPath(), status, objectsFetched, progress.total(), durationMs);
        }
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("MET request to " + uri.getPath() + " returned HTTP " + response.statusCode());
        }
        return Optional.of(response.body());
    }

    private void waitForRequestSlot(ObjectFetchProgress progress) throws InterruptedException {
        boolean hasMadeRequest = workerHasMadeRequest.get();
        if (hasMadeRequest) {
            TimeUnit.NANOSECONDS.sleep(REQUEST_INTERVAL_NANOS);
        } else if (progress != null) {
            int workerStartSlot = progress.nextWorkerStartSlot().getAndIncrement();
            TimeUnit.NANOSECONDS.sleep(workerStartSlot * REQUEST_INTERVAL_NANOS);
        }
        workerHasMadeRequest.set(true);
    }

    private int objectEndDate(JsonNode object, int objectId) throws IOException {
        JsonNode date = object.get("objectEndDate");
        if (date == null || !date.canConvertToInt()) {
            throw new IOException("MET object " + objectId + " has an invalid objectEndDate");
        }
        return date.intValue();
    }

    private record DatedObject(JsonNode object, int objectEndDate) {}

    private record ObjectFetchProgress(AtomicInteger fetched, int total, AtomicInteger nextWorkerStartSlot) {}
}
