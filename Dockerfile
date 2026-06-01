# Build-only image: compiles the Scala Native `bootstrap` binary on Amazon Linux 2023
# so that glibc and libcurl match the Lambda `provided.al2023` runtime exactly
# (a binary built on Debian links against an incompatible libcurl/glibc and crashes
# on Lambda with GLIBC_* not found / curl OPERATION_TIMEDOUT).
#
# The artifact is extracted from this image and deployed as a Lambda zip
# (provided.al2023 custom runtime) instead of being shipped as a container image.
FROM --platform=linux/amd64 amazonlinux:2023 AS build-image

RUN dnf install -y \
      clang \
      gcc \
      glibc-devel \
      libstdc++-devel \
      zlib-devel \
      libcurl-devel \
      openssl-devel \
      libidn2-devel \
      java-17-amazon-corretto-headless \
      tar gzip which findutils \
    && dnf clean all

# scala-cli (static x86_64 linux launcher)
RUN curl -fsSL https://github.com/VirtusLab/scala-cli/releases/latest/download/scala-cli-x86_64-pc-linux.gz \
      | gunzip > /usr/local/bin/scala-cli \
    && chmod +x /usr/local/bin/scala-cli

WORKDIR /work
COPY ./ ./

RUN scala-cli clean .
RUN scala-cli config power true
# target GraalVM
# RUN scala-cli --power package --native-image -o bootstrap .
# target Scala Native
RUN scala-cli --power package --native -o bootstrap .
RUN chmod +x bootstrap
