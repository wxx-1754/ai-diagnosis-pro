# Design QA

- Source visual truth: `docs/design.png`
- Implementation target: `http://127.0.0.1:5175/`
- Implementation screenshot: unavailable — enterprise browser policy blocked local `127.0.0.1` access
- Intended viewport: 1489 × 1058
- Intended state: completed demo diagnosis with all runbook steps finished

## Full-view comparison evidence

Blocked. The source image was opened successfully, but the local implementation could not be opened or captured in the in-app browser because enterprise network policy denied localhost access. No visual-match claim is made.

## Focused region comparison evidence

Blocked for the same reason. The planned focus regions were the right-side root-cause/recommendation panel and the bottom agent execution plan.

## Findings

- No code-level P0/P1/P2 failures were found by the production build, frontend parser tests, backend tests, or static selector checks.
- Visual layout, responsive behavior, animation timing, and screenshot fidelity remain unverified in a browser.

## Patches made

- Replaced the visible event/report stack with structured root cause, reasons, expected effect, recommended actions, and Markdown download.
- Added legacy/new Markdown report parsing with safe fallbacks.
- Moved the agent runbook below the graph and added arrows, compact cards, completion/failure badges, per-step timing, and reduced-motion handling.
- Updated the backend report prompt to require stable report sections and conservative effect estimates.
- Removed the specific-reasons section.
- Replaced frontend Markdown-derived insights with a second backend AI summarization pass.
- Limited the UI summary to one short root cause, one short expected effect, and at most three short actions.
- Added horizontal-overflow guards and persisted the structured summary in `report_json`.

## Verification completed

- `npm test`
- `npm run build`
- `mvn test` — 35 tests passed
- `git diff --check`

final result: blocked
