import {TestEnum, TestEnumSchema} from './test-enum';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  enumVal: TestEnum;

  setVal: Set<TestEnum>;

  arrayVal: Array<TestEnum>;

}

export class Test implements TestSpec {

  enumVal: TestEnum;

  setVal: Set<TestEnum>;

  arrayVal: Array<TestEnum>;

  constructor(init: TestSpec) {
    this.enumVal = init.enumVal;
    this.setVal = init.setVal;
    this.arrayVal = init.arrayVal;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(enumVal='${this.enumVal}', setVal='${this.setVal}', arrayVal='${this.arrayVal}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'enumVal': runtime.resolveSchema(TestEnumSchema),
    'setVal': z.array(runtime.resolveSchema(TestEnumSchema)),
    'arrayVal': z.array(runtime.resolveSchema(TestEnumSchema))
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      enumVal: value['enumVal'],
      setVal: new Set(value['setVal']),
      arrayVal: value['arrayVal'],
    }),
    encode: (value) => ({
      'enumVal': value.enumVal,
      'setVal': Array.from(value.setVal),
      'arrayVal': value.arrayVal,
    }) as z.infer<typeof wireSchema>,
  });
});
