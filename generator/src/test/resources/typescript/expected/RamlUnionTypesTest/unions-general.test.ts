import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  any: number | string;

  duplicate: string;

  nullable: string | null;

}

export class Test implements TestSpec {

  any: number | string;

  duplicate: string;

  nullable: string | null;

  constructor(init: TestSpec) {
    this.any = init.any;
    this.duplicate = init.duplicate;
    this.nullable = init.nullable;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(any='${this.any}', duplicate='${this.duplicate}', nullable='${this.nullable}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'any': z.union([z.number(), z.string()]),
    'duplicate': z.string(),
    'nullable': z.string().nullable()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      any: value['any'],
      duplicate: value['duplicate'],
      nullable: value['nullable'],
    }),
    encode: (value) => ({
      'any': value.any,
      'duplicate': value.duplicate,
      'nullable': value.nullable,
    }) as z.infer<typeof wireSchema>,
  });
});
