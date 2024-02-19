import { describe, expect, test } from 'vitest'
import { StdUriTemplate } from './index'
import specExamples from '../../uritemplate-test/spec-examples.json'

const levels = {
  "level": 1,
  "variables": {
    "var": "value",
    "hello": "Hello World!"
  },
  "testcases": [
    ["{var}", "value"],
    ["'{var}'", "'value'"],
    ["{hello}", "Hello%20World%21"]
  ]
}

const specExamplesLevels = [
  "Level 1 Examples",
  "Level 2 Examples",
  "Level 3 Examples",
  "Level 4 Examples",
]

test('ensure browser mode is available', () => {
  expect(typeof window).not.toBe('undefined')
})

describe("StdUriTemplate - expand", () => {
  test("manual", () => {
    const template = levels.testcases[0][0]
    const expected = levels.testcases[0][1]
    const data = levels.variables
    const result = StdUriTemplate.expand(template, data);
    expect(result).toBe(expected)
  })

  test("spec-examples.json exists", () => {

    expect(specExamples).toBeDefined();
    expect(Object.keys(specExamples)).toEqual(specExamplesLevels);
  })

})

describe.each(specExamplesLevels)('testing %s', (level: string) => {
  const testcases = specExamples[level]["testcases"]
  expect(testcases).toBeDefined();

  const variables = specExamples[level]["variables"]
  expect(variables).toBeDefined();

  testcases.forEach((testcase: Array<Array<any>>) => {
    const template = testcase[0] as unknown as string;
    const expected = testcase[1] as unknown;
    test.runIf(typeof expected === 'string')(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      expect(result).toBe(expected)
    })

    test.runIf(Array.isArray(expected))(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      expect(result).toMatchObject(Array.from(expected))
    })
  });
})