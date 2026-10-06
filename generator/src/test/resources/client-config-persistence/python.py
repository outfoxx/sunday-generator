"""Application-owned manager storage survives settings instances without eager I/O."""

import asyncio
from collections.abc import Mapping

import httpx
from example_api.api import ExampleAPICredentials, create_example_api
from example_api.config import ExampleAPIDevelopmentConfig
from example_api.users import UsersCredentials, create_users
from sunday import (
    ClientSettings,
    ProviderCredentials,
    SecurityBinding,
    TokenConfiguration,
    TokenManager,
    TokenProvider,
    TokenRequest,
    TokenSet,
)
from sunday.httpx import HttpxTransport


def verify(aggregate: bool) -> None:
    async def run() -> None:
        values: dict[str, TokenSet] = {}
        reads = saves = acquisitions = factories = 0
        refreshes: list[str] = []

        class Store:
            async def load(self, key: str) -> TokenSet | None:
                nonlocal reads
                reads += 1
                return values.get(key)

            async def save(self, key: str, tokens: TokenSet) -> None:
                nonlocal saves
                saves += 1
                values[key] = tokens

            async def remove(self, key: str) -> None:
                values.pop(key, None)

        store = Store()

        def settings(
            time: float, session: str = "session", profile: str = "external-development"
        ) -> ClientSettings:
            class Provider:
                identity = "application"

                def configure(self, binding: SecurityBinding) -> TokenConfiguration:
                    return TokenConfiguration("client", session)

                async def acquire(self, request: TokenRequest) -> TokenSet:
                    nonlocal acquisitions
                    acquisitions += 1
                    return TokenSet(
                        "initial", expires_at=100, refresh_token="refresh-1"
                    )

                async def refresh(
                    self, request: TokenRequest, refresh_token: str
                ) -> TokenSet:
                    refreshes.append(refresh_token)
                    return TokenSet(
                        f"rotated-{len(refreshes)}",
                        expires_at=time + 100,
                        refresh_token=f"refresh-{len(refreshes) + 1}",
                    )

            provider = Provider()

            def factory(providers: Mapping[str, TokenProvider]) -> TokenManager:
                nonlocal factories
                factories += 1
                assert dict(providers) == {"application": provider}
                return TokenManager(
                    providers, store=store, expiry_skew=5, now=lambda: time
                )

            captured = []

            def transport_factory(resolved):
                captured.append(resolved)
                native = httpx.AsyncClient(
                    base_url=resolved.base_url,
                    transport=httpx.MockTransport(lambda _: httpx.Response(204)),
                )
                natives.append(native)
                return HttpxTransport.from_settings(resolved, native)

            constructor = create_example_api if aggregate else create_users
            credential_type = ExampleAPICredentials if aggregate else UsersCredentials
            client = constructor(
                ExampleAPIDevelopmentConfig(),
                transport_factory,
                credentials=credential_type(identity=ProviderCredentials(provider)),
                security_profile=profile,
                token_manager_factory=factory,
            )
            assert len(captured) == 1
            clients[id(captured[0])] = client
            return captured[0]

        async def token(settings: ClientSettings) -> TokenSet:
            assert settings.token_manager is not None
            client = clients[id(settings)]
            users = client.users if aggregate else client
            await users.list_users().execute()
            if aggregate:
                await client.projects.list_projects().execute()
            return (
                await settings.token_manager.credentials(
                    settings.bindings["listUsers"][0]
                )
            ).tokens

        clients = {}
        natives = []
        first = settings(0)
        assert (reads, saves, acquisitions, factories) == (0, 0, 0, 1)
        client = clients[id(first)]
        await (client.users if aggregate else client).register().execute()
        assert reads == 0
        assert first.token_manager is not None
        lease = await first.token_manager.credentials(first.bindings["listUsers"][0])
        assert lease.tokens.access_token == "initial"
        assert (await token(settings(0))).access_token == "initial"
        assert acquisitions == 1
        third = settings(96)
        tokens = await asyncio.gather(*(token(third) for _ in range(20)))
        assert all(value.access_token == "rotated-1" for value in tokens)
        assert refreshes == ["refresh-1"]
        assert (await token(settings(96))).refresh_token == "refresh-2"
        await token(settings(192))
        assert refreshes == ["refresh-1", "refresh-2"]
        assert saves == 3
        await token(settings(0, "other-session"))
        await token(settings(0, profile="external"))
        assert acquisitions == 3
        await store.remove(lease.key)
        await token(settings(0))
        assert acquisitions == 4
        for native in natives:
            await native.aclose()

    asyncio.run(run())


for aggregate in (False, True):
    verify(aggregate)
