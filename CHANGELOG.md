# [](https://github.com/kepler16/gx.cljc/compare/v2.8.10...v) (2023-03-08)



## [2.8.10](https://github.com/kepler16/gx.cljc/compare/v2.8.9...v2.8.10) (2023-02-16)


### Bug Fixes

* show root failure + subsequent failures in ex-data ([1c4af0f](https://github.com/kepler16/gx.cljc/commit/1c4af0f48bb48514009fb95321d4fa2ce6520820))



## [2.8.9](https://github.com/kepler16/gx.cljc/compare/v2.8.8...v2.8.9) (2023-01-26)


### Bug Fixes

* **TRE-1096:** in some cases wrong cause is thrown ([53d0263](https://github.com/kepler16/gx.cljc/commit/53d0263b53a93689325ead62b5013a4e4103c470))



## [2.8.8](https://github.com/kepler16/gx.cljc/compare/v2.8.7...v2.8.8) (2023-01-20)


### Bug Fixes

* mvn/repos ([bcd9fc8](https://github.com/kepler16/gx.cljc/commit/bcd9fc8bee7f6f95865ba17ffba6fa8e6bb69ff9))
* user k16/metabuild ([c2d1273](https://github.com/kepler16/gx.cljc/commit/c2d1273319ef4e50c168e3b5a5af6c10463fdfa2))
* workflow ([255c4d2](https://github.com/kepler16/gx.cljc/commit/255c4d20182f2359a776f33342461f7957a79cc9))



## [2.8.7](https://github.com/kepler16/gx.cljc/compare/v2.8.6...v2.8.7) (2023-01-20)


### Bug Fixes

* bump promesa and malli ([42bce77](https://github.com/kepler16/gx.cljc/commit/42bce771ff4cf7cbe59f3d4037b19de38309da76))
* **resolve-symbol:** pass through non symbols ([9d051a2](https://github.com/kepler16/gx.cljc/commit/9d051a2b6c1240f3e881dcd02dee64476f720a1c))



## [2.8.5](https://github.com/kepler16/gx.cljc/compare/v2.8.4...v2.8.5) (2023-01-06)


### Bug Fixes

* **#20:** do not throw wrapped error ([ab6c06a](https://github.com/kepler16/gx.cljc/commit/ab6c06a4bf9978747b3fa40419c7d3abbc81266b)), closes [#20](https://github.com/kepler16/gx.cljc/issues/20)
* display initial form in evaluate error ([36f2745](https://github.com/kepler16/gx.cljc/commit/36f274582936344b75f8f0432fefd485a9c58979))
* form evaluate errors ([623f563](https://github.com/kepler16/gx.cljc/commit/623f563c557403943f11d63198534d4969362d4a))
* use console.error ([35803ed](https://github.com/kepler16/gx.cljc/commit/35803ed16cb47ced4c79036cc14faaa927de5675))



## [2.8.3](https://github.com/kepler16/gx.cljc/compare/v2.8.2...v2.8.3) (2022-10-27)


### Bug Fixes

* remove extra print ([61a39fd](https://github.com/kepler16/gx.cljc/commit/61a39fdd2a2de23182e30ba034e20ac82986e63a))



## [2.8.2](https://github.com/kepler16/gx.cljc/compare/v2.8.1...v2.8.2) (2022-10-27)


### Bug Fixes

* humanize wasn't showing cause properly ([63775e4](https://github.com/kepler16/gx.cljc/commit/63775e496028cce8b566d6318b903b5967f63958))



## [2.8.1](https://github.com/kepler16/gx.cljc/compare/v2.8.0...v2.8.1) (2022-10-27)


### Bug Fixes

* incorrect props-schema error propagation ([5d79cb4](https://github.com/kepler16/gx.cljc/commit/5d79cb4c6a9721ff20c38783ef10309a403d5d86))


### Features

* **TRE-856:** add error causes ([40d90c2](https://github.com/kepler16/gx.cljc/commit/40d90c2c44bb1a447869a8e2da29ca9518a66c0c))



## [2.7.6](https://github.com/kepler16/gx.cljc/compare/v2.7.5...v2.7.6) (2022-10-24)


### Bug Fixes

* changelog ([2e1f8f1](https://github.com/kepler16/gx.cljc/commit/2e1f8f1e89bdca6c6c27e5f04dc2d71f12113468))



## [2.7.5](https://github.com/kepler16/gx.cljc/compare/v2.7.4...v2.7.5) (2022-10-24)


### Bug Fixes

* **TRE-869:** better props emptiness check ([#19](https://github.com/kepler16/gx.cljc/issues/19)) ([3e5dd5d](https://github.com/kepler16/gx.cljc/commit/3e5dd5d9cd6fecd00f98434c3581864af547816c))



## [2.7.4](https://github.com/kepler16/gx.cljc/compare/v2.7.3...v2.7.4) (2022-10-22)


### Bug Fixes

* bump versions ([5850b2d](https://github.com/kepler16/gx.cljc/commit/5850b2d780747e78c282da55bf96d2fc1bf895f6))
* promesa version ([82754c7](https://github.com/kepler16/gx.cljc/commit/82754c7ad552b0d09aa9bd74b17048545fc61c42))



## [2.7.3](https://github.com/kepler16/gx.cljc/compare/v2.7.2...v2.7.3) (2022-07-14)


### Bug Fixes

* clj/cljs error wrap consistency ([e037b33](https://github.com/kepler16/gx.cljc/commit/e037b339bef57ff886682bca5964f423fa1dc703))



## [2.7.2](https://github.com/kepler16/gx.cljc/compare/v2.7.1...v2.7.2) (2022-07-04)


### Bug Fixes

* in some cases, old failure is not cleaned up ([a85f453](https://github.com/kepler16/gx.cljc/commit/a85f453b9997d5cba83ac6b18ed3fb26c5af239a))
* signal! resolve to nil on wrong system-name ([3b363e1](https://github.com/kepler16/gx.cljc/commit/3b363e1b7021627a08fc3c035577eebb7aa52198))



## [2.7.1](https://github.com/kepler16/gx.cljc/compare/v2.7.0...v2.7.1) (2022-07-01)


### Bug Fixes

* failure is not cleaned from the previous signal ([15063f1](https://github.com/kepler16/gx.cljc/commit/15063f1341402ab6cf8a29eef999dfd10b9d62bb))



# [2.7.0](https://github.com/kepler16/gx.cljc/compare/v2.6.0...v2.7.0) (2022-07-01)


### Bug Fixes

* context signal ([4adef60](https://github.com/kepler16/gx.cljc/commit/4adef606e9b0df90bfd7686ba1a1c1a180ae52a6))


### Features

* graph deps is statically analyzed ([e8aa274](https://github.com/kepler16/gx.cljc/commit/e8aa274266031d4c97d7408695ab7c0c5465a781))



# [2.6.0](https://github.com/kepler16/gx.cljc/compare/v2.5.2...v2.6.0) (2022-06-30)


### Features

* (TRE-421) error propagation ([2ebee31](https://github.com/kepler16/gx.cljc/commit/2ebee3139df027238a6eda632f5923f039f52122))



## [2.5.2](https://github.com/kepler16/gx.cljc/compare/v2.5.1...v2.5.2) (2022-06-30)


### Bug Fixes

* pass node's state to processor ([a7fcc1e](https://github.com/kepler16/gx.cljc/commit/a7fcc1e087588347c3c6356c35bb51749768b432))



## [2.5.1](https://github.com/kepler16/gx.cljc/compare/v2.5.0...v2.5.1) (2022-06-25)


### Bug Fixes

* cljs error message impl ([7d6180e](https://github.com/kepler16/gx.cljc/commit/7d6180e9848f8d3cbd93b8e0d2174630c89e4722))



# [2.5.0](https://github.com/kepler16/gx.cljc/compare/v2.4.3...v2.5.0) (2022-06-24)


### Features

* async processor support ([76c8346](https://github.com/kepler16/gx.cljc/commit/76c83467fb1d44b2f7e1ec003e4187bdc3cde955))



## [2.4.3](https://github.com/kepler16/gx.cljc/compare/v2.4.2...v2.4.3) (2022-06-22)


### Bug Fixes

* not all errors are cached in js runtime ([ba5860f](https://github.com/kepler16/gx.cljc/commit/ba5860ff24d9c4eb82d67a085bfd863596539d8d))



## [2.4.2](https://github.com/kepler16/gx.cljc/compare/v2.4.1...v2.4.2) (2022-06-21)


### Bug Fixes

* human-readable error message formatter ([0c60e6f](https://github.com/kepler16/gx.cljc/commit/0c60e6ff7573091ee9f15403d271ce187d576413))



## [2.4.1](https://github.com/kepler16/gx.cljc/compare/v2.4.0...v2.4.1) (2022-06-20)


### Bug Fixes

* components w/o specific signal should not change its status ([5dffed3](https://github.com/kepler16/gx.cljc/commit/5dffed311e9b314dd94ab231879707f1b773070e))



# [2.4.0](https://github.com/kepler16/gx.cljc/compare/v2.3.1...v2.4.0) (2022-06-18)


### Features

* system utility function node filters + tests ([8c7d134](https://github.com/kepler16/gx.cljc/commit/8c7d134187742b8943d9e4f8e5d5a3f5b07f5412))



## [2.3.1](https://github.com/kepler16/gx.cljc/compare/v2.3.0...v2.3.1) (2022-06-17)


### Bug Fixes

* clj/cljs version of system/signal! ([6498a36](https://github.com/kepler16/gx.cljc/commit/6498a36010783026c0a180faa7a9b83e8f9dda0b))



# [2.3.0](https://github.com/kepler16/gx.cljc/compare/v2.2.5...v2.3.0) (2022-06-17)


### Features

* Make system ns cljc ([091b7db](https://github.com/kepler16/gx.cljc/commit/091b7db15322304d95522cd0068a8f15ba293eb7))



## [2.2.5](https://github.com/kepler16/gx.cljc/compare/v2.2.4...v2.2.5) (2022-06-16)


### Bug Fixes

* better error handling/reporting ([963e1e0](https://github.com/kepler16/gx.cljc/commit/963e1e05d0599455a14f7b8d7e277460b9f5b2dc))



## [2.2.4](https://github.com/kepler16/gx.cljc/compare/v2.2.3...v2.2.4) (2022-06-15)


### Bug Fixes

* better report for unresolved symbols ([426a360](https://github.com/kepler16/gx.cljc/commit/426a360ecb5a7b1b348120ec0e018c983c13c847))



## [2.2.3](https://github.com/kepler16/gx.cljc/compare/v2.2.2...v2.2.3) (2022-06-10)


### Bug Fixes

* selector could cause circular dependency error ([663bd59](https://github.com/kepler16/gx.cljc/commit/663bd5926c924e4703646128b01982262c341f42))



## [2.2.2](https://github.com/kepler16/gx.cljc/compare/v2.2.1...v2.2.2) (2022-06-10)


### Bug Fixes

* remove alpha ([33e76e6](https://github.com/kepler16/gx.cljc/commit/33e76e64cca6bc4adea4c19eec734b8444a69642))



## [2.2.1](https://github.com/kepler16/gx.cljc/compare/v2.2.0...v2.2.1) (2022-06-09)


### Bug Fixes

* use context from fn argument ([9e7114d](https://github.com/kepler16/gx.cljc/commit/9e7114dea656372756c5dcb70ea6a8070a1d36f6))



# [2.2.0](https://github.com/kepler16/gx.cljc/compare/v2.1.0...v2.2.0) (2022-06-09)


### Bug Fixes

* test ([0293b0a](https://github.com/kepler16/gx.cljc/commit/0293b0a6014d006069e37d2de1ad48313772683c))


### Features

* selectors + elegant way to selector topology ([42792b1](https://github.com/kepler16/gx.cljc/commit/42792b157813ccdd3dcbecf1a1b8c62ce17ec5b8))



# [2.1.0](https://github.com/kepler16/gx.cljc/compare/v2.0.4...v2.1.0) (2022-06-09)


### Features

* signal flow control via :gx/after ([a799018](https://github.com/kepler16/gx.cljc/commit/a799018af702198ca4e58c4690ceccddda33e40f))



## [2.0.4](https://github.com/kepler16/gx.cljc/compare/v2.0.3...v2.0.4) (2022-06-08)


### Bug Fixes

* make context optional (use default) ([95ce925](https://github.com/kepler16/gx.cljc/commit/95ce9256af0c4b205468c4bb215c00480bd0ee26))



## [2.0.3](https://github.com/kepler16/gx.cljc/compare/v2.0.2...v2.0.3) (2022-06-08)


### Bug Fixes

* do not show component contents in humanized error ([c89b3c0](https://github.com/kepler16/gx.cljc/commit/c89b3c0b7a62bb1171b6663e989336ac4c523e84))



## [2.0.2](https://github.com/kepler16/gx.cljc/compare/v2.0.1...v2.0.2) (2022-06-07)


### Bug Fixes

* processor can be var to function ([8660038](https://github.com/kepler16/gx.cljc/commit/86600387503aeb63f8151c81f184ea0174536538))



## [2.0.1](https://github.com/kepler16/gx.cljc/compare/v2.0.0...v2.0.1) (2022-06-06)


### Bug Fixes

* force build ([a21923a](https://github.com/kepler16/gx.cljc/commit/a21923a118611c2105112fb2ec63e93f47a12b25))



# [2.0.0](https://github.com/kepler16/gx.cljc/compare/v1.4.0...v2.0.0) (2022-06-06)


### Bug Fixes

* cyclic dependency check wasn't working ([24a5d76](https://github.com/kepler16/gx.cljc/commit/24a5d76af940e16e2642fd9198d1087fd274b2fa))
* improve static component handling ([49447fa](https://github.com/kepler16/gx.cljc/commit/49447fa5ad283314da4db0e91ea56325eef311b6))


### Features

* gx v2 ([675ab5b](https://github.com/kepler16/gx.cljc/commit/675ab5b8d6f5c7395f041ca752d13f9ed25bf686))



# [1.3.0](https://github.com/kepler16/gx.cljc/compare/v1.2.0...v1.3.0) (2022-02-20)


### Features

* support keywords in config ([edd1b24](https://github.com/kepler16/gx.cljc/commit/edd1b248f51b51cd407f61301e8ff8e3c3218cf6))



# [1.2.0](https://github.com/kepler16/gx.cljc/compare/v1.1.0...v1.2.0) (2021-12-22)


### Features

* force build ([711cf87](https://github.com/kepler16/gx.cljc/commit/711cf872d5ae95c65c3760a8b83da210bf28240d))



# [1.1.0](https://github.com/kepler16/gx.cljc/compare/v1.0.1...v1.1.0) (2021-12-22)


### Bug Fixes

* Add back GITHUB_TOKEN env var ([697ae19](https://github.com/kepler16/gx.cljc/commit/697ae198d3f42bcea52bcb6826a63ae97387745d))


### Features

* switch token ([9515142](https://github.com/kepler16/gx.cljc/commit/9515142f11567d39895563987190d98975a2b85c))



## [1.0.1](https://github.com/kepler16/gx.cljc/compare/v1.0.0...v1.0.1) (2021-12-22)


### Bug Fixes

* Fix lib tag ([b46720a](https://github.com/kepler16/gx.cljc/commit/b46720a19693ff652dc31f587bc12f40396ef31e))



# [1.0.0](https://github.com/kepler16/gx.cljc/compare/84d9f88cd8d02f1f3a7d9b5781d692e387f43d7e...v1.0.0) (2021-12-22)


### Bug Fixes

* fix gx repo ([84d9f88](https://github.com/kepler16/gx.cljc/commit/84d9f88cd8d02f1f3a7d9b5781d692e387f43d7e))



