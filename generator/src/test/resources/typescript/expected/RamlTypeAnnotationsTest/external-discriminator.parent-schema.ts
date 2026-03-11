import {Parent} from './parent';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export const ParentSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({});
  return z.codec(wireSchema, z.instanceof(Parent), {
    decode: () => { throw new TypeError(`Parent requires external discriminator`); },
    encode: (value) => value as unknown as Record<string, unknown>,
  });
});
