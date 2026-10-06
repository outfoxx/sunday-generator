function expect(actual: unknown) {
  function compare(expected: unknown) {
    if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
  return {toBe: compare, toEqual: compare};
}
import { ClientSettings, FetchTransport, TokenManager, TokenManagerFactory, TokenProvider, TokenSet, TokenStore } from '@outfoxx/sunday';
import { createExampleAPI } from './example-api.js';
import { createUsersAPI } from './users-api.js';
for (const aggregate of [false, true]) {
  const values = new Map<string, TokenSet>();
  let reads = 0;
  let saves = 0;
  let acquisitions = 0;
  const refreshes: string[] = [];
  let factories = 0;
  const store: TokenStore = {
    load: async key => { reads++; return values.get(key); },
    save: async (key, tokens) => { saves++; values.set(key, tokens); },
    remove: async key => { values.delete(key); },
  };
  function settings(time: number, session = 'session', profile = 'external-development') {
    const provider: TokenProvider = {
      identity: 'application', configure: () => ({ clientIdentity: 'client', grantIdentity: session }),
      acquire: async () => { acquisitions++; return { accessToken: 'initial', refreshToken: 'refresh-1', expiresAt: 100 }; },
      refresh: async (_, refreshToken) => {
        refreshes.push(refreshToken);
        return { accessToken: `rotated-${refreshes.length}`, refreshToken: `refresh-${refreshes.length + 1}`, expiresAt: time + 100 };
      },
    };
    const factory: TokenManagerFactory = providers => {
      factories++;
      expect(Object.keys(providers)).toEqual(['application']);
      expect(providers['application']).toBe(provider);
      return new TokenManager(providers, { store, expirySkewMs: 5, now: () => time });
    };
    const transport = (resolved: ClientSettings) => {
      calls++;
      return FetchTransport.fromSettings(resolved);
    };
    let calls = 0;
    const options = { credentials: {identity: {kind: 'provider' as const, provider}}, tokenManagerFactory: factory, securityProfile: profile };
    const client = aggregate
      ? createExampleAPI({serverId: 'development'}, transport, options)
      : createUsersAPI({serverId: 'development'}, transport, options);
    expect(calls).toBe(1);
    const users = 'users' in client ? client.users : client;
    return {
      read: async () => (await users.listUsers().transportRequest()).headers.get('Authorization')?.split(' ')[1],
      public: async () => (await users.register().transportRequest()).headers.has('Authorization'),
      other: async () => 'projects' in client ? (await client.projects.listProjects().transportRequest()).headers.get('Authorization')?.split(' ')[1] : (await users.listUsers().transportRequest()).headers.get('Authorization')?.split(' ')[1],
    };
  }

  const first = settings(0);
  expect([reads, saves, acquisitions, factories]).toEqual([0, 0, 0, 1]);
  expect(await first.public()).toBe(false);
  expect(reads).toBe(0);
  expect(await first.read()).toBe('initial');
  const key = [...values.keys()][0];
  const second = settings(0);
  expect(await second.read()).toBe('initial');
  expect(acquisitions).toBe(1);
  const third = settings(96);
  const tokens = await Promise.all(Array.from({ length: 20 }, (_, index) => index % 2 ? third.read() : third.other()));
  expect(tokens.every(value => value === 'rotated-1')).toBe(true);
  expect(refreshes).toEqual(['refresh-1']);
  const fourth = settings(96);
  expect((await fourth.read(), values.get(key)!.refreshToken)).toBe('refresh-2');
  const fifth = settings(192);
  await fifth.read();
  expect(refreshes).toEqual(['refresh-1', 'refresh-2']);
  expect(saves).toBe(3);
  for (const isolated of [settings(0, 'other-session'), settings(0, 'session', 'external')]) {
    await isolated.read();
  }
  expect(acquisitions).toBe(3);
  await store.remove(key);
  const reset = settings(0);
  await reset.read();
  expect(acquisitions).toBe(4);
}
