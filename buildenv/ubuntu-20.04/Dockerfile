FROM ubuntu:20.04
LABEL Build environment for https://github.com/gorki-network/node for Debian 20.04
RUN apt-get update
# java 17
RUN apt-get install -y --no-install-recommends openjdk-17-jre
# sbt
# https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html#Ubuntu+and+other+Debian-based+distributions
RUN apt-get update \
    && apt-get install -y --no-install-recommends apt-transport-https curl gnupg \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list \
    && curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import \
    && chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg \
    && apt-get update \
    && apt-get install -y --no-install-recommends sbt
# git
RUN apt-get install -y --no-install-recommends git
# jflex
RUN apt-get install jflex -y --no-install-recommends
# bnfc
RUN apt-get install -y --no-install-recommends curl \
    && curl -sSL https://get.haskellstack.org/ | sh \
    && stack install BNFC \
    && ln -s /root/.local/bin/bnfc /usr/local/bin/bnfc