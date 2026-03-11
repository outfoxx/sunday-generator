import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  fromNilUnion: string | null;

  notRequired?: string;

}

export class Test implements TestSpec {

  fromNilUnion: string | null;

  notRequired: string | undefined;

  constructor(init: TestSpec) {
    this.fromNilUnion = init.fromNilUnion;
    this.notRequired = init.notRequired;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(fromNilUnion='${this.fromNilUnion}', notRequired='${this.notRequired}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'fromNilUnion': z.string().nullable(),
    'notRequired': z.string().optional()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      fromNilUnion: value['fromNilUnion'],
      notRequired: value['notRequired'],
    }),
    encode: (value) => ({
      'fromNilUnion': value.fromNilUnion,
      'notRequired': value.notRequired,
    }) as z.infer<typeof wireSchema>,
  });
});
