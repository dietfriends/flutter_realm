part of flutter_realm;

final MethodChannel _realmMethodChannel = const MethodChannel(
    'plugins.it_nomads.com/flutter_realm',
    const StandardMethodCodec(RealmMessageCodec()));

class MethodChannelTransport {
  final String realmId;
  final MethodChannel _channel;

  MethodChannelTransport(this.realmId, [MethodChannel channel])
      : _channel = channel ?? _realmMethodChannel;

  Future<T> invokeMethod<T>(String method, [Map arguments]) =>
      _channel.invokeMethod<T>(method, _addRealmId(arguments));

  Map _addRealmId(Map arguments) {
    final map = (arguments ?? {});
    map['realmId'] = realmId;
    return map;
  }

  bool _equalRealmId(MethodCall call) => call.arguments['realmId'] == realmId;

  static Future<void> reset() => _realmMethodChannel.invokeMethod('reset');
}
