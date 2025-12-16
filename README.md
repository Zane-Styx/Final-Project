# **Chromashift**
## Project Structure

| Module | Purpose |
|--------|---------|
| `core` | Main module with the application logic shared by all platforms |
| `lwjgl3` | Primary desktop platform using LWJGL3 |

---

## Getting Started

### Building the Project

This project uses **[Gradle](https://gradle.org/)** to manage dependencies. The Gradle wrapper is included, so you can run tasks using `gradlew.bat` (Windows) or `./gradlew` (Linux/Mac).

#### Useful Gradle Commands

- `lwjgl3:run` — Starts the application
- `lwjgl3:jar` — Builds a runnable jar file (output: `lwjgl3/build/libs`)
- `build` — Builds sources and archives of all projects
- `clean` — Removes `build` folders
- `test` — Runs unit tests (if any)

#### Gradle Flags

- `--daemon` — Uses Gradle daemon for faster builds
- `--offline` — Uses cached dependency archives
- `--refresh-dependencies` — Forces validation of all dependencies
- `--continue` — Continues running tasks even if errors occur

---

## Building and Running

```bash
# Run the game
gradlew lwjgl3:run

# Build a runnable JAR
gradlew lwjgl3:jar

# Clean build files
gradlew clean
```

---

**Chromashift — A LibGDX Game Project**
