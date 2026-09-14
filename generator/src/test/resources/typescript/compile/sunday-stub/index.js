const {z} = require('zod');
const joda = require('@js-joda/core');

function defineSchema(builder) {
  return {
    id: Symbol('test-schema'),
    build: builder,
  };
}

class Problem extends Error {
  static BLANK_URL = new URL('about:blank');

  constructor(spec) {
    super(spec.title);
    Object.assign(this, spec);
  }
}

const URLSchema = defineSchema(() => z.codec(
  z.union([z.string(), z.instanceof(URL)]),
  z.instanceof(URL),
  {decode: value => new URL(value), encode: value => value.href},
));

const DateEncoding = {DECIMAL_SECONDS_SINCE_EPOCH: 0, MILLISECONDS_SINCE_EPOCH: 1, ISO8601: 2};
const ArrayBufferEncoding = {BASE64: 0, BASE64URL: 1, RAW_BYTES: 2};

function createSchemaRuntime(policy) {
  const schemas = new Map();
  const runtime = {
    policy,
    resolveSchema(ref) {
      if (!ref || typeof ref.build !== 'function') return ref;
      if (!schemas.has(ref)) schemas.set(ref, ref.build(runtime));
      return schemas.get(ref);
    },
  };
  return runtime;
}

function temporalSchema(type, numeric = false) {
  return defineSchema(runtime => z.codec(
    numeric ? z.union([z.string(), z.number()]) : z.string(), z.instanceof(type), {
      decode: value => typeof value === 'string' ? type.parse(value) :
        joda.OffsetDateTime.ofInstant(joda.Instant.ofEpochMilli(runtime.policy.numericDateDecoding === 0 ? value * 1000 : value), joda.ZoneOffset.UTC),
      encode: value => numeric && runtime.policy.dateEncoding === DateEncoding.MILLISECONDS_SINCE_EPOCH ?
        value.toInstant().toEpochMilli() : value.toString(),
    },
  ));
}

const ArrayBufferSchema = defineSchema(runtime => z.codec(
  runtime.policy.arrayBufferEncoding === ArrayBufferEncoding.RAW_BYTES ? z.instanceof(ArrayBuffer) : z.string(),
  z.instanceof(ArrayBuffer), {
    decode: value => {
      if (value instanceof ArrayBuffer) return value;
      const bytes = Buffer.from(value, 'base64');
      return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
    },
    encode: value => runtime.policy.arrayBufferEncoding === ArrayBufferEncoding.RAW_BYTES ? value :
      Buffer.from(value).toString('base64').replace(/=+$/, ''),
  },
));

function createOperation(transport, spec) {
  return spec;
}

module.exports = {
  defineSchema,
  createSchemaRuntime,
  DateEncoding,
  ArrayBufferEncoding,
  ArrayBufferSchema,
  LocalDate: joda.LocalDate,
  LocalTime: joda.LocalTime,
  LocalDateTime: joda.LocalDateTime,
  OffsetDateTime: joda.OffsetDateTime,
  LocalDateSchema: temporalSchema(joda.LocalDate),
  LocalTimeSchema: temporalSchema(joda.LocalTime),
  LocalDateTimeSchema: temporalSchema(joda.LocalDateTime),
  OffsetDateTimeSchema: temporalSchema(joda.OffsetDateTime, true),
  Problem,
  URLSchema,
  StringSchema: z.string(),
  NumberSchema: z.number(),
  BooleanSchema: z.boolean(),
  createOperation,
};
