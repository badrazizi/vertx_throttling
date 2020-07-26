package vertx.throttling

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.*

class CustomData {
  var clients: MutableCollection<ThrottlingClients> = Collections.synchronizedCollection(arrayListOf<ThrottlingClients>())
    private set

  var periodicID: Long = -1
    private set

  fun setPeriodicID(id: Long) = apply { this.periodicID = id }

  override fun toString(): String {
    return JsonObject().put("ID", periodicID).put("list", JsonArray(clients.toString())).toString()
  }
}
