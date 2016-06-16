(ns sql-datomic.repl
  (:require [sql-datomic.parser :as parser]
            [sql-datomic.datomic :as dat]
            [clojure.pprint :as pp]
            [clojure.tools.cli :as cli]
            [datomic.api :as d]
            clojure.repl
            [clojure.string :as str]))

(def ^:dynamic *prompt* "sql> ")

(declare sys)

(defn squawk
  ([title] (squawk title nil))
  ([title data]
   (let [s (str title ":")
         sep (str/join (repeat (count s) \=))]
     (binding [*out* *err*]
       (println (str "\n" s "\n" sep))
       (when data
         (pp/pprint data))
       (flush)))))

(defn ruler [s]
  (let [nums (map (fn [n] (-> n inc (mod 10))) (range))]
    (->> nums
         (take (count s))
         (map str)
         str/join)))

(defn print-ruler [input]
  (when (seq input)
    (binding [*out* *err*]
      (println "\nInput with column offsets:\n==========================")
      (println input)
      (println (ruler input))
      (flush))))

(defn repl [{:keys [debug pretend] :as opts}]
  (let [dbg (atom debug)
        loljk (atom pretend)
        noop (atom false)]
    (print *prompt*)
    (flush)
    (let [input (read-line)]
      (when-not input
        (System/exit 0))
      (when (re-seq #"^(?ims)\s*(?:quit|exit)\s*$" input)
        (System/exit 0))

      ;; FIXME: Behold, the great leaning tower of REPL code.
      (try
        (when (re-seq #"^(?i)\s*debug\s*$" input)
          (let [new-debug (not @dbg)]
            (println "Set debug to" (if new-debug "ON" "OFF"))
            (flush)
            (reset! dbg new-debug)
            (reset! noop true)))
        (when (re-seq #"^(?i)\s*pretend\s*$" input)
          (let [new-pretend (not @loljk)]
            (println "Set pretend to" (if new-pretend "ON" "OFF"))
            (flush)
            (reset! loljk new-pretend)
            (reset! noop true)))
        (when (and (not @dbg) @loljk)
          (println "Set debug to ON due to pretend ON")
          (flush)
          (reset! dbg true)
          (reset! noop true))

        (when (and (not @noop) (re-seq #"(?ms)\S" input))
          (let [maybe-ast (parser/parser input)]
            (if-not (parser/good-ast? maybe-ast)
              (do
                (squawk "Parse error" maybe-ast)
                (when-let [hint (parser/hint-for-parse-error maybe-ast)]
                  (binding [*out* *err*]
                    (println (str "\n*** Hint: " hint))))
                (print-ruler input))
              (do
                (when @dbg (squawk "AST" maybe-ast))
                (let [ir (parser/transform maybe-ast)]
                  (when @dbg (squawk "Intermediate Repr" ir))
                  (case (:type ir)
                    :select
                    (when-let [wheres (:where ir)]
                      (let [db (->> sys :datomic :connection d/db)
                            query (dat/where->datomic-q db wheres)]
                        (when @dbg
                          (squawk "Datomic Rules" dat/rules)
                          (squawk "Datomic Query" query))
                        (let [results (d/q query db dat/rules)]
                          (when @dbg (squawk "Raw Results" results))
                          (let [ids (mapcat identity results)]
                            (when @dbg
                              (squawk "Entities")
                              (when-not (seq results)
                                (binding [*out* *err*] (println "None"))))
                            (doseq [id ids]
                              (let [entity (d/touch (d/entity db id))]
                                (pp/pprint entity)
                                (flush)))))))

                    (:insert :update :delete)
                    (println "TBD")

                    ;; else
                    (throw (ex-info "Unknown query type" {:type (:type ir)
                                                          :ir ir}))))))))
        (catch Exception ex
          (binding [*out* *err*]
            (println "\n!!! Error !!!")
            (if @dbg
              (do
                (clojure.repl/pst ex)
                (print-ruler input))
              (println (.toString ex)))
            (flush))))

      (recur (assoc opts :debug @dbg :pretend @loljk)))))

(defn -main [& args]
  (let [[opts args banner]
        (cli/cli args
                 ["-h" "--help" "Print this help"
                  :flag true
                  :default false]
                 ["-d" "--debug" "Write debug info to stderr"
                  :flag true
                  :default false]
                 ["-p" "--pretend" "Run without transacting; turns on debug"
                  :flag true
                  :default false]
                 ["-u" "--connection-uri"
                  "URI to Datomic DB; if missing, uses default mem db"])]
    (when (:help opts)
      (println banner)
      (System/exit 0))

    (def sys (.start (dat/system opts)))

    (let [uri (->> sys :datomic :connection-uri)]
      (println "connected to:" uri)
      (when (dat/default-uri? uri)
        (println "*** using default in-mem database ***"))
      (println "type `exit` or `quit` or ^D to exit")
      (println "type `debug` to toggle debug mode")
      (println "type `pretend` to toggle pretend mode"))
    (repl opts)))
