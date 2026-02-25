import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  int8: number;

  int16: number;

  int32: number;

  int64: number;

  int: number;

  long: number;

  none: number;

}

export class Test implements TestSpec {

  int8: number;

  int16: number;

  int32: number;

  int64: number;

  int: number;

  long: number;

  none: number;

  constructor(init: TestSpec) {
    this.int8 = init.int8;
    this.int16 = init.int16;
    this.int32 = init.int32;
    this.int64 = init.int64;
    this.int = init.int;
    this.long = init.long;
    this.none = init.none;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(int8='${this.int8}', int16='${this.int16}', int32='${this.int32}', int64='${this.int64}', int='${this.int}', long='${this.long}', none='${this.none}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'int8': z.number(),
    'int16': z.number(),
    'int32': z.number(),
    'int64': z.number(),
    'int': z.number(),
    'long': z.number(),
    'none': z.number()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      int8: value['int8'],
      int16: value['int16'],
      int32: value['int32'],
      int64: value['int64'],
      int: value['int'],
      long: value['long'],
      none: value['none'],
    }),
    encode: (value) => ({
      'int8': value.int8,
      'int16': value.int16,
      'int32': value.int32,
      'int64': value.int64,
      'int': value.int,
      'long': value.long,
      'none': value.none,
    }) as z.infer<typeof wireSchema>,
  });
});
