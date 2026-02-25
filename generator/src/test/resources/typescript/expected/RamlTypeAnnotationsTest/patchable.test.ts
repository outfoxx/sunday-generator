import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  string: string;

  int: number;

  bool: boolean;

  nullable: string | null;

  optional?: string;

  nullableOptional?: string | null;

}

export class Test implements TestSpec {

  string: string;

  int: number;

  bool: boolean;

  nullable: string | null;

  optional: string | undefined;

  nullableOptional: string | null | undefined;

  constructor(init: TestSpec) {
    this.string = init.string;
    this.int = init.int;
    this.bool = init.bool;
    this.nullable = init.nullable;
    this.optional = init.optional;
    this.nullableOptional = init.nullableOptional;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(string='${this.string}', int='${this.int}', bool='${this.bool}', nullable='${this.nullable}', optional='${this.optional}', nullableOptional='${this.nullableOptional}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'string': z.string(),
    'int': z.number(),
    'bool': z.boolean(),
    'nullable': z.string().nullable(),
    'optional': z.string().optional(),
    'nullableOptional': z.string().nullable().optional()
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      string: value['string'],
      int: value['int'],
      bool: value['bool'],
      nullable: value['nullable'],
      optional: value['optional'],
      nullableOptional: value['nullableOptional'],
    }),
    encode: (value) => ({
      'string': value.string,
      'int': value.int,
      'bool': value.bool,
      'nullable': value.nullable,
      'optional': value.optional,
      'nullableOptional': value.nullableOptional,
    }) as z.infer<typeof wireSchema>,
  });
});
