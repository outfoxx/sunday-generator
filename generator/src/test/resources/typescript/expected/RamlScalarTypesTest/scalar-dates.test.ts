import {LocalDate, LocalDateSchema, LocalDateTime, LocalDateTimeSchema, LocalTime, LocalTimeSchema, OffsetDateTime, OffsetDateTimeSchema, SchemaRuntime, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export interface TestSpec {

  dateOnly: LocalDate;

  timeOnly: LocalTime;

  dateTimeOnly: LocalDateTime;

  dateTime: OffsetDateTime;

}

export class Test implements TestSpec {

  dateOnly: LocalDate;

  timeOnly: LocalTime;

  dateTimeOnly: LocalDateTime;

  dateTime: OffsetDateTime;

  constructor(init: TestSpec) {
    this.dateOnly = init.dateOnly;
    this.timeOnly = init.timeOnly;
    this.dateTimeOnly = init.dateTimeOnly;
    this.dateTime = init.dateTime;
  }

  copy(changes: Partial<TestSpec>): Test {
    return new Test(Object.assign({}, this, changes));
  }

  toString(): string {
    return `Test(dateOnly='${this.dateOnly}', timeOnly='${this.timeOnly}', dateTimeOnly='${this.dateTimeOnly}', dateTime='${this.dateTime}')`;
  }

}

export const TestSchema = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = z.looseObject({
    'dateOnly': runtime.resolveSchema(LocalDateSchema),
    'timeOnly': runtime.resolveSchema(LocalTimeSchema),
    'dateTimeOnly': runtime.resolveSchema(LocalDateTimeSchema),
    'dateTime': runtime.resolveSchema(OffsetDateTimeSchema)
  });
  return z.codec(wireSchema, z.instanceof(Test), {
    decode: (value) => new Test({
      dateOnly: value['dateOnly'],
      timeOnly: value['timeOnly'],
      dateTimeOnly: value['dateTimeOnly'],
      dateTime: value['dateTime'],
    }),
    encode: (value) => ({
      'dateOnly': value.dateOnly,
      'timeOnly': value.timeOnly,
      'dateTimeOnly': value.dateTimeOnly,
      'dateTime': value.dateTime,
    }) as z.infer<typeof wireSchema>,
  });
});
