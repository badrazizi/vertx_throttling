package vertx.throttling

import io.vertx.core.Vertx
import io.vertx.core.shareddata.Shareable
import io.vertx.ext.web.impl.RouterImpl
import io.vertx.ext.web.Router

class ShareableRouter internal constructor(vertx: Vertx) : RouterImpl(vertx), Shareable {
  companion object {
    fun router(vertx: Vertx): Router {
      return vertx.sharedData()
        .getLocalMap<String, ShareableRouter>("router")
        .computeIfAbsent("main") { ShareableRouter(vertx) }
    }
  }
}
