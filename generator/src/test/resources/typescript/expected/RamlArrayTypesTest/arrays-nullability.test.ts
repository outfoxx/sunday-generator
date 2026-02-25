import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  arrayOfStrings: Array<string>;

  arrayOfNullableStrings: Array<string | null>;

  nullableArrayOfStrings: Array<string> | null;

  nullableArrayOfNullableStrings: Array<string | null> | null;

  declaredArrayOfStrings: Array<string>;

  declaredArrayOfNullableStrings: Array<string | null>;

}

export class Test implements TestSpec {

  arrayOfStrings: Array<string>;

  arrayOfNullableStrings: Array<string | null>;

  nullableArrayOfStrings: Array<string> | null;

  nullableArrayOfNullableStrings: Array<string | null> | null;

  declaredArrayOfStrings: Array<string>;

  declaredArrayOfNullableStrings: Array<string | null>;

  constructor(init: TestSpec) {
    this.arrayOfStrings = init.arrayOfStrings;
    this.arrayOfNullableStrings = init.arrayOfNullableStrings;
    this.nullableArrayOfStrings = init.nullableArrayOfStrings;
    this.nullableArrayOfNullableStrings = init.nullableArrayOfNullableStrings;
    this.declaredArrayOfStrings = init.declaredArrayOfStrings;
    this.declaredArrayOfNullableStrings = init.declaredArrayOfNullableStrings;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(arrayOfStrings='${this.arrayOfStrings}', arrayOfNullableStrings='${this.arrayOfNullableStrings}', nullableArrayOfStrings='${this.nullableArrayOfStrings}', nullableArrayOfNullableStrings='${this.nullableArrayOfNullableStrings}', declaredArrayOfStrings='${this.declaredArrayOfStrings}', declaredArrayOfNullableStrings='${this.declaredArrayOfNullableStrings}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'arrayOfStrings': z.array(z.string()),
    'arrayOfNullableStrings': z.array(z.string().nullable()),
    'nullableArrayOfStrings': z.array(z.string()).nullable(),
    'nullableArrayOfNullableStrings': z.array(z.string().nullable()).nullable(),
    'declaredArrayOfStrings': z.array(z.string()),
    'declaredArrayOfNullableStrings': z.array(z.string().nullable())
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      arrayOfStrings: value['arrayOfStrings'],
      arrayOfNullableStrings: value['arrayOfNullableStrings'],
      nullableArrayOfStrings: value['nullableArrayOfStrings'],
      nullableArrayOfNullableStrings: value['nullableArrayOfNullableStrings'],
      declaredArrayOfStrings: value['declaredArrayOfStrings'],
      declaredArrayOfNullableStrings: value['declaredArrayOfNullableStrings'],
    }),
    encode: (value) => ({
      'arrayOfStrings': value.arrayOfStrings,
      'arrayOfNullableStrings': value.arrayOfNullableStrings,
      'nullableArrayOfStrings': value.nullableArrayOfStrings,
      'nullableArrayOfNullableStrings': value.nullableArrayOfNullableStrings,
      'declaredArrayOfStrings': value.declaredArrayOfStrings,
      'declaredArrayOfNullableStrings': value.declaredArrayOfNullableStrings,
    }) as z.infer<typeof wireSchema>,
  });
});
