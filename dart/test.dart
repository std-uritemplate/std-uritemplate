#!/usr/bin/env dart

import 'dart:convert';
import 'dart:io';

import 'std_uri_template.dart';

Future<void> main(List<String> args) async {
  if (args.length != 2) {
    stderr.write("Usage: test.dart <template_file> <data_file>\n");
    exit(1);
  }

  final templateFile = args[0];
  final dataFile = args[1];

  try {
    final data = jsonDecode(File(dataFile).readAsStringSync());
    if (data is Map && data.containsKey("nativedate")) {
      data["nativedate"] =
          DateTime.fromMillisecondsSinceEpoch(data["nativedate"]);
    }
    if (data is Map && data.containsKey("nativedatetwo")) {
      data["nativedatetwo"] =
          DateTime.fromMillisecondsSinceEpoch(data["nativedatetwo"]);
    }

    final template = File(templateFile).readAsStringSync().trim();
    final result = StdUriTemplate.expand(template, data);
    print(result);
  } catch (FileNotFoundError) {
    stderr.write("File '$dataFile' not found.\n");
    exit(1);
  }
}
