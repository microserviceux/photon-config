(ns photon.config
  (:require [clojure.tools.logging :as log]
            [buddy.hashers :as hashers])
  (:import (io.muoncore.config MuonConfigBuilder AutoConfiguration
                               AutoConfigurationWriter)))

(defn load-props
  "Receives a path and loads the Java properties for the file
  represented by the path inside the classpath (typically, a resource)."
  [resource-name]
  (let [config-file (clojure.java.io/resource (str resource-name ".properties"))
        config-file (if (nil? config-file)
                      (java.io.File. (str "./" resource-name ".properties"))
                      config-file)
        io (clojure.java.io/input-stream config-file)
        prop (java.util.Properties.)]
    (log/info "opening resource" config-file)
    (.load prop io)
    (into {} (for [[k v] prop]
               [(keyword k) v]))))

(def default-config
  {:parallel.projections (str (.availableProcessors
                               (Runtime/getRuntime)))
   :rest.host "localhost"
   :rest.port 3000
   :ui.port 3001
   :rest.keystore nil
   :rest.keypass ""
   :db.backend "h2"
   :cassandra.ip "127.0.0.1"
   :cassandra.buffer 100
   :muon.url :local
   :h2.path "/tmp/photon.h2"
   :file.path "/tmp/photon/"
   :microservice.name "photon"
   :mongodb.uri "mongodb://localhost/photon"
   :projections.port 8375
   :measure.active true
   :measure.rate 30000
   :events.port 8376
   :admin.user "admin"
   :admin.pass "p4010n"
   :admin.secret (java.util.UUID/randomUUID)
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
             "-rest.host            : "
             "The IP or hostname of the web server for frontend and API. Change it for external access (default = localhost)\n"
             "-rest.port            : "
             "The port for the UI frontend and the REST API (default = 3000)\n"
             "-ui.port              : "
             "If set, a second web server will be started in this port with a frontend to access photon in UI mode (default = 3001)\n"
             "-rest.keystore        : "
             "If set, the web server will be started in SSL mode using the certificates identified by this path\n"
             "-rest.keypass         : "
             "The password required to open the keystore set in rest.keystore. Not required in not-SSL mode\n"
             "-admin.user           : "
             "The default username for logging in and requesting API tokens (default = admin)\n"
             "-admin.pass           : "
             "The default password for logging in and requesting API tokens (default = p4010n)\n"
             "-admin.secret         : "
             "A secret string that will be used to encode authentication tokens (default is random on launch)\n"
             "-projections.port     : "
             "Port to stream projection updates to (default = 8375)\n"
             "-events.port          : "
             "Port to stream incoming events to (default = 8376)\n"
             "-muon.url             : "
             "AMQP endpoint for Muon-based transport and discovery (default = amqp://localhost)\n"
             "-parallel.projections : "
             "Number of cores assigned for parallel stream processing (default = number of cores on your machine)\n"
             "-measure.active       : "
             "Whether or not to collect memory usage of projections, deactivate to improve performance (default = true)\n"
             "-measure.rate         : "
             "How often, in milliseconds, should memory usage be collected if memory.active is true (default = 30000)\n"
             "-db.backend           : "
             "DB backend plugin to use (default=h2). Depending on the build of photon, this can be one of: h2, cassandra, redis, file, mongo, riak, dummy.\n"
             "-h2.path              : "
             "If using H2, the file prefix for the database file, including path (default = /tmp/photon.h2)\n"
             "-cassandra.ip         : "
             "If using Cassandra, the host of the cluster\n"
             "-cassandra.buffer     : "
             "If using Cassandra, the number of events to keep in buffer in each query (default = 500)\n"
             "-file.path            : "
             "If using files as backend, the absolute path to the file\n"
             "-mongodb.uri          : "
             "If using MongoDB, the connection URI (default = mongodb://localhost/photon)\n"
             "-riak.default_bucket  : "
             "If using Riak, the name of the bucket\n"
             "-riak.node.X          : "
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
    (recur (if (or (nil? (get m (first ps))) (number? (get m (first ps))))
             m
             (update-in m [(first ps)] (comp read-string str)))
           (rest ps))))

(defn raw-config [& args]
  (let [props
        (try
          (load-props "photon")
          (catch Exception e
            (try (load-props "config") (catch Exception e {}))))
        with-default (merge default-config props)
        command-line-props (merge-command-line with-default args)
        final-props (integer-params command-line-props
                                    [:parallel.projections :rest.port
                                     :measure.rate :measure.active
                                     :cassandra.buffer :ui.port])
        final-props (update-in final-props [:admin.pass] hashers/encrypt)]
    (if (= (:rest.port final-props) (:ui.port final-props))
      (throw-error "rest.port and ui.port cannot have the same value")
      (do
        (log/info "Properties" (with-out-str (clojure.pprint/pprint final-props)))
        final-props))))

(defn photon-writer [rc]
  (reify AutoConfigurationWriter
    (^void writeConfiguration [this ^AutoConfiguration config]
     (let [props (.getProperties config)]
       (dorun (map #(when-not (or (nil? (val %))
                                  (.containsKey props (name (key %))))
                      (.put props (name (key %)) (val %)))
                   rc))
       (let [amqp-url (get props "muon.url")]
         (if (or (nil? amqp-url) (= :local amqp-url)
                 (= "local" amqp-url) (= ":local" amqp-url))
           (doto props
             (.put "muon.discovery.factories"
                   "io.muoncore.discovery.InMemDiscoveryFactory")
             (.put "muon.transport.factories"
                   "io.muoncore.transport.InMemTransportFactory"))
           (doto props
             (.put "muon.discovery.factories"
                   "io.muoncore.discovery.amqp.AmqpDiscoveryFactory")
             (.put "muon.transport.factories"
                   "io.muoncore.transport.amqp.AmqpMuonTransportFactory")
             (.put "amqp.transport.url" amqp-url)
             (.put "amqp.discovery.url" amqp-url))))))))

(defn config [& args]
  (let [conf (apply raw-config args)
        mcb (MuonConfigBuilder/withServiceIdentifier
             (:microservice.name conf))]
    (.addWriter mcb (photon-writer conf))
    (let [consolidated (into {} (.getProperties (.build mcb)))
          with-keys (zipmap (map keyword (keys consolidated))
                            (vals consolidated))]
      (merge with-keys {:muon-builder mcb}))))
