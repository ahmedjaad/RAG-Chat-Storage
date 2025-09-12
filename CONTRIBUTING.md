# Contributing

Thank you for contributing to RAG Chat Storage!

Process guideline:

- After each prompt that asks you to make changes, commit the changes when all is well.
- “All is well” means at minimum:
  - The project builds successfully.
  - Tests pass locally.
- Do not push; commits will be pushed by maintainers or CI as appropriate.

Recommended local checklist:

1) Verify build and tests:
   - mvn -q -DskipTests=false verify
2) Stage and commit your changes with a clear message:
   - git add -A
   - git commit -m "<type>(scope): short description"

Conventional commit types are encouraged (feat, fix, docs, test, refactor, chore).

Thanks again for helping improve the project!