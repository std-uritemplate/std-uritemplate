import puppeteer from 'puppeteer';
import fs from 'fs';
import process from 'process';
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// ideally you want to load this as a module script, import the StdUriTemplate
// class and use it in the browser, however, puppeteer doesn't host the
// files in the browser page it creates. This content is now loaded in
// manually and tested.
const pkgContent = fs.readFileSync(path.join(__dirname, 'dist/index.mjs'), 'utf-8')

const templateFile = process.argv[2];
const dataFile = process.argv[3];

const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

const template = fs.readFileSync(templateFile, 'utf8').trim();

try {
  (async ()=>{
    const browser = await puppeteer.launch({headless: true});

    try {
      const page = await browser.newPage();

      await page.addScriptTag({
        // load the stduritemplate dist/index.js content into a module script tag
        content: `${pkgContent};window.StdUriTemplate = StdUriTemplate;`,
        type: "module"
      })

      const result = await page.evaluate((template, data) => {
        if (data["nativedate"] !== undefined) {
          data["nativedate"] = new Date(data["nativedate"]);
        }
        if (data["nativedatetwo"] !== undefined) {
          data["nativedatetwo"] = new Date(data["nativedatetwo"]);
        }
        const expansion = window.StdUriTemplate.expand(template, data)
        return expansion
      }, template, data)
      process.stdout.write(result);
    } catch (e) {
      process.stderr.write(`Error expanding template: ${e.message}\n`);
      process.stderr.write(`${e.stack}\n`);
      process.stdout.write('false\n');
      process.exit(0);
    } finally {
      await browser.close();
    }
  })()
} catch (e) {
  if (e.code === 'ENOENT') {
    process.stderr.write(`File '${e.path}' not found.\n`);
  } else {
    process.stderr.write(`Error parsing JSON data: ${e.message}\n`);
    process.stderr.write(JSON.stringify(e));
  }
  process.exit(1);
}
