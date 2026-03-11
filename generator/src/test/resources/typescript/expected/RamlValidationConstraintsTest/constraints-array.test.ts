import {SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  minList: Array<string>;

  maxList: Array<string>;

  minSet: Set<string>;

  maxSet: Set<string>;

}

export class Test implements TestSpec {

  minList: Array<string>;

  maxList: Array<string>;

  minSet: Set<string>;

  maxSet: Set<string>;

  constructor(init: TestSpec) {
    this.minList = init.minList;
    this.maxList = init.maxList;
    this.minSet = init.minSet;
    this.maxSet = init.maxSet;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(minList='${this.minList}', maxList='${this.maxList}', minSet='${this.minSet}', maxSet='${this.maxSet}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'minList': z.array(z.string()).min(5),
    'maxList': z.array(z.string()).max(10),
    'minSet': z.array(z.string()).min(15),
    'maxSet': z.array(z.string()).max(20)
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      minList: value['minList'],
      maxList: value['maxList'],
      minSet: new Set(value['minSet']),
      maxSet: new Set(value['maxSet']),
    }),
    encode: (value) => ({
      'minList': value.minList,
      'maxList': value.maxList,
      'minSet': Array.from(value.minSet),
      'maxSet': Array.from(value.maxSet),
    }) as z.infer<typeof wireSchema>,
  });
});
