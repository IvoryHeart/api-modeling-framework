(ns api-modelling-framework.generators.domain.raml
  (:require [api-modelling-framework.model.vocabulary :as v]
            [api-modelling-framework.generators.domain.shapes-raml-types :as shapes-parser]
            [api-modelling-framework.model.document :as document]
            [api-modelling-framework.model.domain :as domain]
            [api-modelling-framework.utils :as utils]
            [api-modelling-framework.generators.domain.utils :refer [send]]
            [clojure.string :as string]
            [cemerick.url :as url]
            [clojure.walk :refer [keywordize-keys]]
            [taoensso.timbre :as timbre
             #?(:clj :refer :cljs :refer-macros)
             [debug]]))


(defn to-raml-dispatch-fn [model ctx]
  (cond
    (nil? model)                                    model

    (and (satisfies? document/Includes model)
         (satisfies? document/Node model))          document/Includes


    (and (satisfies? domain/APIDocumentation model)
         (satisfies? document/Node model))          domain/APIDocumentation

    (and (satisfies? domain/EndPoint model)
         (satisfies? document/Node model))          domain/EndPoint

    (and (satisfies? domain/Operation model)
         (satisfies? document/Node model))          domain/Operation

    (and (satisfies? domain/Response model)
         (satisfies? document/Node model))          domain/Response

    (and (satisfies? domain/Type model)
         (satisfies? document/Node model))          domain/Type

    :else                                           (type model)))

(defmulti to-raml (fn [model ctx] (to-raml-dispatch-fn model ctx)))

(defn model->base-uri [model]
  (let [scheme (or (domain/scheme model) [])
        host (domain/host model)
        base-path (domain/base-path model)]
    (if (some? host)
      (str (or (first scheme) "http") "://" host base-path)
      nil)))

(defn model->protocols [model]
  (let [schemes (domain/scheme model)]
    (cond
      (and (some? schemes)
           (= 1 (count schemes))) (first schemes)
      (some? schemes)             schemes
      :else                       nil)))

(defn model->media-type [model]
  (let [contents (or (domain/content-type model) [])
        accepts (or (domain/accepts model) [])
        media-types (concat contents accepts)]
    (cond
      (empty? media-types)      nil
      (nil? media-types)        nil
      (= 1 (count media-types)) (first media-types)
      :else                     media-types)))

(defn find-children-resources
  "Find the children for a particular model using the information stored in the tags"
  [id children]
  (->> children
       (filter (fn [child] (= (-> child
                                 (document/find-tag document/nested-resource-parent-id-tag)
                                 (->> (mapv document/value))
                                 first)

                             id)))))

(defn merge-children-resources
  "We merge the children in the current node using the paths RAML style"
  [node children-resources ctx]
  (let [children-node (->> children-resources
                           (mapv (fn [child]
                                   (let [child-path (-> child
                                                        (document/find-tag document/nested-resource-path-parsed-tag)
                                                        first)
                                         ;; the node might come from a OpenAPI model, it will not have path tag
                                         child-path (if (some? child-path)
                                                      (document/value child-path)
                                                      (domain/path child))]
                                     [(keyword (utils/safe-str child-path)) (to-raml child ctx)]))))
        children-node (into {} children-node)]
    (merge node children-node)))

