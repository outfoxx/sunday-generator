import {Test as Test_, TestSpec as TestSpec_} from './test/client/test';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec extends TestSpec_ {

  value2: string;

}

export class Test extends Test_ implements TestSpec {

  value2: string;

  constructor(init: TestSpec) {
    super(init);
    this.value2 = init.value2;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(value='${this.value}', value2='${this.value2}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'value': z.string(),
    'value2': z.string()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      value: value['value'],
      value2: value['value2'],
    }),
    encode: (value) => ({
      'value': value.value,
      'value2': value.value2,
    }) as z.infer<typeof wireSchema>,
  });
});
