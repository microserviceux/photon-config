(ns photon.config
  (:require [clojure.tools.logging :as log]))

(defn load-props
  "Receives a path and loads the Java properties for the file
  represented by the path inside the classpath (typically, a resource)."
  [resource-name]
  (let [f (java.io.File. "./config.properties")
        config-file (if (.exists f)
                      (clojure.java.io/file f)
                      (clojure.java.io/resource (str resource-name ".properties")))
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

(defonce config
  (let [final-props
        (try
          (let [props (load-props "config")]
            (let [new-props (merge default-config props)]
              (assoc new-props :parallel.projections
                     (read-string (:parallel.projections new-props)))))
          (catch Exception e
            (log/error
              "Falling back to default config. Configuration was not loaded due to "
              (.getMessage e))
            default-config))]
    (log/info "Properties" (pr-str final-props))
    final-props))
