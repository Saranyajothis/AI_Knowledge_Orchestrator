# Contributing to AI Knowledge Orchestrator

First off, thank you for considering contributing to AI Knowledge Orchestrator! It's people like you that make this project such a great tool.

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples**
- **Include logs and stack traces**
- **Describe the behavior you observed and expected**
- **Include your environment details**

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion:

- **Use a clear and descriptive title**
- **Provide a detailed description of the enhancement**
- **Explain why this enhancement would be useful**
- **List examples of how it would be used**

### Pull Requests

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. Ensure the test suite passes
4. Make sure your code follows our style guidelines
5. Issue that pull request!

## Development Setup

1. Install Java 17 and Maven
2. Install MongoDB 6.0+
3. Clone your fork
4. Create a feature branch
5. Make your changes
6. Run tests: `./mvnw test`
7. Submit a pull request

## Style Guidelines

### Java Style

- Use 4 spaces for indentation
- Max line length: 120 characters
- Use meaningful variable and method names
- Add JavaDoc for all public methods
- Follow SOLID principles

### Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters
- Reference issues and pull requests liberally after the first line

Example:
```
Add: JWT refresh token functionality

- Implement refresh token generation
- Add refresh endpoint
- Update authentication service
- Add tests for refresh flow

Fixes #123
```

### JavaDoc Example

```java
/**
 * Processes a user query using the appropriate AI agent.
 * 
 * @param query the user query to process
 * @param context additional context for the query
 * @return the processed response
 * @throws QueryException if the query cannot be processed
 * @since 1.0.0
 */
public Response processQuery(Query query, Context context) throws QueryException {
    // implementation
}
```

## Testing

- Write unit tests for all new functionality
- Maintain minimum 70% code coverage
- Include integration tests for API endpoints
- Test edge cases and error conditions

## Documentation

- Update README.md if needed
- Add JavaDoc for public APIs
- Update Swagger annotations for endpoints
- Include examples in documentation

## Review Process

1. All submissions require review before merging
2. Changes must pass CI/CD pipeline
3. At least one approval required
4. Address all review comments

## Community

- Join our [Discord server](https://discord.gg/aiko)
- Follow us on [Twitter](https://twitter.com/aiko_ai)
- Read our [blog](https://blog.aiko.com)

Thank you for contributing! 🎉