(defn model->traits [model {:keys [references] :as ctx}]
  (->> (document/find-tag model document/inline-fragment-parsed-tag)
       (map (fn [tag]
              (let [trait-id (document/value tag)
                    reference (->> references
                                   (filter (fn [reference] (= (document/id reference) trait-id)))
                                   first)
                    is-trait-tag (-> reference
                                     (document/find-tag document/is-trait-tag)
                                     first)
                    trait-name (if is-trait-tag
                                 (-> is-trait-tag
                                     (document/value)
                                     keyword)
                                 (-> trait-id
                                     name
                                     (clojure.string/split #"/")
                                     last))
                    method (if (some? reference)
                             (domain/to-domain-node reference)
                             (throw (new #?(:cljs js/Error :clj Exception) (str "Cannot find extended trait " trait-name))))
                    generated (to-raml method ctx)]
                [trait-name generated])))
       (into {})))

(defmethod to-raml domain/APIDocumentation [model ctx]
  (debug "Generating RAML root node")
  (let [all-resources (domain/endpoints model)
        ctx (assoc ctx :all-resources all-resources)
        children-resources (find-children-resources (document/id model) all-resources)
        ;; this can happen if we are generating from a model that does not generate the tags,
        ;; e.g. OpenAPI, in this case all the children resources are children of the APIDocumentation node
        children-resources (if (empty? children-resources) all-resources children-resources)]
    (-> {:title (document/name model)
         :description (document/description model)
         :version (domain/version model)
         :baseUri (model->base-uri model)
         :protocols (model->protocols model)
         :mediaType (model->media-type model)
         :traits (model->traits model ctx)}
        (merge-children-resources children-resources ctx)
        utils/clean-nils)))

(defn find-traits [model context]
  (let [extends (document/extends model)]
    ;; @todo do I need both checks, label and is-trait-tag ??
    (->> extends
         (filter (fn [extend] (= "trait" (name (document/label extend)))))
         (map (fn [trait]
                (let [trait-tag (first (document/find-tag trait document/is-trait-tag))]
                  (if (some? trait-tag)
                    (document/value trait-tag)
                    (-> (document/target trait) (string/split #"/") last))))))))


(defmethod to-raml domain/EndPoint [model {:keys [all-resources] :as ctx}]
  (debug "Generating resource " (document/id model))
  (let [children-resources (find-children-resources (document/id model) all-resources)
        operations (->> (or (domain/supported-operations model) [])
                        (map (fn [op]
                               [(keyword (send domain/method op ctx)) (to-raml op ctx)]))
                        (into {}))]
    (-> {:displayName (document/name model)
         :is (find-traits model ctx)
         :description (document/description model)}
        (merge operations)
        (merge-children-resources children-resources ctx)
        (utils/clean-nils))))

(defn group-responses [responses context]
  (->> responses
       (map (fn [response] [(document/name response) response]))
       ;; multiple responses sharing the status-code belong to the same group in RAML
       (reduce (fn [acc [key response]]
                 (let [responses (get acc key [])
                       responses (concat responses [response])]
                   (assoc acc key responses)))
               {})
       ;; We expand element in the groups based in the presence of content type
       (map (fn [[key responses]]
              (if (= 1 (count responses))
                ;; just one response, either it has a content type and become a  map or we plug it directly
                (let [response (first responses)
                      parsed-response (to-raml response context)
                      body (:body parsed-response)]
                  (if-let [content-type (some? (-> response domain/content-type first))]
                    [key (-> parsed-response
                             (assoc :body {(utils/safe-str content-type) body})
                             utils/clean-nils)]
                    [key (utils/clean-nils parsed-response)]))
                ;; If there are more than one, it has to have a content-type
                (let [responses (reduce (fn [acc response]
                                          (assoc acc (-> response domain/content-type first utils/safe-str) (to-raml response context)))
                                        {}
                                        responses)
                      common-response (dissoc (->> responses vals first) :body)
                      responses-bodies (->> responses
                                            (map (fn [[k response]]
                                                   [k (:body response)]))
                                            (into {}))]
                  [key (utils/clean-nils (assoc common-response :body responses-bodies))]))))
       (into {})))

(defn unparse-parameters [parameters context]
  (if (nil? parameters) nil
      (->> parameters
           (map (fn [parameter]
                  (let [parsed-type (keywordize-keys (shapes-parser/parse-shape (domain/shape parameter) context))
                        ;; @todo should we really keep a source-map to see if we should add this mapping by default?
                        ;;parsed-type (if (= "string" (:type parsed-type))
                        ;;              (dissoc parsed-type :type)
                        ;;              parsed-type)
                        parsed-type (if (some? (domain/required parameter))
                                      (assoc parsed-type :required (domain/required parameter))
                                      parsed-type)]
                    [(keyword (document/name parameter))
                     parsed-type])))
           (into {}))))

(defn unparse-domain-body [request context]
  (if (nil? request) nil
      (if-let [body (domain/schema request)]
        (to-raml body context)
        nil)))

(defn unparse-query-parameters [request context]
  (if (nil? request) nil
      (if-let [parameters (domain/parameters request)]
        (unparse-parameters parameters context)
        nil)))

(defmethod to-raml domain/Operation [model context]
  (debug "Generating operation " (document/id model))
  (-> {:displayName (document/name model)
       :description (document/description model)
       :protocols (domain/scheme model)
       :responses (-> (domain/responses model)
                      (group-responses context))
       :is (find-traits model context)
       :body (unparse-domain-body (domain/request model) context)
       :queryParameters (unparse-query-parameters (domain/request model) context)
       :headers (unparse-parameters (domain/headers model) context)}
      utils/clean-nils))

(defmethod to-raml domain/Response [model context]
  (debug "Generating response " (document/name model))
  {:description (document/description model)
   :body (to-raml (domain/schema model) context)})

(defmethod to-raml domain/Type [model context]
  (debug "Generating type")
  (keywordize-keys (shapes-parser/parse-shape (domain/shape model) context)))

(defmethod to-raml document/Includes [model {:keys [fragments expanded-fragments document-generator]
                                             :as context
                                             :or {expanded-fragments (atom {})}}]
  (let [fragment-target (document/target model)
        fragment (get fragments fragment-target)]
    (if (nil? fragment)
      (throw (new #?(:clj Exception :cljs js/Error) (str "Cannot find fragment " fragment-target " for generation")))
      (if-let [expanded-fragment (get expanded-fragments fragment-target)]
        expanded-fragment
        (let [expanded-fragment (document-generator fragment context)]
          (swap! expanded-fragments (fn [acc] (assoc acc fragment-target expanded-fragment)))
          expanded-fragment)))))

(defmethod to-raml nil [_ _]
  (debug "Generating nil")
  nil)
