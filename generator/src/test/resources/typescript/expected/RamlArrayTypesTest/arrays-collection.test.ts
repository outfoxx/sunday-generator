import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  implicit: Array<string>;

  unspecified: Array<string>;

  nonUnique: Array<string>;

  unique: Set<string>;

}

export class Test implements TestSpec {

  implicit: Array<string>;

  unspecified: Array<string>;

  nonUnique: Array<string>;

  unique: Set<string>;

  constructor(init: TestSpec) {
    this.implicit = init.implicit;
    this.unspecified = init.unspecified;
    this.nonUnique = init.nonUnique;
    this.unique = init.unique;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(implicit='${this.implicit}', unspecified='${this.unspecified}', nonUnique='${this.nonUnique}', unique='${this.unique}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'implicit': z.array(z.string()),
    'unspecified': z.array(z.string()),
    'nonUnique': z.array(z.string()),
    'unique': z.array(z.string())
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      implicit: value['implicit'],
      unspecified: value['unspecified'],
      nonUnique: value['nonUnique'],
      unique: new Set(value['unique']),
    }),
    encode: (value) => ({
      'implicit': value.implicit,
      'unspecified': value.unspecified,
      'nonUnique': value.nonUnique,
      'unique': Array.from(value.unique),
    }) as z.infer<typeof wireSchema>,
  });
});
