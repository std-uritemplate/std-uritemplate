#!/usr/bin/env dart

import 'dart:convert';
import 'dart:io';

import 'lib/std_uritemplate.dart';

Future<void> main(List<String> args) async {
  if (args.length != 2) {
    stderr.write('Usage: test.dart <template_file> <data_file>\n');
    exit(1);
  }

  final templateFile = args[0];
  final dataFile = args[1];

  final Map<String, Object?> data;

  try {
    data =
        jsonDecode(File(dataFile).readAsStringSync()) as Map<String, Object?>;
  } catch (e, stack) {
    stderr.write('Error loading data file: $e\n$stack\n');
    exit(1);
  }

  if (data['nativedate'] case final int nativeDate) {
    data['nativedate'] = DateTime.fromMillisecondsSinceEpoch(nativeDate, isUtc:true);
  }

  if (data['nativedatetwo'] case final int nativeDateTwo) {
    data['nativedatetwo'] = DateTime.fromMillisecondsSinceEpoch(nativeDateTwo, isUtc:true);
  }

  final String template;
  try {
    template = File(templateFile).readAsStringSync().trim();
  } catch (e, stack) {
    stderr.write('Error loading template file: $e\n$stack\n');
    exit(1);
  }

  try {
    final result = StdUriTemplate.expand(template, data);
    print(result);
  } catch (e, stack) {
    stderr.write('Error: $e\n$stack\n');

    print('false');
  }
}
