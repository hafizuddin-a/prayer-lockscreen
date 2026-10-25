# R8 rules for release builds. The libraries ship their own consumer rules (WorkManager keeps its
# worker constructors, kotlinx.serialization keeps its generated serializers), and the manifest
# components are kept automatically. Nothing extra is needed yet; add rules here only when a
# release-build test shows something was stripped.
