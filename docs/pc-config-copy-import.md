# PC configuration → Android non-active copy

Objective: safely register a PC-owned HTTP configuration as a non-active Android config, return its stable T4 interfaceKey, and verify a normal PC-only CSP source yields real data without switching the Android active config.

Lane: standard, task guard pc-config-copy-import. Allowed: Manage.java, ConfigImport.java, Nano.java route-only log sanitization, ConfigImportTest.java, this document. Pre-existing Proxy/OriginMediaRelay/ProxyChunkedResponsePolicy fixes and .scratch-goal are protected and excluded.

Current evidence: initial unit13pass; original PC record25 has93sites/78CSP, non-active row32 registered idempotently; “配置” returned11categories/7rows (configuration/menu data, not full movie proof). Numeric cid is not T4 configKey; getInterfaceKey() is required. GET mutation and unrestricted network entry are not accepted for production.

Design basis: reuse Config/DAO and VodSiteCatalog non-active load; keep Android active config untouched; POST-only actual-loopback request, no browser Origin/target-forwarding for this PC-control route; validate a parsed HTTP URI, no credential userinfo, cap fields; return id+interfaceKey. Do not change general management routes. ADB pairing protects the remote transport; local Android apps remain within the trusted device boundary (not multi-tenant isolation).

Recovery anchor
- Branch dev1, baseline997b0889b; three initial uncommitted endpoint files explicitly adopted in guard after parent audit (initial unguarded work is disclosed, not re-labelled guarded).
- Pending: guarded safety/identity rework, targeted tests, same-signature mobile build and representative non-menu source test.
- Risks: temporary HTTP URL will need a stable reverse-port/binding in the PC layer; reverse/auth URL values must not enter logs; source32 remains non-active but its temporary source URL is offline.
- Exactly next action: delegate guarded route/identity rework to the existing Android executor, then Lead accepts before PC wiring.

Best-practice / decision-ready review (2026-11-24)
- Evidence gradeA: local Manage/Config/DAO/VodSiteCatalog and ConfigImportTest at base997b0889b7cde86d4acf142a6c1088a7e40cbcac; current fork supports nonactive configKey loads and stable interfaceKey, but url/type index is not unique. No generic authentication guard surrounds Manage import.
- Official primary: RFC9110 section9.2.1 (https://www.rfc-editor.org/rfc/rfc9110.txt), initial RFC-editor fetch failed TLS EOF; substitute official mirrors checked separately. Normative safe-method rule: GET is read-only and cannot be used for this insertion. Java parsed URI host/port/userinfo, not manualsubstring, is the validation baseline. Android ADB authenticated forwarding plus exact loopback peer is the scoped transport.
- Mature related local code/tests: Config.create/DAO find, /manage/configs and nonactive VodSiteCatalog path; preserve all old management/source identities. Existing OriginSession worker is Android-internal Binder/history-specific, not a free PC HTTP endpoint; using it here adds unnecessary contract/cropping risk.
- Upstream commits/PRs/reverts: inapplicable to a new local management extension, not an upstream candidate. Papers/benchmarks: inapplicable; no novel algorithm/performance claim. External blogs omitted because normative method/URI/loopback constraints decide this narrow design.
- Compare: nochange leaves PC-only config unexecutable; initialGET import works but unsafe and returns onlynumericid; minimaladaptedPOST actual-loopback route with absentbrowserOrigin/non-simpleheader, boundedbody and parsedURL gives the required capability without activating config. A fullprivateworker gives stronger account isolation but is deferred (larger behaviour/API change).
- Acceptance: invalidLAN/browser/GET/body/URL rejected beforeinsertion; noURLtokenlogs; concurrentidenticalimports produceonecid; responseincludes stableinterfaceKey; normal影视source category/detail data plus unchanged204activekeys; no menu actions.
- Rollback: guardtask owns only4codefiles+thisdoc; protectedearlierproxyfixes excluded. Restoreownedpatch/recoverycommit ifneeded; SDKdeployment same-signaturemobilearm64 withuserdata preserved. PCcopyrow is nonactive; explicitdelete not part of thisunit.
- Next action: guarded Android executor rework and a real normal-source representative test; then Lead closes this atomicunit before PCpublisher wiring.
- Official mirror receipts: https://httpwg.org/specs/rfc9110.html and https://datatracker.ietf.org/doc/html/rfc9110 both HTTP200, raw safe-method text verified on2026-11-24; initial RFC-editor TLSfailure is retainedasdisclosure.

Accepted Android unit (Lead, 2026-11-24)
- Independently ran :app:testMobileArm64_v8aDebugUnitTest ConfigImportTest:15tests/0failures/0errors, BUILD SUCCESSFUL. Guard-order/code reviewed: bodylimit beforeparse, POST/loopback/Origin/target checks beforegenericforward, no activation orURLlogs.
- Real executor receipt: 热播影视6homeclasses,12categoryitems, detail1/3flags/143first-lineepisodes with realreturnedIDs; active204keydigest unchanged, copyactive=false, version5.6.0/560 unchanged. Play resolution is still untested; no all78source/native guarantee.
- PC execution will preserveoriginalrecord/sourceKey andbuildoverlay; importedAndroidrows32–34 remainnonactive withoffline temporaryURLs. These are disclosedspikecopies, not a finishedPC runtime UI.
- Correct site-count:204active, never18878(the earlier user-facing number was a transcription error).
- Exactly next action after atomicclosure: implementPCsnapshotpublisher + verifiedADBreverse + stablebinding/overlay.

Targeted catalog invalidation (2026-11-24, Lead-Core)
- PC publishes a fixed URL per binding; content changes without URL change, so the 60s catalog TTL could serve stale JSON after re-import. Added VodSiteCatalog.invalidate(String interfaceKey) (namespace-scoped remove only) and call it in /manage/config/import right after registration. Global invalidate() and all other callers unchanged.
- Verification: testMobileArm64_v8aDebugUnitTest ConfigImportTest 15/0/0 and existing VodApiTest 39/0/0 BUILD SUCCESSFUL. No device-level re-import/stale-cache proof yet; it is scheduled in the final R6 after the PC publisher exists (do not claim it as verified).

Direct cache regression follow-up
- Added CatalogInvalidateTest 3 actual-cache tests: selected namespace removed while other catalog identity retained, null/blank/unknown key no-op, legacy global invalidate still clears all. MobileArm64Debug targeted run also included ConfigImportTest15 and VodApiTest39:57tests/0failures.
- Test order disclosure: cache behavior was implemented before these direct regression tests (earlier54tests only protected callers); these3 tests are post-hoc contract checks, not represented as original RED-first evidence. No further production changes in this follow-up.
