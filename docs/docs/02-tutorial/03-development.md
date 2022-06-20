---
id: development
title: Practical Example App
sidebar_label: Development workflow
slug: /development-workflow
---

# REPL driven development with GX

Complex system configurations are prone to errors. GX was developed to support REPL driven configuration with following workflow:
1. Fire-up an application REPL
2. Create/update components
3. Create/update config file(s) or just define a config in source code
4. Load and register a system(s)
5. Try to launch a system(s) by sending a startup signal
6. Examine failures (if any)
7. Send a stop signal to clear started resources such as HTTP server or a database pool
8. Go to step 2
9. Repeat

## Failure examination

GX's `system` namespace has two main functions to get failures:
- `(gx.system/failures <system-name>)` - returns a list of failures as a maps
- `(gx.system/failures-humanized <system-name>)` - returns a list of human readable stringified failures
