import {Child1Schema} from './child1';
import {Child2Schema} from './child2';
import {Parent} from './parent';
import {SchemaOutput, SchemaRuntime, defineModelSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export type Test = SchemaOutput<typeof TestSchema>;

export const TestSchema = defineModelSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'parent': z.custom<Parent>(),
    'parentType': z.string()
  });
  const externallyConstrainedWireSchema1 = z.discriminatedUnion('parentType', [
wireSchema.extend({ 'parentType': z.literal('Child1'), 'parent': runtime.resolveSchema(Child1Schema) }),
wireSchema.extend({ 'parentType': z.literal('child2'), 'parent': runtime.resolveSchema(Child2Schema) })
  ]);
  const preservedSchema = z.codec(z.custom<z.input<typeof externallyConstrainedWireSchema1>>(), z.custom<z.output<typeof externallyConstrainedWireSchema1>>(), {
    decode: (value, context) => {
      const result = externallyConstrainedWireSchema1.safeParse(value);
      if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
      const entries: [string, unknown][] = Object.entries(result.data);
      if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
        const item = (value as {[key: string]: unknown})['__proto__'];
        entries.push(['__proto__', item]);
      }
      return Object.fromEntries(entries) as z.output<typeof externallyConstrainedWireSchema1>;
    },
    encode: (value, context) => {
      const result = externallyConstrainedWireSchema1.safeEncode(value);
      if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
      const entries: [string, unknown][] = Object.entries(result.data);
      if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
        const item = (value as {[key: string]: unknown})['__proto__'];
        entries.push(['__proto__', item]);
      }
      return Object.fromEntries(entries) as z.input<typeof externallyConstrainedWireSchema1>;
    },
  });
  return preservedSchema;
});
