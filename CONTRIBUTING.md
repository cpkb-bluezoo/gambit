# Contributing to Gambit

Thank you for your interest in contributing to Gambit. This document outlines
the coding standards and conventions for the project.

## Java Version Compatibility

**Gambit requires Java 8 (1.8) and must not use language features from later versions.**

This requirement exists to ensure broad compatibility with:
- Legacy enterprise Java runtimes still in production use
- Embedded systems and appliances with older JVMs
- Organizations with strict upgrade policies requiring long validation cycles
- Environments where upgrading the JVM is impractical or impossible

This is enforced at compile time via `source` and `target` settings in `build.xml`.

**Prohibited features include (but are not limited to):**
- `var` keyword (Java 10+)
- Switch expressions (Java 12+)
- Text blocks (Java 15+)
- Records (Java 16+)
- Pattern matching (Java 16+)
- Sealed classes (Java 17+)
- Virtual threads (Java 21+)

**Additionally, the following Java 8 features are prohibited by project policy:**
- Lambda expressions
- Method references
- Streams API (`java.util.stream`)
- `Optional<T>`

## File Headers

All source files must include a proper file header containing:
- Filename
- Copyright owner and date
- Copyright notice with license reference

Example:

```java
/*
 * ExampleClass.java
 * Copyright (C) 2025 Chris Burdess
 *
 * This file is part of Gambit, a PDF parser.
 *
 * Gambit is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gambit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Gambit.  If not, see <http://www.gnu.org/licenses/>.
 */
```

## Documentation

- All Java classes must have proper Javadoc with `@author` tag
- Document the intent and purpose, not the obvious mechanics
- Don't write comments that simply restate what the code does

**Good:**
```java
// Ensure entity is fully expanded before validation
expandEntity(entity, context);
```

**Bad:**
```java
// Call expandEntity method with entity and context
expandEntity(entity, context);
```

## Control Flow

### Conditional Blocks

All conditional blocks must be properly delimited with curly braces and indented,
even for single-line blocks. No short-form statements on the same line after `if`.

**Good:**
```java
if (value == null) {
    return;
}

if (count > 0) {
    processTokens();
}
```

**Bad:**
```java
if (value == null) return;

if (count > 0)
    processTokens();
```

This prevents a common source of programmer error when modifying code later.

## Imports

- Use proper import statements for all classes
- No fully qualified class names in code unless there is a genuine name clash
- Organize imports logically (java.*, javax.*, then project packages)

**Good:**
```java
import java.nio.ByteBuffer;
import java.nio.CharBuffer;

public void receive(ByteBuffer buffer) {
```

**Bad:**
```java
public void receive(java.nio.ByteBuffer buffer) {
```

## Annotations

- Only `@Override` and `@Deprecated` are permitted in main source code
- Other annotations may be used in test code (e.g., `@Test`)

## Language Features to Avoid

### No Lambdas

Use traditional anonymous classes or explicit method implementations
instead to make all types explicit and the code self-documenting.

**Good:**
```java
executor.submit(new Runnable() {
    @Override
    public void run() {
        processTask();
    }
});
```

**Bad:**
```java
executor.submit(() -> processTask());
```

### No Functional Paradigm

Use clear, traditional procedural code. Avoid streams, functional interfaces, and
method references.

**Good:**
```java
List<String> result = new ArrayList<>();
for (Token token : tokens) {
    if (token.isValid()) {
        result.add(token.getValue());
    }
}
```

**Bad:**
```java
List<String> result = tokens.stream()
    .filter(Token::isValid)
    .map(Token::getValue)
    .collect(Collectors.toList());
```

### No Method Chaining

Avoid chaining method calls. Write each operation as a separate statement
for clarity.

**Good:**
```java
StringBuilder sb = new StringBuilder();
sb.append("Hello");
sb.append(" ");
sb.append("World");
String result = sb.toString();
```

**Bad:**
```java
String result = new StringBuilder().append("Hello").append(" ").append("World").toString();
```

### No Inline Function Calls as Parameters

Avoid calling functions inline as parameters. Assign to variables first for clarity.

**Exception:** The `++` operator may be used inline.

**Good:**
```java
String name = element.getLocalName();
String formatted = formatter.format(name);
handler.startElement(formatted);
```

**Less ideal:**
```java
handler.startElement(formatter.format(element.getLocalName()));
```

**Acceptable (increment operator):**
```java
array[index++] = value;
```

### No Regular Expressions

Avoid `java.util.regex` patterns. Use traditional string parsing methods instead.
This is particularly important for a parser where character-by-character processing
is the norm.

**Good:**
```java
int colonIndex = header.indexOf(':');
if (colonIndex > 0) {
    String name = header.substring(0, colonIndex).trim();
    String value = header.substring(colonIndex + 1).trim();
}
```

**Bad:**
```java
Pattern pattern = Pattern.compile("^([^:]+):\\s*(.*)$");
Matcher matcher = pattern.matcher(header);
```

## Building

Use Ant to build and test:

```bash
ant              # Build JAR
ant test         # Run conformance tests
ant javadoc      # Generate documentation
ant clean        # Clean build artifacts
```

A minimal `pom.xml` is provided for Maven repository integration, but Ant remains
the primary build system.

## Testing

All changes should pass the project tests. Run `ant test` before submitting
contributions.

## Summary

The goal of these standards is to produce code that is:
- **Clear**: Easy to read and understand at a glance
- **Predictable**: Follows consistent patterns throughout
- **Maintainable**: Easy to modify without introducing bugs
- **Traditional**: Uses well-understood Java idioms
- **Compatible**: Runs on Java 8 and later without modification
- **Efficient**: Minimizes allocations and uses NIO buffers effectively

When in doubt, prefer clarity over cleverness.

