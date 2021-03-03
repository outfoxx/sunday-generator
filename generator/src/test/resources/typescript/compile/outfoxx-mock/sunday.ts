import {Observable} from './rxjs';

export declare type ClassType<T> = (new () => T) | (new (...args: any[]) => T) | ((...args: any[]) => T) | ((...args: any[]) => ((cls: any) => T));
export interface ClassList<T> extends Array<any> {
    [index: number]: T | ClassList<T>;
    0: T;
}
export type AnyType = ClassList<ClassType<any>>;

export class OffsetDateTime {}
export class LocalDateTime {}
export class LocalDate {}
export class LocalTime {}
export class Duration {}

export class Problem {
  constructor(
    public type: string,
    public title: string,
    public status: number,
    public description: string,
    public instance: string | null
  ) {
  }
}

export class URLTemplate {
  constructor(url: string, params: object) {}
}

export enum MediaType {
  Text,
  HTML,
  JSON,
  YAML,
  CBOR,
  OctetStream,
  EventStream,
  X509CACert,
  WWWFormURLEncoded,
  ProblemJSON,
}

export interface EventTypes<E> {}

export interface RequestFactory {
  registerProblem(problemType: ClassType<Problem>): void;
  result<T>(params: any, resultType?: any): Observable<T>;
  events(params: any): EventSource;
  events<E>(params: any, eventTypes: EventTypes<E>): Observable<E>;
}
