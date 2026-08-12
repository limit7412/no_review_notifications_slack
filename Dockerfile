# Scala Native の `bootstrap` を scala-cli 公式イメージ (Debian/glibc) 上で
# 完全静的リンクするビルド専用イメージ (#14)。コンテナイメージ自体はデプロイせず、
# 生成したバイナリを抽出して Lambda の zip (provided.al2023 カスタムランタイム) と
# してデプロイする。公式イメージは amd64 単一アーキテクチャのため x86_64 を前提とし、
# タグは再現性のためバージョン固定する。
FROM --platform=linux/amd64 virtuslab/scala-cli:1.16.0 AS build-image

# ビルドに追加で必要なパッケージ。
#   curl / ca-certificates: c-ares と libcurl のソース取得
#   file: 静的リンクの検証 (最下部で使用)
#   libssl-dev / zlib1g-dev: 静的リンクする libcurl の TLS と gzip 展開
#   libzstd-dev: Debian の libcrypto.a が zstd 圧縮 BIO を含み、ZSTD_* を要求するため
#   libidn2 / libunistring: sttp-model が @link("idn2") で要求するため
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
         curl ca-certificates file \
         libssl-dev zlib1g-dev libzstd-dev libidn2-dev libunistring-dev \
    && rm -rf /var/lib/apt/lists/*

# c-ares (非同期 DNS ライブラリ) を静的ビルドする。
# glibc の getaddrinfo は NSS モジュールを dlopen で読み込むため、完全静的リンクでは
# 実行環境側にリンク時と同じ glibc の共有ライブラリが必要になり、名前解決が成立しない。
# libcurl の名前解決を c-ares に置き換えると /etc/resolv.conf から自前で DNS を引くため、
# この依存が消える。Debian の libc-ares-dev は静的アーカイブの名前がディストリ依存の
# ため、ソースからビルドする。
ARG CARES_VERSION=1.34.5
RUN curl -fsSL "https://github.com/c-ares/c-ares/releases/download/v${CARES_VERSION}/c-ares-${CARES_VERSION}.tar.gz" | tar xz -C /tmp \
    && cd "/tmp/c-ares-${CARES_VERSION}" \
    && ./configure --prefix=/usr/local --enable-static --disable-shared --disable-tests \
    && make -j "$(nproc)" && make install

# 最小構成の libcurl を静的ビルドする。
# ディストリの libcurl は nghttp2 や brotli などを含み、静的リンクするとそれら全ての
# 静的アーカイブが芋づる式に要る。通信は HTTP/1.1 + TLS で足りるため、それ以外を
# 無効にして依存ごと消す。
# --with-ca-bundle には実行先である AL2023 の CA バンドルのパスを指定する。
# ビルド環境 (Debian) のパスを指定すると、実行時に存在せず TLS 検証に失敗する。
ARG CURL_VERSION=8.11.1
RUN curl -fsSL "https://curl.se/download/curl-${CURL_VERSION}.tar.gz" | tar xz -C /tmp \
    && cd "/tmp/curl-${CURL_VERSION}" \
    && ./configure --prefix=/usr/local --enable-static --disable-shared \
         --with-openssl --with-zlib --enable-ares=/usr/local \
         --with-ca-bundle=/etc/pki/tls/certs/ca-bundle.crt \
         --disable-ftp --disable-file --disable-ldap --disable-ldaps \
         --disable-rtsp --disable-dict --disable-telnet --disable-tftp \
         --disable-pop3 --disable-imap --disable-smb --disable-smtp \
         --disable-gopher --disable-mqtt --disable-manual --disable-docs \
         --without-brotli --without-zstd --without-libpsl --without-libidn2 \
         --without-nghttp2 --without-ngtcp2 --without-libssh2 --without-librtmp \
    && make -j "$(nproc)" && make install

# libcurl が TLS (OpenSSL) と c-ares を伴っていることの検証。どちらかが欠けても
# リンクは通ってしまい、HTTPS 通信や名前解決が実行時に初めて失敗するため、
# ビルド時に検知する。
RUN /usr/local/bin/curl --version | grep -q "OpenSSL/" \
    && /usr/local/bin/curl --version | grep -q "c-ares/"

WORKDIR /work
COPY ./ ./

RUN scala-cli clean .
RUN scala-cli config power true
#  --server=false: Bloop ビルドサーバを使わずインプロセスでビルドする。
#  --native-linking: リンクオプションは Linux 前提のため project.scala ではなく
#    ここで指定し、macOS/Windows でのローカル開発を壊さない。ライブラリ群は
#    リンカーの解決順序に依存しないよう --start-group で 1 つの引数にまとめる。
#  --wrap=dlopen: Scala Native 0.5.12 は起動時のスタック境界検出で dlclose 済みの
#    関数ポインタを呼ぶ不具合があり、glibc 完全静的リンクでは起動直後に SIGSEGV する。
#    dlopen の呼び出しを getenv へ差し替えて常に NULL を返させ、近似スタック境界への
#    フォールバックに落とす (getenv は未知の名前に NULL を返し、余分な引数は
#    無視されるため代役になる)。
RUN scala-cli --power package --native --server=false \
      --native-linking "-static" \
      --native-linking "-Wl,--wrap=dlopen" \
      --native-linking "-Wl,--defsym=__wrap_dlopen=getenv" \
      --native-linking "-L/usr/local/lib" \
      --native-linking "-Wl,--start-group,-lcurl,-lssl,-lcrypto,-lz,-lzstd,-lcares,-lidn2,-lunistring,--end-group" \
      -o bootstrap .
RUN chmod +x bootstrap
# 静的リンクの検証。glibc の ldd は静的バイナリの判定に使えないため file で判定する。
RUN file bootstrap | grep -q "statically linked"
# 起動の検証。リンクが通り静的でもあるのに起動しない場合 (上記 dlopen の項) がある
# ため、実際に実行する。環境変数が未設定のため、設定エラーで即終了するのが正常。
RUN ./bootstrap 2>&1 | grep -q "required environment variable not set"
