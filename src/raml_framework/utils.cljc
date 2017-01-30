(ns raml-framework.utils
  (:require [raml-framework.model.document :as document]
            [raml-framework.model.vocabulary :as v]
            [clojure.string :as string]
            [taoensso.timbre :as timbre
             #?(:clj :refer :cljs refer-macros)
             [log]]))

(defn has-class? [m c]
  (let [types (flatten [(get m "@type")])]
    (->> types (some #(= % c)))))


(defn find-tag [source-map tag-id]
  (when (some? source-map)
    (->> (document/tags source-map)
         (filter #(= tag-id
                     (document/tag-id %)))
         first)))


(defn find-value [m property]
  (-> m (get property) first (get "@value")))

(defn find-link [m property]
  (-> m (get property) first (get "@id")))


(defn find-values [m property]
  (-> m (get property) (->> (map #(get % "@value")))))

(defn find-links [m property]
  (-> m (get property) (->> (map #(get % "@id")))))

(defn clean-nils [jsonld]
  (->> jsonld
       (map (fn [[k v]]
              (let [v (if (and (not (map? v))
                               (coll? v))
                        (filter some? v)
                        v)]
                (cond
                  (nil? v)                   nil
                  (and (coll? v) (empty? v)) nil
                  (and (map? v) (= v {}))    nil
                  :else                      [k v]))))
       (filter some?)
       (into {})))

(defn assoc-value [t m target property]
  (if (some? (property m))
    (assoc t target [{"@value" (property m)}])
    t))

(defn assoc-values [t m target property]
  (if (some? (property m))
    (assoc t target (map (fn [v] {"@value" v}) (property m)))
    t))

(defn assoc-object [t m target property mapping]
  (if (some? (property m))
    (assoc t target [(mapping (property m))])
    t))

(defn assoc-objects [t m target property mapping]
  (if (some? (property m))
    (assoc t target (map #(mapping %) (property m)))
    t))


(defn sanitize-path [path]
  (-> path
      (string/replace "{" "%7B")
      (string/replace "}" "%7D")))
