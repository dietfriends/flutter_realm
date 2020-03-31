import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class RealmMessageCodec extends StandardMessageCodec {
  static const int _kDateTime = 128;

  @override
  void writeValue(WriteBuffer buffer, dynamic value) {
    if (value is DateTime) {
      buffer.putUint8(_kDateTime);
      buffer.putInt64(value.millisecondsSinceEpoch);
    } else {
      super.writeValue(buffer, value);
    }
  }

  @override
  dynamic readValueOfType(int type, ReadBuffer buffer) {
    switch (type) {
      case _kDateTime:
        return DateTime.fromMillisecondsSinceEpoch(buffer.getInt64());
      // These cases are only needed on tests, and therefore handled
      // by [TestFirestoreMessageCodec], a subclass of this codec.
      default:
        return super.readValueOfType(type, buffer);
    }
  }

  const RealmMessageCodec();
}
