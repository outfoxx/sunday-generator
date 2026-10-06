/*
 * Copyright 2026 Outfox, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Foundation
import Sunday
@testable import SundayGenTest
import Synchronization
import Testing

struct GeneratedPersistenceTests {
  @Test(arguments: [false, true]) func persistedSessionsRotationIsolationAndLogout(aggregate: Bool) async throws {
    let store = Store()
    let state = State()
    let managers = Mutex<[TokenManager]>([])
    var transports: [URLSessionTransport] = []
    var readRequests: [ObjectIdentifier: @Sendable () async throws -> Void] = [:]
    var publicRequests: [ObjectIdentifier: @Sendable () async throws -> Void] = [:]
    func settings(
      _ time: TimeInterval,
      session: String = "session",
      profile: String = "external-development"
    ) throws -> ClientSettings {
      let provider = Provider(session: session, time: time, state: state)
      let factory: TokenManagerFactory = { providers in
        state.counts.withLock { $0.factories += 1 }
        #expect(Set(providers.keys) == ["application"])
        #expect(providers["application"]?.identity == "application")
        let manager = try TokenManager(
          providers: providers,
          store: store,
          expirySkew: 5,
          now: { Date(timeIntervalSince1970: time) }
        )
        managers.withLock { $0.append(manager) }
        return manager
      }
      var captured: ClientSettings?
      var calls = 0
      let transportFactory: ClientTransportFactory<URLSessionTransport> = { settings in
        captured = settings
        calls += 1
        return URLSessionTransport(settings: settings)
      }
      if aggregate {
        let client = try createExampleAPI(
          config: ExampleAPIDevelopmentConfig(),
          transportFactory: transportFactory,
          credentials: ExampleAPICredentials(identity: ProviderCredentials(provider: provider)),
          securityProfile: profile,
          tokenManagerFactory: factory
        )
        #expect(client.users.transport === client.transport && client.projects.transport === client.transport)
        transports.append(client.transport)
        let manager = try #require(captured?.tokenManager)
        let key = ObjectIdentifier(manager)
        readRequests[key] = {
          _ = try await client.users.listUsers().transportRequest()
          _ = try await client.projects.listProjects().transportRequest()
        }
        publicRequests[key] = {
          let request = try await client.users.register().transportRequest()
          #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
        }
      }
      else {
        let client = try createUsersAPI(
          config: ExampleAPIDevelopmentConfig(),
          transportFactory: transportFactory,
          credentials: UsersAPICredentials(identity: ProviderCredentials(provider: provider)),
          securityProfile: profile,
          tokenManagerFactory: factory
        )
        transports.append(client.transport)
        let key = try ObjectIdentifier(#require(captured?.tokenManager))
        readRequests[key] = { _ = try await client.listUsers().transportRequest() }
        publicRequests[key] = {
          let request = try await client.register().transportRequest()
          #expect(request.value(forHTTPHeaderField: "Authorization") == nil)
        }
      }
      #expect(calls == 1)
      return try #require(captured)
    }

    func token(_ settings: ClientSettings) async throws -> TokenSet {
      let manager = try #require(settings.tokenManager)
      let read = try #require(readRequests[ObjectIdentifier(manager)])
      try await read()
      return try await manager.credentials(for: #require(settings.bindings["listUsers"]?.first)).tokens
    }
    do {
      let first = try settings(0)
      #expect(await store.reads == 0)
      #expect(state.counts.withLock { $0.factories == 1 && $0.acquisitions == 0 })
      let manager = try #require(first.tokenManager)
      let publicRequest = try #require(publicRequests[ObjectIdentifier(manager)])
      try await publicRequest()
      #expect(await store.reads == 0)
      let lease = try await manager.credentials(for: #require(first.bindings["listUsers"]?.first))
      #expect(lease.tokens.accessToken == "initial")
      await manager.close()
      #expect(try await token(settings(0)).accessToken == "initial")
      #expect(state.counts.withLock { $0.acquisitions == 1 })
      let third = try settings(96)
      let configured = state.counts.withLock { $0.configurations }
      try await withThrowingTaskGroup(of: String.self) { group in
        let manager = try #require(third.tokenManager)
        let selected = try #require(third.bindings["listUsers"]?.first)
        let read = try #require(readRequests[ObjectIdentifier(manager)])
        for _ in 0 ..< 20 {
          group.addTask {
            try await read()
            return try await manager.credentials(for: selected).tokens.accessToken
          }
        }
        let deadline = ContinuousClock.now.advanced(by: .seconds(5))
        while state.counts.withLock({ $0.configurations < configured + 20 || $0.refreshes.isEmpty }),
              ContinuousClock.now < deadline {
          await Task.yield()
        }
        #expect(state.counts.withLock { $0.configurations >= configured + 20 && $0.refreshes.count == 1 })
        await state.refreshGate.open()
        for try await value in group {
          #expect(value == "rotated-1")
        }
      }
      #expect(state.counts.withLock { $0.refreshes == ["refresh-1"] })
      #expect(try await token(settings(96)).refreshToken == "refresh-2")
      _ = try await token(settings(192))
      #expect(state.counts.withLock { $0.refreshes == ["refresh-1", "refresh-2"] })
      #expect(await store.saves == 3)
      _ = try await token(settings(0, session: "other-session"))
      _ = try await token(settings(0, profile: "external"))
      #expect(state.counts.withLock { $0.acquisitions == 3 })
      let key = try #require(await store.initialKey)
      await store.remove(key: key)
      _ = try await token(settings(0))
      #expect(state.counts.withLock { $0.acquisitions == 4 })
    }
    catch {
      transports.forEach { $0.close() }
      for manager in managers.withLock({ $0 }) {
        await manager.close()
      }
      throw error
    }
    transports.forEach { $0.close() }
    for manager in managers.withLock({ $0 }) {
      await manager.close()
    }
  }

  private final class State: Sendable {
    struct Counts { var acquisitions = 0; var factories = 0
      var configurations = 0; var refreshes: [String] = []
    }

    let counts = Mutex(Counts())
    let refreshGate = Signal()
  }

  private struct Provider: RefreshingTokenProvider {
    let identity = "application"
    let session: String
    let time: TimeInterval
    let state: State
    func configure(_: SecurityBinding) -> TokenConfiguration {
      state.counts.withLock { $0.configurations += 1 }
      return .init(clientIdentity: "client", grantIdentity: session)
    }

    func acquire(_: TokenRequest) async throws -> TokenSet {
      state.counts.withLock { $0.acquisitions += 1 }
      return .init(accessToken: "initial", expiresAt: Date(timeIntervalSince1970: 100), refreshToken: "refresh-1")
    }

    func refresh(_: TokenRequest, refreshToken: String) async throws -> TokenSet {
      let count = state.counts.withLock { $0.refreshes.append(refreshToken); return $0.refreshes.count }
      if count == 1 { await state.refreshGate.wait() }
      return .init(
        accessToken: "rotated-\(count)",
        expiresAt: Date(timeIntervalSince1970: time + 100),
        refreshToken: "refresh-\(count + 1)"
      )
    }
  }

  private actor Signal {
    private var opened = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
      if opened { return }
      await withCheckedContinuation { waiters.append($0) }
    }

    func open() {
      opened = true
      for waiter in waiters {
        waiter.resume()
      }
      waiters.removeAll()
    }
  }

  private actor Store: TokenStore {
    var values: [String: TokenSet] = [:]
    var initialKey: String?
    var reads = 0
    var saves = 0
    func load(key: String) -> TokenSet? { reads += 1; return values[key] }
    func save(key: String, tokens: TokenSet) {
      if initialKey == nil { initialKey = key }; saves += 1; values[key] = tokens
    }

    func remove(key: String) { values.removeValue(forKey: key) }
  }
}
