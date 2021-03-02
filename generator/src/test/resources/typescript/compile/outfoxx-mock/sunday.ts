import {Observable} from './rxjs';

export interface EventTypes<E> {}

export interface RequestFactory {
  result<T>(params: any, resultType?: any): Observable<T>;
  events(params: any): EventSource;
  events<E>(params: any, eventTypes: EventTypes<E>): Observable<E>;
}

export interface AnyType {}

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
