(ns api-modelling-framework.model.document)

(defprotocol Node
  "Basic behaviour for every single node in the model"
  (id [this] "Returns a unique identifier for this node")
  (name [this] "A string readable title for this node")
  (description [this] "A human readable description of this node")
  (sources [this] "Collection of source maps for this node")
  (valid? [this] "Checks if this node is valid")
  (extends [this] "Nodes extended by this node"))

(defprotocol Extends
  (target [this] "Target of the extends relationship")
  (label  [this] "Description of the kind of extends relationship")
  (arguments [this] "Arguments for the extension"))

(defrecord ParsedExtends [id name description sources target label arguments]
  Extends
  (target [this] target)
  (label  [this] label)
  (arguments [this] (or arguments []))
  Node
  (id [this] id)
  (name [this] name)
  (description [this] description)
  (sources [this] sources))

(defprotocol SourceMap
  "Defines basic behaviour for all nodes in the model"
  (source [this] "URI identifying the location of the information source in the layer n - 1
                    In the document model, the location will be a file or HTTP resource, in the service layer, it will be a node in the document layer.")
  (tags [this] "Annotations describing the kind of relationship between the source of information and the target node"))

(defrecord DocumentSourceMap [id source tags]
  SourceMap
  (source [this] source)
  (tags [this] tags)
  Node
  (id [this] id)
  (name [this] "Document source map")
  (description [this] (str "Source map for a document located at " source))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(defprotocol Tag
  (tag-id [this] "Identifier for the tag")
  (value [this] "A value associated to this tag"))

(defn find-tag [node tag-id-to-find]
  (let [sources (or (:sources  node) (try (sources node) (catch #?(:cljs js/Error :clj Exception) ex nil)))]
    (if (some? sources)
      (->> sources
           (map tags)
           flatten
           (filter #(= tag-id-to-find (tag-id %)))
           flatten)
      [])))

(def file-parsed-tag "file-parsed")

(defrecord FileParsedTag [id location]
  Tag
  (tag-id [this] file-parsed-tag)
  (value [this] location)
  Node
  (id [this] id)
  (name [this] "File parsed tag")
  (description [this] (str "This node was generating parsing a file located at " location))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def document-type-tag "document-type")

(defrecord DocumentTypeTag [id document-type]
  Tag
  (tag-id [this] document-type-tag)
  (value [this] document-type)
  Node
  (id [this] id)
  (name [this] "Document type tag")
  (description [this] (str "This node was generating parsing a file of type " document-type))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def node-parsed-tag "node-parsed")

(defrecord NodeParsedTag [id path]
  Tag
  (tag-id [this] node-parsed-tag)
  (value [this] path)
  Node
  (id [this] id)
  (name [this] "Node parsed tag")
  (description [this] (str "This node was generating parsing a node located at " path))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def nested-resource-path-parsed-tag "nested-resource-path-parsed")

(defrecord NestedResourcePathParsedTag [id path]
  Tag
  (tag-id [this] nested-resource-path-parsed-tag)
  (value [this] path)
  Node
  (id [this] id)
  (name [this] "Nested resource parsed tag")
  (description [this] (str "This node was generating parsing a nested resource with path " path))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def nested-resource-children-tag "nested-resource-nested-children")

(defrecord NestedResourceChildrenTag [id children-id]
  Tag
  (tag-id [this] nested-resource-children-tag)
  (value [this] children-id)
  Node
  (id [this] id)
  (name [this] "Resource nested children tag")
  (description [this] (str "This resource has a nested resource " children-id))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def nested-resource-parent-id-tag "nested-resource-parent-id")

(defrecord NestedResourceParentIdTag [id parent-id]
  Tag
  (tag-id [this] nested-resource-parent-id-tag)
  (value [this] parent-id)
  Node
  (id [this] id)
  (name [this] "Resource parent id  tag")
  (description [this] (str "This resource has a parent resource " parent-id))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def api-tag-tag "api-tag-tag")

(defrecord APITagTag [id tag-value]
  Tag
  (tag-id [this] api-tag-tag)
  (value [this] tag-value)
  Node
  (id [this] id)
  (name [this] "API specific tag")
  (description [this] (str "API specific tag with value " tag-value))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def inline-fragment-parsed-tag "inline-fragment-parsed-tag")

(defrecord InlineFragmentParsedTag [id tag-value]
  Tag
  (tag-id [this] inline-fragment-parsed-tag)
  (value [this] tag-value)
  Node
  (id [this] id)
  (name [this] "Inline fragment  tag")
  (description [this] (str "Inline fragment tag with value " tag-value))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(def is-trait-tag "is-trait-tag")

(defrecord IsTraitTag [id trait-name]
  Tag
  (tag-id [this] is-trait-tag)
  (value [this] trait-name)
  Node
  (id [this] id)
  (name [this] "Is trait tag")
  (description [this] (str "Is trait tag with name " trait-name))
  (sources [this] [])
  (valid? [this] true)
  (extends [this] []))

(defprotocol DocumentUnit
  "Any parseable unit, it should be backed by a source URI"
  (encodes [this] "Domain description encoded into this document unit")
  (declares [this] "Depdencies of this unit with other parsing units"))



(defn generate-document-sources [source document-type]
  (let [source-map-id (str source "#/source-map/0")
        file-parsed-tag (if (some? source)
                          (FileParsedTag. (str source-map-id "/tag/file-parsed") source)
                          nil)
        document-type-tag (if (some? document-type)
                            (DocumentTypeTag. (str source-map-id "/tag/document-type") document-type)
                            nil)]
    (if (some? source)
      [(DocumentSourceMap. source-map-id source (filter some? [file-parsed-tag document-type-tag]))]
      [])))

(defrecord Document [location encodes declares document-type]
  Node
  (id [this] location)
  (name [this] location)
  (description [this] (str "Document parsed from " location " encoding " document-type " information "))
  (sources [this] (generate-document-sources location document-type))
  (valid? [this] true)
  (extends [this] [])
  DocumentUnit
  (encodes [this] encodes)
  (declares [this] declares))

(defrecord Fragment [location encodes document-type]
  Node
  (id [this] location)
  (name [this] location)
  (description [this] (str "Fragment parsed from " location " encoding " document-type " information "))
  (sources [this] (generate-document-sources location document-type))
  (valid? [this] true)
  (extends [this] [])
  DocumentUnit
  (encodes [this] encodes)
  (declares [this] nil))