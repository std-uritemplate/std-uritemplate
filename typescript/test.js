const fs = require('fs');
const { StdUriTemplate } = require('./dist/index.js');
const process = require('process');

const templateFile = process.argv[2];
const dataFile = process.argv[3];

try {
  const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

  if (data["nativedate"] !== undefined) {
    process.stderr.write(`Converting to Date\n`);
    data["nativedate"] = new Date(data["nativedate"]);
  }

  const template = fs.readFileSync(templateFile, 'utf8').trim();

  try {
    const result = StdUriTemplate.expand(template, data);
    process.stdout.write(result);
  } catch (e) {
    process.stderr.write(`Error expanding template: ${e.message}\n`);
    process.stderr.write(`${e.stack}\n`);
    process.stdout.write('false\n');
    process.exit(0);
  }
} catch (e) {
  if (e.code === 'ENOENT') {
    process.stderr.write(`File '${e.path}' not found.\n`);
  } else {
    process.stderr.write(`Error parsing JSON data: ${e.message}\n`);
    process.stderr.write(e);
  }
  process.exit(1);
}
