import 'package:std_uritemplate/std_uritemplate.dart';

void main() {
  final substitutes = <String, Object?>{
    'id': 'person',
    'fields': ['id', 'name', 'picture'],
    'long': 37.76,
    'lat': -122.427,
    'nullable': null,
  };

  // prints: /person
  print(StdUriTemplate.expand('{/id*}', substitutes));

  // prints: /person?fields=id,name,picture
  print(StdUriTemplate.expand('{/id*}{?fields,nullable}', substitutes));
}
