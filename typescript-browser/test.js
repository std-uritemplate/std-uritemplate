const puppeteer = require('puppeteer');
const fs = require('fs');
const process = require('process');
const { pathToFileURL } = require('node:url');
const {StdUriTemplate} = require('./dist/index.js')

const htmlFilePath = pathToFileURL('./typescript-browser/test.html');

(async () => {
  const templateFile = process.argv[2];
  const dataFile = process.argv[3];
  process.stdout.write(JSON.stringify({ templateFile, dataFile }))
  try {
    // const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

    // if (data["nativedate"] !== undefined) {
    //   data["nativedate"] = new Date(data["nativedate"]);
    // }
    // if (data["nativedatetwo"] !== undefined) {
    //   data["nativedatetwo"] = new Date(data["nativedatetwo"]);
    // }

    // const template = fs.readFileSync(templateFile, 'utf8').trim();
    const browser = await puppeteer.launch();
    const page = await browser.newPage();

    try {
      // const expandFunction = ()=>{
      //   window.StdUriTemplate = StdUriTemplate
      // }
      // await page.evaluateOnNewDocument(expandFunction, template, data);
      // await page.goto(htmlFilePath);
      // const result = await page.evaluate(() => {
        // console.log(window.template, window.data);
        // return window.StdUriTemplate.expand(window.template, window.data);
      // });
      // const result = await page.evaluateHandle(()=> StdUriTemplate.expand(template, data))
      await page.exposeFunction('readfile', filePath => {
        return fs.readFileSync(filePath, 'utf8')
      });

      await page.exposeFunction('expand', (template, data)=> StdUriTemplate.expand(template,data))

      const result = await page.evaluate((dataFile,templateFile)=> {
        const data = JSON.parse(window.readfile(dataFile));

        if (data["nativedate"] !== undefined) {
          data["nativedate"] = new Date(data["nativedate"]);
        }
        if (data["nativedatetwo"] !== undefined) {
          data["nativedatetwo"] = new Date(data["nativedatetwo"]);
        }

        const template = window.readfile(templateFile).trim();

        return window.expand(template, data)

      },dataFile,templateFile)
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