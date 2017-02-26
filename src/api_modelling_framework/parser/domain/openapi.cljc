(ns api-modelling-framework.parser.domain.openapi
  (:require [clojure.string :as string]
            [api-modelling-framework.model.syntax :as syntax]
            [api-modelling-framework.model.document :as document]
            [api-modelling-framework.model.domain :as domain]
            [api-modelling-framework.parser.domain.json-schema-shapes :as shapes]
            [api-modelling-framework.utils :as utils]
            [api-modelling-framework.model.vocabulary :as v]
            [clojure.set :as set]
            [cemerick.url :as url]
            [taoensso.timbre :as timbre
             #?(:clj :refer :cljs :refer-macros)
             [debug]]))


(def properties-map
  {
   :basePath #{:swagger}
   :consumes #{:swagger :operation}
   :contact #{:info}
   :default #{:type}
   :definitions #{:swagger}
   :delete #{:path-item}
   :deprecated #{:operation}
   :description #{:info :operation :response :type}
   :enum #{:type}
   :examples #{:response}
   :exclusiveMaximum #{:type}
   :exclusiveMinimum #{:type}
   :externalDocs #{:swagger :operation}
   :format #{:type}
   :get #{:path-item}
   :head #{:path-item}
   :headers #{:response}
   :host #{:swagger}
   :info #{:swagger}
   :license #{:info}
   :maxItems #{:type}
   :maxLength #{:type}
   :maxProperties #{:type}
   :maximum #{:type}
   :minItems #{:type}
   :minLength #{:type}
   :minProperties #{:type}
   :minimum #{:type}
   :multipleOf #{:type}
   :operationId #{:operation}
   :options #{:path-item}
   :parameters #{:swagger :path-item :operation}
   :patch #{:path-item}
   :paths #{:swagger}
   :pattern #{:type}
   :post #{:path-item}
   :produces #{:swagger :operation}
   :put #{:path-item}
   :required #{:type}
   :responses #{:swagger :operation}
   :schema #{:response}
   :schemes #{:swagger :operation}
   :security #{:swagger :operation}
   :securityDefinitions #{:swagger}
   :summary #{:operation}
   :swagger #{:swagger}
   :tags #{:swagger :operation}
   :termsOfService #{:info}
   :title #{:info :type}
   :type #{:type}
   :uniqueItems #{:type}
   :version #{:info}
   })

