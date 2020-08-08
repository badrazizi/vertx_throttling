package vertx.throttling

import io.vertx.core.json.JsonObject

class ThrottlingClients {
  var ip: String = ""
  var port: Int = -1
  var time: Long = -1
  var throttl: Int = 0
  
  override fun toString(): String {
    return JsonObject().put("ip", ip).put("port", port).put("time", time).put("throttl", throttl).toString()
  }
}
