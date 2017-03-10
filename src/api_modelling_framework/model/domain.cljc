(ns api-modelling-framework.model.domain
  (:require [api-modelling-framework.model.document :as document]))

(defprotocol CommonAPIProperties
  (host [this] "Optional common host for all nodes in the API")
  (scheme [this] "Optional collection of schemes used by default in all the API endpoints")
  (accepts [this] "Optional list of accept media types supported by all endpoints in the API")
  (content-type [this] "Optional list of content media types supported by all endpoints in the API"))

(defprotocol HeadersHolder
  (headers [this] "List of HTTP headers exchanged by the API"))

(defprotocol ParametersHolder
  (parameters [this] "Parameters for this request"))

(defprotocol APIDocumentation
  (provider [this] "Person or organisation providing the API")
  (terms-of-service [this] "Terms of service for the API")
  (version [this] "Version of the API")
  (license [this] "License for the API")
  (base-path [this] "Optional base path for all API endpoints")
  (endpoints [this] "List of endpoints in the API"))

(defrecord ParsedAPIDocumentation [id sources name description extends parametesr
                                   host scheme base-path accepts content-type headers
                                   provider terms-of-service version license endpoints]
  CommonAPIProperties
  (host [this] host)
  (scheme [this] scheme)
  (accepts [this] accepts)
  (content-type [this] content-type)
  HeadersHolder
  (headers [this] (or headers []))
  ParametersHolder
  (parameters [this] (or parameters []))
  APIDocumentation
  (provider [this] provider)
  (terms-of-service [this] terms-of-service)
  (version [this] version)
  (license [this] license)
  (base-path [this] base-path)
  (endpoints [this] endpoints)
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] (and (some? (name this))
                      (some? (version this))))
  (extends [this] (or extends [])))

(defprotocol DomainElement
  (fragment-node [this] "The kind of node this domain element is wrapping")
  (properties [this] "A map of properties that can be used to build a concrete domain component")
  (to-domain-node [this] "Transforms this partially parsed domain element into a concrete domain component"))


(defprotocol EndPoint
  (supported-operations [this] "HTTP operations supported by this end-point")
  (path [this] "(partial) IRI template where the operations are bound to"))

(defrecord ParsedEndPoint [id sources name description extends path supported-operations parameters]
  EndPoint
  (supported-operations [this] supported-operations)
  (path [this] path)
  ParametersHolder
  (parameters [this] (or parameters []))
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends [])))

(defprotocol Payload
  (schema [this] "Schema for the payload")
  (media-type [this] "Media type associated to this payload"))

(defrecord ParsedPayload [id sources name description extends
                          schema media-type]
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends []))
  Payload
  (schema [this] schema)
  (media-type [this] media-type))

(defprotocol PayloadHolder
  (payloads [this] "media-type / payload schemas"))

(defprotocol Operation
  (method [this] "HTTP method this operation is bound to")
  (request [this] "HTTP request information")
  (responses [this] "HTTP responses"))

(defrecord ParsedOperation [id sources name description extends
                            method host scheme accepts content-type
                            responses request]
  Operation
  (method [this] method)
  (request [this] request)
  (responses [this] responses)
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends []))
  CommonAPIProperties
  (scheme [this] scheme)
  (accepts [this] accepts)
  (content-type [this] content-type))

(defprotocol Response
  (status-code [this] "Status code for the response"))


(defrecord ParsedResponse [id sources name description extends
                           status-code headers payloads]
  Response
  (status-code [this] status-code)
  PayloadHolder
  (payloads [this] payloads)
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends []))
  HeadersHolder
  (headers [this] (or headers [])))


(defprotocol Type
  (shape [this] "Constraints for the data type"))


(defrecord ParsedType [id sources name extends description shape ]
  Type
  (shape [this] shape)
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends [])))


(defprotocol Parameter
  (parameter-kind [this] "What kind of parameter is this")
  (required [this] "Is this parameter required"))

(defrecord ParsedParameter [id sources name description extends
                            parameter-kind shape required]
  Parameter
  (parameter-kind [this] parameter-kind)
  (required [this] required)
  Type
  (shape [this] shape)
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends [])))

(defrecord ParsedRequest [id sources name description extends parameters accepts headers payloads]
  HeadersHolder
  (headers [this] (or headers []))
  ParametersHolder
  (parameters [this] (or parameters []))
  PayloadHolder
  (payloads [this] (or payloads []))
  document/Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources)
  (valid? [this] true)
  (extends [this] (or extends [])))

(defrecord  ParsedDomainElement [id fragment-node properties extends ]
  document/Node
  (id [this] id)
  (name [this] "Domain element [" fragment-node "]")
  (description [this] (str "Partially parsed node with properties of type  " fragment-node))
  (sources [this] (:sources properties))
  (valid? [this] true)
  (extends [this] (or extends []))
  DomainElement
  (fragment-node [this] fragment-node)
  (properties [this] properties)
  (to-domain-node [this]
    (condp = fragment-node
      :parsed-api-documentation (map->ParsedAPIDocumentation properties)
      :parsed-parameter         (map->ParsedParameter properties)
      :parsed-request           (map->ParsedRequest properties)
      :parsed-operation         (map->ParsedOperation properties)
      :parsed-type              (map->ParsedType properties)
      :trait                    (map->ParsedOperation properties)
      :parsed-end-point         (map->ParsedEndPoint properties)
      :parsed-includes          (document/map->ParsedIncludes properties)
      (throw (new #?(:clj Exception :cljs js/Error) (str "Unknown fragment " fragment-node))))))
