import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  value: string;

}

export class Test implements TestSpec {

  value: string;

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
    'value': z.string()
  });
  const minPropsSchema = wireSchema.refine((value) => Object.keys(value).length >= 1, { message: 'Must have at least 1 properties' });
  const constrainedWireSchema = minPropsSchema.refine((value) => Object.keys(value).length <= 3, { message: 'Must have at most 3 properties' });
  return z.codec(constrainedWireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      value: value['value'],
    }),
    encode: (value) => ({
      'value': value.value,
    }) as z.infer<typeof constrainedWireSchema>,
  });
});
