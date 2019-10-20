(ns config.system
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [customers.routes :as customers]
            [integrant.core :as ig]
            [org.httpkit.server :as httpkit]
            [reitit.ring :as ring]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.params :refer [wrap-params]]))

(defmethod aero/reader 'ig/ref [_ _ value]
  (ig/ref value))

(defn read-config []
  (aero/read-config (io/resource "system.edn")))

;NAMING PLEASE
(defn dash->underscore [m]
  (map (fn [map-entry]
         (-> map-entry
             key
             name
             (clojure.string/replace #"-" "_")
             keyword))
       m))

(defn replace-keys-with-dashes [m]
  (clojure.set/rename-keys m (zipmap (keys m) (dash->underscore m))))

;NAMING PLEASE
(defn wrap-body-dashes-to-underscore
  [handler]
  (fn [request]
    (let [response (handler request)
          new-body (replace-keys-with-dashes (:body response))]
      (assoc response :body new-body))))

(extend-protocol reitit.core/Expand
  clojure.lang.Var
  (expand [this _]
    {:handler this}))

(def routes [[customers/routes]])

(defmethod ig/init-key :router [_ config]
  (ring/router [routes]))

(defmethod ig/init-key :ring-handler [_ config]
  (-> (:router config)
      (ring/ring-handler (ring/create-default-handler
                           {:not-found (constantly {:status 404 :body "Not found"})}))
      wrap-params
      ;is there a ring json option to this? feels like re-inventing the wheel
      wrap-body-dashes-to-underscore
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      ))

(defmethod ig/init-key :http-kit [_ config]
  (assoc config
    :stop-server
    (httpkit/run-server (:handler config) config)))

(defmethod ig/halt-key! :http-kit [_ {stop-server :stop-server}]
  (stop-server))
