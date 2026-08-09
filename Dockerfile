# ビルド専用イメージ: Scala Native の `bootstrap` を scala-cli 公式イメージ
# (Debian/glibc) 上で完全静的リンクする (#14)。
# 生成物はコンテナイメージではなく、抽出して Lambda の zip
# (provided.al2023 カスタムランタイム) としてデプロイする。
#
# 従来は Lambda 実行環境 (provided.al2023) と glibc/libcurl のバージョンを一致させる
# ために Amazon Linux 2023 上でビルドしていた。完全静的リンクすれば実行環境の共有
# ライブラリに一切依存しなくなるため、その制約自体が不要になる
# (lambda-scala-sls#22 / limit7412/lambda-scala-sls#30 に追従)。
#
# 公式イメージは amd64 単一アーキのため x86_64 が前提となる (arm64 から変更)。
# タグは再現性のためバージョン固定する。
FROM --platform=linux/amd64 virtuslab/scala-cli:1.16.0 AS build-image

# イメージには Debian + build-essential + clang + scala-cli が入っている。
# 追加で要るのは以下だけ。
#   curl / ca-certificates: c-ares / libcurl のソース取得
#   file                  : 静的リンクの検証用 (下部参照)
#   libssl-dev / zlib1g-dev: 静的リンクする libcurl の TLS / gzip 展開に使う
#                            (Debian の -dev パッケージは静的アーカイブ(.a)も含む)
#   libzstd-dev           : libcurl ではなく libcrypto.a が要求する。Debian の OpenSSL は
#                            zstd 圧縮 BIO 込みでビルドされており、静的リンクすると
#                            c_zstd.o が引き込まれて ZSTD_* が未解決になる
#   libidn2 / libunistring: libcurl ではなく sttp-model が @link("idn2") で要求する
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
         curl ca-certificates file \
         libssl-dev zlib1g-dev libzstd-dev libidn2-dev libunistring-dev \
    && rm -rf /var/lib/apt/lists/*

# c-ares (非同期 DNS ライブラリ) を静的ビルドする。
# 本アプリは GitHub API / 通知 webhook / 祝日 API のホスト名を解決する必要があるが、
# glibc の getaddrinfo は NSS モジュール (libnss_dns.so.2 等) を dlopen で読み込むため、
# 完全静的リンクではリンク時と同じ glibc の共有ライブラリが実行環境側に必要になる
# (ld も "Using 'getaddrinfo' in statically linked applications requires ..." と警告する)。
# ビルド環境が Debian で実行環境が AL2023 である以上これは満たせず、さらに後述の
# dlopen 差し替えとも両立しない。libcurl の名前解決を c-ares に置き換えると
# /etc/resolv.conf を読んで自前で DNS を引くようになり、NSS への依存が完全に消える。
# Debian の libc-ares-dev が置く静的アーカイブは名前がディストリ依存 (libcares_static.a)
# のため、リンク指定を安定させる意味でもソースからビルドする。
ARG CARES_VERSION=1.34.5
RUN curl -fsSL "https://github.com/c-ares/c-ares/releases/download/v${CARES_VERSION}/c-ares-${CARES_VERSION}.tar.gz" | tar xz -C /tmp \
    && cd "/tmp/c-ares-${CARES_VERSION}" \
    && ./configure --prefix=/usr/local --enable-static --disable-shared --disable-tests \
    && make -j "$(nproc)" && make install

# 最小構成の libcurl を静的ビルドする。
# ディストリの libcurl は nghttp2/brotli/zstd/psl/libidn2 込みのため、静的リンクすると
# それら全ての静的アーカイブが芋づる式に要る。通信先は GitHub API / 通知 webhook /
# 祝日 API / Lambda Runtime API だけで HTTP/1.1 + TLS があれば足りるので、それ以外を
# 無効にして自前ビルドすれば依存ごと消える。
# --prefix=/usr/local に入れると clang/ld の既定探索パスに載るため、ヘッダ側の
# -I 指定は不要 (ライブラリ側は下の -L/usr/local/lib で明示する)。
# --with-ca-bundle には *実行先* である AL2023 の CA バンドルのパスを指定する。
# ビルド環境 (Debian) のパスは実行時には存在しないため、ここを誤ると TLS 検証に失敗する。
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

# リンクする libcurl が TLS (OpenSSL) と c-ares を伴っていることを確認する。
# どちらかが欠けても静的リンク自体は通ってしまい、HTTPS 通信や名前解決が実行時に
# 初めて失敗するため、ビルド時に検知する。
# (ここでビルドした curl は CA バンドルのパスが AL2023 向けのため、以降の
#  ソース取得等には使わない。検証目的で絶対パス指定で叩く)
RUN /usr/local/bin/curl --version | grep -q "OpenSSL/" \
    && /usr/local/bin/curl --version | grep -q "c-ares/"

WORKDIR /work
COPY ./ ./

RUN scala-cli clean .
RUN scala-cli config power true
#  --server=false : Bloop ビルドサーバを使わずインプロセスでビルド
#  --native-linking: リンクオプションは Linux/リンカー依存のため project.scala ではなく
#    ここで指定し、macOS/Windows でのローカル開発を壊さないようにする。
#    Scala Native は @link 由来の -l を独自順で並べるため、解決順序に依存しないよう
#    ライブラリ群は 1 つの -Wl, 引数にまとめて原子的に渡す。
#
#  --wrap=dlopen / --defsym=__wrap_dlopen=getenv について:
#    Scala Native ランタイムは起動時のスタック境界検出で
#    dlopen("libpthread.so.0") → dlsym → dlclose と進み、**アンロード済み**の関数
#    ポインタを呼ぶ (nativeThreadTLS.c の get_pthread_getattr_np)。glibc 完全静的
#    リンクではこれが解放済みコードへの分岐になり、起動直後に SIGSEGV する
#    (println だけの最小プログラムでも再現する Scala Native 0.5.12 側の不具合)。
#    そこで dlopen の呼び出しを libc の getenv へ差し替えて常に NULL を返させ、
#    近似スタック境界へのフォールバックに落とす。dlopen は失敗時 NULL、getenv も
#    未定義の名前に対し NULL を返すため戻り値の形が一致し、dlopen の第 2 引数は
#    レジスタ渡しで無視される。
RUN scala-cli --power package --native --server=false \
      --native-linking "-static" \
      --native-linking "-Wl,--wrap=dlopen" \
      --native-linking "-Wl,--defsym=__wrap_dlopen=getenv" \
      --native-linking "-L/usr/local/lib" \
      --native-linking "-Wl,--start-group,-lcurl,-lssl,-lcrypto,-lz,-lzstd,-lcares,-lidn2,-lunistring,--end-group" \
      -o bootstrap .
RUN chmod +x bootstrap
# 静的リンクの検証。musl の ldd は静的バイナリに対し非ゼロ終了するのでそれを利用できたが、
# glibc の ldd は挙動が異なるため file で判定する。
RUN file bootstrap | grep -q "statically linked"
# 起動できることの検証。環境変数未設定なので Config の読み込みで即終了するのが正常。
# リンクが通り静的でもある (= 上の 2 つのチェックは通る) のに起動しない、という上記
# dlopen 由来の状態を検知するため。
RUN ./bootstrap 2>&1 | grep -q "required environment variable not set"
