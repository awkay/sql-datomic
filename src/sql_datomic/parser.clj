(ns sql-datomic.parser
  (:require [instaparse.core :as insta]
            [clojure.zip :as zip]
            [clojure.string :as str]))

(def parser
  (-> "resources/sql-eensy.bnf"
      #_"resources/sql.bnf"
      slurp
      (insta/parser
       :input-format :ebnf
       :no-slurp true
       :string-ci true
       :auto-whitespace :standard)))

(defn column-name-ast? [ast]
  (and (vector? ast) (= (first ast) :column_name)))

(defn column-name-ast->ir [ast]
  {:pre [(vector? ast)
         (= 3 (count ast))
         (= (first ast) :column_name)
         (string? (nth ast 1))
         (string? (nth ast 2))]}
  (let [[_ table-name column-name] ast]
    {:table table-name, :column column-name}))

(defn where-clause-ast->ir [ast]
  {:pre [(vector? ast)
         (= (first ast) :where_clause)]}
  (rest ast))

(defn table-ref-ast->ir [ast]
  {:pre [(vector? ast)
         (= (first ast) :table_ref)
         (vector? (second ast))
         (= (get-in ast [1 0]) :table_name)
         (string? (get-in ast [1 1]))]}
  (when (>= (count ast) 3)
    (assert (vector? (get-in ast [2])) "invalid table_alias")
    (assert (= (get-in ast [2 0]) :table_alias) "invalid table_alias")
    (assert (string? (get-in ast [2 1])) "invalid table_alias"))
  (let [[_ [_ table-name] maybe-table-alias] ast
        result {:name table-name}]
    (if maybe-table-alias
      (assoc result :alias (second maybe-table-alias))
      result)))

(defn from-clause-ast->ir [ast]
  {:pre [(vector? ast)
         (= (first ast) :from_clause)]}
  (->> (rest ast)
       (map table-ref-ast->ir)))

(defn select-list-ast->ir [ast]
  {:pre [(vector? ast)
         (= (first ast) :select_list)]}
  (->> (rest ast)
       (map (fn [v]
              (if (column-name-ast? v)
                (column-name-ast->ir v)
                v)))))

(defn select-statement-ast->ir [ast]
  {:pre [(vector? ast)
         (>= (count ast) 3)
         (= (first ast) :select_statement)]}
  (let [[_ select-list from-clause where-clause] ast
        result {:tables (from-clause-ast->ir from-clause)
                :fields (select-list-ast->ir select-list)}]
    (if where-clause
      (assoc result :where (where-clause-ast->ir where-clause))
      result)))

;; (def transform-operator
;;   {"+" +
;;    "-" -
;;    "*" *
;;    "/" /})

;; (defn transform-operation [f v]
;;   (if (coll? v)
;;     (apply f v)
;;     (f v)))

;; (def transform-options
;;   {:expr identity
;;    :vector vector
;;    :number read-string
;;    :operator transform-operator
;;    :operation transform-operation})

;; (defn parse [input]
;;   (->> (parser input)
;;        (insta/transform transform-options)))
