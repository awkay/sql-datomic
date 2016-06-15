(ns sql-datomic.types
  "Module for reader support for custom types

  Adds support for reader tagged literals:
  - #uri \"some://really.neato/uri/string\"
  - #bytes base64-str
  "
  (:import [java.nio.charset StandardCharsets]
           [java.util Base64]
           [java.net URI]
           [java.io Writer]))

;; URI support

(defn ->uri [s] (URI. s))

(defmethod print-dup URI [uri ^Writer writer]
  (.write writer (str "#uri \"" (.toString uri) "\"")))

(defmethod print-method URI [uri ^Writer writer]
  (print-dup uri writer))


;; Byte Array support

(defn string->bytes [^String s]
  (.getBytes s StandardCharsets/ISO_8859_1))

(defn bytes->string [#^bytes b]
  (String. b StandardCharsets/ISO_8859_1))

(defn bytes->base64-str [#^bytes b]
  (.encodeToString (Base64/getEncoder) b))

(defn base64-str->bytes [^String b64str]
  (.decode (Base64/getDecoder) b64str))

(defonce bytes-class (Class/forName "[B"))

(defmethod print-dup bytes-class [#^bytes b, ^Writer writer]
  (.write writer (str "#bytes \"" (bytes->base64-str b) "\"")))

(defmethod print-method bytes-class [#^bytes b, ^Writer writer]
  (print-dup b writer))

;; http://docs.datomic.com/schema.html#bytes-limitations
;; <quote>
;; The bytes :db.type/bytes type maps directly to Java byte arrays,
;; which do not have value semantics (semantically equal byte arrays
;; do not compare or hash as equal).
;; </quote>
#_(defn bytes= [& bs]
  (apply = (map seq bs)))
