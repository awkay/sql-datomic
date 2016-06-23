(ns sql-datomic.tabula
  (:require [clojure.pprint :as pp]
            [clojure.string :as str])
  (:import [datomic.query EntityMap]))

(defn entity-map? [e]
  (isa? (class e) EntityMap))

(defn abbreviate-entity [entity]
  (select-keys entity [:db/id]))

(defn abbreviate-entity-maps [entity]
  (->> entity
       (map (fn [[k v]]
              (let [v' (if (entity-map? v)
                         (abbreviate-entity v)
                         v)]
                [k v'])))
       (into {})))

(def cardinality-many? set?)

(defn select-cardinality-many-attrs [entity]
  (->> entity
       (keep (fn [[k v]]
               (when (cardinality-many? v)
                 k)))
       sort
       vec))

(defn abbreviate-cardinality-many-attrs [entity]
  (->> entity
       (map (fn [[k v]]
              (let [v' (if (cardinality-many? v)
                         (->> v (map abbreviate-entity) (into #{}))
                         v)]
                [k v'])))
       (into {})))

(defn elide-cardinality-manys [entity]
  (->> entity
       (remove (fn [[_ v]] (cardinality-many? v)))
       (into {})))

(defn string->single-quoted-string [s]
  (str \' (str/escape s {\' "\\'"}) \'))

(defn entity->printable-row [entity]
  (->> entity
       (map (fn [[k v]]
              (let [v' (if (string? v)
                         (string->single-quoted-string v)
                         (pr-str v))]
                [k v'])))
       (into {})))

(def process-entity (comp entity->printable-row
                          abbreviate-entity-maps
                          elide-cardinality-manys))

(defn -print-elided-cardinality-many-attrs
  ([rows]
   (when (seq rows)
     (let [attrs (select-cardinality-many-attrs (first rows))]
       (when (seq attrs)
         (println "Elided cardinality-many attrs: " attrs)))))
  ([ks rows]
   (->> rows
        (map (fn [row] (select-keys row ks)))
        -print-elided-cardinality-many-attrs)))

(defn -print-row-count [rows]
  (printf "(%d rows)\n" (count rows)))

(defn -print-simple-table [{:keys [ks rows print-fn]}]
  (->> rows
       (map process-entity)
       (into [])
       print-fn)
  (-print-row-count rows)
  (if (seq ks)
    (-print-elided-cardinality-many-attrs ks rows)
    (-print-elided-cardinality-many-attrs rows))
  (println))

(defn print-simple-table
  ([ks rows]
   (-print-simple-table {:ks ks
                         :rows rows
                         :print-fn (partial pp/print-table ks)}))
  ([rows]
   (-print-simple-table {:rows rows
                         :print-fn pp/print-table})))

(defn -print-expanded-table [{:keys [ks rows]}]
  (when (seq rows)
    (let [ks' (if (seq ks)
                ks
                (->> rows (mapcat keys) (into #{}) sort))
          k-max-len (->> ks'
                         (map (comp count str))
                         (sort >)
                         first)
          row-fmt (str "%-" k-max-len "s | %s\n")
          rows' (->> rows
                     (map (fn [row] (select-keys row ks')))
                     (map (comp entity->printable-row
                                abbreviate-entity-maps
                                abbreviate-cardinality-many-attrs))
                     (map-indexed vector))]
      (doseq [[i row] rows']
        (printf "-[ RECORD %d ]-%s\n"
                (inc i)
                (apply str (repeat 40 \-)))
        (let [xs (->> ks'
                      (map (fn [k]
                             [k (get row k)]))
                      (filter second))]
          (doseq [[k v] xs]
            (printf row-fmt k v))))))
  (-print-row-count rows)
  (println))

(defn print-expanded-table
  ([ks rows]
   (-print-expanded-table {:ks ks :rows rows}))
  ([rows]
   (-print-expanded-table {:rows rows})))
