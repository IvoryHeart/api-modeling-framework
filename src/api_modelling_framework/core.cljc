(ns api-modelling-framework.core
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  #?(:clj (:require [clojure.core.async :refer [<! >! go chan] :as async]
                    [api-modelling-framework.model.syntax :as syntax]
                    [api-modelling-framework.resolution :as resolution]
                    [api-modelling-framework.parser.syntax.yaml :as yaml-parser]
                    [api-modelling-framework.parser.syntax.json :as json-parser]
                    [api-modelling-framework.parser.document.raml :as raml-document-parser]
                    [api-modelling-framework.parser.document.openapi :as openapi-document-parser]
                    [api-modelling-framework.generators.syntax.yaml :as yaml-generator]
                    [api-modelling-framework.generators.syntax.json :as json-generator]
                    [api-modelling-framework.generators.document.raml :as raml-document-generator]
                    [api-modelling-framework.generators.document.openapi :as openapi-document-generator]
                    [api-modelling-framework.platform :as platform]
                    [clojure.walk :refer [keywordize-keys]]
                    [taoensso.timbre :as timbre :refer [debug]]))
  #?(:cljs (:require [cljs.core.async :refer [<! >! chan] :as async]
                     [api-modelling-framework.model.syntax :as syntax]
                     [api-modelling-framework.resolution :as resolution]
                     [api-modelling-framework.parser.syntax.yaml :as yaml-parser]
                     [api-modelling-framework.parser.syntax.json :as json-parser]
                     [api-modelling-framework.parser.document.raml :as raml-document-parser]
                     [api-modelling-framework.parser.document.openapi :as openapi-document-parser]
                     [api-modelling-framework.generators.syntax.yaml :as yaml-generator]
                     [api-modelling-framework.generators.syntax.json :as json-generator]
                     [api-modelling-framework.generators.document.raml :as raml-document-generator]
                     [api-modelling-framework.generators.document.openapi :as openapi-document-generator]
                     [api-modelling-framework.platform :as platform]
                     [clojure.walk :refer [keywordize-keys]]
                     [taoensso.timbre :as timbre :refer-macros [debug]])))

(defprotocol Model
  (^:export document-model [this] "returns the domain model for the parsed document")
  (^:export domain-model [this] "Resolves the document model generating a domain model"))

(defprotocol Parser
  (^:export parse-file [this uri cb]
   "Parses a local or remote stand-alone document file and builds a model")
  (^:export parse-string [this uri string cb]
   "Parses a raw string with document URI identifier and builds a model"))

(defprotocol Generator
  (^:export generate-string [this uri model options cb]
   "Serialises a model into a string")
  (^:export generate-file [this uri model options cb]
   "Serialises a model into a file located at the provided URI"))

(defn to-model [res]
  (let [domain-cache (atom nil)]
    (reify Model
      (document-model [_] res)
      (domain-model [_]
        (if (some? @domain-cache)
          @domain-cache
          (let [res (resolution/resolve res {})]
            (reset! domain-cache res)
            res))))))

(defrecord RAMLParser []
  Parser
  (parse-file [this uri cb]
    (go (let [res (<! (yaml-parser/parse-file uri))]
          (if (platform/error? res)
            (cb (platform/<-clj res) nil)
            (try (cb nil (to-model (raml-document-parser/parse-ast res {})))
                 (catch #?(:clj Exception :cljs js/Error) ex
                   (cb (platform/<-clj ex) nil)))))))
  (parse-string [this uri string cb]
    (go (let [res (<! (yaml-parser/parse-string uri string))]
          (if (platform/error? res)
            (cb (platform/<-clj res) nil)
            (try (cb nil (to-model (raml-document-parser/parse-ast res {})))
                 (catch #?(:clj Exception :cljs js/Error) ex
                   (cb (platform/<-clj ex) nil))))))))

(defrecord OpenAPIParser []
  Parser
  (parse-file [this uri cb]
    (go (let [res (<! (json-parser/parse-file uri))]
          (if (platform/error? res)
            (cb (platform/<-clj res) nil)
            (try (cb nil (to-model (openapi-document-parser/parse-ast res {})))
                 (catch #?(:clj Exception :cljs js/Error) ex
                   (cb (platform/<-clj ex) nil)))))))
  (parse-string [this uri string cb]
    (go (let [res (<! (json-parser/parse-string uri string))]
          (if (platform/error? res)
            (cb (platform/<-clj res) nil)
            (try (cb nil (to-model (openapi-document-parser/parse-ast res {})))
                 (catch #?(:clj Exception :cljs js/Error) ex
                   (cb (platform/<-clj ex) nil))))))))


(defrecord RAMLGenerator []
  Generator
  (generate-string [this uri model options cb]
    (go (try (let [options (keywordize-keys (merge (or (platform/->clj options) {})
                                                   {:location uri}))
                   res (-> model
                           (raml-document-generator/to-raml options)
                           (syntax/<-data)
                           (yaml-generator/generate-string options))]
              (cb nil (platform/<-clj res)))
            (catch #?(:clj Exception :cljs js/Error) ex
              (cb (platform/<-clj ex) nil)))))
  (generate-file [this uri model options cb]
    (go (let [options (keywordize-keys (merge (or (platform/->clj options) {})
                                              {:location uri}))
              res (<! (-> model
                          (raml-document-generator/to-raml options)
                          (syntax/<-data)
                          (yaml-generator/generate-file uri options)))]
         (if (platform/error? res)
           (cb (platform/<-clj res) nil)
           (cb nil (platform/<-clj res)))))))


(defrecord OpenAPIGenerator []
  Generator
  (generate-string [this uri model options cb]
    (debug "Generating OpenAPI string")
    (go (try (let [options (keywordize-keys (merge (or (platform/->clj options) {})
                                                   {:location uri}))
                   res (-> model
                           (openapi-document-generator/to-openapi options)
                           (syntax/<-data)
                           (json-generator/generate-string options))]
               (cb nil (platform/<-clj res)))
             (catch #?(:clj Exception :cljs js/Error) ex
               (cb (platform/<-clj ex) nil)))))
  (generate-file [this uri model options cb]
    (debug "Generating OpenAPI file")
    (go (let [options (keywordize-keys (merge (or (platform/->clj options) {})
                                              {:location uri}))
              res (-> model











                      (openapi-document-generator/to-openapi options)
                      (syntax/<-data)
                      (json-generator/generate-string options))]
          (if (platform/error? res)
            (cb (platform/<-clj res) nil)
            (cb nil (platform/<-clj res)))))))
