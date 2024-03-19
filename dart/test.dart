#!/usr/bin/env dart

import 'dart:convert';
import 'dart:io';

import 'lib/std_uritemplate.dart';

Future<void> main(List<String> args) async {
  if (args.length != 2) {
    stderr.write("Usage: test.dart <template_file> <data_file>\n");
    exit(1);
  }

  final templateFile = args[0];
  final dataFile = args[1];

  final dynamic data;
  try {
    data = jsonDecode(File(dataFile).readAsStringSync());
  } catch (e, stack) {
    stderr.write("Error loading data file: $e\n$stack\n");
    exit(1);
  }

  if (data is Map) {
    if (data.containsKey("nativedate")) {
      data["nativedate"] =
          DateTime.fromMillisecondsSinceEpoch(data["nativedate"]);
    }

    if (data.containsKey("nativedatetwo")) {
      data["nativedatetwo"] =
          DateTime.fromMillisecondsSinceEpoch(data["nativedatetwo"]);
    }

    if (data.containsKey("uuid")) {
      // there are no native UUID in Dart: https://github.com/dart-lang/sdk/issues/49487
    }
  }

  final String template;
  try {
    template = File(templateFile).readAsStringSync().trim();
  } catch (e, stack) {
    stderr.write("Error loading template file: $e\n$stack\n");
    exit(1);
  }

  try {
    final result = StdUriTemplate.expand(template, data);
    print(result);
  } catch (e, stack) {
    stderr.write("Error: $e\n$stack\n");

    print("false");
  }
}
