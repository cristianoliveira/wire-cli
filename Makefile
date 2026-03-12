.PHONY: format format-check lint test all ktlint-baseline

.PHONY: help
help: ## Lists the available commands. Add a comment with '##' to describe a command.
	@grep -E '^[a-zA-Z_-].+:.*?## .*$$' $(MAKEFILE_LIST)\
		| sort\
		| awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

format: ## Formats the code
	./gradlew --no-daemon ktlintFormat

format-check: ## Checks if the code is formatted
	./gradlew --no-daemon ktlintCheck

lint: ## Checks the code
	./gradlew --no-daemon detekt

test: ## Runs the tests
	./gradlew --no-daemon test batsTest

all: format-check lint test ## Runs the checks and tests

ktlint-baseline: ## Generates the ktlint baseline
	./gradlew --no-daemon ktlintGenerateBaseline
