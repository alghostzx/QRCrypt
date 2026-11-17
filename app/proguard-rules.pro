# Keep BouncyCastle Argon2 / Blake2b classes used directly
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$Builder { *; }
-keep class org.bouncycastle.crypto.digests.Blake2bDigest { *; }
-dontwarn org.bouncycastle.**

# CameraX & Guava ListenableFuture (optional transitive warnings)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
