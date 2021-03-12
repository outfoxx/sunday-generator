import {Observable} from './rxjs';

export declare type ClassType<T> = (new () => T) | (new (...args: any[]) => T) | ((...args: any[]) => T) | ((...args: any[]) => ((cls: any) => T));
export interface ClassList<T> extends Array<any> {
    [index: number]: T | ClassList<T>;
    0: T;
}
export type AnyType = ClassList<ClassType<any>>;

export class Instant {
  private constructor() {}
}
export class ZonedDateTime {
  private constructor() {}
}
export class OffsetDateTime {
  private constructor() {}
}
export class OffsetTime {
  private constructor() {}
}
export class LocalDateTime {
  private constructor() {}
}
export class LocalDate {
  private constructor() {}
}
export class LocalTime {
  private constructor() {}
}
export class Duration {
  private constructor() {}
}
export class Period {
  private constructor() {}
}

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
  registerProblem(typeId: string, problemType: ClassType<Problem>): void;
  result<T>(params: any, resultType?: any): Observable<T>;
  events(params: any): EventSource;
  events<E>(params: any, eventTypes: EventTypes<E>): Observable<E>;
}
