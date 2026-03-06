# wire-cli

Minimal Kotlin CLI project using [Clikt](https://github.com/ajalt/clikt) and a Nix flake dev shell.

## Requirements

- Nix with flakes enabled

## Getting started

```bash
nix develop
gradle run
```

Run with a custom name:

```bash
gradle run --args='--name Kotlin'
```

Show CLI help:

```bash
gradle run --args='--help'
```
