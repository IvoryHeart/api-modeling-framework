(ns raml-framework.parser.domain.openapi-test
  (:require [clojure.test :refer :all]
            [raml-framework.parser.domain.openapi :as openapi-parser]
            [raml-framework.model.domain :as domain]))


(deftest parse-ast-root
  (let [node {:swagger "2.0"
              :info {:title "title"
                     :description "description"
                     :termsOfService "terms-of-service"
                     :version "2.0"}
              :host "api.test.com"
              :basePath "/test/endpoint"
              :schemes ["http" "https"]
              :consumes ["application/json" "application/xml"]
              :produces ["application/ld+json"]}
        parsed (openapi-parser/parse-ast node {:location "file://path/to/resource.raml#"
                                               :parsed-location "file://path/to/resource.raml#"
                                               :is-fragment false})]
    (is (satisfies? domain/APIDocumentation parsed))
    (is (= "api.test.com" (domain/host parsed)))
    (is (= ["http" "https"] (domain/scheme parsed)))
    (is (= "/test/endpoint" (domain/base-path parsed)))
    (is (= ["application/ld+json"] (domain/content-type parsed)))
    (is (= ["application/json" "application/xml"] (domain/accepts parsed)))))
