(ns photon.config
  (:require [clojure.tools.logging :as log]
            [photon.db :as db]))

(defn load-props
  "Receives a path and loads the Java properties for the file
  represented by the path inside the classpath (typically, a resource)."
  [resource-name]
  (let [config-file (clojure.java.io/resource (str resource-name ".properties"))
        io (clojure.java.io/input-stream config-file)
        prop (java.util.Properties.)]
    (log/info "opening resource" config-file)
    (.load prop io)
    (into {} (for [[k v] prop]
               [(keyword k) v]))))

(def default-config
  {:parallel.projections (str (.availableProcessors
                               (Runtime/getRuntime)))
   :db.backend "file"
   :cassandra.ip "127.0.0.1"
   :amqp.url "amqp://localhost"
   :projections.path "/tmp/"
   :file.path "/tmp/photon/"
   :microservice.name "photon"
   :mongodb.host "localhost"
   :riak.default_bucket "rxriak-events-v1"
   :riak.node.1 "riak1.node.com"
   :riak.node.2 "riak2.node.com"
   :riak.node.3 "riak3.node.com"})

(defn throw-error [message]
  (let [main-text
        (str "Usage: java -jar photon-x.x.x-standalone.jar [-h] [-option=value] ... [-option=value]\n"
             "Options:\n"
             "-microservice.name    : "
             "(default = photon)\n"
             "-amqp.url             : "
             "AMQP endpoint (default = amqp://localhost)\n"
             "-parallel.projections : "
             "Number of cores assigned for parallel stream processing (default = number of cores on your machine)\n"
             "-projections.path     : "
             "Local folder with projections, in EDN format, to pre-load on start (default = /tmp/photon)\n"
             "-db.backend           : "
             "DB backend plugin to use (default=dummy). This build of photon is exposing the following backends: "
             (clojure.string/join #", "
                                  (map #(db/driver-name (% {}))
                                       (db/available-backends))) "\n"
             "cassandra.ip          : "
             "If using Cassandra, the host of the cluster\n"
             "file.path             : "
             "If using files as backend, the absolute path to the file\n"
             "mongodb.host          : "
             "If using MongoDB, the host of the cluster\n"
             "riak.default_bucket   : "
             "If using Riak, the name of the bucket\n"
             "riak.node.X           : "
             "If using Riak, the nodes that form the cluster (riak.node.1, riak.node.2, etc.)")]
    (throw (UnsupportedOperationException.
             (str "Error: " message "\n\n" main-text)))))

(defn clean [s]
  (if (or (and (.startsWith s "\"") (.endsWith s "\""))
          (and (.startsWith s "'") (.endsWith s "'")))
    (subs s 1 (dec (count s)))
    s))

(defn merge-command-line [m args]
  (let [set-args (into #{} args)]
    (if (contains? set-args "-h")
      (throw-error "Invoking help...")
      (let [only-args (map #(subs % 1) (disj set-args "-h"))
            tokenized (map #(clojure.string/split % #"=") only-args)
            ks (map (comp keyword first) tokenized)
            original-ks (into #{} (keys default-config))
            not-found (remove #(contains? original-ks %) ks)]
        (if (empty? not-found)
          (let [m-args (zipmap ks (map (comp clean second) tokenized))]
            (merge m m-args))
          (throw-error
            (str "Invalid arguments: "
                 (clojure.string/join #", " (map name not-found)))))))))

(defn config [& args]
  (let [props
        (try
          (load-props "photon")
          (catch Exception e
            (try (load-props "config") (catch Exception e {}))))
        with-default (merge default-config props)
        command-line-props (merge-command-line with-default args)
        final-props (update-in command-line-props
                               [:parallel.projections] (comp read-string str))]
    (log/info "Properties" (with-out-str (clojure.pprint/pprint final-props)))
    final-props))

