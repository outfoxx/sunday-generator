import {SchemaOutput, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export type Child1 = SchemaOutput<typeof Child1Schema>;

export const Child1Schema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'type': z.literal('Child1'),
    'value': z.string().nullish(),
    'value1': z.number()
  });
  return wireSchema;
});
