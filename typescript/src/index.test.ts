import { describe, expect, expectTypeOf, test } from 'vitest'
import { StdUriTemplate } from './index'
import specExamples from '../../uritemplate-test/spec-examples.json';
import specExamplesBySection from '../../uritemplate-test/spec-examples-by-section.json';
import extendedTests from '../../uritemplate-test/extended-tests.json';
import negativeTests from '../../uritemplate-test/negative-tests.json';

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

const extendedTestsLevels = [
  "Additional Examples 1",
  "Additional Examples 2",
  "Additional Examples 3: Empty Variables",
  "Additional Examples 4: Numeric Keys",
  "Additional Examples 5: Explode Combinations",
  "Additional Examples 6: Reserved Expansion",
]

const negativeTestsLevels = [
  "Failure Tests"
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

  test("extended-tests.json exists", () => {
    expect(extendedTests).toBeDefined();
    expect(Object.keys(extendedTests)).toEqual(extendedTestsLevels);
  })

  test("negativeTests.json exists", () => {
    expect(negativeTests).toBeDefined();
    expect(Object.keys(negativeTests)).toEqual(negativeTestsLevels);
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
    test(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      if(typeof expected === 'string'){
        expectTypeOf(result).toBeString;
        expect(result).toBe(expected)
      }else if(Array.isArray(expected)){
        expectTypeOf(expected).toBeArray;
        expect(expected).toContain(result);
      }
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
        expect(expected).toContain(result);
      }
    })
  });
})

describe.each(negativeTestsLevels)('testing %s', (level: string) => {
  const testcases = negativeTests[level]["testcases"]
  expect(testcases).toBeDefined();

  const variables = negativeTests[level]["variables"]
  expect(variables).toBeDefined();

  testcases.forEach((testcase: Array<Array<any>>) => {
    const template = testcase[0] as unknown as string;
    const expected = testcase[1] as unknown;
    test.fails(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
      const result = StdUriTemplate.expand(template, variables);
      if(typeof expected === 'string'){
        expectTypeOf(result).toBeString;
        expect(result).toBe(expected)
      }else if(Array.isArray(expected)){
        expectTypeOf(expected).toBeArray;
        expect(expected).toContain(result);
      }
    })
  });
})