(ns atprotocol.atprotocol-test
  (:require [clojure.test :refer [deftest is testing]]
            [atprotocol.app-record :as app-record]
            [atprotocol.boundary :as boundary]
            [atprotocol.profile :as profile]
            [atprotocol.projection :as projection]))

(def cid "bafybeidl5t4ztktqmfcqrfqpio6qf64n6t65a7inkz2pa6jq4tyqwfjfhy")
(def ipns (str "k51qzi5uqu5d" (apply str (repeat 50 "a"))))

;; ── boundary ─────────────────────────────────────────────────────────────────

(deftest boundary-splits-cleanly
  (testing "wire/互換形は atprotocol が所有"
    (doseq [c [:handle-resolution :lexicon :record-codec :xrpc
               :repo-commit-wire :profile-view]]
      (is (= :atprotocol (boundary/owner c)) (str c))))
  (testing "真実は kotoba-protocol へ委譲"
    (doseq [c [:identity-keys :fact-truth :graph-cid :mutable-head
               :distribution :app-manifest]]
      (is (boundary/delegated? c) (str c))))
  (is (nil? (boundary/owner :unheard-of))))

;; ── projection codec 契約 ────────────────────────────────────────────────────

(deftest codec-contract
  (let [c (projection/codec {:record->datoms (fn [_] [])
                             :datoms->view (fn [_] {})
                             :collections #{"app.bsky.feed.post"}})]
    (is (projection/projects? c "app.bsky.feed.post"))
    (is (not (projection/projects? c "app.bsky.graph.follow"))
        "未知 collection は generic 保全 (:atproto.record/*) に落ちる契約"))
  (is (= {:error :invalid-codec :missing [:datoms->view :collections]}
         (projection/codec {:record->datoms (fn [_] [])}))))

;; ── profile 投影 + W-Protocol alias 降格 ─────────────────────────────────────

(def manifest
  {:kotoba.app/id "net.kotoba.mangaka"
   :kotoba.app/version "0.1.0"
   :kotoba.app/kind "embed"
   :kotoba.app/bundle-cid cid
   :kotoba.app/embed-url "https://aozora.app/studio"
   :kotoba.app/caps ["graph/query" "llm/complete"]
   :kotoba.app/latest ipns})

(deftest profile-projection
  (let [v (profile/project-app manifest)]
    (is (= "net.kotoba.mangaka" (:appId v)))
    (is (= "embed" (:appKind v)))
    (is (= "https://aozora.app/studio" (:embedUrl v)))
    (is (= cid (:bundleCid v)))
    (is (= ipns (:appLatest v)))
    (is (= ["graph/query" "llm/complete"] (:appCaps v))
        "host は appCaps ∩ 対応 caps を bridge grant にする")
    (testing "legacy fields は既定で出ない (新規実装は読まない・書かない)"
      (is (not (contains? v :uiType)))
      (is (not (contains? v :contentMode)))))
  (testing "ipfs embed-url は gateway 解決される"
    (let [v (profile/project-app (assoc manifest :kotoba.app/embed-url
                                        (str "ipfs://" cid "/index.html"))
                                 {:gateway "https://kotobase.net"})]
      (is (= (str "https://kotobase.net/ipfs/" cid "/index.html") (:embedUrl v)))))
  (testing "invalid manifest は投影しない"
    (is (= :invalid-manifest
           (:error (profile/project-app (dissoc manifest :kotoba.app/version)))))))

(deftest legacy-aliases-are-deprecated-and-derivable
  (doseq [[field spec] profile/legacy-aliases]
    (is (:deprecated spec) (str field " must be marked deprecated")))
  (let [v (profile/project-app manifest {:include-legacy? true})]
    (is (= "interactive" (:contentMode v)))
    (is (= "iframe" (:uiType v)))
    (is (= "service" (:performerType v))))
  (let [v (profile/project-app (assoc manifest
                                      :kotoba.app/kind "appview"
                                      :kotoba.app/appview-of {:graphs ["manga"]})
                               {:include-legacy? true})]
    (is (= "appview" (:uiType v)))))

;; ── app manifest record 投影 ─────────────────────────────────────────────────

(deftest app-record-round-trip
  (let [record-value {:$type "net.kotoba.app.manifest"
                      :id "net.kotoba.mangaka"
                      :version "0.1.0"
                      :kind "embed"
                      :embedUrl "https://aozora.app/studio"
                      :bundleCid cid
                      :future-field "preserved"}
        m (app-record/record->manifest record-value)]
    (is (= "net.kotoba.mangaka" (:kotoba.app/id m)))
    (is (= "https://aozora.app/studio" (:kotoba.app/embed-url m)))
    (testing "未知フィールドは保全される (黙って落とさない)"
      (is (= "preserved" (get-in m [:atprotocol/unprojected :future-field]))))
    (is (= [] (app-record/valid-record? record-value)))
    (testing "manifest → record は一級語彙のみ + $type"
      (let [r (app-record/manifest->record (dissoc m :atprotocol/unprojected))]
        (is (= "net.kotoba.app.manifest" (:$type r)))
        (is (= "embed" (:kind r)))
        (is (= record-value (assoc r :future-field "preserved")))))))

(deftest app-record-validation-flows-through
  (is (some #(= :missing (:error %))
            (app-record/valid-record? {:id "net.kotoba.x" :kind "embed"
                                       :embedUrl "https://x.example/app"}))
      "version 欠落は kotoba-protocol の validate-manifest が検出"))
