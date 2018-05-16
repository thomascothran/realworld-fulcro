(ns conduit.intro
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.routing :as r]
            [conduit.handler.mutations :as mutations]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.network :as net]
            [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.client.cards :refer [defcard-fulcro]]
            [conduit.ui.components :as comp]
            [fulcro.client.dom :as dom]))

;; (dom/div {:onClick #(prim/transact! this `[(comp/use-article-as-form #:article{:id 2})])} "Edit")

(r/defrouter TopRouter :router/top
  [:screen :screen-id]
  :screen/home     comp/Home
  :screen/settings comp/SettingScreen
  :screen/editor   comp/EditorScreen
  :screen/sign-up  comp/Home
  :screen/profile  comp/ProfileScreen)

(def ui-top (prim/factory TopRouter))

(def routing-tree
  (r/routing-tree
    (r/make-route :screen/home
      [(r/router-instruction :router/top [:screen/home :top])])

    (r/make-route :screen/editor
      [(r/router-instruction :router/top [:screen/editor :param/screen-id])])

    (r/make-route :screen/settings
      [(r/router-instruction :router/top [:screen/settings :top])])
    (r/make-route :screen/sign-up
      [(r/router-instruction :router/top [:screen/sign-up :top])])

    (r/make-route :screen.feed/global
      [(r/router-instruction :router/top [:screen/home :top])
       (r/router-instruction :router/feeds [:screen.feed/global :top])])
    (r/make-route :screen.feed/personal
      [(r/router-instruction :router/top [:screen/home :top])
       (r/router-instruction :router/feeds [:screen.feed/personal :top])])

    (r/make-route :screen.profile/owned-articles
      [(r/router-instruction :router/top [:screen/profile :param/screen-id])
       (r/router-instruction :router/profile [:screen.profile/owned-articles :param/screen-id])])
    (r/make-route :screen.profile/liked-articles
      [(r/router-instruction :router/top [:screen/profile :param/screen-id])
       (r/router-instruction :router/profile [:screen.profile/liked-articles :param/screen-id])])))

(defn go-to-home [this]
  (prim/transact! this `[(r/route-to {:handler :screen/home})]))

(defsc Root [this {router :router/top :as props}]
  {:initial-state (fn [params] (merge routing-tree
                                 {:root/settings-form {:settings [:user/whoami '_]}
                                  :user/whoami        [:user/by-id :guest]
                                  :user/by-id         {:guest {:user/id       :guest
                                                               :user/name     "Guest"
                                                               :user/email    "non@exist.com"
                                                               :user/like     []
                                                               :user/articles []}}}
                                 {:router/top (prim/get-initial-state TopRouter {})}))
   :query         [{:router/top (prim/get-query TopRouter {})}
                   {:user/whoami (prim/get-query comp/NavBar)}]}
  (let [current-user (get props :user/whoami)]
    (dom/div {}
      (comp/ui-nav-bar current-user)
      (ui-top router)
      (comp/ui-footer))))

(def token-store (atom "No token"))

(defn wrap-remember-token [res]
  (when-let [new-token (or (-> (:body res) (get :user/whoami) :token))]
    ;;(println (str "found token: " new-token))
    (reset! token-store (str "Token " new-token)))
  res)

(defn wrap-with-token [req]
  (assoc-in req [:headers "Authorization"] @token-store))

(defcard-fulcro yolo
  Root
  {} ; initial state. Leave empty to use :initial-state from root component
  {:inspect-data true
   :fulcro       {:started-callback
                  (fn [app]
                    (df/load app :articles/all comp/ArticlePreview)
                    (df/load app :tags/all comp/Tag))
                  :networking {:remote (net/fulcro-http-remote
                                         {:url "/api"
                                          :response-middleware (net/wrap-fulcro-response wrap-remember-token)
                                          :request-middleware  (net/wrap-fulcro-request wrap-with-token)})}}})
(dc/start-devcard-ui!)
