import fs from 'fs';
import process from 'process';
import { StdUriTemplate } from './dist/index.cjs';

const templateFile = process.argv[2];
const dataFile = process.argv[3];

try {
  const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

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
