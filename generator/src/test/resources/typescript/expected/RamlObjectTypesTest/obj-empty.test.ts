import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {
}

export class Test implements TestSpec {
}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({});
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: () => new Test(),
    encode: (value) => ({

    }) as z.infer<typeof wireSchema>,
  });
});
