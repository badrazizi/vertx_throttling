package vertx.throttling

import io.vertx.ext.web.Router

interface Throttl {
  companion object {
    fun getThrottling(vertx: Vertx, ips: List<String>? = null, customData: CustomData? = null): ThrottlImpl {
      return ThrottlImpl(vertx, ips, customData)
    }
  }
}
