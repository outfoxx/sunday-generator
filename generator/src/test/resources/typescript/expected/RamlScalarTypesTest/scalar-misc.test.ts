import {ArrayBufferSchema, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  bool: boolean;

  string: string;

  file: ArrayBuffer;

  any: any;

  nil: null;

}

export class Test implements TestSpec {

  bool: boolean;

  string: string;

  file: ArrayBuffer;

  any: any;

  nil: null;

  constructor(init: TestSpec) {
    this.bool = init.bool;
    this.string = init.string;
    this.file = init.file;
    this.any = init.any;
    this.nil = init.nil;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(bool='${this.bool}', string='${this.string}', file='${this.file}', any='${this.any}', nil='${this.nil}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'bool': z.boolean(),
    'string': z.string(),
    'file': runtime.resolveSchema(ArrayBufferSchema),
    'any': z.any(),
    'nil': z.null()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      bool: value['bool'],
      string: value['string'],
      file: value['file'],
      any: value['any'],
      nil: value['nil'],
    }),
    encode: (value) => ({
      'bool': value.bool,
      'string': value.string,
      'file': value.file,
      'any': value.any,
      'nil': value.nil,
    }) as z.infer<typeof wireSchema>,
  });
});
