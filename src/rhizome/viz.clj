(ns rhizome.viz
  (:use
    [rhizome.dot])
  (:require
    [clojure.string :as str]
    [clojure.java.shell :as sh]
    [clojure.java.io :as io])
  (:import
    [java.awt
     Toolkit]
    [java.awt.event
     KeyEvent]
    [javax.imageio
     ImageIO]
    [javax.swing
     AbstractAction JComponent JFrame JLabel JScrollPane ImageIcon KeyStroke]
    [javax.script
     ScriptEngineManager]))

(def ^:private shortcut-mask
  (.. Toolkit getDefaultToolkit getMenuShortcutKeyMask))

(def ^:private close-key
  (KeyStroke/getKeyStroke KeyEvent/VK_W shortcut-mask))

(defn create-frame
  "Creates a frame for viewing graphviz images.  Only useful if you don't want to use the default frame."
  [name]
  (delay
    (let [frame (JFrame. ^String name)
          image-icon (ImageIcon.)
          pane (-> image-icon JLabel. JScrollPane.)]
      (doto pane
        (.. (getInputMap JComponent/WHEN_IN_FOCUSED_WINDOW)
            (put close-key "closeWindow"))
        (.. getActionMap (put "closeWindow"
                              (proxy [AbstractAction] []
                                (actionPerformed [e]
                                  (.setVisible frame false))))))
      (doto frame
        (.setContentPane pane)
        (.setSize 1024 768)
        (.setDefaultCloseOperation javax.swing.WindowConstants/HIDE_ON_CLOSE))
      [frame image-icon])))

(defonce default-frame (create-frame "rhizome"))

(defn- send-to-front
  "Makes absolutely, completely sure that the frame is moved to the front."
  [^JFrame frame]
  (doto frame
    (.setExtendedState JFrame/NORMAL)
    (.setAlwaysOnTop true)
    .repaint
    .toFront
    .requestFocus
    (.setAlwaysOnTop false))

  ;; may I one day be forgiven
  (when-let [applescript (.getEngineByName (ScriptEngineManager.) "AppleScript")]
    (try
      (.eval applescript "tell me to activate")
      (catch Throwable e
        ))))

(defn view-image
  "Takes an `image`, and displays it in a window.  If `frame` is not specified, then the default frame will be used."
  ([image]
     (view-image default-frame image))
  ([frame image]
     (let [[^JFrame frame ^ImageIcon image-icon] @frame]
       (.setImage image-icon image)
       (.setVisible frame true)
       (java.awt.EventQueue/invokeLater
         #(send-to-front frame)))))

(defn- format-error [s err]
  (apply str
    err "\n"
    (interleave
      (map
        (fn [idx s]
          (format "%3d: %s" idx s))
        (range)
        (str/split-lines s))
      (repeat "\n"))))

(defn dot->image
  "Takes a string containing a GraphViz dot file, and renders it to an image.  This requires that GraphViz
   is installed on the local machine."
  [s]
  (let [{:keys [out err]} (sh/sh "dot" "-Tpng" :in s :out-enc :bytes)]
    (or
      (ImageIO/read (io/input-stream out))
      (throw (IllegalArgumentException. ^String (format-error s err))))))

(defn dot->svg
  "Takes a string containing a GraphViz dot file, and returns a string containing SVG.  This requires that GraphViz
   is installed on the local machine."
  [s]
  (let [{:keys [out err]} (sh/sh "dot" "-Tsvg" :in s)]
    (or
      out
      (throw (IllegalArgumentException. ^String (format-error s err))))))

(defn save-image
  "Saves the given image buffer to the given filename. The default
file type for the image is png, but an optional type may be supplied
as a third argument."
  ([image filename] 
     (save-image image "png" filename))
  ([image filetype filename] 
     (ImageIO/write image filetype (io/file filename))))

(def
  ^{:doc "Takes a graph descriptor in the style of `graph->dot`, and returns a rendered image."
    :arglists (-> #'graph->dot meta :arglists)}
  graph->image
  (comp dot->image graph->dot))

(def
  ^{:doc "Takes a graph descriptor in the style of `graph->dot`, and returns SVG."
    :arglists (-> #'graph->dot meta :arglists)}
  graph->svg
  (comp dot->svg graph->dot))

(def
  ^{:doc "Takes a graph descriptor in the style of `graph->dot`, and displays a rendered image."
    :arglists (-> #'graph->dot meta :arglists)}
  view-graph
  (comp view-image dot->image graph->dot))

(defn save-graph
  "Takes a graph descriptor in the style of `graph->dot`, and saves the image to disk."
  [nodes adjacent & {:keys [filename] :as options}]
  (-> (apply graph->dot nodes adjacent (apply concat options))
    dot->image
    (save-image filename)))

(def
  ^{:doc "Takes a tree descriptor in the style of `tree->dot`, and returns a rendered image."
    :arglists (-> #'tree->dot meta :arglists)}
  tree->image
  (comp dot->image tree->dot))

(def
  ^{:doc "Takes a tree descriptor in the style of `tree->dot`, and displays a rendered image."
    :arglists (-> #'tree->dot meta :arglists)}
  view-tree
  (comp view-image dot->image tree->dot))

(defn save-tree
  "Takes a graph descriptor in the style of `graph->dot`, and saves the image to disk."
  [branch? children root & {:keys [filename] :as options}]
  (-> (apply tree->dot branch? children root (apply concat options))
    dot->image
    (save-image filename)))
