![GX Library Banner](/docs/static/img/banner.png)

# [Documentation](https://gx.kepler16.com)

# Contributing

## Build
VERSION=2.0.0-SNAPSHOT just build

## Release
VERSION=2.0.0-SNAPSHOT just release

## To run clj tests

```bash
clj -X:test
```

## To run node tests

```bash
# install deps
pnpm i
# run tests
pnpm exec shadow-cljs compile test
```
