(ns lead.builtin-functions
  (:require
    [lead.math :as math]
    [clojure.string :as string]
    [lead.functions :as fns]
    [schema.core :as sm]
    [lead.series :refer [consolidate-series-values
                         normalize-serieses
                         safe-average
                         safe-sum
                         safe-min
                         safe-max
                         name->path
                         path->name
                         RegularSeriesList]]
    #+clj [lead.connector :as connector]
    #+clj [cheshire.core :as cheshire]
    #+clj [clojure.walk])
  #+cljs (:require-macros [lead.functions :refer [leadfn]])
  #+clj (:require [lead.functions :refer [leadfn]]))

(defn
  same-depth?
  [paths]
  (apply = (map count paths)))

(defn filter-path-segments
  [important-segments path]
  (reduce (fn [acc [segment important?]] (if important? (conj acc segment) acc)) [] (map vector path important-segments)))

(defn varying-path-segments
  [paths]
  (apply map
         (fn
           [& segments]
           (not= 1 (count (set segments))))
         paths))

(defn
  simplify-serieses-names
  "Removes common segments from paths if all are the same length."
  [serieses]
  (let [paths (map name->path (map :name serieses))]
    (if (same-depth? paths)
      (let [important-segments (varying-path-segments paths)
            simplified-paths (map (partial filter-path-segments important-segments) paths)]
        (map (fn [series simplified-path] (assoc series :name (path->name simplified-path))) serieses simplified-paths))
      serieses)))

(defn sliced
  "Creates a new series by calling f for each time-slice of serieses"
  [serieses f name]
  (when (seq serieses)
    (let [[normalized-serieses start end step] (normalize-serieses serieses)
           consolidated-values (map consolidate-series-values normalized-serieses)
           values (apply map (fn [& values] (f values)) consolidated-values)]
      [{:start start, :end end, :step step, :values values, :name (str name \( (string/join ", " (map :name serieses)) \))}])))

(defn map-serieses
  "Applies the map function to each value in each series"
  [serieses f name]
  (map #(assoc % :name (str name \( (:name %) \)) :values (map f (:values %))) serieses))

(leadfn
  ^{:uses-opts true
    :aliases ["param"]}
  param-value
  [opts :- fns/Opts & name :- [sm/Any]]                       ; TODO should be (sm/either sm/Str sm/Int)
  (get-in (:params opts) name))

#+clj
(leadfn
  ^{:aliases ["parseJson"]}
  parse-json
  [string :- sm/Str]
  (cheshire/parse-string string))

(leadfn
  serieses
  [o :- sm/Any]
  (if (map? o)
    (clojure.walk/keywordize-keys o)
    (map clojure.walk/keywordize-keys o)))

(leadfn
  ^{:aliases ["avg" "averageSeries"]}
  avg-serieses
  [serieses :- RegularSeriesList]
  (sliced serieses safe-average "averageSeries"))

(leadfn
  ^{:aliases ["min" "minSeries"]}
  min-serieses
  [serieses :- RegularSeriesList]
  (sliced serieses safe-min "minSeries"))

(leadfn
  ^{:aliases ["max" "maxSeries"]}
  max-serieses
  [serieses :- RegularSeriesList]
  (sliced serieses safe-max "maxSeries"))

(leadfn
  ^{:aliases ["sum" "sumSeries"]}
  sum-serieses
  [serieses :- RegularSeriesList]
  (sliced serieses safe-sum "sumSeries"))

(leadfn
  ^{:aliases ["groupByNode"]}
  group-serieses-by-node
  [serieses :- RegularSeriesList node-num :- sm/Int aggregate :- sm/Str]
  (let [groups (group-by #(nth (name->path (:name %)) node-num) serieses)]
    (flatten (map #(fns/call-simple-function aggregate [%]) (vals groups)))))

(leadfn
  ^{:aliases ["flatten" "group"]}
  flatten-serieseses
  [& serieses :- [RegularSeriesList]]
  (flatten serieses))

(leadfn
  ^{:aliases ["offset"]}
  increment-serieses
  [serieses :- RegularSeriesList amount :- sm/Num]
  (map-serieses serieses #(if % (+ amount %)) "offset"))

(leadfn
  ^{:aliases ["scale"]}
  scale-serieses
  [serieses :- RegularSeriesList factor :- sm/Num]
  (map-serieses serieses #(if % (* factor %)) "scale"))

#+clj
(leadfn
  ^{:aliases ["load"]
    :uses-opts true}
  load-from-connector
  [opts :- fns/Opts target :- sm/Str]
  (connector/load @connector/*connector* target opts))

(leadfn
  ^{:aliases ["alias"]}
  rename-serieses
  [serieses :- RegularSeriesList name :- sm/Str]
  (map #(assoc % :name name) serieses))

(defn replace-serieses-values-with-nil [f serieses name]
  (map-serieses serieses #(if (f %) %) name))

(leadfn
  ^{:aliases ["removeBelowValue"]}
  map-values-below-to-nil
  [serieses :- RegularSeriesList value :- sm/Num]
  (replace-serieses-values-with-nil #(>= % value) serieses "removeBelowValue"))

(leadfn
  ^{:aliases ["removeAboveValue"]}
  map-values-above-to-nil
  [serieses :- RegularSeriesList value :- sm/Num]
  (replace-serieses-values-with-nil #(<= % value) serieses "removeBelowValue"))
