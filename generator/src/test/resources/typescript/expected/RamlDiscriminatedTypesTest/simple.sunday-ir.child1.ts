import {SchemaOutput, SchemaRuntime, defineModelSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export type Child1 = SchemaOutput<typeof Child1Schema>;

export const Child1Schema = defineModelSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'type': z.literal('Child1'),
    'value': z.string().optional(),
    'value1': z.number()
  });
  const preservedSchema = z.codec(z.custom<z.input<typeof wireSchema>>(), z.custom<z.output<typeof wireSchema>>(), {
    decode: (value, context) => {
      const result = wireSchema.safeParse(value);
      if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
      const entries: [string, unknown][] = Object.entries(result.data);
      if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
        const item = (value as {[key: string]: unknown})['__proto__'];
        entries.push(['__proto__', item]);
      }
      return Object.fromEntries(entries) as z.output<typeof wireSchema>;
    },
    encode: (value, context) => {
      const result = wireSchema.safeEncode(value);
      if (!result.success) { context.issues.push(...result.error.issues.map(issue => ({...issue, input: undefined}))); return z.NEVER; }
      const entries: [string, unknown][] = Object.entries(result.data);
      if (typeof value === 'object' && value !== null && Object.hasOwn(value, '__proto__')) {
        const item = (value as {[key: string]: unknown})['__proto__'];
        entries.push(['__proto__', item]);
      }
      return Object.fromEntries(entries) as z.input<typeof wireSchema>;
    },
  });
  return preservedSchema;
});
