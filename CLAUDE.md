@AGENTS.md

# Claude-specific notes

- Prefer the skills in `.claude/skills/` over improvising; they encode the working
  Smartcar V3 flow, the Gradle-free build, and the RemoteViews constraints.
- When a user reports a Smartcar error, ask for the exact dialog text: the app surfaces
  Smartcar's own `description`/`error_description` fields, which distinguish wrong
  credentials (401 at the token endpoint) from a missing redirect URI (Connect page 400).
- If you change any `widget_*.xml` layout or the `Spec` table, re-read the "Hard rules"
  in AGENTS.md about RemoteViews ids and SizeF thresholds before building.
