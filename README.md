# privy-coding-interview

Used for coding interview during onsite. A ready-to-go Java (Javalin) starter for the coding project.

## Run

```bash
mvn -q test              # 4 starter tests should pass
mvn -o -q test           # same, offline: confirms dependencies are cached
mvn -q compile exec:java # server on http://localhost:7070
```

Try it:

```bash
curl localhost:7070/health
curl -X POST localhost:7070/items -H "Content-Type: application/json" -d '{"name":"first"}'
curl localhost:7070/items/1
```

## Night before

- [ ] Clone this repo with VS Code (Cmd+Shift+P → Git: Clone → Clone from GitHub)
- [ ] `mvn -q test` and `mvn -o -q test` both pass
- [ ] Open the folder in VS Code and let the Java extension finish indexing
- [ ] Copilot Chat answers a test question; pick your model
- [ ] Settings: Auto Save `afterDelay`, font size ~16, terminal open in VS Code
- [ ] Laptop charged; Do Not Disturb on; Slack and work apps quit
- [ ] Phone hotspot ready as a Wi-Fi backup

## Day of

1. `mvn -q test` once to confirm everything works.
2. Read the prompt and ask clarifying questions.
3. Write a short plan as a comment in `App.java`.
4. Build one endpoint at a time; review what the AI writes.
5. Commit a checkpoint after each working piece, and always before letting the AI make a big change.
6. Before the extension (~1:40 in): commit, and make sure tests pass.

## Walkthrough reminders

- Name your tradeoffs (storage, validation, money, concurrency).
- Point out anything you'd change with more time.
- Mention what you checked or fixed in the AI's output.
