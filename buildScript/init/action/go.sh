#!/bin/bash
set -e

source buildScript/init/env.sh
mkdir -p $PWD/build/golang
cd $golang

curl -Lso go.tar.gz https://go.dev/dl/go1.26.1.linux-amd64.tar.gz
echo "031f088e5d955bab8657ede27ad4e3bc5b7c1ba281f05f245bcc304f327c987a go.tar.gz" | sha256sum -c -
tar xzf go.tar.gz

go version
go env
