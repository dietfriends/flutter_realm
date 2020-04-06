package com.it_nomads.flutter_realm

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.*
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.Registrar
import io.realm.Realm
import io.realm.SyncConfiguration
import io.realm.SyncUser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.*


class FlutterRealmPlugin() : MethodCallHandler, FlutterPlugin, EventChannel.StreamHandler {
  private var events: EventChannel.EventSink? = null
  private val realms = HashMap<String, FlutterRealm>()
  private val handlers: MutableList<MethodSubHandler> = mutableListOf(SyncUserMethodSubHandler())
  lateinit var methodChannel: MethodChannel
  lateinit var eventChannel: EventChannel
  override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
    try {
      val arguments = call.arguments as Map<String, Any>? ?: mapOf();
      for (handler in handlers) {
        if (handler.onMethodCall(call, result)) {
          return
        }
      }
      when (call.method) {
        "initialize" -> {
          onInitialize(result, arguments)
        }
        "reset" -> onReset(result)
        "asyncOpenWithConfiguration" -> onAsyncOpenWithConfiguration(arguments, result)
        "syncOpenWithConfiguration" -> onSyncOpenWithConfiguration(arguments, result)
        else -> {
          val realmId = arguments["realmId"] as String?
          val flutterRealm = realms[realmId]
          if (flutterRealm == null) {
            val message = "Method " + call.method + ":" + arguments.toString()
            result.error("Realm not found", message, null)
            return
          }
          flutterRealm.onMethodCall(call, result)
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      result.error(e.message, e.message, e.stackTrace.toString())
    }
  }

  private fun onInitialize(result: MethodChannel.Result, arguments: Map<String, Any>) {
    val realmId = arguments["realmId"] as String
    val flutterRealm = FlutterRealm.create(events, realmId, arguments)
    realms[realmId] = flutterRealm
    result.success(null)
  }

  private fun onReset(result: MethodChannel.Result) {
    for (realm in realms.values) {
      realm.reset()
    }
    realms.clear()
    result.success(null)
  }

  private fun onAsyncOpenWithConfiguration(arguments: Map<String, Any>,
      result: MethodChannel.Result) {
    val realmId = arguments["realmId"] as String
    val configuration = getSyncConfiguration(arguments)
    Realm.getInstanceAsync(configuration, object : Realm.Callback() {
      override fun onSuccess(realm: Realm) {
        val flutterRealm = FlutterRealm.create(events, realmId, realm)
        realms[realmId] = flutterRealm
        result.success(null)
      }

      override fun onError(exception: Throwable) {
        result.error(exception.localizedMessage, exception.message, exception)
      }
    })
  }

  private fun onSyncOpenWithConfiguration(arguments: Map<String, Any>,
      result: MethodChannel.Result) {
    val realmId = arguments["realmId"] as String
    val configuration = getSyncConfiguration(arguments)
    val flutterRealm = FlutterRealm(events, realmId, configuration)
    realms[realmId] = flutterRealm
    result.success(null)
  }

  private fun getSyncConfiguration(arguments: Map<String, Any>): SyncConfiguration {
    val syncServerURL = arguments["syncServerURL"] as String?
    val fullSynchronization = arguments["fullSynchronization"] as Boolean
    assert(syncServerURL != null)
    val builder = SyncUser.current().createConfiguration(
        syncServerURL!!)
    if (fullSynchronization) {
      builder.fullSynchronization()
    }
    return builder.build()
  }


  companion object {
    private const val EVENT_CHANNEL = "kr.dietfriends.flutter_realm/events"

    @JvmStatic
    fun registerWith(registrar: Registrar) {
      register(registrar.context(), registrar.messenger());
    }

    @JvmStatic
    private fun register(context: Context, messenger: BinaryMessenger) {
      Realm.init(context)
      val plugin = FlutterRealmPlugin()
      plugin.setUpChannels(messenger)
    }
  }

  private fun setUpChannels(messenger: BinaryMessenger) {
    this.methodChannel = MethodChannel(messenger,
        "plugins.it_nomads.com/flutter_realm", StandardMethodCodec(RealmMessageCodec.INSTANCE))
    this.eventChannel = EventChannel(messenger, EVENT_CHANNEL,
        StandardMethodCodec(RealmMessageCodec.INSTANCE))
    this.methodChannel.setMethodCallHandler(this)
    this.eventChannel.setStreamHandler(this)
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    register(binding.applicationContext, binding.binaryMessenger)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    //realms.values.forEach { it.close() }
  }

  override fun onListen(arguments: Any, events: EventChannel.EventSink) {
    this.events = events

  }

  override fun onCancel(arguments: Any) {
    this.events = null
  }
}

class RealmMessageCodec : StandardMessageCodec() {
  override fun writeValue(stream: ByteArrayOutputStream,
      value: Any) {
    if (value is Date) {
      stream.write(DATE_TIME.toInt())
      writeLong(stream, value.time)
    } else {
      super.writeValue(stream, value)
    }
  }

  override fun readValueOfType(type: Byte,
      buffer: ByteBuffer): Any {
    return when (type) {
      DATE_TIME -> Date(
          buffer.long)
      else -> super.readValueOfType(type, buffer)
    }
  }

  companion object {
    val INSTANCE = RealmMessageCodec()
    private const val DATE_TIME = 128.toByte()
  }
}