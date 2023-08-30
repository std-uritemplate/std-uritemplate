#!/usr/bin/env php
<?php
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

try {
    $template = file_get_contents($template_file);
    $template = trim($template);
} catch (FileNotFoundException $e) {
    fwrite(STDERR, "File '$template_file' not found.\n");
    exit(1);
}

try {
    require_once __DIR__.'/src/StdUriTemplate.php';
    $result = StdUriTemplate::expand($template, $data);
    echo $result;
} catch (Exception $e) {
    fwrite(STDERR, "Error expanding template: " . $e->getMessage() . "\n");
    echo "false\n";
}

?>
