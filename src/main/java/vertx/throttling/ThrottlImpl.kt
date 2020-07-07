package vertx.throttling

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

class ThrottlImpl(private val vertx: Vertx, ips: List<String>? = null) : Throttl {
  private lateinit var router: Router
  private val clients = Collections.synchronizedCollection(arrayListOf<ThrottlingClients>())
  var includeHeaders: Boolean = true
    private set
  var throttlingRequest: Int = 30
    private set
  var throttlingTime: Long = 60_000
    private set
  var periodicTime: Long = 1_000
    private set
  var originalIPFrom: List<String> = ips ?: arrayListOf(
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "104.16.0.0/12",
    "108.162.192.0/18",
    "131.0.72.0/22",
    "141.101.64.0/18",
    "162.158.0.0/15",
    "172.64.0.0/13",
    "173.245.48.0/20",
    "188.114.96.0/20",
    "190.93.240.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "199.27.128.0/21"
  )
    private set
  private var ipHeaders: List<String> = arrayListOf(
    "CF-Connecting-IP",
    "True-Client-IP"
  )
    private set
  private val cidrs: MutableList<CIDRUtils> = arrayListOf()

  fun includeHeaders(enabled: Boolean = true) = apply {
    this.includeHeaders = enabled
  }

  fun throttlingRequest(count: Int = 30) = apply {
    this.throttlingRequest = count
  }

  fun throttlingTime(time: Long = 60_000) = apply {
    this.throttlingTime = time
  }

  fun periodicTime(time: Long = 1_000) = apply {
    this.periodicTime = time
  }

  fun getRouter(): Router {
    this.router = ShareableRouter.router(vertx)
    return getRouter(router)
  }

  fun getRouter(router: Router): Router {
    for (ip in originalIPFrom) {
      if (ip.contains("/")) {
        cidrs.add(CIDRUtils(ip))
      }
    }
    router.route().handler(throttlingHandler)
    vertx.setPeriodic(periodicTime, throttlingReseter)
    this.router = router
    return router
  }

  private val throttlingReseter = Handler<Long> {
    val time = Date().time
    clients.filter { c -> (time - c.time) > throttlingTime }.forEach { c ->
      clients.remove(c)
    }
  }

  private val throttlingHandler = Handler<RoutingContext> { ctx ->
    val host = ctx.request().remoteAddress().host()
    val port = ctx.request().remoteAddress().port()
    val headers = ctx.request().headers()

    // if server behind a service such as cloudflare
    // check if ip in list or in range and retrieve ip from headers
    var ip = ""
    if (host in originalIPFrom || cidrs.any { it.isInRange(host) }) {
      if (headers.isEmpty || ipHeaders.isEmpty()) {
        ctx.next()
        return@Handler
      }

      for (ipHeader in ipHeaders) {
        val foundHeader = headers.names().filter { h -> h == ipHeader }
        if (foundHeader.isEmpty()) {
          continue
        } else {
          // got ip address
          ip = headers[foundHeader[0]]
          break
        }
      }

    } else {
      // got ip address
      ip = host
    }

    if (ip.isBlank()) {
      // if ip is blank pass the handler to be processed
      ctx.next()
      return@Handler
    }

    // check if client ip exist in the list
    val ipFound = clients.find { c -> c.ip == ip }

    if (ipFound == null) {
      clients.add(ThrottlingClients().apply {
        this.ip = ip
        this.port = port
        this.throttl = 1
        this.time = Date().time
      })

      if (includeHeaders) {
        ctx.response().putHeader("RATE_LIMIT_COUNT", "1")
        ctx.response().putHeader("RATE_LIMIT_MAX", "$throttlingRequest")
        ctx.response().putHeader("RATE_LIMIT_TIME", "${throttlingTime / 1000}")
        ctx.response().putHeader("RATE_LIMIT_IP", ip)
      }
      ctx.next()
      return@Handler
    } else {
      if (ipFound.throttl >= throttlingRequest) {
        if (includeHeaders) {
          ctx.response().putHeader("RATE_LIMIT_COUNT", "${ipFound.throttl}")
          ctx.response().putHeader("RATE_LIMIT_MAX", "$throttlingRequest")
          ctx.response().putHeader("RATE_LIMIT_TIME", "${throttlingTime / 1000}")
          ctx.response().putHeader("RATE_LIMIT_IP", ip)
        }
        ctx.response().setStatusCode(429).end()
        return@Handler
      }

      ipFound.throttl += 1

      if (includeHeaders) {
        ctx.response().putHeader("RATE_LIMIT_COUNT", "${ipFound.throttl}")
        ctx.response().putHeader("RATE_LIMIT_MAX", "$throttlingRequest")
        ctx.response().putHeader("RATE_LIMIT_TIME", "${throttlingTime / 1000}")
        ctx.response().putHeader("RATE_LIMIT_IP", ip)
      }

      ctx.next()
    }
  }
}
