# atprotocol

atproto 互換を **[kotoba-protocol](https://github.com/kotoba-lang/kotoba-protocol)
の上に立つ投影層**として定義する boundary/spec repo（pure `.cljc`）。
ADR-2607071500。

atproto の record / lexicon / XRPC / handle / repo-commit wire は
「kotoba graph の別 encoding」。真実（鍵・datom・graph CID・IPNS head・配布）
は常に kotoba-protocol 側にあり、この層は投影と互換だけを所有する。
依存方向も同じ（この repo → kotoba-protocol のみ。逆依存は作らない）。

## Namespaces

- `atprotocol.boundary` — owns / delegates の責務表（as data）+ `delegated?`。
- `atprotocol.projection` — record ⇄ datom **codec 契約**（実装 map を注入）。
  参照実装 = `aozora.pds.encode` / `aozora.appview.scan`（本番稼働中 —
  移植せずポインタとして記録）。未知 collection は generic 保全
  （`:atproto.record/*`）に落ちるのが契約。
- `atprotocol.profile` — `:kotoba.app/*` manifest → profile view への投影
  （`appId` / `appKind` / `embedUrl` / `bundleCid` / `appLatest`）。
  **旧 W-Protocol fields（performerType / contentMode / uiType）は
  deprecated compat alias に降格** — `{:include-legacy? true}` でのみ導出され、
  新規実装は読まない・書かない。

## Dev

```bash
clojure -M:test
```
