Java 21 with Javalin 6. Use records for request and response types.
Use BigDecimal for money. Validate input before changing any state.
Return 400 for bad input, 404 for missing resources, 409 for conflicts, with a JSON error body.
Keep business rules in model/service classes, not in route handlers.
Return copies of internal collections, never the collection itself.
Write JUnit 5 tests for each endpoint, including error cases.
Make small, focused changes. Don't rewrite or reformat code that already works.
