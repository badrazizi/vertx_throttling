package vertx.throttling

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.*

class CustomData {
  var customList: MutableCollection<ThrottlingClients> = Collections.synchronizedCollection(arrayListOf<ThrottlingClients>())
    private set

  var customPeriodicID: Long = -1
    private set

  fun setCustomPeriodicID(id: Long) = apply { this.customPeriodicID = id }

  override fun toString(): String {
    return JsonObject().put("ID", customPeriodicID).put("list", JsonArray(customList.toString())).toString()
  }
}
