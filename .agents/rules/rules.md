---
trigger: always_on
---

# Agent Instructions

## Response

- Return code only unless explanation is explicitly requested.
- Do not add intros, markdown preambles, or postambles.
- Give the summary at the end

## Changes

- Keep changes minimal and focused.
- Do not refactor unrelated code.
- Preserve existing style and structure.

## Java

- Use Lombok to reduce boilerplate when appropriate.
- Use existing Apache commons utilities when code becomes shorter or cleaner.
- Do not use fully qualified class names; use class names with the appropriate imports.

## Documentation

- Add concose documentation for new or modified functions/ classes.
