(ns photon.config
  (:require [clojure.tools.logging :as log]
            [buddy.hashers :as hashers]))

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
   :rest.port 3000
   :db.backend "file"
   :cassandra.ip "127.0.0.1"
   :amqp.url "amqp://localhost"
   :projections.path "/tmp/"
   :file.path "/tmp/photon/"
   :microservice.name "photon"
   :mongodb.host "localhost"
   :projections.port 8375
   :events.port 8376
   :admin.user "admin"
   :admin.pass "p4010n"
   :riak.default_bucket "rxriak-events-v1"
   :riak.node.1 "riak1.node.com"
   :riak.node.2 "riak2.node.com"
   :riak.node.3 "riak3.node.com"})

(defn throw-error [message]
  (let [main-text
        (str "Usage: java -jar photon-x.x.x-standalone.jar [-h] [-option value] ... [-option value]\n"
             "Options:\n"
             "-microservice.name    : "
             "Service ID, especially important for Muon (default = photon)\n"
             "-rest.port            : "
             "The port for the UI frontend and the REST API\n"
             "-admin.user           : "
             "The default username for logging in and requesting API tokens (default = admin)\n"
             "-admin.pass           : "
             "The default password for logging in and requesting API tokens (default = p4010n)\n"
             "-projections.port     : "
             "Port to stream projection updates to (default = 8375)\n"
             "-events.port          : "
             "Port to stream incoming events to (default = 8376)\n"
             "-amqp.url             : "
             "AMQP endpoint (default = amqp://localhost)\n"
             "-parallel.projections : "
             "Number of cores assigned for parallel stream processing (default = number of cores on your machine)\n"
             "-projections.path     : "
             "Local folder with projections, in EDN format, to pre-load on start (default = /tmp/photon)\n"
             "-db.backend           : "
             "DB backend plugin to use (default=dummy). Depending on the build of photon, this can be one of: cassandra, file, mongo, riak, dummy.\n"
             "-cassandra.ip          : "
             "If using Cassandra, the host of the cluster\n"
             "-file.path             : "
             "If using files as backend, the absolute path to the file\n"
             "-mongodb.host          : "
             "If using MongoDB, the host of the cluster\n"
             "-riak.default_bucket   : "
             "If using Riak, the name of the bucket\n"
             "-riak.node.X           : "
             "If using Riak, the nodes that form the cluster (riak.node.1, riak.node.2, etc.)")]
    (throw (UnsupportedOperationException.
             (str message "\n\n" main-text)))))

(defn clean [s]
  (if (or (and (.startsWith s "\"") (.endsWith s "\""))
          (and (.startsWith s "'") (.endsWith s "'")))
    (subs s 1 (dec (count s)))
    s))

(defn merge-command-line [m args]
  (let [set-args (into #{} args)]
    (if (or (contains? set-args "-h") (contains? set-args "--help"))
      (throw-error "Invoking help...")
      (let [only-args (remove #(or (= % "-h") (= % "--help")) args)
            tokenized (map #(clojure.string/split % #"=")
                           (map #(subs % 2)
                                (filter #(.startsWith % "--") only-args)))
            posix (try
                    (let [lst (remove #(.startsWith % "--") args)
                          m-lst (apply hash-map lst)]
                      (map vector (map #(subs % 1) (keys m-lst)) (vals m-lst)))
                    (catch Exception e
                      (throw-error
                        (str "Parse error, please check that "
                             "arguments are pairs of key/value"))))
            command-line (merge (into {} tokenized) (into {} posix))
            ks (map (comp keyword first) command-line)
            original-ks (into #{} (keys default-config))
            not-found (remove #(contains? original-ks %) ks)]
        (if (empty? not-found)
          (let [m-args (zipmap ks (map (comp clean second) command-line))]
            (merge m m-args))
          (throw-error
            (str "Invalid arguments: "
                 (clojure.string/join #", " (map name not-found)))))))))

(defn integer-params [m ps]
  (if (empty? ps)
    m
    (recur (update-in m [(first ps)] (comp read-string str))
           (rest ps))))

(defn config [& args]
  (let [props
        (try
          (load-props "photon")
          (catch Exception e
            (try (load-props "config") (catch Exception e {}))))
        with-default (merge default-config props)
        command-line-props (merge-command-line with-default args)
        final-props (integer-params command-line-props
                                    [:parallel.projections :rest.port])
        final-props (update-in final-props [:admin.pass] hashers/encrypt)]
    (log/info "Properties" (with-out-str (clojure.pprint/pprint final-props)))
    final-props))

