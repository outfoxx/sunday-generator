import {ArrayBufferSchema, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  binary: ArrayBuffer;

  nullableBinary: ArrayBuffer | null;

}

export class Test implements TestSpec {

  binary: ArrayBuffer;

  nullableBinary: ArrayBuffer | null;

  constructor(init: TestSpec) {
    this.binary = init.binary;
    this.nullableBinary = init.nullableBinary;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(binary='${this.binary}', nullableBinary='${this.nullableBinary}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'binary': runtime.resolveSchema(ArrayBufferSchema),
    'nullableBinary': runtime.resolveSchema(ArrayBufferSchema).nullable()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      binary: value['binary'],
      nullableBinary: value['nullableBinary'],
    }),
    encode: (value) => ({
      'binary': value.binary,
      'nullableBinary': value.nullableBinary,
    }) as z.infer<typeof wireSchema>,
  });
});
