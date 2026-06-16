// for GraalVM
// //> using packaging.graalvmArgs --static
// //> using packaging.graalvmArgs --no-fallback
// for Scala Native
//> using platform native
//> using toolkit default
//> using dep "com.lihaoyi::upickle:4.4.2"
// HTTP 取得の並列化(errors.parTraverse)でスレッドを使うため明示的に有効化する。
// Scala Native 0.5 では既定で有効だが、依存しないよう明示する。
//> using nativeMultithreading true
