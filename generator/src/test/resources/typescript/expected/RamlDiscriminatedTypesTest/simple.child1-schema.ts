import {Child1} from './child1';
import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export const Child1Schema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'type': z.literal('Child1'),
    'value': z.string().optional(),
    'value1': z.number()
  });
  return z.codec(wireSchema, z.instanceof(Child1), {
    decode: (value) => new Child1({
      value: value['value'],
      value1: value['value1'],
    }),
    encode: (value) => ({
      'type': 'Child1',
      'value': value.value,
      'value1': value.value1,
    }) as z.infer<typeof wireSchema>,
  });
});
