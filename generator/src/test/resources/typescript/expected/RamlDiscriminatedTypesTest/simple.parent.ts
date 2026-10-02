import {Child1Schema} from './child1';
import {Child2Schema} from './child2';
import {SchemaOutput, SchemaRuntime, defineModelSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export type Parent = SchemaOutput<typeof ParentSchema>;

export const ParentSchema = defineModelSchema((runtime: SchemaRuntime) => {
  const branchesSchema = z.union([
    runtime.resolveSchema(Child1Schema),
    runtime.resolveSchema(Child2Schema)
  ]);
  const wireSchema = branchesSchema;
  return wireSchema;
});
