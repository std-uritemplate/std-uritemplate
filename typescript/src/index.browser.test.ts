import { describe, expect, expectTypeOf, test } from 'vitest'
import { StdUriTemplate } from './index'
import specExamples from '../../uritemplate-test/spec-examples.json';
import specExamplesBySection from '../../uritemplate-test/spec-examples-by-section.json';
import extendedTests from '../../uritemplate-test/extended-tests.json';
import negativeTests from '../../uritemplate-test/negative-tests.json';
import edgeCases from '../../uritemplate-test-additional/edge-cases.json';

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

const edgeCasesLevels = [
  "Handling of boolean values",
  "Language native date-time format",
  "Nested primitives",
  "Unicode characters",
]

test('ensure browser mode is available', () => {
  expect(typeof window).not.toBe('undefined')
})

describe("test files", () => {
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

  test("negative-tests.json exists", () => {
    expect(negativeTests).toBeDefined();
    expect(Object.keys(negativeTests)).toEqual(negativeTestsLevels);
  })
  test("edge-cases.json exists", () => {
    expect(edgeCases).toBeDefined();
    expect(Object.keys(edgeCases)).toEqual(edgeCasesLevels);
  })
})

const files: Record<string, object> = {
  specExamples, specExamplesBySection, extendedTests, edgeCases
}

const testcaseRunners: Record<string, string[]>[] = [
  { 'specExamples': specExamplesLevels },
  { 'specExamplesBySection': specExamplesBySectionLevels },
  { 'extendedTests': extendedTestsLevels },
  { 'edgeCases': edgeCasesLevels }
]

testcaseRunners.forEach((testcaseRunner) => {
  const testName = Object.keys(testcaseRunner).pop() ?? '';
  const testData = files[testName]
  const levels = testcaseRunner[testName];

  describe.each(levels)('testing %s', (level: string) => {
    const testcases = testData[level]["testcases"]
    expect(testcases).toBeDefined();

    const variables = testData[level]["variables"]
    expect(variables).toBeDefined();
    if (variables["nativedate"] !== undefined) {
      const newDate = new Date(variables["nativedate"])
      Object.assign(variables, { "nativedate": newDate })

    }
    if (variables["nativedatetwo"] !== undefined) {
      const newDate = new Date(variables["nativedatetwo"])
      Object.assign(variables, { "nativedatetwo": newDate })
    }

    testcases.forEach((testcase: Array<Array<any>>) => {
      const template = testcase[0] as unknown as string;
      const expected = testcase[1] as unknown as string;
      test(`StdUriTemplate.expand(${template}, ${JSON.stringify(variables)})`, () => {
        const result = StdUriTemplate.expand(template, variables);
        if (typeof expected === 'string') {
          expectTypeOf(expected).toBeString;
          expect(result).toBe(expected)
        } else if (Array.isArray(expected)) {
          expectTypeOf(expected).toBeArray;
          expect(expected).toContain(result);
        }
      })
    });
  })
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
      if (typeof expected === 'string') {
        expectTypeOf(expected).toBeString;
        expect(result).toBe(expected)
      } else if (Array.isArray(expected)) {
        expectTypeOf(expected).toBeArray;
        expect(expected).toContain(result);
      }
    })
  });
})