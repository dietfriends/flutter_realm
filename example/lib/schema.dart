class Product {
  final String uuid;
  final String title;
  final DateTime createdAt;

  Product(this.uuid, this.title, [DateTime createdAt])
      : this.createdAt = createdAt ?? DateTime.now();

  Map<String, dynamic> toMap({bool withId = false}) =>
      {if (withId) 'uuid': uuid, 'title': title, 'createdAt': createdAt};

  static Product fromMap(Map map) =>
      Product(map['uuid'], map['title'], map['createdAt']);

  @override
  String toString() {
    return 'Product{uuid: $uuid, title: $title, createdAt: $createdAt}';
  }
}
