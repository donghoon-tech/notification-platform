# Commit Message Guidelines

## 1. Commit Message Format

Each commit message must adhere to the following structure:

```text
<type>: <short summary>

[optional body providing more context, reasoning, or bullet points]
```

## 2. Allowed Prefix Types

Use one of the following prefixes for every commit. Prefixes like `build` or `ci` are generally avoided; use `chore` instead for project configuration.

- **`feat`**: A new feature or functional addition to the application.
- **`fix`**: A bug fix.
- **`docs`**: Documentation-only changes (e.g., README, architecture docs, guidelines).
- **`refactor`**: A code change that neither fixes a bug nor adds a feature (e.g., renaming variables, extracting methods).
- **`test`**: Adding missing tests or correcting existing tests.
- **`chore`**: Maintenance tasks, library updates, or configuration changes (e.g., updating Gradle dependencies, Docker configs).

## 3. Best Practices

- **Use the imperative mood** in the summary (e.g., "Add feature" not "Added feature").
- Keep the summary line concise (under 50 characters).
- Use the body to explain **what** and **why**, rather than **how** (the code shows how).
- Separate the summary from the body with a blank line.

## 4. Commit Process

- **Propose a Plan First**: Never execute a commit immediately when requested. Always present a commit plan (including the staged files and the proposed message) first.
- **Wait for Approval**: Only proceed with the `git commit` command after receiving explicit confirmation from the user.

