package com.it_nomads.flutter_realm

import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.realm.*


class FlutterRealm(
    private val eventSink: EventChannel.EventSink?, private val realmId: String,
    config: RealmConfiguration) {
  private val realm: Realm = Realm.getInstance(config)
  private val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(config)
  private val subscriptions: HashMap<String, RealmResults<*>> = HashMap()

  companion object {
    @JvmStatic
    fun create(eventSink: EventChannel.EventSink?, realmId: String,
        arguments: Map<String, Any>): FlutterRealm {
      val builder: RealmConfiguration.Builder = RealmConfiguration.Builder().modules(
          Realm.getDefaultModule()!!)
      val inMemoryIdentifier = arguments["inMemoryIdentifier"] as String?
      inMemoryIdentifier?.let {
        builder.inMemory().name(inMemoryIdentifier)
      }
      val config: RealmConfiguration = builder.build()
      return FlutterRealm(eventSink, realmId, config);
    }

    @JvmStatic
    fun create(eventSink: EventChannel.EventSink?, realmId: String,
        realm: Realm): FlutterRealm {
      return FlutterRealm(eventSink, realmId, realm.configuration);
    }
  }


  fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    try {
      val arguments: Map<String, Any> = call.arguments as Map<String, Any>
      when (call.method) {
        "createObject" -> {
          val className = arguments["$"] as String?
          val uuid = arguments["uuid"] as String?
          assert(className != null)
          assert(uuid != null)
          dynamicRealm.beginTransaction()
          val dynamicRealmObject: DynamicRealmObject = dynamicRealm.createObject(className!!,
              uuid!!)
          mapToObject(dynamicRealmObject, arguments)
          dynamicRealm.commitTransaction()
          result.success(null)
        }
        "deleteObject" -> {
          val className = arguments["$"] as String
          val primaryKey: Any = arguments["primaryKey"] as String
          val dynamicRealmObject: DynamicRealmObject? = find(className, primaryKey)
          dynamicRealmObject?.let {
            dynamicRealm.beginTransaction()
            it.deleteFromRealm()
            dynamicRealm.commitTransaction()
          }
          result.success(null)
        }
        "allObjects" -> {
          val className = arguments.get("$") as String
          val results: RealmResults<DynamicRealmObject> = dynamicRealm.where(className).findAll()
          val list = convert(results)
          result.success(list)
        }
        "updateObject" -> {
          val className = arguments["$"] as String
          val primaryKey: Any = arguments["primaryKey"] as Any
          val value: Map<String, Any> = arguments["value"] as Map<String, Any>
          val dynamicRealmObject: DynamicRealmObject? = find(className, primaryKey)
          if (null == dynamicRealmObject) {
            val msg: String = String.format("%s not found with primaryKey = %s",
                className, primaryKey)
            result.error(msg, null, null)
            return
          }
          dynamicRealm.beginTransaction()
          mapToObject(dynamicRealmObject, value)
          dynamicRealm.commitTransaction()
          result.success(objectToMap(dynamicRealmObject))
        }
        "subscribeAllObjects" -> {
          val className = arguments["$"] as String
          val subscriptionId = arguments["subscriptionId"] as String
          val subscription: RealmResults<DynamicRealmObject> = dynamicRealm.where(
              className).findAllAsync()
          subscribe(subscriptionId, subscription)
          result.success(null)
        }
        "subscribeObjects" -> {
          val className = arguments["$"] as String
          val subscriptionId = arguments["subscriptionId"] as String
          val subscription: RealmResults<DynamicRealmObject> = getQuery(
              dynamicRealm.where(className),
              arguments["predicate"] as List<List<Any>>?).findAllAsync()
          subscribe(subscriptionId, subscription)
          result.success(null)
        }
        "objects" -> {
          val className = arguments.get("$") as String
          val results: RealmResults<DynamicRealmObject> = getQuery(dynamicRealm.where(className),
              arguments["predicate"] as List<List<Any>>?).findAll()
          val list = convert(results)
          result.success(list)
        }
        "unsubscribe" -> {
          val subscriptionId = arguments.get("subscriptionId") as String
              ?: throw java.lang.Exception("No argument: subscriptionId")
          if (!subscriptions.containsKey(subscriptionId)) {
            throw java.lang.Exception("Not subscribed: $subscriptionId")
          }
          subscriptions.remove(subscriptionId)?.removeAllChangeListeners()
          result.success(null)
        }
        "deleteAllObjects" -> {
          dynamicRealm.beginTransaction()
          dynamicRealm.deleteAll()
          dynamicRealm.commitTransaction()
          result.success(null)
        }
        "filePath" -> {
          result.success(dynamicRealm.configuration.path)
        }
        else -> result.notImplemented()
      }
    } catch (e: java.lang.Exception) {
      if (dynamicRealm.isInTransaction) {
        dynamicRealm.cancelTransaction()
      }
      e.printStackTrace()
      result.error(e.message, e.message, e.stackTrace.toString())
    }
  }

  private fun find(className: String, primaryKey: Any): DynamicRealmObject? {
    var dynamicRealmObject: DynamicRealmObject? = null
    if (primaryKey is String) {
      dynamicRealmObject = dynamicRealm.where(className).equalTo("uuid", primaryKey).findFirst()
    } else if (primaryKey is Int) {
      dynamicRealmObject = dynamicRealm.where(className).equalTo("uuid", primaryKey).findFirst()
    }
    return dynamicRealmObject
  }

  @kotlin.jvm.Throws(java.lang.Exception::class)
  private fun getQuery(query: RealmQuery<DynamicRealmObject>,
      predicate: List<List<Any>>?): RealmQuery<DynamicRealmObject> {
    if (predicate == null) {
      return query
    }
    var result: RealmQuery<DynamicRealmObject> = query
    for (item in predicate) {
      val operator = item[0] as String
      result = when (operator) {
        "greaterThan" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item[2]) {
            is Int -> {
              result.greaterThan(fieldName, argument)
            }
            is Long -> {
              result.greaterThan(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "greaterThanOrEqualTo" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item.get(2)) {
            is Int -> {
              result.greaterThanOrEqualTo(fieldName, argument)
            }
            is Long -> {
              result.greaterThanOrEqualTo(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "lessThan" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item[2]) {
            is Int -> {
              result.lessThan(fieldName, argument)
            }
            is Long -> {
              result.lessThan(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "lessThanOrEqualTo" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item[2]) {
            is Int -> {
              result.lessThanOrEqualTo(fieldName, argument)
            }
            is Long -> {
              result.lessThanOrEqualTo(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "equalTo" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item[2]) {
            is Int -> {
              result.equalTo(fieldName, argument)
            }
            is String -> {
              result.equalTo(fieldName, argument)
            }
            is Long -> {
              result.equalTo(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "notEqualTo" -> {
          val fieldName = item[1] as String
          when (val argument: Any = item[2]) {
            is Int -> {
              result.notEqualTo(fieldName, argument)
            }
            is String -> {
              result.notEqualTo(fieldName, argument)
            }
            is Long -> {
              result.notEqualTo(fieldName, argument)
            }
            else -> {
              throw java.lang.Exception("Unsupported type")
            }
          }
        }
        "contains" -> {
          val fieldName = item[1] as String
          val argument: Any = item[2]
          if (argument is String) {
            result.contains(fieldName, argument)
          } else {
            throw java.lang.Exception("Unsupported type")
          }
        }
        "and" -> result.and()
        "or" -> result.or()
        else -> throw java.lang.Exception("Unknown operator")
      }
    }
    return result
  }

  @kotlin.jvm.Throws(java.lang.Exception::class)
  private fun subscribe(subscriptionId: String,
      subscription: RealmResults<DynamicRealmObject>) {
    if (subscriptions.containsKey(subscriptionId)) {
      //throw Exception("Already subscribed")
      eventSink?.error("ALREADY_SUBSCRIBED", "message", "")
    }

    subscriptions[subscriptionId] = subscription
    subscription.addChangeListener { results, _ ->
      val list: List<Any> = convert(results)
      val map: MutableMap<String, Any> = mutableMapOf()
      map["realmId"] = realmId
      map["subscriptionId"] = subscriptionId
      map["results"] = list
      eventSink?.success(map.toMap())
    }
  }

  private fun objectToMap(dynamicRealmObject: DynamicRealmObject): Map<String, Any> {
    val map: MutableMap<String, Any> = HashMap()
    for (fieldName in dynamicRealmObject.fieldNames) {
      if (dynamicRealmObject.isNull(fieldName)) {
        continue
      }
      if (dynamicRealmObject.getFieldType(fieldName) == RealmFieldType.STRING_LIST) {
        val value: List<String> = dynamicRealmObject.getList(fieldName,
            String::class.java)
        map[fieldName] = value
        continue
      }
      if (dynamicRealmObject.getFieldType(fieldName) == RealmFieldType.INTEGER_LIST) {
        val value: List<Int> = dynamicRealmObject.getList(fieldName, Int::class.java)
        map[fieldName] = value
        continue
      }
      val value: Any = dynamicRealmObject.get(fieldName)
      map[fieldName] = value
    }
    return map
  }

  private fun mapToObject(dynamicRealmObject: DynamicRealmObject,
      map: Map<String, Any>) {
    for (fieldName in dynamicRealmObject.fieldNames) {
      if (!map.containsKey(fieldName) || fieldName == "uuid") {
        continue
      }
      var value: Any = map[fieldName] ?: error("")
      if (value is List<*>) {
        val newValue: RealmList<Any> = RealmList()
        newValue.addAll(value)
        value = newValue
      }
      dynamicRealmObject.set(fieldName, value)
    }
  }

  private fun convert(
      results: RealmResults<DynamicRealmObject>): List<Map<String, Any>> {
    val list = mutableListOf<Map<String, Any>>()
    for (dynamicRealmObject in results) {
      val map = objectToMap(dynamicRealmObject)
      list.add(map)
    }
    return java.util.Collections.unmodifiableList(list)
  }

  fun reset() {
    subscriptions.clear()
    dynamicRealm.beginTransaction()
    dynamicRealm.deleteAll()
    dynamicRealm.commitTransaction()
  }

  fun close() {
    subscriptions.clear()
    dynamicRealm.close()
    realm.close()
  }
}