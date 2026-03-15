#!/usr/bin/env bash

# Validate GitHub Actions workflow files

WORKFLOW_DIR=".github/workflows"

echo "🔍 Validating GitHub Actions workflows..."

# Check if workflow directory exists
if [ ! -d "$WORKFLOW_DIR" ]; then
    echo "❌ Workflow directory not found: $WORKFLOW_DIR"
    exit 1
fi

# Find all workflow files
mapfile -t WORKFLOW_FILES < <(find "$WORKFLOW_DIR" -name "*.yml" -o -name "*.yaml")

if [ ${#WORKFLOW_FILES[@]} -eq 0 ]; then
    echo "⚠️  No workflow files found"
    exit 0
fi

VALID=0
INVALID=0

for workflow in "${WORKFLOW_FILES[@]}"; do
    echo ""
    echo "Checking: $workflow"

    # Check for required fields
    MISSING=()

    if ! grep -q "^name:" "$workflow"; then
        MISSING+=("name")
    fi

    if ! grep -q "^on:" "$workflow"; then
        MISSING+=("on")
    fi

    if ! grep -q "^jobs:" "$workflow"; then
        MISSING+=("jobs")
    fi

    if [ ${#MISSING[@]} -gt 0 ]; then
        echo "✗ Missing required fields: ${MISSING[*]}"
        INVALID=$((INVALID + 1))
        continue
    fi

    echo "✓ All required fields present"
    VALID=$((VALID + 1))

    # Check for common issues
    if grep -q "cachix.*wire-cli" "$workflow"; then
        echo "⚠️  Cachix cache name 'wire-cli' - update to your cache name"
    fi

    if grep -q '\${{ secrets.CACHIX_AUTH_TOKEN }}' "$workflow"; then
        echo "⚠️  Uses CACHIX_AUTH_TOKEN - ensure secret is configured in GitHub"
    fi

    # Count jobs and steps
    JOBS=$(grep "^  [a-z-]*:$" "$workflow" | wc -l | tr -d ' ')
    STEPS=$(grep "^      - name:" "$workflow" | wc -l | tr -d ' ')

    echo "   Jobs: $JOBS, Steps: $STEPS"
done

echo ""
echo "========================================="
echo "Validation complete:"
echo "  Valid: $VALID"
echo "  Invalid: $INVALID"
echo "========================================="

if [ $INVALID -gt 0 ]; then
    exit 1
fi

echo "✓ All workflows validated successfully!"
