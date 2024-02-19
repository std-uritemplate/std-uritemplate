import { describe, expect, expectTypeOf, test } from 'vitest'
import { StdUriTemplate } from './index'
import specExamples from '../../uritemplate-test/spec-examples.json';
import specExamplesBySection from '../../uritemplate-test/spec-examples-by-section.json';

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

const specExamplesBySectionLevels = [
  "2.1 Literals",
  "3.2.1 Variable Expansion",
  "3.2.2 Simple String Expansion",
  "3.2.3 Reserved Expansion",
  "3.2.4 Fragment Expansion",
  "3.2.5 Label Expansion with Dot-Prefix",
  "3.2.6 Path Segment Expansion",
  "3.2.7 Path-Style Parameter Expansion",
  "3.2.8 Form-Style Query Expansion",
  "3.2.9 Form-Style Query Continuation",
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

  test("spec-examples-by-json.json exists", () => {
    expect(specExamplesBySection).toBeDefined();
    expect(Object.keys(specExamplesBySection)).toEqual(specExamplesBySectionLevels);
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
      expectTypeOf(result).toBeString;
      expect(result).toBe(expected)
    })

    test.runIf(Array.isArray(expected))(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      expectTypeOf(expected).toBeArray;
      expect(result).toStrictEqual(expected.pop());
    })
  });
})

describe.each(specExamplesBySectionLevels)('testing %s', (level: string) => {
  const testcases = specExamplesBySection[level]["testcases"]
  expect(testcases).toBeDefined();

  const variables = specExamplesBySection[level]["variables"]
  expect(variables).toBeDefined();

  testcases.forEach((testcase: Array<Array<any>>) => {
    const template = testcase[0] as unknown as string;
    const expected = testcase[1] as unknown;
    test(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      if(typeof expected === 'string'){
        expectTypeOf(result).toBeString;
        expect(result).toBe(expected)
      }else if(Array.isArray(expected)){
        expectTypeOf(expected).toBeArray;
        expect(result).toStrictEqual(expected.pop());
      }
    })
  });
})