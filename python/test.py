#!/usr/bin/env python3
import sys
import json
import traceback
from datetime import datetime, tzinfo, timedelta, timezone

from stduritemplate import StdUriTemplate

template_file = sys.argv[1]
data_file = sys.argv[2]

try:
    with open(data_file, "r") as file:
        data = json.load(file)
except FileNotFoundError:
    sys.stderr.write(f"File '{data_file}' not found.\n")
    sys.exit(1)
except json.JSONDecodeError as e:
    sys.stderr.write(f"Error parsing JSON data: {str(e)}\n")
    sys.exit(1)

if "nativedate" in data:
    data["nativedate"] = datetime.fromtimestamp(data["nativedate"] / 1000)
if "nativedatetwo" in data:
    data["nativedatetwo"] = datetime.fromtimestamp(data["nativedatetwo"] / 1000)
if "nativedatethree" in data:
    data["nativedatethree"] = (
        datetime.fromtimestamp(data["nativedatethree"] / 1000)
    ).replace(tzinfo=timezone(timedelta(hours=1)))
if "nativedatefour" in data:
    data["nativedatefour"] = (
        datetime.fromtimestamp(data["nativedatefour"] / 1000)
    ).replace(tzinfo=timezone(timedelta(hours=1)))

try:
    with open(template_file, "r") as file:
        template = file.read().strip()
except FileNotFoundError:
    sys.stderr.write(f"File '{template_file}' not found.\n")
    sys.exit(1)

try:
    result = StdUriTemplate.expand(template, data)
    sys.stdout.write(result)
except Exception as e:
    sys.stderr.write(f"Error expanding template: {str(e)}\n")
    print(traceback.format_exc(), file=sys.stderr)
    sys.stdout.write("false\n")
