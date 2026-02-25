import {TestEnum as TestEnum_} from './test-enum';
import {z} from 'zod';


export enum TestEnum {
  None = 'none',
  Some = 'some',
  All = 'all',
  SnakeCase = 'snake_case',
  KebabCase = 'kebab-case',
  InvalidChar = 'invalid:char'
}

export const TestEnumSchema = z.enum(TestEnum_);
