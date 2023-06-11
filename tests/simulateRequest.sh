#!/bin/bash

IP="54.158.120.217"
PORT="8000"
URL="http://$IP:$PORT/simulate?generations=3&world=3&scenario=3"
REQUEST_COUNT=2000

echo $URL

for ((i=1; i<=REQUEST_COUNT; i++))
do
  echo "Sending request $i"
  curl -X GET $URL
done