import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  map: Record<string, unknown>;

  array: Array<unknown>;

}

export class Test implements TestSpec {

  map: Record<string, unknown>;

  array: Array<unknown>;

  constructor(init: TestSpec) {
    this.map = init.map;
    this.array = init.array;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(map='${this.map}', array='${this.array}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'map': z.record(z.string(), z.unknown()),
    'array': z.array(z.unknown())
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      map: value['map'],
      array: value['array'],
    }),
    encode: (value) => ({
      'map': value.map,
      'array': value.array,
    }) as z.infer<typeof wireSchema>,
  });
});
