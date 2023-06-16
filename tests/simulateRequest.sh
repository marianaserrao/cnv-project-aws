#!/bin/bash

IP="localhost"
PORT="8000"
REQUEST_COUNT=100

echo "Generating random values..."

for ((i=1; i<=REQUEST_COUNT; i++))
do
  GENERATIONS=$((RANDOM % (100 - 1 + 1) + 1))
  WORLD=$((RANDOM % (4 - 1 + 1) + 1))
  SCENARIO=$((RANDOM % (3 - 1 + 1) + 1))
  
  URL="http://$IP:$PORT/simulate?generations=$GENERATIONS&world=$WORLD&scenario=$SCENARIO"

  echo "Sending request $i: $URL"
  curl -X GET $URL
done
