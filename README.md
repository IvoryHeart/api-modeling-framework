# API Modelling Framework [![Build Status](https://travis-ci.com/mulesoft-labs/api-modelling-framework.svg?token=ueejPvNfLJQ28ZqmJyUt&branch=master)](https://travis-ci.com/mulesoft-labs/api-modelling-framework)

## Architecture

![Architectural diagram](doc/images/arch.png)

## Vision

[ Vision Statement here ]

## Goals

- Support for multiple languages with a unified output API/model for clients
- Support for both, document (RAML modularity) and domain (service clients), layers
- Bi-directional transformation
- Support for validation at document and service layers
- Produce a formal specification for the language
- Extensible, single document model for multiple service levels
- Consistent parsing behaviour

## Installation

The API Modelling Framework Github's repository includes can be used to build the following artifacts:

- API Modelling Framework Clojure/JVM JAR library
- API Modelling Framework Clojurescript/Node NPM package
- API Modelling Framework Clojurescript/Web library
- API Modeller frontend Electron application
- API Modeller frontend Web application

The following leiningen invocations can be used to build each of this artifacts:

### API Modelling Framework Clojure/JVM library

``` shell
$ lein jar
```

### API Modelling Framework Clojurescript/Node library

``` shell
$ lein node
```

The output NPM package will be generated at `output/node`.


### API Modelling Framework Clojurescript/Node library

``` shell
$ lein web
```

The output JS library will be generated at `output/web`.

### API Modeller frontend Electron application

``` shell
$ lein api-modeller
```

The electron will be immediately started after the setup process.

### API Modeller frontend Web application

``` shell
$ lein api-modeller-web
```
A HTTP server will be immediately started after the setup process.


## Tests

The project includes two different test suites, one for the Clojure/JVM code and another one for the Clojruescript/JS code.

Tests can be run using the following leiningen invocations:

### Clojure/JVM tests

``` shell
$ lein test
```

### Clojurescript/JS tests

``` shell
$ lein test-js
```
