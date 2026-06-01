# Build-only image: compiles the Scala Native `bootstrap` binary.
# The artifact is extracted from this image and deployed as a Lambda zip
# (provided.al2 custom runtime) instead of being shipped as a container image.
FROM virtuslab/scala-cli:latest as build-image

RUN apt-get update && apt-get install -y libcurl4-openssl-dev && rm -rf /var/lib/apt/lists/*

WORKDIR /work
COPY ./ ./

RUN scala-cli clean .
RUN scala-cli config power true
# target GraalVM
# RUN scala-cli --power package --native-image -o bootstrap .
# target Scala Native
RUN scala-cli --power package --native -o bootstrap .
RUN chmod +x bootstrap
