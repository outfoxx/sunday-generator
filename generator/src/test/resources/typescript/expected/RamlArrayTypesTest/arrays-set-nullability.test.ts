import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  requiredSet: Set<string>;

  optionalSet?: Set<string>;

  nullableSet: Set<string> | null;

  nullableOptionalSet?: Set<string> | null;

}

export class Test implements TestSpec {

  requiredSet: Set<string>;

  optionalSet: Set<string> | undefined;

  nullableSet: Set<string> | null;

  nullableOptionalSet: Set<string> | null | undefined;

  constructor(init: TestSpec) {
    this.requiredSet = init.requiredSet;
    this.optionalSet = init.optionalSet;
    this.nullableSet = init.nullableSet;
    this.nullableOptionalSet = init.nullableOptionalSet;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(requiredSet='${this.requiredSet}', optionalSet='${this.optionalSet}', nullableSet='${this.nullableSet}', nullableOptionalSet='${this.nullableOptionalSet}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'requiredSet': z.array(z.string()),
    'optionalSet': z.array(z.string()).optional(),
    'nullableSet': z.array(z.string()).nullable(),
    'nullableOptionalSet': z.array(z.string()).nullable().optional()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      requiredSet: new Set(value['requiredSet']),
      optionalSet: value['optionalSet'] !== undefined ? new Set(value['optionalSet']) : undefined,
      nullableSet: value['nullableSet'] === null ? null : new Set(value['nullableSet']),
      nullableOptionalSet: value['nullableOptionalSet'] === undefined ? undefined : value['nullableOptionalSet'] === null ? null : new Set(value['nullableOptionalSet']),
    }),
    encode: (value) => ({
      'requiredSet': Array.from(value.requiredSet),
      'optionalSet': value.optionalSet !== undefined ? Array.from(value.optionalSet) : undefined,
      'nullableSet': value.nullableSet === null ? null : Array.from(value.nullableSet),
      'nullableOptionalSet': value.nullableOptionalSet === undefined ? undefined : value.nullableOptionalSet === null ? null : Array.from(value.nullableOptionalSet),
    }) as z.infer<typeof wireSchema>,
  });
});
