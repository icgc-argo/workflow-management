#!/bin/sh
json=$1

if [ -z $json ]; then
   echo "Usage: $0 <json file>"
   exit 1
fi
set -x
curl -H "Content-type: application/json" -H "Accept: application/json" http://localhost:8080/runs -d @${json}
