package vertx.throttling

import io.vertx.ext.web.Router

interface Throttl {
  fun getThrottlingRouter(): Router

  fun getThrottlingRouter(router: Router): Router
}
