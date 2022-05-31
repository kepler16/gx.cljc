![GX Library Banner](/docs/static/img/banner.png)

# [Documentation](https://gx.kepler16.com)

# Contributing

## Build
VERSION=v2.0.0-beta1 just build

## Release
VERSION=v2.0.0-beta1 just release

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
