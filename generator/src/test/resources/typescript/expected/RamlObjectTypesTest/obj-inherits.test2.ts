import {Test, TestSpec} from './test';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface Test2Spec extends TestSpec {

  value2: string;

}

export class Test2 extends Test implements Test2Spec {

  value2: string;

  constructor(init: Test2Spec) {
    super(init);
    this.value2 = init.value2;
  }

  copy(changes: Partial<Test2Spec>): Test2 {
    return new Test2(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test2(value='${this.value}', value2='${this.value2}')`;
  }

}

export const Test2Schema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'value': z.string(),
    'value2': z.string()
  });
  return z.codec(wireSchema, z.instanceof(Test2), {
    decode: (value) => new Test2({
      value: value['value'],
      value2: value['value2'],
    }),
    encode: (value) => ({
      'value': value.value,
      'value2': value.value2,
    }) as z.infer<typeof wireSchema>,
  });
});
