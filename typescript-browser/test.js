const puppeteer = require('puppeteer');
const fs = require('fs');
const process = require('process');

(async () => {
  const templateFile = process.argv[2];
  const dataFile = process.argv[3];
  process.stdout.write(JSON.stringify({ templateFile, dataFile }))
  try {
    const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

    if (data["nativedate"] !== undefined) {
      data["nativedate"] = new Date(data["nativedate"]);
    }
    if (data["nativedatetwo"] !== undefined) {
      data["nativedatetwo"] = new Date(data["nativedatetwo"]);
    }

    const template = fs.readFileSync(templateFile, 'utf8').trim();
    const browser = await puppeteer.launch();
    
    try {
      const page = await browser.newPage();
      const content = `import {StdUriTemplate} from './dist/index.js';window.StdUriTemplate = StdUriTemplate;window.template=${template};window.data=${JSON.stringify(data)}`
      process.stdout.write(content)
      await page.addScriptTag({
        content,
        type: "module",
        src: "./dist/index.js"
      })
      const result = await page.evaluate(()=>{
        const template = window.template;
        const data = JSON.parse(window.data);
        return window.StdUriTemplate.expand("{var}", {"var":"value","hello":"Hello World!"})
        // return window.StdUriTemplate.expand(template, data)
      })
      process.stdout.write(result.toString())
      await browser.close();
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
      process.stderr.write(JSON.stringify(e));
    }
    process.exit(1);
  }

})();