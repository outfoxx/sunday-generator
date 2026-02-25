import {Child1Schema} from './child1-schema';
import {Child2Schema} from './child2-schema';
import {Parent} from './parent';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export const ParentSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.discriminatedUnion('type', [
runtime.resolveSchema(Child1Schema),
runtime.resolveSchema(Child2Schema)
  ]);
  return z.codec(wireSchema, z.instanceof(Parent), {
    decode: (value) => value as Parent,
    encode: (value) => value as z.infer<typeof wireSchema>,
  });

});
