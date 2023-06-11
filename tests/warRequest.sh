#!/bin/bash

IP="54.158.120.217"
PORT="8000"
URL="http://$IP:$PORT/insectwar?max=100&army1=10&army2=10"
REQUEST_COUNT=2000

for ((i=1; i<=REQUEST_COUNT; i++))
do
  echo "Sending request $i"
  curl -X GET $URL
done