(ns mount.lite.graph
  "Namespace reponsible for deducing a dependency graph for
  a set of state vars."
  {:no-doc true}
  (:require [clojure.java.classpath :as cp]
            [clojure.set :as set]
            [clojure.tools.namespace.dependency :as dep]
            [clojure.tools.namespace.find :as find]
            [clojure.tools.namespace.parse :as parse]
            [mount.lite.dependency :as mydep]
            [mount.lite.util :as util]))


(def ^:private ns-deps
  (let [parse-xs (map (juxt (comp find-ns parse/name-from-ns-decl)
                            (comp set #(keep find-ns %) parse/deps-from-ns-decl)))]
    (fn []
      (-> (into {} parse-xs (find/find-ns-decls (cp/classpath)))
          (dissoc nil)))))

(defn- add-transitives [graph namespaces ns-vars var]
  (reduce (fn [g ns]
            (reduce (fn [g dep-ns-var]
                      (dep/depend g var dep-ns-var))
                    g
                    (get ns-vars ns)))
          graph namespaces))

(defn- add-same-ns [graph var vars]
  (reduce (fn [g ns-var]
            (cond-> g
              (and (not= var ns-var)
                   (< (-> ns-var meta :mount.lite/order)
                      (-> var meta :mount.lite/order)))
              (dep/depend var ns-var)))
          graph vars))

(defn ns-graph
  "Creates a dependency graph of all the loaded namespaces."
  []
  (let [ns-deps (ns-deps)]
    (reduce (fn [g ns]
              (reduce (fn [g dep]
                        (dep/depend g ns dep))
                      g (ns-deps ns)))
            (dep/graph) (keys ns-deps))))

(defn- var-graph* [vars]
  (let [ns-graph (ns-graph)
        ns-vars  (group-by #(.ns %) vars)
        graph    (reduce mydep/add-node (dep/graph) vars)]
    (reduce (fn [g var]
              (if-let [deps (-> var meta :dependencies)]
                (reduce (fn [g dep] (dep/depend g var dep)) g deps)
                (let [ns (.ns var)]
                  (when-not (-> ns-graph :dependencies (get ns))
                    (throw (ex-info (str "Cannot find namespace declaration source for " ns
                                         ", ensure the source file is on the classpath.")
                                    {:ns ns})))
                  (-> (add-transitives g (dep/transitive-dependencies ns-graph ns) ns-vars var)
                      (add-same-ns var (get ns-vars ns))))))
            graph vars)))

(def ^{:doc "Create a dependency graph of the given state vars. Memoizes the last input."}
  var-graph (util/memoize-1 var-graph*))
