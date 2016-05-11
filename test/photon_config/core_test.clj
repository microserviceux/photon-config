(ns photon-config.core-test
  (:require [clojure.test :refer :all]
            [photon.config :refer :all]
            [midje.sweet :refer :all])
  (:import (java.lang ProcessEnvironment$Variable
                      ProcessEnvironment$Value)))

(fact "Default config is default"
      (dissoc (raw-config) :admin.pass :parallel.projections)
      => (dissoc default-config :admin.pass :parallel.projections))
