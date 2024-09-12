#!/usr/bin/env php
<?php
use StdUriTemplate\StdUriTemplate;

require_once __DIR__.'/src/StdUriTemplate.php';

if ($argc != 3) {
    fwrite(STDERR, "Usage: " . $argv[0] . " <template_file> <data_file>\n");
    exit(1);
}

$template_file = $argv[1];
$data_file = $argv[2];

try {
    $data = json_decode(file_get_contents($data_file), true, 512, JSON_THROW_ON_ERROR);
} catch (FileNotFoundException $e) {
    fwrite(STDERR, "File '$data_file' not found.\n");
    exit(1);
} catch (JsonException $e) {
    fwrite(STDERR, "Error parsing JSON data: " . $e->getMessage() . "\n");
    exit(1);
}

if (array_key_exists("nativedate", $data)) {
    $data["nativedate"] = \DateTime::createFromFormat("U\.u", sprintf('%1.6F', $data["nativedate"]/1000.0));
}
if (array_key_exists("nativedatetwo", $data)) {
    $data["nativedatetwo"] = \DateTime::createFromFormat("U\.u", sprintf('%1.6F', $data["nativedatetwo"]/1000.0));
    $data["nativedatetwo"]->setTimezone(new \DateTimeZone("UTC"));
}
if (array_key_exists("nativedatethree", $data)) {
    $data["nativedatethree"] = \DateTime::createFromFormat("U\.u", sprintf('%1.6F', $data["nativedatethree"]/1000.0 - 3600));
    $data["nativedatethree"]->setTimezone(new \DateTimeZone("Europe/Rome"));
}
if (array_key_exists("nativedatefour", $data)) {
    $data["nativedatefour"] = \DateTime::createFromFormat("U\.u", sprintf('%1.6F', $data["nativedatefour"]/1000.0 - 3600));
    $data["nativedatefour"]->setTimezone(new \DateTimeZone("Europe/Rome"));
}

try {
    $template = file_get_contents($template_file);
    $template = trim($template);
} catch (FileNotFoundException $e) {
    fwrite(STDERR, "File '$template_file' not found.\n");
    exit(1);
}

try {
    $result = StdUriTemplate::expand($template, $data);
    echo $result;
} catch (Exception $e) {
    fwrite(STDERR, "Error expanding template: " . $e->getMessage() . "\n");
    echo "false\n";
}

?>
