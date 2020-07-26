package vertx.throttling

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import java.util.*

@Volatile
private var inCustomData = CustomData()

class ThrottlImpl(private val vertx: Vertx, ips: List<String>? = null, private val customData: CustomData? = null) {
  private lateinit var router: Router
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
  var ipHeaders: List<String> = arrayListOf(
    "CF-Connecting-IP",
    "True-Client-IP"
  )
    private set
  private val cidrs: MutableList<CIDRUtils> = arrayListOf()

  fun ipHeaders(headers: List<String>) = apply {
    this.ipHeaders = headers
  }
  
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
    this.router = Router.router(vertx)
    return getRouter(router)
  }

  fun getRouter(router: Router): Router {
    for (ip in originalIPFrom) {
      if (ip.contains("/")) {
        cidrs.add(CIDRUtils(ip))
      }
    }
    router.route().handler(throttlingHandler)

    if(customData != null) {
      if (customData.periodicID == -1L) {
        customData.setPeriodicID(vertx.setPeriodic(periodicTime, throttlingReseter))
      }
    } else {
      if (inCustomData.periodicID == -1L) {
        inCustomData.setPeriodicID(vertx.setPeriodic(periodicTime, throttlingReseter))
      }
    }
    this.router = router
    return router
  }

  private val throttlingReseter = Handler<Long> {
    val time = Date().time
    if (customData != null) {
      synchronized(customData.clients) {
        customData.clients.filter { c -> (time - c.time) > throttlingTime }.forEach { c ->
          customData.clients.remove(c)
        }
      }
    } else {
      synchronized(inCustomData.clients) {
        inCustomData.clients.filter { c -> (time - c.time) > throttlingTime }.forEach { c ->
          inCustomData.clients.remove(c)
        }
      }
    }
  }

  private val throttlingHandler = Handler<RoutingContext> { ctx ->
    val host = ctx.request().remoteAddress().host()
    val port = ctx.request().remoteAddress().port()
    val headers = ctx.request().headers()

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
          ip = headers[foundHeader[0]]
          break
        }
      }

    } else {
      ip = host
    }

    if (ip.isBlank()) {
      ctx.next()
      return@Handler
    }

    if(customData != null) {
      synchronized(customData.clients) {
        val ipFound = customData.clients.find { c -> c.ip == ip }

        val time = Date().time

        if (ipFound == null) {
          customData.clients.add(ThrottlingClients().apply {
            this.ip = ip
            this.port = port
            this.throttl = 1
            this.time = time
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

            ipFound.time = time

            ctx.response().setStatusCode(429).end()
            return@Handler
          }

          ipFound.throttl += 1
          ipFound.time = time

          if (includeHeaders) {
            ctx.response().putHeader("RATE_LIMIT_COUNT", "${ipFound.throttl}")
            ctx.response().putHeader("RATE_LIMIT_MAX", "$throttlingRequest")
            ctx.response().putHeader("RATE_LIMIT_TIME", "${throttlingTime / 1000}")
            ctx.response().putHeader("RATE_LIMIT_IP", ip)
          }

          ctx.next()
        }
      }
    } else {
      synchronized(inCustomData.clients) {
        val ipFound = inCustomData.clients.find { c -> c.ip == ip }

        val time = Date().time

        if (ipFound == null) {
          inCustomData.clients.add(ThrottlingClients().apply {
            this.ip = ip
            this.port = port
            this.throttl = 1
            this.time = time
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

            ipFound.time = time

            ctx.response().setStatusCode(429).end()
            return@Handler
          }

          ipFound.throttl += 1
          ipFound.time = time

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
  }
}
