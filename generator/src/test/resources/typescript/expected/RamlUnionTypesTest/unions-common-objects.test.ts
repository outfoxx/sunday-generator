import {Base, BaseSchema} from './base';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  value: Base;

}

export class Test implements TestSpec {

  value: Base;

  constructor(init: TestSpec) {
    this.value = init.value;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(value='${this.value}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'value': z.lazy(() => runtime.resolveSchema(BaseSchema))
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      value: value['value'],
    }),
    encode: (value) => ({
      'value': value.value,
    }) as z.infer<typeof wireSchema>,
  });
});
