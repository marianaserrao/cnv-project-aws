#!/bin/bash

IP="3.238.221.192"
PORT="8000"
URL="http://$IP:$PORT/simulate?generations=3&world=3&scenario=3"
REQUEST_COUNT=5

echo $URL

for ((i=1; i<=REQUEST_COUNT; i++))
do
  echo "Sending request $i"
  curl -X GET $URL
done