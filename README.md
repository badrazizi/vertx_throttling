
# Eclipse Vert.x Throttling

configurable in-memory rate limiter

**note: not designed to work with cluster, change <u>synchronized collection</u> with <u>redis</u> to be able to share client state between nodes.**

```kotlin
// optional
// you can pass null, and will use default custom data.
private var clients = CustomData()

val throttling = Throttl
        .getThrottling(vertx, null, clients)
        .includeHeaders(true) // will add throttling header to the response (default true).
        .throttlingRequest(30) // requests limit (default 30 requests).
        .throttlingTime(60_000) // time limit (default 60 seconds).
        .periodicTime(1_000) // periodic check to reset clients (default 1 second).
        
val router = throttling.getRouter()
```
