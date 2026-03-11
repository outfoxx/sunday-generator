import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  pattern: string;

  min: string;

  max: string;

}

export class Test implements TestSpec {

  pattern: string;

  min: string;

  max: string;

  constructor(init: TestSpec) {
    this.pattern = init.pattern;
    this.min = init.min;
    this.max = init.max;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(pattern='${this.pattern}', min='${this.min}', max='${this.max}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'pattern': z.string().regex(/^[a-zA-Z0-9]+$/),
    'min': z.string().min(5),
    'max': z.string().max(10)
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      pattern: value['pattern'],
      min: value['min'],
      max: value['max'],
    }),
    encode: (value) => ({
      'pattern': value.pattern,
      'min': value.min,
      'max': value.max,
    }) as z.infer<typeof wireSchema>,
  });
});