(defn guess-type-from-predicates [x]
  (->> [(fn [x] (when (string/starts-with? (utils/safe-str x) "/") #{:paths}))
        (fn [x] (when (some? (re-matches #"^\d+$" (utils/safe-str x))) #{:responses}))]
       (mapv (fn [p] (p x)))
       (filterv some?)
       first))

(defn guess-type [node]
  (let [node-types (->> node
                        (mapv (fn [[k _]] (get properties-map k (guess-type-from-predicates k))))
                        (filterv some?))
        node-type (first (if (empty? node-types) [] (apply set/intersection node-types)))]
    (or node-type :undefined)))

(defn parse-ast-dispatch-function [node context]
  (cond
    (some? (syntax/<-location node)) :fragment
    (nil? node)                              :undefined
    (some? (:type-hint context))             (:type-hint context)
    :else                                    (guess-type node)))

(defmulti parse-ast (fn [node context] (parse-ast-dispatch-function node context)))

(defn generate-inline-fragment-parsed-sources [parsed-location fragment-name fragment-location]
  (let [source-map-id (str parsed-location "/source-map/inline-fragment/" fragment-name)
        inline-fragment-parsed-tag (document/->InlineFragmentParsedTag source-map-id fragment-location)]
    [(document/->DocumentSourceMap (str parsed-location "/source-map")
                                   fragment-location
                                   [inline-fragment-parsed-tag])]))

(defn generate-extend-include-fragment-sources [parsed-location fragment-location]
  (let [source-map-id (str parsed-location "/source-map/inline-fragment")
        parsed-tag (document/->ExtendIncludeFragmentParsedTag source-map-id fragment-location)]
    [(document/->DocumentSourceMap (str parsed-location "/source-map")
                                   fragment-location
                                   [parsed-tag])]))

(defn generate-is-trait-sources [trait-name location parsed-location]
  (let [source-map-id (str parsed-location "/source-map/is-trait")
        is-trait-tag (document/->IsTraitTag source-map-id trait-name)]
    [(document/->DocumentSourceMap (str parsed-location "/source-map") location [is-trait-tag])]))

(defn process-traits [node {:keys [location parsed-location] :as context}]
  (debug "Processing " (count (:x-traits node [])) "traits")
  (let [location (str location "/traits")
        parsed-location (str parsed-location "/traits")
        nested-context (-> context (assoc :location location) (assoc :parsed-location parsed-location))]
    (->> (:x-traits node {})
         (reduce (fn [acc [trait-name trait-node]]
                   (debug (str "Processing trait " trait-name))
                   (let [fragment-name (url/url-encode (utils/safe-str trait-name))
                         trait-fragment (parse-ast trait-node (-> nested-context
                                                                  (assoc :method fragment-name)
                                                                  (assoc :location location)
                                                                  (assoc :parsed-location parsed-location)
                                                                  (assoc :is-fragment true)
                                                                  (assoc :type-hint :operation)))
                         trait-fragment (assoc trait-fragment :id (str parsed-location "/" fragment-name))
                         trait-fragment (assoc-in trait-fragment [:properties :id] (str parsed-location "/" fragment-name))
                         sources (or (-> trait-fragment :properties :sources) [])
                         sources (concat sources (generate-is-trait-sources fragment-name
                                                                            (str location "/" fragment-name)
                                                                            (str parsed-location "/" fragment-name)))
                         parsed-trait (assoc-in trait-fragment [:properties :sources] sources)]
                     (assoc acc (keyword trait-name) parsed-trait)))
                 {}))))

(defn generate-parsed-node-sources [node-name location parsed-location]
  (let [source-map-parsed-location (str parsed-location "/source-map/" node-name)
        node-parsed-tag (document/->NodeParsedTag source-map-parsed-location location)]
    [(document/->DocumentSourceMap (str parsed-location "/source-map") location [node-parsed-tag])]))

(defn generate-open-api-tags-sources [tags location parsed-location]
  (let [tags (or tags [])]
    (->> tags
         (mapv (fn [i tag]
                 (let [parsed-location (str parsed-location "/source-map/api-tags-" i "/tag")]
                   (document/->DocumentSourceMap
                    (str parsed-location "/source-map/api-tags-" i)
                    location
                    [(document/->APITagTag parsed-location tag)])))
               (range 0 (count tags))))))

(defn find-extend-tags [{:keys [location parsed-location references] :as context}]
  (->> references
       (mapv (fn [[extend-name parsed-domain-element]]
               (condp = (:fragment-node parsed-domain-element)
                 :trait (generate-inline-fragment-parsed-sources (str parsed-location "/traits")
                                                                 (name extend-name)
                                                                 (:id parsed-domain-element))
                 (generate-inline-fragment-parsed-sources (str parsed-location "/declares")
                                                          (name extend-name)
                                                          (:id parsed-domain-element)))))
       flatten))

(defmethod parse-ast :swagger [node {:keys [location parsed-location is-fragment] :as context}]
  (debug "Parsing swagger")
  (let [parsed-location (str parsed-location "/api-documentation")
        location (str location "/")
        trait-tags (find-extend-tags (-> context
                                         (assoc :location location)
                                         (assoc :parsed-location parsed-location)))
        sources (concat (generate-parsed-node-sources "root" location parsed-location)
                        trait-tags)
        endpoints (parse-ast (:paths node) (-> context
                                               (assoc :type-hint :paths)
                                               (assoc :parsed-location parsed-location)
                                               (assoc :location location)))
        properties {:id parsed-location
                    :host (:host node)
                    :scheme (flatten [(:schemes node)])
                    :base-path (:basePath node)
                    :accepts (flatten [(:consumes node)])
                    :content-type (flatten [(:produces node)])
                    :provider nil
                    :license nil
                    :endpoints endpoints}
        node-info-properties (if (some? (:info node))
                               (domain/properties (parse-ast (:info node) (-> context
                                                                              (assoc :type-hint :info)
                                                                              (assoc :is-fragment true)
                                                                              (assoc :parsed-location parsed-location)
                                                                              (assoc :location location))))
                               {})
        sources (concat (get node-info-properties :sources []) sources)
        properties (merge properties (dissoc node-info-properties :fragment-node))
        properties (assoc properties :sources sources)]
    (if is-fragment
      (domain/map->ParsedDomainElement {:id parsed-location
                                        :fragment-node :parsed-api-documentation
                                        :properties {:id parsed-location
                                                     :fragment-node :parsed-api-documentation
                                                     :properties properties}})
      (domain/map->ParsedAPIDocumentation properties))))

(defmethod parse-ast :info [node {:keys [location parsed-location is-fragment]}]
  (debug "Parsing info")
  (let [location (str location "info")]
    (if (not is-fragment)
      (throw (new #?(:clj Exception :cljs js/Error) (str "Cannot parsed domain element from info node at " location)))
      (domain/map->ParsedDomainElement {:id parsed-location
                                        :fragment-node :parsed-api-documentation
                                        :properties {:name (:title node)
                                                     :fragment-node :info
                                                     :description (:description node)
                                                     :version (:version node)
                                                     :sources (generate-parsed-node-sources "info" location parsed-location)
                                                     :terms-of-service (:termsOfService node)}}))))

(defmethod parse-ast :paths [node {:keys [location parsed-location is-fragment] :as context}]
  (debug "Parsing paths")
  (let [location (str location "paths")
        paths-sources (generate-parsed-node-sources "paths" location parsed-location)
        nested-resources (utils/extract-nested-resources node)]
    (mapv (fn [i {:keys [path resource]}]
            (let [context (-> context
                              (assoc :type-hint :path-item)
                              (assoc :location (str location "/" (url/url-encode path)))
                              (assoc :parsed-location (str parsed-location "/end-points/" i))
                              (assoc :path path)
                              (assoc :paths-sources paths-sources))]
              (parse-ast resource context)))
          (range 0 (count nested-resources))
          nested-resources)))

(defn generate-extends-trait-sources [trait-name location parsed-location]
  (let [source-map-id (str parsed-location "/source-map/extend-trait")
        extends-trait-tag (document/->ExtendsTraitTag source-map-id trait-name)]
    [(document/->DocumentSourceMap (str parsed-location "/source-map") location [extends-trait-tag])]))

(defn parse-traits [resource-id node references {:keys [location parsed-location]}]
  (let [traits (flatten [(:x-is node [])])]
    (->> traits
         (mapv (fn [trait-name]
                 [trait-name (-> references
                                 (get (keyword trait-name)))]))
         (mapv (fn [i [trait-name trait]]
                 (if (some? trait)
                   (let
                       [extend-id (str parsed-location "/extends/" (url/url-encode trait-name))
                        extend-location (str location "/x-is/" i)
                        node-parsed-source-map (generate-parsed-node-sources "x-is" location extend-id)
                        extends-trait-source-map (generate-extends-trait-sources trait-name extend-location extend-id)]
                     (document/map->ParsedExtends {:id extend-id
                                                   :sources (concat node-parsed-source-map
                                                                    extends-trait-source-map)
                                                   :target (document/id trait)
                                                   :label "trait"
                                                   :arguments []}))
                   (throw (new #?(:clj Exception :cljs js/Error)
                               (str "Cannot find trait '" trait-name "' to extend in node '" resource-id "'")))))
               (range 0 (count traits))))))

(defmethod parse-ast :path-item [node {:keys [location parsed-location is-fragment path paths-sources references] :as context}]
  (debug "Parsing path-item")
  (when (nil? path)
    (throw (new #?(:clj Exception :cljs js/Error) "Cannot parse path-item object without contextual path information")))
  (let [location (str location "/" (url/url-encode path))
        parsed-location (str parsed-location "/" (url/url-encode path))
        traits (parse-traits parsed-location node references (assoc context :parsed-location parsed-location))
        operations (->> [:get :put :post :delete :options :head :patch]
                        (mapv (fn [op] (if-let [method-node (get node op)]
                                        (parse-ast method-node (-> context
                                                                   (assoc :type-hint :operation)
                                                                   (assoc :method (name op))
                                                                   (assoc :location location)
                                                                   (assoc :parsed-location parsed-location)))
                                        nil)))
                        (filterv some?))
        properties {:path path
                    :sources (concat (generate-parsed-node-sources "path-item" location parsed-location) (or paths-sources []))
                    :id parsed-location
                    :supported-operations operations
                    :extends traits}]
    (if is-fragment
      (domain/map->ParsedDomainElement {:id parsed-location
                                        :fragment-node :parsed-end-point
                                        :properties properties})
      (domain/map->ParsedEndPoint properties))))

(defn parse-params [parameters {:keys [location parsed-location is-fragment] :as context}]
  (->> (or parameters [])
       (filterv (fn [parameter] (not= "body" (:in parameter))))
       (mapv (fn [i parameter]
               (let [name (:name parameter)
                     location (str location "[" i "]")
                     parsed-location (str parsed-location "/" (url/url-encode name))
                     node-sources (generate-parsed-node-sources "parameter" location parsed-location)]
                 {:id parsed-location
                  :name name
                  :description (:description parameter)
                  :sources node-sources
                  :parameter-kind (:in parameter)
                  :required (:required parameter)
                  :shape (shapes/parse-type (-> parameter
                                                (dissoc :name)
                                                (dissoc :description)
                                                (dissoc :in))
                                            (-> context
                                                (assoc :location location)
                                                (assoc :parsed-location parsed-location)))}))
             (range 0 (count parameters)))
       (mapv (fn [properties]
               (if is-fragment
                 (domain/map->ParsedDomainElement {:id parsed-location
                                                   :fragment-node :parsed-parameter
                                                   :properties properties})
                 (domain/map->ParsedParameter properties))))))

(defn parse-body [parameters {:keys [location parsed-location is-fragment] :as context}]
  (->> (or parameters [])
       (filterv (fn [parameter] (= "body" (:in parameter))))
       (mapv (fn [i parameter]
               (let [name (:name parameter)
                     location (str location "/parameters[" i "]")
                     parsed-location (str parsed-location "/body")
                     node-sources (generate-parsed-node-sources "body" location parsed-location)]
                 {:id parsed-location
                  :name name
                  :description (:description parameter)
                  :sources node-sources
                  :shape (shapes/parse-type (:schema parameter)
                                            (-> context
                                                (assoc :is-fragment false)
                                                (assoc :type-hint :type)
                                                (assoc :location location)
                                                (assoc :parsed-location parsed-location)))}))
             (range 0 (count parameters)))
       (mapv (fn [properties]
               (if is-fragment
                 (domain/map->ParsedDomainElement {:id parsed-location
                                                   :fragment-node :parsed-type
                                                   :properties properties})
                 (domain/map->ParsedType properties))))
       first))

(defmethod parse-ast :operation [node {:keys [location parsed-location is-fragment method references] :as context}]
  (debug "Parsing method " method)
  (let [location (str location "/" method)
        parsed-location (str location "/" method)
        next-context (-> context
                         (assoc :is-fragment false)
                         (assoc :parsed-location parsed-location)
                         (assoc :location location))
        traits (parse-traits parsed-location node references next-context)
        node-parsed-source-map (generate-parsed-node-sources method location parsed-location)
        api-tags (generate-open-api-tags-sources (:tags node) location parsed-location)
        x-response-bodies-with-media-types (:x-response-bodies-with-media-types node)
        parameters (parse-params (:parameters node) (-> context
                                                        (assoc :is-fragment false)
                                                        (assoc :location (str location "/parameters"))
                                                        (assoc :parsed-location (str parsed-location "/parameters"))))
        headers (->> parameters (filterv #(= "header" (:parameter-kind %))))
        parameters (->> parameters (filterv #(not= "header" (:parameter-kind %))))
        body (parse-body (:parameters node) (-> context
                                                (assoc :is-fragment false)
                                                (assoc :location (str location "/parameters"))
                                                (assoc :parsed-location (str parsed-location "/body"))))
        request-id (str parsed-location "/request")
        request (domain/map->ParsedRequest {:id request-id
                                            :sources (generate-parsed-node-sources "request" location request-id)
                                            :parameters parameters
                                            :schema body})
        properties {:id parsed-location
                    :method method
                    :sources (concat node-parsed-source-map api-tags)
                    :name (:operationId node)
                    :description (:description node)
                    :scheme (:schemes node)
                    :accepts (:consumes node)
                    :content-type (:produces node)
                    :headers headers
                    :request request
                    :responses (parse-ast (:responses node) (-> context
                                                                (assoc :type-hint :responses)
                                                                (assoc :location location)
                                                                (assoc :parsed-location parsed-location)
                                                                (assoc :is-fragment false)
                                                                (assoc :x-response-bodies-with-media-types x-response-bodies-with-media-types)))}]
    (if is-fragment
      (domain/map->ParsedDomainElement {:id parsed-location
                                        :fragment-node :parsed-operation
                                        :properties properties})
      (domain/map->ParsedOperation properties))))

(defmethod parse-ast :responses [node {:keys [location parsed-location is-fragment x-response-bodies-with-media-types] :as context}]
  (debug "Parsing responses")
  (->> node
       (mapv (fn [[key response]]
               (let [key (if x-response-bodies-with-media-types
                           (first (string/split (utils/safe-str key) #"--"))
                           key)]
                 (parse-ast response (-> context
                                         (assoc :type-hint :response)
                                         (assoc :response-key key)
                                         (assoc :is-fragment false)
                                         (assoc :location (str location "/responses"))
                                         (assoc :parsed-location (str parsed-location "/responses")))))))))

(defmethod parse-ast :response [node {:keys [location parsed-location is-fragment response-key] :as context}]
  (debug "Parsing response " response-key)
  (let [response-key (name response-key)
        response-id (str parsed-location "/" response-key)
        location (str location "/" response-key)
        is-status (some? (re-find #"^\d+$" (name response-key)))
        node-parsed-source-map (generate-parsed-node-sources (str "response-" response-key) response-id parsed-location)
        properties {:id response-id
                    :sources node-parsed-source-map
                    :status-code (if is-status (name response-key) nil)
                    :name response-key
                    :description (:description node)
                    :schema (parse-ast (:schema node) (-> context
                                                          (assoc :location location)
                                                          (assoc :parsed-location response-id)
                                                          (assoc :type-hint :type)))}]
    (if is-fragment
      (domain/map->ParsedDomainElement {:id response-id
                                        :fragment-node :parsed-response
                                        :properties properties})
      (domain/map->ParsedResponse properties))))



(defmethod parse-ast :type [node {:keys [location parsed-location is-fragment] :as context}]
  (debug "Parsing type")
  (let [type-id (str parsed-location "/type")
        shape (shapes/parse-type node (assoc context :parsed-location type-id))]
    (if is-fragment
      {:id type-id
       :shape shape}
      (domain/map->ParsedType {:id type-id
                               :shape shape}))))

(defmethod parse-ast :fragment [node {:keys [location parsed-location is-fragment fragments type-hint document-parser]
                                      :or {fragments (atom {})}
                                      :as context}]
  (let [fragment-location (syntax/<-location node)]
    (let [parsed-fragment (document-parser node context)
          encoded-element (document/encodes parsed-fragment)
          encoded-element-sources (-> encoded-element :properties :sources)
          clean-encoded-element (condp = type-hint
                                  ;; this information is sensitive to the context, can never be in the fragment
                                  :operation (-> encoded-element
                                                 (assoc-in [:properties :method] nil)
                                                 (assoc-in [:properties :sources] nil))
                                  :path-item (-> encoded-element
                                                 (assoc [:properties:path] nil)
                                                 (assoc [:properties:path :sources] nil))
                                  encoded-element)
          parsed-location (str parsed-location "/includes")
          extends [(document/map->ParsedExtends {:id parsed-location
                                                 :sources (generate-extend-include-fragment-sources parsed-location fragment-location)
                                                 :target fragment-location
                                                 :label "!includes"
                                                 :arguments []})]]
      (swap! fragments (fn [acc]
                         (if (some? (get acc fragment-location))
                           acc
                           (assoc acc fragment-location (assoc parsed-fragment :encodes clean-encoded-element)))))
      (condp = type-hint
        :operation (domain/map->ParsedOperation {:id parsed-location
                                                 :method (utils/safe-str (-> encoded-element :properties :method))
                                                 :sources encoded-element-sources
                                                 :extends extends})
        :path-item (domain/map->ParsedEndPoint {:id parsed-location
                                                :path (-> encoded-element :properties :path)
                                                :extends extends
                                                :sources encoded-element-sources})
        (let [properties {:id parsed-location
                          :label "$ref"
                          :target fragment-location}]
          (document/map->ParsedIncludes properties))))))

(defmethod parse-ast :undefined [_ _]
  (debug "Parsing undefined")
  nil)
