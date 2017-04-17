(defproject tranchis/photon-config "0.9.7"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :repositories [["snapshots"
                  {:url
                   "https://simplicityitself.artifactoryonline.com/simplicityitself/muon/"
                   :creds :gpg}]
                 ["releases" "https://simplicityitself.artifactoryonline.com/simplicityitself/repo/"]]
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [buddy/buddy-hashers "1.2.0"]
                 [io.muoncore/muon-core "7.2.9"]
                 [io.muoncore/muon-transport-amqp "7.2.9"]
                 [io.muoncore/muon-discovery-amqp "7.2.9"]
                 [midje "1.8.3"]]
  :plugins [[lein-midje "3.2"]])
