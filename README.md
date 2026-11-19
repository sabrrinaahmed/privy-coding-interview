# privy-coding-interview

Used for coding interview during onsite. A ready-to-go Java (Javalin) starter for the coding project.

Privy Onsite: The Met API

Timeline
12:30 - Segment 1 (Solo): Build a working application meeting the Functional Requirements. You may use AI tools freely.
1:45 - Segment 2 (Paired): An interviewer joins you. Together, you'll review your code and work on meeting new, non-Functional requirements. We want to see how you think through challenges collaboratively.
2:40 - Debrief: Walkthrough and discussion.


Introduction
We're going to the Met!

We're excited about the trip and want to explore their archives for cool bread!

Build a working application that uses the Met Museum search functionality (https://metmuseum.github.io/#endpoints) and returns the 5 most recent works related to a query term, as determined by objectEndDate.

This should be an API server, and you can choose your own architecture. The default query should be bread, but support custom terms as time allows.


Functional Requirements
- Query the Met API for a search term.
- Retrieve matching objects and relevant details.
- Return/display up to 5 most recent works by objectEndDate (descending).

Expectations
Feel free to Google and ask questions. Don’t hesitate to pop out and visit us at our desk with questions.
Start simple, then polish. We're looking for production-ready code you'd be proud to have your peer review.
You may use AI tools, but be ready to explain your implementation and tradeoffs line-by-line.
Use any language, framework, or runtime you prefer.

If You Finish Functional Requirements Early
You get to play the role of product manager or tech lead in deciding where to focus your remaining time. We’re excited to see whatever improvements you’d like to bring and encourage you to pick something in your wheelhouse to really show off. For example:
- Resilience or latency improvements
- Structured error responses and status codes
- Pagination or query parameter design
- Filtering or alternative sorting strategies

Set Up and Delivery
Use any environment/framework you prefer. Please set up your project scaffolding in advance. After the interview, send either a zip file by email, or share a GitHub repo by link.


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

## Codespaces

Code → Codespaces → Create codespace on main. The `.devcontainer` config installs Java 21, Maven, the Java extensions, and Copilot, then downloads dependencies and runs the tests automatically (the first build takes a few minutes).

- Create the codespace the night before, so tomorrow it only has to restart.
- Codespaces stop after 30 idle minutes by default. Raise the idle timeout at github.com/settings/codespaces.
- Keep a phone hotspot ready, and a local clone as a backup if Wi-Fi is slow.

## Night before

- [ ] Create the codespace (or clone locally with VS Code: Cmd+Shift+P → Git: Clone → Clone from GitHub)
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
