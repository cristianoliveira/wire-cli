.PHONY: format format-check lint test all ktlint-baseline

JAVA_AVAILABLE := $(shell java -version >/dev/null 2>&1 && echo 1 || echo 0)
ifeq ($(JAVA_AVAILABLE),1)
GRADLEW := ./gradlew --no-daemon
else
GRADLEW := nix develop --command ./gradlew --no-daemon
endif

.PHONY: help
help: ## Lists the available commands. Add a comment with '##' to describe a command.
	@grep -E '^[a-zA-Z_-].+:.*?## .*$$' $(MAKEFILE_LIST)\
		| sort\
		| awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}'

format: ## Formats the code
	$(GRADLEW) ktlintFormat

format-check: ## Checks if the code is formatted
	$(GRADLEW) ktlintCheck

lint: ## Checks the code
	$(GRADLEW) detekt

test: ## Runs the tests
	$(GRADLEW) test batsTest

all: format-check lint test ## Runs the checks and tests

ktlint-baseline: ## Generates the ktlint baseline
	$(GRADLEW) ktlintGenerateBaseline
