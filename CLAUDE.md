# Watchlistarr Java — Claude Instructions

## Java Coding Style

- **No inline if-statements.** Always use braces and put the body on a new line.
  ```java
  // Wrong
  if (condition) doSomething();

  // Correct
  if (condition) {
      doSomething();
  }
  ```

- **`catch`, `else`, and `else if` go on a new line** (Allman-adjacent style for these blocks).
  ```java
  // Wrong
  } catch (Exception e) {

  // Correct
  }
  catch (Exception e) {
  ```

- **Minimise use of `var`.** Prefer explicit types. Only use `var` when the type is obvious from the right-hand side (e.g. constructor calls) or when the type is excessively verbose with no readability gain.
  ```java
  // Acceptable
  var mapper = new ObjectMapper();

  // Avoid — type not obvious
  var result = http.get(url, apiKey);

  // Prefer
  Optional<JsonNode> result = http.get(url, apiKey);
  ```

## Project Context

- Java 21, Quarkus 3.32.1, Maven
- Build: `mvn package -DskipTests`
- Tests: `mvn test` (23 tests, JUnit 5 + Mockito)
- See `src/main/java/watchlistarr/` for package structure
