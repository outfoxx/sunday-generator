import {ArrayBufferSchema, DateSchema, Instant, InstantSchema, LocalDate, LocalDateSchema, LocalDateTime, LocalDateTimeSchema, LocalTime, LocalTimeSchema, OffsetDateTime, OffsetDateTimeSchema, OffsetTime, OffsetTimeSchema, Problem, ProblemWireSchema, SchemaLike, SchemaRuntime, ZonedDateTime, ZonedDateTimeSchema, createProblemCodec, defineSchema} from '@outfoxx/sunday';
import {z} from 'zod';


export class CreateFailedProblem extends Problem {

  static TYPE: string = 'http://example.com/create_failed';

  any: any;

  dateOnly: LocalDate;

  zonedDateTime: ZonedDateTime;

  offsetTime: OffsetTime;

  dateTime: OffsetDateTime;

  num: number;

  str: string;

  bool: boolean;

  date: Date;

  instant: Instant;

  dateTimeOnly: LocalDateTime;

  int: number;

  timeOnly: LocalTime;

  bin: ArrayBuffer;

  constructor(spec: { any: any, dateOnly: LocalDate, zonedDateTime: ZonedDateTime, offsetTime: OffsetTime, dateTime: OffsetDateTime, num: number, str: string, bool: boolean, date: Date, instant: Instant, dateTimeOnly: LocalDateTime, int: number, timeOnly: LocalTime, bin: ArrayBuffer, instance?: string | URL }) {
    super({
      type: CreateFailedProblem.TYPE,
      title: 'Create Failed',
      status: 500,
      detail: 'Object creation failed.',
      instance: spec.instance,
      any: spec.any,
      dateOnly: spec.dateOnly,
      zonedDateTime: spec.zonedDateTime,
      offsetTime: spec.offsetTime,
      dateTime: spec.dateTime,
      num: spec.num,
      str: spec.str,
      bool: spec.bool,
      date: spec.date,
      instant: spec.instant,
      dateTimeOnly: spec.dateTimeOnly,
      int: spec.int,
      timeOnly: spec.timeOnly,
      bin: spec.bin
    });

    this.any = spec.any;
    this.dateOnly = spec.dateOnly;
    this.zonedDateTime = spec.zonedDateTime;
    this.offsetTime = spec.offsetTime;
    this.dateTime = spec.dateTime;
    this.num = spec.num;
    this.str = spec.str;
    this.bool = spec.bool;
    this.date = spec.date;
    this.instant = spec.instant;
    this.dateTimeOnly = spec.dateTimeOnly;
    this.int = spec.int;
    this.timeOnly = spec.timeOnly;
    this.bin = spec.bin;
  }

}

export const CreateFailedProblemSchema: SchemaLike<CreateFailedProblem> = defineSchema((runtime: SchemaRuntime) => {
  const wireSchema = ProblemWireSchema.extend({
    'any': z.any(),
    'dateOnly': runtime.resolveSchema(LocalDateSchema),
    'zonedDateTime': runtime.resolveSchema(ZonedDateTimeSchema),
    'offsetTime': runtime.resolveSchema(OffsetTimeSchema),
    'dateTime': runtime.resolveSchema(OffsetDateTimeSchema),
    'num': z.number(),
    'str': z.string(),
    'bool': z.boolean(),
    'date': runtime.resolveSchema(DateSchema),
    'instant': runtime.resolveSchema(InstantSchema),
    'dateTimeOnly': runtime.resolveSchema(LocalDateTimeSchema),
    'int': z.number(),
    'timeOnly': runtime.resolveSchema(LocalTimeSchema),
    'bin': runtime.resolveSchema(ArrayBufferSchema)
  });
  return createProblemCodec(CreateFailedProblem, wireSchema);
});
