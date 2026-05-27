import {Child1Schema} from './child1';
import {Child2Schema} from './child2';
import {Parent} from './parent';
import {SchemaOutput, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export type Test = SchemaOutput<typeof TestSchema>;

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'parent': z.custom<Parent>(),
    'parentType': z.string()
  });
  const externallyConstrainedWireSchema1 = z.discriminatedUnion('parentType', [
z.looseObject({ ...wireSchema.shape, 'parentType': z.literal('Child1'), 'parent': runtime.resolveSchema(Child1Schema) }),
z.looseObject({ ...wireSchema.shape, 'parentType': z.literal('child2'), 'parent': runtime.resolveSchema(Child2Schema) })
  ]);
  return externallyConstrainedWireSchema1;
});
