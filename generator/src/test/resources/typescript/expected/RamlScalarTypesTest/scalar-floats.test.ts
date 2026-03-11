import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  float: number;

  double: number;

  none: number;

}

export class Test implements TestSpec {

  float: number;

  double: number;

  none: number;

  constructor(init: TestSpec) {
    this.float = init.float;
    this.double = init.double;
    this.none = init.none;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(float='${this.float}', double='${this.double}', none='${this.none}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'float': z.number(),
    'double': z.number(),
    'none': z.number()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      float: value['float'],
      double: value['double'],
      none: value['none'],
    }),
    encode: (value) => ({
      'float': value.float,
      'double': value.double,
      'none': value.none,
    }) as z.infer<typeof wireSchema>,
  });
});
